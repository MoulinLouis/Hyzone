package io.hyvexa.purge.data;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

public class PurgeSession {

    private final String sessionId;
    private final String instanceId;
    private final ConcurrentHashMap<UUID, PurgeSessionPlayerState> players = new ConcurrentHashMap<>();

    // Unchanged fields
    private volatile int currentWave = 0;
    private volatile int waveZombieCount = 0;
    private volatile SessionState state = SessionState.COUNTDOWN;
    private volatile boolean spawningComplete = false;
    private final Set<Ref<EntityStore>> aliveZombies = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Ref<EntityStore>, PurgeZombieVariant> zombieVariants = new ConcurrentHashMap<>();
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
        return players.values().stream()
                .filter(PurgeSessionPlayerState::isConnected)
                .map(PurgeSessionPlayerState::getPlayerId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<UUID> getAliveParticipants() {
        return players.values().stream()
                .filter(PurgeSessionPlayerState::isAlive)
                .map(PurgeSessionPlayerState::getPlayerId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<UUID> getAliveConnectedParticipants() {
        return players.values().stream()
                .filter(s -> s.isAlive() && s.isConnected())
                .map(PurgeSessionPlayerState::getPlayerId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<UUID> getDeadThisWaveParticipants() {
        return players.values().stream()
                .filter(PurgeSessionPlayerState::isDeadThisWave)
                .map(PurgeSessionPlayerState::getPlayerId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public int getConnectedCount() {
        return (int) players.values().stream().filter(PurgeSessionPlayerState::isConnected).count();
    }

    public int getAliveConnectedCount() {
        return (int) players.values().stream().filter(s -> s.isAlive() && s.isConnected()).count();
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

    public void disconnectPlayer(UUID playerId) {
        PurgeSessionPlayerState ps = players.get(playerId);
        if (ps != null) {
            ps.setConnected(false);
        }
    }

    public void removePlayer(UUID playerId) {
        players.remove(playerId);
    }

    // --- Positions ---

    @Nullable
    public Ref<EntityStore> getRandomAlivePlayerRef() {
        List<Ref<EntityStore>> alive = players.values().stream()
                .filter(s -> s.isAlive() && s.isConnected())
                .map(PurgeSessionPlayerState::getPlayerRef)
                .filter(ref -> ref != null && ref.isValid())
                .collect(Collectors.toList());
        if (alive.isEmpty()) {
            return null;
        }
        return alive.get(new Random().nextInt(alive.size()));
    }

    public List<double[]> getAlivePlayerPositions(Store<EntityStore> store) {
        List<double[]> positions = new ArrayList<>();
        for (PurgeSessionPlayerState ps : players.values()) {
            if (!ps.isAlive() || !ps.isConnected()) continue;
            Ref<EntityStore> ref = ps.getPlayerRef();
            if (ref == null || !ref.isValid()) continue;
            try {
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null) {
                    var pos = transform.getPosition();
                    positions.add(new double[]{pos.getX(), pos.getY(), pos.getZ()});
                }
            } catch (Exception ignored) {
            }
        }
        return positions;
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

    public Set<Ref<EntityStore>> getAliveZombies() {
        return aliveZombies;
    }

    public int getAliveZombieCount() {
        return aliveZombies.size();
    }

    public void addAliveZombie(Ref<EntityStore> ref) {
        aliveZombies.add(ref);
    }

    public void addAliveZombie(Ref<EntityStore> ref, PurgeZombieVariant variant) {
        aliveZombies.add(ref);
        if (variant != null) {
            zombieVariants.put(ref, variant);
        }
    }

    public PurgeZombieVariant getZombieVariant(Ref<EntityStore> ref) {
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

    public void cancelSpawnTask() {
        ScheduledFuture<?> task = spawnTask;
        if (task != null) {
            task.cancel(false);
            spawnTask = null;
        }
    }

    public void cancelIntermissionTask() {
        ScheduledFuture<?> task = intermissionTask;
        if (task != null) {
            task.cancel(false);
            intermissionTask = null;
        }
    }

    public void cancelAllTasks() {
        ScheduledFuture<?> wt = waveTick;
        if (wt != null) {
            wt.cancel(false);
            waveTick = null;
        }
        ScheduledFuture<?> st = spawnTask;
        if (st != null) {
            st.cancel(false);
            spawnTask = null;
        }
        ScheduledFuture<?> it = intermissionTask;
        if (it != null) {
            it.cancel(false);
            intermissionTask = null;
        }
        ScheduledFuture<?> ut = upgradeTimeoutTask;
        if (ut != null) {
            ut.cancel(false);
            upgradeTimeoutTask = null;
        }
    }
}
