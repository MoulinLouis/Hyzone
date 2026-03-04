package org.hyvote.plugins.votifier.vote;

/**
 * Immutable record representing a vote notification from a voting site.
 *
 * <p>Contains the standard Votifier protocol fields transmitted by voting sites.
 * The vote data is originally encrypted with the server's public RSA key,
 * decrypted by the server, and parsed into this record structure.</p>
 *
 * <p>Protocol format (newline-delimited):</p>
 * <pre>
 * VOTE
 * serviceName
 * username
 * address
 * timestamp
 * </pre>
 *
 * @param serviceName the identifier of the voting site (e.g., "Hyvote")
 * @param username the in-game username of the player who voted
 * @param address the IP address of the voter as reported by the voting site
 * @param timestamp the epoch milliseconds when the vote was cast (or received if unavailable)
 */
public record Vote(
        String serviceName,
        String username,
        String address,
        long timestamp
) {

    /**
     * Creates a Vote record with validated parameters.
     *
     * @param serviceName the voting site identifier
     * @param username the player username
     * @param address the voter's IP address
     * @param timestamp the vote timestamp in epoch milliseconds
     * @throws IllegalArgumentException if serviceName or username is null or empty
     */
    public Vote {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName cannot be null or empty");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username cannot be null or empty");
        }
        if (address == null) {
            address = "";
        }
    }
}
