package io.hyvexa.purge.mission;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeMissionStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static volatile PurgeMissionStore instance;

    private final ConnectionProvider db;
    private final ConcurrentHashMap<UUID, DailyMissionProgress> cache = new ConcurrentHashMap<>();

    private PurgeMissionStore(ConnectionProvider db) {
        this.db = db;
    }

    public static PurgeMissionStore createAndRegister(ConnectionProvider db) {
        if (instance != null) throw new IllegalStateException("PurgeMissionStore already initialized");
        instance = new PurgeMissionStore(db);
        return instance;
    }

    public static PurgeMissionStore get() {
        PurgeMissionStore ref = instance;
        if (ref == null) throw new IllegalStateException("PurgeMissionStore not yet initialized");
        return ref;
    }

    public static void destroy() { instance = null; }

    public void initialize() {
        if (!this.db.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, PurgeMissionStore will use in-memory mode");
        }
    }

    public DailyMissionProgress getProgress(UUID playerId) {
        if (playerId == null) {
            return new DailyMissionProgress(todayUtc());
        }
        DailyMissionProgress cached = cache.get(playerId);
        if (cached != null) {
            if (cached.getDate().equals(todayUtc())) {
                return cached;
            }
            // Date is stale — treat as fresh day
            cache.remove(playerId);
        }
        loadFromDatabase(playerId);
        return cache.getOrDefault(playerId, new DailyMissionProgress(todayUtc()));
    }

    public void updateAfterSession(UUID playerId, int sessionKills, int sessionBestWave, int sessionBestCombo) {
        if (playerId == null) {
            return;
        }
        DailyMissionProgress progress = getProgress(playerId);
        progress.setTotalKills(progress.getTotalKills() + sessionKills);
        if (sessionBestWave > progress.getBestWave()) {
            progress.setBestWave(sessionBestWave);
        }
        if (sessionBestCombo > progress.getBestCombo()) {
            progress.setBestCombo(sessionBestCombo);
        }
        cache.put(playerId, progress);
        persistToDatabase(playerId, progress);
    }

    public void markClaimed(UUID playerId, MissionDefinition.MissionCategory category) {
        if (playerId == null) {
            return;
        }
        DailyMissionProgress progress = getProgress(playerId);
        switch (category) {
            case WAVE -> progress.setClaimedWave(true);
            case KILL -> progress.setClaimedKill(true);
            case COMBO -> progress.setClaimedCombo(true);
        }
        cache.put(playerId, progress);
        persistToDatabase(playerId, progress);
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    private void loadFromDatabase(UUID playerId) {
        if (!this.db.isInitialized()) {
            return;
        }
        LocalDate today = todayUtc();
        String sql = "SELECT total_kills, best_wave, best_combo, claimed_wave, claimed_kill, claimed_combo "
                + "FROM purge_daily_missions WHERE uuid = ? AND mission_date = ?";
        DailyMissionProgress progress = DatabaseManager.queryOne(this.db, sql,
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, today.toString());
                },
                rs -> {
                    DailyMissionProgress p = new DailyMissionProgress(today);
                    p.setTotalKills(rs.getInt("total_kills"));
                    p.setBestWave(rs.getInt("best_wave"));
                    p.setBestCombo(rs.getInt("best_combo"));
                    p.setClaimedWave(rs.getInt("claimed_wave") != 0);
                    p.setClaimedKill(rs.getInt("claimed_kill") != 0);
                    p.setClaimedCombo(rs.getInt("claimed_combo") != 0);
                    return p;
                }, new DailyMissionProgress(today));
        cache.putIfAbsent(playerId, progress);
    }

    private void persistToDatabase(UUID playerId, DailyMissionProgress progress) {
        String sql = "INSERT INTO purge_daily_missions "
                + "(uuid, mission_date, total_kills, best_wave, best_combo, claimed_wave, claimed_kill, claimed_combo) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "total_kills = VALUES(total_kills), best_wave = VALUES(best_wave), best_combo = VALUES(best_combo), "
                + "claimed_wave = VALUES(claimed_wave), claimed_kill = VALUES(claimed_kill), claimed_combo = VALUES(claimed_combo)";
        DatabaseManager.execute(this.db, sql, stmt -> {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, progress.getDate().toString());
            stmt.setInt(3, progress.getTotalKills());
            stmt.setInt(4, progress.getBestWave());
            stmt.setInt(5, progress.getBestCombo());
            stmt.setInt(6, progress.isClaimedWave() ? 1 : 0);
            stmt.setInt(7, progress.isClaimedKill() ? 1 : 0);
            stmt.setInt(8, progress.isClaimedCombo() ? 1 : 0);
        });
    }

    private static LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
