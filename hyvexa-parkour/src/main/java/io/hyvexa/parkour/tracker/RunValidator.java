package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
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
import io.hyvexa.HyvexaPlugin;
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
class RunValidator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double TOUCH_RADIUS_SQ = ParkourConstants.TOUCH_RADIUS * ParkourConstants.TOUCH_RADIUS;
    private static final String CHECKPOINT_HUD_BG_FAST = "#1E4A7A";
    private static final String CHECKPOINT_HUD_BG_SLOW = "#6A1E1E";
    private static final String CHECKPOINT_HUD_BG_TIE = "#000000";

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private GhostRecorder ghostRecorder;
    private GhostNpcManager ghostNpcManager;

    RunValidator(MapStore mapStore, ProgressStore progressStore) {
        this.mapStore = mapStore;
        this.progressStore = progressStore;
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
        List<TransformData> checkpoints = map.getCheckpoints();
        if (checkpoints == null || checkpoints.isEmpty()) {
            return;
        }
        if (run.touchedCheckpoints.size() >= checkpoints.size()) {
            return;
        }
        List<Long> personalBestSplits = run.personalBestSplits;
        for (int i = 0; i < checkpoints.size(); i++) {
            if (run.touchedCheckpoints.contains(i)) {
                continue;
            }
            TransformData checkpoint = checkpoints.get(i);
            if (checkpoint == null) {
                continue;
            }
            if (distanceSqWithVerticalBonus(position, checkpoint) <= TOUCH_RADIUS_SQ) {
                run.touchedCheckpoints.add(i);
                run.lastCheckpointIndex = i;
                long elapsedMs = resolveInterpolatedTimeMs(run, previousPosition, position, checkpoint,
                        previousElapsedMs, deltaMs);
                run.checkpointTouchTimes.put(i, elapsedMs);
                TrackerUtils.playCheckpointSound(playerRef);
                CheckpointSplitInfo splitInfo = buildCheckpointSplitInfo(i, elapsedMs, personalBestSplits);
                player.sendMessage(splitInfo.message);
                HyvexaPlugin plugin = HyvexaPlugin.getInstance();
                if (plugin != null && plugin.getHudManager() != null) {
                    if (splitInfo.hudText != null && splitInfo.hudColor != null) {
                        plugin.getHudManager().showCheckpointSplit(playerRef, splitInfo.hudText, splitInfo.hudColor);
                    } else {
                        plugin.getHudManager().showCheckpointSplit(playerRef, null, null);
                    }
                }
            }
        }
    }

    void checkFinish(RunTracker.ActiveRun run, PlayerRef playerRef, Player player, Vector3d position, Map map,
                     TransformComponent transform, Ref<EntityStore> ref, Store<EntityStore> store,
                     CommandBuffer<EntityStore> buffer, Vector3d previousPosition, long previousElapsedMs,
                     double deltaMs, RunSessionTracker sessionTracker, RunTeleporter teleporter,
                     RunTracker runTracker) {
        if (run.practiceEnabled || run.finishTouched || map.getFinish() == null) {
            return;
        }
        if (distanceSqWithVerticalBonus(position, map.getFinish()) <= TOUCH_RADIUS_SQ) {
            List<TransformData> checkpoints = map.getCheckpoints();
            int checkpointCount = checkpoints != null ? checkpoints.size() : 0;
            if (checkpointCount > 0 && run.touchedCheckpoints.size() < checkpointCount) {
                long now = System.currentTimeMillis();
                if (now - run.lastFinishWarningMs >= 2000L) {
                    run.lastFinishWarningMs = now;
                    player.sendMessage(SystemMessageUtils.parkourWarn("You did not reach all checkpoints."));
                }
                return;
            }
            run.finishTouched = true;
            TrackerUtils.playFinishSound(playerRef);
            long durationMs = resolveInterpolatedTimeMs(run, previousPosition, position, map.getFinish(),
                    previousElapsedMs, deltaMs);
            List<Long> checkpointTimes = new ArrayList<>();
            for (int i = 0; i < checkpointCount; i++) {
                Long time = run.checkpointTouchTimes.get(i);
                checkpointTimes.add(time != null ? time : 0L);
            }
            UUID playerId = playerRef.getUuid();
            String playerName = playerRef.getUsername();
            Long previousBestMs = progressStore.getBestTimeMs(playerId, map.getId());
            int oldRank = progressStore.getCompletionRank(playerId, mapStore);
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
            if (result.firstCompletion) {
                HyvexaPlugin plugin = HyvexaPlugin.getInstance();
                if (plugin != null) {
                    plugin.refreshLeaderboardHologram(store);
                }
            }
            int leaderboardPosition = progressStore.getLeaderboardPosition(map.getId(), playerId);
            if (leaderboardPosition <= 0) {
                leaderboardPosition = 1;
            }
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin != null) {
                plugin.refreshMapLeaderboardHologram(map.getId(), store);
                plugin.logMapHologramDebug("Map holo refresh fired for '" + map.getId()
                        + "' (player " + playerName + ").");
            }

            int attempts = sessionTracker.getAttempts(playerId, map.getId()) + 1;
            sessionTracker.recordAttempt(playerId, map.getId());

            String mapName = getMapDisplayName(map);
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

            if (result.xpAwarded > 0L) {
                player.sendMessage(SystemMessageUtils.parkourSuccess("You earned " + result.xpAwarded + " XP."));
            }
            int newRank = progressStore.getCompletionRank(playerId, mapStore);
            boolean reachedVexaGod = newRank == ParkourConstants.COMPLETION_RANK_NAMES.length && oldRank < newRank;
            if (newRank > oldRank) {
                if (plugin != null) {
                    plugin.invalidateRankCache(playerId);
                }
                String rankName = progressStore.getRankName(playerId, mapStore);
                player.sendMessage(SystemMessageUtils.parkourSuccess("Rank up! You are now " + rankName + "."));
                DiscordLinkStore.getInstance().updateRankIfLinkedAsync(playerId, rankName)
                        .exceptionally(ex -> {
                            LOGGER.atWarning().withCause(ex).log("Discord rank sync failed for " + playerId);
                            return null;
                        });
            }
            if (result.newBest) {
                broadcastCompletion(playerId, playerName, map, durationMs, leaderboardPosition);
                if (reachedVexaGod) {
                    broadcastVexaGod(playerName);
                }
            }
            MedalAwardResult medalResult = awardMedals(playerId, map, durationMs, player);
            if (medalResult != null && plugin != null) {
                if (plugin.getHudManager() != null) {
                    plugin.getHudManager().showMedalNotification(
                            playerId, medalResult.medal(), medalResult.featherReward());
                }
                io.hyvexa.core.cosmetic.CosmeticManager.getInstance().applyCelebrationEffect(
                        ref, store, medalResult.medal().getEffectId(), 4.0f);
            }
            runTracker.recordFinishPing(run, playerRef);
            runTracker.sendLatencyWarning(run, player);
            teleporter.teleportToSpawn(ref, store, transform, buffer);
            teleporter.recordTeleport(playerId, RunTeleporter.TeleportCause.FINISH);
            runTracker.clearActiveMap(playerId);
            InventoryUtils.giveMenuItems(player);
        }
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

    private static double distanceSqWithVerticalBonus(Vector3d position, TransformData target) {
        return TrackerUtils.distanceSqWithVerticalBonus(position, target, ParkourConstants.TOUCH_VERTICAL_BONUS);
    }

    private long getRunElapsedMs(RunTracker.ActiveRun run) {
        if (run == null || run.waitingForStart) {
            return 0L;
        }
        return Math.max(0L, run.elapsedMs);
    }

    private record MedalAwardResult(Medal medal, int featherReward) {}

    private MedalAwardResult awardMedals(UUID playerId, Map map, long durationMs, Player player) {
        MedalStore medalStore = MedalStore.getInstance();
        MedalRewardStore rewardStore = MedalRewardStore.getInstance();
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
            int featherReward = rewardStore.getReward(category, medal);
            if (featherReward > 0) {
                featherStore.addFeathers(playerId, featherReward);
                player.sendMessage(SystemMessageUtils.parkourSuccess(
                        medal.name() + " Medal! +" + featherReward + " feathers"));
            } else {
                player.sendMessage(SystemMessageUtils.parkourSuccess(medal.name() + " Medal!"));
            }
            // Medal.values() goes BRONZE -> SILVER -> GOLD -> PLATINUM, so last assigned is always highest
            highestEarned = medal;
            highestFeathers = featherReward;
        }
        return highestEarned != null ? new MedalAwardResult(highestEarned, highestFeathers) : null;
    }
}
