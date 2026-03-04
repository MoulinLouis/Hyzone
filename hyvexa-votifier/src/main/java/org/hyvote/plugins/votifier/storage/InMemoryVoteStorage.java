package org.hyvote.plugins.votifier.storage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link VoteStorage}.
 *
 * <p>This implementation stores vote timestamps in a thread-safe map that is
 * cleared when the server restarts. Useful for testing or when persistence
 * is not required.</p>
 */
public class InMemoryVoteStorage implements VoteStorage {

    private final Map<String, Long> lastVoteTimestamps = new ConcurrentHashMap<>();

    @Override
    public void recordVote(String username, long timestamp) {
        lastVoteTimestamps.put(username.toLowerCase(), timestamp);
    }

    @Override
    public Optional<Long> getLastVoteTimestamp(String username) {
        return Optional.ofNullable(lastVoteTimestamps.get(username.toLowerCase()));
    }

    @Override
    public int cleanupExpiredVotes(int voteExpiryInterval) {
        long expiryMillis = voteExpiryInterval * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();
        int removed = 0;

        var iterator = lastVoteTimestamps.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if ((now - entry.getValue()) >= expiryMillis) {
                iterator.remove();
                removed++;
            }
        }

        return removed;
    }

    @Override
    public void initialize() {
        // No initialization needed for in-memory storage
    }

    @Override
    public void shutdown() {
        lastVoteTimestamps.clear();
    }

    @Override
    public String getType() {
        return "memory";
    }

    /**
     * Returns the number of players with tracked votes.
     *
     * @return the count of tracked players
     */
    public int size() {
        return lastVoteTimestamps.size();
    }
}
