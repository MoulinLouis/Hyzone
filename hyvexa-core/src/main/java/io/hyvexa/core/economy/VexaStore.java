package io.hyvexa.core.economy;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
/**
 * Global vexa currency store. Singleton shared across all modules.
 * Lazy-loads per-player vexa counts from MySQL, evicts on disconnect.
 */
public class VexaStore extends CachedCurrencyStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static volatile VexaStore instance;

    private VexaStore(ConnectionProvider db) {
        super(db);
    }

    public static VexaStore createAndRegister(ConnectionProvider db) {
        if (instance != null) {
            throw new IllegalStateException("VexaStore already initialized");
        }
        instance = new VexaStore(db);
        instance.initialize();
        return instance;
    }

    public static VexaStore get() {
        VexaStore ref = instance;
        if (ref == null) {
            throw new IllegalStateException("VexaStore not yet initialized — check plugin load order");
        }
        return ref;
    }

    public static void destroy() {
        instance = null;
    }

    @Override
    protected HytaleLogger logger() {
        return LOGGER;
    }

    @Override
    protected String tableName() {
        return "player_vexa";
    }

    @Override
    protected String columnName() {
        return "vexa";
    }

    @Override
    protected String currencyLabel() {
        return "vexa";
    }

    @Override
    protected void preMigrate(Connection conn) throws SQLException {
        migratePlayerGemsToVexa(conn);
    }

    @Override
    protected void registerBridge() {
        CurrencyBridge.register("vexa", this);
    }

    // ── Convenience methods preserving existing public API ───────────────

    public long getVexa(UUID playerId) {
        return getBalance(playerId);
    }

    public long getCachedVexa(UUID playerId) {
        return getCachedBalance(playerId);
    }

    public void setVexa(UUID playerId, long vexa) {
        setBalance(playerId, vexa);
    }

    public long addVexa(UUID playerId, long amount) {
        return addBalance(playerId, amount);
    }

    public long removeVexa(UUID playerId, long amount) {
        return removeBalance(playerId, amount);
    }

    // ── Migration ────────────────────────────────────────────────────────

    private void migratePlayerGemsToVexa(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            if (tableExists(conn, "player_gems") && !tableExists(conn, "player_vexa")) {
                try (PreparedStatement stmt = DatabaseManager.prepare(conn, "RENAME TABLE player_gems TO player_vexa")) {
                    stmt.executeUpdate();
                    LOGGER.atInfo().log("Renamed table player_gems -> player_vexa");
                }
            }

            if (tableExists(conn, "player_vexa")
                    && columnExists(conn, "player_vexa", "gems")
                    && !columnExists(conn, "player_vexa", "vexa")) {
                try (PreparedStatement stmt = DatabaseManager.prepare(conn, "ALTER TABLE player_vexa RENAME COLUMN gems TO vexa")) {
                    stmt.executeUpdate();
                    LOGGER.atInfo().log("Renamed column player_vexa.gems -> player_vexa.vexa");
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to migrate player_gems/player_vexa schema");
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, tableName, columnName)) {
            return rs.next();
        }
    }
}
