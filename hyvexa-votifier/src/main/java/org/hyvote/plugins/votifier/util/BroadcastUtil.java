package org.hyvote.plugins.votifier.util;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import io.github.insideranh.talemessage.TaleMessage;
import org.hyvote.plugins.votifier.BroadcastConfig;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.vote.Vote;

import java.util.List;
import java.util.logging.Level;

/**
 * Utility class for broadcasting vote announcements to all online players.
 */
public final class BroadcastUtil {

    private BroadcastUtil() {
        // Utility class
    }

    /**
     * Broadcasts a vote announcement to all online players, if enabled in config.
     *
     * @param plugin the plugin instance for config and logging
     * @param vote   the vote to broadcast
     */
    public static void broadcastVote(HytaleVotifierPlugin plugin, Vote vote) {
        BroadcastConfig broadcastConfig = plugin.getConfig().broadcast();
        if (!broadcastConfig.enabled()) {
            return;
        }

        // Get all online players
        List<PlayerRef> onlinePlayers = Universe.get().getPlayers();
        if (onlinePlayers.isEmpty()) {
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log("No players online, skipping vote broadcast");
            }
            return;
        }

        // Replace placeholders in message (single-pass to avoid issues if values contain placeholder syntax)
        String messageText = PlaceholderUtil.replaceVotePlaceholders(broadcastConfig.message(), vote);

        // Parse message with TaleMessage formatting
        Message message = TaleMessage.parse(messageText);

        // Broadcast to all online players
        for (PlayerRef player : onlinePlayers) {
            player.sendMessage(message);
        }

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log("Broadcasted vote announcement to %d players", onlinePlayers.size());
        }
    }
}
