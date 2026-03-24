package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.hyvexa.parkour.ParkourConstants;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** MySQL-backed storage for parkour settings and spawn configuration. */
public class SettingsStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final Type CATEGORY_LIST_TYPE = new TypeToken<List<String>>() {}.getType();
    private final ConnectionProvider db;
    private final ReadWriteLock fileLock = new ReentrantReadWriteLock();

    private double fallRespawnSeconds = ParkourConstants.DEFAULT_FALL_RESPAWN_SECONDS;
    private double fallFailsafeVoidY = ParkourConstants.FALL_FAILSAFE_VOID_Y;
    private TransformData spawnPosition = defaultSpawn();
    private final List<String> categoryOrder = new ArrayList<>();
    private boolean idleFallRespawnForOp = false;
    private boolean disableWeaponDamage = false;
    private boolean teleportDebugEnabled = false;

    public SettingsStore(ConnectionProvider db) {
        this.db = db;
    }

    public void syncLoad() {
        if (!this.db.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, using defaults for SettingsStore");
            return;
        }

        String sql = """
            SELECT fall_respawn_seconds, void_y_failsafe, weapon_damage_disabled, debug_mode,
                   spawn_x, spawn_y, spawn_z, spawn_rot_x, spawn_rot_y, spawn_rot_z,
                   idle_fall_respawn_for_op, category_order_json
            FROM settings WHERE id = 1
            """;

        // Use a record-like holder to carry all fields out of the mapper
        record SettingsRow(double fallRespawn, double voidY, boolean weaponDisabled, boolean debug,
                           Double spawnX, double spawnY, double spawnZ, float spawnRotX, float spawnRotY, float spawnRotZ,
                           Boolean idleFall, String categoryJson) {}

        SettingsRow row = DatabaseManager.queryOne(this.db, sql, rs -> new SettingsRow(
                rs.getDouble("fall_respawn_seconds"),
                rs.getDouble("void_y_failsafe"),
                rs.getBoolean("weapon_damage_disabled"),
                rs.getBoolean("debug_mode"),
                rs.getObject("spawn_x", Double.class),
                rs.getDouble("spawn_y"),
                rs.getDouble("spawn_z"),
                rs.getFloat("spawn_rot_x"),
                rs.getFloat("spawn_rot_y"),
                rs.getFloat("spawn_rot_z"),
                rs.getObject("idle_fall_respawn_for_op", Boolean.class),
                rs.getString("category_order_json")
        ), null);

        if (row == null) {
            insertDefaults();
            LOGGER.atInfo().log("SettingsStore created with defaults");
            return;
        }

        fileLock.writeLock().lock();
        try {
            fallRespawnSeconds = row.fallRespawn() <= 0
                    ? ParkourConstants.DEFAULT_FALL_RESPAWN_SECONDS : row.fallRespawn();
            fallFailsafeVoidY = row.voidY();
            disableWeaponDamage = row.weaponDisabled();
            teleportDebugEnabled = row.debug();

            if (row.spawnX() != null) {
                spawnPosition = new TransformData();
                spawnPosition.setX(row.spawnX());
                spawnPosition.setY(row.spawnY());
                spawnPosition.setZ(row.spawnZ());
                spawnPosition.setRotX(row.spawnRotX());
                spawnPosition.setRotY(row.spawnRotY());
                spawnPosition.setRotZ(row.spawnRotZ());
            } else {
                spawnPosition = defaultSpawn();
            }

            idleFallRespawnForOp = row.idleFall() != null && row.idleFall();

            categoryOrder.clear();
            String categoryJson = row.categoryJson();
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
                    LOGGER.atWarning().log("Failed to parse category order: " + e.getMessage());
                }
            }
        } finally {
            fileLock.writeLock().unlock();
        }
        LOGGER.atInfo().log("SettingsStore loaded from database");
    }

    private void insertDefaults() {
        String sql = """
            INSERT INTO settings (id, fall_respawn_seconds, void_y_failsafe, weapon_damage_disabled, debug_mode,
                spawn_x, spawn_y, spawn_z, spawn_rot_x, spawn_rot_y, spawn_rot_z,
                idle_fall_respawn_for_op, category_order_json)
            VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        DatabaseManager.execute(this.db, sql, stmt -> {
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
        });
    }

    private void syncSave() {
        if (!this.db.isInitialized()) {
            return;
        }

        double localFallRespawn;
        double localVoidY;
        boolean localDisableWeapon;
        boolean localDebug;
        TransformData localSpawn;
        boolean localIdleFall;
        String localCategoryJson;

        fileLock.readLock().lock();
        try {
            localFallRespawn = fallRespawnSeconds;
            localVoidY = fallFailsafeVoidY;
            localDisableWeapon = disableWeaponDamage;
            localDebug = teleportDebugEnabled;
            localSpawn = spawnPosition;
            localIdleFall = idleFallRespawnForOp;
            localCategoryJson = GSON.toJson(categoryOrder);
        } finally {
            fileLock.readLock().unlock();
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
        DatabaseManager.execute(this.db, sql, stmt -> {
            stmt.setDouble(1, localFallRespawn);
            stmt.setDouble(2, localVoidY);
            stmt.setBoolean(3, localDisableWeapon);
            stmt.setBoolean(4, localDebug);

            if (localSpawn != null) {
                stmt.setDouble(5, localSpawn.getX());
                stmt.setDouble(6, localSpawn.getY());
                stmt.setDouble(7, localSpawn.getZ());
                stmt.setFloat(8, localSpawn.getRotX());
                stmt.setFloat(9, localSpawn.getRotY());
                stmt.setFloat(10, localSpawn.getRotZ());
            } else {
                stmt.setNull(5, java.sql.Types.DOUBLE);
                stmt.setNull(6, java.sql.Types.DOUBLE);
                stmt.setNull(7, java.sql.Types.DOUBLE);
                stmt.setNull(8, java.sql.Types.FLOAT);
                stmt.setNull(9, java.sql.Types.FLOAT);
                stmt.setNull(10, java.sql.Types.FLOAT);
            }
            stmt.setBoolean(11, localIdleFall);
            stmt.setString(12, localCategoryJson);
        });
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

    public boolean isFallRespawnEnabled() {
        return fallRespawnSeconds > 0;
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
