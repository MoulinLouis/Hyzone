package org.hyvote.plugins.votifier;

/**
 * Configuration for a reward command to be executed when a vote is received.
 *
 * <p>Commands support placeholder substitution:</p>
 * <ul>
 *   <li>{@code {username}} - The username of the player who voted</li>
 *   <li>{@code {from}} - The name of the voting site</li>
 * </ul>
 *
 * <p>Example configuration:</p>
 * <pre>
 * {
 *   "enabled": true,
 *   "command": "give {username} diamonds 5",
 *   "chance": 1.0
 * }
 * </pre>
 *
 * @param enabled Whether this reward command is active. Set to false to disable without removing.
 * @param command The command string to execute (without leading slash). Supports {username} and {from} placeholders.
 * @param chance  Probability of executing this command (0.0 to 1.0). A value of 1.0 means always execute.
 */
public record RewardCommand(boolean enabled, String command, double chance) {

    /**
     * Validates that the command is not null/empty and chance is within valid range.
     */
    public RewardCommand {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command cannot be null or empty");
        }
        if (chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("chance must be between 0.0 and 1.0");
        }
    }
}
