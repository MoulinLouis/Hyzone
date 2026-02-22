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
import io.hyvexa.core.economy.VexaStore;
import io.hyvexa.purge.data.PurgeSkinDefinition;
import io.hyvexa.purge.data.PurgeSkinRegistry;
import io.hyvexa.purge.data.PurgeSkinStore;
import io.hyvexa.purge.util.DailyShopRotation;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public class PurgeSkinShopPage extends InteractiveCustomUIPage<PurgeSkinShopPage.SkinShopEventData> {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_CONFIRM = "Confirm";
    private static final String BUTTON_CANCEL = "Cancel";
    private static final String PREFIX_BUY = "Buy:";

    private static final List<String> PREVIEW_IDS = List.of(
            "AK47Default", "AK47Asimov", "AK47Blossom", "AK47CyberpunkNeon", "AK47FrozenVoltage"
    );

    private final UUID playerId;
    // Pending buy confirmation — stores "weaponId:skinId" while confirm overlay is shown
    private String pendingBuyKey;

    public PurgeSkinShopPage(@Nonnull PlayerRef playerRef, UUID playerId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, SkinShopEventData.CODEC);
        this.playerId = playerId;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Purge_SkinShop.ui");

        long vexa = VexaStore.getInstance().getVexa(playerId);
        commandBuilder.set("#VexaBalance.Text", String.valueOf(vexa));

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        buildDailyShop(commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull SkinShopEventData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) {
            return;
        }

        if (BUTTON_CLOSE.equals(button)) {
            close();
            return;
        }
        if (BUTTON_CONFIRM.equals(button)) {
            if (pendingBuyKey != null) {
                String key = pendingBuyKey;
                pendingBuyKey = null;
                handleBuy(ref, store, key);
            }
            return;
        }
        if (BUTTON_CANCEL.equals(button)) {
            pendingBuyKey = null;
            hideConfirmOverlay();
            return;
        }
        if (button.startsWith(PREFIX_BUY)) {
            String skinKey = button.substring(PREFIX_BUY.length());
            pendingBuyKey = skinKey;
            showConfirmOverlay(skinKey);
        }
    }

    private void handleBuy(Ref<EntityStore> ref, Store<EntityStore> store, String skinKey) {
        String[] parts = skinKey.split(":", 2);
        if (parts.length != 2) return;
        String weaponId = parts[0];
        String skinId = parts[1];

        Player player = store.getComponent(ref, Player.getComponentType());
        PurgeSkinStore.PurchaseResult result = PurgeSkinStore.getInstance().purchaseSkin(playerId, weaponId, skinId);

        PurgeSkinDefinition def = PurgeSkinRegistry.getSkin(weaponId, skinId);
        String name = def != null ? def.getDisplayName() : skinId;

        if (player != null) {
            switch (result) {
                case SUCCESS -> player.sendMessage(Message.raw("Purchased " + name + " skin!"));
                case ALREADY_OWNED -> player.sendMessage(Message.raw("You already own this skin."));
                case NOT_ENOUGH_VEXA -> player.sendMessage(Message.raw("Not enough Vexa!"));
            }
        }
        sendRefresh();
    }

    private void showConfirmOverlay(String skinKey) {
        String[] parts = skinKey.split(":", 2);
        if (parts.length != 2) return;

        PurgeSkinDefinition def = PurgeSkinRegistry.getSkin(parts[0], parts[1]);
        if (def == null) return;

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();

        cmd.set("#ConfirmOverlay.Visible", true);
        cmd.set("#ConfirmSkinName.Text", def.getDisplayName());
        cmd.set("#ConfirmPrice.Text", String.valueOf(def.getPrice()));

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CONFIRM), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CANCEL), false);

        this.sendUpdate(cmd, evt, false);
    }

    private void hideConfirmOverlay() {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#ConfirmOverlay.Visible", false);
        this.sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();

        long vexa = VexaStore.getInstance().getVexa(playerId);
        commandBuilder.set("#VexaBalance.Text", String.valueOf(vexa));

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        commandBuilder.set("#ConfirmOverlay.Visible", false);
        commandBuilder.set("#WeaponGrid.Visible", false);
        commandBuilder.clear("#SkinList");

        buildDailyShop(commandBuilder, eventBuilder);

        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void buildDailyShop(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.set("#WeaponGrid.Visible", false);
        commandBuilder.set("#SkinList.Visible", true);
        commandBuilder.set("#Subtitle.Visible", true);
        commandBuilder.set("#Title.Text", "Daily Shop");
        commandBuilder.set("#BackButton.Text", "Close");

        List<PurgeSkinDefinition> rotation = DailyShopRotation.getRotation(playerId);

        if (rotation.isEmpty()) {
            commandBuilder.set("#Subtitle.Text", "You own all skins!");
            return;
        }

        long seconds = DailyShopRotation.getSecondsUntilReset();
        commandBuilder.set("#Subtitle.Text", "Resets in " + DailyShopRotation.formatTimeRemaining(seconds));

        long vexa = VexaStore.getInstance().getVexa(playerId);

        for (int i = 0; i < rotation.size(); i++) {
            PurgeSkinDefinition def = rotation.get(i);
            String root = "#SkinList[" + i + "]";
            commandBuilder.append("#SkinList", "Pages/Purge_SkinShopEntry.ui");

            commandBuilder.set(root + " #SkinName.Text", def.getDisplayName());

            // Show weapon badge
            commandBuilder.set(root + " #WeaponBadge.Visible", true);
            commandBuilder.set(root + " #WeaponBadge.Text", def.getWeaponId());

            // Toggle preview image
            String previewKey = def.getWeaponId() + def.getSkinId();
            for (String previewId : PREVIEW_IDS) {
                commandBuilder.set(root + " #Prev" + previewId + ".Visible", previewId.equals(previewKey));
            }

            // Price badge (buy or can't afford)
            if (vexa >= def.getPrice()) {
                commandBuilder.set(root + " #PriceBuyBadge.Visible", true);
                commandBuilder.set(root + " #BuyPriceLabel.Text", String.valueOf(def.getPrice()));
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                        root + " #ActionButton",
                        EventData.of(ButtonEventData.KEY_BUTTON,
                                PREFIX_BUY + def.getWeaponId() + ":" + def.getSkinId()), false);
            } else {
                commandBuilder.set(root + " #PriceCantAffordBadge.Visible", true);
                commandBuilder.set(root + " #CantAffordPrice.Text", String.valueOf(def.getPrice()));
            }
        }
    }

    public static class SkinShopEventData extends ButtonEventData {
        public static final BuilderCodec<SkinShopEventData> CODEC =
                BuilderCodec.<SkinShopEventData>builder(SkinShopEventData.class, SkinShopEventData::new)
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
