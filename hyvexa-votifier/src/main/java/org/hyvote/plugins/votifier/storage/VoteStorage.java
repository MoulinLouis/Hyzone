package org.hyvote.plugins.votifier.storage;

import java.util.Optional;

/**
 * Core interface for persistent vote storage.
 *
 * <p>Implementations of this interface provide different storage backends
 * (e.g., in-memory, SQLite, MySQL, etc.) for tracking when players last voted.</p>
 *
 * <p>All implementations must be thread-safe as votes can be recorded from
 * multiple sources (HTTP, socket) concurrently.</p>
 */
public interface VoteStorage {

    /**
     * Records a vote for a player with the current timestamp.
     *
     * @param username the player's username (case-insensitive)
     */
    default void recordVote(String username) {
        recordVote(username, System.currentTimeMillis());
    }

    /**
     * Records a vote for a player with a specific timestamp.
     *
     * @param username  the player's username (case-insensitive)
     * @param timestamp the vote timestamp in epoch milliseconds
     */
    void recordVote(String username, long timestamp);

    /**
     * Gets the timestamp of a player's last vote.
     *
     * @param username the player's username (case-insensitive)
     * @return an Optional containing the last vote timestamp, or empty if no vote recorded
     */
    Optional<Long> getLastVoteTimestamp(String username);

    /**
     * Checks if a player has voted within the specified expiry period.
     *
     * @param username        the player's username (case-insensitive)
     * @param voteExpiryInterval how many hours before a vote is considered "expired"
     * @return true if the player has voted within the expiry period
     */
    default boolean hasVotedRecently(String username, int voteExpiryInterval) {
        Optional<Long> lastVote = getLastVoteTimestamp(username);
        if (lastVote.isEmpty()) {
            return false;
        }

        long expiryMillis = voteExpiryInterval * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();
        return (now - lastVote.get()) < expiryMillis;
    }

    /**
     * Removes expired vote records to prevent storage bloat.
     *
     * @param voteExpiryInterval how many hours before a vote is considered "expired"
     * @return the number of expired records removed
     */
    int cleanupExpiredVotes(int voteExpiryInterval);

    /**
     * Initializes the storage backend.
     *
     * <p>Called once when the plugin starts. Implementations should create
     * any necessary tables, files, or connections here.</p>
     *
     * @throws StorageException if initialization fails
     */
    void initialize() throws StorageException;

    /**
     * Shuts down the storage backend.
     *
     * <p>Called when the plugin is disabled. Implementations should close
     * any connections and release resources here.</p>
     */
    void shutdown();

    /**
     * Returns the type identifier for this storage backend.
     *
     * @return the storage type (e.g., "memory", "sqlite", "mysql")
     */
    String getType();
}
