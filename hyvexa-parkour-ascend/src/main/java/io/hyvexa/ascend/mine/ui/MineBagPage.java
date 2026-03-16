package io.hyvexa.ascend.mine.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
import io.hyvexa.ascend.mine.MineBlockDisplay;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.ui.ButtonEventData;

public class MineBagPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_SELL_ALL = "SellAll";
    private static final String SELL_PREFIX = "Sell:";
    private static final String LOCK_PREFIX = "Lock:";
    private static final String UNLOCK_PREFIX = "Unlock:";

    private static final Map<String, String> BLOCK_ICONS = Map.of(
        "Rock_Stone", "#IconStone",
        "Rock_Crystal_Blue_Block", "#IconBlue",
        "Rock_Crystal_Green_Block", "#IconGreen",
        "Rock_Crystal_Pink_Block", "#IconPink",
        "Rock_Crystal_Red_Block", "#IconRed",
        "Rock_Crystal_White_Block", "#IconWhite",
        "Rock_Crystal_Yellow_Block", "#IconYellow"
    );

    private final MinePlayerProgress mineProgress;
    private final PlayerRef playerRef;
    private final Set<String> lockedBlocks = new HashSet<>();

    public MineBagPage(@Nonnull PlayerRef playerRef, MinePlayerProgress mineProgress) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerRef = playerRef;
        this.mineProgress = mineProgress;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_MineBag.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellAllButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELL_ALL), false);

        populateContent(commandBuilder, eventBuilder);
    }

    private void populateContent(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        int total = mineProgress.getInventoryTotal();
        int capacity = mineProgress.getBagCapacity();

        commandBuilder.set("#CapacityValue.Text", total + " / " + capacity + " blocks");

        Map<String, BigNumber> prices = gatherAllPrices();
        long totalValue = mineProgress.calculateInventoryValue(prices);
        commandBuilder.set("#TotalValue.Text", totalValue + " crystals");

        commandBuilder.clear("#BagItems");

        Map<String, Integer> inventory = mineProgress.getInventory();
        if (inventory.isEmpty()) {
            commandBuilder.set("#EmptyLabel.Visible", true);
            commandBuilder.set("#ScrollArea.Visible", false);
            commandBuilder.set("#SellAllWrap.Visible", false);
        } else {
            commandBuilder.set("#EmptyLabel.Visible", false);
            int index = 0;
            for (var entry : inventory.entrySet()) {
                commandBuilder.append("#BagItems", "Pages/Ascend_MineBagEntry.ui");
                String sel = "#BagItems[" + index + "]";

                // Show the correct icon
                String iconId = BLOCK_ICONS.get(entry.getKey());
                if (iconId != null) {
                    commandBuilder.set(sel + " " + iconId + ".Visible", true);
                }

                // Set amount, name, and value
                commandBuilder.set(sel + " #Amount.Text", "x" + entry.getValue());
                commandBuilder.set(sel + " #BlockName.Text", MineBlockDisplay.getDisplayName(entry.getKey()));

                BigNumber price = prices.getOrDefault(entry.getKey(), BigNumber.ONE);
                long lineValue = price.multiply(BigNumber.of(entry.getValue(), 0)).toLong();
                commandBuilder.set(sel + " #Value.Text", lineValue + " crystals");

                // Lock state
                boolean locked = lockedBlocks.contains(entry.getKey());
                commandBuilder.set(sel + " #LockWrap.Visible", !locked);
                commandBuilder.set(sel + " #UnlockWrap.Visible", locked);
                commandBuilder.set(sel + " #SellWrap.Visible", !locked);
                commandBuilder.set(sel + " #SellDisabledWrap.Visible", locked);

                // Sell button
                String sellButtonSel = sel + " #SellButton";
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sellButtonSel,
                    EventData.of(ButtonEventData.KEY_BUTTON, SELL_PREFIX + entry.getKey()), false);

                // Lock button
                String lockButtonSel = sel + " #LockButton";
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, lockButtonSel,
                    EventData.of(ButtonEventData.KEY_BUTTON, LOCK_PREFIX + entry.getKey()), false);

                // Unlock button
                String unlockButtonSel = sel + " #UnlockButton";
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, unlockButtonSel,
                    EventData.of(ButtonEventData.KEY_BUTTON, UNLOCK_PREFIX + entry.getKey()), false);

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

        if (BUTTON_CLOSE.equals(button)) {
            this.close();
        } else if (BUTTON_SELL_ALL.equals(button)) {
            handleSellAll(ref, store);
        } else if (button.startsWith(SELL_PREFIX)) {
            handleSellBlock(ref, store, button.substring(SELL_PREFIX.length()));
        } else if (button.startsWith(LOCK_PREFIX)) {
            lockedBlocks.add(button.substring(LOCK_PREFIX.length()));
            sendRefresh(ref, store);
        } else if (button.startsWith(UNLOCK_PREFIX)) {
            lockedBlocks.remove(button.substring(UNLOCK_PREFIX.length()));
            sendRefresh(ref, store);
        }
    }

    private void handleSellBlock(Ref<EntityStore> ref, Store<EntityStore> store, String blockTypeId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        Map<String, BigNumber> prices = gatherAllPrices();
        long earned = mineProgress.sellBlock(blockTypeId, prices);
        if (earned <= 0) {
            player.sendMessage(Message.raw("Nothing to sell."));
            return;
        }

        lockedBlocks.remove(blockTypeId);
        markDirty();
        player.sendMessage(Message.raw("Sold " + MineBlockDisplay.getDisplayName(blockTypeId) + " for " + earned + " crystals!"));
        trackCrystals(earned);
        sendRefresh(ref, store);
    }

    private void handleSellAll(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        if (!mineProgress.hasInventoryItems()) {
            player.sendMessage(Message.raw("Nothing to sell."));
            return;
        }

        Map<String, BigNumber> prices = gatherAllPrices();
        long earned;
        if (lockedBlocks.isEmpty()) {
            earned = mineProgress.sellAll(prices);
        } else {
            earned = mineProgress.sellAllExcept(lockedBlocks, prices);
        }

        if (earned <= 0) {
            player.sendMessage(Message.raw("All blocks are locked."));
            return;
        }

        markDirty();
        player.sendMessage(Message.raw("Sold all blocks for " + earned + " crystals!"));
        trackCrystals(earned);
        sendRefresh(ref, store);
    }

    private void markDirty() {
        MinePlayerStore mineStore = ParkourAscendPlugin.getInstance().getMinePlayerStore();
        if (mineStore != null) {
            mineStore.markDirty(playerRef.getUuid());
        }
    }

    private void trackCrystals(long earned) {
        if (earned > 0) {
            MineAchievementTracker tracker = ParkourAscendPlugin.getInstance().getMineAchievementTracker();
            if (tracker != null) {
                tracker.incrementCrystalsEarned(playerRef.getUuid(), earned);
            }
        }
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateContent(commandBuilder, eventBuilder);
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
