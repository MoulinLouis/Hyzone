package io.hyvexa.ascend.mine.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlockConfigStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ConnectionProvider db;

    // blockTypeId -> price (global, not per-mine)
    private final Map<String, Long> blockPrices = new ConcurrentHashMap<>();

    // blockTypeId -> hp (global, not per-zone)
    private final Map<String, Integer> blockHpMap = new ConcurrentHashMap<>();

    public BlockConfigStore(ConnectionProvider db) {
        this.db = db;
    }

    public void syncLoad(Connection conn) throws SQLException {
        loadBlockPrices(conn);
        loadBlockHp(conn);
    }

    // --- Block Prices ---

    private void loadBlockPrices(Connection conn) throws SQLException {
        blockPrices.clear();
        String sql = "SELECT block_type_id, price FROM block_prices";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    blockPrices.put(rs.getString("block_type_id"), rs.getLong("price"));
                }
            }
        }
    }

    public void saveBlockPrice(String blockTypeId, long price) {
        if (blockTypeId == null) return;

        if (price <= 1) {
            blockPrices.remove(blockTypeId);
        } else {
            blockPrices.put(blockTypeId, price);
        }

        if (!this.db.isInitialized()) return;

        if (price <= 1) {
            removeBlockPriceFromDatabase(blockTypeId);
            return;
        }

        String sql = """
            INSERT INTO block_prices (block_type_id, price)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE price = VALUES(price)
            """;

        DatabaseManager.execute(this.db, sql, stmt -> {
            stmt.setString(1, blockTypeId);
            stmt.setLong(2, price);
        });
    }

    public void removeBlockPrice(String blockTypeId) {
        if (blockTypeId == null) return;
        blockPrices.remove(blockTypeId);
        removeBlockPriceFromDatabase(blockTypeId);
    }

    private void removeBlockPriceFromDatabase(String blockTypeId) {
        DatabaseManager.execute(this.db, "DELETE FROM block_prices WHERE block_type_id = ?",
            stmt -> stmt.setString(1, blockTypeId));
    }

    public long getBlockPrice(String blockTypeId) {
        return blockPrices.getOrDefault(blockTypeId, 1L);
    }

    public Map<String, Long> getBlockPrices() {
        return Collections.unmodifiableMap(blockPrices);
    }

    // --- Block HP (global) ---

    private void loadBlockHp(Connection conn) throws SQLException {
        blockHpMap.clear();
        String sql = "SELECT block_type_id, hp FROM block_hp";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    blockHpMap.put(rs.getString("block_type_id"), rs.getInt("hp"));
                }
            }
        }
    }

    public void saveBlockHp(String blockTypeId, int hp) {
        if (blockTypeId == null) return;

        if (hp <= 1) {
            blockHpMap.remove(blockTypeId);
        } else {
            blockHpMap.put(blockTypeId, hp);
        }

        if (!this.db.isInitialized()) return;

        if (hp <= 1) {
            removeBlockHpFromDatabase(blockTypeId);
            return;
        }

        String sql = """
            INSERT INTO block_hp (block_type_id, hp)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE hp = VALUES(hp)
            """;

        DatabaseManager.execute(this.db, sql, stmt -> {
            stmt.setString(1, blockTypeId);
            stmt.setInt(2, hp);
        });
    }

    public void removeBlockHp(String blockTypeId) {
        if (blockTypeId == null) return;
        blockHpMap.remove(blockTypeId);
        removeBlockHpFromDatabase(blockTypeId);
    }

    private void removeBlockHpFromDatabase(String blockTypeId) {
        DatabaseManager.execute(this.db, "DELETE FROM block_hp WHERE block_type_id = ?",
            stmt -> stmt.setString(1, blockTypeId));
    }

    public int getBlockHp(String blockTypeId) {
        return blockHpMap.getOrDefault(blockTypeId, 1);
    }

    public Map<String, Integer> getBlockHpMap() {
        return Collections.unmodifiableMap(blockHpMap);
    }
}
