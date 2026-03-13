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
import io.hyvexa.common.util.PlayerCleanupHelper;
import io.hyvexa.common.util.StoreInitializer;
import io.hyvexa.core.analytics.AnalyticsStore;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.discord.DiscordLinkStore;
import io.hyvexa.core.economy.VexaStore;
import io.hyvexa.purge.command.PurgeCommand;
import io.hyvexa.purge.command.CamTestCommand;
import io.hyvexa.purge.command.SetAmmoCommand;
import io.hyvexa.purge.data.PurgeClassStore;
import io.hyvexa.purge.data.PurgePlayerStore;
import io.hyvexa.purge.data.PurgeScrapStore;
import io.hyvexa.common.skin.PurgeSkinRegistry;
import io.hyvexa.common.skin.PurgeSkinStore;
import io.hyvexa.purge.data.PurgeWeaponUpgradeStore;
import io.hyvexa.purge.data.WeaponXpStore;
import io.hyvexa.purge.hud.PurgeHudManager;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeSessionPlayerState;
import io.hyvexa.purge.interaction.PurgeLootboxInteraction;
import io.hyvexa.purge.interaction.PurgeOrangeOrbInteraction;
import io.hyvexa.purge.interaction.PurgeRedOrbInteraction;
import io.hyvexa.purge.interaction.PurgeStartInteraction;
import io.hyvexa.common.util.MultiHudBridge;
import io.hyvexa.purge.mission.PurgeMissionManager;
import io.hyvexa.purge.mission.PurgeMissionStore;
import io.hyvexa.purge.manager.PurgeClassManager;
import io.hyvexa.purge.manager.PurgeInstanceManager;
import io.hyvexa.purge.manager.PurgePartyManager;
import io.hyvexa.purge.manager.PurgeSessionManager;
import io.hyvexa.purge.manager.PurgeUpgradeManager;
import io.hyvexa.purge.manager.PurgeVariantConfigManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;
import io.hyvexa.purge.manager.PurgeManagerRegistry;
import io.hyvexa.purge.manager.PurgeWaveManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;
import io.hyvexa.purge.manager.WeaponXpManager;

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
    private static final String ITEM_LOOTBOX = "Purge_Lootbox";
    private static final int STARTING_BULLET_COUNT = 120;
    private static final short SLOT_ORB_BLUE = 0;
    private static final short SLOT_ORB_ORANGE = 1;
    private static final short SLOT_PRIMARY_WEAPON = 0;
    private static final short SLOT_MELEE_WEAPON = 1;
    private static final short SLOT_PRIMARY_AMMO = 3;
    private static final short SLOT_LOOTBOX = 2;
    private static final short SLOT_SHOP = 2;
    private static final short SLOT_QUIT_ORB = 8;
    private static final short SLOT_SERVER_SELECTOR = 8;
    private static HyvexaPurgePlugin INSTANCE;

    private ScheduledFuture<?> hudUpdateTask;
    private ScheduledFuture<?> comboTickTask;

    private PurgeInstanceManager instanceManager;
    private PurgeVariantConfigManager variantConfigManager;
    private PurgeWaveConfigManager waveConfigManager;
    private PurgeWeaponConfigManager weaponConfigManager;
    private PurgeWaveManager waveManager;
    private PurgeHudManager hudManager;
    private PurgeSessionManager sessionManager;
    private PurgePartyManager partyManager;
    private PurgeUpgradeManager upgradeManager;
    private PurgeClassManager classManager;
    private WeaponXpManager weaponXpManager;

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

    public WeaponXpManager getWeaponXpManager() {
        return weaponXpManager;
    }

    public PurgeHudManager getHudManager() {
        return hudManager;
    }

    @Override
    protected void setup() {
        // Initialize all stores
        StoreInitializer.initialize(LOGGER,
                () -> DatabaseManager.getInstance().initialize(),
                () -> VexaStore.getInstance().initialize(),
                () -> DiscordLinkStore.getInstance().initialize(),
                () -> AnalyticsStore.getInstance().initialize(),
                () -> PurgeScrapStore.getInstance().initialize(),
                () -> PurgePlayerStore.getInstance().initialize(),
                () -> PurgeWeaponUpgradeStore.getInstance().initialize(),
                () -> PurgeSkinStore.getInstance().initialize(),
                () -> WeaponXpStore.getInstance().initialize(),
                () -> PurgeClassStore.getInstance().initialize(),
                () -> PurgeMissionStore.getInstance().initialize()
        );

        // Create managers
        instanceManager = new PurgeInstanceManager();
        instanceManager.loadConfiguredInstances();
        variantConfigManager = new PurgeVariantConfigManager();
        waveConfigManager = new PurgeWaveConfigManager(variantConfigManager);
        weaponConfigManager = new PurgeWeaponConfigManager();
        hudManager = new PurgeHudManager();
        waveManager = new PurgeWaveManager(instanceManager, waveConfigManager, variantConfigManager, hudManager);
        partyManager = new PurgePartyManager();
        sessionManager = new PurgeSessionManager(partyManager, instanceManager, waveManager, hudManager);
        upgradeManager = new PurgeUpgradeManager();
        weaponXpManager = new WeaponXpManager();
        classManager = new PurgeClassManager(upgradeManager);
        PurgeMissionManager missionManager = new PurgeMissionManager();

        PurgeManagerRegistry.builder()
                .sessionManager(sessionManager)
                .waveManager(waveManager)
                .partyManager(partyManager)
                .hudManager(hudManager)
                .upgradeManager(upgradeManager)
                .weaponXpManager(weaponXpManager)
                .classManager(classManager)
                .missionManager(missionManager)
                .build();

        // Register damage modifier system
        registerPurgeDamageModifierSystem();

        // Register commands
        this.getCommandRegistry().registerCommand(
                new PurgeCommand(sessionManager, waveConfigManager, partyManager, instanceManager, weaponConfigManager, variantConfigManager));
        this.getCommandRegistry().registerCommand(new SetAmmoCommand());
        this.getCommandRegistry().registerCommand(new CamTestCommand());

        // Register item interaction codecs
        registerInteractionCodecs();

        // --- Event Handlers ---

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
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
            // Evict stale skin cache (wardrobe purchases happen in a different classloader)
            PurgeSkinStore.getInstance().evictPlayer(playerId);
            ensurePurgeIdleState(playerRef, player);
            LOGGER.atInfo().log("Player entered Purge: " + (playerId != null ? playerId : "unknown"));
            DiscordLinkStore linkStore = DiscordLinkStore.getInstance();
            linkStore.checkAndRewardVexaOnLoginAsync(playerId)
                    .thenAcceptAsync(rewarded -> {
                        if (rewarded && ref.isValid()) {
                            linkStore.sendRewardGrantedMessage(player);
                        }
                    }, world)
                    .exceptionally(ex -> {
                        LOGGER.atWarning().withCause(ex).log("Discord link check failed (purge)");
                        return null;
                    });
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
                // Evict stale skin cache (wardrobe purchases happen in a different classloader)
                if (playerId != null) {
                    PurgeSkinStore.getInstance().evictPlayer(playerId);
                }
                Player player = holder.getComponent(Player.getComponentType());
                if (playerRef != null && player != null) {
                    ensurePurgeIdleState(playerRef, player);
                }
                return;
            }
            // Leaving Purge world
            if (playerId != null) {
                if (sessionManager.getSessionByPlayer(playerId) != null) {
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
            PlayerCleanupHelper.cleanup(playerId, LOGGER,
                    id -> VexaStore.getInstance().evictPlayer(id),
                    id -> DiscordLinkStore.getInstance().evictPlayer(id),
                    id -> PurgePlayerStore.getInstance().evictPlayer(id),
                    id -> PurgeScrapStore.getInstance().evictPlayer(id),
                    id -> PurgeWeaponUpgradeStore.getInstance().evictPlayer(id),
                    id -> PurgeSkinStore.getInstance().evictPlayer(id),
                    id -> WeaponXpStore.getInstance().evictPlayer(id),
                    id -> PurgeClassStore.getInstance().evictPlayer(id),
                    id -> PurgeMissionStore.getInstance().evictPlayer(id),
                    id -> MultiHudBridge.evictPlayer(id)
            );
        });

        // Slow HUD updates (player count, vexa, scrap) every 5 seconds
        hudUpdateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                () -> {
                    try { hudManager.tickSlowUpdates(); }
                    catch (Exception e) { LOGGER.atWarning().withCause(e).log("HUD tick error"); }
                },
                5000L, 5000L, TimeUnit.MILLISECONDS
        );

        // Fast combo bar ticker (50ms for smooth progress bar decay)
        comboTickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                () -> {
                    try { hudManager.tickComboBars(); }
                    catch (Exception e) { LOGGER.atWarning().withCause(e).log("Combo tick error"); }
                },
                50L, 50L, TimeUnit.MILLISECONDS
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
        if (comboTickTask != null) {
            comboTickTask.cancel(false);
            comboTickTask = null;
        }
        try { PurgeScrapStore.getInstance().shutdown(); }
        catch (Exception e) { LOGGER.atWarning().withCause(e).log("Shutdown: PurgeScrapStore"); }
        try { DatabaseManager.getInstance().shutdown(); }
        catch (Exception e) { /* Purge DB shutdown */ }
    }

    // --- Public loadout methods for PurgeSessionManager ---

    public void grantLoadout(Player player) {
        grantLoadout(player, null);
    }

    public void grantLoadout(Player player, PurgeSessionPlayerState state) {
        InventoryUtils.clearAllContainers(player);
        giveStartingWeapon(player, state);
        giveStartingMeleeWeapon(player, state);
        giveStartingAmmo(player);
        giveQuitOrb(player);
        // Apply weapon XP ammo multiplier to starting weapon
        if (state != null) {
            reapplyAmmoUpgrade(state.getPlayerId(), player);
        }
    }

    public void removeLoadout(Player player) {
        InventoryUtils.clearAllContainers(player);
        giveBaseLoadout(player);
    }

    public void giveWaitingLoadout(Player player) {
        InventoryUtils.clearAllContainers(player);
        giveQuitOrb(player);
    }

    public void switchWeapon(Player player, PurgeSessionPlayerState state, String newWeaponId) {
        state.setCurrentWeaponId(newWeaponId);
        String itemId = resolveWeaponItemId(state.getPlayerId(), newWeaponId);
        Inventory inventory = player.getInventory();
        if (inventory != null && inventory.getHotbar() != null) {
            inventory.getHotbar().setItemStackForSlot(SLOT_PRIMARY_WEAPON, new ItemStack(itemId, 1), false);
        }
        // Re-apply ammo upgrade to the new weapon ItemStack
        reapplyAmmoUpgrade(state.getPlayerId(), player);
        // Update weapon XP HUD for the new weapon
        String displayName = weaponConfigManager.getDisplayName(newWeaponId);
        hudManager.updateWeaponXpHud(state.getPlayerId(), newWeaponId, displayName);
    }

    public void switchMeleeWeapon(Player player, PurgeSessionPlayerState state, String newMeleeId) {
        state.setCurrentMeleeWeaponId(newMeleeId);
        String itemId = weaponConfigManager.getMeleeItemId(newMeleeId);
        Inventory inventory = player.getInventory();
        if (inventory != null && inventory.getHotbar() != null) {
            inventory.getHotbar().setItemStackForSlot(
                    SLOT_MELEE_WEAPON,
                    new ItemStack(itemId != null ? itemId : newMeleeId, 1),
                    false
            );
        }
        String displayName = weaponConfigManager.getDisplayName(newMeleeId);
        hudManager.updateMeleeXpHud(state.getPlayerId(), newMeleeId, displayName);
    }

    public void grantLootbox(Player player, int count) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        ItemStack existing = inventory.getHotbar().getItemStack(SLOT_LOOTBOX);
        int current = (existing != null && !existing.isEmpty()) ? existing.getQuantity() : 0;
        int newCount = Math.min(current + count, 10);
        inventory.getHotbar().setItemStackForSlot(SLOT_LOOTBOX, new ItemStack(ITEM_LOOTBOX, newCount), false);
    }

    /**
     * Re-applies ammo upgrade to the player's current weapon ItemStack.
     * Call after any operation that replaces the weapon (revive, weapon switch).
     */
    public void reapplyAmmoUpgrade(UUID playerId, Player player) {
        if (playerId == null || player == null || upgradeManager == null || sessionManager == null) return;
        PurgeSession session = sessionManager.getSessionByPlayer(playerId);
        if (session == null) return;
        upgradeManager.reapplyAmmoUpgrade(session, playerId, player);
    }

    // --- Private item grant methods ---

    private void giveBaseLoadout(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        inventory.getHotbar().setItemStackForSlot(SLOT_ORB_BLUE, new ItemStack(ITEM_ORB_BLUE, 1), false);
        inventory.getHotbar().setItemStackForSlot(SLOT_ORB_ORANGE, new ItemStack(ITEM_ORB_ORANGE, 1), false);
        giveServerSelector(player);
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

    private void giveStartingWeapon(Player player, PurgeSessionPlayerState state) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        String weaponItem = (state != null && state.getCurrentWeaponId() != null)
                ? state.getCurrentWeaponId()
                : weaponConfigManager.getSessionWeaponId();
        UUID ownerId = (state != null) ? state.getPlayerId() : null;
        String itemId = resolveWeaponItemId(ownerId, weaponItem);
        inventory.getHotbar().setItemStackForSlot(SLOT_PRIMARY_WEAPON, new ItemStack(itemId, 1), false);
    }

    private void giveStartingMeleeWeapon(Player player, PurgeSessionPlayerState state) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        String meleeWeaponId = (state != null && state.getCurrentMeleeWeaponId() != null)
                ? state.getCurrentMeleeWeaponId()
                : weaponConfigManager.getSessionMeleeWeaponId();
        String itemId = weaponConfigManager.getMeleeItemId(meleeWeaponId);
        inventory.getHotbar().setItemStackForSlot(
                SLOT_MELEE_WEAPON,
                new ItemStack(itemId != null ? itemId : meleeWeaponId, 1),
                false
        );
    }

    private String resolveWeaponItemId(UUID playerId, String weaponId) {
        if (weaponId == null) {
            return null;
        }
        if (weaponConfigManager.isMeleeWeapon(weaponId)) {
            String meleeItemId = weaponConfigManager.getMeleeItemId(weaponId);
            return meleeItemId != null ? meleeItemId : weaponId;
        }
        if (playerId == null) {
            return weaponId;
        }
        String skinId = PurgeSkinStore.getInstance().getSelectedSkin(playerId, weaponId);
        if (skinId != null) {
            return PurgeSkinRegistry.getSkinnedItemId(weaponId, skinId);
        }
        return weaponId;
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

    private void ensurePurgeIdleState(PlayerRef playerRef, Player player) {
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        hudManager.attach(playerRef, player);
        PurgeWeaponUpgradeStore.getInstance().initializeDefaults(
                playerId, weaponConfigManager.getDefaultWeaponIds());
        if (sessionManager != null && sessionManager.hasActiveSession(playerId)) {
            return;
        }
        // PlayerReadyEvent can run before the entity is fully attached to a world.
        // AddPlayerToWorldEvent will call this again once the inventory is safe to mutate.
        if (!isInventoryWriteSafe(player)) {
            return;
        }
        ensureIdleBaseLoadout(player);
    }

    private void ensureIdleBaseLoadout(Player player) {
        if (!isInventoryWriteSafe(player)) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        InventoryUtils.clearAllContainers(player);
        inventory.getHotbar().setItemStackForSlot(SLOT_ORB_BLUE, new ItemStack(ITEM_ORB_BLUE, 1), false);
        inventory.getHotbar().setItemStackForSlot(SLOT_ORB_ORANGE, new ItemStack(ITEM_ORB_ORANGE, 1), false);
        inventory.getHotbar().setItemStackForSlot(SLOT_SHOP, new ItemStack(WorldConstants.ITEM_SHOP, 1), false);
        giveServerSelector(player);
    }

    private static boolean isInventoryWriteSafe(Player player) {
        return player != null && player.getWorld() != null;
    }

    private void registerPurgeDamageModifierSystem() {
        var registry = EntityStore.REGISTRY;
        if (registry.getEntityEventTypeForClass(com.hypixel.hytale.server.core.modules.entity.damage.Damage.class) == null) {
            registry.registerEntityEventType(com.hypixel.hytale.server.core.modules.entity.damage.Damage.class);
        }
        if (!registry.hasSystemClass(PurgeDamageModifierSystem.class)) {
            PurgeDamageModifierSystem dmgSystem = new PurgeDamageModifierSystem(sessionManager, variantConfigManager, weaponConfigManager, weaponXpManager, classManager);
            registry.registerSystem(dmgSystem);
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
        registry.register("Purge_Lootbox_Interaction",
                PurgeLootboxInteraction.class, PurgeLootboxInteraction.CODEC);
        registry.register("Shop_Item_Interaction",
                io.hyvexa.common.interaction.ShopItemInteraction.class,
                io.hyvexa.common.interaction.ShopItemInteraction.CODEC);
    }
}
