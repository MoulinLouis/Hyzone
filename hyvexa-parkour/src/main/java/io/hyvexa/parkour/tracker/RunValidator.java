package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.core.discord.DiscordLinkStore;
import io.hyvexa.parkour.ParkourConstants;
import io.hyvexa.core.economy.FeatherStore;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.Medal;
import io.hyvexa.parkour.data.MedalRewardStore;
import io.hyvexa.parkour.data.MedalStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.TransformData;
import io.hyvexa.parkour.ghost.GhostNpcManager;
import io.hyvexa.parkour.ghost.GhostRecorder;
import io.hyvexa.parkour.util.InventoryUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Checkpoint detection and finish logic extracted from RunTracker. */
public class RunValidator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double TOUCH_RADIUS_SQ = ParkourConstants.TOUCH_RADIUS_SQ;
    private static final String CHECKPOINT_HUD_BG_FAST = "#1E4A7A";
    private static final String CHECKPOINT_HUD_BG_SLOW = "#6A1E1E";
    private static final String CHECKPOINT_HUD_BG_TIE = "#000000";

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final MedalStore medalStore;
    private final MedalRewardStore medalRewardStore;
    private GhostRecorder ghostRecorder;
    private GhostNpcManager ghostNpcManager;
    private io.hyvexa.manager.HudManager hudManager;
    private io.hyvexa.core.cosmetic.CosmeticManager cosmeticManager;
    private java.util.function.Consumer<UUID> rankCacheInvalidator;
    private java.util.function.Consumer<Store<EntityStore>> leaderboardHologramRefresher;
    private java.util.function.BiConsumer<String, Store<EntityStore>> mapLeaderboardHologramRefresher;

    RunValidator(MapStore mapStore, ProgressStore progressStore,
                 MedalStore medalStore, MedalRewardStore medalRewardStore) {
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.medalStore = medalStore;
        this.medalRewardStore = medalRewardStore;
    }

    public void setPluginServices(io.hyvexa.manager.HudManager hudManager,
                           io.hyvexa.core.cosmetic.CosmeticManager cosmeticManager,
                           java.util.function.Consumer<UUID> rankCacheInvalidator,
                           java.util.function.Consumer<Store<EntityStore>> leaderboardHologramRefresher,
                           java.util.function.BiConsumer<String, Store<EntityStore>> mapLeaderboardHologramRefresher) {
        this.hudManager = hudManager;
        this.cosmeticManager = cosmeticManager;
        this.rankCacheInvalidator = rankCacheInvalidator;
        this.leaderboardHologramRefresher = leaderboardHologramRefresher;
        this.mapLeaderboardHologramRefresher = mapLeaderboardHologramRefresher;
    }

    void setGhostRecorder(GhostRecorder ghostRecorder) {
        this.ghostRecorder = ghostRecorder;
    }

    void setGhostNpcManager(GhostNpcManager ghostNpcManager) {
        this.ghostNpcManager = ghostNpcManager;
    }

    void checkCheckpoints(RunTracker.ActiveRun run, PlayerRef playerRef, Player player, Vector3d position, Map map,
                          Vector3d previousPosition, long previousElapsedMs, double deltaMs) {
        if (run.practiceEnabled) {
            return;
        }
        List<Long> personalBestSplits = run.personalBestSplits;
        List<TransformData> checkpoints = map.getCheckpoints();
        CheckpointDetector.detectCheckpoints(run, position, map,
                TOUCH_RADIUS_SQ, ParkourConstants.TOUCH_VERTICAL_BONUS,
                index -> {
                    TransformData checkpoint = checkpoints.get(index);
                    long elapsedMs = resolveInterpolatedTimeMs(run, previousPosition, position, checkpoint,
                            previousElapsedMs, deltaMs);
                    run.checkpointTouchTimes.put(index, elapsedMs);
                    TrackerUtils.playCheckpointSound(playerRef);
                    CheckpointSplitInfo splitInfo = buildCheckpointSplitInfo(index, elapsedMs, personalBestSplits);
                    player.sendMessage(splitInfo.message);
                    if (hudManager != null) {
                        if (splitInfo.hudText != null && splitInfo.hudColor != null) {
                            hudManager.showCheckpointSplit(playerRef, splitInfo.hudText, splitInfo.hudColor);
                        } else {
                            hudManager.showCheckpointSplit(playerRef, null, null);
                        }
                    }
                });
    }

    void checkFinish(RunTracker.ActiveRun run, PlayerRef playerRef, Player player, Vector3d position, Map map,
                     TransformComponent transform, Ref<EntityStore> ref, Store<EntityStore> store,
                     CommandBuffer<EntityStore> buffer, Vector3d previousPosition, long previousElapsedMs,
                     double deltaMs, RunSessionTracker sessionTracker, RunTeleporter teleporter,
                     RunTracker runTracker) {
        if (run.practiceEnabled) {
            return;
        }
        boolean finished = CheckpointDetector.detectFinish(run, position, map,
                TOUCH_RADIUS_SQ, ParkourConstants.TOUCH_VERTICAL_BONUS,
                () -> player.sendMessage(SystemMessageUtils.parkourWarn("You did not reach all checkpoints.")));
        if (!finished) {
            return;
        }
        TrackerUtils.playFinishSound(playerRef);
        long durationMs = resolveInterpolatedTimeMs(run, previousPosition, position, map.getFinish(),
                previousElapsedMs, deltaMs);

        UUID playerId = playerRef.getUuid();
        String playerName = playerRef.getUsername();
        Long previousBestMs = progressStore.getBestTimeMs(playerId, map.getId());
        int oldRank = progressStore.getCompletionRank(playerId, mapStore);

        ProgressStore.ProgressionResult result = recordCompletion(run, map, playerId, playerName,
                durationMs, ref, store);

        refreshHolograms(result, map, playerId, playerName, store);

        int leaderboardPosition = progressStore.getLeaderboardPosition(map.getId(), playerId);
        if (leaderboardPosition <= 0) {
            leaderboardPosition = 1;
        }

        int attempts = sessionTracker.getAttempts(playerId, map.getId()) + 1;
        sessionTracker.recordAttempt(playerId, map.getId());

        String mapName = getMapDisplayName(map);
        sendCompletionMessages(player, mapName, durationMs, previousBestMs, attempts, result.xpAwarded);
        int newRank = handleRankUp(playerId, player, oldRank);

        broadcastFinish(result, playerId, playerName, map, durationMs, leaderboardPosition,
                oldRank, newRank);

        awardMedalAndNotify(playerId, map, durationMs, player, ref, store);

        runTracker.recordFinishPing(run, playerRef);
        runTracker.sendLatencyWarning(run, player);
        teleporter.teleportToSpawn(ref, store, transform, buffer);
        teleporter.recordTeleport(playerId, RunTeleporter.TeleportCause.FINISH);
        runTracker.clearActiveMap(playerId);
        InventoryUtils.giveMenuItems(player);
    }

    private ProgressStore.ProgressionResult recordCompletion(RunTracker.ActiveRun run, Map map,
                                                              UUID playerId, String playerName,
                                                              long durationMs, Ref<EntityStore> ref,
                                                              Store<EntityStore> store) {
        List<TransformData> checkpoints = map.getCheckpoints();
        int checkpointCount = checkpoints != null ? checkpoints.size() : 0;
        List<Long> checkpointTimes = new ArrayList<>();
        for (int i = 0; i < checkpointCount; i++) {
            Long time = run.checkpointTouchTimes.get(i);
            checkpointTimes.add(time != null ? time : 0L);
        }
        ProgressStore.ProgressionResult result = progressStore.recordMapCompletion(playerId, playerName,
                map.getId(), durationMs, mapStore, checkpointTimes,
                completionSaved -> {
                    if (!completionSaved) {
                        warnCompletionSaveFailure(playerId, ref, store);
                    }
                });
        if (ghostRecorder != null) {
            ghostRecorder.stopRecording(playerId, durationMs, result.firstCompletion || result.newBest);
        }
        if (ghostNpcManager != null) {
            ghostNpcManager.despawnGhost(playerId);
        }
        return result;
    }

    private void refreshHolograms(ProgressStore.ProgressionResult result,
                                   Map map, UUID playerId, String playerName, Store<EntityStore> store) {
        if (result.firstCompletion && leaderboardHologramRefresher != null) {
            leaderboardHologramRefresher.accept(store);
        }
        if (mapLeaderboardHologramRefresher != null) {
            mapLeaderboardHologramRefresher.accept(map.getId(), store);
        }
    }

    private void broadcastFinish(ProgressStore.ProgressionResult result,
                                  UUID playerId, String playerName, Map map, long durationMs,
                                  int leaderboardPosition, int oldRank, int newRank) {
        if (!result.newBest) {
            return;
        }
        broadcastCompletion(playerId, playerName, map, durationMs, leaderboardPosition);
        boolean reachedVexaGod = newRank == ParkourConstants.COMPLETION_RANK_NAMES.length && oldRank < newRank;
        if (reachedVexaGod) {
            broadcastVexaGod(playerName);
        }
    }

    private void awardMedalAndNotify(UUID playerId, Map map, long durationMs,
                                      Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        MedalAwardResult medalResult = awardMedals(playerId, map, durationMs, player);
        if (medalResult == null) {
            return;
        }
        if (hudManager != null) {
            hudManager.showMedalNotification(
                    playerId, medalResult.medal(), medalResult.featherReward());
        }
        if (cosmeticManager != null) {
            cosmeticManager.applyCelebrationEffect(
                    ref, store, medalResult.medal().getEffectId(), 4.0f);
        }
    }

    private void sendCompletionMessages(Player player, String mapName, long durationMs,
                                        Long previousBestMs, int attempts, long xpAwarded) {
        player.sendMessage(SystemMessageUtils.withParkourPrefix(
                Message.raw("MAP COMPLETED: ").color(SystemMessageUtils.SUCCESS).bold(true),
                Message.raw(mapName).color(SystemMessageUtils.PRIMARY_TEXT)
        ));

        Message finishSplitPart = buildFinishSplitPart(durationMs, previousBestMs);
        player.sendMessage(SystemMessageUtils.withParkourPrefix(
                Message.raw("Time: ").color(SystemMessageUtils.SECONDARY),
                Message.raw(FormatUtils.formatDuration(durationMs)).color(SystemMessageUtils.PRIMARY_TEXT),
                finishSplitPart != null ? finishSplitPart : Message.raw("")
        ));

        if (attempts > 1) {
            player.sendMessage(SystemMessageUtils.withParkourPrefix(
                    Message.raw("Attempts: ").color(SystemMessageUtils.SECONDARY),
                    Message.raw(String.valueOf(attempts)).color(SystemMessageUtils.PRIMARY_TEXT)
            ));
        }

        if (xpAwarded > 0L) {
            player.sendMessage(SystemMessageUtils.parkourSuccess("You earned " + xpAwarded + " XP."));
        }
    }

    private int handleRankUp(UUID playerId, Player player, int oldRank) {
        int newRank = progressStore.getCompletionRank(playerId, mapStore);
        if (newRank > oldRank) {
            if (rankCacheInvalidator != null) {
                rankCacheInvalidator.accept(playerId);
            }
            String rankName = progressStore.getRankName(playerId, mapStore);
            player.sendMessage(SystemMessageUtils.parkourSuccess("Rank up! You are now " + rankName + "."));
            DiscordLinkStore.getInstance().updateRankIfLinkedAsync(playerId, rankName)
                    .exceptionally(ex -> {
                        LOGGER.atWarning().withCause(ex).log("Discord rank sync failed for " + playerId);
                        return null;
                    });
        }
        return newRank;
    }

    private CheckpointSplitInfo buildCheckpointSplitInfo(int checkpointIndex, long elapsedMs,
                                                         List<Long> personalBestSplits) {
        long pbSplitMs = 0L;
        if (personalBestSplits != null && checkpointIndex >= 0 && checkpointIndex < personalBestSplits.size()) {
            Long pbSplit = personalBestSplits.get(checkpointIndex);
            pbSplitMs = pbSplit != null ? Math.max(0L, pbSplit) : 0L;
        }
        if (pbSplitMs <= 0L) {
            return new CheckpointSplitInfo(SystemMessageUtils.parkourInfo("Checkpoint reached."), null, null);
        }

        long deltaMs = elapsedMs - pbSplitMs;
        long absDeltaMs = Math.abs(deltaMs);
        String deltaPrefix = deltaMs < 0L ? "-" : "+";
        String deltaColor = deltaMs <= 0L ? SystemMessageUtils.SUCCESS : SystemMessageUtils.ERROR;
        String hudBackground = deltaMs < 0L ? CHECKPOINT_HUD_BG_FAST
                : (deltaMs > 0L ? CHECKPOINT_HUD_BG_SLOW : CHECKPOINT_HUD_BG_TIE);
        String deltaText = deltaPrefix + FormatUtils.formatDuration(absDeltaMs);
        String checkpointTime = FormatUtils.formatDuration(Math.max(0L, elapsedMs));

        Message message = SystemMessageUtils.withParkourPrefix(
                Message.raw("Checkpoint ").color(SystemMessageUtils.SECONDARY),
                Message.raw("#" + (checkpointIndex + 1)).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(" split: ").color(SystemMessageUtils.SECONDARY),
                Message.raw(deltaText).color(deltaColor),
                Message.raw(" at ").color(SystemMessageUtils.SECONDARY),
                Message.raw(checkpointTime).color(SystemMessageUtils.PRIMARY_TEXT)
        );
        return new CheckpointSplitInfo(message, deltaText, hudBackground);
    }

    static final class CheckpointSplitInfo {
        final Message message;
        final String hudText;
        final String hudColor;

        CheckpointSplitInfo(Message message, String hudText, String hudColor) {
            this.message = message;
            this.hudText = hudText;
            this.hudColor = hudColor;
        }
    }

    private Message buildFinishSplitPart(long durationMs, Long previousBestMs) {
        if (previousBestMs == null || previousBestMs <= 0L) {
            return null;
        }
        long deltaMs = durationMs - previousBestMs;
        long absDeltaMs = Math.abs(deltaMs);
        String deltaPrefix = deltaMs < 0L ? "-" : "+";
        String deltaColor = deltaMs < 0L ? SystemMessageUtils.SUCCESS : SystemMessageUtils.ERROR;
        String deltaText = deltaPrefix + FormatUtils.formatDuration(absDeltaMs);
        return Message.join(
                Message.raw(" (").color(SystemMessageUtils.SECONDARY),
                Message.raw(deltaText).color(deltaColor),
                Message.raw(")").color(SystemMessageUtils.SECONDARY)
        );
    }

    private void broadcastCompletion(UUID playerId, String playerName, Map map, long durationMs, int leaderboardPosition) {
        String mapName = map.getName();
        if (mapName == null || mapName.isBlank()) {
            mapName = map.getId();
        }
        String category = map.getCategory();
        if (category == null || category.isBlank()) {
            category = "Uncategorized";
        } else {
            category = category.trim();
        }
        String rank = progressStore != null ? progressStore.getRankName(playerId, mapStore) : "Unranked";
        Message rankPart = FormatUtils.getRankMessage(rank);
        String categoryColor = getCategoryColor(category);
        boolean isWorldRecord = leaderboardPosition == 1;
        Message positionPart = Message.raw("#" + leaderboardPosition)
                .color(isWorldRecord ? "#ffd166" : SystemMessageUtils.INFO);
        Message wrPart = isWorldRecord
                ? Message.raw(" WR!").color("#ffd166")
                : Message.raw("");
        Message message = Message.join(
                Message.raw("[").color("#ffffff"),
                rankPart,
                Message.raw("] ").color("#ffffff"),
                Message.raw(playerName).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(" finished ").color(SystemMessageUtils.INFO),
                Message.raw(mapName).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(" (").color(SystemMessageUtils.INFO),
                Message.raw(category).color(categoryColor),
                Message.raw(") in ").color(SystemMessageUtils.INFO),
                Message.raw(FormatUtils.formatDuration(durationMs)).color(SystemMessageUtils.SUCCESS),
                Message.raw(" - ").color(SystemMessageUtils.INFO),
                positionPart,
                wrPart,
                Message.raw(".").color(SystemMessageUtils.INFO)
        );
        Message ggMessage = isWorldRecord
                ? Message.raw("WORLD RECORD! SAY GG!").color(SystemMessageUtils.SUCCESS).bold(true)
                : Message.empty();
        for (PlayerRef target : Universe.get().getPlayers()) {
            target.sendMessage(message);
            if (isWorldRecord) {
                target.sendMessage(ggMessage);
            }
        }
    }

    private void broadcastVexaGod(String playerName) {
        Message message = SystemMessageUtils.withParkourPrefix(
                Message.raw(playerName).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(" is now ").color(SystemMessageUtils.SECONDARY),
                FormatUtils.getRankMessage("VexaGod"),
                Message.raw(" (but for how long?)").color(SystemMessageUtils.SECONDARY)
        );
        Message ggMessage = SystemMessageUtils.withParkourPrefix(
                Message.raw("SEND GG IN THE CHAT!").color(SystemMessageUtils.SUCCESS).bold(true)
        );
        for (PlayerRef target : Universe.get().getPlayers()) {
            target.sendMessage(message);
            target.sendMessage(ggMessage);
        }
    }

    private String getCategoryColor(String category) {
        if (category == null) {
            return "#b2c0c7";
        }
        return switch (category.trim().toLowerCase()) {
            case "easy" -> "#54d28e";
            case "medium" -> "#f2c04d";
            case "hard" -> "#ff7a45";
            case "insane" -> "#ff4d6d";
            default -> "#b2c0c7";
        };
    }

    String getMapDisplayName(Map map) {
        if (map == null) {
            return "Map";
        }
        String mapName = map.getName();
        if (mapName == null || mapName.isBlank()) {
            return map.getId() != null && !map.getId().isBlank() ? map.getId() : "Map";
        }
        return mapName;
    }

    private void warnCompletionSaveFailure(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (playerId == null || store == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            Ref<EntityStore> targetRef = ref;
            Store<EntityStore> targetStore = store;
            if (targetRef == null || !targetRef.isValid()) {
                PlayerRef livePlayerRef = Universe.get().getPlayer(playerId);
                if (livePlayerRef == null) {
                    return;
                }
                targetRef = livePlayerRef.getReference();
                if (targetRef == null || !targetRef.isValid()) {
                    return;
                }
                targetStore = targetRef.getStore();
            }
            Player targetPlayer = targetStore.getComponent(targetRef, Player.getComponentType());
            if (targetPlayer == null) {
                return;
            }
            targetPlayer.sendMessage(SystemMessageUtils.parkourWarn(
                    "Warning: Your time might not have been saved. Please report this."));
        }, world).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> null);
    }

    private long resolveInterpolatedTimeMs(RunTracker.ActiveRun run, Vector3d previousPosition, Vector3d currentPosition,
                                           TransformData target, long previousElapsedMs, double deltaMs) {
        long currentElapsedMs = getRunElapsedMs(run);
        if (run == null || run.waitingForStart || previousPosition == null || currentPosition == null
                || target == null || deltaMs <= 0.0) {
            LOGGER.atFine().log("resolveInterpolatedTimeMs: fallback (invalid inputs, deltaMs=%.3f)", deltaMs);
            return currentElapsedMs;
        }
        double t = segmentSphereIntersectionT(previousPosition, currentPosition, target, ParkourConstants.TOUCH_RADIUS);
        if (!Double.isFinite(t)) {
            LOGGER.atFine().log("resolveInterpolatedTimeMs: fallback (non-finite t=%.4f)", t);
            return currentElapsedMs;
        }
        long interpolated = previousElapsedMs + Math.round(deltaMs * t);
        return Math.max(0L, Math.min(currentElapsedMs, interpolated));
    }

    private static double segmentSphereIntersectionT(Vector3d from, Vector3d to, TransformData target, double radius) {
        if (from == null || to == null || target == null) {
            LOGGER.atFine().log("segmentSphereIntersectionT: NaN (null inputs)");
            return Double.NaN;
        }
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double a = dx * dx + dy * dy + dz * dz;
        if (a <= 1e-9) {
            LOGGER.atFine().log("segmentSphereIntersectionT: NaN (zero-length segment, a=%.9f)", a);
            return Double.NaN;
        }
        double fx = from.getX() - target.getX();
        double fy = from.getY() - target.getY();
        double fz = from.getZ() - target.getZ();
        double b = 2.0 * (fx * dx + fy * dy + fz * dz);
        double c = fx * fx + fy * fy + fz * fz - radius * radius;
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0.0) {
            LOGGER.atFine().log("segmentSphereIntersectionT: NaN (no intersection, discriminant=%.4f)", discriminant);
            return Double.NaN;
        }
        double sqrt = Math.sqrt(discriminant);
        double t1 = (-b - sqrt) / (2.0 * a);
        if (t1 >= 0.0 && t1 <= 1.0) {
            return t1;
        }
        double t2 = (-b + sqrt) / (2.0 * a);
        if (t2 >= 0.0 && t2 <= 1.0) {
            return t2;
        }
        LOGGER.atFine().log("segmentSphereIntersectionT: NaN (roots outside [0,1], t1=%.4f, t2=%.4f)", t1, t2);
        return Double.NaN;
    }

    private long getRunElapsedMs(RunTracker.ActiveRun run) {
        if (run == null || run.waitingForStart) {
            return 0L;
        }
        return Math.max(0L, run.elapsedMs);
    }

    private record MedalAwardResult(Medal medal, int featherReward) {}

    private MedalAwardResult awardMedals(UUID playerId, Map map, long durationMs, Player player) {
        FeatherStore featherStore = FeatherStore.getInstance();
        String category = map.getCategory();
        Medal highestEarned = null;
        int highestFeathers = 0;
        // Award all qualifying medals (bronze, silver, gold) the player hasn't earned yet
        for (Medal medal : Medal.values()) {
            Long threshold = medal.getThreshold(map);
            if (threshold == null || threshold <= 0L) {
                continue;
            }
            if (durationMs > threshold) {
                continue;
            }
            if (medalStore.hasEarnedMedal(playerId, map.getId(), medal)) {
                continue;
            }
            medalStore.awardMedal(playerId, map.getId(), medal);
            int featherReward = medalRewardStore.getReward(category, medal);
            if (featherReward > 0) {
                featherStore.addFeathers(playerId, featherReward);
                player.sendMessage(SystemMessageUtils.parkourSuccess(
                        medal.name() + " Medal! +" + featherReward + " feathers"));
            } else {
                player.sendMessage(SystemMessageUtils.parkourSuccess(medal.name() + " Medal!"));
            }
            // Medal.values() goes BRONZE -> SILVER -> GOLD -> EMERALD, so last assigned is always highest
            highestEarned = medal;
            highestFeathers = featherReward;
        }
        // Award insane medal on any completion of an insane-category map
        if (category != null && category.trim().equalsIgnoreCase("insane")
                && !medalStore.hasEarnedMedal(playerId, map.getId(), Medal.INSANE)) {
            medalStore.awardMedal(playerId, map.getId(), Medal.INSANE);
            int featherReward = medalRewardStore.getReward(category, Medal.INSANE);
            if (featherReward > 0) {
                featherStore.addFeathers(playerId, featherReward);
                player.sendMessage(SystemMessageUtils.parkourSuccess(
                        "INSANE Medal! +" + featherReward + " feathers"));
            } else {
                player.sendMessage(SystemMessageUtils.parkourSuccess("INSANE Medal!"));
            }
            highestEarned = Medal.INSANE;
            highestFeathers = featherReward;
        }
        return highestEarned != null ? new MedalAwardResult(highestEarned, highestFeathers) : null;
    }
}
