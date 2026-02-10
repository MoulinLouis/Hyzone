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
 * Ascension grants skill tree points and resets ALL progress (vexa, elevation, Summit levels).
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
        io.hyvexa.common.math.BigNumber vexa = playerStore.getVexa(playerId);
        return vexa.gte(AscendConstants.ASCENSION_VEXA_THRESHOLD);
    }

    /**
     * Performs an Ascension: grants 1 skill tree point, resets all progress.
     *
     * @return the new Ascension count, or -1 if insufficient vexa
     */
    public int performAscension(UUID playerId) {
        io.hyvexa.common.math.BigNumber vexa = playerStore.getVexa(playerId);
        if (vexa.lt(AscendConstants.ASCENSION_VEXA_THRESHOLD)) {
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
        progress.setVexa(io.hyvexa.common.math.BigNumber.ZERO);
        progress.setElevationMultiplier(1);
        progress.setSummitAccumulatedVexa(io.hyvexa.common.math.BigNumber.ZERO);
        progress.setElevationAccumulatedVexa(io.hyvexa.common.math.BigNumber.ZERO);
        progress.clearSummitXp();

        // Reset all map progress (multipliers, unlocks, robots)
        progress.getMapProgress().clear();

        playerStore.markDirty(playerId);

        // Flush immediately to prevent data loss on crash
        playerStore.flushPendingSave();

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

        // Check prerequisites (OR logic)
        if (!node.hasPrerequisitesSatisfied(progress.getUnlockedSkillNodes())) {
            return false;
        }

        // Unlock the node
        progress.unlockSkillNode(node);

        // Auto-enable automation toggles when their skills are unlocked
        if (node == SkillTreeNode.AUTO_RUNNERS) {
            playerStore.setAutoUpgradeEnabled(playerId, true);
        } else if (node == SkillTreeNode.AUTO_EVOLUTION) {
            playerStore.setAutoEvolutionEnabled(playerId, true);
        }

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

        if (!node.hasPrerequisitesSatisfied(progress.getUnlockedSkillNodes())) {
            return false;
        }

        return true;
    }

    // Skill Node Accessors

    public boolean hasAutoRunners(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.AUTO_RUNNERS);
    }

    public boolean hasAutoEvolution(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.AUTO_EVOLUTION);
    }

    public boolean hasPersistence(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.PERSISTENCE);
    }

    public boolean hasRunnerSpeedBoost(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.RUNNER_SPEED);
    }

    public boolean hasOfflineBoost(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.OFFLINE_BOOST);
    }

    public boolean hasSummitMemory(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.SUMMIT_MEMORY);
    }

    public boolean hasEvolutionPowerBoost(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.EVOLUTION_POWER);
    }

    public boolean hasAscensionChallenges(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.ASCENSION_CHALLENGES);
    }

    private boolean hasSkillNode(UUID playerId, SkillTreeNode node) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        return progress != null && progress.hasSkillNode(node);
    }

    // Skill Tree Summary

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
