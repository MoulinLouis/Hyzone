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
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.common.math.BigNumber;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MineBlockPricesPage extends InteractiveCustomUIPage<MineBlockPricesPage.PriceData> {

    private final PlayerRef playerRef;
    private final MineConfigStore mineConfigStore;
    private String blockId = "";
    private String priceMantissa = "";
    private String priceExp = "";

    public MineBlockPricesPage(@Nonnull PlayerRef playerRef, MineConfigStore mineConfigStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PriceData.CODEC);
        this.playerRef = playerRef;
        this.mineConfigStore = mineConfigStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_MineBlockPrices.ui");
        bindEvents(eventBuilder);
        populateFields(commandBuilder);
        buildPriceList(commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PriceData data) {
        super.handleDataEvent(ref, store, data);
        if (data.blockId != null) {
            blockId = data.blockId.trim();
        }
        if (data.priceMantissa != null) {
            priceMantissa = data.priceMantissa.trim();
        }
        if (data.priceExp != null) {
            priceExp = data.priceExp.trim();
        }
        if (data.button == null) {
            return;
        }
        if (data.button.equals(PriceData.BUTTON_CLOSE)) {
            this.close();
            return;
        }
        if (data.button.startsWith(PriceData.BUTTON_SELECT_PREFIX)) {
            String selectedBlock = data.button.substring(PriceData.BUTTON_SELECT_PREFIX.length());
            blockId = selectedBlock;
            BigNumber price = mineConfigStore.getBlockPrice(selectedBlock);
            if (price != null && !price.equals(BigNumber.ONE)) {
                priceMantissa = String.valueOf(price.getMantissa());
                priceExp = String.valueOf(price.getExponent());
            } else {
                priceMantissa = "";
                priceExp = "";
            }
            sendRefresh(ref, store);
            return;
        }
        switch (data.button) {
            case PriceData.BUTTON_SET_PRICE -> handleSetPrice(ref, store);
            case PriceData.BUTTON_REMOVE_PRICE -> handleRemovePrice(ref, store);
            case PriceData.BUTTON_BACK -> handleBack(ref, store);
            default -> {
            }
        }
    }

    private void handleSetPrice(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if (blockId.isEmpty()) {
            player.sendMessage(Message.raw("Block type ID is required."));
            return;
        }
        if (priceMantissa.isEmpty()) {
            player.sendMessage(Message.raw("Price mantissa is required."));
            return;
        }
        try {
            double mantissa = Double.parseDouble(priceMantissa);
            int exp = priceExp.isEmpty() ? 0 : Integer.parseInt(priceExp);
            BigNumber price = BigNumber.of(mantissa, exp);
            mineConfigStore.saveBlockPrice(blockId, price);
            player.sendMessage(Message.raw("Price set: " + blockId + " -> " + price));
            sendRefresh(ref, store);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Invalid number format. Mantissa must be a number, exponent must be an integer."));
        }
    }

    private void handleRemovePrice(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if (blockId.isEmpty()) {
            player.sendMessage(Message.raw("Block type ID is required."));
            return;
        }
        mineConfigStore.removeBlockPrice(blockId);
        player.sendMessage(Message.raw("Price removed: " + blockId));
        blockId = "";
        priceMantissa = "";
        priceExp = "";
        sendRefresh(ref, store);
    }

    private void handleBack(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pRef == null) return;
        player.getPageManager().openCustomPage(ref, store, new MineAdminPage(pRef, mineConfigStore));
    }

    private void bindEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BlockIdField",
            EventData.of(PriceData.KEY_BLOCK_ID, "#BlockIdField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PriceMantissaField",
            EventData.of(PriceData.KEY_PRICE_MANTISSA, "#PriceMantissaField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PriceExpField",
            EventData.of(PriceData.KEY_PRICE_EXP, "#PriceExpField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetPriceButton",
            EventData.of(PriceData.KEY_BUTTON, PriceData.BUTTON_SET_PRICE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RemovePriceButton",
            EventData.of(PriceData.KEY_BUTTON, PriceData.BUTTON_REMOVE_PRICE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of(PriceData.KEY_BUTTON, PriceData.BUTTON_BACK), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(PriceData.KEY_BUTTON, PriceData.BUTTON_CLOSE), false);
    }

    private void populateFields(UICommandBuilder commandBuilder) {
        commandBuilder.set("#BlockIdField.Value", blockId);
        commandBuilder.set("#PriceMantissaField.Value", priceMantissa);
        commandBuilder.set("#PriceExpField.Value", priceExp);
        commandBuilder.set("#SelectedBlockText.Text", "Global Block Prices");
    }

    private void buildPriceList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#PriceCards");
        Map<String, BigNumber> prices = mineConfigStore.getBlockPrices();
        List<String> sortedKeys = new ArrayList<>(prices.keySet());
        sortedKeys.sort(Comparator.naturalOrder());
        int index = 0;
        for (String blockTypeId : sortedKeys) {
            BigNumber price = prices.get(blockTypeId);
            commandBuilder.append("#PriceCards", "Pages/Ascend_MineBlockPricesEntry.ui");
            String entrySelector = "#PriceCards[" + index + "]";
            String nameLabel = blockTypeId;
            boolean isSelected = blockTypeId.equals(blockId);
            if (isSelected) {
                nameLabel = ">> " + nameLabel;
                commandBuilder.set(entrySelector + ".Background", "#253742");
                commandBuilder.set(entrySelector + ".Style.Default.Background", "#253742");
            }
            commandBuilder.set(entrySelector + " #BlockName.Text", nameLabel);
            commandBuilder.set(entrySelector + " #BlockPrice.Text", "Price: " + price.toString());
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                entrySelector,
                EventData.of(PriceData.KEY_BUTTON, PriceData.BUTTON_SELECT_PREFIX + blockTypeId), false);
            index++;
        }
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateFields(commandBuilder);
        buildPriceList(commandBuilder, eventBuilder);
        bindEvents(eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    public static class PriceData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_BLOCK_ID = "@BlockId";
        static final String KEY_PRICE_MANTISSA = "@PriceMantissa";
        static final String KEY_PRICE_EXP = "@PriceExp";
        static final String BUTTON_SELECT_PREFIX = "Select:";
        static final String BUTTON_SET_PRICE = "SetPrice";
        static final String BUTTON_REMOVE_PRICE = "RemovePrice";
        static final String BUTTON_BACK = "Back";
        static final String BUTTON_CLOSE = "Close";

        public static final BuilderCodec<PriceData> CODEC = BuilderCodec.<PriceData>builder(PriceData.class, PriceData::new)
            .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, value) -> data.button = value, data -> data.button)
            .addField(new KeyedCodec<>(KEY_BLOCK_ID, Codec.STRING), (data, value) -> data.blockId = value, data -> data.blockId)
            .addField(new KeyedCodec<>(KEY_PRICE_MANTISSA, Codec.STRING), (data, value) -> data.priceMantissa = value, data -> data.priceMantissa)
            .addField(new KeyedCodec<>(KEY_PRICE_EXP, Codec.STRING), (data, value) -> data.priceExp = value, data -> data.priceExp)
            .build();

        private String button;
        private String blockId;
        private String priceMantissa;
        private String priceExp;
    }
}
