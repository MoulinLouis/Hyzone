package org.hyvote.plugins.votifier;

/**
 * Configuration for vote notification messages displayed as toast popups.
 *
 * <p>Supports TaleMessage formatting tags for styled text. Available placeholders:</p>
 * <ul>
 *   <li>{@code {from}} - The name of the voting site</li>
 *   <li>{@code {username}} - The username of the player who voted</li>
 * </ul>
 *
 * @param enabled            Whether to display toast messages when votes are received (default false)
 * @param titleMessage       The toast title with TaleMessage formatting (default "&lt;orange&gt;Vote Received!&lt;/orange&gt;")
 * @param descriptionMessage The toast description with TaleMessage formatting and placeholders
 * @param iconItem           The item ID to display as the toast icon (default "Ore_Gold")
 */
public record VoteMessageConfig(
        boolean enabled,
        String titleMessage,
        String descriptionMessage,
        String iconItem
) {

    /**
     * Returns a VoteMessageConfig with default values.
     *
     * @return default vote message configuration
     */
    public static VoteMessageConfig defaults() {
        return new VoteMessageConfig(
                false,
                "<orange>Vote Received!</orange>",
                "<gray>Thanks for your vote on <orange>{from}</orange>!</gray>",
                "Ore_Gold"
        );
    }

    /**
     * Merges this config with defaults, using default values for any null fields.
     *
     * @param defaults the default configuration to fall back to for null fields
     * @return a new VoteMessageConfig with null fields replaced by defaults
     */
    public VoteMessageConfig merge(VoteMessageConfig defaults) {
        return new VoteMessageConfig(
                this.enabled,
                this.titleMessage != null ? this.titleMessage : defaults.titleMessage(),
                this.descriptionMessage != null ? this.descriptionMessage : defaults.descriptionMessage(),
                this.iconItem != null ? this.iconItem : defaults.iconItem()
        );
    }
}
