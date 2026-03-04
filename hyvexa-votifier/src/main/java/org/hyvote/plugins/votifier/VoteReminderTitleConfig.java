package org.hyvote.plugins.votifier;

/**
 * Configuration for the title display shown to players as a vote reminder.
 *
 * <p>The title appears at the top center of the screen, similar to biome entrance titles.</p>
 *
 * @param enabled         Whether to display a title reminder (default true)
 * @param title           The main title text
 * @param subTitle        The subtitle text shown below the main title
 * @param durationSeconds How long the title is displayed (default 3 seconds)
 * @param fadeInSeconds   Duration of the fade-in animation (default 0.5 seconds)
 * @param fadeOutSeconds  Duration of the fade-out animation (default 0.5 seconds)
 */
public record VoteReminderTitleConfig(
        boolean enabled,
        String title,
        String subTitle,
        Double durationSeconds,
        Double fadeInSeconds,
        Double fadeOutSeconds
) {

    /**
     * Returns a VoteReminderTitleConfig with default values.
     *
     * @return default title configuration
     */
    public static VoteReminderTitleConfig defaults() {
        return new VoteReminderTitleConfig(
                true,
                "You can /vote every day for free rewards!",
                "You haven't voted today",
                3.0,
                0.5,
                0.5
        );
    }

    /**
     * Merges this config with defaults, using default values for any null fields.
     *
     * @param defaults the default configuration to fall back to for null fields
     * @return a new VoteReminderTitleConfig with null fields replaced by defaults
     */
    public VoteReminderTitleConfig merge(VoteReminderTitleConfig defaults) {
        return new VoteReminderTitleConfig(
                this.enabled,
                this.title != null ? this.title : defaults.title(),
                this.subTitle != null ? this.subTitle : defaults.subTitle(),
                this.durationSeconds != null ? this.durationSeconds : defaults.durationSeconds(),
                this.fadeInSeconds != null ? this.fadeInSeconds : defaults.fadeInSeconds(),
                this.fadeOutSeconds != null ? this.fadeOutSeconds : defaults.fadeOutSeconds()
        );
    }
}
