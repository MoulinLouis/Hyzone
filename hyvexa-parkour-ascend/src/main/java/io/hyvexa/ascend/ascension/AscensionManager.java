package io.hyvexa.ascend.ascension;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.SkillTreeNode;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.tracker.AscendRunTracker;

import java.util.Set;
import java.util.UUID;

/**
 * Manages the Ascension prestige system.
 * Ascension grants skill tree points and resets ALL progress (coins, elevation, Summit levels).
 */
public class AscensionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AscendPlayerStore playerStore;
    private final AscendRunTracker runTracker;

    public AscensionManager(AscendPlayerStore playerStore, AscendRunTracker runTracker) {
        this.playerStore = playerStore;
        this.runTracker = runTracker;
    }

    /**
     * Checks if a player can perform an Ascension.
     */
    public boolean canAscend(UUID playerId) {
        java.math.BigDecimal coins = playerStore.getCoins(playerId);
        return coins.compareTo(AscendConstants.ASCENSION_COIN_THRESHOLD) >= 0;
    }

    /**
     * Performs an Ascension: grants 1 skill tree point, resets all progress.
     *
     * @return the new Ascension count, or -1 if insufficient coins
     */
    public int performAscension(UUID playerId) {
        java.math.BigDecimal coins = playerStore.getCoins(playerId);
        if (coins.compareTo(AscendConstants.ASCENSION_COIN_THRESHOLD) < 0) {
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

        // Cancel any active run to prevent stale completion on reset state
        runTracker.cancelRun(playerId);

        // Reset progress
        progress.setCoins(java.math.BigDecimal.ZERO);
        progress.setElevationMultiplier(1);
        progress.clearSummitLevels();

        // Reset all map progress (multipliers, unlocks, robots)
        progress.getMapProgress().clear();

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

        // Unlock the node
        progress.unlockSkillNode(node);
        playerStore.markDirty(playerId);

        LOGGER.atInfo().log("[Ascension] Player " + playerId + " unlocked skill: " + node.name());
        return true;
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

        return true;
    }

    // ========================================
    // Skill Effect Getters (all removed — return defaults)
    // ========================================

    public double getBaseRewardBonus(UUID playerId) {
        return 0.0;
    }

    public double getElevationCostMultiplier(UUID playerId) {
        return 1.0;
    }

    @Deprecated
    public int getElevationCost(UUID playerId) {
        return 1000;
    }

    public double getSummitCostMultiplier(UUID playerId) {
        return 1.0;
    }

    public boolean hasAutoElevation(UUID playerId) {
        return false;
    }

    public double getRunnerSpeedBonus(UUID playerId) {
        return 0.0;
    }

    public int getMaxSpeedLevel(UUID playerId) {
        return 20;
    }

    public double getEvolutionCostMultiplier(UUID playerId) {
        return 1.0;
    }

    public boolean hasDoubleLap(UUID playerId) {
        return false;
    }

    public boolean hasInstantEvolution(UUID playerId) {
        return false;
    }

    public double getManualMultiplierBonus(UUID playerId) {
        return 0.0;
    }

    public double getChainBonus(UUID playerId, int consecutiveRuns) {
        return 0.0;
    }

    public double getSessionFirstRunMultiplier(UUID playerId) {
        return 1.0;
    }

    public boolean hasRunnerBoost(UUID playerId) {
        return false;
    }

    public boolean hasPersonalBestTracking(UUID playerId) {
        return false;
    }

    // ========================================
    // New Skill: Auto Runners
    // ========================================

    /**
     * Checks if the player has the Auto Runners skill unlocked.
     */
    public boolean hasAutoRunners(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        return progress != null && progress.hasSkillNode(SkillTreeNode.AUTO_RUNNERS);
    }

    // ========================================
    // Skill Tree Summary
    // ========================================

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
