package io.hyvexa.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.InventoryUtils;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.ui.WelcomeTutorialScreen1Page;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

/**
 * Manages inventory synchronization on player connect/ready events.
 * Handles giving appropriate items based on player state (in run vs menu),
 * showing welcome page for first-time players, and enforcing drop protection.
 */
public class InventorySyncManager {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final RunTracker runTracker;
    private final BiPredicate<PlayerRef, Store<EntityStore>> parkourModeChecker;
    private final String discordUrl;
    private final String languageNotice;
    private final String languageNoticeSuffix;

    /**
     * Creates a new InventorySyncManager.
     *
     * @param mapStore           the map store for retrieving map data
     * @param progressStore      the progress store for player progress data
     * @param runTracker         the run tracker for active run state
     * @param parkourModeChecker predicate to check if parkour mode should be applied
     * @param discordUrl         the Discord URL for the language notice
     * @param languageNotice     the language notice message prefix
     * @param languageNoticeSuffix the language notice message suffix
     */
    public InventorySyncManager(MapStore mapStore, ProgressStore progressStore, RunTracker runTracker,
                                BiPredicate<PlayerRef, Store<EntityStore>> parkourModeChecker,
                                String discordUrl, String languageNotice, String languageNoticeSuffix) {
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.runTracker = runTracker;
        this.parkourModeChecker = parkourModeChecker;
        this.discordUrl = discordUrl;
        this.languageNotice = languageNotice;
        this.languageNoticeSuffix = languageNoticeSuffix;
    }

    /**
     * Synchronizes inventory for a player on connect.
     * Checks if player is in parkour mode and delegates to the full sync method.
     *
     * @param playerRef the player reference
     */
    public void syncRunInventoryOnConnect(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        if (!shouldApplyParkourMode(playerRef, store)) {
            return;
        }
        syncRunInventoryOnConnect(ref, store, playerRef);
    }

    /**
     * Synchronizes inventory for a player on ready event.
     * Dispatches to World thread and performs full sync including welcome page and language notice.
     *
     * @param ref the entity reference
     */
    public void syncRunInventoryOnReady(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        // Must dispatch to World thread before accessing components
        CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) {
                return;
            }
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            if (!shouldApplyParkourMode(playerRef, store)) {
                return;
            }
            syncRunInventoryOnConnect(ref, store, playerRef);
            showWelcomeIfFirstJoin(ref, store, playerRef);
            sendLanguageNotice(playerRef);
        }, world);
    }

    /**
     * Synchronizes inventory for a player with full context.
     * Gives run items if player is in a run, otherwise gives menu items.
     *
     * @param ref       the entity reference
     * @param store     the entity store
     * @param playerRef the player reference
     */
    public void syncRunInventoryOnConnect(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            if (!shouldApplyParkourMode(playerRef, store)) {
                return;
            }
            String activeMap = runTracker.getActiveMapId(playerRef.getUuid());
            if (activeMap == null) {
                InventoryUtils.giveMenuItems(player);
                return;
            }
            boolean practiceEnabled = runTracker.isPracticeEnabled(playerRef.getUuid());
            InventoryUtils.giveRunItems(player, mapStore != null ? mapStore.getMap(activeMap) : null, practiceEnabled);
        }, world);
    }

    /**
     * Shows the welcome page if this is the player's first join.
     *
     * @param ref       the entity reference
     * @param store     the entity store
     * @param playerRef the player reference
     */
    public void showWelcomeIfFirstJoin(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (progressStore == null || playerRef == null || !progressStore.shouldShowWelcome(playerRef.getUuid())) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            WelcomeTutorialScreen1Page page = new WelcomeTutorialScreen1Page(playerRef);
            player.getPageManager().openCustomPage(ref, store, page);
        }, world);
    }

    /**
     * Sends the language notice to the player with Discord link.
     *
     * @param playerRef the player reference
     */
    public void sendLanguageNotice(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            Message link = Message.raw("Discord").color("#8ab4f8").link(discordUrl);
            Message message = Message.join(
                    Message.raw(languageNotice),
                    link,
                    Message.raw(languageNoticeSuffix)
            );
            player.sendMessage(message);
        }, world);
    }

    /**
     * Updates drop protection filters for all inventory containers.
     * Non-OP players cannot drop or remove items.
     *
     * @param player the player to update
     */
    public void updateDropProtection(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        boolean allowDrop = PermissionUtils.isOp(player);
        applyDropFilter(inventory.getHotbar(), allowDrop);
        applyDropFilter(inventory.getStorage(), allowDrop);
        applyDropFilter(inventory.getBackpack(), allowDrop);
        applyDropFilter(inventory.getTools(), allowDrop);
        applyDropFilter(inventory.getUtility(), allowDrop);
        applyDropFilter(inventory.getArmor(), allowDrop);
    }

    /**
     * Applies drop and remove filters to an item container.
     *
     * @param container the container to apply filters to
     * @param allowDrop whether to allow dropping items
     */
    public void applyDropFilter(ItemContainer container, boolean allowDrop) {
        if (container == null) {
            return;
        }
        SlotFilter filter = allowDrop ? SlotFilter.ALLOW : SlotFilter.DENY;
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            container.setSlotFilter(FilterActionType.DROP, slot, filter);
            container.setSlotFilter(FilterActionType.REMOVE, slot, filter);
        }
    }

    private boolean shouldApplyParkourMode(PlayerRef playerRef, Store<EntityStore> store) {
        if (parkourModeChecker == null) {
            return false;
        }
        return parkourModeChecker.test(playerRef, store);
    }
}
