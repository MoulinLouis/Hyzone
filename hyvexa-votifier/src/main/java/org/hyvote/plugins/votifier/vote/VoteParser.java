package org.hyvote.plugins.votifier.vote;

import java.nio.charset.StandardCharsets;

/**
 * Utility class for parsing decrypted vote data into Vote records.
 *
 * <p>Parses the standard Votifier protocol format:</p>
 * <pre>
 * VOTE
 * serviceName
 * username
 * address
 * timestamp
 * </pre>
 *
 * <p>The first line must be "VOTE" (case-insensitive). The timestamp field
 * is parsed as a long; if parsing fails, the current system time is used
 * as a fallback.</p>
 */
public final class VoteParser {

    /**
     * Expected header line for Votifier protocol.
     */
    private static final String VOTE_HEADER = "VOTE";

    /**
     * Minimum number of lines required in a valid vote message.
     */
    private static final int MIN_LINES = 5;

    private VoteParser() {
        // Utility class - prevent instantiation
    }

    /**
     * Parses decrypted vote data into a Vote record.
     *
     * <p>Expects newline-delimited fields in Votifier protocol format.
     * The first line must be "VOTE", followed by serviceName, username,
     * address, and timestamp.</p>
     *
     * @param decryptedData the decrypted bytes from the vote payload
     * @return a Vote record containing the parsed vote data
     * @throws VoteParseException if the data format is invalid
     */
    public static Vote parse(byte[] decryptedData) throws VoteParseException {
        if (decryptedData == null || decryptedData.length == 0) {
            throw new VoteParseException("Vote data is null or empty");
        }

        String data = new String(decryptedData, StandardCharsets.UTF_8);
        String[] lines = data.split("\n");

        // Validate minimum line count
        if (lines.length < MIN_LINES) {
            throw new VoteParseException(String.format(
                    "Invalid vote format: expected at least %d lines, got %d",
                    MIN_LINES, lines.length));
        }

        // Validate VOTE header
        String header = lines[0].trim();
        if (!VOTE_HEADER.equalsIgnoreCase(header)) {
            throw new VoteParseException(String.format(
                    "Invalid vote header: expected 'VOTE', got '%s'", header));
        }

        // Extract fields
        String serviceName = lines[1].trim();
        String username = lines[2].trim();
        String address = lines[3].trim();
        String timestampStr = lines[4].trim();

        // Parse timestamp with fallback
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            // Fallback to current time if timestamp is invalid
            timestamp = System.currentTimeMillis();
        }

        try {
            return new Vote(serviceName, username, address, timestamp);
        } catch (IllegalArgumentException e) {
            throw new VoteParseException("Invalid vote data: " + e.getMessage(), e);
        }
    }
}
