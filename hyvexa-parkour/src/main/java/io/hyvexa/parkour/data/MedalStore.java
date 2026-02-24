package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which medals each player has earned per map.
 * Lazy-loads per player, evicts on disconnect.
 */
public class MedalStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final MedalStore INSTANCE = new MedalStore();

    // player -> mapId -> set of earned medal names
    private final ConcurrentHashMap<UUID, java.util.Map<String, Set<Medal>>> cache = new ConcurrentHashMap<>();

    private MedalStore() {
    }

    public static MedalStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, MedalStore will use in-memory mode");
            return;
        }
        String createSql = "CREATE TABLE IF NOT EXISTS player_medals ("
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "map_id VARCHAR(64) NOT NULL, "
                + "medal VARCHAR(6) NOT NULL, "
                + "earned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (player_uuid, map_id, medal)"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(createSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            LOGGER.atInfo().log("MedalStore initialized (player_medals table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create player_medals table");
        }
    }

    public Set<Medal> getEarnedMedals(UUID playerId, String mapId) {
        if (playerId == null || mapId == null) {
            return Set.of();
        }
        java.util.Map<String, Set<Medal>> playerMedals = ensurePlayerLoaded(playerId);
        Set<Medal> medals = playerMedals.get(mapId);
        return medals != null ? Collections.unmodifiableSet(medals) : Set.of();
    }

    public boolean hasEarnedMedal(UUID playerId, String mapId, Medal medal) {
        if (playerId == null || mapId == null || medal == null) {
            return false;
        }
        return getEarnedMedals(playerId, mapId).contains(medal);
    }

    public void awardMedal(UUID playerId, String mapId, Medal medal) {
        if (playerId == null || mapId == null || medal == null) {
            return;
        }
        java.util.Map<String, Set<Medal>> playerMedals = ensurePlayerLoaded(playerId);
        playerMedals.computeIfAbsent(mapId, k -> EnumSet.noneOf(Medal.class)).add(medal);
        persistMedal(playerId, mapId, medal);
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    private java.util.Map<String, Set<Medal>> ensurePlayerLoaded(UUID playerId) {
        return cache.computeIfAbsent(playerId, this::loadFromDatabase);
    }

    private java.util.Map<String, Set<Medal>> loadFromDatabase(UUID playerId) {
        java.util.Map<String, Set<Medal>> result = new ConcurrentHashMap<>();
        if (!DatabaseManager.getInstance().isInitialized()) {
            return result;
        }
        String sql = "SELECT map_id, medal FROM player_medals WHERE player_uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String mapId = rs.getString("map_id");
                    String medalStr = rs.getString("medal");
                    try {
                        Medal medal = Medal.valueOf(medalStr);
                        result.computeIfAbsent(mapId, k -> EnumSet.noneOf(Medal.class)).add(medal);
                    } catch (IllegalArgumentException ignored) {
                        // Skip unknown medal values
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load medals for " + playerId);
        }
        return result;
    }

    private void persistMedal(UUID playerId, String mapId, Medal medal) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT IGNORE INTO player_medals (player_uuid, map_id, medal) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, mapId);
            stmt.setString(3, medal.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist medal for " + playerId + " map=" + mapId);
        }
    }
}
