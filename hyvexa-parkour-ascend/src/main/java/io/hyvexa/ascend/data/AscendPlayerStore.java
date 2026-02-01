package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
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
        String sql = "SELECT uuid, coins, elevation_multiplier FROM ascend_players";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    AscendPlayerProgress progress = new AscendPlayerProgress();
                    progress.setCoins(rs.getLong("coins"));
                    progress.setElevationMultiplier(rs.getInt("elevation_multiplier"));
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
                   robot_speed_level, multiplier
            FROM ascend_player_maps
            """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    AscendPlayerProgress player = players.get(uuid);
                    if (player == null) {
                        continue;
                    }

                    String mapId = rs.getString("map_id");
                    AscendPlayerProgress.MapProgress mapProgress = player.getOrCreateMapProgress(mapId);
                    mapProgress.setUnlocked(rs.getBoolean("unlocked"));
                    mapProgress.setCompletedManually(rs.getBoolean("completed_manually"));
                    mapProgress.setHasRobot(rs.getBoolean("has_robot"));
                    mapProgress.setRobotSpeedLevel(rs.getInt("robot_speed_level"));
                    mapProgress.setMultiplier(rs.getDouble("multiplier"));
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load ascend map progress: " + e.getMessage());
        }
    }

    public AscendPlayerProgress getPlayer(UUID playerId) {
        return players.get(playerId);
    }

    public Map<UUID, AscendPlayerProgress> getPlayersSnapshot() {
        return new HashMap<>(players);
    }

    public AscendPlayerProgress getOrCreatePlayer(UUID playerId) {
        return players.computeIfAbsent(playerId, k -> {
            AscendPlayerProgress progress = new AscendPlayerProgress();
            dirtyPlayers.add(playerId);
            queueSave();
            return progress;
        });
    }

    public void resetPlayerProgress(UUID playerId) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setCoins(0L);
        progress.setElevationMultiplier(1);
        progress.getMapProgress().clear();
        markDirty(playerId);
    }

    public void markDirty(UUID playerId) {
        dirtyPlayers.add(playerId);
        queueSave();
    }

    public long getCoins(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getCoins() : 0L;
    }

    public void setCoins(UUID playerId, long coins) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setCoins(Math.max(0L, coins));
        markDirty(playerId);
    }

    public int getElevationMultiplier(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getElevationMultiplier() : 1;
    }

    public int addElevationMultiplier(UUID playerId, int amount) {
        if (amount <= 0) {
            return getElevationMultiplier(playerId);
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        int value = progress.addElevationMultiplier(amount);
        markDirty(playerId);
        return value;
    }

    public AscendPlayerProgress.MapProgress getMapProgress(UUID playerId, String mapId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return null;
        }
        return progress.getMapProgress().get(mapId);
    }

    public AscendPlayerProgress.MapProgress getOrCreateMapProgress(UUID playerId, String mapId) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        return progress.getOrCreateMapProgress(mapId);
    }

    public boolean setMapUnlocked(UUID playerId, String mapId, boolean unlocked) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        if (mapProgress.isUnlocked() == unlocked) {
            return false;
        }
        mapProgress.setUnlocked(unlocked);
        markDirty(playerId);
        return true;
    }

    public void addCoins(UUID playerId, long amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addCoins(amount);
        markDirty(playerId);
    }

    public boolean spendCoins(UUID playerId, long amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        if (progress.getCoins() < amount) {
            return false;
        }
        progress.addCoins(-amount);
        markDirty(playerId);
        return true;
    }

    public double getMapMultiplier(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return 1.0;
        }
        return Math.max(1.0, mapProgress.getMultiplier());
    }

    public double addMapMultiplier(UUID playerId, String mapId, double amount) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        double value = mapProgress.addMultiplier(amount);
        markDirty(playerId);
        return value;
    }

    public boolean hasRobot(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        return mapProgress != null && mapProgress.hasRobot();
    }

    public void setHasRobot(UUID playerId, String mapId, boolean hasRobot) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        mapProgress.setHasRobot(hasRobot);
        markDirty(playerId);
    }

    public int getRobotSpeedLevel(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return 0;
        }
        return Math.max(0, mapProgress.getRobotSpeedLevel());
    }

    public int incrementRobotSpeedLevel(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        int value = mapProgress.incrementRobotSpeedLevel();
        markDirty(playerId);
        return value;
    }

    public double[] getMultiplierDisplayValues(UUID playerId, List<AscendMap> maps, int slotCount) {
        int slots = Math.max(0, slotCount);
        double[] digits = new double[slots];
        for (int i = 0; i < slots; i++) {
            digits[i] = 1.0;
        }
        if (maps == null || maps.isEmpty() || slots == 0) {
            return digits;
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            digits[index] = getMapMultiplier(playerId, map.getId());
            index++;
        }
        return digits;
    }

    public long getMultiplierProduct(UUID playerId, List<AscendMap> maps, int slotCount) {
        double product = 1.0;
        int slots = Math.max(0, slotCount);
        if (maps == null || maps.isEmpty() || slots == 0) {
            int elevation = Math.max(1, getElevationMultiplier(playerId));
            return (long) Math.floor(product * elevation);
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            double value = getMapMultiplier(playerId, map.getId());
            product *= Math.max(1.0, value);
            index++;
        }
        int elevation = Math.max(1, getElevationMultiplier(playerId));
        return (long) Math.floor(product * elevation);
    }

    public long getCompletionPayout(UUID playerId, List<AscendMap> maps, int slotCount, String mapId, double bonusAmount) {
        double product = 1.0;
        int slots = Math.max(0, slotCount);
        if (maps == null || maps.isEmpty() || slots == 0) {
            int elevation = Math.max(1, getElevationMultiplier(playerId));
            return (long) Math.floor(product * elevation);
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            double value = getMapMultiplier(playerId, map.getId());
            if (map.getId().equals(mapId)) {
                value += bonusAmount;
            }
            product *= Math.max(1.0, value);
            index++;
        }
        int elevation = Math.max(1, getElevationMultiplier(playerId));
        return (long) Math.floor(product * elevation);
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
        if (!saveQueued.compareAndSet(false, true)) {
            return;
        }
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
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        Set<UUID> toSave = Set.copyOf(dirtyPlayers);
        dirtyPlayers.clear();
        if (toSave.isEmpty()) {
            return;
        }

        String playerSql = """
            INSERT INTO ascend_players (uuid, coins, elevation_multiplier) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE coins = VALUES(coins), elevation_multiplier = VALUES(elevation_multiplier)
            """;

        String mapSql = """
            INSERT INTO ascend_player_maps (player_uuid, map_id, unlocked, completed_manually,
                has_robot, robot_speed_level, multiplier)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                unlocked = VALUES(unlocked), completed_manually = VALUES(completed_manually),
                has_robot = VALUES(has_robot), robot_speed_level = VALUES(robot_speed_level),
                multiplier = VALUES(multiplier)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement playerStmt = conn.prepareStatement(playerSql);
             PreparedStatement mapStmt = conn.prepareStatement(mapSql)) {
            DatabaseManager.applyQueryTimeout(playerStmt);
            DatabaseManager.applyQueryTimeout(mapStmt);

            for (UUID playerId : toSave) {
                AscendPlayerProgress progress = players.get(playerId);
                if (progress == null) {
                    continue;
                }

                playerStmt.setString(1, playerId.toString());
                playerStmt.setLong(2, progress.getCoins());
                playerStmt.setInt(3, progress.getElevationMultiplier());
                playerStmt.addBatch();

                for (Map.Entry<String, AscendPlayerProgress.MapProgress> entry : progress.getMapProgress().entrySet()) {
                    AscendPlayerProgress.MapProgress mapProgress = entry.getValue();
                    mapStmt.setString(1, playerId.toString());
                    mapStmt.setString(2, entry.getKey());
                    mapStmt.setBoolean(3, mapProgress.isUnlocked());
                    mapStmt.setBoolean(4, mapProgress.isCompletedManually());
                    mapStmt.setBoolean(5, mapProgress.hasRobot());
                    mapStmt.setInt(6, mapProgress.getRobotSpeedLevel());
                    mapStmt.setDouble(7, mapProgress.getMultiplier());
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
