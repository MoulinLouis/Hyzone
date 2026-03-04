package org.hyvote.plugins.votifier.command;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.event.VoteEvent;
import org.hyvote.plugins.votifier.util.BroadcastUtil;
import org.hyvote.plugins.votifier.util.RewardCommandUtil;
import org.hyvote.plugins.votifier.util.VoteNotificationUtil;
import org.hyvote.plugins.votifier.vote.Vote;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * In-game command for testing vote event firing.
 *
 * <p>Usage: /testvote &lt;username&gt; [service]</p>
 *
 * <p>Allows admins with the votifier.admin.testvote permission to fire test VoteEvents
 * from within the game, useful for testing vote reward plugins without requiring
 * actual vote site integration.</p>
 *
 * @see VoteEvent
 * @see Vote
 */
public class TestVoteCommand extends AbstractCommand {

    private final HytaleVotifierPlugin plugin;
    private final RequiredArg<String> usernameArg;
    private final DefaultArg<String> serviceArg;

    /**
     * Creates a new TestVoteCommand.
     *
     * @param plugin the HytaleVotifier plugin instance
     */
    public TestVoteCommand(HytaleVotifierPlugin plugin) {
        super("testvote", "Fire a test vote event for a player");
        this.plugin = plugin;

        // Require permission for access
        requirePermission("votifier.admin.testvote");

        // Define command arguments
        this.usernameArg = withRequiredArg("username", "votifier.admin.testvote.username.desc", ArgTypes.STRING);
        this.serviceArg = withDefaultArg("service", "votifier.admin.testvote.service.desc", ArgTypes.STRING, "TestService", "TestService");
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext context) {
        // Get command arguments
        String username = context.get(usernameArg);
        String serviceName = context.get(serviceArg);

        // Determine sender address
        String address = context.isPlayer() ? "in-game" : "console";

        // Create Vote record
        Vote vote = new Vote(serviceName, username, address, System.currentTimeMillis());

        // Fire VoteEvent
        VoteEvent voteEvent = new VoteEvent(plugin, vote);
        HytaleServer.get().getEventBus().dispatchFor(VoteEvent.class, plugin.getClass()).dispatch(voteEvent);

        // Display toast notification to the player if enabled
        VoteNotificationUtil.displayVoteToast(plugin, vote);

        // Broadcast vote announcement to all online players if enabled
        BroadcastUtil.broadcastVote(plugin, vote);

        // Execute reward commands
        RewardCommandUtil.executeRewardCommands(plugin, vote);

        // Send feedback to command sender
        context.sendMessage(Message.raw("Test vote fired for " + username + " from " + serviceName));

        // Log if debug enabled
        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log("Test vote command: fired VoteEvent for %s from service %s", username, serviceName);
        }

        return CompletableFuture.completedFuture(null);
    }
}
