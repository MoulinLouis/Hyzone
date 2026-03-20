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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.mine.achievement.MineAchievement;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.data.CollectedMiner;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineUpgradeType;
import io.hyvexa.ascend.mine.data.MineZoneLayer;
import io.hyvexa.ascend.mine.MineBlockDisplay;
import io.hyvexa.ascend.mine.robot.MineRobotManager;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.PermissionUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.hyvexa.ascend.mine.data.PickaxeTier;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Tabbed mine page opened by pickaxe right-click.
 * Tabs: Upgrade, Slots, Eggs, Collection.
 */
public class MinePage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TAB_UPGRADE = "TabUpgrade";
    private static final String BUTTON_TAB_SLOTS = "TabSlots";
    private static final String BUTTON_TAB_COLLECTION = "TabCollection";
    private static final String BUTTON_BUY_UPGRADE_PREFIX = "BuyUpgrade_";
    private static final String BUTTON_RESET = "ResetAll";
    private static final String BUTTON_BUY_PICKAXE = "BuyPickaxe";
    private static final String BUTTON_ENHANCE_PICKAXE = "EnhancePickaxe";

    // Gacha buttons
    private static final String BUTTON_ASSIGN_SLOT_PREFIX = "AssignSlot_";
    private static final String BUTTON_REMOVE_SLOT_PREFIX = "RemoveSlot_";
    private static final String BUTTON_SPEED_SLOT_PREFIX = "SpeedSlot_";
    private static final String BUTTON_PICK_MINER_PREFIX = "PickMiner_";

    private static final MineUpgradeType[] GRID_UPGRADE_ORDER = {
        MineUpgradeType.MOMENTUM,
        MineUpgradeType.FORTUNE,
        MineUpgradeType.JACKHAMMER,
        MineUpgradeType.STOMP,
        MineUpgradeType.BLAST,
        MineUpgradeType.HASTE,
        MineUpgradeType.BAG_CAPACITY,
        MineUpgradeType.CONVEYOR_CAPACITY,
        MineUpgradeType.CASHBACK
    };

    private final MinePlayerProgress mineProgress;
    private final PlayerRef playerRef;
    private String activeTab = "Upgrade";
    private int pickerSlotIndex = -1;

    public MinePage(@Nonnull PlayerRef playerRef, MinePlayerProgress mineProgress) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerRef = playerRef;
        this.mineProgress = mineProgress;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Ascend_MinePage.ui");

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabUpgrade",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_UPGRADE), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabSlots",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_SLOTS), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabCollection",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_COLLECTION), false);

        if (PermissionUtils.isOp(playerRef.getUuid())) {
            cmd.set("#ResetWrap.Visible", true);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_RESET), false);
        }

        populateUpgradeTab(cmd, evt);
        populateSlotsTab(cmd, evt);
        populateCollectionTab(cmd, evt);
    }

    // ==================== Slots Tab ====================

    private void populateSlotsTab(UICommandBuilder cmd, UIEventBuilder evt) {
        MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
        if (configStore == null) return;

        List<io.hyvexa.ascend.mine.data.MinerSlot> slots = configStore.getMinerSlots();
        int i = 0;
        for (io.hyvexa.ascend.mine.data.MinerSlot slot : slots) {
            addSlotEntry(cmd, evt, i, slot.getSlotIndex());
            i++;
        }
        if (slots.isEmpty()) {
            addSlotEntry(cmd, evt, 0, 0);
        }
    }

    private void addSlotEntry(UICommandBuilder cmd, UIEventBuilder evt, int i, int slotIndex) {
        cmd.append("#SlotEntries", "Pages/Ascend_MineSlotEntry.ui");
        String sel = "#SlotEntries[" + i + "]";

        cmd.set(sel + " #SlotTitle.Text", "Slot #" + (slotIndex + 1));

        CollectedMiner assigned = mineProgress.getAssignedMiner(slotIndex);
        if (assigned != null) {
            cmd.set(sel + " #AccentEmpty.Visible", false);
            cmd.set(sel + " #AccentFilled.Visible", true);
            cmd.set(sel + " #AccentFilled.Background", assigned.getRarity().getColor());

            MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
            MineZoneLayer layer = configStore != null ? configStore.getLayerById(assigned.getLayerId()) : null;
            String layerName = layer != null && !layer.getDisplayName().isEmpty() ? layer.getDisplayName() : assigned.getLayerId();

            cmd.set(sel + " #MinerInfo.Text", assigned.getRarity().getDisplayName() + " - " + layerName);
            cmd.set(sel + " #MinerInfo.Style.TextColor", assigned.getRarity().getColor());
            cmd.set(sel + " #ProductionInfo.Text",
                "Lv." + assigned.getSpeedLevel() + " | " +
                String.format("%.1f", assigned.getProductionRate()) + " b/m");

            cmd.set(sel + " #AssignText.Text", "Remove");
            cmd.set(sel + " #AssignBtnBg.Background", "#ef4444(0.85)");
            evt.addEventBinding(CustomUIEventBindingType.Activating,
                sel + " #AssignButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_REMOVE_SLOT_PREFIX + slotIndex), false);

            cmd.set(sel + " #SpeedBtnWrap.Visible", true);
            long speedCost = CollectedMiner.getSpeedUpgradeCost(assigned.getSpeedLevel());
            cmd.set(sel + " #SpeedText.Text", "Speed " + speedCost + "c");
            boolean canAfford = mineProgress.getCrystals() >= speedCost;
            cmd.set(sel + " #SpeedDisabledOverlay.Visible", !canAfford);
            evt.addEventBinding(CustomUIEventBindingType.Activating,
                sel + " #SpeedButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SPEED_SLOT_PREFIX + slotIndex), false);
        } else {
            evt.addEventBinding(CustomUIEventBindingType.Activating,
                sel + " #AssignButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ASSIGN_SLOT_PREFIX + slotIndex), false);
        }
    }

    // ==================== Collection Tab ====================

    private void populateCollectionTab(UICommandBuilder cmd, UIEventBuilder evt) {
        List<CollectedMiner> miners = mineProgress.getMinerCollection();
        MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();

        if (miners.isEmpty()) {
            cmd.set("#NoMinersLabel.Visible", true);
            return;
        }
        cmd.set("#NoMinersLabel.Visible", false);

        int i = 0;
        for (CollectedMiner miner : miners) {
            cmd.append("#CollectionEntries", "Pages/Ascend_MineCollectionEntry.ui");
            String sel = "#CollectionEntries[" + i + "]";

            cmd.set(sel + " #RarityBar.Background", miner.getRarity().getColor());
            cmd.set(sel + " #RarityLabel.Text", miner.getRarity().getDisplayName());
            cmd.set(sel + " #RarityLabel.Style.TextColor", miner.getRarity().getColor());

            MineZoneLayer layer = configStore != null ? configStore.getLayerById(miner.getLayerId()) : null;
            String layerName = layer != null && !layer.getDisplayName().isEmpty() ? layer.getDisplayName() : "";
            cmd.set(sel + " #LayerLabel.Text", layerName);

            cmd.set(sel + " #SpeedLabel.Text",
                "Speed Lv." + miner.getSpeedLevel() + " | " +
                String.format("%.1f", miner.getProductionRate()) + " b/m");

            if (mineProgress.isMinerAssigned(miner.getId())) {
                cmd.set(sel + " #AssignedText.Visible", true);
                cmd.set(sel + " #AssignedText.Text", "Assigned");
            }
            i++;
        }
    }

    // ==================== Picker ====================

    private void showPicker(int slotIndex) {
        pickerSlotIndex = slotIndex;

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();

        cmd.set("#SlotEntries.Visible", false);
        cmd.set("#PickerOverlay.Visible", true);
        cmd.set("#PickerTitle.Text", "Select a miner for Slot #" + (slotIndex + 1) + ":");
        cmd.clear("#PickerEntries");

        MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
        List<CollectedMiner> miners = mineProgress.getMinerCollection();

        int i = 0;
        for (CollectedMiner miner : miners) {
            if (mineProgress.isMinerAssigned(miner.getId())) continue;

            cmd.append("#PickerEntries", "Pages/Ascend_MineMinerPicker.ui");
            String sel = "#PickerEntries[" + i + "]";

            cmd.set(sel + " #RarityBar.Background", miner.getRarity().getColor());
            cmd.set(sel + " #RarityLabel.Text", miner.getRarity().getDisplayName());
            cmd.set(sel + " #RarityLabel.Style.TextColor", miner.getRarity().getColor());

            MineZoneLayer layer = configStore != null ? configStore.getLayerById(miner.getLayerId()) : null;
            String layerName = layer != null && !layer.getDisplayName().isEmpty() ? layer.getDisplayName() : "";
            cmd.set(sel + " #LayerLabel.Text", layerName);
            cmd.set(sel + " #SpeedLabel.Text",
                "Lv." + miner.getSpeedLevel() + " | " +
                String.format("%.1f", miner.getProductionRate()) + " b/m");

            evt.addEventBinding(CustomUIEventBindingType.Activating,
                sel + " #SelectButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PICK_MINER_PREFIX + miner.getId()), false);
            i++;
        }

        this.sendUpdate(cmd, evt, false);
    }

    private void hidePicker() {
        pickerSlotIndex = -1;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#SlotEntries.Visible", true);
        cmd.set("#PickerOverlay.Visible", false);
        this.sendUpdate(cmd, new UIEventBuilder(), false);
    }

    // ==================== Upgrade Tab ====================

    private void populateUpgradeTab(UICommandBuilder cmd, UIEventBuilder evt) {
        populatePickaxeCard(cmd, evt);
        for (int slot = 0; slot < GRID_UPGRADE_ORDER.length; slot++) {
            MineUpgradeType type = GRID_UPGRADE_ORDER[slot];
            cmd.append("#Slot" + slot, "Pages/Ascend_MinePageUpgradeCard.ui");
            String sel = "#Slot" + slot + "[0]";
            populateUpgradeCard(cmd, evt, sel, type);
        }
    }

    private void populateUpgradeCard(UICommandBuilder cmd, UIEventBuilder evt, String sel, MineUpgradeType type) {
        int level = mineProgress.getUpgradeLevel(type);
        int maxLevel = type.getMaxLevel();
        boolean maxed = level >= maxLevel;

        cmd.set(sel + " #CardName.Text", type.getDisplayName());
        cmd.set(sel + " #CardEffect.Text", getEffectDescription(type, level));
        cmd.set(sel + " #CardBuyBtn.TooltipText", buildUpgradeTooltip(type, level, maxLevel));

        if (maxed) {
            cmd.set(sel + " #CardLevel.Text", "MAX");
            cmd.set(sel + " #CardCost.Text", "");
            evt.addEventBinding(CustomUIEventBindingType.Activating, sel + " #CardBuyBtn",
                EventData.of(ButtonEventData.KEY_BUTTON, "Noop"), false);
        } else {
            cmd.set(sel + " #CardLevel.Text", "Lv." + level);
            long cost = type.getCost(level);
            cmd.set(sel + " #CardCost.Text", cost + " cryst");
            boolean canAfford = mineProgress.getCrystals() >= cost;
            cmd.set(sel + " #CardDisabled.Visible", !canAfford);
            evt.addEventBinding(CustomUIEventBindingType.Activating, sel + " #CardBuyBtn",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BUY_UPGRADE_PREFIX + type.name()), false);
        }
    }

    private void populatePickaxeCard(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.append("#PickaxeRow", "Pages/Ascend_MinePagePickaxeCard.ui");
        String sel = "#PickaxeRow[0]";

        PickaxeTier current = mineProgress.getPickaxeTierEnum();
        PickaxeTier next = current.next();
        int enhancement = mineProgress.getPickaxeEnhancement();
        int damage = mineProgress.getPickaxeDamage();

        cmd.set(sel + " #TierName.Text", current.getDisplayName(enhancement));
        cmd.set(sel + " #SpeedText.Text", "Damage: " + damage);
        cmd.set(sel + " #TierLabel.Text", "Tier " + current.getTier());

        MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();

        if (next == null && enhancement >= PickaxeTier.MAX_ENHANCEMENT) {
            cmd.set(sel + " #RequirementText.Text", "");
            cmd.set(sel + " #ActionText.Text", "Maxed!");
            cmd.set(sel + " #ActionPrice.Text", "");
            evt.addEventBinding(CustomUIEventBindingType.Activating, sel + " #PickaxeBuyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, "Noop"), false);
        } else if (enhancement < PickaxeTier.MAX_ENHANCEMENT) {
            int nextLevel = enhancement + 1;
            long cost = configStore != null ? configStore.getEnhanceCost(current.getTier(), nextLevel) : 0;
            cmd.set(sel + " #RequirementText.Text", "Next: +" + nextLevel);
            cmd.set(sel + " #ActionText.Text", "Enhance");
            cmd.set(sel + " #ActionPrice.Text", cost > 0 ? cost + " cryst" : "Free");
            boolean canAfford = cost <= 0 || mineProgress.getCrystals() >= cost;
            if (!canAfford) cmd.set(sel + " #ButtonDisabledOverlay.Visible", true);
            evt.addEventBinding(CustomUIEventBindingType.Activating, sel + " #PickaxeBuyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, canAfford ? BUTTON_ENHANCE_PICKAXE : "Noop"), false);
        } else {
            Map<String, Integer> recipe = configStore != null
                ? configStore.getTierRecipe(next.getTier()) : java.util.Collections.emptyMap();
            boolean configured = !recipe.isEmpty();
            boolean hasBlocks = configured && mineProgress.hasInventoryBlocks(recipe);

            if (!configured) {
                cmd.set(sel + " #RequirementText.Text", "Not configured");
                cmd.set(sel + " #RequirementText.Style.TextColor", "#ef4444");
            } else {
                StringBuilder reqText = new StringBuilder();
                for (var entry : recipe.entrySet()) {
                    if (reqText.length() > 0) reqText.append(", ");
                    reqText.append(entry.getValue()).append("x ").append(formatBlockName(entry.getKey()));
                }
                cmd.set(sel + " #RequirementText.Text", reqText.toString());
                if (!hasBlocks) cmd.set(sel + " #RequirementText.Style.TextColor", "#ef4444");
            }

            cmd.set(sel + " #ActionText.Text", "Upgrade");
            cmd.set(sel + " #ActionPrice.Text", next.getDisplayName());
            boolean canUpgrade = configured && hasBlocks;
            if (!canUpgrade) cmd.set(sel + " #ButtonDisabledOverlay.Visible", true);
            evt.addEventBinding(CustomUIEventBindingType.Activating, sel + " #PickaxeBuyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, canUpgrade ? BUTTON_BUY_PICKAXE : "Noop"), false);
        }
    }

    private static String formatBlockName(String blockTypeId) {
        if (blockTypeId == null) return "?";
        return MineBlockDisplay.getDisplayName(blockTypeId);
    }

    // ==================== Tab Switching ====================

    private void switchTab(String tabName) {
        if (activeTab.equals(tabName)) return;
        activeTab = tabName;
        pickerSlotIndex = -1;

        UICommandBuilder cmd = new UICommandBuilder();
        setTabActive(cmd, "TabUpgrade", "Upgrade".equals(tabName));
        setTabActive(cmd, "TabSlots", "Slots".equals(tabName));
        setTabActive(cmd, "TabCollection", "Collection".equals(tabName));
        cmd.set("#UpgradeContent.Visible", "Upgrade".equals(tabName));
        cmd.set("#SlotsContent.Visible", "Slots".equals(tabName));
        cmd.set("#CollectionContent.Visible", "Collection".equals(tabName));
        cmd.set("#SlotEntries.Visible", true);
        cmd.set("#PickerOverlay.Visible", false);

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
            case BUTTON_TAB_UPGRADE -> switchTab("Upgrade");
            case BUTTON_TAB_SLOTS -> switchTab("Slots");
            case BUTTON_TAB_COLLECTION -> switchTab("Collection");
            case BUTTON_RESET -> handleResetAll(ref, store);
            default -> handleActionButton(ref, store, button);
        }
    }

    private void handleActionButton(Ref<EntityStore> ref, Store<EntityStore> store, String button) {
        if (button.equals(BUTTON_BUY_PICKAXE)) {
            handleBuyPickaxe(ref, store);
        } else if (button.equals(BUTTON_ENHANCE_PICKAXE)) {
            handleEnhancePickaxe(ref, store);
        } else if (button.startsWith(BUTTON_BUY_UPGRADE_PREFIX)) {
            handleBuyUpgrade(ref, store, button.substring(BUTTON_BUY_UPGRADE_PREFIX.length()));
        } else if (button.startsWith(BUTTON_ASSIGN_SLOT_PREFIX)) {
            showPicker(Integer.parseInt(button.substring(BUTTON_ASSIGN_SLOT_PREFIX.length())));
        } else if (button.startsWith(BUTTON_REMOVE_SLOT_PREFIX)) {
            handleRemoveSlot(ref, store, Integer.parseInt(button.substring(BUTTON_REMOVE_SLOT_PREFIX.length())));
        } else if (button.startsWith(BUTTON_SPEED_SLOT_PREFIX)) {
            handleSlotSpeed(ref, store, Integer.parseInt(button.substring(BUTTON_SPEED_SLOT_PREFIX.length())));
        } else if (button.startsWith(BUTTON_PICK_MINER_PREFIX)) {
            handlePickMiner(ref, store, Long.parseLong(button.substring(BUTTON_PICK_MINER_PREFIX.length())));
        }
    }

    // ==================== Gacha Handlers ====================

    private void handlePickMiner(Ref<EntityStore> ref, Store<EntityStore> store, long minerId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || pickerSlotIndex < 0) return;

        CollectedMiner miner = mineProgress.getMinerById(minerId);
        if (miner == null || mineProgress.isMinerAssigned(minerId)) return;

        int slotIndex = pickerSlotIndex;
        mineProgress.assignMinerToSlot(slotIndex, minerId);
        markDirty();

        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        MineRobotManager robotManager = plugin != null ? plugin.getMineRobotManager() : null;
        if (robotManager != null && store.getExternalData() != null) {
            robotManager.syncAssignedMiner(playerRef.getUuid(), slotIndex, store.getExternalData().getWorld());
        }

        player.sendMessage(Message.raw("Assigned " + miner.getRarity().getDisplayName() + " miner to Slot #" + (slotIndex + 1) + "!"));
        hidePicker();
        sendRefresh(ref, store);
    }

    private void handleRemoveSlot(Ref<EntityStore> ref, Store<EntityStore> store, int slotIndex) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || !mineProgress.isSlotAssigned(slotIndex)) return;

        mineProgress.unassignSlot(slotIndex);
        markDirty();

        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        MineRobotManager robotManager = plugin != null ? plugin.getMineRobotManager() : null;
        if (robotManager != null) robotManager.syncUnassignedMiner(playerRef.getUuid(), slotIndex);

        player.sendMessage(Message.raw("Removed miner from Slot #" + (slotIndex + 1)));
        sendRefresh(ref, store);
    }

    private void handleSlotSpeed(Ref<EntityStore> ref, Store<EntityStore> store, int slotIndex) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        CollectedMiner assigned = mineProgress.getAssignedMiner(slotIndex);
        if (assigned == null) return;

        long cost = CollectedMiner.getSpeedUpgradeCost(assigned.getSpeedLevel());
        MinePlayerProgress.MinerSpeedUpgradeResult result = mineProgress.upgradeMinerSpeed(assigned.getId(), cost);
        if (result == MinePlayerProgress.MinerSpeedUpgradeResult.INSUFFICIENT_CRYSTALS) {
            player.sendMessage(Message.raw("Not enough crystals!"));
            return;
        }
        if (result != MinePlayerProgress.MinerSpeedUpgradeResult.SUCCESS) return;

        markDirty();

        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        MineRobotManager robotManager = plugin != null ? plugin.getMineRobotManager() : null;
        if (robotManager != null) {
            robotManager.syncMinerSpeed(playerRef.getUuid(), slotIndex, assigned.getSpeedLevel());
        }

        player.sendMessage(Message.raw("Miner speed -> Lv " + assigned.getSpeedLevel() + "!"));
        sendRefresh(ref, store);
    }

    // ==================== Pickaxe Handlers ====================

    private void handleBuyPickaxe(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        PickaxeTier next = mineProgress.getPickaxeTierEnum().next();
        if (next == null) { player.sendMessage(Message.raw("Already at max pickaxe tier!")); return; }

        MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
        Map<String, Integer> recipe = configStore != null ? configStore.getTierRecipe(next.getTier()) : java.util.Collections.emptyMap();

        MinePlayerProgress.PickaxeUpgradeResult result = mineProgress.upgradePickaxeTier(recipe);
        switch (result) {
            case ALREADY_MAXED -> { player.sendMessage(Message.raw("Already at max pickaxe tier!")); return; }
            case NOT_AT_MAX_ENHANCEMENT -> { player.sendMessage(Message.raw("Max enhancement first!")); return; }
            case MISSING_BLOCKS -> { player.sendMessage(Message.raw("Missing required blocks!")); return; }
            case NOT_CONFIGURED -> { player.sendMessage(Message.raw("Tier recipe not configured!")); return; }
            case SUCCESS -> {}
        }

        markDirty();
        swapPickaxeItem(player);
        player.sendMessage(Message.raw("Upgraded to " + next.getDisplayName() + "!"));
        sendRefresh(ref, store);
    }

    private void handleEnhancePickaxe(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
        int nextLevel = mineProgress.getPickaxeEnhancement() + 1;
        long cost = configStore != null ? configStore.getEnhanceCost(mineProgress.getPickaxeTier(), nextLevel) : 0;

        MinePlayerProgress.PickaxeEnhanceResult result = mineProgress.purchasePickaxeEnhancement(cost);
        if (result == MinePlayerProgress.PickaxeEnhanceResult.ALREADY_MAXED) {
            player.sendMessage(Message.raw("Enhancement maxed! Upgrade tier."));
            return;
        }
        if (result == MinePlayerProgress.PickaxeEnhanceResult.INSUFFICIENT_CRYSTALS) {
            player.sendMessage(Message.raw("Not enough crystals!"));
            return;
        }

        markDirty();
        player.sendMessage(Message.raw("Pickaxe enhanced to +" + mineProgress.getPickaxeEnhancement() + "!"));
        sendRefresh(ref, store);
    }

    private void swapPickaxeItem(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) return;
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null) return;
        hotbar.setItemStackForSlot((short) 0, new ItemStack(mineProgress.getPickaxeTierEnum().getItemId(), 1), false);
    }

    // ==================== Upgrade Handlers ====================

    private void handleBuyUpgrade(Ref<EntityStore> ref, Store<EntityStore> store, String typeName) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        MineUpgradeType type;
        try { type = MineUpgradeType.valueOf(typeName); } catch (IllegalArgumentException e) { return; }

        boolean success = mineProgress.purchaseUpgrade(type);
        if (!success) {
            int level = mineProgress.getUpgradeLevel(type);
            player.sendMessage(Message.raw(level >= type.getMaxLevel() ? "Already maxed!" : "Not enough crystals!"));
            return;
        }

        markDirty();

        boolean allMaxed = true;
        for (MineUpgradeType t : MineUpgradeType.values()) {
            if (mineProgress.getUpgradeLevel(t) < t.getMaxLevel()) { allMaxed = false; break; }
        }
        if (allMaxed) checkMineAchievement(MineAchievement.MAX_UPGRADES);

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
        if (player == null || !PermissionUtils.isOp(playerRef.getUuid())) return;

        for (MineUpgradeType type : MineUpgradeType.values()) mineProgress.setUpgradeLevel(type, 0);
        mineProgress.setPickaxeTier(0);
        mineProgress.setPickaxeEnhancement(0);
        swapPickaxeItem(player);

        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        MineRobotManager robotManager = plugin != null ? plugin.getMineRobotManager() : null;
        UUID uuid = playerRef.getUuid();

        for (var entry : mineProgress.getSlotAssignments().entrySet()) {
            if (robotManager != null) robotManager.despawnMiner(uuid, entry.getKey());
            mineProgress.unassignSlot(entry.getKey());
        }

        markDirty();
        player.sendMessage(Message.raw("All upgrades and miners have been reset."));
        sendRefresh(ref, store);
    }

    // ==================== Helpers ====================

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        cmd.clear("#PickaxeRow");
        for (int slot = 0; slot < GRID_UPGRADE_ORDER.length; slot++) cmd.clear("#Slot" + slot);
        cmd.clear("#SlotEntries");
        cmd.clear("#CollectionEntries");
        cmd.clear("#PickerEntries");
        populateUpgradeTab(cmd, evt);
        populateSlotsTab(cmd, evt);
        populateCollectionTab(cmd, evt);
        this.sendUpdate(cmd, evt, false);
    }

    private void markDirty() {
        MinePlayerStore mineStore = ParkourAscendPlugin.getInstance().getMinePlayerStore();
        if (mineStore != null) mineStore.markDirty(playerRef.getUuid());
    }

    private String buildUpgradeTooltip(MineUpgradeType type, int level, int maxLevel) {
        boolean maxed = level >= maxLevel;
        StringBuilder sb = new StringBuilder();
        sb.append(type.getDisplayName()).append("\n").append(type.getDescription()).append("\n\n");
        if (maxed) {
            sb.append("-- Fully Maxed --\n").append(getEffectDescription(type, level));
        } else {
            sb.append("Purchase Upgrade:\n");
            sb.append("- Level ").append(level).append("/").append(maxLevel).append("\n");
            sb.append("- Price: ").append(type.getCost(level)).append(" crystals\n");
            sb.append("- Next: ").append(getEffectDescription(type, level + 1)).append("\n\n");
            sb.append("Left Click to purchase one level");
        }
        return sb.toString();
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
            case CONVEYOR_CAPACITY -> "Capacity: " + (int) effect + " blocks";
            case CASHBACK -> level == 0 ? "No cashback" : String.format("%.1f", effect) + "% crystal return";
        };
    }

    private void checkMineAchievement(MineAchievement achievement) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineAchievementTracker tracker = plugin.getMineAchievementTracker();
        if (tracker != null) tracker.checkAchievement(playerRef.getUuid(), achievement);
    }
}
