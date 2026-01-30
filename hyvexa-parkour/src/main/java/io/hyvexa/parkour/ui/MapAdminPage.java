package io.hyvexa.parkour.ui;

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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.HylogramsBridge;
import io.hyvexa.parkour.ParkourConstants;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.TransformData;

import javax.annotation.Nonnull;
import java.util.List;

public class MapAdminPage extends InteractiveCustomUIPage<MapAdminPage.MapData> {

    private final MapStore mapStore;
    private String mapId = "";
    private String mapName = "";
    private String mapCategory = "";
    private String mapFirstCompletionXp = String.valueOf(ParkourConstants.MAP_XP_EASY);
    private String mapDifficulty = "0";
    private String mapOrder = String.valueOf(ParkourConstants.DEFAULT_MAP_ORDER);
    private String mapSearch = "";
    private boolean mapMithrilSwordEnabled = false;
    private boolean mapMithrilDaggersEnabled = false;
    private boolean mapFreeFallEnabled = false;
    private boolean mapDuelEnabled = false;
    private String selectedMapId = "";

    public MapAdminPage(@Nonnull PlayerRef playerRef, MapStore mapStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, MapData.CODEC);
        this.mapStore = mapStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_MapAdmin.ui");
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
        if (data.mapCategory != null) {
            mapCategory = data.mapCategory.trim();
        }
        if (data.mapFirstCompletionXp != null) {
            mapFirstCompletionXp = data.mapFirstCompletionXp.trim();
        }
        if (data.mapDifficulty != null) {
            mapDifficulty = data.mapDifficulty.trim();
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
        if (data.button.equals(MapData.BUTTON_BACK)) {
            openIndex(ref, store);
            return;
        }
        if (data.button.startsWith(MapData.BUTTON_SELECT_PREFIX)) {
            selectedMapId = data.button.substring(MapData.BUTTON_SELECT_PREFIX.length());
            Map map = mapStore.getMap(selectedMapId);
            if (map != null) {
                mapId = map.getId();
                mapName = map.getName() != null ? map.getName() : "";
                mapCategory = map.getCategory() != null ? map.getCategory() : "";
                mapFirstCompletionXp = String.valueOf(map.getFirstCompletionXp());
                mapDifficulty = String.valueOf(map.getDifficulty());
                mapOrder = String.valueOf(map.getOrder());
                mapMithrilSwordEnabled = map.isMithrilSwordEnabled();
                mapMithrilDaggersEnabled = map.isMithrilDaggersEnabled();
                mapFreeFallEnabled = map.isFreeFallEnabled();
                mapDuelEnabled = map.isDuelEnabled();
            }
            sendRefresh(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_CREATE)) {
            handleCreate(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_SET_START)) {
            handleMarker(ref, store, true);
            return;
        }
        if (data.button.equals(MapData.BUTTON_SET_FINISH)) {
            handleMarker(ref, store, false);
            return;
        }
        if (data.button.equals(MapData.BUTTON_SET_START_TRIGGER)) {
            handleTrigger(ref, store, true);
            return;
        }
        if (data.button.equals(MapData.BUTTON_SET_LEAVE_TRIGGER)) {
            handleTrigger(ref, store, false);
            return;
        }
        if (data.button.equals(MapData.BUTTON_SET_LEAVE_TELEPORT)) {
            handleLeaveTeleport(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_ADD_CHECKPOINT)) {
            handleCheckpoint(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_UPDATE)) {
            handleUpdate(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_DELETE)) {
            handleDelete(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_REFRESH)) {
            sendRefresh(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_CREATE_HOLO)) {
            handleCreateHologram(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_TOGGLE_MITHRIL_SWORD)) {
            mapMithrilSwordEnabled = !mapMithrilSwordEnabled;
            sendRefresh(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_TOGGLE_MITHRIL_DAGGERS)) {
            mapMithrilDaggersEnabled = !mapMithrilDaggersEnabled;
            sendRefresh(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_TOGGLE_FREE_FALL)) {
            mapFreeFallEnabled = !mapFreeFallEnabled;
            sendRefresh(ref, store);
            return;
        }
        if (data.button.equals(MapData.BUTTON_TOGGLE_DUEL_ENABLED)) {
            mapDuelEnabled = !mapDuelEnabled;
            sendRefresh(ref, store);
            return;
        }
    }

    private void handleCreate(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (mapId.isEmpty()) {
            player.sendMessage(Message.raw("Map id is required."));
            return;
        }
        if (mapStore.hasMap(mapId)) {
            player.sendMessage(Message.raw("Map '" + mapId + "' already exists."));
            return;
        }
        TransformData start = readTransform(ref, store);
        if (start == null) {
            player.sendMessage(Message.raw("Could not read your position."));
            return;
        }
        Map map = new Map();
        map.setId(mapId);
        map.setName(mapName.isEmpty() ? mapId : mapName);
        map.setCategory(mapCategory.isEmpty() ? ParkourConstants.DEFAULT_CATEGORY : mapCategory);
        map.setWorld(player.getWorld() != null ? player.getWorld().getName() : "Unknown");
        Long firstCompletionXp = parseFirstCompletionXp(player);
        if (firstCompletionXp == null) {
            return;
        }
        map.setFirstCompletionXp(firstCompletionXp);
        Integer difficulty = parseMapDifficulty(player);
        if (difficulty == null) {
            return;
        }
        map.setDifficulty(difficulty);
        Integer order = parseMapOrder(player);
        if (order == null) {
            return;
        }
        map.setOrder(order);
        map.setMithrilSwordEnabled(mapMithrilSwordEnabled);
        map.setMithrilDaggersEnabled(mapMithrilDaggersEnabled);
        map.setFreeFallEnabled(mapFreeFallEnabled);
        map.setDuelEnabled(mapDuelEnabled);
        map.setStart(start);
        map.setCreatedAt(System.currentTimeMillis());
        map.setUpdatedAt(map.getCreatedAt());
        try {
            mapStore.addMap(map);
            selectedMapId = mapId;
            player.sendMessage(Message.raw("Created map '" + mapId + "'."));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Message.raw(exception.getMessage()));
        }
        sendRefresh(ref, store);
    }

    private void handleMarker(Ref<EntityStore> ref, Store<EntityStore> store, boolean isStart) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        Map map = mapStore.getMap(selectedMapId);
        if (map == null) {
            player.sendMessage(Message.raw("Select a map first."));
            return;
        }
        TransformData marker = readTransform(ref, store);
        if (marker == null) {
            player.sendMessage(Message.raw("Could not read your position."));
            return;
        }
        if (isStart) {
            map.setStart(marker);
        } else {
            map.setFinish(marker);
        }
        map.setWorld(player.getWorld() != null ? player.getWorld().getName() : map.getWorld());
        map.setUpdatedAt(System.currentTimeMillis());
        try {
            mapStore.updateMap(map);
            player.sendMessage(Message.raw("Updated " + (isStart ? "start" : "finish") + " for '" + map.getId() + "'."));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Message.raw(exception.getMessage()));
        }
        sendRefresh(ref, store);
    }

    private void handleCheckpoint(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        Map map = mapStore.getMap(selectedMapId);
        if (map == null) {
            player.sendMessage(Message.raw("Select a map first."));
            return;
        }
        TransformData checkpoint = readTransform(ref, store);
        if (checkpoint == null) {
            player.sendMessage(Message.raw("Could not read your position."));
            return;
        }
        map.getCheckpoints().add(checkpoint);
        map.setWorld(player.getWorld() != null ? player.getWorld().getName() : map.getWorld());
        map.setUpdatedAt(System.currentTimeMillis());
        try {
            mapStore.updateMap(map);
            player.sendMessage(Message.raw("Added checkpoint #" + map.getCheckpoints().size()
                    + " to '" + map.getId() + "'."));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Message.raw(exception.getMessage()));
        }
        sendRefresh(ref, store);
    }

    private void handleTrigger(Ref<EntityStore> ref, Store<EntityStore> store, boolean isStartTrigger) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        Map map = mapStore.getMap(selectedMapId);
        if (map == null) {
            player.sendMessage(Message.raw("Select a map first."));
            return;
        }
        TransformData trigger = readTransform(ref, store);
        if (trigger == null) {
            player.sendMessage(Message.raw("Could not read your position."));
            return;
        }
        if (isStartTrigger) {
            map.setStartTrigger(trigger);
        } else {
            map.setLeaveTrigger(trigger);
        }
        map.setWorld(player.getWorld() != null ? player.getWorld().getName() : map.getWorld());
        map.setUpdatedAt(System.currentTimeMillis());
        try {
            mapStore.updateMap(map);
            player.sendMessage(Message.raw("Updated " + (isStartTrigger ? "start trigger" : "leave trigger")
                    + " for '" + map.getId() + "'."));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Message.raw(exception.getMessage()));
        }
        sendRefresh(ref, store);
    }

    private void handleLeaveTeleport(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        Map map = mapStore.getMap(selectedMapId);
        if (map == null) {
            player.sendMessage(Message.raw("Select a map first."));
            return;
        }
        TransformData teleport = readTransform(ref, store);
        if (teleport == null) {
            player.sendMessage(Message.raw("Could not read your position."));
            return;
        }
        map.setLeaveTeleport(teleport);
        map.setWorld(player.getWorld() != null ? player.getWorld().getName() : map.getWorld());
        map.setUpdatedAt(System.currentTimeMillis());
        try {
            mapStore.updateMap(map);
            player.sendMessage(Message.raw("Updated leave teleport for '" + map.getId() + "'."));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Message.raw(exception.getMessage()));
        }
        sendRefresh(ref, store);
    }

    private void handleUpdate(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (selectedMapId.isEmpty()) {
            player.sendMessage(Message.raw("Select a map first."));
            return;
        }
        Map map = mapStore.getMap(selectedMapId);
        if (map == null) {
            player.sendMessage(Message.raw("Map '" + selectedMapId + "' not found."));
            return;
        }
        map.setName(mapName.isEmpty() ? map.getId() : mapName);
        map.setCategory(mapCategory.isEmpty() ? ParkourConstants.DEFAULT_CATEGORY : mapCategory);
        Long firstCompletionXp = parseFirstCompletionXp(player);
        if (firstCompletionXp == null) {
            return;
        }
        map.setFirstCompletionXp(firstCompletionXp);
        Integer difficulty = parseMapDifficulty(player);
        if (difficulty == null) {
            return;
        }
        map.setDifficulty(difficulty);
        Integer order = parseMapOrder(player);
        if (order == null) {
            return;
        }
        map.setOrder(order);
        map.setMithrilSwordEnabled(mapMithrilSwordEnabled);
        map.setMithrilDaggersEnabled(mapMithrilDaggersEnabled);
        map.setFreeFallEnabled(mapFreeFallEnabled);
        map.setDuelEnabled(mapDuelEnabled);
        map.setUpdatedAt(System.currentTimeMillis());
        try {
            mapStore.updateMap(map);
            player.sendMessage(Message.raw("Updated map '" + map.getId() + "'."));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Message.raw(exception.getMessage()));
        }
        sendRefresh(ref, store);
    }

    private void handleDelete(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (selectedMapId.isEmpty()) {
            player.sendMessage(Message.raw("Select a map first."));
            return;
        }
        boolean removed = mapStore.removeMap(selectedMapId);
        if (!removed) {
            player.sendMessage(Message.raw("Map '" + selectedMapId + "' not found."));
            return;
        }
        player.sendMessage(Message.raw("Deleted map '" + selectedMapId + "'."));
        selectedMapId = "";
        sendRefresh(ref, store);
    }

    private void handleCreateHologram(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (selectedMapId.isEmpty()) {
            player.sendMessage(Message.raw("Select a map first."));
            return;
        }
        Map map = mapStore.getMap(selectedMapId);
        if (map == null) {
            player.sendMessage(Message.raw("Map '" + selectedMapId + "' not found."));
            return;
        }
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        ProgressStore progressStore = plugin != null ? plugin.getProgressStore() : null;
        if (progressStore == null) {
            player.sendMessage(Message.raw("Progress store not available."));
            return;
        }
        if (!HylogramsBridge.isAvailable()) {
            player.sendMessage(Message.raw("Hylograms plugin not available."));
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            player.sendMessage(Message.raw("Could not read your position."));
            return;
        }
        String holoName = map.getId() + "_holo";
        if (plugin == null) {
            player.sendMessage(Message.raw("Plugin not available."));
            return;
        }
        List<String> lines = plugin.buildMapLeaderboardHologramLines(map.getId());
        if (HylogramsBridge.exists(holoName)) {
            HylogramsBridge.Hologram holo = HylogramsBridge.get(holoName);
            if (holo != null) {
                holo.moveTo(transform.getPosition()).respawn(store).save(store);
            }
            HylogramsBridge.updateHologramLines(holoName, lines, store);
            player.sendMessage(Message.raw("Updated hologram '" + holoName + "'."));
            return;
        }
        HylogramsBridge.HologramBuilder builder = HylogramsBridge.create(holoName, store);
        if (builder == null) {
            player.sendMessage(Message.raw("Failed to create hologram."));
            return;
        }
        if (player.getWorld() != null) {
            builder.inWorld(player.getWorld().getName());
        }
        builder.at(transform.getPosition());
        for (String line : lines) {
            builder.addLine(line);
        }
        HylogramsBridge.Hologram holo = builder.spawn();
        if (holo != null) {
            holo.save(store);
        }
        player.sendMessage(Message.raw("Created hologram '" + holoName + "'."));
    }

    private void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        ProgressStore progressStore = HyvexaPlugin.getInstance().getProgressStore();
        player.getPageManager().openCustomPage(ref, store,
                new AdminIndexPage(playerRef, mapStore, progressStore, HyvexaPlugin.getInstance().getSettingsStore(),
                        HyvexaPlugin.getInstance().getPlayerCountStore()));
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateFields(commandBuilder);
        buildMapList(commandBuilder, eventBuilder);
        bindEvents(eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void populateFields(UICommandBuilder commandBuilder) {
        commandBuilder.set("#MapIdField.Value", mapId);
        commandBuilder.set("#MapNameField.Value", mapName);
        commandBuilder.set("#MapCategoryField.Value", mapCategory);
        commandBuilder.set("#MapFirstCompletionXpField.Value", mapFirstCompletionXp);
        commandBuilder.set("#MapDifficultyField.Value", mapDifficulty);
        commandBuilder.set("#MapOrderField.Value", mapOrder);
        commandBuilder.set("#MapSearchField.Value", mapSearch);
        commandBuilder.set("#MithrilSwordValue.Text", mapMithrilSwordEnabled ? "Enabled" : "Disabled");
        commandBuilder.set("#MithrilDaggersValue.Text", mapMithrilDaggersEnabled ? "Enabled" : "Disabled");
        commandBuilder.set("#FreeFallValue.Text", mapFreeFallEnabled ? "YES" : "NO");
        commandBuilder.set("#DuelEnabledValue.Text", mapDuelEnabled ? "YES" : "NO");
        String selectedText = "Selected: (none)";
        if (!selectedMapId.isEmpty()) {
            Map map = mapStore.getMap(selectedMapId);
            String categoryLabel = map != null
                    ? FormatUtils.normalizeCategory(map.getCategory())
                    : ParkourConstants.DEFAULT_CATEGORY;
            selectedText = "Selected: " + selectedMapId + " (Category: " + categoryLabel + ")";
        }
        commandBuilder.set("#SelectedMapText.Text", selectedText);
    }

    private void buildMapList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#MapCards");
        String filter = mapSearch != null ? mapSearch.trim().toLowerCase() : "";
        List<Map> maps = mapStore.listMaps();
        int index = 0;
        for (Map map : maps) {
            String displayName = map.getName() != null ? map.getName() : map.getId();
            String displayLower = displayName != null ? displayName.toLowerCase() : "";
            if (!filter.isEmpty() && !displayLower.startsWith(filter)) {
                continue;
            }
            commandBuilder.append("#MapCards", "Pages/Parkour_MapEntry.ui");
            commandBuilder.set("#MapCards[" + index + "].Background", "$C.@InputBoxBackground");
            commandBuilder.set("#MapCards[" + index + "] #MapName.Text", displayName);
            boolean isSelected = map.getId().equals(selectedMapId);
            if (isSelected) {
                commandBuilder.set("#MapCards[" + index + "] #MapName.Text", ">> " + displayName);
                commandBuilder.set("#MapCards[" + index + "].Background", "#253742");
            }
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#MapCards[" + index + "]",
                    EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SELECT_PREFIX + map.getId()), false);
            index++;
        }
    }

    private void bindEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapIdField",
                EventData.of(MapData.KEY_MAP_ID, "#MapIdField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapNameField",
                EventData.of(MapData.KEY_MAP_NAME, "#MapNameField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapCategoryField",
                EventData.of(MapData.KEY_MAP_CATEGORY, "#MapCategoryField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapFirstCompletionXpField",
                EventData.of(MapData.KEY_MAP_FIRST_COMPLETION_XP, "#MapFirstCompletionXpField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapDifficultyField",
                EventData.of(MapData.KEY_MAP_DIFFICULTY, "#MapDifficultyField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapOrderField",
                EventData.of(MapData.KEY_MAP_ORDER, "#MapOrderField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapSearchField",
                EventData.of(MapData.KEY_MAP_SEARCH, "#MapSearchField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CreateMapButton",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_CREATE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetStartButton",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SET_START), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetFinishButton",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SET_FINISH), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetStartTriggerButton",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SET_START_TRIGGER), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetLeaveTriggerButton",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SET_LEAVE_TRIGGER), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetLeaveTeleportButton",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_SET_LEAVE_TELEPORT), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddCheckpointButton",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_ADD_CHECKPOINT), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#UpdateMapButton",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_UPDATE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteMapButton",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_DELETE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshButton",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_REFRESH), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CreateHoloButton",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_CREATE_HOLO), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MithrilSwordToggle",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_TOGGLE_MITHRIL_SWORD), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MithrilDaggersToggle",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_TOGGLE_MITHRIL_DAGGERS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FreeFallToggle",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_TOGGLE_FREE_FALL), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DuelEnabledToggle",
                EventData.of(MapData.KEY_BUTTON, MapData.BUTTON_TOGGLE_DUEL_ENABLED), false);
    }

    private static TransformData readTransform(Ref<EntityStore> ref, Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return null;
        }
        Vector3d position = transform.getPosition();
        Vector3f rotation = transform.getRotation();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Vector3f headRotation = playerRef != null ? playerRef.getHeadRotation() : null;
        Vector3f useRotation = headRotation != null ? headRotation : rotation;
        TransformData data = new TransformData();
        data.setX(position.getX());
        data.setY(position.getY());
        data.setZ(position.getZ());
        data.setRotX(useRotation.getX());
        data.setRotY(useRotation.getY());
        data.setRotZ(useRotation.getZ());
        return data;
    }

    private Long parseFirstCompletionXp(Player player) {
        String raw = mapFirstCompletionXp != null ? mapFirstCompletionXp.trim() : "";
        if (raw.isEmpty()) {
            return ProgressStore.getCategoryXp(mapCategory);
        }
        try {
            long value = Long.parseLong(raw);
            if (value < 0L) {
                player.sendMessage(Message.raw("First completion XP must be 0 or higher."));
                return null;
            }
            if (value > 100_000L) {
                player.sendMessage(Message.raw("First completion XP cannot exceed 100,000."));
                return null;
            }
            return value;
        } catch (NumberFormatException ignored) {
            player.sendMessage(Message.raw("First completion XP must be a number."));
            return null;
        }
    }

    private Integer parseMapOrder(Player player) {
        String raw = mapOrder != null ? mapOrder.trim() : "";
        if (raw.isEmpty()) {
            return ParkourConstants.DEFAULT_MAP_ORDER;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                player.sendMessage(Message.raw("Order must be 0 or higher."));
                return null;
            }
            return value;
        } catch (NumberFormatException ignored) {
            player.sendMessage(Message.raw("Order must be a number."));
            return null;
        }
    }

    private Integer parseMapDifficulty(Player player) {
        String raw = mapDifficulty != null ? mapDifficulty.trim() : "";
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                player.sendMessage(Message.raw("Difficulty must be 0 or higher."));
                return null;
            }
            if (value > 1_000_000) {
                player.sendMessage(Message.raw("Difficulty cannot exceed 1,000,000."));
                return null;
            }
            return value;
        } catch (NumberFormatException ignored) {
            player.sendMessage(Message.raw("Difficulty must be a number."));
            return null;
        }
    }

    public static class MapData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_MAP_ID = "@MapId";
        static final String KEY_MAP_NAME = "@MapName";
        static final String KEY_MAP_CATEGORY = "@MapCategory";
        static final String KEY_MAP_FIRST_COMPLETION_XP = "@MapFirstCompletionXp";
        static final String KEY_MAP_DIFFICULTY = "@MapDifficulty";
        static final String KEY_MAP_ORDER = "@MapOrder";
        static final String KEY_MAP_SEARCH = "@MapSearch";
        static final String BUTTON_SELECT_PREFIX = "Select:";
        static final String BUTTON_BACK = "BackButton";
        static final String BUTTON_CREATE = "CreateMap";
        static final String BUTTON_SET_START = "SetStart";
        static final String BUTTON_SET_FINISH = "SetFinish";
        static final String BUTTON_SET_START_TRIGGER = "SetStartTrigger";
        static final String BUTTON_SET_LEAVE_TRIGGER = "SetLeaveTrigger";
        static final String BUTTON_SET_LEAVE_TELEPORT = "SetLeaveTeleport";
        static final String BUTTON_ADD_CHECKPOINT = "AddCheckpoint";
        static final String BUTTON_UPDATE = "UpdateMap";
        static final String BUTTON_DELETE = "DeleteMap";
        static final String BUTTON_REFRESH = "Refresh";
        static final String BUTTON_CREATE_HOLO = "CreateHolo";
        static final String BUTTON_TOGGLE_MITHRIL_SWORD = "ToggleMithrilSword";
        static final String BUTTON_TOGGLE_MITHRIL_DAGGERS = "ToggleMithrilDaggers";
        static final String BUTTON_TOGGLE_FREE_FALL = "ToggleFreeFall";
        static final String BUTTON_TOGGLE_DUEL_ENABLED = "ToggleDuelEnabled";

        public static final BuilderCodec<MapData> CODEC = BuilderCodec.<MapData>builder(MapData.class, MapData::new)
                .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, value) -> data.button = value, data -> data.button)
                .addField(new KeyedCodec<>(KEY_MAP_ID, Codec.STRING), (data, value) -> data.mapId = value, data -> data.mapId)
                .addField(new KeyedCodec<>(KEY_MAP_NAME, Codec.STRING), (data, value) -> data.mapName = value, data -> data.mapName)
                .addField(new KeyedCodec<>(KEY_MAP_CATEGORY, Codec.STRING), (data, value) -> data.mapCategory = value, data -> data.mapCategory)
                .addField(new KeyedCodec<>(KEY_MAP_FIRST_COMPLETION_XP, Codec.STRING),
                        (data, value) -> data.mapFirstCompletionXp = value, data -> data.mapFirstCompletionXp)
                .addField(new KeyedCodec<>(KEY_MAP_DIFFICULTY, Codec.STRING),
                        (data, value) -> data.mapDifficulty = value, data -> data.mapDifficulty)
                .addField(new KeyedCodec<>(KEY_MAP_ORDER, Codec.STRING), (data, value) -> data.mapOrder = value, data -> data.mapOrder)
                .addField(new KeyedCodec<>(KEY_MAP_SEARCH, Codec.STRING), (data, value) -> data.mapSearch = value, data -> data.mapSearch)
                .build();

        private String button;
        private String mapId;
        private String mapName;
        private String mapCategory;
        private String mapFirstCompletionXp;
        private String mapDifficulty;
        private String mapOrder;
        private String mapSearch;
    }
}
