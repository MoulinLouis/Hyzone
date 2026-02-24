package io.hyvexa.runorfall.manager;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.runorfall.data.RunOrFallConfig;
import io.hyvexa.runorfall.data.RunOrFallLocation;
import io.hyvexa.runorfall.data.RunOrFallMapConfig;
import io.hyvexa.runorfall.data.RunOrFallPlatform;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RunOrFallConfigStore {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final int SETTINGS_ID = 1;
    private static final String DEFAULT_MAP_ID = "default";
    private static final double DEFAULT_VOID_Y = 40.0d;
    private static final double DEFAULT_BREAK_DELAY_SECONDS = 0.2d;
    private static final int DEFAULT_MIN_PLAYERS = 2;
    private static final int DEFAULT_MIN_PLAYERS_TIME_SECONDS = 300;
    private static final int DEFAULT_OPTIMAL_PLAYERS = 4;
    private static final int DEFAULT_OPTIMAL_PLAYERS_TIME_SECONDS = 60;
    private static final int DEFAULT_BLINK_DISTANCE_BLOCKS = 7;

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
              block_break_delay_seconds DOUBLE NOT NULL DEFAULT 0.2,
              min_players INT NOT NULL DEFAULT 2,
              min_players_time_seconds INT NOT NULL DEFAULT 300,
              optimal_players INT NOT NULL DEFAULT 4,
              optimal_players_time_seconds INT NOT NULL DEFAULT 60,
              blink_distance_blocks INT NOT NULL DEFAULT 7,
              active_map_id VARCHAR(64) NULL
            ) ENGINE=InnoDB
            """;

    private static final String CREATE_MAPS_TABLE = """
            CREATE TABLE IF NOT EXISTS runorfall_maps (
              map_id VARCHAR(64) NOT NULL PRIMARY KEY,
              lobby_x DOUBLE NULL,
              lobby_y DOUBLE NULL,
              lobby_z DOUBLE NULL,
              lobby_rot_x FLOAT NULL,
              lobby_rot_y FLOAT NULL,
              lobby_rot_z FLOAT NULL
            ) ENGINE=InnoDB
            """;

    private static final String CREATE_MAP_SPAWNS_TABLE = """
            CREATE TABLE IF NOT EXISTS runorfall_map_spawns (
              map_id VARCHAR(64) NOT NULL,
              spawn_order INT NOT NULL,
              x DOUBLE NOT NULL,
              y DOUBLE NOT NULL,
              z DOUBLE NOT NULL,
              rot_x FLOAT NOT NULL,
              rot_y FLOAT NOT NULL,
              rot_z FLOAT NOT NULL,
              PRIMARY KEY (map_id, spawn_order)
            ) ENGINE=InnoDB
            """;

    private static final String CREATE_MAP_PLATFORMS_TABLE = """
            CREATE TABLE IF NOT EXISTS runorfall_map_platforms (
              map_id VARCHAR(64) NOT NULL,
              platform_order INT NOT NULL,
              min_x INT NOT NULL,
              min_y INT NOT NULL,
              min_z INT NOT NULL,
              max_x INT NOT NULL,
              max_y INT NOT NULL,
              max_z INT NOT NULL,
              target_block_item_id VARCHAR(128) NULL,
              PRIMARY KEY (map_id, platform_order)
            ) ENGINE=InnoDB
            """;

    private static final String UPSERT_SETTINGS_SQL = """
            INSERT INTO runorfall_settings (id, lobby_x, lobby_y, lobby_z, lobby_rot_x, lobby_rot_y, lobby_rot_z,
                                            void_y, block_break_delay_seconds, min_players, min_players_time_seconds,
                                            optimal_players, optimal_players_time_seconds, blink_distance_blocks,
                                            active_map_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              lobby_x = VALUES(lobby_x),
              lobby_y = VALUES(lobby_y),
              lobby_z = VALUES(lobby_z),
              lobby_rot_x = VALUES(lobby_rot_x),
              lobby_rot_y = VALUES(lobby_rot_y),
              lobby_rot_z = VALUES(lobby_rot_z),
              void_y = VALUES(void_y),
              block_break_delay_seconds = VALUES(block_break_delay_seconds),
              min_players = VALUES(min_players),
              min_players_time_seconds = VALUES(min_players_time_seconds),
              optimal_players = VALUES(optimal_players),
              optimal_players_time_seconds = VALUES(optimal_players_time_seconds),
              blink_distance_blocks = VALUES(blink_distance_blocks),
              active_map_id = VALUES(active_map_id)
            """;

    private final File legacyConfigFile;
    private RunOrFallConfig config;

    public RunOrFallConfigStore(File legacyConfigFile) {
        this.legacyConfigFile = legacyConfigFile;
        this.config = createDefaultConfig();
        initializeTables();
        loadFromDatabase();
        migrateLegacyJsonIfNeeded();
    }

    public synchronized RunOrFallConfig snapshot() {
        return config.copy();
    }

    public synchronized List<String> listMapIds() {
        List<String> ids = new ArrayList<>();
        for (RunOrFallMapConfig map : config.maps) {
            if (map != null && map.id != null && !map.id.isBlank()) {
                ids.add(map.id);
            }
        }
        return ids;
    }

    public synchronized String getSelectedMapId() {
        return config.selectedMapId;
    }

    public synchronized boolean selectMap(String mapId) {
        String normalized = normalizeMapId(mapId);
        if (normalized.isEmpty()) {
            return false;
        }
        RunOrFallMapConfig target = findMapByIdInternal(normalized);
        if (target == null) {
            return false;
        }
        config.selectedMapId = target.id;
        saveSettingsToDatabase();
        return true;
    }

    public synchronized boolean createMap(String mapId) {
        String normalized = normalizeMapId(mapId);
        if (normalized.isEmpty()) {
            return false;
        }
        if (findMapByIdInternal(normalized) != null) {
            return false;
        }
        RunOrFallMapConfig map = new RunOrFallMapConfig();
        map.id = normalized;
        config.maps.add(map);
        if (config.selectedMapId == null || config.selectedMapId.isBlank()) {
            config.selectedMapId = normalized;
        }
        saveMapsToDatabase();
        saveSettingsToDatabase();
        return true;
    }

    public synchronized boolean deleteMap(String mapId) {
        String normalized = normalizeMapId(mapId);
        if (normalized.isEmpty()) {
            return false;
        }
        boolean removed = config.maps.removeIf(map -> map != null && normalized.equals(map.id));
        if (!removed) {
            return false;
        }
        if (config.maps.isEmpty()) {
            RunOrFallMapConfig fallback = new RunOrFallMapConfig();
            fallback.id = DEFAULT_MAP_ID;
            config.maps.add(fallback);
        }
        RunOrFallMapConfig selected = getSelectedMapInternal();
        config.selectedMapId = selected != null ? selected.id : DEFAULT_MAP_ID;
        persistAllToDatabase();
        return true;
    }

    public synchronized RunOrFallLocation getLobby() {
        RunOrFallMapConfig map = getOrCreateSelectedMapInternal();
        return map.lobby != null ? map.lobby.copy() : null;
    }

    public synchronized void setLobby(RunOrFallLocation location) {
        RunOrFallMapConfig map = getOrCreateSelectedMapInternal();
        map.lobby = location != null ? location.copy() : null;
        saveMapsToDatabase();
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

    public synchronized int getMinPlayers() {
        return sanitizeMinPlayers(config.minPlayers);
    }

    public synchronized int getMinPlayersTimeSeconds() {
        return sanitizeCountdownTime(config.minPlayersTimeSeconds, DEFAULT_MIN_PLAYERS_TIME_SECONDS);
    }

    public synchronized int getOptimalPlayers() {
        int minPlayers = getMinPlayers();
        return Math.max(minPlayers, sanitizeMinPlayers(config.optimalPlayers));
    }

    public synchronized int getOptimalPlayersTimeSeconds() {
        return sanitizeCountdownTime(config.optimalPlayersTimeSeconds, DEFAULT_OPTIMAL_PLAYERS_TIME_SECONDS);
    }

    public synchronized int getBlinkDistanceBlocks() {
        return sanitizeBlinkDistanceBlocks(config.blinkDistanceBlocks);
    }

    public synchronized void setAutoStartSettings(int minPlayers, int minPlayersTimeSeconds,
                                                  int optimalPlayers, int optimalPlayersTimeSeconds) {
        int sanitizedMinPlayers = sanitizeMinPlayers(minPlayers);
        int sanitizedMinPlayersTime = sanitizeCountdownTime(minPlayersTimeSeconds, DEFAULT_MIN_PLAYERS_TIME_SECONDS);
        int sanitizedOptimalPlayers = Math.max(sanitizedMinPlayers, sanitizeMinPlayers(optimalPlayers));
        int sanitizedOptimalPlayersTime = sanitizeCountdownTime(optimalPlayersTimeSeconds,
                DEFAULT_OPTIMAL_PLAYERS_TIME_SECONDS);

        config.minPlayers = sanitizedMinPlayers;
        config.minPlayersTimeSeconds = sanitizedMinPlayersTime;
        config.optimalPlayers = sanitizedOptimalPlayers;
        config.optimalPlayersTimeSeconds = sanitizedOptimalPlayersTime;
        saveSettingsToDatabase();
    }

    public synchronized void setBlinkDistanceBlocks(int blinkDistanceBlocks) {
        config.blinkDistanceBlocks = sanitizeBlinkDistanceBlocks(blinkDistanceBlocks);
        saveSettingsToDatabase();
    }

    public synchronized List<RunOrFallLocation> getSpawns() {
        List<RunOrFallLocation> copy = new ArrayList<>();
        RunOrFallMapConfig map = getOrCreateSelectedMapInternal();
        for (RunOrFallLocation spawn : map.spawns) {
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
        RunOrFallMapConfig map = getOrCreateSelectedMapInternal();
        map.spawns.add(location.copy());
        saveSpawnsToDatabase();
    }

    public synchronized void clearSpawns() {
        RunOrFallMapConfig map = getOrCreateSelectedMapInternal();
        map.spawns.clear();
        saveSpawnsToDatabase();
    }

    public synchronized List<RunOrFallPlatform> getPlatforms() {
        List<RunOrFallPlatform> copy = new ArrayList<>();
        RunOrFallMapConfig map = getOrCreateSelectedMapInternal();
        for (RunOrFallPlatform platform : map.platforms) {
            if (platform != null) {
                copy.add(platform.copy());
            }
        }
        return copy;
    }

    public synchronized boolean addPlatform(RunOrFallPlatform platform) {
        if (platform == null) {
            return false;
        }
        RunOrFallMapConfig map = getOrCreateSelectedMapInternal();
        map.platforms.add(platform.copy());
        savePlatformsToDatabase();
        return true;
    }

    public synchronized boolean removePlatformByIndex(int index) {
        RunOrFallMapConfig map = getOrCreateSelectedMapInternal();
        if (index < 0 || index >= map.platforms.size()) {
            return false;
        }
        map.platforms.remove(index);
        savePlatformsToDatabase();
        return true;
    }

    public synchronized void clearPlatforms() {
        RunOrFallMapConfig map = getOrCreateSelectedMapInternal();
        map.platforms.clear();
        savePlatformsToDatabase();
    }

    private synchronized void initializeTables() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, RunOrFall config will stay in-memory only.");
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(CREATE_SETTINGS_TABLE);
                stmt.executeUpdate(CREATE_MAPS_TABLE);
                stmt.executeUpdate(CREATE_MAP_SPAWNS_TABLE);
                stmt.executeUpdate(CREATE_MAP_PLATFORMS_TABLE);
            }
            ensureColumnExists(conn, "runorfall_settings", "active_map_id",
                    "ALTER TABLE runorfall_settings ADD COLUMN active_map_id VARCHAR(64) NULL");
            ensureColumnExists(conn, "runorfall_settings", "min_players",
                    "ALTER TABLE runorfall_settings ADD COLUMN min_players INT NOT NULL DEFAULT 2");
            ensureColumnExists(conn, "runorfall_settings", "min_players_time_seconds",
                    "ALTER TABLE runorfall_settings ADD COLUMN min_players_time_seconds INT NOT NULL DEFAULT 300");
            ensureColumnExists(conn, "runorfall_settings", "optimal_players",
                    "ALTER TABLE runorfall_settings ADD COLUMN optimal_players INT NOT NULL DEFAULT 4");
            ensureColumnExists(conn, "runorfall_settings", "optimal_players_time_seconds",
                    "ALTER TABLE runorfall_settings ADD COLUMN optimal_players_time_seconds INT NOT NULL DEFAULT 60");
            ensureColumnExists(conn, "runorfall_settings", "blink_distance_blocks",
                    "ALTER TABLE runorfall_settings ADD COLUMN blink_distance_blocks INT NOT NULL DEFAULT 7");
            ensureColumnExists(conn, "runorfall_map_platforms", "target_block_item_id",
                    "ALTER TABLE runorfall_map_platforms ADD COLUMN target_block_item_id VARCHAR(128) NULL");
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO runorfall_settings "
                    + "(id, void_y, block_break_delay_seconds, min_players, min_players_time_seconds, "
                            + "optimal_players, optimal_players_time_seconds, blink_distance_blocks, active_map_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE id = id")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setInt(1, SETTINGS_ID);
                stmt.setDouble(2, DEFAULT_VOID_Y);
                stmt.setDouble(3, DEFAULT_BREAK_DELAY_SECONDS);
                stmt.setInt(4, DEFAULT_MIN_PLAYERS);
                stmt.setInt(5, DEFAULT_MIN_PLAYERS_TIME_SECONDS);
                stmt.setInt(6, DEFAULT_OPTIMAL_PLAYERS);
                stmt.setInt(7, DEFAULT_OPTIMAL_PLAYERS_TIME_SECONDS);
                stmt.setInt(8, DEFAULT_BLINK_DISTANCE_BLOCKS);
                stmt.setString(9, DEFAULT_MAP_ID);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize RunOrFall SQL tables.");
        }
    }

    private synchronized void loadFromDatabase() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            config = createDefaultConfig();
            return;
        }

        RunOrFallConfig loaded = new RunOrFallConfig();
        loaded.voidY = DEFAULT_VOID_Y;
        loaded.blockBreakDelaySeconds = DEFAULT_BREAK_DELAY_SECONDS;
        loaded.minPlayers = DEFAULT_MIN_PLAYERS;
        loaded.minPlayersTimeSeconds = DEFAULT_MIN_PLAYERS_TIME_SECONDS;
        loaded.optimalPlayers = DEFAULT_OPTIMAL_PLAYERS;
        loaded.optimalPlayersTimeSeconds = DEFAULT_OPTIMAL_PLAYERS_TIME_SECONDS;
        loaded.blinkDistanceBlocks = DEFAULT_BLINK_DISTANCE_BLOCKS;
        loaded.selectedMapId = "";
        loaded.maps = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            loadSettings(conn, loaded);
            loadMaps(conn, loaded);
            loadMapSpawns(conn, loaded);
            loadMapPlatforms(conn, loaded);
            if (loaded.maps.isEmpty()) {
                loadLegacySingleMap(conn, loaded);
            }
            sanitizeLoadedConfig(loaded);
            config = loaded;
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load RunOrFall config from database.");
        }
    }
    private void loadSettings(Connection conn, RunOrFallConfig target) throws SQLException {
        String sql = """
                SELECT lobby_x, lobby_y, lobby_z, lobby_rot_x, lobby_rot_y, lobby_rot_z,
                       void_y, block_break_delay_seconds, min_players, min_players_time_seconds,
                       optimal_players, optimal_players_time_seconds, blink_distance_blocks, active_map_id
                FROM runorfall_settings
                WHERE id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setInt(1, SETTINGS_ID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                double loadedVoidY = rs.getDouble("void_y");
                target.voidY = Double.isFinite(loadedVoidY) ? loadedVoidY : DEFAULT_VOID_Y;
                double loadedBreakDelay = rs.getDouble("block_break_delay_seconds");
                if (!Double.isFinite(loadedBreakDelay) || loadedBreakDelay < 0.0d) {
                    loadedBreakDelay = DEFAULT_BREAK_DELAY_SECONDS;
                }
                target.blockBreakDelaySeconds = loadedBreakDelay;
                target.minPlayers = sanitizeMinPlayers(rs.getInt("min_players"));
                target.minPlayersTimeSeconds = sanitizeCountdownTime(
                        rs.getInt("min_players_time_seconds"), DEFAULT_MIN_PLAYERS_TIME_SECONDS);
                target.optimalPlayers = Math.max(target.minPlayers, sanitizeMinPlayers(rs.getInt("optimal_players")));
                target.optimalPlayersTimeSeconds = sanitizeCountdownTime(
                        rs.getInt("optimal_players_time_seconds"), DEFAULT_OPTIMAL_PLAYERS_TIME_SECONDS);
                target.blinkDistanceBlocks = sanitizeBlinkDistanceBlocks(rs.getInt("blink_distance_blocks"));
                String loadedActiveMapId = rs.getString("active_map_id");
                target.selectedMapId = normalizeMapId(loadedActiveMapId);

                Double lobbyX = rs.getObject("lobby_x", Double.class);
                if (lobbyX != null) {
                    target.lobby = new RunOrFallLocation(
                            lobbyX,
                            rs.getDouble("lobby_y"),
                            rs.getDouble("lobby_z"),
                            rs.getFloat("lobby_rot_x"),
                            rs.getFloat("lobby_rot_y"),
                            rs.getFloat("lobby_rot_z")
                    );
                }
            }
        }
    }

    private void loadMaps(Connection conn, RunOrFallConfig target) throws SQLException {
        String sql = """
                SELECT map_id, lobby_x, lobby_y, lobby_z, lobby_rot_x, lobby_rot_y, lobby_rot_z
                FROM runorfall_maps
                ORDER BY map_id ASC
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String mapId = normalizeMapId(rs.getString("map_id"));
                    if (mapId.isEmpty()) {
                        continue;
                    }
                    RunOrFallMapConfig map = new RunOrFallMapConfig();
                    map.id = mapId;
                    Double lobbyX = rs.getObject("lobby_x", Double.class);
                    if (lobbyX != null) {
                        map.lobby = new RunOrFallLocation(
                                lobbyX,
                                rs.getDouble("lobby_y"),
                                rs.getDouble("lobby_z"),
                                rs.getFloat("lobby_rot_x"),
                                rs.getFloat("lobby_rot_y"),
                                rs.getFloat("lobby_rot_z")
                        );
                    }
                    target.maps.add(map);
                }
            }
        }
    }

    private void loadMapSpawns(Connection conn, RunOrFallConfig target) throws SQLException {
        String sql = """
                SELECT map_id, x, y, z, rot_x, rot_y, rot_z
                FROM runorfall_map_spawns
                ORDER BY map_id ASC, spawn_order ASC
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    RunOrFallMapConfig map = findMapByIdInternal(target, normalizeMapId(rs.getString("map_id")));
                    if (map == null) {
                        continue;
                    }
                    RunOrFallLocation spawn = new RunOrFallLocation(
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("rot_x"),
                            rs.getFloat("rot_y"),
                            rs.getFloat("rot_z")
                    );
                    if (isFiniteLocation(spawn)) {
                        map.spawns.add(spawn);
                    }
                }
            }
        }
    }

    private void loadMapPlatforms(Connection conn, RunOrFallConfig target) throws SQLException {
        String sql = """
                SELECT map_id, min_x, min_y, min_z, max_x, max_y, max_z, target_block_item_id
                FROM runorfall_map_platforms
                ORDER BY map_id ASC, platform_order ASC
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    RunOrFallMapConfig map = findMapByIdInternal(target, normalizeMapId(rs.getString("map_id")));
                    if (map == null) {
                        continue;
                    }
                    String targetBlockItemId = rs.getString("target_block_item_id");
                    map.platforms.add(new RunOrFallPlatform(
                            rs.getInt("min_x"),
                            rs.getInt("min_y"),
                            rs.getInt("min_z"),
                            rs.getInt("max_x"),
                            rs.getInt("max_y"),
                            rs.getInt("max_z"),
                            targetBlockItemId
                    ));
                }
            }
        }
    }

    private void loadLegacySingleMap(Connection conn, RunOrFallConfig target) {
        try {
            if (!tableExists(conn, "runorfall_spawns") && !tableExists(conn, "runorfall_platforms")) {
                return;
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed checking legacy RunOrFall tables.");
            return;
        }

        RunOrFallMapConfig legacyMap = new RunOrFallMapConfig();
        legacyMap.id = DEFAULT_MAP_ID;
        legacyMap.lobby = target.lobby != null ? target.lobby.copy() : null;

        if (tableExistsSafe(conn, "runorfall_spawns")) {
            String spawnsSql = """
                    SELECT x, y, z, rot_x, rot_y, rot_z
                    FROM runorfall_spawns
                    ORDER BY spawn_order ASC
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(spawnsSql)) {
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
                            legacyMap.spawns.add(spawn);
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed loading legacy RunOrFall spawns.");
            }
        }

        if (tableExistsSafe(conn, "runorfall_platforms")) {
            String platformsSql = """
                    SELECT min_x, min_y, min_z, max_x, max_y, max_z
                    FROM runorfall_platforms
                    ORDER BY name ASC
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(platformsSql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        legacyMap.platforms.add(new RunOrFallPlatform(
                                rs.getInt("min_x"),
                                rs.getInt("min_y"),
                                rs.getInt("min_z"),
                                rs.getInt("max_x"),
                                rs.getInt("max_y"),
                                rs.getInt("max_z")
                        ));
                    }
                }
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed loading legacy RunOrFall platforms.");
            }
        }

        if (legacyMap.lobby != null || !legacyMap.spawns.isEmpty() || !legacyMap.platforms.isEmpty()) {
            target.maps.add(legacyMap);
            if (target.selectedMapId == null || target.selectedMapId.isBlank()) {
                target.selectedMapId = DEFAULT_MAP_ID;
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
            RunOrFallMapConfig selectedMap = getSelectedMapInternal();
            RunOrFallLocation selectedLobby = selectedMap != null ? selectedMap.lobby : null;

            int i = 1;
            stmt.setInt(i++, SETTINGS_ID);
            if (selectedLobby != null && isFiniteLocation(selectedLobby)) {
                stmt.setDouble(i++, selectedLobby.x);
                stmt.setDouble(i++, selectedLobby.y);
                stmt.setDouble(i++, selectedLobby.z);
                stmt.setFloat(i++, selectedLobby.rotX);
                stmt.setFloat(i++, selectedLobby.rotY);
                stmt.setFloat(i++, selectedLobby.rotZ);
            } else {
                stmt.setNull(i++, java.sql.Types.DOUBLE);
                stmt.setNull(i++, java.sql.Types.DOUBLE);
                stmt.setNull(i++, java.sql.Types.DOUBLE);
                stmt.setNull(i++, java.sql.Types.FLOAT);
                stmt.setNull(i++, java.sql.Types.FLOAT);
                stmt.setNull(i++, java.sql.Types.FLOAT);
            }
            stmt.setDouble(i++, config.voidY);
            stmt.setDouble(i++, config.blockBreakDelaySeconds);
            stmt.setInt(i++, sanitizeMinPlayers(config.minPlayers));
            stmt.setInt(i++, sanitizeCountdownTime(config.minPlayersTimeSeconds, DEFAULT_MIN_PLAYERS_TIME_SECONDS));
            stmt.setInt(i++, Math.max(sanitizeMinPlayers(config.minPlayers), sanitizeMinPlayers(config.optimalPlayers)));
            stmt.setInt(i++, sanitizeCountdownTime(config.optimalPlayersTimeSeconds, DEFAULT_OPTIMAL_PLAYERS_TIME_SECONDS));
            stmt.setInt(i++, sanitizeBlinkDistanceBlocks(config.blinkDistanceBlocks));
            stmt.setString(i, selectedMap != null ? selectedMap.id : DEFAULT_MAP_ID);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save RunOrFall settings.");
        }
    }

    private synchronized void saveMapsToDatabase() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String deleteSql = "DELETE FROM runorfall_maps";
        String insertSql = """
                INSERT INTO runorfall_maps (map_id, lobby_x, lobby_y, lobby_z, lobby_rot_x, lobby_rot_y, lobby_rot_z)
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

                for (RunOrFallMapConfig map : config.maps) {
                    if (map == null || map.id == null || map.id.isBlank()) {
                        continue;
                    }
                    insertStmt.setString(1, map.id);
                    if (map.lobby != null && isFiniteLocation(map.lobby)) {
                        insertStmt.setDouble(2, map.lobby.x);
                        insertStmt.setDouble(3, map.lobby.y);
                        insertStmt.setDouble(4, map.lobby.z);
                        insertStmt.setFloat(5, map.lobby.rotX);
                        insertStmt.setFloat(6, map.lobby.rotY);
                        insertStmt.setFloat(7, map.lobby.rotZ);
                    } else {
                        insertStmt.setNull(2, java.sql.Types.DOUBLE);
                        insertStmt.setNull(3, java.sql.Types.DOUBLE);
                        insertStmt.setNull(4, java.sql.Types.DOUBLE);
                        insertStmt.setNull(5, java.sql.Types.FLOAT);
                        insertStmt.setNull(6, java.sql.Types.FLOAT);
                        insertStmt.setNull(7, java.sql.Types.FLOAT);
                    }
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
            LOGGER.atWarning().withCause(e).log("Failed to save RunOrFall maps.");
        }
    }

    private synchronized void saveSpawnsToDatabase() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String deleteSql = "DELETE FROM runorfall_map_spawns";
        String insertSql = """
                INSERT INTO runorfall_map_spawns (map_id, spawn_order, x, y, z, rot_x, rot_y, rot_z)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                DatabaseManager.applyQueryTimeout(deleteStmt);
                DatabaseManager.applyQueryTimeout(insertStmt);
                deleteStmt.executeUpdate();

                for (RunOrFallMapConfig map : config.maps) {
                    if (map == null || map.id == null || map.id.isBlank()) {
                        continue;
                    }
                    int index = 0;
                    for (RunOrFallLocation spawn : map.spawns) {
                        if (spawn == null || !isFiniteLocation(spawn)) {
                            continue;
                        }
                        insertStmt.setString(1, map.id);
                        insertStmt.setInt(2, index++);
                        insertStmt.setDouble(3, spawn.x);
                        insertStmt.setDouble(4, spawn.y);
                        insertStmt.setDouble(5, spawn.z);
                        insertStmt.setFloat(6, spawn.rotX);
                        insertStmt.setFloat(7, spawn.rotY);
                        insertStmt.setFloat(8, spawn.rotZ);
                        insertStmt.executeUpdate();
                    }
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
        String deleteSql = "DELETE FROM runorfall_map_platforms";
        String insertSql = """
                INSERT INTO runorfall_map_platforms
                (map_id, platform_order, min_x, min_y, min_z, max_x, max_y, max_z, target_block_item_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                DatabaseManager.applyQueryTimeout(deleteStmt);
                DatabaseManager.applyQueryTimeout(insertStmt);
                deleteStmt.executeUpdate();

                for (RunOrFallMapConfig map : config.maps) {
                    if (map == null || map.id == null || map.id.isBlank()) {
                        continue;
                    }
                    int index = 0;
                    for (RunOrFallPlatform platform : map.platforms) {
                        if (platform == null) {
                            continue;
                        }
                        insertStmt.setString(1, map.id);
                        insertStmt.setInt(2, index++);
                        insertStmt.setInt(3, platform.minX);
                        insertStmt.setInt(4, platform.minY);
                        insertStmt.setInt(5, platform.minZ);
                        insertStmt.setInt(6, platform.maxX);
                        insertStmt.setInt(7, platform.maxY);
                        insertStmt.setInt(8, platform.maxZ);
                        String targetBlockItemId = platform.targetBlockItemId != null
                                ? platform.targetBlockItemId.trim()
                                : "";
                        if (targetBlockItemId.isBlank()) {
                            insertStmt.setNull(9, java.sql.Types.VARCHAR);
                        } else {
                            insertStmt.setString(9, targetBlockItemId);
                        }
                        insertStmt.executeUpdate();
                    }
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
        saveMapsToDatabase();
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
            return loaded != null ? loaded : createDefaultConfig();
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
        if (snapshot.maps != null && !snapshot.maps.isEmpty()) {
            if (snapshot.maps.size() == 1) {
                RunOrFallMapConfig only = snapshot.maps.get(0);
                boolean emptyDefaultMap = only != null
                        && DEFAULT_MAP_ID.equalsIgnoreCase(only.id)
                        && only.lobby == null
                        && (only.spawns == null || only.spawns.isEmpty())
                        && (only.platforms == null || only.platforms.isEmpty());
                boolean defaultSelection = snapshot.selectedMapId == null
                        || snapshot.selectedMapId.isBlank()
                        || DEFAULT_MAP_ID.equalsIgnoreCase(snapshot.selectedMapId);
                if (emptyDefaultMap && defaultSelection
                        && nearlyEqual(snapshot.voidY, DEFAULT_VOID_Y)
                        && nearlyEqual(snapshot.blockBreakDelaySeconds, DEFAULT_BREAK_DELAY_SECONDS)
                        && sanitizeMinPlayers(snapshot.minPlayers) == DEFAULT_MIN_PLAYERS
                        && sanitizeCountdownTime(snapshot.minPlayersTimeSeconds, DEFAULT_MIN_PLAYERS_TIME_SECONDS)
                        == DEFAULT_MIN_PLAYERS_TIME_SECONDS
                        && Math.max(sanitizeMinPlayers(snapshot.minPlayers), sanitizeMinPlayers(snapshot.optimalPlayers))
                        == DEFAULT_OPTIMAL_PLAYERS
                        && sanitizeCountdownTime(snapshot.optimalPlayersTimeSeconds, DEFAULT_OPTIMAL_PLAYERS_TIME_SECONDS)
                        == DEFAULT_OPTIMAL_PLAYERS_TIME_SECONDS
                        && sanitizeBlinkDistanceBlocks(snapshot.blinkDistanceBlocks) == DEFAULT_BLINK_DISTANCE_BLOCKS) {
                    return true;
                }
            }
            return false;
        }
        return nearlyEqual(snapshot.voidY, DEFAULT_VOID_Y)
                && nearlyEqual(snapshot.blockBreakDelaySeconds, DEFAULT_BREAK_DELAY_SECONDS)
                && sanitizeMinPlayers(snapshot.minPlayers) == DEFAULT_MIN_PLAYERS
                && sanitizeCountdownTime(snapshot.minPlayersTimeSeconds, DEFAULT_MIN_PLAYERS_TIME_SECONDS)
                == DEFAULT_MIN_PLAYERS_TIME_SECONDS
                && Math.max(sanitizeMinPlayers(snapshot.minPlayers), sanitizeMinPlayers(snapshot.optimalPlayers))
                == DEFAULT_OPTIMAL_PLAYERS
                && sanitizeCountdownTime(snapshot.optimalPlayersTimeSeconds, DEFAULT_OPTIMAL_PLAYERS_TIME_SECONDS)
                == DEFAULT_OPTIMAL_PLAYERS_TIME_SECONDS
                && sanitizeBlinkDistanceBlocks(snapshot.blinkDistanceBlocks) == DEFAULT_BLINK_DISTANCE_BLOCKS;
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
        loaded.minPlayers = sanitizeMinPlayers(loaded.minPlayers);
        loaded.minPlayersTimeSeconds = sanitizeCountdownTime(loaded.minPlayersTimeSeconds,
                DEFAULT_MIN_PLAYERS_TIME_SECONDS);
        loaded.optimalPlayers = Math.max(loaded.minPlayers, sanitizeMinPlayers(loaded.optimalPlayers));
        loaded.optimalPlayersTimeSeconds = sanitizeCountdownTime(loaded.optimalPlayersTimeSeconds,
                DEFAULT_OPTIMAL_PLAYERS_TIME_SECONDS);
        loaded.blinkDistanceBlocks = sanitizeBlinkDistanceBlocks(loaded.blinkDistanceBlocks);

        List<RunOrFallMapConfig> sanitizedMaps = new ArrayList<>();
        Map<String, RunOrFallMapConfig> uniqueMaps = new LinkedHashMap<>();
        if (loaded.maps != null) {
            for (RunOrFallMapConfig map : loaded.maps) {
                if (map == null) {
                    continue;
                }
                String mapId = normalizeMapId(map.id);
                if (mapId.isEmpty()) {
                    continue;
                }
                RunOrFallMapConfig sanitized = new RunOrFallMapConfig();
                sanitized.id = mapId;
                sanitized.lobby = isFiniteLocation(map.lobby) ? map.lobby.copy() : null;
                if (map.spawns != null) {
                    for (RunOrFallLocation spawn : map.spawns) {
                        if (spawn != null && isFiniteLocation(spawn)) {
                            sanitized.spawns.add(spawn.copy());
                        }
                    }
                }
                if (map.platforms != null) {
                    for (RunOrFallPlatform platform : map.platforms) {
                        if (platform != null) {
                            sanitized.platforms.add(platform.copy());
                        }
                    }
                }
                uniqueMaps.putIfAbsent(mapId, sanitized);
            }
        }

        if (uniqueMaps.isEmpty()) {
            RunOrFallMapConfig legacyMap = new RunOrFallMapConfig();
            legacyMap.id = DEFAULT_MAP_ID;
            legacyMap.lobby = isFiniteLocation(loaded.lobby) ? loaded.lobby.copy() : null;
            if (loaded.spawns != null) {
                for (RunOrFallLocation spawn : loaded.spawns) {
                    if (spawn != null && isFiniteLocation(spawn)) {
                        legacyMap.spawns.add(spawn.copy());
                    }
                }
            }
            if (loaded.platforms != null) {
                for (RunOrFallPlatform platform : loaded.platforms) {
                    if (platform != null) {
                        legacyMap.platforms.add(platform.copy());
                    }
                }
            }
            uniqueMaps.put(legacyMap.id, legacyMap);
        }

        sanitizedMaps.addAll(uniqueMaps.values());
        loaded.maps = sanitizedMaps;

        String selectedMapId = normalizeMapId(loaded.selectedMapId);
        if (selectedMapId.isEmpty() || findMapByIdInternal(loaded, selectedMapId) == null) {
            selectedMapId = loaded.maps.isEmpty() ? DEFAULT_MAP_ID : loaded.maps.get(0).id;
        }
        loaded.selectedMapId = selectedMapId;

        loaded.lobby = null;
        loaded.spawns = new ArrayList<>();
        loaded.platforms = new ArrayList<>();
    }

    private RunOrFallConfig createDefaultConfig() {
        RunOrFallConfig created = new RunOrFallConfig();
        created.voidY = DEFAULT_VOID_Y;
        created.blockBreakDelaySeconds = DEFAULT_BREAK_DELAY_SECONDS;
        created.minPlayers = DEFAULT_MIN_PLAYERS;
        created.minPlayersTimeSeconds = DEFAULT_MIN_PLAYERS_TIME_SECONDS;
        created.optimalPlayers = DEFAULT_OPTIMAL_PLAYERS;
        created.optimalPlayersTimeSeconds = DEFAULT_OPTIMAL_PLAYERS_TIME_SECONDS;
        created.blinkDistanceBlocks = DEFAULT_BLINK_DISTANCE_BLOCKS;
        RunOrFallMapConfig map = new RunOrFallMapConfig();
        map.id = DEFAULT_MAP_ID;
        created.maps.add(map);
        created.selectedMapId = DEFAULT_MAP_ID;
        return created;
    }

    private RunOrFallMapConfig getOrCreateSelectedMapInternal() {
        RunOrFallMapConfig selected = getSelectedMapInternal();
        if (selected != null) {
            return selected;
        }
        if (config.maps == null) {
            config.maps = new ArrayList<>();
        }
        RunOrFallMapConfig map = new RunOrFallMapConfig();
        map.id = DEFAULT_MAP_ID;
        config.maps.add(map);
        config.selectedMapId = map.id;
        return map;
    }

    private RunOrFallMapConfig getSelectedMapInternal() {
        if (config.maps == null || config.maps.isEmpty()) {
            return null;
        }
        String selectedMapId = normalizeMapId(config.selectedMapId);
        RunOrFallMapConfig selected = findMapByIdInternal(selectedMapId);
        if (selected != null) {
            return selected;
        }
        RunOrFallMapConfig fallback = config.maps.get(0);
        config.selectedMapId = fallback != null ? fallback.id : DEFAULT_MAP_ID;
        return fallback;
    }

    private RunOrFallMapConfig findMapByIdInternal(String mapId) {
        return findMapByIdInternal(config, mapId);
    }

    private RunOrFallMapConfig findMapByIdInternal(RunOrFallConfig source, String mapId) {
        if (source == null || source.maps == null || mapId == null || mapId.isBlank()) {
            return null;
        }
        for (RunOrFallMapConfig map : source.maps) {
            if (map != null && map.id != null && map.id.equals(mapId)) {
                return map;
            }
        }
        return null;
    }

    private static String normalizeMapId(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean lastDash = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean isAlphaNum = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (isAlphaNum || c == '_' || c == '-') {
                builder.append(c);
                lastDash = false;
                continue;
            }
            if (!lastDash) {
                builder.append('-');
                lastDash = true;
            }
        }
        String normalized = builder.toString();
        while (normalized.startsWith("-")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("-")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
        }
        return normalized;
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

    private static int sanitizeMinPlayers(int value) {
        return Math.max(1, value);
    }

    private static int sanitizeCountdownTime(int value, int fallback) {
        if (value <= 0) {
            return Math.max(1, fallback);
        }
        return value;
    }

    private static int sanitizeBlinkDistanceBlocks(int value) {
        return Math.max(1, value);
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getTables(conn.getCatalog(), null, tableName, null)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = metaData.getTables(conn.getCatalog(), null, tableName.toUpperCase(Locale.ROOT), null)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = metaData.getTables(conn.getCatalog(), null, tableName.toLowerCase(Locale.ROOT), null)) {
            return rs.next();
        }
    }

    private static boolean tableExistsSafe(Connection conn, String tableName) {
        try {
            return tableExists(conn, tableName);
        } catch (SQLException ignored) {
            return false;
        }
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null, tableName, columnName)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null,
                tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT))) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null,
                tableName.toLowerCase(Locale.ROOT), columnName.toLowerCase(Locale.ROOT))) {
            return rs.next();
        }
    }

    private static void ensureColumnExists(Connection conn, String tableName, String columnName, String alterSql) {
        try {
            if (columnExists(conn, tableName, columnName)) {
                return;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(alterSql);
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e)
                    .log("Failed to ensure column " + columnName + " on table " + tableName + ".");
        }
    }
}
