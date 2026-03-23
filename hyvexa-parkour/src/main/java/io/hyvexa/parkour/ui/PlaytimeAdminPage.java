package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.AbstractSearchablePaginatedPage;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.ui.PaginationState;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.parkour.data.ProgressStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;

public class PlaytimeAdminPage extends AbstractSearchablePaginatedPage {

    private static final String BUTTON_BACK = "BackButton";
    private final ProgressStore progressStore;
    private final BiConsumer<Ref<EntityStore>, Store<EntityStore>> openIndexCallback;

    public PlaytimeAdminPage(@Nonnull PlayerRef playerRef, ProgressStore progressStore,
                             BiConsumer<Ref<EntityStore>, Store<EntityStore>> openIndexCallback) {
        super(playerRef, 30);
        this.progressStore = progressStore;
        this.openIndexCallback = openIndexCallback;
    }

    @Override
    protected String getPagePath() {
        return "Pages/Parkour_PlaytimeAdmin.ui";
    }

    @Override
    protected String getSearchFieldId() {
        return "#PlaytimeSearchField";
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
        }
    }

    @Override
    protected void buildContent(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#PlaytimeCards");
        commandBuilder.set("#PlaytimeSearchField.Value", getSearchText());
        List<UUID> playerIds = new ArrayList<>(progressStore.getPlayerIds());
        playerIds.sort(Comparator.comparingLong((UUID id) -> progressStore.getPlaytimeMs(id)).reversed());
        String filter = getSearchText() != null ? getSearchText().trim().toLowerCase(Locale.ROOT) : "";
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
        PaginationState.PageSlice slice = getPagination().slice(filtered.size());
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

    private void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (openIndexCallback != null) {
            openIndexCallback.accept(ref, store);
        }
    }

    private String formatDisplayName(UUID playerId) {
        PlayerRef onlineRef = Universe.get().getPlayer(playerId);
        String name = onlineRef != null ? onlineRef.getUsername() : progressStore.getPlayerName(playerId);
        if (name == null || name.isBlank()) {
            return playerId.toString();
        }
        return name;
    }
}
