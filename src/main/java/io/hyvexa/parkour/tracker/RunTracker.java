package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.MovementStates;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.InventoryUtils;
import io.hyvexa.common.util.PermissionUtils;
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
import java.util.concurrent.atomic.AtomicInteger;

public class RunTracker {

    private static final double TOUCH_RADIUS_SQ = ParkourConstants.TOUCH_RADIUS * ParkourConstants.TOUCH_RADIUS;
    private static final double START_MOVE_THRESHOLD_SQ = 0.0025;

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

    public void setActiveMap(UUID playerId, String mapId) {
        setActiveMap(playerId, mapId, null);
    }

    public void setActiveMap(UUID playerId, String mapId, TransformData start) {
        ActiveRun run = new ActiveRun(mapId, System.currentTimeMillis());
        activeRuns.put(playerId, run);
        idleFalls.remove(playerId);
        armStartOnMovement(run, start);
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

    public Long getElapsedTimeMs(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        if (run == null) {
            return null;
        }
        if (run.waitingForStart) {
            return 0L;
        }
        return Math.max(0L, System.currentTimeMillis() - run.startTimeMs);
    }

    public void checkPlayer(Ref<EntityStore> ref, Store<EntityStore> store) {
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
        ActiveRun run = activeRuns.get(playerRef.getUuid());
        Vector3d position = transform.getPosition();
        if (shouldTeleportFromVoid(position.getY())) {
            if (run == null) {
                teleportToSpawn(ref, store, transform);
                recordTeleport(playerRef.getUuid(), TeleportCause.IDLE_RESPAWN);
                return;
            }
            Map map = mapStore.getMap(run.mapId);
            if (map != null) {
                teleportToRespawn(ref, store, run, map);
                run.fallStartTime = null;
                run.lastY = null;
                recordTeleport(playerRef.getUuid(), TeleportCause.RUN_RESPAWN);
            } else {
                teleportToSpawn(ref, store, transform);
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
                teleportToSpawn(ref, store, transform);
                recordTeleport(playerRef.getUuid(), TeleportCause.IDLE_RESPAWN);
                return;
            }
            Map triggerMap = findStartTriggerMap(position);
            if (triggerMap != null) {
                startRunFromTrigger(ref, store, playerRef, player, triggerMap);
            }
            return;
        }
        Map map = mapStore.getMap(run.mapId);
        if (map == null) {
            return;
        }
        updateStartOnMovement(run, position);
        if (checkLeaveTrigger(ref, store, player, playerRef, position, map)) {
            return;
        }
        checkCheckpoints(run, playerRef, player, position, map);
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
            teleportToRespawn(ref, store, run, map);
            recordTeleport(playerRef.getUuid(), TeleportCause.RUN_RESPAWN);
            return;
        }
        checkFinish(run, playerRef, player, position, map, transform, ref, store);
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
                                     Player player, Map map) {
        if (map.getStart() == null) {
            player.sendMessage(Message.raw("Map '" + map.getId() + "' has no start set."));
            return;
        }
        setActiveMap(playerRef.getUuid(), map.getId(), map.getStart());
        Vector3d position = new Vector3d(map.getStart().getX(), map.getStart().getY(), map.getStart().getZ());
        Vector3f rotation = new Vector3f(map.getStart().getRotX(), map.getStart().getRotY(),
                map.getStart().getRotZ());
        store.addComponent(ref, Teleport.getComponentType(),
                new Teleport(store.getExternalData().getWorld(), position, rotation));
        recordTeleport(playerRef.getUuid(), TeleportCause.START_TRIGGER);
        player.sendMessage(Message.raw("Map loaded."));
        InventoryUtils.giveRunItems(player, map);
    }

    private boolean checkLeaveTrigger(Ref<EntityStore> ref, Store<EntityStore> store, Player player,
                                      PlayerRef playerRef, Vector3d position, Map map) {
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
            store.addComponent(ref, Teleport.getComponentType(),
                    new Teleport(store.getExternalData().getWorld(), targetPosition, targetRotation));
            recordTeleport(playerRef.getUuid(), TeleportCause.LEAVE_TRIGGER);
        }
        clearActiveMap(playerRef.getUuid());
        InventoryUtils.giveMenuItems(player);
        player.sendMessage(Message.raw("Left map."));
        return true;
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

    private void checkCheckpoints(ActiveRun run, PlayerRef playerRef, Player player, Vector3d position, Map map) {
        for (int i = 0; i < map.getCheckpoints().size(); i++) {
            if (run.touchedCheckpoints.contains(i)) {
                continue;
            }
            TransformData checkpoint = map.getCheckpoints().get(i);
            if (checkpoint == null) {
                continue;
            }
            if (distanceSq(position, checkpoint) <= TOUCH_RADIUS_SQ) {
                run.touchedCheckpoints.add(i);
                run.lastCheckpointIndex = i;
                long elapsedMs = run.waitingForStart ? 0L : Math.max(0L, System.currentTimeMillis() - run.startTimeMs);
                run.checkpointTouchTimes.put(i, elapsedMs);
                playCheckpointSound(playerRef);
                player.sendMessage(Message.raw("Checkpoint touched"));
            }
        }
    }

    private void checkFinish(ActiveRun run, PlayerRef playerRef, Player player, Vector3d position, Map map,
                             TransformComponent transform, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (run.finishTouched || map.getFinish() == null) {
            return;
        }
        if (distanceSq(position, map.getFinish()) <= TOUCH_RADIUS_SQ) {
            int checkpointCount = map.getCheckpoints().size();
            if (checkpointCount > 0 && run.touchedCheckpoints.size() < checkpointCount) {
                long now = System.currentTimeMillis();
                if (now - run.lastFinishWarningMs >= 2000L) {
                    run.lastFinishWarningMs = now;
                    player.sendMessage(Message.raw("You did not get all checkpoints."));
                }
                return;
            }
            run.finishTouched = true;
            playFinishSound(playerRef);
            long durationMs = Math.max(0L, System.currentTimeMillis() - run.startTimeMs);
            List<Long> checkpointTimes = new ArrayList<>();
            for (int i = 0; i < checkpointCount; i++) {
                Long time = run.checkpointTouchTimes.get(i);
                checkpointTimes.add(time != null ? time : 0L);
            }
            UUID playerId = playerRef.getUuid();
            String playerName = playerRef.getUsername();
            int oldRank = progressStore.getCompletionRank(playerId, mapStore);
            ProgressStore.ProgressionResult result = progressStore.recordMapCompletion(playerId, playerName,
                    map.getId(), durationMs, map.getFirstCompletionXp(), checkpointTimes);
            int leaderboardPosition = progressStore.getLeaderboardPosition(map.getId(), playerId);
            if (leaderboardPosition <= 0) {
                leaderboardPosition = 1;
            }
            player.sendMessage(Message.raw("Finish line touched"));
            player.sendMessage(Message.raw("Map completed in " + FormatUtils.formatDuration(durationMs) + "."));
            if (result.xpAwarded > 0L) {
                player.sendMessage(Message.raw("You earned " + result.xpAwarded + " XP."));
            }
            int newRank = progressStore.getCompletionRank(playerId, mapStore);
            boolean reachedVexaGod = newRank == ParkourConstants.COMPLETION_RANK_NAMES.length && oldRank < newRank;
            if (newRank > oldRank) {
                HyvexaPlugin plugin = HyvexaPlugin.getInstance();
                if (plugin != null) {
                    plugin.invalidateRankCache(playerId);
                }
                String rankName = progressStore.getRankName(playerId, mapStore);
                player.sendMessage(Message.raw("Rank up! You are now " + rankName + "."));
            }
            if (result.newBest) {
                broadcastCompletion(playerId, playerName, map, durationMs, leaderboardPosition);
                if (reachedVexaGod) {
                    broadcastVexaGod(playerName);
                }
            }
            teleportToSpawn(ref, store, transform);
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
    }

    private void updateStartOnMovement(ActiveRun run, Vector3d position) {
        if (run == null || !run.waitingForStart || run.startPosition == null || position == null) {
            return;
        }
        if (distanceSq(position, run.startPosition) > START_MOVE_THRESHOLD_SQ) {
            run.waitingForStart = false;
            run.startTimeMs = System.currentTimeMillis();
        }
    }

    private void teleportToRespawn(Ref<EntityStore> ref, Store<EntityStore> store, ActiveRun run, Map map) {
        TransformData spawn = null;
        int checkpointIndex = resolveCheckpointIndex(run, map);
        if (checkpointIndex >= 0 && checkpointIndex < map.getCheckpoints().size()) {
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
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(store.getExternalData().getWorld(), position, rotation));
    }

    public boolean teleportToLastCheckpoint(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (playerRef == null) {
            return false;
        }
        ActiveRun run = activeRuns.get(playerRef.getUuid());
        if (run == null) {
            return false;
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
            player.sendMessage(Message.raw("World not available."));
            return false;
        }
        String mapId = getActiveMapId(playerRef.getUuid());
        if (mapId == null) {
            player.sendMessage(Message.raw("No active map to reset."));
            return false;
        }
        Map map = mapStore.getMap(mapId);
        if (map == null || map.getStart() == null) {
            player.sendMessage(Message.raw("Map start not available."));
            return false;
        }
        setActiveMap(playerRef.getUuid(), mapId, map.getStart());
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
        Message rankPart = Message.raw(rank).color(FormatUtils.getRankColor(rank));
        String categoryColor = getCategoryColor(category);
        boolean isWorldRecord = leaderboardPosition == 1;
        Message positionPart = Message.raw("#" + leaderboardPosition).color(isWorldRecord ? "#ffd166" : "#9fb0ba");
        Message wrPart = isWorldRecord
                ? Message.raw(" WR!").color("#ffd166")
                : Message.raw("");
        Message message = Message.join(
                Message.raw("["),
                rankPart,
                Message.raw("] "),
                Message.raw(playerName),
                Message.raw(" finished "),
                Message.raw(mapName),
                Message.raw(" ("),
                Message.raw(category).color(categoryColor),
                Message.raw(") in "),
                Message.raw(FormatUtils.formatDuration(durationMs)),
                Message.raw(" - "),
                positionPart,
                wrPart,
                Message.raw(".")
        );
        Message ggMessage = isWorldRecord
                ? Message.raw("SAY GG IN THE CHAT!!").bold(true)
                : Message.empty();
        for (PlayerRef target : Universe.get().getPlayers()) {
            target.sendMessage(message);
            if (isWorldRecord) {
                target.sendMessage(ggMessage);
            }
        }
    }

    private void broadcastVexaGod(String playerName) {
        Message message = Message.join(
                Message.raw(playerName),
                Message.raw(" is now a "),
                FormatUtils.getRankMessage("VexaGod"),
                Message.raw(" (but for how long?)")
        );
        Message ggMessage = Message.raw("SEND GG IN THE CHAT").bold(true);
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
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, position, rotation));
    }

    private static class ActiveRun {
        private final String mapId;
        private long startTimeMs;
        private boolean waitingForStart;
        private Vector3d startPosition;
        private final Set<Integer> touchedCheckpoints = new HashSet<>();
        private final java.util.Map<Integer, Long> checkpointTouchTimes = new HashMap<>();
        private boolean finishTouched;
        private int lastCheckpointIndex = -1;
        private Long fallStartTime;
        private Double lastY;
        private long lastFinishWarningMs;

        private ActiveRun(String mapId, long startTimeMs) {
            this.mapId = mapId;
            this.startTimeMs = startTimeMs;
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
}
