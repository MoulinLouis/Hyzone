package org.hyvote.plugins.votifier;

/**
 * Configuration for vote reminders sent to players who haven't voted today.
 *
 * <p>When a player joins the server and hasn't voted today, a reminder can be sent
 * after a configurable delay. The reminder can include a direct message, title display,
 * toast notification, and sound.</p>
 *
 * @param enabled          Whether the vote reminder system is enabled (default false)
 * @param sendOnJoin       Whether to send reminders when a player joins (default true)
 * @param voteExpiryInterval How long (in hours) before a vote "expires" and reminders resume (default 24)
 * @param delayInSeconds   Delay (in seconds) after joining before sending the reminder (default 15)
 * @param storage          Configuration for vote storage backend (default SQLite)
 * @param message          Configuration for the direct message reminder
 * @param title            Configuration for the title display reminder
 * @param notification     Configuration for the toast notification reminder
 * @param sound            Configuration for the sound played with the reminder
 */
public record VoteReminderConfig(
        boolean enabled,
        Boolean sendOnJoin,
        Integer voteExpiryInterval,
        Integer delayInSeconds,
        VoteStorageConfig storage,
        VoteReminderMessageConfig message,
        VoteReminderTitleConfig title,
        VoteReminderNotificationConfig notification,
        VoteReminderSoundConfig sound
) {

    /**
     * Returns a VoteReminderConfig with default values.
     *
     * @return default reminder configuration
     */
    public static VoteReminderConfig defaults() {
        return new VoteReminderConfig(
                false,
                true,
                24,
                15,
                VoteStorageConfig.defaults(),
                VoteReminderMessageConfig.defaults(),
                VoteReminderTitleConfig.defaults(),
                VoteReminderNotificationConfig.defaults(),
                VoteReminderSoundConfig.defaults()
        );
    }

    /**
     * Merges this config with defaults, using default values for any null fields.
     *
     * @param defaults the default configuration to fall back to for null fields
     * @return a new VoteReminderConfig with null fields replaced by defaults
     */
    public VoteReminderConfig merge(VoteReminderConfig defaults) {
        VoteStorageConfig mergedStorage = this.storage != null
                ? this.storage.merge(defaults.storage())
                : defaults.storage();
        VoteReminderMessageConfig mergedMessage = this.message != null
                ? this.message.merge(defaults.message())
                : defaults.message();
        VoteReminderTitleConfig mergedTitle = this.title != null
                ? this.title.merge(defaults.title())
                : defaults.title();
        VoteReminderNotificationConfig mergedNotification = this.notification != null
                ? this.notification.merge(defaults.notification())
                : defaults.notification();
        VoteReminderSoundConfig mergedSound = this.sound != null
                ? this.sound.merge(defaults.sound())
                : defaults.sound();

        return new VoteReminderConfig(
                this.enabled,
                this.sendOnJoin != null ? this.sendOnJoin : defaults.sendOnJoin(),
                this.voteExpiryInterval != null ? this.voteExpiryInterval : defaults.voteExpiryInterval(),
                this.delayInSeconds != null ? this.delayInSeconds : defaults.delayInSeconds(),
                mergedStorage,
                mergedMessage,
                mergedTitle,
                mergedNotification,
                mergedSound
        );
    }
}
