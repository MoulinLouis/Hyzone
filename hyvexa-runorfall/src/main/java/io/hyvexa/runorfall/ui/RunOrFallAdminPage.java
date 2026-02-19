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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.runorfall.data.RunOrFallLocation;
import io.hyvexa.runorfall.data.RunOrFallPlatform;
import io.hyvexa.runorfall.manager.RunOrFallConfigStore;
import io.hyvexa.runorfall.manager.RunOrFallGameManager;

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
    private static final String BUTTON_START = "Start";
    private static final String BUTTON_STOP = "Stop";
    private static final String BUTTON_JOIN = "Join";
    private static final String BUTTON_LEAVE = "Leave";

    private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();

    private final RunOrFallConfigStore configStore;
    private final RunOrFallGameManager gameManager;
    private String platformNameInput;
    private String voidYInput;
    private String breakDelayInput;

    public RunOrFallAdminPage(@Nonnull PlayerRef playerRef,
                              RunOrFallConfigStore configStore,
                              RunOrFallGameManager gameManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, AdminData.CODEC);
        this.configStore = configStore;
        this.gameManager = gameManager;
        this.platformNameInput = "";
        this.voidYInput = formatDouble(configStore.getVoidY());
        this.breakDelayInput = formatDouble(configStore.getBlockBreakDelaySeconds());
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/RunOrFall_Admin.ui");
        bindEvents(eventBuilder);
        populateFields(ref, store, commandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull AdminData data) {
        super.handleDataEvent(ref, store, data);
        if (data.platformName != null) {
            platformNameInput = data.platformName.trim();
        }
        if (data.voidY != null) {
            voidYInput = data.voidY.trim();
        }
        if (data.breakDelay != null) {
            breakDelayInput = data.breakDelay.trim();
        }
        if (data.button == null) {
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
            case BUTTON_START -> handleStart(ref, store);
            case BUTTON_STOP -> handleStop(ref, store);
            case BUTTON_JOIN -> handleJoin(ref, store);
            case BUTTON_LEAVE -> handleLeave(ref, store);
            default -> {
            }
        }
    }

    private void handleSetLobby(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = resolvePlayer(ref, store);
        if (player == null) {
            return;
        }
        RunOrFallLocation location = readLocation(ref, store);
        if (location == null) {
            player.sendMessage(Message.raw(PREFIX + "Could not read your position."));
            return;
        }
        configStore.setLobby(location);
        player.sendMessage(Message.raw(PREFIX + "Lobby set."));
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
        RunOrFallLocation spawn = readLocation(ref, store);
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
        Vector3i pos = readCurrentBlock(ref, store);
        if (pos == null) {
            player.sendMessage(Message.raw(PREFIX + "Could not read your block position."));
            return;
        }
        Selection selection = SELECTIONS.computeIfAbsent(playerRef.getUuid(), ignored -> new Selection());
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
        Vector3i pos = readCurrentBlock(ref, store);
        if (pos == null) {
            player.sendMessage(Message.raw(PREFIX + "Could not read your block position."));
            return;
        }
        Selection selection = SELECTIONS.computeIfAbsent(playerRef.getUuid(), ignored -> new Selection());
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
        Selection selection = SELECTIONS.get(playerRef.getUuid());
        if (selection == null || selection.pos1 == null || selection.pos2 == null) {
            player.sendMessage(Message.raw(PREFIX + "Set pos1 and pos2 first."));
            return;
        }
        String name = platformNameInput != null ? platformNameInput.trim() : "";
        if (name.isBlank()) {
            name = "platform-" + (configStore.getPlatforms().size() + 1);
        }
        RunOrFallPlatform platform = new RunOrFallPlatform(name,
                selection.pos1.x, selection.pos1.y, selection.pos1.z,
                selection.pos2.x, selection.pos2.y, selection.pos2.z);
        if (!configStore.upsertPlatform(platform)) {
            player.sendMessage(Message.raw(PREFIX + "Could not save platform."));
            return;
        }
        player.sendMessage(Message.raw(PREFIX + "Platform '" + name + "' saved."));
        if (platformNameInput != null && platformNameInput.isBlank()) {
            platformNameInput = "";
        }
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
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#StartButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_START), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#StopButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_STOP), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#JoinButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_JOIN), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LeaveButton",
                EventData.of(AdminData.KEY_BUTTON, BUTTON_LEAVE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PlatformNameField",
                EventData.of(AdminData.KEY_PLATFORM_NAME, "#PlatformNameField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#VoidYField",
                EventData.of(AdminData.KEY_VOID_Y, "#VoidYField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BreakDelayField",
                EventData.of(AdminData.KEY_BREAK_DELAY, "#BreakDelayField.Value"), false);
    }

    private void populateFields(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = resolvePlayerRef(ref, store);
        UUID playerId = playerRef != null ? playerRef.getUuid() : null;
        Selection selection = playerId != null ? SELECTIONS.get(playerId) : null;

        List<RunOrFallLocation> spawns = configStore.getSpawns();
        List<RunOrFallPlatform> platforms = configStore.getPlatforms();
        RunOrFallLocation lobby = configStore.getLobby();
        double voidY = configStore.getVoidY();
        double breakDelay = configStore.getBlockBreakDelaySeconds();

        voidYInput = formatDouble(voidY);
        breakDelayInput = formatDouble(breakDelay);

        commandBuilder.set("#PlatformNameField.Value", platformNameInput == null ? "" : platformNameInput);
        commandBuilder.set("#VoidYField.Value", voidYInput);
        commandBuilder.set("#BreakDelayField.Value", breakDelayInput);
        commandBuilder.set("#StatusValue.Text", gameManager.statusLine());
        commandBuilder.set("#LobbyValue.Text", lobby == null ? "Lobby: not set" : "Lobby: " + formatLocation(lobby));
        commandBuilder.set("#SpawnsValue.Text", "Spawns: " + spawns.size());
        commandBuilder.set("#PlatformsValue.Text", "Platforms: " + platforms.size());
        commandBuilder.set("#VoidYCurrent.Text", "Current: " + formatDouble(voidY));
        commandBuilder.set("#BreakDelayCurrent.Text", "Current: " + formatDouble(breakDelay) + "s");
        commandBuilder.set("#Pos1Value.Text", "Pos1: " + formatSelection(selection != null ? selection.pos1 : null));
        commandBuilder.set("#Pos2Value.Text", "Pos2: " + formatSelection(selection != null ? selection.pos2 : null));
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindEvents(eventBuilder);
        populateFields(ref, store, commandBuilder);
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

    private static RunOrFallLocation readLocation(Ref<EntityStore> ref, Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            return null;
        }
        Vector3d position = transform.getPosition();
        Vector3f rotation = transform.getRotation();
        float rotX = rotation != null ? rotation.getX() : 0f;
        float rotY = rotation != null ? rotation.getY() : 0f;
        float rotZ = rotation != null ? rotation.getZ() : 0f;
        return new RunOrFallLocation(position.getX(), position.getY(), position.getZ(), rotX, rotY, rotZ);
    }

    private static Vector3i readCurrentBlock(Ref<EntityStore> ref, Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            return null;
        }
        Vector3d position = transform.getPosition();
        int x = (int) Math.floor(position.getX());
        int y = (int) Math.floor(position.getY() - 0.2d);
        int z = (int) Math.floor(position.getZ());
        return new Vector3i(x, y, z);
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

    private static final class Selection {
        private Vector3i pos1;
        private Vector3i pos2;
    }

    public static class AdminData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_PLATFORM_NAME = "@PlatformName";
        static final String KEY_VOID_Y = "@VoidY";
        static final String KEY_BREAK_DELAY = "@BreakDelay";

        public static final BuilderCodec<AdminData> CODEC = BuilderCodec.<AdminData>builder(AdminData.class, AdminData::new)
                .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING),
                        (data, value) -> data.button = value, data -> data.button)
                .addField(new KeyedCodec<>(KEY_PLATFORM_NAME, Codec.STRING),
                        (data, value) -> data.platformName = value, data -> data.platformName)
                .addField(new KeyedCodec<>(KEY_VOID_Y, Codec.STRING),
                        (data, value) -> data.voidY = value, data -> data.voidY)
                .addField(new KeyedCodec<>(KEY_BREAK_DELAY, Codec.STRING),
                        (data, value) -> data.breakDelay = value, data -> data.breakDelay)
                .build();

        private String button;
        private String platformName;
        private String voidY;
        private String breakDelay;
    }
}
