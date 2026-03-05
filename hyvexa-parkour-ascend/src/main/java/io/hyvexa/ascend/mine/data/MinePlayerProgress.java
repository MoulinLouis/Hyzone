package io.hyvexa.ascend.mine.data;

import io.hyvexa.common.math.BigNumber;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MinePlayerProgress {
    private final UUID playerId;
    private final Map<String, Integer> inventory = new ConcurrentHashMap<>(); // blockTypeId -> count
    private final AtomicLong crystals = new AtomicLong(0);
    private final Map<MineUpgradeType, Integer> upgradeLevels = new ConcurrentHashMap<>();

    public MinePlayerProgress(UUID playerId) {
        this.playerId = playerId;
    }

    public boolean addToInventory(String blockTypeId, int amount) {
        int total = inventory.values().stream().mapToInt(Integer::intValue).sum();
        if (total + amount > getBagCapacity()) return false; // bag full
        inventory.merge(blockTypeId, amount, Integer::sum);
        return true;
    }

    public int getInventoryTotal() {
        return inventory.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean isInventoryFull() {
        return getInventoryTotal() >= getBagCapacity();
    }

    /**
     * Sells all blocks in inventory, returns total crystals earned.
     */
    public long sellAll(Map<String, BigNumber> blockPrices) {
        long total = 0;
        for (var entry : inventory.entrySet()) {
            BigNumber price = blockPrices.getOrDefault(entry.getKey(), BigNumber.ONE);
            total += price.multiply(BigNumber.of(entry.getValue(), 0)).toLong();
        }
        crystals.addAndGet(total);
        inventory.clear();
        return total;
    }

    /**
     * Calculate total value without selling.
     */
    public long calculateInventoryValue(Map<String, BigNumber> blockPrices) {
        long total = 0;
        for (var entry : inventory.entrySet()) {
            BigNumber price = blockPrices.getOrDefault(entry.getKey(), BigNumber.ONE);
            total += price.multiply(BigNumber.of(entry.getValue(), 0)).toLong();
        }
        return total;
    }

    public Map<String, Integer> getInventory() { return inventory; }
    public void clearInventory() { inventory.clear(); }
    public long getCrystals() { return crystals.get(); }
    public void setCrystals(long value) { crystals.set(value); }
    public void addCrystals(long amount) { crystals.addAndGet(amount); }
    public UUID getPlayerId() { return playerId; }

    // --- Upgrades ---

    public int getUpgradeLevel(MineUpgradeType type) {
        return upgradeLevels.getOrDefault(type, 0);
    }

    public void setUpgradeLevel(MineUpgradeType type, int level) {
        upgradeLevels.put(type, level);
    }

    public boolean purchaseUpgrade(MineUpgradeType type) {
        int currentLevel = getUpgradeLevel(type);
        if (currentLevel >= type.getMaxLevel()) return false;

        long cost = type.getCost(currentLevel);
        long current = crystals.get();
        if (current < cost) return false;

        crystals.set(current - cost);
        upgradeLevels.put(type, currentLevel + 1);
        return true;
    }

    public int getBagCapacity() {
        return (int) MineUpgradeType.BAG_CAPACITY.getEffect(getUpgradeLevel(MineUpgradeType.BAG_CAPACITY));
    }

    public double getMiningSpeedMultiplier() {
        return MineUpgradeType.MINING_SPEED.getEffect(getUpgradeLevel(MineUpgradeType.MINING_SPEED));
    }

    public double getMultiBreakChance() {
        return MineUpgradeType.MULTI_BREAK.getEffect(getUpgradeLevel(MineUpgradeType.MULTI_BREAK)) / 100.0;
    }

    public boolean isAutoSellEnabled() {
        return getUpgradeLevel(MineUpgradeType.AUTO_SELL) >= 1;
    }

    // Per-mine state (unlocked, completed, etc.) will be added in Phase 6
}
