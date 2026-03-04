package org.hyvote.plugins.votifier.util;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import io.github.insideranh.talemessage.TaleMessage;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.VoteMessageConfig;
import org.hyvote.plugins.votifier.vote.Vote;

import java.util.logging.Level;

/**
 * Utility class for displaying vote notification toasts to players.
 */
public final class VoteNotificationUtil {

    private static final String DEFAULT_ICON_ITEM = "Ore_Gold";

    private VoteNotificationUtil() {
        // Utility class
    }

    /**
     * Displays a vote toast notification to the player who voted, if enabled in config.
     *
     * @param plugin the plugin instance for config and logging
     * @param vote   the vote to display a toast for
     */
    public static void displayVoteToast(HytaleVotifierPlugin plugin, Vote vote) {
        VoteMessageConfig messageConfig = plugin.getConfig().voteMessage();
        if (!messageConfig.enabled()) {
            return;
        }

        // Find the player by username
        PlayerRef playerRef = Universe.get().getPlayerByUsername(vote.username(), NameMatching.EXACT_IGNORE_CASE);
        if (playerRef == null) {
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log("Player %s not online, skipping vote toast", vote.username());
            }
            return;
        }

        // Replace placeholders in messages (single-pass to avoid issues if values contain placeholder syntax)
        String titleText = PlaceholderUtil.replaceVotePlaceholders(messageConfig.titleMessage(), vote);
        String descriptionText = PlaceholderUtil.replaceVotePlaceholders(messageConfig.descriptionMessage(), vote);

        // Parse messages with TaleMessage formatting
        Message title = TaleMessage.parse(titleText);
        Message description = TaleMessage.parse(descriptionText);

        // Create the icon item with fallback for invalid item IDs
        ItemStack iconStack;
        try {
            iconStack = new ItemStack(messageConfig.iconItem(), 1);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().at(Level.WARNING).log(
                    "Invalid icon item '%s', using default '%s'",
                    messageConfig.iconItem(), DEFAULT_ICON_ITEM);
            iconStack = new ItemStack(DEFAULT_ICON_ITEM, 1);
        }

        // Get packet handler and send notification
        PacketHandler packetHandler = playerRef.getPacketHandler();
        NotificationUtil.sendNotification(packetHandler, title, description, iconStack.toPacket());

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log("Displayed vote toast to player %s", vote.username());
        }
    }
}
