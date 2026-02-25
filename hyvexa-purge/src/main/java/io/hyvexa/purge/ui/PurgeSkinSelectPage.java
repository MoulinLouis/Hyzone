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
import io.hyvexa.common.skin.PurgeSkinDefinition;
import io.hyvexa.common.skin.PurgeSkinRegistry;
import io.hyvexa.common.skin.PurgeSkinStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public class PurgeSkinSelectPage extends InteractiveCustomUIPage<PurgeSkinSelectPage.SkinSelectEventData> {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_DEFAULT = "Default";
    private static final String PREFIX_SELECT = "Select:";

    private static final List<String> PREVIEW_IDS = List.of(
            "AK47Default", "AK47Asimov", "AK47Blossom", "AK47CyberpunkNeon", "AK47FrozenVoltage"
    );

    private final UUID playerId;
    private final String weaponId;
    private final String weaponDisplayName;

    public PurgeSkinSelectPage(@Nonnull PlayerRef playerRef, UUID playerId,
                               String weaponId, String weaponDisplayName) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, SkinSelectEventData.CODEC);
        this.playerId = playerId;
        this.weaponId = weaponId;
        this.weaponDisplayName = weaponDisplayName;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Purge_SkinShop.ui");

        // Hide vexa balance (not needed for select-only view)
        commandBuilder.set("#VexaGroup.Visible", false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        buildSkinList(commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull SkinSelectEventData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) {
            return;
        }

        if (BUTTON_CLOSE.equals(button)) {
            close();
            return;
        }
        if (BUTTON_DEFAULT.equals(button)) {
            handleDefault(ref, store);
            return;
        }
        if (button.startsWith(PREFIX_SELECT)) {
            String skinId = button.substring(PREFIX_SELECT.length());
            handleSelect(ref, store, skinId);
        }
    }

    private void handleDefault(Ref<EntityStore> ref, Store<EntityStore> store) {
        PurgeSkinStore.getInstance().deselectSkin(playerId, weaponId);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw("Reverted to default skin."));
        }
        sendRefresh();
    }

    private void handleSelect(Ref<EntityStore> ref, Store<EntityStore> store, String skinId) {
        PurgeSkinStore.getInstance().selectSkin(playerId, weaponId, skinId);

        Player player = store.getComponent(ref, Player.getComponentType());
        PurgeSkinDefinition def = PurgeSkinRegistry.getSkin(weaponId, skinId);
        String name = def != null ? def.getDisplayName() : skinId;
        if (player != null) {
            player.sendMessage(Message.raw("Selected " + name + " skin."));
        }
        sendRefresh();
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        commandBuilder.set("#WeaponGrid.Visible", false);
        commandBuilder.clear("#SkinList");

        buildSkinList(commandBuilder, eventBuilder);

        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void buildSkinList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.set("#WeaponGrid.Visible", false);
        commandBuilder.set("#SkinList.Visible", true);
        commandBuilder.set("#Subtitle.Visible", true);
        commandBuilder.set("#Title.Text", weaponDisplayName + " Skins");
        commandBuilder.set("#Subtitle.Text", "Select a skin");
        commandBuilder.set("#BackButton.Text", "Close");

        String currentSelected = PurgeSkinStore.getInstance().getSelectedSkin(playerId, weaponId);

        // Default skin card (first entry)
        String defaultRoot = "#SkinList[0]";
        commandBuilder.append("#SkinList", "Pages/Purge_SkinShopEntry.ui");
        commandBuilder.set(defaultRoot + " #SkinName.Text", "Default");

        String defaultPreviewKey = weaponId + "Default";
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

        // Owned skin entries
        List<PurgeSkinDefinition> skins = PurgeSkinRegistry.getSkinsForWeapon(weaponId);
        int index = 1;
        for (PurgeSkinDefinition def : skins) {
            if (!PurgeSkinStore.getInstance().ownsSkin(playerId, weaponId, def.getSkinId())) {
                continue;
            }

            String root = "#SkinList[" + index + "]";
            commandBuilder.append("#SkinList", "Pages/Purge_SkinShopEntry.ui");

            commandBuilder.set(root + " #SkinName.Text", def.getDisplayName());

            // Toggle preview image
            String previewKey = def.getWeaponId() + def.getSkinId();
            for (String previewId : PREVIEW_IDS) {
                commandBuilder.set(root + " #Prev" + previewId + ".Visible", previewId.equals(previewKey));
            }

            boolean selected = def.getSkinId().equals(currentSelected);
            if (selected) {
                commandBuilder.set(root + " #EntrySelectedOverlay.Visible", true);
                commandBuilder.set(root + " #SelectedLabel.Visible", true);
            } else {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                        root + " #ActionButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, PREFIX_SELECT + def.getSkinId()), false);
            }

            index++;
        }
    }

    public static class SkinSelectEventData extends ButtonEventData {
        public static final BuilderCodec<SkinSelectEventData> CODEC =
                BuilderCodec.<SkinSelectEventData>builder(SkinSelectEventData.class, SkinSelectEventData::new)
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
