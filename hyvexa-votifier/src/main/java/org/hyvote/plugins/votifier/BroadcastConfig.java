package org.hyvote.plugins.votifier;

/**
 * Configuration for server-wide vote broadcast announcements.
 *
 * <p>Supports TaleMessage formatting tags for styled text. Available placeholders:</p>
 * <ul>
 *   <li>{@code {from}} - The name of the voting site</li>
 *   <li>{@code {username}} - The username of the player who voted</li>
 * </ul>
 *
 * @param enabled Whether to broadcast vote announcements to all online players (default false)
 * @param message The broadcast message with TaleMessage formatting and placeholders
 */
public record BroadcastConfig(
        boolean enabled,
        String message
) {

    /**
     * Returns a BroadcastConfig with default values.
     *
     * @return default broadcast configuration
     */
    public static BroadcastConfig defaults() {
        return new BroadcastConfig(
                false,
                "<orange>{username}</orange> <gray>voted on</gray> <orange>{from}</orange><gray>!</gray>"
        );
    }

    /**
     * Merges this config with defaults, using default values for any null fields.
     *
     * @param defaults the default configuration to fall back to for null fields
     * @return a new BroadcastConfig with null fields replaced by defaults
     */
    public BroadcastConfig merge(BroadcastConfig defaults) {
        return new BroadcastConfig(
                this.enabled,
                this.message != null ? this.message : defaults.message()
        );
    }
}
