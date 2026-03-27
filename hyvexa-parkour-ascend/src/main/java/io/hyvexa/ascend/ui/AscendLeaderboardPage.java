package io.hyvexa.ascend.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.AscendPlayerStore.LeaderboardEntry;
import io.hyvexa.common.ui.AbstractLeaderboardPage;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;

import javax.annotation.Nonnull;
import io.hyvexa.common.math.BigNumber;

import java.util.ArrayList;
import java.util.List;

public class AscendLeaderboardPage extends AbstractLeaderboardPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TAB_VOLT = "TabVolt";
    private static final String BUTTON_TAB_ASCENSIONS = "TabAscensions";
    private static final String BUTTON_TAB_RUNS = "TabRuns";
    private static final String BUTTON_TAB_FASTEST = "TabFastest";

    private final AscendPlayerStore playerStore;
    private LeaderboardCategory currentCategory = LeaderboardCategory.VOLT;

    public AscendLeaderboardPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore) {
        super(playerRef, 50);
        this.playerStore = playerStore;
    }

    @Override
    protected String getPagePath() {
        return "Pages/Ascend_Leaderboard.ui";
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
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabVolt",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_VOLT), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabAscensions",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_ASCENSIONS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabRuns",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_RUNS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabFastest",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_FASTEST), false);
    }

    @Override
    protected void handleCustomButton(String button, Ref<EntityStore> ref, Store<EntityStore> store) {
        switch (button) {
            case BUTTON_CLOSE -> this.close();
            case BUTTON_TAB_VOLT -> switchCategory(LeaderboardCategory.VOLT);
            case BUTTON_TAB_ASCENSIONS -> switchCategory(LeaderboardCategory.ASCENSIONS);
            case BUTTON_TAB_RUNS -> switchCategory(LeaderboardCategory.MANUAL_RUNS);
            case BUTTON_TAB_FASTEST -> switchCategory(LeaderboardCategory.FASTEST_ASCENSION);
        }
    }

    @Override
    protected void onBuildLeaderboard(UICommandBuilder cmd) {
        setTabActive(cmd, "TabVolt", currentCategory == LeaderboardCategory.VOLT);
        setTabActive(cmd, "TabAscensions", currentCategory == LeaderboardCategory.ASCENSIONS);
        setTabActive(cmd, "TabRuns", currentCategory == LeaderboardCategory.MANUAL_RUNS);
        setTabActive(cmd, "TabFastest", currentCategory == LeaderboardCategory.FASTEST_ASCENSION);
    }

    @Override
    protected List<LeaderboardRow> loadRows() {
        List<LeaderboardEntry> entries = playerStore.getLeaderboardEntries();
        if (entries.isEmpty()) {
            return null;
        }

        List<LeaderboardEntry> sorted = new ArrayList<>(entries);
        switch (currentCategory) {
            case VOLT -> {
                sorted.removeIf(e -> e.totalVoltEarnedExp10() == 0 && e.totalVoltEarnedMantissa() == 0);
                sorted.sort((a, b) -> {
                    int cmp = Integer.compare(b.totalVoltEarnedExp10(), a.totalVoltEarnedExp10());
                    if (cmp != 0) return cmp;
                    return Double.compare(b.totalVoltEarnedMantissa(), a.totalVoltEarnedMantissa());
                });
            }
            case ASCENSIONS -> {
                sorted.removeIf(e -> e.ascensionCount() == 0);
                sorted.sort((a, b) -> Integer.compare(b.ascensionCount(), a.ascensionCount()));
            }
            case MANUAL_RUNS -> {
                sorted.removeIf(e -> e.totalManualRuns() == 0);
                sorted.sort((a, b) -> Integer.compare(b.totalManualRuns(), a.totalManualRuns()));
            }
            case FASTEST_ASCENSION -> {
                sorted.removeIf(e -> e.fastestAscensionMs() == null);
                sorted.sort((a, b) -> Long.compare(a.fastestAscensionMs(), b.fastestAscensionMs()));
            }
        }

        List<LeaderboardRow> rows = new ArrayList<>();
        int rank = 1;
        for (LeaderboardEntry entry : sorted) {
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

    private String resolveName(LeaderboardEntry entry) {
        if (entry.playerName() != null && !entry.playerName().isEmpty()) {
            return entry.playerName();
        }
        String cachedName = playerStore.getPlayerName(entry.playerId());
        if (cachedName != null) {
            return cachedName;
        }
        PlayerRef playerRef = Universe.get().getPlayer(entry.playerId());
        if (playerRef != null) {
            return playerRef.getUsername();
        }
        return entry.playerId().toString().substring(0, 8) + "...";
    }

    private String formatValue(LeaderboardEntry entry) {
        return switch (currentCategory) {
            case VOLT -> FormatUtils.formatBigNumber(BigNumber.of(entry.totalVoltEarnedMantissa(), entry.totalVoltEarnedExp10()));
            case ASCENSIONS -> String.valueOf(entry.ascensionCount());
            case MANUAL_RUNS -> String.valueOf(entry.totalManualRuns());
            case FASTEST_ASCENSION -> entry.fastestAscensionMs() != null
                    ? FormatUtils.formatDurationLong(entry.fastestAscensionMs())
                    : "-";
        };
    }

    public enum LeaderboardCategory {
        VOLT,
        ASCENSIONS,
        MANUAL_RUNS,
        FASTEST_ASCENSION
    }
}
