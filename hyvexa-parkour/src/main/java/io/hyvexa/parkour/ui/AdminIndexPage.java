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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.PlayerCountStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;

public class AdminIndexPage extends InteractiveCustomUIPage<AdminIndexPage.AdminIndexData> {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final SettingsStore settingsStore;
    private final PlayerCountStore playerCountStore;
    private static final String BUTTON_MAPS = "Maps";
    private static final String BUTTON_PROGRESS = "Progress";
    private static final String BUTTON_SETTINGS = "Settings";
    private static final String BUTTON_PLAYTIME = "Playtime";
    private static final String BUTTON_POPULATION = "Population";
    private static final String BUTTON_GLOBAL_MESSAGES = "GlobalMessages";
    private static final String BUTTON_BROADCAST = "Broadcast";
    private static final String BUTTON_PLAYERS = "Players";
    private static final String BUTTON_MEDAL_REWARDS = "MedalRewards";
    private String announcementInput = "";

    public AdminIndexPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                 ProgressStore progressStore) {
        this(playerRef, mapStore, progressStore, HyvexaPlugin.getInstance() != null
                ? HyvexaPlugin.getInstance().getSettingsStore()
                : null, HyvexaPlugin.getInstance() != null
                ? HyvexaPlugin.getInstance().getPlayerCountStore()
                : null);
    }

    public AdminIndexPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                 ProgressStore progressStore, SettingsStore settingsStore,
                                 PlayerCountStore playerCountStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, AdminIndexData.CODEC);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.settingsStore = settingsStore;
        this.playerCountStore = playerCountStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_AdminIndex.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MapsButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_MAPS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ProgressButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_PROGRESS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SettingsButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_SETTINGS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PlayersButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_PLAYERS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PlaytimeButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_PLAYTIME), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PopulationButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_POPULATION), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GlobalMessageButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_GLOBAL_MESSAGES), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MedalRewardsButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_MEDAL_REWARDS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#AnnouncementField",
                EventData.of(AdminIndexData.KEY_ANNOUNCEMENT, "#AnnouncementField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AnnouncementSendButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_BROADCAST), false);
        uiCommandBuilder.set("#AnnouncementField.Value", announcementInput);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull AdminIndexData data) {
        super.handleDataEvent(ref, store, data);
        if (data.announcement != null) {
            announcementInput = data.announcement.trim();
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if (data.button == null) {
            return;
        }
        if (BUTTON_MAPS.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store, new MapAdminPage(playerRef, mapStore));
            return;
        }
        if (BUTTON_PROGRESS.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store, new ProgressAdminPage(playerRef, progressStore));
            return;
        }
        if (BUTTON_SETTINGS.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store,
                    new SettingsAdminPage(playerRef, settingsStore, mapStore));
            return;
        }
        if (BUTTON_PLAYERS.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store,
                    new AdminPlayersPage(playerRef, mapStore, progressStore));
            return;
        }
        if (BUTTON_PLAYTIME.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store, new PlaytimeAdminPage(playerRef, progressStore));
            return;
        }
        if (BUTTON_POPULATION.equals(data.button)) {
            if (playerCountStore == null) {
                player.sendMessage(Message.raw("Population history unavailable."));
                return;
            }
            player.getPageManager().openCustomPage(ref, store, new PlayerCountAdminPage(playerRef, playerCountStore));
            return;
        }
        if (BUTTON_MEDAL_REWARDS.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store,
                    new MedalRewardAdminPage(playerRef, mapStore, progressStore));
            return;
        }
        if (BUTTON_GLOBAL_MESSAGES.equals(data.button)) {
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin == null) {
                player.sendMessage(Message.raw("Global messages unavailable."));
                return;
            }
            player.getPageManager().openCustomPage(ref, store,
                    new GlobalMessageAdminPage(playerRef, plugin.getGlobalMessageStore()));
            return;
        }
        if (BUTTON_BROADCAST.equals(data.button)) {
            if (announcementInput.isBlank()) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Enter a message to broadcast."));
                return;
            }
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin == null) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Broadcast unavailable."));
                return;
            }
            plugin.broadcastAnnouncement(announcementInput, playerRef);
            announcementInput = "";
            sendRefresh();
        }
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        commandBuilder.set("#AnnouncementField.Value", announcementInput);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MapsButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_MAPS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ProgressButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_PROGRESS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SettingsButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_SETTINGS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PlayersButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_PLAYERS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PlaytimeButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_PLAYTIME), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PopulationButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_POPULATION), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GlobalMessageButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_GLOBAL_MESSAGES), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MedalRewardsButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_MEDAL_REWARDS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#AnnouncementField",
                EventData.of(AdminIndexData.KEY_ANNOUNCEMENT, "#AnnouncementField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AnnouncementSendButton",
                EventData.of(AdminIndexData.KEY_BUTTON, BUTTON_BROADCAST), false);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    public static class AdminIndexData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_ANNOUNCEMENT = "@Announcement";

        public static final BuilderCodec<AdminIndexData> CODEC = BuilderCodec.<AdminIndexData>builder(AdminIndexData.class, AdminIndexData::new)
                .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, value) -> data.button = value, data -> data.button)
                .addField(new KeyedCodec<>(KEY_ANNOUNCEMENT, Codec.STRING),
                        (data, value) -> data.announcement = value, data -> data.announcement)
                .build();

        String button;
        String announcement;
    }
}
