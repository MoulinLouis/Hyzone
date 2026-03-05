package io.hyvexa.ascend.mine.ui;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.ui.ButtonEventData;

public class MineSellPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_SELL_ALL = "SellAll";

    private static final Map<String, String> BLOCK_DISPLAY_NAMES = Map.of(
        "Rock_Stone", "Stone",
        "Rock_Crystal_Blue_Block", "Blue Crystal",
        "Rock_Crystal_Green_Block", "Green Crystal",
        "Rock_Crystal_Pink_Block", "Pink Crystal",
        "Rock_Crystal_Red_Block", "Red Crystal",
        "Rock_Crystal_White_Block", "White Crystal",
        "Rock_Crystal_Yellow_Block", "Yellow Crystal"
    );

    private final MinePlayerProgress mineProgress;
    private final PlayerRef playerRef;

    public MineSellPage(@Nonnull PlayerRef playerRef, MinePlayerProgress mineProgress) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerRef = playerRef;
        this.mineProgress = mineProgress;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_MineSell.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellAllButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELL_ALL), false);

        populateContent(commandBuilder);
    }

    private void populateContent(UICommandBuilder commandBuilder) {
        int total = mineProgress.getInventoryTotal();
        int capacity = mineProgress.getBagCapacity();

        commandBuilder.set("#CapacityValue.Text", total + " / " + capacity + " blocks");
        commandBuilder.set("#CrystalsValue.Text", mineProgress.getCrystals().toString());

        Map<String, BigNumber> prices = gatherAllPrices();
        BigNumber totalValue = mineProgress.calculateInventoryValue(prices);
        commandBuilder.set("#TotalValue.Text", totalValue.toString() + " crystals");

        Map<String, Integer> inventory = mineProgress.getInventory();
        if (inventory.isEmpty()) {
            commandBuilder.set("#EmptyLabel.Visible", true);
            commandBuilder.set("#ScrollArea.Visible", false);
            commandBuilder.set("#SellAllWrap.Visible", false);
        } else {
            commandBuilder.set("#EmptyLabel.Visible", false);
            int index = 0;
            for (var entry : inventory.entrySet()) {
                commandBuilder.append("#SellItems", "Pages/Ascend_MineSellEntry.ui");
                String sel = "#SellItems[" + index + "]";

                String displayName = BLOCK_DISPLAY_NAMES.getOrDefault(
                    entry.getKey(), entry.getKey().replace('_', ' '));
                commandBuilder.set(sel + " #BlockName.Text", displayName);
                commandBuilder.set(sel + " #Quantity.Text", "x" + entry.getValue());

                BigNumber price = prices.getOrDefault(entry.getKey(), BigNumber.ONE);
                BigNumber lineValue = price.multiply(BigNumber.of(entry.getValue(), 0));
                commandBuilder.set(sel + " #Value.Text", lineValue.toString() + " crystals");

                index++;
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) return;

        switch (button) {
            case BUTTON_CLOSE -> this.close();
            case BUTTON_SELL_ALL -> handleSellAll(ref, store);
        }
    }

    private void handleSellAll(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        if (mineProgress.getInventory().isEmpty()) {
            player.sendMessage(Message.raw("Nothing to sell."));
            return;
        }

        Map<String, BigNumber> prices = gatherAllPrices();
        BigNumber earned = mineProgress.sellAll(prices);

        MinePlayerStore mineStore = ParkourAscendPlugin.getInstance().getMinePlayerStore();
        if (mineStore != null) {
            mineStore.markDirty(playerRef.getUuid());
        }

        player.sendMessage(Message.raw("Sold all blocks for " + earned.toString() + " crystals!"));

        sendRefresh(ref, store);
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateContent(commandBuilder);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellAllButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELL_ALL), false);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private Map<String, BigNumber> gatherAllPrices() {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return Map.of();
        MineConfigStore config = plugin.getMineConfigStore();
        if (config == null) return Map.of();

        Map<String, BigNumber> prices = new HashMap<>();
        for (Mine mine : config.listMinesSorted()) {
            prices.putAll(config.getBlockPrices(mine.getId()));
        }
        return prices;
    }
}
