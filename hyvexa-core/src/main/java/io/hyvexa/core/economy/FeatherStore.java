package io.hyvexa.core.economy;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;

import java.util.UUID;

/**
 * Feather currency store. Singleton, lazy-load, immediate writes.
 */
public class FeatherStore extends CachedCurrencyStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static volatile FeatherStore instance;

    private FeatherStore(ConnectionProvider db) {
        super(db);
    }

    public static FeatherStore createAndRegister(ConnectionProvider db) {
        if (instance != null) {
            throw new IllegalStateException("FeatherStore already initialized");
        }
        instance = new FeatherStore(db);
        instance.initialize();
        return instance;
    }

    public static FeatherStore get() {
        FeatherStore ref = instance;
        if (ref == null) {
            throw new IllegalStateException("FeatherStore not yet initialized — check plugin load order");
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
        return "player_feathers";
    }

    @Override
    protected String columnName() {
        return "feathers";
    }

    @Override
    protected String currencyLabel() {
        return "feathers";
    }

    @Override
    protected void registerBridge() {
        CurrencyBridge.register("feathers", this);
    }

    // ── Convenience methods preserving existing public API ───────────────

    public long getFeathers(UUID playerId) {
        return getBalance(playerId);
    }

    public long getCachedFeathers(UUID playerId) {
        return getCachedBalance(playerId);
    }

    public void setFeathers(UUID playerId, long feathers) {
        setBalance(playerId, feathers);
    }

    public long addFeathers(UUID playerId, long amount) {
        return addBalance(playerId, amount);
    }

    public long removeFeathers(UUID playerId, long amount) {
        return removeBalance(playerId, amount);
    }
}
