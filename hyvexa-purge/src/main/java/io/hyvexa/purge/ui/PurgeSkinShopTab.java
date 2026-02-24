package io.hyvexa.purge.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.shop.ShopTab;
import io.hyvexa.common.shop.ShopTabResult;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.purge.data.PurgeSkinDefinition;
import io.hyvexa.purge.data.PurgeSkinRegistry;
import io.hyvexa.purge.data.PurgeSkinStore;
import io.hyvexa.purge.util.DailyShopRotation;

import java.util.List;
import java.util.UUID;

public class PurgeSkinShopTab implements ShopTab {

    private static final String ACTION_BUY = "Buy:";

    private static final List<String> PREVIEW_IDS = List.of(
            "AK47Default", "AK47Asimov", "AK47Blossom", "AK47CyberpunkNeon", "AK47FrozenVoltage"
    );

    @Override
    public String getId() {
        return "skins";
    }

    @Override
    public String getLabel() {
        return "Weapon Skins";
    }

    @Override
    public String getAccentColor() {
        return "#f59e0b";
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public boolean isVisibleTo(UUID playerId) {
        return PermissionUtils.isOp(playerId);
    }

    @Override
    public void buildContent(UICommandBuilder cmd, UIEventBuilder evt, UUID playerId, long vexa) {
        List<PurgeSkinDefinition> rotation = DailyShopRotation.getRotation(playerId);

        if (rotation.isEmpty()) {
            cmd.append("#TabContent", "Pages/Shop_EmptyLabel.ui");
            cmd.set("#EmptyLabel.Text", "You own all skins!");
            return;
        }

        // Subtitle with timer
        cmd.append("#TabContent", "Pages/Shop_SubtitleLabel.ui");
        long seconds = DailyShopRotation.getSecondsUntilReset();
        cmd.set("#SubtitleLabel.Text",
                "Daily rotation -- Resets in " + DailyShopRotation.formatTimeRemaining(seconds));

        // Skin grid wrapper (LeftCenterWrap inside the TopScrolling content)
        cmd.append("#TabContent", "Pages/Shop_SkinGrid.ui");

        for (int i = 0; i < rotation.size(); i++) {
            PurgeSkinDefinition def = rotation.get(i);
            String root = "#SkinGrid[" + i + "]";
            cmd.append("#SkinGrid", "Pages/Purge_SkinShopEntry.ui");

            cmd.set(root + " #SkinName.Text", def.getDisplayName());

            // Weapon badge
            cmd.set(root + " #WeaponBadge.Visible", true);
            cmd.set(root + " #WeaponBadge.Text", def.getWeaponId());

            // Toggle preview image
            String previewKey = def.getWeaponId() + def.getSkinId();
            for (String previewId : PREVIEW_IDS) {
                cmd.set(root + " #Prev" + previewId + ".Visible", previewId.equals(previewKey));
            }

            // Price badge
            if (vexa >= def.getPrice()) {
                cmd.set(root + " #PriceBuyBadge.Visible", true);
                cmd.set(root + " #BuyPriceLabel.Text", String.valueOf(def.getPrice()));
                evt.addEventBinding(CustomUIEventBindingType.Activating,
                        root + " #ActionButton",
                        EventData.of(ButtonEventData.KEY_BUTTON,
                                getId() + ":" + ACTION_BUY + def.getWeaponId() + ":" + def.getSkinId()), false);
            } else {
                cmd.set(root + " #PriceCantAffordBadge.Visible", true);
                cmd.set(root + " #CantAffordPrice.Text", String.valueOf(def.getPrice()));
            }
        }
    }

    @Override
    public ShopTabResult handleEvent(String button, Ref<EntityStore> ref, Store<EntityStore> store,
                                     Player player, UUID playerId) {
        if (button.startsWith(ACTION_BUY)) {
            String skinKey = button.substring(ACTION_BUY.length());
            return ShopTabResult.showConfirm(skinKey);
        }
        return ShopTabResult.NONE;
    }

    @Override
    public void populateConfirmOverlay(UICommandBuilder cmd, String confirmKey) {
        String[] parts = confirmKey.split(":", 2);
        if (parts.length != 2) return;

        PurgeSkinDefinition def = PurgeSkinRegistry.getSkin(parts[0], parts[1]);
        if (def == null) return;

        cmd.set("#ConfirmSkinName.Text", def.getDisplayName());
        cmd.set("#ConfirmPrice.Text", String.valueOf(def.getPrice()));
    }

    @Override
    public boolean handleConfirm(String confirmKey, Ref<EntityStore> ref, Store<EntityStore> store,
                                 Player player, UUID playerId) {
        String[] parts = confirmKey.split(":", 2);
        if (parts.length != 2) return false;
        String weaponId = parts[0];
        String skinId = parts[1];

        PurgeSkinStore.PurchaseResult result = PurgeSkinStore.getInstance().purchaseSkin(playerId, weaponId, skinId);
        PurgeSkinDefinition def = PurgeSkinRegistry.getSkin(weaponId, skinId);
        String name = def != null ? def.getDisplayName() : skinId;

        switch (result) {
            case SUCCESS -> player.sendMessage(Message.raw("Purchased " + name + " skin!"));
            case ALREADY_OWNED -> player.sendMessage(Message.raw("You already own this skin."));
            case NOT_ENOUGH_VEXA -> player.sendMessage(Message.raw("Not enough Vexa!"));
        }
        return true;
    }
}
