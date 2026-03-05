package io.hyvexa.ascend.mine.ui;

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
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineUpgradeType;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.common.ui.ButtonEventData;

public class MineUpgradePage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_BUY_PREFIX = "Buy_";

    private static final String[] DISPLAY_NAMES = {
        "Mining Speed",
        "Bag Capacity",
        "Multi-Break",
        "Auto-Sell"
    };

    private final MinePlayerProgress mineProgress;
    private final PlayerRef playerRef;

    public MineUpgradePage(@Nonnull PlayerRef playerRef, MinePlayerProgress mineProgress) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerRef = playerRef;
        this.mineProgress = mineProgress;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_MineUpgrade.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        populateContent(commandBuilder, eventBuilder);
    }

    private void populateContent(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.set("#CrystalsValue.Text", String.valueOf(mineProgress.getCrystals()));

        MineUpgradeType[] types = MineUpgradeType.values();
        for (int i = 0; i < types.length; i++) {
            MineUpgradeType type = types[i];
            int level = mineProgress.getUpgradeLevel(type);
            int maxLevel = type.getMaxLevel();
            boolean maxed = level >= maxLevel;

            commandBuilder.append("#UpgradeItems", "Pages/Ascend_MineUpgradeEntry.ui");
            String sel = "#UpgradeItems[" + i + "]";

            commandBuilder.set(sel + " #UpgradeName.Text", DISPLAY_NAMES[i]);
            commandBuilder.set(sel + " #LevelText.Text", "Lv " + level + " / " + maxLevel);
            commandBuilder.set(sel + " #EffectText.Text", getEffectDescription(type, level));

            if (maxed) {
                commandBuilder.set(sel + " #CostText.Visible", false);
                commandBuilder.set(sel + " #BuyWrap.Visible", false);
                commandBuilder.set(sel + " #MaxedLabel.Visible", true);
            } else {
                commandBuilder.set(sel + " #CostText.Text", "Cost: " + type.getCost(level));
                commandBuilder.set(sel + " #MaxedLabel.Visible", false);

                String buttonId = BUTTON_BUY_PREFIX + type.name();
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    sel + " #BuyButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, buttonId), false);
            }
        }
    }

    private String getEffectDescription(MineUpgradeType type, int level) {
        double effect = type.getEffect(level);
        return switch (type) {
            case MINING_SPEED -> "Speed: " + String.format("%.1f", effect) + "x";
            case BAG_CAPACITY -> "Capacity: " + (int) effect + " blocks";
            case MULTI_BREAK -> "Chance: " + (int) effect + "%";
            case AUTO_SELL -> effect >= 1.0 ? "Enabled" : "Disabled";
        };
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) return;

        if (BUTTON_CLOSE.equals(button)) {
            this.close();
            return;
        }

        if (button.startsWith(BUTTON_BUY_PREFIX)) {
            String typeName = button.substring(BUTTON_BUY_PREFIX.length());
            handleBuy(ref, store, typeName);
        }
    }

    private void handleBuy(Ref<EntityStore> ref, Store<EntityStore> store, String typeName) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        MineUpgradeType type;
        try {
            type = MineUpgradeType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return;
        }

        boolean success = mineProgress.purchaseUpgrade(type);
        if (!success) {
            int level = mineProgress.getUpgradeLevel(type);
            if (level >= type.getMaxLevel()) {
                player.sendMessage(Message.raw("Already maxed!"));
            } else {
                player.sendMessage(Message.raw("Not enough crystals!"));
            }
            return;
        }

        MinePlayerStore mineStore = ParkourAscendPlugin.getInstance().getMinePlayerStore();
        if (mineStore != null) {
            mineStore.markDirty(playerRef.getUuid());
        }

        int displayIndex = type.ordinal();
        player.sendMessage(Message.raw("Upgraded " + DISPLAY_NAMES[displayIndex] + " to Lv " + mineProgress.getUpgradeLevel(type) + "!"));

        sendRefresh(ref, store);
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateContent(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }
}
