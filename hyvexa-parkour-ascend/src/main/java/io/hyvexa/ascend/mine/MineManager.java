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

    public void generateZone(World world, MineZone zone) {
        // Pre-resolve block IDs from names
        Map<Integer, Double> resolvedTable = new LinkedHashMap<>();
        for (var entry : zone.getBlockTable().entrySet()) {
            int blockId = BlockType.getAssetMap().getIndex(entry.getKey());
            if (blockId >= 0) {
                resolvedTable.put(blockId, entry.getValue());
            }
        }
        if (resolvedTable.isEmpty()) return;

        // Build cumulative probability array for weighted random
        int[] blockIds = new int[resolvedTable.size()];
        double[] cumulativeProbs = new double[resolvedTable.size()];
        int i = 0;
        double cumulative = 0;
        for (var entry : resolvedTable.entrySet()) {
            blockIds[i] = entry.getKey();
            cumulative += entry.getValue();
            cumulativeProbs[i] = cumulative;
            i++;
        }

        // Fill all positions in the zone
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int x = zone.getMinX(); x <= zone.getMaxX(); x++) {
            for (int y = zone.getMinY(); y <= zone.getMaxY(); y++) {
                for (int z = zone.getMinZ(); z <= zone.getMaxZ(); z++) {
                    double roll = random.nextDouble() * cumulative;
                    int selectedBlockId = blockIds[blockIds.length - 1]; // fallback
                    for (int j = 0; j < cumulativeProbs.length; j++) {
                        if (roll < cumulativeProbs[j]) {
                            selectedBlockId = blockIds[j];
                            break;
                        }
                    }
                    writeBlock(world, x, y, z, selectedBlockId);
                }
            }
        }

        // Clear broken blocks tracking for this zone
        brokenBlocks.remove(zone.getId());
    }

    private boolean writeBlock(World world, int x, int y, int z, int blockId) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        var chunk = world.getChunkIfInMemory(chunkIndex);
        if (chunk == null) chunk = world.loadChunkIfInMemory(chunkIndex);
        if (chunk == null) return false;
        return chunk.setBlock(x, y, z, blockId);
    }

    private int readBlock(World world, int x, int y, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        var chunk = world.getChunkIfInMemory(chunkIndex);
        if (chunk == null) chunk = world.loadChunkIfInMemory(chunkIndex);
        if (chunk == null) return -1;
        return chunk.getBlock(x, y, z);
    }

    static long encodePosition(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }
}
