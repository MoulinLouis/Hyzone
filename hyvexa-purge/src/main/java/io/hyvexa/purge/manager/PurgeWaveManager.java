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
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeSpawnPoint;
import io.hyvexa.purge.data.PurgeUpgradeType;
import io.hyvexa.purge.data.PurgeWaveDefinition;
import io.hyvexa.purge.data.PurgeZombieVariant;
import io.hyvexa.purge.data.SessionState;
import io.hyvexa.purge.hud.PurgeHudManager;
import io.hyvexa.purge.ui.PurgeUpgradePickPage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PurgeWaveManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PURGE_HP_MODIFIER = "purge_wave_hp";
    private static final String PURGE_THICK_HIDE_MODIFIER = "purge_thick_hide";
    private static final long WAVE_TICK_INTERVAL_MS = 200;
    private static final long SPAWN_STAGGER_MS = 500;
    private static final int SPAWN_BATCH_SIZE = 5;
    private static final int INTERMISSION_SECONDS = 5;
    private static final int COUNTDOWN_SECONDS = 5;
    private static final double SPAWN_RANDOM_OFFSET = 2.0;

    private final PurgeSpawnPointManager spawnPointManager;
    private final PurgeWaveConfigManager waveConfigManager;
    private final PurgeHudManager hudManager;
    private volatile NPCPlugin npcPlugin;

    // Set by PurgeSessionManager after construction
    private volatile PurgeSessionManager sessionManager;
    private volatile PurgeUpgradeManager upgradeManager;

    public PurgeWaveManager(PurgeSpawnPointManager spawnPointManager,
                            PurgeWaveConfigManager waveConfigManager,
                            PurgeHudManager hudManager) {
        this.spawnPointManager = spawnPointManager;
        this.waveConfigManager = waveConfigManager;
        this.hudManager = hudManager;
        try {
            this.npcPlugin = NPCPlugin.get();
        } catch (Exception e) {
            LOGGER.atWarning().log("NPCPlugin not available: " + e.getMessage());
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

    public static double speedMultiplier(int wave) {
        return 1.0 + Math.max(0, wave - 4) * 0.025;
    }

    public static double damageMultiplier(int wave) {
        return 1.0 + Math.max(0, wave - 4) * 0.05;
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
                sendMessage(session, "Wave " + (session.getCurrentWave() + 1) + " starting in " + remaining + "...");
                hudManager.updateIntermission(session.getPlayerId(), remaining);
            } catch (Exception e) {
                LOGGER.atWarning().log("Countdown tick error: " + e.getMessage());
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

        sendMessage(session, "-- Wave " + nextWave + " -- (slow " + wave.slowCount()
                + ", normal " + wave.normalCount() + ", fast " + wave.fastCount() + ")");
        hudManager.updateWaveStatus(session.getPlayerId(), nextWave, totalCount, totalCount);

        List<PurgeZombieVariant> spawnQueue = buildSpawnQueue(wave);
        if (spawnQueue.isEmpty()) {
            markSpawningComplete(session);
            onWaveComplete(session);
            return;
        }

        startSpawning(session, spawnQueue);
        startWaveTick(session);
    }

    private List<PurgeZombieVariant> buildSpawnQueue(PurgeWaveDefinition wave) {
        int slow = Math.max(0, wave.slowCount());
        int normal = Math.max(0, wave.normalCount());
        int fast = Math.max(0, wave.fastCount());

        List<PurgeZombieVariant> queue = new ArrayList<>(wave.totalCount());
        while (slow > 0 || normal > 0 || fast > 0) {
            if (normal > 0) {
                queue.add(PurgeZombieVariant.NORMAL);
                normal--;
            }
            if (slow > 0) {
                queue.add(PurgeZombieVariant.SLOW);
                slow--;
            }
            if (fast > 0) {
                queue.add(PurgeZombieVariant.FAST);
                fast--;
            }
        }
        return queue;
    }

    private void startSpawning(PurgeSession session, List<PurgeZombieVariant> spawnQueue) {
        int totalCount = spawnQueue.size();

        AtomicInteger remaining = new AtomicInteger(totalCount);
        AtomicInteger queueIndex = new AtomicInteger(0);

        ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                if (session.getState() == SessionState.ENDED) {
                    session.cancelSpawnTask();
                    return;
                }
                int batch = Math.min(SPAWN_BATCH_SIZE, remaining.get());
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
                        Store<EntityStore> store = world.getEntityStore().getStore();
                        if (store == null) {
                            return;
                        }
                        double[] currentPos = getPlayerPosition(session);
                        double spawnX = currentPos != null ? currentPos[0] : 0;
                        double spawnZ = currentPos != null ? currentPos[2] : 0;

                        int toSpawn = Math.min(SPAWN_BATCH_SIZE, remaining.get());
                        for (int i = 0; i < toSpawn && remaining.get() > 0; i++) {
                            int idx = queueIndex.getAndIncrement();
                            if (idx >= spawnQueue.size()) {
                                remaining.set(0);
                                break;
                            }

                            PurgeSpawnPoint spawnPoint = spawnPointManager.selectSpawnPoint(spawnX, spawnZ);
                            PurgeZombieVariant variant = spawnQueue.get(idx);
                            boolean spawned = spawnZombie(session, store, spawnPoint, variant, session.getCurrentWave());
                            if (!spawned) {
                                LOGGER.atWarning().log("Wave spawn failed: wave=" + session.getCurrentWave()
                                        + " variant=" + variant.name());
                            }
                            remaining.decrementAndGet();
                        }

                        if (remaining.get() <= 0) {
                            markSpawningComplete(session);
                            session.cancelSpawnTask();
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().log("Spawn batch error: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                LOGGER.atWarning().log("Spawn task error: " + e.getMessage());
            }
        }, 0, SPAWN_STAGGER_MS, TimeUnit.MILLISECONDS);
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
                                PurgeZombieVariant variant,
                                int wave) {
        if (npcPlugin == null) {
            return false;
        }
        if (point == null || variant == null) {
            return false;
        }

        double x = point.x() + ThreadLocalRandom.current().nextDouble(-SPAWN_RANDOM_OFFSET, SPAWN_RANDOM_OFFSET);
        double z = point.z() + ThreadLocalRandom.current().nextDouble(-SPAWN_RANDOM_OFFSET, SPAWN_RANDOM_OFFSET);
        Vector3d position = new Vector3d(x, point.y(), z);
        Vector3f rotation = new Vector3f(0, point.yaw(), 0);

        for (String npcType : candidateNpcTypes(variant)) {
            try {
                Object result = npcPlugin.spawnNPC(store, npcType, "", position, rotation);
                if (result != null) {
                    Ref<EntityStore> entityRef = extractEntityRef(result);
                    if (entityRef != null) {
                        session.addAliveZombie(entityRef);
                        // Apply wave HP scaling + show HP on nameplate
                        try {
                            EntityStatMap statMap = store.getComponent(entityRef, EntityStatMap.getComponentType());
                            Nameplate nameplate = store.ensureAndGetComponent(entityRef, Nameplate.getComponentType());
                            if (statMap != null) {
                                int healthIndex = DefaultEntityStatTypes.getHealth();
                                double hpMult = hpMultiplier(wave);
                                if (hpMult > 1.0) {
                                    statMap.putModifier(healthIndex, PURGE_HP_MODIFIER,
                                            new StaticModifier(Modifier.ModifierTarget.MAX,
                                                    StaticModifier.CalculationType.MULTIPLICATIVE, (float) hpMult));
                                    statMap.update();
                                }
                                // Apply Thick Hide damage reduction (reduces zombie HP as proxy)
                                PurgeUpgradeManager um = upgradeManager;
                                if (um != null) {
                                    double dmgMult = um.getZombieDamageMultiplier(session);
                                    if (dmgMult < 1.0) {
                                        statMap.putModifier(healthIndex, PURGE_THICK_HIDE_MODIFIER,
                                                new StaticModifier(Modifier.ModifierTarget.MAX,
                                                        StaticModifier.CalculationType.MULTIPLICATIVE, (float) dmgMult));
                                        statMap.update();
                                    }
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
                            LOGGER.atWarning().log("Failed to apply zombie stats: " + e.getMessage());
                        }
                        // Force aggro on player immediately
                        try {
                            NPCEntity npcEntity = store.getComponent(entityRef, NPCEntity.getComponentType());
                            if (npcEntity != null && npcEntity.getRole() != null) {
                                npcEntity.getRole().setMarkedTarget("LockedTarget", session.getPlayerRef());
                                npcEntity.getRole().getStateSupport().setState(entityRef, "Angry", "", store);
                            }
                        } catch (Exception e) {
                            LOGGER.atWarning().log("Failed to set zombie aggro: " + e.getMessage());
                        }
                        return true;
                    }
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to spawn zombie with role " + npcType
                        + " (variant " + variant.name() + "): " + e.getMessage());
            }
        }
        return false;
    }

    private List<String> candidateNpcTypes(PurgeZombieVariant variant) {
        return switch (variant) {
            case SLOW -> List.of(PurgeZombieVariant.SLOW.getNpcType(), PurgeZombieVariant.NORMAL.getNpcType());
            case NORMAL -> List.of(PurgeZombieVariant.NORMAL.getNpcType(), "Purge_Zombie_Normal");
            case FAST -> List.of(PurgeZombieVariant.FAST.getNpcType(), PurgeZombieVariant.NORMAL.getNpcType());
        };
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

                // Check player death
                Ref<EntityStore> playerRef = session.getPlayerRef();
                if (playerRef == null || !playerRef.isValid()) {
                    PurgeSessionManager sm = sessionManager;
                    if (sm != null) {
                        sm.stopSession(session.getPlayerId(), "death");
                    }
                    return;
                }

                checkZombieDeaths(session);
                updateZombieNameplates(session);
                updatePlayerHealthHud(session);

                // Update HUD with alive count
                int alive = session.getAliveZombieCount();
                int total = session.getWaveZombieCount();
                hudManager.updateWaveStatus(session.getPlayerId(), session.getCurrentWave(), alive, total);

                if (session.isSpawningComplete() && alive == 0) {
                    onWaveComplete(session);
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("Wave tick error: " + e.getMessage());
            }
        }, WAVE_TICK_INTERVAL_MS, WAVE_TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        session.setWaveTick(task);
    }

    private void checkZombieDeaths(PurgeSession session) {
        Set<Ref<EntityStore>> dead = new HashSet<>();
        for (Ref<EntityStore> ref : session.getAliveZombies()) {
            if (ref == null || !ref.isValid()) {
                dead.add(ref);
                session.incrementKills();
            }
        }
        session.getAliveZombies().removeAll(dead);
    }

    private void updateZombieNameplates(PurgeSession session) {
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
                        // Ignore per-zombie errors
                    }
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to update zombie nameplates: " + e.getMessage());
            }
        });
    }

    private void updatePlayerHealthHud(PurgeSession session) {
        Ref<EntityStore> ref = session.getPlayerRef();
        if (ref == null || !ref.isValid()) {
            return;
        }
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
                Ref<EntityStore> playerRef = session.getPlayerRef();
                if (playerRef == null || !playerRef.isValid()) {
                    return;
                }
                EntityStatMap statMap = store.getComponent(playerRef, EntityStatMap.getComponentType());
                if (statMap != null) {
                    EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
                    if (health != null) {
                        int current = Math.round(health.get());
                        int max = Math.round(health.getMax());
                        hudManager.updatePlayerHealth(session.getPlayerId(), current, max);
                        if (current <= 0 && session.getState() != SessionState.ENDED) {
                            PurgeSessionManager sm = sessionManager;
                            if (sm != null) {
                                sm.stopSession(session.getPlayerId(), "death");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore health read errors
            }
        });
    }

    private void onWaveComplete(PurgeSession session) {
        // Cancel wave tick
        ScheduledFuture<?> wt = session.getWaveTick();
        if (wt != null) {
            wt.cancel(false);
            session.setWaveTick(null);
        }

        sendMessage(session, "Wave " + session.getCurrentWave() + " complete! (" + session.getTotalKills() + " total kills)");

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
            // No upgrade manager wired — skip straight to intermission
            session.setState(SessionState.INTERMISSION);
            startIntermission(session);
            return;
        }

        List<PurgeUpgradeType> offered = um.selectRandomUpgrades(3);
        Ref<EntityStore> playerRef = session.getPlayerRef();
        if (playerRef == null || !playerRef.isValid()) {
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
                Ref<EntityStore> ref = session.getPlayerRef();
                if (ref == null || !ref.isValid()) {
                    session.setState(SessionState.INTERMISSION);
                    startIntermission(session);
                    return;
                }

                Player player = store.getComponent(ref, Player.getComponentType());
                PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (player == null || pRef == null) {
                    session.setState(SessionState.INTERMISSION);
                    startIntermission(session);
                    return;
                }

                Runnable onComplete = () -> {
                    if (session.getState() == SessionState.ENDED) {
                        return;
                    }
                    session.setState(SessionState.INTERMISSION);
                    startIntermission(session);
                };

                PurgeUpgradePickPage page = new PurgeUpgradePickPage(pRef, session, um, offered, onComplete);
                player.getPageManager().openCustomPage(ref, store, page);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to show upgrade popup: " + e.getMessage());
                session.setState(SessionState.INTERMISSION);
                startIntermission(session);
            }
        });
    }

    private void handleVictory(PurgeSession session) {
        if (session.getState() == SessionState.ENDED) {
            return;
        }
        sendMessage(session, "You won! You cleared all configured Purge waves.");
        PurgeSessionManager sm = sessionManager;
        if (sm != null) {
            World world = getPurgeWorld();
            if (world != null) {
                world.execute(() -> sm.stopSession(session.getPlayerId(), "victory"));
            } else {
                sm.stopSession(session.getPlayerId(), "victory");
            }
        }
    }

    private void startIntermission(PurgeSession session) {
        AtomicInteger countdown = new AtomicInteger(INTERMISSION_SECONDS);
        // TODO: Heal player to full (API discovery needed)

        ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                if (session.getState() == SessionState.ENDED) {
                    session.cancelIntermissionTask();
                    return;
                }
                int remaining = countdown.decrementAndGet();
                if (remaining <= 0) {
                    session.cancelIntermissionTask();
                    startNextWave(session);
                    return;
                }
                hudManager.updateIntermission(session.getPlayerId(), remaining);
                sendMessage(session, "Next wave in " + remaining + "...");
            } catch (Exception e) {
                LOGGER.atWarning().log("Intermission tick error: " + e.getMessage());
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
        session.setIntermissionTask(task);
    }

    // --- Cleanup ---

    public void removeAllZombies(PurgeSession session) {
        World world = getPurgeWorld();
        for (Ref<EntityStore> ref : session.getAliveZombies()) {
            if (ref != null && ref.isValid()) {
                if (world != null) {
                    world.execute(() -> {
                        try {
                            Store<EntityStore> store = ref.getStore();
                            if (store != null) {
                                store.removeEntity(ref, RemoveReason.REMOVE);
                            }
                        } catch (Exception e) {
                            LOGGER.atWarning().log("Failed to remove zombie: " + e.getMessage());
                        }
                    });
                }
            }
        }
        session.getAliveZombies().clear();
    }

    // --- Utility ---

    private double[] getPlayerPosition(PurgeSession session) {
        Ref<EntityStore> ref = session.getPlayerRef();
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
            // Ignore position read errors
        }
        return null;
    }

    private void sendMessage(PurgeSession session, String text) {
        Ref<EntityStore> ref = session.getPlayerRef();
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
            // Ignore message send errors
        }
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
            LOGGER.atWarning().log("Failed to extract entity ref from NPC result: " + e.getMessage());
        }
        return null;
    }
}
