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

import io.hyvexa.ascend.mine.achievement.MineAchievement;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.MineGateChecker;
import io.hyvexa.ascend.mine.data.CollectedMiner;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinerVariant;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineUpgradeType;
import io.hyvexa.ascend.mine.data.MineZoneLayer;
import io.hyvexa.ascend.mine.MineBlockDisplay;
import io.hyvexa.ascend.mine.robot.MineRobotManager;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.common.ui.AccentOverlayUtils;
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
    private static final String BUTTON_BACK_PICKER = "BackPicker";

    private static final int SLOT_GRID_COLUMNS = 3;
    private static final int PICKER_GRID_COLUMNS = 4;
    private static final int COLLECTION_GRID_COLUMNS = 4;

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
    private final MineConfigStore configStore;
    private final MinePlayerStore minePlayerStore;
    private final MineRobotManager mineRobotManager;
    private final MineGateChecker mineGateChecker;
    private final MineAchievementTracker mineAchievementTracker;
    private String activeTab = "Upgrade";
    private int pickerSlotIndex = -1;

    public MinePage(@Nonnull PlayerRef playerRef, MinePlayerProgress mineProgress,
                    MineConfigStore configStore, MinePlayerStore minePlayerStore,
                    MineRobotManager mineRobotManager, MineGateChecker mineGateChecker,
                    MineAchievementTracker mineAchievementTracker) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerRef = playerRef;
        this.mineProgress = mineProgress;
        this.configStore = configStore;
        this.minePlayerStore = minePlayerStore;
        this.mineRobotManager = mineRobotManager;
        this.mineGateChecker = mineGateChecker;
        this.mineAchievementTracker = mineAchievementTracker;
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
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK_PICKER), false);

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
        if (configStore == null) return;

        List<io.hyvexa.ascend.mine.data.MinerSlot> slots = configStore.getMinerSlots();
        int slotCount = Math.max(slots.size(), 1);

        int row = -1;
        for (int i = 0; i < slotCount; i++) {
            if (i % SLOT_GRID_COLUMNS == 0) {
                cmd.append("#SlotGrid", "Pages/Ascend_MineSlotRow.ui");
                row++;
            }
            String rowSel = "#SlotGrid[" + row + "]";
            cmd.append(rowSel, "Pages/Ascend_MineSlotEntry.ui");
            int col = i % SLOT_GRID_COLUMNS;
            String sel = rowSel + "[" + col + "]";
            int slotIndex = i < slots.size() ? slots.get(i).getSlotIndex() : 0;
            addSlotEntry(cmd, evt, sel, slotIndex);
        }
    }

    private void addSlotEntry(UICommandBuilder cmd, UIEventBuilder evt, String sel, int slotIndex) {
        CollectedMiner assigned = mineProgress.getAssignedMiner(slotIndex);

        if (assigned != null) {
            // Border: rarity color
            cmd.set(sel + " #AccentEmpty.Visible", false);
            AccentOverlayUtils.applyAccent(cmd, sel + " #CardBorder",
                    assigned.getRarity().getColor(), AccentOverlayUtils.RARITY_ACCENTS);

            // Rarity banner
            cmd.set(sel + " #RarityLabel.Text", assigned.getRarity().getDisplayName());
            cmd.set(sel + " #RarityLabel.Style.TextColor", assigned.getRarity().getColor());
            applyBanner(cmd, sel + " #RarityBanner", assigned.getRarity());

            // Portrait
            MinerVariant.applyPortrait(cmd, sel + " #PortraitZone", configStore, assigned);

            // Info banner — show name + stats, hide slot label
            cmd.set(sel + " #MinerName.Visible", true);
            cmd.set(sel + " #MinerName.Text", MinerVariant.getDisplayName(configStore, assigned));
            cmd.set(sel + " #MinerStats.Visible", true);
            cmd.set(sel + " #MinerStats.Text",
                "Lv." + assigned.getSpeedLevel() + " \u2014 " +
                String.format("%.1f", assigned.getProductionRate()) + " b/m");
            cmd.set(sel + " #SlotLabel.Visible", false);

            // Button row
            cmd.set(sel + " #ButtonRow.Visible", true);
            long speedCost = CollectedMiner.getSpeedUpgradeCost(assigned.getSpeedLevel());
            cmd.set(sel + " #SpeedText.Text", "Speed " + speedCost + "c");
            boolean canAfford = mineProgress.getCrystals() >= speedCost;
            cmd.set(sel + " #SpeedDisabledOverlay.Visible", !canAfford);
            evt.addEventBinding(CustomUIEventBindingType.Activating,
                sel + " #SpeedButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SPEED_SLOT_PREFIX + slotIndex), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating,
                sel + " #RemoveButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_REMOVE_SLOT_PREFIX + slotIndex), false);

            // Hide click overlay when assigned (buttons handle interaction)
            cmd.set(sel + " #SlotButton.Visible", false);
        } else {
            // Empty slot — default template state is correct, just set label + click handler
            cmd.set(sel + " #SlotLabel.Text", "Slot #" + (slotIndex + 1));
            evt.addEventBinding(CustomUIEventBindingType.Activating,
                sel + " #SlotButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ASSIGN_SLOT_PREFIX + slotIndex), false);
        }
    }

    // ==================== Collection Tab ====================

    private void populateCollectionTab(UICommandBuilder cmd, UIEventBuilder evt) {
        List<CollectedMiner> miners = mineProgress.getMinerCollection();

        if (miners.isEmpty()) {
            cmd.set("#NoMinersLabel.Visible", true);
            return;
        }
        cmd.set("#NoMinersLabel.Visible", false);

        Map<Integer, Long> assignments = mineProgress.getSlotAssignments();
        int row = -1;
        for (int i = 0; i < miners.size(); i++) {
            if (i % COLLECTION_GRID_COLUMNS == 0) {
                cmd.append("#CollectionGrid", "Pages/Ascend_MinePickerRow.ui");
                row++;
            }
            CollectedMiner miner = miners.get(i);
            String rowSel = "#CollectionGrid[" + row + "]";
            cmd.append(rowSel, "Pages/Ascend_MineCollectionEntry.ui");
            int col = i % COLLECTION_GRID_COLUMNS;
            String sel = rowSel + "[" + col + "]";

            // Border + banner
            AccentOverlayUtils.applyAccent(cmd, sel + " #CardBorder",
                    miner.getRarity().getColor(), AccentOverlayUtils.RARITY_ACCENTS);
            applyBanner(cmd, sel + " #RarityBanner", miner.getRarity());

            // Rarity label
            cmd.set(sel + " #RarityLabel.Text", miner.getRarity().getDisplayName());
            cmd.set(sel + " #RarityLabel.Style.TextColor", miner.getRarity().getColor());

            // Portrait
            MinerVariant.applyPortrait(cmd, sel + " #PortraitZone", configStore, miner);

            // Name + stats
            cmd.set(sel + " #NameLabel.Text", MinerVariant.getDisplayName(configStore, miner));
            cmd.set(sel + " #StatsLabel.Text",
                "Lv." + miner.getSpeedLevel() + " \u2014 " +
                String.format("%.1f", miner.getProductionRate()) + " b/m");

            // Assigned badge
            int assignedSlot = getAssignedSlotIndex(assignments, miner.getId());
            if (assignedSlot >= 0) {
                cmd.set(sel + " #AssignedBadge.Visible", true);
                cmd.set(sel + " #AssignedLabel.Text", "Slot #" + (assignedSlot + 1));
            }
        }
    }

    // ==================== Picker ====================

    private void showPicker(int slotIndex) {
        pickerSlotIndex = slotIndex;

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();

        cmd.set("#SlotGrid.Visible", false);
        cmd.set("#PickerOverlay.Visible", true);
        cmd.set("#PickerTitle.Text", "Select a miner for Slot #" + (slotIndex + 1));
        cmd.clear("#PickerGrid");

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK_PICKER), false);

        // Show all miners (including assigned), sorted by rarity desc then level desc
        List<CollectedMiner> miners = new java.util.ArrayList<>(mineProgress.getMinerCollection());
        miners.sort((a, b) -> {
            int cmp = Integer.compare(b.getRarity().ordinal(), a.getRarity().ordinal());
            return cmp != 0 ? cmp : Integer.compare(b.getSpeedLevel(), a.getSpeedLevel());
        });

        Map<Integer, Long> assignments = mineProgress.getSlotAssignments();
        int row = -1;
        for (int i = 0; i < miners.size(); i++) {
            if (i % PICKER_GRID_COLUMNS == 0) {
                cmd.append("#PickerGrid", "Pages/Ascend_MinePickerRow.ui");
                row++;
            }
            CollectedMiner miner = miners.get(i);
            String rowSel = "#PickerGrid[" + row + "]";
            cmd.append(rowSel, "Pages/Ascend_MineMinerPicker.ui");
            int col = i % PICKER_GRID_COLUMNS;
            String sel = rowSel + "[" + col + "]";

            // Border + banner
            AccentOverlayUtils.applyAccent(cmd, sel + " #CardBorder",
                    miner.getRarity().getColor(), AccentOverlayUtils.RARITY_ACCENTS);
            applyBanner(cmd, sel + " #RarityBanner", miner.getRarity());

            // Rarity label
            cmd.set(sel + " #RarityLabel.Text", miner.getRarity().getDisplayName());
            cmd.set(sel + " #RarityLabel.Style.TextColor", miner.getRarity().getColor());

            // Portrait
            MinerVariant.applyPortrait(cmd, sel + " #PortraitZone", configStore, miner);

            // Name + stats
            cmd.set(sel + " #NameLabel.Text", MinerVariant.getDisplayName(configStore, miner));
            cmd.set(sel + " #StatsLabel.Text",
                "Lv." + miner.getSpeedLevel() + " \u2014 " +
                String.format("%.1f", miner.getProductionRate()) + " b/m");

            // Assigned overlay
            int assignedSlot = getAssignedSlotIndex(assignments, miner.getId());
            if (assignedSlot >= 0) {
                cmd.set(sel + " #AssignedOverlay.Visible", true);
                cmd.set(sel + " #AssignedLabel.Text", "In Slot #" + (assignedSlot + 1));
            }

            evt.addEventBinding(CustomUIEventBindingType.Activating,
                sel + " #SelectButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PICK_MINER_PREFIX + miner.getId()), false);
        }

        this.sendUpdate(cmd, evt, false);
    }

    private void hidePicker() {
        pickerSlotIndex = -1;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#SlotGrid.Visible", true);
        cmd.set("#PickerOverlay.Visible", false);
        this.sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private static int getAssignedSlotIndex(Map<Integer, Long> assignments, long minerId) {
        for (var entry : assignments.entrySet()) {
            if (entry.getValue() == minerId) return entry.getKey();
        }
        return -1;
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
        cmd.set("#SlotGrid.Visible", true);
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
        } else if (button.equals(BUTTON_BACK_PICKER)) {
            hidePicker();
        }
    }

    // ==================== Gacha Handlers ====================

    private void handlePickMiner(Ref<EntityStore> ref, Store<EntityStore> store, long minerId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || pickerSlotIndex < 0) return;

        CollectedMiner miner = mineProgress.getMinerById(minerId);
        if (miner == null) return;

        int slotIndex = pickerSlotIndex;

        // If miner is assigned to another slot, unassign it first (reassignment)
        int oldSlot = getAssignedSlotIndex(mineProgress.getSlotAssignments(), minerId);
        if (oldSlot >= 0) {
            mineProgress.unassignSlot(oldSlot);
            if (mineRobotManager != null) {
                mineRobotManager.syncUnassignedMiner(playerRef.getUuid(), oldSlot);
            }
        }

        mineProgress.assignMinerToSlot(slotIndex, minerId);
        markDirty();

        if (mineRobotManager != null && store.getExternalData() != null) {
            mineRobotManager.syncAssignedMiner(playerRef.getUuid(), slotIndex, store.getExternalData().getWorld());
        }

        String minerName = MinerVariant.getDisplayName(configStore, miner);
        player.sendMessage(Message.raw("Assigned " + minerName + " to Slot #" + (slotIndex + 1) + "!"));
        hidePicker();
        sendRefresh(ref, store);
    }

    private void handleRemoveSlot(Ref<EntityStore> ref, Store<EntityStore> store, int slotIndex) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || !mineProgress.isSlotAssigned(slotIndex)) return;

        mineProgress.unassignSlot(slotIndex);
        markDirty();

        if (mineRobotManager != null) {
            mineRobotManager.syncUnassignedMiner(playerRef.getUuid(), slotIndex);
        }

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

        if (mineRobotManager != null) {
            mineRobotManager.syncMinerSpeed(playerRef.getUuid(), slotIndex, assigned.getSpeedLevel());
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
            if (mineGateChecker != null) {
                mineGateChecker.applyHasteSpeed(mineProgress, ref, store, playerRef);
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

        UUID uuid = playerRef.getUuid();

        for (var entry : mineProgress.getSlotAssignments().entrySet()) {
            if (mineRobotManager != null) mineRobotManager.despawnMiner(uuid, entry.getKey());
            mineProgress.unassignSlot(entry.getKey());
        }

        markDirty();
        player.sendMessage(Message.raw("All upgrades and miners have been reset."));
        sendRefresh(ref, store);
    }

    // ==================== Banner Accents ====================

    private static final String[] BANNER_IDS = {"BannerGray", "BannerGreen", "BannerBlue", "BannerPurple", "BannerOrange"};

    private static void applyBanner(UICommandBuilder cmd, String selector, io.hyvexa.ascend.mine.data.MinerRarity rarity) {
        String target = BANNER_IDS[rarity.ordinal()];
        for (String id : BANNER_IDS) {
            cmd.set(selector + " #" + id + ".Visible", id.equals(target));
        }
    }

    // ==================== Helpers ====================

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        cmd.clear("#PickaxeRow");
        for (int slot = 0; slot < GRID_UPGRADE_ORDER.length; slot++) cmd.clear("#Slot" + slot);
        cmd.clear("#SlotGrid");
        cmd.clear("#CollectionGrid");
        cmd.clear("#PickerGrid");
        populateUpgradeTab(cmd, evt);
        populateSlotsTab(cmd, evt);
        populateCollectionTab(cmd, evt);
        this.sendUpdate(cmd, evt, false);
    }

    private void markDirty() {
        if (minePlayerStore != null) {
            minePlayerStore.markDirty(playerRef.getUuid());
        }
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
        if (mineAchievementTracker != null) {
            mineAchievementTracker.checkAchievement(playerRef.getUuid(), achievement);
        }
    }
}
