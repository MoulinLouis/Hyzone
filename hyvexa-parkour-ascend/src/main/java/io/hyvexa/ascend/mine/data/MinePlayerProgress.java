package io.hyvexa.ascend.mine.data;

import io.hyvexa.common.math.BigNumber;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MinePlayerProgress {
    private final UUID playerId;
    private final Map<String, Integer> inventory = new HashMap<>(); // blockTypeId -> count
    private long crystals;
    private final Map<MineUpgradeType, Integer> upgradeLevels = new ConcurrentHashMap<>();
    private final Map<String, MineProgress> mineStates = new ConcurrentHashMap<>();
    private final Map<String, MinerProgress> minerStates = new ConcurrentHashMap<>();

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
        return inventory.values().stream().mapToInt(Integer::intValue).sum();
    }

    private int getRemainingBagSpaceLocked() {
        return Math.max(0, getBagCapacity() - getInventoryTotalLocked());
    }

    public synchronized boolean isInventoryFull() {
        return getInventoryTotal() >= getBagCapacity();
    }

    /**
     * Sells all blocks in inventory, returns total crystals earned.
     */
    public synchronized long sellAll(Map<String, BigNumber> blockPrices) {
        long total = 0;
        for (var entry : inventory.entrySet()) {
            BigNumber price = blockPrices.getOrDefault(entry.getKey(), BigNumber.ONE);
            total += price.multiply(BigNumber.of(entry.getValue(), 0)).toLong();
        }
        crystals += total;
        inventory.clear();
        return total;
    }

     /**
     * Calculate total value without selling.
     */
    public synchronized long calculateInventoryValue(Map<String, BigNumber> blockPrices) {
        long total = 0;
        for (var entry : inventory.entrySet()) {
            BigNumber price = blockPrices.getOrDefault(entry.getKey(), BigNumber.ONE);
            total += price.multiply(BigNumber.of(entry.getValue(), 0)).toLong();
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
    }

    public synchronized void loadInventoryItem(String blockTypeId, int amount) {
        if (blockTypeId == null || amount <= 0) {
            return;
        }
        inventory.put(blockTypeId, amount);
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

    public synchronized int getBagCapacity() {
        return (int) MineUpgradeType.BAG_CAPACITY.getEffect(getUpgradeLevel(MineUpgradeType.BAG_CAPACITY));
    }

    public synchronized double getMiningSpeedMultiplier() {
        return MineUpgradeType.MINING_SPEED.getEffect(getUpgradeLevel(MineUpgradeType.MINING_SPEED));
    }

    public synchronized double getMultiBreakChance() {
        return MineUpgradeType.MULTI_BREAK.getEffect(getUpgradeLevel(MineUpgradeType.MULTI_BREAK)) / 100.0;
    }

    public synchronized boolean isAutoSellEnabled() {
        return getUpgradeLevel(MineUpgradeType.AUTO_SELL) >= 1;
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
            getMinerStates()
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

    public static final class MineProgressSnapshot {
        private final boolean unlocked;
        private final boolean completedManually;

        public MineProgressSnapshot(boolean unlocked, boolean completedManually) {
            this.unlocked = unlocked;
            this.completedManually = completedManually;
        }

        public boolean isUnlocked() { return unlocked; }
        public boolean isCompletedManually() { return completedManually; }
    }

    public static final class MinerProgressSnapshot {
        private final boolean hasMiner;
        private final int speedLevel;
        private final int stars;

        public MinerProgressSnapshot(boolean hasMiner, int speedLevel, int stars) {
            this.hasMiner = hasMiner;
            this.speedLevel = speedLevel;
            this.stars = stars;
        }

        public boolean isHasMiner() { return hasMiner; }
        public int getSpeedLevel() { return speedLevel; }
        public int getStars() { return stars; }
    }

    public static final class PlayerSaveSnapshot {
        private final long crystals;
        private final Map<MineUpgradeType, Integer> upgradeLevels;
        private final Map<String, Integer> inventory;
        private final Map<String, MineProgressSnapshot> mineStates;
        private final Map<String, MinerProgressSnapshot> minerStates;

        public PlayerSaveSnapshot(long crystals,
                                  Map<MineUpgradeType, Integer> upgradeLevels,
                                  Map<String, Integer> inventory,
                                  Map<String, MineProgressSnapshot> mineStates,
                                  Map<String, MinerProgressSnapshot> minerStates) {
            this.crystals = crystals;
            this.upgradeLevels = upgradeLevels;
            this.inventory = inventory;
            this.mineStates = mineStates;
            this.minerStates = minerStates;
        }

        public long getCrystals() { return crystals; }
        public Map<MineUpgradeType, Integer> getUpgradeLevels() { return upgradeLevels; }
        public Map<String, Integer> getInventory() { return inventory; }
        public Map<String, MineProgressSnapshot> getMineStates() { return mineStates; }
        public Map<String, MinerProgressSnapshot> getMinerStates() { return minerStates; }
    }
}
