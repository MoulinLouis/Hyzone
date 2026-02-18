package io.hyvexa.purge.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.purge.data.PurgeSpawnPoint;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PurgeSpawnPointManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double MIN_SPAWN_DISTANCE = 15.0;

    private final ConcurrentHashMap<Integer, PurgeSpawnPoint> spawnPoints = new ConcurrentHashMap<>();

    public PurgeSpawnPointManager() {
        createTable();
        loadAll();
    }

    public boolean hasSpawnPoints() {
        return !spawnPoints.isEmpty();
    }

    public Collection<PurgeSpawnPoint> getAll() {
        return List.copyOf(spawnPoints.values());
    }

    public int addSpawnPoint(double x, double y, double z, float yaw) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return -1;
        }
        String sql = "INSERT INTO purge_spawn_points (x, y, z, yaw) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setDouble(1, x);
            stmt.setDouble(2, y);
            stmt.setDouble(3, z);
            stmt.setFloat(4, yaw);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    spawnPoints.put(id, new PurgeSpawnPoint(id, x, y, z, yaw));
                    return id;
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to add spawn point");
        }
        return -1;
    }

    public boolean removeSpawnPoint(int id) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return false;
        }
        String sql = "DELETE FROM purge_spawn_points WHERE id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setInt(1, id);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                spawnPoints.remove(id);
                return true;
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove spawn point " + id);
        }
        return false;
    }

    public void clearAll() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            spawnPoints.clear();
            return;
        }
        String sql = "DELETE FROM purge_spawn_points";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            spawnPoints.clear();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to clear spawn points");
        }
    }

    /**
     * Select a spawn point using weighted random selection.
     * Farther points from the player are preferred. Points closer than 15 blocks are filtered out.
     * If all points are too close, the farthest one is returned.
     */
    public PurgeSpawnPoint selectSpawnPoint(double playerX, double playerZ) {
        Collection<PurgeSpawnPoint> all = spawnPoints.values();
        if (all.isEmpty()) {
            return null;
        }

        // Filter points >= MIN_SPAWN_DISTANCE from player
        List<PurgeSpawnPoint> eligible = all.stream()
                .filter(p -> horizontalDistance(playerX, playerZ, p.x(), p.z()) >= MIN_SPAWN_DISTANCE)
                .toList();

        // If none pass filter, pick the farthest one
        if (eligible.isEmpty()) {
            PurgeSpawnPoint farthest = null;
            double maxDist = -1;
            for (PurgeSpawnPoint p : all) {
                double dist = horizontalDistance(playerX, playerZ, p.x(), p.z());
                if (dist > maxDist) {
                    maxDist = dist;
                    farthest = p;
                }
            }
            return farthest;
        }

        // Weighted random pick: weight = distance^2
        double totalWeight = 0;
        double[] weights = new double[eligible.size()];
        for (int i = 0; i < eligible.size(); i++) {
            PurgeSpawnPoint p = eligible.get(i);
            double dist = horizontalDistance(playerX, playerZ, p.x(), p.z());
            weights[i] = dist * dist;
            totalWeight += weights[i];
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0;
        for (int i = 0; i < eligible.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return eligible.get(i);
            }
        }
        return eligible.get(eligible.size() - 1);
    }

    /**
     * Build a balanced spawn plan that prefers farther points while distributing
     * spawns across all eligible points in round-robin order.
     */
    public List<PurgeSpawnPoint> buildSpawnPlan(double playerX, double playerZ, int totalCount) {
        if (totalCount <= 0) {
            return List.of();
        }
        Collection<PurgeSpawnPoint> all = spawnPoints.values();
        if (all.isEmpty()) {
            return List.of();
        }

        List<PurgeSpawnPoint> eligible = all.stream()
                .filter(p -> horizontalDistance(playerX, playerZ, p.x(), p.z()) >= MIN_SPAWN_DISTANCE)
                .sorted(Comparator.comparingDouble((PurgeSpawnPoint p) ->
                        horizontalDistance(playerX, playerZ, p.x(), p.z())).reversed())
                .toList();

        List<PurgeSpawnPoint> preferred = eligible.isEmpty()
                ? all.stream()
                .sorted(Comparator.comparingDouble((PurgeSpawnPoint p) ->
                        horizontalDistance(playerX, playerZ, p.x(), p.z())).reversed())
                .toList()
                : eligible;

        if (preferred.isEmpty()) {
            return List.of();
        }

        int start = ThreadLocalRandom.current().nextInt(preferred.size());
        List<PurgeSpawnPoint> plan = new ArrayList<>(totalCount);
        for (int i = 0; i < totalCount; i++) {
            int idx = (start + i) % preferred.size();
            plan.add(preferred.get(idx));
        }
        return plan;
    }

    private static double horizontalDistance(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void createTable() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS purge_spawn_points ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "x DOUBLE NOT NULL, "
                + "y DOUBLE NOT NULL, "
                + "z DOUBLE NOT NULL, "
                + "yaw FLOAT NOT NULL DEFAULT 0"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create purge_spawn_points table");
        }
    }

    private void loadAll() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "SELECT id, x, y, z, yaw FROM purge_spawn_points";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    spawnPoints.put(id, new PurgeSpawnPoint(
                            id,
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw")
                    ));
                }
            }
            LOGGER.atInfo().log("Loaded " + spawnPoints.size() + " purge spawn points");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load spawn points");
        }
    }
}
