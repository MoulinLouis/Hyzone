package io.hyvexa.purge.data;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class PurgeSession {

    private final String sessionId;
    private final String instanceId;
    private final ConcurrentHashMap<UUID, PurgeSessionPlayerState> players = new ConcurrentHashMap<>();

    // Unchanged fields
    private volatile int currentWave = 0;
    private volatile int waveZombieCount = 0;
    private volatile SessionState state = SessionState.COUNTDOWN;
    private volatile boolean spawningComplete = false;
    private final AtomicInteger waveSpawnAttempts = new AtomicInteger();
    private final AtomicInteger waveSpawnSuccesses = new AtomicInteger();
    private final AtomicBoolean transitionGuard = new AtomicBoolean(false);
    private final Set<Ref<EntityStore>> aliveZombies = ConcurrentHashMap.newKeySet();
    private final Set<Ref<EntityStore>> pendingZombieDeaths = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Ref<EntityStore>, String> zombieVariants = new ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> waveTick;
    private volatile ScheduledFuture<?> spawnTask;
    private volatile ScheduledFuture<?> intermissionTask;

    // Upgrade phase
    private final Set<UUID> pendingUpgradeChoices = ConcurrentHashMap.newKeySet();
    private volatile ScheduledFuture<?> upgradeTimeoutTask;

    public PurgeSession(String sessionId, String instanceId, Map<UUID, Ref<EntityStore>> playerRefs) {
        this.sessionId = sessionId;
        this.instanceId = instanceId;
        for (var entry : playerRefs.entrySet()) {
            players.put(entry.getKey(), new PurgeSessionPlayerState(entry.getKey(), entry.getValue()));
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    // --- Player queries ---

    public Set<UUID> getParticipants() {
        return Set.copyOf(players.keySet());
    }

    public Set<UUID> getConnectedParticipants() {
        return filterPlayers(PurgeSessionPlayerState::isConnected);
    }

    public Set<UUID> getAliveParticipants() {
        return filterPlayers(PurgeSessionPlayerState::isAlive);
    }

    public Set<UUID> getAliveConnectedParticipants() {
        return filterPlayers(ps -> ps.isAlive() && ps.isConnected());
    }

    public Set<UUID> getDeadThisWaveParticipants() {
        return filterPlayers(PurgeSessionPlayerState::isDeadThisWave);
    }

    private Set<UUID> filterPlayers(Predicate<PurgeSessionPlayerState> predicate) {
        Set<UUID> result = new HashSet<>();
        for (PurgeSessionPlayerState playerState : players.values()) {
            if (predicate.test(playerState)) {
                result.add(playerState.getPlayerId());
            }
        }
        return Set.copyOf(result);
    }

    public int getConnectedCount() {
        int count = 0;
        for (PurgeSessionPlayerState playerState : players.values()) {
            if (playerState.isConnected()) {
                count++;
            }
        }
        return count;
    }

    public int getAliveConnectedCount() {
        int count = 0;
        for (PurgeSessionPlayerState playerState : players.values()) {
            if (playerState.isAlive() && playerState.isConnected()) {
                count++;
            }
        }
        return count;
    }

    public void forEachConnectedParticipant(Consumer<UUID> consumer) {
        for (PurgeSessionPlayerState playerState : players.values()) {
            if (playerState.isConnected()) {
                consumer.accept(playerState.getPlayerId());
            }
        }
    }

    public void forEachAliveConnectedPlayerState(Consumer<PurgeSessionPlayerState> consumer) {
        for (PurgeSessionPlayerState playerState : players.values()) {
            if (playerState.isAlive() && playerState.isConnected()) {
                consumer.accept(playerState);
            }
        }
    }

    // --- Player state ---

    public PurgeSessionPlayerState getPlayerState(UUID playerId) {
        return playerId != null ? players.get(playerId) : null;
    }

    public void markDeadThisWave(UUID playerId) {
        PurgeSessionPlayerState ps = players.get(playerId);
        if (ps != null) {
            ps.setDeadThisWave(true);
            ps.setAlive(false);
        }
    }

    public void removePlayer(UUID playerId) {
        players.remove(playerId);
    }

    // --- Positions ---

    public Ref<EntityStore> getRandomAlivePlayerRef() {
        List<Ref<EntityStore>> alive = new ArrayList<>();
        for (PurgeSessionPlayerState playerState : players.values()) {
            if (!playerState.isAlive() || !playerState.isConnected()) {
                continue;
            }
            Ref<EntityStore> playerRef = playerState.getPlayerRef();
            if (playerRef != null && playerRef.isValid()) {
                alive.add(playerRef);
            }
        }
        if (alive.isEmpty()) {
            return null;
        }
        return alive.get(ThreadLocalRandom.current().nextInt(alive.size()));
    }

    // --- Upgrades ---

    public PurgeUpgradeState getUpgradeState(UUID playerId) {
        PurgeSessionPlayerState ps = players.get(playerId);
        return ps != null ? ps.getUpgradeState() : null;
    }

    public Set<UUID> getPendingUpgradeChoices() {
        return pendingUpgradeChoices;
    }

    public ScheduledFuture<?> getUpgradeTimeoutTask() {
        return upgradeTimeoutTask;
    }

    public void setUpgradeTimeoutTask(ScheduledFuture<?> upgradeTimeoutTask) {
        this.upgradeTimeoutTask = upgradeTimeoutTask;
    }

    // --- Wave / zombie state (unchanged) ---

    public int getCurrentWave() {
        return currentWave;
    }

    public void setCurrentWave(int currentWave) {
        this.currentWave = currentWave;
    }

    public int getWaveZombieCount() {
        return waveZombieCount;
    }

    public void setWaveZombieCount(int waveZombieCount) {
        this.waveZombieCount = waveZombieCount;
    }

    public SessionState getState() {
        return state;
    }

    public void setState(SessionState state) {
        this.state = state;
    }

    public boolean isSpawningComplete() {
        return spawningComplete;
    }

    public void setSpawningComplete(boolean spawningComplete) {
        this.spawningComplete = spawningComplete;
    }

    public void resetWaveSpawnProgress() {
        waveSpawnAttempts.set(0);
        waveSpawnSuccesses.set(0);
    }

    public void incrementWaveSpawnAttempt() {
        waveSpawnAttempts.incrementAndGet();
    }

    public void incrementWaveSpawnSuccess() {
        waveSpawnSuccesses.incrementAndGet();
    }

    public int getWaveSpawnAttempts() {
        return waveSpawnAttempts.get();
    }

    public int getWaveSpawnSuccesses() {
        return waveSpawnSuccesses.get();
    }

    public boolean tryBeginTransition() {
        return transitionGuard.compareAndSet(false, true);
    }

    public void resetTransitionGuard() {
        transitionGuard.set(false);
    }

    public Set<Ref<EntityStore>> getAliveZombies() {
        return aliveZombies;
    }

    public int getAliveZombieCount() {
        return aliveZombies.size();
    }

    public void addAliveZombie(Ref<EntityStore> ref, String variantKey) {
        if (ref == null) {
            return;
        }
        aliveZombies.add(ref);
        pendingZombieDeaths.remove(ref);
        if (variantKey != null) {
            zombieVariants.put(ref, variantKey);
        }
    }

    public void markZombiePendingDeath(Ref<EntityStore> ref) {
        if (ref != null) {
            pendingZombieDeaths.add(ref);
        }
    }

    public void clearZombiePendingDeath(Ref<EntityStore> ref) {
        if (ref != null) {
            pendingZombieDeaths.remove(ref);
        }
    }

    public Set<Ref<EntityStore>> getPendingZombieDeathsSnapshot() {
        return new HashSet<>(pendingZombieDeaths);
    }

    public void clearPendingZombieDeaths() {
        pendingZombieDeaths.clear();
    }

    public void removeAliveZombie(Ref<EntityStore> ref) {
        if (ref == null) {
            return;
        }
        aliveZombies.remove(ref);
        pendingZombieDeaths.remove(ref);
        zombieVariants.remove(ref);
    }

    public Set<Ref<EntityStore>> drainAliveZombies() {
        Set<Ref<EntityStore>> snapshot = new HashSet<>(aliveZombies);
        aliveZombies.clear();
        pendingZombieDeaths.clear();
        zombieVariants.clear();
        return snapshot;
    }

    public String getZombieVariantKey(Ref<EntityStore> ref) {
        return ref != null ? zombieVariants.get(ref) : null;
    }

    // --- Task scheduling ---

    public ScheduledFuture<?> getWaveTick() {
        return waveTick;
    }

    public void setWaveTick(ScheduledFuture<?> waveTick) {
        this.waveTick = waveTick;
    }

    public void setSpawnTask(ScheduledFuture<?> spawnTask) {
        this.spawnTask = spawnTask;
    }

    public void setIntermissionTask(ScheduledFuture<?> intermissionTask) {
        this.intermissionTask = intermissionTask;
    }

    public ScheduledFuture<?> getIntermissionTask() {
        return intermissionTask;
    }

    public void cancelSpawnTask() {
        spawnTask = cancelTask(spawnTask);
    }

    public void cancelIntermissionTask() {
        intermissionTask = cancelTask(intermissionTask);
    }

    public void cancelAllTasks() {
        waveTick = cancelTask(waveTick);
        spawnTask = cancelTask(spawnTask);
        intermissionTask = cancelTask(intermissionTask);
        upgradeTimeoutTask = cancelTask(upgradeTimeoutTask);
    }

    private static ScheduledFuture<?> cancelTask(ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
        return null;
    }
}
