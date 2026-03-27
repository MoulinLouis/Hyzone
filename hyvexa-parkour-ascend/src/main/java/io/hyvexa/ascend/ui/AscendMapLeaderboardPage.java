package io.hyvexa.ascend.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.common.ui.AccentOverlayUtils;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerEventHandler;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.AscendPlayerStore.MapLeaderboardEntry;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.robot.RunnerSpeedCalculator;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.transcendence.TranscendenceManager;
import io.hyvexa.ascend.tutorial.TutorialTriggerService;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.core.analytics.PlayerAnalytics;
import io.hyvexa.common.ui.AbstractLeaderboardPage;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class AscendMapLeaderboardPage extends AbstractLeaderboardPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TAB_PREFIX = "Tab";
    private static final int MAX_TABS = 5;

    private static final String[] MAP_TAB_COLORS = {
        "#ef4444", // Red
        "#f59e0b", // Amber
        "#eab308", // Yellow
        "#10b981", // Green
        "#3b82f6"  // Blue
    };

    private final AscendPlayerStore playerStore;
    private final AscendMapStore mapStore;
    private final AscendRunTracker runTracker;
    private final RobotManager robotManager;
    private final GhostStore ghostStore;
    private final AscensionManager ascensionManager;
    private final ChallengeManager challengeManager;
    private final SummitManager summitManager;
    private final TranscendenceManager transcendenceManager;
    private final AchievementManager achievementManager;
    private final TutorialTriggerService tutorialTriggerService;
    private final RunnerSpeedCalculator speedCalculator;
    private final PlayerAnalytics analytics;
    private final AscendPlayerEventHandler eventHandler;
    private final List<AscendMap> maps;
    private int currentTabIndex = 0;

    public AscendMapLeaderboardPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore, AscendMapStore mapStore) {
        this(playerRef, playerStore, mapStore, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public AscendMapLeaderboardPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore, AscendMapStore mapStore,
                                    AscendRunTracker runTracker, RobotManager robotManager, GhostStore ghostStore,
                                    AscensionManager ascensionManager, ChallengeManager challengeManager,
                                    SummitManager summitManager,
                                    TranscendenceManager transcendenceManager, AchievementManager achievementManager,
                                    TutorialTriggerService tutorialTriggerService,
                                    RunnerSpeedCalculator speedCalculator,
                                    PlayerAnalytics analytics,
                                    AscendPlayerEventHandler eventHandler) {
        super(playerRef, 50);
        this.playerStore = playerStore;
        this.mapStore = mapStore;
        this.runTracker = runTracker;
        this.robotManager = robotManager;
        this.ghostStore = ghostStore;
        this.ascensionManager = ascensionManager;
        this.challengeManager = challengeManager;
        this.summitManager = summitManager;
        this.transcendenceManager = transcendenceManager;
        this.achievementManager = achievementManager;
        this.tutorialTriggerService = tutorialTriggerService;
        this.speedCalculator = speedCalculator;
        this.analytics = analytics;
        this.eventHandler = eventHandler;
        this.maps = new ArrayList<>(mapStore.listMapsSorted());
    }

    @Override
    protected String getPagePath() {
        return "Pages/Ascend_MapLeaderboard.ui";
    }

    @Override
    protected String getSearchFieldId() {
        return "#SearchField";
    }

    @Override
    protected String getCardTemplatePath() {
        return "Pages/Ascend_LeaderboardEntry.ui";
    }

    @Override
    protected String getRankAccentColor(int rank) {
        return AscendUIUtils.getRankAccentColor(rank);
    }

    @Override
    protected boolean useFilteredRanks() {
        return true;
    }

    @Override
    protected String getNoDataMessage() {
        return "No maps available.";
    }

    @Override
    protected String getNoEntriesMessage() {
        return "No times recorded yet.";
    }

    @Override
    protected void bindCustomEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        int tabCount = Math.min(maps.size(), MAX_TABS);
        for (int i = 0; i < tabCount; i++) {
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Tab" + i,
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_PREFIX + i), false);
        }
    }

    @Override
    protected void onPageSetup(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        setupTabs(commandBuilder);
    }

    @Override
    protected void handleCustomButton(String button, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (BUTTON_CLOSE.equals(button)) {
            handleBack(ref, store);
            return;
        }
        if (button.startsWith(BUTTON_TAB_PREFIX)) {
            try {
                int tabIndex = Integer.parseInt(button.substring(BUTTON_TAB_PREFIX.length()));
                switchTab(tabIndex);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Override
    protected void onBuildLeaderboard(UICommandBuilder cmd) {
        updateTabStyles(cmd);
    }

    @Override
    protected List<LeaderboardRow> loadRows() {
        if (maps.isEmpty() || currentTabIndex >= maps.size()) {
            return null;
        }

        String mapId = maps.get(currentTabIndex).getId();
        List<MapLeaderboardEntry> entries = playerStore.getMapLeaderboard(mapId);
        if (entries.isEmpty()) {
            return List.of();
        }

        List<LeaderboardRow> rows = new ArrayList<>();
        int rank = 1;
        for (MapLeaderboardEntry entry : entries) {
            String name = entry.playerName() != null ? entry.playerName() : "Unknown";
            rows.add(new LeaderboardRow(rank, null, name, FormatUtils.formatDurationLong(entry.bestTimeMs())));
            rank++;
        }
        return rows;
    }

    private void switchTab(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= maps.size() || tabIndex >= MAX_TABS) {
            return;
        }
        if (currentTabIndex == tabIndex) {
            return;
        }
        currentTabIndex = tabIndex;
        resetSearchAndPagination();
        sendRefresh();
    }

    private void handleBack(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (runTracker != null && robotManager != null && ghostStore != null) {
            PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
            Player player = store.getComponent(ref, Player.getComponentType());
            if (pRef != null && player != null) {
                player.getPageManager().openCustomPage(ref, store,
                    new AscendMapSelectPage(pRef, mapStore, playerStore, runTracker, robotManager, ghostStore,
                        ascensionManager, challengeManager, summitManager, transcendenceManager,
                        achievementManager, tutorialTriggerService, speedCalculator, analytics, eventHandler));
                return;
            }
        }
        this.close();
    }

    private void setupTabs(UICommandBuilder cmd) {
        int tabCount = Math.min(maps.size(), MAX_TABS);
        for (int i = 0; i < tabCount; i++) {
            AscendMap map = maps.get(i);
            String mapName = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
            cmd.set("#Tab" + i + "Label.Text", mapName);
            cmd.set("#Tab" + i + "Wrap.Visible", true);
        }
        for (int i = tabCount; i < MAX_TABS; i++) {
            cmd.set("#Tab" + i + "Wrap.Visible", false);
        }
        updateTabStyles(cmd);
    }

    private void updateTabStyles(UICommandBuilder cmd) {
        int tabCount = Math.min(maps.size(), MAX_TABS);
        for (int i = 0; i < tabCount; i++) {
            boolean active = (i == currentTabIndex);
            cmd.set("#Tab" + i + "Wrap #TabActive.Visible", active);
            String accentColor = i < MAP_TAB_COLORS.length ? MAP_TAB_COLORS[i] : MAP_TAB_COLORS[MAP_TAB_COLORS.length - 1];
            if (active) {
                AccentOverlayUtils.applyAccent(cmd, "#Tab" + i + "Accent",
                        accentColor, AccentOverlayUtils.MAP_TAB_ACCENTS);
            } else {
                for (String id : AccentOverlayUtils.MAP_TAB_ACCENTS) {
                    cmd.set("#Tab" + i + "Accent #" + id + ".Visible", false);
                }
            }
            cmd.set("#Tab" + i + "Label.Style.TextColor",
                    active ? "#f0f4f8" : "#9fb0ba");
        }
    }
}
