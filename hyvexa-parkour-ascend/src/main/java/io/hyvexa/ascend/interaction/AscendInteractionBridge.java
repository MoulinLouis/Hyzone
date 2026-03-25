package io.hyvexa.ascend.interaction;

import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerEventHandler;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.AscendSettingsStore;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.mine.MineGateChecker;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.egg.EggOpenService;
import io.hyvexa.ascend.mine.robot.MineRobotManager;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.robot.RunnerSpeedCalculator;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.transcendence.TranscendenceManager;
import io.hyvexa.ascend.tutorial.TutorialTriggerService;
import io.hyvexa.ascend.ui.AscendMenuNavigator;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.core.analytics.PlayerAnalytics;

/**
 * Narrow static bootstrap for codec-instantiated interactions.
 * Hytale requires no-arg handlers, so these interactions cannot receive
 * constructor injection directly from the plugin composition root.
 */
public final class AscendInteractionBridge {

    private static volatile Services services;

    private AscendInteractionBridge() {}

    public static void configure(Services services) {
        AscendInteractionBridge.services = services;
    }

    public static Services get() {
        return services;
    }

    public static void clear() {
        services = null;
    }

    public record Services(
        AscendMapStore mapStore,
        AscendPlayerStore playerStore,
        AscendSettingsStore settingsStore,
        GhostStore ghostStore,
        AscendRunTracker runTracker,
        AscendHudManager hudManager,
        RobotManager robotManager,
        AscensionManager ascensionManager,
        TranscendenceManager transcendenceManager,
        SummitManager summitManager,
        ChallengeManager challengeManager,
        AchievementManager achievementManager,
        TutorialTriggerService tutorialTriggerService,
        RunnerSpeedCalculator runnerSpeedCalculator,
        EggOpenService eggOpenService,
        MinePlayerStore minePlayerStore,
        MineConfigStore mineConfigStore,
        MineAchievementTracker mineAchievementTracker,
        MineRobotManager mineRobotManager,
        MineGateChecker mineGateChecker,
        AscendMenuNavigator menuNavigator,
        PlayerAnalytics analytics,
        AscendPlayerEventHandler eventHandler
    ) {}
}
