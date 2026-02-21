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

    // Volt (BigNumber as mantissa + exp10)
    private double voltMantissa;
    private int voltExp10;

    // Elevation
    private int elevationMultiplier;

    // Map progress
    private Map<String, MapSnapshot> maps;

    // Summit XP
    private Map<String, Double> summitXp;

    // Accumulated volt trackers (mantissa + exp10)
    private double summitAccumulatedVoltMantissa;
    private int summitAccumulatedVoltExp10;
    private double elevationAccumulatedVoltMantissa;
    private int elevationAccumulatedVoltExp10;
    private double totalVoltEarnedMantissa;
    private int totalVoltEarnedExp10;

    // Stats
    private int totalManualRuns;
    private int consecutiveManualRuns;
    private boolean autoUpgradeEnabled;
    private boolean autoEvolutionEnabled;
    private Long ascensionStartedAt;

    public static ChallengeSnapshot capture(AscendPlayerProgress progress) {
        ChallengeSnapshot snapshot = new ChallengeSnapshot();

        BigNumber volt = progress.getVolt();
        snapshot.voltMantissa = volt.getMantissa();
        snapshot.voltExp10 = volt.getExponent();

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

        BigNumber summitAccum = progress.getSummitAccumulatedVolt();
        snapshot.summitAccumulatedVoltMantissa = summitAccum.getMantissa();
        snapshot.summitAccumulatedVoltExp10 = summitAccum.getExponent();

        BigNumber elevAccum = progress.getElevationAccumulatedVolt();
        snapshot.elevationAccumulatedVoltMantissa = elevAccum.getMantissa();
        snapshot.elevationAccumulatedVoltExp10 = elevAccum.getExponent();

        BigNumber totalEarned = progress.getTotalVoltEarned();
        snapshot.totalVoltEarnedMantissa = totalEarned.getMantissa();
        snapshot.totalVoltEarnedExp10 = totalEarned.getExponent();

        snapshot.totalManualRuns = progress.getTotalManualRuns();
        snapshot.consecutiveManualRuns = progress.getConsecutiveManualRuns();
        snapshot.autoUpgradeEnabled = progress.isAutoUpgradeEnabled();
        snapshot.autoEvolutionEnabled = progress.isAutoEvolutionEnabled();
        snapshot.ascensionStartedAt = progress.getAscensionStartedAt();

        return snapshot;
    }

    public void restore(AscendPlayerProgress progress) {
        // Keep lifetime counters monotonic across challenge restore.
        // Challenge runs can increment totalManualRuns and totalVoltEarned; restoring the
        // pre-challenge snapshot must not roll these global stats backward.
        int currentManualRuns = progress.getTotalManualRuns();
        BigNumber currentTotalVoltEarned = progress.getTotalVoltEarned();

        progress.setVolt(BigNumber.of(voltMantissa, voltExp10));
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

        progress.setSummitAccumulatedVolt(BigNumber.of(summitAccumulatedVoltMantissa, summitAccumulatedVoltExp10));
        progress.setElevationAccumulatedVolt(BigNumber.of(elevationAccumulatedVoltMantissa, elevationAccumulatedVoltExp10));
        progress.setTotalVoltEarned(BigNumber.of(totalVoltEarnedMantissa, totalVoltEarnedExp10).max(currentTotalVoltEarned));

        progress.setTotalManualRuns(Math.max(totalManualRuns, currentManualRuns));
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
