package io.hyvexa.ascend.robot;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.command.AscendCommand;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AutomationConfig;
import io.hyvexa.ascend.data.GameplayState;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.ghost.GhostRecording;
import io.hyvexa.common.math.BigNumber;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

class AutoRunnerUpgradeEngine {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final RobotManager manager;
    private final Map<UUID, Long> lastAutoElevationMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAutoSummitMs = new ConcurrentHashMap<>();

    AutoRunnerUpgradeEngine(RobotManager manager) {
        this.manager = manager;
    }

    // Auto Runner Upgrades (Skill Tree)

    void performAutoRunnerUpgrades() {
        AscensionManager ascensionManager = manager.getAscensionManager();
        if (ascensionManager == null) return;

        for (UUID playerId : manager.getOnlinePlayers()) {
            if (!ascensionManager.hasAutoRunners(playerId)) continue;
            autoUpgradeRunners(playerId);
        }
    }

    void autoUpgradeRunners(UUID playerId) {
        AscendPlayerProgress progress = manager.getPlayerStore().getPlayer(playerId);
        if (progress == null) return;
        if (!progress.automation().isAutoUpgradeEnabled()) return;

        List<AscendMap> maps = manager.getMapStore().listMapsSorted();

        // First priority: buy runners on unlocked maps that have been completed (free)
        for (AscendMap map : maps) {
            GameplayState.MapProgress mp = progress.gameplay().getMapProgress().get(map.getId());
            if (mp == null || !mp.isUnlocked() || mp.hasRobot()) continue;

            // Accept ghost recording OR best time as proof of completion
            GhostRecording ghost = manager.getGhostStore().getRecording(playerId, map.getId());
            if (ghost == null && mp.getBestTimeMs() == null) continue;

            manager.getPlayerStore().runners().setHasRobot(playerId, map.getId(), true);
            return; // One action per call for smooth visual
        }

        // Auto-evolve eligible maps (free, all at once — each map independent of others)
        // Runs before speed upgrades so a map at max level evolves immediately,
        // even while other maps still have affordable speed upgrades.
        if (progress.automation().isAutoEvolutionEnabled()) {
            AscensionManager am = manager.getAscensionManager();
            if (am != null && am.hasAutoEvolution(playerId)) {
                boolean anyEvolved = false;
                for (AscendMap map : maps) {
                    GameplayState.MapProgress mp = progress.gameplay().getMapProgress().get(map.getId());
                    if (mp == null || !mp.hasRobot()) continue;
                    if (mp.getRobotSpeedLevel() >= AscendConstants.MAX_SPEED_LEVEL
                            && mp.getRobotStars() < AscendConstants.MAX_ROBOT_STARS) {
                        int newStars = manager.getPlayerStore().runners().evolveRobot(playerId, map.getId());
                        manager.respawnRobot(playerId, map.getId(), newStars);
                        anyEvolved = true;
                    }
                }
                if (anyEvolved && manager.getAchievementManager() != null) {
                    manager.getAchievementManager().checkAndUnlockAchievements(playerId, null);
                }
            }
        }

        // Speed upgrade: find cheapest across all maps (one per call for smooth visual)
        BigNumber volt = progress.economy().getVolt();
        String cheapestMapId = null;
        BigNumber cheapestCost = null;

        for (AscendMap map : maps) {
            GameplayState.MapProgress mp = progress.gameplay().getMapProgress().get(map.getId());
            if (mp == null || !mp.hasRobot()) continue;

            int speedLevel = mp.getRobotSpeedLevel();
            if (speedLevel >= AscendConstants.MAX_SPEED_LEVEL) continue;

            BigNumber cost = AscendConstants.getRunnerUpgradeCost(
                speedLevel, map.getDisplayOrder(), mp.getRobotStars());

            if (cheapestCost == null || cost.lt(cheapestCost)) {
                cheapestCost = cost;
                cheapestMapId = map.getId();
            }
        }

        if (cheapestMapId != null && cheapestCost != null && volt.gte(cheapestCost)) {
            if (!manager.getPlayerStore().volt().atomicSpendVolt(playerId, cheapestCost)) return;
            manager.getPlayerStore().runners().incrementRobotSpeedLevel(playerId, cheapestMapId);
            manager.getPlayerStore().runners().checkAndUnlockEligibleMaps(playerId, manager.getMapStore());
        }
    }

    // Auto-Elevation

    void performAutoElevation(long now) {
        AscensionManager ascensionMgr = manager.getAscensionManager();
        AscendRunTracker runTracker = manager.getRunTracker();
        ChallengeManager challengeMgr = manager.getChallengeManager();
        for (UUID playerId : manager.getOnlinePlayers()) {
            if (ascensionMgr == null || !ascensionMgr.hasAutoElevation(playerId)) continue;
            // Skip if player is actively playing a map — don't reset progress mid-run
            if (runTracker != null && runTracker.getActiveMapId(playerId) != null) continue;
            // Skip if elevation is blocked by active challenge
            if (challengeMgr != null && challengeMgr.isElevationBlocked(playerId)) continue;
            try {
                autoElevatePlayer(playerId, now);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Auto-elevation failed for " + playerId);
            }
        }
    }

    void autoElevatePlayer(UUID playerId, long now) {
        AscendPlayerProgress progress = manager.getPlayerStore().getPlayer(playerId);
        if (progress == null) return;
        if (!progress.automation().isAutoElevationEnabled()) return;

        List<Long> targets = progress.automation().getAutoElevationTargets();
        int targetIndex = progress.automation().getAutoElevationTargetIndex();

        // Skip targets already surpassed by current multiplier
        int currentLevel = progress.economy().getElevationMultiplier();
        long currentActualMultiplier = Math.round(AscendConstants.getElevationMultiplier(currentLevel));
        while (targetIndex < targets.size() && targets.get(targetIndex) <= currentActualMultiplier) {
            targetIndex++;
        }
        if (targetIndex != progress.automation().getAutoElevationTargetIndex()) {
            manager.getPlayerStore().settings().setAutoElevationTargetIndex(playerId, targetIndex);
        }

        if (targets.isEmpty() || targetIndex >= targets.size()) return;

        // Check timer
        int timerSeconds = progress.automation().getAutoElevationTimerSeconds();
        if (timerSeconds > 0) {
            Long lastMs = lastAutoElevationMs.get(playerId);
            if (lastMs != null && (now - lastMs) < (long) timerSeconds * 1000L) return;
        }

        // Calculate purchasable levels
        BigNumber accumulatedVolt = progress.economy().getElevationAccumulatedVolt();
        AscendConstants.ElevationPurchaseResult result = AscendConstants.calculateElevationPurchase(currentLevel, accumulatedVolt, BigNumber.ONE);
        if (result.levels <= 0) return;

        int newLevel = currentLevel + result.levels;
        long newMultiplier = Math.round(AscendConstants.getElevationMultiplier(newLevel));
        long nextTarget = targets.get(targetIndex);
        if (newMultiplier < nextTarget) return;

        // Execute elevation — reset progress first, then despawn robots.
        // resetProgressForElevation sets hasRobot=false, so refreshRobots() will clean up stale
        // robots on the next cycle. We despawn after to accelerate cleanup, but the critical part
        // is that the reset happens first — if despawn fails or throws, robots won't be re-created
        // because hasRobot is already false.
        manager.getPlayerStore().progression().atomicSetElevationAndResetVolt(playerId, newLevel);

        // Get first map ID for reset
        List<AscendMap> maps = manager.getMapStore().listMapsSorted();
        String firstMapId = maps.isEmpty() ? null : maps.get(0).getId();

        manager.getPlayerStore().resetProgressForElevation(playerId, firstMapId);

        // Now despawn robot NPCs (safe — even if this fails, hasRobot=false prevents re-creation)
        manager.despawnRobotsForPlayer(playerId);

        // Send chat message
        PlayerRef playerRef = manager.getPlayerRef(playerId);
        if (playerRef != null) {
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                "[Auto-Elevation] Elevated to x" + newMultiplier)
                .color(io.hyvexa.common.util.SystemMessageUtils.SUCCESS));
        }

        // Advance targetIndex past all surpassed targets
        int newIndex = targetIndex;
        while (newIndex < targets.size() && newMultiplier >= targets.get(newIndex)) {
            newIndex++;
        }
        manager.getPlayerStore().settings().setAutoElevationTargetIndex(playerId, newIndex);

        lastAutoElevationMs.put(playerId, now);
        manager.getPlayerStore().markDirty(playerId);

        // Close the player's ascend page so they see fresh state on reopen
        AscendCommand.forceCloseActivePage(playerId);
    }

    // Auto-Summit

    void performAutoSummit(long now) {
        AscensionManager ascensionMgr = manager.getAscensionManager();
        AscendRunTracker runTracker = manager.getRunTracker();
        ChallengeManager challengeMgr = manager.getChallengeManager();
        for (UUID playerId : manager.getOnlinePlayers()) {
            if (ascensionMgr == null || !ascensionMgr.hasAutoSummit(playerId)) continue;
            // Skip if player is actively playing a map — don't reset progress mid-run
            if (runTracker != null && runTracker.getActiveMapId(playerId) != null) continue;
            // Skip if all summit is blocked by active challenge
            if (challengeMgr != null && challengeMgr.isAllSummitBlocked(playerId)) continue;
            try {
                autoSummitPlayer(playerId, now);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Auto-summit failed for " + playerId);
            }
        }
    }

    void autoSummitPlayer(UUID playerId, long now) {
        AscendPlayerProgress progress = manager.getPlayerStore().getPlayer(playerId);
        if (progress == null) return;
        if (!progress.automation().isAutoSummitEnabled()) return;

        SummitManager summitManager = manager.getSummitManager();
        if (summitManager == null) return;

        if (!summitManager.canSummit(playerId)) return;

        // Check timer
        int timerSeconds = progress.automation().getAutoSummitTimerSeconds();
        if (timerSeconds > 0) {
            Long lastMs = lastAutoSummitMs.get(playerId);
            if (lastMs != null && (now - lastMs) < (long) timerSeconds * 1000L) return;
        }

        List<AutomationConfig.AutoSummitCategoryConfig> config = progress.automation().getAutoSummitConfig();
        AscendConstants.SummitCategory[] categories = AscendConstants.SummitCategory.values();

        // Target-based: iterate all categories and summit the first one that can reach its target
        for (int i = 0; i < categories.length; i++) {
            if (i >= config.size()) continue;

            AutomationConfig.AutoSummitCategoryConfig catConfig = config.get(i);
            if (!catConfig.isEnabled()) continue;
            int targetLevel = catConfig.getTargetLevel();
            if (targetLevel <= 0) continue;

            AscendConstants.SummitCategory category = categories[i];
            int currentLevel = manager.getPlayerStore().progression().getSummitLevel(playerId, category);

            // Skip if target already reached
            if (currentLevel >= targetLevel) continue;

            // Preview the summit
            SummitManager.SummitPreview preview = summitManager.previewSummit(playerId, category);
            if (!preview.hasGain()) continue;

            // Only summit if projected level reaches the target
            if (preview.newLevel() < targetLevel) continue;

            // Perform summit — this calls resetProgressForSummit which sets hasRobot=false,
            // so refreshRobots() won't re-create them. We despawn NPCs after to accelerate cleanup.
            // Critical: reset BEFORE despawn — if despawn throws, robots won't be re-created
            // because hasRobot is already false (prevents infinite despawn-recreate loop).
            SummitManager.SummitResult result = summitManager.performSummit(playerId, category);
            if (!result.succeeded()) continue;

            // Now despawn robot NPCs (safe — even if this fails, hasRobot=false prevents re-creation)
            manager.despawnRobotsForPlayer(playerId);

            // Send chat message
            PlayerRef playerRef = manager.getPlayerRef(playerId);
            if (playerRef != null) {
                playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                    "[Auto-Summit] " + category.getDisplayName() + " Lv " + result.newLevel())
                    .color(io.hyvexa.common.util.SystemMessageUtils.SUCCESS));
            }

            lastAutoSummitMs.put(playerId, now);

            // Close the player's ascend page so they see fresh state on reopen
            AscendCommand.forceCloseActivePage(playerId);

            return; // One summit per tick for smooth visual
        }
    }

    void onPlayerLeave(UUID playerId) {
        lastAutoElevationMs.remove(playerId);
        lastAutoSummitMs.remove(playerId);
    }
}
