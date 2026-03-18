package io.hyvexa.ascend.mine.data;

import io.hyvexa.common.math.BigNumber;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final Map<MineUpgradeType, Integer> upgradeLevels = new ConcurrentHashMap<>();
    private final Map<String, MineProgress> mineStates = new ConcurrentHashMap<>();
    private final Map<String, MinerProgress> minerStates = new ConcurrentHashMap<>();

    // Momentum combo state (transient, not persisted)
    private volatile int comboCount;
    private volatile long lastBreakTimeMs;

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

    public PickaxeTier getPickaxeTierEnum() { return PickaxeTier.fromTier(pickaxeTier); }

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

    public synchronized PickaxeUpgradeResult purchasePickaxeTier() {
        PickaxeTier current = getPickaxeTierEnum();
        PickaxeTier next = current.next();
        if (next == null) return PickaxeUpgradeResult.ALREADY_MAXED;
        if (!trySpendCrystals(next.getUnlockCost())) return PickaxeUpgradeResult.INSUFFICIENT_CRYSTALS;
        pickaxeTier = next.getTier();
        return PickaxeUpgradeResult.SUCCESS;
    }

    public enum PickaxeUpgradeResult {
        SUCCESS,
        ALREADY_MAXED,
        INSUFFICIENT_CRYSTALS
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

    // --- Per-mine state ---

    public static class MineProgress {
        volatile boolean unlocked;
        volatile boolean completedManually;

        public MineProgress() {}
        public boolean isUnlocked() { return unlocked; }
        public void setUnlocked(boolean u) { unlocked = u; }
        public boolean isCompletedManually() { return completedManually; }
        public void setCompletedManually(boolean c) { completedManually = c; }
    }

    public MineProgress getMineState(String mineId) {
        return mineStates.computeIfAbsent(mineId, k -> new MineProgress());
    }

    public synchronized MineProgressSnapshot getMineSnapshot(String mineId) {
        MineProgress state = getMineState(mineId);
        return new MineProgressSnapshot(state.isUnlocked(), state.isCompletedManually());
    }

    public synchronized Map<String, MineProgressSnapshot> getMineStates() {
        Map<String, MineProgressSnapshot> copy = new HashMap<>();
        for (var entry : mineStates.entrySet()) {
            MineProgress state = entry.getValue();
            copy.put(entry.getKey(), new MineProgressSnapshot(state.isUnlocked(), state.isCompletedManually()));
        }
        return copy;
    }

    public synchronized void loadMineState(String mineId, boolean unlocked, boolean completedManually) {
        MineProgress state = getMineState(mineId);
        state.setUnlocked(unlocked);
        state.setCompletedManually(completedManually);
    }

    // --- Per-mine miner state ---

    public static class MinerProgress {
        volatile boolean hasMiner;
        volatile int speedLevel;
        volatile int stars;

        public MinerProgress() {}
        public boolean isHasMiner() { return hasMiner; }
        public void setHasMiner(boolean h) { hasMiner = h; }
        public int getSpeedLevel() { return speedLevel; }
        public void setSpeedLevel(int s) { speedLevel = s; }
        public int getStars() { return stars; }
        public void setStars(int s) { stars = s; }
    }

    public MinerProgress getMinerState(String mineId) {
        return minerStates.computeIfAbsent(mineId, k -> new MinerProgress());
    }

    public synchronized MinerProgressSnapshot getMinerSnapshot(String mineId) {
        MinerProgress state = getMinerState(mineId);
        return new MinerProgressSnapshot(state.isHasMiner(), state.getSpeedLevel(), state.getStars());
    }

    public synchronized Map<String, MinerProgressSnapshot> getMinerStates() {
        Map<String, MinerProgressSnapshot> copy = new HashMap<>();
        for (var entry : minerStates.entrySet()) {
            MinerProgress state = entry.getValue();
            copy.put(entry.getKey(), new MinerProgressSnapshot(
                state.isHasMiner(),
                state.getSpeedLevel(),
                state.getStars()
            ));
        }
        return copy;
    }

    public synchronized void loadMinerState(String mineId, boolean hasMiner, int speedLevel, int stars) {
        MinerProgress state = getMinerState(mineId);
        state.setHasMiner(hasMiner);
        state.setSpeedLevel(speedLevel);
        state.setStars(stars);
    }

    public synchronized MinerPurchaseResult purchaseMiner(String mineId, long cost) {
        MinerProgress state = getMinerState(mineId);
        if (state.isHasMiner()) {
            return MinerPurchaseResult.ALREADY_OWNED;
        }
        if (!trySpendCrystals(cost)) {
            return MinerPurchaseResult.INSUFFICIENT_CRYSTALS;
        }
        state.setHasMiner(true);
        return MinerPurchaseResult.SUCCESS;
    }

    public synchronized MinerSpeedUpgradeResult upgradeMinerSpeed(String mineId, long cost, int maxSpeedLevel) {
        MinerProgress state = getMinerState(mineId);
        if (!state.isHasMiner()) {
            return MinerSpeedUpgradeResult.NO_MINER;
        }
        if (state.getSpeedLevel() >= maxSpeedLevel) {
            return MinerSpeedUpgradeResult.SPEED_MAXED;
        }
        if (!trySpendCrystals(cost)) {
            return MinerSpeedUpgradeResult.INSUFFICIENT_CRYSTALS;
        }
        state.setSpeedLevel(state.getSpeedLevel() + 1);
        return MinerSpeedUpgradeResult.SUCCESS;
    }

    public synchronized MinerEvolutionResult evolveMiner(String mineId, long cost, int maxSpeedLevel, int maxStars) {
        MinerProgress state = getMinerState(mineId);
        if (!state.isHasMiner()) {
            return MinerEvolutionResult.NO_MINER;
        }
        if (state.getSpeedLevel() < maxSpeedLevel) {
            return MinerEvolutionResult.SPEED_NOT_MAXED;
        }
        if (state.getStars() >= maxStars) {
            return MinerEvolutionResult.STAR_MAXED;
        }
        if (!trySpendCrystals(cost)) {
            return MinerEvolutionResult.INSUFFICIENT_CRYSTALS;
        }
        state.setSpeedLevel(0);
        state.setStars(state.getStars() + 1);
        return MinerEvolutionResult.SUCCESS;
    }

    public synchronized boolean unlockMine(String mineId, BigNumber cost) {
        MineProgress state = getMineState(mineId);
        if (state.isUnlocked()) return false;
        if (!trySpendCrystals(cost.toLong())) return false;
        state.setUnlocked(true);
        return true;
    }

    public synchronized PlayerSaveSnapshot createSaveSnapshot() {
        Map<MineUpgradeType, Integer> upgradeSnapshot = new EnumMap<>(MineUpgradeType.class);
        upgradeSnapshot.putAll(upgradeLevels);
        return new PlayerSaveSnapshot(
            crystals,
            upgradeSnapshot,
            new LinkedHashMap<>(inventory),
            getMineStates(),
            getMinerStates(),
            inMine,
            pickaxeTier
        );
    }

    public enum MinerPurchaseResult {
        SUCCESS,
        ALREADY_OWNED,
        INSUFFICIENT_CRYSTALS
    }

    public enum MinerSpeedUpgradeResult {
        SUCCESS,
        NO_MINER,
        SPEED_MAXED,
        INSUFFICIENT_CRYSTALS
    }

    public enum MinerEvolutionResult {
        SUCCESS,
        NO_MINER,
        SPEED_NOT_MAXED,
        STAR_MAXED,
        INSUFFICIENT_CRYSTALS
    }

    public record MineProgressSnapshot(boolean unlocked, boolean completedManually) {}

    public record MinerProgressSnapshot(boolean hasMiner, int speedLevel, int stars) {}

    public record PlayerSaveSnapshot(long crystals,
                                     Map<MineUpgradeType, Integer> upgradeLevels,
                                     Map<String, Integer> inventory,
                                     Map<String, MineProgressSnapshot> mineStates,
                                     Map<String, MinerProgressSnapshot> minerStates,
                                     boolean inMine,
                                     int pickaxeTier) {}
}
