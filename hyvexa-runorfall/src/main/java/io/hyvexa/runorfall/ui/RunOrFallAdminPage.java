package io.hyvexa.runorfall.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.runorfall.data.BlockSelection;
import io.hyvexa.runorfall.data.RunOrFallLocation;
import io.hyvexa.runorfall.data.RunOrFallPlatform;
import io.hyvexa.runorfall.manager.RunOrFallConfigStore;
import io.hyvexa.runorfall.manager.RunOrFallGameManager;
import io.hyvexa.runorfall.util.RunOrFallUtils;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RunOrFallAdminPage extends InteractiveCustomUIPage<RunOrFallAdminPage.AdminData> {
    private static final String PREFIX = "[RunOrFall] ";

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_REFRESH = "Refresh";
    private static final String BUTTON_SET_LOBBY = "SetLobby";
    private static final String BUTTON_TP_LOBBY = "TpLobby";
    private static final String BUTTON_ADD_SPAWN = "AddSpawn";
    private static final String BUTTON_CLEAR_SPAWNS = "ClearSpawns";
    private static final String BUTTON_SET_POS1 = "SetPos1";
    private static final String BUTTON_SET_POS2 = "SetPos2";
    private static final String BUTTON_SAVE_PLATFORM = "SavePlatform";
    private static final String BUTTON_CLEAR_PLATFORMS = "ClearPlatforms";
    private static final String BUTTON_SAVE_VOIDY = "SaveVoidY";
    private static final String BUTTON_SAVE_BREAK_DELAY = "SaveBreakDelay";
    private static final String BUTTON_SAVE_BLINK_DISTANCE = "SaveBlinkDistance";
    private static final String BUTTON_SAVE_BLINK_CHARGE_SETTINGS = "SaveBlinkChargeSettings";
    private static final String BUTTON_SAVE_AUTO_START = "SaveAutoStart";
    private static final String BUTTON_START = "Start";
    private static final String BUTTON_STOP = "Stop";
    private static final String BUTTON_JOIN = "Join";
    private static final String BUTTON_LEAVE = "Leave";
    private static final String BUTTON_MAP_CREATE = "MapCreate";
    private static final String BUTTON_MAP_DELETE = "MapDelete";
    private static final String BUTTON_MAP_SELECT_PREFIX = "MapSelect:";

    private static final Map<UUID, BlockSelection> SELECTIONS = new ConcurrentHashMap<>();

    public static void clearSelection(UUID playerId) {
        if (playerId != null) {
            SELECTIONS.remove(playerId);
        }
    }

    private final RunOrFallConfigStore configStore;
    private final RunOrFallGameManager gameManager;
    private String mapIdInput;
    private String mapSearchInput;
    private String voidYInput;
    private String breakDelayInput;
    private String blinkDistanceInput;
    private String blinkStartChargesInput;
    private String blinkChargeEveryBlocksInput;
    private String platformBlockIdInput;
    private String minPlayersInput;
    private String minPlayersTimeInput;
    private String optimalPlayersInput;
    private String optimalPlayersTimeInput;

    public RunOrFallAdminPage(@Nonnull PlayerRef playerRef,
                              RunOrFallConfigStore configStore,
                              RunOrFallGameManager gameManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, AdminData.CODEC);
        this.configStore = configStore;
        this.gameManager = gameManager;
        this.mapIdInput = "";
        this.mapSearchInput = "";
        this.voidYInput = formatDouble(configStore.getVoidY());
        this.breakDelayInput = formatDouble(configStore.getBlockBreakDelaySeconds());
        this.blinkDistanceInput = Integer.toString(configStore.getBlinkDistanceBlocks());
        this.blinkStartChargesInput = Integer.toString(configStore.getBlinkStartCharges());
        this.blinkChargeEveryBlocksInput = Integer.toString(configStore.getBlinkChargeEveryBlocksBroken());
        this.platformBlockIdInput = "";
        this.minPlayersInput = Integer.toString(configStore.getMinPlayers());
        this.minPlayersTimeInput = Integer.toString(configStore.getMinPlayersTimeSeconds());
        this.optimalPlayersInput = Integer.toString(configStore.getOptimalPlayers());
        this.optimalPlayersTimeInput = Integer.toString(configStore.getOptimalPlayersTimeSeconds());
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/RunOrFall_Admin.ui");
        bindEvents(eventBuilder);
        populateFields(ref, store, commandBuilder);
        buildMapList(commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull AdminData data) {
        super.handleDataEvent(ref, store, data);
        String previousMapSearch = mapSearchInput;
        if (data.mapId != null) {
            mapIdInput = data.mapId.trim();
        }
        if (data.mapSearch != null) {
            mapSearchInput = data.mapSearch.trim();
        }
        if (data.voidY != null) {
            voidYInput = data.voidY.trim();
        }
        if (data.breakDelay != null) {
            breakDelayInput = data.breakDelay.trim();
        }
        if (data.blinkDistance != null) {
            blinkDistanceInput = data.blinkDistance.trim();
        }
        if (data.blinkStartCharges != null) {
            blinkStartChargesInput = data.blinkStartCharges.trim();
        }
        if (data.blinkChargeEveryBlocks != null) {
            blinkChargeEveryBlocksInput = data.blinkChargeEveryBlocks.trim();
        }
        if (data.platformBlockId != null) {
            platformBlockIdInput = data.platformBlockId.trim();
        }
        if (data.minPlayers != null) {
            minPlayersInput = data.minPlayers.trim();
        }
        if (data.minPlayersTime != null) {
            minPlayersTimeInput = data.minPlayersTime.trim();
        }
        if (data.optimalPlayers != null) {
            optimalPlayersInput = data.optimalPlayers.trim();
        }
        if (data.optimalPlayersTime != null) {
            optimalPlayersTimeInput = data.optimalPlayersTime.trim();
        }
        if (data.button == null) {
            if (previousMapSearch == null) {
                previousMapSearch = "";
            }
            if (mapSearchInput == null) {
                mapSearchInput = "";
            }
            if (!previousMapSearch.equals(mapSearchInput)) {
                sendRefresh(ref, store);
            }
            return;
        }
        if (data.button.startsWith(BUTTON_MAP_SELECT_PREFIX)) {
            String mapId = data.button.substring(BUTTON_MAP_SELECT_PREFIX.length());
            if (!mapId.isBlank()) {
                configStore.selectMap(mapId);
            }
            sendRefresh(ref, store);
            return;
        }
        switch (data.button) {
            case BUTTON_CLOSE -> this.close();
            case BUTTON_REFRESH -> sendRefresh(ref, store);
            case BUTTON_SET_LOBBY -> handleSetLobby(ref, store);
            case BUTTON_TP_LOBBY -> handleTeleportLobby(ref, store);
            case BUTTON_ADD_SPAWN -> handleAddSpawn(ref, store);
            case BUTTON_CLEAR_SPAWNS -> handleClearSpawns(ref, store);
            case BUTTON_SET_POS1 -> handleSetPos1(ref, store);
            case BUTTON_SET_POS2 -> handleSetPos2(ref, store);
            case BUTTON_SAVE_PLATFORM -> handleSavePlatform(ref, store);
            case BUTTON_CLEAR_PLATFORMS -> handleClearPlatforms(ref, store);
            case BUTTON_SAVE_VOIDY -> handleSaveVoidY(ref, store);
            case BUTTON_SAVE_BREAK_DELAY -> handleSaveBreakDelay(ref, store);
            case BUTTON_SAVE_BLINK_DISTANCE -> handleSaveBlinkDistance(ref, store);
            case BUTTON_SAVE_BLINK_CHARGE_SETTINGS -> handleSaveBlinkChargeSettings(ref, store);
            case BUTTON_SAVE_AUTO_START -> handleSaveAutoStart(ref, store);
            case BUTTON_START -> handleStart(ref, store);
            case BUTTON_STOP -> handleStop(ref, store);
            case BUTTON_JOIN -> handleJoin(ref, store);
            case BUTTON_LEAVE -> handleLeave(ref, store);
            case BUTTON_MAP_CREATE -> handleCreateMap(ref, store);
            case BUTTON_MAP_DELETE -> handleDeleteMap(ref, store);
            default -> {
            }
        }
    }

    private void handleSetLobby(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        if (player == null) {
            return;
        }
        RunOrFallLocation location = RunOrFallUtils.readLocation(ref, store);
        if (location == null) {
            player.sendMessage(Message.raw(PREFIX + "Could not read your position."));
            return;
        }
        configStore.setLobby(location);
        player.sendMessage(Message.raw(PREFIX + "Lobby set."));
        sendRefresh(ref, store);
    }

    private void handleCreateMap(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        if (player == null) {
            return;
        }
        if (mapIdInput == null || mapIdInput.isBlank()) {
            player.sendMessage(Message.raw(PREFIX + "Map id is required."));
            return;
        }
        boolean created = configStore.createMap(mapIdInput);
        if (!created) {
            player.sendMessage(Message.raw(PREFIX + "Could not create map."));
            return;
        }
        configStore.selectMap(mapIdInput);
        player.sendMessage(Message.raw(PREFIX + "Map created and selected."));
        sendRefresh(ref, store);
    }

    private void handleDeleteMap(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        if (player == null) {
            return;
        }
        String selectedMapId = configStore.getSelectedMapId();
        if (selectedMapId == null || selectedMapId.isBlank()) {
            player.sendMessage(Message.raw(PREFIX + "No selected map."));
            return;
        }
        boolean deleted = configStore.deleteMap(selectedMapId);
        player.sendMessage(Message.raw(PREFIX + (deleted ? "Map deleted." : "Could not delete map.")));
        sendRefresh(ref, store);
    }

    private void handleTeleportLobby(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        World world = resolveWorld(store);
        if (player == null || world == null) {
            return;
        }
        RunOrFallLocation lobby = configStore.getLobby();
        if (lobby == null) {
            player.sendMessage(Message.raw(PREFIX + "Lobby is not configured."));
            return;
        }
        store.addComponent(ref, Teleport.getComponentType(),
                new Teleport(world,
                        new Vector3d(lobby.x, lobby.y, lobby.z),
                        new Vector3f(lobby.rotX, lobby.rotY, lobby.rotZ)));
        player.sendMessage(Message.raw(PREFIX + "Teleported to lobby."));
    }

    private void handleAddSpawn(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        if (player == null) {
            return;
        }
        RunOrFallLocation spawn = RunOrFallUtils.readLocation(ref, store);
        if (spawn == null) {
            player.sendMessage(Message.raw(PREFIX + "Could not read your position."));
            return;
        }
        configStore.addSpawn(spawn);
        player.sendMessage(Message.raw(PREFIX + "Spawn added (#" + configStore.getSpawns().size() + ")."));
        sendRefresh(ref, store);
    }

    private void handleClearSpawns(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        if (player == null) {
            return;
        }
        configStore.clearSpawns();
        player.sendMessage(Message.raw(PREFIX + "All spawns cleared."));
        sendRefresh(ref, store);
    }

    private void handleSetPos1(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        PlayerRef playerRef = resolvePlayerRef(ref, store);
        if (player == null || playerRef == null || playerRef.getUuid() == null) {
            return;
        }
        Vector3i pos = RunOrFallUtils.readCurrentBlock(ref, store);
        if (pos == null) {
            player.sendMessage(Message.raw(PREFIX + "Could not read your block position."));
            return;
        }
        BlockSelection selection = SELECTIONS.computeIfAbsent(playerRef.getUuid(), ignored -> new BlockSelection());
        selection.pos1 = pos;
        player.sendMessage(Message.raw(PREFIX + "pos1 = " + formatVector(pos)));
        sendRefresh(ref, store);
    }

    private void handleSetPos2(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        PlayerRef playerRef = resolvePlayerRef(ref, store);
        if (player == null || playerRef == null || playerRef.getUuid() == null) {
            return;
        }
        Vector3i pos = RunOrFallUtils.readCurrentBlock(ref, store);
        if (pos == null) {
            player.sendMessage(Message.raw(PREFIX + "Could not read your block position."));
            return;
        }
        BlockSelection selection = SELECTIONS.computeIfAbsent(playerRef.getUuid(), ignored -> new BlockSelection());
        selection.pos2 = pos;
        player.sendMessage(Message.raw(PREFIX + "pos2 = " + formatVector(pos)));
        sendRefresh(ref, store);
    }

    private void handleSavePlatform(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        PlayerRef playerRef = resolvePlayerRef(ref, store);
        if (player == null || playerRef == null || playerRef.getUuid() == null) {
            return;
        }
        BlockSelection selection = SELECTIONS.get(playerRef.getUuid());
        if (selection == null || selection.pos1 == null || selection.pos2 == null) {
            player.sendMessage(Message.raw(PREFIX + "Set pos1 and pos2 first."));
            return;
        }
        if (platformBlockIdInput == null || platformBlockIdInput.isBlank()) {
            player.sendMessage(Message.raw(PREFIX + "Block item ID is required (example: Rock_Marble_Brick_Smooth)."));
            return;
        }
        String targetBlockItemId = platformBlockIdInput.trim();
        int resolvedBlockId = BlockType.getAssetMap().getIndex(targetBlockItemId);
        if (resolvedBlockId < 0) {
            player.sendMessage(Message.raw(PREFIX + "Unknown block item ID: " + targetBlockItemId + "."));
            return;
        }
        RunOrFallPlatform platform = new RunOrFallPlatform(
                selection.pos1.x, selection.pos1.y, selection.pos1.z,
                selection.pos2.x, selection.pos2.y, selection.pos2.z,
                targetBlockItemId);
        if (!configStore.addPlatform(platform)) {
            player.sendMessage(Message.raw(PREFIX + "Could not save platform."));
            return;
        }
        player.sendMessage(Message.raw(PREFIX + "Platform saved (#" + configStore.getPlatforms().size()
                + ", item ID " + targetBlockItemId + ")."));
        sendRefresh(ref, store);
    }

    private void handleClearPlatforms(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        if (player == null) {
            return;
        }
        configStore.clearPlatforms();
        player.sendMessage(Message.raw(PREFIX + "All platforms cleared."));
        sendRefresh(ref, store);
    }

    private void handleSaveVoidY(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        if (player == null) {
            return;
        }
        double voidY;
        try {
            voidY = Double.parseDouble(voidYInput);
        } catch (NumberFormatException ex) {
            player.sendMessage(Message.raw(PREFIX + "Void Y must be a number."));
            return;
        }
        if (!Double.isFinite(voidY)) {
            player.sendMessage(Message.raw(PREFIX + "Void Y must be finite."));
            return;
        }
        configStore.setVoidY(voidY);
        player.sendMessage(Message.raw(PREFIX + "Void Y set to " + formatDouble(voidY) + "."));
        sendRefresh(ref, store);
    }

    private void handleSaveBreakDelay(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        if (player == null) {
            return;
        }
        double seconds;
        try {
            seconds = Double.parseDouble(breakDelayInput);
        } catch (NumberFormatException ex) {
            player.sendMessage(Message.raw(PREFIX + "Break delay must be a number."));
            return;
        }
        if (!Double.isFinite(seconds) || seconds < 0.0d) {
            player.sendMessage(Message.raw(PREFIX + "Break delay must be >= 0."));
            return;
        }
        configStore.setBlockBreakDelaySeconds(seconds);
        player.sendMessage(Message.raw(PREFIX + "Break delay set to " + formatDouble(seconds) + "s."));
        sendRefresh(ref, store);
    }

    private void handleSaveBlinkDistance(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        if (player == null) {
            return;
        }
        int blocks;
        try {
            blocks = Integer.parseInt(blinkDistanceInput);
        } catch (NumberFormatException ex) {
            player.sendMessage(Message.raw(PREFIX + "Blink distance must be a whole number."));
            return;
        }
        if (blocks < 1) {
            player.sendMessage(Message.raw(PREFIX + "Blink distance must be >= 1."));
            return;
        }
        configStore.setBlinkDistanceBlocks(blocks);
        player.sendMessage(Message.raw(PREFIX + "Blink distance set to " + configStore.getBlinkDistanceBlocks() + " blocks."));
        sendRefresh(ref, store);
    }

    private void handleSaveBlinkChargeSettings(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        if (player == null) {
            return;
        }
        int startCharges;
        int everyBlocksBroken;
        try {
            startCharges = Integer.parseInt(blinkStartChargesInput);
            everyBlocksBroken = Integer.parseInt(blinkChargeEveryBlocksInput);
        } catch (NumberFormatException ex) {
            player.sendMessage(Message.raw(PREFIX + "Blink charge values must be whole numbers."));
            return;
        }
        if (startCharges < 0) {
            player.sendMessage(Message.raw(PREFIX + "Start blink charges must be >= 0."));
            return;
        }
        if (everyBlocksBroken < 1) {
            player.sendMessage(Message.raw(PREFIX + "Blocks per blink charge must be >= 1."));
            return;
        }
        configStore.setBlinkChargeSettings(startCharges, everyBlocksBroken);
        player.sendMessage(Message.raw(PREFIX + "Blink charge settings saved."));
        sendRefresh(ref, store);
    }

    private void handleSaveAutoStart(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        if (player == null) {
            return;
        }
        int minPlayers;
        int minPlayersTime;
        int optimalPlayers;
        int optimalPlayersTime;
        try {
            minPlayers = Integer.parseInt(minPlayersInput);
            minPlayersTime = Integer.parseInt(minPlayersTimeInput);
            optimalPlayers = Integer.parseInt(optimalPlayersInput);
            optimalPlayersTime = Integer.parseInt(optimalPlayersTimeInput);
        } catch (NumberFormatException ex) {
            player.sendMessage(Message.raw(PREFIX + "Auto-start values must be whole numbers."));
            return;
        }
        if (minPlayers < 1 || minPlayersTime < 1 || optimalPlayers < 1 || optimalPlayersTime < 1) {
            player.sendMessage(Message.raw(PREFIX + "Values must be >= 1."));
            return;
        }
        if (optimalPlayers < minPlayers) {
            player.sendMessage(Message.raw(PREFIX + "Optimal players must be >= min players."));
            return;
        }
        configStore.setAutoStartSettings(minPlayers, minPlayersTime, optimalPlayers, optimalPlayersTime);
        player.sendMessage(Message.raw(PREFIX + "Auto-start settings saved."));
        sendRefresh(ref, store);
    }

    private void handleStart(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        if (player == null) {
            return;
        }
        gameManager.requestStart(true);
        player.sendMessage(Message.raw(PREFIX + "Start requested."));
        sendRefresh(ref, store);
    }

    private void handleStop(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        if (player == null) {
            return;
        }
        gameManager.requestStop("stopped by admin");
        player.sendMessage(Message.raw(PREFIX + "Stop requested."));
        sendRefresh(ref, store);
    }

    private void handleJoin(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = resolvePlayerRef(ref, store);
        Player player = resolvePlayer(ref, store);
        World world = resolveWorld(store);
        if (playerRef == null || playerRef.getUuid() == null || player == null || world == null) {
            return;
        }
        gameManager.joinLobby(playerRef.getUuid(), world);
        sendRefresh(ref, store);
    }

    private void handleLeave(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = resolvePlayerRef(ref, store);
        if (playerRef == null || playerRef.getUuid() == null) {
            return;
        }
        gameManager.leaveLobby(playerRef.getUuid(), true);
        sendRefresh(ref, store);
    }

    private void bindEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_REFRESH), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MapCreateButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_MAP_CREATE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MapDeleteButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_MAP_DELETE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetLobbyButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_SET_LOBBY), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TpLobbyButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_TP_LOBBY), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddSpawnButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_ADD_SPAWN), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClearSpawnsButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_CLEAR_SPAWNS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetPos1Button",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_SET_POS1), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetPos2Button",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_SET_POS2), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SavePlatformButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_SAVE_PLATFORM), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClearPlatformsButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_CLEAR_PLATFORMS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveVoidYButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_SAVE_VOIDY), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveBreakDelayButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_SAVE_BREAK_DELAY), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveBlinkDistanceButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_SAVE_BLINK_DISTANCE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveBlinkChargeSettingsButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_SAVE_BLINK_CHARGE_SETTINGS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveAutoStartButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_SAVE_AUTO_START), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#StartButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_START), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#StopButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_STOP), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#JoinButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_JOIN), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LeaveButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_LEAVE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapIdField",
                EventData.of(AdminData.KEY_MAP_ID, "#MapIdField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MapSearchField",
                EventData.of(AdminData.KEY_MAP_SEARCH, "#MapSearchField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#VoidYField",
                EventData.of(AdminData.KEY_VOID_Y, "#VoidYField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BreakDelayField",
                EventData.of(AdminData.KEY_BREAK_DELAY, "#BreakDelayField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BlinkDistanceField",
                EventData.of(AdminData.KEY_BLINK_DISTANCE, "#BlinkDistanceField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BlinkStartChargesField",
                EventData.of(AdminData.KEY_BLINK_START_CHARGES, "#BlinkStartChargesField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BlinkChargeEveryBlocksField",
                EventData.of(AdminData.KEY_BLINK_CHARGE_EVERY_BLOCKS, "#BlinkChargeEveryBlocksField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PlatformBlockIdField",
                EventData.of(AdminData.KEY_PLATFORM_BLOCK_ID, "#PlatformBlockIdField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MinPlayersField",
                EventData.of(AdminData.KEY_MIN_PLAYERS, "#MinPlayersField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MinPlayersTimeField",
                EventData.of(AdminData.KEY_MIN_PLAYERS_TIME, "#MinPlayersTimeField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#OptimalPlayersField",
                EventData.of(AdminData.KEY_OPTIMAL_PLAYERS, "#OptimalPlayersField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#OptimalPlayersTimeField",
                EventData.of(AdminData.KEY_OPTIMAL_PLAYERS_TIME, "#OptimalPlayersTimeField.Value"), false);
    }

    private void populateFields(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = resolvePlayerRef(ref, store);
        UUID playerId = playerRef != null ? playerRef.getUuid() : null;
        BlockSelection selection = playerId != null ? SELECTIONS.get(playerId) : null;

        List<RunOrFallLocation> spawns = configStore.getSpawns();
        List<RunOrFallPlatform> platforms = configStore.getPlatforms();
        RunOrFallLocation lobby = configStore.getLobby();
        String selectedMapId = configStore.getSelectedMapId();
        double voidY = configStore.getVoidY();
        double breakDelay = configStore.getBlockBreakDelaySeconds();
        int blinkDistance = configStore.getBlinkDistanceBlocks();
        int blinkStartCharges = configStore.getBlinkStartCharges();
        int blinkChargeEveryBlocks = configStore.getBlinkChargeEveryBlocksBroken();
        int minPlayers = configStore.getMinPlayers();
        int minPlayersTime = configStore.getMinPlayersTimeSeconds();
        int optimalPlayers = configStore.getOptimalPlayers();
        int optimalPlayersTime = configStore.getOptimalPlayersTimeSeconds();

        voidYInput = formatDouble(voidY);
        breakDelayInput = formatDouble(breakDelay);
        blinkDistanceInput = Integer.toString(blinkDistance);
        blinkStartChargesInput = Integer.toString(blinkStartCharges);
        blinkChargeEveryBlocksInput = Integer.toString(blinkChargeEveryBlocks);
        minPlayersInput = Integer.toString(minPlayers);
        minPlayersTimeInput = Integer.toString(minPlayersTime);
        optimalPlayersInput = Integer.toString(optimalPlayers);
        optimalPlayersTimeInput = Integer.toString(optimalPlayersTime);
        if (selectedMapId != null && !selectedMapId.isBlank() && (mapIdInput == null || mapIdInput.isBlank())) {
            mapIdInput = selectedMapId;
        }

        commandBuilder.set("#MapIdField.Value", mapIdInput == null ? "" : mapIdInput);
        commandBuilder.set("#MapSearchField.Value", mapSearchInput == null ? "" : mapSearchInput);
        commandBuilder.set("#VoidYField.Value", voidYInput);
        commandBuilder.set("#BreakDelayField.Value", breakDelayInput);
        commandBuilder.set("#BlinkDistanceField.Value", blinkDistanceInput);
        commandBuilder.set("#BlinkStartChargesField.Value", blinkStartChargesInput);
        commandBuilder.set("#BlinkChargeEveryBlocksField.Value", blinkChargeEveryBlocksInput);
        commandBuilder.set("#PlatformBlockIdField.Value", platformBlockIdInput == null ? "" : platformBlockIdInput);
        commandBuilder.set("#MinPlayersField.Value", minPlayersInput);
        commandBuilder.set("#MinPlayersTimeField.Value", minPlayersTimeInput);
        commandBuilder.set("#OptimalPlayersField.Value", optimalPlayersInput);
        commandBuilder.set("#OptimalPlayersTimeField.Value", optimalPlayersTimeInput);
        commandBuilder.set("#StatusValue.Text", gameManager.statusLine());
        commandBuilder.set("#SelectedMapValue.Text",
                selectedMapId == null || selectedMapId.isBlank() ? "Selected map: none" : "Selected map: " + selectedMapId);
        commandBuilder.set("#LobbyValue.Text", lobby == null ? "Lobby: not set" : "Lobby: " + formatLocation(lobby));
        commandBuilder.set("#SpawnsValue.Text", "Spawns: " + spawns.size());
        commandBuilder.set("#PlatformsValue.Text", "Platforms: " + platforms.size());
        commandBuilder.set("#VoidYCurrent.Text", "Current: " + formatDouble(voidY));
        commandBuilder.set("#BreakDelayCurrent.Text", "Current: " + formatDouble(breakDelay) + "s");
        commandBuilder.set("#BlinkDistanceCurrent.Text", "Current: " + blinkDistance + " blocks");
        commandBuilder.set("#BlinkChargeSettingsCurrent.Text", "Current: start " + blinkStartCharges
                + ", +1 every " + blinkChargeEveryBlocks + " blocks");
        commandBuilder.set("#AutoStartCurrent.Text", "Current: min " + minPlayers + " -> " + minPlayersTime
                + "s, optimal " + optimalPlayers + " -> " + optimalPlayersTime + "s");
        commandBuilder.set("#Pos1Value.Text", "Pos1: " + formatSelection(selection != null ? selection.pos1 : null));
        commandBuilder.set("#Pos2Value.Text", "Pos2: " + formatSelection(selection != null ? selection.pos2 : null));
    }

    private void buildMapList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#MapCards");
        String selectedMapId = configStore.getSelectedMapId();
        String filter = mapSearchInput != null ? mapSearchInput.trim().toLowerCase(Locale.ROOT) : "";
        List<String> mapIds = configStore.listMapIds();
        int index = 0;
        for (String mapId : mapIds) {
            if (mapId == null || mapId.isBlank()) {
                continue;
            }
            String mapIdLower = mapId.toLowerCase(Locale.ROOT);
            if (!filter.isEmpty() && !mapIdLower.startsWith(filter)) {
                continue;
            }
            commandBuilder.append("#MapCards", "Pages/RunOrFall_MapEntry.ui");
            boolean selected = selectedMapId != null && selectedMapId.equalsIgnoreCase(mapId);
            commandBuilder.set("#MapCards[" + index + "] #MapName.Text", selected ? ">> " + mapId : mapId);
            commandBuilder.set("#MapCards[" + index + "] #SelectedOverlay.Visible", selected);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#MapCards[" + index + "]",
                    EventData.of(AdminData.KEY_BUTTON, BUTTON_MAP_SELECT_PREFIX + mapId), false);
            index++;
        }
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindEvents(eventBuilder);
        populateFields(ref, store, commandBuilder);
        buildMapList(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private static Player resolvePlayer(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid() || store == null) {
            return null;
        }
        return store.getComponent(ref, Player.getComponentType());
    }

    private static PlayerRef resolvePlayerRef(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid() || store == null) {
            return null;
        }
        return store.getComponent(ref, PlayerRef.getComponentType());
    }

    private static World resolveWorld(Store<EntityStore> store) {
        if (store == null || store.getExternalData() == null) {
            return null;
        }
        return store.getExternalData().getWorld();
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String formatLocation(RunOrFallLocation location) {
        if (location == null) {
            return "not set";
        }
        return String.format(Locale.US, "%.1f, %.1f, %.1f", location.x, location.y, location.z);
    }

    private static String formatSelection(Vector3i pos) {
        if (pos == null) {
            return "not set";
        }
        return formatVector(pos);
    }

    private static String formatVector(Vector3i pos) {
        return pos.x + ", " + pos.y + ", " + pos.z;
    }

    public static class AdminData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_MAP_ID = "@MapId";
        static final String KEY_MAP_SEARCH = "@MapSearch";
        static final String KEY_VOID_Y = "@VoidY";
        static final String KEY_BREAK_DELAY = "@BreakDelay";
        static final String KEY_BLINK_DISTANCE = "@BlinkDistance";
        static final String KEY_BLINK_START_CHARGES = "@BlinkStartCharges";
        static final String KEY_BLINK_CHARGE_EVERY_BLOCKS = "@BlinkChargeEveryBlocks";
        static final String KEY_PLATFORM_BLOCK_ID = "@PlatformBlockId";
        static final String KEY_MIN_PLAYERS = "@MinPlayers";
        static final String KEY_MIN_PLAYERS_TIME = "@MinPlayersTime";
        static final String KEY_OPTIMAL_PLAYERS = "@OptimalPlayers";
        static final String KEY_OPTIMAL_PLAYERS_TIME = "@OptimalPlayersTime";

        public static final BuilderCodec<AdminData> CODEC = BuilderCodec.<AdminData>builder(AdminData.class, AdminData::new)
                .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING),
                        (data, value) -> data.button = value, data -> data.button)
                .addField(new KeyedCodec<>(KEY_MAP_ID, Codec.STRING),
                        (data, value) -> data.mapId = value, data -> data.mapId)
                .addField(new KeyedCodec<>(KEY_MAP_SEARCH, Codec.STRING),
                        (data, value) -> data.mapSearch = value, data -> data.mapSearch)
                .addField(new KeyedCodec<>(KEY_VOID_Y, Codec.STRING),
                        (data, value) -> data.voidY = value, data -> data.voidY)
                .addField(new KeyedCodec<>(KEY_BREAK_DELAY, Codec.STRING),
                        (data, value) -> data.breakDelay = value, data -> data.breakDelay)
                .addField(new KeyedCodec<>(KEY_BLINK_DISTANCE, Codec.STRING),
                        (data, value) -> data.blinkDistance = value, data -> data.blinkDistance)
                .addField(new KeyedCodec<>(KEY_BLINK_START_CHARGES, Codec.STRING),
                        (data, value) -> data.blinkStartCharges = value, data -> data.blinkStartCharges)
                .addField(new KeyedCodec<>(KEY_BLINK_CHARGE_EVERY_BLOCKS, Codec.STRING),
                        (data, value) -> data.blinkChargeEveryBlocks = value, data -> data.blinkChargeEveryBlocks)
                .addField(new KeyedCodec<>(KEY_PLATFORM_BLOCK_ID, Codec.STRING),
                        (data, value) -> data.platformBlockId = value, data -> data.platformBlockId)
                .addField(new KeyedCodec<>(KEY_MIN_PLAYERS, Codec.STRING),
                        (data, value) -> data.minPlayers = value, data -> data.minPlayers)
                .addField(new KeyedCodec<>(KEY_MIN_PLAYERS_TIME, Codec.STRING),
                        (data, value) -> data.minPlayersTime = value, data -> data.minPlayersTime)
                .addField(new KeyedCodec<>(KEY_OPTIMAL_PLAYERS, Codec.STRING),
                        (data, value) -> data.optimalPlayers = value, data -> data.optimalPlayers)
                .addField(new KeyedCodec<>(KEY_OPTIMAL_PLAYERS_TIME, Codec.STRING),
                        (data, value) -> data.optimalPlayersTime = value, data -> data.optimalPlayersTime)
                .build();

        private String button;
        private String mapId;
        private String mapSearch;
        private String voidY;
        private String breakDelay;
        private String blinkDistance;
        private String blinkStartCharges;
        private String blinkChargeEveryBlocks;
        private String platformBlockId;
        private String minPlayers;
        private String minPlayersTime;
        private String optimalPlayers;
        private String optimalPlayersTime;
    }
}
