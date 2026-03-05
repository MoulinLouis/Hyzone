package io.hyvexa.ascend.mine.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.util.OrphanedEntityCleanup;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class MineRobotManager {

    /*
     * Threading model:
     * - miners: ConcurrentHashMap (thread-safe by construction).
     * - orphanCleanup: owns orphan UUID + pending-removal concurrency state.
     * - npcPlugin: volatile, set once in start(), thereafter read-only.
     * - tickTask: only accessed in start()/stop() (single-threaded lifecycle).
     *
     * Entity operations (spawn/despawn) must run on the World thread via world.execute().
     */

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String MINER_UUIDS_FILE = "miner_uuids.txt";
    private static final long TICK_INTERVAL_MS = 50L;

    private final MineConfigStore configStore;
    private final MinePlayerStore playerStore;
    private final OrphanedEntityCleanup orphanCleanup;

    // ownerId -> mineId -> state
    private final Map<UUID, Map<String, MinerRobotState>> miners = new ConcurrentHashMap<>();

    private ScheduledFuture<?> tickTask;
    private volatile NPCPlugin npcPlugin;

    public MineRobotManager(MineConfigStore configStore, MinePlayerStore playerStore) {
        this.configStore = configStore;
        this.playerStore = playerStore;
        this.orphanCleanup = new OrphanedEntityCleanup(LOGGER,
                Path.of("mods", "Parkour", MINER_UUIDS_FILE));
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    public void start() {
        orphanCleanup.loadOrphanedUuids();

        try {
            npcPlugin = NPCPlugin.get();
        } catch (Exception e) {
            LOGGER.atWarning().log("NPCPlugin not available, miners will be invisible: " + e.getMessage());
            npcPlugin = null;
        }

        tickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                this::tick,
                TICK_INTERVAL_MS,
                TICK_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }

        orphanCleanup.saveUuidsForCleanup(getActiveEntityUuids());
        despawnAll();
        miners.clear();
    }

    // ── Player events ──────────────────────────────────────────────────

    public void onPlayerJoin(UUID playerId, World world) {
        if (playerId == null || world == null) return;

        MinePlayerProgress progress = playerStore.getOrCreatePlayer(playerId);
        if (progress == null) return;

        List<Mine> allMines = configStore.listMinesSorted();
        for (Mine mine : allMines) {
            MinePlayerProgress.MinerProgress minerProg = progress.getMinerState(mine.getId());
            if (minerProg.isHasMiner()) {
                spawnMiner(playerId, mine.getId(), world);
            }
        }
    }

    public void onPlayerLeave(UUID playerId) {
        if (playerId == null) return;

        Map<String, MinerRobotState> playerMiners = miners.remove(playerId);
        if (playerMiners == null) return;

        for (MinerRobotState state : playerMiners.values()) {
            despawnNpc(state);
        }
    }

    // ── Spawn / Despawn ────────────────────────────────────────────────

    public void spawnMiner(UUID ownerId, String mineId, World world) {
        if (ownerId == null || mineId == null || world == null) return;

        Mine mine = configStore.getMine(mineId);
        if (mine == null) return;

        List<MineZone> zones = mine.getZones();
        if (zones.isEmpty()) return;

        MineZone zone = zones.get(0);
        double cx = (zone.getMinX() + zone.getMaxX()) / 2.0;
        double cy = zone.getMinY();
        double cz = (zone.getMinZ() + zone.getMaxZ()) / 2.0;

        // Load speed/star levels from player progress
        MinePlayerProgress progress = playerStore.getOrCreatePlayer(ownerId);
        MinePlayerProgress.MinerProgress minerProg = progress != null
                ? progress.getMinerState(mineId)
                : null;

        MinerRobotState state = new MinerRobotState(ownerId, mineId);
        if (minerProg != null) {
            state.setSpeedLevel(minerProg.getSpeedLevel());
            state.setStars(minerProg.getStars());
        }
        state.setLastProductionTick(System.currentTimeMillis());

        Map<String, MinerRobotState> playerMiners = miners.computeIfAbsent(ownerId,
                k -> new ConcurrentHashMap<>());
        if (playerMiners.putIfAbsent(mineId, state) != null) {
            return; // Already spawned
        }

        if (npcPlugin == null) return;

        String entityType = getMinerEntityType(state.getStars());
        world.execute(() -> spawnNpcOnWorldThread(state, entityType, world, cx, cy, cz));
    }

    private void spawnNpcOnWorldThread(MinerRobotState state, String entityType,
                                       World world, double x, double y, double z) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store == null) return;

            Vector3d position = new Vector3d(x, y, z);
            Vector3f rotation = new Vector3f(0f, 0f, 0f);

            Object result = npcPlugin.spawnNPC(store, entityType, "Miner", position, rotation);
            if (result == null) return;

            Ref<EntityStore> entityRef = extractEntityRef(result);
            if (entityRef == null) return;

            state.setEntityRef(entityRef);

            try {
                UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
                if (uuidComponent != null) {
                    UUID entityUuid = uuidComponent.getUuid();
                    state.setEntityUuid(entityUuid);
                    orphanCleanup.addOrphan(entityUuid);
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to get miner NPC UUID: " + e.getMessage());
            }

            try {
                store.addComponent(entityRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to make miner NPC invulnerable: " + e.getMessage());
            }

            try {
                store.addComponent(entityRef, Frozen.getComponentType(), Frozen.get());
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to freeze miner NPC: " + e.getMessage());
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to spawn miner NPC: " + e.getMessage());
        }
    }

    public void despawnMiner(UUID ownerId, String mineId) {
        if (ownerId == null || mineId == null) return;

        Map<String, MinerRobotState> playerMiners = miners.get(ownerId);
        if (playerMiners == null) return;

        MinerRobotState state = playerMiners.remove(mineId);
        if (state == null) return;

        if (playerMiners.isEmpty()) {
            miners.remove(ownerId);
        }

        UUID entityUuid = state.getEntityUuid();
        despawnNpc(state);

        // If despawn didn't clear UUID, entity may still exist (e.g., unloaded chunk)
        if (entityUuid != null && state.getEntityUuid() != null) {
            orphanCleanup.addOrphan(entityUuid);
        }
    }

    private void despawnNpc(MinerRobotState state) {
        if (state == null) return;

        Ref<EntityStore> entityRef = state.getEntityRef();
        if (entityRef == null || !entityRef.isValid()) {
            state.setEntityRef(null);
            return;
        }

        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store != null) {
                World world = store.getExternalData().getWorld();
                if (world != null) {
                    world.execute(() -> despawnNpcOnWorldThread(state, entityRef));
                    return;
                }
                // Fallback: try direct removal
                store.removeEntity(entityRef, RemoveReason.REMOVE);
                clearEntityState(state);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to despawn miner NPC: " + e.getMessage());
        }
    }

    private void despawnNpcOnWorldThread(MinerRobotState state, Ref<EntityStore> entityRef) {
        boolean success = false;
        try {
            if (entityRef != null && entityRef.isValid()) {
                Store<EntityStore> store = entityRef.getStore();
                if (store != null) {
                    store.removeEntity(entityRef, RemoveReason.REMOVE);
                    success = true;
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to despawn miner NPC on world thread: " + e.getMessage());
        }
        state.setEntityRef(null);
        if (success) {
            UUID uuid = state.getEntityUuid();
            state.setEntityUuid(null);
            if (uuid != null) {
                orphanCleanup.markCleaned(uuid);
            }
        }
    }

    private void despawnAll() {
        for (Map<String, MinerRobotState> playerMiners : miners.values()) {
            for (MinerRobotState state : playerMiners.values()) {
                despawnNpc(state);
            }
        }
    }

    private void clearEntityState(MinerRobotState state) {
        UUID uuid = state.getEntityUuid();
        state.setEntityRef(null);
        state.setEntityUuid(null);
        if (uuid != null) {
            orphanCleanup.markCleaned(uuid);
        }
    }

    // ── Tick / Production ──────────────────────────────────────────────

    private void tick() {
        try {
            long now = System.currentTimeMillis();

            for (Map<String, MinerRobotState> playerMiners : miners.values()) {
                for (MinerRobotState state : playerMiners.values()) {
                    tickMiner(state, now);
                }
            }

            orphanCleanup.processPendingRemovals();
        } catch (Exception e) {
            LOGGER.atWarning().log("Miner tick error: " + e.getMessage());
        }
    }

    private void tickMiner(MinerRobotState state, long now) {
        long interval = state.getProductionIntervalMs();
        if (now - state.getLastProductionTick() < interval) return;

        state.setLastProductionTick(now);

        Mine mine = configStore.getMine(state.getMineId());
        if (mine == null) return;

        List<MineZone> zones = mine.getZones();
        if (zones.isEmpty()) return;

        Map<String, Double> blockTable = zones.get(0).getBlockTable();
        if (blockTable.isEmpty()) return;

        String blockType = pickRandomBlock(blockTable);
        if (blockType == null) return;

        MinePlayerProgress progress = playerStore.getPlayer(state.getOwnerId());
        if (progress == null) return;

        if (progress.isAutoSellEnabled()) {
            BigNumber price = configStore.getBlockPrice(state.getMineId(), blockType);
            progress.addCrystals(price.toLong());
        } else {
            boolean added = progress.addToInventory(blockType, 1);
            if (!added) return; // Bag full, skip
        }

        playerStore.markDirty(state.getOwnerId());
    }

    // ── Helpers ────────────────────────────────────────────────────────

    static String getMinerEntityType(int stars) {
        return switch (stars) {
            case 0 -> "Kweebec_Seedling";
            case 1 -> "Kweebec_Sapling";
            case 2 -> "Kweebec_Sproutling";
            case 3 -> "Kweebec_Sapling_Pink";
            case 4 -> "Kweebec_Razorleaf";
            default -> "Kweebec_Rootling";
        };
    }

    static String pickRandomBlock(Map<String, Double> blockTable) {
        double totalWeight = 0.0;
        for (double w : blockTable.values()) {
            totalWeight += w;
        }
        if (totalWeight <= 0.0) return null;

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0;
        for (Map.Entry<String, Double> entry : blockTable.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return entry.getKey();
            }
        }
        // Fallback (shouldn't happen due to floating-point, but safe)
        return blockTable.keySet().iterator().next();
    }

    @SuppressWarnings("unchecked")
    private Ref<EntityStore> extractEntityRef(Object pairResult) {
        if (pairResult == null) return null;
        try {
            for (String methodName : List.of("getFirst", "getLeft", "getKey", "first", "left")) {
                try {
                    java.lang.reflect.Method method = pairResult.getClass().getMethod(methodName);
                    Object value = method.invoke(pairResult);
                    if (value instanceof Ref<?> ref) {
                        return (Ref<EntityStore>) ref;
                    }
                } catch (NoSuchMethodException ignored) {
                    // Try next method
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to extract entity ref from miner NPC result: " + e.getMessage());
        }
        return null;
    }

    // ── Accessors ──────────────────────────────────────────────────────

    public MinerRobotState getMinerState(UUID ownerId, String mineId) {
        Map<String, MinerRobotState> playerMiners = miners.get(ownerId);
        if (playerMiners == null) return null;
        return playerMiners.get(mineId);
    }

    public Set<UUID> getActiveEntityUuids() {
        Set<UUID> uuids = new HashSet<>();
        for (Map<String, MinerRobotState> playerMiners : miners.values()) {
            for (MinerRobotState state : playerMiners.values()) {
                UUID uuid = state.getEntityUuid();
                if (uuid != null) {
                    uuids.add(uuid);
                }
            }
        }
        return uuids;
    }
}
