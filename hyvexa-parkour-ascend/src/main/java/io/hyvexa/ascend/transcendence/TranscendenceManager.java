package io.hyvexa.ascend.transcendence;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.math.BigNumber;

import java.util.HashMap;
import java.util.Map;
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

    public TranscendenceManager(AscendPlayerStore playerStore, AscendRunTracker runTracker) {
        this.playerStore = playerStore;
        this.runTracker = runTracker;
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
        return progress.getVolt().gte(AscendConstants.TRANSCENDENCE_VOLT_THRESHOLD)
            && progress.isBreakAscensionEnabled()
            && progress.hasAllChallengeRewards();
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
        int newCount = progress.incrementTranscendenceCount();

        // Cancel any active run
        runTracker.cancelRun(playerId);

        // === Reset all prestige state ===

        // Volt
        progress.setVolt(BigNumber.ZERO);
        progress.setSummitAccumulatedVolt(BigNumber.ZERO);
        progress.setElevationAccumulatedVolt(BigNumber.ZERO);

        // Elevation
        progress.setElevationMultiplier(1);

        // Ascension
        progress.setAscensionCount(0);
        progress.setSkillTreePoints(0);
        progress.setUnlockedSkillNodes(null);

        // Summit
        progress.clearSummitXp();

        // Challenges (permanent rewards wiped — this is what makes transcendence special)
        progress.setCompletedChallengeRewards(null);

        // Automation toggles
        progress.setBreakAscensionEnabled(false);
        progress.setAutoUpgradeEnabled(false);
        progress.setAutoEvolutionEnabled(false);
        progress.setAutoElevationEnabled(false);
        progress.setAutoElevationTargetIndex(0);
        progress.setAutoSummitEnabled(false);

        // Ascension timer
        progress.setFastestAscensionMs(null);
        progress.setAscensionStartedAt(System.currentTimeMillis());

        // === Map progress reset (PB-preserving) ===
        Map<String, Long> savedPBs = new HashMap<>();
        for (Map.Entry<String, AscendPlayerProgress.MapProgress> entry : progress.getMapProgress().entrySet()) {
            Long bestTime = entry.getValue().getBestTimeMs();
            if (bestTime != null) {
                savedPBs.put(entry.getKey(), bestTime);
            }
        }
        progress.getMapProgress().clear();
        for (Map.Entry<String, Long> entry : savedPBs.entrySet()) {
            AscendPlayerProgress.MapProgress mp = progress.getOrCreateMapProgress(entry.getKey());
            mp.setBestTimeMs(entry.getValue());
        }

        // === DB cleanup ===
        // markResetPending handles: maps, summit, skills, achievements
        playerStore.markResetPending(playerId);
        // markTranscendenceResetPending handles: challenge records
        playerStore.markTranscendenceResetPending(playerId);

        playerStore.markDirty(playerId);
        playerStore.flushPendingSave();

        LOGGER.atInfo().log("[Transcendence] Player " + playerId + " transcended! Count: " + newCount);

        try {
            io.hyvexa.core.analytics.AnalyticsStore.getInstance().logEvent(playerId, "ascend_transcendence",
                    "{\"count\":" + newCount + "}");
        } catch (Exception e) { /* silent */ }

        return newCount;
    }

    /**
     * Whether the player has unlocked map 6 via transcendence milestone 1.
     */
    public boolean hasMap6Unlocked(UUID playerId) {
        return playerStore.getTranscendenceCount(playerId) >= 1;
    }
}
