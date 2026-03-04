package org.hyvote.plugins.votifier.util;

import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.RewardCommand;
import org.hyvote.plugins.votifier.vote.Vote;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Utility class for executing reward commands when votes are received.
 */
public final class RewardCommandUtil {

    /**
     * Pattern for valid usernames: alphanumeric characters and underscores only.
     * This prevents command injection via malicious usernames.
     */
    private static final Pattern SAFE_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{1,32}$");

    private RewardCommandUtil() {
        // Utility class
    }

    /**
     * Validates that a username is safe for use in command substitution.
     *
     * @param username the username to validate
     * @return true if the username contains only safe characters
     */
    public static boolean isValidUsername(String username) {
        return username != null && SAFE_USERNAME_PATTERN.matcher(username).matches();
    }

    /**
     * Executes configured reward commands for a received vote.
     *
     * <p>Each command is executed with its configured probability (chance).
     * Command strings support placeholder substitution:</p>
     * <ul>
     *   <li>{@code {username}} - The username of the player who voted</li>
     *   <li>{@code {from}} - The name of the voting site</li>
     * </ul>
     *
     * <p><strong>Security:</strong> Usernames are validated before command execution to prevent
     * command injection attacks. Usernames must be alphanumeric/underscore (1-32 chars).
     * Votes with invalid usernames are rejected and logged.</p>
     *
     * @param plugin the plugin instance for config and logging
     * @param vote   the vote that triggered the reward commands
     */
    public static void executeRewardCommands(HytaleVotifierPlugin plugin, Vote vote) {
        List<RewardCommand> commands = plugin.getConfig().rewardCommands();
        if (commands == null || commands.isEmpty()) {
            return;
        }

        // Validate username to prevent command injection
        if (!isValidUsername(vote.username())) {
            plugin.getLogger().at(Level.WARNING).log(
                    "Skipping reward commands: invalid username '%s' (must be alphanumeric/underscore, 1-32 chars)",
                    vote.username());
            return;
        }

        for (RewardCommand rewardCommand : commands) {
            // Skip disabled commands
            if (!rewardCommand.enabled()) {
                continue;
            }

            // Check probability
            if (rewardCommand.chance() < 1.0) {
                double roll = ThreadLocalRandom.current().nextDouble();
                if (roll > rewardCommand.chance()) {
                    if (plugin.getConfig().debug()) {
                        plugin.getLogger().at(Level.INFO).log(
                                "Skipping reward command (roll %.3f > chance %.3f): %s",
                                roll, rewardCommand.chance(), rewardCommand.command());
                    }
                    continue;
                }
            }

            // Apply placeholder substitutions (single-pass to avoid issues if values contain placeholder syntax)
            String command = PlaceholderUtil.replaceVotePlaceholders(rewardCommand.command(), vote);

            // Execute command via server console
            try {
                CommandManager.get().handleCommand(ConsoleSender.INSTANCE, command);
                if (plugin.getConfig().debug()) {
                    plugin.getLogger().at(Level.INFO).log("Executed reward command: %s", command);
                }
            } catch (Exception e) {
                plugin.getLogger().at(Level.WARNING).log(
                        "Failed to execute reward command '%s': %s", command, e.getMessage());
            }
        }
    }
}
