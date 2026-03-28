package io.hyvexa.ascend.mine.ui;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.hyvexa.ascend.mine.MineBlockDisplay;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineUpgradeType;
import io.hyvexa.ascend.mine.quest.MineQuestManager;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.ascend.ui.PageRefreshScheduler;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.PermissionUtils;

public class ConveyorChestPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_COLLECT_ALL = "CollectAll";
    private static final String BUTTON_EMPTY_OP = "EmptyOp";
    private static final String BUTTON_UPGRADE_CAPACITY = "UpgradeCapacity";
    private static final String TAKE_PREFIX = "Take:";
    private static final long REFRESH_INTERVAL_MS = 1000L;

    private final MinePlayerProgress progress;
    private final PlayerRef playerRef;
    private final MinePlayerStore minePlayerStore;
    private final MineQuestManager mineQuestManager;

    private volatile ScheduledFuture<?> refreshTask;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final AtomicBoolean refreshRequested = new AtomicBoolean(false);
    private volatile boolean dismissed = false;
    private volatile int lastKnownBufferCount;

    public ConveyorChestPage(@Nonnull PlayerRef playerRef, MinePlayerProgress progress,
                              MinePlayerStore minePlayerStore) {
        this(playerRef, progress, minePlayerStore, null);
    }

    public ConveyorChestPage(@Nonnull PlayerRef playerRef, MinePlayerProgress progress,
                              MinePlayerStore minePlayerStore, MineQuestManager mineQuestManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerRef = playerRef;
        this.progress = progress;
        this.minePlayerStore = minePlayerStore;
        this.mineQuestManager = mineQuestManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_ConveyorChest.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CollectAllButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_COLLECT_ALL), false);

        if (PermissionUtils.isOp(playerRef.getUuid())) {
            commandBuilder.set("#EmptyOpWrap.Visible", true);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#EmptyOpButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_EMPTY_OP), false);
        }

        populateUpgradeBar(commandBuilder, eventBuilder);
        populateContent(commandBuilder, eventBuilder);
        lastKnownBufferCount = progress.getConveyorBufferCount();
        startAutoRefresh(ref, store);
    }

    private void populateContent(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        Map<String, Integer> buffer = progress.getConveyorBuffer();
        int totalItems = progress.getConveyorBufferCount();
        int bagSpace = Math.max(0, progress.getBagCapacity() - progress.getInventoryTotal());

        commandBuilder.set("#CapacityValue.Text", bagSpace + " / " + progress.getBagCapacity() + " free");
        commandBuilder.set("#BufferValue.Text", totalItems + " / " + progress.getConveyorCapacity() + " blocks");

        commandBuilder.clear("#BlockEntries");

        if (buffer.isEmpty()) {
            commandBuilder.set("#EmptyLabel.Visible", true);
            commandBuilder.set("#ScrollArea.Visible", false);
            commandBuilder.set("#CollectAllWrap.Visible", false);
        } else {
            commandBuilder.set("#EmptyLabel.Visible", false);
            int index = 0;
            for (var entry : buffer.entrySet()) {
                commandBuilder.append("#BlockEntries", "Pages/Ascend_ConveyorChestEntry.ui");
                String sel = "#BlockEntries[" + index + "]";

                commandBuilder.set(sel + " #BlockIcon.ItemId", MineBlockDisplay.getItemId(entry.getKey()));
                commandBuilder.set(sel + " #BlockName.Text", MineBlockDisplay.getDisplayName(entry.getKey()));
                commandBuilder.set(sel + " #Amount.Text", "x" + entry.getValue());

                String takeButtonSel = sel + " #TakeButton";
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, takeButtonSel,
                    EventData.of(ButtonEventData.KEY_BUTTON, TAKE_PREFIX + entry.getKey()), false);

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
        } else if (BUTTON_COLLECT_ALL.equals(button)) {
            handleCollectAll(ref, store);
        } else if (BUTTON_UPGRADE_CAPACITY.equals(button)) {
            handleUpgradeCapacity(ref, store);
        } else if (BUTTON_EMPTY_OP.equals(button)) {
            handleEmptyOp(ref, store);
        } else if (button.startsWith(TAKE_PREFIX)) {
            handleTakeBlock(ref, store, button.substring(TAKE_PREFIX.length()));
        }
    }

    private void populateUpgradeBar(UICommandBuilder cmd, UIEventBuilder evt) {
        MineUpgradeType type = MineUpgradeType.CONVEYOR_CAPACITY;
        int level = progress.getUpgradeLevel(type);
        int maxLevel = type.getMaxLevel();
        boolean maxed = level >= maxLevel;

        cmd.set("#UpgradeLevel.Text", maxed ? "MAX" : "Lv." + level);

        if (maxed) {
            cmd.set("#UpgradeBtn.Text", "Maxed");
            cmd.set("#UpgradeBtnDisabled.Visible", true);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#UpgradeBtn",
                EventData.of(ButtonEventData.KEY_BUTTON, "Noop"), false);
        } else {
            long cost = type.getCost(level);
            cmd.set("#UpgradeBtn.Text", "Upgrade \u2014 " + cost + " cryst");
            boolean canAfford = progress.getCrystals() >= cost;
            cmd.set("#UpgradeBtnDisabled.Visible", !canAfford);

            int nextCapacity = (int) type.getEffect(level + 1);
            cmd.set("#UpgradeBtn.TooltipText",
                "Conveyor Capacity\n" + type.getDescription()
                + "\n\nLevel " + level + "/" + maxLevel
                + "\nCurrent: " + (int) type.getEffect(level) + " blocks"
                + "\nNext: " + nextCapacity + " blocks"
                + "\nCost: " + cost + " crystals");

            evt.addEventBinding(CustomUIEventBindingType.Activating, "#UpgradeBtn",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_UPGRADE_CAPACITY), false);
        }
    }

    private void handleUpgradeCapacity(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        MineUpgradeType type = MineUpgradeType.CONVEYOR_CAPACITY;
        boolean success = progress.purchaseUpgrade(type);
        if (!success) {
            int level = progress.getUpgradeLevel(type);
            player.sendMessage(Message.raw(level >= type.getMaxLevel() ? "Already maxed!" : "Not enough crystals!"));
            return;
        }

        minePlayerStore.markDirty(playerRef.getUuid());

        // Quest: conveyor capacity upgrade
        if (mineQuestManager != null) {
            mineQuestManager.onUpgradePurchased(playerRef.getUuid(), type, progress.getUpgradeLevel(type));
        }

        player.sendMessage(Message.raw("Conveyor Capacity upgraded to Lv " + progress.getUpgradeLevel(type) + "!"));
        sendRefresh(ref, store);
    }

    private void handleTakeBlock(Ref<EntityStore> ref, Store<EntityStore> store, String blockTypeId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        int transferred = progress.transferBlockFromBuffer(blockTypeId);
        if (transferred > 0) {
            minePlayerStore.markDirty(playerRef.getUuid());
            player.sendMessage(Message.raw("Collected " + transferred + "x "
                + MineBlockDisplay.getDisplayName(blockTypeId) + "."));
        } else {
            player.sendMessage(Message.raw("Your bag is full!"));
        }

        if (!progress.hasConveyorItems()) {
            this.close();
        } else {
            sendRefresh(ref, store);
        }
    }

    private void handleCollectAll(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        if (!progress.hasConveyorItems()) {
            player.sendMessage(Message.raw("The chest is empty."));
            this.close();
            return;
        }

        int transferred = progress.transferBufferToInventory();
        if (transferred > 0) {
            minePlayerStore.markDirty(playerRef.getUuid());
            player.sendMessage(Message.raw("Collected " + transferred + " blocks from conveyor."));
        } else {
            player.sendMessage(Message.raw("Your bag is full!"));
        }

        if (!progress.hasConveyorItems()) {
            this.close();
        } else {
            sendRefresh(ref, store);
        }
    }

    private void handleEmptyOp(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || !PermissionUtils.isOp(player)) return;

        int count = progress.getConveyorBufferCount();
        progress.clearConveyorBuffer();
        minePlayerStore.markDirty(playerRef.getUuid());
        player.sendMessage(Message.raw("Emptied conveyor (" + count + " blocks discarded)."));
        this.close();
    }

    // ── Auto-refresh ────────────────────────────────────────────────────

    private void startAutoRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (refreshTask != null) return;
        if (store.getExternalData() == null) return;
        var world = store.getExternalData().getWorld();
        if (world == null) return;
        refreshTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            if (dismissed) { stopAutoRefresh(); return; }
            if (ref == null || !ref.isValid()) { stopAutoRefresh(); return; }
            int current = progress.getConveyorBufferCount();
            if (current == lastKnownBufferCount) return;
            lastKnownBufferCount = current;
            PageRefreshScheduler.requestRefresh(
                world, refreshInFlight, refreshRequested,
                () -> sendRefresh(ref, store),
                this::stopAutoRefresh,
                "ConveyorChestPage"
            );
        }, REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        refreshRequested.set(false);
    }

    @Override
    protected void stopBackgroundTasks() {
        dismissed = true;
        stopAutoRefresh();
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (dismissed) return;
        lastKnownBufferCount = progress.getConveyorBufferCount();
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateUpgradeBar(commandBuilder, eventBuilder);
        populateContent(commandBuilder, eventBuilder);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CollectAllButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_COLLECT_ALL), false);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }
}
