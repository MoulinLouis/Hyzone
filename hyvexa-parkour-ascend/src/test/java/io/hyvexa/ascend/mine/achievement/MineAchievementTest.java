package io.hyvexa.ascend.mine.achievement;

import io.hyvexa.ascend.mine.achievement.MineAchievement.StatType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineAchievementTest {

    @Test
    void counterBasedAchievementsHavePositiveThresholds() {
        assertEquals(100, MineAchievement.BLOCKS_100.getThreshold());
        assertEquals(1_000, MineAchievement.BLOCKS_1K.getThreshold());
        assertEquals(10_000, MineAchievement.BLOCKS_10K.getThreshold());
        assertEquals(100_000, MineAchievement.BLOCKS_100K.getThreshold());
        assertEquals(1_000, MineAchievement.CRYSTALS_1K.getThreshold());
        assertEquals(10_000, MineAchievement.CRYSTALS_10K.getThreshold());
        assertEquals(100_000, MineAchievement.CRYSTALS_100K.getThreshold());
        assertEquals(1_000_000, MineAchievement.CRYSTALS_1M.getThreshold());
    }

    @Test
    void eventBasedAchievementsHaveNegativeOneThreshold() {
        assertEquals(-1, MineAchievement.FIRST_EGG.getThreshold());
        assertEquals(-1, MineAchievement.FIRST_LEGENDARY.getThreshold());
        assertEquals(-1, MineAchievement.MAX_UPGRADES.getThreshold());
        assertEquals(-1, MineAchievement.EXPLORER.getThreshold());
    }

    @Test
    void counterBasedAchievementsHaveStatTypes() {
        assertEquals(StatType.TOTAL_BLOCKS_MINED, MineAchievement.BLOCKS_100.getStatType());
        assertEquals(StatType.TOTAL_BLOCKS_MINED, MineAchievement.BLOCKS_1K.getStatType());
        assertEquals(StatType.TOTAL_BLOCKS_MINED, MineAchievement.BLOCKS_10K.getStatType());
        assertEquals(StatType.TOTAL_BLOCKS_MINED, MineAchievement.BLOCKS_100K.getStatType());
        assertEquals(StatType.TOTAL_CRYSTALS_EARNED, MineAchievement.CRYSTALS_1K.getStatType());
        assertEquals(StatType.TOTAL_CRYSTALS_EARNED, MineAchievement.CRYSTALS_10K.getStatType());
        assertEquals(StatType.TOTAL_CRYSTALS_EARNED, MineAchievement.CRYSTALS_100K.getStatType());
        assertEquals(StatType.TOTAL_CRYSTALS_EARNED, MineAchievement.CRYSTALS_1M.getStatType());
    }

    @Test
    void eventBasedAchievementsHaveNullStatType() {
        assertNull(MineAchievement.FIRST_EGG.getStatType());
        assertNull(MineAchievement.FIRST_LEGENDARY.getStatType());
        assertNull(MineAchievement.MAX_UPGRADES.getStatType());
        assertNull(MineAchievement.EXPLORER.getStatType());
    }

    @Test
    void fromIdFindsAllAchievements() {
        for (MineAchievement a : MineAchievement.values()) {
            assertEquals(a, MineAchievement.fromId(a.getId()));
        }
    }

    @Test
    void fromIdReturnsNullForUnknownId() {
        assertNull(MineAchievement.fromId("nonexistent"));
        assertNull(MineAchievement.fromId(null));
    }

    @Test
    void allAchievementsHavePositiveCrystalRewards() {
        for (MineAchievement a : MineAchievement.values()) {
            assertTrue(a.getCrystalReward() > 0, a.name() + " should have positive reward");
        }
    }

    @Test
    void thresholdsAreStrictlyIncreasingPerStatType() {
        Map<StatType, List<Long>> grouped = new EnumMap<>(StatType.class);
        for (MineAchievement a : MineAchievement.values()) {
            StatType st = a.getStatType();
            if (st != null) {
                grouped.computeIfAbsent(st, k -> new ArrayList<>()).add(a.getThreshold());
            }
        }
        for (var entry : grouped.entrySet()) {
            List<Long> thresholds = entry.getValue();
            for (int i = 1; i < thresholds.size(); i++) {
                assertTrue(thresholds.get(i) > thresholds.get(i - 1),
                        entry.getKey() + " thresholds should be strictly increasing");
            }
        }
    }

    @Test
    void allAchievementsHaveNonBlankDisplayNameAndDescription() {
        for (MineAchievement a : MineAchievement.values()) {
            assertNotNull(a.getDisplayName());
            assertFalse(a.getDisplayName().isEmpty(), a.name() + " displayName");
            assertNotNull(a.getDescription());
            assertFalse(a.getDescription().isEmpty(), a.name() + " description");
        }
    }
}
