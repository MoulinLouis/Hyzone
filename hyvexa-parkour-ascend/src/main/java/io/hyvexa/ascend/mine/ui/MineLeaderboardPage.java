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
import io.hyvexa.common.ui.AccentOverlayUtils;
import io.hyvexa.common.ui.AbstractSearchablePaginatedPage;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.ui.PaginationState;
import io.hyvexa.common.util.FormatUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MineLeaderboardPage extends AbstractSearchablePaginatedPage {

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
    protected void bindCustomEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabCrystals",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_CRYSTALS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabBlocks",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_BLOCKS), false);
    }

    @Override
    protected void buildContent(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        buildLeaderboard(commandBuilder);
    }

    @Override
    protected void handleCustomButton(String button, Ref<EntityStore> ref, Store<EntityStore> store) {
        switch (button) {
            case BUTTON_CLOSE -> this.close();
            case BUTTON_TAB_CRYSTALS -> switchCategory(LeaderboardCategory.CRYSTALS);
            case BUTTON_TAB_BLOCKS -> switchCategory(LeaderboardCategory.BLOCKS_MINED);
        }
    }

    private void switchCategory(LeaderboardCategory category) {
        if (currentCategory == category) {
            return;
        }
        currentCategory = category;
        getPagination().reset();
        sendRefresh();
    }

    private void buildLeaderboard(UICommandBuilder commandBuilder) {
        commandBuilder.clear("#LeaderboardCards");
        commandBuilder.set("#SearchField.Value", getSearchText());

        updateTabStyles(commandBuilder);

        List<MineLeaderboardEntry> entries = achievementTracker.getMineLeaderboardEntries();
        if (entries.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No players yet.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }

        List<LeaderboardRow> sorted = getSortedEntries(entries);
        if (sorted.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No data available.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }

        String filter = getSearchText().toLowerCase();
        List<LeaderboardRow> filtered = new ArrayList<>();
        for (LeaderboardRow row : sorted) {
            if (!filter.isEmpty()) {
                String safeName = row.name != null ? row.name : "";
                if (!safeName.toLowerCase().startsWith(filter)) {
                    continue;
                }
            }
            filtered.add(row);
        }

        if (filtered.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No matches.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }

        commandBuilder.set("#EmptyText.Text", "");
        PaginationState.PageSlice slice = getPagination().slice(filtered.size());
        int start = slice.startIndex;
        int end = slice.endIndex;
        int index = 0;

        for (int i = start; i < end; i++) {
            LeaderboardRow row = filtered.get(i);
            commandBuilder.append("#LeaderboardCards", "Pages/Ascend_LeaderboardEntry.ui");
            String accentColor = AscendUIUtils.getRankAccentColor(row.rank);
            AccentOverlayUtils.applyAccent(commandBuilder, "#LeaderboardCards[" + index + "] #AccentBar",
                    accentColor, AccentOverlayUtils.RANK_ACCENTS);
            commandBuilder.set("#LeaderboardCards[" + index + "] #Rank.Text", "#" + row.rank);
            commandBuilder.set("#LeaderboardCards[" + index + "] #PlayerName.Text", row.name);
            commandBuilder.set("#LeaderboardCards[" + index + "] #Value.Text", row.formattedValue);
            index++;
        }

        commandBuilder.set("#PageLabel.Text", slice.getLabel());
    }

    private void updateTabStyles(UICommandBuilder commandBuilder) {
        setTabActive(commandBuilder, "TabCrystals", currentCategory == LeaderboardCategory.CRYSTALS);
        setTabActive(commandBuilder, "TabBlocks", currentCategory == LeaderboardCategory.BLOCKS_MINED);
    }

    private void setTabActive(UICommandBuilder commandBuilder, String tabId, boolean active) {
        String wrapPath = "#" + tabId + "Wrap";
        String accentPath = "#" + tabId + "Accent";
        commandBuilder.set(wrapPath + " #" + tabId + "ActiveBg.Visible", active);
        commandBuilder.set(wrapPath + " #" + tabId + "InactiveBg.Visible", !active);
        commandBuilder.set(wrapPath + " " + accentPath + " #" + tabId + "AccentActive.Visible", active);
        commandBuilder.set(wrapPath + " " + accentPath + " #" + tabId + "AccentInactive.Visible", !active);
    }

    private List<LeaderboardRow> getSortedEntries(List<MineLeaderboardEntry> entries) {
        List<LeaderboardRow> rows = new ArrayList<>();
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

        int rank = 1;
        for (MineLeaderboardEntry entry : sorted) {
            String name = resolveName(entry);
            String formattedValue = formatValue(entry);
            rows.add(new LeaderboardRow(rank, entry.playerId(), name, formattedValue));
            rank++;
        }

        return rows;
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

    private record LeaderboardRow(int rank, UUID playerId, String name, String formattedValue) {
    }
}
