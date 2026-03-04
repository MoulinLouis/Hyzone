package org.hyvote.plugins.votifier;

import java.util.List;

/**
 * Configuration for the /vote command that displays voting site links to players.
 *
 * <p>Supports TaleMessage formatting tags for styled text. The siteTemplate supports placeholders:</p>
 * <ul>
 *   <li>{@code {name}} - The name of the voting site</li>
 *   <li>{@code {link}} - The URL of the voting site</li>
 * </ul>
 *
 * @param enabled      Whether the /vote command is enabled (default false)
 * @param header       Header message displayed before the site list
 * @param siteTemplate Template for each voting site entry with {name} and {link} placeholders
 * @param footer       Footer message displayed after the site list
 * @param sites        List of voting sites (order is preserved)
 */
public record VoteCommandConfig(
        boolean enabled,
        String header,
        String siteTemplate,
        String footer,
        List<VoteSite> sites
) {

    /**
     * Returns a VoteCommandConfig with default values.
     *
     * @return default vote command configuration
     */
    public static VoteCommandConfig defaults() {
        return new VoteCommandConfig(
                false,
                "<red>================<orange> Vote Now </orange>================</red>",
                "<orange><click:{link}>~{name}~</click></orange>",
                "<red>==============<orange> Earn Rewards </orange>==============</red>",
                List.of(new VoteSite("Hyvote.org", "https://hyvote.org/blog/posts/how-to-add-your-server-to-hyvote-with-hyquery"))
        );
    }

    /**
     * Merges this config with defaults, using default values for any null fields.
     *
     * @param defaults the default configuration to fall back to for null fields
     * @return a new VoteCommandConfig with null fields replaced by defaults
     */
    public VoteCommandConfig merge(VoteCommandConfig defaults) {
        return new VoteCommandConfig(
                this.enabled,
                this.header != null ? this.header : defaults.header(),
                this.siteTemplate != null ? this.siteTemplate : defaults.siteTemplate(),
                this.footer != null ? this.footer : defaults.footer(),
                this.sites != null ? this.sites : defaults.sites()
        );
    }
}
