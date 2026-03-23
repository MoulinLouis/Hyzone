package io.hyvexa.runorfall.util;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.economy.CurrencyStore;

import java.util.UUID;

public final class RunOrFallFeatherBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final CurrencyStore featherStore;

    public RunOrFallFeatherBridge(CurrencyStore featherStore) {
        this.featherStore = featherStore;
    }

    public long getFeathers(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        try {
            return featherStore.getBalance(playerId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("RunOrFall Feather bridge read failed.");
            return 0L;
        }
    }

    public long getCachedFeathers(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        try {
            return featherStore.getBalance(playerId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("RunOrFall Feather bridge cached read failed.");
            return 0L;
        }
    }

    public void evictPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        try {
            featherStore.evictPlayer(playerId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("RunOrFall Feather bridge evict failed.");
        }
    }

    public boolean addFeathers(UUID playerId, long amount) {
        if (playerId == null || amount <= 0L) {
            return false;
        }
        try {
            featherStore.addBalance(playerId, amount);
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("RunOrFall Feather bridge add failed.");
            return false;
        }
    }
}
