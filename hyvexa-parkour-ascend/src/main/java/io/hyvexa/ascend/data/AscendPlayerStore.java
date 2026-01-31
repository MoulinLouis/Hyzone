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
        String sql = "SELECT uuid, coins, rebirth_multiplier FROM ascend_players";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    AscendPlayerProgress progress = new AscendPlayerProgress();
                    progress.setCoins(rs.getLong("coins"));
                    progress.setRebirthMultiplier(rs.getInt("rebirth_multiplier"));
                    players.put(uuid, progress);
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load ascend players: " + e.getMessage());
        }
    }

    private void loadMapProgress() {
        String sql = """
            SELECT player_uuid, map_id, unlocked, completed_manually, has_robot, robot_count,
                   robot_speed_level, robot_gains_level, multiplier_value, robot_multiplier_bonus
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
                    mapProgress.setRobotCount(rs.getInt("robot_count"));
                    mapProgress.setRobotSpeedLevel(rs.getInt("robot_speed_level"));
                    mapProgress.setRobotGainsLevel(rs.getInt("robot_gains_level"));
                    mapProgress.setMultiplierValue(rs.getInt("multiplier_value"));
                    mapProgress.setRobotMultiplierBonus(rs.getDouble("robot_multiplier_bonus"));
                    if (mapProgress.hasRobot() && mapProgress.getRobotCount() == 0) {
                        mapProgress.setRobotCount(1);
                        dirtyPlayers.add(uuid);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load ascend map progress: " + e.getMessage());
        }
        if (!dirtyPlayers.isEmpty()) {
            queueSave();
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
        progress.setRebirthMultiplier(1);
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

    public int getRebirthMultiplier(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getRebirthMultiplier() : 1;
    }

    public int addRebirthMultiplier(UUID playerId, int amount) {
        if (amount <= 0) {
            return getRebirthMultiplier(playerId);
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        int value = progress.addRebirthMultiplier(amount);
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

    public int getMapMultiplierValue(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return 1;
        }
        return Math.max(1, mapProgress.getMultiplierValue());
    }

    public double getMapMultiplierDisplayValue(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return 1.0;
        }
        return Math.max(1.0, mapProgress.getMultiplierValue() + mapProgress.getRobotMultiplierBonus());
    }

    public int incrementMapMultiplier(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        int value = mapProgress.incrementMultiplier();
        markDirty(playerId);
        return value;
    }

    public double addRobotMultiplierBonus(UUID playerId, String mapId, double amount) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        double value = mapProgress.addRobotMultiplierBonus(amount);
        markDirty(playerId);
        return value;
    }

    public int getRobotCount(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return 0;
        }
        return Math.max(0, mapProgress.getRobotCount());
    }

    public int addRobotCount(UUID playerId, String mapId, int amount) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        int value = mapProgress.addRobotCount(amount);
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
            digits[index] = getMapMultiplierDisplayValue(playerId, map.getId());
            index++;
        }
        return digits;
    }

    public long getMultiplierProduct(UUID playerId, List<AscendMap> maps, int slotCount) {
        long product = 1L;
        int slots = Math.max(0, slotCount);
        if (maps == null || maps.isEmpty() || slots == 0) {
            int rebirth = Math.max(1, getRebirthMultiplier(playerId));
            return safeMultiply(product, rebirth);
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            int value = getMapMultiplierValue(playerId, map.getId());
            product *= Math.max(1, value);
            index++;
        }
        int rebirth = Math.max(1, getRebirthMultiplier(playerId));
        return safeMultiply(product, rebirth);
    }

    public long getCompletionPayout(UUID playerId, List<AscendMap> maps, int slotCount, String mapId) {
        long product = 1L;
        int slots = Math.max(0, slotCount);
        if (maps == null || maps.isEmpty() || slots == 0) {
            int rebirth = Math.max(1, getRebirthMultiplier(playerId));
            return safeMultiply(product, rebirth);
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            int value = getMapMultiplierValue(playerId, map.getId());
            if (map.getId().equals(mapId)) {
                value += 1;
            }
            product *= Math.max(1, value);
            index++;
        }
        int rebirth = Math.max(1, getRebirthMultiplier(playerId));
        return safeMultiply(product, rebirth);
    }

    private long safeMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
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
            INSERT INTO ascend_players (uuid, coins, rebirth_multiplier) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE coins = VALUES(coins), rebirth_multiplier = VALUES(rebirth_multiplier)
            """;

        String mapSql = """
            INSERT INTO ascend_player_maps (player_uuid, map_id, unlocked, completed_manually,
                has_robot, robot_count, robot_speed_level, robot_gains_level, multiplier_value,
                robot_multiplier_bonus)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                unlocked = VALUES(unlocked), completed_manually = VALUES(completed_manually),
                has_robot = VALUES(has_robot), robot_count = VALUES(robot_count),
                robot_speed_level = VALUES(robot_speed_level), robot_gains_level = VALUES(robot_gains_level),
                multiplier_value = VALUES(multiplier_value), robot_multiplier_bonus = VALUES(robot_multiplier_bonus)
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
                playerStmt.setInt(3, progress.getRebirthMultiplier());
                playerStmt.addBatch();

                for (Map.Entry<String, AscendPlayerProgress.MapProgress> entry : progress.getMapProgress().entrySet()) {
                    AscendPlayerProgress.MapProgress mapProgress = entry.getValue();
                    mapStmt.setString(1, playerId.toString());
                    mapStmt.setString(2, entry.getKey());
                    mapStmt.setBoolean(3, mapProgress.isUnlocked());
                    mapStmt.setBoolean(4, mapProgress.isCompletedManually());
                    mapStmt.setBoolean(5, mapProgress.hasRobot());
                    mapStmt.setInt(6, mapProgress.getRobotCount());
                    mapStmt.setInt(7, mapProgress.getRobotSpeedLevel());
                    mapStmt.setInt(8, mapProgress.getRobotGainsLevel());
                    mapStmt.setInt(9, mapProgress.getMultiplierValue());
                    mapStmt.setDouble(10, mapProgress.getRobotMultiplierBonus());
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
