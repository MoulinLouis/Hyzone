package io.hyvexa.ascend.mine.egg;

import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.mine.achievement.MineAchievement;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.data.CollectedMiner;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MinerRarity;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EggOpenService {

    // Equal weights for testing — tune later
    private static final Map<MinerRarity, Double> RARITY_WEIGHTS;
    static {
        RARITY_WEIGHTS = new EnumMap<>(MinerRarity.class);
        RARITY_WEIGHTS.put(MinerRarity.COMMON, 20.0);
        RARITY_WEIGHTS.put(MinerRarity.UNCOMMON, 20.0);
        RARITY_WEIGHTS.put(MinerRarity.RARE, 20.0);
        RARITY_WEIGHTS.put(MinerRarity.EPIC, 20.0);
        RARITY_WEIGHTS.put(MinerRarity.LEGENDARY, 20.0);
    }

    private final MinePlayerStore store;

    public EggOpenService(MinePlayerStore store) {
        this.store = store;
    }

    /**
     * Opens an egg of the given layer for the player.
     * Returns the new miner, or null if no egg available.
     */
    public CollectedMiner openEgg(UUID playerId, String layerId, MinePlayerProgress progress) {
        if (progress.getEggCount(layerId) <= 0) return null;
        progress.removeEgg(layerId);

        MinerRarity rarity = rollRarity();
        CollectedMiner miner = new CollectedMiner(0, layerId, rarity, 0);
        long dbId = store.insertMiner(playerId, miner);
        miner.setId(dbId);
        progress.addMiner(miner);
        store.markDirty(playerId);

        // Check legendary achievement
        if (rarity == MinerRarity.LEGENDARY) {
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin != null) {
                MineAchievementTracker tracker = plugin.getMineAchievementTracker();
                if (tracker != null) {
                    tracker.checkAchievement(playerId, MineAchievement.FIRST_LEGENDARY);
                }
            }
        }

        return miner;
    }

    private MinerRarity rollRarity() {
        double totalWeight = 0;
        for (double w : RARITY_WEIGHTS.values()) {
            totalWeight += w;
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0;
        for (var entry : RARITY_WEIGHTS.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return entry.getKey();
            }
        }
        return MinerRarity.COMMON;
    }
}
