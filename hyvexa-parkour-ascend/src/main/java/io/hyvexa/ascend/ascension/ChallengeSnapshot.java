package io.hyvexa.ascend.ascension;

import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.common.math.BigNumber;

import java.util.HashMap;
import java.util.Map;

/**
 * Captures and restores player progress for the challenge system.
 * Designed for Gson serialization to persist in the database (crash recovery).
 */
public class ChallengeSnapshot {

    // Vexa (BigNumber as mantissa + exp10)
    private double vexaMantissa;
    private int vexaExp10;

    // Elevation
    private int elevationMultiplier;

    // Map progress
    private Map<String, MapSnapshot> maps;

    // Summit XP
    private Map<String, Double> summitXp;

    // Accumulated vexa trackers (mantissa + exp10)
    private double summitAccumulatedVexaMantissa;
    private int summitAccumulatedVexaExp10;
    private double elevationAccumulatedVexaMantissa;
    private int elevationAccumulatedVexaExp10;
    private double totalVexaEarnedMantissa;
    private int totalVexaEarnedExp10;

    // Compound Elevation
    private double compoundedElevation;
    private int cycleLevel;

    // Stats
    private int totalManualRuns;
    private int consecutiveManualRuns;
    private boolean autoUpgradeEnabled;
    private boolean autoEvolutionEnabled;
    private Long ascensionStartedAt;

    public static ChallengeSnapshot capture(AscendPlayerProgress progress) {
        ChallengeSnapshot snapshot = new ChallengeSnapshot();

        BigNumber vexa = progress.getVexa();
        snapshot.vexaMantissa = vexa.getMantissa();
        snapshot.vexaExp10 = vexa.getExponent();

        snapshot.elevationMultiplier = progress.getElevationMultiplier();

        // Capture map progress
        snapshot.maps = new HashMap<>();
        for (Map.Entry<String, AscendPlayerProgress.MapProgress> entry : progress.getMapProgress().entrySet()) {
            AscendPlayerProgress.MapProgress mp = entry.getValue();
            MapSnapshot ms = new MapSnapshot();
            ms.unlocked = mp.isUnlocked();
            ms.completedManually = mp.isCompletedManually();
            ms.hasRobot = mp.hasRobot();
            ms.robotSpeedLevel = mp.getRobotSpeedLevel();
            ms.robotStars = mp.getRobotStars();
            ms.multiplierMantissa = mp.getMultiplier().getMantissa();
            ms.multiplierExp10 = mp.getMultiplier().getExponent();
            ms.bestTimeMs = mp.getBestTimeMs();
            snapshot.maps.put(entry.getKey(), ms);
        }

        // Capture summit XP
        snapshot.summitXp = new HashMap<>();
        for (AscendConstants.SummitCategory cat : AscendConstants.SummitCategory.values()) {
            snapshot.summitXp.put(cat.name(), progress.getSummitXp(cat));
        }

        BigNumber summitAccum = progress.getSummitAccumulatedVexa();
        snapshot.summitAccumulatedVexaMantissa = summitAccum.getMantissa();
        snapshot.summitAccumulatedVexaExp10 = summitAccum.getExponent();

        BigNumber elevAccum = progress.getElevationAccumulatedVexa();
        snapshot.elevationAccumulatedVexaMantissa = elevAccum.getMantissa();
        snapshot.elevationAccumulatedVexaExp10 = elevAccum.getExponent();

        BigNumber totalEarned = progress.getTotalVexaEarned();
        snapshot.totalVexaEarnedMantissa = totalEarned.getMantissa();
        snapshot.totalVexaEarnedExp10 = totalEarned.getExponent();

        snapshot.compoundedElevation = progress.getCompoundedElevation();
        snapshot.cycleLevel = progress.getCycleLevel();

        snapshot.totalManualRuns = progress.getTotalManualRuns();
        snapshot.consecutiveManualRuns = progress.getConsecutiveManualRuns();
        snapshot.autoUpgradeEnabled = progress.isAutoUpgradeEnabled();
        snapshot.autoEvolutionEnabled = progress.isAutoEvolutionEnabled();
        snapshot.ascensionStartedAt = progress.getAscensionStartedAt();

        return snapshot;
    }

    public void restore(AscendPlayerProgress progress) {
        progress.setVexa(BigNumber.of(vexaMantissa, vexaExp10));
        progress.setElevationMultiplier(elevationMultiplier);

        // Restore map progress
        progress.getMapProgress().clear();
        if (maps != null) {
            for (Map.Entry<String, MapSnapshot> entry : maps.entrySet()) {
                AscendPlayerProgress.MapProgress mp = progress.getOrCreateMapProgress(entry.getKey());
                MapSnapshot ms = entry.getValue();
                mp.setUnlocked(ms.unlocked);
                mp.setCompletedManually(ms.completedManually);
                mp.setHasRobot(ms.hasRobot);
                mp.setRobotSpeedLevel(ms.robotSpeedLevel);
                mp.setRobotStars(ms.robotStars);
                mp.setMultiplier(BigNumber.of(ms.multiplierMantissa, ms.multiplierExp10));
                mp.setBestTimeMs(ms.bestTimeMs);
            }
        }

        // Restore summit XP
        progress.clearSummitXp();
        if (summitXp != null) {
            for (Map.Entry<String, Double> entry : summitXp.entrySet()) {
                try {
                    AscendConstants.SummitCategory cat = AscendConstants.SummitCategory.valueOf(entry.getKey());
                    progress.setSummitXp(cat, entry.getValue());
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        progress.setSummitAccumulatedVexa(BigNumber.of(summitAccumulatedVexaMantissa, summitAccumulatedVexaExp10));
        progress.setElevationAccumulatedVexa(BigNumber.of(elevationAccumulatedVexaMantissa, elevationAccumulatedVexaExp10));
        progress.setTotalVexaEarned(BigNumber.of(totalVexaEarnedMantissa, totalVexaEarnedExp10));

        progress.setCompoundedElevation(compoundedElevation);
        progress.setCycleLevel(cycleLevel);

        progress.setTotalManualRuns(totalManualRuns);
        progress.setConsecutiveManualRuns(consecutiveManualRuns);
        progress.setAutoUpgradeEnabled(autoUpgradeEnabled);
        progress.setAutoEvolutionEnabled(autoEvolutionEnabled);
        progress.setAscensionStartedAt(ascensionStartedAt);

        // Clear challenge state
        progress.setActiveChallenge(null);
        progress.setChallengeStartedAtMs(0);
    }

    public static class MapSnapshot {
        boolean unlocked;
        boolean completedManually;
        boolean hasRobot;
        int robotSpeedLevel;
        int robotStars;
        double multiplierMantissa;
        int multiplierExp10;
        Long bestTimeMs;
    }
}
