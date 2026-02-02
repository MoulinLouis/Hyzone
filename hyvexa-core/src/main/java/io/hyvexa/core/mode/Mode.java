package io.hyvexa.core.mode;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.UUID;

/**
 * Interface defining shared lifecycle contracts for game mode plugins.
 * Standardizes how modes handle player transitions, HUD management,
 * and inventory setup across Hub, Parkour, and Ascend modules.
 *
 * <p>Implementation pattern:</p>
 * <ul>
 *   <li>Hub mode: Minimal inventory, simple HUD</li>
 *   <li>Parkour mode: Full run items, run timer HUD</li>
 *   <li>Ascend mode: Dev items, economy HUD</li>
 * </ul>
 */
public interface Mode {

    /**
     * Get the unique identifier for this mode.
     * @return The mode type enum value
     */
    ModeType getModeType();

    /**
     * Get the world name associated with this mode.
     * @return The world name (case-insensitive match)
     */
    String getWorldName();

    /**
     * Called when a player enters this mode's world.
     * Implementations should:
     * - Set up the player's inventory
     * - Attach the mode-specific HUD
     * - Initialize any mode-specific state
     *
     * @param playerRef The player reference
     * @param player The player entity (may be null if called before entity ready)
     */
    void onPlayerEnter(PlayerRef playerRef, Player player);

    /**
     * Called when a player leaves this mode's world.
     * Implementations should:
     * - Clean up mode-specific state
     * - Cancel any active runs/timers
     * - Save progress if needed
     *
     * @param playerId The player's UUID
     */
    void onPlayerLeave(UUID playerId);

    /**
     * Called when a player disconnects while in this mode.
     * Similar to onPlayerLeave but may have different persistence behavior.
     *
     * @param playerId The player's UUID
     */
    default void onPlayerDisconnect(UUID playerId) {
        onPlayerLeave(playerId);
    }

    /**
     * Check if a world name matches this mode.
     * @param worldName The world name to check
     * @return true if the world belongs to this mode
     */
    default boolean isWorldMatch(String worldName) {
        if (worldName == null) {
            return false;
        }
        return getWorldName().equalsIgnoreCase(worldName);
    }

    /**
     * Attach the mode-specific HUD to a player.
     * @param playerRef The player reference
     * @param player The player entity
     */
    void attachHud(PlayerRef playerRef, Player player);

    /**
     * Detach or clear the mode-specific HUD from a player.
     * @param playerId The player's UUID
     */
    void detachHud(UUID playerId);

    /**
     * Set up the player's inventory for this mode.
     * @param player The player entity
     */
    void setupInventory(Player player);
}
