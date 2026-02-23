package io.hyvexa.hub;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.common.util.AsyncExecutionHelper;
import io.hyvexa.common.util.InventoryUtils;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.MultiHudBridge;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.core.analytics.AnalyticsStore;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.discord.DiscordLinkStore;
import io.hyvexa.core.economy.VexaStore;
import io.hyvexa.hub.command.HubCommand;
import io.hyvexa.hub.hud.HubHud;
import io.hyvexa.hub.interaction.HubMenuInteraction;
import io.hyvexa.hub.routing.HubRouter;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class HyvexaHubPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** Delay before first HUD update after attach, to allow the client to load the UI. */
    private static final long HUD_READY_DELAY_MS = 250L;
    /** Fallback recovery cadence for players that still need HUD attach/reattach. */
    private static final long HUD_RECOVERY_INTERVAL_MS = 1000L;
    public static final short SLOT_SERVER_SELECTOR = 0;
    private static HyvexaHubPlugin INSTANCE;

    private HubRouter router;
    private volatile boolean databaseAvailable = true;
    private final ConcurrentHashMap<UUID, HudLifecycle> hubHudLifecycles = new ConcurrentHashMap<>();
    private ScheduledFuture<?> hubHudTask;
    private ScheduledFuture<?> playerCountTask;

    private enum HudPhase { PENDING, ATTACHING, READY }

    private record HudLifecycle(HubHud hud, long readyAt, HudPhase phase) {
        static HudLifecycle pending() {
            return new HudLifecycle(null, 0, HudPhase.PENDING);
        }
        static HudLifecycle attaching() {
            return new HudLifecycle(null, 0, HudPhase.ATTACHING);
        }
        HudLifecycle withReady(HubHud hud, long readyAt) {
            return new HudLifecycle(hud, readyAt, HudPhase.READY);
        }
    }

    public HyvexaHubPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    public static HyvexaHubPlugin getInstance() {
        return INSTANCE;
    }

    @Override
    protected void setup() {
        var folder = new File("mods/Hub");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        if (!DatabaseManager.getInstance().isInitialized()) {
            try {
                DatabaseManager.getInstance().initialize();
            } catch (Exception e) {
                LOGGER.atSevere().log("Failed to initialize database: " + e.getMessage());
                databaseAvailable = false;
            }
        }
        try {
            VexaStore.getInstance().initialize();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize VexaStore for Hub");
        }
        try {
            DiscordLinkStore.getInstance().initialize();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize DiscordLinkStore for Hub");
        }
        try {
            AnalyticsStore.getInstance().initialize();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize AnalyticsStore for Hub");
        }
        router = new HubRouter();
        preloadWorlds();

        registerInteractionCodecs();
        this.getCommandRegistry().registerCommand(new HubCommand(this));

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            if (!isHubWorld(store)) {
                return;
            }
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            World world = store.getExternalData().getWorld();
            if (world == null) {
                return;
            }
            String playerIdText = playerRef.getUuid() != null ? playerRef.getUuid().toString() : "unknown";
            String worldName = world.getName() != null ? world.getName() : "unknown";
            AsyncExecutionHelper.runBestEffort(world, () -> {
                if (!ref.isValid()) {
                    return;
                }
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }
                if (!databaseAvailable && PermissionUtils.isOp(player)) {
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                        "[Hub] Warning: Database unavailable. Some features may be degraded."));
                }
                InventoryUtils.clearAllContainers(player);
                giveHubItems(player);
                requestHubHudAttach(ref, store, playerRef);
                try {
                    DiscordLinkStore.getInstance().checkAndRewardVexa(playerRef.getUuid(), player);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Discord link check failed (hub)");
                }
            }, "hub.player_ready.setup", "hub player ready setup",
                    "player=" + playerIdText + ", world=" + worldName);
        });
        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            try {
                World world = event.getWorld();
                if (world == null) {
                    return;
                }
                var holder = event.getHolder();
                if (holder == null) {
                    return;
                }
                PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
                if (playerRef == null) {
                    return;
                }
                UUID playerId = playerRef.getUuid();
                if (playerId == null) {
                    return;
                }
                if (!isHubWorld(world)) {
                    clearHubHudState(playerId);
                    return;
                }
                hubHudLifecycles.putIfAbsent(playerId, HudLifecycle.pending());
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) {
                    return;
                }
                requestHubHudAttach(ref, ref.getStore(), playerRef);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Exception in AddPlayerToWorldEvent (hub HUD)");
            }
        });
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            if (playerRef == null) {
                return;
            }
            UUID playerId = playerRef.getUuid();
            if (playerId == null) {
                return;
            }
            clearHubHudState(playerId);
            try { VexaStore.getInstance().evictPlayer(playerId); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: VexaStore"); }
            try { DiscordLinkStore.getInstance().evictPlayer(playerId); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: DiscordLinkStore"); }
        });

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            Ref<EntityStore> ref = playerRef != null ? playerRef.getReference() : null;
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            if (isHubWorld(store)) {
                requestHubHudAttach(ref, store, playerRef);
            }
        }

        hubHudTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                this::tickHubHudRecovery, HUD_RECOVERY_INTERVAL_MS, HUD_RECOVERY_INTERVAL_MS, TimeUnit.MILLISECONDS
        );
        playerCountTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                this::tickPlayerCount, 5000L, 5000L, TimeUnit.MILLISECONDS
        );
    }

    private void tickPlayerCount() {
        long now = System.currentTimeMillis();
        for (var entry : hubHudLifecycles.entrySet()) {
            HudLifecycle lifecycle = entry.getValue();
            if (lifecycle.hud() != null && now >= lifecycle.readyAt()) {
                lifecycle.hud().updatePlayerCount();
                lifecycle.hud().updateVexa(VexaStore.getInstance().getVexa(entry.getKey()));
            }
        }
    }

    private void requestHubHudAttach(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        UUID playerId = playerRef != null ? playerRef.getUuid() : null;
        if (playerId == null) {
            return;
        }
        boolean[] shouldAttach = {false};
        hubHudLifecycles.compute(playerId, (id, existing) -> {
            if (existing != null && existing.phase() == HudPhase.ATTACHING) {
                return existing; // Already in-flight
            }
            shouldAttach[0] = true;
            return HudLifecycle.attaching();
        });
        if (shouldAttach[0]) {
            attachHubHud(ref, store, playerRef, playerId);
        }
    }

    private void attachHubHud(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef, UUID playerId) {
        var world = store.getExternalData().getWorld();
        if (world == null) {
            hubHudLifecycles.compute(playerId, (id, existing) ->
                existing != null && existing.phase() == HudPhase.ATTACHING ? HudLifecycle.pending() : existing);
            return;
        }
        String worldName = world.getName() != null ? world.getName() : "unknown";
        String playerIdText = playerId != null ? playerId.toString() : "unknown";
        AsyncExecutionHelper.runBestEffort(world, () -> {
            try {
                if (!ref.isValid() || !playerRef.isValid()) {
                    return;
                }
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }
                HudLifecycle state = hubHudLifecycles.compute(playerId, (ignored, existing) -> {
                    HubHud hud = (existing != null && existing.hud() != null) ? existing.hud() : new HubHud(playerRef);
                    return new HudLifecycle(hud, System.currentTimeMillis() + HUD_READY_DELAY_MS, HudPhase.READY);
                });
                if (state != null && state.hud() != null) {
                    MultiHudBridge.setCustomHud(player, playerRef, state.hud());
                    player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass);
                    MultiHudBridge.showIfNeeded(state.hud());
                }
            } finally {
                // If still ATTACHING (e.g. early return), revert to PENDING for recovery
                hubHudLifecycles.computeIfPresent(playerId, (id, existing) ->
                    existing.phase() == HudPhase.ATTACHING ? HudLifecycle.pending() : existing);
            }
        }, "hub.hud.attach", "hub HUD attach",
                "player=" + playerIdText + ", world=" + worldName);
    }

    private void registerInteractionCodecs() {
        var registry = this.getCodecRegistry(Interaction.CODEC);
        registry.register("Hub_Menu_Interaction", HubMenuInteraction.class, HubMenuInteraction.CODEC);
    }

    private void preloadWorlds() {
        try {
            Universe.get().loadWorld(WorldConstants.WORLD_HUB);
            Universe.get().loadWorld(WorldConstants.WORLD_PARKOUR);
            Universe.get().loadWorld(WorldConstants.WORLD_ASCEND);
            Universe.get().loadWorld(WorldConstants.WORLD_PURGE);
            Universe.get().loadWorld(WorldConstants.WORLD_RUN_OR_FALL);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to preload hub worlds: " + e.getMessage());
        }
    }

    public HubRouter getRouter() {
        return router;
    }

    private void giveHubItems(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        inventory.getHotbar().setItemStackForSlot(SLOT_SERVER_SELECTOR, new ItemStack(WorldConstants.ITEM_SERVER_SELECTOR, 1), false);
    }

    private void tickHubHudRecovery() {
        long now = System.currentTimeMillis();
        List<UUID> toRemove = null;
        for (var entry : hubHudLifecycles.entrySet()) {
            UUID playerId = entry.getKey();
            HudLifecycle lifecycle = entry.getValue();

            if (lifecycle.phase() == HudPhase.ATTACHING) {
                continue; // In-flight, don't interfere
            }
            if (lifecycle.phase() == HudPhase.READY && lifecycle.hud() != null) {
                if (now < lifecycle.readyAt()) {
                    continue; // Still stabilizing
                }
                // Stabilized — no action needed
                continue;
            }

            // PENDING phase — attempt recovery
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null || !playerRef.isValid()) {
                if (toRemove == null) toRemove = new java.util.ArrayList<>();
                toRemove.add(playerId);
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                if (toRemove == null) toRemove = new java.util.ArrayList<>();
                toRemove.add(playerId);
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            if (!isHubWorld(store)) {
                if (toRemove == null) toRemove = new java.util.ArrayList<>();
                toRemove.add(playerId);
                continue;
            }
            requestHubHudAttach(ref, store, playerRef);
        }
        if (toRemove != null) {
            for (UUID id : toRemove) {
                hubHudLifecycles.remove(id);
            }
        }
    }

    private void clearHubHudState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        hubHudLifecycles.remove(playerId);
    }

    private boolean isHubWorld(World world) {
        return ModeGate.isHubWorld(world);
    }

    private boolean isHubWorld(Store<EntityStore> store) {
        if (store == null || store.getExternalData() == null) {
            return false;
        }
        return isHubWorld(store.getExternalData().getWorld());
    }

    @Override
    protected void shutdown() {
        if (hubHudTask != null) {
            hubHudTask.cancel(false);
            hubHudTask = null;
        }
        if (playerCountTask != null) {
            playerCountTask.cancel(false);
            playerCountTask = null;
        }
        hubHudLifecycles.clear();
    }
}
