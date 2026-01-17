package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class PlaytimeAdminPage extends BaseParkourPage {

    private static final String BUTTON_BACK = "BackButton";
    private final ProgressStore progressStore;

    public PlaytimeAdminPage(@Nonnull PlayerRef playerRef, ProgressStore progressStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.progressStore = progressStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_PlaytimeAdmin.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        buildPlaytimeList(uiCommandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }
        if (BUTTON_BACK.equals(data.getButton())) {
            openIndex(ref, store);
        }
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
        List<UUID> playerIds = new ArrayList<>(progressStore.getPlayerIds());
        playerIds.sort(Comparator.comparingLong((UUID id) -> progressStore.getPlaytimeMs(id)).reversed());
        int index = 0;
        for (UUID playerId : playerIds) {
            commandBuilder.append("#PlaytimeCards", "Pages/Parkour_PlaytimeEntry.ui");
            String name = formatDisplayName(playerId);
            long playtimeMs = progressStore.getPlaytimeMs(playerId);
            commandBuilder.set("#PlaytimeCards[" + index + "] #PlaytimeName.Text", name);
            commandBuilder.set("#PlaytimeCards[" + index + "] #PlaytimeValue.Text",
                    FormatUtils.formatPlaytime(playtimeMs));
            index++;
        }
        if (playerIds.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No playtime tracked yet.");
        } else {
            commandBuilder.set("#EmptyText.Text", "");
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
