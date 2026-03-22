package io.hyvexa.ascend.mine.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectedMinerTest {

    @Test
    void productionRateScalesLinearlyWithSpeedLevel() {
        CollectedMiner miner = new CollectedMiner(1L, "deep", MinerRarity.LEGENDARY, 3);

        assertEquals(7.8, miner.getProductionRate(), 1e-9);
        assertEquals(6.6, CollectedMiner.getProductionRate(1), 1e-9);
    }

    @Test
    void speedUpgradeCostGrowsWithLevel() {
        assertEquals(50L, CollectedMiner.getSpeedUpgradeCost(0));
        assertEquals(57L, CollectedMiner.getSpeedUpgradeCost(1));
        assertEquals(66L, CollectedMiner.getSpeedUpgradeCost(2));
    }

    @Test
    void gettersAndSettersExposeMinerState() {
        CollectedMiner miner = new CollectedMiner(5L, "ice", MinerRarity.RARE, 1);

        miner.setId(8L);
        miner.setSpeedLevel(4);

        assertEquals(8L, miner.getId());
        assertEquals("ice", miner.getLayerId());
        assertEquals(MinerRarity.RARE, miner.getRarity());
        assertEquals(4, miner.getSpeedLevel());
    }
}
