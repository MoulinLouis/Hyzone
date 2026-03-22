package io.hyvexa.ascend.mine.data;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinePlayerProgressTest {

    @Test
    void addToInventoryUpToRespectsBagCapacityAndFullBoundary() {
        MinePlayerProgress progress = newProgress();

        assertTrue(progress.addToInventory("stone", 40));
        assertEquals(10, progress.addToInventoryUpTo("dirt", 20));
        assertFalse(progress.addToInventory("gold", 1));
        assertTrue(progress.isInventoryFull());
        assertEquals(50, progress.getInventoryTotal());
        assertEquals(Map.of("stone", 40, "dirt", 10), progress.getInventory());
    }

    @Test
    void addToInventoryRejectsInvalidValues() {
        MinePlayerProgress progress = newProgress();

        assertEquals(0, progress.addToInventoryUpTo(null, 10));
        assertEquals(0, progress.addToInventoryUpTo("stone", 0));
        assertEquals(0, progress.addToInventoryUpTo("stone", -4));
        assertTrue(progress.canAddToInventory(-5));
        assertEquals(0, progress.getInventoryTotal());
    }

    @Test
    void sellBlockUsesConfiguredPriceAndClearsOnlyThatBlock() {
        MinePlayerProgress progress = newProgress();
        progress.loadInventoryItem("stone", 5);
        progress.loadInventoryItem("dirt", 2);

        long earned = progress.sellBlock("stone", Map.of("stone", 4L));

        assertEquals(20L, earned);
        assertEquals(20.0, progress.getCrystals(), 1e-9);
        assertEquals(Map.of("dirt", 2), progress.getInventory());
        assertEquals(2, progress.getInventoryTotal());
    }

    @Test
    void sellAllSumsInventoryValueAndClearsBag() {
        MinePlayerProgress progress = newProgress();
        progress.loadInventoryItem("stone", 5);
        progress.loadInventoryItem("gold", 3);

        long earned = progress.sellAll(Map.of("stone", 4L, "gold", 10L));

        assertEquals(50L, earned);
        assertEquals(50.0, progress.getCrystals(), 1e-9);
        assertTrue(progress.getInventory().isEmpty());
        assertEquals(0, progress.getInventoryTotal());
    }

    @Test
    void sellAllExceptRetainsExcludedBlocksAndUsesFallbackPrices() {
        MinePlayerProgress progress = newProgress();
        progress.loadInventoryItem("stone", 3);
        progress.loadInventoryItem("gold", 1);
        progress.loadInventoryItem("dirt", 2);

        long earned = progress.sellAllExcept(Set.of("dirt"), Map.of("stone", 5L));

        assertEquals(16L, earned);
        assertEquals(16.0, progress.getCrystals(), 1e-9);
        assertEquals(Map.of("dirt", 2), progress.getInventory());
        assertEquals(2, progress.getInventoryTotal());
    }

    @Test
    void calculateInventoryValueFallsBackToOneForUnknownBlocks() {
        MinePlayerProgress progress = newProgress();
        progress.loadInventoryItem("stone", 4);
        progress.loadInventoryItem("mystery", 3);

        assertEquals(15L, progress.calculateInventoryValue(Map.of("stone", 3L)));
    }

    @Test
    void purchaseUpgradeSpendsCrystalsAndIncrementsLevel() {
        MinePlayerProgress progress = newProgress();
        progress.setCrystals(MineUpgradeType.HASTE.getCost(0));

        assertTrue(progress.purchaseUpgrade(MineUpgradeType.HASTE));
        assertEquals(1, progress.getUpgradeLevel(MineUpgradeType.HASTE));
        assertEquals(0.0, progress.getCrystals(), 1e-9);
    }

    @Test
    void purchaseUpgradeRejectsMaxedAndUnaffordablePurchases() {
        MinePlayerProgress maxed = newProgress();
        maxed.setCrystals(10_000);
        maxed.setUpgradeLevel(MineUpgradeType.HASTE, MineUpgradeType.HASTE.getMaxLevel());
        assertFalse(maxed.purchaseUpgrade(MineUpgradeType.HASTE));

        MinePlayerProgress poor = newProgress();
        poor.setCrystals(MineUpgradeType.HASTE.getCost(0) - 1);
        assertFalse(poor.purchaseUpgrade(MineUpgradeType.HASTE));
        assertEquals(0, poor.getUpgradeLevel(MineUpgradeType.HASTE));
    }

    @Test
    void purchasePickaxeEnhancementReturnsExpectedResults() {
        MinePlayerProgress success = newProgress();
        success.setCrystals(50);
        assertEquals(MinePlayerProgress.PickaxeEnhanceResult.SUCCESS, success.purchasePickaxeEnhancement(50));
        assertEquals(1, success.getPickaxeEnhancement());
        assertEquals(0.0, success.getCrystals(), 1e-9);

        MinePlayerProgress maxed = newProgress();
        maxed.setPickaxeEnhancement(PickaxeTier.MAX_ENHANCEMENT);
        assertEquals(MinePlayerProgress.PickaxeEnhanceResult.ALREADY_MAXED, maxed.purchasePickaxeEnhancement(1));

        MinePlayerProgress poor = newProgress();
        assertEquals(MinePlayerProgress.PickaxeEnhanceResult.INSUFFICIENT_CRYSTALS, poor.purchasePickaxeEnhancement(25));
        assertEquals(0, poor.getPickaxeEnhancement());
    }

    @Test
    void upgradePickaxeTierReturnsFailureReasonsBeforeSuccess() {
        MinePlayerProgress notReady = newProgress();
        assertEquals(MinePlayerProgress.PickaxeUpgradeResult.NOT_AT_MAX_ENHANCEMENT,
            notReady.upgradePickaxeTier(Map.of("stone", 1)));

        MinePlayerProgress alreadyMaxed = newProgress();
        alreadyMaxed.setPickaxeTier(PickaxeTier.PRISMATIC.getTier());
        alreadyMaxed.setPickaxeEnhancement(PickaxeTier.MAX_ENHANCEMENT);
        assertEquals(MinePlayerProgress.PickaxeUpgradeResult.ALREADY_MAXED,
            alreadyMaxed.upgradePickaxeTier(Map.of("stone", 1)));

        MinePlayerProgress notConfigured = newProgress();
        notConfigured.setPickaxeEnhancement(PickaxeTier.MAX_ENHANCEMENT);
        assertEquals(MinePlayerProgress.PickaxeUpgradeResult.NOT_CONFIGURED, notConfigured.upgradePickaxeTier(null));

        MinePlayerProgress missingBlocks = newProgress();
        missingBlocks.setPickaxeEnhancement(PickaxeTier.MAX_ENHANCEMENT);
        assertEquals(MinePlayerProgress.PickaxeUpgradeResult.MISSING_BLOCKS,
            missingBlocks.upgradePickaxeTier(Map.of("stone", 2)));
    }

    @Test
    void upgradePickaxeTierConsumesBlocksAndAdvancesTier() {
        MinePlayerProgress progress = newProgress();
        progress.setPickaxeEnhancement(PickaxeTier.MAX_ENHANCEMENT);
        progress.loadInventoryItem("stone", 2);

        MinePlayerProgress.PickaxeUpgradeResult result = progress.upgradePickaxeTier(Map.of("stone", 2));

        assertEquals(MinePlayerProgress.PickaxeUpgradeResult.SUCCESS, result);
        assertEquals(PickaxeTier.STONE.getTier(), progress.getPickaxeTier());
        assertEquals(0, progress.getPickaxeEnhancement());
        assertTrue(progress.getInventory().isEmpty());
    }

    @Test
    void addToConveyorBufferStopsAtConfiguredCapacity() {
        MinePlayerProgress progress = newProgress();

        assertTrue(progress.addToConveyorBuffer("stone", 990));
        assertTrue(progress.addToConveyorBuffer("stone", 20));
        assertFalse(progress.addToConveyorBuffer("gold", 1));
        assertEquals(1000, progress.getConveyorBufferCount());
        assertEquals(Map.of("stone", 1000), progress.getConveyorBuffer());
    }

    @Test
    void transferBufferToInventoryMovesOnlyAvailableBagSpace() {
        MinePlayerProgress progress = newProgress();
        progress.addToInventory("stone", 45);
        progress.loadConveyorBufferItem("gold", 20);

        int moved = progress.transferBufferToInventory();

        assertEquals(5, moved);
        assertEquals(50, progress.getInventoryTotal());
        assertEquals(15, progress.getConveyorBufferCount());
        assertEquals(5, progress.getInventory().get("gold"));
        assertEquals(15, progress.getConveyorBuffer().get("gold"));
    }

    @Test
    void transferBlockFromBufferMovesOnlyRequestedType() {
        MinePlayerProgress progress = newProgress();
        progress.addToInventory("stone", 48);
        progress.loadConveyorBufferItem("gold", 5);
        progress.loadConveyorBufferItem("dirt", 4);

        int moved = progress.transferBlockFromBuffer("gold");

        assertEquals(2, moved);
        assertEquals(50, progress.getInventoryTotal());
        assertEquals(7, progress.getConveyorBufferCount());
        assertEquals(2, progress.getInventory().get("gold"));
        assertEquals(3, progress.getConveyorBuffer().get("gold"));
        assertEquals(4, progress.getConveyorBuffer().get("dirt"));
    }

    @Test
    void eggInventoryTracksCountsAndRemovesZeroEntries() {
        MinePlayerProgress progress = newProgress();

        progress.addEgg("deep");
        progress.addEgg("deep");
        assertEquals(2, progress.getEggCount("deep"));
        assertTrue(progress.removeEgg("deep"));
        assertEquals(1, progress.getEggCount("deep"));
        assertTrue(progress.removeEgg("deep"));
        assertEquals(0, progress.getEggCount("deep"));
        assertFalse(progress.removeEgg("deep"));
        assertTrue(progress.getEggInventory().isEmpty());
    }

    @Test
    void minerCollectionAndAssignmentsRoundTrip() {
        MinePlayerProgress progress = newProgress();
        CollectedMiner miner = new CollectedMiner(7L, "volcanic", MinerRarity.EPIC, 2);

        progress.addMiner(miner);
        progress.assignMinerToSlot(3, miner.getId());

        assertEquals(miner, progress.getMinerById(7L));
        assertEquals(miner, progress.getAssignedMiner(3));
        assertEquals(7L, progress.getAssignedMinerId(3));
        assertTrue(progress.isSlotAssigned(3));
        assertTrue(progress.isMinerAssigned(7L));

        progress.unassignSlot(3);
        assertFalse(progress.isSlotAssigned(3));
        assertFalse(progress.isMinerAssigned(7L));
        assertNull(progress.getAssignedMiner(3));
    }

    @Test
    void upgradeMinerSpeedHandlesMissingMinerCostAndSuccess() {
        MinePlayerProgress missing = newProgress();
        assertEquals(MinePlayerProgress.MinerSpeedUpgradeResult.NO_MINER, missing.upgradeMinerSpeed(99L, 10));

        MinePlayerProgress poor = newProgress();
        poor.addMiner(new CollectedMiner(3L, "ice", MinerRarity.RARE, 1));
        assertEquals(MinePlayerProgress.MinerSpeedUpgradeResult.INSUFFICIENT_CRYSTALS,
            poor.upgradeMinerSpeed(3L, 10));

        MinePlayerProgress success = newProgress();
        success.addMiner(new CollectedMiner(4L, "ice", MinerRarity.RARE, 1));
        success.setCrystals(10);
        assertEquals(MinePlayerProgress.MinerSpeedUpgradeResult.SUCCESS, success.upgradeMinerSpeed(4L, 10));
        assertEquals(2, success.getMinerById(4L).getSpeedLevel());
        assertEquals(0.0, success.getCrystals(), 1e-9);
    }

    @Test
    void momentumAndHasteMultipliersReflectCurrentState() {
        MinePlayerProgress progress = newProgress();

        assertEquals(1.0, progress.getMomentumMultiplier(), 1e-9);
        assertEquals(1.0, progress.getHasteMultiplier(), 1e-9);
        assertEquals(0, progress.getMaxCombo());

        progress.setUpgradeLevel(MineUpgradeType.MOMENTUM, 3);
        progress.setUpgradeLevel(MineUpgradeType.HASTE, 4);
        progress.incrementCombo();
        progress.incrementCombo();
        progress.incrementCombo();

        assertEquals(14, progress.getMaxCombo());
        assertEquals(1.06, progress.getMomentumMultiplier(), 1e-9);
        assertEquals(1.20, progress.getHasteMultiplier(), 1e-9);
    }

    private MinePlayerProgress newProgress() {
        return new MinePlayerProgress(UUID.randomUUID());
    }
}
