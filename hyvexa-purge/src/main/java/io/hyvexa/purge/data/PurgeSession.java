package io.hyvexa.purge.data;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class PurgeSession {

    private final UUID playerId;
    private volatile Ref<EntityStore> playerRef;
    private volatile int currentWave = 0;
    private final AtomicInteger totalKills = new AtomicInteger(0);
    private volatile int waveZombieCount = 0;
    private volatile SessionState state = SessionState.COUNTDOWN;
    private volatile boolean spawningComplete = false;
    private final Set<Ref<EntityStore>> aliveZombies = ConcurrentHashMap.newKeySet();
    private volatile ScheduledFuture<?> waveTick;
    private volatile ScheduledFuture<?> spawnTask;
    private volatile ScheduledFuture<?> intermissionTask;
    private final PurgeUpgradeState upgradeState = new PurgeUpgradeState();

    public PurgeSession(UUID playerId, Ref<EntityStore> playerRef) {
        this.playerId = playerId;
        this.playerRef = playerRef;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Ref<EntityStore> getPlayerRef() {
        return playerRef;
    }

    public void setPlayerRef(Ref<EntityStore> playerRef) {
        this.playerRef = playerRef;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public void setCurrentWave(int currentWave) {
        this.currentWave = currentWave;
    }

    public int getTotalKills() {
        return totalKills.get();
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

    public void incrementKills() {
        totalKills.incrementAndGet();
    }

    public PurgeUpgradeState getUpgradeState() {
        return upgradeState;
    }

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
    }
}
