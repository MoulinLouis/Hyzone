package org.hyvote.plugins.votifier.util;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import io.github.insideranh.talemessage.TaleMessage;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;

import java.util.logging.Level;

/**
 * Utility class for sending update notifications to players.
 */
public final class UpdateNotificationUtil {

    private static final String UPDATE_MESSAGE_FORMAT =
            "<gray><orange>[Votifier]</orange> A new update is available, click here to download: " +
            "<click:%s><blue>[CurseForge]</blue></click> " +
            "<click:%s><blue>[GitHub]</blue></click></gray>";

    private UpdateNotificationUtil() {
        // Utility class
    }

    /**
     * Sends an update notification to a player with a clickable link.
     * The link opens the GitHub releases page directly.
     *
     * @param plugin the plugin instance for logging
     * @param player the player to notify
     */
    public static void sendUpdateNotification(HytaleVotifierPlugin plugin, Player player) {
        String messageText = String.format(UPDATE_MESSAGE_FORMAT,
                UpdateChecker.getCurseForgeUrl(),
                UpdateChecker.getGitHubReleasesUrl());
        Message message = TaleMessage.parse(messageText);
        player.sendMessage(message);

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log(
                    "Sent update notification to player %s", player.getDisplayName());
        }
    }

    /**
     * Sends an update notification to the console/log.
     *
     * @param plugin        the plugin instance for logging
     * @param latestVersion the latest available version
     */
    public static void logUpdateAvailable(HytaleVotifierPlugin plugin, String latestVersion) {
        plugin.getLogger().at(Level.INFO).log(
                "[Votifier] A new update is available: v%s", latestVersion);
        plugin.getLogger().at(Level.INFO).log(
                "[Votifier] Download from CurseForge: %s", UpdateChecker.getCurseForgeUrl());
        plugin.getLogger().at(Level.INFO).log(
                "[Votifier] Download from GitHub: %s", UpdateChecker.getGitHubReleasesUrl());
    }
}
