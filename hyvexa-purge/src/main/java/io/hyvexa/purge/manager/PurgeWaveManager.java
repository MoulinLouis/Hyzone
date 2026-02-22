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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.common.util.DamageBypassRegistry;
import io.hyvexa.purge.HyvexaPurgePlugin;
import io.hyvexa.purge.data.PurgeLocation;
import io.hyvexa.purge.data.PurgeMapInstance;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeSessionPlayerState;
import io.hyvexa.purge.data.PurgeSpawnPoint;
import io.hyvexa.purge.data.PurgeUpgradeType;
import io.hyvexa.purge.data.PurgeVariantConfig;
import io.hyvexa.purge.data.PurgeWaveDefinition;
import io.hyvexa.purge.data.SessionState;
import io.hyvexa.purge.hud.PurgeHudManager;
import io.hyvexa.purge.ui.PurgeUpgradePickPage;
import io.hyvexa.purge.util.PurgePlayerNameResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final int INTERMISSION_SECONDS = 5;
    private static final int COUNTDOWN_SECONDS = 5;
    private static final int UPGRADE_TIMEOUT_SECONDS = 15;
    private static final double SPAWN_RANDOM_OFFSET = 2.0;
    private static final ConcurrentHashMap<Class<?>, Long> MAX_SPEED_OFFSET_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, java.lang.reflect.Field> DROP_LIST_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Set<Class<?>> DROP_LIST_FIELD_MISSING = ConcurrentHashMap.newKeySet();
    private static volatile java.lang.reflect.Field MOTION_CONTROLLERS_FIELD;
    private static volatile sun.misc.Unsafe UNSAFE_INSTANCE;

    private final PurgeInstanceManager instanceManager;
    private final PurgeWaveConfigManager waveConfigManager;
    private final PurgeVariantConfigManager variantConfigManager;
    private final PurgeHudManager hudManager;
    private volatile NPCPlugin npcPlugin;

    // Set by PurgeSessionManager after construction
    private volatile PurgeSessionManager sessionManager;
    private volatile PurgeUpgradeManager upgradeManager;

    public PurgeWaveManager(PurgeInstanceManager instanceManager,
                            PurgeWaveConfigManager waveConfigManager,
                            PurgeVariantConfigManager variantConfigManager,
                            PurgeHudManager hudManager) {
        this.instanceManager = instanceManager;
        this.waveConfigManager = waveConfigManager;
        this.variantConfigManager = variantConfigManager;
        this.hudManager = hudManager;
        try {
            this.npcPlugin = NPCPlugin.get();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("NPCPlugin not available");
        }
    }

    public void setSessionManager(PurgeSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void setUpgradeManager(PurgeUpgradeManager upgradeManager) {
        this.upgradeManager = upgradeManager;
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
        session.setState(SessionState.COUNTDOWN);
        AtomicInteger countdown = new AtomicInteger(COUNTDOWN_SECONDS);

        ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                if (session.getState() == SessionState.ENDED) {
                    session.cancelAllTasks();
                    return;
                }
                int remaining = countdown.getAndDecrement();
                if (remaining <= 0) {
                    session.cancelSpawnTask(); // reusing spawn task slot for countdown
                    startNextWave(session);
                    return;
                }
                sendMessageToAll(session, "Wave " + (session.getCurrentWave() + 1) + " starting in " + remaining + "...");
                session.forEachConnectedParticipant(pid -> hudManager.updateIntermission(pid, remaining));
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Countdown tick error");
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        session.setSpawnTask(task);
    }

    public void startNextWave(PurgeSession session) {
        int nextWave = session.getCurrentWave() + 1;
        PurgeWaveDefinition wave = waveConfigManager.getWave(nextWave);
        if (wave == null) {
            handleVictory(session);
            return;
        }

        session.setCurrentWave(nextWave);
        int totalCount = wave.totalCount();
        session.setWaveZombieCount(totalCount);
        session.setSpawningComplete(false);
        session.setState(SessionState.SPAWNING);

        sendMessageToAll(session, "-- Wave " + nextWave + " -- (" + wave.totalCount() + " zombies, "
                + wave.spawnDelayMs() + "ms delay, batch " + wave.spawnBatchSize() + ")");
        session.forEachConnectedParticipant(pid -> hudManager.updateWaveStatus(pid, nextWave, totalCount, totalCount));

        List<String> spawnQueue = buildSpawnQueue(wave);
        if (spawnQueue.isEmpty()) {
            markSpawningComplete(session);
            onWaveComplete(session);
            return;
        }

        startSpawning(session, spawnQueue, wave);
        startWaveTick(session);
    }

    private List<String> buildSpawnQueue(PurgeWaveDefinition wave) {
        // Build remaining counts map
        Map<String, Integer> remaining = new java.util.LinkedHashMap<>();
        for (String key : wave.getVariantKeys()) {
            int count = Math.max(0, wave.getCount(key));
            if (count > 0) {
                remaining.put(key, count);
            }
        }

        List<String> queue = new ArrayList<>(wave.totalCount());
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

    private void startSpawning(PurgeSession session, List<String> spawnQueue, PurgeWaveDefinition wave) {
        int totalCount = spawnQueue.size();

        AtomicInteger remaining = new AtomicInteger(totalCount);
        AtomicInteger queueIndex = new AtomicInteger(0);

        ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                if (session.getState() == SessionState.ENDED) {
                    session.cancelSpawnTask();
                    return;
                }
                int batch = Math.min(wave.spawnBatchSize(), remaining.get());
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

                        int toSpawn = Math.min(wave.spawnBatchSize(), remaining.get());
                        for (int i = 0; i < toSpawn && remaining.get() > 0; i++) {
                            if (session.getState() == SessionState.ENDED) {
                                return;
                            }
                            int idx = queueIndex.getAndIncrement();
                            if (idx >= spawnQueue.size()) {
                                remaining.set(0);
                                break;
                            }

                            PurgeSpawnPoint spawnPoint = instanceManager.selectSpawnPoint(instance, spawnX, spawnZ);
                            String variantKey = spawnQueue.get(idx);
                            boolean spawned = spawnZombie(session, store, spawnPoint, variantKey, session.getCurrentWave());
                            if (!spawned) {
                                LOGGER.atWarning().log("Wave spawn failed: wave=" + session.getCurrentWave()
                                        + " variant=" + variantKey);
                            }
                            remaining.decrementAndGet();
                        }

                        if (remaining.get() <= 0) {
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

            // Force aggro on a random alive player
            try {
                NPCEntity npcEntity = store.getComponent(entityRef, NPCEntity.getComponentType());
                if (npcEntity != null && npcEntity.getRole() != null) {
                    Ref<EntityStore> targetRef = session.getRandomAlivePlayerRef();
                    if (targetRef != null) {
                        npcEntity.getRole().setMarkedTarget("LockedTarget", targetRef);
                        npcEntity.getRole().getStateSupport().setState(entityRef, "Angry", "", store);
                    }
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to set zombie aggro");
            }

            // Apply speed multiplier to all motion controllers
            applySpeedMultiplier(store, entityRef, variantConfig);
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

                // Apply variant base HP (adjust from vanilla 49 to variant's baseHealth)
                if (variant.baseHealth() != VANILLA_ZOMBIE_HP) {
                    float hpRatio = (float) variant.baseHealth() / VANILLA_ZOMBIE_HP;
                    statMap.putModifier(healthIndex, PURGE_VARIANT_HP_MODIFIER,
                            new StaticModifier(Modifier.ModifierTarget.MAX,
                                    StaticModifier.CalculationType.MULTIPLICATIVE, hpRatio));
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

    private void applySpeedMultiplier(Store<EntityStore> store, Ref<EntityStore> entityRef,
                                       PurgeVariantConfig variant) {
        if (variant.speedMultiplier() == 1.0) {
            return;
        }
        try {
            NPCEntity npcEntity = store.getComponent(entityRef, NPCEntity.getComponentType());
            if (npcEntity == null || npcEntity.getRole() == null) {
                return;
            }
            // Modify maxHorizontalSpeed (final field) on all motion controllers.
            // horizontalSpeedMultiplier is reset to 1.0 every tick by the engine, so we scale the base speed instead.
            Map<String, ?> controllers = getMotionControllers(npcEntity.getRole());
            if (controllers == null || controllers.isEmpty()) {
                return;
            }
            sun.misc.Unsafe unsafe = getUnsafe();
            if (unsafe == null) {
                LOGGER.atWarning().log("Unsafe not available, cannot apply speed multiplier");
                return;
            }
            int applied = 0;
            for (Map.Entry<String, ?> entry : controllers.entrySet()) {
                long offset = resolveMaxSpeedFieldOffset(unsafe, entry.getValue());
                if (offset >= 0) {
                    double baseSpeed = unsafe.getDouble(entry.getValue(), offset);
                    unsafe.putDouble(entry.getValue(), offset, baseSpeed * variant.speedMultiplier());
                    applied++;
                }
            }
            LOGGER.atInfo().log("Applied speed x" + variant.speedMultiplier() + " to " + applied + "/" + controllers.size()
                    + " motion controllers for variant " + variant.key());
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to apply speed multiplier for " + variant.key() + ": " + e.getMessage());
        }
    }

    private static sun.misc.Unsafe getUnsafe() {
        if (UNSAFE_INSTANCE != null) return UNSAFE_INSTANCE;
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE_INSTANCE = (sun.misc.Unsafe) f.get(null);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to get Unsafe: " + e.getMessage());
        }
        return UNSAFE_INSTANCE;
    }

    private long resolveMaxSpeedFieldOffset(sun.misc.Unsafe unsafe, Object controller) {
        Class<?> controllerClass = controller.getClass();
        Long cached = MAX_SPEED_OFFSET_CACHE.get(controllerClass);
        if (cached != null) {
            return cached;
        }
        // maxHorizontalSpeed is declared on MotionControllerBase, search up hierarchy
        Class<?> clazz = controllerClass;
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField("maxHorizontalSpeed");
                long offset = unsafe.objectFieldOffset(field);
                MAX_SPEED_OFFSET_CACHE.put(controllerClass, offset);
                return offset;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        MAX_SPEED_OFFSET_CACHE.put(controllerClass, -1L);
        LOGGER.atWarning().log("maxHorizontalSpeed field not found in " + controllerClass.getName() + " hierarchy");
        return -1;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> getMotionControllers(Object role) {
        try {
            if (MOTION_CONTROLLERS_FIELD == null) {
                // motionControllers is declared on Role parent class, not concrete subclass
                Class<?> clazz = role.getClass();
                while (clazz != null) {
                    try {
                        MOTION_CONTROLLERS_FIELD = clazz.getDeclaredField("motionControllers");
                        MOTION_CONTROLLERS_FIELD.setAccessible(true);
                        break;
                    } catch (NoSuchFieldException e) {
                        clazz = clazz.getSuperclass();
                    }
                }
                if (MOTION_CONTROLLERS_FIELD == null) {
                    LOGGER.atWarning().log("motionControllers field not found in " + role.getClass().getName() + " hierarchy");
                    return null;
                }
            }
            return (Map<String, ?>) MOTION_CONTROLLERS_FIELD.get(role);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to access motionControllers map: " + e.getMessage());
            return null;
        }
    }

    private void clearDropList(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        try {
            NPCEntity npcEntity = store.getComponent(entityRef, NPCEntity.getComponentType());
            if (npcEntity == null || npcEntity.getRole() == null) {
                return;
            }
            java.lang.reflect.Field field = resolveDropListField(npcEntity.getRole());
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

                checkZombieDeaths(session);
                updateWaveWorldState(session);

                // Team wipe check (after HP loop processes deaths)
                if (session.getAliveConnectedCount() == 0) {
                    PurgeSessionManager sm = sessionManager;
                    if (sm != null) {
                        sm.stopSessionById(session.getSessionId(), "team wiped");
                    }
                    return;
                }

                // Update HUD for all connected players
                int alive = session.getAliveZombieCount();
                int total = session.getWaveZombieCount();
                session.forEachConnectedParticipant(
                        pid -> hudManager.updateWaveStatus(pid, session.getCurrentWave(), alive, total));

                if (session.isSpawningComplete() && alive == 0) {
                    onWaveComplete(session);
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Wave tick error");
            }
        }, WAVE_TICK_INTERVAL_MS, WAVE_TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        session.setWaveTick(task);
    }

    private void checkZombieDeaths(PurgeSession session) {
        List<Ref<EntityStore>> dead = null;
        for (Ref<EntityStore> ref : session.getAliveZombies()) {
            if (ref == null || !ref.isValid()) {
                if (dead == null) {
                    dead = new ArrayList<>();
                }
                dead.add(ref);
            }
        }
        if (dead == null || dead.isEmpty()) {
            return;
        }
        for (Ref<EntityStore> deadRef : dead) {
            session.removeAliveZombie(deadRef);
        }
        // Shared kills: all alive connected players get +1 per zombie death
        for (int i = 0; i < dead.size(); i++) {
            session.forEachAliveConnectedPlayerState(PurgeSessionPlayerState::incrementKills);
        }

        // Lootbox drop: configurable % chance per dead zombie per alive player
        HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
        if (plugin != null) {
            double dropChance = plugin.getWeaponConfigManager().getLootboxDropChance();
            World world = getPurgeWorld();
            if (world != null && dropChance > 0) {
                for (int i = 0; i < dead.size(); i++) {
                    session.forEachAliveConnectedPlayerState(ps -> {
                        if (ThreadLocalRandom.current().nextDouble() < dropChance) {
                            Ref<EntityStore> pRef = ps.getPlayerRef();
                            if (pRef != null && pRef.isValid()) {
                                world.execute(() -> {
                                    try {
                                        Store<EntityStore> store = pRef.getStore();
                                        Player player = store.getComponent(pRef, Player.getComponentType());
                                        if (player != null) {
                                            plugin.grantLootbox(player, 1);
                                        }
                                    } catch (Exception e) {
                                        LOGGER.atFine().log("Failed to grant lootbox: " + e.getMessage());
                                    }
                                });
                            }
                        }
                    });
                }
            }
        }
    }

    private void updateWaveWorldState(PurgeSession session) {
        World world = getPurgeWorld();
        if (world == null) {
            return;
        }
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                if (store == null) {
                    return;
                }
                updateZombieNameplates(session, store);
                updatePlayerHealthHud(session, store, world);
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
                        nameplate.setText(current + " / " + max);
                    }
                }
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to update one zombie nameplate: " + e.getMessage());
            }
        }
    }

    private void updatePlayerHealthHud(PurgeSession session, Store<EntityStore> store, World world) {
        PurgeMapInstance instance = instanceManager.getInstance(session.getInstanceId());
        HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
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
                handlePlayerDeath(session, pid, ps, store, world, instance, plugin);
                anyDied[0] = true;
            }
        });

        if (anyDied[0]) {
            retargetZombies(session, store);
        }
    }

    private void handlePlayerDeath(PurgeSession session, UUID playerId, PurgeSessionPlayerState ps,
                                    Store<EntityStore> store, World world, PurgeMapInstance instance,
                                    HyvexaPurgePlugin plugin) {
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
            if (player != null && plugin != null) {
                try {
                    plugin.giveWaitingLoadout(player);
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
        for (Ref<EntityStore> zombieRef : session.getAliveZombies()) {
            if (zombieRef == null || !zombieRef.isValid()) continue;
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
    }

    private void onWaveComplete(PurgeSession session) {
        // Cancel wave tick
        ScheduledFuture<?> wt = session.getWaveTick();
        if (wt != null) {
            wt.cancel(false);
            session.setWaveTick(null);
        }

        // Revive players who died this wave
        revivePlayersDownedThisWave(session);

        // Sum total kills across all players for the summary
        int totalKills = 0;
        for (UUID pid : session.getParticipants()) {
            PurgeSessionPlayerState ps = session.getPlayerState(pid);
            if (ps != null) totalKills += ps.getKills();
        }
        sendMessageToAll(session, "Wave " + session.getCurrentWave() + " complete! (" + totalKills + " total kills)");

        if (!waveConfigManager.hasWave(session.getCurrentWave() + 1)) {
            handleVictory(session);
            return;
        }

        session.setState(SessionState.UPGRADE_PICK);
        showUpgradePopup(session);
    }

    private void showUpgradePopup(PurgeSession session) {
        PurgeUpgradeManager um = upgradeManager;
        if (um == null) {
            session.setState(SessionState.INTERMISSION);
            startIntermission(session);
            return;
        }

        World world = getPurgeWorld();
        if (world == null) {
            session.setState(SessionState.INTERMISSION);
            startIntermission(session);
            return;
        }

        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                if (store == null || session.getState() == SessionState.ENDED) {
                    return;
                }

                boolean anyShown = false;
                for (UUID pid : session.getAliveConnectedParticipants()) {
                    PurgeSessionPlayerState ps = session.getPlayerState(pid);
                    if (ps == null) continue;
                    Ref<EntityStore> ref = ps.getPlayerRef();
                    if (ref == null || !ref.isValid()) continue;

                    Player player = store.getComponent(ref, Player.getComponentType());
                    PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
                    if (player == null || pRef == null) continue;

                    // Per-player random upgrade selection
                    List<PurgeUpgradeType> offered = um.selectRandomUpgrades(3);

                    Runnable onComplete = () -> {
                        if (session.getState() == SessionState.ENDED) {
                            return;
                        }
                        session.getPendingUpgradeChoices().remove(pid);
                        if (session.getPendingUpgradeChoices().isEmpty()) {
                            cancelUpgradeTimeout(session);
                            session.setState(SessionState.INTERMISSION);
                            startIntermission(session);
                        }
                    };

                    session.getPendingUpgradeChoices().add(pid);
                    PurgeUpgradePickPage page = new PurgeUpgradePickPage(pRef, pid, session, um, offered, onComplete);
                    player.getPageManager().openCustomPage(ref, store, page);
                    anyShown = true;
                }

                if (!anyShown) {
                    session.setState(SessionState.INTERMISSION);
                    startIntermission(session);
                } else {
                    // Start 15s timeout for upgrade selection
                    ScheduledFuture<?> timeout = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                        try {
                            if (session.getState() != SessionState.UPGRADE_PICK) {
                                return;
                            }
                            session.getPendingUpgradeChoices().clear();
                            session.setState(SessionState.INTERMISSION);
                            startIntermission(session);
                        } catch (Exception e) {
                            LOGGER.atWarning().withCause(e).log("Upgrade timeout error");
                        }
                    }, UPGRADE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    session.setUpgradeTimeoutTask(timeout);
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to show upgrade popup");
                session.setState(SessionState.INTERMISSION);
                startIntermission(session);
            }
        });
    }

    private void cancelUpgradeTimeout(PurgeSession session) {
        ScheduledFuture<?> task = session.getUpgradeTimeoutTask();
        if (task != null) {
            task.cancel(false);
            session.setUpgradeTimeoutTask(null);
        }
    }

    private void handleVictory(PurgeSession session) {
        if (session.getState() == SessionState.ENDED) {
            return;
        }
        sendMessageToAll(session, "You won! You cleared all configured Purge waves.");
        PurgeSessionManager sm = sessionManager;
        if (sm != null) {
            sm.stopSessionById(session.getSessionId(), "victory");
        }
    }

    public void startIntermission(PurgeSession session) {
        synchronized (session) {
            if (session.getState() == SessionState.ENDED) {
                return;
            }
            ScheduledFuture<?> existing = session.getIntermissionTask();
            if (existing != null && !existing.isDone() && !existing.isCancelled()) {
                return;
            }
            session.cancelIntermissionTask();
            if (session.getState() != SessionState.INTERMISSION) {
                session.setState(SessionState.INTERMISSION);
            }
            AtomicInteger countdown = new AtomicInteger(INTERMISSION_SECONDS);

            ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
                try {
                    if (session.getState() == SessionState.ENDED) {
                        session.cancelIntermissionTask();
                        return;
                    }
                    int remaining = countdown.getAndDecrement();
                    if (remaining <= 0) {
                        session.cancelIntermissionTask();
                        startNextWave(session);
                        return;
                    }
                    session.forEachConnectedParticipant(pid -> hudManager.updateIntermission(pid, remaining));
                    sendMessageToAll(session, "Next wave in " + remaining + "...");
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Intermission tick error");
                }
            }, 1000, 1000, TimeUnit.MILLISECONDS);
            session.setIntermissionTask(task);
        }
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

    private void revivePlayersDownedThisWave(PurgeSession session) {
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
                HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();

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
                        if (player != null && plugin != null) {
                            plugin.grantLoadout(player, ps);
                        }
                    } catch (Exception e) {
                        LOGGER.atFine().log("Failed to re-grant loadout: " + e.getMessage());
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

    private void sendMessageToAll(PurgeSession session, String text) {
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

    private World getPurgeWorld() {
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

    private java.lang.reflect.Field resolveDropListField(Object role) {
        Class<?> roleClass = role.getClass();
        java.lang.reflect.Field cached = DROP_LIST_FIELD_CACHE.get(roleClass);
        if (cached != null) {
            return cached;
        }
        if (DROP_LIST_FIELD_MISSING.contains(roleClass)) {
            return null;
        }
        try {
            java.lang.reflect.Field field = roleClass.getDeclaredField("dropListId");
            field.setAccessible(true);
            DROP_LIST_FIELD_CACHE.put(roleClass, field);
            return field;
        } catch (Exception e) {
            DROP_LIST_FIELD_MISSING.add(roleClass);
            LOGGER.atFine().log("Drop list field not available for " + roleClass.getName() + ": " + e.getMessage());
            return null;
        }
    }
}
