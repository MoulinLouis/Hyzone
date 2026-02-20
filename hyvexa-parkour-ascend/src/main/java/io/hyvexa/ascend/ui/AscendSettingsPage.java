package io.hyvexa.ascend.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.common.visibility.EntityVisibilityManager;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AscendSettingsPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_MUSIC = "Music";
    private static final String BUTTON_HIDE_HUD = "HideHud";
    private static final String BUTTON_SHOW_HUD = "ShowHud";
    private static final String BUTTON_TOGGLE_RUNNERS = "ToggleRunners";
    private static final String BUTTON_HIDE_ALL = "HideAll";
    private static final String BUTTON_SHOW_ALL = "ShowAll";
    private static final ConcurrentHashMap<UUID, Boolean> PLAYERS_HIDDEN = new ConcurrentHashMap<>();

    private final AscendPlayerStore playerStore;
    private final RobotManager robotManager;
    private final boolean fromProfile;

    public AscendSettingsPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore, RobotManager robotManager) {
        this(playerRef, playerStore, robotManager, false);
    }

    public AscendSettingsPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore,
                              RobotManager robotManager, boolean fromProfile) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.robotManager = robotManager;
        this.fromProfile = fromProfile;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Settings.ui");

        if (fromProfile) {
            commandBuilder.set("#CloseButton.Text", "Back");
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        UUID playerId = playerRef != null ? playerRef.getUuid() : null;

        // Set runners toggle label
        boolean runnersHidden = playerId != null && playerStore.isHideOtherRunners(playerId);
        commandBuilder.set("#ToggleRunnersButton.Text", runnersHidden ? "Hide: On" : "Hide: Off");

        // Set HUD and player visibility indicators
        applyIndicators(commandBuilder, playerId);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MusicButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_MUSIC), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#HideHudButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_HIDE_HUD), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ShowHudButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SHOW_HUD), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleRunnersButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TOGGLE_RUNNERS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#HideAllButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_HIDE_ALL), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ShowAllButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SHOW_ALL), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }

        if (BUTTON_CLOSE.equals(data.getButton())) {
            if (fromProfile) {
                Player p = store.getComponent(ref, Player.getComponentType());
                PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
                if (p != null && pr != null) {
                    p.getPageManager().openCustomPage(ref, store,
                            new AscendProfilePage(pr, playerStore, robotManager));
                } else {
                    this.close();
                }
            } else {
                this.close();
            }
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }

        if (BUTTON_MUSIC.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store,
                    new AscendMusicPage(playerRef, playerStore, robotManager));
            return;
        }

        if (BUTTON_HIDE_HUD.equals(data.getButton())) {
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin != null && plugin.getHudManager() != null) {
                plugin.getHudManager().hideHud(playerRef.getUuid());
                player.sendMessage(Message.raw("HUD hidden."));
                player.getPageManager().openCustomPage(ref, store,
                        new AscendSettingsPage(playerRef, playerStore, robotManager, fromProfile));
            }
            return;
        }

        if (BUTTON_SHOW_HUD.equals(data.getButton())) {
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin != null && plugin.getHudManager() != null) {
                plugin.getHudManager().showHud(playerRef.getUuid());
                player.sendMessage(Message.raw("HUD shown."));
                player.getPageManager().openCustomPage(ref, store,
                        new AscendSettingsPage(playerRef, playerStore, robotManager, fromProfile));
            }
            return;
        }

        if (BUTTON_TOGGLE_RUNNERS.equals(data.getButton())) {
            handleToggleRunners(ref, store, player, playerRef);
            return;
        }

        if (BUTTON_HIDE_ALL.equals(data.getButton())) {
            hideAllPlayers(playerRef);
            PLAYERS_HIDDEN.put(playerRef.getUuid(), true);
            player.sendMessage(Message.raw("All players hidden."));
            player.getPageManager().openCustomPage(ref, store,
                    new AscendSettingsPage(playerRef, playerStore, robotManager, fromProfile));
            return;
        }

        if (BUTTON_SHOW_ALL.equals(data.getButton())) {
            showAllPlayers(playerRef);
            PLAYERS_HIDDEN.remove(playerRef.getUuid());
            player.sendMessage(Message.raw("All players shown."));
            player.getPageManager().openCustomPage(ref, store,
                    new AscendSettingsPage(playerRef, playerStore, robotManager, fromProfile));
            return;
        }
    }

    private void handleToggleRunners(Ref<EntityStore> ref, Store<EntityStore> store,
                                     Player player, PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        boolean current = playerStore.isHideOtherRunners(playerId);
        boolean newState = !current;
        playerStore.setHideOtherRunners(playerId, newState);

        if (robotManager != null) {
            robotManager.applyRunnerVisibility(playerId);
        }

        if (newState) {
            player.sendMessage(Message.raw("[Settings] Other players' runners are now hidden.")
                    .color(SystemMessageUtils.SUCCESS));
        } else {
            player.sendMessage(Message.raw("[Settings] Other players' runners are now visible.")
                    .color(SystemMessageUtils.SECONDARY));
        }

        player.getPageManager().openCustomPage(ref, store,
                new AscendSettingsPage(playerRef, playerStore, robotManager, fromProfile));
    }

    private void applyIndicators(UICommandBuilder cmd, UUID playerId) {
        // HUD indicators
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        boolean hudHidden = plugin != null && plugin.getHudManager() != null
                && plugin.getHudManager().isHudHidden(playerId);
        cmd.set("#HideHudIndicator.Visible", hudHidden);
        cmd.set("#ShowHudIndicator.Visible", !hudHidden);

        // Player visibility indicators
        boolean playersHidden = isPlayersHidden(playerId);
        cmd.set("#HidePlayersIndicator.Visible", playersHidden);
        cmd.set("#ShowPlayersIndicator.Visible", !playersHidden);
    }

    private static boolean isPlayersHidden(UUID playerId) {
        return playerId != null && Boolean.TRUE.equals(PLAYERS_HIDDEN.get(playerId));
    }

    private void hideAllPlayers(@Nonnull PlayerRef viewerRef) {
        Universe.get().getWorlds().forEach((worldId, world) ->
                world.execute(() -> applyHiddenState(viewerRef, world, true)));
    }

    private void showAllPlayers(@Nonnull PlayerRef viewerRef) {
        EntityVisibilityManager.get().clearHidden(viewerRef.getUuid());
    }

    private void applyHiddenState(@Nonnull PlayerRef viewerRef, @Nonnull World world, boolean hide) {
        if (viewerRef.getReference() == null || !viewerRef.getReference().isValid()) {
            return;
        }
        for (PlayerRef targetRef : world.getPlayerRefs()) {
            if (viewerRef.equals(targetRef)) {
                continue;
            }
            Ref<EntityStore> targetEntityRef = targetRef.getReference();
            if (targetEntityRef == null || !targetEntityRef.isValid()) {
                continue;
            }
            Store<EntityStore> targetStore = targetEntityRef.getStore();
            if (targetStore == null) {
                continue;
            }
            UUIDComponent uuidComponent = targetStore.getComponent(targetEntityRef, UUIDComponent.getComponentType());
            if (uuidComponent == null) {
                continue;
            }
            if (hide) {
                EntityVisibilityManager.get().hideEntity(viewerRef.getUuid(), uuidComponent.getUuid());
            } else {
                EntityVisibilityManager.get().showEntity(viewerRef.getUuid(), uuidComponent.getUuid());
            }
        }
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        PLAYERS_HIDDEN.remove(playerId);
    }
}
