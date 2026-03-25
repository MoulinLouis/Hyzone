package io.hyvexa.purge.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.common.util.DamageBypassRegistry;
import io.hyvexa.purge.PurgeLoadoutService;
import io.hyvexa.purge.data.PurgeLocation;
import io.hyvexa.purge.data.PurgeMapInstance;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeSessionPlayerState;
import io.hyvexa.purge.data.PurgeSpawnPoint;
import io.hyvexa.purge.data.PurgeVariantConfig;
import io.hyvexa.purge.data.PurgeWaveDefinition;
import io.hyvexa.purge.data.SessionState;
import io.hyvexa.purge.hud.PurgeHudManager;
import io.hyvexa.purge.util.PurgePlayerNameResolver;
import io.hyvexa.purge.util.UnsafeReflectionHelper;
import io.hyvexa.purge.util.ZombieAggroBooster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PurgeWaveManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int VANILLA_ZOMBIE_HP = 49;
    private static final String PURGE_HP_MODIFIER = "purge_wave_hp";
    private static final String PURGE_VARIANT_HP_MODIFIER = "purge_variant_hp";
    private static final long WAVE_TICK_INTERVAL_MS = 200;
    private static final double SPAWN_RANDOM_OFFSET = 2.0;
    /** Spawn count multiplier added per extra player above 1. */
    private static final double PLAYER_SCALE_PER_EXTRA = 0.75;

    private final PurgeInstanceManager instanceManager;
    private final PurgeWaveConfigManager waveConfigManager;
    private final PurgeVariantConfigManager variantConfigManager;
    private final PurgeHudManager hudManager;
    private final PurgeWeaponConfigManager weaponConfigManager;
    private final PurgeLoadoutService loadoutService;
    private final WaveDeathTracker deathTracker;
    private final WaveProgressionController progressionController;
    private volatile NPCPlugin npcPlugin;
    private PurgeManagerRegistry registry;

    public PurgeWaveManager(PurgeInstanceManager instanceManager,
                            PurgeWaveConfigManager waveConfigManager,
                            PurgeVariantConfigManager variantConfigManager,
                            PurgeHudManager hudManager,
                            PurgeWeaponConfigManager weaponConfigManager,
                            PurgeLoadoutService loadoutService) {
        this.instanceManager = instanceManager;
        this.waveConfigManager = waveConfigManager;
        this.variantConfigManager = variantConfigManager;
        this.hudManager = hudManager;
        this.weaponConfigManager = weaponConfigManager;
        this.loadoutService = loadoutService;
        this.deathTracker = new WaveDeathTracker(variantConfigManager, hudManager, weaponConfigManager, loadoutService);
        this.progressionController = new WaveProgressionController(this, waveConfigManager, hudManager);
        try {
            this.npcPlugin = NPCPlugin.get();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("NPCPlugin not available");
        }
    }

    void initRegistry(PurgeManagerRegistry registry) {
        this.registry = registry;
        deathTracker.initRegistry(registry);
        progressionController.initRegistry(registry);
    }

    public boolean hasConfiguredWaves() {
        return waveConfigManager.hasWaves();
    }

    // --- Scaling Formulas ---

    public static double hpMultiplier(int wave) {
        return 1.0 + Math.max(0, wave - 2) * 0.12;
    }

    // --- Wave Lifecycle ---

    public void startCountdown(PurgeSession session) {
        progressionController.startCountdown(session);
    }

    public void startNextWave(PurgeSession session) {
        int nextWave = session.getCurrentWave() + 1;
        PurgeWaveDefinition wave = waveConfigManager.getWave(nextWave);
        if (wave == null) {
            progressionController.handleVictory(session);
            return;
        }

        session.setCurrentWave(nextWave);

        // Scale spawn counts by connected player count
        int playerCount = session.getConnectedCount();
        Map<String, Integer> scaledCounts = scaleVariantCounts(wave, playerCount);
        int total = 0;
        for (int c : scaledCounts.values()) total += c;
        final int totalCount = total;

        session.setWaveZombieCount(totalCount);
        session.setSpawningComplete(false);
        session.resetWaveSpawnProgress();
        session.clearPendingZombieDeaths();
        session.resetTransitionGuard();
        session.setState(SessionState.SPAWNING);

        sendMessageToAll(session, "-- Wave " + nextWave + " -- (" + totalCount + " zombies, "
                + wave.spawnDelayMs() + "ms delay, batch " + wave.spawnBatchSize() + ")");
        session.forEachConnectedParticipant(pid -> hudManager.updateWaveStatus(pid, nextWave, totalCount, totalCount));

        List<String> spawnQueue = buildSpawnQueue(scaledCounts);
        if (spawnQueue.isEmpty()) {
            markSpawningComplete(session);
            if (tryBeginSessionTransition(session)) {
                progressionController.onWaveComplete(session);
            }
            return;
        }

        startSpawning(session, spawnQueue, wave);
        startWaveTick(session);
    }

    private List<String> buildSpawnQueue(Map<String, Integer> variantCounts) {
        Map<String, Integer> remaining = new LinkedHashMap<>(variantCounts);

        int total = 0;
        for (int c : remaining.values()) total += c;
        List<String> queue = new ArrayList<>(total);
        // Round-robin interleave variants
        while (!remaining.isEmpty()) {
            var it = remaining.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                queue.add(entry.getKey());
                int left = entry.getValue() - 1;
                if (left <= 0) {
                    it.remove();
                } else {
                    entry.setValue(left);
                }
            }
        }
        return queue;
    }

    private static Map<String, Integer> scaleVariantCounts(PurgeWaveDefinition wave, int playerCount) {
        Map<String, Integer> scaled = new LinkedHashMap<>();
        double multiplier = 1.0 + PLAYER_SCALE_PER_EXTRA * Math.max(0, playerCount - 1);
        for (String key : wave.getVariantKeys()) {
            int base = Math.max(0, wave.getCount(key));
            if (base > 0) {
                scaled.put(key, (int) Math.round(base * multiplier));
            }
        }
        return scaled;
    }

    private void startSpawning(PurgeSession session, List<String> spawnQueue, PurgeWaveDefinition wave) {
        int totalCount = spawnQueue.size();

        AtomicInteger queueIndex = new AtomicInteger(0);

        ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                if (session.getState() == SessionState.ENDED) {
                    session.cancelSpawnTask();
                    return;
                }
                int batch = Math.min(wave.spawnBatchSize(), totalCount - queueIndex.get());
                if (batch <= 0) {
                    markSpawningComplete(session);
                    session.cancelSpawnTask();
                    return;
                }

                World world = getPurgeWorld();
                if (world == null) {
                    return;
                }

                world.execute(() -> {
                    try {
                        if (session.getState() == SessionState.ENDED) {
                            return;
                        }
                        Store<EntityStore> store = world.getEntityStore().getStore();
                        if (store == null) {
                            return;
                        }

                        // Use random alive player position for spawn point selection
                        Ref<EntityStore> targetRef = session.getRandomAlivePlayerRef();
                        if (targetRef == null) return; // all dead, team wipe imminent
                        double spawnX = 0, spawnZ = 0;
                        if (targetRef.isValid()) {
                            double[] pos = getRefPosition(targetRef);
                            if (pos != null) {
                                spawnX = pos[0];
                                spawnZ = pos[2];
                            }
                        }

                        PurgeMapInstance instance = instanceManager.getInstance(session.getInstanceId());

                        int toSpawn = Math.min(wave.spawnBatchSize(), totalCount - queueIndex.get());
                        for (int i = 0; i < toSpawn; i++) {
                            if (session.getState() == SessionState.ENDED) {
                                return;
                            }
                            int idx = queueIndex.getAndIncrement();
                            if (idx >= spawnQueue.size()) {
                                break;
                            }

                            session.incrementWaveSpawnAttempt();
                            PurgeSpawnPoint spawnPoint = instanceManager.selectSpawnPoint(instance, spawnX, spawnZ);
                            String variantKey = spawnQueue.get(idx);
                            boolean spawned = spawnZombie(session, store, spawnPoint, variantKey, session.getCurrentWave());
                            if (!spawned) {
                                LOGGER.atWarning().log("Wave spawn failed: wave=" + session.getCurrentWave()
                                        + " variant=" + variantKey);
                            } else {
                                session.incrementWaveSpawnSuccess();
                            }
                        }

                        if (queueIndex.get() >= totalCount) {
                            markSpawningComplete(session);
                            session.cancelSpawnTask();
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("Spawn batch error");
                    }
                });
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Spawn task error");
            }
        }, 0, wave.spawnDelayMs(), TimeUnit.MILLISECONDS);
        session.setSpawnTask(task);
    }

    private void markSpawningComplete(PurgeSession session) {
        session.setSpawningComplete(true);
        if (session.getState() == SessionState.SPAWNING) {
            session.setState(SessionState.COMBAT);
        }
    }

    private boolean spawnZombie(PurgeSession session,
                                Store<EntityStore> store,
                                PurgeSpawnPoint point,
                                String variantKey,
                                int wave) {
        if (session.getState() == SessionState.ENDED) {
            return false;
        }
        if (npcPlugin == null) {
            return false;
        }
        if (point == null || variantKey == null) {
            return false;
        }

        PurgeVariantConfig variantConfig = variantConfigManager.getVariant(variantKey);
        if (variantConfig == null) {
            LOGGER.atWarning().log("Unknown variant key: " + variantKey);
            return false;
        }

        double x = point.x() + ThreadLocalRandom.current().nextDouble(-SPAWN_RANDOM_OFFSET, SPAWN_RANDOM_OFFSET);
        double z = point.z() + ThreadLocalRandom.current().nextDouble(-SPAWN_RANDOM_OFFSET, SPAWN_RANDOM_OFFSET);
        Vector3d position = new Vector3d(x, point.y(), z);
        Vector3f rotation = new Vector3f(0, point.yaw(), 0);

        try {
            Object result = npcPlugin.spawnNPC(store, variantConfig.effectiveNpcType(), "", position, rotation);
            if (result == null) {
                return false;
            }
            Ref<EntityStore> entityRef = extractEntityRef(result);
            if (entityRef == null) {
                return false;
            }
            if (session.getState() == SessionState.ENDED) {
                removeZombieEntity(store, entityRef);
                return false;
            }
            session.addAliveZombie(entityRef, variantKey);

            // Apply wave HP scaling + show HP on nameplate
            applyZombieStats(store, entityRef, variantConfig, wave);

            // Disable drops
            clearDropList(store, entityRef);

            // Set target — the boosted sensor range (80 blocks) + LockedTarget should let
            // the natural AI detect the player and transition through its own state machine.
            // We do NOT force setState — forcing an unknown state paralyzes the NPC.
            try {
                NPCEntity npcEntity = store.getComponent(entityRef, NPCEntity.getComponentType());
                if (npcEntity != null && npcEntity.getRole() != null) {
                    Ref<EntityStore> targetRef = session.getRandomAlivePlayerRef();
                    if (targetRef != null) {
                        npcEntity.getRole().setMarkedTarget("LockedTarget", targetRef);
                    }
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to set zombie target");
            }

            ZombieAggroBooster.applySpeedMultiplier(store, entityRef, variantConfig);
            ZombieAggroBooster.boostZombieAggro(store, entityRef);
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to spawn zombie (variant " + variantKey + ")");
        }
        return false;
    }

    private void applyZombieStats(Store<EntityStore> store, Ref<EntityStore> entityRef,
                                   PurgeVariantConfig variant, int wave) {
        try {
            EntityStatMap statMap = store.getComponent(entityRef, EntityStatMap.getComponentType());
            Nameplate nameplate = store.ensureAndGetComponent(entityRef, Nameplate.getComponentType());
            if (statMap != null) {
                int healthIndex = DefaultEntityStatTypes.getHealth();
                boolean modified = false;

                // Force absolute variant HP regardless of NPC archetype base HP (e.g., Zombie_Aberrant).
                EntityStatValue currentHealth = statMap.get(healthIndex);
                float currentMaxHp = currentHealth != null ? currentHealth.getMax() : VANILLA_ZOMBIE_HP;
                if (currentMaxHp <= 0f) {
                    currentMaxHp = VANILLA_ZOMBIE_HP;
                }
                float variantHpDelta = variant.baseHealth() - currentMaxHp;
                if (Math.abs(variantHpDelta) > 0.01f) {
                    statMap.putModifier(healthIndex, PURGE_VARIANT_HP_MODIFIER,
                            new StaticModifier(Modifier.ModifierTarget.MAX,
                                    StaticModifier.CalculationType.ADDITIVE, variantHpDelta));
                    modified = true;
                }

                // Apply wave HP scaling
                double hpMult = hpMultiplier(wave);
                if (hpMult > 1.0) {
                    statMap.putModifier(healthIndex, PURGE_HP_MODIFIER,
                            new StaticModifier(Modifier.ModifierTarget.MAX,
                                    StaticModifier.CalculationType.MULTIPLICATIVE, (float) hpMult));
                    modified = true;
                }

                if (modified) {
                    statMap.update();
                }
                statMap.maximizeStatValue(healthIndex);
                EntityStatValue health = statMap.get(healthIndex);
                if (health != null) {
                    int hp = Math.round(health.getMax());
                    nameplate.setText(hp + " / " + hp);
                }
            } else {
                nameplate.setText("");
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to apply zombie stats");
        }
    }

    private void clearDropList(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        try {
            NPCEntity npcEntity = store.getComponent(entityRef, NPCEntity.getComponentType());
            if (npcEntity == null || npcEntity.getRole() == null) {
                return;
            }
            java.lang.reflect.Field field = UnsafeReflectionHelper.resolveDropListField(npcEntity.getRole());
            if (field == null) {
                return;
            }
            field.set(npcEntity.getRole(), "Empty");
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to clear drop list: " + e.getMessage());
        }
    }

    private void startWaveTick(PurgeSession session) {
        ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                if (session.getState() == SessionState.ENDED) {
                    ScheduledFuture<?> wt = session.getWaveTick();
                    if (wt != null) {
                        wt.cancel(false);
                    }
                    return;
                }
                updateWaveWorldState(session);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Wave tick error");
            }
        }, WAVE_TICK_INTERVAL_MS, WAVE_TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        session.setWaveTick(task);
    }

    private void updateWaveWorldState(PurgeSession session) {
        World world = getPurgeWorld();
        if (world == null) {
            return;
        }
        world.execute(() -> {
            try {
                if (session.getState() == SessionState.ENDED) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                if (store == null) {
                    return;
                }
                deathTracker.checkZombieDeaths(session, store);
                ZombieAggroBooster.refreshZombieAggro(session, store);
                updateZombieNameplates(session, store);
                updatePlayerHealthHud(session, store, world);
                registry.getClassManager().tickMedicRegen(session, store);

                int alivePlayers = session.getAliveConnectedCount();
                int aliveZombies = session.getAliveZombieCount();
                int totalZombies = session.getWaveZombieCount();

                if (alivePlayers == 0) {
                    if (tryBeginSessionTransition(session)) {
                        registry.getSessionManager().stopSessionById(session.getSessionId(), "team wiped");
                    }
                    return;
                }

                session.forEachConnectedParticipant(
                        pid -> hudManager.updateWaveStatus(pid, session.getCurrentWave(), aliveZombies, totalZombies));

                if (session.isSpawningComplete() && aliveZombies == 0) {
                    if (totalZombies == 0 || session.getWaveSpawnSuccesses() > 0) {
                        if (tryBeginSessionTransition(session)) {
                            progressionController.onWaveComplete(session);
                        }
                    } else if (tryBeginSessionTransition(session)) {
                        sendMessageToAll(session,
                                "Session ended due to a technical wave spawn failure. Please contact an admin.");
                        LOGGER.atWarning().log("Ending session after spawn failure: session="
                                + session.getSessionId()
                                + " wave=" + session.getCurrentWave()
                                + " attempted=" + session.getWaveSpawnAttempts()
                                + " spawned=" + session.getWaveSpawnSuccesses());
                        registry.getSessionManager().stopSessionById(session.getSessionId(), "wave spawn failure");
                    }
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to process wave world updates");
            }
        });
    }

    private void updateZombieNameplates(PurgeSession session, Store<EntityStore> store) {
        int healthIndex = DefaultEntityStatTypes.getHealth();
        for (Ref<EntityStore> ref : session.getAliveZombies()) {
            if (ref == null || !ref.isValid()) {
                continue;
            }
            try {
                EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                Nameplate nameplate = store.getComponent(ref, Nameplate.getComponentType());
                if (statMap != null && nameplate != null) {
                    EntityStatValue health = statMap.get(healthIndex);
                    if (health != null) {
                        int current = Math.round(health.get());
                        int max = Math.round(health.getMax());
                        if (current <= 0) {
                            nameplate.setText("");
                        } else {
                            nameplate.setText(current + " / " + max);
                        }
                    } else {
                        nameplate.setText("");
                    }
                }
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to update one zombie nameplate: " + e.getMessage());
            }
        }
    }

    private void updatePlayerHealthHud(PurgeSession session, Store<EntityStore> store, World world) {
        PurgeMapInstance instance = instanceManager.getInstance(session.getInstanceId());
        boolean[] anyDied = {false};

        session.forEachAliveConnectedPlayerState(ps -> {
            UUID pid = ps.getPlayerId();
            Ref<EntityStore> ref = ps.getPlayerRef();

            boolean dead = false;
            if (ref == null || !ref.isValid()) {
                dead = true;
            } else {
                try {
                    EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                    if (statMap != null) {
                        EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
                        if (health != null) {
                            int current = Math.round(health.get());
                            int max = Math.round(health.getMax());
                            hudManager.updatePlayerHealth(pid, current, max);
                            if (current <= 0) {
                                dead = true;
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.atFine().log("Failed to update player HP HUD: " + e.getMessage());
                }
            }

            if (dead && session.getState() != SessionState.ENDED) {
                handlePlayerDeath(session, pid, ps, store, world, instance);
                anyDied[0] = true;
            }
        });

        if (anyDied[0]) {
            retargetZombies(session, store);
        }
    }

    private void handlePlayerDeath(PurgeSession session, UUID playerId, PurgeSessionPlayerState ps,
                                    Store<EntityStore> store, World world, PurgeMapInstance instance) {
        session.markDeadThisWave(playerId);
        DamageBypassRegistry.remove(playerId);

        Ref<EntityStore> ref = ps.getPlayerRef();
        Player player = null;
        if (ref != null && ref.isValid()) {
            try {
                player = store.getComponent(ref, Player.getComponentType());
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to resolve dead player component for " + playerId + ": " + e.getMessage());
            }

            // Heal full
            try {
                EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    statMap.maximizeStatValue(DefaultEntityStatTypes.getHealth());
                }
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to heal dead player: " + e.getMessage());
            }

            // Clear inventory, give quit orb only
            if (player != null && loadoutService != null) {
                try {
                    loadoutService.giveWaitingLoadout(player);
                } catch (Exception e) {
                    LOGGER.atFine().log("Failed to update dead player inventory: " + e.getMessage());
                }
            }

            // Teleport to waiting area (start point)
            if (instance != null) {
                try {
                    PurgeLocation loc = instance.startPoint();
                    if (loc != null) {
                        store.addComponent(ref, Teleport.getComponentType(),
                                new Teleport(world, loc.toPosition(), loc.toRotation()));
                    }
                } catch (Exception e) {
                    LOGGER.atFine().log("Failed to teleport dead player: " + e.getMessage());
                }
            }

            // Send death message to this player
            if (player != null) {
                try {
                    player.sendMessage(Message.raw("You died! Waiting for wave clear to revive..."));
                } catch (Exception e) {
                    LOGGER.atFine().log("Failed to send death message to " + playerId + ": " + e.getMessage());
                }
            }
        }

        // Broadcast to alive teammates
        String name = PurgePlayerNameResolver.resolve(playerId, PurgePlayerNameResolver.FallbackStyle.SHORT_UUID);
        for (UUID teammatePid : session.getAliveConnectedParticipants()) {
            PurgeSessionPlayerState tps = session.getPlayerState(teammatePid);
            if (tps == null) continue;
            Ref<EntityStore> tRef = tps.getPlayerRef();
            if (tRef == null || !tRef.isValid()) continue;
            try {
                Player tp = store.getComponent(tRef, Player.getComponentType());
                if (tp != null) {
                    tp.sendMessage(Message.raw(name + " is down!"));
                }
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to notify teammate " + teammatePid + ": " + e.getMessage());
            }
        }
    }

    private void retargetZombies(PurgeSession session, Store<EntityStore> store) {
        List<Ref<EntityStore>> aliveSnapshot = new ArrayList<>(session.getAliveZombies());
        List<Ref<EntityStore>> invalidRefs = null;
        for (Ref<EntityStore> zombieRef : aliveSnapshot) {
            if (zombieRef == null || !zombieRef.isValid()) {
                if (zombieRef != null) {
                    if (invalidRefs == null) {
                        invalidRefs = new ArrayList<>();
                    }
                    invalidRefs.add(zombieRef);
                }
                continue;
            }
            try {
                NPCEntity npc = store.getComponent(zombieRef, NPCEntity.getComponentType());
                if (npc != null && npc.getRole() != null) {
                    Ref<EntityStore> newTarget = session.getRandomAlivePlayerRef();
                    if (newTarget != null) {
                        npc.getRole().setMarkedTarget("LockedTarget", newTarget);
                    }
                }
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to retarget zombie: " + e.getMessage());
            }
        }
        if (invalidRefs != null) {
            for (Ref<EntityStore> invalidRef : invalidRefs) {
                session.removeAliveZombie(invalidRef);
            }
        }
    }

    public void startIntermission(PurgeSession session) {
        progressionController.startIntermission(session);
    }

    // --- Cleanup ---

    public CompletableFuture<Void> removeAllZombies(PurgeSession session) {
        CompletableFuture<Void> cleanupFuture = new CompletableFuture<>();
        World world = getPurgeWorld();
        Set<Ref<EntityStore>> toRemove = session.drainAliveZombies();
        if (world == null || toRemove.isEmpty()) {
            cleanupFuture.complete(null);
            return cleanupFuture;
        }
        try {
            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    if (store == null) {
                        cleanupFuture.complete(null);
                        return;
                    }
                    for (Ref<EntityStore> ref : toRemove) {
                        if (ref == null || !ref.isValid()) {
                            continue;
                        }
                        try {
                            store.removeEntity(ref, RemoveReason.REMOVE);
                        } catch (Exception e) {
                            LOGGER.atFine().log("Failed to remove one zombie during cleanup: " + e.getMessage());
                        }
                    }
                    cleanupFuture.complete(null);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Failed to remove zombies");
                    cleanupFuture.completeExceptionally(e);
                }
            });
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to schedule zombie cleanup");
            cleanupFuture.completeExceptionally(e);
        }
        return cleanupFuture;
    }

    // --- Revive ---

    void revivePlayersDownedThisWave(PurgeSession session) {
        Set<UUID> toRevive = session.getDeadThisWaveParticipants();
        if (toRevive.isEmpty()) return;

        // Flip state flags synchronously (before upgrade popup checks alive count)
        for (UUID pid : toRevive) {
            PurgeSessionPlayerState ps = session.getPlayerState(pid);
            if (ps == null || !ps.isConnected()) continue;
            Ref<EntityStore> ref = ps.getPlayerRef();
            if (ref == null || !ref.isValid()) continue;
            ps.setAlive(true);
            ps.setDeadThisWave(false);
            DamageBypassRegistry.add(pid);
        }

        // World operations (heal, loadout, teleport, message)
        World world = getPurgeWorld();
        if (world == null) return;

        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                if (store == null) return;
                PurgeMapInstance instance = instanceManager.getInstance(session.getInstanceId());

                for (UUID pid : toRevive) {
                    PurgeSessionPlayerState ps = session.getPlayerState(pid);
                    if (ps == null || !ps.isConnected()) continue;
                    Ref<EntityStore> ref = ps.getPlayerRef();
                    if (ref == null || !ref.isValid()) continue;

                    try {
                        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                        if (statMap != null) {
                            statMap.maximizeStatValue(DefaultEntityStatTypes.getHealth());
                        }
                    } catch (Exception e) {
                        LOGGER.atFine().log("Failed to heal revived player: " + e.getMessage());
                    }

                    Player player = null;
                    try {
                        player = store.getComponent(ref, Player.getComponentType());
                        if (player != null && loadoutService != null) {
                            loadoutService.grantLoadout(player, ps);
                        }
                    } catch (Exception e) {
                        LOGGER.atFine().log("Failed to re-grant loadout: " + e.getMessage());
                    }

                    // Re-apply ammo upgrade to the fresh weapon ItemStack
                    try {
                        if (player != null) {
                            registry.getUpgradeManager().reapplyAmmoUpgrade(session, pid, player);
                        }
                    } catch (Exception e) {
                        LOGGER.atFine().log("Failed to re-apply ammo upgrade: " + e.getMessage());
                    }

                    if (instance != null) {
                        try {
                            PurgeLocation loc = instance.startPoint();
                            if (loc != null) {
                                store.addComponent(ref, Teleport.getComponentType(),
                                        new Teleport(world, loc.toPosition(), loc.toRotation()));
                            }
                        } catch (Exception e) {
                            LOGGER.atFine().log("Failed to teleport revived player: " + e.getMessage());
                        }
                    }

                    if (player != null) {
                        try {
                            player.sendMessage(Message.raw("You have been revived!"));
                        } catch (Exception e) {
                            LOGGER.atFine().log("Failed to send revive message to " + pid + ": " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to revive players");
            }
        });
    }

    // --- Utility ---

    private double[] getRefPosition(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        try {
            Store<EntityStore> store = ref.getStore();
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null && transform.getPosition() != null) {
                Vector3d pos = transform.getPosition();
                return new double[]{pos.getX(), pos.getY(), pos.getZ()};
            }
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to read position: " + e.getMessage());
        }
        return null;
    }

    void sendMessageToAll(PurgeSession session, String text) {
        session.forEachConnectedParticipant(pid -> {
            PurgeSessionPlayerState ps = session.getPlayerState(pid);
            if (ps == null) {
                return;
            }
            Ref<EntityStore> ref = ps.getPlayerRef();
            if (ref == null || !ref.isValid()) {
                return;
            }
            try {
                Store<EntityStore> store = ref.getStore();
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.sendMessage(Message.raw(text));
                }
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to send wave message: " + e.getMessage());
            }
        });
    }

    private boolean tryBeginSessionTransition(PurgeSession session) {
        return session.getState() != SessionState.ENDED && session.tryBeginTransition();
    }

    World getPurgeWorld() {
        try {
            return Universe.get().getWorld(WorldConstants.WORLD_PURGE);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Ref<EntityStore> extractEntityRef(Object pairResult) {
        if (pairResult == null) {
            return null;
        }
        try {
            for (String methodName : List.of("getFirst", "getLeft", "getKey", "first", "left")) {
                try {
                    java.lang.reflect.Method method = pairResult.getClass().getMethod(methodName);
                    Object value = method.invoke(pairResult);
                    if (value instanceof Ref<?> ref) {
                        return (Ref<EntityStore>) ref;
                    }
                } catch (NoSuchMethodException ignored) {
                    // Try next method
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to extract entity ref from NPC result");
        }
        return null;
    }

    private void removeZombieEntity(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        try {
            if (store != null && entityRef != null && entityRef.isValid()) {
                store.removeEntity(entityRef, RemoveReason.REMOVE);
            }
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to remove late zombie spawn: " + e.getMessage());
        }
    }
}
