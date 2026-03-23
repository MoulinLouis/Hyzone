package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.AbstractSearchablePaginatedPage;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.ui.PaginationState;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.util.ParkourUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;

public class AdminPlayersPage extends AbstractSearchablePaginatedPage {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_SELECT_PREFIX = "Player:";
    private static final int PAGE_SIZE = 40;

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final BiConsumer<Ref<EntityStore>, Store<EntityStore>> openIndexCallback;

    public AdminPlayersPage(@Nonnull PlayerRef playerRef, MapStore mapStore, ProgressStore progressStore,
                            BiConsumer<Ref<EntityStore>, Store<EntityStore>> openIndexCallback) {
        super(playerRef, PAGE_SIZE);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.openIndexCallback = openIndexCallback;
    }

    @Override
    protected String getPagePath() {
        return "Pages/Parkour_AdminPlayers.ui";
    }

    @Override
    protected String getSearchFieldId() {
        return "#PlayerSearchField";
    }

    @Override
    protected void bindCustomEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
    }

    @Override
    protected void handleCustomButton(String button, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (BUTTON_BACK.equals(button)) {
            openIndex(ref, store);
            return;
        }
        if (button.startsWith(BUTTON_SELECT_PREFIX)) {
            String raw = button.substring(BUTTON_SELECT_PREFIX.length());
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

    @Override
    protected void buildContent(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#PlayerCards");
        commandBuilder.set("#PlayerSearchField.Value", getSearchText());
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
        String filter = getSearchText().trim().toLowerCase(Locale.ROOT);
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
        PaginationState.PageSlice slice = getPagination().slice(filtered.size());
        int index = 0;
        for (int i = slice.startIndex; i < slice.endIndex; i++) {
            PlayerRow row = filtered.get(i);
            commandBuilder.append("#PlayerCards", "Pages/Parkour_AdminPlayerEntry.ui");
            commandBuilder.set("#PlayerCards[" + index + "] #PlayerName.Text", row.name);
            commandBuilder.set("#PlayerCards[" + index + "] #PlayerProgress.Text",
                    row.completed + "/" + totalMaps + " maps");
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#PlayerCards[" + index + "] #SelectButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + row.playerId), false);
            index++;
        }
        commandBuilder.set("#PageLabel.Text", slice.getLabel());
    }

    private void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (openIndexCallback != null) {
            openIndexCallback.accept(ref, store);
        }
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
}
