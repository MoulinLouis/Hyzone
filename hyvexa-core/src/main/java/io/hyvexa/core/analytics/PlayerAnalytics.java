package io.hyvexa.core.analytics;

import java.util.UUID;

/**
 * Write-side analytics contract used by gameplay code.
 */
public interface PlayerAnalytics {

    void logEvent(UUID playerId, String eventType, String dataJson);

    void logPurchase(UUID playerId, String itemId, long amount, String currency, String source);
}
