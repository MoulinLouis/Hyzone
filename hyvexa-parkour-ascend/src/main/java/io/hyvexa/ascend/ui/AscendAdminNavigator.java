package io.hyvexa.ascend.ui;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.AscendSettingsStore;
import io.hyvexa.ascend.holo.AscendHologramManager;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.data.BlockConfigStore;
import io.hyvexa.ascend.mine.data.ConveyorConfigStore;
import io.hyvexa.ascend.mine.data.GateConfigStore;
import io.hyvexa.ascend.mine.data.MineHierarchyStore;
import io.hyvexa.ascend.mine.data.MinerConfigStore;
import io.hyvexa.ascend.mine.data.TierConfigStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.mine.ui.MineAdminPage;
import io.hyvexa.ascend.mine.ui.MinerDefAdminPage;
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
    private final MineHierarchyStore mineHierarchyStore;
    private final MinerConfigStore minerConfigStore;
    private final TierConfigStore tierConfigStore;
    private final ConveyorConfigStore conveyorConfigStore;
    private final GateConfigStore gateConfigStore;
    private final BlockConfigStore blockConfigStore;
    private final MineManager mineManager;
    private final AscendHologramManager hologramManager;

    public AscendAdminNavigator(AscendPlayerStore playerStore,
                                AscendMapStore mapStore,
                                AscendSettingsStore settingsStore,
                                AscensionManager ascensionManager,
                                ChallengeManager challengeManager,
                                RobotManager robotManager,
                                AchievementManager achievementManager,
                                AscendWhitelistManager whitelistManager,
                                MineHierarchyStore mineHierarchyStore,
                                MinerConfigStore minerConfigStore,
                                TierConfigStore tierConfigStore,
                                ConveyorConfigStore conveyorConfigStore,
                                GateConfigStore gateConfigStore,
                                BlockConfigStore blockConfigStore,
                                MineManager mineManager,
                                AscendHologramManager hologramManager) {
        this.playerStore = playerStore;
        this.mapStore = mapStore;
        this.settingsStore = settingsStore;
        this.ascensionManager = ascensionManager;
        this.challengeManager = challengeManager;
        this.robotManager = robotManager;
        this.achievementManager = achievementManager;
        this.whitelistManager = whitelistManager;
        this.mineHierarchyStore = mineHierarchyStore;
        this.minerConfigStore = minerConfigStore;
        this.tierConfigStore = tierConfigStore;
        this.conveyorConfigStore = conveyorConfigStore;
        this.gateConfigStore = gateConfigStore;
        this.blockConfigStore = blockConfigStore;
        this.mineManager = mineManager;
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
        return mineHierarchyStore != null ? new MineAdminPage(playerRef, mineHierarchyStore,
            minerConfigStore, conveyorConfigStore, gateConfigStore, blockConfigStore,
            mineManager, this) : null;
    }

    public MinerDefAdminPage createMinerDefAdminPage(@Nonnull PlayerRef playerRef, String mineId) {
        return mineHierarchyStore != null
            ? new MinerDefAdminPage(playerRef, mineHierarchyStore, minerConfigStore, mineManager, this, mineId) : null;
    }

    public PickaxeAdminPage createPickaxeAdminPage(@Nonnull PlayerRef playerRef) {
        return tierConfigStore != null ? new PickaxeAdminPage(playerRef, tierConfigStore) : null;
    }
}
