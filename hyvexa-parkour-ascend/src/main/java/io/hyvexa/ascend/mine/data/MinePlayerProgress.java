package io.hyvexa.ascend.mine.data;

import io.hyvexa.common.math.BigNumber;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class MinePlayerProgress {
    private final UUID playerId;
    private final Map<String, Integer> inventory = new ConcurrentHashMap<>(); // blockTypeId -> count
    private volatile int bagCapacity = 50; // default starting capacity
    private final AtomicReference<BigNumber> crystals = new AtomicReference<>(BigNumber.ZERO);

    public MinePlayerProgress(UUID playerId) {
        this.playerId = playerId;
    }

    public boolean addToInventory(String blockTypeId, int amount) {
        int total = inventory.values().stream().mapToInt(Integer::intValue).sum();
        if (total + amount > bagCapacity) return false; // bag full
        inventory.merge(blockTypeId, amount, Integer::sum);
        return true;
    }

    public int getInventoryTotal() {
        return inventory.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean isInventoryFull() {
        return getInventoryTotal() >= bagCapacity;
    }

    /**
     * Sells all blocks in inventory, returns total crystals earned.
     */
    public BigNumber sellAll(Map<String, BigNumber> blockPrices) {
        BigNumber total = BigNumber.ZERO;
        for (var entry : inventory.entrySet()) {
            BigNumber price = blockPrices.getOrDefault(entry.getKey(), BigNumber.ONE);
            BigNumber earned = price.multiply(BigNumber.of(entry.getValue(), 0));
            total = total.add(earned);
        }
        crystals.set(crystals.get().add(total));
        inventory.clear();
        return total;
    }

    /**
     * Calculate total value without selling.
     */
    public BigNumber calculateInventoryValue(Map<String, BigNumber> blockPrices) {
        BigNumber total = BigNumber.ZERO;
        for (var entry : inventory.entrySet()) {
            BigNumber price = blockPrices.getOrDefault(entry.getKey(), BigNumber.ONE);
            total = total.add(price.multiply(BigNumber.of(entry.getValue(), 0)));
        }
        return total;
    }

    public Map<String, Integer> getInventory() { return inventory; }
    public void clearInventory() { inventory.clear(); }
    public int getBagCapacity() { return bagCapacity; }
    public void setBagCapacity(int capacity) { this.bagCapacity = capacity; }
    public BigNumber getCrystals() { return crystals.get(); }
    public void setCrystals(BigNumber value) { crystals.set(value); }
    public UUID getPlayerId() { return playerId; }
    // Per-mine state (unlocked, completed, etc.) will be added in Phase 6
}
