package io.hyvexa.ascend.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.AscendPlayerStore.MapLeaderboardEntry;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.ui.PaginationState;
import io.hyvexa.common.util.FormatUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class AscendMapLeaderboardPage extends InteractiveCustomUIPage<AscendMapLeaderboardPage.MapLeaderboardData> {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_PREV = "PrevPage";
    private static final String BUTTON_NEXT = "NextPage";
    private static final String BUTTON_TAB_PREFIX = "Tab";

    private static final int MAX_TABS = 5;

    private static final String COLOR_RANK_1 = "#ffd700";
    private static final String COLOR_RANK_2 = "#c0c0c0";
    private static final String COLOR_RANK_3 = "#cd7f32";
    private static final String COLOR_RANK_DEFAULT = "#9fb0ba";

    private static final String COLOR_TAB_ACTIVE = "#2d3f50";
    private static final String COLOR_TAB_INACTIVE = "#142029";

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
    private final PaginationState pagination = new PaginationState(50);
    private final List<AscendMap> maps;
    private int currentTabIndex = 0;
    private String searchText = "";

    public AscendMapLeaderboardPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore, AscendMapStore mapStore) {
        this(playerRef, playerStore, mapStore, null, null, null);
    }

    public AscendMapLeaderboardPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore, AscendMapStore mapStore,
                                    AscendRunTracker runTracker, RobotManager robotManager, GhostStore ghostStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, MapLeaderboardData.CODEC);
        this.playerStore = playerStore;
        this.mapStore = mapStore;
        this.runTracker = runTracker;
        this.robotManager = robotManager;
        this.ghostStore = ghostStore;
        this.maps = new ArrayList<>(mapStore.listMapsSorted());
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_MapLeaderboard.ui");
        bindEvents(eventBuilder);
        setupTabs(commandBuilder);
        buildLeaderboard(commandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull MapLeaderboardData data) {
        super.handleDataEvent(ref, store, data);

        String previousSearch = searchText;
        if (data.search != null) {
            searchText = data.search.trim();
        }

        if (data.getButton() == null) {
            if (!previousSearch.equals(searchText)) {
                pagination.reset();
                sendRefresh();
            }
            return;
        }

        switch (data.getButton()) {
            case BUTTON_CLOSE -> handleBack(ref, store);
            case BUTTON_PREV -> {
                pagination.previous();
                sendRefresh();
            }
            case BUTTON_NEXT -> {
                pagination.next();
                sendRefresh();
            }
            default -> {
                if (data.getButton().startsWith(BUTTON_TAB_PREFIX)) {
                    try {
                        int tabIndex = Integer.parseInt(data.getButton().substring(BUTTON_TAB_PREFIX.length()));
                        switchTab(tabIndex);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    private void switchTab(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= maps.size() || tabIndex >= MAX_TABS) {
            return;
        }
        if (currentTabIndex == tabIndex) {
            return;
        }
        currentTabIndex = tabIndex;
        pagination.reset();
        searchText = "";
        sendRefresh();
    }

    private void handleBack(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (runTracker != null && robotManager != null && ghostStore != null) {
            PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
            Player player = store.getComponent(ref, Player.getComponentType());
            if (pRef != null && player != null) {
                player.getPageManager().openCustomPage(ref, store,
                    new AscendMapSelectPage(pRef, mapStore, playerStore, runTracker, robotManager, ghostStore));
                return;
            }
        }
        this.close();
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindEvents(eventBuilder);
        updateTabStyles(commandBuilder);
        buildLeaderboard(commandBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchField",
                EventData.of(MapLeaderboardData.KEY_SEARCH, "#SearchField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PREV), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NEXT), false);

        int tabCount = Math.min(maps.size(), MAX_TABS);
        for (int i = 0; i < tabCount; i++) {
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Tab" + i,
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_PREFIX + i), false);
        }
    }

    private void setupTabs(UICommandBuilder commandBuilder) {
        int tabCount = Math.min(maps.size(), MAX_TABS);
        for (int i = 0; i < tabCount; i++) {
            AscendMap map = maps.get(i);
            String mapName = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
            commandBuilder.set("#Tab" + i + "Label.Text", mapName);
            commandBuilder.set("#Tab" + i + "Wrap.Visible", true);
        }
        // Hide unused tabs
        for (int i = tabCount; i < MAX_TABS; i++) {
            commandBuilder.set("#Tab" + i + "Wrap.Visible", false);
        }
        updateTabStyles(commandBuilder);
    }

    private void updateTabStyles(UICommandBuilder commandBuilder) {
        int tabCount = Math.min(maps.size(), MAX_TABS);
        for (int i = 0; i < tabCount; i++) {
            boolean active = (i == currentTabIndex);
            commandBuilder.set("#Tab" + i + "Wrap.Background",
                    active ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
            String accentColor = i < MAP_TAB_COLORS.length ? MAP_TAB_COLORS[i] : MAP_TAB_COLORS[MAP_TAB_COLORS.length - 1];
            commandBuilder.set("#Tab" + i + "Accent.Background",
                    active ? accentColor : COLOR_TAB_INACTIVE);
            commandBuilder.set("#Tab" + i + "Label.Style.TextColor",
                    active ? "#f0f4f8" : "#9fb0ba");
        }
    }

    private void buildLeaderboard(UICommandBuilder commandBuilder) {
        commandBuilder.clear("#LeaderboardCards");
        commandBuilder.set("#SearchField.Value", searchText);

        if (maps.isEmpty() || currentTabIndex >= maps.size()) {
            commandBuilder.set("#EmptyText.Text", "No maps available.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }

        String mapId = maps.get(currentTabIndex).getId();
        List<MapLeaderboardEntry> entries = playerStore.getMapLeaderboard(mapId);

        if (entries.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No times recorded yet.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }

        // Apply search filter
        String filter = searchText.toLowerCase();
        List<MapLeaderboardEntry> filtered = new ArrayList<>();
        for (MapLeaderboardEntry entry : entries) {
            if (!filter.isEmpty()) {
                String safeName = entry.playerName() != null ? entry.playerName() : "";
                if (!safeName.toLowerCase().startsWith(filter)) {
                    continue;
                }
            }
            filtered.add(entry);
        }

        if (filtered.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No matches.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }

        commandBuilder.set("#EmptyText.Text", "");
        PaginationState.PageSlice slice = pagination.slice(filtered.size());
        int start = slice.startIndex;
        int end = slice.endIndex;
        int index = 0;

        for (int i = start; i < end; i++) {
            MapLeaderboardEntry entry = filtered.get(i);
            int rank = i + 1;
            commandBuilder.append("#LeaderboardCards", "Pages/Ascend_LeaderboardEntry.ui");
            String accentColor = getRankAccentColor(rank);
            commandBuilder.set("#LeaderboardCards[" + index + "] #AccentBar.Background", accentColor);
            commandBuilder.set("#LeaderboardCards[" + index + "] #Rank.Text", "#" + rank);
            commandBuilder.set("#LeaderboardCards[" + index + "] #PlayerName.Text",
                    entry.playerName() != null ? entry.playerName() : "Unknown");
            commandBuilder.set("#LeaderboardCards[" + index + "] #Value.Text",
                    FormatUtils.formatDurationLong(entry.bestTimeMs()));
            index++;
        }

        commandBuilder.set("#PageLabel.Text", slice.getLabel());
    }

    private String getRankAccentColor(int rank) {
        return switch (rank) {
            case 1 -> COLOR_RANK_1;
            case 2 -> COLOR_RANK_2;
            case 3 -> COLOR_RANK_3;
            default -> COLOR_RANK_DEFAULT;
        };
    }

    public static class MapLeaderboardData extends ButtonEventData {
        static final String KEY_SEARCH = "@Search";

        public static final BuilderCodec<MapLeaderboardData> CODEC = BuilderCodec.<MapLeaderboardData>builder(
                        MapLeaderboardData.class, MapLeaderboardData::new)
                .addField(new KeyedCodec<>(ButtonEventData.KEY_BUTTON, Codec.STRING),
                        (data, value) -> data.button = value, data -> data.button)
                .addField(new KeyedCodec<>(KEY_SEARCH, Codec.STRING),
                        (data, value) -> data.search = value, data -> data.search)
                .build();

        private String button;
        private String search;

        @Override
        public String getButton() {
            return button;
        }
    }
}
