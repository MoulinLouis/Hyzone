package org.hyvote.plugins.votifier.storage;

import com.hypixel.hytale.logger.HytaleLogger;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.logging.Level;

/**
 * SQLite implementation of {@link VoteStorage}.
 *
 * <p>This implementation persists vote timestamps to a SQLite database file,
 * allowing vote history to survive server restarts.</p>
 *
 * <p>The database schema is automatically created on initialization if it
 * doesn't exist.</p>
 */
public class SQLiteVoteStorage implements VoteStorage {

    private static final String TABLE_NAME = "player_votes";
    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS %s (
                username TEXT PRIMARY KEY NOT NULL,
                last_vote_timestamp INTEGER NOT NULL
            )
            """.formatted(TABLE_NAME);
    private static final String UPSERT_SQL = """
            INSERT INTO %s (username, last_vote_timestamp) VALUES (?, ?)
            ON CONFLICT(username) DO UPDATE SET last_vote_timestamp = excluded.last_vote_timestamp
            """.formatted(TABLE_NAME);
    private static final String SELECT_SQL = "SELECT last_vote_timestamp FROM %s WHERE username = ?".formatted(TABLE_NAME);
    private static final String DELETE_EXPIRED_SQL = "DELETE FROM %s WHERE last_vote_timestamp < ?".formatted(TABLE_NAME);

    private final Path databasePath;
    private final HytaleLogger logger;
    private Connection connection;

    /**
     * Creates a new SQLiteVoteStorage.
     *
     * @param databasePath the path to the SQLite database file
     * @param logger       the logger for debug and error messages
     */
    public SQLiteVoteStorage(Path databasePath, HytaleLogger logger) {
        this.databasePath = databasePath;
        this.logger = logger;
    }

    @Override
    public void initialize() throws StorageException {
        try {
            // Ensure parent directory exists
            Path parentDir = databasePath.getParent();
            if (parentDir != null) {
                java.nio.file.Files.createDirectories(parentDir);
            }

            // Explicitly load the SQLite JDBC driver to ensure it's registered with DriverManager
            Class.forName("org.sqlite.JDBC");

            // Connect to the database (creates file if it doesn't exist)
            String jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
            connection = DriverManager.getConnection(jdbcUrl);

            // Enable WAL mode for better concurrent performance
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
            }

            // Create table if it doesn't exist
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(CREATE_TABLE_SQL);
            }

            // Create index on timestamp for efficient cleanup queries
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_last_vote ON %s (last_vote_timestamp)".formatted(TABLE_NAME));
            }

            logger.at(Level.INFO).log("SQLite vote storage initialized at %s", databasePath);
        } catch (SQLException e) {
            throw new StorageException("Failed to initialize SQLite database", e);
        } catch (java.io.IOException e) {
            throw new StorageException("Failed to create database directory", e);
        } catch (ClassNotFoundException e) {
            throw new StorageException("SQLite JDBC driver not found", e);
        }
    }

    @Override
    public void recordVote(String username, long timestamp) {
        if (connection == null) {
            logger.at(Level.WARNING).log("Cannot record vote: SQLite storage not initialized");
            return;
        }

        try (PreparedStatement stmt = connection.prepareStatement(UPSERT_SQL)) {
            stmt.setString(1, username.toLowerCase());
            stmt.setLong(2, timestamp);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.at(Level.WARNING).log("Failed to record vote for %s: %s", username, e.getMessage());
        }
    }

    @Override
    public Optional<Long> getLastVoteTimestamp(String username) {
        if (connection == null) {
            return Optional.empty();
        }

        try (PreparedStatement stmt = connection.prepareStatement(SELECT_SQL)) {
            stmt.setString(1, username.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getLong("last_vote_timestamp"));
                }
            }
        } catch (SQLException e) {
            logger.at(Level.WARNING).log("Failed to get last vote for %s: %s", username, e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public int cleanupExpiredVotes(int voteExpiryInterval) {
        if (connection == null) {
            return 0;
        }

        long expiryMillis = voteExpiryInterval * 60L * 60L * 1000L;
        long cutoffTimestamp = System.currentTimeMillis() - expiryMillis;

        try (PreparedStatement stmt = connection.prepareStatement(DELETE_EXPIRED_SQL)) {
            stmt.setLong(1, cutoffTimestamp);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            logger.at(Level.WARNING).log("Failed to cleanup expired votes: %s", e.getMessage());
            return 0;
        }
    }

    @Override
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
                logger.at(Level.INFO).log("SQLite vote storage closed");
            } catch (SQLException e) {
                logger.at(Level.WARNING).log("Failed to close SQLite connection: %s", e.getMessage());
            }
            connection = null;
        }
    }

    @Override
    public String getType() {
        return "sqlite";
    }

    /**
     * Returns the path to the database file.
     *
     * @return the database path
     */
    public Path getDatabasePath() {
        return databasePath;
    }

    /**
     * Checks if the storage is connected and operational.
     *
     * @return true if the connection is valid
     */
    public boolean isConnected() {
        if (connection == null) {
            return false;
        }
        try {
            return connection.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }
}
