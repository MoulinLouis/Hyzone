package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.packets.connection.PongType;
import com.hypixel.hytale.metrics.metric.HistoricMetric;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.InventoryUtils;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.parkour.ParkourConstants;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;
import io.hyvexa.parkour.data.TransformData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Tracks active parkour runs, checkpoint detection, and finish logic. */
public class RunTracker {

    private static final double TOUCH_RADIUS_SQ = ParkourConstants.TOUCH_RADIUS * ParkourConstants.TOUCH_RADIUS;
    private static final double START_MOVE_THRESHOLD_SQ = 0.0025;
    private static final long PING_SAMPLE_INTERVAL_MS = 1000L;
    private static final long PING_HIGH_THRESHOLD_MS = 100L;

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final SettingsStore settingsStore;
    private final ConcurrentHashMap<UUID, ActiveRun> activeRuns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, FallState> idleFalls = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TeleportStats> teleportStats = new ConcurrentHashMap<>();
    private final Set<UUID> readyPlayers = ConcurrentHashMap.newKeySet();

    public RunTracker(MapStore mapStore, ProgressStore progressStore,
                             SettingsStore settingsStore) {
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.settingsStore = settingsStore;
    }

    public ActiveRun setActiveMap(UUID playerId, String mapId) {
        return setActiveMap(playerId, mapId, null);
    }

    public ActiveRun setActiveMap(UUID playerId, String mapId, TransformData start) {
        ActiveRun run = new ActiveRun(mapId, System.currentTimeMillis());
        activeRuns.put(playerId, run);
        idleFalls.remove(playerId);
        armStartOnMovement(run, start);
        return run;
    }

    public void clearActiveMap(UUID playerId) {
        activeRuns.remove(playerId);
    }

    public void clearPlayer(UUID playerId) {
        clearActiveMap(playerId);
        idleFalls.remove(playerId);
        teleportStats.remove(playerId);
        readyPlayers.remove(playerId);
    }

    public java.util.Map<UUID, TeleportStatsSnapshot> drainTeleportStats() {
        if (teleportStats.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<UUID, TeleportStatsSnapshot> snapshots = new HashMap<>();
        for (java.util.Map.Entry<UUID, TeleportStats> entry : teleportStats.entrySet()) {
            TeleportStatsSnapshot snapshot = entry.getValue().snapshotAndReset();
            if (snapshot.isEmpty()) {
                teleportStats.remove(entry.getKey(), entry.getValue());
                continue;
            }
            snapshots.put(entry.getKey(), snapshot);
        }
        return snapshots;
    }

    public static final class TeleportStatsSnapshot {
        public final int startTrigger;
        public final int leaveTrigger;
        public final int runRespawn;
        public final int idleRespawn;
        public final int finish;
        public final int checkpoint;

        private TeleportStatsSnapshot(int startTrigger, int leaveTrigger, int runRespawn, int idleRespawn, int finish,
                                      int checkpoint) {
            this.startTrigger = startTrigger;
            this.leaveTrigger = leaveTrigger;
            this.runRespawn = runRespawn;
            this.idleRespawn = idleRespawn;
            this.finish = finish;
            this.checkpoint = checkpoint;
        }

        public boolean isEmpty() {
            return startTrigger == 0 && leaveTrigger == 0 && runRespawn == 0
                    && idleRespawn == 0 && finish == 0 && checkpoint == 0;
        }
    }

    public CheckpointProgress getCheckpointProgress(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        if (run == null) {
            return null;
        }
        Map map = mapStore.getMap(run.mapId);
        if (map == null) {
            return null;
        }
        int total = map.getCheckpoints().size();
        int touched = Math.min(run.touchedCheckpoints.size(), total);
        return new CheckpointProgress(touched, total);
    }

    public CheckpointSplit getLastCheckpointSplit(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        if (run == null) {
            return null;
        }
        int index = run.lastCheckpointIndex;
        if (index < 0) {
            return null;
        }
        Long timeMs = run.checkpointTouchTimes.get(index);
        if (timeMs == null) {
            return null;
        }
        return new CheckpointSplit(index, timeMs);
    }

    public void sweepStalePlayers(Set<UUID> onlinePlayers) {
        if (onlinePlayers == null || onlinePlayers.isEmpty()) {
            activeRuns.clear();
            idleFalls.clear();
            teleportStats.clear();
            return;
        }
        activeRuns.keySet().removeIf(id -> !onlinePlayers.contains(id));
        idleFalls.keySet().removeIf(id -> !onlinePlayers.contains(id));
        teleportStats.keySet().removeIf(id -> !onlinePlayers.contains(id));
        readyPlayers.removeIf(id -> !onlinePlayers.contains(id));
    }

    public String getActiveMapId(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        return run != null ? run.mapId : null;
    }

    public boolean isPracticeEnabled(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        return run != null && run.practiceEnabled;
    }

    public boolean enablePractice(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        if (run == null) {
            return false;
        }
        run.practiceEnabled = true;
        run.practiceCheckpoint = null;
        run.practiceHeadRotation = null;
        run.touchedCheckpoints.clear();
        run.checkpointTouchTimes.clear();
        run.lastCheckpointIndex = -1;
        run.finishTouched = false;
        return true;
    }

    public boolean setPracticeCheckpoint(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (playerRef == null) {
            return false;
        }
        ActiveRun run = activeRuns.get(playerRef.getUuid());
        if (run == null || !run.practiceEnabled) {
            return false;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return false;
        }
        Vector3d position = transform.getPosition();
        Vector3f rotation = transform.getRotation();
        if (position == null || rotation == null) {
            return false;
        }
        Vector3f headRotation = playerRef.getHeadRotation();
        TransformData checkpoint = new TransformData();
        checkpoint.setX(position.getX());
        checkpoint.setY(position.getY());
        checkpoint.setZ(position.getZ());
        Vector3f useRotation = headRotation != null ? headRotation : rotation;
        checkpoint.setRotX(useRotation.getX());
        checkpoint.setRotY(useRotation.getY());
        checkpoint.setRotZ(useRotation.getZ());
        run.practiceCheckpoint = checkpoint;
        run.practiceHeadRotation = headRotation != null ? headRotation.clone() : null;
        return true;
    }

    public Long getElapsedTimeMs(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        if (run == null) {
            return null;
        }
        if (run.waitingForStart) {
            return 0L;
        }
        return Math.max(0L, run.elapsedMs);
    }

    public void checkPlayer(Ref<EntityStore> ref, Store<EntityStore> store) {
        checkPlayer(ref, store, null, Float.NaN);
    }

    public void checkPlayer(Ref<EntityStore> ref, Store<EntityStore> store, float deltaSeconds) {
        checkPlayer(ref, store, null, deltaSeconds);
    }

    public void checkPlayer(Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                            float deltaSeconds) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        MovementStatesComponent movementStatesComponent = store.getComponent(ref, MovementStatesComponent.getComponentType());
        MovementStates movementStates = movementStatesComponent != null
                ? movementStatesComponent.getMovementStates()
                : null;
        if (playerRef == null || player == null || transform == null) {
            return;
        }
        if (!isPlayerReady(playerRef.getUuid())) {
            return;
        }
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin != null && plugin.getDuelTracker() != null
                && plugin.getDuelTracker().isInMatch(playerRef.getUuid())) {
            return;
        }
        ActiveRun run = activeRuns.get(playerRef.getUuid());
        Vector3d position = transform.getPosition();
        if (shouldTeleportFromVoid(position.getY())) {
            if (run == null) {
                teleportToSpawn(ref, store, transform, buffer);
                recordTeleport(playerRef.getUuid(), TeleportCause.IDLE_RESPAWN);
                return;
            }
            Map map = mapStore.getMap(run.mapId);
            if (map != null) {
                teleportToRespawn(ref, store, run, map, buffer);
                run.fallStartTime = null;
                run.lastY = null;
                recordTeleport(playerRef.getUuid(), TeleportCause.RUN_RESPAWN);
            } else {
                teleportToSpawn(ref, store, transform, buffer);
                recordTeleport(playerRef.getUuid(), TeleportCause.IDLE_RESPAWN);
            }
            return;
        }
        if (run == null) {
            long fallTimeoutMs = getFallRespawnTimeoutMs();
            boolean allowOpIdleFall = settingsStore != null && settingsStore.isIdleFallRespawnForOp();
            if (fallTimeoutMs > 0 && (allowOpIdleFall || !PermissionUtils.isOp(player))
                    && shouldRespawnFromFall(getIdleFallState(playerRef.getUuid()), position.getY(),
                    movementStates,
                    fallTimeoutMs)) {
                teleportToSpawn(ref, store, transform, buffer);
                recordTeleport(playerRef.getUuid(), TeleportCause.IDLE_RESPAWN);
                return;
            }
            Map triggerMap = findStartTriggerMap(position);
            if (triggerMap != null) {
                startRunFromTrigger(ref, store, playerRef, player, triggerMap, buffer);
            }
            return;
        }
        Map map = mapStore.getMap(run.mapId);
        if (map == null) {
            return;
        }
        updateStartOnMovement(run, position);
        long previousElapsedMs = getRunElapsedMs(run);
        Vector3d previousPosition = run.lastPosition;
        advanceRunTime(run, deltaSeconds);
        long currentElapsedMs = getRunElapsedMs(run);
        double deltaMs = Math.max(0.0, currentElapsedMs - previousElapsedMs);
        updatePingStats(run, playerRef, currentElapsedMs);
        if (checkLeaveTrigger(ref, store, player, playerRef, position, map, buffer)) {
            return;
        }
        checkCheckpoints(run, playerRef, player, position, map, previousPosition, previousElapsedMs, deltaMs);
        long fallTimeoutMs = getFallRespawnTimeoutMs();
        if (map.isFreeFallEnabled()) {
            run.fallStartTime = null;
            run.lastY = position.getY();
            fallTimeoutMs = 0L;
        }
        if (fallTimeoutMs > 0 && shouldRespawnFromFall(run, position.getY(), movementStates, fallTimeoutMs)) {
            run.fallStartTime = null;
            run.lastY = null;
            if (run.lastCheckpointIndex < 0) {
                armStartOnMovement(run, map.getStart());
            }
            teleportToRespawn(ref, store, run, map, buffer);
            recordTeleport(playerRef.getUuid(), TeleportCause.RUN_RESPAWN);
            return;
        }
        checkFinish(run, playerRef, player, position, map, transform, ref, store, buffer, previousPosition,
                previousElapsedMs, deltaMs);
        if (activeRuns.get(playerRef.getUuid()) == run) {
            run.lastPosition = copyPosition(position);
        }
    }

    private Map findStartTriggerMap(Vector3d position) {
        for (Map map : mapStore.listMaps()) {
            TransformData trigger = map.getStartTrigger();
            if (trigger == null) {
                continue;
            }
            if (distanceSq(position, trigger) <= TOUCH_RADIUS_SQ) {
                return map;
            }
        }
        return null;
    }

    private void startRunFromTrigger(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
                                     Player player, Map map, CommandBuffer<EntityStore> buffer) {
        if (map.getStart() == null) {
            player.sendMessage(SystemMessageUtils.parkourError("Map '" + map.getId() + "' has no start set."));
            return;
        }
        setActiveMap(playerRef.getUuid(), map.getId(), map.getStart());
        Vector3d position = new Vector3d(map.getStart().getX(), map.getStart().getY(), map.getStart().getZ());
        Vector3f rotation = new Vector3f(map.getStart().getRotX(), map.getStart().getRotY(),
                map.getStart().getRotZ());
        addTeleport(ref, store, buffer, new Teleport(store.getExternalData().getWorld(), position, rotation));
        recordTeleport(playerRef.getUuid(), TeleportCause.START_TRIGGER);
        player.sendMessage(buildRunStartMessage(map));
        InventoryUtils.giveRunItems(player, map, false);
    }

    private boolean checkLeaveTrigger(Ref<EntityStore> ref, Store<EntityStore> store, Player player,
                                      PlayerRef playerRef, Vector3d position, Map map,
                                      CommandBuffer<EntityStore> buffer) {
        TransformData trigger = map.getLeaveTrigger();
        if (trigger == null) {
            return false;
        }
        if (distanceSq(position, trigger) > TOUCH_RADIUS_SQ) {
            return false;
        }
        TransformData leaveTeleport = map.getLeaveTeleport();
        if (leaveTeleport != null) {
            Vector3d targetPosition = new Vector3d(leaveTeleport.getX(), leaveTeleport.getY(), leaveTeleport.getZ());
            Vector3f targetRotation = new Vector3f(leaveTeleport.getRotX(), leaveTeleport.getRotY(),
                    leaveTeleport.getRotZ());
            addTeleport(ref, store, buffer, new Teleport(store.getExternalData().getWorld(), targetPosition, targetRotation));
            recordTeleport(playerRef.getUuid(), TeleportCause.LEAVE_TRIGGER);
        }
        clearActiveMap(playerRef.getUuid());
        InventoryUtils.giveMenuItems(player);
        player.sendMessage(buildRunEndMessage(map));
        return true;
    }

    private Message buildRunStartMessage(Map map) {
        String mapName = getMapDisplayName(map);
        return SystemMessageUtils.withParkourPrefix(
                Message.raw("Run started: ").color(SystemMessageUtils.SECONDARY),
                Message.raw(mapName).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(".").color(SystemMessageUtils.SECONDARY)
        );
    }

    private Message buildRunEndMessage(Map map) {
        String mapName = getMapDisplayName(map);
        return SystemMessageUtils.withParkourPrefix(
                Message.raw("Run ended: ").color(SystemMessageUtils.SECONDARY),
                Message.raw(mapName).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(".").color(SystemMessageUtils.SECONDARY)
        );
    }

    private String getMapDisplayName(Map map) {
        if (map == null) {
            return "Map";
        }
        String mapName = map.getName();
        if (mapName == null || mapName.isBlank()) {
            return map.getId() != null && !map.getId().isBlank() ? map.getId() : "Map";
        }
        return mapName;
    }

    private long getFallRespawnTimeoutMs() {
        double seconds = settingsStore != null
                ? settingsStore.getFallRespawnSeconds()
                : ParkourConstants.DEFAULT_FALL_RESPAWN_SECONDS;
        if (seconds <= 0) {
            return 0L;
        }
        return (long) Math.max(1L, seconds * 1000.0);
    }

    private boolean shouldRespawnFromFall(ActiveRun run, double currentY, MovementStates movementStates,
                                          long fallTimeoutMs) {
        if (isFallTrackingBlocked(movementStates)) {
            run.fallStartTime = null;
            run.lastY = currentY;
            return false;
        }
        if (run.lastY == null) {
            run.lastY = currentY;
            run.fallStartTime = null;
            return false;
        }
        long now = System.currentTimeMillis();
        if (currentY < run.lastY) {
            if (run.fallStartTime == null) {
                run.fallStartTime = now;
            }
            if (now - run.fallStartTime >= fallTimeoutMs) {
                return true;
            }
        } else {
            run.fallStartTime = null;
        }
        run.lastY = currentY;
        return false;
    }

    private boolean shouldRespawnFromFall(FallState fallState, double currentY, MovementStates movementStates,
                                          long fallTimeoutMs) {
        if (isFallTrackingBlocked(movementStates)) {
            fallState.fallStartTime = null;
            fallState.lastY = currentY;
            return false;
        }
        if (fallState.lastY == null) {
            fallState.lastY = currentY;
            fallState.fallStartTime = null;
            return false;
        }
        long now = System.currentTimeMillis();
        if (currentY < fallState.lastY) {
            if (fallState.fallStartTime == null) {
                fallState.fallStartTime = now;
            }
            if (now - fallState.fallStartTime >= fallTimeoutMs) {
                fallState.fallStartTime = null;
                fallState.lastY = currentY;
                return true;
            }
        } else {
            fallState.fallStartTime = null;
        }
        fallState.lastY = currentY;
        return false;
    }

    private void checkCheckpoints(ActiveRun run, PlayerRef playerRef, Player player, Vector3d position, Map map,
                                  Vector3d previousPosition, long previousElapsedMs, double deltaMs) {
        if (run.practiceEnabled) {
            return;
        }
        List<TransformData> checkpoints = map.getCheckpoints();
        if (checkpoints == null || checkpoints.isEmpty()) {
            return;
        }
        List<Long> personalBestSplits = progressStore != null
                ? progressStore.getCheckpointTimes(playerRef.getUuid(), map.getId())
                : List.of();
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
                playCheckpointSound(playerRef);
                player.sendMessage(buildCheckpointSplitMessage(i, elapsedMs, personalBestSplits));
            }
        }
    }

    private Message buildCheckpointSplitMessage(int checkpointIndex, long elapsedMs, List<Long> personalBestSplits) {
        long pbSplitMs = 0L;
        if (personalBestSplits != null && checkpointIndex >= 0 && checkpointIndex < personalBestSplits.size()) {
            Long pbSplit = personalBestSplits.get(checkpointIndex);
            pbSplitMs = pbSplit != null ? Math.max(0L, pbSplit) : 0L;
        }
        if (pbSplitMs <= 0L) {
            return SystemMessageUtils.parkourInfo("Checkpoint reached.");
        }

        long deltaMs = elapsedMs - pbSplitMs;
        long absDeltaMs = Math.abs(deltaMs);
        String deltaPrefix = deltaMs < 0L ? "-" : "+";
        String deltaColor = deltaMs <= 0L ? SystemMessageUtils.SUCCESS : SystemMessageUtils.ERROR;
        String deltaText = deltaPrefix + FormatUtils.formatDurationPrecise(absDeltaMs);
        String checkpointTime = FormatUtils.formatDurationPrecise(Math.max(0L, elapsedMs));

        return SystemMessageUtils.withParkourPrefix(
                Message.raw("Checkpoint ").color(SystemMessageUtils.SECONDARY),
                Message.raw("#" + (checkpointIndex + 1)).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(" split: ").color(SystemMessageUtils.SECONDARY),
                Message.raw(deltaText).color(deltaColor),
                Message.raw(" at ").color(SystemMessageUtils.SECONDARY),
                Message.raw(checkpointTime).color(SystemMessageUtils.PRIMARY_TEXT)
        );
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

    private void checkFinish(ActiveRun run, PlayerRef playerRef, Player player, Vector3d position, Map map,
                             TransformComponent transform, Ref<EntityStore> ref, Store<EntityStore> store,
                             CommandBuffer<EntityStore> buffer, Vector3d previousPosition, long previousElapsedMs,
                             double deltaMs) {
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
            playFinishSound(playerRef);
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
                    map.getId(), durationMs, mapStore, checkpointTimes);
            if (!result.completionSaved) {
                player.sendMessage(SystemMessageUtils.parkourWarn(
                        "Warning: Your time might not have been saved. Please report this."));
            }
            int leaderboardPosition = progressStore.getLeaderboardPosition(map.getId(), playerId);
            if (leaderboardPosition <= 0) {
                leaderboardPosition = 1;
            }
            Message finishSplitPart = buildFinishSplitPart(durationMs, previousBestMs);
            Message completionMessage = SystemMessageUtils.withParkourPrefix(
                    Message.raw("Map completed in ").color(SystemMessageUtils.SECONDARY),
                    Message.raw(FormatUtils.formatDuration(durationMs)).color(SystemMessageUtils.SUCCESS),
                    finishSplitPart != null ? finishSplitPart : Message.raw(""),
                    Message.raw(".").color(SystemMessageUtils.SECONDARY)
            );
            player.sendMessage(completionMessage);
            if (result.xpAwarded > 0L) {
                player.sendMessage(SystemMessageUtils.parkourSuccess("You earned " + result.xpAwarded + " XP."));
            }
            int newRank = progressStore.getCompletionRank(playerId, mapStore);
            boolean reachedVexaGod = newRank == ParkourConstants.COMPLETION_RANK_NAMES.length && oldRank < newRank;
            if (newRank > oldRank) {
                HyvexaPlugin plugin = HyvexaPlugin.getInstance();
                if (plugin != null) {
                    plugin.invalidateRankCache(playerId);
                }
                String rankName = progressStore.getRankName(playerId, mapStore);
                player.sendMessage(SystemMessageUtils.parkourSuccess("Rank up! You are now " + rankName + "."));
            }
            if (result.newBest) {
                broadcastCompletion(playerId, playerName, map, durationMs, leaderboardPosition);
                if (reachedVexaGod) {
                    broadcastVexaGod(playerName);
                }
            }
            sendLagAccuracyMessage(run, player);
            teleportToSpawn(ref, store, transform, buffer);
            recordTeleport(playerId, TeleportCause.FINISH);
            clearActiveMap(playerId);
            InventoryUtils.giveMenuItems(player);
        }
    }

    private void playFinishSound(PlayerRef playerRef) {
        if (playerRef == null || !io.hyvexa.parkour.ui.PlayerMusicPage.isVictorySfxEnabled(playerRef.getUuid())) {
            return;
        }
        int soundIndex = SoundEvent.getAssetMap().getIndex("SFX_Parkour_Victory");
        if (soundIndex <= SoundEvent.EMPTY_ID) {
            return;
        }
        SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, com.hypixel.hytale.protocol.SoundCategory.SFX);
    }

    private void playCheckpointSound(PlayerRef playerRef) {
        if (playerRef == null || !io.hyvexa.parkour.ui.PlayerMusicPage.isCheckpointSfxEnabled(playerRef.getUuid())) {
            return;
        }
        int soundIndex = SoundEvent.getAssetMap().getIndex("SFX_Parkour_Checkpoint");
        if (soundIndex <= SoundEvent.EMPTY_ID) {
            return;
        }
        SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, com.hypixel.hytale.protocol.SoundCategory.SFX);
    }

    private static double distanceSq(Vector3d position, TransformData target) {
        double dx = position.getX() - target.getX();
        double dy = position.getY() - target.getY();
        double dz = position.getZ() - target.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static double distanceSqWithVerticalBonus(Vector3d position, TransformData target) {
        double dx = position.getX() - target.getX();
        double dy = position.getY() - target.getY();
        if (dy > 0) {
            dy = Math.max(0.0, dy - ParkourConstants.TOUCH_VERTICAL_BONUS);
        }
        double dz = position.getZ() - target.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static double distanceSq(Vector3d position, Vector3d target) {
        double dx = position.getX() - target.getX();
        double dy = position.getY() - target.getY();
        double dz = position.getZ() - target.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private void armStartOnMovement(ActiveRun run, TransformData start) {
        if (run == null) {
            return;
        }
        if (start == null) {
            run.waitingForStart = false;
            run.startPosition = null;
            return;
        }
        run.startPosition = new Vector3d(start.getX(), start.getY(), start.getZ());
        run.waitingForStart = true;
        run.startTimeMs = System.currentTimeMillis();
        run.elapsedMs = 0L;
        run.elapsedRemainderMs = 0.0;
        run.skipNextTimeIncrement = false;
        resetPingStats(run);
    }

    private void updateStartOnMovement(ActiveRun run, Vector3d position) {
        if (run == null || !run.waitingForStart || run.startPosition == null || position == null) {
            return;
        }
        if (distanceSq(position, run.startPosition) > START_MOVE_THRESHOLD_SQ) {
            run.waitingForStart = false;
            run.startTimeMs = System.currentTimeMillis();
            run.elapsedMs = 0L;
            run.elapsedRemainderMs = 0.0;
            run.skipNextTimeIncrement = true;
            resetPingStats(run);
        }
    }

    private void teleportToRespawn(Ref<EntityStore> ref, Store<EntityStore> store, ActiveRun run, Map map,
                                   CommandBuffer<EntityStore> buffer) {
        if (run != null) {
            run.lastPosition = null;
        }
        TransformData spawn = null;
        if (run.practiceEnabled && run.practiceCheckpoint != null) {
            spawn = run.practiceCheckpoint;
        }
        int checkpointIndex = resolveCheckpointIndex(run, map);
        if (spawn == null && checkpointIndex >= 0 && checkpointIndex < map.getCheckpoints().size()) {
            spawn = map.getCheckpoints().get(checkpointIndex);
        }
        if (spawn == null) {
            spawn = map.getStart();
        }
        if (spawn == null) {
            return;
        }
        Vector3d position = new Vector3d(spawn.getX(), spawn.getY(), spawn.getZ());
        Vector3f rotation = new Vector3f(spawn.getRotX(), spawn.getRotY(), spawn.getRotZ());
        addTeleport(ref, store, buffer, new Teleport(store.getExternalData().getWorld(), position, rotation));
    }

    public boolean teleportToLastCheckpoint(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (playerRef == null) {
            return false;
        }
        ActiveRun run = activeRuns.get(playerRef.getUuid());
        if (run == null) {
            return false;
        }
        if (run.practiceEnabled) {
            return teleportToPracticeCheckpoint(ref, store, playerRef, run);
        }
        Map map = mapStore.getMap(run.mapId);
        if (map == null) {
            return false;
        }
        int checkpointIndex = resolveCheckpointIndex(run, map);
        if (checkpointIndex < 0 || checkpointIndex >= map.getCheckpoints().size()) {
            return false;
        }
        TransformData checkpoint = map.getCheckpoints().get(checkpointIndex);
        if (checkpoint == null) {
            return false;
        }
        Vector3d position = new Vector3d(checkpoint.getX(), checkpoint.getY(), checkpoint.getZ());
        Vector3f rotation = new Vector3f(checkpoint.getRotX(), checkpoint.getRotY(), checkpoint.getRotZ());
        store.addComponent(ref, Teleport.getComponentType(),
                new Teleport(store.getExternalData().getWorld(), position, rotation));
        recordTeleport(playerRef.getUuid(), TeleportCause.CHECKPOINT);
        run.fallStartTime = null;
        run.lastY = null;
        run.lastPosition = null;
        return true;
    }

    private boolean teleportToPracticeCheckpoint(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
                                                 ActiveRun run) {
        if (run == null || run.practiceCheckpoint == null) {
            return false;
        }
        Vector3d position = new Vector3d(run.practiceCheckpoint.getX(),
                run.practiceCheckpoint.getY(),
                run.practiceCheckpoint.getZ());
        Vector3f rotation = new Vector3f(run.practiceCheckpoint.getRotX(),
                run.practiceCheckpoint.getRotY(),
                run.practiceCheckpoint.getRotZ());
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return false;
        }
        Teleport teleport = Teleport.createForPlayer(world, new Transform(position, rotation));
        if (run.practiceHeadRotation != null) {
            teleport.setHeadRotation(run.practiceHeadRotation.clone());
        }
        store.addComponent(ref, Teleport.getComponentType(), teleport);
        recordTeleport(playerRef.getUuid(), TeleportCause.CHECKPOINT);
        run.fallStartTime = null;
        run.lastY = null;
        run.lastPosition = null;
        return true;
    }

    private int resolveCheckpointIndex(ActiveRun run, Map map) {
        if (run == null || map == null) {
            return -1;
        }
        int index = run.lastCheckpointIndex;
        if (index >= 0 && index < map.getCheckpoints().size()) {
            return index;
        }
        int best = -1;
        for (Integer touched : run.touchedCheckpoints) {
            if (touched == null) {
                continue;
            }
            int candidate = touched;
            if (candidate >= 0 && candidate < map.getCheckpoints().size()) {
                best = Math.max(best, candidate);
            }
        }
        return best;
    }

    public boolean resetRunToStart(Ref<EntityStore> ref, Store<EntityStore> store, Player player, PlayerRef playerRef) {
        if (playerRef == null || player == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            player.sendMessage(SystemMessageUtils.parkourError("World not available."));
            return false;
        }
        String mapId = getActiveMapId(playerRef.getUuid());
        if (mapId == null) {
            player.sendMessage(SystemMessageUtils.parkourWarn("No active map to reset."));
            return false;
        }
        Map map = mapStore.getMap(mapId);
        if (map == null || map.getStart() == null) {
            player.sendMessage(SystemMessageUtils.parkourError("Map start not available."));
            return false;
        }
        ActiveRun previous = activeRuns.get(playerRef.getUuid());
        boolean practiceEnabled = previous != null && previous.practiceEnabled;
        TransformData practiceCheckpoint = previous != null ? previous.practiceCheckpoint : null;
        Vector3f practiceHeadRotation = previous != null ? previous.practiceHeadRotation : null;
        ActiveRun run = setActiveMap(playerRef.getUuid(), mapId, map.getStart());
        if (run != null) {
            run.practiceEnabled = practiceEnabled;
            run.practiceCheckpoint = practiceCheckpoint;
            run.practiceHeadRotation = practiceHeadRotation;
        }
        Vector3d position = new Vector3d(map.getStart().getX(), map.getStart().getY(), map.getStart().getZ());
        Vector3f rotation = new Vector3f(map.getStart().getRotX(), map.getStart().getRotY(),
                map.getStart().getRotZ());
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, position, rotation));
        return true;
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

    public void teleportToSpawn(Ref<EntityStore> ref, Store<EntityStore> store, TransformComponent transform) {
        teleportToSpawn(ref, store, transform, null);
    }

    private void teleportToSpawn(Ref<EntityStore> ref, Store<EntityStore> store, TransformComponent transform,
                                 CommandBuffer<EntityStore> buffer) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        com.hypixel.hytale.math.vector.Transform spawnTransform = null;
        var worldConfig = world.getWorldConfig();
        if (worldConfig != null && worldConfig.getSpawnProvider() != null) {
            spawnTransform = worldConfig.getSpawnProvider().getSpawnPoint(world, playerRef.getUuid());
        }
        Vector3d position = spawnTransform != null
                ? spawnTransform.getPosition()
                : (transform != null ? transform.getPosition() : new Vector3d(0.0, 0.0, 0.0));
        Vector3f rotation = spawnTransform != null
                ? spawnTransform.getRotation()
                : (transform != null ? transform.getRotation() : new Vector3f(0f, 0f, 0f));
        addTeleport(ref, store, buffer, new Teleport(world, position, rotation));
    }

    private static class ActiveRun {
        private final String mapId;
        private long startTimeMs;
        private boolean waitingForStart;
        private Vector3d startPosition;
        private Vector3d lastPosition;
        private final Set<Integer> touchedCheckpoints = new HashSet<>();
        private final java.util.Map<Integer, Long> checkpointTouchTimes = new HashMap<>();
        private boolean finishTouched;
        private int lastCheckpointIndex = -1;
        private boolean practiceEnabled;
        private TransformData practiceCheckpoint;
        private Vector3f practiceHeadRotation;
        private Long fallStartTime;
        private Double lastY;
        private long lastFinishWarningMs;
        private long elapsedMs;
        private double elapsedRemainderMs;
        private boolean skipNextTimeIncrement;
        private long pingSampleCount;
        private double pingSumMs;
        private long nextPingSampleAtMs;

        private ActiveRun(String mapId, long startTimeMs) {
            this.mapId = mapId;
            this.startTimeMs = startTimeMs;
            this.elapsedMs = 0L;
        }
    }

    private static class FallState {
        private Long fallStartTime;
        private Double lastY;
    }

    private enum TeleportCause {
        START_TRIGGER,
        LEAVE_TRIGGER,
        RUN_RESPAWN,
        IDLE_RESPAWN,
        FINISH,
        CHECKPOINT
    }

    private static final class TeleportStats {
        private final AtomicInteger startTrigger = new AtomicInteger();
        private final AtomicInteger leaveTrigger = new AtomicInteger();
        private final AtomicInteger runRespawn = new AtomicInteger();
        private final AtomicInteger idleRespawn = new AtomicInteger();
        private final AtomicInteger finish = new AtomicInteger();
        private final AtomicInteger checkpoint = new AtomicInteger();

        private void increment(TeleportCause cause) {
            if (cause == null) {
                return;
            }
            switch (cause) {
                case START_TRIGGER -> startTrigger.incrementAndGet();
                case LEAVE_TRIGGER -> leaveTrigger.incrementAndGet();
                case RUN_RESPAWN -> runRespawn.incrementAndGet();
                case IDLE_RESPAWN -> idleRespawn.incrementAndGet();
                case FINISH -> finish.incrementAndGet();
                case CHECKPOINT -> checkpoint.incrementAndGet();
            }
        }

        private TeleportStatsSnapshot snapshotAndReset() {
            return new TeleportStatsSnapshot(
                    startTrigger.getAndSet(0),
                    leaveTrigger.getAndSet(0),
                    runRespawn.getAndSet(0),
                    idleRespawn.getAndSet(0),
                    finish.getAndSet(0),
                    checkpoint.getAndSet(0)
            );
        }
    }

    public static class CheckpointProgress {
        public final int touched;
        public final int total;

        public CheckpointProgress(int touched, int total) {
            this.touched = touched;
            this.total = total;
        }
    }

    public static class CheckpointSplit {
        public final int index;
        public final long timeMs;

        public CheckpointSplit(int index, long timeMs) {
            this.index = index;
            this.timeMs = timeMs;
        }
    }

    private boolean isFallTrackingBlocked(MovementStates movementStates) {
        return movementStates != null && (movementStates.climbing || movementStates.onGround);
    }

    private FallState getIdleFallState(UUID playerId) {
        return idleFalls.computeIfAbsent(playerId, ignored -> new FallState());
    }

    private boolean shouldTeleportFromVoid(double currentY) {
        double voidY = settingsStore != null
                ? settingsStore.getFallFailsafeVoidY()
                : ParkourConstants.FALL_FAILSAFE_VOID_Y;
        return Double.isFinite(voidY) && currentY <= voidY;
    }

    public void markPlayerReady(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId != null) {
            readyPlayers.add(playerId);
        }
    }

    public void markPlayerReady(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) {
                return;
            }
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            UUID playerId = playerRef.getUuid();
            if (playerId != null) {
                readyPlayers.add(playerId);
            }
        }, world);
    }

    private boolean isPlayerReady(UUID playerId) {
        return playerId != null && readyPlayers.contains(playerId);
    }

    private void recordTeleport(UUID playerId, TeleportCause cause) {
        if (playerId == null || cause == null) {
            return;
        }
        teleportStats.computeIfAbsent(playerId, ignored -> new TeleportStats()).increment(cause);
    }

    private void addTeleport(Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                             Teleport teleport) {
        if (ref == null || teleport == null) {
            return;
        }
        if (buffer != null) {
            buffer.addComponent(ref, Teleport.getComponentType(), teleport);
        } else if (store != null) {
            store.addComponent(ref, Teleport.getComponentType(), teleport);
        }
    }

    private long resolveInterpolatedTimeMs(ActiveRun run, Vector3d previousPosition, Vector3d currentPosition,
                                           TransformData target, long previousElapsedMs, double deltaMs) {
        long currentElapsedMs = getRunElapsedMs(run);
        if (run == null || run.waitingForStart || previousPosition == null || currentPosition == null
                || target == null || deltaMs <= 0.0) {
            return currentElapsedMs;
        }
        double t = segmentSphereIntersectionT(previousPosition, currentPosition, target, ParkourConstants.TOUCH_RADIUS);
        if (!Double.isFinite(t)) {
            return currentElapsedMs;
        }
        long interpolated = previousElapsedMs + Math.round(deltaMs * t);
        return Math.max(0L, Math.min(currentElapsedMs, interpolated));
    }

    private static double segmentSphereIntersectionT(Vector3d from, Vector3d to, TransformData target, double radius) {
        if (from == null || to == null || target == null) {
            return Double.NaN;
        }
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double a = dx * dx + dy * dy + dz * dz;
        if (a <= 1e-9) {
            return Double.NaN;
        }
        double fx = from.getX() - target.getX();
        double fy = from.getY() - target.getY();
        double fz = from.getZ() - target.getZ();
        double b = 2.0 * (fx * dx + fy * dy + fz * dz);
        double c = fx * fx + fy * fy + fz * fz - radius * radius;
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0.0) {
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
        return Double.NaN;
    }

    private static Vector3d copyPosition(Vector3d position) {
        if (position == null) {
            return null;
        }
        return new Vector3d(position.getX(), position.getY(), position.getZ());
    }

    private void updatePingStats(ActiveRun run, PlayerRef playerRef, long elapsedMs) {
        if (run == null || playerRef == null || run.waitingForStart) {
            return;
        }
        if (elapsedMs < run.nextPingSampleAtMs) {
            return;
        }
        run.nextPingSampleAtMs = elapsedMs + PING_SAMPLE_INTERVAL_MS;
        PacketHandler handler = playerRef.getPacketHandler();
        if (handler == null) {
            return;
        }
        PacketHandler.PingInfo pingInfo = handler.getPingInfo(PongType.Tick);
        if (pingInfo == null) {
            return;
        }
        HistoricMetric metric = pingInfo.getPingMetricSet();
        if (metric == null) {
            return;
        }
        double avg = metric.getAverage(0);
        double avgMs = convertPingToMs(avg, PacketHandler.PingInfo.TIME_UNIT);
        if (!Double.isFinite(avgMs) || avgMs <= 0.0) {
            return;
        }
        run.pingSampleCount++;
        run.pingSumMs += avgMs;
    }

    private void resetPingStats(ActiveRun run) {
        if (run == null) {
            return;
        }
        run.pingSampleCount = 0L;
        run.pingSumMs = 0.0;
        run.nextPingSampleAtMs = 0L;
    }

    private void sendLagAccuracyMessage(ActiveRun run, Player player) {
        if (player == null || run == null || run.pingSampleCount <= 0L) {
            return;
        }
        long avgPingMs = Math.round(run.pingSumMs / (double) run.pingSampleCount);
        Message message;
        if (avgPingMs < PING_HIGH_THRESHOLD_MS) {
            message = SystemMessageUtils.withParkourPrefix(
                    Message.raw("Connection stable ").color(SystemMessageUtils.SUCCESS),
                    Message.raw("(avg ").color(SystemMessageUtils.SECONDARY),
                    Message.raw(avgPingMs + "ms").color(SystemMessageUtils.PRIMARY_TEXT),
                    Message.raw("). ").color(SystemMessageUtils.SECONDARY),
                    Message.raw("Timer is accurate and fair.").color(SystemMessageUtils.INFO)
            );
        } else {
            message = SystemMessageUtils.withParkourPrefix(
                    Message.raw("High latency ").color(SystemMessageUtils.WARN),
                    Message.raw("(avg ").color(SystemMessageUtils.SECONDARY),
                    Message.raw(avgPingMs + "ms").color(SystemMessageUtils.PRIMARY_TEXT),
                    Message.raw("). ").color(SystemMessageUtils.SECONDARY),
                    Message.raw("Timer may be less accurate due to lag.").color(SystemMessageUtils.WARN)
            );
        }
        player.sendMessage(message);
    }

    private static double convertPingToMs(double value, TimeUnit unit) {
        if (unit == null) {
            return value;
        }
        double unitToMs = unit.toNanos(1L) / 1_000_000.0;
        return value * unitToMs;
    }


    private void advanceRunTime(ActiveRun run, float deltaSeconds) {
        if (run == null) {
            return;
        }
        if (run.waitingForStart) {
            run.elapsedMs = 0L;
            run.elapsedRemainderMs = 0.0;
            run.skipNextTimeIncrement = false;
            return;
        }
        if (run.skipNextTimeIncrement) {
            run.skipNextTimeIncrement = false;
            return;
        }
        if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
            run.elapsedMs = Math.max(0L, System.currentTimeMillis() - run.startTimeMs);
            run.elapsedRemainderMs = 0.0;
            return;
        }
        double deltaMs = Math.max(0.0, deltaSeconds * 1000.0);
        long wholeMs = (long) deltaMs;
        double fractionMs = deltaMs - wholeMs + run.elapsedRemainderMs;
        long carryMs = (long) fractionMs;
        run.elapsedRemainderMs = fractionMs - carryMs;
        run.elapsedMs += wholeMs + carryMs;
    }

    private long getRunElapsedMs(ActiveRun run) {
        if (run == null || run.waitingForStart) {
            return 0L;
        }
        return Math.max(0L, run.elapsedMs);
    }
}
