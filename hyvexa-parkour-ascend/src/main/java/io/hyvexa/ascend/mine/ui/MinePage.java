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

    private static final int MINER_MAX_SPEED_PER_STAR = 25;
    private static final int MINER_MAX_STARS = 5;

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

    private static final String[] UPGRADE_DISPLAY_NAMES = {
        "Bag Capacity", "Momentum", "Fortune", "Jackhammer", "Stomp", "Blast", "Haste"
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

        List<Mine> mines = configStore.listMinesSorted();
        for (int i = 0; i < mines.size(); i++) {
            Mine mine = mines.get(i);
            cmd.append("#MinerEntries", "Pages/Ascend_MinePageMinerEntry.ui");
            String sel = "#MinerEntries[" + i + "]";

            // Accent color + button zone color
            int colorIndex = i % ACCENT_COLORS.length;
            String colorName = ACCENT_COLORS[colorIndex];
            String colorHex = ACCENT_HEX[colorIndex];
            cmd.set(sel + " #AccentViolet.Visible", colorIndex == 0);
            cmd.set(sel + " #Accent" + colorName + ".Visible", true);
            cmd.set(sel + " #ButtonBg" + colorName + ".Visible", true);

            // Set segment colors to match accent
            for (int seg = 1; seg <= MINER_MAX_SPEED_PER_STAR; seg++) {
                cmd.set(sel + " #Seg" + seg + ".Background", colorHex);
            }

            cmd.set(sel + " #MineName.Text", mine.getName());

            boolean unlocked = mineProgress.getMineSnapshot(mine.getId()).unlocked();
            if (!unlocked) {
                cmd.set(sel + " #LockedOverlay.Visible", true);
                continue;
            }

            MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(mine.getId());
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
                EventData.of(ButtonEventData.KEY_BUTTON, resolveMinerButtonId(hasMiner, speedLevel, stars, mine.getId())), false);

            if (!hasMiner) {
                cmd.set(sel + " #MinerLevel.Text", "");
                cmd.set(sel + " #MinerActionStatus.Text", "No Miner");
                cmd.set(sel + " #ActionText.Text", "Buy Miner");
                cmd.set(sel + " #ActionPrice.Text", "");
                cmd.set(sel + " #MinerStatus.Text", "");
            } else {
                boolean fullyMaxed = stars >= MINER_MAX_STARS && speedLevel >= MINER_MAX_SPEED_PER_STAR;
                double blocksPerMin = MinerRobotState.getProductionRate(speedLevel, stars);
                cmd.set(sel + " #MinerStatus.Text", String.format("%.1f", blocksPerMin) + " blocks/min");

                if (fullyMaxed) {
                    cmd.set(sel + " #MinerLevel.Text", "MAX");
                    cmd.set(sel + " #MinerActionStatus.Text", String.format("%.1f", blocksPerMin) + " b/m");
                    cmd.set(sel + " #ActionText.Text", "Maxed!");
                    cmd.set(sel + " #ActionPrice.Text", "");
                } else if (speedLevel >= MINER_MAX_SPEED_PER_STAR) {
                    cmd.set(sel + " #MinerLevel.Text", "Lv." + speedLevel);
                    cmd.set(sel + " #MinerActionStatus.Text", String.format("%.1f", blocksPerMin) + " b/m");
                    cmd.set(sel + " #ActionText.Text", "Evolve");
                    cmd.set(sel + " #ActionPrice.Text", "");
                } else {
                    cmd.set(sel + " #MinerLevel.Text", "Lv." + speedLevel);
                    cmd.set(sel + " #MinerActionStatus.Text", String.format("%.1f", blocksPerMin) + " b/m");
                    long cost = getMinerSpeedCost(speedLevel, stars);
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
        }
    }

    private String resolveMinerButtonId(boolean hasMiner, int speedLevel, int stars, String mineId) {
        if (!hasMiner) return BUTTON_BUY_MINER_PREFIX + mineId;
        if (stars >= MINER_MAX_STARS && speedLevel >= MINER_MAX_SPEED_PER_STAR) return "Noop";
        if (speedLevel >= MINER_MAX_SPEED_PER_STAR) return BUTTON_MINER_EVOLVE_PREFIX + mineId;
        return BUTTON_MINER_SPEED_PREFIX + mineId;
    }

    private void populateUpgradeTab(UICommandBuilder cmd, UIEventBuilder evt) {
        // --- Pickaxe Tier Card ---
        populatePickaxeCard(cmd, evt);

        // --- Existing upgrade entries ---
        MineUpgradeType[] types = MineUpgradeType.values();
        for (int i = 0; i < types.length; i++) {
            MineUpgradeType type = types[i];
            int level = mineProgress.getUpgradeLevel(type);
            int maxLevel = type.getMaxLevel();
            boolean maxed = level >= maxLevel;

            cmd.append("#UpgradeItems", "Pages/Ascend_MinePageUpgradeEntry.ui");
            String sel = "#UpgradeItems[" + (i + 1) + "]";  // +1 because pickaxe card is at index 0

            // Accent color + button zone color
            String colorName = UPGRADE_ACCENT_COLORS[i];
            String colorHex = UPGRADE_ACCENT_HEX[i];
            cmd.set(sel + " #AccentViolet.Visible", false);
            cmd.set(sel + " #Accent" + colorName + ".Visible", true);
            cmd.set(sel + " #ButtonBg" + colorName + ".Visible", true);

            // Segment colors + progress
            int filledSegments = maxLevel > 0 ? Math.min(UPGRADE_SEGMENT_COUNT, level * UPGRADE_SEGMENT_COUNT / maxLevel) : 0;
            for (int seg = 1; seg <= UPGRADE_SEGMENT_COUNT; seg++) {
                cmd.set(sel + " #Seg" + seg + ".Background", colorHex);
                if (seg <= filledSegments) {
                    cmd.set(sel + " #Seg" + seg + ".Visible", true);
                }
            }

            cmd.set(sel + " #UpgradeName.Text", UPGRADE_DISPLAY_NAMES[i]);
            cmd.set(sel + " #EffectText.Text", getEffectDescription(type, level));

            // Bind buy button
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
    }

    private void populatePickaxeCard(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.append("#UpgradeItems", "Pages/Ascend_MinePagePickaxeCard.ui");
        String sel = "#UpgradeItems[0]";

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
        MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
        if (configStore == null) return false;
        List<Mine> mines = configStore.listMinesSorted();
        int totalMineCount = mines.size();

        List<String> unlockedMineIds = new java.util.ArrayList<>();
        for (Mine mine : mines) {
            if (mineProgress.getMineSnapshot(mine.getId()).unlocked()) {
                unlockedMineIds.add(mine.getId());
            }
        }

        return tier.meetsRequirement(unlockedMineIds, totalMineCount);
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
            handleBuyMiner(ref, store, button.substring(BUTTON_BUY_MINER_PREFIX.length()));
        } else if (button.startsWith(BUTTON_MINER_SPEED_PREFIX)) {
            handleMinerSpeedUpgrade(ref, store, button.substring(BUTTON_MINER_SPEED_PREFIX.length()));
        } else if (button.startsWith(BUTTON_MINER_EVOLVE_PREFIX)) {
            handleMinerEvolve(ref, store, button.substring(BUTTON_MINER_EVOLVE_PREFIX.length()));
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

        markDirty();
        syncBoughtMiner(store, mineId);
        checkMineAchievement(MineAchievement.FIRST_MINER);

        player.sendMessage(Message.raw("Bought miner for " + getMineName(mineId) + "!"));
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
        syncMinerSpeed(mineId);

        player.sendMessage(Message.raw(getMineName(mineId) + " Miner speed -> Lv " + (speedLevel + 1) + "!"));
        sendRefresh(ref, store);
    }

    private void handleMinerEvolve(Ref<EntityStore> ref, Store<EntityStore> store, String mineId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(mineId);
        int stars = minerState.stars();

        long cost = getMinerEvolveCost(stars);
        MinePlayerProgress.MinerEvolutionResult result =
            mineProgress.evolveMiner(mineId, cost, MINER_MAX_SPEED_PER_STAR, MINER_MAX_STARS);
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
        syncMinerEvolution(store, mineId);
        checkMineAchievement(MineAchievement.EVOLVE_STAR1);

        player.sendMessage(Message.raw(getMineName(mineId) + " Miner evolved to Star " + (stars + 1) + "!"));
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

        player.sendMessage(Message.raw("Upgraded " + UPGRADE_DISPLAY_NAMES[type.ordinal()] + " to Lv " + mineProgress.getUpgradeLevel(type) + "!"));
        sendRefresh(ref, store);
    }

    private void handleResetAll(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if (!PermissionUtils.isOp(playerRef.getUuid())) return;

        for (MineUpgradeType type : MineUpgradeType.values()) {
            mineProgress.setUpgradeLevel(type, 0);
        }
        mineProgress.setPickaxeTier(0);
        swapPickaxeItem(player);

        for (var entry : mineProgress.getMinerStates().entrySet()) {
            String mineId = entry.getKey();
            if (entry.getValue().hasMiner()) {
                mineProgress.loadMinerState(mineId, true, 0, 0);
            }
        }

        markDirty();
        player.sendMessage(Message.raw("All upgrades and miner levels have been reset."));
        sendRefresh(ref, store);
    }

    // ==================== Helpers ====================

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        cmd.clear("#MinerEntries");
        cmd.clear("#UpgradeItems");
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

    private String getEffectDescription(MineUpgradeType type, int level) {
        double effect = type.getEffect(level);
        return switch (type) {
            case BAG_CAPACITY -> "Capacity: " + (int) effect + " blocks";
            case MOMENTUM -> level == 0 ? "No combo" : "Max combo: " + (int) effect;
            case FORTUNE -> level == 0 ? "No bonus drops" : "x2: " + (int) effect + "%, x3: " + String.format("%.1f", level * 0.4) + "%";
            case JACKHAMMER -> level == 0 ? "No column break" : "Depth: " + (int) effect + " blocks";
            case STOMP -> level == 0 ? "No layer break" : "Radius: " + (int) effect;
            case BLAST -> level == 0 ? "No sphere break" : "Radius: " + (int) effect;
            case HASTE -> level == 0 ? "No speed bonus" : "+" + (int) effect + "% speed";
        };
    }

    private String getMineName(String mineId) {
        MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
        if (configStore != null) {
            Mine mine = configStore.getMine(mineId);
            if (mine != null) return mine.getName();
        }
        return mineId;
    }

    private void checkMineAchievement(MineAchievement achievement) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineAchievementTracker tracker = plugin.getMineAchievementTracker();
        if (tracker != null) {
            tracker.checkAchievement(playerRef.getUuid(), achievement);
        }
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

    private void syncBoughtMiner(Store<EntityStore> store, String mineId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineRobotManager robotManager = plugin.getMineRobotManager();
        if (robotManager == null || store.getExternalData() == null) return;
        robotManager.syncPurchasedMiner(playerRef.getUuid(), mineId, store.getExternalData().getWorld());
    }

    private void syncMinerSpeed(String mineId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineRobotManager robotManager = plugin.getMineRobotManager();
        if (robotManager == null) return;
        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(mineId);
        robotManager.syncMinerSpeed(playerRef.getUuid(), mineId, minerState.speedLevel());
    }

    private void syncMinerEvolution(Store<EntityStore> store, String mineId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineRobotManager robotManager = plugin.getMineRobotManager();
        if (robotManager == null || store.getExternalData() == null) return;
        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(mineId);
        robotManager.syncMinerEvolution(
            playerRef.getUuid(), mineId,
            minerState.speedLevel(), minerState.stars(),
            store.getExternalData().getWorld()
        );
    }
}
