package org.hyvote.plugins.votifier;

/**
 * Configuration for the toast notification shown to players as a vote reminder.
 *
 * <p>The notification appears as a toast popup, similar to the vote received notification.</p>
 *
 * @param enabled            Whether to display a toast notification reminder (default true)
 * @param titleMessage       The toast title with TaleMessage formatting
 * @param descriptionMessage The toast description with TaleMessage formatting
 * @param iconItem           The item ID to display as the toast icon
 */
public record VoteReminderNotificationConfig(
        boolean enabled,
        String titleMessage,
        String descriptionMessage,
        String iconItem
) {

    /**
     * Returns a VoteReminderNotificationConfig with default values.
     *
     * @return default notification configuration
     */
    public static VoteReminderNotificationConfig defaults() {
        return new VoteReminderNotificationConfig(
                true,
                "<orange>You haven't voted today</orange>",
                "<gray>You can <orange>/vote</orange> every day for free rewards!</gray>",
                "Upgrade_Backpack_2"
        );
    }

    /**
     * Merges this config with defaults, using default values for any null fields.
     *
     * @param defaults the default configuration to fall back to for null fields
     * @return a new VoteReminderNotificationConfig with null fields replaced by defaults
     */
    public VoteReminderNotificationConfig merge(VoteReminderNotificationConfig defaults) {
        return new VoteReminderNotificationConfig(
                this.enabled,
                this.titleMessage != null ? this.titleMessage : defaults.titleMessage(),
                this.descriptionMessage != null ? this.descriptionMessage : defaults.descriptionMessage(),
                this.iconItem != null ? this.iconItem : defaults.iconItem()
        );
    }
}
