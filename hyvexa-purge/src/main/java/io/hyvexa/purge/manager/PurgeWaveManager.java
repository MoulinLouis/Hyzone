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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeSpawnPoint;
import io.hyvexa.purge.data.SessionState;
import io.hyvexa.purge.hud.PurgeHudManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PurgeWaveManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String ZOMBIE_NPC_TYPE = "Trork_Grunt";
    private static final long WAVE_TICK_INTERVAL_MS = 200;
    private static final long SPAWN_STAGGER_MS = 500;
    private static final int SPAWN_BATCH_SIZE = 5;
    private static final int INTERMISSION_SECONDS = 5;
    private static final int COUNTDOWN_SECONDS = 5;
    private static final double SPAWN_RANDOM_OFFSET = 2.0;

    private final PurgeSpawnPointManager spawnPointManager;
    private final PurgeHudManager hudManager;
    private volatile NPCPlugin npcPlugin;

    // Set by PurgeSessionManager after construction
    private volatile PurgeSessionManager sessionManager;

    public PurgeWaveManager(PurgeSpawnPointManager spawnPointManager, PurgeHudManager hudManager) {
        this.spawnPointManager = spawnPointManager;
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

    // --- Scaling Formulas ---

    public static int zombieCount(int wave) {
        return 5 + (wave - 1) * 2;
    }

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
        session.setCurrentWave(session.getCurrentWave() + 1);
        int count = zombieCount(session.getCurrentWave());
        session.setWaveZombieCount(count);
        session.setSpawningComplete(false);
        session.setState(SessionState.SPAWNING);

        sendMessage(session, "-- Wave " + session.getCurrentWave() + " -- (" + count + " zombies)");
        hudManager.updateWaveStatus(session.getPlayerId(), session.getCurrentWave(), count, count);

        startSpawning(session, count);
        startWaveTick(session);
    }

    private void startSpawning(PurgeSession session, int totalCount) {
        double[] playerPos = getPlayerPosition(session);
        AtomicInteger remaining = new AtomicInteger(totalCount);

        ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                if (session.getState() == SessionState.ENDED) {
                    session.cancelSpawnTask();
                    return;
                }
                int batch = Math.min(SPAWN_BATCH_SIZE, remaining.get());
                if (batch <= 0) {
                    session.setSpawningComplete(true);
                    if (session.getState() == SessionState.SPAWNING) {
                        session.setState(SessionState.COMBAT);
                    }
                    session.cancelSpawnTask();
                    return;
                }

                World world = getPurgeWorld();
                if (world == null) {
                    return;
                }

                // Re-read player position for each batch
                double[] currentPos = getPlayerPosition(session);
                double spawnX = currentPos != null ? currentPos[0] : (playerPos != null ? playerPos[0] : 0);
                double spawnZ = currentPos != null ? currentPos[2] : (playerPos != null ? playerPos[2] : 0);

                world.execute(() -> {
                    try {
                        Store<EntityStore> store = world.getEntityStore().getStore();
                        if (store == null) {
                            return;
                        }
                        int toSpawn = Math.min(SPAWN_BATCH_SIZE, remaining.get());
                        for (int i = 0; i < toSpawn && remaining.get() > 0; i++) {
                            spawnZombie(session, store, spawnX, spawnZ, session.getCurrentWave());
                            remaining.decrementAndGet();
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

    private void spawnZombie(PurgeSession session, Store<EntityStore> store, double playerX, double playerZ, int wave) {
        if (npcPlugin == null) {
            return;
        }
        PurgeSpawnPoint point = spawnPointManager.selectSpawnPoint(playerX, playerZ);
        if (point == null) {
            return;
        }

        double x = point.x() + ThreadLocalRandom.current().nextDouble(-SPAWN_RANDOM_OFFSET, SPAWN_RANDOM_OFFSET);
        double z = point.z() + ThreadLocalRandom.current().nextDouble(-SPAWN_RANDOM_OFFSET, SPAWN_RANDOM_OFFSET);
        Vector3d position = new Vector3d(x, point.y(), z);
        Vector3f rotation = new Vector3f(0, point.yaw(), 0);

        try {
            Object result = npcPlugin.spawnNPC(store, ZOMBIE_NPC_TYPE, "", position, rotation);
            if (result != null) {
                Ref<EntityStore> entityRef = extractEntityRef(result);
                if (entityRef != null) {
                    session.addAliveZombie(entityRef);
                    // Hide nameplate
                    try {
                        Nameplate nameplate = store.ensureAndGetComponent(entityRef, Nameplate.getComponentType());
                        nameplate.setText("");
                    } catch (Exception e) {
                        // Ignore nameplate errors
                    }
                    // TODO: Apply HP/speed/damage scaling when API is discovered
                    //   double hp = baseHp * hpMultiplier(wave);
                    //   double speed = baseSpeed * speedMultiplier(wave);
                    //   double damage = baseDamage * damageMultiplier(wave);
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to spawn zombie: " + e.getMessage());
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

    private void onWaveComplete(PurgeSession session) {
        // Cancel wave tick
        ScheduledFuture<?> wt = session.getWaveTick();
        if (wt != null) {
            wt.cancel(false);
            session.setWaveTick(null);
        }

        session.setState(SessionState.INTERMISSION);
        sendMessage(session, "Wave " + session.getCurrentWave() + " complete! (" + session.getTotalKills() + " total kills)");
        startIntermission(session);
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
