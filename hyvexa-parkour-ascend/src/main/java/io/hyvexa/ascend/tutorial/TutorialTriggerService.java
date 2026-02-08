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
import io.hyvexa.ascend.ui.AscendTutorialPage;
import io.hyvexa.ascend.ui.AscendWelcomePage;

import io.hyvexa.common.math.BigNumber;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

    private static final long TRIGGER_DELAY_MS = 500L;

    private final AscendPlayerStore playerStore;

    public TutorialTriggerService(AscendPlayerStore playerStore) {
        this.playerStore = playerStore;
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

    public void checkCoinThresholds(UUID playerId, BigNumber oldBalance, BigNumber newBalance) {
        BigNumber elevationThreshold = BigNumber.fromLong(AscendConstants.ELEVATION_BASE_COST);
        BigNumber summitThreshold = BigNumber.fromLong(AscendConstants.SUMMIT_MIN_COINS);
        BigNumber ascensionThreshold = AscendConstants.ASCENSION_COIN_THRESHOLD;

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
        playerStore.markTutorialSeen(playerId, bit);

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
