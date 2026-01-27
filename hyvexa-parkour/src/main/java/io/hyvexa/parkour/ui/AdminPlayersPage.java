package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.util.ParkourUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AdminPlayersPage extends InteractiveCustomUIPage<AdminPlayersPage.AdminPlayersData> {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_PREV = "PrevPage";
    private static final String BUTTON_NEXT = "NextPage";
    private static final String BUTTON_SELECT_PREFIX = "Player:";
    private static final int PAGE_SIZE = 40;

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final PaginationState pagination = new PaginationState(PAGE_SIZE);
    private String searchText = "";

    public AdminPlayersPage(@Nonnull PlayerRef playerRef, MapStore mapStore, ProgressStore progressStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, AdminPlayersData.CODEC);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_AdminPlayers.ui");
        bindEvents(uiEventBuilder);
        buildPlayersList(uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull AdminPlayersData data) {
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
        if (BUTTON_PREV.equals(data.getButton())) {
            pagination.previous();
            sendRefresh();
            return;
        }
        if (BUTTON_NEXT.equals(data.getButton())) {
            pagination.next();
            sendRefresh();
            return;
        }
        if (BUTTON_BACK.equals(data.getButton())) {
            openIndex(ref, store);
            return;
        }
        if (data.getButton().startsWith(BUTTON_SELECT_PREFIX)) {
            String raw = data.getButton().substring(BUTTON_SELECT_PREFIX.length());
            UUID targetId = parsePlayerId(raw, store, ref);
            if (targetId == null) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || playerRef == null) {
                return;
            }
            player.getPageManager().openCustomPage(ref, store,
                    new AdminPlayerStatsPage(playerRef, mapStore, progressStore, targetId));
        }
    }

    private void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new AdminIndexPage(playerRef, mapStore, progressStore, plugin.getSettingsStore(),
                        plugin.getPlayerCountStore()));
    }

    private UUID parsePlayerId(String raw, Store<EntityStore> store, Ref<EntityStore> ref) {
        if (raw == null || raw.isBlank()) {
            sendMessage(store, ref, "Invalid player selection.");
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            sendMessage(store, ref, "Invalid player id.");
            return null;
        }
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindEvents(eventBuilder);
        buildPlayersList(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PlayerSearchField",
                EventData.of(AdminPlayersData.KEY_SEARCH, "#PlayerSearchField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PREV), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NEXT), false);
    }

    private void buildPlayersList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#PlayerCards");
        commandBuilder.set("#PlayerSearchField.Value", searchText);
        int totalMaps = mapStore != null ? mapStore.listMaps().size() : 0;
        List<PlayerRow> rows = new ArrayList<>();
        for (UUID playerId : progressStore.getPlayerIds()) {
            String name = ParkourUtils.resolveName(playerId, progressStore);
            rows.add(new PlayerRow(playerId, name, progressStore.getCompletedMapCount(playerId)));
        }
        if (rows.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No player progress recorded yet.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }
        rows.sort(Comparator.comparing((PlayerRow row) -> row.name.toLowerCase(Locale.ROOT))
                .thenComparing(row -> row.playerId));
        String filter = searchText != null ? searchText.trim().toLowerCase(Locale.ROOT) : "";
        List<PlayerRow> filtered = new ArrayList<>();
        for (PlayerRow row : rows) {
            if (!filter.isEmpty() && !row.name.toLowerCase(Locale.ROOT).startsWith(filter)) {
                continue;
            }
            filtered.add(row);
        }
        if (filtered.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No matches.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }
        commandBuilder.set("#EmptyText.Text", "");
        PaginationState.PageSlice slice = pagination.slice(filtered.size());
        int index = 0;
        for (int i = slice.startIndex; i < slice.endIndex; i++) {
            PlayerRow row = filtered.get(i);
            commandBuilder.append("#PlayerCards", "Pages/Parkour_AdminPlayerEntry.ui");
            commandBuilder.set("#PlayerCards[" + index + "] #PlayerName.Text", row.name);
            commandBuilder.set("#PlayerCards[" + index + "] #PlayerProgress.Text",
                    row.completed + "/" + totalMaps + " maps");
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#PlayerCards[" + index + "]",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + row.playerId), false);
            index++;
        }
        commandBuilder.set("#PageLabel.Text", slice.getLabel());
    }

    private void sendMessage(Store<EntityStore> store, Ref<EntityStore> ref, String text) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(text));
        }
    }

    private static final class PlayerRow {
        private final UUID playerId;
        private final String name;
        private final int completed;

        private PlayerRow(UUID playerId, String name, int completed) {
            this.playerId = playerId;
            this.name = name != null ? name : playerId.toString();
            this.completed = completed;
        }
    }

    public static class AdminPlayersData extends ButtonEventData {
        static final String KEY_SEARCH = "@Search";

        public static final BuilderCodec<AdminPlayersData> CODEC = BuilderCodec.<AdminPlayersData>builder(AdminPlayersData.class,
                        AdminPlayersData::new)
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
