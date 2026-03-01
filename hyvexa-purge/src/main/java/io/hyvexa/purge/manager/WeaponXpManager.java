package io.hyvexa.purge.manager;

import io.hyvexa.purge.data.WeaponXpStore;

import java.util.UUID;

public class WeaponXpManager {

    public static final int MAX_LEVEL = 20;

    /**
     * Grants 1 kill XP. Returns the new level if a level-up occurred, or -1 if no level-up.
     */
    public int addKillXp(UUID playerId, String weaponId) {
        if (playerId == null || weaponId == null) {
            return -1;
        }
        int[] data = WeaponXpStore.getInstance().getXpData(playerId, weaponId);
        int currentXp = data[0];
        int currentLevel = data[1];
        if (currentLevel >= MAX_LEVEL) {
            return -1;
        }
        int newXp = currentXp + 1;
        int newLevel = levelFromXp(newXp);
        WeaponXpStore.getInstance().incrementXp(playerId, weaponId, newXp, newLevel);
        if (newLevel > currentLevel) {
            return newLevel;
        }
        return -1;
    }

    public double getDamageMultiplier(UUID playerId, String weaponId) {
        int level = getLevel(playerId, weaponId);
        return 1.0 + 0.015 * level;
    }

    public int getBonusScrap(UUID playerId, String weaponId) {
        int level = getLevel(playerId, weaponId);
        return (int) (0.5 * level);
    }

    public double getAmmoMultiplier(UUID playerId, String weaponId) {
        int level = getLevel(playerId, weaponId);
        return 1.0 + 0.05 * level;
    }

    public int[] getXpData(UUID playerId, String weaponId) {
        return WeaponXpStore.getInstance().getXpData(playerId, weaponId);
    }

    public int getLevel(UUID playerId, String weaponId) {
        if (playerId == null || weaponId == null) {
            return 0;
        }
        return WeaponXpStore.getInstance().getXpData(playerId, weaponId)[1];
    }

    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    // --- XP formulas ---

    public static int xpForLevel(int n) {
        return 25 * n;
    }

    public static int cumulativeXp(int n) {
        return 25 * n * (n + 1) / 2;
    }

    public static int levelFromXp(int xp) {
        if (xp <= 0) return 0;
        int level = (int) ((-1.0 + Math.sqrt(1.0 + 8.0 * xp / 25.0)) / 2.0);
        return Math.min(level, MAX_LEVEL);
    }
}
