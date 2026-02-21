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
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.purge.system.PurgeDamageModifierSystem;
import io.hyvexa.common.util.InventoryUtils;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.core.analytics.AnalyticsStore;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.discord.DiscordLinkStore;
import io.hyvexa.core.economy.GemStore;
import io.hyvexa.purge.command.PurgeCommand;
import io.hyvexa.purge.data.PurgePlayerStore;
import io.hyvexa.purge.data.PurgeScrapStore;
import io.hyvexa.purge.data.PurgeWeaponUpgradeStore;
import io.hyvexa.purge.hud.PurgeHudManager;
import io.hyvexa.purge.interaction.PurgeOrangeOrbInteraction;
import io.hyvexa.purge.interaction.PurgeRedOrbInteraction;
import io.hyvexa.purge.interaction.PurgeStartInteraction;
import io.hyvexa.purge.manager.PurgeInstanceManager;
import io.hyvexa.purge.manager.PurgePartyManager;
import io.hyvexa.purge.manager.PurgeSessionManager;
import io.hyvexa.purge.manager.PurgeUpgradeManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;
import io.hyvexa.purge.manager.PurgeWaveManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HyvexaPurgePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String ITEM_ORB_BLUE = "Purge_Orb_Blue";
    private static final String ITEM_ORB_ORANGE = "Purge_Orb_Orange";
    private static final String ITEM_ORB_RED = "Purge_Orb_Red";
    private static final String ITEM_BULLET = "Bullet";
    private static final int STARTING_BULLET_COUNT = 120;
    private static final short SLOT_ORB_BLUE = 0;
    private static final short SLOT_ORB_ORANGE = 1;
    private static final short SLOT_PRIMARY_WEAPON = 0;
    private static final short SLOT_PRIMARY_AMMO = 1;
    private static final short SLOT_QUIT_ORB = 8;
    private static final short SLOT_SERVER_SELECTOR = 8;
    private static HyvexaPurgePlugin INSTANCE;

    private ScheduledFuture<?> hudUpdateTask;

    private PurgeInstanceManager instanceManager;
    private PurgeWaveConfigManager waveConfigManager;
    private PurgeWeaponConfigManager weaponConfigManager;
    private PurgeWaveManager waveManager;
    private PurgeHudManager hudManager;
    private PurgeSessionManager sessionManager;
    private PurgePartyManager partyManager;

    public HyvexaPurgePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    public static HyvexaPurgePlugin getInstance() {
        return INSTANCE;
    }

    public PurgeSessionManager getSessionManager() {
        return sessionManager;
    }

    public PurgeWeaponConfigManager getWeaponConfigManager() {
        return weaponConfigManager;
    }

    public PurgePartyManager getPartyManager() {
        return partyManager;
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
        try { PurgeWeaponUpgradeStore.getInstance().initialize(); }
        catch (Exception e) { LOGGER.atWarning().withCause(e).log("Failed to initialize PurgeWeaponUpgradeStore"); }

        // Create managers
        instanceManager = new PurgeInstanceManager();
        instanceManager.loadConfiguredInstances();
        waveConfigManager = new PurgeWaveConfigManager();
        weaponConfigManager = new PurgeWeaponConfigManager();
        hudManager = new PurgeHudManager();
        waveManager = new PurgeWaveManager(instanceManager, waveConfigManager, hudManager);
        partyManager = new PurgePartyManager();
        sessionManager = new PurgeSessionManager(partyManager, instanceManager, waveManager, hudManager);
        partyManager.setSessionManager(sessionManager);
        PurgeUpgradeManager upgradeManager = new PurgeUpgradeManager();
        waveManager.setUpgradeManager(upgradeManager);
        sessionManager.setUpgradeManager(upgradeManager);

        // Register damage modifier system
        registerPurgeDamageModifierSystem();

        // Register commands
        this.getCommandRegistry().registerCommand(
                new PurgeCommand(sessionManager, waveConfigManager, partyManager, instanceManager, weaponConfigManager));

        // Register item interaction codecs
        registerInteractionCodecs();

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
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            // Attach HUD (base info panel, wave status hidden by default)
            hudManager.attach(playerRef, player);
            // Base idle loadout in Purge world.
            InventoryUtils.clearAllContainers(player);
            giveBaseLoadout(player, true);
            // Initialize default weapons for this player
            PurgeWeaponUpgradeStore.getInstance().initializeDefaults(
                    playerId, weaponConfigManager.getDefaultWeaponIds());
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
                Player player = holder.getComponent(Player.getComponentType());
                if (playerRef != null && player != null) {
                    ensurePurgeWorldSetup(playerRef, player);
                }
                return;
            }
            // Leaving Purge world
            if (playerId != null) {
                if (sessionManager.hasActiveSession(playerId)) {
                    sessionManager.leaveSession(playerId, "left world");
                }
                partyManager.cleanupPlayer(playerId);
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
            sessionManager.cleanupPlayer(playerId);
            partyManager.cleanupPlayer(playerId);
            hudManager.removePlayer(playerId);
            try { GemStore.getInstance().evictPlayer(playerId); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: GemStore"); }
            try { DiscordLinkStore.getInstance().evictPlayer(playerId); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: DiscordLinkStore"); }
            try { PurgePlayerStore.getInstance().evictPlayer(playerId); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: PurgePlayerStore"); }
            try { PurgeScrapStore.getInstance().evictPlayer(playerId); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: PurgeScrapStore"); }
            try { PurgeWeaponUpgradeStore.getInstance().evictPlayer(playerId); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: PurgeWeaponUpgradeStore"); }
        });

        // Slow HUD updates (player count, gems, scrap) every 5 seconds
        hudUpdateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                () -> {
                    try { hudManager.tickSlowUpdates(); }
                    catch (Exception e) { LOGGER.atWarning().withCause(e).log("HUD tick error"); }
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
    }

    // --- Public loadout methods for PurgeSessionManager ---

    public void grantLoadout(Player player) {
        InventoryUtils.clearAllContainers(player);
        giveStartingWeapon(player);
        giveStartingAmmo(player);
        giveQuitOrb(player);
    }

    public void removeLoadout(Player player) {
        InventoryUtils.clearAllContainers(player);
        giveBaseLoadout(player, true);
    }

    public void giveWaitingLoadout(Player player) {
        InventoryUtils.clearAllContainers(player);
        giveQuitOrb(player);
    }

    // --- Private item grant methods ---

    private void giveBaseLoadout(Player player, boolean includeServerSelector) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        inventory.getHotbar().setItemStackForSlot(SLOT_ORB_BLUE, new ItemStack(ITEM_ORB_BLUE, 1), false);
        inventory.getHotbar().setItemStackForSlot(SLOT_ORB_ORANGE, new ItemStack(ITEM_ORB_ORANGE, 1), false);
        if (includeServerSelector) {
            giveServerSelector(player);
        }
    }

    private void giveServerSelector(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        inventory.getHotbar().setItemStackForSlot(SLOT_SERVER_SELECTOR,
                new ItemStack(WorldConstants.ITEM_SERVER_SELECTOR, 1), false);
    }

    private void giveQuitOrb(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        inventory.getHotbar().setItemStackForSlot(SLOT_QUIT_ORB, new ItemStack(ITEM_ORB_RED, 1), false);
    }

    private void giveStartingWeapon(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        String weaponItem = weaponConfigManager.getSessionWeaponId();
        inventory.getHotbar().setItemStackForSlot(SLOT_PRIMARY_WEAPON, new ItemStack(weaponItem, 1), false);
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

    private void ensurePurgeWorldSetup(PlayerRef playerRef, Player player) {
        UUID playerId = playerRef.getUuid();
        if (playerId != null && hudManager.getHud(playerId) == null) {
            hudManager.attach(playerRef, player);
        }
        if (playerId != null && sessionManager != null && sessionManager.hasActiveSession(playerId)) {
            return;
        }
        ensureIdleBaseLoadout(player);
    }

    private void ensureIdleBaseLoadout(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        ItemStack blueOrb = inventory.getHotbar().getItemStack(SLOT_ORB_BLUE);
        ItemStack orangeOrb = inventory.getHotbar().getItemStack(SLOT_ORB_ORANGE);
        boolean missingBlue = blueOrb == null || blueOrb.isEmpty();
        boolean missingOrange = orangeOrb == null || orangeOrb.isEmpty();
        if (!missingBlue && !missingOrange) {
            return;
        }
        if (missingBlue) {
            inventory.getHotbar().setItemStackForSlot(SLOT_ORB_BLUE, new ItemStack(ITEM_ORB_BLUE, 1), false);
        }
        if (missingOrange) {
            inventory.getHotbar().setItemStackForSlot(SLOT_ORB_ORANGE, new ItemStack(ITEM_ORB_ORANGE, 1), false);
        }
    }

    private void registerPurgeDamageModifierSystem() {
        var registry = EntityStore.REGISTRY;
        if (registry.getEntityEventTypeForClass(com.hypixel.hytale.server.core.modules.entity.damage.Damage.class) == null) {
            registry.registerEntityEventType(com.hypixel.hytale.server.core.modules.entity.damage.Damage.class);
        }
        if (!registry.hasSystemClass(PurgeDamageModifierSystem.class)) {
            registry.registerSystem(new PurgeDamageModifierSystem(sessionManager, weaponConfigManager));
        }
    }

    private void registerInteractionCodecs() {
        var registry = this.getCodecRegistry(Interaction.CODEC);
        registry.register("Purge_Start_Interaction",
                PurgeStartInteraction.class, PurgeStartInteraction.CODEC);
        registry.register("Purge_Orange_Orb_Interaction",
                PurgeOrangeOrbInteraction.class, PurgeOrangeOrbInteraction.CODEC);
        registry.register("Purge_Red_Orb_Interaction",
                PurgeRedOrbInteraction.class, PurgeRedOrbInteraction.CODEC);
    }
}
