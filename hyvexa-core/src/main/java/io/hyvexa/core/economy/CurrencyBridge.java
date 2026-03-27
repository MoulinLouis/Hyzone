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
    private static final ConcurrentHashMap<String, CurrencyStore> providers = new ConcurrentHashMap<>();

    public static void register(String name, CurrencyStore provider) {
        providers.put(name, provider);
        LOGGER.atInfo().log("CurrencyBridge: registered provider '" + name + "'");
    }

    public static long getBalance(String currency, UUID playerId) {
        CurrencyStore provider = providers.get(currency);
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
        if (amount <= 0) {
            return false;
        }
        CurrencyStore provider = providers.get(currency);
        if (provider == null) {
            LOGGER.atWarning().log("CurrencyBridge: unknown currency '" + currency + "'");
            return false;
        }
        return provider.deductIfSufficient(playerId, amount);
    }

    public static void unregister(String name) {
        if (providers.remove(name) != null) {
            LOGGER.atInfo().log("CurrencyBridge: unregistered provider '" + name + "'");
        }
    }

    public static void clear() {
        providers.clear();
    }

    private CurrencyBridge() {}
}
