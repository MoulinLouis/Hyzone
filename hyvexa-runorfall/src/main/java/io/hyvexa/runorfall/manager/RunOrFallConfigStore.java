package io.hyvexa.runorfall.manager;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.runorfall.data.RunOrFallConfig;
import io.hyvexa.runorfall.data.RunOrFallLocation;
import io.hyvexa.runorfall.data.RunOrFallPlatform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RunOrFallConfigStore {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final int SETTINGS_ID = 1;
    private static final double DEFAULT_VOID_Y = 40.0d;
    private static final double DEFAULT_BREAK_DELAY_SECONDS = 0.2d;
    private static final String CREATE_SETTINGS_TABLE = """
            CREATE TABLE IF NOT EXISTS runorfall_settings (
              id TINYINT NOT NULL PRIMARY KEY,
              lobby_x DOUBLE NULL,
              lobby_y DOUBLE NULL,
              lobby_z DOUBLE NULL,
              lobby_rot_x FLOAT NULL,
              lobby_rot_y FLOAT NULL,
              lobby_rot_z FLOAT NULL,
              void_y DOUBLE NOT NULL DEFAULT 40.0,
              block_break_delay_seconds DOUBLE NOT NULL DEFAULT 0.2
            ) ENGINE=InnoDB
            """;
    private static final String CREATE_SPAWNS_TABLE = """
            CREATE TABLE IF NOT EXISTS runorfall_spawns (
              spawn_order INT NOT NULL PRIMARY KEY,
              x DOUBLE NOT NULL,
              y DOUBLE NOT NULL,
              z DOUBLE NOT NULL,
              rot_x FLOAT NOT NULL,
              rot_y FLOAT NOT NULL,
              rot_z FLOAT NOT NULL
            ) ENGINE=InnoDB
            """;
    private static final String CREATE_PLATFORMS_TABLE = """
            CREATE TABLE IF NOT EXISTS runorfall_platforms (
              name VARCHAR(64) NOT NULL PRIMARY KEY,
              min_x INT NOT NULL,
              min_y INT NOT NULL,
              min_z INT NOT NULL,
              max_x INT NOT NULL,
              max_y INT NOT NULL,
              max_z INT NOT NULL
            ) ENGINE=InnoDB
            """;
    private static final String UPSERT_SETTINGS_SQL = """
            INSERT INTO runorfall_settings (id, lobby_x, lobby_y, lobby_z, lobby_rot_x, lobby_rot_y, lobby_rot_z,
                                            void_y, block_break_delay_seconds)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              lobby_x = VALUES(lobby_x),
              lobby_y = VALUES(lobby_y),
              lobby_z = VALUES(lobby_z),
              lobby_rot_x = VALUES(lobby_rot_x),
              lobby_rot_y = VALUES(lobby_rot_y),
              lobby_rot_z = VALUES(lobby_rot_z),
              void_y = VALUES(void_y),
              block_break_delay_seconds = VALUES(block_break_delay_seconds)
            """;

    private final File legacyConfigFile;
    private RunOrFallConfig config;

    public RunOrFallConfigStore(File legacyConfigFile) {
        this.legacyConfigFile = legacyConfigFile;
        this.config = new RunOrFallConfig();
        initializeTables();
        loadFromDatabase();
        migrateLegacyJsonIfNeeded();
    }

    public synchronized RunOrFallConfig snapshot() {
        return config.copy();
    }

    public synchronized RunOrFallLocation getLobby() {
        return config.lobby != null ? config.lobby.copy() : null;
    }

    public synchronized void setLobby(RunOrFallLocation location) {
        config.lobby = location != null ? location.copy() : null;
        saveSettingsToDatabase();
    }

    public synchronized double getVoidY() {
        return config.voidY;
    }

    public synchronized void setVoidY(double voidY) {
        if (!Double.isFinite(voidY)) {
            return;
        }
        config.voidY = voidY;
        saveSettingsToDatabase();
    }

    public synchronized double getBlockBreakDelaySeconds() {
        return config.blockBreakDelaySeconds;
    }

    public synchronized void setBlockBreakDelaySeconds(double delaySeconds) {
        if (!Double.isFinite(delaySeconds)) {
            return;
        }
        config.blockBreakDelaySeconds = Math.max(0.0, delaySeconds);
        saveSettingsToDatabase();
    }

    public synchronized List<RunOrFallLocation> getSpawns() {
        List<RunOrFallLocation> copy = new ArrayList<>();
        for (RunOrFallLocation spawn : config.spawns) {
            if (spawn != null) {
                copy.add(spawn.copy());
            }
        }
        return copy;
    }

    public synchronized void addSpawn(RunOrFallLocation location) {
        if (location == null) {
            return;
        }
        config.spawns.add(location.copy());
        saveSpawnsToDatabase();
    }

    public synchronized void clearSpawns() {
        config.spawns.clear();
        saveSpawnsToDatabase();
    }

    public synchronized List<RunOrFallPlatform> getPlatforms() {
        List<RunOrFallPlatform> copy = new ArrayList<>();
        for (RunOrFallPlatform platform : config.platforms) {
            if (platform != null) {
                copy.add(platform.copy());
            }
        }
        return copy;
    }

    public synchronized boolean upsertPlatform(RunOrFallPlatform platform) {
        if (platform == null || platform.name == null || platform.name.isBlank()) {
            return false;
        }
        String target = normalizeName(platform.name);
        for (int i = 0; i < config.platforms.size(); i++) {
            RunOrFallPlatform existing = config.platforms.get(i);
            if (existing == null || existing.name == null) {
                continue;
            }
            if (normalizeName(existing.name).equals(target)) {
                config.platforms.set(i, platform.copy());
                savePlatformsToDatabase();
                return true;
            }
        }
        config.platforms.add(platform.copy());
        savePlatformsToDatabase();
        return true;
    }

    public synchronized boolean removePlatform(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String target = normalizeName(name);
        boolean removed = config.platforms.removeIf(platform ->
                platform != null && platform.name != null && normalizeName(platform.name).equals(target));
        if (removed) {
            savePlatformsToDatabase();
        }
        return removed;
    }

    public synchronized void clearPlatforms() {
        config.platforms.clear();
        savePlatformsToDatabase();
    }

    private String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private synchronized void initializeTables() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, RunOrFall config will stay in-memory only.");
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(CREATE_SETTINGS_TABLE);
                stmt.executeUpdate(CREATE_SPAWNS_TABLE);
                stmt.executeUpdate(CREATE_PLATFORMS_TABLE);
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO runorfall_settings (id, void_y, block_break_delay_seconds) VALUES (?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE id = id")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setInt(1, SETTINGS_ID);
                stmt.setDouble(2, DEFAULT_VOID_Y);
                stmt.setDouble(3, DEFAULT_BREAK_DELAY_SECONDS);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize RunOrFall SQL tables.");
        }
    }

    private synchronized void loadFromDatabase() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        RunOrFallConfig loaded = new RunOrFallConfig();
        loaded.spawns = new ArrayList<>();
        loaded.platforms = new ArrayList<>();
        loaded.voidY = DEFAULT_VOID_Y;
        loaded.blockBreakDelaySeconds = DEFAULT_BREAK_DELAY_SECONDS;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            loadSettings(conn, loaded);
            loadSpawns(conn, loaded);
            loadPlatforms(conn, loaded);
            sanitizeLoadedConfig(loaded);
            config = loaded;
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load RunOrFall config from database.");
        }
    }

    private void loadSettings(Connection conn, RunOrFallConfig target) throws SQLException {
        String sql = """
                SELECT lobby_x, lobby_y, lobby_z, lobby_rot_x, lobby_rot_y, lobby_rot_z,
                       void_y, block_break_delay_seconds
                FROM runorfall_settings WHERE id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setInt(1, SETTINGS_ID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                Double lobbyX = rs.getObject("lobby_x", Double.class);
                if (lobbyX != null) {
                    double lobbyY = rs.getDouble("lobby_y");
                    double lobbyZ = rs.getDouble("lobby_z");
                    float rotX = rs.getFloat("lobby_rot_x");
                    float rotY = rs.getFloat("lobby_rot_y");
                    float rotZ = rs.getFloat("lobby_rot_z");
                    target.lobby = new RunOrFallLocation(lobbyX, lobbyY, lobbyZ, rotX, rotY, rotZ);
                }
                double loadedVoidY = rs.getDouble("void_y");
                target.voidY = Double.isFinite(loadedVoidY) ? loadedVoidY : DEFAULT_VOID_Y;
                double loadedBreakDelay = rs.getDouble("block_break_delay_seconds");
                if (!Double.isFinite(loadedBreakDelay) || loadedBreakDelay < 0.0d) {
                    loadedBreakDelay = DEFAULT_BREAK_DELAY_SECONDS;
                }
                target.blockBreakDelaySeconds = loadedBreakDelay;
            }
        }
    }

    private void loadSpawns(Connection conn, RunOrFallConfig target) throws SQLException {
        String sql = """
                SELECT x, y, z, rot_x, rot_y, rot_z
                FROM runorfall_spawns
                ORDER BY spawn_order ASC
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    RunOrFallLocation spawn = new RunOrFallLocation(
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("rot_x"),
                            rs.getFloat("rot_y"),
                            rs.getFloat("rot_z")
                    );
                    if (isFiniteLocation(spawn)) {
                        target.spawns.add(spawn);
                    }
                }
            }
        }
    }

    private void loadPlatforms(Connection conn, RunOrFallConfig target) throws SQLException {
        String sql = """
                SELECT name, min_x, min_y, min_z, max_x, max_y, max_z
                FROM runorfall_platforms
                ORDER BY name ASC
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    RunOrFallPlatform platform = new RunOrFallPlatform(
                            name,
                            rs.getInt("min_x"),
                            rs.getInt("min_y"),
                            rs.getInt("min_z"),
                            rs.getInt("max_x"),
                            rs.getInt("max_y"),
                            rs.getInt("max_z")
                    );
                    target.platforms.add(platform);
                }
            }
        }
    }

    private synchronized void saveSettingsToDatabase() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPSERT_SETTINGS_SQL)) {
            DatabaseManager.applyQueryTimeout(stmt);
            int i = 1;
            stmt.setInt(i++, SETTINGS_ID);
            if (config.lobby != null && isFiniteLocation(config.lobby)) {
                stmt.setDouble(i++, config.lobby.x);
                stmt.setDouble(i++, config.lobby.y);
                stmt.setDouble(i++, config.lobby.z);
                stmt.setFloat(i++, config.lobby.rotX);
                stmt.setFloat(i++, config.lobby.rotY);
                stmt.setFloat(i++, config.lobby.rotZ);
            } else {
                stmt.setNull(i++, java.sql.Types.DOUBLE);
                stmt.setNull(i++, java.sql.Types.DOUBLE);
                stmt.setNull(i++, java.sql.Types.DOUBLE);
                stmt.setNull(i++, java.sql.Types.FLOAT);
                stmt.setNull(i++, java.sql.Types.FLOAT);
                stmt.setNull(i++, java.sql.Types.FLOAT);
            }
            stmt.setDouble(i++, config.voidY);
            stmt.setDouble(i, config.blockBreakDelaySeconds);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save RunOrFall settings.");
        }
    }

    private synchronized void saveSpawnsToDatabase() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String deleteSql = "DELETE FROM runorfall_spawns";
        String insertSql = "INSERT INTO runorfall_spawns (spawn_order, x, y, z, rot_x, rot_y, rot_z) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                DatabaseManager.applyQueryTimeout(deleteStmt);
                DatabaseManager.applyQueryTimeout(insertStmt);
                deleteStmt.executeUpdate();

                int index = 0;
                for (RunOrFallLocation spawn : config.spawns) {
                    if (spawn == null || !isFiniteLocation(spawn)) {
                        continue;
                    }
                    insertStmt.setInt(1, index++);
                    insertStmt.setDouble(2, spawn.x);
                    insertStmt.setDouble(3, spawn.y);
                    insertStmt.setDouble(4, spawn.z);
                    insertStmt.setFloat(5, spawn.rotX);
                    insertStmt.setFloat(6, spawn.rotY);
                    insertStmt.setFloat(7, spawn.rotZ);
                    insertStmt.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save RunOrFall spawns.");
        }
    }

    private synchronized void savePlatformsToDatabase() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String deleteSql = "DELETE FROM runorfall_platforms";
        String insertSql = """
                INSERT INTO runorfall_platforms (name, min_x, min_y, min_z, max_x, max_y, max_z)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                DatabaseManager.applyQueryTimeout(deleteStmt);
                DatabaseManager.applyQueryTimeout(insertStmt);
                deleteStmt.executeUpdate();

                for (RunOrFallPlatform platform : config.platforms) {
                    if (platform == null || platform.name == null || platform.name.isBlank()) {
                        continue;
                    }
                    insertStmt.setString(1, platform.name);
                    insertStmt.setInt(2, platform.minX);
                    insertStmt.setInt(3, platform.minY);
                    insertStmt.setInt(4, platform.minZ);
                    insertStmt.setInt(5, platform.maxX);
                    insertStmt.setInt(6, platform.maxY);
                    insertStmt.setInt(7, platform.maxZ);
                    insertStmt.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save RunOrFall platforms.");
        }
    }

    private synchronized void persistAllToDatabase() {
        saveSettingsToDatabase();
        saveSpawnsToDatabase();
        savePlatformsToDatabase();
    }

    private synchronized void migrateLegacyJsonIfNeeded() {
        if (legacyConfigFile == null || !legacyConfigFile.exists()) {
            return;
        }
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        if (!isDatabaseConfigEmpty(config)) {
            return;
        }
        RunOrFallConfig legacy = readLegacyConfigFile();
        if (legacy == null) {
            return;
        }
        sanitizeLoadedConfig(legacy);
        config = legacy;
        persistAllToDatabase();
        markLegacyConfigMigrated();
        LOGGER.atInfo().log("RunOrFall legacy JSON config migrated to SQL.");
    }

    private RunOrFallConfig readLegacyConfigFile() {
        Path filePath = legacyConfigFile.toPath();
        try {
            if (!Files.exists(filePath)) {
                return null;
            }
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            RunOrFallConfig loaded = GSON.fromJson(json, RunOrFallConfig.class);
            return loaded != null ? loaded : new RunOrFallConfig();
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed reading legacy RunOrFall config JSON.");
            return null;
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Failed parsing legacy RunOrFall config JSON.");
            return null;
        }
    }

    private void markLegacyConfigMigrated() {
        if (legacyConfigFile == null) {
            return;
        }
        try {
            Path source = legacyConfigFile.toPath();
            if (!Files.exists(source)) {
                return;
            }
            Path target = source.resolveSibling(legacyConfigFile.getName() + ".migrated");
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to mark legacy RunOrFall JSON as migrated.");
        }
    }

    private boolean isDatabaseConfigEmpty(RunOrFallConfig snapshot) {
        if (snapshot == null) {
            return true;
        }
        if (snapshot.lobby != null) {
            return false;
        }
        if (snapshot.spawns != null && !snapshot.spawns.isEmpty()) {
            return false;
        }
        if (snapshot.platforms != null && !snapshot.platforms.isEmpty()) {
            return false;
        }
        return nearlyEqual(snapshot.voidY, DEFAULT_VOID_Y)
                && nearlyEqual(snapshot.blockBreakDelaySeconds, DEFAULT_BREAK_DELAY_SECONDS);
    }

    private static boolean nearlyEqual(double a, double b) {
        return Math.abs(a - b) < 1.0e-9;
    }

    private void sanitizeLoadedConfig(RunOrFallConfig loaded) {
        if (loaded == null) {
            return;
        }
        if (!Double.isFinite(loaded.voidY)) {
            loaded.voidY = DEFAULT_VOID_Y;
        }
        if (!Double.isFinite(loaded.blockBreakDelaySeconds) || loaded.blockBreakDelaySeconds < 0.0d) {
            loaded.blockBreakDelaySeconds = DEFAULT_BREAK_DELAY_SECONDS;
        }
        if (loaded.spawns == null) {
            loaded.spawns = new ArrayList<>();
        } else {
            List<RunOrFallLocation> sanitizedSpawns = new ArrayList<>();
            for (RunOrFallLocation spawn : loaded.spawns) {
                if (spawn != null && isFiniteLocation(spawn)) {
                    sanitizedSpawns.add(spawn.copy());
                }
            }
            loaded.spawns = sanitizedSpawns;
        }
        if (loaded.platforms == null) {
            loaded.platforms = new ArrayList<>();
        } else {
            List<RunOrFallPlatform> sanitizedPlatforms = new ArrayList<>();
            for (RunOrFallPlatform platform : loaded.platforms) {
                if (platform == null || platform.name == null || platform.name.isBlank()) {
                    continue;
                }
                sanitizedPlatforms.add(platform.copy());
            }
            loaded.platforms = sanitizedPlatforms;
        }
        if (loaded.lobby != null && !isFiniteLocation(loaded.lobby)) {
            loaded.lobby = null;
        }
    }

    private boolean isFiniteLocation(RunOrFallLocation location) {
        if (location == null) {
            return false;
        }
        return Double.isFinite(location.x)
                && Double.isFinite(location.y)
                && Double.isFinite(location.z)
                && Float.isFinite(location.rotX)
                && Float.isFinite(location.rotY)
                && Float.isFinite(location.rotZ);
    }
}
