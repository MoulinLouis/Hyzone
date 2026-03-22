package io.hyvexa.ascend.mine.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineUpgradeTypeTest {

    @Test
    void getCostMatchesConfiguredCurveAtRepresentativeLevels() {
        for (MineUpgradeType type : MineUpgradeType.values()) {
            int midLevel = Math.max(1, type.getMaxLevel() / 2);
            int lateLevel = Math.max(0, type.getMaxLevel() - 1);

            assertEquals(expectedCost(type, 0), type.getCost(0), type + " level 0 cost");
            assertEquals(expectedCost(type, midLevel), type.getCost(midLevel), type + " mid cost");
            assertEquals(expectedCost(type, lateLevel), type.getCost(lateLevel), type + " late cost");
        }
    }

    @Test
    void getEffectMatchesEachUpgradeFormula() {
        assertEquals(50.0, MineUpgradeType.BAG_CAPACITY.getEffect(0), 1e-9);
        assertEquals(110.0, MineUpgradeType.BAG_CAPACITY.getEffect(6), 1e-9);

        assertEquals(5.0, MineUpgradeType.MOMENTUM.getEffect(0), 1e-9);
        assertEquals(14.0, MineUpgradeType.MOMENTUM.getEffect(3), 1e-9);

        assertEquals(0.0, MineUpgradeType.FORTUNE.getEffect(0), 1e-9);
        assertEquals(18.0, MineUpgradeType.FORTUNE.getEffect(9), 1e-9);

        assertEquals(0.0, MineUpgradeType.JACKHAMMER.getEffect(0), 1e-9);
        assertEquals(7.0, MineUpgradeType.JACKHAMMER.getEffect(7), 1e-9);

        assertEquals(1.0, MineUpgradeType.STOMP.getEffect(0), 1e-9);
        assertEquals(3.0, MineUpgradeType.STOMP.getEffect(10), 1e-9);

        assertEquals(1.0, MineUpgradeType.BLAST.getEffect(4), 1e-9);
        assertEquals(4.0, MineUpgradeType.BLAST.getEffect(15), 1e-9);

        assertEquals(0.0, MineUpgradeType.HASTE.getEffect(0), 1e-9);
        assertEquals(60.0, MineUpgradeType.HASTE.getEffect(12), 1e-9);

        assertEquals(1000.0, MineUpgradeType.CONVEYOR_CAPACITY.getEffect(0), 1e-9);
        assertEquals(2000.0, MineUpgradeType.CONVEYOR_CAPACITY.getEffect(5), 1e-9);

        assertEquals(0.0, MineUpgradeType.CASHBACK.getEffect(0), 1e-9);
        assertEquals(6.0, MineUpgradeType.CASHBACK.getEffect(12), 1e-9);
    }

    @Test
    void getChanceInterpolatesForAoeUpgrades() {
        assertEquals(0.0, MineUpgradeType.JACKHAMMER.getChance(0), 1e-9);
        assertEquals(1.0 / 300.0, MineUpgradeType.JACKHAMMER.getChance(1), 1e-9);
        assertEquals((1.0 + 4.0 * (24.0 / 9.0)) / 300.0, MineUpgradeType.JACKHAMMER.getChance(5), 1e-9);
        assertEquals(0.25 / 3.0, MineUpgradeType.JACKHAMMER.getChance(10), 1e-9);
        assertEquals(0.25 / 3.0, MineUpgradeType.JACKHAMMER.getChance(99), 1e-9);

        assertEquals(0.0, MineUpgradeType.STOMP.getChance(0), 1e-9);
        assertEquals((1.0 + 6.0 * (24.0 / 14.0)) / 300.0, MineUpgradeType.STOMP.getChance(7), 1e-9);
        assertEquals(0.25 / 3.0, MineUpgradeType.STOMP.getChance(15), 1e-9);

        assertEquals(0.0, MineUpgradeType.BLAST.getChance(-3), 1e-9);
        assertEquals((1.0 + 9.0 * (24.0 / 14.0)) / 300.0, MineUpgradeType.BLAST.getChance(10), 1e-9);
        assertEquals(0.25 / 3.0, MineUpgradeType.BLAST.getChance(15), 1e-9);
    }

    @Test
    void getChanceReturnsMinusOneForNonAoeUpgrades() {
        assertEquals(-1.0, MineUpgradeType.BAG_CAPACITY.getChance(5), 1e-9);
        assertEquals(-1.0, MineUpgradeType.MOMENTUM.getChance(5), 1e-9);
        assertEquals(-1.0, MineUpgradeType.FORTUNE.getChance(5), 1e-9);
        assertEquals(-1.0, MineUpgradeType.HASTE.getChance(5), 1e-9);
        assertEquals(-1.0, MineUpgradeType.CONVEYOR_CAPACITY.getChance(5), 1e-9);
        assertEquals(-1.0, MineUpgradeType.CASHBACK.getChance(5), 1e-9);
    }

    private long expectedCost(MineUpgradeType type, int level) {
        return switch (type) {
            case BAG_CAPACITY -> Math.round(25 * Math.pow(1.2, level));
            case MOMENTUM -> Math.round(50 * Math.pow(1.22, level));
            case FORTUNE -> Math.round(60 * Math.pow(1.22, level));
            case JACKHAMMER -> Math.round(150 * Math.pow(1.28, level));
            case STOMP -> Math.round(200 * Math.pow(1.30, level));
            case BLAST -> Math.round(250 * Math.pow(1.30, level));
            case HASTE -> Math.round(40 * Math.pow(1.20, level));
            case CONVEYOR_CAPACITY -> Math.round(30 * Math.pow(1.18, level));
            case CASHBACK -> Math.round(80 * Math.pow(1.22, level));
        };
    }
}
