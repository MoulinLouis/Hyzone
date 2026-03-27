package io.hyvexa.common.hud;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for HUD managers that track per-player HUD state.
 * Owns the primary HUD map, ready-at throttle, and query infrastructure.
 * Subclasses call {@link #registerHud} from their own attach methods
 * (attach signatures vary too much across modules to unify).
 *
 * @param <H> the HUD state type (e.g. MineHudState, PurgeHud, AscendHud)
 */
public abstract class AbstractHudManager<H> {

    protected final ConcurrentHashMap<UUID, H> huds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> readyAt = new ConcurrentHashMap<>();
    private final long readyDelayMs;

    protected AbstractHudManager(long readyDelayMs) {
        this.readyDelayMs = readyDelayMs;
    }

    // --- Registration (called by subclass attach methods) ---

    protected void registerHud(UUID playerId, H hud) {
        huds.put(playerId, hud);
        readyAt.put(playerId, System.currentTimeMillis() + readyDelayMs);
    }

    // --- Removal ---

    public void removePlayer(UUID playerId) {
        H hud = huds.remove(playerId);
        readyAt.remove(playerId);
        if (hud != null) {
            onRemove(playerId, hud);
        }
    }

    // --- Throttling ---

    protected void clearThrottle(UUID playerId) {
        readyAt.remove(playerId);
    }

    protected boolean isReady(UUID playerId) {
        Long ready = readyAt.get(playerId);
        return ready != null && System.currentTimeMillis() >= ready;
    }

    // --- Queries ---

    public H getHud(UUID playerId) {
        return playerId != null ? huds.get(playerId) : null;
    }

    public boolean hasHud(UUID playerId) {
        return huds.containsKey(playerId);
    }

    public Set<UUID> getTrackedPlayerIds() {
        return huds.keySet();
    }

    // --- Abstract ---

    protected void onRemove(UUID playerId, H hud) {
    }
}
