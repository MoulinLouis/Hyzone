package io.hyvexa.ascend.mine.ui;

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

import io.hyvexa.ascend.mine.MineBlockDisplay;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.ui.BaseAscendPage;

import io.hyvexa.common.ui.ButtonEventData;

public class MineSellPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_SELL_ALL = "SellAll";

    private final MinePlayerProgress mineProgress;
    private final PlayerRef playerRef;
    private final MineConfigStore configStore;
    private final MinePlayerStore minePlayerStore;
    private final MineAchievementTracker mineAchievementTracker;

    public MineSellPage(@Nonnull PlayerRef playerRef, MinePlayerProgress mineProgress,
                        MineConfigStore configStore, MinePlayerStore minePlayerStore,
                        MineAchievementTracker mineAchievementTracker) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerRef = playerRef;
        this.mineProgress = mineProgress;
        this.configStore = configStore;
        this.minePlayerStore = minePlayerStore;
        this.mineAchievementTracker = mineAchievementTracker;
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
        commandBuilder.set("#CrystalsValue.Text", FormatUtils.formatDouble(mineProgress.getCrystals()));

        Map<String, Long> prices = gatherAllPrices();
        long totalValue = mineProgress.calculateInventoryValue(prices);
        commandBuilder.set("#TotalValue.Text", totalValue + " crystals");

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

                commandBuilder.set(sel + " #BlockName.Text", MineBlockDisplay.getDisplayName(entry.getKey()));
                commandBuilder.set(sel + " #Quantity.Text", "x" + entry.getValue());

                long price = prices.getOrDefault(entry.getKey(), 1L);
                long lineValue = price * entry.getValue();
                commandBuilder.set(sel + " #Value.Text", lineValue + " crystals");

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

        if (!mineProgress.hasInventoryItems()) {
            player.sendMessage(Message.raw("Nothing to sell."));
            return;
        }

        Map<String, Long> prices = gatherAllPrices();
        long earned = mineProgress.sellAll(prices);

        if (minePlayerStore != null) {
            minePlayerStore.markDirty(playerRef.getUuid());
        }

        player.sendMessage(Message.raw("Sold all blocks for " + earned + " crystals!"));

        // Track crystals earned for achievements
        if (earned > 0 && mineAchievementTracker != null) {
            mineAchievementTracker.incrementCrystalsEarned(playerRef.getUuid(), earned);
        }

        sendRefresh(ref, store);
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.clear("#SellItems");
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateContent(commandBuilder);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellAllButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELL_ALL), false);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private Map<String, Long> gatherAllPrices() {
        return configStore != null ? configStore.getBlockPrices() : Map.of();
    }
}
