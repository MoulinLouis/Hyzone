package io.hyvexa.core.economy;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight currency provider registry. Modules register their currencies here
 * so cross-module code (e.g. wardrobe) can deduct any currency without direct dependencies.
 */
public class CurrencyBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<String, CurrencyProvider> providers = new ConcurrentHashMap<>();

    public interface CurrencyProvider {
        long getBalance(UUID playerId);
        void deduct(UUID playerId, long amount);
    }

    public static void register(String name, CurrencyProvider provider) {
        providers.put(name, provider);
        LOGGER.atInfo().log("CurrencyBridge: registered provider '" + name + "'");
    }

    public static long getBalance(String currency, UUID playerId) {
        CurrencyProvider provider = providers.get(currency);
        if (provider == null) {
            LOGGER.atWarning().log("CurrencyBridge: unknown currency '" + currency + "'");
            return 0;
        }
        return provider.getBalance(playerId);
    }

    /**
     * Deduct currency from player. Returns true if successful, false if insufficient balance.
     */
    public static boolean deduct(String currency, UUID playerId, long amount) {
        CurrencyProvider provider = providers.get(currency);
        if (provider == null) {
            LOGGER.atWarning().log("CurrencyBridge: unknown currency '" + currency + "'");
            return false;
        }
        if (provider.getBalance(playerId) < amount) {
            return false;
        }
        provider.deduct(playerId, amount);
        return true;
    }

    private CurrencyBridge() {}
}
