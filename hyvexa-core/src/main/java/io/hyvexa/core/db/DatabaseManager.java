package io.hyvexa.core.db;

import com.hypixel.hytale.logger.HytaleLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.logging.Level;

/** Manages the MySQL connection pool and schema setup for parkour data. */
public class DatabaseManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final int QUERY_TIMEOUT_SECONDS = 10;
    private static final Set<String> ALLOWED_COUNT_TABLES = Set.of(
            "players",
            "maps",
            "player_completions",
            "player_checkpoint_times",
            "map_checkpoints",
            "settings",
            "player_count_samples",
            "global_messages",
            "global_message_settings",
            "duel_matches",
            "duel_category_prefs",
            "duel_player_stats"
    );
    private static final DatabaseManager INSTANCE = new DatabaseManager();
    private static final Object INIT_LOCK = new Object();
    private volatile HikariDataSource dataSource;
    private volatile DatabaseConfig config;
    private volatile boolean initialized = false;

    private DatabaseManager() {
    }

    public static DatabaseManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initialize using credentials from config file (Parkour/database.json).
     */
    public void initialize() {
        synchronized (INIT_LOCK) {
            if (initialized) {
                LOGGER.atWarning().log("DatabaseManager already initialized, skipping");
                return;
            }
            config = DatabaseConfig.load();
            LOGGER.atInfo().log("DB config loaded. Host=" + config.getHost()
                    + " Port=" + config.getPort()
                    + " Database=" + config.getDatabase()
                    + " User=" + config.getUser());
            initialize(config.getHost(), config.getPort(), config.getDatabase(),
                       config.getUser(), config.getPassword());
            initialized = true;
        }
    }

    /**
     * Initialize with custom credentials.
     */
    public void initialize(String host, int port, String database, String user, String password) {
        synchronized (INIT_LOCK) {
            // Close existing dataSource if present before reinitializing
            if (dataSource != null && !dataSource.isClosed()) {
                LOGGER.atInfo().log("Closing existing database connection pool before reinitializing");
                dataSource.close();
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
            config.setUsername(user);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Connection pool settings
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(300000);       // 5 minutes
            config.setConnectionTimeout(10000);  // 10 seconds
            config.setMaxLifetime(1800000);      // 30 minutes

            // MySQL optimizations
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            try {
                dataSource = new HikariDataSource(config);
                LOGGER.atInfo().log("Database connection pool initialized successfully");
                ensureCheckpointTimesTable();
                ensureDuelEnabledColumn();
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("Failed to initialize database connection pool");
                throw new RuntimeException("Database initialization failed", e);
            }
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database not initialized");
        }
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.atInfo().log("Database connection pool closed");
        }
    }

    public boolean isInitialized() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Tests the database connection by executing a simple query.
     * @return TestResult with success status and message
     */
    public TestResult testConnection() {
        if (dataSource == null) {
            return new TestResult(false, "Database not initialized");
        }

        try (Connection conn = getConnection()) {
            // Test basic connectivity
            if (!conn.isValid(5)) {
                return new TestResult(false, "Connection is not valid");
            }

            // Test we can query
            try (PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
                applyQueryTimeout(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return new TestResult(false, "SELECT 1 returned no results");
                    }
                }
            }

            // Test tables exist
            String[] tables = {"players", "maps", "player_completions", "player_checkpoint_times", "map_checkpoints",
                    "settings"};
            StringBuilder missingTables = new StringBuilder();

            for (String table : tables) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
                    applyQueryTimeout(stmt);
                    stmt.setString(1, table);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            if (missingTables.length() > 0) {
                                missingTables.append(", ");
                            }
                            missingTables.append(table);
                        }
                    }
                }
            }

            if (missingTables.length() > 0) {
                return new TestResult(false, "Missing tables: " + missingTables);
            }

            // Count existing data
            int playerCount = countRows(conn, "players");
            int mapCount = countRows(conn, "maps");
            int completionCount = countRows(conn, "player_completions");

            return new TestResult(true, String.format(
                    "Connection successful! Tables found. Current data: %d players, %d maps, %d completions",
                    playerCount, mapCount, completionCount));

        } catch (SQLException e) {
            return new TestResult(false, "SQL error: " + e.getMessage(), e);
        }
    }

    private int countRows(Connection conn, String table) throws SQLException {
        if (table == null || !ALLOWED_COUNT_TABLES.contains(table)) {
            throw new SQLException("Invalid table name for countRows: " + table);
        }
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM " + table)) {
            applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    private void ensureCheckpointTimesTable() {
        try (Connection conn = getConnection()) {
            createPlayerCheckpointTimesTable(conn);
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to ensure player_checkpoint_times table");
        }
    }

    private void ensureDuelEnabledColumn() {
        try (Connection conn = getConnection()) {
            if (columnExists(conn, "maps", "duel_enabled")) {
                return;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE maps ADD COLUMN duel_enabled BOOLEAN DEFAULT FALSE");
            }
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to ensure maps.duel_enabled column");
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        String sql = """
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            applyQueryTimeout(stmt);
            stmt.setString(1, table);
            stmt.setString(2, column);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void createPlayerCheckpointTimesTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS player_checkpoint_times (
              player_uuid CHAR(36) NOT NULL,
              map_id VARCHAR(64) NOT NULL,
              checkpoint_index INT NOT NULL,
              time_ms BIGINT NOT NULL,
              PRIMARY KEY (player_uuid, map_id, checkpoint_index)
            ) ENGINE=InnoDB
            """;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    public static void applyQueryTimeout(PreparedStatement stmt) throws SQLException {
        if (stmt != null) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        }
    }

    public record TestResult(boolean success, String message, Throwable cause) {
        public TestResult(boolean success, String message) {
            this(success, message, null);
        }
    }
}
