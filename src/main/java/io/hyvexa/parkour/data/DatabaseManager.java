package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

public class DatabaseManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static DatabaseManager instance;
    private HikariDataSource dataSource;
    private DatabaseConfig config;

    private DatabaseManager() {
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Initialize using credentials from config file (Parkour/database.json).
     */
    public void initialize() {
        config = DatabaseConfig.load();
        LOGGER.atInfo().log("DB config loaded. Host=" + config.getHost()
                + " Port=" + config.getPort()
                + " Database=" + config.getDatabase()
                + " User=" + config.getUser());
        initialize(config.getHost(), config.getPort(), config.getDatabase(),
                   config.getUser(), config.getPassword());
    }

    /**
     * Initialize with custom credentials.
     */
    public void initialize(String host, int port, String database, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.at(Level.SEVERE).log("MySQL driver not found on classpath: " + e.getMessage());
            throw new RuntimeException("MySQL driver missing", e);
        }

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
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("Failed to initialize database connection pool: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
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
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    return new TestResult(false, "SELECT 1 returned no results");
                }
            }

            // Test tables exist
            String[] tables = {"players", "maps", "player_completions", "map_checkpoints", "settings"};
            StringBuilder missingTables = new StringBuilder();

            for (String table : tables) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
                    stmt.setString(1, table);
                    ResultSet rs = stmt.executeQuery();
                    if (!rs.next()) {
                        if (missingTables.length() > 0) {
                            missingTables.append(", ");
                        }
                        missingTables.append(table);
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
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM " + table)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    public static class TestResult {
        public final boolean success;
        public final String message;
        public final Throwable cause;

        public TestResult(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.cause = null;
        }

        public TestResult(boolean success, String message, Throwable cause) {
            this.success = success;
            this.message = message;
            this.cause = cause;
        }
    }
}
