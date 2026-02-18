package io.hyvexa.purge;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.common.util.InventoryUtils;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.core.analytics.AnalyticsStore;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.discord.DiscordLinkStore;
import io.hyvexa.core.economy.GemStore;
import io.hyvexa.purge.command.PurgeCommand;
import io.hyvexa.purge.command.PurgeSpawnCommand;
import io.hyvexa.purge.data.PurgePlayerStore;
import io.hyvexa.purge.data.PurgeScrapStore;
import io.hyvexa.purge.hud.PurgeHudManager;
import io.hyvexa.purge.manager.PurgeSessionManager;
import io.hyvexa.purge.manager.PurgeSpawnPointManager;
import io.hyvexa.purge.manager.PurgeWaveManager;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HyvexaPurgePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String ITEM_AK47 = "AK47";
    private static final String ITEM_BULLET = "Bullet";
    private static final int STARTING_BULLET_COUNT = 120;
    private static final short SLOT_PRIMARY_WEAPON = 0;
    private static final short SLOT_PRIMARY_AMMO = 1;
    private static final short SLOT_SERVER_SELECTOR = 8;
    private static HyvexaPurgePlugin INSTANCE;

    private final Set<UUID> playersInPurgeWorld = ConcurrentHashMap.newKeySet();
    private ScheduledFuture<?> hudUpdateTask;

    private PurgeSpawnPointManager spawnPointManager;
    private PurgeWaveManager waveManager;
    private PurgeHudManager hudManager;
    private PurgeSessionManager sessionManager;

    public HyvexaPurgePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    public static HyvexaPurgePlugin getInstance() {
        return INSTANCE;
    }

    @Override
    protected void setup() {
        // Initialize shared stores
        try { DatabaseManager.getInstance().initialize(); }
        catch (Exception e) { LOGGER.atWarning().withCause(e).log("Failed to initialize database for Purge"); }
        try { GemStore.getInstance().initialize(); }
        catch (Exception e) { LOGGER.atWarning().withCause(e).log("Failed to initialize GemStore for Purge"); }
        try { DiscordLinkStore.getInstance().initialize(); }
        catch (Exception e) { LOGGER.atWarning().withCause(e).log("Failed to initialize DiscordLinkStore for Purge"); }
        try { AnalyticsStore.getInstance().initialize(); }
        catch (Exception e) { LOGGER.atWarning().withCause(e).log("Failed to initialize AnalyticsStore for Purge"); }

        // Initialize Purge-specific stores
        try { PurgeScrapStore.getInstance().initialize(); }
        catch (Exception e) { LOGGER.atWarning().withCause(e).log("Failed to initialize PurgeScrapStore"); }
        try { PurgePlayerStore.getInstance().initialize(); }
        catch (Exception e) { LOGGER.atWarning().withCause(e).log("Failed to initialize PurgePlayerStore"); }

        // Create managers
        spawnPointManager = new PurgeSpawnPointManager();
        hudManager = new PurgeHudManager();
        waveManager = new PurgeWaveManager(spawnPointManager, hudManager);
        sessionManager = new PurgeSessionManager(spawnPointManager, waveManager, hudManager);

        // Register commands
        this.getCommandRegistry().registerCommand(new PurgeCommand(sessionManager));
        this.getCommandRegistry().registerCommand(new PurgeSpawnCommand(spawnPointManager));

        // --- Event Handlers ---

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            if (world == null || !ModeGate.isPurgeWorld(world)) {
                return;
            }
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            UUID playerId = playerRef.getUuid();
            if (playerId != null) {
                playersInPurgeWorld.add(playerId);
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            // Attach HUD (base info panel, wave status hidden by default)
            hudManager.attach(playerRef, player);
            // Give server selector only (session start handles weapon/ammo)
            giveServerSelector(player);
            LOGGER.atInfo().log("Player entered Purge: " + (playerId != null ? playerId : "unknown"));
            try {
                DiscordLinkStore.getInstance().checkAndRewardGems(playerId, player);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Discord link check failed (purge)");
            }
        });

        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            World world = event.getWorld();
            if (world == null) {
                return;
            }
            var holder = event.getHolder();
            if (holder == null) {
                return;
            }
            PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
            UUID playerId = playerRef != null ? playerRef.getUuid() : null;
            if (ModeGate.isPurgeWorld(world)) {
                if (playerId != null) {
                    playersInPurgeWorld.add(playerId);
                }
                return;
            }
            // Leaving Purge world
            if (playerId != null) {
                playersInPurgeWorld.remove(playerId);
                sessionManager.cleanupPlayer(playerId);
                hudManager.removePlayer(playerId);
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
            playersInPurgeWorld.remove(playerId);
            sessionManager.cleanupPlayer(playerId);
            hudManager.removePlayer(playerId);
            try { GemStore.getInstance().evictPlayer(playerId); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: GemStore"); }
            try { DiscordLinkStore.getInstance().evictPlayer(playerId); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: DiscordLinkStore"); }
            try { PurgePlayerStore.getInstance().evictPlayer(playerId); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: PurgePlayerStore"); }
            try { PurgeScrapStore.getInstance().evictPlayer(playerId); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: PurgeScrapStore"); }
        });

        // Slow HUD updates (player count, gems, scrap) every 5 seconds
        hudUpdateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                () -> {
                    try { hudManager.tickSlowUpdates(); }
                    catch (Exception e) { LOGGER.atWarning().log("HUD tick error: " + e.getMessage()); }
                },
                5000L, 5000L, TimeUnit.MILLISECONDS
        );

        LOGGER.atInfo().log("HyvexaPurge plugin loaded");
    }

    @Override
    protected void shutdown() {
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        if (hudUpdateTask != null) {
            hudUpdateTask.cancel(false);
            hudUpdateTask = null;
        }
        playersInPurgeWorld.clear();
    }

    // --- Public loadout methods for PurgeSessionManager ---

    public void grantLoadout(Player player) {
        InventoryUtils.clearAllContainers(player);
        giveStartingWeapon(player);
        giveStartingAmmo(player);
        giveServerSelector(player);
    }

    public void removeLoadout(Player player) {
        InventoryUtils.clearAllContainers(player);
        giveServerSelector(player);
    }

    // --- Private item grant methods ---

    private void giveServerSelector(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        inventory.getHotbar().setItemStackForSlot(SLOT_SERVER_SELECTOR,
                new ItemStack(WorldConstants.ITEM_SERVER_SELECTOR, 1), false);
    }

    private void giveStartingWeapon(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        inventory.getHotbar().setItemStackForSlot(SLOT_PRIMARY_WEAPON, new ItemStack(ITEM_AK47, 1), false);
    }

    private void giveStartingAmmo(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        inventory.getHotbar().setItemStackForSlot(
                SLOT_PRIMARY_AMMO,
                new ItemStack(ITEM_BULLET, STARTING_BULLET_COUNT),
                false
        );
    }
}
