package io.hyvexa.ascend.mine.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker.MineLeaderboardEntry;
import io.hyvexa.ascend.ui.AscendUIUtils;
import io.hyvexa.common.ui.AbstractLeaderboardPage;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class MineLeaderboardPage extends AbstractLeaderboardPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TAB_CRYSTALS = "TabCrystals";
    private static final String BUTTON_TAB_BLOCKS = "TabBlocks";

    private final MineAchievementTracker achievementTracker;
    private LeaderboardCategory currentCategory = LeaderboardCategory.CRYSTALS;

    public MineLeaderboardPage(@Nonnull PlayerRef playerRef, MineAchievementTracker achievementTracker) {
        super(playerRef, 50);
        this.achievementTracker = achievementTracker;
    }

    @Override
    protected String getPagePath() {
        return "Pages/Mine_Leaderboard.ui";
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
    protected void bindCustomEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabCrystals",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_CRYSTALS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabBlocks",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_BLOCKS), false);
    }

    @Override
    protected void handleCustomButton(String button, Ref<EntityStore> ref, Store<EntityStore> store) {
        switch (button) {
            case BUTTON_CLOSE -> this.close();
            case BUTTON_TAB_CRYSTALS -> switchCategory(LeaderboardCategory.CRYSTALS);
            case BUTTON_TAB_BLOCKS -> switchCategory(LeaderboardCategory.BLOCKS_MINED);
        }
    }

    @Override
    protected void onBuildLeaderboard(UICommandBuilder cmd) {
        setTabActive(cmd, "TabCrystals", currentCategory == LeaderboardCategory.CRYSTALS);
        setTabActive(cmd, "TabBlocks", currentCategory == LeaderboardCategory.BLOCKS_MINED);
    }

    @Override
    protected List<LeaderboardRow> loadRows() {
        List<MineLeaderboardEntry> entries = achievementTracker.getMineLeaderboardEntries();
        if (entries.isEmpty()) {
            return null;
        }

        List<MineLeaderboardEntry> sorted = new ArrayList<>(entries);
        switch (currentCategory) {
            case CRYSTALS -> {
                sorted.removeIf(e -> e.totalCrystalsEarned() == 0);
                sorted.sort((a, b) -> Long.compare(b.totalCrystalsEarned(), a.totalCrystalsEarned()));
            }
            case BLOCKS_MINED -> {
                sorted.removeIf(e -> e.manualBlocksMined() == 0);
                sorted.sort((a, b) -> Long.compare(b.manualBlocksMined(), a.manualBlocksMined()));
            }
        }

        List<LeaderboardRow> rows = new ArrayList<>();
        int rank = 1;
        for (MineLeaderboardEntry entry : sorted) {
            rows.add(new LeaderboardRow(rank, entry.playerId(), resolveName(entry), formatValue(entry)));
            rank++;
        }
        return rows;
    }

    private void switchCategory(LeaderboardCategory category) {
        if (currentCategory == category) {
            return;
        }
        currentCategory = category;
        getPagination().reset();
        sendRefresh();
    }

    private String resolveName(MineLeaderboardEntry entry) {
        if (entry.playerName() != null && !entry.playerName().isEmpty()) {
            return entry.playerName();
        }
        PlayerRef playerRef = Universe.get().getPlayer(entry.playerId());
        if (playerRef != null) {
            return playerRef.getUsername();
        }
        return entry.playerId().toString().substring(0, 8) + "...";
    }

    private String formatValue(MineLeaderboardEntry entry) {
        return switch (currentCategory) {
            case CRYSTALS -> FormatUtils.formatLong(entry.totalCrystalsEarned());
            case BLOCKS_MINED -> FormatUtils.formatLong(entry.manualBlocksMined());
        };
    }

    public enum LeaderboardCategory {
        CRYSTALS,
        BLOCKS_MINED
    }
}
