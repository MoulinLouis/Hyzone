package org.hyvote.plugins.votifier;

/**
 * Represents a voting site with a name and URL.
 *
 * @param name the display name of the voting site
 * @param url  the URL where players can vote
 */
public record VoteSite(String name, String url) {
}
