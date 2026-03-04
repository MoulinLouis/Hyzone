package org.hyvote.plugins.votifier.reminder;

import org.hyvote.plugins.votifier.storage.VoteStorage;

import java.util.Optional;

/**
 * Tracks which players have voted recently.
 *
 * <p>This class delegates to a {@link VoteStorage} implementation for actual
 * storage operations. The storage backend can be in-memory, SQLite, or any
 * other implementation of the VoteStorage interface.</p>
 */
public final class VoteTracker {

    private final VoteStorage storage;

    /**
     * Creates a new VoteTracker with the specified storage backend.
     *
     * @param storage the storage implementation to use
     */
    public VoteTracker(VoteStorage storage) {
        this.storage = storage;
    }

    /**
     * Records a vote for a player.
     *
     * @param username the player's username (case-insensitive)
     */
    public void recordVote(String username) {
        storage.recordVote(username);
    }

    /**
     * Records a vote for a player with a specific timestamp.
     *
     * @param username  the player's username (case-insensitive)
     * @param timestamp the vote timestamp in epoch milliseconds
     */
    public void recordVote(String username, long timestamp) {
        storage.recordVote(username, timestamp);
    }

    /**
     * Checks if a player has voted within the specified expiry period.
     *
     * @param username        the player's username (case-insensitive)
     * @param voteExpiryInterval how many hours before a vote is considered "expired"
     * @return true if the player has voted within the expiry period
     */
    public boolean hasVotedRecently(String username, int voteExpiryInterval) {
        return storage.hasVotedRecently(username, voteExpiryInterval);
    }

    /**
     * Gets the timestamp of a player's last vote.
     *
     * @param username the player's username (case-insensitive)
     * @return an Optional containing the last vote timestamp, or empty if no vote recorded
     */
    public Optional<Long> getLastVoteTimestamp(String username) {
        return storage.getLastVoteTimestamp(username);
    }

    /**
     * Removes expired vote records to prevent storage bloat.
     *
     * @param voteExpiryInterval how many hours before a vote is considered "expired"
     * @return the number of expired records removed
     */
    public int cleanupExpiredVotes(int voteExpiryInterval) {
        return storage.cleanupExpiredVotes(voteExpiryInterval);
    }

    /**
     * Returns the storage backend used by this tracker.
     *
     * @return the storage implementation
     */
    public VoteStorage getStorage() {
        return storage;
    }

    /**
     * Returns the type of storage backend in use.
     *
     * @return the storage type (e.g., "memory", "sqlite")
     */
    public String getStorageType() {
        return storage.getType();
    }
}
