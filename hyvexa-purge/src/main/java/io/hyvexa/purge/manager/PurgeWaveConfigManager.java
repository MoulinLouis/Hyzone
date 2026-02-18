package io.hyvexa.purge.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.purge.data.PurgeWaveDefinition;
import io.hyvexa.purge.data.PurgeZombieVariant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeWaveConfigManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int DEFAULT_SLOW_COUNT = 0;
    private static final int DEFAULT_NORMAL_COUNT = 5;
    private static final int DEFAULT_FAST_COUNT = 0;
    private static final int DEFAULT_SPAWN_DELAY_MS = 500;
    private static final int DEFAULT_SPAWN_BATCH_SIZE = 5;
    private static final String PERSISTENCE_DISABLED_MESSAGE =
            "Database is unavailable. Purge wave settings require database connectivity.";

    private final ConcurrentHashMap<Integer, PurgeWaveDefinition> waves = new ConcurrentHashMap<>();

    public PurgeWaveConfigManager() {
        createTable();
        migrateTable();
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
        return DatabaseManager.getInstance().isInitialized();
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
            String sql = "INSERT INTO purge_waves (wave_number, slow_count, normal_count, fast_count, spawn_delay_ms, spawn_batch_size) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setInt(1, waveNumber);
                stmt.setInt(2, DEFAULT_SLOW_COUNT);
                stmt.setInt(3, DEFAULT_NORMAL_COUNT);
                stmt.setInt(4, DEFAULT_FAST_COUNT);
                stmt.setInt(5, DEFAULT_SPAWN_DELAY_MS);
                stmt.setInt(6, DEFAULT_SPAWN_BATCH_SIZE);
                stmt.executeUpdate();
                waves.put(waveNumber, new PurgeWaveDefinition(
                        waveNumber,
                        DEFAULT_SLOW_COUNT,
                        DEFAULT_NORMAL_COUNT,
                        DEFAULT_FAST_COUNT,
                        DEFAULT_SPAWN_DELAY_MS,
                        DEFAULT_SPAWN_BATCH_SIZE
                ));
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
            String deleteSql = "DELETE FROM purge_waves WHERE wave_number = ?";
            String shiftSql = "UPDATE purge_waves SET wave_number = wave_number - 1 WHERE wave_number > ?";
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                     PreparedStatement shiftStmt = conn.prepareStatement(shiftSql)) {
                    DatabaseManager.applyQueryTimeout(deleteStmt);
                    DatabaseManager.applyQueryTimeout(shiftStmt);
                    deleteStmt.setInt(1, waveNumber);
                    deleteStmt.executeUpdate();

                    shiftStmt.setInt(1, waveNumber);
                    shiftStmt.executeUpdate();

                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to remove purge wave " + waveNumber);
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
        String sql = "DELETE FROM purge_waves";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            waves.clear();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to clear purge waves");
        }
    }

    public boolean adjustVariantCount(int waveNumber, PurgeZombieVariant variant, int delta) {
        if (variant == null || delta == 0) {
            return false;
        }
        synchronized (waves) {
            PurgeWaveDefinition wave = waves.get(waveNumber);
            if (wave == null) {
                return false;
            }
            int newCount = Math.max(0, wave.getCount(variant) + delta);
            return setVariantCount(waveNumber, variant, newCount);
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
                    w -> new PurgeWaveDefinition(w.waveNumber(), w.slowCount(), w.normalCount(), w.fastCount(), newDelay, w.spawnBatchSize()));
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
                    w -> new PurgeWaveDefinition(w.waveNumber(), w.slowCount(), w.normalCount(), w.fastCount(), w.spawnDelayMs(), newSize));
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
        try (Connection conn = DatabaseManager.getInstance().getConnection();
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

    private boolean setVariantCount(int waveNumber, PurgeZombieVariant variant, int count) {
        if (!isPersistenceAvailable()) {
            return false;
        }
        int safeCount = Math.max(0, count);
        PurgeWaveDefinition wave = waves.get(waveNumber);
        if (wave == null) {
            return false;
        }

        String column = switch (variant) {
            case SLOW -> "slow_count";
            case NORMAL -> "normal_count";
            case FAST -> "fast_count";
        };

        String sql = "UPDATE purge_waves SET " + column + " = ? WHERE wave_number = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setInt(1, safeCount);
            stmt.setInt(2, waveNumber);
            int rows = stmt.executeUpdate();
            if (rows <= 0) {
                return false;
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to update purge wave " + waveNumber + " " + variant.name());
            return false;
        }

        PurgeWaveDefinition updated = switch (variant) {
            case SLOW -> new PurgeWaveDefinition(wave.waveNumber(), safeCount, wave.normalCount(), wave.fastCount(), wave.spawnDelayMs(), wave.spawnBatchSize());
            case NORMAL -> new PurgeWaveDefinition(wave.waveNumber(), wave.slowCount(), safeCount, wave.fastCount(), wave.spawnDelayMs(), wave.spawnBatchSize());
            case FAST -> new PurgeWaveDefinition(wave.waveNumber(), wave.slowCount(), wave.normalCount(), safeCount, wave.spawnDelayMs(), wave.spawnBatchSize());
        };
        waves.put(waveNumber, updated);
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
                        shiftedWave,
                        wave.slowCount(),
                        wave.normalCount(),
                        wave.fastCount(),
                        wave.spawnDelayMs(),
                        wave.spawnBatchSize()
                ));
            }
        }
    }

    private void createTable() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS purge_waves ("
                + "wave_number INT NOT NULL PRIMARY KEY, "
                + "slow_count INT NOT NULL DEFAULT 0, "
                + "normal_count INT NOT NULL DEFAULT 0, "
                + "fast_count INT NOT NULL DEFAULT 0, "
                + "spawn_delay_ms INT NOT NULL DEFAULT 500, "
                + "spawn_batch_size INT NOT NULL DEFAULT 5"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create purge_waves table");
        }
    }

    private void migrateTable() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String[] migrations = {
            "ALTER TABLE purge_waves ADD COLUMN IF NOT EXISTS spawn_delay_ms INT NOT NULL DEFAULT 500",
            "ALTER TABLE purge_waves ADD COLUMN IF NOT EXISTS spawn_batch_size INT NOT NULL DEFAULT 5"
        };
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            for (String sql : migrations) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    DatabaseManager.applyQueryTimeout(stmt);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to migrate purge_waves table");
        }
    }

    private void loadAll() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "SELECT wave_number, slow_count, normal_count, fast_count, spawn_delay_ms, spawn_batch_size FROM purge_waves ORDER BY wave_number ASC";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int waveNumber = rs.getInt("wave_number");
                    waves.put(waveNumber, new PurgeWaveDefinition(
                            waveNumber,
                            Math.max(0, rs.getInt("slow_count")),
                            Math.max(0, rs.getInt("normal_count")),
                            Math.max(0, rs.getInt("fast_count")),
                            Math.max(100, rs.getInt("spawn_delay_ms")),
                            Math.max(1, rs.getInt("spawn_batch_size"))
                    ));
                }
            }
            LOGGER.atInfo().log("Loaded " + waves.size() + " purge wave definitions");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load purge waves");
        }
    }
}
