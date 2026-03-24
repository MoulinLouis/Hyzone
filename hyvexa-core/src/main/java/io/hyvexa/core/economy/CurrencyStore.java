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

    /**
     * Atomically deduct the given amount only if the player has sufficient balance.
     * Returns true if the deduction was applied, false if the balance was insufficient.
     */
    boolean deductIfSufficient(UUID playerId, long amount);

    void evictPlayer(UUID playerId);
}
