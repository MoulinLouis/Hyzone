package org.hyvote.plugins.votifier;

/**
 * Configuration for the direct message sent to players as a vote reminder.
 *
 * <p>The message is sent as a direct/whisper message to the player.</p>
 *
 * @param enabled Whether to send a direct message reminder (default true)
 * @param text    The message text with TaleMessage formatting (supports click tags, colors, etc.)
 */
public record VoteReminderMessageConfig(
        boolean enabled,
        String text
) {

    /**
     * Returns a VoteReminderMessageConfig with default values.
     *
     * @return default message configuration
     */
    public static VoteReminderMessageConfig defaults() {
        return new VoteReminderMessageConfig(
                true,
                "<gray>You haven't voted today! You can <orange>'/vote'</orange> every day to receive free rewards!</gray>"
        );
    }

    /**
     * Merges this config with defaults, using default values for any null fields.
     *
     * @param defaults the default configuration to fall back to for null fields
     * @return a new VoteReminderMessageConfig with null fields replaced by defaults
     */
    public VoteReminderMessageConfig merge(VoteReminderMessageConfig defaults) {
        return new VoteReminderMessageConfig(
                this.enabled,
                this.text != null ? this.text : defaults.text()
        );
    }
}
