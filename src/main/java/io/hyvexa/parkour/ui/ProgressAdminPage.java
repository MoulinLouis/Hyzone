package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ProgressAdminPage extends BaseParkourPage {

    private final ProgressStore progressStore;
    private String playerUuidInput = "";
    private String summaryText = "Select a player to view progress.";
    private static final String BUTTON_BACK = "BackButton";
    private static final String BUTTON_RESET = "ResetProgress";
    private static final String BUTTON_SELECT_PREFIX = "Select:";

    public ProgressAdminPage(@Nonnull PlayerRef playerRef, ProgressStore progressStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.progressStore = progressStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_ProgressAdmin.ui");
        bindEvents(uiEventBuilder);
        populateFields(uiCommandBuilder);
        buildPlayerList(uiCommandBuilder, uiEventBuilder);
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
            return;
        }
        if (data.getButton().startsWith(BUTTON_SELECT_PREFIX)) {
            playerUuidInput = data.getButton().substring(BUTTON_SELECT_PREFIX.length());
            handleLoad(ref, store);
            return;
        }
        if (BUTTON_RESET.equals(data.getButton())) {
            handleReset(ref, store);
        }
    }

    private void handleLoad(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        UUID target = parseUuid(player);
        if (player == null || target == null) {
            return;
        }
        summaryText = buildSummary(target);
        sendRefresh(ref, store);
    }

    private void handleReset(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        UUID target = parseUuid(player);
        if (player == null || target == null) {
            return;
        }
        boolean cleared = progressStore.clearProgress(target);
        summaryText = cleared
                ? "Progress cleared for " + target + "."
                : "No progress found for " + target + ".";
        sendRefresh(ref, store);
    }

    private UUID parseUuid(Player player) {
        if (playerUuidInput.isEmpty()) {
            if (player != null) {
                player.sendMessage(Message.raw("Select a player first."));
            }
            return null;
        }
        try {
            return UUID.fromString(playerUuidInput);
        } catch (IllegalArgumentException ex) {
            if (player != null) {
                player.sendMessage(Message.raw("Invalid UUID format."));
            }
            return null;
        }
    }

    private String buildSummary(UUID playerId) {
        long xp = progressStore.getXp(playerId);
        MapStore mapStore = HyvexaPlugin.getInstance() != null
                ? HyvexaPlugin.getInstance().getMapStore()
                : null;
        String rankName = progressStore.getRankName(playerId, mapStore);
        int completed = progressStore.getCompletedMapCount(playerId);
        Set<String> titles = progressStore.getTitles(playerId);
        return "Player: " + formatDisplayName(playerId)
                + "\nRank: " + rankName + " (" + xp + " XP)"
                + "\nCompleted maps: " + completed
                + "\nTitles: " + formatList(titles);
    }

    private void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        MapStore mapStore = HyvexaPlugin.getInstance().getMapStore();
        player.getPageManager().openCustomPage(ref, store,
                new AdminIndexPage(playerRef, mapStore, progressStore, HyvexaPlugin.getInstance().getSettingsStore()));
    }

    private void populateFields(UICommandBuilder commandBuilder) {
        commandBuilder.set("#ProgressSummary.Text", summaryText);
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateFields(commandBuilder);
        buildPlayerList(commandBuilder, eventBuilder);
        bindEvents(eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ResetProgressButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_RESET), false);
    }

    private void buildPlayerList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#PlayerCards");
        List<UUID> playerIds = new ArrayList<>(progressStore.getPlayerIds());
        playerIds.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
        int index = 0;
        for (UUID playerId : playerIds) {
            commandBuilder.append("#PlayerCards", "Pages/Parkour_PlayerEntry.ui");
            String displayName = formatDisplayName(playerId);
            commandBuilder.set("#PlayerCards[" + index + "] #PlayerName.Text", displayName);
            boolean isSelected = playerId.toString().equals(playerUuidInput);
            if (isSelected) {
                commandBuilder.set("#PlayerCards[" + index + "].Background", "#253742");
                commandBuilder.set("#PlayerCards[" + index + "] #PlayerName.Text", ">> " + displayName);
            }
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#PlayerCards[" + index + "]",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + playerId), false);
            index++;
        }
    }

    private String formatDisplayName(UUID playerId) {
        PlayerRef onlineRef = Universe.get().getPlayer(playerId);
        String name = onlineRef != null ? onlineRef.getUsername() : progressStore.getPlayerName(playerId);
        if (name == null || name.isBlank()) {
            return playerId.toString();
        }
        return name + " (" + playerId + ")";
    }

    private static String formatList(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "None";
        }
        List<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", sorted);
    }

}
