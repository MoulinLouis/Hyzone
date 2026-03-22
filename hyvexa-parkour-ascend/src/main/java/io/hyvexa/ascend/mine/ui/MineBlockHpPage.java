package io.hyvexa.ascend.mine.ui;

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
import io.hyvexa.ascend.mine.MineBlockRegistry;
import io.hyvexa.ascend.mine.data.MineConfigStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

/**
 * Unified block config page: shows ALL registered blocks with their HP and price.
 */
public class MineBlockHpPage extends InteractiveCustomUIPage<MineBlockHpPage.ConfigData> {

    private final MineConfigStore mineConfigStore;
    private String selectedBlockId = "";
    private String hpValue = "";
    private String priceValue = "";

    public MineBlockHpPage(@Nonnull PlayerRef playerRef, MineConfigStore mineConfigStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ConfigData.CODEC);
        this.mineConfigStore = mineConfigStore;
    }

    public void setSelectedBlockId(String blockId) {
        if (blockId != null && !blockId.isEmpty()) {
            this.selectedBlockId = blockId;
            loadFieldsForBlock(blockId);
        }
    }

    private void loadFieldsForBlock(String blockId) {
        int hp = mineConfigStore.getBlockHp(blockId);
        hpValue = String.valueOf(hp);
        long price = mineConfigStore.getBlockPrice(blockId);
        priceValue = String.valueOf(price);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_MineBlockHp.ui");
        bindEvents(eventBuilder);
        populateFields(commandBuilder);
        buildBlockList(commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ConfigData data) {
        super.handleDataEvent(ref, store, data);
        if (data.hpValue != null) hpValue = data.hpValue.trim();
        if (data.priceValue != null) priceValue = data.priceValue.trim();
        if (data.button == null) return;

        if (data.button.startsWith(ConfigData.BUTTON_SELECT_PREFIX)) {
            selectedBlockId = data.button.substring(ConfigData.BUTTON_SELECT_PREFIX.length());
            loadFieldsForBlock(selectedBlockId);
            sendRefresh(ref, store);
            return;
        }

        switch (data.button) {
            case ConfigData.BUTTON_SET_HP -> handleSetHp(ref, store);
            case ConfigData.BUTTON_SET_PRICE -> handleSetPrice(ref, store);
            case ConfigData.BUTTON_BACK -> handleBack(ref, store);
            default -> {}
        }
    }

    private void handleSetHp(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if (selectedBlockId.isEmpty()) {
            player.sendMessage(Message.raw("Select a block first."));
            return;
        }
        try {
            int hp = Integer.parseInt(hpValue);
            if (hp < 1) {
                player.sendMessage(Message.raw("HP must be at least 1."));
                return;
            }
            mineConfigStore.saveBlockHp(selectedBlockId, hp);
            String name = MineBlockRegistry.getDisplayName(selectedBlockId);
            player.sendMessage(Message.raw(name + " HP -> " + hp));
            sendRefresh(ref, store);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("HP must be an integer."));
        }
    }

    private void handleSetPrice(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if (selectedBlockId.isEmpty()) {
            player.sendMessage(Message.raw("Select a block first."));
            return;
        }
        try {
            long price = Long.parseLong(priceValue);
            if (price < 1) {
                player.sendMessage(Message.raw("Price must be at least 1."));
                return;
            }
            mineConfigStore.saveBlockPrice(selectedBlockId, price);
            String name = MineBlockRegistry.getDisplayName(selectedBlockId);
            player.sendMessage(Message.raw(name + " Price -> " + price));
            sendRefresh(ref, store);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Price must be an integer."));
        }
    }

    private void handleBack(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pRef == null) return;
        player.getPageManager().openCustomPage(ref, store, new MineAdminPage(pRef, mineConfigStore));
    }

    private void bindEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#HpField",
            EventData.of(ConfigData.KEY_HP, "#HpField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PriceField",
            EventData.of(ConfigData.KEY_PRICE, "#PriceField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetHpButton",
            EventData.of(ConfigData.KEY_BUTTON, ConfigData.BUTTON_SET_HP), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetPriceButton",
            EventData.of(ConfigData.KEY_BUTTON, ConfigData.BUTTON_SET_PRICE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of(ConfigData.KEY_BUTTON, ConfigData.BUTTON_BACK), false);
    }

    private void populateFields(UICommandBuilder cmd) {
        boolean hasSelection = !selectedBlockId.isEmpty();
        cmd.set("#EditRow.Visible", hasSelection);

        if (hasSelection) {
            String name = MineBlockRegistry.getDisplayName(selectedBlockId);
            cmd.set("#SelectedBlockText.Text", name);
        } else {
            cmd.set("#SelectedBlockText.Text", "Select a block below");
        }
        cmd.set("#HpField.Value", hpValue);
        cmd.set("#PriceField.Value", priceValue);
    }

    private void buildBlockList(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.clear("#BlockEntries");
        int index = 0;

        for (Map.Entry<String, List<MineBlockRegistry.BlockDef>> category : MineBlockRegistry.getByCategory().entrySet()) {
            // Category header
            cmd.append("#BlockEntries", "Pages/Ascend_MineBlockPickerHeader.ui");
            cmd.set("#BlockEntries[" + index + "] #CategoryName.Text", category.getKey());
            index++;

            // Block entries
            for (MineBlockRegistry.BlockDef block : category.getValue()) {
                cmd.append("#BlockEntries", "Pages/Ascend_MineBlockConfigEntry.ui");
                String sel = "#BlockEntries[" + index + "]";

                cmd.set(sel + " #BlockIcon.ItemId", block.blockTypeId);
                cmd.set(sel + " #BlockName.Text", block.displayName);

                int hp = mineConfigStore.getBlockHp(block.blockTypeId);
                cmd.set(sel + " #BlockHpLabel.Text", hp + " HP");
                if (hp > 1) {
                    cmd.set(sel + " #BlockHpLabel.Style.TextColor", "#ef4444");
                }

                long price = mineConfigStore.getBlockPrice(block.blockTypeId);
                cmd.set(sel + " #BlockPriceLabel.Text", String.valueOf(price));
                if (price > 1) {
                    cmd.set(sel + " #BlockPriceLabel.Style.TextColor", "#22d3ee");
                }

                boolean isSelected = block.blockTypeId.equals(selectedBlockId);
                if (isSelected) {
                    cmd.set(sel + " #SelectedOverlay.Visible", true);
                }

                evt.addEventBinding(CustomUIEventBindingType.Activating, sel,
                    EventData.of(ConfigData.KEY_BUTTON, ConfigData.BUTTON_SELECT_PREFIX + block.blockTypeId), false);
                index++;
            }
        }
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        populateFields(cmd);
        buildBlockList(cmd, evt);
        bindEvents(evt);
        this.sendUpdate(cmd, evt, false);
    }

    public static class ConfigData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_HP = "@Hp";
        static final String KEY_PRICE = "@Price";
        static final String BUTTON_SELECT_PREFIX = "Select:";
        static final String BUTTON_SET_HP = "SetHp";
        static final String BUTTON_SET_PRICE = "SetPrice";
        static final String BUTTON_BACK = "Back";

        public static final BuilderCodec<ConfigData> CODEC = BuilderCodec.<ConfigData>builder(ConfigData.class, ConfigData::new)
            .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, value) -> data.button = value, data -> data.button)
            .addField(new KeyedCodec<>(KEY_HP, Codec.STRING), (data, value) -> data.hpValue = value, data -> data.hpValue)
            .addField(new KeyedCodec<>(KEY_PRICE, Codec.STRING), (data, value) -> data.priceValue = value, data -> data.priceValue)
            .build();

        private String button;
        private String hpValue;
        private String priceValue;
    }
}
