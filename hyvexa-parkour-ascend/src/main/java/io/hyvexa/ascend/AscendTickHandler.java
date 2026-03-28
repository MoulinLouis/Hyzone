package io.hyvexa.ascend;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.mine.MineGateChecker;
import io.hyvexa.ascend.mine.hud.MineHudManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.util.ModeGate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles per-player-per-world ticking for the Ascend module.
 * Manages the tick loop, player-world tracking, and player ref caching.
 */
class AscendTickHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    static final int FULL_TICK_INTERVAL = 4; // every 4th tick = 200ms at 50ms interval

    private final AscendRunTracker runTracker;
    private final AscendHudManager hudManager;
    private final MineHudManager mineHudManager;
    private final MineGateChecker mineGateChecker;

    private int tickCounter;
    private final ConcurrentHashMap<UUID, PlayerRef> playerRefCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<World, AtomicBoolean> worldTickInFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<World, Set<UUID>> tickPlayersByWorld = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, World> playerTickWorlds = new ConcurrentHashMap<>();
    private final Set<UUID> playersInAscendWorld = ConcurrentHashMap.newKeySet();

    AscendTickHandler(AscendRunTracker runTracker,
                      AscendHudManager hudManager,
                      MineHudManager mineHudManager,
                      MineGateChecker mineGateChecker) {
        this.runTracker = runTracker;
        this.hudManager = hudManager;
        this.mineHudManager = mineHudManager;
        this.mineGateChecker = mineGateChecker;
    }

    /**
     * Main tick method, called every 50ms from the scheduled executor.
     * Dispatches per-world tick work to world threads.
     */
    void tick() {
        if (runTracker == null) {
            return;
        }
        tickCounter++;
        boolean fullTick = tickCounter % FULL_TICK_INTERVAL == 0;

        for (Map.Entry<World, Set<UUID>> entry : tickPlayersByWorld.entrySet()) {
            World world = entry.getKey();
            if (!ModeGate.isAscendWorld(world)) {
                continue;
            }
            Set<UUID> playerIds = entry.getValue();
            if (playerIds == null) {
                continue;
            }
            if (playerIds.isEmpty()) {
                tickPlayersByWorld.remove(world, playerIds);
                continue;
            }
            AtomicBoolean inFlight = worldTickInFlight.computeIfAbsent(world, key -> new AtomicBoolean(false));
            if (!inFlight.compareAndSet(false, true)) {
                continue;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    for (UUID playerId : playerIds) {
                        PlayerRef playerRef = playerRefCache.get(playerId);
                        if (playerRef == null) {
                            removeTickPlayer(playerId);
                            continue;
                        }
                        Ref<EntityStore> ref = playerRef.getReference();
                        if (ref == null || !ref.isValid()) {
                            removeTickPlayerFromWorld(playerId);
                            continue;
                        }
                        Store<EntityStore> store = ref.getStore();
                        if (store == null || store.getExternalData() == null) {
                            removeTickPlayerFromWorld(playerId);
                            continue;
                        }
                        World playerWorld = store.getExternalData().getWorld();
                        if (playerWorld != world) {
                            syncTickPlayerWorld(playerRef, playerId);
                            continue;
                        }
                        // Gate check every tick (50ms) to prevent fast players from passing through
                        if (mineGateChecker != null) {
                            mineGateChecker.checkPlayer(playerId, ref, store);
                        }
                        // Always update the Ascend HUD (info panel always visible; economy skipped in mine mode)
                        if (fullTick) {
                            hudManager.updateFull(ref, store, playerRef);
                        }
                        // Mine HUD updates when in mine mode, run tracker when not
                        if (mineHudManager != null && mineHudManager.hasHud(playerId)) {
                            if (fullTick) {
                                mineHudManager.updateFull(playerId);
                            }
                            if (tickCounter % 20 == 0) {
                                mineHudManager.updateCooldowns(playerId);
                            }
                            mineHudManager.updateToasts(playerId);
                            mineHudManager.tickBlockHealth(playerId);
                            mineHudManager.tickCombo(playerId);
                        } else {
                            if (fullTick) {
                                runTracker.checkPlayer(ref, store);
                            }
                            hudManager.updateTimer(playerRef);
                            hudManager.updateRunnerBars(playerRef);
                            hudManager.updateToasts(playerRef.getUuid());
                        }
                    }
                } finally {
                    inFlight.set(false);
                }
            }, world).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
                LOGGER.atWarning().withCause(ex).log("Exception in tick async task");
                return null;
            });
        }
    }

    void applyHiddenStateForPlayer(PlayerRef viewerRef, World world) {
        if (viewerRef == null || viewerRef.getReference() == null || !viewerRef.getReference().isValid()) {
            return;
        }
        for (PlayerRef targetRef : world.getPlayerRefs()) {
            if (viewerRef.equals(targetRef)) {
                continue;
            }
            Ref<EntityStore> targetEntityRef = targetRef.getReference();
            if (targetEntityRef == null || !targetEntityRef.isValid()) {
                continue;
            }
            Store<EntityStore> targetStore = targetEntityRef.getStore();
            if (targetStore == null) {
                continue;
            }
            com.hypixel.hytale.server.core.entity.UUIDComponent uuidComponent =
                    targetStore.getComponent(targetEntityRef, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if (uuidComponent != null) {
                io.hyvexa.common.visibility.EntityVisibilityManager.get().hideEntity(
                        viewerRef.getUuid(), uuidComponent.getUuid());
            }
        }
    }

    void cacheTickPlayer(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        playerRefCache.put(playerId, playerRef);
        syncTickPlayerWorld(playerRef, playerId);
    }

    private void syncTickPlayerWorld(PlayerRef playerRef, UUID playerId) {
        if (playerId == null) {
            return;
        }
        Ref<EntityStore> ref = playerRef != null ? playerRef.getReference() : null;
        if (ref == null || !ref.isValid()) {
            removeTickPlayerFromWorld(playerId);
            return;
        }
        Store<EntityStore> store = ref.getStore();
        if (store == null || store.getExternalData() == null) {
            removeTickPlayerFromWorld(playerId);
            return;
        }
        World world = store.getExternalData().getWorld();
        if (!ModeGate.isAscendWorld(world)) {
            removeTickPlayerFromWorld(playerId);
            return;
        }
        World previousWorld = playerTickWorlds.put(playerId, world);
        if (previousWorld != null && previousWorld != world) {
            removeTickPlayerFromWorld(previousWorld, playerId);
        }
        tickPlayersByWorld.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    void removeTickPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        playerRefCache.remove(playerId);
        removeTickPlayerFromWorld(playerId);
    }

    private void removeTickPlayerFromWorld(UUID playerId) {
        if (playerId == null) {
            return;
        }
        World previousWorld = playerTickWorlds.remove(playerId);
        if (previousWorld != null) {
            removeTickPlayerFromWorld(previousWorld, playerId);
        }
    }

    private void removeTickPlayerFromWorld(World world, UUID playerId) {
        if (world == null || playerId == null) {
            return;
        }
        Set<UUID> playerIds = tickPlayersByWorld.get(world);
        if (playerIds == null) {
            return;
        }
        playerIds.remove(playerId);
        if (playerIds.isEmpty()) {
            tickPlayersByWorld.remove(world, playerIds);
        }
    }

    // --- Accessors for plugin-level use ---

    PlayerRef getPlayerRef(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return playerRefCache.get(playerId);
    }

    Set<UUID> playersInAscendWorld() {
        return playersInAscendWorld;
    }

    /** Clears all tick state. Called during plugin shutdown. */
    void clearAll() {
        worldTickInFlight.clear();
        tickPlayersByWorld.clear();
        playerTickWorlds.clear();
        playerRefCache.clear();
        playersInAscendWorld.clear();
    }
}
