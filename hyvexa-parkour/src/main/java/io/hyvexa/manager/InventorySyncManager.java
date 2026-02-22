package io.hyvexa.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.parkour.util.InventoryUtils;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.ui.WelcomeTutorialScreen1Page;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

public class InventorySyncManager {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final RunTracker runTracker;
    private final BiPredicate<PlayerRef, Store<EntityStore>> parkourModeChecker;
    private final String discordUrl;
    private final String languageNotice;
    private final String languageNoticeSuffix;

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

    public void updateDropProtection(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        boolean allowDrop = PermissionUtils.isOp(player);
        InventoryUtils.applyDropFilter(inventory.getHotbar(), allowDrop);
        InventoryUtils.applyDropFilter(inventory.getStorage(), allowDrop);
        InventoryUtils.applyDropFilter(inventory.getBackpack(), allowDrop);
        InventoryUtils.applyDropFilter(inventory.getTools(), allowDrop);
        InventoryUtils.applyDropFilter(inventory.getUtility(), allowDrop);
        InventoryUtils.applyDropFilter(inventory.getArmor(), allowDrop);
    }

    private boolean shouldApplyParkourMode(PlayerRef playerRef, Store<EntityStore> store) {
        if (parkourModeChecker == null) {
            return false;
        }
        return parkourModeChecker.test(playerRef, store);
    }
}
