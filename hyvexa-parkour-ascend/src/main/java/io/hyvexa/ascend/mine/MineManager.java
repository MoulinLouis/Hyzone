package io.hyvexa.ascend.mine;

import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MineZone;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    static long encodePosition(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }
}
