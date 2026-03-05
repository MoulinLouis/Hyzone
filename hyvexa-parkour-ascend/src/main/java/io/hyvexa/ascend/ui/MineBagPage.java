package io.hyvexa.ascend.ui;

import java.util.Map;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.common.ui.ButtonEventData;

public class MineBagPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";

    // blockTypeId -> icon element ID in the entry template
    private static final Map<String, String> BLOCK_ICONS = Map.of(
        "Rock_Stone", "#IconStone",
        "Rock_Crystal_Blue_Block", "#IconBlue",
        "Rock_Crystal_Green_Block", "#IconGreen",
        "Rock_Crystal_Pink_Block", "#IconPink",
        "Rock_Crystal_Red_Block", "#IconRed",
        "Rock_Crystal_White_Block", "#IconWhite",
        "Rock_Crystal_Yellow_Block", "#IconYellow"
    );

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

    public MineBagPage(@Nonnull PlayerRef playerRef, MinePlayerProgress mineProgress) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.mineProgress = mineProgress;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_MineBag.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        int total = mineProgress.getInventoryTotal();
        int capacity = mineProgress.getBagCapacity();

        // Capacity
        commandBuilder.set("#CapacityValue.Text", total + " / " + capacity + " blocks");

        // Crystals
        commandBuilder.set("#CrystalsValue.Text", String.valueOf(mineProgress.getCrystals()));

        // Block grid
        Map<String, Integer> inventory = mineProgress.getInventory();
        if (inventory.isEmpty()) {
            commandBuilder.set("#EmptyLabel.Visible", true);
            commandBuilder.set("#ScrollArea.Visible", false);
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

                // Set amount and name
                commandBuilder.set(sel + " #Amount.Text", "x" + entry.getValue());
                String displayName = BLOCK_DISPLAY_NAMES.getOrDefault(entry.getKey(), entry.getKey().replace('_', ' '));
                commandBuilder.set(sel + " #BlockName.Text", displayName);

                index++;
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (BUTTON_CLOSE.equals(data.getButton())) {
            this.close();
        }
    }
}
