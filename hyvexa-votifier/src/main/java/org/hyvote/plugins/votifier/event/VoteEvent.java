package org.hyvote.plugins.votifier.event;

import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.event.PluginEvent;
import org.hyvote.plugins.votifier.vote.Vote;

/**
 * Event fired when a valid vote is received from a voting site.
 *
 * <p>This event fires regardless of whether the voted player is currently online.
 * Listening plugins should handle both online and offline scenarios appropriately
 * (e.g., storing rewards for later delivery).</p>
 *
 * <p>Usage example for listening plugins:</p>
 * <pre>{@code
 * plugin.getEventRegistry().register(VoteEvent.class, event -> {
 *     Vote vote = event.getVote();
 *     String username = vote.username();
 *     // Handle vote reward...
 * });
 * }</pre>
 *
 * @see Vote
 */
public class VoteEvent extends PluginEvent {

    private final Vote vote;

    /**
     * Creates a new VoteEvent with the specified vote data.
     *
     * @param plugin the plugin firing this event
     * @param vote the vote data received from the voting site
     */
    public VoteEvent(PluginBase plugin, Vote vote) {
        super(plugin);
        this.vote = vote;
    }

    /**
     * Returns the vote data associated with this event.
     *
     * @return the vote record containing all vote details
     */
    public Vote getVote() {
        return vote;
    }

    /**
     * Convenience method to get the voting service name.
     *
     * @return the identifier of the voting site (e.g., "TopHytaleSites")
     */
    public String getServiceName() {
        return vote.serviceName();
    }

    /**
     * Convenience method to get the username of the voter.
     *
     * @return the in-game username of the player who voted
     */
    public String getUsername() {
        return vote.username();
    }

    /**
     * Convenience method to get the voter's IP address.
     *
     * @return the IP address of the voter as reported by the voting site
     */
    public String getAddress() {
        return vote.address();
    }

    /**
     * Convenience method to get the vote timestamp.
     *
     * @return the epoch milliseconds when the vote was cast
     */
    public long getTimestamp() {
        return vote.timestamp();
    }

    @Override
    public String toString() {
        return String.format("VoteEvent{service=%s, username=%s, address=%s, timestamp=%d}",
                getServiceName(), getUsername(), getAddress(), getTimestamp());
    }
}
