package io.hyvexa.core.db;

import com.hypixel.hytale.logger.HytaleLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Manages the MySQL connection pool and schema setup for parkour data. */
public class DatabaseManager implements ConnectionProvider {

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
    private static volatile DatabaseManager instance;
    private static final Object INIT_LOCK = new Object();
    private volatile HikariDataSource dataSource;

    private DatabaseManager() {
    }

    public static DatabaseManager createAndRegister() {
        if (instance != null) {
            throw new IllegalStateException("DatabaseManager already initialized");
        }
        instance = new DatabaseManager();
        return instance;
    }

    public static DatabaseManager get() {
        DatabaseManager ref = instance;
        if (ref == null) {
            throw new IllegalStateException("DatabaseManager not yet initialized — check plugin load order");
        }
        return ref;
    }

    public static void destroy() {
        instance = null;
    }

    /**
     * Initialize using credentials from config file (Parkour/database.json).
     */
    public void initialize() {
        synchronized (INIT_LOCK) {
            if (isInitialized()) {
                LOGGER.atWarning().log("DatabaseManager already initialized, skipping");
                return;
            }
            DatabaseConfig config = DatabaseConfig.load();
            LOGGER.atInfo().log("DB config loaded. Host=" + config.getHost()
                    + " Port=" + config.getPort()
                    + " Database=" + config.getDatabase()
                    + " User=" + config.getUser());
            initPool(config.getHost(), config.getPort(), config.getDatabase(),
                    config.getUser(), config.getPassword());
        }
    }

    /**
     * Initialize with custom credentials.
     */
    public void initialize(String host, int port, String database, String user, String password) {
        synchronized (INIT_LOCK) {
            // Close existing dataSource if present before reinitializing
            closePool("reinitializing");
            initPool(host, port, database, user, password);
        }
    }

    public Connection getConnection() throws SQLException {
        HikariDataSource source = dataSource;
        if (source == null || source.isClosed()) {
            throw new SQLException("Database not initialized");
        }
        return source.getConnection();
    }

    public void shutdown() {
        synchronized (INIT_LOCK) {
            closePool("shutdown");
        }
    }

    public boolean isInitialized() {
        HikariDataSource source = dataSource;
        return source != null && !source.isClosed();
    }

    private void initPool(String host, int port, String database, String user, String password) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Connection pool settings
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setIdleTimeout(300000);       // 5 minutes
        hikariConfig.setConnectionTimeout(10000);  // 10 seconds
        hikariConfig.setMaxLifetime(1800000);      // 30 minutes

        // MySQL optimizations
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

        try {
            dataSource = new HikariDataSource(hikariConfig);
            LOGGER.atInfo().log("Database connection pool initialized successfully");
            ensureCheckpointTimesTable();
            ensureDuelEnabledColumn();
            // Verify database is reachable
            try (Connection conn = getConnection();
                 PreparedStatement stmt = prepare(conn, "SELECT 1")) {
                stmt.executeQuery();
                LOGGER.atInfo().log("Database health check passed");
            } catch (SQLException healthEx) {
                LOGGER.atSevere().withCause(healthEx).log("DATABASE HEALTH CHECK FAILED - database may be unreachable");
            }
        } catch (Exception e) {
            // Init failure is fatal -- propagate as RuntimeException to prevent startup with broken DB
            LOGGER.atSevere().withCause(e).log("Failed to initialize database connection pool");
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void closePool(String reason) {
        HikariDataSource source = dataSource;
        if (source == null || source.isClosed()) {
            return;
        }
        LOGGER.atInfo().log("Closing database connection pool (" + reason + ")");
        source.close();
        dataSource = null;
    }

    /**
     * Tests the database connection by executing a simple query.
     * @return TestResult with success status and message
     */
    public TestResult testConnection() {
        if (!isInitialized()) {
            return new TestResult(false, "Database not initialized");
        }

        try (Connection conn = getConnection()) {
            // Test basic connectivity
            if (!conn.isValid(5)) {
                return new TestResult(false, "Connection is not valid");
            }

            // Test we can query
            try (PreparedStatement stmt = prepare(conn, "SELECT 1")) {
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

            String tableCheckSql = "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
            try (PreparedStatement tableStmt = prepare(conn, tableCheckSql)) {
                for (String table : tables) {
                    tableStmt.setString(1, table);
                    try (ResultSet rs = tableStmt.executeQuery()) {
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
        try (PreparedStatement stmt = prepare(conn, "SELECT COUNT(*) FROM " + table)) {
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
            LOGGER.atWarning().withCause(e).log("Failed to ensure player_checkpoint_times table");
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
            LOGGER.atWarning().withCause(e).log("Failed to ensure maps.duel_enabled column");
        }
    }

    public static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        String sql = """
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
            """;
        try (PreparedStatement stmt = prepare(conn, sql)) {
            stmt.setString(1, table);
            stmt.setString(2, column);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void addColumnIfMissing(Connection conn, String table, String column, String definition) {
        try {
            if (columnExists(conn, table, column)) {
                return;
            }
            String sql = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition;
            try (PreparedStatement stmt = prepare(conn, sql)) {
                stmt.executeUpdate();
                LOGGER.atInfo().log("Added column " + table + "." + column);
            }
        } catch (SQLException e) {
            LOGGER.atWarning().log("Failed to add column " + table + "." + column + ": " + e.getMessage());
        }
    }

    public static void renameColumnIfExists(Connection conn, String table, String oldColumn, String newColumn, String definition) {
        try {
            if (!columnExists(conn, table, oldColumn)) {
                return;
            }
            String sql = "ALTER TABLE " + table + " CHANGE COLUMN " + oldColumn + " " + newColumn + " " + definition;
            try (PreparedStatement stmt = prepare(conn, sql)) {
                stmt.executeUpdate();
                LOGGER.atInfo().log("Renamed column " + table + "." + oldColumn + " to " + newColumn);
            }
        } catch (SQLException e) {
            LOGGER.atWarning().log("Failed to rename column " + table + "." + oldColumn + ": " + e.getMessage());
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

    public static PreparedStatement prepare(Connection conn, String sql) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(sql);
        applyQueryTimeout(stmt);
        return stmt;
    }

    /**
     * Execute an action inside a transaction, returning a result.
     * Handles getConnection, setAutoCommit(false), commit, rollback, and setAutoCommit(true).
     *
     * @param action       receives the Connection, returns a result
     * @param defaultValue returned when the action throws
     * @return the action's result, or defaultValue on failure
     */
    public <T> T withTransaction(SQLFunction<Connection, T> action, T defaultValue) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = action.apply(conn);
                conn.commit();
                return result;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Transaction failed");
            return defaultValue;
        }
    }

    /**
     * Execute a void action inside a transaction.
     * Handles getConnection, setAutoCommit(false), commit, rollback, and setAutoCommit(true).
     *
     * @param action receives the Connection
     * @return true if the transaction committed successfully, false on failure
     */
    public boolean withTransaction(SQLConsumer<Connection> action) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                action.accept(conn);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Transaction failed");
            return false;
        }
    }

    /**
     * Execute an action inside a transaction on an existing connection.
     * Saves and restores the connection's autocommit state.
     * Intended for migrations that receive a connection from a caller.
     *
     * @param conn   an existing Connection (not closed by this method)
     * @param action receives the Connection
     */
    public static void withTransaction(Connection conn, SQLConsumer<Connection> action) throws SQLException {
        boolean wasAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            action.accept(conn);
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException re) { /* ignore */ }
            throw e;
        } finally {
            try { conn.setAutoCommit(wasAutoCommit); } catch (SQLException e) { /* ignore */ }
        }
    }

    public static void applyQueryTimeout(PreparedStatement stmt) throws SQLException {
        if (stmt != null) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        }
    }

    private static final long SLOW_QUERY_THRESHOLD_MS = 500;

    /**
     * Log a warning if a query took longer than the slow query threshold.
     * Call after executing any query with the elapsed time.
     */
    public static void logSlowQuery(String context, long startNanos) {
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (elapsedMs > SLOW_QUERY_THRESHOLD_MS) {
            LOGGER.atWarning().log("Slow query (" + elapsedMs + "ms): " + context);
        }
    }

    // ── JDBC Template Methods ─────────────────────────────────────────

    /**
     * Execute a query that returns a single row, mapped to a domain object.
     * Returns {@code defaultValue} if no row is found, DB is not initialized, or an error occurs.
     */
    public static <T> T queryOne(ConnectionProvider db, String sql, ParamBinder binder,
                                  RowMapper<T> mapper, T defaultValue) {
        if (!db.isInitialized()) return defaultValue;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = prepare(conn, sql)) {
            binder.bind(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapper.map(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("queryOne failed: " + truncateSql(sql));
        }
        return defaultValue;
    }

    /**
     * Execute a query that returns multiple rows, each mapped to a domain object.
     * Returns an empty list if DB is not initialized or an error occurs.
     */
    public static <T> List<T> queryList(ConnectionProvider db, String sql, ParamBinder binder,
                                         RowMapper<T> mapper) {
        List<T> result = new ArrayList<>();
        if (!db.isInitialized()) return result;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = prepare(conn, sql)) {
            binder.bind(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("queryList failed: " + truncateSql(sql));
        }
        return result;
    }

    /**
     * Execute an INSERT, UPDATE, or DELETE statement.
     * Returns {@code true} on success, {@code false} if DB is not initialized or an error occurs.
     */
    public static boolean execute(ConnectionProvider db, String sql, ParamBinder binder) {
        if (!db.isInitialized()) return false;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = prepare(conn, sql)) {
            binder.bind(stmt);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("execute failed: " + truncateSql(sql));
            return false;
        }
    }

    /**
     * Execute an INSERT, UPDATE, or DELETE statement and return the number of affected rows.
     * Returns {@code -1} if DB is not initialized or an error occurs.
     */
    public static int executeCount(ConnectionProvider db, String sql, ParamBinder binder) {
        if (!db.isInitialized()) return -1;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = prepare(conn, sql)) {
            binder.bind(stmt);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("executeCount failed: " + truncateSql(sql));
            return -1;
        }
    }

    /**
     * Execute a batch operation over a collection of items.
     * Returns {@code true} on success, {@code false} if DB is not initialized or an error occurs.
     */
    public static <T> boolean executeBatch(ConnectionProvider db, String sql,
                                            Iterable<T> items,
                                            SQLBiConsumer<PreparedStatement, T> binder) {
        if (!db.isInitialized()) return false;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = prepare(conn, sql)) {
            for (T item : items) {
                binder.accept(stmt, item);
                stmt.addBatch();
            }
            stmt.executeBatch();
            return true;
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("executeBatch failed: " + truncateSql(sql));
            return false;
        }
    }

    /** Convenience overload for parameterless queries. */
    public static <T> T queryOne(ConnectionProvider db, String sql,
                                  RowMapper<T> mapper, T defaultValue) {
        return queryOne(db, sql, stmt -> {}, mapper, defaultValue);
    }

    /** Convenience overload for parameterless queries. */
    public static <T> List<T> queryList(ConnectionProvider db, String sql, RowMapper<T> mapper) {
        return queryList(db, sql, stmt -> {}, mapper);
    }

    /** Convenience overload for parameterless statements. */
    public static boolean execute(ConnectionProvider db, String sql) {
        return execute(db, sql, stmt -> {});
    }

    private static String truncateSql(String sql) {
        if (sql == null) return "null";
        String trimmed = sql.strip();
        return trimmed.length() > 80 ? trimmed.substring(0, 80) + "..." : trimmed;
    }

    public record TestResult(boolean success, String message, Throwable cause) {
        public TestResult(boolean success, String message) {
            this(success, message, null);
        }
    }
}
