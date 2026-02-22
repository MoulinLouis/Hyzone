package io.hyvexa.purge.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.purge.manager.PurgeInstanceManager;
import io.hyvexa.purge.manager.PurgeVariantConfigManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;

import javax.annotation.Nonnull;

public class PurgeSettingsPage extends InteractiveCustomUIPage<PurgeSettingsPage.PurgeSettingsData> {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_LOOTBOX_MINUS = "LootboxMinus";
    private static final String BUTTON_LOOTBOX_PLUS = "LootboxPlus";

    private final PurgeWeaponConfigManager weaponConfigManager;
    private final PurgeWaveConfigManager waveConfigManager;
    private final PurgeInstanceManager instanceManager;
    private final PurgeVariantConfigManager variantConfigManager;

    public PurgeSettingsPage(@Nonnull PlayerRef playerRef,
                             PurgeWeaponConfigManager weaponConfigManager,
                             PurgeWaveConfigManager waveConfigManager,
                             PurgeInstanceManager instanceManager,
                             PurgeVariantConfigManager variantConfigManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeSettingsData.CODEC);
        this.weaponConfigManager = weaponConfigManager;
        this.waveConfigManager = waveConfigManager;
        this.instanceManager = instanceManager;
        this.variantConfigManager = variantConfigManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder,
                      @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_Settings.ui");
        bindEvents(uiEventBuilder);
        populateValues(uiCommandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PurgeSettingsData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) {
            return;
        }

        if (BUTTON_BACK.equals(button)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null && playerRef != null) {
                player.getPageManager().openCustomPage(ref, store,
                        new PurgeAdminIndexPage(playerRef, waveConfigManager, instanceManager, weaponConfigManager, variantConfigManager));
            }
            return;
        }
        if (BUTTON_LOOTBOX_MINUS.equals(button)) {
            weaponConfigManager.setLootboxDropPercent(weaponConfigManager.getLootboxDropPercent() - 1);
            sendRefresh();
            return;
        }
        if (BUTTON_LOOTBOX_PLUS.equals(button)) {
            weaponConfigManager.setLootboxDropPercent(weaponConfigManager.getLootboxDropPercent() + 1);
            sendRefresh();
        }
    }

    private void bindEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LootboxMinus",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_LOOTBOX_MINUS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LootboxPlus",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_LOOTBOX_PLUS), false);
    }

    private void populateValues(UICommandBuilder commandBuilder) {
        commandBuilder.set("#LootboxValue.Text", weaponConfigManager.getLootboxDropPercent() + "%");
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindEvents(eventBuilder);
        populateValues(commandBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    public static class PurgeSettingsData extends ButtonEventData {
        public static final BuilderCodec<PurgeSettingsData> CODEC =
                BuilderCodec.<PurgeSettingsData>builder(PurgeSettingsData.class, PurgeSettingsData::new)
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
