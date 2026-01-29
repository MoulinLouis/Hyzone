package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.InventoryUtils;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.util.ParkourUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class AdminPlayerStatsPage extends BaseParkourPage {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_TELEPORT_TO_PLAYER = "TeleportToPlayer";
    private static final String BUTTON_TELEPORT_PLAYER_HERE = "TeleportPlayerHere";
    private static final String BUTTON_KILL_PLAYER = "KillPlayer";
    private static final String BUTTON_GIVE_FLY = "GiveFly";
    private static final String BUTTON_REMOVE_FLY = "RemoveFly";
    private static final String BUTTON_RESET_INVENTORY = "ResetInventory";
    private static final String BUTTON_CLEAR_ALL_PROGRESS = "ClearAllProgress";
    private static final String BUTTON_CLEAR_MAP_PROGRESS = "ClearMapProgress";

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final UUID targetId;
    private String statusText = "";
    private String pendingAction = "";

    public AdminPlayerStatsPage(@Nonnull PlayerRef playerRef, MapStore mapStore, ProgressStore progressStore,
                                @Nonnull UUID targetId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.targetId = targetId;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_AdminPlayerStats.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TeleportToPlayerButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TELEPORT_TO_PLAYER), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TeleportPlayerHereButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TELEPORT_PLAYER_HERE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#KillPlayerButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_KILL_PLAYER), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GiveFlyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_GIVE_FLY), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RemoveFlyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_REMOVE_FLY), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ResetInventoryButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_RESET_INVENTORY), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClearAllProgressButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLEAR_ALL_PROGRESS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClearMapProgressButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLEAR_MAP_PROGRESS), false);
        populateSummary(uiCommandBuilder);
        uiCommandBuilder.set("#ActionStatus.Text", statusText);
        buildMapStats(uiCommandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }
        if (BUTTON_BACK.equals(data.getButton())) {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || playerRef == null) {
                return;
            }
            player.getPageManager().openCustomPage(ref, store,
                    new AdminPlayersPage(playerRef, mapStore, progressStore));
            return;
        }
        if (BUTTON_TELEPORT_TO_PLAYER.equals(data.getButton())) {
            handleTeleportToPlayer(ref, store);
            return;
        }
        if (BUTTON_TELEPORT_PLAYER_HERE.equals(data.getButton())) {
            handleTeleportPlayerHere(ref, store);
            return;
        }
        if (BUTTON_KILL_PLAYER.equals(data.getButton())) {
            handleKillPlayer(ref, store);
            return;
        }
        if (BUTTON_GIVE_FLY.equals(data.getButton())) {
            handleFlyToggle(ref, store, true);
            return;
        }
        if (BUTTON_REMOVE_FLY.equals(data.getButton())) {
            handleFlyToggle(ref, store, false);
            return;
        }
        if (BUTTON_RESET_INVENTORY.equals(data.getButton())) {
            handleResetInventory(ref, store);
            return;
        }
        if (BUTTON_CLEAR_ALL_PROGRESS.equals(data.getButton())) {
            handleClearAllProgress(ref, store);
            return;
        }
        if (BUTTON_CLEAR_MAP_PROGRESS.equals(data.getButton())) {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || playerRef == null) {
                return;
            }
            player.getPageManager().openCustomPage(ref, store,
                    new AdminPlayerMapProgressPage(playerRef, mapStore, progressStore, targetId));
        }
    }

    private void populateSummary(UICommandBuilder commandBuilder) {
        String name = ParkourUtils.resolveName(targetId, progressStore);
        long xp = progressStore.getCalculatedCompletionXp(targetId, mapStore);
        int completed = progressStore.getCompletedMapCount(targetId);
        int totalMaps = mapStore != null ? mapStore.listMaps().size() : 0;
        String rankName = progressStore.getRankName(targetId, mapStore);
        long playtimeMs = progressStore.getPlaytimeMs(targetId);
        boolean vip = progressStore.isVip(targetId);
        boolean founder = progressStore.isFounder(targetId);

        commandBuilder.set("#PlayerName.Text", name);
        commandBuilder.set("#PlayerUuid.Text", targetId.toString());
        commandBuilder.set("#PlayerRank.Text", rankName);
        commandBuilder.set("#PlayerRank.Style.TextColor", FormatUtils.getRankColor(rankName));
        commandBuilder.set("#PlayerXp.Text", xp + " XP");
        commandBuilder.set("#PlayerMaps.Text", completed + "/" + totalMaps + " maps");
        commandBuilder.set("#PlayerPlaytime.Text", FormatUtils.formatPlaytime(playtimeMs));
        commandBuilder.set("#PlayerVip.Text", vip ? "Yes" : "No");
        commandBuilder.set("#PlayerFounder.Text", founder ? "Yes" : "No");
    }

    private void handleTeleportToPlayer(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!confirmAction(BUTTON_TELEPORT_TO_PLAYER, "Teleporting to player")) {
            return;
        }
        PlayerRef targetRef = Universe.get().getPlayer(targetId);
        if (targetRef == null) {
            setStatus("Player not connected.");
            return;
        }
        Ref<EntityStore> targetEntityRef = targetRef.getReference();
        if (targetEntityRef == null || !targetEntityRef.isValid()) {
            setStatus("Player entity not available.");
            return;
        }
        Store<EntityStore> targetStore = targetEntityRef.getStore();
        World targetWorld = targetStore.getExternalData().getWorld();
        if (targetWorld == null) {
            setStatus("Player world unavailable.");
            return;
        }
        TransformComponent transform = targetStore.getComponent(targetEntityRef, TransformComponent.getComponentType());
        if (transform == null) {
            setStatus("Player position unavailable.");
            return;
        }
        PlayerRef adminRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (adminRef == null) {
            return;
        }
        Vector3d position = transform.getPosition();
        Vector3f rotation = transform.getRotation();
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(targetWorld, position, rotation));
        setStatus("Teleporting to " + resolveTargetName(targetRef) + ".");
    }

    private void handleTeleportPlayerHere(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!confirmAction(BUTTON_TELEPORT_PLAYER_HERE, "Teleporting player to you")) {
            return;
        }
        PlayerRef adminRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (adminRef == null) {
            return;
        }
        TransformComponent adminTransform = store.getComponent(ref, TransformComponent.getComponentType());
        if (adminTransform == null) {
            setStatus("Your position unavailable.");
            return;
        }
        World adminWorld = store.getExternalData().getWorld();
        if (adminWorld == null) {
            return;
        }
        PlayerRef targetRef = Universe.get().getPlayer(targetId);
        if (targetRef == null) {
            setStatus("Player not connected.");
            return;
        }
        Ref<EntityStore> targetEntityRef = targetRef.getReference();
        if (targetEntityRef == null || !targetEntityRef.isValid()) {
            setStatus("Player entity not available.");
            return;
        }
        Store<EntityStore> targetStore = targetEntityRef.getStore();
        World targetWorld = targetStore.getExternalData().getWorld();
        if (targetWorld == null) {
            return;
        }
        Vector3d position = adminTransform.getPosition();
        Vector3f rotation = adminTransform.getRotation();
        targetStore.addComponent(targetEntityRef, Teleport.getComponentType(),
                new Teleport(adminWorld, position, rotation));
        setStatus("Teleporting " + resolveTargetName(targetRef) + " to you.");
    }

    private void handleKillPlayer(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!confirmAction(BUTTON_KILL_PLAYER, "Killing player")) {
            return;
        }
        PlayerRef targetRef = Universe.get().getPlayer(targetId);
        if (targetRef == null) {
            setStatus("Player not connected.");
            return;
        }
        Ref<EntityStore> targetEntityRef = targetRef.getReference();
        if (targetEntityRef == null || !targetEntityRef.isValid()) {
            setStatus("Player entity not available.");
            return;
        }
        Store<EntityStore> targetStore = targetEntityRef.getStore();
        Damage damage = new Damage(Damage.NULL_SOURCE, DamageCause.COMMAND, 9999f);
        DamageSystems.executeDamage(targetEntityRef, targetStore, damage);
        setStatus("Damage applied to " + resolveTargetName(targetRef) + ".");
    }

    private void handleFlyToggle(Ref<EntityStore> ref, Store<EntityStore> store, boolean enabled) {
        String label = enabled ? "Giving fly" : "Removing fly";
        String actionKey = enabled ? BUTTON_GIVE_FLY : BUTTON_REMOVE_FLY;
        if (!confirmAction(actionKey, label)) {
            return;
        }
        PlayerRef targetRef = Universe.get().getPlayer(targetId);
        if (targetRef == null) {
            setStatus("Player not connected.");
            return;
        }
        Ref<EntityStore> targetEntityRef = targetRef.getReference();
        if (targetEntityRef == null || !targetEntityRef.isValid()) {
            setStatus("Player entity not available.");
            return;
        }
        Store<EntityStore> targetStore = targetEntityRef.getStore();
        MovementManager movementManager = targetStore.getComponent(targetEntityRef, MovementManager.getComponentType());
        if (movementManager == null) {
            setStatus("Movement settings unavailable.");
            return;
        }
        movementManager.refreshDefaultSettings(targetEntityRef, targetStore);
        movementManager.applyDefaultSettings();
        MovementSettings settings = movementManager.getSettings();
        if (settings == null) {
            setStatus("Movement settings unavailable.");
            return;
        }
        settings.canFly = enabled;
        var packetHandler = targetRef.getPacketHandler();
        if (packetHandler != null) {
            movementManager.update(packetHandler);
        }
        if (!enabled) {
            MovementStatesComponent movementStates = targetStore.getComponent(targetEntityRef,
                    MovementStatesComponent.getComponentType());
            if (movementStates != null && movementStates.getMovementStates() != null) {
                movementStates.getMovementStates().flying = false;
            }
        }
        setStatus((enabled ? "Fly enabled for " : "Fly removed from ") + resolveTargetName(targetRef) + ".");
    }

    private void handleResetInventory(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!confirmAction(BUTTON_RESET_INVENTORY, "Resetting inventory")) {
            return;
        }
        PlayerRef targetRef = Universe.get().getPlayer(targetId);
        if (targetRef == null) {
            setStatus("Player not connected.");
            return;
        }
        Ref<EntityStore> targetEntityRef = targetRef.getReference();
        if (targetEntityRef == null || !targetEntityRef.isValid()) {
            setStatus("Player entity not available.");
            return;
        }
        Store<EntityStore> targetStore = targetEntityRef.getStore();
        Player targetPlayer = targetStore.getComponent(targetEntityRef, Player.getComponentType());
        if (targetPlayer == null) {
            setStatus("Player entity not available.");
            return;
        }
        InventoryUtils.giveMenuItems(targetPlayer);
        setStatus("Inventory reset for " + resolveTargetName(targetRef) + ".");
    }

    private void handleClearAllProgress(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!confirmAction(BUTTON_CLEAR_ALL_PROGRESS, "Clearing all progress")) {
            return;
        }
        boolean cleared = progressStore.clearProgress(targetId);
        if (cleared) {
            setStatus("All progress cleared for " + ParkourUtils.resolveName(targetId, progressStore) + ".");
        } else {
            setStatus("No progress found for this player.");
        }
    }

    private boolean confirmAction(String actionKey, String actionLabel) {
        if (!actionKey.equals(pendingAction)) {
            pendingAction = actionKey;
            statusText = "Confirm: " + actionLabel + " (click again).";
            sendRefresh();
            return false;
        }
        pendingAction = "";
        statusText = actionLabel + "...";
        sendRefresh();
        return true;
    }

    private void setStatus(String text) {
        pendingAction = "";
        statusText = text != null ? text : "";
        sendRefresh();
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TeleportToPlayerButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TELEPORT_TO_PLAYER), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TeleportPlayerHereButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TELEPORT_PLAYER_HERE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#KillPlayerButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_KILL_PLAYER), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GiveFlyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_GIVE_FLY), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RemoveFlyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_REMOVE_FLY), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ResetInventoryButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_RESET_INVENTORY), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClearAllProgressButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLEAR_ALL_PROGRESS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClearMapProgressButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLEAR_MAP_PROGRESS), false);
        commandBuilder.set("#ActionStatus.Text", statusText);
        populateSummary(commandBuilder);
        buildMapStats(commandBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private String resolveTargetName(PlayerRef targetRef) {
        if (targetRef == null) {
            return targetId.toString();
        }
        String name = targetRef.getUsername();
        if (name == null || name.isBlank()) {
            name = ParkourUtils.resolveName(targetId, progressStore);
        }
        return name;
    }

    private void buildMapStats(UICommandBuilder commandBuilder) {
        commandBuilder.clear("#PlayerMapCards");
        if (mapStore == null) {
            commandBuilder.set("#EmptyText.Text", "Maps unavailable.");
            return;
        }
        List<Map> maps = new ArrayList<>(mapStore.listMaps());
        maps.sort(Comparator.comparingInt(Map::getOrder)
                .thenComparing(map -> map.getName() != null ? map.getName() : map.getId(),
                        String.CASE_INSENSITIVE_ORDER));
        int index = 0;
        for (Map map : maps) {
            commandBuilder.append("#PlayerMapCards", "Pages/Parkour_AdminPlayerMapEntry.ui");
            String mapName = ParkourUtils.formatMapName(map);
            commandBuilder.set("#PlayerMapCards[" + index + "] #MapName.Text", mapName);
            boolean completed = progressStore.isMapCompleted(targetId, map.getId());
            String status = completed ? "Completed" : "Not completed";
            if (completed) {
                Long bestTime = progressStore.getBestTimeMs(targetId, map.getId());
                if (bestTime != null) {
                    status += " | Best: " + FormatUtils.formatDuration(bestTime);
                }
            }
            commandBuilder.set("#PlayerMapCards[" + index + "] #MapStatus.Text", status);
            index++;
        }
        if (maps.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No maps available.");
        } else {
            commandBuilder.set("#EmptyText.Text", "");
        }
    }
}
