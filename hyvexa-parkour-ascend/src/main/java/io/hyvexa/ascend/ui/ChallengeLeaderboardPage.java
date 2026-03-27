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
import io.hyvexa.ascend.AscensionConstants.ChallengeType;
import io.hyvexa.common.ui.AccentOverlayUtils;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.ascension.ChallengeManager.ChallengeLeaderboardEntry;
import io.hyvexa.ascend.data.AscendPlayerEventHandler;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.common.ui.AbstractLeaderboardPage;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class ChallengeLeaderboardPage extends AbstractLeaderboardPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TAB_PREFIX = "Tab";
    private static final int MAX_TABS = 8;

    private final AscendPlayerStore playerStore;
    private final ChallengeManager challengeManager;
    private final RobotManager robotManager;
    private final AscendPlayerEventHandler eventHandler;
    private final ChallengeType[] challengeTypes;
    private int currentTabIndex = 0;

    public ChallengeLeaderboardPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore,
                                    ChallengeManager challengeManager, RobotManager robotManager,
                                    AscendPlayerEventHandler eventHandler) {
        super(playerRef, 50);
        this.playerStore = playerStore;
        this.challengeManager = challengeManager;
        this.robotManager = robotManager;
        this.eventHandler = eventHandler;
        this.challengeTypes = ChallengeType.values();
    }

    @Override
    protected String getPagePath() {
        return "Pages/Ascend_ChallengeLeaderboard.ui";
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
        return "No challenges available.";
    }

    @Override
    protected String getNoEntriesMessage() {
        return "No times recorded yet.";
    }

    @Override
    protected void bindCustomEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        int tabCount = Math.min(challengeTypes.length, MAX_TABS);
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
        if (currentTabIndex >= challengeTypes.length) {
            return null;
        }

        ChallengeType type = challengeTypes[currentTabIndex];
        List<ChallengeLeaderboardEntry> entries = challengeManager.getChallengeLeaderboard(type);
        if (entries.isEmpty()) {
            return List.of();
        }

        List<LeaderboardRow> rows = new ArrayList<>();
        int rank = 1;
        for (ChallengeLeaderboardEntry entry : entries) {
            String name = entry.playerName() != null ? entry.playerName() : "Unknown";
            rows.add(new LeaderboardRow(rank, null, name, FormatUtils.formatDurationLong(entry.bestTimeMs())));
            rank++;
        }
        return rows;
    }

    private void switchTab(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= challengeTypes.length || tabIndex >= MAX_TABS) {
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
        PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (pRef != null && player != null) {
            player.getPageManager().openCustomPage(ref, store,
                new AscendChallengePage(pRef, playerStore, challengeManager, robotManager, eventHandler));
            return;
        }
        this.close();
    }

    private void setupTabs(UICommandBuilder cmd) {
        int tabCount = Math.min(challengeTypes.length, MAX_TABS);
        for (int i = 0; i < tabCount; i++) {
            cmd.set("#Tab" + i + "Label.Text", "C" + (i + 1));
            cmd.set("#Tab" + i + "Wrap.Visible", true);
        }
        for (int i = tabCount; i < MAX_TABS; i++) {
            cmd.set("#Tab" + i + "Wrap.Visible", false);
        }
        updateTabStyles(cmd);
    }

    private void updateTabStyles(UICommandBuilder cmd) {
        int tabCount = Math.min(challengeTypes.length, MAX_TABS);
        for (int i = 0; i < tabCount; i++) {
            boolean active = (i == currentTabIndex);
            cmd.set("#Tab" + i + "Wrap #TabActive.Visible", active);
            if (active) {
                AccentOverlayUtils.applyAccent(cmd, "#Tab" + i + "Accent",
                        challengeTypes[i].getAccentColor(), AccentOverlayUtils.CHALLENGE_TAB_ACCENTS);
            } else {
                for (String id : AccentOverlayUtils.CHALLENGE_TAB_ACCENTS) {
                    cmd.set("#Tab" + i + "Accent #" + id + ".Visible", false);
                }
            }
            cmd.set("#Tab" + i + "Label.Style.TextColor",
                    active ? "#f0f4f8" : "#9fb0ba");
        }
    }
}
