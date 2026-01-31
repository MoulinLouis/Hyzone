package io.hyvexa.ascend.ui;

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
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.holo.AscendHologramManager;
import io.hyvexa.common.util.HylogramsBridge;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AscendAdminPage extends InteractiveCustomUIPage<AscendAdminPage.MapData> {

    private final AscendMapStore mapStore;
    private String mapId = "";
    private String mapName = "";
    private String mapReward = "0";
    private String mapPrice = "0";
    private String mapOrder = "0";
    private String mapRobotBaseTime = "30000";
    private String mapRobotReduction = "0";
    private String mapSearch = "";
    private String selectedMapId = "";

    public AscendAdminPage(@Nonnull PlayerRef playerRef, AscendMapStore mapStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, MapData.CODEC);
        this.mapStore = mapStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Ascend_MapAdmin.ui");
        bindEvents(uiEventBuilder);
        populateFields(uiCommandBuilder);
        buildMapList(uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull MapData data) {
        super.handleDataEvent(ref, store, data);
        String previousSearch = mapSearch;
        if (data.mapId != null) {
            mapId = data.mapId.trim();
        }
        if (data.mapName != null) {
            mapName = data.mapName.trim();
        }
        if (data.mapReward != null) {
            mapReward = data.mapReward.trim();
        }
        if (data.mapPrice != null) {
            mapPrice = data.mapPrice.trim();
        }
        if (data.mapOrder != null) {
            mapOrder = data.mapOrder.trim();
        }
        if (data.mapRobotBaseTime != null) {
            mapRobotBaseTime = data.mapRobotBaseTime.trim();
        }
        if (data.mapRobotReduction != null) {
            mapRobotReduction = data.mapRobotReduction.trim();
        }
        if (data.mapSearch != null) {
            mapSearch = data.mapSearch.trim();
        }
        if (data.button == null) {
            if (!previousSearch.equals(mapSearch)) {
                sendRefresh(ref, store);
            }
            return;
        }
        if (data.button.equals(MapData.BUTTON_CLOSE)) {
            this.close();
            return;
        }
        if (data.button.startsWith(MapData.BUTTON_SELECT_PREFIX)) {
            selectedMapId = data.button.substring(MapData.BUTTON_SELECT_PREFIX.length());
            AscendMap map = mapStore.getMap(selectedMapId);
            if (map != null) {
                mapId = map.getId();
                mapName = map.getName() != null ? map.getName() : "";
                mapReward = String.valueOf(map.getBaseReward());
                mapPrice = String.valueOf(map.getPrice());
                mapOrder = String.valueOf(map.getDisplayOrder());
                mapRobotBaseTime = String.valueOf(map.getBaseRunTimeMs());
                mapRobotReduction = String.valueOf(map.getRobotTimeReductionMs());
            }
            sendRefresh(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_CREATE)) {
            handleCreate(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_UPDATE)) {
            handleUpdate(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_SET_START)) {
            handleSetStart(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_SET_FINISH)) {
            handleSetFinish(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_ADD_WAYPOINT)) {
            handleAddWaypoint(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_CLEAR_WAYPOINTS)) {
            handleClearWaypoints(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_SET_PRICE)) {
            handleSetPrice(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_SET_ORDER)) {
            handleSetOrder(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_SET_ROBOT_TIMING)) {
            handleSetRobotTiming(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_CREATE_MAP_HOLO)) {
            handleCreateMapHolo(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_DELETE_MAP_HOLO)) {
            handleDeleteMapHolo(ref, store);
        }
    }

    private void handleCreate(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        String id = mapId != null ? mapId.trim() : "";
        if (id.isEmpty()) {
            player.sendMessage(Message.raw("Map id is required."));
            return;
        }
        if (mapStore.getMap(id) != null) {
            player.sendMessage(Message.raw("Map already exists: " + id));
            return;
        }
        String name = mapName != null ? mapName.trim() : "";
        long reward = parseReward(player);
        if (reward < 0) {
            return;
        }
        long price = parsePrice(player);
        if (price < 0) {
            return;
        }
        long robotBaseTime = parseRobotBaseTime(player, 30000L);
        if (robotBaseTime < 0) {
            return;
        }
        long robotReduction = parseRobotReduction(player, 0L);
        if (robotReduction < 0) {
            return;
        }
        int order = parseOrder(player, resolveNextOrder());
        AscendMap map = new AscendMap();
        map.setId(id);
        map.setName(name);
        map.setPrice(price);
        map.setRobotPrice(0L);
        map.setBaseReward(reward);
        map.setBaseRunTimeMs(robotBaseTime);
        map.setRobotTimeReductionMs(robotReduction);
        map.setStorageCapacity((int) AscendConstants.DEFAULT_ROBOT_STORAGE);
        World world = store.getExternalData().getWorld();
        map.setWorld(world != null ? world.getName() : "Ascend");
        map.setDisplayOrder(order);
        mapStore.saveMap(map);
        refreshMapHolos(map, store);
        selectedMapId = id;
        player.sendMessage(Message.raw("Ascend map created: " + id));
        sendRefresh(ref, store);
    }

    private void handleUpdate(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        AscendMap map = resolveSelectedMap(player);
        if (map == null) {
            return;
        }
        String name = mapName != null ? mapName.trim() : "";
        long reward = parseReward(player);
        if (reward < 0) {
            return;
        }
        long price = parsePrice(player);
        if (price < 0) {
            return;
        }
        long robotBaseTime = parseRobotBaseTime(player, map.getBaseRunTimeMs());
        if (robotBaseTime < 0) {
            return;
        }
        long robotReduction = parseRobotReduction(player, map.getRobotTimeReductionMs());
        if (robotReduction < 0) {
            return;
        }
        int order = parseOrder(player, map.getDisplayOrder());
        map.setName(name);
        map.setBaseReward(reward);
        map.setPrice(price);
        map.setDisplayOrder(order);
        map.setBaseRunTimeMs(robotBaseTime);
        map.setRobotTimeReductionMs(robotReduction);
        mapStore.saveMap(map);
        refreshMapHolos(map, store);
        player.sendMessage(Message.raw("Map updated: " + map.getId()));
        sendRefresh(ref, store);
    }

    private void handleSetStart(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        AscendMap map = resolveSelectedMap(player);
        if (map == null) {
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("Unable to read player position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        map.setStartX(pos.getX());
        map.setStartY(pos.getY());
        map.setStartZ(pos.getZ());
        map.setStartRotX(rot.getX());
        map.setStartRotY(rot.getY());
        map.setStartRotZ(rot.getZ());
        World world = store.getExternalData().getWorld();
        map.setWorld(world != null ? world.getName() : map.getWorld());
        mapStore.saveMap(map);
        refreshMapHolos(map, store);
        player.sendMessage(Message.raw("Start set for map: " + map.getId()));
        sendRefresh(ref, store);
    }

    private void handleSetFinish(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        AscendMap map = resolveSelectedMap(player);
        if (map == null) {
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("Unable to read player position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        map.setFinishX(pos.getX());
        map.setFinishY(pos.getY());
        map.setFinishZ(pos.getZ());
        mapStore.saveMap(map);
        refreshMapHolos(map, store);
        player.sendMessage(Message.raw("Finish set for map: " + map.getId()));
        sendRefresh(ref, store);
    }

    private void handleAddWaypoint(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        AscendMap map = resolveSelectedMap(player);
        if (map == null) {
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("Unable to read player position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        AscendMap.Waypoint waypoint = new AscendMap.Waypoint(pos.getX(), pos.getY(), pos.getZ(), false, 0L);
        map.getWaypoints().add(waypoint);
        mapStore.saveMap(map);
        refreshMapHolos(map, store);
        player.sendMessage(Message.raw("Waypoint added to map: " + map.getId()));
        sendRefresh(ref, store);
    }

    private void handleClearWaypoints(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        AscendMap map = resolveSelectedMap(player);
        if (map == null) {
            return;
        }
        map.getWaypoints().clear();
        mapStore.saveMap(map);
        refreshMapHolos(map, store);
        player.sendMessage(Message.raw("Cleared waypoints for map: " + map.getId()));
        sendRefresh(ref, store);
    }

    private void handleSetPrice(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        AscendMap map = resolveSelectedMap(player);
        if (map == null) {
            return;
        }
        long price = parsePrice(player);
        if (price < 0) {
            return;
        }
        map.setPrice(price);
        mapStore.saveMap(map);
        refreshMapHolos(map, store);
        player.sendMessage(Message.raw("Price updated for map: " + map.getId()));
        sendRefresh(ref, store);
    }

    private void handleSetOrder(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        AscendMap map = resolveSelectedMap(player);
        if (map == null) {
            return;
        }
        int order = parseOrder(player, map.getDisplayOrder());
        map.setDisplayOrder(order);
        mapStore.saveMap(map);
        refreshMapHolos(map, store);
        player.sendMessage(Message.raw("Display order updated for map: " + map.getId()));
        sendRefresh(ref, store);
    }

    private void handleSetRobotTiming(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        AscendMap map = resolveSelectedMap(player);
        if (map == null) {
            return;
        }
        long robotBaseTime = parseRobotBaseTime(player, map.getBaseRunTimeMs());
        if (robotBaseTime < 0) {
            return;
        }
        long robotReduction = parseRobotReduction(player, map.getRobotTimeReductionMs());
        if (robotReduction < 0) {
            return;
        }
        map.setBaseRunTimeMs(robotBaseTime);
        map.setRobotTimeReductionMs(robotReduction);
        mapStore.saveMap(map);
        refreshMapHolos(map, store);
        player.sendMessage(Message.raw("Robot timing updated for map: " + map.getId()));
        sendRefresh(ref, store);
    }

    private void handleCreateMapHolo(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        AscendMap map = resolveSelectedMap(player);
        if (map == null) {
            return;
        }
        AscendHologramManager manager = resolveHologramManager(player);
        if (manager == null) {
            return;
        }
        Vector3d pos = resolvePlayerPosition(ref, store, player);
        if (pos == null) {
            return;
        }
        String worldName = resolveWorldName(store);
        boolean updated = manager.createOrUpdateMapInfoHolo(map, store, pos, worldName);
        if (updated) {
            player.sendMessage(Message.raw("Map info hologram saved for: " + map.getId()));
        } else {
            player.sendMessage(Message.raw("Failed to save map hologram."));
        }
    }

    private void handleDeleteMapHolo(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        AscendMap map = resolveSelectedMap(player);
        if (map == null) {
            return;
        }
        AscendHologramManager manager = resolveHologramManager(player);
        if (manager == null) {
            return;
        }
        boolean removed = manager.deleteMapInfoHolo(map.getId(), store);
        if (removed) {
            player.sendMessage(Message.raw("Removed map hologram for map: " + map.getId()));
        } else {
            player.sendMessage(Message.raw("No map hologram found for map: " + map.getId()));
        }
    }

    private AscendMap resolveSelectedMap(Player player) {
        String id = mapId != null && !mapId.isBlank() ? mapId : selectedMapId;
        if (id == null || id.isBlank()) {
            player.sendMessage(Message.raw("Select a map or enter a map id first."));
            return null;
        }
        AscendMap map = mapStore.getMap(id);
        if (map == null) {
            player.sendMessage(Message.raw("Map not found: " + id));
        }
        return map;
    }

    private long parseReward(Player player) {
        String raw = mapReward != null ? mapReward.trim() : "";
        if (raw.isEmpty()) {
            return 0L;
        }
        try {
            long value = Long.parseLong(raw);
            if (value < 0L) {
                player.sendMessage(Message.raw("Reward must be 0 or higher."));
                return -1L;
            }
            return value;
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Reward must be a number."));
            return -1L;
        }
    }

    private long parsePrice(Player player) {
        String raw = mapPrice != null ? mapPrice.trim() : "";
        if (raw.isEmpty()) {
            return 0L;
        }
        try {
            long value = Long.parseLong(raw);
            if (value < 0L) {
                player.sendMessage(Message.raw("Price must be 0 or higher."));
                return -1L;
            }
            return value;
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Price must be a number."));
            return -1L;
        }
    }

    private int parseOrder(Player player, int fallback) {
        String raw = mapOrder != null ? mapOrder.trim() : "";
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Order must be a number."));
            return fallback;
        }
    }

    private long parseRobotBaseTime(Player player, long fallback) {
        String raw = mapRobotBaseTime != null ? mapRobotBaseTime.trim() : "";
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            long value = Long.parseLong(raw);
            if (value <= 0L) {
                player.sendMessage(Message.raw("Robot base time must be greater than 0."));
                return -1L;
            }
            return value;
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Robot base time must be a number."));
            return -1L;
        }
    }

    private long parseRobotReduction(Player player, long fallback) {
        String raw = mapRobotReduction != null ? mapRobotReduction.trim() : "";
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            long value = Long.parseLong(raw);
            if (value < 0L) {
                player.sendMessage(Message.raw("Robot reduction must be 0 or higher."));
                return -1L;
            }
            return value;
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Robot reduction must be a number."));
            return -1L;
        }
    }

    private int resolveNextOrder() {
        int max = 0;
        for (AscendMap map : mapStore.listMaps()) {
            max = Math.max(max, map.getDisplayOrder());
        }
        return max + 1;
    }

    private void bindEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapIdField",
            EventData.of(MapData.KEY_MAP_ID, "#MapIdField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapNameField",
            EventData.of(MapData.KEY_MAP_NAME, "#MapNameField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapRewardField",
            EventData.of(MapData.KEY_MAP_REWARD, "#MapRewardField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapPriceField",
            EventData.of(MapData.KEY_MAP_PRICE, "#MapPriceField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapOrderField",
            EventData.of(MapData.KEY_MAP_ORDER, "#MapOrderField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#RobotBaseTimeField",
            EventData.of(MapData.KEY_ROBOT_BASE_TIME, "#RobotBaseTimeField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#RobotReductionField",
            EventData.of(MapData.KEY_ROBOT_REDUCTION, "#RobotReductionField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapSearchField",
            EventData.of(MapData.KEY_MAP_SEARCH, "#MapSearchField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CreateButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_CREATE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#UpdateButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_UPDATE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetStartButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SET_START), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetFinishButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SET_FINISH), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddWaypointButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_ADD_WAYPOINT), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClearWaypointsButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_CLEAR_WAYPOINTS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetPriceButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SET_PRICE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetOrderButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SET_ORDER), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetRobotTimingButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SET_ROBOT_TIMING), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CreateMapHoloButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_CREATE_MAP_HOLO), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteMapHoloButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_DELETE_MAP_HOLO), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_REFRESH), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_CLOSE), false);
    }

    private void populateFields(UICommandBuilder commandBuilder) {
        commandBuilder.set("#MapIdField.Value", mapId != null ? mapId : "");
        commandBuilder.set("#MapNameField.Value", mapName != null ? mapName : "");
        commandBuilder.set("#MapRewardField.Value", mapReward != null ? mapReward : "0");
        commandBuilder.set("#MapPriceField.Value", mapPrice != null ? mapPrice : "0");
        commandBuilder.set("#MapOrderField.Value", mapOrder != null ? mapOrder : "0");
        commandBuilder.set("#RobotBaseTimeField.Value", mapRobotBaseTime != null ? mapRobotBaseTime : "30000");
        commandBuilder.set("#RobotReductionField.Value", mapRobotReduction != null ? mapRobotReduction : "0");
        commandBuilder.set("#MapSearchField.Value", mapSearch != null ? mapSearch : "");
        String selectedText = selectedMapId != null && !selectedMapId.isBlank()
            ? "Selected: " + selectedMapId
            : "Selected: (none)";
        commandBuilder.set("#SelectedMapText.Text", selectedText);
    }

    private void buildMapList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#MapCards");
        List<AscendMap> maps = new ArrayList<>(mapStore.listMaps());
        maps.sort(Comparator.comparingInt(AscendMap::getDisplayOrder)
            .thenComparing(map -> map.getName() != null ? map.getName() : map.getId(),
                String.CASE_INSENSITIVE_ORDER));
        int index = 0;
        for (AscendMap map : maps) {
            String id = map.getId();
            if (mapSearch != null && !mapSearch.isBlank()) {
                String search = mapSearch.trim().toLowerCase();
                String name = map.getName() != null ? map.getName().toLowerCase() : "";
                if (!id.toLowerCase().contains(search) && !name.contains(search)) {
                    continue;
                }
            }
            commandBuilder.append("#MapCards", "Pages/Ascend_MapAdminEntry.ui");
            commandBuilder.set("#MapCards[" + index + "].Background", "$C.@InputBoxBackground");
            String mapNameLabel = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
            boolean isSelected = map.getId().equals(selectedMapId);
            if (isSelected) {
                mapNameLabel = ">> " + mapNameLabel;
                commandBuilder.set("#MapCards[" + index + "].Background", "#253742");
            }
            commandBuilder.set("#MapCards[" + index + "] #MapName.Text", mapNameLabel);
            commandBuilder.set("#MapCards[" + index + "] #MapStatus.Text",
                "Reward: " + map.getBaseReward() + " | Price: " + map.getPrice() + " | Order: " + map.getDisplayOrder());
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#MapCards[" + index + "]",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SELECT_PREFIX + map.getId()), false);
            index++;
        }
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateFields(commandBuilder);
        buildMapList(commandBuilder, eventBuilder);
        bindEvents(eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    public static class MapData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_MAP_ID = "@MapId";
        static final String KEY_MAP_NAME = "@MapName";
        static final String KEY_MAP_REWARD = "@MapReward";
        static final String KEY_MAP_PRICE = "@MapPrice";
        static final String KEY_MAP_ORDER = "@MapOrder";
        static final String KEY_ROBOT_BASE_TIME = "@RobotBaseTime";
        static final String KEY_ROBOT_REDUCTION = "@RobotReduction";
        static final String KEY_MAP_SEARCH = "@MapSearch";
        static final String BUTTON_SELECT_PREFIX = "Select:";
        static final String BUTTON_CREATE = "CreateMap";
        static final String BUTTON_UPDATE = "UpdateMap";
        static final String BUTTON_SET_START = "SetStart";
        static final String BUTTON_SET_FINISH = "SetFinish";
        static final String BUTTON_ADD_WAYPOINT = "AddWaypoint";
        static final String BUTTON_CLEAR_WAYPOINTS = "ClearWaypoints";
        static final String BUTTON_SET_PRICE = "SetPrice";
        static final String BUTTON_SET_ORDER = "SetOrder";
        static final String BUTTON_SET_ROBOT_TIMING = "SetRobotTiming";
        static final String BUTTON_CREATE_MAP_HOLO = "CreateMapHolo";
        static final String BUTTON_DELETE_MAP_HOLO = "DeleteMapHolo";
        static final String BUTTON_REFRESH = "Refresh";
        static final String BUTTON_CLOSE = "Close";

        public static final BuilderCodec<MapData> CODEC = BuilderCodec.<MapData>builder(MapData.class, MapData::new)
            .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, value) -> data.button = value, data -> data.button)
            .addField(new KeyedCodec<>(KEY_MAP_ID, Codec.STRING), (data, value) -> data.mapId = value, data -> data.mapId)
            .addField(new KeyedCodec<>(KEY_MAP_NAME, Codec.STRING), (data, value) -> data.mapName = value, data -> data.mapName)
            .addField(new KeyedCodec<>(KEY_MAP_REWARD, Codec.STRING), (data, value) -> data.mapReward = value, data -> data.mapReward)
            .addField(new KeyedCodec<>(KEY_MAP_PRICE, Codec.STRING), (data, value) -> data.mapPrice = value, data -> data.mapPrice)
            .addField(new KeyedCodec<>(KEY_MAP_ORDER, Codec.STRING), (data, value) -> data.mapOrder = value, data -> data.mapOrder)
            .addField(new KeyedCodec<>(KEY_ROBOT_BASE_TIME, Codec.STRING), (data, value) -> data.mapRobotBaseTime = value, data -> data.mapRobotBaseTime)
            .addField(new KeyedCodec<>(KEY_ROBOT_REDUCTION, Codec.STRING), (data, value) -> data.mapRobotReduction = value, data -> data.mapRobotReduction)
            .addField(new KeyedCodec<>(KEY_MAP_SEARCH, Codec.STRING), (data, value) -> data.mapSearch = value, data -> data.mapSearch)
            .build();

        private String button;
        private String mapId;
        private String mapName;
        private String mapReward;
        private String mapPrice;
        private String mapOrder;
        private String mapRobotBaseTime;
        private String mapRobotReduction;
        private String mapSearch;
    }

    private AscendHologramManager resolveHologramManager(Player player) {
        if (!HylogramsBridge.isAvailable()) {
            if (player != null) {
                player.sendMessage(Message.raw("Hylograms plugin not available."));
            }
            return null;
        }
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        AscendHologramManager manager = plugin != null ? plugin.getHologramManager() : null;
        if (manager == null && player != null) {
            player.sendMessage(Message.raw("Hologram manager not available."));
        }
        return manager;
    }

    private Vector3d resolvePlayerPosition(Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            if (player != null) {
                player.sendMessage(Message.raw("Could not read your position."));
            }
            return null;
        }
        return transform.getPosition();
    }

    private String resolveWorldName(Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        return world != null ? world.getName() : null;
    }

    private void refreshMapHolos(AscendMap map, Store<EntityStore> store) {
        if (!HylogramsBridge.isAvailable()) {
            return;
        }
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        AscendHologramManager manager = plugin != null ? plugin.getHologramManager() : null;
        if (manager != null) {
            manager.refreshMapHolosIfPresent(map, store);
        }
    }
}
