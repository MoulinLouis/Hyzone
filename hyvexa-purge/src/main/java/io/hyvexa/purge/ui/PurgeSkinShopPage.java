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
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PurgeSkinShopPage extends InteractiveCustomUIPage<PurgeSkinShopPage.SkinShopEventData> {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_DEFAULT = "Default";
    private static final String BUTTON_CONFIRM = "Confirm";
    private static final String BUTTON_CANCEL = "Cancel";
    private static final String PREFIX_WEAPON = "Weapon:";
    private static final String PREFIX_BUY = "Buy:";
    private static final String PREFIX_SELECT = "Select:";

    private static final List<String> ICON_WEAPON_IDS = List.of(
            "AK47", "Barret50", "ColtRevolver", "DesertEagle", "DoubleBarrel",
            "Flamethrower", "Glock18", "M4A1s", "MP9", "Mac10", "Thompson"
    );

    // UI element IDs for preview groups in the entry template (no underscores — UI IDs don't allow them)
    private static final List<String> PREVIEW_IDS = List.of(
            "AK47Default", "AK47Asimov", "AK47Blossom", "AK47CyberpunkNeon", "AK47FrozenVoltage"
    );

    private final UUID playerId;
    private final PurgeWeaponConfigManager weaponConfigManager;
    // null = show weapon list, non-null = show skins for this weapon
    private String selectedWeaponId;
    // Pending buy confirmation — stores "weaponId:skinId" while confirm overlay is shown
    private String pendingBuyKey;

    public PurgeSkinShopPage(@Nonnull PlayerRef playerRef, UUID playerId,
                             PurgeWeaponConfigManager weaponConfigManager,
                             String preselectedWeaponId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, SkinShopEventData.CODEC);
        this.playerId = playerId;
        this.weaponConfigManager = weaponConfigManager;
        this.selectedWeaponId = preselectedWeaponId;
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
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);

        if (selectedWeaponId != null) {
            buildSkinList(commandBuilder, eventBuilder);
        } else {
            buildWeaponGrid(commandBuilder, eventBuilder);
        }
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

        if (BUTTON_BACK.equals(button)) {
            if (selectedWeaponId != null) {
                selectedWeaponId = null;
                sendRefresh();
            } else {
                close();
            }
            return;
        }
        if (BUTTON_DEFAULT.equals(button)) {
            handleDefault(ref, store);
            return;
        }
        if (button.startsWith(PREFIX_WEAPON)) {
            String weaponId = button.substring(PREFIX_WEAPON.length());
            selectedWeaponId = weaponId;
            sendRefresh();
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
            return;
        }
        if (button.startsWith(PREFIX_SELECT)) {
            String skinKey = button.substring(PREFIX_SELECT.length());
            handleSelect(ref, store, skinKey);
        }
    }

    private void handleBuy(Ref<EntityStore> ref, Store<EntityStore> store, String skinKey) {
        // skinKey = "weaponId:skinId"
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

    private void handleSelect(Ref<EntityStore> ref, Store<EntityStore> store, String skinKey) {
        String[] parts = skinKey.split(":", 2);
        if (parts.length != 2) return;
        String weaponId = parts[0];
        String skinId = parts[1];

        PurgeSkinStore.getInstance().selectSkin(playerId, weaponId, skinId);

        Player player = store.getComponent(ref, Player.getComponentType());
        PurgeSkinDefinition def = PurgeSkinRegistry.getSkin(weaponId, skinId);
        String name = def != null ? def.getDisplayName() : skinId;
        if (player != null) {
            player.sendMessage(Message.raw("Selected " + name + " skin."));
        }
        sendRefresh();
    }

    private void handleDefault(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (selectedWeaponId == null) return;
        PurgeSkinStore.getInstance().deselectSkin(playerId, selectedWeaponId);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw("Reverted to default skin."));
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
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);

        // Reset visibility
        commandBuilder.set("#ConfirmOverlay.Visible", false);
        commandBuilder.set("#WeaponGrid.Visible", false);
        commandBuilder.set("#SkinList.Visible", false);
        commandBuilder.set("#Subtitle.Visible", false);
        commandBuilder.clear("#WeaponGrid");
        commandBuilder.clear("#SkinList");

        if (selectedWeaponId != null) {
            buildSkinList(commandBuilder, eventBuilder);
        } else {
            buildWeaponGrid(commandBuilder, eventBuilder);
        }

        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void buildWeaponGrid(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.set("#WeaponGrid.Visible", true);
        commandBuilder.set("#SkinList.Visible", false);
        commandBuilder.set("#Subtitle.Visible", false);
        commandBuilder.set("#Title.Text", "Weapon Skins");
        commandBuilder.set("#BackButton.Text", "Close");

        List<String> weaponIds = new ArrayList<>(weaponConfigManager.getWeaponIds());
        weaponIds.sort(String::compareTo);

        int index = 0;
        for (String weaponId : weaponIds) {
            if (!PurgeSkinRegistry.hasAnySkins(weaponId)) {
                continue;
            }

            String root = "#WeaponGrid[" + index + "]";
            commandBuilder.append("#WeaponGrid", "Pages/Purge_WeaponSelectEntry.ui");

            // Show weapon icon
            String normalized = ICON_WEAPON_IDS.contains(weaponId) ? weaponId : "AK47";
            for (String iconId : ICON_WEAPON_IDS) {
                commandBuilder.set(root + " #Icon" + iconId + ".Visible", false);
            }
            commandBuilder.set(root + " #Icon" + normalized + ".Visible", true);

            // Show weapon name with skin count
            int skinCount = PurgeSkinRegistry.getSkinsForWeapon(weaponId).size();
            String displayName = weaponConfigManager.getDisplayName(weaponId);
            commandBuilder.set(root + " #WeaponName.Visible", true);
            commandBuilder.set(root + " #WeaponName.Text", displayName + " (" + skinCount + ")");

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #SelectButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, PREFIX_WEAPON + weaponId), false);

            index++;
        }
    }

    private void buildSkinList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.set("#WeaponGrid.Visible", false);
        commandBuilder.set("#SkinList.Visible", true);
        commandBuilder.set("#Subtitle.Visible", true);
        commandBuilder.set("#BackButton.Text", "< Back");

        String displayName = weaponConfigManager.getDisplayName(selectedWeaponId);
        commandBuilder.set("#Title.Text", displayName + " Skins");
        commandBuilder.set("#Subtitle.Text", "Choose a skin for your " + displayName);

        long vexa = VexaStore.getInstance().getVexa(playerId);
        String currentSelected = PurgeSkinStore.getInstance().getSelectedSkin(playerId, selectedWeaponId);

        // Default skin card (first entry)
        String defaultRoot = "#SkinList[0]";
        commandBuilder.append("#SkinList", "Pages/Purge_SkinShopEntry.ui");
        commandBuilder.set(defaultRoot + " #SkinName.Text", "Default");

        String defaultPreviewKey = selectedWeaponId + "Default";
        for (String previewId : PREVIEW_IDS) {
            commandBuilder.set(defaultRoot + " #Prev" + previewId + ".Visible", previewId.equals(defaultPreviewKey));
        }

        if (currentSelected == null) {
            commandBuilder.set(defaultRoot + " #EntrySelectedOverlay.Visible", true);
            commandBuilder.set(defaultRoot + " #SelectedLabel.Visible", true);
        } else {
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    defaultRoot + " #ActionButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_DEFAULT), false);
        }

        // Skin entries
        List<PurgeSkinDefinition> skins = PurgeSkinRegistry.getSkinsForWeapon(selectedWeaponId);
        for (int i = 0; i < skins.size(); i++) {
            PurgeSkinDefinition def = skins.get(i);
            String root = "#SkinList[" + (i + 1) + "]";
            commandBuilder.append("#SkinList", "Pages/Purge_SkinShopEntry.ui");

            commandBuilder.set(root + " #SkinName.Text", def.getDisplayName());

            // Toggle preview image — hide all, show the matching one
            String previewKey = def.getWeaponId() + def.getSkinId();
            for (String previewId : PREVIEW_IDS) {
                commandBuilder.set(root + " #Prev" + previewId + ".Visible", previewId.equals(previewKey));
            }

            boolean owned = PurgeSkinStore.getInstance().ownsSkin(playerId, selectedWeaponId, def.getSkinId());
            boolean selected = def.getSkinId().equals(currentSelected);

            if (!owned) {
                if (vexa >= def.getPrice()) {
                    commandBuilder.set(root + " #PriceBuyBadge.Visible", true);
                    commandBuilder.set(root + " #BuyPriceLabel.Text", String.valueOf(def.getPrice()));
                    eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                            root + " #ActionButton",
                            EventData.of(ButtonEventData.KEY_BUTTON,
                                    PREFIX_BUY + selectedWeaponId + ":" + def.getSkinId()), false);
                } else {
                    commandBuilder.set(root + " #PriceCantAffordBadge.Visible", true);
                    commandBuilder.set(root + " #CantAffordPrice.Text", String.valueOf(def.getPrice()));
                }
            } else if (selected) {
                commandBuilder.set(root + " #EntrySelectedOverlay.Visible", true);
                commandBuilder.set(root + " #SelectedLabel.Visible", true);
            } else {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                        root + " #ActionButton",
                        EventData.of(ButtonEventData.KEY_BUTTON,
                                PREFIX_SELECT + selectedWeaponId + ":" + def.getSkinId()), false);
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
