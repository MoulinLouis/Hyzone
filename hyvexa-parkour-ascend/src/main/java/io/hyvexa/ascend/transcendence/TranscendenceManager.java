package io.hyvexa.ascend.transcendence;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.ascend.AscensionConstants;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.core.analytics.PlayerAnalytics;

import java.util.UUID;

/**
 * Manages the Transcendence system (4th prestige layer).
 * Transcendence is a full reset (including skill tree and challenges)
 * that increments a permanent counter. Milestone 1 unlocks map 6.
 */
public class TranscendenceManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AscendPlayerStore playerStore;
    private final AscendRunTracker runTracker;
    private final PlayerAnalytics analytics;

    public TranscendenceManager(AscendPlayerStore playerStore, AscendRunTracker runTracker, PlayerAnalytics analytics) {
        this.playerStore = playerStore;
        this.runTracker = runTracker;
        this.analytics = analytics;
    }

    /**
     * Checks if a player is eligible to Transcend.
     * Requires: volt >= 1e100, BREAK_ASCENSION active, all challenges completed.
     */
    public boolean isEligible(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return false;
        }
        return progress.economy().getVolt().gte(AscensionConstants.TRANSCENDENCE_VOLT_THRESHOLD)
            && progress.automation().isBreakAscensionEnabled()
            && progress.gameplay().hasAllChallengeRewards();
    }

    /**
     * Performs a Transcendence: full reset (including skill tree and challenges)
     * while preserving best times, achievements, transcendence count, and lifetime stats.
     *
     * @return the new transcendence count, or -1 if not eligible
     */
    public int performTranscendence(UUID playerId) {
        if (!isEligible(playerId)) {
            return -1;
        }

        AscendPlayerProgress progress = playerStore.getOrCreatePlayer(playerId);
        int newCount = progress.gameplay().incrementTranscendenceCount();

        // Cancel any active run
        runTracker.cancelRun(playerId);

        // === Reset all prestige state ===

        // Volt
        progress.economy().setVolt(BigNumber.ZERO);
        progress.economy().setSummitAccumulatedVolt(BigNumber.ZERO);
        progress.economy().setElevationAccumulatedVolt(BigNumber.ZERO);

        // Elevation
        progress.economy().setElevationMultiplier(1);

        // Ascension
        progress.gameplay().setAscensionCount(0);
        progress.gameplay().setSkillTreePoints(0);
        progress.gameplay().setUnlockedSkillNodes(null);

        // Summit
        progress.economy().clearSummitXp();

        // Challenges (permanent rewards wiped — this is what makes transcendence special)
        progress.gameplay().setCompletedChallengeRewards(null);

        // Automation toggles
        progress.automation().setBreakAscensionEnabled(false);
        progress.automation().setAutoUpgradeEnabled(false);
        progress.automation().setAutoEvolutionEnabled(false);
        progress.automation().setAutoElevationEnabled(false);
        progress.automation().setAutoElevationTargetIndex(0);
        progress.automation().setAutoSummitEnabled(false);

        // Ascension timer
        progress.gameplay().setFastestAscensionMs(null);
        progress.gameplay().setAscensionStartedAt(System.currentTimeMillis());

        // === Map progress reset (PB-preserving) ===
        progress.gameplay().resetMapProgressPreservingPBs();

        // === DB cleanup ===
        // markResetPending handles: maps, summit, skills, achievements
        playerStore.markResetPending(playerId);
        // markTranscendenceResetPending handles: challenge records
        playerStore.markTranscendenceResetPending(playerId);

        playerStore.markDirty(playerId);
        playerStore.flushPendingSave();

        LOGGER.atInfo().log("[Transcendence] Player " + playerId + " transcended! Count: " + newCount);

        try {
            analytics.logEvent(playerId, "ascend_transcendence",
                    "{\"count\":" + newCount + "}");
        } catch (Exception e) { /* silent */ }

        return newCount;
    }

    /**
     * Whether the player has unlocked map 6 via transcendence milestone 1.
     */
    public boolean hasMap6Unlocked(UUID playerId) {
        return playerStore.progression().getTranscendenceCount(playerId) >= 1;
    }
}
