package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.world.UpdateEnvironmentMusic;
import com.hypixel.hytale.server.core.asset.type.ambiencefx.config.AmbienceFX;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.parkour.visibility.PlayerVisibilityManager;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSettingsPage extends BaseParkourPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_HIDE_ALL = "HideAll";
    private static final String BUTTON_SHOW_ALL = "ShowAll";
    private static final String BUTTON_HIDE_HUD = "HideHud";
    private static final String BUTTON_SHOW_HUD = "ShowHud";
    private static final String BUTTON_PLAY_ZELDA = "PlayZelda";
    private static final String BUTTON_PLAY_CELESTE = "PlayCeleste";
    private static final String DEFAULT_MUSIC_AMBIENCE = "Mus_Fallback_Overground";
    private static final String DEFAULT_MUSIC_AMBIENCE_ALT = "Mus_Fallback_Underground";
    private static final String CELESTE_MUSIC_AMBIENCE = "Mus_Parkour_Celeste";
    private static final String MUSIC_LABEL_SELECTOR = "#CurrentMusicLabel";
    private static final String DEFAULT_MUSIC_LABEL = "Zelda OST";
    private static final ConcurrentHashMap<UUID, String> MUSIC_LABELS = new ConcurrentHashMap<>();

    public PlayerSettingsPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_PlayerSettings.ui");
        uiCommandBuilder.set(MUSIC_LABEL_SELECTOR + ".Text",
                "Now Playing: " + getStoredMusicLabel(playerRef.getUuid()));
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#HideAllButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_HIDE_ALL), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ShowAllButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SHOW_ALL), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#HideHudButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_HIDE_HUD), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ShowHudButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SHOW_HUD), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PlayZeldaMusicButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PLAY_ZELDA), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PlayCelesteMusicButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PLAY_CELESTE), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }
        if (BUTTON_CLOSE.equals(data.getButton())) {
            this.close();
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if (BUTTON_HIDE_ALL.equals(data.getButton())) {
            hideAllPlayers(playerRef);
            player.sendMessage(Message.raw("All players hidden."));
            return;
        }
        if (BUTTON_SHOW_ALL.equals(data.getButton())) {
            showAllPlayers(playerRef);
            player.sendMessage(Message.raw("All players shown."));
        }
        if (BUTTON_HIDE_HUD.equals(data.getButton())) {
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin != null) {
                plugin.hideRunHud(playerRef);
                player.sendMessage(Message.raw("Server HUD hidden."));
            }
            return;
        }
        if (BUTTON_SHOW_HUD.equals(data.getButton())) {
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin != null) {
                plugin.showRunHud(playerRef);
                player.sendMessage(Message.raw("Server HUD shown."));
            }
            return;
        }
        if (BUTTON_PLAY_ZELDA.equals(data.getButton())) {
            restartDefaultMusic(ref, store, playerRef, DEFAULT_MUSIC_LABEL);
            return;
        }
        if (BUTTON_PLAY_CELESTE.equals(data.getButton())) {
            playMusic(ref, store, playerRef, CELESTE_MUSIC_AMBIENCE, "Celeste OST");
        }
    }

    private void hideAllPlayers(@Nonnull PlayerRef viewerRef) {
        Universe.get().getWorlds().forEach((worldId, world) ->
                world.execute(() -> applyHiddenState(viewerRef, world, true)));
    }

    private void showAllPlayers(@Nonnull PlayerRef viewerRef) {
        PlayerVisibilityManager.get().clearHidden(viewerRef.getUuid());
    }

    private void applyHiddenState(@Nonnull PlayerRef viewerRef, @Nonnull World world, boolean hide) {
        if (viewerRef.getReference() == null || !viewerRef.getReference().isValid()) {
            return;
        }
        Store<EntityStore> store = viewerRef.getReference().getStore();
        for (PlayerRef targetRef : world.getPlayerRefs()) {
            if (viewerRef.equals(targetRef)) {
                continue;
            }
            Ref<EntityStore> targetEntityRef = targetRef.getReference();
            if (targetEntityRef == null || !targetEntityRef.isValid()) {
                continue;
            }
            UUIDComponent uuidComponent = store.getComponent(targetEntityRef, UUIDComponent.getComponentType());
            if (uuidComponent == null) {
                continue;
            }
            if (hide) {
                PlayerVisibilityManager.get().hidePlayer(viewerRef.getUuid(), uuidComponent.getUuid());
            } else {
                PlayerVisibilityManager.get().showPlayer(viewerRef.getUuid(), uuidComponent.getUuid());
            }
        }
    }

    private void restartDefaultMusic(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                     @Nonnull PlayerRef playerRef, @Nonnull String label) {
        playMusic(ref, store, playerRef, resolveDefaultMusicIndex(), label);
    }

    private void playMusic(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                           @Nonnull PlayerRef playerRef, @Nonnull String ambienceId,
                           @Nonnull String label) {
        int musicIndex = AmbienceFX.getAssetMap().getIndex(ambienceId);
        playMusic(ref, store, playerRef, musicIndex, label);
    }

    private void playMusic(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                           @Nonnull PlayerRef playerRef, int musicIndex, @Nonnull String label) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        world.execute(() -> {
            if (!ref.isValid() || !playerRef.isValid()) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            var packetHandler = playerRef.getPacketHandler();
            if (packetHandler == null) {
                player.sendMessage(Message.raw("Unable to restart music right now."));
                return;
            }
            if (musicIndex <= 0) {
                player.sendMessage(Message.raw("Music not loaded."));
                return;
            }
            storeMusicLabel(playerRef.getUuid(), label);
            packetHandler.write(new UpdateEnvironmentMusic(musicIndex));
            player.getPageManager().openCustomPage(ref, store, new PlayerSettingsPage(playerRef));
            player.sendMessage(Message.raw("Now playing: " + label + "."));
        });
    }

    private int resolveDefaultMusicIndex() {
        int musicIndex = AmbienceFX.getAssetMap().getIndex(DEFAULT_MUSIC_AMBIENCE);
        if (musicIndex <= 0) {
            musicIndex = AmbienceFX.getAssetMap().getIndex(DEFAULT_MUSIC_AMBIENCE_ALT);
        }
        return musicIndex;
    }

    public static String getStoredMusicLabel(UUID playerId) {
        if (playerId == null) {
            return DEFAULT_MUSIC_LABEL;
        }
        return MUSIC_LABELS.getOrDefault(playerId, DEFAULT_MUSIC_LABEL);
    }

    private static void storeMusicLabel(UUID playerId, String label) {
        if (playerId == null || label == null) {
            return;
        }
        MUSIC_LABELS.put(playerId, label);
    }
}
