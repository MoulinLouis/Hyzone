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
import io.hyvexa.parkour.ParkourConstants;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;

import javax.annotation.Nonnull;
import java.util.Locale;

public class SettingsAdminPage extends InteractiveCustomUIPage<SettingsAdminPage.SettingsData> {

    private static final String BUTTON_BACK = "BackButton";
    private static final String BUTTON_SAVE = "SaveSettings";
    private static final String BUTTON_TOGGLE_IDLE_FALL_OP = "ToggleIdleFallOp";
    private static final String LABEL_CURRENT_VALUE = "#CurrentValue.Text";

    private final SettingsStore settingsStore;
    private final MapStore mapStore;
    private String fallRespawnSecondsInput = "";

    public SettingsAdminPage(@Nonnull PlayerRef playerRef, SettingsStore settingsStore, MapStore mapStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, SettingsData.CODEC);
        this.settingsStore = settingsStore;
        this.mapStore = mapStore;
        this.fallRespawnSecondsInput = formatSeconds(getCurrentSeconds());
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_SettingsAdmin.ui");
        bindEvents(uiEventBuilder);
        populateFields(uiCommandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull SettingsData data) {
        super.handleDataEvent(ref, store, data);
        if (data.fallRespawnSeconds != null) {
            fallRespawnSecondsInput = data.fallRespawnSeconds.trim();
        }
        if (data.button == null) {
            return;
        }
        if (BUTTON_BACK.equals(data.button)) {
            openIndex(ref, store);
            return;
        }
        if (BUTTON_TOGGLE_IDLE_FALL_OP.equals(data.button)) {
            toggleIdleFallForOp(ref, store);
            return;
        }
        if (BUTTON_SAVE.equals(data.button)) {
            handleSave(ref, store);
        }
    }

    private void handleSave(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (settingsStore == null) {
            player.sendMessage(Message.raw("Settings are unavailable."));
            return;
        }
        double seconds;
        try {
            seconds = Double.parseDouble(fallRespawnSecondsInput);
        } catch (NumberFormatException ex) {
            player.sendMessage(Message.raw("Enter a valid number of seconds."));
            return;
        }
        if (seconds <= 0) {
            player.sendMessage(Message.raw("Fall respawn time must be greater than 0."));
            return;
        }
        settingsStore.setFallRespawnSeconds(seconds);
        fallRespawnSecondsInput = formatSeconds(settingsStore.getFallRespawnSeconds());
        player.sendMessage(Message.raw("Updated fall respawn time to " + fallRespawnSecondsInput + "s."));
        sendRefresh();
    }

    private void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        MapStore mapStore = HyvexaPlugin.getInstance().getMapStore();
        ProgressStore progressStore = HyvexaPlugin.getInstance().getProgressStore();
        player.getPageManager().openCustomPage(ref, store,
                new AdminIndexPage(playerRef, mapStore, progressStore, settingsStore));
    }

    private void populateFields(UICommandBuilder commandBuilder) {
        commandBuilder.set("#FallRespawnField.Value", fallRespawnSecondsInput);
        commandBuilder.set(LABEL_CURRENT_VALUE, "Current: " + formatSeconds(getCurrentSeconds()) + "s");
        commandBuilder.set("#IdleFallOpValue.Text", getIdleFallOpLabel());
        applyRankThresholds(commandBuilder);
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateFields(commandBuilder);
        bindEvents(eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(SettingsData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#FallRespawnField",
                EventData.of(SettingsData.KEY_FALL_RESPAWN, "#FallRespawnField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#IdleFallOpToggle",
                EventData.of(SettingsData.KEY_BUTTON, BUTTON_TOGGLE_IDLE_FALL_OP), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveSettingsButton",
                EventData.of(SettingsData.KEY_BUTTON, BUTTON_SAVE), false);
    }

    private double getCurrentSeconds() {
        return settingsStore != null
                ? settingsStore.getFallRespawnSeconds()
                : ParkourConstants.DEFAULT_FALL_RESPAWN_SECONDS;
    }

    private String getIdleFallOpLabel() {
        boolean enabled = settingsStore != null && settingsStore.isIdleFallRespawnForOp();
        return enabled ? "On" : "Off";
    }

    private static String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.2f", seconds);
    }

    private void toggleIdleFallForOp(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || settingsStore == null) {
            return;
        }
        boolean newValue = !settingsStore.isIdleFallRespawnForOp();
        settingsStore.setIdleFallRespawnForOp(newValue);
        player.sendMessage(Message.raw("Idle fall respawn for OPs: " + (newValue ? "On" : "Off") + "."));
        sendRefresh();
    }

    private void applyRankThresholds(UICommandBuilder commandBuilder) {
        long totalXp = ProgressStore.getTotalPossibleXp(mapStore);
        if (totalXp <= 0L) {
            commandBuilder.set("#RankThresholds.Text", "No maps available to calculate rank thresholds.");
            commandBuilder.set("#RankBronze.Text", "");
            commandBuilder.set("#RankSilver.Text", "");
            commandBuilder.set("#RankGold.Text", "");
            commandBuilder.set("#RankPlatinum.Text", "");
            commandBuilder.set("#RankDiamond.Text", "");
            commandBuilder.set("#RankMaster.Text", "");
            return;
        }
        long t1 = Math.round(totalXp * 0.2);
        long t2 = Math.round(totalXp * 0.4);
        long t3 = Math.round(totalXp * 0.6);
        long t4 = Math.round(totalXp * 0.8);
        long t5 = totalXp;
        commandBuilder.set("#RankThresholds.Text", "Total XP: " + totalXp);
        commandBuilder.set("#RankBronze.Text", "Bronze: 0 XP");
        commandBuilder.set("#RankSilver.Text", "Silver: " + t1 + " XP");
        commandBuilder.set("#RankGold.Text", "Gold: " + t2 + " XP");
        commandBuilder.set("#RankPlatinum.Text", "Platinum: " + t3 + " XP");
        commandBuilder.set("#RankDiamond.Text", "Diamond: " + t4 + " XP");
        commandBuilder.set("#RankMaster.Text", "Master: " + t5 + " XP");
    }

    public static class SettingsData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_FALL_RESPAWN = "@FallRespawnSeconds";

        public static final BuilderCodec<SettingsData> CODEC = BuilderCodec.<SettingsData>builder(SettingsData.class, SettingsData::new)
                .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, value) -> data.button = value, data -> data.button)
                .addField(new KeyedCodec<>(KEY_FALL_RESPAWN, Codec.STRING),
                        (data, value) -> data.fallRespawnSeconds = value, data -> data.fallRespawnSeconds)
                .build();

        private String button;
        private String fallRespawnSeconds;
    }
}
