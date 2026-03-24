package io.hyvexa.purge.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.purge.data.PurgeWaveDefinition;

import java.sql.Connection;
import java.sql.PreparedStatement;
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
            boolean success = DatabaseManager.execute(this.db, sql, stmt -> {
                stmt.setInt(1, waveNumber);
                stmt.setInt(2, DEFAULT_SPAWN_DELAY_MS);
                stmt.setInt(3, DEFAULT_SPAWN_BATCH_SIZE);
            });
            if (!success) {
                return -1;
            }
            waves.put(waveNumber, new PurgeWaveDefinition(
                    waveNumber, new HashMap<>(), DEFAULT_SPAWN_DELAY_MS, DEFAULT_SPAWN_BATCH_SIZE));
            return waveNumber;
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
        DatabaseManager.execute(this.db, "DELETE FROM purge_wave_variant_counts");
        DatabaseManager.execute(this.db, "DELETE FROM purge_waves");
        waves.clear();
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
        int rows = DatabaseManager.executeCount(this.db, sql, stmt -> {
            stmt.setInt(1, value);
            stmt.setInt(2, waveNumber);
        });
        if (rows <= 0) {
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

        boolean success;
        if (safeCount == 0) {
            String sql = "DELETE FROM purge_wave_variant_counts WHERE wave_number = ? AND variant_key = ?";
            success = DatabaseManager.execute(this.db, sql, stmt -> {
                stmt.setInt(1, waveNumber);
                stmt.setString(2, variantKey);
            });
        } else {
            String sql = "INSERT INTO purge_wave_variant_counts (wave_number, variant_key, count) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE count = ?";
            success = DatabaseManager.execute(this.db, sql, stmt -> {
                stmt.setInt(1, waveNumber);
                stmt.setString(2, variantKey);
                stmt.setInt(3, safeCount);
                stmt.setInt(4, safeCount);
            });
        }
        if (!success) {
            return false;
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
        List<Map.Entry<Integer, int[]>> waveRows = DatabaseManager.queryList(this.db, waveSql,
                rs -> Map.entry(rs.getInt("wave_number"), new int[]{
                        Math.max(100, rs.getInt("spawn_delay_ms")),
                        Math.max(1, rs.getInt("spawn_batch_size"))
                }));
        Map<Integer, int[]> waveBaseData = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : waveRows) {
            waveBaseData.put(entry.getKey(), entry.getValue());
        }
        if (waveBaseData.isEmpty()) {
            return;
        }

        // Load variant counts
        String countsSql = "SELECT wave_number, variant_key, count FROM purge_wave_variant_counts";
        List<Object[]> countRows = DatabaseManager.queryList(this.db, countsSql,
                rs -> new Object[]{rs.getInt("wave_number"), rs.getString("variant_key"), Math.max(0, rs.getInt("count"))});
        Map<Integer, Map<String, Integer>> variantCountsMap = new HashMap<>();
        for (Object[] row : countRows) {
            variantCountsMap.computeIfAbsent((Integer) row[0], k -> new HashMap<>())
                    .put((String) row[1], (Integer) row[2]);
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
