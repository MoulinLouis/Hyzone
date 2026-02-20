package io.hyvexa.ascend.tutorial;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.ui.AscendTutorialPage;
import io.hyvexa.ascend.ui.AscendWelcomePage;

import io.hyvexa.common.math.BigNumber;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class TutorialTriggerService {

    // Bit constants for seen_tutorials bitmask
    public static final int WELCOME = 1;
    public static final int FIRST_COMPLETION = 2;
    public static final int MAP_UNLOCK = 4;
    public static final int EVOLUTION = 8;
    public static final int ELEVATION = 16;
    public static final int SUMMIT = 32;
    public static final int ASCENSION = 64;
    public static final int CHALLENGES = 128;

    private static final long TRIGGER_DELAY_MS = 500L;

    private final AscendPlayerStore playerStore;
    private final AscendRunTracker runTracker;

    // Pending tutorials deferred because the player was in a run
    private final ConcurrentHashMap<UUID, ConcurrentLinkedQueue<TutorialOpener>> pendingTutorials = new ConcurrentHashMap<>();

    public TutorialTriggerService(AscendPlayerStore playerStore, AscendRunTracker runTracker) {
        this.playerStore = playerStore;
        this.runTracker = runTracker;
    }

    public void checkWelcome(UUID playerId, Ref<EntityStore> entityRef) {
        triggerIfUnseen(playerId, entityRef, WELCOME, (playerRef, ref, store, player) ->
            player.getPageManager().openCustomPage(ref, store, new AscendWelcomePage(playerRef)));
    }

    public void checkFirstCompletion(UUID playerId, Ref<EntityStore> entityRef) {
        triggerIfUnseen(playerId, entityRef, FIRST_COMPLETION, (playerRef, ref, store, player) ->
            player.getPageManager().openCustomPage(ref, store,
                new AscendTutorialPage(playerRef, AscendTutorialPage.Tutorial.FIRST_COMPLETION)));
    }

    public void checkMapUnlock(UUID playerId, Ref<EntityStore> entityRef) {
        triggerIfUnseen(playerId, entityRef, MAP_UNLOCK, (playerRef, ref, store, player) ->
            player.getPageManager().openCustomPage(ref, store,
                new AscendTutorialPage(playerRef, AscendTutorialPage.Tutorial.MAP_UNLOCK)));
    }

    public void checkEvolution(UUID playerId, Ref<EntityStore> entityRef) {
        triggerIfUnseen(playerId, entityRef, EVOLUTION, (playerRef, ref, store, player) ->
            player.getPageManager().openCustomPage(ref, store,
                new AscendTutorialPage(playerRef, AscendTutorialPage.Tutorial.EVOLUTION)));
    }

    public void checkVexaThresholds(UUID playerId, BigNumber oldBalance, BigNumber newBalance) {
        BigNumber elevationThreshold = BigNumber.fromLong(AscendConstants.ELEVATION_BASE_COST);
        BigNumber summitThreshold = BigNumber.fromLong(AscendConstants.SUMMIT_MIN_VEXA);
        BigNumber ascensionThreshold = AscendConstants.ASCENSION_VEXA_THRESHOLD;

        if (crossedThreshold(oldBalance, newBalance, elevationThreshold)) {
            triggerFromUuid(playerId, ELEVATION, (playerRef, ref, store, player) ->
                player.getPageManager().openCustomPage(ref, store,
                    new AscendTutorialPage(playerRef, AscendTutorialPage.Tutorial.ELEVATION)));
        }
        if (crossedThreshold(oldBalance, newBalance, summitThreshold)) {
            triggerFromUuid(playerId, SUMMIT, (playerRef, ref, store, player) ->
                player.getPageManager().openCustomPage(ref, store,
                    new AscendTutorialPage(playerRef, AscendTutorialPage.Tutorial.SUMMIT)));
        }
        if (crossedThreshold(oldBalance, newBalance, ascensionThreshold)) {
            triggerFromUuid(playerId, ASCENSION, (playerRef, ref, store, player) ->
                player.getPageManager().openCustomPage(ref, store,
                    new AscendTutorialPage(playerRef, AscendTutorialPage.Tutorial.ASCENSION)));
        }
    }

    /**
     * Flush pending tutorials for a player after their run ends.
     * Called from AscendRunTracker.completeRun() and cancelRun().
     */
    public void flushPendingTutorials(UUID playerId) {
        Queue<TutorialOpener> pending = pendingTutorials.remove(playerId);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        List<TutorialOpener> drained = new ArrayList<>();
        TutorialOpener opener;
        while ((opener = pending.poll()) != null) {
            drained.add(opener);
        }

        // Open each deferred tutorial with a small delay between them
        for (int i = 0; i < drained.size(); i++) {
            TutorialOpener deferredOpener = drained.get(i);
            long delay = TRIGGER_DELAY_MS + (i * 300L);
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
                if (plugin == null) return;
                PlayerRef playerRef = plugin.getPlayerRef(playerId);
                if (playerRef == null) return;
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) return;
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                if (world == null) return;
                CompletableFuture.runAsync(() -> {
                    if (!ref.isValid()) return;
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player == null) return;
                    deferredOpener.open(playerRef, ref, store, player);
                }, world);
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Clean up pending tutorials for a disconnecting player.
     * Tutorials are already marked as seen, so no data loss.
     */
    public void clearPendingTutorials(UUID playerId) {
        pendingTutorials.remove(playerId);
    }

    private boolean crossedThreshold(BigNumber oldBalance, BigNumber newBalance, BigNumber threshold) {
        return oldBalance.lt(threshold) && newBalance.gte(threshold);
    }

    private void triggerIfUnseen(UUID playerId, Ref<EntityStore> entityRef, int bit, TutorialOpener opener) {
        if (playerStore.hasSeenTutorial(playerId, bit)) {
            return;
        }
        playerStore.markTutorialSeen(playerId, bit);

        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (entityRef == null || !entityRef.isValid()) {
                return;
            }
            Store<EntityStore> store = entityRef.getStore();
            World world = store.getExternalData().getWorld();
            if (world == null) {
                return;
            }
            CompletableFuture.runAsync(() -> {
                if (!entityRef.isValid()) {
                    return;
                }
                PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
                Player player = store.getComponent(entityRef, Player.getComponentType());
                if (playerRef == null || player == null) {
                    return;
                }
                opener.open(playerRef, entityRef, store, player);
            }, world);
        }, TRIGGER_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void triggerFromUuid(UUID playerId, int bit, TutorialOpener opener) {
        if (playerStore.hasSeenTutorial(playerId, bit)) {
            return;
        }
        // Mark as seen immediately to prevent re-triggers
        playerStore.markTutorialSeen(playerId, bit);

        // If player is in a run, defer the tutorial opening
        if (runTracker != null && (runTracker.isRunActive(playerId) || runTracker.isPendingRun(playerId))) {
            pendingTutorials.computeIfAbsent(playerId, k -> new ConcurrentLinkedQueue<>()).offer(opener);
            return;
        }

        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin == null) {
                return;
            }
            PlayerRef playerRef = plugin.getPlayerRef(playerId);
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
                if (!ref.isValid()) {
                    return;
                }
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }
                opener.open(playerRef, ref, store, player);
            }, world);
        }, TRIGGER_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    @FunctionalInterface
    private interface TutorialOpener {
        void open(PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, Player player);
    }
}
