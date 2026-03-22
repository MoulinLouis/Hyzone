package io.hyvexa.core.economy;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.UUID;

/**
 * Feather currency store. Singleton, lazy-load, immediate writes.
 */
public class FeatherStore extends CachedCurrencyStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final FeatherStore INSTANCE = new FeatherStore();

    private FeatherStore() {
        super();
    }

    public static FeatherStore getInstance() {
        return INSTANCE;
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
