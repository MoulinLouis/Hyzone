package io.hyvexa.ascend.mine;

import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.ascend.mine.data.MineZoneLayer;
import io.hyvexa.ascend.mine.util.MinePositionUtils;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.mine.hud.MineHudManager;
import com.hypixel.hytale.math.vector.Vector3d;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public class MineManager {
    private final MineConfigStore configStore;
    private final Function<UUID, PlayerRef> playerRefResolver;

    // zoneId -> set of broken block positions (packed as long: x<<38 | y<<26 | z)
    private final Map<String, Set<Long>> brokenBlocks = new ConcurrentHashMap<>();

    // Cache of resolved block tables per zone to avoid re-resolving on every regen
    private final Map<String, ResolvedZoneCache> resolvedZoneCache = new ConcurrentHashMap<>();

    private volatile long nextRegenTimestamp = 0;
    private volatile boolean regenerating = false;
    private MineHudManager mineHudManager;

    private volatile World mineWorld;

    public MineManager(MineConfigStore configStore, Function<UUID, PlayerRef> playerRefResolver) {
        this.configStore = configStore;
        this.playerRefResolver = playerRefResolver;
    }

    public MineZone findZoneAt(int x, int y, int z) {
        MineZone zone = configStore.getZone();
        if (zone != null && zone.contains(x, y, z)) {
            return zone;
        }
        return null;
    }

    /**
     * Atomically claims a block position as broken. Returns true if this caller is the first
     * to break it (and should receive the reward). Returns false if already broken.
     */
    public boolean tryClaimBlock(String zoneId, int x, int y, int z) {
        return brokenBlocks.computeIfAbsent(zoneId, k -> ConcurrentHashMap.newKeySet())
            .add(MinePositionUtils.packPosition(x, y, z));
    }

    /**
     * Rolls back a claim (e.g. when bag is full after claiming but before breaking).
     */
    public void unclaimBlock(String zoneId, int x, int y, int z) {
        Set<Long> broken = brokenBlocks.get(zoneId);
        if (broken != null) {
            broken.remove(MinePositionUtils.packPosition(x, y, z));
        }
    }

    /**
     * Returns a random non-broken block from the highest available Y layer in the zone.
     * Mines top-down so the Kweebec walks on the surface instead of through blocks.
     * Returns int[3] {x, y, z}, or null if all blocks are broken.
     */
    public int[] pickRandomUnbrokenBlock(MineZone zone) {
        if (zone == null) return null;

        Set<Long> broken = brokenBlocks.get(zone.getId());
        int totalBlocks = zone.getTotalBlocks();
        int brokenCount = broken != null ? broken.size() : 0;

        if (brokenCount >= totalBlocks) return null;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int rangeX = zone.getMaxX() - zone.getMinX() + 1;
        int rangeZ = zone.getMaxZ() - zone.getMinZ() + 1;

        // Scan from top layer down — pick a random unbroken block in the highest layer
        for (int y = zone.getMaxY(); y >= zone.getMinY(); y--) {
            // Quick random attempts on this layer
            for (int attempt = 0; attempt < 10; attempt++) {
                int x = random.nextInt(zone.getMinX(), zone.getMaxX() + 1);
                int z = random.nextInt(zone.getMinZ(), zone.getMaxZ() + 1);
                if (!isBlockBroken(zone.getId(), x, y, z)) {
                    return new int[]{x, y, z};
                }
            }

            // Fallback: linear scan of this layer from random start
            int layerSize = rangeX * rangeZ;
            int startIdx = random.nextInt(layerSize);
            for (int i = 0; i < layerSize; i++) {
                int idx = (startIdx + i) % layerSize;
                int x = zone.getMinX() + (idx / rangeZ);
                int z = zone.getMinZ() + (idx % rangeZ);
                if (!isBlockBroken(zone.getId(), x, y, z)) {
                    return new int[]{x, y, z};
                }
            }
            // This layer fully broken, try next layer down
        }

        return null;
    }

    public boolean isBlockBroken(String zoneId, int x, int y, int z) {
        Set<Long> broken = brokenBlocks.get(zoneId);
        return broken != null && broken.contains(MinePositionUtils.packPosition(x, y, z));
    }

    public boolean isZoneInCooldown(String zoneId) {
        return regenerating;
    }

    public long getRegenRemainingMs() {
        if (nextRegenTimestamp == 0) return 0;
        return Math.max(0, nextRegenTimestamp - System.currentTimeMillis());
    }

    private MineZone findZoneById(String zoneId) {
        MineZone zone = configStore.getZone();
        if (zone != null && zone.getId().equals(zoneId)) {
            return zone;
        }
        return null;
    }

    public MineConfigStore getConfigStore() {
        return configStore;
    }

    public void setWorld(World world) { this.mineWorld = world; }

    public void setMineHudManager(MineHudManager mineHudManager) { this.mineHudManager = mineHudManager; }

    public void initTimer() {
        MineZone zone = configStore.getZone();
        if (zone != null) {
            nextRegenTimestamp = System.currentTimeMillis() + zone.getRegenIntervalSeconds() * 1000L;
        }
    }

    /**
     * Sets a block to air on the world thread using the canonical mineWorld reference.
     * Same code path as zone regeneration (which is known to propagate to clients).
     */
    public void breakBlockVisually(int x, int y, int z) {
        World world = mineWorld;
        if (world == null) return;
        world.execute(() -> {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            var chunk = world.getChunkIfInMemory(chunkIndex);
            if (chunk == null) chunk = world.loadChunkIfInMemory(chunkIndex);
            if (chunk != null) {
                chunk.setBlock(x, y, z, 0);
            }
        });
    }

    public void tick() {
        MineZone zone = configStore.getZone();
        if (zone == null || regenerating || nextRegenTimestamp == 0) return;
        if (System.currentTimeMillis() < nextRegenTimestamp) return;

        World world = mineWorld;
        if (world == null) return;

        regenerating = true;
        world.execute(() -> {
            try {
                teleportPlayersOutOfZone(world, zone);
                generateZone(world, zone);
            } finally {
                nextRegenTimestamp = System.currentTimeMillis() + zone.getRegenIntervalSeconds() * 1000L;
                regenerating = false;
            }
        });
    }

    private void teleportPlayersOutOfZone(World world, MineZone zone) {
        MineHudManager hudMgr = this.mineHudManager;
        if (hudMgr == null) return;

        double safeX = (zone.getMinX() + zone.getMaxX()) / 2.0;
        double safeY = zone.getMaxY() + 2.0;
        double safeZ = (zone.getMinZ() + zone.getMaxZ()) / 2.0;
        Vector3d safePos = new Vector3d(safeX, safeY, safeZ);

        for (UUID playerId : hudMgr.getTrackedPlayerIds()) {
            PlayerRef playerRef = playerRefResolver != null ? playerRefResolver.apply(playerId) : null;
            if (playerRef == null) continue;
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) continue;
            Store<EntityStore> store = ref.getStore();
            if (store == null) continue;

            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null || transform.getPosition() == null) continue;

            int px = (int) Math.floor(transform.getPosition().getX());
            int py = (int) Math.floor(transform.getPosition().getY());
            int pz = (int) Math.floor(transform.getPosition().getZ());
            if (!zone.contains(px, py, pz)) continue;

            store.addComponent(ref, Teleport.getComponentType(),
                new Teleport(world, safePos, transform.getRotation()));
        }
    }

    public void generateAllZones(World world) {
        MineZone zone = configStore.getZone();
        if (zone != null) {
            generateZone(world, zone);
        }
    }

    public boolean generateZone(World world, MineZone zone) {
        // Use cached resolved tables (avoids re-resolving BlockType asset map on every regen)
        ResolvedZoneCache cached = resolvedZoneCache.computeIfAbsent(zone.getId(),
                k -> buildResolvedZoneCache(zone));
        Map<MineZoneLayer, ResolvedZoneTable> layerTables = cached.layerTables();
        ResolvedZoneTable fallbackTable = cached.fallbackTable();

        boolean hasAnyTable = !layerTables.isEmpty()
            || (fallbackTable != null && fallbackTable.blockIds().length > 0);
        if (!hasAnyTable) return false;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        long currentChunkIndex = ChunkUtil.indexChunkFromBlock(zone.getMinX(), zone.getMinZ());
        var currentChunk = world.getChunkIfInMemory(currentChunkIndex);
        if (currentChunk == null) {
            currentChunk = world.loadChunkIfInMemory(currentChunkIndex);
        }
        for (int x = zone.getMinX(); x <= zone.getMaxX(); x++) {
            for (int z = zone.getMinZ(); z <= zone.getMaxZ(); z++) {
                long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
                if (chunkIndex != currentChunkIndex) {
                    currentChunkIndex = chunkIndex;
                    currentChunk = world.getChunkIfInMemory(chunkIndex);
                    if (currentChunk == null) {
                        currentChunk = world.loadChunkIfInMemory(chunkIndex);
                    }
                }
                if (currentChunk == null) {
                    continue;
                }
                for (int y = zone.getMinY(); y <= zone.getMaxY(); y++) {
                    ResolvedZoneTable table = resolveTableForY(y, layerTables, fallbackTable);
                    if (table == null || table.blockIds().length == 0) continue;

                    double roll = random.nextDouble() * table.totalWeight();
                    int selectedBlockId = table.blockIds()[table.blockIds().length - 1];
                    for (int j = 0; j < table.cumulativeWeights().length; j++) {
                        if (roll < table.cumulativeWeights()[j]) {
                            selectedBlockId = table.blockIds()[j];
                            break;
                        }
                    }
                    currentChunk.setBlock(x, y, z, selectedBlockId);
                }
            }
        }

        brokenBlocks.remove(zone.getId());
        return true;
    }

    private ResolvedZoneTable resolveTableForY(int y, Map<MineZoneLayer, ResolvedZoneTable> layerTables,
                                                ResolvedZoneTable fallback) {
        for (var entry : layerTables.entrySet()) {
            if (entry.getKey().containsY(y)) {
                return entry.getValue();
            }
        }
        return fallback;
    }

    private ResolvedZoneTable resolveBlockTable(Map<String, Double> blockTable) {
        Map<Integer, Double> resolvedTable = new LinkedHashMap<>();
        for (var entry : blockTable.entrySet()) {
            int blockId = BlockType.getAssetMap().getIndex(entry.getKey());
            if (blockId >= 0) {
                resolvedTable.put(blockId, entry.getValue());
            }
        }
        if (resolvedTable.isEmpty()) {
            return new ResolvedZoneTable(new int[0], new double[0], 0.0);
        }

        int[] blockIds = new int[resolvedTable.size()];
        double[] cumulativeWeights = new double[resolvedTable.size()];
        int i = 0;
        double totalWeight = 0.0;
        for (var entry : resolvedTable.entrySet()) {
            blockIds[i] = entry.getKey();
            totalWeight += entry.getValue();
            cumulativeWeights[i] = totalWeight;
            i++;
        }
        return new ResolvedZoneTable(blockIds, cumulativeWeights, totalWeight);
    }

    private ResolvedZoneCache buildResolvedZoneCache(MineZone zone) {
        Map<MineZoneLayer, ResolvedZoneTable> layerTables = new LinkedHashMap<>();
        for (MineZoneLayer layer : zone.getLayers()) {
            ResolvedZoneTable resolved = resolveBlockTable(layer.getBlockTable());
            if (resolved != null && resolved.blockIds().length > 0) {
                layerTables.put(layer, resolved);
            }
        }
        ResolvedZoneTable fallbackTable = resolveBlockTable(zone.getBlockTable());
        return new ResolvedZoneCache(layerTables, fallbackTable);
    }

    /**
     * Invalidates the resolved block table cache for a zone (e.g. after admin edits block table).
     */
    public void invalidateZoneCache(String zoneId) {
        resolvedZoneCache.remove(zoneId);
    }

    private record ResolvedZoneTable(int[] blockIds, double[] cumulativeWeights, double totalWeight) {}

    private record ResolvedZoneCache(Map<MineZoneLayer, ResolvedZoneTable> layerTables,
                                     ResolvedZoneTable fallbackTable) {}
}
