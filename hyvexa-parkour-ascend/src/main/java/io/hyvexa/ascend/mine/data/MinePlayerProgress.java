package io.hyvexa.ascend.mine.data;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MinePlayerProgress {
    private final UUID playerId;
    private final Map<String, Integer> inventory = new HashMap<>(); // blockTypeId -> count
    private int inventoryCount;
    private long crystals;
    private volatile boolean inMine;
    private volatile int pickaxeTier;
    private volatile int pickaxeEnhancement;
    private final Map<MineUpgradeType, Integer> upgradeLevels = new ConcurrentHashMap<>();

    // --- Gacha system ---
    private final Map<String, Integer> eggInventory = new ConcurrentHashMap<>(); // layerId -> count
    private final List<CollectedMiner> minerCollection = new ArrayList<>();
    private final Map<Integer, Long> slotAssignments = new ConcurrentHashMap<>(); // slotIndex -> minerId

    // Momentum combo state (transient, not persisted)
    private volatile int comboCount;
    private volatile long lastBreakTimeMs;

    // Conveyor buffer (transient, not persisted)
    private final Map<String, Integer> conveyorBuffer = new HashMap<>();
    private int conveyorBufferCount;

    public MinePlayerProgress(UUID playerId) {
        this.playerId = playerId;
    }

    public synchronized boolean addToInventory(String blockTypeId, int amount) {
        return addToInventoryUpTo(blockTypeId, amount) == amount;
    }

    public synchronized int addToInventoryUpTo(String blockTypeId, int amount) {
        if (blockTypeId == null || amount <= 0) {
            return 0;
        }
        int toAdd = Math.min(amount, getRemainingBagSpaceLocked());
        if (toAdd <= 0) {
            return 0;
        }
        inventory.merge(blockTypeId, toAdd, Integer::sum);
        inventoryCount += toAdd;
        return toAdd;
    }

    public synchronized boolean canAddToInventory(int amount) {
        if (amount <= 0) {
            return true;
        }
        return getInventoryTotalLocked() + amount <= getBagCapacity();
    }

    public synchronized int getInventoryTotal() {
        return getInventoryTotalLocked();
    }

    private int getInventoryTotalLocked() {
        return inventoryCount;
    }

    private int getRemainingBagSpaceLocked() {
        return Math.max(0, getBagCapacity() - getInventoryTotalLocked());
    }

    public synchronized boolean isInventoryFull() {
        return getInventoryTotal() >= getBagCapacity();
    }

    /**
     * Sells all of a single block type, returns crystals earned.
     */
    public synchronized long sellBlock(String blockTypeId, Map<String, Long> blockPrices) {
        Integer count = inventory.remove(blockTypeId);
        if (count == null || count <= 0) return 0;
        inventoryCount -= count;
        long price = blockPrices.getOrDefault(blockTypeId, 1L);
        long earned = price * count;
        crystals += earned;
        return earned;
    }

    /**
     * Sells all blocks in inventory, returns total crystals earned.
     */
    public synchronized long sellAll(Map<String, Long> blockPrices) {
        long total = calculateInventoryValue(blockPrices);
        crystals += total;
        inventory.clear();
        inventoryCount = 0;
        return total;
    }

    /**
     * Sells all blocks except the ones in the excluded set.
     */
    public synchronized long sellAllExcept(Set<String> excludedBlocks, Map<String, Long> blockPrices) {
        long total = 0;
        int removed = 0;
        var it = inventory.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (excludedBlocks.contains(entry.getKey())) continue;
            long price = blockPrices.getOrDefault(entry.getKey(), 1L);
            total += price * entry.getValue();
            removed += entry.getValue();
            it.remove();
        }
        inventoryCount -= removed;
        crystals += total;
        return total;
    }

    /**
     * Calculate total value without selling.
     */
    public synchronized long calculateInventoryValue(Map<String, Long> blockPrices) {
        long total = 0;
        for (var entry : inventory.entrySet()) {
            long price = blockPrices.getOrDefault(entry.getKey(), 1L);
            total += price * entry.getValue();
        }
        return total;
    }

    public synchronized Map<String, Integer> getInventory() {
        return new LinkedHashMap<>(inventory);
    }

    public synchronized boolean hasInventoryItems() {
        return !inventory.isEmpty();
    }

    public synchronized void clearInventory() {
        inventory.clear();
        inventoryCount = 0;
    }

    public synchronized void loadInventoryItem(String blockTypeId, int amount) {
        if (blockTypeId == null || amount <= 0) {
            return;
        }
        inventory.put(blockTypeId, amount);
        inventoryCount += amount;
    }

    public synchronized long getCrystals() {
        return crystals;
    }

    public synchronized void setCrystals(long value) {
        crystals = value;
    }

    public synchronized long addCrystals(long amount) {
        crystals += amount;
        return crystals;
    }

    public synchronized boolean trySpendCrystals(long cost) {
        if (cost < 0 || crystals < cost) {
            return false;
        }
        crystals -= cost;
        return true;
    }

    public UUID getPlayerId() { return playerId; }

    public boolean isInMine() { return inMine; }
    public void setInMine(boolean inMine) { this.inMine = inMine; }

    public int getPickaxeTier() { return pickaxeTier; }
    public void setPickaxeTier(int tier) { this.pickaxeTier = tier; }

    public int getPickaxeEnhancement() { return pickaxeEnhancement; }
    public void setPickaxeEnhancement(int enhancement) { this.pickaxeEnhancement = enhancement; }

    public PickaxeTier getPickaxeTierEnum() { return PickaxeTier.fromTier(pickaxeTier); }

    public int getPickaxeDamage() {
        return getPickaxeTierEnum().getBaseDamage() + pickaxeEnhancement;
    }

    public boolean isHoldingExpectedPickaxe(String heldItemId) {
        if (heldItemId == null || heldItemId.isEmpty()) return false;
        String expectedItemId = getPickaxeTierEnum().getItemId();
        return expectedItemId != null && expectedItemId.equals(heldItemId);
    }

    // --- Upgrades ---

    public synchronized int getUpgradeLevel(MineUpgradeType type) {
        return upgradeLevels.getOrDefault(type, 0);
    }

    public synchronized void setUpgradeLevel(MineUpgradeType type, int level) {
        upgradeLevels.put(type, level);
    }

    public synchronized boolean purchaseUpgrade(MineUpgradeType type) {
        int currentLevel = getUpgradeLevel(type);
        if (currentLevel >= type.getMaxLevel()) return false;

        long cost = type.getCost(currentLevel);
        if (!trySpendCrystals(cost)) return false;
        upgradeLevels.put(type, currentLevel + 1);
        return true;
    }

    public synchronized PickaxeEnhanceResult purchasePickaxeEnhancement(long cost) {
        if (pickaxeEnhancement >= PickaxeTier.MAX_ENHANCEMENT) return PickaxeEnhanceResult.ALREADY_MAXED;
        if (cost > 0 && !trySpendCrystals(cost)) return PickaxeEnhanceResult.INSUFFICIENT_CRYSTALS;
        pickaxeEnhancement++;
        return PickaxeEnhanceResult.SUCCESS;
    }

    public synchronized PickaxeUpgradeResult upgradePickaxeTier(Map<String, Integer> requiredBlocks) {
        if (pickaxeEnhancement < PickaxeTier.MAX_ENHANCEMENT) return PickaxeUpgradeResult.NOT_AT_MAX_ENHANCEMENT;
        PickaxeTier current = getPickaxeTierEnum();
        PickaxeTier next = current.next();
        if (next == null) return PickaxeUpgradeResult.ALREADY_MAXED;
        if (requiredBlocks == null || requiredBlocks.isEmpty()) return PickaxeUpgradeResult.NOT_CONFIGURED;
        if (!hasInventoryBlocks(requiredBlocks)) return PickaxeUpgradeResult.MISSING_BLOCKS;
        removeInventoryBlocks(requiredBlocks);
        pickaxeTier = next.getTier();
        pickaxeEnhancement = 0;
        return PickaxeUpgradeResult.SUCCESS;
    }

    public synchronized boolean hasInventoryBlocks(Map<String, Integer> required) {
        for (var entry : required.entrySet()) {
            int have = inventory.getOrDefault(entry.getKey(), 0);
            if (have < entry.getValue()) return false;
        }
        return true;
    }

    public synchronized void removeInventoryBlocks(Map<String, Integer> required) {
        for (var entry : required.entrySet()) {
            String blockId = entry.getKey();
            int toRemove = entry.getValue();
            Integer current = inventory.get(blockId);
            if (current == null) continue;
            int remaining = current - toRemove;
            if (remaining <= 0) {
                inventory.remove(blockId);
                inventoryCount -= current;
            } else {
                inventory.put(blockId, remaining);
                inventoryCount -= toRemove;
            }
        }
    }

    public enum PickaxeEnhanceResult {
        SUCCESS,
        ALREADY_MAXED,
        INSUFFICIENT_CRYSTALS
    }

    public enum PickaxeUpgradeResult {
        SUCCESS,
        ALREADY_MAXED,
        NOT_AT_MAX_ENHANCEMENT,
        MISSING_BLOCKS,
        NOT_CONFIGURED
    }

    // --- Momentum combo ---

    public int getComboCount() { return comboCount; }

    public void incrementCombo() {
        comboCount++;
        lastBreakTimeMs = System.currentTimeMillis();
    }

    public void resetCombo() {
        comboCount = 0;
    }

    public long getLastBreakTimeMs() { return lastBreakTimeMs; }

    /**
     * Returns the max combo for the current Momentum level.
     * Formula: 5 + level * 3
     */
    public int getMaxCombo() {
        int level = getUpgradeLevel(MineUpgradeType.MOMENTUM);
        return level > 0 ? (int) MineUpgradeType.MOMENTUM.getEffect(level) : 0;
    }

    /**
     * Returns the damage multiplier from the current Momentum combo.
     * +2% per combo hit.
     */
    public double getMomentumMultiplier() {
        if (comboCount <= 0) return 1.0;
        return 1.0 + comboCount * 0.02;
    }

    /**
     * Returns the movement speed multiplier from the Haste upgrade.
     * +5% per level (max level 20 = +100%).
     */
    public double getHasteMultiplier() {
        int level = getUpgradeLevel(MineUpgradeType.HASTE);
        if (level <= 0) return 1.0;
        return 1.0 + MineUpgradeType.HASTE.getEffect(level) / 100.0;
    }

    /**
     * Checks if combo has expired (3 seconds since last break).
     * If expired, resets combo and returns true.
     */
    public boolean checkComboExpired() {
        if (comboCount > 0 && System.currentTimeMillis() - lastBreakTimeMs > 3000) {
            resetCombo();
            return true;
        }
        return false;
    }

    public synchronized int getBagCapacity() {
        return (int) MineUpgradeType.BAG_CAPACITY.getEffect(getUpgradeLevel(MineUpgradeType.BAG_CAPACITY));
    }

    // --- Egg inventory ---

    public synchronized void addEgg(String layerId) {
        eggInventory.merge(layerId, 1, Integer::sum);
    }

    public synchronized boolean removeEgg(String layerId) {
        Integer count = eggInventory.get(layerId);
        if (count == null || count <= 0) return false;
        if (count == 1) {
            eggInventory.remove(layerId);
        } else {
            eggInventory.put(layerId, count - 1);
        }
        return true;
    }

    public synchronized int getEggCount(String layerId) {
        return eggInventory.getOrDefault(layerId, 0);
    }

    public synchronized Map<String, Integer> getEggInventory() {
        return new LinkedHashMap<>(eggInventory);
    }

    public synchronized void loadEgg(String layerId, int count) {
        if (layerId == null || count <= 0) return;
        eggInventory.put(layerId, count);
    }

    // --- Miner collection ---

    public synchronized void addMiner(CollectedMiner miner) {
        minerCollection.add(miner);
    }

    public synchronized CollectedMiner getMinerById(long minerId) {
        for (CollectedMiner m : minerCollection) {
            if (m.getId() == minerId) return m;
        }
        return null;
    }

    public synchronized List<CollectedMiner> getMinerCollection() {
        return new ArrayList<>(minerCollection);
    }

    // --- Slot assignments ---

    public synchronized void assignMinerToSlot(int slotIndex, long minerId) {
        slotAssignments.put(slotIndex, minerId);
    }

    public synchronized void unassignSlot(int slotIndex) {
        slotAssignments.remove(slotIndex);
    }

    public synchronized Long getAssignedMinerId(int slotIndex) {
        return slotAssignments.get(slotIndex);
    }

    public synchronized CollectedMiner getAssignedMiner(int slotIndex) {
        Long minerId = slotAssignments.get(slotIndex);
        if (minerId == null) return null;
        return getMinerById(minerId);
    }

    public synchronized boolean isSlotAssigned(int slotIndex) {
        return slotAssignments.containsKey(slotIndex);
    }

    public synchronized Map<Integer, Long> getSlotAssignments() {
        return new LinkedHashMap<>(slotAssignments);
    }

    /**
     * Returns true if the given miner is currently assigned to any slot.
     */
    public synchronized boolean isMinerAssigned(long minerId) {
        return slotAssignments.containsValue(minerId);
    }

    /**
     * Upgrade the speed of a collected miner by ID.
     */
    public synchronized MinerSpeedUpgradeResult upgradeMinerSpeed(long minerId, long cost) {
        CollectedMiner miner = getMinerById(minerId);
        if (miner == null) return MinerSpeedUpgradeResult.NO_MINER;
        if (!trySpendCrystals(cost)) return MinerSpeedUpgradeResult.INSUFFICIENT_CRYSTALS;
        miner.setSpeedLevel(miner.getSpeedLevel() + 1);
        return MinerSpeedUpgradeResult.SUCCESS;
    }

    public synchronized PlayerSaveSnapshot createSaveSnapshot() {
        Map<MineUpgradeType, Integer> upgradeSnapshot = new EnumMap<>(MineUpgradeType.class);
        upgradeSnapshot.putAll(upgradeLevels);
        return new PlayerSaveSnapshot(
            crystals,
            upgradeSnapshot,
            new LinkedHashMap<>(inventory),
            new LinkedHashMap<>(eggInventory),
            new ArrayList<>(minerCollection),
            new LinkedHashMap<>(slotAssignments),
            inMine,
            pickaxeTier,
            pickaxeEnhancement,
            new LinkedHashMap<>(conveyorBuffer)
        );
    }

    // --- Conveyor buffer ---

    public synchronized void loadConveyorBufferItem(String blockTypeId, int amount) {
        if (blockTypeId == null || amount <= 0) return;
        conveyorBuffer.put(blockTypeId, amount);
        conveyorBufferCount += amount;
    }

    /** Add block to conveyor buffer (no capacity limit). */
    public synchronized void addToConveyorBuffer(String blockTypeId, int amount) {
        if (blockTypeId == null || amount <= 0) return;
        conveyorBuffer.merge(blockTypeId, amount, Integer::sum);
        conveyorBufferCount += amount;
    }

    /**
     * Transfer conveyor buffer to mine inventory, respecting bag capacity.
     * Returns total items transferred.
     */
    public synchronized int transferBufferToInventory() {
        if (conveyorBuffer.isEmpty()) return 0;
        int transferred = 0;
        var it = conveyorBuffer.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            int space = getBagCapacity() - inventoryCount;
            if (space <= 0) break;
            int toMove = Math.min(entry.getValue(), space);
            inventory.merge(entry.getKey(), toMove, Integer::sum);
            inventoryCount += toMove;
            transferred += toMove;
            int remaining = entry.getValue() - toMove;
            if (remaining <= 0) {
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
        conveyorBufferCount -= transferred;
        return transferred;
    }

    public synchronized int getConveyorBufferCount() {
        return conveyorBufferCount;
    }

    public synchronized boolean hasConveyorItems() {
        return conveyorBufferCount > 0;
    }

    public synchronized Map<String, Integer> getConveyorBuffer() {
        return new LinkedHashMap<>(conveyorBuffer);
    }

    /**
     * Transfer a single block type from conveyor buffer to inventory, respecting bag capacity.
     * Returns the number of items transferred.
     */
    public synchronized int transferBlockFromBuffer(String blockTypeId) {
        if (blockTypeId == null) return 0;
        Integer count = conveyorBuffer.get(blockTypeId);
        if (count == null || count <= 0) return 0;
        int space = getBagCapacity() - inventoryCount;
        if (space <= 0) return 0;
        int toMove = Math.min(count, space);
        inventory.merge(blockTypeId, toMove, Integer::sum);
        inventoryCount += toMove;
        int remaining = count - toMove;
        if (remaining <= 0) {
            conveyorBuffer.remove(blockTypeId);
        } else {
            conveyorBuffer.put(blockTypeId, remaining);
        }
        conveyorBufferCount -= toMove;
        return toMove;
    }

    public enum MinerSpeedUpgradeResult {
        SUCCESS,
        NO_MINER,
        INSUFFICIENT_CRYSTALS
    }

    public record PlayerSaveSnapshot(long crystals,
                                     Map<MineUpgradeType, Integer> upgradeLevels,
                                     Map<String, Integer> inventory,
                                     Map<String, Integer> eggInventory,
                                     List<CollectedMiner> minerCollection,
                                     Map<Integer, Long> slotAssignments,
                                     boolean inMine,
                                     int pickaxeTier,
                                     int pickaxeEnhancement,
                                     Map<String, Integer> conveyorBuffer) {}
}
