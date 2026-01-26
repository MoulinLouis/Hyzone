package io.hyvexa.duel.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.duel.DuelMatch;
import io.hyvexa.parkour.data.DatabaseManager;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Level;

/** MySQL-backed storage for duel match history. */
public class DuelMatchStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS duel_matches (
            id VARCHAR(36) PRIMARY KEY,
            player1_uuid VARCHAR(36) NOT NULL,
            player2_uuid VARCHAR(36) NOT NULL,
            map_id VARCHAR(64) NOT NULL,
            winner_uuid VARCHAR(36),
            player1_time_ms BIGINT,
            player2_time_ms BIGINT,
            finish_reason VARCHAR(20),
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """;

    public void ensureTable() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE_SQL)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to create duel_matches table: " + e.getMessage());
        }
    }

    public void saveMatch(@Nonnull DuelMatch match) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = """
            INSERT INTO duel_matches (id, player1_uuid, player2_uuid, map_id, winner_uuid,
                player1_time_ms, player2_time_ms, finish_reason, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, match.getMatchId());
            stmt.setString(2, match.getPlayer1().toString());
            stmt.setString(3, match.getPlayer2().toString());
            stmt.setString(4, match.getMapId());
            stmt.setString(5, match.getWinnerId() != null ? match.getWinnerId().toString() : null);
            stmt.setObject(6, match.getPlayer1FinishMs() > 0 ? match.getPlayer1FinishMs() : null);
            stmt.setObject(7, match.getPlayer2FinishMs() > 0 ? match.getPlayer2FinishMs() : null);
            stmt.setString(8, match.getFinishReason() != null ? match.getFinishReason().name() : null);
            stmt.setTimestamp(9, new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save duel match: " + e.getMessage());
        }
    }
}
