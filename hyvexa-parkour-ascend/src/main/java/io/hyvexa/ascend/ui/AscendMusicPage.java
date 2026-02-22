package io.hyvexa.ascend.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.world.UpdateEnvironmentMusic;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.ambiencefx.config.AmbienceFX;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.common.ui.ButtonEventData;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AscendMusicPage extends BaseAscendPage {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_PLAY_ZELDA = "PlayZelda";
    private static final String BUTTON_PLAY_CELESTE = "PlayCeleste";
    private static final String BUTTON_PLAY_DEFAULT = "PlayDefault";
    private static final String BUTTON_PLAY_NONE = "PlayNone";
    private static final String BUTTON_TOGGLE_CHECKPOINT_SFX = "ToggleCheckpointSfx";
    private static final String BUTTON_TOGGLE_VICTORY_SFX = "ToggleVictorySfx";

    private static final String DEFAULT_MUSIC_AMBIENCE = "Mus_Fallback_Overground";
    private static final String DEFAULT_MUSIC_AMBIENCE_ALT = "Mus_Fallback_Underground";
    private static final String CELESTE_MUSIC_AMBIENCE = "Mus_Parkour_Celeste";
    private static final String NO_MUSIC_AMBIENCE = "AmbFX_Void";
    private static final String HYTALE_DEFAULT_MUSIC_AMBIENCE = "Mus_Forgotten_Temple";

    private static final String DEFAULT_MUSIC_LABEL = "Zelda OST";
    private static final String HYTALE_MUSIC_LABEL = "Default Music";
    private static final String NO_MUSIC_LABEL = "No Music";
    private static final String SFX_ENABLED_LABEL = "Disable";
    private static final String SFX_DISABLED_LABEL = "Enable";

    private static final ConcurrentHashMap<UUID, String> MUSIC_LABELS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, MusicSelection> MUSIC_SELECTIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> CHECKPOINT_SFX_ENABLED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> VICTORY_SFX_ENABLED = new ConcurrentHashMap<>();

    private final AscendPlayerStore playerStore;
    private final RobotManager robotManager;

    public AscendMusicPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore, RobotManager robotManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.robotManager = robotManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_PlayerMusic.ui");

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        UUID playerId = playerRef != null ? playerRef.getUuid() : null;

        commandBuilder.set("#CurrentMusicLabel.Text",
                "Now Playing: " + getStoredMusicLabel(playerId));
        commandBuilder.set("#CheckpointSfxButton.Text", getCheckpointSfxLabel(playerId));
        commandBuilder.set("#VictorySfxButton.Text", getVictorySfxLabel(playerId));
        applySfxIndicators(commandBuilder, playerId);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PlayZeldaMusicButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PLAY_ZELDA), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PlayCelesteMusicButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PLAY_CELESTE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PlayDefaultMusicButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PLAY_DEFAULT), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PlayNoMusicButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PLAY_NONE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CheckpointSfxButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TOGGLE_CHECKPOINT_SFX), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#VictorySfxButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TOGGLE_VICTORY_SFX), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }

        if (BUTTON_BACK.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store,
                    new AscendSettingsPage(playerRef, playerStore, robotManager));
            return;
        }
        if (BUTTON_PLAY_ZELDA.equals(data.getButton())) {
            restartDefaultMusic(ref, store, playerRef, DEFAULT_MUSIC_LABEL);
            return;
        }
        if (BUTTON_PLAY_CELESTE.equals(data.getButton())) {
            playMusic(ref, store, playerRef, CELESTE_MUSIC_AMBIENCE, "Celeste OST");
            return;
        }
        if (BUTTON_PLAY_DEFAULT.equals(data.getButton())) {
            playMusic(ref, store, playerRef, HYTALE_DEFAULT_MUSIC_AMBIENCE, HYTALE_MUSIC_LABEL);
            return;
        }
        if (BUTTON_PLAY_NONE.equals(data.getButton())) {
            playMusic(ref, store, playerRef, NO_MUSIC_AMBIENCE, NO_MUSIC_LABEL);
            return;
        }
        if (BUTTON_TOGGLE_CHECKPOINT_SFX.equals(data.getButton())) {
            toggleCheckpointSfx(playerRef.getUuid());
            player.getPageManager().openCustomPage(ref, store,
                    new AscendMusicPage(playerRef, playerStore, robotManager));
            return;
        }
        if (BUTTON_TOGGLE_VICTORY_SFX.equals(data.getButton())) {
            toggleVictorySfx(playerRef.getUuid());
            player.getPageManager().openCustomPage(ref, store,
                    new AscendMusicPage(playerRef, playerStore, robotManager));
        }
    }

    private void restartDefaultMusic(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                     @Nonnull PlayerRef playerRef, @Nonnull String label) {
        playMusic(ref, store, playerRef, resolveDefaultMusicIndex(), label,
                MusicSelection.defaultSelection(label), false);
    }

    private void playMusic(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                           @Nonnull PlayerRef playerRef, @Nonnull String ambienceId,
                           @Nonnull String label) {
        int musicIndex = AmbienceFX.getAssetMap().getIndex(ambienceId);
        playMusic(ref, store, playerRef, musicIndex, label,
                MusicSelection.ambienceSelection(label, ambienceId), false);
    }

    private void playMusic(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                           @Nonnull PlayerRef playerRef, int musicIndex, @Nonnull String label,
                           @Nonnull MusicSelection selection, boolean allowZero) {
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
                player.sendMessage(Message.raw("Unable to change music right now."));
                return;
            }
            if (musicIndex <= AmbienceFX.EMPTY_ID && !allowZero) {
                player.sendMessage(Message.raw("Music not loaded."));
                return;
            }
            storeMusicLabel(playerRef.getUuid(), label);
            storeMusicSelection(playerRef.getUuid(), selection);
            packetHandler.write(new UpdateEnvironmentMusic(musicIndex));
            player.getPageManager().openCustomPage(ref, store,
                    new AscendMusicPage(playerRef, playerStore, robotManager));
            player.sendMessage(Message.raw("Now playing: " + label + "."));
        });
    }

    private static int resolveDefaultMusicIndex() {
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
        String storedLabel = MUSIC_LABELS.get(playerId);
        return storedLabel == null ? DEFAULT_MUSIC_LABEL : storedLabel;
    }

    private static void storeMusicLabel(UUID playerId, String label) {
        if (playerId == null || label == null) {
            return;
        }
        MUSIC_LABELS.put(playerId, label);
    }

    private static void storeMusicSelection(UUID playerId, MusicSelection selection) {
        if (playerId == null || selection == null) {
            return;
        }
        MUSIC_SELECTIONS.put(playerId, selection);
    }

    public static void applyStoredMusic(@Nonnull PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
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
                return;
            }
            Integer musicIndex = resolveStoredMusicIndex(playerRef.getUuid());
            if (musicIndex == null || musicIndex <= AmbienceFX.EMPTY_ID) {
                return;
            }
            packetHandler.write(new UpdateEnvironmentMusic(musicIndex));
        });
    }

    private static Integer resolveStoredMusicIndex(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        MusicSelection selection = MUSIC_SELECTIONS.get(playerId);
        if (selection == null && MUSIC_LABELS.containsKey(playerId)) {
            selection = MusicSelection.fromLabel(MUSIC_LABELS.get(playerId));
        }
        if (selection == null) {
            return null;
        }
        if (selection.type == MusicSelectionType.DEFAULT) {
            return resolveDefaultMusicIndex();
        }
        if (selection.ambienceId == null) {
            return null;
        }
        return AmbienceFX.getAssetMap().getIndex(selection.ambienceId);
    }

    public static boolean isCheckpointSfxEnabled(UUID playerId) {
        return playerId != null && CHECKPOINT_SFX_ENABLED.getOrDefault(playerId, true);
    }

    public static boolean isVictorySfxEnabled(UUID playerId) {
        return playerId != null && VICTORY_SFX_ENABLED.getOrDefault(playerId, true);
    }

    private static void toggleCheckpointSfx(UUID playerId) {
        if (playerId == null) {
            return;
        }
        boolean enabled = CHECKPOINT_SFX_ENABLED.getOrDefault(playerId, true);
        CHECKPOINT_SFX_ENABLED.put(playerId, !enabled);
    }

    private static void toggleVictorySfx(UUID playerId) {
        if (playerId == null) {
            return;
        }
        boolean enabled = VICTORY_SFX_ENABLED.getOrDefault(playerId, true);
        VICTORY_SFX_ENABLED.put(playerId, !enabled);
    }

    private static void applySfxIndicators(UICommandBuilder cmd, UUID playerId) {
        boolean checkpointEnabled = isCheckpointSfxEnabled(playerId);
        cmd.set("#CheckpointSfxIndicator.Visible", checkpointEnabled);
        boolean victoryEnabled = isVictorySfxEnabled(playerId);
        cmd.set("#VictorySfxIndicator.Visible", victoryEnabled);
    }

    private static String getCheckpointSfxLabel(UUID playerId) {
        return (isCheckpointSfxEnabled(playerId) ? SFX_ENABLED_LABEL : SFX_DISABLED_LABEL) + " Checkpoint SFX";
    }

    private static String getVictorySfxLabel(UUID playerId) {
        return (isVictorySfxEnabled(playerId) ? SFX_ENABLED_LABEL : SFX_DISABLED_LABEL) + " Victory SFX";
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        MUSIC_LABELS.remove(playerId);
        MUSIC_SELECTIONS.remove(playerId);
        CHECKPOINT_SFX_ENABLED.remove(playerId);
        VICTORY_SFX_ENABLED.remove(playerId);
    }

    private enum MusicSelectionType {
        DEFAULT,
        AMBIENCE_ID
    }

    private static final class MusicSelection {
        private final MusicSelectionType type;
        private final String ambienceId;
        private final String label;

        private MusicSelection(MusicSelectionType type, String ambienceId, String label) {
            this.type = type;
            this.ambienceId = ambienceId;
            this.label = label;
        }

        private static MusicSelection defaultSelection(String label) {
            return new MusicSelection(MusicSelectionType.DEFAULT, null, label);
        }

        private static MusicSelection ambienceSelection(String label, String ambienceId) {
            return new MusicSelection(MusicSelectionType.AMBIENCE_ID, ambienceId, label);
        }

        private static MusicSelection fromLabel(String label) {
            if (label == null) {
                return null;
            }
            if (DEFAULT_MUSIC_LABEL.equals(label)) {
                return defaultSelection(label);
            }
            if ("Celeste OST".equals(label)) {
                return ambienceSelection(label, CELESTE_MUSIC_AMBIENCE);
            }
            if (HYTALE_MUSIC_LABEL.equals(label)) {
                return ambienceSelection(label, HYTALE_DEFAULT_MUSIC_AMBIENCE);
            }
            if (NO_MUSIC_LABEL.equals(label)) {
                return ambienceSelection(label, NO_MUSIC_AMBIENCE);
            }
            return null;
        }
    }
}
