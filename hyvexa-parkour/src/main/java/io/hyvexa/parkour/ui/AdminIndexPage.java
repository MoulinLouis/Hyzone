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
import io.hyvexa.parkour.data.GlobalMessageStore;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.MedalRewardStore;
import io.hyvexa.parkour.data.PlayerCountStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class AdminIndexPage extends InteractiveCustomUIPage<AdminIndexPage.AdminIndexData> {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final SettingsStore settingsStore;
    private final PlayerCountStore playerCountStore;
    private final MedalRewardStore medalRewardStore;
    private final GlobalMessageStore globalMessageStore;
    private final Runnable refreshAnnouncementsCallback;
    private final BiConsumer<String, PlayerRef> broadcastAnnouncement;
    private final Consumer<UUID> invalidateRankCache;
    private final Function<String, List<String>> hologramLinesBuilder;
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
                          ProgressStore progressStore, SettingsStore settingsStore,
                          PlayerCountStore playerCountStore, MedalRewardStore medalRewardStore,
                          GlobalMessageStore globalMessageStore,
                          Runnable refreshAnnouncementsCallback,
                          BiConsumer<String, PlayerRef> broadcastAnnouncement,
                          Consumer<UUID> invalidateRankCache,
                          Function<String, List<String>> hologramLinesBuilder) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, AdminIndexData.CODEC);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.settingsStore = settingsStore;
        this.playerCountStore = playerCountStore;
        this.medalRewardStore = medalRewardStore;
        this.globalMessageStore = globalMessageStore;
        this.refreshAnnouncementsCallback = refreshAnnouncementsCallback;
        this.broadcastAnnouncement = broadcastAnnouncement;
        this.invalidateRankCache = invalidateRankCache;
        this.hologramLinesBuilder = hologramLinesBuilder;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_AdminIndex.ui");
        bindEvents(uiEventBuilder);
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
        BiConsumer<Ref<EntityStore>, Store<EntityStore>> backCallback = createOpenIndexCallback();
        if (BUTTON_MAPS.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store,
                    new MapAdminPage(playerRef, mapStore, progressStore, hologramLinesBuilder, backCallback));
            return;
        }
        if (BUTTON_PROGRESS.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store,
                    new ProgressAdminPage(playerRef, progressStore, mapStore, invalidateRankCache, backCallback));
            return;
        }
        if (BUTTON_SETTINGS.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store,
                    new SettingsAdminPage(playerRef, settingsStore, mapStore, backCallback));
            return;
        }
        if (BUTTON_PLAYERS.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store,
                    new AdminPlayersPage(playerRef, mapStore, progressStore, backCallback));
            return;
        }
        if (BUTTON_PLAYTIME.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store,
                    new PlaytimeAdminPage(playerRef, progressStore, backCallback));
            return;
        }
        if (BUTTON_POPULATION.equals(data.button)) {
            if (playerCountStore == null) {
                player.sendMessage(Message.raw("Population history unavailable."));
                return;
            }
            player.getPageManager().openCustomPage(ref, store,
                    new PlayerCountAdminPage(playerRef, playerCountStore, backCallback));
            return;
        }
        if (BUTTON_MEDAL_REWARDS.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store,
                    new MedalRewardAdminPage(playerRef, mapStore, progressStore, medalRewardStore, backCallback));
            return;
        }
        if (BUTTON_GLOBAL_MESSAGES.equals(data.button)) {
            if (globalMessageStore == null) {
                player.sendMessage(Message.raw("Global messages unavailable."));
                return;
            }
            player.getPageManager().openCustomPage(ref, store,
                    new GlobalMessageAdminPage(playerRef, globalMessageStore, refreshAnnouncementsCallback,
                            backCallback));
            return;
        }
        if (BUTTON_BROADCAST.equals(data.button)) {
            if (announcementInput.isBlank()) {
                player.sendMessage(Message.raw("Enter a message to broadcast."));
                return;
            }
            if (broadcastAnnouncement == null) {
                player.sendMessage(Message.raw("Broadcast unavailable."));
                return;
            }
            broadcastAnnouncement.accept(announcementInput, playerRef);
            announcementInput = "";
            sendRefresh();
        }
    }

    private BiConsumer<Ref<EntityStore>, Store<EntityStore>> createOpenIndexCallback() {
        return AdminPageUtils.createOpenIndexCallback(mapStore, progressStore, settingsStore,
                playerCountStore, medalRewardStore, globalMessageStore,
                refreshAnnouncementsCallback, broadcastAnnouncement,
                invalidateRankCache, hologramLinesBuilder);
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        commandBuilder.set("#AnnouncementField.Value", announcementInput);
        bindEvents(eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindEvents(UIEventBuilder uiEventBuilder) {
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
