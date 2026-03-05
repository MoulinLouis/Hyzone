package io.hyvexa.ascend.mine;

import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MineZone;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MineManager {
    private final MineConfigStore configStore;

    // zoneId -> set of broken block positions (encoded as long: x<<40 | y<<20 | z)
    private final Map<String, Set<Long>> brokenBlocks = new ConcurrentHashMap<>();

    // zoneId -> timestamp when cooldown started (0 = not in cooldown)
    private final Map<String, Long> zoneCooldownStart = new ConcurrentHashMap<>();
    private final Map<String, ResolvedZoneTable> resolvedZoneTables = new ConcurrentHashMap<>();

    private volatile World mineWorld;

    public MineManager(MineConfigStore configStore) {
        this.configStore = configStore;
    }

    public MineZone findZoneAt(int x, int y, int z) {
        for (Mine mine : configStore.listMinesSorted()) {
            for (MineZone zone : mine.getZones()) {
                if (zone.contains(x, y, z)) {
                    return zone;
                }
            }
        }
        return null;
    }

    public void markBlockBroken(String zoneId, int x, int y, int z) {
        brokenBlocks.computeIfAbsent(zoneId, k -> ConcurrentHashMap.newKeySet())
            .add(encodePosition(x, y, z));
    }

    public boolean isBlockBroken(String zoneId, int x, int y, int z) {
        Set<Long> broken = brokenBlocks.get(zoneId);
        return broken != null && broken.contains(encodePosition(x, y, z));
    }

    public double getBrokenRatio(String zoneId) {
        MineZone zone = findZoneById(zoneId);
        if (zone == null) return 0;
        Set<Long> broken = brokenBlocks.get(zoneId);
        if (broken == null) return 0;
        return (double) broken.size() / zone.getTotalBlocks();
    }

    public boolean isZoneInCooldown(String zoneId) {
        Long start = zoneCooldownStart.get(zoneId);
        return start != null && start > 0;
    }

    public long getZoneCooldownRemainingMs(String zoneId) {
        Long start = zoneCooldownStart.get(zoneId);
        if (start == null || start == 0) return 0;
        MineZone zone = findZoneById(zoneId);
        if (zone == null) return 0;
        long elapsed = System.currentTimeMillis() - start;
        long cooldownMs = zone.getRegenCooldownSeconds() * 1000L;
        return Math.max(0, cooldownMs - elapsed);
    }

    private MineZone findZoneById(String zoneId) {
        for (Mine mine : configStore.listMinesSorted()) {
            for (MineZone zone : mine.getZones()) {
                if (zone.getId().equals(zoneId)) {
                    return zone;
                }
            }
        }
        return null;
    }

    public MineConfigStore getConfigStore() {
        return configStore;
    }

    public void setWorld(World world) { this.mineWorld = world; }

    public void tick() {
        long now = System.currentTimeMillis();

        for (Mine mine : configStore.listMinesSorted()) {
            for (MineZone zone : mine.getZones()) {
                String zoneId = zone.getId();

                // Check if zone is in cooldown
                Long cooldownStart = zoneCooldownStart.get(zoneId);
                if (cooldownStart != null && cooldownStart > 0) {
                    // Check if cooldown is over
                    long cooldownMs = zone.getRegenCooldownSeconds() * 1000L;
                    if (now - cooldownStart >= cooldownMs) {
                        // Regenerate on world thread
                        World world = mineWorld;
                        if (world != null) {
                            world.execute(() -> {
                                if (generateZone(world, zone)) {
                                    zoneCooldownStart.remove(zoneId, cooldownStart);
                                }
                            });
                        }
                    }
                    continue; // skip threshold check while in cooldown
                }

                // Check if threshold is reached
                double ratio = getBrokenRatio(zoneId);
                if (ratio >= zone.getRegenThreshold()) {
                    // Start cooldown
                    zoneCooldownStart.put(zoneId, now);
                }
            }
        }
    }

    public void generateAllZones(World world) {
        for (Mine mine : configStore.listMinesSorted()) {
            for (MineZone zone : mine.getZones()) {
                generateZone(world, zone);
            }
        }
    }

    public boolean generateZone(World world, MineZone zone) {
        ResolvedZoneTable resolvedTable = resolvedZoneTables.computeIfAbsent(zone.getId(), ignored -> resolveZoneTable(zone));
        if (resolvedTable == null || resolvedTable.blockIds.length == 0) return false;

        // Fill all positions in the zone
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
                    double roll = random.nextDouble() * resolvedTable.totalWeight;
                    int selectedBlockId = resolvedTable.blockIds[resolvedTable.blockIds.length - 1];
                    for (int j = 0; j < resolvedTable.cumulativeWeights.length; j++) {
                        if (roll < resolvedTable.cumulativeWeights[j]) {
                            selectedBlockId = resolvedTable.blockIds[j];
                            break;
                        }
                    }
                    currentChunk.setBlock(x, y, z, selectedBlockId);
                }
            }
        }

        // Clear broken blocks tracking for this zone
        brokenBlocks.remove(zone.getId());
        return true;
    }

    private ResolvedZoneTable resolveZoneTable(MineZone zone) {
        Map<Integer, Double> resolvedTable = new LinkedHashMap<>();
        for (var entry : zone.getBlockTable().entrySet()) {
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

    static long encodePosition(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }

    private static final class ResolvedZoneTable {
        final int[] blockIds;
        final double[] cumulativeWeights;
        final double totalWeight;

        private ResolvedZoneTable(int[] blockIds, double[] cumulativeWeights, double totalWeight) {
            this.blockIds = blockIds;
            this.cumulativeWeights = cumulativeWeights;
            this.totalWeight = totalWeight;
        }
    }
}
