package io.hyvexa.ascend.mine.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
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
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.mine.MineBlockRegistry;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.ascend.mine.data.MineZoneLayer;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

public class MineZoneAdminPage extends InteractiveCustomUIPage<MineZoneAdminPage.ZoneData> {

    private static final String TAB_ZONES = "zones";
    private static final String TAB_BLOCKS = "blocks";
    private static final String TAB_LAYERS = "layers";

    // Shared with AscendAdminCommand so chat pos1/pos2 and UI are synchronized
    private static Map<UUID, int[]> pos1Selections() { return io.hyvexa.ascend.command.AscendAdminCommand.minePos1; }
    private static Map<UUID, int[]> pos2Selections() { return io.hyvexa.ascend.command.AscendAdminCommand.minePos2; }

    private final MineConfigStore mineConfigStore;
    private final String mineId;
    private final UUID playerUuid;
    private String zoneId = "";
    private String blockId = "";
    private String blockProb = "";
    private String threshold = "";
    private String cooldown = "";
    private String selectedZoneId = "";
    private String selectedLayerId = "";
    private String layerMinY = "";
    private String layerMaxY = "";
    private String activeTab = TAB_ZONES;

    public MineZoneAdminPage(@Nonnull PlayerRef playerRef, MineConfigStore mineConfigStore, String mineId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ZoneData.CODEC);
        this.mineConfigStore = mineConfigStore;
        this.mineId = mineId;
        this.playerUuid = playerRef.getUuid();
    }

    public void setSelectedZoneId(String zoneId) {
        this.selectedZoneId = zoneId != null ? zoneId : "";
    }

    public void setSelectedLayerId(String layerId) {
        this.selectedLayerId = layerId != null ? layerId : "";
        if (!this.selectedLayerId.isEmpty()) {
            this.activeTab = TAB_BLOCKS;
        }
    }

    public void setSelectedBlockId(String blockId) {
        this.blockId = blockId != null ? blockId : "";
        if (!this.blockId.isEmpty()) {
            this.activeTab = TAB_BLOCKS;
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Ascend_MineZoneAdmin.ui");
        bindEvents(uiEventBuilder);
        populateFields(uiCommandBuilder);
        applyTabState(uiCommandBuilder);
        buildZoneList(uiCommandBuilder, uiEventBuilder);
        buildBlockTable(uiCommandBuilder, uiEventBuilder);
        buildLayerList(uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ZoneData data) {
        super.handleDataEvent(ref, store, data);
        if (data.zoneId != null) {
            zoneId = data.zoneId.trim();
        }
        if (data.blockProb != null) {
            blockProb = data.blockProb.trim();
        }
        if (data.threshold != null) {
            threshold = data.threshold.trim();
        }
        if (data.cooldown != null) {
            cooldown = data.cooldown.trim();
        }
        if (data.layerMinY != null) {
            layerMinY = data.layerMinY.trim();
        }
        if (data.layerMaxY != null) {
            layerMaxY = data.layerMaxY.trim();
        }
        if (data.button == null) {
            return;
        }
        // Tab switching
        if (data.button.equals(ZoneData.BUTTON_TAB_ZONES)) {
            activeTab = TAB_ZONES;
            sendRefresh(ref, store);
            return;
        }
        if (data.button.equals(ZoneData.BUTTON_TAB_BLOCKS)) {
            activeTab = TAB_BLOCKS;
            sendRefresh(ref, store);
            return;
        }
        if (data.button.equals(ZoneData.BUTTON_TAB_LAYERS)) {
            activeTab = TAB_LAYERS;
            sendRefresh(ref, store);
            return;
        }
        if (data.button.equals(ZoneData.BUTTON_BACK)) {
            handleBack(ref, store);
            return;
        }
        if (data.button.startsWith(ZoneData.BUTTON_SELECT_PREFIX)) {
            selectedZoneId = data.button.substring(ZoneData.BUTTON_SELECT_PREFIX.length());
            selectedLayerId = "";
            layerMinY = "";
            layerMaxY = "";
            MineZone zone = findZone(selectedZoneId);
            if (zone != null) {
                zoneId = zone.getId();
            }
            sendRefresh(ref, store);
            return;
        }
        if (data.button.equals(ZoneData.BUTTON_POS1)) {
            handlePos1(ref, store);
            return;
        }
        if (data.button.equals(ZoneData.BUTTON_POS2)) {
            handlePos2(ref, store);
            return;
        }
        if (data.button.equals(ZoneData.BUTTON_CREATE_ZONE)) {
            handleCreateZone(ref, store);
            return;
        }
        if (data.button.equals(ZoneData.BUTTON_DELETE_ZONE)) {
            handleDeleteZone(ref, store);
            return;
        }
        if (data.button.equals(ZoneData.BUTTON_ADD_LAYER)) {
            handleAddLayer(ref, store);
            return;
        }
        if (data.button.startsWith(ZoneData.BUTTON_DELETE_LAYER_PREFIX)) {
            handleDeleteLayer(ref, store, data.button.substring(ZoneData.BUTTON_DELETE_LAYER_PREFIX.length()));
            return;
        }
        if (data.button.startsWith(ZoneData.BUTTON_SELECT_LAYER_PREFIX)) {
            selectedLayerId = data.button.substring(ZoneData.BUTTON_SELECT_LAYER_PREFIX.length());
            activeTab = TAB_BLOCKS;
            sendRefresh(ref, store);
            return;
        }
        if (data.button.equals(ZoneData.BUTTON_CHOOSE_BLOCK)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null && playerRef != null) {
                MineBlockPickerPage picker = new MineBlockPickerPage(
                    playerRef, mineConfigStore, selectedZoneId, selectedLayerId, blockId
                );
                player.getPageManager().openCustomPage(ref, store, picker);
            }
            return;
        }
        if (data.button.equals(ZoneData.BUTTON_ADD_BLOCK)) {
            handleAddBlock(ref, store);
            return;
        }
        if (data.button.startsWith(ZoneData.BUTTON_REMOVE_BLOCK_PREFIX)) {
            handleRemoveBlock(ref, store, data.button.substring(ZoneData.BUTTON_REMOVE_BLOCK_PREFIX.length()));
            return;
        }
        if (data.button.equals(ZoneData.BUTTON_SET_THRESHOLD)) {
            handleSetThreshold(ref, store);
            return;
        }
        if (data.button.equals(ZoneData.BUTTON_SET_COOLDOWN)) {
            handleSetCooldown(ref, store);
            return;
        }
        if (data.button.equals(ZoneData.BUTTON_REGEN)) {
            handleRegen(ref, store);
            return;
        }
    }

    private void handlePos1(Ref<EntityStore> ref, Store<EntityStore> store) {
        handleSetPos(ref, store, pos1Selections(), "Pos1");
    }

    private void handlePos2(Ref<EntityStore> ref, Store<EntityStore> store) {
        handleSetPos(ref, store, pos2Selections(), "Pos2");
    }

    private void handleSetPos(Ref<EntityStore> ref, Store<EntityStore> store,
                              Map<UUID, int[]> posMap, String label) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("Unable to read player position."));
            return;
        }
        Vector3d position = transform.getPosition();
        int x = (int) Math.floor(position.getX());
        int y = (int) Math.floor(position.getY() - 0.2d);
        int z = (int) Math.floor(position.getZ());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        UUID uuid = playerRef != null ? playerRef.getUuid() : null;
        if (uuid == null) return;
        posMap.put(uuid, new int[]{x, y, z});
        player.sendMessage(Message.raw(label + ": (" + x + ", " + y + ", " + z + ")"));
        sendRefresh(ref, store);
    }

    private void handleCreateZone(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        String id = zoneId != null ? zoneId.trim() : "";
        if (id.isEmpty()) {
            player.sendMessage(Message.raw("Zone ID is required."));
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        UUID uuid = playerRef != null ? playerRef.getUuid() : null;
        if (uuid == null) return;
        int[] p1 = pos1Selections().get(uuid);
        int[] p2 = pos2Selections().get(uuid);
        if (p1 == null || p2 == null) {
            player.sendMessage(Message.raw("Set both Pos1 and Pos2 first."));
            return;
        }
        MineZone existing = findZone(id);
        if (existing != null) {
            player.sendMessage(Message.raw("Zone already exists: " + id));
            return;
        }
        MineZone zone = new MineZone(id, mineId, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2]);
        mineConfigStore.saveZone(zone);
        pos1Selections().remove(uuid);
        pos2Selections().remove(uuid);
        selectedZoneId = id;
        player.sendMessage(Message.raw("Zone created: " + id + " (" + zone.getTotalBlocks() + " blocks)"));
        sendRefresh(ref, store);
    }

    private void handleDeleteZone(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        String id = selectedZoneId;
        if (id == null || id.isEmpty()) {
            player.sendMessage(Message.raw("Select a zone first."));
            return;
        }
        boolean removed = mineConfigStore.deleteZone(id);
        if (removed) {
            player.sendMessage(Message.raw("Zone deleted: " + id));
            selectedZoneId = "";
            selectedLayerId = "";
        } else {
            player.sendMessage(Message.raw("Zone not found: " + id));
        }
        sendRefresh(ref, store);
    }

    private void handleAddBlock(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        MineZone zone = resolveSelectedZone(player);
        if (zone == null) return;
        if (blockId.isEmpty()) {
            player.sendMessage(Message.raw("Select a block first."));
            return;
        }
        double prob;
        try {
            prob = Double.parseDouble(blockProb);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Probability must be a number (0.0-1.0)."));
            return;
        }
        if (prob < 0.0 || prob > 1.0) {
            player.sendMessage(Message.raw("Probability must be between 0.0 and 1.0."));
            return;
        }
        String displayName = MineBlockRegistry.getDisplayName(blockId);
        if (!selectedLayerId.isEmpty()) {
            MineZoneLayer layer = findLayer(selectedLayerId);
            if (layer != null) {
                layer.getBlockTable().put(blockId, prob);
                mineConfigStore.saveLayer(layer);
                player.sendMessage(Message.raw("Block added to layer: " + displayName + " = " + prob));
                sendRefresh(ref, store);
                return;
            }
        }
        zone.getBlockTable().put(blockId, prob);
        mineConfigStore.saveZone(zone);
        player.sendMessage(Message.raw("Block added: " + displayName + " = " + prob));
        sendRefresh(ref, store);
    }

    private void handleRemoveBlock(Ref<EntityStore> ref, Store<EntityStore> store, String removeBlockId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        MineZone zone = resolveSelectedZone(player);
        if (zone == null) return;
        if (!selectedLayerId.isEmpty()) {
            MineZoneLayer layer = findLayer(selectedLayerId);
            if (layer != null) {
                Double removedFromLayer = layer.getBlockTable().remove(removeBlockId);
                if (removedFromLayer != null) {
                    mineConfigStore.saveLayer(layer);
                    String displayName = MineBlockRegistry.getDisplayName(removeBlockId);
                    player.sendMessage(Message.raw("Block removed from layer: " + displayName));
                }
                sendRefresh(ref, store);
                return;
            }
        }
        Double removed = zone.getBlockTable().remove(removeBlockId);
        if (removed != null) {
            mineConfigStore.saveZone(zone);
            String displayName = MineBlockRegistry.getDisplayName(removeBlockId);
            player.sendMessage(Message.raw("Block removed: " + displayName));
        }
        sendRefresh(ref, store);
    }

    private void handleSetThreshold(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        player.sendMessage(Message.raw("Threshold no longer used. Use cooldown field to set reset interval (seconds)."));
    }

    private void handleSetCooldown(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        MineZone zone = resolveSelectedZone(player);
        if (zone == null) return;
        int value;
        try {
            value = Integer.parseInt(cooldown);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Interval must be a whole number (seconds)."));
            return;
        }
        if (value < 0) {
            player.sendMessage(Message.raw("Interval must be >= 0."));
            return;
        }
        zone.setRegenIntervalSeconds(value);
        mineConfigStore.saveZone(zone);
        player.sendMessage(Message.raw("Reset interval set: " + value + "s"));
        sendRefresh(ref, store);
    }

    private void handleRegen(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        MineZone zone = resolveSelectedZone(player);
        if (zone == null) return;

        MineManager mineManager = ParkourAscendPlugin.getInstance().getMineManager();
        if (mineManager == null) {
            player.sendMessage(Message.raw("Mine manager not initialized."));
            return;
        }

        World world = store.getExternalData().getWorld();
        if (world == null) {
            player.sendMessage(Message.raw("Unable to get world."));
            return;
        }

        mineManager.invalidateZoneCache(zone.getId());
        world.execute(() -> {
            mineManager.generateZone(world, zone);
        });
        player.sendMessage(Message.raw("Zone regenerating: " + zone.getId() + " (" + zone.getTotalBlocks() + " blocks)"));
    }

    private void handleAddLayer(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        MineZone zone = resolveSelectedZone(player);
        if (zone == null) return;
        int minY, maxY;
        try {
            minY = Integer.parseInt(layerMinY);
            maxY = Integer.parseInt(layerMaxY);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("MinY and MaxY must be whole numbers."));
            return;
        }
        if (minY < zone.getMinY() || maxY > zone.getMaxY()) {
            player.sendMessage(Message.raw("Layer Y range must be within zone bounds (" + zone.getMinY() + "-" + zone.getMaxY() + ")."));
            return;
        }
        for (MineZoneLayer existing : zone.getLayers()) {
            if (rangesOverlap(minY, maxY, existing.getMinY(), existing.getMaxY())) {
                player.sendMessage(Message.raw(
                    "Layer overlaps existing layer " + existing.getId()
                        + " (Y " + existing.getMinY() + "-" + existing.getMaxY() + ")."));
                return;
            }
        }

        String layerId = java.util.UUID.randomUUID().toString().replace("-", "");
        MineZoneLayer layer = new MineZoneLayer(layerId, zone.getId(), minY, maxY);
        mineConfigStore.saveLayer(layer);
        selectedLayerId = layerId;
        layerMinY = "";
        layerMaxY = "";
        player.sendMessage(Message.raw("Layer created: Y " + layer.getMinY() + "-" + layer.getMaxY()));
        sendRefresh(ref, store);
    }

    private void handleDeleteLayer(Ref<EntityStore> ref, Store<EntityStore> store, String layerId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        boolean removed = mineConfigStore.deleteLayer(layerId);
        if (removed) {
            player.sendMessage(Message.raw("Layer deleted."));
            if (layerId.equals(selectedLayerId)) {
                selectedLayerId = "";
            }
            layerMinY = "";
            layerMaxY = "";
        } else {
            player.sendMessage(Message.raw("Layer not found."));
        }
        sendRefresh(ref, store);
    }

    private boolean rangesOverlap(int minA, int maxA, int minB, int maxB) {
        return Math.max(minA, minB) <= Math.min(maxA, maxB);
    }

    private void handleBack(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return;
        player.getPageManager().openCustomPage(ref, store, new MineAdminPage(playerRef, mineConfigStore));
    }

    private MineZone resolveSelectedZone(Player player) {
        if (selectedZoneId == null || selectedZoneId.isEmpty()) {
            player.sendMessage(Message.raw("Select a zone first."));
            return null;
        }
        MineZone zone = findZone(selectedZoneId);
        if (zone == null) {
            player.sendMessage(Message.raw("Zone not found: " + selectedZoneId));
        }
        return zone;
    }

    private MineZone findZone(String zoneId) {
        Mine mine = mineConfigStore.getMine(mineId);
        if (mine == null) return null;
        for (MineZone z : mine.getZones()) {
            if (z.getId().equals(zoneId)) {
                return z;
            }
        }
        return null;
    }

    private MineZoneLayer findLayer(String layerId) {
        MineZone zone = findZone(selectedZoneId);
        if (zone == null) return null;
        for (MineZoneLayer l : zone.getLayers()) {
            if (l.getId().equals(layerId)) return l;
        }
        return null;
    }

    // ---- Tab state ----

    private void applyTabState(UICommandBuilder cmd) {
        boolean zonesActive = TAB_ZONES.equals(activeTab);
        boolean blocksActive = TAB_BLOCKS.equals(activeTab);
        boolean layersActive = TAB_LAYERS.equals(activeTab);

        // Tab visibility
        cmd.set("#ZonesTab.Visible", zonesActive);
        cmd.set("#BlocksTab.Visible", blocksActive);
        cmd.set("#LayersTab.Visible", layersActive);

        // Zones tab styling
        cmd.set("#TabZonesInactiveBg.Visible", !zonesActive);
        cmd.set("#TabZonesActiveBg.Visible", zonesActive);
        cmd.set("#TabZonesAccentInactive.Visible", !zonesActive);
        cmd.set("#TabZonesAccentActive.Visible", zonesActive);
        cmd.set("#TabZonesLabel.Style.TextColor", zonesActive ? "#f0f4f8" : "#9fb0ba");

        // Blocks tab styling
        cmd.set("#TabBlocksInactiveBg.Visible", !blocksActive);
        cmd.set("#TabBlocksActiveBg.Visible", blocksActive);
        cmd.set("#TabBlocksAccentInactive.Visible", !blocksActive);
        cmd.set("#TabBlocksAccentActive.Visible", blocksActive);
        cmd.set("#TabBlocksLabel.Style.TextColor", blocksActive ? "#f0f4f8" : "#9fb0ba");

        // Layers tab styling
        cmd.set("#TabLayersInactiveBg.Visible", !layersActive);
        cmd.set("#TabLayersActiveBg.Visible", layersActive);
        cmd.set("#TabLayersAccentInactive.Visible", !layersActive);
        cmd.set("#TabLayersAccentActive.Visible", layersActive);
        cmd.set("#TabLayersLabel.Style.TextColor", layersActive ? "#f0f4f8" : "#9fb0ba");
    }

    // ---- Event binding ----

    private void bindEvents(UIEventBuilder uiEventBuilder) {
        // Tab buttons
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabZones",
            EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_TAB_ZONES), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabBlocks",
            EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_TAB_BLOCKS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabLayers",
            EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_TAB_LAYERS), false);

        // Fields
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ZoneIdField",
            EventData.of(ZoneData.KEY_ZONE_ID, "#ZoneIdField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BlockProbField",
            EventData.of(ZoneData.KEY_BLOCK_PROB, "#BlockProbField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ThresholdField",
            EventData.of(ZoneData.KEY_THRESHOLD, "#ThresholdField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CooldownField",
            EventData.of(ZoneData.KEY_COOLDOWN, "#CooldownField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#LayerMinYField",
            EventData.of(ZoneData.KEY_LAYER_MIN_Y, "#LayerMinYField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#LayerMaxYField",
            EventData.of(ZoneData.KEY_LAYER_MAX_Y, "#LayerMaxYField.Value"), false);

        // Zones tab buttons
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Pos1Button",
            EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_POS1), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Pos2Button",
            EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_POS2), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CreateZoneButton",
            EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_CREATE_ZONE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteZoneButton",
            EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_DELETE_ZONE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetThresholdButton",
            EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_SET_THRESHOLD), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetCooldownButton",
            EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_SET_COOLDOWN), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RegenButton",
            EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_REGEN), false);

        // Blocks tab buttons
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ChooseBlockButton",
            EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_CHOOSE_BLOCK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddBlockButton",
            EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_ADD_BLOCK), false);

        // Layers tab buttons
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddLayerButton",
            EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_ADD_LAYER), false);

        // Back button
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_BACK), false);
    }

    // ---- Field population ----

    private void populateFields(UICommandBuilder commandBuilder) {
        Mine mine = mineConfigStore.getMine(mineId);
        String mineName = mine != null ? mine.getName() : mineId;
        commandBuilder.set("#HeaderTitle.Text", "Zones - " + mineName);

        commandBuilder.set("#ZoneIdField.Value", zoneId);
        commandBuilder.set("#BlockProbField.Value", blockProb);
        commandBuilder.set("#ThresholdField.Value", threshold);
        commandBuilder.set("#CooldownField.Value", cooldown);
        commandBuilder.set("#LayerMinYField.Value", layerMinY);
        commandBuilder.set("#LayerMaxYField.Value", layerMaxY);

        // Selected block display
        String selectedBlockDisplay = blockId.isEmpty() ? "(none)" : MineBlockRegistry.getDisplayName(blockId);
        commandBuilder.set("#SelectedBlockText.Text", "Selected: " + selectedBlockDisplay);

        // Block context label
        String blockContext = selectedLayerId.isEmpty() ? "Zone blocks" : "Layer blocks";
        commandBuilder.set("#BlockContextLabel.Text", blockContext);

        // Selected zone info
        String selectedInfo = "No zone selected";
        String zoneInfo = "";
        if (selectedZoneId != null && !selectedZoneId.isEmpty()) {
            MineZone zone = findZone(selectedZoneId);
            if (zone != null) {
                selectedInfo = zone.getId();
                int w = zone.getMaxX() - zone.getMinX() + 1;
                int h = zone.getMaxY() - zone.getMinY() + 1;
                int d = zone.getMaxZ() - zone.getMinZ() + 1;
                StringBuilder sb = new StringBuilder();
                sb.append(w).append("x").append(h).append("x").append(d);
                sb.append(" | Blocks: ").append(zone.getBlockTable().size());
                sb.append(" | Reset: ").append(zone.getRegenIntervalSeconds()).append("s");
                sb.append(" | Layers: ").append(zone.getLayers().size());
                zoneInfo = sb.toString();
            } else {
                selectedInfo = selectedZoneId + " (not found)";
            }
        }
        commandBuilder.set("#SelectedZoneText.Text", "Selected: " + selectedInfo);
        commandBuilder.set("#ZoneInfoText.Text", zoneInfo);

        // Pos1/Pos2 display
        if (playerUuid != null) {
            int[] p1 = pos1Selections().get(playerUuid);
            int[] p2 = pos2Selections().get(playerUuid);
            commandBuilder.set("#Pos1Text.Text", p1 != null
                ? "Pos1: (" + p1[0] + ", " + p1[1] + ", " + p1[2] + ")"
                : "Pos1: (not set)");
            commandBuilder.set("#Pos2Text.Text", p2 != null
                ? "Pos2: (" + p2[0] + ", " + p2[1] + ", " + p2[2] + ")"
                : "Pos2: (not set)");
        }
    }

    // ---- List builders ----

    private void buildZoneList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#ZoneCards");
        Mine mine = mineConfigStore.getMine(mineId);
        boolean hasZones = mine != null && !mine.getZones().isEmpty();
        commandBuilder.set("#ZonesEmptyLabel.Visible", !hasZones);
        if (mine == null) return;

        int index = 0;
        for (MineZone zone : mine.getZones()) {
            commandBuilder.append("#ZoneCards", "Pages/Ascend_MineZoneEntry.ui");
            String entrySelector = "#ZoneCards[" + index + "]";

            boolean isSelected = zone.getId().equals(selectedZoneId);
            if (isSelected) {
                commandBuilder.set(entrySelector + " #SelectedOverlay.Visible", true);
                commandBuilder.set(entrySelector + " #AccentBar.Visible", true);
            }
            commandBuilder.set(entrySelector + " #ZoneName.Text", zone.getId());

            int w = zone.getMaxX() - zone.getMinX() + 1;
            int h = zone.getMaxY() - zone.getMinY() + 1;
            int d = zone.getMaxZ() - zone.getMinZ() + 1;
            String info = w + "x" + h + "x" + d
                + " | Blocks: " + zone.getBlockTable().size()
                + " | Reset: " + zone.getRegenIntervalSeconds() + "s";
            commandBuilder.set(entrySelector + " #ZoneInfo.Text", info);

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                entrySelector,
                EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_SELECT_PREFIX + zone.getId()), false);
            index++;
        }
    }

    private void buildBlockTable(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#BlockEntries");
        if (selectedZoneId == null || selectedZoneId.isEmpty()) {
            commandBuilder.set("#BlocksEmptyLabel.Visible", true);
            commandBuilder.set("#BlocksEmptyLabel.Text", "Select a zone first");
            return;
        }
        MineZone zone = findZone(selectedZoneId);
        if (zone == null) {
            commandBuilder.set("#BlocksEmptyLabel.Visible", true);
            commandBuilder.set("#BlocksEmptyLabel.Text", "Zone not found");
            return;
        }

        Map<String, Double> tableToShow = zone.getBlockTable();
        if (!selectedLayerId.isEmpty()) {
            MineZoneLayer layer = findLayer(selectedLayerId);
            if (layer != null) {
                tableToShow = layer.getBlockTable();
            } else {
                selectedLayerId = "";
            }
        }

        commandBuilder.set("#BlocksEmptyLabel.Visible", tableToShow.isEmpty());
        if (tableToShow.isEmpty()) {
            commandBuilder.set("#BlocksEmptyLabel.Text", "No blocks configured");
            return;
        }

        int index = 0;
        for (Map.Entry<String, Double> entry : tableToShow.entrySet()) {
            commandBuilder.append("#BlockEntries", "Pages/Ascend_MineBlockEntry.ui");
            String sel = "#BlockEntries[" + index + "]";
            String displayName = MineBlockRegistry.getDisplayName(entry.getKey());
            commandBuilder.set(sel + " #BlockIcon.ItemId", entry.getKey());
            commandBuilder.set(sel + " #BlockName.Text", displayName);
            commandBuilder.set(sel + " #BlockProb.Text", formatProb(entry.getValue()));

            int hp = mineConfigStore.getBlockHp(entry.getKey());
            commandBuilder.set(sel + " #BlockHp.Text", hp + " HP");
            if (hp > 1) {
                commandBuilder.set(sel + " #BlockHp.Style.TextColor", "#ef4444");
            } else {
                commandBuilder.set(sel + " #BlockHp.Style.TextColor", "#9fb0ba");
            }

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                sel + " #RemoveBtn",
                EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_REMOVE_BLOCK_PREFIX + entry.getKey()), false);
            index++;
        }
    }

    private void buildLayerList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#LayerCards");
        if (selectedZoneId == null || selectedZoneId.isEmpty()) {
            commandBuilder.set("#LayersEmptyLabel.Visible", true);
            commandBuilder.set("#LayersEmptyLabel.Text", "Select a zone first");
            return;
        }
        MineZone zone = findZone(selectedZoneId);
        if (zone == null) {
            commandBuilder.set("#LayersEmptyLabel.Visible", true);
            commandBuilder.set("#LayersEmptyLabel.Text", "Zone not found");
            return;
        }

        boolean hasLayers = !zone.getLayers().isEmpty();
        commandBuilder.set("#LayersEmptyLabel.Visible", !hasLayers);

        int index = 0;
        for (MineZoneLayer layer : zone.getLayers()) {
            commandBuilder.append("#LayerCards", "Pages/Ascend_MineLayerEntry.ui");
            String sel = "#LayerCards[" + index + "]";

            boolean isSelected = layer.getId().equals(selectedLayerId);
            if (isSelected) {
                commandBuilder.set(sel + " #SelectedOverlay.Visible", true);
                commandBuilder.set(sel + " #AccentBar.Visible", true);
            }

            String nameLabel = "Y " + layer.getMinY() + " - " + layer.getMaxY();
            commandBuilder.set(sel + " #LayerName.Text", nameLabel);
            commandBuilder.set(sel + " #LayerDetails.Text",
                layer.getBlockTable().size() + " block types");

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel,
                EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_SELECT_LAYER_PREFIX + layer.getId()), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sel + " #DeleteLayerBtn",
                EventData.of(ZoneData.KEY_BUTTON, ZoneData.BUTTON_DELETE_LAYER_PREFIX + layer.getId()), false);

            index++;
        }
    }

    // ---- Refresh ----

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateFields(commandBuilder);
        applyTabState(commandBuilder);
        buildZoneList(commandBuilder, eventBuilder);
        buildBlockTable(commandBuilder, eventBuilder);
        buildLayerList(commandBuilder, eventBuilder);
        bindEvents(eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private String formatProb(double value) {
        if (value == (long) value) return String.valueOf((long) value);
        return String.format("%.3f", value);
    }

    public static class ZoneData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_ZONE_ID = "@ZoneId";
        static final String KEY_BLOCK_PROB = "@BlockProb";
        static final String KEY_THRESHOLD = "@Threshold";
        static final String KEY_COOLDOWN = "@Cooldown";
        static final String KEY_LAYER_MIN_Y = "@LayerMinY";
        static final String KEY_LAYER_MAX_Y = "@LayerMaxY";
        static final String BUTTON_SELECT_PREFIX = "Select:";
        static final String BUTTON_POS1 = "Pos1";
        static final String BUTTON_POS2 = "Pos2";
        static final String BUTTON_CREATE_ZONE = "CreateZone";
        static final String BUTTON_DELETE_ZONE = "DeleteZone";
        static final String BUTTON_CHOOSE_BLOCK = "ChooseBlock";
        static final String BUTTON_ADD_BLOCK = "AddBlock";
        static final String BUTTON_REMOVE_BLOCK_PREFIX = "RemoveBlock:";
        static final String BUTTON_SET_THRESHOLD = "SetThreshold";
        static final String BUTTON_SET_COOLDOWN = "SetCooldown";
        static final String BUTTON_REGEN = "Regen";
        static final String BUTTON_BACK = "Back";
        static final String BUTTON_ADD_LAYER = "AddLayer";
        static final String BUTTON_DELETE_LAYER_PREFIX = "DeleteLayer:";
        static final String BUTTON_SELECT_LAYER_PREFIX = "SelectLayer:";
        static final String BUTTON_TAB_ZONES = "TabZones";
        static final String BUTTON_TAB_BLOCKS = "TabBlocks";
        static final String BUTTON_TAB_LAYERS = "TabLayers";

        public static final BuilderCodec<ZoneData> CODEC = BuilderCodec.<ZoneData>builder(ZoneData.class, ZoneData::new)
            .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, value) -> data.button = value, data -> data.button)
            .addField(new KeyedCodec<>(KEY_ZONE_ID, Codec.STRING), (data, value) -> data.zoneId = value, data -> data.zoneId)
            .addField(new KeyedCodec<>(KEY_BLOCK_PROB, Codec.STRING), (data, value) -> data.blockProb = value, data -> data.blockProb)
            .addField(new KeyedCodec<>(KEY_THRESHOLD, Codec.STRING), (data, value) -> data.threshold = value, data -> data.threshold)
            .addField(new KeyedCodec<>(KEY_COOLDOWN, Codec.STRING), (data, value) -> data.cooldown = value, data -> data.cooldown)
            .addField(new KeyedCodec<>(KEY_LAYER_MIN_Y, Codec.STRING), (data, value) -> data.layerMinY = value, data -> data.layerMinY)
            .addField(new KeyedCodec<>(KEY_LAYER_MAX_Y, Codec.STRING), (data, value) -> data.layerMaxY = value, data -> data.layerMaxY)
            .build();

        private String button;
        private String zoneId;
        private String blockProb;
        private String threshold;
        private String cooldown;
        private String layerMinY;
        private String layerMaxY;
    }
}
