# Ascend Mode Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement MVP of Ascend idle game mode with 3 maps, robots, and upgrade system.

**Architecture:** Separate plugin module (`hyvexa-parkour-ascend`) with own data stores, commands, and UI. Uses `hyvexa-core` for database and mode gating. Robots are `Kweebec_Sapling` entities with scripted velocity movement.

**Tech Stack:** Java 17, Hytale Server API, MySQL (via HikariCP from core), ECS component system.

---

## Phase 1: Data Layer

### Task 1.1: Create Ascend Constants

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java`

**Step 1: Create constants file**

```java
package io.hyvexa.ascend;

public final class AscendConstants {

    private AscendConstants() {}

    // Database
    public static final String TABLE_PREFIX = "ascend_";

    // Economy
    public static final long DEFAULT_ROBOT_STORAGE = 100L;
    public static final double SPEED_UPGRADE_MULTIPLIER = 0.10; // +10% per level
    public static final double GAINS_UPGRADE_MULTIPLIER = 0.15; // +15% per level
    public static final int MAX_UPGRADE_LEVEL = 5;

    // Robot
    public static final String ROBOT_ENTITY_TYPE = "Kweebec_Sapling";
    public static final long ROBOT_TICK_INTERVAL_MS = 200L; // 5 ticks/second
    public static final double ROBOT_BASE_SPEED = 5.0;
    public static final double ROBOT_JUMP_FORCE = 8.0;
    public static final double WAYPOINT_REACH_DISTANCE = 1.5;

    // Timing
    public static final long SAVE_DEBOUNCE_MS = 5000L;
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java
git commit -m "feat(ascend): add constants for economy, robot, and timing"
```

---

### Task 1.2: Create AscendMap Data Class

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendMap.java`

**Step 1: Create map data class**

```java
package io.hyvexa.ascend.data;

import java.util.ArrayList;
import java.util.List;

public class AscendMap {

    private String id;
    private String name;
    private long price;
    private long robotPrice;
    private long baseReward;
    private long baseRunTimeMs;
    private int storageCapacity;
    private String world;
    private double startX, startY, startZ;
    private float startRotX, startRotY, startRotZ;
    private double finishX, finishY, finishZ;
    private List<Waypoint> waypoints = new ArrayList<>();
    private int displayOrder;

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    public long getRobotPrice() { return robotPrice; }
    public void setRobotPrice(long robotPrice) { this.robotPrice = robotPrice; }

    public long getBaseReward() { return baseReward; }
    public void setBaseReward(long baseReward) { this.baseReward = baseReward; }

    public long getBaseRunTimeMs() { return baseRunTimeMs; }
    public void setBaseRunTimeMs(long baseRunTimeMs) { this.baseRunTimeMs = baseRunTimeMs; }

    public int getStorageCapacity() { return storageCapacity; }
    public void setStorageCapacity(int storageCapacity) { this.storageCapacity = storageCapacity; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public double getStartX() { return startX; }
    public void setStartX(double startX) { this.startX = startX; }
    public double getStartY() { return startY; }
    public void setStartY(double startY) { this.startY = startY; }
    public double getStartZ() { return startZ; }
    public void setStartZ(double startZ) { this.startZ = startZ; }

    public float getStartRotX() { return startRotX; }
    public void setStartRotX(float startRotX) { this.startRotX = startRotX; }
    public float getStartRotY() { return startRotY; }
    public void setStartRotY(float startRotY) { this.startRotY = startRotY; }
    public float getStartRotZ() { return startRotZ; }
    public void setStartRotZ(float startRotZ) { this.startRotZ = startRotZ; }

    public double getFinishX() { return finishX; }
    public void setFinishX(double finishX) { this.finishX = finishX; }
    public double getFinishY() { return finishY; }
    public void setFinishY(double finishY) { this.finishY = finishY; }
    public double getFinishZ() { return finishZ; }
    public void setFinishZ(double finishZ) { this.finishZ = finishZ; }

    public List<Waypoint> getWaypoints() { return waypoints; }
    public void setWaypoints(List<Waypoint> waypoints) { this.waypoints = waypoints; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public static class Waypoint {
        private double x, y, z;
        private boolean jump;
        private long delayMs;

        public Waypoint() {}

        public Waypoint(double x, double y, double z, boolean jump, long delayMs) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.jump = jump;
            this.delayMs = delayMs;
        }

        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        public double getZ() { return z; }
        public void setZ(double z) { this.z = z; }
        public boolean isJump() { return jump; }
        public void setJump(boolean jump) { this.jump = jump; }
        public long getDelayMs() { return delayMs; }
        public void setDelayMs(long delayMs) { this.delayMs = delayMs; }
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendMap.java
git commit -m "feat(ascend): add AscendMap data class with waypoints"
```

---

### Task 1.3: Create AscendPlayerProgress Data Class

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerProgress.java`

**Step 1: Create player progress class**

```java
package io.hyvexa.ascend.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AscendPlayerProgress {

    private long coins;
    private final Map<String, MapProgress> mapProgress = new ConcurrentHashMap<>();

    public long getCoins() { return coins; }
    public void setCoins(long coins) { this.coins = coins; }
    public void addCoins(long amount) { this.coins = Math.max(0, this.coins + amount); }

    public Map<String, MapProgress> getMapProgress() { return mapProgress; }

    public MapProgress getOrCreateMapProgress(String mapId) {
        return mapProgress.computeIfAbsent(mapId, k -> new MapProgress());
    }

    public static class MapProgress {
        private boolean unlocked;
        private boolean completedManually;
        private boolean hasRobot;
        private int robotSpeedLevel;
        private int robotGainsLevel;
        private long pendingCoins;

        public boolean isUnlocked() { return unlocked; }
        public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }

        public boolean isCompletedManually() { return completedManually; }
        public void setCompletedManually(boolean completedManually) { this.completedManually = completedManually; }

        public boolean hasRobot() { return hasRobot; }
        public void setHasRobot(boolean hasRobot) { this.hasRobot = hasRobot; }

        public int getRobotSpeedLevel() { return robotSpeedLevel; }
        public void setRobotSpeedLevel(int robotSpeedLevel) { this.robotSpeedLevel = robotSpeedLevel; }

        public int getRobotGainsLevel() { return robotGainsLevel; }
        public void setRobotGainsLevel(int robotGainsLevel) { this.robotGainsLevel = robotGainsLevel; }

        public long getPendingCoins() { return pendingCoins; }
        public void setPendingCoins(long pendingCoins) { this.pendingCoins = pendingCoins; }
        public void addPendingCoins(long amount) { this.pendingCoins = Math.max(0, this.pendingCoins + amount); }
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerProgress.java
git commit -m "feat(ascend): add AscendPlayerProgress data class"
```

---

### Task 1.4: Create AscendMapStore

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendMapStore.java`

**Step 1: Create map store with MySQL persistence**

```java
package io.hyvexa.ascend.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

public class AscendMapStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();

    private final Map<String, AscendMap> maps = new LinkedHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, AscendMapStore will be empty");
            return;
        }

        String sql = """
            SELECT id, name, price, robot_price, base_reward, base_run_time_ms, storage_capacity,
                   world, start_x, start_y, start_z, start_rot_x, start_rot_y, start_rot_z,
                   finish_x, finish_y, finish_z, waypoints_json, display_order
            FROM ascend_maps ORDER BY display_order, id
            """;

        lock.writeLock().lock();
        try {
            maps.clear();
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        AscendMap map = new AscendMap();
                        map.setId(rs.getString("id"));
                        map.setName(rs.getString("name"));
                        map.setPrice(rs.getLong("price"));
                        map.setRobotPrice(rs.getLong("robot_price"));
                        map.setBaseReward(rs.getLong("base_reward"));
                        map.setBaseRunTimeMs(rs.getLong("base_run_time_ms"));
                        map.setStorageCapacity(rs.getInt("storage_capacity"));
                        map.setWorld(rs.getString("world"));
                        map.setStartX(rs.getDouble("start_x"));
                        map.setStartY(rs.getDouble("start_y"));
                        map.setStartZ(rs.getDouble("start_z"));
                        map.setStartRotX(rs.getFloat("start_rot_x"));
                        map.setStartRotY(rs.getFloat("start_rot_y"));
                        map.setStartRotZ(rs.getFloat("start_rot_z"));
                        map.setFinishX(rs.getDouble("finish_x"));
                        map.setFinishY(rs.getDouble("finish_y"));
                        map.setFinishZ(rs.getDouble("finish_z"));
                        map.setDisplayOrder(rs.getInt("display_order"));

                        String waypointsJson = rs.getString("waypoints_json");
                        if (waypointsJson != null && !waypointsJson.isBlank()) {
                            List<AscendMap.Waypoint> waypoints = GSON.fromJson(waypointsJson,
                                new TypeToken<List<AscendMap.Waypoint>>(){}.getType());
                            map.setWaypoints(waypoints != null ? waypoints : new ArrayList<>());
                        }

                        maps.put(map.getId(), map);
                    }
                }
                LOGGER.atInfo().log("AscendMapStore loaded " + maps.size() + " maps");
            } catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to load AscendMapStore: " + e.getMessage());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public AscendMap getMap(String id) {
        lock.readLock().lock();
        try {
            return maps.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<AscendMap> listMaps() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(maps.values()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void saveMap(AscendMap map) {
        if (map == null || map.getId() == null) return;

        lock.writeLock().lock();
        try {
            maps.put(map.getId(), map);
        } finally {
            lock.writeLock().unlock();
        }

        saveMapToDatabase(map);
    }

    private void saveMapToDatabase(AscendMap map) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = """
            INSERT INTO ascend_maps (id, name, price, robot_price, base_reward, base_run_time_ms,
                storage_capacity, world, start_x, start_y, start_z, start_rot_x, start_rot_y, start_rot_z,
                finish_x, finish_y, finish_z, waypoints_json, display_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name), price = VALUES(price), robot_price = VALUES(robot_price),
                base_reward = VALUES(base_reward), base_run_time_ms = VALUES(base_run_time_ms),
                storage_capacity = VALUES(storage_capacity), world = VALUES(world),
                start_x = VALUES(start_x), start_y = VALUES(start_y), start_z = VALUES(start_z),
                start_rot_x = VALUES(start_rot_x), start_rot_y = VALUES(start_rot_y), start_rot_z = VALUES(start_rot_z),
                finish_x = VALUES(finish_x), finish_y = VALUES(finish_y), finish_z = VALUES(finish_z),
                waypoints_json = VALUES(waypoints_json), display_order = VALUES(display_order)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            int i = 1;
            stmt.setString(i++, map.getId());
            stmt.setString(i++, map.getName());
            stmt.setLong(i++, map.getPrice());
            stmt.setLong(i++, map.getRobotPrice());
            stmt.setLong(i++, map.getBaseReward());
            stmt.setLong(i++, map.getBaseRunTimeMs());
            stmt.setInt(i++, map.getStorageCapacity());
            stmt.setString(i++, map.getWorld());
            stmt.setDouble(i++, map.getStartX());
            stmt.setDouble(i++, map.getStartY());
            stmt.setDouble(i++, map.getStartZ());
            stmt.setFloat(i++, map.getStartRotX());
            stmt.setFloat(i++, map.getStartRotY());
            stmt.setFloat(i++, map.getStartRotZ());
            stmt.setDouble(i++, map.getFinishX());
            stmt.setDouble(i++, map.getFinishY());
            stmt.setDouble(i++, map.getFinishZ());
            stmt.setString(i++, GSON.toJson(map.getWaypoints()));
            stmt.setInt(i, map.getDisplayOrder());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save map: " + e.getMessage());
        }
    }

    public boolean deleteMap(String id) {
        lock.writeLock().lock();
        boolean removed;
        try {
            removed = maps.remove(id) != null;
        } finally {
            lock.writeLock().unlock();
        }

        if (removed) {
            deleteMapFromDatabase(id);
        }
        return removed;
    }

    private void deleteMapFromDatabase(String id) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM ascend_maps WHERE id = ?")) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to delete map: " + e.getMessage());
        }
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendMapStore.java
git commit -m "feat(ascend): add AscendMapStore with MySQL persistence"
```

---

### Task 1.5: Create AscendPlayerStore

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java`

**Step 1: Create player store with MySQL persistence**

```java
package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public class AscendPlayerStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<UUID, AscendPlayerProgress> players = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> saveFuture = new AtomicReference<>();

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, AscendPlayerStore will be empty");
            return;
        }

        players.clear();
        loadPlayers();
        loadMapProgress();
        LOGGER.atInfo().log("AscendPlayerStore loaded " + players.size() + " players");
    }

    private void loadPlayers() {
        String sql = "SELECT uuid, coins FROM ascend_players";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    AscendPlayerProgress progress = new AscendPlayerProgress();
                    progress.setCoins(rs.getLong("coins"));
                    players.put(uuid, progress);
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load ascend players: " + e.getMessage());
        }
    }

    private void loadMapProgress() {
        String sql = """
            SELECT player_uuid, map_id, unlocked, completed_manually, has_robot,
                   robot_speed_level, robot_gains_level, pending_coins
            FROM ascend_player_maps
            """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    AscendPlayerProgress player = players.get(uuid);
                    if (player == null) continue;

                    String mapId = rs.getString("map_id");
                    AscendPlayerProgress.MapProgress mp = player.getOrCreateMapProgress(mapId);
                    mp.setUnlocked(rs.getBoolean("unlocked"));
                    mp.setCompletedManually(rs.getBoolean("completed_manually"));
                    mp.setHasRobot(rs.getBoolean("has_robot"));
                    mp.setRobotSpeedLevel(rs.getInt("robot_speed_level"));
                    mp.setRobotGainsLevel(rs.getInt("robot_gains_level"));
                    mp.setPendingCoins(rs.getLong("pending_coins"));
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load ascend map progress: " + e.getMessage());
        }
    }

    public AscendPlayerProgress getPlayer(UUID playerId) {
        return players.get(playerId);
    }

    public AscendPlayerProgress getOrCreatePlayer(UUID playerId) {
        return players.computeIfAbsent(playerId, k -> {
            AscendPlayerProgress p = new AscendPlayerProgress();
            dirtyPlayers.add(playerId);
            queueSave();
            return p;
        });
    }

    public void markDirty(UUID playerId) {
        dirtyPlayers.add(playerId);
        queueSave();
    }

    public long getCoins(UUID playerId) {
        AscendPlayerProgress p = players.get(playerId);
        return p != null ? p.getCoins() : 0L;
    }

    public void addCoins(UUID playerId, long amount) {
        AscendPlayerProgress p = getOrCreatePlayer(playerId);
        p.addCoins(amount);
        markDirty(playerId);
    }

    public boolean spendCoins(UUID playerId, long amount) {
        AscendPlayerProgress p = getOrCreatePlayer(playerId);
        if (p.getCoins() < amount) return false;
        p.addCoins(-amount);
        markDirty(playerId);
        return true;
    }

    public void flushPendingSave() {
        ScheduledFuture<?> pending = saveFuture.getAndSet(null);
        if (pending != null) {
            pending.cancel(false);
        }
        saveQueued.set(false);
        syncSave();
    }

    private void queueSave() {
        if (!saveQueued.compareAndSet(false, true)) return;
        ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                syncSave();
            } finally {
                saveQueued.set(false);
                saveFuture.set(null);
            }
        }, AscendConstants.SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        saveFuture.set(future);
    }

    private void syncSave() {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        Set<UUID> toSave = Set.copyOf(dirtyPlayers);
        dirtyPlayers.clear();
        if (toSave.isEmpty()) return;

        String playerSql = """
            INSERT INTO ascend_players (uuid, coins) VALUES (?, ?)
            ON DUPLICATE KEY UPDATE coins = VALUES(coins)
            """;

        String mapSql = """
            INSERT INTO ascend_player_maps (player_uuid, map_id, unlocked, completed_manually,
                has_robot, robot_speed_level, robot_gains_level, pending_coins)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                unlocked = VALUES(unlocked), completed_manually = VALUES(completed_manually),
                has_robot = VALUES(has_robot), robot_speed_level = VALUES(robot_speed_level),
                robot_gains_level = VALUES(robot_gains_level), pending_coins = VALUES(pending_coins)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement playerStmt = conn.prepareStatement(playerSql);
             PreparedStatement mapStmt = conn.prepareStatement(mapSql)) {
            DatabaseManager.applyQueryTimeout(playerStmt);
            DatabaseManager.applyQueryTimeout(mapStmt);

            for (UUID playerId : toSave) {
                AscendPlayerProgress p = players.get(playerId);
                if (p == null) continue;

                playerStmt.setString(1, playerId.toString());
                playerStmt.setLong(2, p.getCoins());
                playerStmt.addBatch();

                for (Map.Entry<String, AscendPlayerProgress.MapProgress> entry : p.getMapProgress().entrySet()) {
                    AscendPlayerProgress.MapProgress mp = entry.getValue();
                    mapStmt.setString(1, playerId.toString());
                    mapStmt.setString(2, entry.getKey());
                    mapStmt.setBoolean(3, mp.isUnlocked());
                    mapStmt.setBoolean(4, mp.isCompletedManually());
                    mapStmt.setBoolean(5, mp.hasRobot());
                    mapStmt.setInt(6, mp.getRobotSpeedLevel());
                    mapStmt.setInt(7, mp.getRobotGainsLevel());
                    mapStmt.setLong(8, mp.getPendingCoins());
                    mapStmt.addBatch();
                }
            }
            playerStmt.executeBatch();
            mapStmt.executeBatch();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save ascend players: " + e.getMessage());
            dirtyPlayers.addAll(toSave);
        }
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java
git commit -m "feat(ascend): add AscendPlayerStore with MySQL persistence"
```

---

### Task 1.6: Create Database Schema Setup

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendDatabaseSetup.java`

**Step 1: Create schema setup**

```java
package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.logging.Level;

public final class AscendDatabaseSetup {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private AscendDatabaseSetup() {}

    public static void ensureTables() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, skipping Ascend table setup");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    coins BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_maps (
                    id VARCHAR(32) PRIMARY KEY,
                    name VARCHAR(64) NOT NULL,
                    price BIGINT NOT NULL DEFAULT 0,
                    robot_price BIGINT NOT NULL,
                    base_reward BIGINT NOT NULL,
                    base_run_time_ms BIGINT NOT NULL,
                    storage_capacity INT NOT NULL DEFAULT 100,
                    world VARCHAR(64) NOT NULL,
                    start_x DOUBLE NOT NULL,
                    start_y DOUBLE NOT NULL,
                    start_z DOUBLE NOT NULL,
                    start_rot_x FLOAT NOT NULL DEFAULT 0,
                    start_rot_y FLOAT NOT NULL DEFAULT 0,
                    start_rot_z FLOAT NOT NULL DEFAULT 0,
                    finish_x DOUBLE NOT NULL,
                    finish_y DOUBLE NOT NULL,
                    finish_z DOUBLE NOT NULL,
                    waypoints_json TEXT,
                    display_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_player_maps (
                    player_uuid VARCHAR(36) NOT NULL,
                    map_id VARCHAR(32) NOT NULL,
                    unlocked BOOLEAN NOT NULL DEFAULT FALSE,
                    completed_manually BOOLEAN NOT NULL DEFAULT FALSE,
                    has_robot BOOLEAN NOT NULL DEFAULT FALSE,
                    robot_speed_level INT NOT NULL DEFAULT 0,
                    robot_gains_level INT NOT NULL DEFAULT 0,
                    pending_coins BIGINT NOT NULL DEFAULT 0,
                    last_collection_at TIMESTAMP NULL,
                    PRIMARY KEY (player_uuid, map_id),
                    FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE,
                    FOREIGN KEY (map_id) REFERENCES ascend_maps(id) ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_upgrade_costs (
                    upgrade_type VARCHAR(32) NOT NULL,
                    level INT NOT NULL,
                    cost BIGINT NOT NULL,
                    PRIMARY KEY (upgrade_type, level)
                ) ENGINE=InnoDB
                """);

            LOGGER.atInfo().log("Ascend database tables ensured");

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to create Ascend tables: " + e.getMessage());
        }
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendDatabaseSetup.java
git commit -m "feat(ascend): add database schema setup"
```

---

## Phase 2: Plugin Bootstrap

### Task 2.1: Update ParkourAscendPlugin

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java`

**Step 1: Add stores initialization**

```java
package io.hyvexa.ascend;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import io.hyvexa.ascend.data.AscendDatabaseSetup;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;

import javax.annotation.Nonnull;

public class ParkourAscendPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static ParkourAscendPlugin INSTANCE;

    private AscendMapStore mapStore;
    private AscendPlayerStore playerStore;

    public ParkourAscendPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
        LOGGER.atInfo().log("Hello from " + this.getName() + " version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up Ascend plugin...");

        // Ensure database tables exist
        AscendDatabaseSetup.ensureTables();

        // Initialize stores
        mapStore = new AscendMapStore();
        mapStore.syncLoad();

        playerStore = new AscendPlayerStore();
        playerStore.syncLoad();

        // TODO: Register commands
        // TODO: Register event listeners
        // TODO: Start robot manager

        LOGGER.atInfo().log("Ascend plugin setup complete");
    }

    public static ParkourAscendPlugin getInstance() {
        return INSTANCE;
    }

    public AscendMapStore getMapStore() {
        return mapStore;
    }

    public AscendPlayerStore getPlayerStore() {
        return playerStore;
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java
git commit -m "feat(ascend): initialize stores in plugin setup"
```

---

## Phase 3: Commands (Basic)

### Task 3.1: Create AscendCommand

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/AscendCommand.java`

**Step 1: Create main command**

```java
package io.hyvexa.ascend.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class AscendCommand extends AbstractAsyncCommand {

    public AscendCommand() {
        super("ascend", "Open the Ascend menu");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            ctx.sendMessage(Message.raw("This command can only be used by players."));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Player not in world."));
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;

            String[] args = getArgs(ctx);

            if (args.length == 0) {
                showStatus(player, playerRef);
                return;
            }

            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "collect" -> handleCollect(player, playerRef);
                case "stats" -> showStatus(player, playerRef);
                default -> ctx.sendMessage(Message.raw("Unknown subcommand. Use: /ascend, /ascend collect, /ascend stats"));
            }
        }, world);
    }

    private String[] getArgs(CommandContext ctx) {
        String input = ctx.input();
        if (input == null || input.isBlank()) return new String[0];
        String[] parts = input.trim().split("\\s+");
        if (parts.length <= 1) return new String[0];
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return args;
    }

    private void showStatus(Player player, PlayerRef playerRef) {
        AscendPlayerStore playerStore = ParkourAscendPlugin.getInstance().getPlayerStore();
        long coins = playerStore.getCoins(playerRef.getUuid());
        player.sendMessage(Message.raw("[Ascend] Your coins: " + coins).color(SystemMessageUtils.PRIMARY_TEXT));
    }

    private void handleCollect(Player player, PlayerRef playerRef) {
        AscendPlayerStore playerStore = ParkourAscendPlugin.getInstance().getPlayerStore();
        var progress = playerStore.getPlayer(playerRef.getUuid());

        if (progress == null) {
            player.sendMessage(Message.raw("[Ascend] No earnings to collect.").color(SystemMessageUtils.SECONDARY));
            return;
        }

        long totalPending = 0;
        for (var mp : progress.getMapProgress().values()) {
            totalPending += mp.getPendingCoins();
            mp.setPendingCoins(0);
        }

        if (totalPending <= 0) {
            player.sendMessage(Message.raw("[Ascend] No earnings to collect.").color(SystemMessageUtils.SECONDARY));
            return;
        }

        progress.addCoins(totalPending);
        playerStore.markDirty(playerRef.getUuid());

        player.sendMessage(Message.raw("[Ascend] Collected " + totalPending + " coins!").color(SystemMessageUtils.SUCCESS));
    }
}
```

**Step 2: Register command in plugin**

Update `ParkourAscendPlugin.java` setup method to add:

```java
// In setup() method, after stores initialization:
getCommandRegistry().registerCommand(new AscendCommand());
```

**Step 3: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/AscendCommand.java
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java
git commit -m "feat(ascend): add /ascend command with collect and stats"
```

---

## Phase 4: Manual Run Tracking

### Task 4.1: Create AscendRunTracker

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/tracker/AscendRunTracker.java`

**Step 1: Create run tracker for manual completions**

```java
package io.hyvexa.ascend.tracker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.util.SystemMessageUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AscendRunTracker {

    private static final double FINISH_RADIUS_SQ = 2.25; // 1.5^2

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final Map<UUID, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    public AscendRunTracker(AscendMapStore mapStore, AscendPlayerStore playerStore) {
        this.mapStore = mapStore;
        this.playerStore = playerStore;
    }

    public void startRun(UUID playerId, String mapId) {
        AscendMap map = mapStore.getMap(mapId);
        if (map == null) return;

        activeRuns.put(playerId, new ActiveRun(mapId, System.currentTimeMillis()));
    }

    public void cancelRun(UUID playerId) {
        activeRuns.remove(playerId);
    }

    public String getActiveMapId(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        return run != null ? run.mapId : null;
    }

    public void checkPlayer(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());

        if (playerRef == null || player == null || transform == null) return;

        ActiveRun run = activeRuns.get(playerRef.getUuid());
        if (run == null) return;

        AscendMap map = mapStore.getMap(run.mapId);
        if (map == null) {
            activeRuns.remove(playerRef.getUuid());
            return;
        }

        Vector3d pos = transform.getPosition();
        double dx = pos.getX() - map.getFinishX();
        double dy = pos.getY() - map.getFinishY();
        double dz = pos.getZ() - map.getFinishZ();

        if (dx * dx + dy * dy + dz * dz <= FINISH_RADIUS_SQ) {
            completeRun(playerRef, player, run, map, ref, store);
        }
    }

    private void completeRun(PlayerRef playerRef, Player player, ActiveRun run,
                             AscendMap map, Ref<EntityStore> ref, Store<EntityStore> store) {
        UUID playerId = playerRef.getUuid();
        activeRuns.remove(playerId);

        AscendPlayerProgress progress = playerStore.getOrCreatePlayer(playerId);
        AscendPlayerProgress.MapProgress mapProgress = progress.getOrCreateMapProgress(run.mapId);

        long reward = map.getBaseReward();
        boolean firstCompletion = !mapProgress.isCompletedManually();

        mapProgress.setCompletedManually(true);
        progress.addCoins(reward);
        playerStore.markDirty(playerId);

        if (firstCompletion) {
            player.sendMessage(Message.raw("[Ascend] Map completed! You can now buy a robot for this map.")
                .color(SystemMessageUtils.SUCCESS));
        }
        player.sendMessage(Message.raw("[Ascend] +" + reward + " coins!")
            .color(SystemMessageUtils.PRIMARY_TEXT));

        // Teleport back to start
        Vector3d startPos = new Vector3d(map.getStartX(), map.getStartY(), map.getStartZ());
        Vector3f startRot = new Vector3f(map.getStartRotX(), map.getStartRotY(), map.getStartRotZ());
        store.addComponent(ref, Teleport.getComponentType(),
            new Teleport(store.getExternalData().getWorld(), startPos, startRot));
    }

    public void teleportToMapStart(Ref<EntityStore> ref, Store<EntityStore> store,
                                   PlayerRef playerRef, String mapId) {
        AscendMap map = mapStore.getMap(mapId);
        if (map == null) return;

        Vector3d pos = new Vector3d(map.getStartX(), map.getStartY(), map.getStartZ());
        Vector3f rot = new Vector3f(map.getStartRotX(), map.getStartRotY(), map.getStartRotZ());
        store.addComponent(ref, Teleport.getComponentType(),
            new Teleport(store.getExternalData().getWorld(), pos, rot));

        startRun(playerRef.getUuid(), mapId);
    }

    private static class ActiveRun {
        final String mapId;
        final long startTimeMs;

        ActiveRun(String mapId, long startTimeMs) {
            this.mapId = mapId;
            this.startTimeMs = startTimeMs;
        }
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/tracker/AscendRunTracker.java
git commit -m "feat(ascend): add AscendRunTracker for manual map completions"
```

---

## Phase 5: Robot System (Core)

### Task 5.1: Create RobotState

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotState.java`

**Step 1: Create robot state tracking**

```java
package io.hyvexa.ascend.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

public class RobotState {

    private final UUID ownerId;
    private final String mapId;
    private Ref<EntityStore> entityRef;
    private int currentWaypointIndex;
    private long lastTickMs;
    private long waypointReachedMs;
    private boolean waiting;
    private long runsCompleted;

    public RobotState(UUID ownerId, String mapId) {
        this.ownerId = ownerId;
        this.mapId = mapId;
        this.currentWaypointIndex = 0;
        this.lastTickMs = System.currentTimeMillis();
        this.waiting = false;
        this.runsCompleted = 0;
    }

    public UUID getOwnerId() { return ownerId; }
    public String getMapId() { return mapId; }

    public Ref<EntityStore> getEntityRef() { return entityRef; }
    public void setEntityRef(Ref<EntityStore> entityRef) { this.entityRef = entityRef; }

    public int getCurrentWaypointIndex() { return currentWaypointIndex; }
    public void setCurrentWaypointIndex(int index) { this.currentWaypointIndex = index; }
    public void incrementWaypoint() { this.currentWaypointIndex++; }

    public long getLastTickMs() { return lastTickMs; }
    public void setLastTickMs(long lastTickMs) { this.lastTickMs = lastTickMs; }

    public long getWaypointReachedMs() { return waypointReachedMs; }
    public void setWaypointReachedMs(long ms) { this.waypointReachedMs = ms; }

    public boolean isWaiting() { return waiting; }
    public void setWaiting(boolean waiting) { this.waiting = waiting; }

    public long getRunsCompleted() { return runsCompleted; }
    public void incrementRunsCompleted() { this.runsCompleted++; }

    public void resetForNewRun() {
        this.currentWaypointIndex = 0;
        this.waypointReachedMs = 0;
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotState.java
git commit -m "feat(ascend): add RobotState class"
```

---

### Task 5.2: Create RobotManager

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotManager.java`

**Step 1: Create robot manager skeleton**

```java
package io.hyvexa.ascend.robot;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class RobotManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final Map<String, RobotState> robots = new ConcurrentHashMap<>(); // key: ownerId:mapId
    private ScheduledFuture<?> tickTask;

    public RobotManager(AscendMapStore mapStore, AscendPlayerStore playerStore) {
        this.mapStore = mapStore;
        this.playerStore = playerStore;
    }

    public void start() {
        tickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
            this::tick,
            AscendConstants.ROBOT_TICK_INTERVAL_MS,
            AscendConstants.ROBOT_TICK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        LOGGER.atInfo().log("RobotManager started");
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
        despawnAllRobots();
        LOGGER.atInfo().log("RobotManager stopped");
    }

    public void spawnRobot(UUID ownerId, String mapId) {
        String key = robotKey(ownerId, mapId);
        if (robots.containsKey(key)) {
            LOGGER.atWarning().log("Robot already exists for " + key);
            return;
        }

        RobotState state = new RobotState(ownerId, mapId);
        robots.put(key, state);

        // TODO: Actually spawn the entity in the world
        LOGGER.atInfo().log("Robot spawned for " + ownerId + " on map " + mapId);
    }

    public void despawnRobot(UUID ownerId, String mapId) {
        String key = robotKey(ownerId, mapId);
        RobotState state = robots.remove(key);
        if (state != null && state.getEntityRef() != null && state.getEntityRef().isValid()) {
            // TODO: Remove entity from world
        }
    }

    public void despawnAllRobots() {
        for (RobotState state : robots.values()) {
            if (state.getEntityRef() != null && state.getEntityRef().isValid()) {
                // TODO: Remove entity from world
            }
        }
        robots.clear();
    }

    public RobotState getRobot(UUID ownerId, String mapId) {
        return robots.get(robotKey(ownerId, mapId));
    }

    private void tick() {
        try {
            for (RobotState robot : robots.values()) {
                tickRobot(robot);
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Error in robot tick: " + e.getMessage());
        }
    }

    private void tickRobot(RobotState robot) {
        if (robot.isWaiting()) {
            return; // Storage full, waiting for collection
        }

        // TODO: Implement waypoint movement logic
        // 1. Get current position
        // 2. Calculate direction to next waypoint
        // 3. Set velocity
        // 4. Check if waypoint reached
        // 5. If at finish, add coins to pending, reset to start
    }

    private String robotKey(UUID ownerId, String mapId) {
        return ownerId.toString() + ":" + mapId;
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotManager.java
git commit -m "feat(ascend): add RobotManager skeleton"
```

---

## Phase 6: Integration

### Task 6.1: Wire Everything in Plugin

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java`

**Step 1: Complete plugin setup**

```java
package io.hyvexa.ascend;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import io.hyvexa.ascend.command.AscendCommand;
import io.hyvexa.ascend.data.AscendDatabaseSetup;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;

import javax.annotation.Nonnull;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ParkourAscendPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static ParkourAscendPlugin INSTANCE;

    private AscendMapStore mapStore;
    private AscendPlayerStore playerStore;
    private AscendRunTracker runTracker;
    private RobotManager robotManager;
    private ScheduledFuture<?> runTrackerTask;

    public ParkourAscendPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
        LOGGER.atInfo().log("Hello from " + this.getName() + " version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up Ascend plugin...");

        // Ensure database tables exist
        AscendDatabaseSetup.ensureTables();

        // Initialize stores
        mapStore = new AscendMapStore();
        mapStore.syncLoad();

        playerStore = new AscendPlayerStore();
        playerStore.syncLoad();

        // Initialize trackers
        runTracker = new AscendRunTracker(mapStore, playerStore);

        // Initialize robot manager
        robotManager = new RobotManager(mapStore, playerStore);
        robotManager.start();

        // Register commands
        getCommandRegistry().registerCommand(new AscendCommand());

        // Start run tracking tick (for manual runs)
        runTrackerTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
            this::tickRunTracker,
            200, 200, TimeUnit.MILLISECONDS
        );

        LOGGER.atInfo().log("Ascend plugin setup complete");
    }

    private void tickRunTracker() {
        // TODO: Iterate online players in Ascend world and check for completions
    }

    @Override
    protected void shutdown() {
        LOGGER.atInfo().log("Shutting down Ascend plugin...");

        if (runTrackerTask != null) {
            runTrackerTask.cancel(false);
        }

        if (robotManager != null) {
            robotManager.stop();
        }

        if (playerStore != null) {
            playerStore.flushPendingSave();
        }

        LOGGER.atInfo().log("Ascend plugin shutdown complete");
    }

    public static ParkourAscendPlugin getInstance() {
        return INSTANCE;
    }

    public AscendMapStore getMapStore() { return mapStore; }
    public AscendPlayerStore getPlayerStore() { return playerStore; }
    public AscendRunTracker getRunTracker() { return runTracker; }
    public RobotManager getRobotManager() { return robotManager; }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java
git commit -m "feat(ascend): complete plugin wiring with all components"
```

---

## Summary

### Implementation Order

| Phase | Tasks | Description |
|-------|-------|-------------|
| 1 | 1.1-1.6 | Data layer (constants, models, stores, schema) |
| 2 | 2.1 | Plugin bootstrap |
| 3 | 3.1 | Basic commands (/ascend) |
| 4 | 4.1 | Manual run tracking |
| 5 | 5.1-5.2 | Robot system core |
| 6 | 6.1 | Integration |

### Files Created

```
hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/
├── AscendConstants.java
├── ParkourAscendPlugin.java (modified)
├── command/
│   └── AscendCommand.java
├── data/
│   ├── AscendDatabaseSetup.java
│   ├── AscendMap.java
│   ├── AscendMapStore.java
│   ├── AscendPlayerProgress.java
│   └── AscendPlayerStore.java
├── robot/
│   ├── RobotManager.java
│   └── RobotState.java
└── tracker/
    └── AscendRunTracker.java
```

### Post-MVP Tasks (Not in This Plan)

- Admin commands for map creation/waypoint recording
- UI pages (menu, collection, upgrades)
- Entity spawning implementation
- Robot velocity movement
- Upgrade cost system
- Event listeners (join/leave)
