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
import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineUpgradeType;
import io.hyvexa.ascend.mine.robot.MineRobotManager;
import io.hyvexa.ascend.mine.robot.MinerRobotState;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.PermissionUtils;

import java.util.List;

public class MineUpgradePage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_BUY_PREFIX = "Buy_";
    private static final String BUTTON_BUY_MINER_PREFIX = "BuyMiner_";
    private static final String BUTTON_MINER_SPEED_PREFIX = "MinerSpeed_";
    private static final String BUTTON_MINER_EVOLVE_PREFIX = "MinerEvolve_";
    private static final String BUTTON_RESET = "ResetAll";

    private static final int MINER_MAX_SPEED_PER_STAR = 25;
    private static final int MINER_MAX_STARS = 5;

    private static final String[] DISPLAY_NAMES = {
        "Mining Speed",
        "Bag Capacity",
        "Multi-Break"
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

        // Show reset button for OP players
        if (PermissionUtils.isOp(playerRef.getUuid())) {
            commandBuilder.set("#ResetWrap.Visible", true);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_RESET), false);
        }

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

        // --- Miner entries for each unlocked mine ---
        MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
        if (configStore != null) {
            List<Mine> mines = configStore.listMinesSorted();
            int entryIndex = types.length;
            for (Mine mine : mines) {
                if (!mineProgress.getMineSnapshot(mine.getId()).unlocked()) continue;

                MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(mine.getId());
                commandBuilder.append("#UpgradeItems", "Pages/Ascend_MineUpgradeEntry.ui");
                String sel = "#UpgradeItems[" + entryIndex + "]";

                commandBuilder.set(sel + " #UpgradeName.Text", mine.getName() + " Miner");

                if (!minerState.hasMiner()) {
                    commandBuilder.set(sel + " #LevelText.Text", "Not purchased");
                    commandBuilder.set(sel + " #EffectText.Text", "");
                    commandBuilder.set(sel + " #CostText.Text", "Cost: " + getMinerBuyCost());
                    commandBuilder.set(sel + " #MaxedLabel.Visible", false);

                    eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                        sel + " #BuyButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BUY_MINER_PREFIX + mine.getId()), false);
                } else {
                    int speedLevel = minerState.speedLevel();
                    int stars = minerState.stars();
                    boolean fullyMaxed = stars >= MINER_MAX_STARS && speedLevel >= MINER_MAX_SPEED_PER_STAR;

                    commandBuilder.set(sel + " #LevelText.Text", "Speed Lv " + speedLevel + " | Star " + stars);
                    double blocksPerMin = MinerRobotState.getProductionRate(speedLevel, stars);
                    commandBuilder.set(sel + " #EffectText.Text", String.format("%.1f", blocksPerMin) + " blocks/min");

                    if (fullyMaxed) {
                        commandBuilder.set(sel + " #CostText.Visible", false);
                        commandBuilder.set(sel + " #BuyWrap.Visible", false);
                        commandBuilder.set(sel + " #MaxedLabel.Visible", true);
                    } else if (speedLevel >= MINER_MAX_SPEED_PER_STAR) {
                        // Speed maxed for this star tier, show evolve
                        commandBuilder.set(sel + " #CostText.Text", "Cost: " + getMinerEvolveCost(stars));
                        commandBuilder.set(sel + " #MaxedLabel.Visible", false);

                        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                            sel + " #BuyButton",
                            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_MINER_EVOLVE_PREFIX + mine.getId()), false);
                    } else {
                        // Show speed upgrade
                        commandBuilder.set(sel + " #CostText.Text", "Cost: " + getMinerSpeedCost(speedLevel, stars));
                        commandBuilder.set(sel + " #MaxedLabel.Visible", false);

                        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                            sel + " #BuyButton",
                            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_MINER_SPEED_PREFIX + mine.getId()), false);
                    }
                }
                entryIndex++;
            }
        }
    }

    private String getEffectDescription(MineUpgradeType type, int level) {
        double effect = type.getEffect(level);
        return switch (type) {
            case MINING_SPEED -> "Speed: " + String.format("%.1f", effect) + "x";
            case BAG_CAPACITY -> "Capacity: " + (int) effect + " blocks";
            case MULTI_BREAK -> "Chance: " + (int) effect + "%";
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

        if (BUTTON_RESET.equals(button)) {
            handleResetAll(ref, store);
            return;
        }

        if (button.startsWith(BUTTON_BUY_MINER_PREFIX)) {
            String mineId = button.substring(BUTTON_BUY_MINER_PREFIX.length());
            handleBuyMiner(ref, store, mineId);
            return;
        }

        if (button.startsWith(BUTTON_MINER_SPEED_PREFIX)) {
            String mineId = button.substring(BUTTON_MINER_SPEED_PREFIX.length());
            handleMinerSpeedUpgrade(ref, store, mineId);
            return;
        }

        if (button.startsWith(BUTTON_MINER_EVOLVE_PREFIX)) {
            String mineId = button.substring(BUTTON_MINER_EVOLVE_PREFIX.length());
            handleMinerEvolve(ref, store, mineId);
            return;
        }

        if (button.startsWith(BUTTON_BUY_PREFIX)) {
            String typeName = button.substring(BUTTON_BUY_PREFIX.length());
            handleBuy(ref, store, typeName);
        }
    }

    private void handleResetAll(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if (!PermissionUtils.isOp(playerRef.getUuid())) return;

        for (MineUpgradeType type : MineUpgradeType.values()) {
            mineProgress.setUpgradeLevel(type, 0);
        }

        MinePlayerStore mineStore = ParkourAscendPlugin.getInstance().getMinePlayerStore();
        if (mineStore != null) {
            mineStore.markDirty(playerRef.getUuid());
        }

        player.sendMessage(Message.raw("All upgrades have been reset."));
        sendRefresh(ref, store);
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

    private void handleBuyMiner(Ref<EntityStore> ref, Store<EntityStore> store, String mineId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        long cost = getMinerBuyCost();
        MinePlayerProgress.MinerPurchaseResult result = mineProgress.purchaseMiner(mineId, cost);
        if (result == MinePlayerProgress.MinerPurchaseResult.ALREADY_OWNED) {
            player.sendMessage(Message.raw("Already own this miner!"));
            return;
        }
        if (result == MinePlayerProgress.MinerPurchaseResult.INSUFFICIENT_CRYSTALS) {
            player.sendMessage(Message.raw("Not enough crystals!"));
            return;
        }

        MinePlayerStore mineStore = ParkourAscendPlugin.getInstance().getMinePlayerStore();
        if (mineStore != null) {
            mineStore.markDirty(playerRef.getUuid());
        }

        syncBoughtMiner(store, mineId);

        String mineName = getMineName(mineId);
        player.sendMessage(Message.raw("Bought miner for " + mineName + "!"));
        sendRefresh(ref, store);
    }

    private void handleMinerSpeedUpgrade(Ref<EntityStore> ref, Store<EntityStore> store, String mineId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(mineId);
        int speedLevel = minerState.speedLevel();

        long cost = getMinerSpeedCost(speedLevel, minerState.stars());
        MinePlayerProgress.MinerSpeedUpgradeResult result =
            mineProgress.upgradeMinerSpeed(mineId, cost, MINER_MAX_SPEED_PER_STAR);
        if (result == MinePlayerProgress.MinerSpeedUpgradeResult.NO_MINER) {
            return;
        }
        if (result == MinePlayerProgress.MinerSpeedUpgradeResult.SPEED_MAXED) {
            player.sendMessage(Message.raw("Speed maxed! Evolve to continue."));
            return;
        }
        if (result == MinePlayerProgress.MinerSpeedUpgradeResult.INSUFFICIENT_CRYSTALS) {
            player.sendMessage(Message.raw("Not enough crystals!"));
            return;
        }

        MinePlayerStore mineStore = ParkourAscendPlugin.getInstance().getMinePlayerStore();
        if (mineStore != null) {
            mineStore.markDirty(playerRef.getUuid());
        }

        syncMinerSpeed(mineId);

        String mineName = getMineName(mineId);
        player.sendMessage(Message.raw(mineName + " Miner speed -> Lv " + (speedLevel + 1) + "!"));
        sendRefresh(ref, store);
    }

    private void handleMinerEvolve(Ref<EntityStore> ref, Store<EntityStore> store, String mineId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(mineId);
        int speedLevel = minerState.speedLevel();
        int stars = minerState.stars();

        long cost = getMinerEvolveCost(stars);
        MinePlayerProgress.MinerEvolutionResult result =
            mineProgress.evolveMiner(mineId, cost, MINER_MAX_SPEED_PER_STAR, MINER_MAX_STARS);
        if (result == MinePlayerProgress.MinerEvolutionResult.NO_MINER) {
            return;
        }
        if (result == MinePlayerProgress.MinerEvolutionResult.SPEED_NOT_MAXED) {
            player.sendMessage(Message.raw("Max speed first before evolving!"));
            return;
        }
        if (result == MinePlayerProgress.MinerEvolutionResult.STAR_MAXED) {
            player.sendMessage(Message.raw("Already max stars!"));
            return;
        }
        if (result == MinePlayerProgress.MinerEvolutionResult.INSUFFICIENT_CRYSTALS) {
            player.sendMessage(Message.raw("Not enough crystals!"));
            return;
        }

        MinePlayerStore mineStore = ParkourAscendPlugin.getInstance().getMinePlayerStore();
        if (mineStore != null) {
            mineStore.markDirty(playerRef.getUuid());
        }

        syncMinerEvolution(store, mineId);

        String mineName = getMineName(mineId);
        player.sendMessage(Message.raw(mineName + " Miner evolved to Star " + (stars + 1) + "!"));
        sendRefresh(ref, store);
    }

    private static long getMinerBuyCost() {
        return 1000L;
    }

    private static long getMinerSpeedCost(int speedLevel, int stars) {
        int totalLevel = stars * MINER_MAX_SPEED_PER_STAR + speedLevel;
        return Math.round(50 * Math.pow(1.15, totalLevel));
    }

    private static long getMinerEvolveCost(int stars) {
        return Math.round(5000 * Math.pow(3, stars));
    }

    private String getMineName(String mineId) {
        MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
        if (configStore != null) {
            Mine mine = configStore.getMine(mineId);
            if (mine != null) return mine.getName();
        }
        return mineId;
    }

    private void syncBoughtMiner(Store<EntityStore> store, String mineId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        MineRobotManager robotManager = plugin.getMineRobotManager();
        if (robotManager == null || store.getExternalData() == null) {
            return;
        }
        robotManager.syncPurchasedMiner(playerRef.getUuid(), mineId, store.getExternalData().getWorld());
    }

    private void syncMinerSpeed(String mineId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        MineRobotManager robotManager = plugin.getMineRobotManager();
        if (robotManager == null) {
            return;
        }
        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(mineId);
        robotManager.syncMinerSpeed(playerRef.getUuid(), mineId, minerState.speedLevel());
    }

    private void syncMinerEvolution(Store<EntityStore> store, String mineId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        MineRobotManager robotManager = plugin.getMineRobotManager();
        if (robotManager == null || store.getExternalData() == null) {
            return;
        }
        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(mineId);
        robotManager.syncMinerEvolution(
            playerRef.getUuid(),
            mineId,
            minerState.speedLevel(),
            minerState.stars(),
            store.getExternalData().getWorld()
        );
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        commandBuilder.clear("#UpgradeItems");
        populateContent(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }
}
