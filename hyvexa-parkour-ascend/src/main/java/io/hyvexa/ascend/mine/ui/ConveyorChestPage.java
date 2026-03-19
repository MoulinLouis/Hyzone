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
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.common.ui.ButtonEventData;

public class ConveyorChestPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_COLLECT_ALL = "CollectAll";
    private static final String TAKE_PREFIX = "Take:";

    private final MinePlayerProgress progress;
    private final PlayerRef playerRef;
    private final MinePlayerStore minePlayerStore;

    public ConveyorChestPage(@Nonnull PlayerRef playerRef, MinePlayerProgress progress,
                              MinePlayerStore minePlayerStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerRef = playerRef;
        this.progress = progress;
        this.minePlayerStore = minePlayerStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_ConveyorChest.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CollectAllButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_COLLECT_ALL), false);

        populateContent(commandBuilder, eventBuilder);
    }

    private void populateContent(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        Map<String, Integer> buffer = progress.getConveyorBuffer();
        int totalItems = progress.getConveyorBufferCount();
        int bagSpace = Math.max(0, progress.getBagCapacity() - progress.getInventoryTotal());

        commandBuilder.set("#CapacityValue.Text", bagSpace + " / " + progress.getBagCapacity() + " free");
        commandBuilder.set("#BufferValue.Text", totalItems + " blocks");

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
        } else if (button.startsWith(TAKE_PREFIX)) {
            handleTakeBlock(ref, store, button.substring(TAKE_PREFIX.length()));
        }
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

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateContent(commandBuilder, eventBuilder);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CollectAllButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_COLLECT_ALL), false);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }
}
