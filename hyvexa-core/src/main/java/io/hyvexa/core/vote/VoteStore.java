package io.hyvexa.core.vote;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized vote tracking store. Records every vote in MySQL for leaderboards,
 * milestones, and analytics. Maintains a denormalized count table for fast reads.
 */
public class VoteStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ConnectionProvider connectionProvider;
    private final ConcurrentHashMap<UUID, Integer> countCache = new ConcurrentHashMap<>();

    public VoteStore(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void initialize() {
        if (!connectionProvider.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, VoteStore will not persist");
            return;
        }
        try (Connection conn = connectionProvider.getConnection()) {
            try (PreparedStatement stmt = DatabaseManager.prepare(conn,
                    "CREATE TABLE IF NOT EXISTS player_votes ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "player_uuid VARCHAR(36) NOT NULL, "
                    + "player_name VARCHAR(32) NOT NULL, "
                    + "source VARCHAR(32) NOT NULL, "
                    + "voted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "INDEX idx_player (player_uuid), "
                    + "INDEX idx_voted_at (voted_at), "
                    + "INDEX idx_player_voted (player_uuid, voted_at)"
                    + ") ENGINE=InnoDB")) {
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = DatabaseManager.prepare(conn,
                    "CREATE TABLE IF NOT EXISTS player_vote_counts ("
                    + "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                    + "player_name VARCHAR(32) NOT NULL, "
                    + "total_votes INT NOT NULL DEFAULT 0, "
                    + "last_voted_at TIMESTAMP NULL"
                    + ") ENGINE=InnoDB")) {
                stmt.executeUpdate();
            }
            LOGGER.atInfo().log("VoteStore initialized (player_votes + player_vote_counts tables ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create vote tables");
        }
    }

    /**
     * Records a vote: inserts into player_votes log and increments player_vote_counts.
     */
    public void recordVote(UUID playerId, String username, String source) {
        if (playerId == null || username == null || source == null) {
            return;
        }
        if (!connectionProvider.isInitialized()) {
            return;
        }
        boolean committed = connectionProvider.withTransaction(conn -> {
            try (PreparedStatement stmt = DatabaseManager.prepare(conn,
                    "INSERT INTO player_votes (player_uuid, player_name, source) VALUES (?, ?, ?)")) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, username);
                stmt.setString(3, source);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = DatabaseManager.prepare(conn,
                    "INSERT INTO player_vote_counts (player_uuid, player_name, total_votes, last_voted_at) "
                    + "VALUES (?, ?, 1, NOW()) "
                    + "ON DUPLICATE KEY UPDATE total_votes = total_votes + 1, "
                    + "last_voted_at = NOW(), player_name = VALUES(player_name)")) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, username);
                stmt.executeUpdate();
            }
        });
        if (committed) {
            countCache.compute(playerId, (uuid, current) -> (current != null ? current : 0) + 1);
        }
    }

    /**
     * Returns total vote count for a player (cached).
     */
    public int getVoteCount(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        Integer cached = countCache.get(playerId);
        if (cached != null) {
            return cached;
        }
        int count = loadVoteCount(playerId);
        countCache.put(playerId, count);
        return count;
    }

    /**
     * Returns top N voters by total vote count.
     */
    public List<VoterEntry> getTopVoters(int limit) {
        return queryTopVoters("SELECT player_uuid, player_name, total_votes FROM player_vote_counts "
                + "ORDER BY total_votes DESC LIMIT ?", limit);
    }

    /**
     * Returns top N voters within a time period.
     */
    public List<VoterEntry> getTopVotersForPeriod(int limit, long sinceMs) {
        List<VoterEntry> result = new ArrayList<>();
        if (!connectionProvider.isInitialized()) {
            return result;
        }
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = DatabaseManager.prepare(conn,
                     "SELECT player_uuid, player_name, COUNT(*) AS vote_count FROM player_votes "
                     + "WHERE voted_at >= FROM_UNIXTIME(? / 1000) "
                     + "GROUP BY player_uuid, player_name ORDER BY vote_count DESC LIMIT ?")) {
            stmt.setLong(1, sinceMs);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new VoterEntry(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("player_name"),
                            rs.getInt("vote_count")));
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to query top voters for period");
        }
        return result;
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            countCache.remove(playerId);
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private int loadVoteCount(UUID playerId) {
        if (!connectionProvider.isInitialized()) {
            return 0;
        }
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = DatabaseManager.prepare(conn,
                     "SELECT total_votes FROM player_vote_counts WHERE player_uuid = ?")) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total_votes");
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load vote count for " + playerId);
        }
        return 0;
    }

    private List<VoterEntry> queryTopVoters(String sql, int limit) {
        List<VoterEntry> result = new ArrayList<>();
        if (!connectionProvider.isInitialized()) {
            return result;
        }
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = DatabaseManager.prepare(conn, sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new VoterEntry(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("player_name"),
                            rs.getInt("total_votes")));
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to query top voters");
        }
        return result;
    }

    /**
     * Leaderboard entry for a voter.
     */
    public record VoterEntry(UUID playerId, String playerName, int totalVotes) {
    }
}
