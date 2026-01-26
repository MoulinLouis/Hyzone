package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.hyvexa.parkour.ParkourConstants;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

/** MySQL-backed storage for parkour settings and spawn configuration. */
public class SettingsStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final Type CATEGORY_LIST_TYPE = new TypeToken<List<String>>() {}.getType();
    private final ReadWriteLock fileLock = new ReentrantReadWriteLock();

    private double fallRespawnSeconds = ParkourConstants.DEFAULT_FALL_RESPAWN_SECONDS;
    private double fallFailsafeVoidY = ParkourConstants.FALL_FAILSAFE_VOID_Y;
    private TransformData spawnPosition = defaultSpawn();
    private final List<String> categoryOrder = new ArrayList<>();
    private boolean idleFallRespawnForOp = false;
    private boolean disableWeaponDamage = false;
    private boolean teleportDebugEnabled = false;

    public SettingsStore() {
    }

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, using defaults for SettingsStore");
            return;
        }

        String sql = """
            SELECT fall_respawn_seconds, void_y_failsafe, weapon_damage_disabled, debug_mode,
                   spawn_x, spawn_y, spawn_z, spawn_rot_x, spawn_rot_y, spawn_rot_z,
                   idle_fall_respawn_for_op, category_order_json
            FROM settings WHERE id = 1
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    fileLock.writeLock().lock();
                    try {
                        fallRespawnSeconds = rs.getDouble("fall_respawn_seconds");
                        if (fallRespawnSeconds <= 0) {
                            fallRespawnSeconds = ParkourConstants.DEFAULT_FALL_RESPAWN_SECONDS;
                        }
                        fallFailsafeVoidY = rs.getDouble("void_y_failsafe");
                        disableWeaponDamage = rs.getBoolean("weapon_damage_disabled");
                        teleportDebugEnabled = rs.getBoolean("debug_mode");

                        Double spawnX = rs.getObject("spawn_x", Double.class);
                        if (spawnX != null) {
                            spawnPosition = new TransformData();
                            spawnPosition.setX(spawnX);
                            spawnPosition.setY(rs.getDouble("spawn_y"));
                            spawnPosition.setZ(rs.getDouble("spawn_z"));
                            spawnPosition.setRotX(rs.getFloat("spawn_rot_x"));
                            spawnPosition.setRotY(rs.getFloat("spawn_rot_y"));
                            spawnPosition.setRotZ(rs.getFloat("spawn_rot_z"));
                        } else {
                            spawnPosition = defaultSpawn();
                        }

                        Boolean idleFall = rs.getObject("idle_fall_respawn_for_op", Boolean.class);
                        idleFallRespawnForOp = idleFall != null && idleFall;

                        categoryOrder.clear();
                        String categoryJson = rs.getString("category_order_json");
                        if (categoryJson != null && !categoryJson.isBlank()) {
                            try {
                                List<String> loaded = GSON.fromJson(categoryJson, CATEGORY_LIST_TYPE);
                                if (loaded != null) {
                                    for (String entry : loaded) {
                                        if (entry == null) {
                                            continue;
                                        }
                                        String trimmed = entry.trim();
                                        if (!trimmed.isEmpty()) {
                                            categoryOrder.add(trimmed);
                                        }
                                    }
                                }
                            } catch (RuntimeException e) {
                                LOGGER.at(Level.WARNING).log("Failed to parse category order: " + e.getMessage());
                            }
                        }
                    } finally {
                        fileLock.writeLock().unlock();
                    }
                    LOGGER.atInfo().log("SettingsStore loaded from database");
                } else {
                    // No settings row exists, insert defaults
                    insertDefaults();
                    LOGGER.atInfo().log("SettingsStore created with defaults");
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load SettingsStore from database: " + e.getMessage());
        }
    }

    private void insertDefaults() {
        String sql = """
            INSERT INTO settings (id, fall_respawn_seconds, void_y_failsafe, weapon_damage_disabled, debug_mode,
                spawn_x, spawn_y, spawn_z, spawn_rot_x, spawn_rot_y, spawn_rot_z,
                idle_fall_respawn_for_op, category_order_json)
            VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setDouble(1, fallRespawnSeconds);
            stmt.setDouble(2, fallFailsafeVoidY);
            stmt.setBoolean(3, disableWeaponDamage);
            stmt.setBoolean(4, teleportDebugEnabled);
            stmt.setDouble(5, spawnPosition.getX());
            stmt.setDouble(6, spawnPosition.getY());
            stmt.setDouble(7, spawnPosition.getZ());
            stmt.setFloat(8, spawnPosition.getRotX());
            stmt.setFloat(9, spawnPosition.getRotY());
            stmt.setFloat(10, spawnPosition.getRotZ());
            stmt.setBoolean(11, idleFallRespawnForOp);
            stmt.setString(12, GSON.toJson(categoryOrder));
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to insert default settings: " + e.getMessage());
        }
    }

    private void syncSave() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        String sql = """
            UPDATE settings SET
                fall_respawn_seconds = ?,
                void_y_failsafe = ?,
                weapon_damage_disabled = ?,
                debug_mode = ?,
                spawn_x = ?,
                spawn_y = ?,
                spawn_z = ?,
                spawn_rot_x = ?,
                spawn_rot_y = ?,
                spawn_rot_z = ?,
                idle_fall_respawn_for_op = ?,
                category_order_json = ?
            WHERE id = 1
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setDouble(1, fallRespawnSeconds);
            stmt.setDouble(2, fallFailsafeVoidY);
            stmt.setBoolean(3, disableWeaponDamage);
            stmt.setBoolean(4, teleportDebugEnabled);

            if (spawnPosition != null) {
                stmt.setDouble(5, spawnPosition.getX());
                stmt.setDouble(6, spawnPosition.getY());
                stmt.setDouble(7, spawnPosition.getZ());
                stmt.setFloat(8, spawnPosition.getRotX());
                stmt.setFloat(9, spawnPosition.getRotY());
                stmt.setFloat(10, spawnPosition.getRotZ());
            } else {
                stmt.setNull(5, java.sql.Types.DOUBLE);
                stmt.setNull(6, java.sql.Types.DOUBLE);
                stmt.setNull(7, java.sql.Types.DOUBLE);
                stmt.setNull(8, java.sql.Types.FLOAT);
                stmt.setNull(9, java.sql.Types.FLOAT);
                stmt.setNull(10, java.sql.Types.FLOAT);
            }
            stmt.setBoolean(11, idleFallRespawnForOp);
            stmt.setString(12, GSON.toJson(categoryOrder));

            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save SettingsStore to database: " + e.getMessage());
        }
    }

    public double getFallRespawnSeconds() {
        return fallRespawnSeconds;
    }

    public void setFallRespawnSeconds(double fallRespawnSeconds) {
        this.fileLock.writeLock().lock();
        try {
            this.fallRespawnSeconds = Math.max(0.1, fallRespawnSeconds);
        } finally {
            this.fileLock.writeLock().unlock();
        }
        syncSave();
    }

    public TransformData getSpawnPosition() {
        return spawnPosition;
    }

    public double getFallFailsafeVoidY() {
        return fallFailsafeVoidY;
    }

    public void setFallFailsafeVoidY(double fallFailsafeVoidY) {
        this.fileLock.writeLock().lock();
        try {
            double sanitized = Double.isFinite(fallFailsafeVoidY)
                    ? fallFailsafeVoidY
                    : ParkourConstants.FALL_FAILSAFE_VOID_Y;
            this.fallFailsafeVoidY = sanitized;
        } finally {
            this.fileLock.writeLock().unlock();
        }
        syncSave();
    }

    public boolean isIdleFallRespawnForOp() {
        return idleFallRespawnForOp;
    }

    public boolean isWeaponDamageDisabled() {
        return disableWeaponDamage;
    }

    public boolean isTeleportDebugEnabled() {
        return teleportDebugEnabled;
    }

    public void setWeaponDamageDisabled(boolean disableWeaponDamage) {
        this.fileLock.writeLock().lock();
        try {
            this.disableWeaponDamage = disableWeaponDamage;
        } finally {
            this.fileLock.writeLock().unlock();
        }
        syncSave();
    }

    public void setTeleportDebugEnabled(boolean teleportDebugEnabled) {
        this.fileLock.writeLock().lock();
        try {
            this.teleportDebugEnabled = teleportDebugEnabled;
        } finally {
            this.fileLock.writeLock().unlock();
        }
        syncSave();
    }

    public void setIdleFallRespawnForOp(boolean idleFallRespawnForOp) {
        this.fileLock.writeLock().lock();
        try {
            this.idleFallRespawnForOp = idleFallRespawnForOp;
        } finally {
            this.fileLock.writeLock().unlock();
        }
        syncSave();
    }

    public List<String> getCategoryOrder() {
        return List.copyOf(categoryOrder);
    }

    public void setCategoryOrder(List<String> order) {
        this.fileLock.writeLock().lock();
        try {
            categoryOrder.clear();
            if (order != null) {
                for (String entry : order) {
                    if (entry == null) {
                        continue;
                    }
                    String trimmed = entry.trim();
                    if (!trimmed.isEmpty()) {
                        categoryOrder.add(trimmed);
                    }
                }
            }
        } finally {
            this.fileLock.writeLock().unlock();
        }
        syncSave();
    }

    public boolean isDebugEnabled() {
        return teleportDebugEnabled;
    }

    public boolean isFallRespawnEnabled() {
        return fallRespawnSeconds > 0;
    }

    public double getVoidYFailsafe() {
        return fallFailsafeVoidY;
    }

    private static TransformData defaultSpawn() {
        TransformData data = new TransformData();
        data.setX(ParkourConstants.DEFAULT_SPAWN_POSITION.getX());
        data.setY(ParkourConstants.DEFAULT_SPAWN_POSITION.getY());
        data.setZ(ParkourConstants.DEFAULT_SPAWN_POSITION.getZ());
        data.setRotX(0.0f);
        data.setRotY(0.0f);
        data.setRotZ(0.0f);
        return data;
    }
}
