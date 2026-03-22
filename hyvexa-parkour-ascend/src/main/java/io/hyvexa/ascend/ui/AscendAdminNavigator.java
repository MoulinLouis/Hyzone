package io.hyvexa.ascend.ui;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.AscendSettingsStore;
import io.hyvexa.ascend.holo.AscendHologramManager;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.mine.ui.MineAdminPage;
import io.hyvexa.ascend.mine.ui.PickaxeAdminPage;
import io.hyvexa.common.whitelist.AscendWhitelistManager;

import javax.annotation.Nonnull;

public class AscendAdminNavigator {

    private final AscendPlayerStore playerStore;
    private final AscendMapStore mapStore;
    private final AscendSettingsStore settingsStore;
    private final AscensionManager ascensionManager;
    private final ChallengeManager challengeManager;
    private final RobotManager robotManager;
    private final AchievementManager achievementManager;
    private final AscendWhitelistManager whitelistManager;
    private final MineConfigStore mineConfigStore;
    private final AscendHologramManager hologramManager;

    public AscendAdminNavigator(AscendPlayerStore playerStore,
                                AscendMapStore mapStore,
                                AscendSettingsStore settingsStore,
                                AscensionManager ascensionManager,
                                ChallengeManager challengeManager,
                                RobotManager robotManager,
                                AchievementManager achievementManager,
                                AscendWhitelistManager whitelistManager,
                                MineConfigStore mineConfigStore,
                                AscendHologramManager hologramManager) {
        this.playerStore = playerStore;
        this.mapStore = mapStore;
        this.settingsStore = settingsStore;
        this.ascensionManager = ascensionManager;
        this.challengeManager = challengeManager;
        this.robotManager = robotManager;
        this.achievementManager = achievementManager;
        this.whitelistManager = whitelistManager;
        this.mineConfigStore = mineConfigStore;
        this.hologramManager = hologramManager;
    }

    public AscendAdminPanelPage createPanelPage(@Nonnull PlayerRef playerRef) {
        return new AscendAdminPanelPage(playerRef, this);
    }

    public AscendAdminPage createMapAdminPage(@Nonnull PlayerRef playerRef) {
        return mapStore != null ? new AscendAdminPage(playerRef, mapStore, hologramManager) : null;
    }

    public AscendAdminVoltPage createVoltPage(@Nonnull PlayerRef playerRef) {
        return new AscendAdminVoltPage(playerRef,
            playerStore,
            mapStore,
            settingsStore,
            ascensionManager,
            challengeManager,
            robotManager,
            achievementManager,
            this);
    }

    public AscendWhitelistPage createWhitelistPage(@Nonnull PlayerRef playerRef) {
        return new AscendWhitelistPage(playerRef, whitelistManager, this);
    }

    public MineAdminPage createMineAdminPage(@Nonnull PlayerRef playerRef) {
        return mineConfigStore != null ? new MineAdminPage(playerRef, mineConfigStore, this) : null;
    }

    public PickaxeAdminPage createPickaxeAdminPage(@Nonnull PlayerRef playerRef) {
        return mineConfigStore != null ? new PickaxeAdminPage(playerRef, mineConfigStore) : null;
    }
}
