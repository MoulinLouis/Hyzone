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
import io.hyvexa.common.skin.PurgeSkinDefinition;
import io.hyvexa.common.skin.PurgeSkinRegistry;
import io.hyvexa.purge.manager.PurgeInstanceManager;
import io.hyvexa.purge.manager.PurgeVariantConfigManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;

import javax.annotation.Nonnull;
import java.util.List;

public class PurgeSkinAdminPage extends InteractiveCustomUIPage<PurgeSkinAdminPage.SkinAdminEventData> {

    private static final String BUTTON_BACK = "Back";
    private static final String PREFIX_ADJ = "Adj:";

    private final PurgeWeaponConfigManager weaponConfigManager;
    private final PurgeWaveConfigManager waveConfigManager;
    private final PurgeInstanceManager instanceManager;
    private final PurgeVariantConfigManager variantConfigManager;

    public PurgeSkinAdminPage(@Nonnull PlayerRef playerRef,
                               PurgeWeaponConfigManager weaponConfigManager,
                               PurgeWaveConfigManager waveConfigManager,
                               PurgeInstanceManager instanceManager,
                               PurgeVariantConfigManager variantConfigManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, SkinAdminEventData.CODEC);
        this.weaponConfigManager = weaponConfigManager;
        this.waveConfigManager = waveConfigManager;
        this.instanceManager = instanceManager;
        this.variantConfigManager = variantConfigManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Purge_SkinAdmin.ui");
        bindBackButton(eventBuilder);
        buildSkinList(commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull SkinAdminEventData data) {
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

        if (button.startsWith(PREFIX_ADJ)) {
            handleAdjust(button.substring(PREFIX_ADJ.length()));
        }
    }

    private void handleAdjust(String payload) {
        // payload format: "weaponId:skinId:delta"
        String[] parts = payload.split(":", 3);
        if (parts.length != 3) {
            return;
        }
        try {
            String weaponId = parts[0];
            String skinId = parts[1];
            int delta = Integer.parseInt(parts[2]);
            PurgeSkinDefinition def = PurgeSkinRegistry.getSkin(weaponId, skinId);
            if (def != null) {
                int newPrice = Math.max(0, def.getPrice() + delta);
                weaponConfigManager.setSkinPrice(weaponId, skinId, newPrice);
                sendRefresh();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindBackButton(eventBuilder);
        commandBuilder.clear("#SkinList");
        buildSkinList(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindBackButton(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
    }

    private void buildSkinList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        List<PurgeSkinDefinition> allSkins = PurgeSkinRegistry.getAllSkins();

        for (int i = 0; i < allSkins.size(); i++) {
            PurgeSkinDefinition def = allSkins.get(i);
            String root = "#SkinList[" + i + "]";

            commandBuilder.append("#SkinList", "Pages/Purge_SkinAdminEntry.ui");
            commandBuilder.set(root + " #WeaponBadge.Text", def.getWeaponId());
            commandBuilder.set(root + " #SkinName.Text", def.getDisplayName());
            commandBuilder.set(root + " #PriceValue.Text", String.valueOf(def.getPrice()));

            String key = def.getWeaponId() + ":" + def.getSkinId();
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #Minus100", EventData.of(ButtonEventData.KEY_BUTTON, PREFIX_ADJ + key + ":-100"), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #Minus10", EventData.of(ButtonEventData.KEY_BUTTON, PREFIX_ADJ + key + ":-10"), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #Plus10", EventData.of(ButtonEventData.KEY_BUTTON, PREFIX_ADJ + key + ":10"), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #Plus100", EventData.of(ButtonEventData.KEY_BUTTON, PREFIX_ADJ + key + ":100"), false);
        }
    }

    public static class SkinAdminEventData extends ButtonEventData {
        public static final BuilderCodec<SkinAdminEventData> CODEC =
                BuilderCodec.<SkinAdminEventData>builder(SkinAdminEventData.class, SkinAdminEventData::new)
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
