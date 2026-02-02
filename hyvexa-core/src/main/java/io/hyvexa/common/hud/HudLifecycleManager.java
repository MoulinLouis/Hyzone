package io.hyvexa.common.hud;

import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Standardized HUD lifecycle manager for mode plugins.
 * Addresses timing inconsistencies between mode HUD attachment:
 * - Hub: attaches in HubRouter.routeToWorld()
 * - Ascend: attaches in PlayerReadyEvent handler
 *
 * <p>Usage pattern:</p>
 * <pre>{@code
 * HudLifecycleManager<MyHud> hudManager = new HudLifecycleManager<>(MyHud::new);
 *
 * // On player enter/ready:
 * hudManager.attach(playerRef, player);
 *
 * // On tick (for updates):
 * MyHud hud = hudManager.getHud(playerId);
 * if (hud != null) {
 *     hud.updateValues(...);
 * }
 *
 * // On player leave/disconnect:
 * hudManager.detach(playerId);
 * }</pre>
 *
 * @param <T> The CustomUIHud subclass managed by this manager
 */
public class HudLifecycleManager<T extends CustomUIHud> {

    private final ConcurrentHashMap<UUID, T> huds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> attached = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> readyAt = new ConcurrentHashMap<>();
    private final Function<PlayerRef, T> hudFactory;
    private final long attachDelayMs;

    /**
     * Create a HUD lifecycle manager with default 250ms attachment delay.
     * @param hudFactory Factory function to create new HUD instances
     */
    public HudLifecycleManager(Function<PlayerRef, T> hudFactory) {
        this(hudFactory, 250L);
    }

    /**
     * Create a HUD lifecycle manager with custom attachment delay.
     * @param hudFactory Factory function to create new HUD instances
     * @param attachDelayMs Delay before HUD updates are applied (prevents missing UI elements)
     */
    public HudLifecycleManager(Function<PlayerRef, T> hudFactory, long attachDelayMs) {
        this.hudFactory = hudFactory;
        this.attachDelayMs = attachDelayMs;
    }

    /**
     * Attach a HUD to a player. Creates a new HUD if one doesn't exist.
     * Hides the compass by default (common to all modes).
     *
     * @param playerRef The player reference
     * @param player The player entity
     * @return The attached HUD instance
     */
    public T attach(PlayerRef playerRef, Player player) {
        return attach(playerRef, player, true);
    }

    /**
     * Attach a HUD to a player with optional compass hiding.
     *
     * @param playerRef The player reference
     * @param player The player entity
     * @param hideCompass Whether to hide the default compass HUD
     * @return The attached HUD instance
     */
    public T attach(PlayerRef playerRef, Player player, boolean hideCompass) {
        if (playerRef == null || player == null) {
            return null;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return null;
        }

        T hud = huds.computeIfAbsent(playerId, id -> hudFactory.apply(playerRef));
        player.getHudManager().setCustomHud(playerRef, hud);

        if (hideCompass) {
            player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass);
        }

        hud.show();
        attached.put(playerId, true);
        readyAt.put(playerId, System.currentTimeMillis() + attachDelayMs);

        return hud;
    }

    /**
     * Detach and clean up HUD state for a player.
     * @param playerId The player's UUID
     */
    public void detach(UUID playerId) {
        if (playerId == null) {
            return;
        }
        huds.remove(playerId);
        attached.remove(playerId);
        readyAt.remove(playerId);
    }

    /**
     * Get the HUD instance for a player.
     * @param playerId The player's UUID
     * @return The HUD instance, or null if not attached
     */
    public T getHud(UUID playerId) {
        return playerId != null ? huds.get(playerId) : null;
    }

    /**
     * Check if a HUD is attached for a player.
     * @param playerId The player's UUID
     * @return true if a HUD is attached
     */
    public boolean isAttached(UUID playerId) {
        return Boolean.TRUE.equals(attached.get(playerId));
    }

    /**
     * Check if a HUD needs to be attached for a player.
     * @param playerId The player's UUID
     * @return true if no HUD is attached
     */
    public boolean needsAttach(UUID playerId) {
        return !isAttached(playerId);
    }

    /**
     * Check if a HUD is ready for updates (past the attachment delay).
     * @param playerId The player's UUID
     * @return true if the HUD is ready for updates
     */
    public boolean isReady(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        long ready = readyAt.getOrDefault(playerId, Long.MAX_VALUE);
        return System.currentTimeMillis() >= ready;
    }

    /**
     * Ensure a HUD is attached, reattaching if necessary.
     * Useful in tick loops to handle mode transitions.
     *
     * @param playerRef The player reference
     * @param player The player entity
     * @return The HUD instance
     */
    public T ensureAttached(PlayerRef playerRef, Player player) {
        if (playerRef == null || player == null) {
            return null;
        }
        UUID playerId = playerRef.getUuid();
        T hud = huds.get(playerId);

        if (needsAttach(playerId) || hud == null) {
            return attach(playerRef, player);
        }

        // Always ensure HUD is set on player (handles world transitions)
        player.getHudManager().setCustomHud(playerRef, hud);
        return hud;
    }

    /**
     * Clear all HUD state. Use on plugin shutdown.
     */
    public void clear() {
        huds.clear();
        attached.clear();
        readyAt.clear();
    }

    /**
     * Get the number of tracked HUDs.
     * @return The HUD count
     */
    public int size() {
        return huds.size();
    }
}
