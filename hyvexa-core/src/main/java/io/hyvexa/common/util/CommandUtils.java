package io.hyvexa.common.util;

import com.hypixel.hytale.server.core.command.system.CommandContext;

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
        if (tokens.length == 0) {
            return tokens;
        }
        String first = tokens[0];
        if (first.startsWith("/")) {
            first = first.substring(1);
        }
        String commandName = ctx.getCalledCommand().getName();
        if (first.equalsIgnoreCase(commandName)) {
            if (tokens.length == 1) {
                return new String[0];
            }
            String[] trimmed = new String[tokens.length - 1];
            System.arraycopy(tokens, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return tokens;
    }

    /**
     * Parse command arguments from a CommandContext, stripping the command name.
     *
     * @param ctx The command context
     * @return Array of arguments (empty if none)
     */
    public static String[] getArgs(CommandContext ctx) {
        return tokenize(ctx);
    }

}
