package io.hyvexa.purge.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.purge.data.PurgeWaveDefinition;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeWaveConfigManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int DEFAULT_SPAWN_DELAY_MS = 500;
    private static final int DEFAULT_SPAWN_BATCH_SIZE = 5;
    private static final String PERSISTENCE_DISABLED_MESSAGE =
            "Database is unavailable. Purge wave settings require database connectivity.";

    private final ConnectionProvider db;
    private final ConcurrentHashMap<Integer, PurgeWaveDefinition> waves = new ConcurrentHashMap<>();
    private final PurgeVariantConfigManager variantConfigManager;

    public PurgeWaveConfigManager(PurgeVariantConfigManager variantConfigManager) {
        this(DatabaseManager.getInstance(), variantConfigManager);
    }

    public PurgeWaveConfigManager(ConnectionProvider db, PurgeVariantConfigManager variantConfigManager) {
        this.db = db;
        this.variantConfigManager = variantConfigManager;
        loadAll();
    }

    public boolean hasWaves() {
        return !waves.isEmpty();
    }

    public boolean hasWave(int waveNumber) {
        return waveNumber > 0 && waves.containsKey(waveNumber);
    }

    public PurgeWaveDefinition getWave(int waveNumber) {
        return waves.get(waveNumber);
    }

    public List<PurgeWaveDefinition> getAllWaves() {
        List<PurgeWaveDefinition> list = new ArrayList<>(waves.values());
        list.sort(Comparator.comparingInt(PurgeWaveDefinition::waveNumber));
        return list;
    }

    public boolean isPersistenceAvailable() {
        return this.db.isInitialized();
    }

    public String getPersistenceDisabledMessage() {
        return PERSISTENCE_DISABLED_MESSAGE;
    }

    public int addWave() {
        if (!isPersistenceAvailable()) {
            return -1;
        }
        synchronized (waves) {
            int waveNumber = waves.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
            String sql = "INSERT INTO purge_waves (wave_number, spawn_delay_ms, spawn_batch_size) VALUES (?, ?, ?)";
            try (Connection conn = this.db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setInt(1, waveNumber);
                stmt.setInt(2, DEFAULT_SPAWN_DELAY_MS);
                stmt.setInt(3, DEFAULT_SPAWN_BATCH_SIZE);
                stmt.executeUpdate();
                waves.put(waveNumber, new PurgeWaveDefinition(
                        waveNumber, new HashMap<>(), DEFAULT_SPAWN_DELAY_MS, DEFAULT_SPAWN_BATCH_SIZE));
                return waveNumber;
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to add purge wave " + waveNumber);
                return -1;
            }
        }
    }

    public boolean removeWave(int waveNumber) {
        if (!isPersistenceAvailable()) {
            return false;
        }
        synchronized (waves) {
            if (!waves.containsKey(waveNumber)) {
                return false;
            }
            String deleteCountsSql = "DELETE FROM purge_wave_variant_counts WHERE wave_number = ?";
            String deleteSql = "DELETE FROM purge_waves WHERE wave_number = ?";
            String shiftCountsSql = "UPDATE purge_wave_variant_counts SET wave_number = wave_number - 1 WHERE wave_number > ?";
            String shiftSql = "UPDATE purge_waves SET wave_number = wave_number - 1 WHERE wave_number > ?";
            boolean committed = this.db.withTransaction(conn -> {
                try (PreparedStatement deleteCountsStmt = conn.prepareStatement(deleteCountsSql);
                     PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                     PreparedStatement shiftCountsStmt = conn.prepareStatement(shiftCountsSql);
                     PreparedStatement shiftStmt = conn.prepareStatement(shiftSql)) {
                    DatabaseManager.applyQueryTimeout(deleteCountsStmt);
                    DatabaseManager.applyQueryTimeout(deleteStmt);
                    DatabaseManager.applyQueryTimeout(shiftCountsStmt);
                    DatabaseManager.applyQueryTimeout(shiftStmt);

                    deleteCountsStmt.setInt(1, waveNumber);
                    deleteCountsStmt.executeUpdate();

                    deleteStmt.setInt(1, waveNumber);
                    deleteStmt.executeUpdate();

                    shiftCountsStmt.setInt(1, waveNumber);
                    shiftCountsStmt.executeUpdate();

                    shiftStmt.setInt(1, waveNumber);
                    shiftStmt.executeUpdate();
                }
            });
            if (!committed) {
                return false;
            }

            reloadFromMapAfterDeletion(waveNumber);
            return true;
        }
    }

    public void clearAll() {
        if (!isPersistenceAvailable()) {
            return;
        }
        try (Connection conn = this.db.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM purge_wave_variant_counts")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM purge_waves")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            waves.clear();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to clear purge waves");
        }
    }

    public boolean adjustVariantCount(int waveNumber, String variantKey, int delta) {
        if (variantKey == null || delta == 0) {
            return false;
        }
        synchronized (waves) {
            PurgeWaveDefinition wave = waves.get(waveNumber);
            if (wave == null) {
                return false;
            }
            int newCount = Math.max(0, wave.getCount(variantKey) + delta);
            return setVariantCount(waveNumber, variantKey, newCount);
        }
    }

    public boolean adjustSpawnDelay(int waveNumber, int deltaMs) {
        if (deltaMs == 0) {
            return false;
        }
        synchronized (waves) {
            PurgeWaveDefinition wave = waves.get(waveNumber);
            if (wave == null) {
                return false;
            }
            int newDelay = Math.max(100, wave.spawnDelayMs() + deltaMs);
            return setSpawnField(waveNumber, "spawn_delay_ms", newDelay,
                    w -> new PurgeWaveDefinition(w.waveNumber(), w.variantCounts(), newDelay, w.spawnBatchSize()));
        }
    }

    public boolean adjustBatchSize(int waveNumber, int delta) {
        if (delta == 0) {
            return false;
        }
        synchronized (waves) {
            PurgeWaveDefinition wave = waves.get(waveNumber);
            if (wave == null) {
                return false;
            }
            int newSize = Math.max(1, wave.spawnBatchSize() + delta);
            return setSpawnField(waveNumber, "spawn_batch_size", newSize,
                    w -> new PurgeWaveDefinition(w.waveNumber(), w.variantCounts(), w.spawnDelayMs(), newSize));
        }
    }

    private boolean setSpawnField(int waveNumber, String column, int value,
                                   java.util.function.Function<PurgeWaveDefinition, PurgeWaveDefinition> updater) {
        if (!isPersistenceAvailable()) {
            return false;
        }
        PurgeWaveDefinition wave = waves.get(waveNumber);
        if (wave == null) {
            return false;
        }
        String sql = "UPDATE purge_waves SET " + column + " = ? WHERE wave_number = ?";
        try (Connection conn = this.db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setInt(1, value);
            stmt.setInt(2, waveNumber);
            int rows = stmt.executeUpdate();
            if (rows <= 0) {
                return false;
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to update purge wave " + waveNumber + " " + column);
            return false;
        }
        waves.put(waveNumber, updater.apply(wave));
        return true;
    }

    private boolean setVariantCount(int waveNumber, String variantKey, int count) {
        if (!isPersistenceAvailable()) {
            return false;
        }
        int safeCount = Math.max(0, count);
        PurgeWaveDefinition wave = waves.get(waveNumber);
        if (wave == null) {
            return false;
        }

        if (safeCount == 0) {
            String sql = "DELETE FROM purge_wave_variant_counts WHERE wave_number = ? AND variant_key = ?";
            try (Connection conn = this.db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setInt(1, waveNumber);
                stmt.setString(2, variantKey);
                stmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to delete variant count for wave " + waveNumber + " " + variantKey);
                return false;
            }
        } else {
            String sql = "INSERT INTO purge_wave_variant_counts (wave_number, variant_key, count) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE count = ?";
            try (Connection conn = this.db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setInt(1, waveNumber);
                stmt.setString(2, variantKey);
                stmt.setInt(3, safeCount);
                stmt.setInt(4, safeCount);
                stmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to update variant count for wave " + waveNumber + " " + variantKey);
                return false;
            }
        }

        Map<String, Integer> newCounts = new HashMap<>(wave.variantCounts());
        if (safeCount == 0) {
            newCounts.remove(variantKey);
        } else {
            newCounts.put(variantKey, safeCount);
        }
        waves.put(waveNumber, new PurgeWaveDefinition(wave.waveNumber(), newCounts, wave.spawnDelayMs(), wave.spawnBatchSize()));
        return true;
    }

    private void reloadFromMapAfterDeletion(int deletedWaveNumber) {
        List<PurgeWaveDefinition> snapshot = getAllWaves();
        waves.clear();
        for (PurgeWaveDefinition wave : snapshot) {
            if (wave.waveNumber() < deletedWaveNumber) {
                waves.put(wave.waveNumber(), wave);
            } else if (wave.waveNumber() > deletedWaveNumber) {
                int shiftedWave = wave.waveNumber() - 1;
                waves.put(shiftedWave, new PurgeWaveDefinition(
                        shiftedWave, wave.variantCounts(), wave.spawnDelayMs(), wave.spawnBatchSize()));
            }
        }
    }

    private void loadAll() {
        if (!this.db.isInitialized()) {
            return;
        }
        // Load wave base data
        String waveSql = "SELECT wave_number, spawn_delay_ms, spawn_batch_size FROM purge_waves ORDER BY wave_number ASC";
        Map<Integer, int[]> waveBaseData = new HashMap<>();
        try (Connection conn = this.db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(waveSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int waveNumber = rs.getInt("wave_number");
                    waveBaseData.put(waveNumber, new int[]{
                            Math.max(100, rs.getInt("spawn_delay_ms")),
                            Math.max(1, rs.getInt("spawn_batch_size"))
                    });
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load purge waves");
            return;
        }

        // Load variant counts
        Map<Integer, Map<String, Integer>> variantCountsMap = new HashMap<>();
        String countsSql = "SELECT wave_number, variant_key, count FROM purge_wave_variant_counts";
        try (Connection conn = this.db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(countsSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int waveNumber = rs.getInt("wave_number");
                    String variantKey = rs.getString("variant_key");
                    int count = Math.max(0, rs.getInt("count"));
                    variantCountsMap.computeIfAbsent(waveNumber, k -> new HashMap<>()).put(variantKey, count);
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load purge wave variant counts");
        }

        // Build wave definitions
        for (var entry : waveBaseData.entrySet()) {
            int waveNumber = entry.getKey();
            int[] base = entry.getValue();
            Map<String, Integer> counts = variantCountsMap.getOrDefault(waveNumber, new HashMap<>());
            waves.put(waveNumber, new PurgeWaveDefinition(waveNumber, counts, base[0], base[1]));
        }

        LOGGER.atInfo().log("Loaded " + waves.size() + " purge wave definitions");
    }
}
