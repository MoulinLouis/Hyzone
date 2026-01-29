package io.hyvexa.parkour.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;

import javax.annotation.Nonnull;
import java.util.UUID;

public class ProgressAdminPage extends InteractiveCustomUIPage<ProgressAdminPage.ProgressData> {

    private final ProgressStore progressStore;
    private String playerUuidInput = "";
    private String playerNameInput = "";
    private String mapIdInput = "";
    private String summaryText = "Enter a player UUID to delete progress.";
    private String refreshStatusText = "Enter a player name to reload progress from disk.";
    private String purgeStatusText = "Enter a map id to purge and recalculate XP.";
    private static final String BUTTON_BACK = "BackButton";
    private static final String BUTTON_DELETE = "DeleteProgress";
    private static final String BUTTON_PURGE = "PurgeMapProgress";
    private static final String BUTTON_REFRESH = "RefreshProgress";

    public ProgressAdminPage(@Nonnull PlayerRef playerRef, ProgressStore progressStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ProgressData.CODEC);
        this.progressStore = progressStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_ProgressAdmin.ui");
        bindEvents(uiEventBuilder);
        populateFields(uiCommandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ProgressData data) {
        super.handleDataEvent(ref, store, data);
        if (data.playerUuid != null) {
            playerUuidInput = data.playerUuid.trim();
        }
        if (data.mapId != null) {
            mapIdInput = data.mapId.trim();
        }
        if (data.playerName != null) {
            playerNameInput = data.playerName.trim();
        }
        if (data.button == null) {
            return;
        }
        if (BUTTON_BACK.equals(data.button)) {
            openIndex(ref, store);
            return;
        }
        if (BUTTON_DELETE.equals(data.button)) {
            handleDelete(ref, store);
            return;
        }
        if (BUTTON_PURGE.equals(data.button)) {
            handleMapPurge(ref, store);
            return;
        }
        if (BUTTON_REFRESH.equals(data.button)) {
            handleRefresh(ref, store);
        }
    }

    private void handleDelete(Ref<EntityStore> ref, Store<EntityStore> store) {
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

    private void handleMapPurge(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        String mapId = mapIdInput != null ? mapIdInput.trim() : "";
        if (mapId.isEmpty()) {
            player.sendMessage(Message.raw("Map id is required."));
            purgeStatusText = "Map id is required.";
            sendRefresh(ref, store);
            return;
        }
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        MapStore mapStore = plugin != null ? plugin.getMapStore() : null;
        ProgressStore.MapPurgeResult result = progressStore.purgeMapProgress(mapId, mapStore);
        if (result.playersUpdated == 0) {
            purgeStatusText = "No progress found for map '" + mapId + "'.";
        } else {
            purgeStatusText = "Removed map '" + mapId + "' from " + result.playersUpdated
                    + " player(s). XP recalculated (total " + result.totalXpRemoved + " removed).";
        }
        sendRefresh(ref, store);
    }

    private UUID parseUuid(Player player) {
        if (playerUuidInput == null || playerUuidInput.isBlank()) {
            if (player != null) {
                player.sendMessage(Message.raw("Enter a player UUID."));
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

    private void handleRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerNameInput == null || playerNameInput.isBlank()) {
            if (player != null) {
                player.sendMessage(Message.raw("Enter a player name to refresh."));
            }
            refreshStatusText = "Player name is required.";
            sendRefresh(ref, store);
            return;
        }
        progressStore.syncLoad();
        String trimmedName = playerNameInput.trim();
        UUID playerId = null;
        int matchCount = 0;
        for (UUID candidateId : progressStore.getPlayerIds()) {
            String storedName = progressStore.getPlayerName(candidateId);
            if (storedName == null) {
                continue;
            }
            if (storedName.equalsIgnoreCase(trimmedName)) {
                playerId = candidateId;
                matchCount++;
            }
        }
        if (matchCount == 0) {
            refreshStatusText = "No player found named '" + playerNameInput + "'.";
            sendRefresh(ref, store);
            return;
        }
        if (matchCount > 1) {
            refreshStatusText = "Multiple players found named '" + playerNameInput + "'. Use UUID.";
            sendRefresh(ref, store);
            return;
        }
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin != null) {
            plugin.invalidateRankCache(playerId);
        }
        refreshStatusText = "Reloaded progress for " + playerNameInput + " (" + playerId + ").";
        sendRefresh(ref, store);
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

    private void populateFields(UICommandBuilder commandBuilder) {
        commandBuilder.set("#ProgressSummary.Text", summaryText);
        commandBuilder.set("#RefreshStatus.Text", refreshStatusText);
        commandBuilder.set("#MapPurgeStatus.Text", purgeStatusText);
        commandBuilder.set("#PlayerUuidField.Value", playerUuidInput);
        commandBuilder.set("#PlayerNameField.Value", playerNameInput);
        commandBuilder.set("#MapPurgeIdField.Value", mapIdInput);
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateFields(commandBuilder);
        bindEvents(eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ProgressData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PlayerUuidField",
                EventData.of(ProgressData.KEY_PLAYER_UUID, "#PlayerUuidField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteProgressButton",
                EventData.of(ProgressData.KEY_BUTTON, BUTTON_DELETE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PlayerNameField",
                EventData.of(ProgressData.KEY_PLAYER_NAME, "#PlayerNameField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshProgressButton",
                EventData.of(ProgressData.KEY_BUTTON, BUTTON_REFRESH), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapPurgeIdField",
                EventData.of(ProgressData.KEY_MAP_ID, "#MapPurgeIdField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PurgeMapButton",
                EventData.of(ProgressData.KEY_BUTTON, BUTTON_PURGE), false);
    }

    public static class ProgressData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_PLAYER_UUID = "@PlayerUuid";
        static final String KEY_PLAYER_NAME = "@PlayerName";
        static final String KEY_MAP_ID = "@MapId";

        public static final BuilderCodec<ProgressData> CODEC = BuilderCodec.<ProgressData>builder(ProgressData.class,
                        ProgressData::new)
                .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING),
                        (data, value) -> data.button = value, data -> data.button)
                .addField(new KeyedCodec<>(KEY_PLAYER_UUID, Codec.STRING),
                        (data, value) -> data.playerUuid = value, data -> data.playerUuid)
                .addField(new KeyedCodec<>(KEY_PLAYER_NAME, Codec.STRING),
                        (data, value) -> data.playerName = value, data -> data.playerName)
                .addField(new KeyedCodec<>(KEY_MAP_ID, Codec.STRING),
                        (data, value) -> data.mapId = value, data -> data.mapId)
                .build();

        private String button;
        private String playerUuid;
        private String playerName;
        private String mapId;
    }

}
