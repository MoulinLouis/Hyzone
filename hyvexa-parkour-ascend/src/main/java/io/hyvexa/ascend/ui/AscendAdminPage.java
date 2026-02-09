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

    private static final String[] LEVEL_COLORS = {"Rouge", "Orange", "Jaune", "Vert", "Bleu"};

    private final AscendMapStore mapStore;
    private String mapId = "";
    private String mapName = "";
    private String mapOrder = "0";
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
        if (data.mapOrder != null) {
            mapOrder = data.mapOrder.trim();
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
                mapOrder = String.valueOf(map.getDisplayOrder());
            }
            sendRefresh(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_CREATE)) {
            handleCreate(ref, store);
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
        if (data.button.equals(MapData.BUTTON_SET_ORDER)) {
            handleSetOrder(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_SET_NAME)) {
            handleSetName(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_CREATE_MAP_HOLO)) {
            handleCreateMapHolo(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_DELETE_MAP_HOLO)) {
            handleDeleteMapHolo(ref, store);
            return;
        }
        // Waypoint system removed - ghost replay now uses recorded player paths
        // if (data.button.equals(MapData.BUTTON_ADD_WAYPOINT)) {
        //     handleAddWaypoint(ref, store);
        //     return;
        // }
        // if (data.button.equals(MapData.BUTTON_CLEAR_WAYPOINTS)) {
        //     handleClearWaypoints(ref, store);
        // }
    }

    private void handleCreate(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        String id = mapId != null ? mapId.trim() : "";
        if (id.isEmpty()) {
            player.sendMessage(Message.raw("Map ID is required."));
            return;
        }
        if (mapStore.getMap(id) != null) {
            player.sendMessage(Message.raw("Map already exists: " + id));
            return;
        }
        String name = mapName != null ? mapName.trim() : "";
        int order = parseOrder(player, resolveNextOrder());
        if (order < 0 || order > 4) {
            player.sendMessage(Message.raw("Order must be 0-4 (Rouge=0, Orange=1, Jaune=2, Vert=3, Bleu=4)."));
            return;
        }
        AscendMap map = new AscendMap();
        map.setId(id);
        map.setName(name.isEmpty() ? id : name);
        map.setDisplayOrder(order);
        World world = store.getExternalData().getWorld();
        map.setWorld(world != null ? world.getName() : "Ascend");
        mapStore.saveMap(map);
        refreshMapHolos(map, store);
        selectedMapId = id;
        String levelColor = order < LEVEL_COLORS.length ? LEVEL_COLORS[order] : "?";
        player.sendMessage(Message.raw("Map created: " + id + " (Level " + order + " - " + levelColor + ")"));
        player.sendMessage(Message.raw("  -> Unlock: " + map.getEffectivePrice() + " vexa"));
        player.sendMessage(Message.raw("  -> Runner: " + map.getEffectiveRobotPrice() + " vexa"));
        player.sendMessage(Message.raw("  -> Reward: " + map.getEffectiveBaseReward() + " vexa/run"));
        player.sendMessage(Message.raw("  -> Run time: " + formatTimeShort(map.getEffectiveBaseRunTimeMs())));
        player.sendMessage(Message.raw("Now use Set Start and Set Finish to configure the parkour."));
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
        Vector3f bodyRot = transform.getRotation();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Vector3f headRot = playerRef != null ? playerRef.getHeadRotation() : null;
        Vector3f rot = headRot != null ? headRot : bodyRot;
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
        player.sendMessage(Message.raw("  Pos: " + String.format("%.2f, %.2f, %.2f", pos.getX(), pos.getY(), pos.getZ())));
        player.sendMessage(Message.raw("  Rot: " + String.format("%.2f, %.2f, %.2f", rot.getX(), rot.getY(), rot.getZ())));
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
        if (order < 0 || order > 4) {
            player.sendMessage(Message.raw("Order must be 0-4 (Rouge=0, Orange=1, Jaune=2, Vert=3, Bleu=4)."));
            return;
        }
        map.setDisplayOrder(order);
        mapStore.saveMap(map);
        refreshMapHolos(map, store);
        String levelColor = order < LEVEL_COLORS.length ? LEVEL_COLORS[order] : "?";
        player.sendMessage(Message.raw("Order updated: " + map.getId() + " -> Level " + order + " (" + levelColor + ")"));
        player.sendMessage(Message.raw("  -> Unlock: " + map.getEffectivePrice() + " vexa"));
        player.sendMessage(Message.raw("  -> Runner: " + map.getEffectiveRobotPrice() + " vexa"));
        player.sendMessage(Message.raw("  -> Reward: " + map.getEffectiveBaseReward() + " vexa/run"));
        player.sendMessage(Message.raw("  -> Run time: " + formatTimeShort(map.getEffectiveBaseRunTimeMs())));
        sendRefresh(ref, store);
    }

    private void handleSetName(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        AscendMap map = resolveSelectedMap(player);
        if (map == null) {
            return;
        }
        String name = mapName != null ? mapName.trim() : "";
        if (name.isEmpty()) {
            player.sendMessage(Message.raw("Name cannot be empty."));
            return;
        }
        map.setName(name);
        mapStore.saveMap(map);
        refreshMapHolos(map, store);
        player.sendMessage(Message.raw("Name updated: " + map.getId() + " -> " + name));
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
            player.sendMessage(Message.raw("Map hologram created for: " + map.getId()));
        } else {
            player.sendMessage(Message.raw("Failed to create map hologram."));
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
            player.sendMessage(Message.raw("Hologram removed for: " + map.getId()));
        } else {
            player.sendMessage(Message.raw("No hologram found for: " + map.getId()));
        }
    }

    // Waypoint system removed - ghost replay now uses recorded player paths
    /*
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
        AscendMap.Waypoint waypoint = new AscendMap.Waypoint(pos.getX(), pos.getY(), pos.getZ(), false, 0);
        List<AscendMap.Waypoint> waypoints = map.getWaypoints();
        if (waypoints == null) {
            waypoints = new ArrayList<>();
            map.setWaypoints(waypoints);
        }
        waypoints.add(waypoint);
        mapStore.saveMap(map);
        int count = waypoints.size();
        player.sendMessage(Message.raw("Waypoint #" + count + " added for map: " + map.getId()));
        player.sendMessage(Message.raw("  Pos: " + String.format("%.2f, %.2f, %.2f", pos.getX(), pos.getY(), pos.getZ())));
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
        List<AscendMap.Waypoint> waypoints = map.getWaypoints();
        int count = waypoints != null ? waypoints.size() : 0;
        if (count == 0) {
            player.sendMessage(Message.raw("No waypoints to clear for map: " + map.getId()));
            return;
        }
        map.setWaypoints(new ArrayList<>());
        mapStore.saveMap(map);
        player.sendMessage(Message.raw("Cleared " + count + " waypoint(s) for map: " + map.getId()));
        sendRefresh(ref, store);
    }
    */

    private AscendMap resolveSelectedMap(Player player) {
        String id = mapId != null && !mapId.isBlank() ? mapId : selectedMapId;
        if (id == null || id.isBlank()) {
            player.sendMessage(Message.raw("Select a map or enter a map ID first."));
            return null;
        }
        AscendMap map = mapStore.getMap(id);
        if (map == null) {
            player.sendMessage(Message.raw("Map not found: " + id));
        }
        return map;
    }

    private int parseOrder(Player player, int fallback) {
        String raw = mapOrder != null ? mapOrder.trim() : "";
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Order must be a number (0-4)."));
            return fallback;
        }
    }

    private int resolveNextOrder() {
        int maxUsed = -1;
        for (AscendMap map : mapStore.listMaps()) {
            maxUsed = Math.max(maxUsed, map.getDisplayOrder());
        }
        return Math.min(maxUsed + 1, 4);
    }

    private void bindEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapIdField",
            EventData.of(MapData.KEY_MAP_ID, "#MapIdField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapNameField",
            EventData.of(MapData.KEY_MAP_NAME, "#MapNameField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapOrderField",
            EventData.of(MapData.KEY_MAP_ORDER, "#MapOrderField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapSearchField",
            EventData.of(MapData.KEY_MAP_SEARCH, "#MapSearchField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CreateButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_CREATE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetStartButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SET_START), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetFinishButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SET_FINISH), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetOrderButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SET_ORDER), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetNameButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SET_NAME), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CreateMapHoloButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_CREATE_MAP_HOLO), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteMapHoloButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_DELETE_MAP_HOLO), false);
        // Waypoint system removed - ghost replay now uses recorded player paths
        // uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddWaypointButton",
        //     EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_ADD_WAYPOINT), false);
        // uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClearWaypointsButton",
        //     EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_CLEAR_WAYPOINTS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_CLOSE), false);
    }

    private void populateFields(UICommandBuilder commandBuilder) {
        commandBuilder.set("#MapIdField.Value", mapId != null ? mapId : "");
        commandBuilder.set("#MapNameField.Value", mapName != null ? mapName : "");
        commandBuilder.set("#MapOrderField.Value", mapOrder != null ? mapOrder : "0");
        commandBuilder.set("#MapSearchField.Value", mapSearch != null ? mapSearch : "");

        String selectedInfo = "No map selected";
        if (selectedMapId != null && !selectedMapId.isBlank()) {
            AscendMap map = mapStore.getMap(selectedMapId);
            if (map != null) {
                int order = map.getDisplayOrder();
                String levelColor = order < LEVEL_COLORS.length ? LEVEL_COLORS[order] : "?";
                selectedInfo = map.getId() + " (Level " + order + " - " + levelColor + ")";
            } else {
                selectedInfo = selectedMapId + " (not found)";
            }
        }
        commandBuilder.set("#SelectedMapText.Text", "Selected: " + selectedInfo);

        String balanceInfo = "";
        String waypointInfo = "No waypoints";
        if (selectedMapId != null && !selectedMapId.isBlank()) {
            AscendMap map = mapStore.getMap(selectedMapId);
            if (map != null) {
                balanceInfo = "Unlock: " + map.getEffectivePrice()
                    + " | Runner: " + map.getEffectiveRobotPrice()
                    + " | Reward: " + map.getEffectiveBaseReward()
                    + " | Time: " + formatTimeShort(map.getEffectiveBaseRunTimeMs());
                // Waypoint system removed - ghost replay now uses recorded player paths
                waypointInfo = "Ghost Replay: Runners follow recorded player paths";
                // List<AscendMap.Waypoint> waypoints = map.getWaypoints();
                // int waypointCount = waypoints != null ? waypoints.size() : 0;
                // if (waypointCount == 0) {
                //     waypointInfo = "No waypoints (runner goes straight)";
                // } else {
                //     waypointInfo = waypointCount + " waypoint" + (waypointCount > 1 ? "s" : "") + " defined";
                // }
            }
        }
        commandBuilder.set("#BalanceInfoText.Text", balanceInfo);
        commandBuilder.set("#WaypointCountText.Text", waypointInfo);

        // Balancing table from AscendConstants
        // Format: Level Color: Unlock / Runner / Reward / Time
        StringBuilder balanceTable = new StringBuilder();
        balanceTable.append("Lv: Unlock / Runner / Reward / Time\n");
        for (int i = 0; i < 5; i++) {
            String color = i < LEVEL_COLORS.length ? LEVEL_COLORS[i] : "Lv" + i;
            long unlock = AscendConstants.getMapUnlockPrice(i);
            long runner = 0L; // Runners are free
            long reward = AscendConstants.getMapBaseReward(i);
            long timeMs = AscendConstants.getMapBaseRunTimeMs(i);
            String timeStr = formatTimeShort(timeMs);
            balanceTable.append(i).append(" ").append(color).append(": ")
                .append(unlock).append(" / ").append(runner).append(" / ").append(reward).append(" / ").append(timeStr);
            if (i < 4) {
                balanceTable.append("\n");
            }
        }
        commandBuilder.set("#BalancingTableText.Text", balanceTable.toString());

        // Multiplier info
        String multiplierInfo = "Manual: 2x runner gain/run\n"
            + "Runner: +" + formatDouble(AscendConstants.RUNNER_MULTIPLIER_INCREMENT) + "/run (base)\n"
            + "Speed upgrade: +10%/level";
        commandBuilder.set("#MultiplierInfoText.Text", multiplierInfo);
    }

    private String formatTimeShort(long ms) {
        if (ms < 1000) return ms + "ms";
        long sec = ms / 1000;
        if (sec < 60) return sec + "s";
        long min = sec / 60;
        sec = sec % 60;
        return sec == 0 ? min + "m" : min + "m" + sec + "s";
    }

    private String formatDouble(double val) {
        if (val == (long) val) return String.valueOf((long) val);
        return String.format("%.2f", val);
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
            String entrySelector = "#MapCards[" + index + "]";
            String mapNameLabel = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
            int order = map.getDisplayOrder();
            String levelColor = order < LEVEL_COLORS.length ? LEVEL_COLORS[order] : "Lv" + order;
            boolean isSelected = map.getId().equals(selectedMapId);
            if (isSelected) {
                mapNameLabel = ">> " + mapNameLabel;
                commandBuilder.set(entrySelector + ".Background", "#253742");
                commandBuilder.set(entrySelector + ".Style.Default.Background", "#253742");
            }
            commandBuilder.set(entrySelector + " #MapName.Text", "[" + levelColor + "] " + mapNameLabel);
            boolean hasStart = map.getStartX() != 0 || map.getStartY() != 0 || map.getStartZ() != 0;
            boolean hasFinish = map.getFinishX() != 0 || map.getFinishY() != 0 || map.getFinishZ() != 0;
            // Waypoint system removed - ghost replay now uses recorded player paths
            // List<AscendMap.Waypoint> waypoints = map.getWaypoints();
            // int waypointCount = waypoints != null ? waypoints.size() : 0;
            String status = "Start: " + (hasStart ? "OK" : "NO") + " | Finish: " + (hasFinish ? "OK" : "NO")
                + " | Ghost Replay";
            commandBuilder.set(entrySelector + " #MapStatus.Text", status);

            // Show balancing info for this map's level
            String balanceStr = "Unlock: " + map.getEffectivePrice()
                + " | Runner: " + map.getEffectiveRobotPrice()
                + " | Reward: " + map.getEffectiveBaseReward()
                + " | Time: " + formatTimeShort(map.getEffectiveBaseRunTimeMs());
            commandBuilder.set(entrySelector + " #MapBalance.Text", balanceStr);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                entrySelector,
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
        static final String KEY_MAP_ORDER = "@MapOrder";
        static final String KEY_MAP_SEARCH = "@MapSearch";
        static final String BUTTON_SELECT_PREFIX = "Select:";
        static final String BUTTON_CREATE = "CreateMap";
        static final String BUTTON_SET_START = "SetStart";
        static final String BUTTON_SET_FINISH = "SetFinish";
        static final String BUTTON_SET_ORDER = "SetOrder";
        static final String BUTTON_SET_NAME = "SetName";
        static final String BUTTON_CREATE_MAP_HOLO = "CreateMapHolo";
        static final String BUTTON_DELETE_MAP_HOLO = "DeleteMapHolo";
        static final String BUTTON_ADD_WAYPOINT = "AddWaypoint";
        static final String BUTTON_CLEAR_WAYPOINTS = "ClearWaypoints";
        static final String BUTTON_CLOSE = "Close";

        public static final BuilderCodec<MapData> CODEC = BuilderCodec.<MapData>builder(MapData.class, MapData::new)
            .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, value) -> data.button = value, data -> data.button)
            .addField(new KeyedCodec<>(KEY_MAP_ID, Codec.STRING), (data, value) -> data.mapId = value, data -> data.mapId)
            .addField(new KeyedCodec<>(KEY_MAP_NAME, Codec.STRING), (data, value) -> data.mapName = value, data -> data.mapName)
            .addField(new KeyedCodec<>(KEY_MAP_ORDER, Codec.STRING), (data, value) -> data.mapOrder = value, data -> data.mapOrder)
            .addField(new KeyedCodec<>(KEY_MAP_SEARCH, Codec.STRING), (data, value) -> data.mapSearch = value, data -> data.mapSearch)
            .build();

        private String button;
        private String mapId;
        private String mapName;
        private String mapOrder;
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
