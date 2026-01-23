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
    private static final String BUTTON_SAVE_FAILSAFE = "SaveFailsafeBuffer";
    private static final String BUTTON_TOGGLE_IDLE_FALL_OP = "ToggleIdleFallOp";
    private static final String BUTTON_TOGGLE_WEAPON_DAMAGE = "ToggleWeaponDamage";
    private static final String BUTTON_TOGGLE_TELEPORT_DEBUG = "ToggleTeleportDebug";
    private static final String LABEL_CURRENT_VALUE = "#CurrentValue.Text";
    private static final String LABEL_FAILSAFE_VALUE = "#FallFailSafeValue.Text";

    private final SettingsStore settingsStore;
    private final MapStore mapStore;
    private String fallRespawnSecondsInput = "";
    private String fallFailsafeVoidInput = "";

    public SettingsAdminPage(@Nonnull PlayerRef playerRef, SettingsStore settingsStore, MapStore mapStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, SettingsData.CODEC);
        this.settingsStore = settingsStore;
        this.mapStore = mapStore;
        this.fallRespawnSecondsInput = formatSeconds(getCurrentSeconds());
        this.fallFailsafeVoidInput = formatVoidCutoff(getCurrentFailsafeVoidY());
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
        if (data.fallFailsafeVoidY != null) {
            fallFailsafeVoidInput = data.fallFailsafeVoidY.trim();
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
        if (BUTTON_TOGGLE_WEAPON_DAMAGE.equals(data.button)) {
            toggleWeaponDamage(ref, store);
            return;
        }
        if (BUTTON_TOGGLE_TELEPORT_DEBUG.equals(data.button)) {
            toggleTeleportDebug(ref, store);
            return;
        }
        if (BUTTON_SAVE.equals(data.button)) {
            handleSaveFallRespawn(ref, store);
            return;
        }
        if (BUTTON_SAVE_FAILSAFE.equals(data.button)) {
            handleSaveFailsafeVoid(ref, store);
        }
    }

    private void handleSaveFallRespawn(Ref<EntityStore> ref, Store<EntityStore> store) {
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

    private void handleSaveFailsafeVoid(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (settingsStore == null) {
            player.sendMessage(Message.raw("Settings are unavailable."));
            return;
        }
        double voidY;
        try {
            voidY = Double.parseDouble(fallFailsafeVoidInput);
        } catch (NumberFormatException ex) {
            player.sendMessage(Message.raw("Enter a valid void cutoff Y value."));
            return;
        }
        settingsStore.setFallFailsafeVoidY(voidY);
        fallFailsafeVoidInput = formatVoidCutoff(settingsStore.getFallFailsafeVoidY());
        player.sendMessage(Message.raw("Updated void cutoff Y to " + fallFailsafeVoidInput + "."));
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
                new AdminIndexPage(playerRef, mapStore, progressStore, settingsStore,
                        HyvexaPlugin.getInstance().getPlayerCountStore()));
    }

    private void populateFields(UICommandBuilder commandBuilder) {
        commandBuilder.set("#FallRespawnField.Value", fallRespawnSecondsInput);
        commandBuilder.set(LABEL_CURRENT_VALUE, "Current: " + formatSeconds(getCurrentSeconds()) + "s");
        commandBuilder.set("#FallFailSafeField.Value", fallFailsafeVoidInput);
        commandBuilder.set(LABEL_FAILSAFE_VALUE, "Current: " + formatVoidCutoff(getCurrentFailsafeVoidY()));
        commandBuilder.set("#IdleFallOpValue.Text", getIdleFallOpLabel());
        commandBuilder.set("#WeaponDamageValue.Text", getWeaponDamageLabel());
        commandBuilder.set("#TeleportDebugValue.Text", getTeleportDebugLabel());
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
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#FallFailSafeField",
                EventData.of(SettingsData.KEY_FALL_FAILSAFE_VOID, "#FallFailSafeField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#IdleFallOpToggle",
                EventData.of(SettingsData.KEY_BUTTON, BUTTON_TOGGLE_IDLE_FALL_OP), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#WeaponDamageToggle",
                EventData.of(SettingsData.KEY_BUTTON, BUTTON_TOGGLE_WEAPON_DAMAGE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TeleportDebugToggle",
                EventData.of(SettingsData.KEY_BUTTON, BUTTON_TOGGLE_TELEPORT_DEBUG), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveSettingsButton",
                EventData.of(SettingsData.KEY_BUTTON, BUTTON_SAVE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveFailSafeButton",
                EventData.of(SettingsData.KEY_BUTTON, BUTTON_SAVE_FAILSAFE), false);
    }

    private double getCurrentSeconds() {
        return settingsStore != null
                ? settingsStore.getFallRespawnSeconds()
                : ParkourConstants.DEFAULT_FALL_RESPAWN_SECONDS;
    }

    private double getCurrentFailsafeVoidY() {
        return settingsStore != null
                ? settingsStore.getFallFailsafeVoidY()
                : ParkourConstants.FALL_FAILSAFE_VOID_Y;
    }

    private String getIdleFallOpLabel() {
        boolean enabled = settingsStore != null && settingsStore.isIdleFallRespawnForOp();
        return enabled ? "On" : "Off";
    }

    private String getWeaponDamageLabel() {
        boolean enabled = settingsStore != null && settingsStore.isWeaponDamageDisabled();
        return enabled ? "On" : "Off";
    }

    private String getTeleportDebugLabel() {
        boolean enabled = settingsStore != null && settingsStore.isTeleportDebugEnabled();
        return enabled ? "On" : "Off";
    }

    private static String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.2f", seconds);
    }

    private static String formatVoidCutoff(double voidY) {
        return String.format(Locale.ROOT, "%.2f", voidY);
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

    private void toggleWeaponDamage(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || settingsStore == null) {
            return;
        }
        boolean newValue = !settingsStore.isWeaponDamageDisabled();
        settingsStore.setWeaponDamageDisabled(newValue);
        player.sendMessage(Message.raw("Weapon damage/knockback disabled: " + (newValue ? "On" : "Off") + "."));
        sendRefresh();
    }

    private void toggleTeleportDebug(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || settingsStore == null) {
            return;
        }
        boolean newValue = !settingsStore.isTeleportDebugEnabled();
        settingsStore.setTeleportDebugEnabled(newValue);
        player.sendMessage(Message.raw("Teleport debug logging: " + (newValue ? "On" : "Off") + "."));
        sendRefresh();
    }

    private void applyRankThresholds(UICommandBuilder commandBuilder) {
        long totalXp = ProgressStore.getTotalPossibleXp(mapStore);
        if (totalXp <= 0L) {
            commandBuilder.set("#RankThresholds.Text", "No maps available to calculate rank thresholds.");
            commandBuilder.set("#RankUnranked.Text", "");
            commandBuilder.set("#RankIron.Text", "");
            commandBuilder.set("#RankBronze.Text", "");
            commandBuilder.set("#RankSilver.Text", "");
            commandBuilder.set("#RankGold.Text", "");
            commandBuilder.set("#RankPlatinum.Text", "");
            commandBuilder.set("#RankEmerald.Text", "");
            commandBuilder.set("#RankDiamond.Text", "");
            commandBuilder.set("#RankMaster.Text", "");
            commandBuilder.set("#RankGrandmaster.Text", "");
            commandBuilder.set("#RankChallenger.Text", "");
            commandBuilder.set("#RankVexaGodV.Text", "");
            commandBuilder.set("#RankVexaGode.Text", "");
            commandBuilder.set("#RankVexaGodx.Text", "");
            commandBuilder.set("#RankVexaGoda.Text", "");
            commandBuilder.set("#RankVexaGodG.Text", "");
            commandBuilder.set("#RankVexaGodo.Text", "");
            commandBuilder.set("#RankVexaGodd.Text", "");
            commandBuilder.set("#RankVexaGodSuffix.Text", "");
            return;
        }
        commandBuilder.set("#RankThresholds.Text", "Total XP: " + totalXp);
        commandBuilder.set("#RankUnranked.Text", "Unranked: 0%");
        commandBuilder.set("#RankIron.Text", "Iron: 0.01% - 9.99%");
        commandBuilder.set("#RankBronze.Text", "Bronze: 10.00% - 19.99%");
        commandBuilder.set("#RankSilver.Text", "Silver: 20.00% - 29.99%");
        commandBuilder.set("#RankGold.Text", "Gold: 30.00% - 39.99%");
        commandBuilder.set("#RankPlatinum.Text", "Platinum: 40.00% - 49.99%");
        commandBuilder.set("#RankEmerald.Text", "Emerald: 50.00% - 59.99%");
        commandBuilder.set("#RankDiamond.Text", "Diamond: 60.00% - 69.99%");
        commandBuilder.set("#RankMaster.Text", "Master: 70.00% - 79.99%");
        commandBuilder.set("#RankGrandmaster.Text", "Grandmaster: 80.00% - 89.99%");
        commandBuilder.set("#RankChallenger.Text", "Challenger: 90.00% - 99.99%");
        commandBuilder.set("#RankVexaGodV.Text", "V");
        commandBuilder.set("#RankVexaGode.Text", "e");
        commandBuilder.set("#RankVexaGodx.Text", "x");
        commandBuilder.set("#RankVexaGoda.Text", "a");
        commandBuilder.set("#RankVexaGodG.Text", "G");
        commandBuilder.set("#RankVexaGodo.Text", "o");
        commandBuilder.set("#RankVexaGodd.Text", "d");
        commandBuilder.set("#RankVexaGodSuffix.Text", ": 100.00%");
    }

    public static class SettingsData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_FALL_RESPAWN = "@FallRespawnSeconds";
        static final String KEY_FALL_FAILSAFE_VOID = "@FallFailsafeVoidY";

        public static final BuilderCodec<SettingsData> CODEC = BuilderCodec.<SettingsData>builder(SettingsData.class, SettingsData::new)
                .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, value) -> data.button = value, data -> data.button)
                .addField(new KeyedCodec<>(KEY_FALL_RESPAWN, Codec.STRING),
                        (data, value) -> data.fallRespawnSeconds = value, data -> data.fallRespawnSeconds)
                .addField(new KeyedCodec<>(KEY_FALL_FAILSAFE_VOID, Codec.STRING),
                        (data, value) -> data.fallFailsafeVoidY = value, data -> data.fallFailsafeVoidY)
                .build();

        private String button;
        private String fallRespawnSeconds;
        private String fallFailsafeVoidY;
    }
}
