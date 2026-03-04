package org.hyvote.plugins.votifier.util;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.protocol.SoundCategory;
import io.github.insideranh.talemessage.TaleMessage;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.VoteReminderConfig;
import org.hyvote.plugins.votifier.VoteReminderMessageConfig;
import org.hyvote.plugins.votifier.VoteReminderNotificationConfig;
import org.hyvote.plugins.votifier.VoteReminderSoundConfig;
import org.hyvote.plugins.votifier.VoteReminderTitleConfig;

import java.util.logging.Level;

/**
 * Utility class for sending vote reminders to players.
 *
 * <p>Supports multiple reminder types:</p>
 * <ul>
 *   <li>Direct message (whisper)</li>
 *   <li>Title display (on-screen event title)</li>
 *   <li>Toast notification</li>
 *   <li>Sound effect</li>
 * </ul>
 */
public final class VoteReminderUtil {

    private static final String DEFAULT_ICON_ITEM = "Tool_Growth_Potion";

    private VoteReminderUtil() {
        // Utility class
    }

    /**
     * Sends all enabled vote reminders to a player.
     *
     * @param plugin    the plugin instance for config and logging
     * @param playerRef the player reference to send reminders to
     * @param username  the player's display name (for logging)
     */
    public static void sendReminders(HytaleVotifierPlugin plugin, PlayerRef playerRef, String username) {
        VoteReminderConfig reminderConfig = plugin.getConfig().voteReminder();
        if (reminderConfig == null || !reminderConfig.enabled()) {
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log(
                        "Vote reminders disabled or config null, skipping for %s", username);
            }
            return;
        }

        try {
            sendMessage(plugin, playerRef, username, reminderConfig.message());
            sendTitle(plugin, playerRef, username, reminderConfig.title());
            sendNotification(plugin, playerRef, username, reminderConfig.notification());
            sendSound(plugin, playerRef, username, reminderConfig.sound());

            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log("Sent vote reminders to player %s", username);
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log(
                    "Error in sendReminders for %s: %s", username, e.getMessage());
            if (plugin.getConfig().debug()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Sends a direct message reminder to the player.
     *
     * @param plugin    the plugin instance
     * @param playerRef the player reference to message
     * @param username  the player's display name (for logging)
     * @param config    the message configuration
     */
    private static void sendMessage(HytaleVotifierPlugin plugin, PlayerRef playerRef, String username, VoteReminderMessageConfig config) {
        if (config == null || !config.enabled()) {
            return;
        }

        Message message = TaleMessage.parse(config.text());
        playerRef.sendMessage(message);

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log("Sent vote reminder message to player %s", username);
        }
    }

    /**
     * Displays a title reminder to the player.
     *
     * <p>Uses EventTitleUtil to show an on-screen title with configurable
     * duration and fade effects.</p>
     *
     * @param plugin    the plugin instance
     * @param playerRef the player reference to show the title to
     * @param username  the player's display name (for logging)
     * @param config    the title configuration
     */
    private static void sendTitle(HytaleVotifierPlugin plugin, PlayerRef playerRef, String username, VoteReminderTitleConfig config) {
        if (config == null || !config.enabled()) {
            return;
        }

        Message primaryTitle = Message.raw(config.title());
        Message secondaryTitle = Message.raw(config.subTitle());

        float duration = config.durationSeconds() != null ? config.durationSeconds().floatValue() : 3.0f;
        float fadeIn = config.fadeInSeconds() != null ? config.fadeInSeconds().floatValue() : 0.5f;
        float fadeOut = config.fadeOutSeconds() != null ? config.fadeOutSeconds().floatValue() : 0.5f;

        EventTitleUtil.showEventTitleToPlayer(
                playerRef,
                primaryTitle,
                secondaryTitle,
                false,  // isMajor - use standard size for vote reminders
                null,   // no icon
                duration,
                fadeIn,
                fadeOut
        );

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log("Sent vote reminder title to player %s", username);
        }
    }

    /**
     * Displays a toast notification reminder to the player.
     *
     * @param plugin    the plugin instance
     * @param playerRef the player reference to show the notification to
     * @param username  the player's display name (for logging)
     * @param config    the notification configuration
     */
    private static void sendNotification(HytaleVotifierPlugin plugin, PlayerRef playerRef, String username, VoteReminderNotificationConfig config) {
        if (config == null || !config.enabled()) {
            return;
        }

        Message title = TaleMessage.parse(config.titleMessage());
        Message description = TaleMessage.parse(config.descriptionMessage());

        // Create the icon item with fallback for invalid item IDs
        ItemStack iconStack;
        try {
            iconStack = new ItemStack(config.iconItem(), 1);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().at(Level.WARNING).log(
                    "Invalid reminder icon item '%s', using default '%s'",
                    config.iconItem(), DEFAULT_ICON_ITEM);
            iconStack = new ItemStack(DEFAULT_ICON_ITEM, 1);
        }

        PacketHandler packetHandler = playerRef.getPacketHandler();
        NotificationUtil.sendNotification(packetHandler, title, description, iconStack.toPacket());

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log("Sent vote reminder notification to player %s", username);
        }
    }

    /**
     * Plays a sound effect reminder to the player.
     *
     * @param plugin    the plugin instance
     * @param playerRef the player reference to play the sound to
     * @param username  the player's display name (for logging)
     * @param config    the sound configuration
     */
    private static void sendSound(HytaleVotifierPlugin plugin, PlayerRef playerRef, String username, VoteReminderSoundConfig config) {
        if (config == null || !config.enabled()) {
            return;
        }

        try {
            int soundIndex = SoundEvent.getAssetMap().getIndex(config.sound());
            SoundCategory category = parseSoundCategory(config.soundCategory());

            SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, category);

            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log("Played vote reminder sound '%s' to player %s", config.sound(), username);
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log(
                    "Failed to play vote reminder sound '%s' to player %s: %s",
                    config.sound(), username, e.getMessage());
            if (plugin.getConfig().debug()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Parses a sound category string to the corresponding enum value.
     *
     * @param category the category string (e.g., "UI", "SFX", "MUSIC")
     * @return the SoundCategory enum value, defaulting to UI if not recognized
     */
    private static SoundCategory parseSoundCategory(String category) {
        if (category == null) {
            return SoundCategory.UI;
        }
        try {
            return SoundCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SoundCategory.UI;
        }
    }
}
