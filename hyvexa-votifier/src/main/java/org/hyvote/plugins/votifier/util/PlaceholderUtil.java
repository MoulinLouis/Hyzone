package org.hyvote.plugins.votifier.util;

import org.hyvote.plugins.votifier.vote.Vote;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for placeholder substitution in message templates.
 *
 * <p>Supported placeholders:</p>
 * <ul>
 *   <li>{@code {username}} - The username of the player who voted</li>
 *   <li>{@code {from}} - The name of the voting site</li>
 * </ul>
 *
 * <p>Uses single-pass replacement to avoid issues where replacement values
 * might contain placeholder syntax (e.g., a username containing "{from}").</p>
 */
public final class PlaceholderUtil {

    /** Pattern to match placeholders like {username} and {from} */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(username|from)}");

    private PlaceholderUtil() {
        // Utility class
    }

    /**
     * Replaces vote placeholders in a template string.
     *
     * @param template the template string with {placeholder} syntax
     * @param vote     the vote to extract replacement values from
     * @return the template with all placeholders replaced
     */
    public static String replaceVotePlaceholders(String template, Vote vote) {
        return replacePlaceholders(template, Map.of(
                "username", vote.username(),
                "from", vote.serviceName()
        ));
    }

    /**
     * Replaces placeholders in a single pass to avoid issues where replacement values
     * might contain placeholder syntax (e.g., a username containing "{from}").
     *
     * @param template     the template string with {placeholder} syntax
     * @param replacements map of placeholder names to their replacement values
     * @return the template with all placeholders replaced
     */
    public static String replacePlaceholders(String template, Map<String, String> replacements) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = replacements.getOrDefault(placeholder, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
