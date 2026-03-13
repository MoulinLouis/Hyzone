package io.hyvexa.purge.mission;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeMissionStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final PurgeMissionStore INSTANCE = new PurgeMissionStore();

    private final ConcurrentHashMap<UUID, DailyMissionProgress> cache = new ConcurrentHashMap<>();

    private PurgeMissionStore() {}

    public static PurgeMissionStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, PurgeMissionStore will use in-memory mode");
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS purge_daily_missions ("
                + "uuid VARCHAR(36) NOT NULL, "
                + "mission_date DATE NOT NULL, "
                + "total_kills INT NOT NULL DEFAULT 0, "
                + "best_wave INT NOT NULL DEFAULT 0, "
                + "best_combo INT NOT NULL DEFAULT 0, "
                + "claimed_wave TINYINT NOT NULL DEFAULT 0, "
                + "claimed_combo TINYINT NOT NULL DEFAULT 0, "
                + "claimed_kill TINYINT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (uuid, mission_date)"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            LOGGER.atInfo().log("PurgeMissionStore initialized (purge_daily_missions table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create purge_daily_missions table");
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
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        LocalDate today = todayUtc();
        String sql = "SELECT total_kills, best_wave, best_combo, claimed_wave, claimed_kill, claimed_combo "
                + "FROM purge_daily_missions WHERE uuid = ? AND mission_date = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, today.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                DailyMissionProgress progress = new DailyMissionProgress(today);
                if (rs.next()) {
                    progress.setTotalKills(rs.getInt("total_kills"));
                    progress.setBestWave(rs.getInt("best_wave"));
                    progress.setBestCombo(rs.getInt("best_combo"));
                    progress.setClaimedWave(rs.getInt("claimed_wave") != 0);
                    progress.setClaimedKill(rs.getInt("claimed_kill") != 0);
                    progress.setClaimedCombo(rs.getInt("claimed_combo") != 0);
                }
                cache.putIfAbsent(playerId, progress);
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load mission progress for " + playerId);
        }
    }

    private void persistToDatabase(UUID playerId, DailyMissionProgress progress) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT INTO purge_daily_missions "
                + "(uuid, mission_date, total_kills, best_wave, best_combo, claimed_wave, claimed_kill, claimed_combo) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "total_kills = VALUES(total_kills), best_wave = VALUES(best_wave), best_combo = VALUES(best_combo), "
                + "claimed_wave = VALUES(claimed_wave), claimed_kill = VALUES(claimed_kill), claimed_combo = VALUES(claimed_combo)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, progress.getDate().toString());
            stmt.setInt(3, progress.getTotalKills());
            stmt.setInt(4, progress.getBestWave());
            stmt.setInt(5, progress.getBestCombo());
            stmt.setInt(6, progress.isClaimedWave() ? 1 : 0);
            stmt.setInt(7, progress.isClaimedKill() ? 1 : 0);
            stmt.setInt(8, progress.isClaimedCombo() ? 1 : 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist mission progress for " + playerId);
        }
    }

    private static LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
