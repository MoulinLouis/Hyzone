package org.hyvote.plugins.votifier;

/**
 * Configuration for the sound played to players as a vote reminder.
 *
 * @param enabled       Whether to play a sound with the reminder (default true)
 * @param sound         The sound ID to play (e.g., "SFX_Player_Pickup_Item")
 * @param soundCategory The sound category for volume control (e.g., "UI", "MUSIC", "SFX")
 */
public record VoteReminderSoundConfig(
        boolean enabled,
        String sound,
        String soundCategory
) {

    /**
     * Returns a VoteReminderSoundConfig with default values.
     *
     * @return default sound configuration
     */
    public static VoteReminderSoundConfig defaults() {
        return new VoteReminderSoundConfig(
                true,
                "SFX_Avatar_Powers_Enable",
                "UI"
        );
    }

    /**
     * Merges this config with defaults, using default values for any null fields.
     *
     * @param defaults the default configuration to fall back to for null fields
     * @return a new VoteReminderSoundConfig with null fields replaced by defaults
     */
    public VoteReminderSoundConfig merge(VoteReminderSoundConfig defaults) {
        return new VoteReminderSoundConfig(
                this.enabled,
                this.sound != null ? this.sound : defaults.sound(),
                this.soundCategory != null ? this.soundCategory : defaults.soundCategory()
        );
    }
}
