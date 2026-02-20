package io.hyvexa.purge.ui;

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
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.purge.manager.PurgeInstanceManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;

import javax.annotation.Nonnull;
import java.util.List;

public class PurgeWeaponAdminPage extends InteractiveCustomUIPage<PurgeWeaponAdminPage.PurgeWeaponAdminData> {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_RESET = "Reset";
    private static final String BUTTON_ADJ_DMG_PREFIX = "AdjDmg:";
    private static final String BUTTON_ADJ_COST_PREFIX = "AdjCost:";

    private final String weaponId;
    private final PurgeWeaponConfigManager weaponConfigManager;
    private final PurgeWaveConfigManager waveConfigManager;
    private final PurgeInstanceManager instanceManager;

    public PurgeWeaponAdminPage(@Nonnull PlayerRef playerRef,
                                 String weaponId,
                                 PurgeWeaponConfigManager weaponConfigManager,
                                 PurgeWaveConfigManager waveConfigManager,
                                 PurgeInstanceManager instanceManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeWeaponAdminData.CODEC);
        this.weaponId = weaponId;
        this.weaponConfigManager = weaponConfigManager;
        this.waveConfigManager = waveConfigManager;
        this.instanceManager = instanceManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder,
                      @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_WeaponAdmin.ui");

        String displayName = weaponConfigManager.getDisplayName(weaponId);
        int levelCount = weaponConfigManager.getAllLevels(weaponId).size();
        String maxStars = weaponConfigManager.getStarDisplay(weaponConfigManager.getMaxLevel());
        uiCommandBuilder.set("#WeaponSubtitle.Text",
                displayName + " -- " + levelCount + " levels (0 to " + maxStars + " stars)");

        bindStaticEvents(uiEventBuilder);
        buildLevelList(uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PurgeWeaponAdminData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) {
            return;
        }

        if (BUTTON_BACK.equals(button)) {
            openWeaponSelect(ref, store);
            return;
        }
        if (BUTTON_RESET.equals(button)) {
            handleReset(ref, store);
            return;
        }
        if (button.startsWith(BUTTON_ADJ_DMG_PREFIX)) {
            handleAdjustDamage(button.substring(BUTTON_ADJ_DMG_PREFIX.length()));
            return;
        }
        if (button.startsWith(BUTTON_ADJ_COST_PREFIX)) {
            handleAdjustCost(button.substring(BUTTON_ADJ_COST_PREFIX.length()));
        }
    }

    private void handleAdjustDamage(String payload) {
        String[] parts = payload.split(":");
        if (parts.length != 2) {
            return;
        }
        try {
            int level = Integer.parseInt(parts[0]);
            int delta = Integer.parseInt(parts[1]);
            if (weaponConfigManager.adjustDamage(weaponId, level, delta)) {
                sendRefresh();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void handleAdjustCost(String payload) {
        String[] parts = payload.split(":");
        if (parts.length != 2) {
            return;
        }
        try {
            int level = Integer.parseInt(parts[0]);
            long delta = Long.parseLong(parts[1]);
            if (weaponConfigManager.adjustCost(weaponId, level, delta)) {
                sendRefresh();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void handleReset(Ref<EntityStore> ref, Store<EntityStore> store) {
        weaponConfigManager.resetDefaults(weaponId);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(weaponConfigManager.getDisplayName(weaponId) + " weapon levels reset to defaults."));
        }
        sendRefresh();
    }

    private void openWeaponSelect(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PurgeWeaponSelectPage(playerRef, PurgeWeaponSelectPage.Mode.ADMIN, null,
                        weaponConfigManager, waveConfigManager, instanceManager));
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindStaticEvents(eventBuilder);
        buildLevelList(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindStaticEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_RESET), false);
    }

    private void buildLevelList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#LevelCards");

        List<PurgeWeaponConfigManager.WeaponLevelEntry> levels = weaponConfigManager.getAllLevels(weaponId);

        if (levels.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No weapon levels configured.");
            return;
        }
        commandBuilder.set("#EmptyText.Text", "");

        for (int i = 0; i < levels.size(); i++) {
            PurgeWeaponConfigManager.WeaponLevelEntry entry = levels.get(i);
            String root = "#LevelCards[" + i + "]";

            commandBuilder.append("#LevelCards", "Pages/Purge_WeaponAdminEntry.ui");
            commandBuilder.set(root + " #StarLabel.Text", weaponConfigManager.getStarDisplay(entry.level()) + " star");
            commandBuilder.set(root + " #DmgValue.Text", String.valueOf(entry.damage()));
            commandBuilder.set(root + " #CostValue.Text", String.valueOf(entry.cost()));

            // Damage +/- buttons (delta +/-1)
            String dmgMinus = BUTTON_ADJ_DMG_PREFIX + entry.level() + ":-1";
            String dmgPlus = BUTTON_ADJ_DMG_PREFIX + entry.level() + ":1";
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #DmgMinusButton", EventData.of(ButtonEventData.KEY_BUTTON, dmgMinus), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #DmgPlusButton", EventData.of(ButtonEventData.KEY_BUTTON, dmgPlus), false);

            // Cost +/- buttons (delta +/-50)
            String costMinus = BUTTON_ADJ_COST_PREFIX + entry.level() + ":-50";
            String costPlus = BUTTON_ADJ_COST_PREFIX + entry.level() + ":50";
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #CostMinusButton", EventData.of(ButtonEventData.KEY_BUTTON, costMinus), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #CostPlusButton", EventData.of(ButtonEventData.KEY_BUTTON, costPlus), false);
        }
    }

    public static class PurgeWeaponAdminData extends ButtonEventData {
        public static final BuilderCodec<PurgeWeaponAdminData> CODEC =
                BuilderCodec.<PurgeWeaponAdminData>builder(PurgeWeaponAdminData.class, PurgeWeaponAdminData::new)
                        .addField(new KeyedCodec<>(ButtonEventData.KEY_BUTTON, Codec.STRING),
                                (data, value) -> data.button = value, data -> data.button)
                        .build();

        private String button;

        @Override
        public String getButton() {
            return button;
        }
    }
}
