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
import io.hyvexa.ascend.mine.achievement.MineAchievement;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
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
import java.util.UUID;

import io.hyvexa.ascend.mine.data.PickaxeTier;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Tabbed mine page opened by pickaxe right-click.
 * Tab "Miner": per-mine miner cards (buy, upgrade speed, evolve).
 * Tab "Upgrade": global pickaxe upgrades (bag capacity).
 */
public class MinePage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TAB_MINER = "TabMiner";
    private static final String BUTTON_TAB_UPGRADE = "TabUpgrade";
    private static final String BUTTON_BUY_UPGRADE_PREFIX = "BuyUpgrade_";
    private static final String BUTTON_BUY_MINER_PREFIX = "BuyMiner_";
    private static final String BUTTON_MINER_SPEED_PREFIX = "MinerSpeed_";
    private static final String BUTTON_MINER_EVOLVE_PREFIX = "MinerEvolve_";
    private static final String BUTTON_RESET = "ResetAll";
    private static final String BUTTON_BUY_PICKAXE = "BuyPickaxe";

    private static final String[] ACCENT_COLORS = {
        "Violet", "Red", "Orange", "Green", "Blue", "Gold"
    };
    private static final String[] ACCENT_HEX = {
        "#7c3aed", "#ef4444", "#f59e0b", "#10b981", "#3b82f6", "#f59e0b"
    };

    private static final String[] UPGRADE_ACCENT_COLORS = {
        "Green", "Blue", "Gold", "Red", "Orange", "Violet", "Blue"
    };
    private static final String[] UPGRADE_ACCENT_HEX = {
        "#10b981", "#3b82f6", "#f59e0b", "#ef4444", "#f97316", "#7c3aed", "#3b82f6"
    };
    private static final int UPGRADE_SEGMENT_COUNT = 20;
    private static final MineUpgradeType[] GRID_UPGRADE_ORDER = {
        MineUpgradeType.MOMENTUM,
        MineUpgradeType.FORTUNE,
        MineUpgradeType.JACKHAMMER,
        MineUpgradeType.STOMP,
        MineUpgradeType.BLAST,
        MineUpgradeType.HASTE
    };

    private final MinePlayerProgress mineProgress;
    private final PlayerRef playerRef;
    private boolean minerTabActive = false;

    public MinePage(@Nonnull PlayerRef playerRef, MinePlayerProgress mineProgress) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerRef = playerRef;
        this.mineProgress = mineProgress;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Ascend_MinePage.ui");

        // Close button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        // Tab buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabMiner",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_MINER), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabUpgrade",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_UPGRADE), false);

        // OP reset
        if (PermissionUtils.isOp(playerRef.getUuid())) {
            cmd.set("#ResetWrap.Visible", true);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_RESET), false);
        }

        populateMinerTab(cmd, evt);
        populateUpgradeTab(cmd, evt);
    }

    // ==================== Content Population ====================

    private void populateMinerTab(UICommandBuilder cmd, UIEventBuilder evt) {
        MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
        if (configStore == null) return;

        Mine mine = configStore.getMine();
        if (mine == null) return;

        List<io.hyvexa.ascend.mine.data.MinerSlot> slots = configStore.getMinerSlots();
        int entryIndex = 0;
        if (slots.isEmpty()) {
            entryIndex = addMinerEntry(cmd, evt, entryIndex, mine, 0);
        } else {
            for (io.hyvexa.ascend.mine.data.MinerSlot slot : slots) {
                entryIndex = addMinerEntry(cmd, evt, entryIndex, mine, slot.getSlotIndex());
            }
        }
    }

    private int addMinerEntry(UICommandBuilder cmd, UIEventBuilder evt, int i,
                               Mine mine, int slotIndex) {
        cmd.append("#MinerEntries", "Pages/Ascend_MinePageMinerEntry.ui");
        String sel = "#MinerEntries[" + i + "]";
        String slotId = String.valueOf(slotIndex);

        // Accent color + button zone color
        int colorIndex = i % ACCENT_COLORS.length;
        String colorName = ACCENT_COLORS[colorIndex];
        String colorHex = ACCENT_HEX[colorIndex];
        cmd.set(sel + " #AccentViolet.Visible", colorIndex == 0);
        cmd.set(sel + " #Accent" + colorName + ".Visible", true);
        cmd.set(sel + " #ButtonBg" + colorName + ".Visible", true);

        // Set segment colors to match accent
        for (int seg = 1; seg <= MinerRobotState.MAX_SPEED_PER_STAR; seg++) {
            cmd.set(sel + " #Seg" + seg + ".Background", colorHex);
        }

        // Label: "Mine Name" or "Mine Name #2" for slot > 0
        String label = mine.getName();
        if (slotIndex > 0) {
            label = label + " #" + (slotIndex + 1);
        }
        cmd.set(sel + " #MineName.Text", label);

        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(slotIndex);
        boolean hasMiner = minerState.hasMiner();
        int speedLevel = hasMiner ? minerState.speedLevel() : 0;
        int stars = hasMiner ? minerState.stars() : 0;

        // Progress bar segments
        for (int seg = 1; seg <= speedLevel; seg++) {
            cmd.set(sel + " #Seg" + seg + ".Visible", true);
        }

        // Stars
        for (int s = 1; s <= stars; s++) {
            cmd.set(sel + " #Star" + s + ".Visible", true);
        }

        // Level text
        cmd.set(sel + " #MinerLevel.Style.TextColor", "#ffffff");

        // Bind buy button
        evt.addEventBinding(CustomUIEventBindingType.Activating,
            sel + " #MinerBuyButton",
            EventData.of(ButtonEventData.KEY_BUTTON, resolveMinerButtonId(hasMiner, speedLevel, stars, slotId)), false);

        if (!hasMiner) {
            cmd.set(sel + " #MinerLevel.Text", "");
            cmd.set(sel + " #MinerActionStatus.Text", "No Miner");
            cmd.set(sel + " #ActionText.Text", "Buy Miner");
            cmd.set(sel + " #ActionPrice.Text", "");
            cmd.set(sel + " #MinerStatus.Text", "");
        } else {
            boolean fullyMaxed = stars >= MinerRobotState.MAX_STARS && speedLevel >= MinerRobotState.MAX_SPEED_PER_STAR;
            double blocksPerMin = MinerRobotState.getProductionRate(speedLevel, stars);
            cmd.set(sel + " #MinerStatus.Text", String.format("%.1f", blocksPerMin) + " blocks/min");

            if (fullyMaxed) {
                cmd.set(sel + " #MinerLevel.Text", "MAX");
                cmd.set(sel + " #MinerActionStatus.Text", String.format("%.1f", blocksPerMin) + " b/m");
                cmd.set(sel + " #ActionText.Text", "Maxed!");
                cmd.set(sel + " #ActionPrice.Text", "");
            } else if (speedLevel >= MinerRobotState.MAX_SPEED_PER_STAR) {
                cmd.set(sel + " #MinerLevel.Text", "Lv." + speedLevel);
                cmd.set(sel + " #MinerActionStatus.Text", String.format("%.1f", blocksPerMin) + " b/m");
                cmd.set(sel + " #ActionText.Text", "Evolve");
                cmd.set(sel + " #ActionPrice.Text", "");
            } else {
                cmd.set(sel + " #MinerLevel.Text", "Lv." + speedLevel);
                cmd.set(sel + " #MinerActionStatus.Text", String.format("%.1f", blocksPerMin) + " b/m");
                long cost = MinerRobotState.getMinerSpeedCost(speedLevel, stars);
                cmd.set(sel + " #ActionText.Text", "Cost:");
                cmd.set(sel + " #ActionPrice.Text", cost + " cryst");
                boolean canAfford = mineProgress.getCrystals() >= cost;
                cmd.set(sel + " #ButtonDisabledOverlay.Visible", !canAfford);
                if (!canAfford) {
                    cmd.set(sel + " #MinerActionStatus.Style.TextColor", "#9fb0ba");
                    cmd.set(sel + " #ActionText.Style.TextColor", "#9fb0ba");
                    cmd.set(sel + " #ActionPrice.Style.TextColor", "#9fb0ba");
                }
            }
        }
        return i + 1;
    }

    private String resolveMinerButtonId(boolean hasMiner, int speedLevel, int stars, String slotId) {
        if (!hasMiner) return BUTTON_BUY_MINER_PREFIX + slotId;
        if (stars >= MinerRobotState.MAX_STARS && speedLevel >= MinerRobotState.MAX_SPEED_PER_STAR) return "Noop";
        if (speedLevel >= MinerRobotState.MAX_SPEED_PER_STAR) return BUTTON_MINER_EVOLVE_PREFIX + slotId;
        return BUTTON_MINER_SPEED_PREFIX + slotId;
    }

    private void populateUpgradeTab(UICommandBuilder cmd, UIEventBuilder evt) {
        // --- Pickaxe Tier Card ---
        populatePickaxeCard(cmd, evt);

        // --- Grid cards (6 power upgrades in 3x2 grid) ---
        for (int slot = 0; slot < GRID_UPGRADE_ORDER.length; slot++) {
            MineUpgradeType type = GRID_UPGRADE_ORDER[slot];
            cmd.append("#Slot" + slot, "Pages/Ascend_MinePageUpgradeCard.ui");
            String sel = "#Slot" + slot + "[0]";
            populateUpgradeCard(cmd, evt, sel, type);
        }

        // --- Bag Capacity (bottom row, uses existing horizontal entry) ---
        populateBagCapacityEntry(cmd, evt);
    }

    private void populateUpgradeCard(UICommandBuilder cmd, UIEventBuilder evt,
                                     String sel, MineUpgradeType type) {
        int level = mineProgress.getUpgradeLevel(type);
        int maxLevel = type.getMaxLevel();
        boolean maxed = level >= maxLevel;
        int ordinal = type.ordinal();

        String colorName = UPGRADE_ACCENT_COLORS[ordinal];
        String colorHex = UPGRADE_ACCENT_HEX[ordinal];

        // Accent bar color
        cmd.set(sel + " #Accent" + colorName + ".Visible", true);

        // Labels
        cmd.set(sel + " #CardName.Text", type.getDisplayName());
        cmd.set(sel + " #CardEffect.Text", getEffectDescription(type, level));

        // Tooltip
        cmd.set(sel + " #CardBuyBtn.TooltipText", buildUpgradeTooltip(type, level, maxLevel));

        if (maxed) {
            cmd.set(sel + " #CardLevel.Text", "MAX");
            cmd.set(sel + " #CardCost.Text", "");
            evt.addEventBinding(CustomUIEventBindingType.Activating,
                sel + " #CardBuyBtn",
                EventData.of(ButtonEventData.KEY_BUTTON, "Noop"), false);
        } else {
            cmd.set(sel + " #CardLevel.Text", "Lv." + level);
            long cost = type.getCost(level);
            cmd.set(sel + " #CardCost.Text", cost + " cryst");
            boolean canAfford = mineProgress.getCrystals() >= cost;
            cmd.set(sel + " #CardDisabled.Visible", !canAfford);
            evt.addEventBinding(CustomUIEventBindingType.Activating,
                sel + " #CardBuyBtn",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BUY_UPGRADE_PREFIX + type.name()), false);
        }
    }

    private void populateBagCapacityEntry(UICommandBuilder cmd, UIEventBuilder evt) {
        MineUpgradeType type = MineUpgradeType.BAG_CAPACITY;
        int level = mineProgress.getUpgradeLevel(type);
        int maxLevel = type.getMaxLevel();
        boolean maxed = level >= maxLevel;

        cmd.append("#SlotBag", "Pages/Ascend_MinePageUpgradeEntry.ui");
        String sel = "#SlotBag[0]";

        // Accent + button: Green
        cmd.set(sel + " #AccentViolet.Visible", false);
        cmd.set(sel + " #AccentGreen.Visible", true);
        cmd.set(sel + " #ButtonBgGreen.Visible", true);

        // Segment colors + progress
        String colorHex = "#10b981";
        int filledSegments = maxLevel > 0 ? Math.min(UPGRADE_SEGMENT_COUNT, level * UPGRADE_SEGMENT_COUNT / maxLevel) : 0;
        for (int seg = 1; seg <= UPGRADE_SEGMENT_COUNT; seg++) {
            cmd.set(sel + " #Seg" + seg + ".Background", colorHex);
            if (seg <= filledSegments) {
                cmd.set(sel + " #Seg" + seg + ".Visible", true);
            }
        }

        cmd.set(sel + " #UpgradeName.Text", "Bag Capacity");
        cmd.set(sel + " #EffectText.Text", getEffectDescription(type, level));

        evt.addEventBinding(CustomUIEventBindingType.Activating,
            sel + " #UpgradeBuyButton",
            EventData.of(ButtonEventData.KEY_BUTTON, maxed ? "Noop" : BUTTON_BUY_UPGRADE_PREFIX + type.name()), false);

        if (maxed) {
            cmd.set(sel + " #LevelText.Text", "MAX");
            cmd.set(sel + " #UpgradeStatus.Text", getEffectDescription(type, level));
            cmd.set(sel + " #ActionText.Text", "Maxed!");
            cmd.set(sel + " #ActionPrice.Text", "");
        } else {
            cmd.set(sel + " #LevelText.Text", "Lv." + level);
            cmd.set(sel + " #UpgradeStatus.Text", getEffectDescription(type, level));
            long cost = type.getCost(level);
            cmd.set(sel + " #ActionText.Text", "Cost:");
            cmd.set(sel + " #ActionPrice.Text", cost + " cryst");
            boolean canAfford = mineProgress.getCrystals() >= cost;
            cmd.set(sel + " #ButtonDisabledOverlay.Visible", !canAfford);
            if (!canAfford) {
                cmd.set(sel + " #UpgradeStatus.Style.TextColor", "#9fb0ba");
                cmd.set(sel + " #ActionText.Style.TextColor", "#9fb0ba");
                cmd.set(sel + " #ActionPrice.Style.TextColor", "#9fb0ba");
            }
        }
    }

    private void populatePickaxeCard(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.append("#PickaxeRow", "Pages/Ascend_MinePagePickaxeCard.ui");
        String sel = "#PickaxeRow[0]";

        PickaxeTier current = mineProgress.getPickaxeTierEnum();
        PickaxeTier next = current.next();

        cmd.set(sel + " #TierName.Text", current.getDisplayName());
        cmd.set(sel + " #SpeedText.Text", "Speed: " + String.format("%.1f", current.getSpeedMultiplier()) + "x");
        cmd.set(sel + " #TierLabel.Text", "Tier " + current.getTier());

        if (next == null) {
            // Maxed
            cmd.set(sel + " #RequirementText.Text", "");
            cmd.set(sel + " #ActionText.Text", "Maxed!");
            cmd.set(sel + " #ActionPrice.Text", "");
            evt.addEventBinding(CustomUIEventBindingType.Activating,
                sel + " #PickaxeBuyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, "Noop"), false);
        } else {
            cmd.set(sel + " #RequirementText.Text", "Next: " + next.getDisplayName());
            cmd.set(sel + " #ActionText.Text", "Upgrade");
            cmd.set(sel + " #ActionPrice.Text", next.getUnlockCost() + " cryst");

            boolean meetsReq = checkPickaxeRequirement(next);
            boolean canAfford = mineProgress.getCrystals() >= next.getUnlockCost();
            boolean canBuy = meetsReq && canAfford;

            if (!canBuy) {
                cmd.set(sel + " #ButtonDisabledOverlay.Visible", true);
                if (!meetsReq) {
                    cmd.set(sel + " #RequirementText.Text", "Requires: " + next.getRequirementDescription());
                    cmd.set(sel + " #RequirementText.Style.TextColor", "#ef4444");
                }
            }

            evt.addEventBinding(CustomUIEventBindingType.Activating,
                sel + " #PickaxeBuyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, canBuy ? BUTTON_BUY_PICKAXE : "Noop"), false);
        }
    }

    private boolean checkPickaxeRequirement(PickaxeTier tier) {
        // Single mine: mine-count requirements are always met
        return true;
    }

    // ==================== Tab Switching ====================
    // Uses overlay visibility pattern from AscendLeaderboardPage.setTabActive()

    private void switchToMinerTab() {
        if (minerTabActive) return;
        minerTabActive = true;

        UICommandBuilder cmd = new UICommandBuilder();
        setTabActive(cmd, "TabMiner", true);
        setTabActive(cmd, "TabUpgrade", false);
        cmd.set("#MinerContent.Visible", true);
        cmd.set("#UpgradeContent.Visible", false);

        this.sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private void switchToUpgradeTab() {
        if (!minerTabActive) return;
        minerTabActive = false;

        UICommandBuilder cmd = new UICommandBuilder();
        setTabActive(cmd, "TabMiner", false);
        setTabActive(cmd, "TabUpgrade", true);
        cmd.set("#MinerContent.Visible", false);
        cmd.set("#UpgradeContent.Visible", true);

        this.sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private void setTabActive(UICommandBuilder cmd, String tabId, boolean active) {
        String wrapPath = "#" + tabId + "Wrap";
        String accentPath = "#" + tabId + "Accent";
        cmd.set(wrapPath + " #" + tabId + "ActiveBg.Visible", active);
        cmd.set(wrapPath + " #" + tabId + "InactiveBg.Visible", !active);
        cmd.set(wrapPath + " " + accentPath + " #" + tabId + "AccentActive.Visible", active);
        cmd.set(wrapPath + " " + accentPath + " #" + tabId + "AccentInactive.Visible", !active);
    }

    // ==================== Event Handling ====================

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) return;

        switch (button) {
            case BUTTON_CLOSE -> this.close();
            case BUTTON_TAB_MINER -> switchToMinerTab();
            case BUTTON_TAB_UPGRADE -> switchToUpgradeTab();
            case BUTTON_RESET -> handleResetAll(ref, store);
            default -> handleActionButton(ref, store, button);
        }
    }

    private void handleActionButton(Ref<EntityStore> ref, Store<EntityStore> store, String button) {
        if (button.equals(BUTTON_BUY_PICKAXE)) {
            handleBuyPickaxe(ref, store);
        } else if (button.startsWith(BUTTON_BUY_MINER_PREFIX)) {
            int slotIndex = Integer.parseInt(button.substring(BUTTON_BUY_MINER_PREFIX.length()));
            handleBuyMiner(ref, store, slotIndex);
        } else if (button.startsWith(BUTTON_MINER_SPEED_PREFIX)) {
            int slotIndex = Integer.parseInt(button.substring(BUTTON_MINER_SPEED_PREFIX.length()));
            handleMinerSpeedUpgrade(ref, store, slotIndex);
        } else if (button.startsWith(BUTTON_MINER_EVOLVE_PREFIX)) {
            int slotIndex = Integer.parseInt(button.substring(BUTTON_MINER_EVOLVE_PREFIX.length()));
            handleMinerEvolve(ref, store, slotIndex);
        } else if (button.startsWith(BUTTON_BUY_UPGRADE_PREFIX)) {
            handleBuyUpgrade(ref, store, button.substring(BUTTON_BUY_UPGRADE_PREFIX.length()));
        }
    }

    private void handleBuyPickaxe(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        PickaxeTier next = mineProgress.getPickaxeTierEnum().next();
        if (next == null) {
            player.sendMessage(Message.raw("Already at max pickaxe tier!"));
            return;
        }

        if (!checkPickaxeRequirement(next)) {
            player.sendMessage(Message.raw("Requirement not met: " + next.getRequirementDescription()));
            return;
        }

        MinePlayerProgress.PickaxeUpgradeResult result = mineProgress.purchasePickaxeTier();
        if (result == MinePlayerProgress.PickaxeUpgradeResult.ALREADY_MAXED) {
            player.sendMessage(Message.raw("Already at max pickaxe tier!"));
            return;
        }
        if (result == MinePlayerProgress.PickaxeUpgradeResult.INSUFFICIENT_CRYSTALS) {
            player.sendMessage(Message.raw("Not enough crystals!"));
            return;
        }

        markDirty();

        // Swap held pickaxe to new tier
        swapPickaxeItem(player);

        player.sendMessage(Message.raw("Upgraded to " + next.getDisplayName() + "!"));
        sendRefresh(ref, store);
    }

    private void swapPickaxeItem(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) return;
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null) return;
        String newItemId = mineProgress.getPickaxeTierEnum().getItemId();
        hotbar.setItemStackForSlot((short) 0, new ItemStack(newItemId, 1), false);
    }

    // ==================== Miner Handlers ====================

    private void handleBuyMiner(Ref<EntityStore> ref, Store<EntityStore> store, int slotIndex) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        long cost = MinerRobotState.getMinerBuyCost();
        MinePlayerProgress.MinerPurchaseResult result = mineProgress.purchaseMiner(slotIndex, cost);
        if (result == MinePlayerProgress.MinerPurchaseResult.ALREADY_OWNED) {
            player.sendMessage(Message.raw("Already own this miner!"));
            return;
        }
        if (result == MinePlayerProgress.MinerPurchaseResult.INSUFFICIENT_CRYSTALS) {
            player.sendMessage(Message.raw("Not enough crystals!"));
            return;
        }

        markDirty();
        syncBoughtMiner(store, slotIndex);
        checkMineAchievement(MineAchievement.FIRST_MINER);

        String mineName = getMineName();
        String label = slotIndex > 0 ? mineName + " #" + (slotIndex + 1) : mineName;
        player.sendMessage(Message.raw("Bought miner for " + label + "!"));
        sendRefresh(ref, store);
    }

    private void handleMinerSpeedUpgrade(Ref<EntityStore> ref, Store<EntityStore> store, int slotIndex) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(slotIndex);
        int speedLevel = minerState.speedLevel();

        long cost = MinerRobotState.getMinerSpeedCost(speedLevel, minerState.stars());
        MinePlayerProgress.MinerSpeedUpgradeResult result =
            mineProgress.upgradeMinerSpeed(slotIndex, cost, MinerRobotState.MAX_SPEED_PER_STAR);
        if (result == MinePlayerProgress.MinerSpeedUpgradeResult.NO_MINER) return;
        if (result == MinePlayerProgress.MinerSpeedUpgradeResult.SPEED_MAXED) {
            player.sendMessage(Message.raw("Speed maxed! Evolve to continue."));
            return;
        }
        if (result == MinePlayerProgress.MinerSpeedUpgradeResult.INSUFFICIENT_CRYSTALS) {
            player.sendMessage(Message.raw("Not enough crystals!"));
            return;
        }

        markDirty();
        syncMinerSpeed(slotIndex);

        player.sendMessage(Message.raw("Miner speed -> Lv " + (speedLevel + 1) + "!"));
        sendRefresh(ref, store);
    }

    private void handleMinerEvolve(Ref<EntityStore> ref, Store<EntityStore> store, int slotIndex) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(slotIndex);
        int stars = minerState.stars();

        long cost = MinerRobotState.getMinerEvolveCost(stars);
        MinePlayerProgress.MinerEvolutionResult result =
            mineProgress.evolveMiner(slotIndex, cost, MinerRobotState.MAX_SPEED_PER_STAR, MinerRobotState.MAX_STARS);
        if (result == MinePlayerProgress.MinerEvolutionResult.NO_MINER) return;
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

        markDirty();
        syncMinerEvolution(store, slotIndex);
        checkMineAchievement(MineAchievement.EVOLVE_STAR1);

        player.sendMessage(Message.raw("Miner evolved to Star " + (stars + 1) + "!"));
        sendRefresh(ref, store);
    }

    // ==================== Upgrade Handlers ====================

    private void handleBuyUpgrade(Ref<EntityStore> ref, Store<EntityStore> store, String typeName) {
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

        markDirty();

        // Check if all 3 global upgrades are now maxed
        boolean allMaxed = true;
        for (MineUpgradeType t : MineUpgradeType.values()) {
            if (mineProgress.getUpgradeLevel(t) < t.getMaxLevel()) {
                allMaxed = false;
                break;
            }
        }
        if (allMaxed) {
            checkMineAchievement(MineAchievement.MAX_UPGRADES);
        }

        // Apply Haste speed boost immediately if player is in mine
        if (type == MineUpgradeType.HASTE && mineProgress.isInMine()) {
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin != null && plugin.getMineGateChecker() != null) {
                plugin.getMineGateChecker().applyHasteSpeed(mineProgress, ref, store, playerRef);
            }
        }

        player.sendMessage(Message.raw("Upgraded " + type.getDisplayName() + " to Lv " + mineProgress.getUpgradeLevel(type) + "!"));
        sendRefresh(ref, store);
    }

    private void handleResetAll(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if (!PermissionUtils.isOp(playerRef.getUuid())) return;

        // Reset all upgrades
        for (MineUpgradeType type : MineUpgradeType.values()) {
            mineProgress.setUpgradeLevel(type, 0);
        }
        mineProgress.setPickaxeTier(0);
        swapPickaxeItem(player);

        // Despawn all miners and reset ownership (all slots)
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        MineRobotManager robotManager = plugin != null ? plugin.getMineRobotManager() : null;
        UUID uuid = playerRef.getUuid();

        for (var entry : mineProgress.getMinerStates().entrySet()) {
            if (!entry.getValue().hasMiner()) continue;

            int slotIndex = entry.getKey();

            if (robotManager != null) {
                robotManager.despawnMiner(uuid, slotIndex);
            }

            mineProgress.loadMinerState(slotIndex, false, 0, 0);
        }

        markDirty();
        player.sendMessage(Message.raw("All upgrades and miners have been reset."));
        sendRefresh(ref, store);
    }

    // ==================== Helpers ====================

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        cmd.clear("#MinerEntries");
        cmd.clear("#PickaxeRow");
        for (int slot = 0; slot < GRID_UPGRADE_ORDER.length; slot++) {
            cmd.clear("#Slot" + slot);
        }
        cmd.clear("#SlotBag");
        populateMinerTab(cmd, evt);
        populateUpgradeTab(cmd, evt);
        this.sendUpdate(cmd, evt, false);
    }

    private void markDirty() {
        MinePlayerStore mineStore = ParkourAscendPlugin.getInstance().getMinePlayerStore();
        if (mineStore != null) {
            mineStore.markDirty(playerRef.getUuid());
        }
    }

    private String buildUpgradeTooltip(MineUpgradeType type, int level, int maxLevel) {
        boolean maxed = level >= maxLevel;
        String name = type.getDisplayName();
        String desc = getUpgradeDescription(type);

        StringBuilder sb = new StringBuilder();
        sb.append(name).append("\n");
        sb.append(desc).append("\n\n");

        if (maxed) {
            sb.append("-- Fully Maxed --\n");
            sb.append(getEffectDescription(type, level));
        } else {
            sb.append("Purchase Upgrade:\n");
            sb.append("- Level ").append(level).append("/").append(maxLevel).append("\n");
            sb.append("- Price: ").append(type.getCost(level)).append(" crystals\n");
            sb.append("- Next: ").append(getEffectDescription(type, level + 1)).append("\n\n");
            sb.append("Left Click to purchase one level");
        }
        return sb.toString();
    }

    private static String getUpgradeDescription(MineUpgradeType type) {
        return type.getDescription();
    }

    private String getEffectDescription(MineUpgradeType type, int level) {
        double effect = type.getEffect(level);
        return switch (type) {
            case BAG_CAPACITY -> "Capacity: " + (int) effect + " blocks";
            case MOMENTUM -> level == 0 ? "No combo" : "Max combo: " + (int) effect + " (+2% dmg/hit)";
            case FORTUNE -> level == 0 ? "No bonus drops" : "x2: " + (int) effect + "%, x3: " + String.format("%.1f", level * 0.4) + "%";
            case JACKHAMMER -> level == 0 ? "No column break" : String.format("%.0f%%", type.getChance(level) * 100) + " | Depth: " + (int) effect;
            case STOMP -> level == 0 ? "No layer break" : String.format("%.0f%%", type.getChance(level) * 100) + " | Radius: " + (int) effect;
            case BLAST -> level == 0 ? "No sphere break" : String.format("%.0f%%", type.getChance(level) * 100) + " | Radius: " + (int) effect;
            case HASTE -> level == 0 ? "No speed bonus" : "+" + (int) effect + "% speed";
        };
    }

    private String getMineName() {
        MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
        if (configStore != null) {
            Mine mine = configStore.getMine();
            if (mine != null) return mine.getName();
        }
        return "Mine";
    }

    private void checkMineAchievement(MineAchievement achievement) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineAchievementTracker tracker = plugin.getMineAchievementTracker();
        if (tracker != null) {
            tracker.checkAchievement(playerRef.getUuid(), achievement);
        }
    }

    private void syncBoughtMiner(Store<EntityStore> store, int slotIndex) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineRobotManager robotManager = plugin.getMineRobotManager();
        if (robotManager == null || store.getExternalData() == null) return;
        robotManager.syncPurchasedMiner(playerRef.getUuid(), slotIndex, store.getExternalData().getWorld());
    }

    private void syncMinerSpeed(int slotIndex) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineRobotManager robotManager = plugin.getMineRobotManager();
        if (robotManager == null) return;
        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(slotIndex);
        robotManager.syncMinerSpeed(playerRef.getUuid(), slotIndex, minerState.speedLevel());
    }

    private void syncMinerEvolution(Store<EntityStore> store, int slotIndex) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineRobotManager robotManager = plugin.getMineRobotManager();
        if (robotManager == null || store.getExternalData() == null) return;
        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(slotIndex);
        robotManager.syncMinerEvolution(
            playerRef.getUuid(), slotIndex,
            minerState.speedLevel(), minerState.stars(),
            store.getExternalData().getWorld()
        );
    }
}
