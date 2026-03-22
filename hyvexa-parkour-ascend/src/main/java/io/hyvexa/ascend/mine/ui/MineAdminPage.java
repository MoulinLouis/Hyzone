package io.hyvexa.ascend.mine.ui;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinerSlot;
import io.hyvexa.ascend.ui.AscendAdminNavigator;
import io.hyvexa.common.math.BigNumber;

public class MineAdminPage extends InteractiveCustomUIPage<MineAdminPage.MineData> {

    private final PlayerRef playerRef;
    private final MineConfigStore mineConfigStore;
    private final MineManager mineManager;
    private final AscendAdminNavigator adminNavigator;
    private String mineId = "";
    private String mineName = "";
    private String mineOrder = "0";
    private String mineCost = "";
    private String selectedMineId = "";
    private int selectedSlotIndex = 0;

    public MineAdminPage(@Nonnull PlayerRef playerRef,
                         MineConfigStore mineConfigStore,
                         MineManager mineManager,
                         AscendAdminNavigator adminNavigator) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, MineData.CODEC);
        this.playerRef = playerRef;
        this.mineConfigStore = mineConfigStore;
        this.mineManager = mineManager;
        this.adminNavigator = adminNavigator;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_MineAdmin.ui");
        bindEvents(eventBuilder);
        populateFields(commandBuilder);
        buildMineList(commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull MineData data) {
        super.handleDataEvent(ref, store, data);
        if (data.mineId != null) {
            mineId = data.mineId.trim();
        }
        if (data.mineName != null) {
            mineName = data.mineName.trim();
        }
        if (data.mineOrder != null) {
            mineOrder = data.mineOrder.trim();
        }
        if (data.mineCost != null) {
            mineCost = data.mineCost.trim();
        }
        if (data.button == null) {
            return;
        }
        if (data.button.equals(MineData.BUTTON_CLOSE)) {
            this.close();
            return;
        }
        if (data.button.startsWith(MineData.BUTTON_SELECT_PREFIX)) {
            selectedMineId = data.button.substring(MineData.BUTTON_SELECT_PREFIX.length());
            Mine mine = mineConfigStore.getMine(selectedMineId);
            if (mine != null) {
                mineId = mine.getId();
                mineName = mine.getName() != null ? mine.getName() : "";
                mineOrder = String.valueOf(mine.getDisplayOrder());
                BigNumber cost = mine.getUnlockCost();
                if (cost != null && !cost.isZero()) {
                    mineCost = String.valueOf(cost.toDouble());
                } else {
                    mineCost = "";
                }
            }
            sendRefresh(ref, store);
            return;
        }
        switch (data.button) {
            case MineData.BUTTON_CREATE -> handleCreate(ref, store);
            case MineData.BUTTON_SET_NAME -> handleSetName(ref, store);
            case MineData.BUTTON_SET_ORDER -> handleSetOrder(ref, store);
            case MineData.BUTTON_SET_SPAWN -> handleSetSpawn(ref, store);
            case MineData.BUTTON_SET_COST -> handleSetCost(ref, store);
            case MineData.BUTTON_DELETE -> handleDelete(ref, store);
            case MineData.BUTTON_ZONES -> handleZones(ref, store);
            case MineData.BUTTON_GATE -> handleGate(ref, store);
            case MineData.BUTTON_BLOCK_CONFIG -> handleBlockConfig(ref, store);
            case MineData.BUTTON_SET_MINER_POS -> handleSetMinerPos(ref, store);
            case MineData.BUTTON_SLOT_NEXT -> handleSlotNext(ref, store);
            case MineData.BUTTON_SLOT_PREV -> handleSlotPrev(ref, store);
            case MineData.BUTTON_ADD_SLOT_WP -> handleAddSlotWp(ref, store);
            case MineData.BUTTON_ADD_MAIN_WP -> handleAddMainWp(ref, store);
            case MineData.BUTTON_CLEAR_SLOT_WP -> handleClearSlotWp(ref, store);
            case MineData.BUTTON_CLEAR_MAIN_WP -> handleClearMainWp(ref, store);
            case MineData.BUTTON_BACK -> handleBack(ref, store);
            default -> {
            }
        }
    }

    private void handleCreate(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        String id = mineId.trim();
        if (id.isEmpty()) {
            player.sendMessage(Message.raw("Mine ID is required."));
            return;
        }
        String name = mineName.trim();
        if (name.isEmpty()) {
            player.sendMessage(Message.raw("Mine name is required."));
            return;
        }
        if (mineConfigStore.getMine(id) != null) {
            player.sendMessage(Message.raw("Mine already exists: " + id));
            return;
        }
        Mine mine = new Mine(id, name);
        int order = parseOrder(player);
        if (order >= 0) {
            mine.setDisplayOrder(order);
        }
        mineConfigStore.saveMine(mine);
        selectedMineId = id;
        player.sendMessage(Message.raw("Mine created: " + id + " (" + name + ")"));
        sendRefresh(ref, store);
    }

    private void handleSetName(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        Mine mine = resolveSelectedMine(player);
        if (mine == null) return;
        String name = mineName.trim();
        if (name.isEmpty()) {
            player.sendMessage(Message.raw("Name cannot be empty."));
            return;
        }
        mine.setName(name);
        mineConfigStore.saveMine(mine);
        player.sendMessage(Message.raw("Name updated: " + mine.getId() + " -> " + name));
        sendRefresh(ref, store);
    }

    private void handleSetOrder(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        Mine mine = resolveSelectedMine(player);
        if (mine == null) return;
        int order = parseOrder(player);
        if (order < 0) return;
        mine.setDisplayOrder(order);
        mineConfigStore.saveMine(mine);
        player.sendMessage(Message.raw("Order updated: " + mine.getId() + " -> " + order));
        sendRefresh(ref, store);
    }

    private void handleSetSpawn(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        Mine mine = resolveSelectedMine(player);
        if (mine == null) return;
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("Unable to read player position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3f bodyRot = transform.getRotation();
        PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
        Vector3f headRot = pRef != null ? pRef.getHeadRotation() : null;
        Vector3f rot = headRot != null ? headRot : bodyRot;
        mine.setSpawnX(pos.getX());
        mine.setSpawnY(pos.getY());
        mine.setSpawnZ(pos.getZ());
        mine.setSpawnRotX(rot.getX());
        mine.setSpawnRotY(rot.getY());
        mine.setSpawnRotZ(rot.getZ());
        World world = store.getExternalData().getWorld();
        mine.setWorld(world != null ? world.getName() : "");
        mineConfigStore.saveMine(mine);
        player.sendMessage(Message.raw("Spawn set for mine: " + mine.getId()));
        player.sendMessage(Message.raw("  Pos: " + String.format("%.2f, %.2f, %.2f", pos.getX(), pos.getY(), pos.getZ())));
        player.sendMessage(Message.raw("  Rot: " + String.format("%.2f, %.2f, %.2f", rot.getX(), rot.getY(), rot.getZ())));
        sendRefresh(ref, store);
    }

    private void handleSetCost(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        Mine mine = resolveSelectedMine(player);
        if (mine == null) return;
        String raw = mineCost.trim();
        if (raw.isEmpty()) {
            player.sendMessage(Message.raw("Enter a cost value (e.g. 1500000)."));
            return;
        }
        try {
            double value = Double.parseDouble(raw);
            BigNumber cost = BigNumber.fromDouble(value);
            mine.setUnlockCost(cost);
            mineConfigStore.saveMine(mine);
            player.sendMessage(Message.raw("Cost updated: " + mine.getId() + " -> " + cost));
            sendRefresh(ref, store);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Invalid number. Enter a cost value (e.g. 1500000)."));
        }
    }

    private void handleDelete(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        Mine mine = resolveSelectedMine(player);
        if (mine == null) return;
        String id = mine.getId();
        boolean deleted = mineConfigStore.deleteMine(id);
        if (deleted) {
            player.sendMessage(Message.raw("Mine deleted: " + id));
            selectedMineId = "";
            mineId = "";
            mineName = "";
            mineOrder = "0";
            mineCost = "";
        } else {
            player.sendMessage(Message.raw("Failed to delete mine: " + id));
        }
        sendRefresh(ref, store);
    }

    private void handleZones(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if (selectedMineId.isEmpty()) {
            player.sendMessage(Message.raw("Select a mine first."));
            return;
        }
        PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (pRef == null) return;
        MineZoneAdminPage page = new MineZoneAdminPage(
            pRef, mineConfigStore, mineManager, adminNavigator, selectedMineId);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void handleGate(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (pRef == null) return;
        player.getPageManager().openCustomPage(ref, store,
            new MineGateAdminPage(pRef, mineConfigStore, adminNavigator));
    }

    private void handleBlockConfig(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (pRef == null) return;
        player.getPageManager().openCustomPage(ref, store,
            new MineBlockHpPage(pRef, mineConfigStore, adminNavigator));
    }

    private void handleSetMinerPos(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        Mine mine = resolveSelectedMine(player);
        if (mine == null) return;
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("Unable to read position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
        Vector3f rot = pRef != null ? pRef.getHeadRotation() : transform.getRotation();
        float yaw = rot.getY();

        // Block position: 1 block in +Z direction from NPC
        int blockX = (int) Math.floor(pos.getX()) + 2;
        int blockY = (int) Math.floor(pos.getY());
        int blockZ = (int) Math.floor(pos.getZ());

        MinerSlot slot = mineConfigStore.getMinerSlot(mine.getId(), selectedSlotIndex);
        if (slot == null) {
            slot = new MinerSlot(mine.getId(), selectedSlotIndex);
        }
        slot.setNpcPosition(pos.getX(), pos.getY(), pos.getZ(), yaw);
        slot.setBlockPosition(blockX, blockY, blockZ);
        mineConfigStore.saveMinerSlot(slot);

        player.sendMessage(Message.raw("Miner pos set: " + mine.getId() + " slot " + selectedSlotIndex));
        player.sendMessage(Message.raw("  NPC: " + String.format("%.1f, %.1f, %.1f yaw=%.1f",
                pos.getX(), pos.getY(), pos.getZ(), yaw)));
        player.sendMessage(Message.raw("  Block: " + blockX + ", " + blockY + ", " + blockZ));
    }

    private void handleSlotNext(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        selectedSlotIndex = (selectedSlotIndex + 1) % 5;
        if (player != null) {
            player.sendMessage(Message.raw("Slot -> " + selectedSlotIndex));
        }
        sendRefresh(ref, store);
    }

    private void handleSlotPrev(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        selectedSlotIndex = (selectedSlotIndex + 4) % 5;
        if (player != null) {
            player.sendMessage(Message.raw("Slot -> " + selectedSlotIndex));
        }
        sendRefresh(ref, store);
    }

    private void handleAddSlotWp(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        Mine mine = resolveSelectedMine(player);
        if (mine == null) return;
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) { player.sendMessage(Message.raw("Unable to read position.")); return; }
        Vector3d pos = transform.getPosition();
        mineConfigStore.addConveyorWaypoint(mine.getId(), selectedSlotIndex, pos.getX(), pos.getY(), pos.getZ());
        int count = mineConfigStore.getSlotWaypoints(mine.getId(), selectedSlotIndex).size();
        player.sendMessage(Message.raw("+Slot WP #" + count + " for slot " + selectedSlotIndex
            + ": " + String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ())));
    }

    private void handleAddMainWp(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        Mine mine = resolveSelectedMine(player);
        if (mine == null) return;
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) { player.sendMessage(Message.raw("Unable to read position.")); return; }
        Vector3d pos = transform.getPosition();
        mineConfigStore.addConveyorWaypoint(mine.getId(), -1, pos.getX(), pos.getY(), pos.getZ());
        int count = mineConfigStore.getMainLineWaypoints(mine.getId()).size();
        player.sendMessage(Message.raw("+Main WP #" + count
            + ": " + String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ())));
    }

    private void handleClearSlotWp(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        Mine mine = resolveSelectedMine(player);
        if (mine == null) return;
        mineConfigStore.clearConveyorWaypoints(mine.getId(), selectedSlotIndex);
        player.sendMessage(Message.raw("Cleared slot " + selectedSlotIndex + " waypoints for " + mine.getId()));
    }

    private void handleClearMainWp(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        Mine mine = resolveSelectedMine(player);
        if (mine == null) return;
        mineConfigStore.clearConveyorWaypoints(mine.getId(), -1);
        player.sendMessage(Message.raw("Cleared main line waypoints for " + mine.getId()));
    }

    private void handleBack(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pRef == null) return;
        player.getPageManager().openCustomPage(ref, store, adminNavigator.createPanelPage(pRef));
    }

    private Mine resolveSelectedMine(Player player) {
        String id = mineId != null && !mineId.isBlank() ? mineId : selectedMineId;
        if (id == null || id.isBlank()) {
            player.sendMessage(Message.raw("Select a mine or enter a mine ID first."));
            return null;
        }
        Mine mine = mineConfigStore.getMine(id);
        if (mine == null) {
            player.sendMessage(Message.raw("Mine not found: " + id));
        }
        return mine;
    }

    private int parseOrder(Player player) {
        String raw = mineOrder != null ? mineOrder.trim() : "";
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Order must be a number."));
            return -1;
        }
    }

    private void bindEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MineIdField",
            EventData.of(MineData.KEY_MINE_ID, "#MineIdField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MineNameField",
            EventData.of(MineData.KEY_MINE_NAME, "#MineNameField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MineOrderField",
            EventData.of(MineData.KEY_MINE_ORDER, "#MineOrderField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MineCostField",
            EventData.of(MineData.KEY_MINE_COST, "#MineCostField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CreateButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_CREATE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetNameButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_SET_NAME), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetOrderButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_SET_ORDER), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetSpawnButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_SET_SPAWN), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetCostButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_SET_COST), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_DELETE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ZonesButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_ZONES), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GateButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_GATE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BlockConfigButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_BLOCK_CONFIG), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetMinerPosButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_SET_MINER_POS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SlotNextButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_SLOT_NEXT), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SlotPrevButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_SLOT_PREV), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddSlotWpButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_ADD_SLOT_WP), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddMainWpButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_ADD_MAIN_WP), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClearSlotWpButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_CLEAR_SLOT_WP), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClearMainWpButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_CLEAR_MAIN_WP), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_BACK), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_CLOSE), false);
    }

    private void populateFields(UICommandBuilder commandBuilder) {
        commandBuilder.set("#MineIdField.Value", mineId);
        commandBuilder.set("#MineNameField.Value", mineName);
        commandBuilder.set("#MineOrderField.Value", mineOrder);
        commandBuilder.set("#MineCostField.Value", mineCost);

        boolean hasMines = !mineConfigStore.listMinesSorted().isEmpty();
        boolean hasSelection = selectedMineId != null && !selectedMineId.isBlank();

        // Show form section when a mine is selected or there are no mines (create mode)
        commandBuilder.set("#FormSection.Visible", hasSelection || !hasMines);
        // Show empty state only when no mines and no selection
        commandBuilder.set("#EmptyLabel.Visible", !hasMines);

        String selectedText = "No mine selected";
        String mineInfo = "";
        if (hasSelection) {
            Mine mine = mineConfigStore.getMine(selectedMineId);
            if (mine != null) {
                selectedText = mine.getId() + " (" + mine.getName() + ")";
                boolean hasSpawn = mine.getSpawnX() != 0 || mine.getSpawnY() != 0 || mine.getSpawnZ() != 0;
                int zoneCount = mine.getZones() != null ? mine.getZones().size() : 0;
                BigNumber cost = mine.getUnlockCost();
                String costStr = cost != null && !cost.equals(BigNumber.ZERO) ? cost.toString() : "Free";
                mineInfo = "Zones: " + zoneCount + " | Spawn: " + (hasSpawn ? "OK" : "NO")
                    + " | Order: " + mine.getDisplayOrder() + " | Cost: " + costStr;
            } else {
                selectedText = selectedMineId + " (not found)";
            }
        }
        commandBuilder.set("#SelectedMineText.Text", selectedText);
        commandBuilder.set("#MineInfoText.Text", mineInfo);
        commandBuilder.set("#SlotLabel.Text", "Slot: " + selectedSlotIndex);
    }

    private void buildMineList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#MineCards");
        List<Mine> mines = new ArrayList<>(mineConfigStore.listMinesSorted());
        int index = 0;
        for (Mine mine : mines) {
            commandBuilder.append("#MineCards", "Pages/Ascend_MineAdminEntry.ui");
            String entrySelector = "#MineCards[" + index + "]";
            String nameLabel = mine.getName() != null && !mine.getName().isBlank() ? mine.getName() : mine.getId();
            boolean isSelected = mine.getId().equals(selectedMineId);
            if (isSelected) {
                commandBuilder.set(entrySelector + " #SelectedOverlay.Visible", true);
                commandBuilder.set(entrySelector + " #AccentBar.Visible", true);
            }
            commandBuilder.set(entrySelector + " #MineName.Text", nameLabel);
            boolean hasSpawn = mine.getSpawnX() != 0 || mine.getSpawnY() != 0 || mine.getSpawnZ() != 0;
            int zoneCount = mine.getZones() != null ? mine.getZones().size() : 0;
            BigNumber cost = mine.getUnlockCost();
            String costStr = cost != null && !cost.equals(BigNumber.ZERO) ? cost.toString() : "Free";
            String status = "Zones: " + zoneCount + " | Spawn: " + (hasSpawn ? "OK" : "NO")
                + " | Order: " + mine.getDisplayOrder() + " | Cost: " + costStr;
            commandBuilder.set(entrySelector + " #MineStatus.Text", status);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                entrySelector,
                EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_SELECT_PREFIX + mine.getId()), false);
            index++;
        }
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateFields(commandBuilder);
        buildMineList(commandBuilder, eventBuilder);
        bindEvents(eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    public static class MineData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_MINE_ID = "@MineId";
        static final String KEY_MINE_NAME = "@MineName";
        static final String KEY_MINE_ORDER = "@MineOrder";
        static final String KEY_MINE_COST = "@MineCost";
        static final String BUTTON_SELECT_PREFIX = "Select:";
        static final String BUTTON_CREATE = "CreateMine";
        static final String BUTTON_SET_NAME = "SetName";
        static final String BUTTON_SET_ORDER = "SetOrder";
        static final String BUTTON_SET_SPAWN = "SetSpawn";
        static final String BUTTON_SET_COST = "SetCost";
        static final String BUTTON_DELETE = "DeleteMine";
        static final String BUTTON_ZONES = "Zones";
        static final String BUTTON_GATE = "Gate";
        static final String BUTTON_BLOCK_CONFIG = "BlockConfig";
        static final String BUTTON_SET_MINER_POS = "SetMinerPos";
        static final String BUTTON_SLOT_NEXT = "SlotNext";
        static final String BUTTON_SLOT_PREV = "SlotPrev";
        static final String BUTTON_ADD_SLOT_WP = "AddSlotWp";
        static final String BUTTON_ADD_MAIN_WP = "AddMainWp";
        static final String BUTTON_CLEAR_SLOT_WP = "ClearSlotWp";
        static final String BUTTON_CLEAR_MAIN_WP = "ClearMainWp";
        static final String BUTTON_BACK = "Back";
        static final String BUTTON_CLOSE = "Close";

        public static final BuilderCodec<MineData> CODEC = BuilderCodec.<MineData>builder(MineData.class, MineData::new)
            .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, value) -> data.button = value, data -> data.button)
            .addField(new KeyedCodec<>(KEY_MINE_ID, Codec.STRING), (data, value) -> data.mineId = value, data -> data.mineId)
            .addField(new KeyedCodec<>(KEY_MINE_NAME, Codec.STRING), (data, value) -> data.mineName = value, data -> data.mineName)
            .addField(new KeyedCodec<>(KEY_MINE_ORDER, Codec.STRING), (data, value) -> data.mineOrder = value, data -> data.mineOrder)
            .addField(new KeyedCodec<>(KEY_MINE_COST, Codec.STRING), (data, value) -> data.mineCost = value, data -> data.mineCost)
            .build();

        private String button;
        private String mineId;
        private String mineName;
        private String mineOrder;
        private String mineCost;
    }
}
