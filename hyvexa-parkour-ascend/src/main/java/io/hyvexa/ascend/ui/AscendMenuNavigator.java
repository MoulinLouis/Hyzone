package io.hyvexa.ascend.ui;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.robot.RunnerSpeedCalculator;
import io.hyvexa.common.ghost.GhostStore;

import javax.annotation.Nonnull;

public class AscendMenuNavigator {

    private final AscendPlayerStore playerStore;
    private final AscendMapStore mapStore;
    private final GhostStore ghostStore;
    private final RunnerSpeedCalculator speedCalculator;
    private final AchievementManager achievementManager;
    private final RobotManager robotManager;
    private final AscendHudManager hudManager;

    public AscendMenuNavigator(AscendPlayerStore playerStore,
                               AscendMapStore mapStore,
                               GhostStore ghostStore,
                               RunnerSpeedCalculator speedCalculator,
                               AchievementManager achievementManager,
                               RobotManager robotManager,
                               AscendHudManager hudManager) {
        this.playerStore = playerStore;
        this.mapStore = mapStore;
        this.ghostStore = ghostStore;
        this.speedCalculator = speedCalculator;
        this.achievementManager = achievementManager;
        this.robotManager = robotManager;
        this.hudManager = hudManager;
    }

    public AscendProfilePage createProfilePage(@Nonnull PlayerRef playerRef) {
        return new AscendProfilePage(playerRef, this);
    }

    public StatsPage createStatsPage(@Nonnull PlayerRef playerRef) {
        return createStatsPage(playerRef, false);
    }

    public StatsPage createStatsPage(@Nonnull PlayerRef playerRef, boolean fromProfile) {
        return new StatsPage(playerRef, playerStore, mapStore, ghostStore, speedCalculator, this, fromProfile);
    }

    public AscendAchievementPage createAchievementPage(@Nonnull PlayerRef playerRef, boolean fromProfile) {
        return new AscendAchievementPage(playerRef, playerStore, achievementManager, this, fromProfile);
    }

    public AscendSettingsPage createSettingsPage(@Nonnull PlayerRef playerRef) {
        return createSettingsPage(playerRef, false);
    }

    public AscendSettingsPage createSettingsPage(@Nonnull PlayerRef playerRef, boolean fromProfile) {
        return new AscendSettingsPage(playerRef, playerStore, robotManager, hudManager, this, fromProfile);
    }

    public AscendMusicPage createMusicPage(@Nonnull PlayerRef playerRef) {
        return createMusicPage(playerRef, false);
    }

    public AscendMusicPage createMusicPage(@Nonnull PlayerRef playerRef, boolean fromProfile) {
        return new AscendMusicPage(playerRef, this, fromProfile);
    }

    public boolean canOpenAchievementPage() {
        return achievementManager != null;
    }
}
