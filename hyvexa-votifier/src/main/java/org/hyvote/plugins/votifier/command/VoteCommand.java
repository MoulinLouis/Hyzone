package org.hyvote.plugins.votifier.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import io.github.insideranh.talemessage.TaleMessage;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.VoteCommandConfig;
import org.hyvote.plugins.votifier.VoteSite;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-game command for displaying voting site links to players.
 *
 * <p>Usage: /vote</p>
 *
 * <p>Displays a configurable list of voting sites with clickable links,
 * allowing players to easily find where to vote for the server.</p>
 */
public class VoteCommand extends AbstractCommand {

    /** Pattern to match placeholders like {name} and {link} */
    private static final Pattern SITE_PLACEHOLDER_PATTERN = Pattern.compile("\\{(name|link)}");

    private final HytaleVotifierPlugin plugin;

    /**
     * Creates a new VoteCommand.
     *
     * @param plugin the HytaleVotifier plugin instance
     */
    public VoteCommand(HytaleVotifierPlugin plugin) {
        super("vote", "View links to vote for the server");
        this.setPermissionGroup(GameMode.Adventure);
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext context) {
        VoteCommandConfig config = plugin.getConfig().voteCommand();

        if (!config.enabled()) {
            context.sendMessage(TaleMessage.parse("<gray>The vote command is not enabled on this server.</gray>"));
            return CompletableFuture.completedFuture(null);
        }

        // Send header
        if (config.header() != null && !config.header().isEmpty()) {
            context.sendMessage(TaleMessage.parse(config.header()));
        }

        // Send each voting site
        List<VoteSite> sites = config.sites();
        String siteTemplate = config.siteTemplate();
        if (sites != null && !sites.isEmpty() && siteTemplate != null && !siteTemplate.isEmpty()) {
            for (VoteSite site : sites) {
                String message = replaceSitePlaceholders(siteTemplate, site.name(), site.url());
                context.sendMessage(TaleMessage.parse(message));
            }
        }

        // Send footer
        if (config.footer() != null && !config.footer().isEmpty()) {
            context.sendMessage(TaleMessage.parse(config.footer()));
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Replaces site placeholders in a template string.
     *
     * @param template the template string with {name} and {link} placeholders
     * @param name     the site name
     * @param link     the site URL
     * @return the template with placeholders replaced
     */
    private String replaceSitePlaceholders(String template, String name, String link) {
        Map<String, String> replacements = Map.of("name", name, "link", link);
        Matcher matcher = SITE_PLACEHOLDER_PATTERN.matcher(template);
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
