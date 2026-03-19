package io.hyvexa.ascend.mine.robot;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.ascend.mine.data.MinerSlot;
import io.hyvexa.common.util.OrphanedEntityCleanup;

import java.nio.file.Path;
import java.util.ArrayList;
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
    private static final long ANIM_REPLAY_MS = 500L;
    private static final long STOPPED_RECHECK_MS = 2000L;

    private final MineConfigStore configStore;
    private final MinePlayerStore playerStore;
    private final MineManager mineManager;
    private final OrphanedEntityCleanup orphanCleanup;

    // ownerId -> mineId -> state
    private final Map<UUID, Map<String, MinerRobotState>> miners = new ConcurrentHashMap<>();
    private final Set<String> startupCleanupWorlds = ConcurrentHashMap.newKeySet();

    private ScheduledFuture<?> tickTask;
    private volatile NPCPlugin npcPlugin;
    private volatile Query<EntityStore> orphanSweepQuery;

    public MineRobotManager(MineConfigStore configStore, MinePlayerStore playerStore, MineManager mineManager) {
        this.configStore = configStore;
        this.playerStore = playerStore;
        this.mineManager = mineManager;
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
        startupCleanupWorlds.clear();
    }

    // ── Player events ──────────────────────────────────────────────────

    public void prepareWorld(World world) {
        if (world == null) return;
        world.execute(() -> cleanupOrphanedMinersOnWorldThread(world));
    }

    public void onPlayerJoin(UUID playerId, World world) {
        if (playerId == null || world == null) return;

        MinePlayerProgress progress = playerStore.getOrCreatePlayer(playerId);
        if (progress == null) return;

        List<Mine> allMines = configStore.listMinesSorted();
        for (Mine mine : allMines) {
            MinePlayerProgress.MinerProgressSnapshot minerProg = progress.getMinerSnapshot(mine.getId());
            if (minerProg.hasMiner()) {
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
        if (getMinerState(ownerId, mineId) != null) return; // already spawned

        MinerSlot slot = configStore.getMinerSlot(mineId);
        if (slot == null || !slot.isConfigured()) {
            LOGGER.atWarning().log("No miner slot configured for mine: " + mineId);
            return;
        }
        if (npcPlugin == null) return;

        MinePlayerProgress progress = playerStore.getOrCreatePlayer(ownerId);
        MinePlayerProgress.MinerProgressSnapshot minerProg = progress != null
                ? progress.getMinerSnapshot(mineId) : null;

        MinerRobotState state = new MinerRobotState(ownerId, mineId);
        if (minerProg != null) {
            state.setSpeedLevel(minerProg.speedLevel());
            state.setStars(minerProg.stars());
        }
        state.setWorldName(world.getName());
        state.setLastBreakTime(System.currentTimeMillis());

        Map<String, MinerRobotState> playerMiners = miners.computeIfAbsent(ownerId,
                k -> new ConcurrentHashMap<>());
        if (playerMiners.putIfAbsent(mineId, state) != null) return;

        world.execute(() -> {
            cleanupOrphanedMinersOnWorldThread(world);
            spawnNpcOnWorldThread(state, world, slot);
        });
    }

    private void spawnNpcOnWorldThread(MinerRobotState state, World world, MinerSlot slot) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store == null) return;

            Vector3d position = new Vector3d(slot.getNpcX(), slot.getNpcY(), slot.getNpcZ());
            Vector3f rotation = new Vector3f(0f, slot.getNpcYaw(), 0f);

            Object result = npcPlugin.spawnNPC(store, "Kweebec_Seedling", "Miner", position, rotation);
            if (result == null) return;
            Ref<EntityStore> entityRef = extractEntityRef(result);
            if (entityRef == null) return;
            state.setEntityRef(entityRef);

            try {
                UUIDComponent uuidComp = store.getComponent(entityRef, UUIDComponent.getComponentType());
                if (uuidComp != null) state.setEntityUuid(uuidComp.getUuid());
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

            try {
                NPCEntity npcEntity = store.getComponent(entityRef, NPCEntity.getComponentType());
                if (npcEntity != null) {
                    Inventory inv = npcEntity.getInventory();
                    if (inv == null) { inv = new Inventory(); npcEntity.setInventory(inv); }
                    inv.getHotbar().setItemStackForSlot((short) 0, new ItemStack("Tool_Pickaxe_Wood"));
                    inv.setActiveHotbarSlot((byte) 0);
                    npcEntity.invalidateEquipmentNetwork();
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to equip miner NPC: " + e.getMessage());
            }

            // Place initial block and start mining animation
            placeInitialBlock(world, slot, state);
            AnimationUtils.playAnimation(entityRef, AnimationSlot.Action, "Pickaxe", "Mine", store);
            state.setAnimating(true);
            state.setLastAnimTime(System.currentTimeMillis());
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

    public void syncPurchasedMiner(UUID ownerId, String mineId, World world) {
        if (getMinerState(ownerId, mineId) == null) {
            spawnMiner(ownerId, mineId, world);
        }
    }

    public void syncMinerSpeed(UUID ownerId, String mineId, int speedLevel) {
        MinerRobotState state = getMinerState(ownerId, mineId);
        if (state == null) {
            return;
        }
        state.setSpeedLevel(speedLevel);
    }

    public void syncMinerEvolution(UUID ownerId, String mineId, int speedLevel, int stars, World world) {
        MinerRobotState state = getMinerState(ownerId, mineId);
        if (state == null) {
            spawnMiner(ownerId, mineId, world);
            return;
        }

        int previousStars = state.getStars();
        state.setSpeedLevel(speedLevel);
        state.setStars(stars);
        if (previousStars == stars || world == null) {
            return;
        }

        // Respawn with new entity type (future: different Kweebec for different star levels)
        UUID entityUuid = state.getEntityUuid();
        despawnNpc(state);
        if (entityUuid != null && state.getEntityUuid() != null) {
            orphanCleanup.addOrphan(entityUuid);
        }

        MinerSlot slot = configStore.getMinerSlot(mineId);
        if (slot == null || !slot.isConfigured()) return;

        state.setWorldName(world.getName());
        state.setLastBreakTime(System.currentTimeMillis());
        world.execute(() -> {
            cleanupOrphanedMinersOnWorldThread(world);
            spawnNpcOnWorldThread(state, world, slot);
        });
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

    private void cleanupOrphanedMinersOnWorldThread(World world) {
        String worldName = world != null ? world.getName() : null;
        if (worldName == null || worldName.isEmpty()) {
            return;
        }
        if (!startupCleanupWorlds.add(worldName)) {
            return;
        }

        boolean success = false;
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store == null) {
                return;
            }

            int removed = removePersistedOrphansByUuid(world, store);
            removed += removeFrozenMinersInsideMineZones(worldName, store);
            if (removed > 0) {
                LOGGER.atInfo().log("Removed " + removed + " orphaned miner NPC(s) from world " + worldName);
            }

            clearStartupCleanupStateIfExhausted();
            success = true;
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed startup miner cleanup for world " + worldName + ": " + e.getMessage());
        } finally {
            if (!success) {
                startupCleanupWorlds.remove(worldName);
            }
        }
    }

    private int removePersistedOrphansByUuid(World world, Store<EntityStore> store) {
        int removed = 0;
        for (UUID orphanUuid : orphanCleanup.getOrphanedUuidsSnapshot()) {
            if (orphanUuid == null || isActiveMinerUuid(orphanUuid)) {
                continue;
            }
            Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(orphanUuid);
            if (removeOrphanedMiner(store, ref, orphanUuid)) {
                removed++;
            }
        }
        return removed;
    }

    private int removeFrozenMinersInsideMineZones(String worldName, Store<EntityStore> store) {
        List<PendingOrphanRemoval> removals = new ArrayList<>();
        store.forEachChunk(getOrphanSweepQuery(), (chunk, buffer) -> {
            for (int entityId = 0; entityId < chunk.size(); entityId++) {
                UUIDComponent uuidComponent = chunk.getComponent(entityId, UUIDComponent.getComponentType());
                TransformComponent transform = chunk.getComponent(entityId, TransformComponent.getComponentType());
                if (uuidComponent == null || transform == null) {
                    continue;
                }

                UUID entityUuid = uuidComponent.getUuid();
                if (entityUuid != null && isActiveMinerUuid(entityUuid)) {
                    continue;
                }

                Vector3d position = transform.getPosition();
                if (!isInsideMineZone(worldName, position)) {
                    continue;
                }

                Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
                if (ref == null || !ref.isValid()) {
                    continue;
                }

                removals.add(new PendingOrphanRemoval(entityUuid, ref));
            }
        });

        int removed = 0;
        for (PendingOrphanRemoval pendingRemoval : removals) {
            if (removeOrphanedMiner(store, pendingRemoval.entityRef(), pendingRemoval.entityUuid())) {
                removed++;
            }
        }
        return removed;
    }

    private boolean removeOrphanedMiner(Store<EntityStore> store, Ref<EntityStore> ref, UUID entityUuid) {
        if (ref == null) {
            return false;
        }
        if (!ref.isValid()) {
            if (entityUuid != null) {
                orphanCleanup.markCleaned(entityUuid);
            }
            return false;
        }
        try {
            store.removeEntity(ref, RemoveReason.REMOVE);
            if (entityUuid != null) {
                orphanCleanup.markCleaned(entityUuid);
            }
            return true;
        } catch (Exception e) {
            String uuidText = entityUuid != null ? entityUuid.toString() : "unknown";
            LOGGER.atWarning().log("Failed to remove orphaned miner NPC " + uuidText + ": " + e.getMessage());
            return false;
        }
    }

    private boolean isInsideMineZone(String worldName, Vector3d position) {
        if (worldName == null || position == null) {
            return false;
        }
        double x = position.getX();
        double y = position.getY();
        double z = position.getZ();

        for (Mine mine : configStore.listMinesSorted()) {
            String mineWorld = mine.getWorld();
            if (mineWorld != null && !mineWorld.isEmpty() && !mineWorld.equals(worldName)) {
                continue;
            }
            for (MineZone zone : mine.getZones()) {
                if (x >= zone.getMinX() && x < zone.getMaxX() + 1.0
                        && z >= zone.getMinZ() && z < zone.getMaxZ() + 1.0
                        && y >= zone.getMinY() - 0.5 && y <= zone.getMaxY() + 1.5) {
                    return true;
                }
            }
        }
        return false;
    }

    private Query<EntityStore> getOrphanSweepQuery() {
        Query<EntityStore> current = orphanSweepQuery;
        if (current != null) {
            return current;
        }
        var frozenType = Frozen.getComponentType();
        var invulnerableType = Invulnerable.getComponentType();
        var uuidType = UUIDComponent.getComponentType();
        var transformType = TransformComponent.getComponentType();
        if (frozenType == null || invulnerableType == null || uuidType == null || transformType == null) {
            return Query.any();
        }
        current = Archetype.of(frozenType, invulnerableType, uuidType, transformType);
        orphanSweepQuery = current;
        return current;
    }

    private void clearStartupCleanupStateIfExhausted() {
        Set<String> configuredMineWorlds = new HashSet<>();
        for (Mine mine : configStore.listMinesSorted()) {
            String mineWorld = mine.getWorld();
            if (mineWorld != null && !mineWorld.isEmpty()) {
                configuredMineWorlds.add(mineWorld);
            }
        }
        if (configuredMineWorlds.isEmpty() || startupCleanupWorlds.containsAll(configuredMineWorlds)) {
            orphanCleanup.clearAll();
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
        Ref<EntityStore> ref = state.getEntityRef();
        if (ref == null || !ref.isValid()) return;

        String mineId = state.getMineId();
        MinerSlot slot = configStore.getMinerSlot(mineId);
        if (slot == null) return;

        // --- Full bag: pause mining, stop animation, recheck periodically ---
        MinePlayerProgress progress = playerStore.getPlayer(state.getOwnerId());
        if (progress == null || progress.isInventoryFull()) {
            if (!state.isStopped()) {
                state.setStopped(true);
                if (state.isAnimating()) {
                    stopMineAnimation(state);
                    state.setAnimating(false);
                }
            }
            if (now - state.getLastBreakTime() >= STOPPED_RECHECK_MS) {
                state.setLastBreakTime(now);
            }
            return;
        }

        // --- Resume from stopped state ---
        if (state.isStopped()) {
            state.setStopped(false);
            state.setAnimating(true);
            state.setLastAnimTime(now);
            replayMineAnimation(state);
            state.setLastBreakTime(now);
            return;
        }

        // --- Replay mine animation to keep it looping ---
        if (state.isAnimating() && now - state.getLastAnimTime() >= ANIM_REPLAY_MS) {
            replayMineAnimation(state);
            state.setLastAnimTime(now);
        }

        // --- Check if it's time to break the block ---
        long intervalMs = (long) (slot.getIntervalSeconds() * 1000);
        if (now - state.getLastBreakTime() < intervalMs) return;
        state.setLastBreakTime(now);

        // Loot = the block currently displayed
        String lootType = state.getCurrentBlockType();
        if (lootType != null) {
            progress.addToInventory(lootType, 1);
            playerStore.markDirty(state.getOwnerId());
        }

        // Track blocks mined for achievements
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            MineAchievementTracker tracker = plugin.getMineAchievementTracker();
            if (tracker != null) {
                tracker.incrementBlocksMined(state.getOwnerId(), 1);
            }
        }

        // Break current block and place a NEW random block
        Mine mine = configStore.getMine(mineId);
        if (mine == null || mine.getZones().isEmpty()) return;
        MineZone zone = mine.getZones().get(0);

        Map<String, Double> blockTable = zone.getBlockTableForY(slot.getBlockY());
        String nextBlockType = pickRandomBlock(blockTable);
        state.setCurrentBlockType(nextBlockType);

        String worldName = state.getWorldName();
        World world = Universe.get().getWorld(worldName);
        if (world != null) {
            breakAndReplaceBlock(world, slot, nextBlockType, state.getOwnerId(), mineId);
        }
    }

    // ── Block placement helpers ────────────────────────────────────────

    private void placeInitialBlock(World world, MinerSlot slot, MinerRobotState state) {
        Mine mine = configStore.getMine(slot.getMineId());
        if (mine == null || mine.getZones().isEmpty()) return;
        MineZone zone = mine.getZones().get(0);
        Map<String, Double> blockTable = zone.getBlockTableForY(slot.getBlockY());
        String blockType = pickRandomBlock(blockTable);
        if (blockType == null) return;

        state.setCurrentBlockType(blockType);

        int blockId = BlockType.getAssetMap().getIndex(blockType);
        if (blockId < 0) return;

        int bx = slot.getBlockX(), by = slot.getBlockY(), bz = slot.getBlockZ();
        // Already on world thread (called from spawnNpcOnWorldThread)
        long ci = ChunkUtil.indexChunkFromBlock(bx, bz);
        var chunk = world.getChunkIfInMemory(ci);
        if (chunk != null) chunk.setBlock(bx, by, bz, blockId);
    }

    private void breakAndReplaceBlock(World world, MinerSlot slot, String nextBlockType,
                                       UUID ownerId, String mineId) {
        int bx = slot.getBlockX(), by = slot.getBlockY(), bz = slot.getBlockZ();
        int newBlockId = nextBlockType != null ? BlockType.getAssetMap().getIndex(nextBlockType) : -1;

        world.execute(() -> {
            long ci = ChunkUtil.indexChunkFromBlock(bx, bz);
            var chunk = world.getChunkIfInMemory(ci);
            if (chunk == null) return;

            // Break (set to air)
            chunk.setBlock(bx, by, bz, 0);

            // Schedule block regeneration after a short delay
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                if (getMinerState(ownerId, mineId) == null) return;
                world.execute(() -> {
                    if (newBlockId >= 0) {
                        var c = world.getChunkIfInMemory(ci);
                        if (c != null) c.setBlock(bx, by, bz, newBlockId);
                    }
                });
            }, 300, TimeUnit.MILLISECONDS);
        });
    }

    // ── Animation helpers ──────────────────────────────────────────────

    private void replayMineAnimation(MinerRobotState state) {
        Ref<EntityStore> ref = state.getEntityRef();
        if (ref == null || !ref.isValid()) return;
        String worldName = state.getWorldName();
        World world = Universe.get().getWorld(worldName);
        if (world == null) return;
        world.execute(() -> {
            if (!ref.isValid()) return;
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store != null) {
                AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Pickaxe", "Mine", store);
            }
        });
    }

    private void stopMineAnimation(MinerRobotState state) {
        Ref<EntityStore> ref = state.getEntityRef();
        if (ref == null || !ref.isValid()) return;
        String worldName = state.getWorldName();
        World world = Universe.get().getWorld(worldName);
        if (world == null) return;
        world.execute(() -> {
            if (!ref.isValid()) return;
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store != null) {
                AnimationUtils.stopAnimation(ref, AnimationSlot.Action, store);
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────

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

    public boolean isActiveMinerUuid(UUID entityUuid) {
        if (entityUuid == null) return false;
        if (orphanCleanup.isPendingRemoval(entityUuid)) return true;
        for (Map<String, MinerRobotState> playerMiners : miners.values()) {
            for (MinerRobotState state : playerMiners.values()) {
                if (entityUuid.equals(state.getEntityUuid())) return true;
            }
        }
        return false;
    }

    private record PendingOrphanRemoval(UUID entityUuid, Ref<EntityStore> entityRef) {}
}
