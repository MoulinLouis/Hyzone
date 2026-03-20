package io.hyvexa.ascend.ascension;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.SkillTreeNode;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.math.BigNumber;

import java.util.Set;
import java.util.UUID;

/**
 * Manages the Ascension prestige system.
 * Ascension grants skill tree points and resets progress (volt, elevation, Summit levels) but preserves map PBs.
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
        BigNumber volt = playerStore.getVolt(playerId);
        return volt.gte(AscendConstants.ASCENSION_VOLT_THRESHOLD);
    }

    /**
     * Performs an Ascension: grants AP (1 + completed challenges), resets progress (preserves map PBs).
     *
     * @return the new Ascension count, or -1 if insufficient volt
     */
    public int performAscension(UUID playerId) {
        BigNumber volt = playerStore.getVolt(playerId);
        if (volt.lt(AscendConstants.ASCENSION_VOLT_THRESHOLD)) {
            return -1;
        }

        AscendPlayerProgress progress = playerStore.getOrCreatePlayer(playerId);

        // Grant skill tree points (1 base + 1 per completed challenge)
        int apGained = 1 + progress.getCompletedChallengeCount();
        int newPoints = progress.addSkillTreePoints(apGained);
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
        progress.setVolt(BigNumber.ZERO);
        progress.setElevationMultiplier(1);
        progress.setAutoElevationTargetIndex(0);
        progress.setSummitAccumulatedVolt(BigNumber.ZERO);
        progress.setElevationAccumulatedVolt(BigNumber.ZERO);

        progress.clearSummitXp();

        // Mark for full child-row deletion so stale DB rows are purged
        playerStore.markResetPending(playerId);

        // Reset all map progress (multipliers, unlocks, robots) while preserving PBs
        progress.resetMapProgressPreservingPBs();

        playerStore.markDirty(playerId);

        // Flush immediately to prevent data loss on crash
        playerStore.flushPendingSave();

        LOGGER.atInfo().log("[Ascension] Player " + playerId + " ascended! Count: " + newAscensionCount
            + ", AP gained: " + apGained + ", total AP: " + newPoints);

        try {
            io.hyvexa.core.analytics.AnalyticsStore.getInstance().logEvent(playerId, "ascend_ascension",
                    "{\"count\":" + newAscensionCount + "}");
        } catch (Exception e) { /* silent */ }

        return newAscensionCount;
    }

    /**
     * Attempts to unlock a skill tree node for a player.
     *
     * @return true if successfully unlocked, false otherwise
     */
    public boolean tryUnlockSkillNode(UUID playerId, SkillTreeNode node) {
        if (!canUnlockSkillNode(playerId, node)) {
            return false;
        }

        AscendPlayerProgress progress = playerStore.getPlayer(playerId);

        // Unlock the node
        progress.unlockSkillNode(node);

        // Auto-enable automation toggles when their skills are unlocked
        switch (node) {
            case AUTO_RUNNERS -> playerStore.setAutoUpgradeEnabled(playerId, true);
            case AUTO_EVOLUTION -> playerStore.setAutoEvolutionEnabled(playerId, true);
            case AUTO_ELEVATION -> playerStore.setAutoElevationEnabled(playerId, true);
            case AUTO_SUMMIT -> playerStore.setAutoSummitEnabled(playerId, true);
            case AUTO_ASCEND -> playerStore.setAutoAscendEnabled(playerId, true);
            default -> {}
        }

        playerStore.markDirty(playerId);

        LOGGER.atInfo().log("[Ascension] Player " + playerId + " unlocked skill: " + node.name());
        return true;
    }

    public boolean canUnlockSkillNode(UUID playerId, SkillTreeNode node) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return false;
        }

        if (progress.hasSkillNode(node)) {
            return false;
        }

        if (progress.getAvailableSkillPoints() < node.getCost()) {
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

    public boolean hasAutoElevation(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.AUTO_ELEVATION);
    }

    public boolean hasAutoSummit(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.AUTO_SUMMIT);
    }

    public boolean hasRunnerSpeedBoost(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.RUNNER_SPEED);
    }

    public boolean hasRunnerSpeedBoost2(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.RUNNER_SPEED_2);
    }

    public boolean hasEvolutionPowerBoost(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.EVOLUTION_POWER);
    }

    public boolean hasAscensionChallenges(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.ASCENSION_CHALLENGES);
    }

    public boolean hasMomentumSurge(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.MOMENTUM_SURGE);
    }

    public boolean hasMultiplierBoost(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.MULTIPLIER_BOOST);
    }

    public boolean hasRunnerSpeedBoost3(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.RUNNER_SPEED_3);
    }

    public boolean hasEvolutionPowerBoost2(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.EVOLUTION_POWER_2);
    }

    public boolean hasMomentumEndurance(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.MOMENTUM_ENDURANCE);
    }

    public boolean hasRunnerSpeedBoost4(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.RUNNER_SPEED_4);
    }

    public boolean hasEvolutionPowerBoost3(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.EVOLUTION_POWER_3);
    }

    public boolean hasMomentumMastery(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.MOMENTUM_MASTERY);
    }

    public boolean hasMultiplierBoost2(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.MULTIPLIER_BOOST_2);
    }

    public boolean hasAutoAscend(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.AUTO_ASCEND);
    }

    public boolean hasRunnerSpeedBoost5(UUID playerId) {
        return hasSkillNode(playerId, SkillTreeNode.RUNNER_SPEED_5);
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
