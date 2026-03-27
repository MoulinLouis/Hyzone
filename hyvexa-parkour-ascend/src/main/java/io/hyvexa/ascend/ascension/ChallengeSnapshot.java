package io.hyvexa.ascend.ascension;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.ascend.SummitConstants;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.GameplayState;
import io.hyvexa.common.math.BigNumber;

import java.util.HashMap;
import java.util.Map;

/**
 * Captures and restores player progress for the challenge system.
 * Designed for Gson serialization to persist in the database (crash recovery).
 */
public class ChallengeSnapshot {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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

        BigNumber volt = progress.economy().getVolt();
        snapshot.voltMantissa = volt.getMantissa();
        snapshot.voltExp10 = volt.getExponent();

        snapshot.elevationMultiplier = progress.economy().getElevationMultiplier();

        // Capture map progress
        snapshot.maps = new HashMap<>();
        for (Map.Entry<String, GameplayState.MapProgress> entry : progress.gameplay().getMapProgress().entrySet()) {
            GameplayState.MapProgress mp = entry.getValue();
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
        for (SummitConstants.SummitCategory cat : SummitConstants.SummitCategory.values()) {
            snapshot.summitXp.put(cat.name(), progress.economy().getSummitXp(cat));
        }

        BigNumber summitAccum = progress.economy().getSummitAccumulatedVolt();
        snapshot.summitAccumulatedVoltMantissa = summitAccum.getMantissa();
        snapshot.summitAccumulatedVoltExp10 = summitAccum.getExponent();

        BigNumber elevAccum = progress.economy().getElevationAccumulatedVolt();
        snapshot.elevationAccumulatedVoltMantissa = elevAccum.getMantissa();
        snapshot.elevationAccumulatedVoltExp10 = elevAccum.getExponent();

        BigNumber totalEarned = progress.economy().getTotalVoltEarned();
        snapshot.totalVoltEarnedMantissa = totalEarned.getMantissa();
        snapshot.totalVoltEarnedExp10 = totalEarned.getExponent();

        snapshot.totalManualRuns = progress.gameplay().getTotalManualRuns();
        snapshot.consecutiveManualRuns = progress.gameplay().getConsecutiveManualRuns();
        snapshot.autoUpgradeEnabled = progress.automation().isAutoUpgradeEnabled();
        snapshot.autoEvolutionEnabled = progress.automation().isAutoEvolutionEnabled();
        snapshot.ascensionStartedAt = progress.gameplay().getAscensionStartedAt();

        return snapshot;
    }

    public void restore(AscendPlayerProgress progress) {
        // Validate deserialized fields — Gson may leave invalid defaults
        if (elevationMultiplier < 1) {
            LOGGER.atWarning().log("[ChallengeSnapshot] Clamped elevationMultiplier from " + elevationMultiplier + " to 1");
            elevationMultiplier = 1;
        }
        if (voltMantissa < 0) {
            LOGGER.atWarning().log("[ChallengeSnapshot] Clamped voltMantissa from " + voltMantissa + " to 0");
            voltMantissa = 0;
        }

        // Keep lifetime counters monotonic across challenge restore.
        // Challenge runs can increment totalManualRuns and totalVoltEarned; restoring the
        // pre-challenge snapshot must not roll these global stats backward.
        int currentManualRuns = progress.gameplay().getTotalManualRuns();
        BigNumber currentTotalVoltEarned = progress.economy().getTotalVoltEarned();

        progress.economy().setVolt(BigNumber.of(voltMantissa, voltExp10));
        progress.economy().setElevationMultiplier(elevationMultiplier);

        // Restore map progress
        progress.gameplay().getMapProgress().clear();
        if (maps != null) {
            for (Map.Entry<String, MapSnapshot> entry : maps.entrySet()) {
                GameplayState.MapProgress mp = progress.gameplay().getOrCreateMapProgress(entry.getKey());
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
        progress.economy().clearSummitXp();
        if (summitXp != null) {
            for (Map.Entry<String, Double> entry : summitXp.entrySet()) {
                try {
                    SummitConstants.SummitCategory cat = SummitConstants.SummitCategory.valueOf(entry.getKey());
                    progress.economy().setSummitXp(cat, entry.getValue());
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        progress.economy().setSummitAccumulatedVolt(BigNumber.of(summitAccumulatedVoltMantissa, summitAccumulatedVoltExp10));
        progress.economy().setElevationAccumulatedVolt(BigNumber.of(elevationAccumulatedVoltMantissa, elevationAccumulatedVoltExp10));
        progress.economy().setTotalVoltEarned(BigNumber.of(totalVoltEarnedMantissa, totalVoltEarnedExp10).max(currentTotalVoltEarned));

        progress.gameplay().setTotalManualRuns(Math.max(totalManualRuns, currentManualRuns));
        progress.gameplay().setConsecutiveManualRuns(consecutiveManualRuns);
        progress.automation().setAutoUpgradeEnabled(autoUpgradeEnabled);
        progress.automation().setAutoEvolutionEnabled(autoEvolutionEnabled);
        progress.gameplay().setAscensionStartedAt(ascensionStartedAt);

        // Clear challenge state
        progress.gameplay().setActiveChallenge(null);
        progress.gameplay().setChallengeStartedAtMs(0);
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
