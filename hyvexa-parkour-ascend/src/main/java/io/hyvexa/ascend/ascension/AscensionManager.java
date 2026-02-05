package io.hyvexa.ascend.ascension;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.SkillTreeNode;
import io.hyvexa.ascend.AscendConstants.SkillTreePath;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the Ascension prestige system.
 * Ascension grants skill tree points and resets ALL progress (coins, elevation, Summit levels).
 */
public class AscensionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AscendPlayerStore playerStore;

    public AscensionManager(AscendPlayerStore playerStore) {
        this.playerStore = playerStore;
    }

    /**
     * Checks if a player can perform an Ascension.
     */
    public boolean canAscend(UUID playerId) {
        java.math.BigDecimal coins = playerStore.getCoins(playerId);
        java.math.BigDecimal threshold = java.math.BigDecimal.valueOf(AscendConstants.ASCENSION_COIN_THRESHOLD);
        return coins.compareTo(threshold) >= 0;
    }

    /**
     * Performs an Ascension: grants 1 skill tree point, resets all progress.
     *
     * @return the new Ascension count, or -1 if insufficient coins
     */
    public int performAscension(UUID playerId) {
        java.math.BigDecimal coins = playerStore.getCoins(playerId);
        java.math.BigDecimal threshold = java.math.BigDecimal.valueOf(AscendConstants.ASCENSION_COIN_THRESHOLD);
        if (coins.compareTo(threshold) < 0) {
            return -1;
        }

        AscendPlayerProgress progress = playerStore.getOrCreatePlayer(playerId);

        // Grant skill tree point
        int newPoints = progress.addSkillTreePoints(1);
        int newAscensionCount = progress.incrementAscensionCount();

        // Update ascension timer stats
        Long startedAt = progress.getAscensionStartedAt();
        if (startedAt != null) {
            long elapsed = System.currentTimeMillis() - startedAt;
            Long fastest = progress.getFastestAscensionMs();
            if (fastest == null || elapsed < fastest) {
                progress.setFastestAscensionMs(elapsed);
                LOGGER.atInfo().log("[Ascension] Player " + playerId + " new fastest ascension: " + elapsed + "ms");
            }
        }
        // Reset timer for next ascension
        progress.setAscensionStartedAt(System.currentTimeMillis());

        // Check for Summit Persistence skill
        boolean hasSummitPersist = progress.hasSkillNode(SkillTreeNode.HYBRID_SUMMIT_PERSIST);
        Map<SummitCategory, Integer> preservedLevels = null;
        if (hasSummitPersist) {
            // Preserve 50% of Summit levels (rounded down)
            preservedLevels = new EnumMap<>(SummitCategory.class);
            for (SummitCategory cat : SummitCategory.values()) {
                int level = progress.getSummitLevel(cat);
                preservedLevels.put(cat, level / 2);
            }
        }

        // Reset progress
        progress.setCoins(java.math.BigDecimal.ZERO);
        progress.setElevationMultiplier(1);
        progress.clearSummitLevels();

        // Reset all map progress (multipliers, unlocks, robots)
        progress.getMapProgress().clear();

        // Restore preserved Summit levels if applicable (convert level to XP)
        if (preservedLevels != null) {
            for (Map.Entry<SummitCategory, Integer> entry : preservedLevels.entrySet()) {
                if (entry.getValue() > 0) {
                    long xp = AscendConstants.getCumulativeXpForLevel(entry.getValue());
                    progress.setSummitXp(entry.getKey(), xp);
                }
            }
        }

        // Apply starting coins skill
        if (progress.hasSkillNode(SkillTreeNode.COIN_T1_STARTING_COINS)) {
            progress.setCoins(java.math.BigDecimal.valueOf(1000));
        }

        playerStore.markDirty(playerId);

        LOGGER.atInfo().log("[Ascension] Player " + playerId + " ascended! Count: " + newAscensionCount
            + ", Skill Points: " + newPoints);

        return newAscensionCount;
    }

    /**
     * Attempts to unlock a skill tree node for a player.
     *
     * @return true if successfully unlocked, false otherwise
     */
    public boolean tryUnlockSkillNode(UUID playerId, SkillTreeNode node) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return false;
        }

        // Already unlocked?
        if (progress.hasSkillNode(node)) {
            return false;
        }

        // Has available points?
        if (progress.getAvailableSkillPoints() <= 0) {
            return false;
        }

        // Check prerequisite
        SkillTreeNode prereq = node.getPrerequisite();
        if (prereq != null && !progress.hasSkillNode(prereq)) {
            return false;
        }

        // Check hybrid/ultimate requirements
        if (node.getPath() == SkillTreePath.HYBRID) {
            if (!canUnlockHybridNode(progress, node)) {
                return false;
            }
        } else if (node.getPath() == SkillTreePath.ULTIMATE) {
            if (!canUnlockUltimateNode(progress)) {
                return false;
            }
        }

        // Unlock the node
        progress.unlockSkillNode(node);
        playerStore.markDirty(playerId);

        LOGGER.atInfo().log("[Ascension] Player " + playerId + " unlocked skill: " + node.name());
        return true;
    }

    private boolean canUnlockHybridNode(AscendPlayerProgress progress, SkillTreeNode node) {
        // Hybrid nodes require points in multiple paths
        int coinPoints = countPointsInPath(progress, SkillTreePath.COIN);
        int speedPoints = countPointsInPath(progress, SkillTreePath.SPEED);
        int manualPoints = countPointsInPath(progress, SkillTreePath.MANUAL);

        // For HYBRID_OFFLINE_EARNINGS: need 3 in COIN + 3 in SPEED
        // For HYBRID_SUMMIT_PERSIST: need 3 in any two paths
        if (node == SkillTreeNode.HYBRID_OFFLINE_EARNINGS) {
            return coinPoints >= AscendConstants.HYBRID_PATH_REQUIREMENT
                && speedPoints >= AscendConstants.HYBRID_PATH_REQUIREMENT;
        } else if (node == SkillTreeNode.HYBRID_SUMMIT_PERSIST) {
            int pathsWithEnough = 0;
            if (coinPoints >= AscendConstants.HYBRID_PATH_REQUIREMENT) pathsWithEnough++;
            if (speedPoints >= AscendConstants.HYBRID_PATH_REQUIREMENT) pathsWithEnough++;
            if (manualPoints >= AscendConstants.HYBRID_PATH_REQUIREMENT) pathsWithEnough++;
            return pathsWithEnough >= 2;
        }
        return false;
    }

    private boolean canUnlockUltimateNode(AscendPlayerProgress progress) {
        // Ultimate node requires total points spent across all paths
        int totalSpent = progress.getSpentSkillPoints();
        return totalSpent >= AscendConstants.ULTIMATE_TOTAL_REQUIREMENT;
    }

    private int countPointsInPath(AscendPlayerProgress progress, SkillTreePath path) {
        int count = 0;
        for (SkillTreeNode node : progress.getUnlockedSkillNodes()) {
            if (node.getPath() == path) {
                count++;
            }
        }
        return count;
    }

    /**
     * Checks if a skill node can be unlocked by the player.
     */
    public boolean canUnlockSkillNode(UUID playerId, SkillTreeNode node) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return false;
        }

        if (progress.hasSkillNode(node)) {
            return false;
        }

        if (progress.getAvailableSkillPoints() <= 0) {
            return false;
        }

        SkillTreeNode prereq = node.getPrerequisite();
        if (prereq != null && !progress.hasSkillNode(prereq)) {
            return false;
        }

        if (node.getPath() == SkillTreePath.HYBRID) {
            return canUnlockHybridNode(progress, node);
        } else if (node.getPath() == SkillTreePath.ULTIMATE) {
            return canUnlockUltimateNode(progress);
        }

        return true;
    }

    // ========================================
    // Skill Effect Getters
    // ========================================

    /**
     * Gets the bonus to base coin rewards from skill tree.
     */
    public double getBaseRewardBonus(UUID playerId) {
        double bonus = 0.0;
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return bonus;
        }

        if (progress.hasSkillNode(SkillTreeNode.COIN_T2_BASE_REWARD)) {
            bonus += 0.25; // +25%
        }
        if (progress.hasSkillNode(SkillTreeNode.ULTIMATE_GLOBAL_BOOST)) {
            bonus += 1.0; // +100%
        }
        return bonus;
    }

    /**
     * Gets the elevation cost multiplier (1.0 = full cost, 0.8 = 20% discount).
     * Used with the new level-based elevation system.
     */
    public double getElevationCostMultiplier(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress != null && progress.hasSkillNode(SkillTreeNode.COIN_T3_ELEVATION_COST)) {
            return 0.8; // -20%
        }
        return 1.0;
    }

    /**
     * @deprecated Use {@link #getElevationCostMultiplier(UUID)} with the new level-based system.
     */
    @Deprecated
    public int getElevationCost(UUID playerId) {
        return (int) (1000 * getElevationCostMultiplier(playerId));
    }

    /**
     * Gets the Summit cost reduction multiplier.
     */
    public double getSummitCostMultiplier(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress != null && progress.hasSkillNode(SkillTreeNode.COIN_T4_SUMMIT_COST)) {
            return 0.85; // -15% cost
        }
        return 1.0;
    }

    /**
     * Checks if auto-elevation is enabled.
     */
    public boolean hasAutoElevation(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        return progress != null && progress.hasSkillNode(SkillTreeNode.COIN_T5_AUTO_ELEVATION);
    }

    /**
     * Gets the bonus to base runner speed from skill tree.
     */
    public double getRunnerSpeedBonus(UUID playerId) {
        double bonus = 0.0;
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return bonus;
        }

        if (progress.hasSkillNode(SkillTreeNode.SPEED_T1_BASE_SPEED)) {
            bonus += 0.10; // +10%
        }
        if (progress.hasSkillNode(SkillTreeNode.ULTIMATE_GLOBAL_BOOST)) {
            bonus += 1.0; // +100%
        }
        return bonus;
    }

    /**
     * Gets the max speed upgrade level.
     */
    public int getMaxSpeedLevel(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress != null && progress.hasSkillNode(SkillTreeNode.SPEED_T2_MAX_LEVEL)) {
            return 25;
        }
        return 20;
    }

    /**
     * Gets the evolution cost multiplier.
     */
    public double getEvolutionCostMultiplier(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress != null && progress.hasSkillNode(SkillTreeNode.SPEED_T3_EVOLUTION_COST)) {
            return 0.5; // -50%
        }
        return 1.0;
    }

    /**
     * Checks if runners complete double laps.
     */
    public boolean hasDoubleLap(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        return progress != null && progress.hasSkillNode(SkillTreeNode.SPEED_T4_DOUBLE_LAP);
    }

    /**
     * Checks if evolution preserves speed level.
     */
    public boolean hasInstantEvolution(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        return progress != null && progress.hasSkillNode(SkillTreeNode.SPEED_T5_INSTANT_EVOLVE);
    }

    /**
     * Gets the bonus to manual run multiplier from skill tree.
     */
    public double getManualMultiplierBonus(UUID playerId) {
        double bonus = 0.0;
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return bonus;
        }

        if (progress.hasSkillNode(SkillTreeNode.MANUAL_T1_MULTIPLIER)) {
            bonus += 0.5; // +50%
        }
        if (progress.hasSkillNode(SkillTreeNode.ULTIMATE_GLOBAL_BOOST)) {
            bonus += 1.0; // +100%
        }
        return bonus;
    }

    /**
     * Gets the chain bonus for consecutive manual runs.
     */
    public double getChainBonus(UUID playerId, int consecutiveRuns) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null || !progress.hasSkillNode(SkillTreeNode.MANUAL_T2_CHAIN_BONUS)) {
            return 0.0;
        }
        // +10% per consecutive run, capped at +100%
        return Math.min(1.0, consecutiveRuns * 0.10);
    }

    /**
     * Gets the session first run multiplier.
     */
    public double getSessionFirstRunMultiplier(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null || !progress.hasSkillNode(SkillTreeNode.MANUAL_T3_SESSION_BONUS)) {
            return 1.0;
        }
        if (progress.isSessionFirstRunClaimed()) {
            return 1.0;
        }
        return 3.0; // 3x for first run
    }

    /**
     * Checks if manual runs boost runner speed.
     */
    public boolean hasRunnerBoost(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        return progress != null && progress.hasSkillNode(SkillTreeNode.MANUAL_T4_RUNNER_BOOST);
    }

    /**
     * Checks if personal best tracking is enabled.
     */
    public boolean hasPersonalBestTracking(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        return progress != null && progress.hasSkillNode(SkillTreeNode.MANUAL_T5_PERSONAL_BEST);
    }

    /**
     * Gets skill tree summary for a player.
     */
    public SkillTreeSummary getSkillTreeSummary(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return new SkillTreeSummary(0, 0, 0, Set.of());
        }
        return new SkillTreeSummary(
            progress.getSkillTreePoints(),
            progress.getSpentSkillPoints(),
            progress.getAvailableSkillPoints(),
            progress.getUnlockedSkillNodes()
        );
    }

    public record SkillTreeSummary(
        int totalPoints,
        int spentPoints,
        int availablePoints,
        Set<SkillTreeNode> unlockedNodes
    ) {}
}
