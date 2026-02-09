package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.ui.PaginationState;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PlaytimeAdminPage extends InteractiveCustomUIPage<PlaytimeAdminPage.PlaytimeData> {

    private static final String BUTTON_BACK = "BackButton";
    private static final String BUTTON_PREV = "PrevPage";
    private static final String BUTTON_NEXT = "NextPage";
    private final ProgressStore progressStore;
    private final PaginationState pagination = new PaginationState(30);
    private String searchText = "";

    public PlaytimeAdminPage(@Nonnull PlayerRef playerRef, ProgressStore progressStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PlaytimeData.CODEC);
        this.progressStore = progressStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_PlaytimeAdmin.ui");
        bindEvents(uiEventBuilder);
        buildPlaytimeList(uiCommandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PlaytimeData data) {
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
        }
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindEvents(eventBuilder);
        buildPlaytimeList(commandBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PlaytimeSearchField",
                EventData.of(PlaytimeData.KEY_SEARCH, "#PlaytimeSearchField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PREV), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NEXT), false);
    }

    private void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        MapStore mapStore = HyvexaPlugin.getInstance().getMapStore();
        player.getPageManager().openCustomPage(ref, store,
                new AdminIndexPage(playerRef, mapStore, progressStore, HyvexaPlugin.getInstance().getSettingsStore(),
                        HyvexaPlugin.getInstance().getPlayerCountStore()));
    }

    private void buildPlaytimeList(UICommandBuilder commandBuilder) {
        commandBuilder.clear("#PlaytimeCards");
        commandBuilder.set("#PlaytimeSearchField.Value", searchText);
        List<UUID> playerIds = new ArrayList<>(progressStore.getPlayerIds());
        playerIds.sort(Comparator.comparingLong((UUID id) -> progressStore.getPlaytimeMs(id)).reversed());
        String filter = searchText != null ? searchText.trim().toLowerCase(Locale.ROOT) : "";
        List<UUID> filtered = new ArrayList<>();
        for (UUID playerId : playerIds) {
            if (filter.isEmpty()) {
                filtered.add(playerId);
                continue;
            }
            String name = formatDisplayName(playerId);
            String safeName = name != null ? name : "";
            if (safeName.toLowerCase(Locale.ROOT).startsWith(filter)) {
                filtered.add(playerId);
            }
        }
        if (filtered.isEmpty()) {
            commandBuilder.set("#AveragePlaytime.Text", "Average playtime: --");
            commandBuilder.set("#EmptyText.Text", playerIds.isEmpty() ? "No playtime tracked yet." : "No matches.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }
        commandBuilder.set("#EmptyText.Text", "");
        PaginationState.PageSlice slice = pagination.slice(filtered.size());
        long totalPlaytimeMs = 0L;
        for (UUID playerId : filtered) {
            totalPlaytimeMs += progressStore.getPlaytimeMs(playerId);
        }
        int index = 0;
        for (int i = slice.startIndex; i < slice.endIndex; i++) {
            UUID playerId = filtered.get(i);
            commandBuilder.append("#PlaytimeCards", "Pages/Parkour_PlaytimeEntry.ui");
            String name = formatDisplayName(playerId);
            long playtimeMs = progressStore.getPlaytimeMs(playerId);
            commandBuilder.set("#PlaytimeCards[" + index + "] #PlaytimeName.Text", name);
            commandBuilder.set("#PlaytimeCards[" + index + "] #PlaytimeValue.Text",
                    FormatUtils.formatPlaytime(playtimeMs));
            index++;
        }
        long averageMs = totalPlaytimeMs / Math.max(1, filtered.size());
        commandBuilder.set("#AveragePlaytime.Text", "Average playtime: " + FormatUtils.formatPlaytime(averageMs));
        commandBuilder.set("#PageLabel.Text", slice.getLabel());
    }

    private String formatDisplayName(UUID playerId) {
        PlayerRef onlineRef = Universe.get().getPlayer(playerId);
        String name = onlineRef != null ? onlineRef.getUsername() : progressStore.getPlayerName(playerId);
        if (name == null || name.isBlank()) {
            return playerId.toString();
        }
        return name;
    }

    public static class PlaytimeData extends ButtonEventData {
        static final String KEY_SEARCH = "@Search";

        public static final BuilderCodec<PlaytimeData> CODEC = BuilderCodec.<PlaytimeData>builder(PlaytimeData.class,
                        PlaytimeData::new)
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
