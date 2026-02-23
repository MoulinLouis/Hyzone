package io.hyvexa.purge.data;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class PurgeSessionPlayerState {

    private final UUID playerId;
    private volatile Ref<EntityStore> playerRef;
    private volatile boolean connected = true;
    private volatile boolean alive = true;
    private volatile boolean deadThisWave = false;
    private final AtomicInteger kills = new AtomicInteger(0);
    private final PurgeUpgradeState upgradeState = new PurgeUpgradeState();
    private volatile String currentWeaponId;
    private volatile long lastKillTimeMs;
    private volatile int killStreak;

    public PurgeSessionPlayerState(UUID playerId, Ref<EntityStore> playerRef) {
        this.playerId = playerId;
        this.playerRef = playerRef;
    }

    public UUID getPlayerId() { return playerId; }
    public Ref<EntityStore> getPlayerRef() { return playerRef; }
    public void setPlayerRef(Ref<EntityStore> ref) { this.playerRef = ref; }

    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }

    public boolean isDeadThisWave() { return deadThisWave; }
    public void setDeadThisWave(boolean deadThisWave) { this.deadThisWave = deadThisWave; }

    public int getKills() { return kills.get(); }
    public void incrementKills() { kills.incrementAndGet(); }

    public PurgeUpgradeState getUpgradeState() { return upgradeState; }

    public String getCurrentWeaponId() { return currentWeaponId; }
    public void setCurrentWeaponId(String currentWeaponId) { this.currentWeaponId = currentWeaponId; }

    public long getLastKillTimeMs() { return lastKillTimeMs; }
    public int getKillStreak() { return killStreak; }

    /**
     * Records a kill and returns the current streak level (1-9).
     * Streak resets to 1 if more than {@code streakWindowMs} has passed since last kill.
     */
    public int recordKillStreak(long streakWindowMs) {
        long now = System.currentTimeMillis();
        if (now - lastKillTimeMs <= streakWindowMs && killStreak > 0) {
            killStreak = Math.min(killStreak + 1, 9);
        } else {
            killStreak = 1;
        }
        lastKillTimeMs = now;
        return killStreak;
    }
}
