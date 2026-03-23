package io.hyvexa.core.economy;

import java.util.UUID;

/**
 * Common contract for persistent player currencies.
 */
public interface CurrencyStore {

    long getBalance(UUID playerId);

    void setBalance(UUID playerId, long amount);

    long addBalance(UUID playerId, long amount);

    long removeBalance(UUID playerId, long amount);

    void evictPlayer(UUID playerId);
}
