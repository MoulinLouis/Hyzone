package io.hyvexa.common.util;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.Arrays;

/**
 * Utility methods for command argument parsing.
 */
public final class CommandUtils {

    private CommandUtils() {
    }

    /**
     * Tokenize command input and strip the called command name.
     *
     * @param ctx The command context
     * @return Array of arguments (empty if none)
     */
    public static String[] tokenize(CommandContext ctx) {
        String input = ctx.getInputString();
        if (input == null || input.trim().isEmpty()) {
            return new String[0];
        }
        String[] tokens = input.trim().split("\\s+");
        String first = tokens[0];
        if (first.startsWith("/")) {
            first = first.substring(1);
        }
        String commandName = ctx.getCalledCommand().getName();
        if (first.equalsIgnoreCase(commandName)) {
            if (tokens.length == 1) {
                return new String[0];
            }
            return Arrays.copyOfRange(tokens, 1, tokens.length);
        }
        return tokens;
    }

    /**
     * Find an online player by name (case-insensitive).
     *
     * @param name The player name to search for
     * @return The matching PlayerRef, or null if not found
     */
    public static PlayerRef findPlayerByName(String name) {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef != null && name.equalsIgnoreCase(playerRef.getUsername())) {
                return playerRef;
            }
        }
        return null;
    }

}
