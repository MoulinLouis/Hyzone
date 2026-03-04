package org.hyvote.plugins.votifier.reminder;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.VoteReminderConfig;
import org.hyvote.plugins.votifier.util.VoteReminderUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Service that manages vote reminders for players.
 *
 * <p>Handles scheduling reminders when players join if they haven't voted recently,
 * and canceling reminders when players vote or leave.</p>
 *
 * <p>Also runs a periodic cleanup task to remove expired vote records from storage.</p>
 */
public final class VoteReminderService {

    /**
     * Default interval between database cleanup runs (in hours).
     * Used when no cleanup interval is configured.
     */
    private static final int DEFAULT_CLEANUP_INTERVAL_HOURS = 6;

    private final HytaleVotifierPlugin plugin;
    private final VoteTracker voteTracker;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledReminders = new ConcurrentHashMap<>();

    /**
     * Creates a new VoteReminderService.
     *
     * @param plugin      the plugin instance
     * @param voteTracker the vote tracker to check if players have voted
     */
    public VoteReminderService(HytaleVotifierPlugin plugin, VoteTracker voteTracker) {
        this.plugin = plugin;
        this.voteTracker = voteTracker;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "VoteReminderScheduler");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic cleanup of expired vote records
        scheduleCleanupTask();
    }

    /**
     * Schedules a periodic task to clean up expired vote records from storage.
     *
     * <p>This keeps the database file size reasonable by removing records
     * for players who voted more than voteExpiryInterval ago.</p>
     *
     * <p>Cleanup runs immediately on startup, then at the configured interval.</p>
     */
    private void scheduleCleanupTask() {
        int cleanupIntervalHours = getCleanupIntervalHours();

        // Run cleanup immediately on startup to handle servers that restart frequently
        scheduler.execute(this::runCleanup);

        // Schedule periodic cleanup
        scheduler.scheduleAtFixedRate(
                this::runCleanup,
                cleanupIntervalHours,
                cleanupIntervalHours,
                TimeUnit.HOURS
        );

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log(
                    "Scheduled vote storage cleanup task to run every %d hours (also running on startup)", cleanupIntervalHours);
        }
    }

    /**
     * Gets the cleanup interval from config, or returns the default.
     *
     * @return the cleanup interval in hours
     */
    private int getCleanupIntervalHours() {
        VoteReminderConfig config = plugin.getConfig().voteReminder();
        if (config != null && config.storage() != null && config.storage().cleanupIntervalHours() != null) {
            return config.storage().cleanupIntervalHours();
        }
        return DEFAULT_CLEANUP_INTERVAL_HOURS;
    }

    /**
     * Runs the cleanup task to remove expired vote records.
     */
    private void runCleanup() {
        try {
            VoteReminderConfig config = plugin.getConfig().voteReminder();
            int voteExpiryInterval = config != null && config.voteExpiryInterval() != null
                    ? config.voteExpiryInterval()
                    : 24;

            int removed = voteTracker.cleanupExpiredVotes(voteExpiryInterval);

            if (removed > 0 || plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log(
                        "Vote storage cleanup: removed %d expired record(s) older than %d hours",
                        removed, voteExpiryInterval);
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log(
                    "Error during vote storage cleanup: %s", e.getMessage());
        }
    }

    /**
     * Handles a player joining the server.
     *
     * <p>If the player hasn't voted recently and reminders are enabled,
     * schedules a reminder to be sent after the configured delay.</p>
     *
     * @param player the player who joined
     */
    public void onPlayerJoin(Player player) {
        VoteReminderConfig config = plugin.getConfig().voteReminder();
        if (config == null) {
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log(
                        "Vote reminder config is null for player %s", player.getDisplayName());
            }
            return;
        }

        if (!config.enabled()) {
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log(
                        "Vote reminders disabled, skipping for player %s", player.getDisplayName());
            }
            return;
        }

        if (!Boolean.TRUE.equals(config.sendOnJoin())) {
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log(
                        "sendOnJoin is false, skipping reminder for player %s", player.getDisplayName());
            }
            return;
        }

        String username = player.getDisplayName();
        int voteExpiryInterval = config.voteExpiryInterval() != null ? config.voteExpiryInterval() : 24;

        // Check if player has voted recently
        if (voteTracker.hasVotedRecently(username, voteExpiryInterval)) {
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log(
                        "Player %s has voted recently, skipping reminder", username);
            }
            return;
        }

        // Schedule reminder after delay
        int delaySeconds = config.delayInSeconds() != null ? config.delayInSeconds() : 60;
        String playerKey = username.toLowerCase();

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log(
                    "Scheduling vote reminder for player %s in %d second(s)", username, delaySeconds);
        }

        ScheduledFuture<?> future = scheduler.schedule(
                () -> {
                    try {
                        sendReminderToPlayer(playerKey, username);
                    } catch (Exception e) {
                        plugin.getLogger().at(Level.WARNING).log(
                                "Error sending vote reminder to %s: %s", username, e.getMessage());
                        if (plugin.getConfig().debug()) {
                            e.printStackTrace();
                        }
                    }
                },
                delaySeconds,
                TimeUnit.SECONDS
        );

        // Store the scheduled task so we can cancel it if needed
        scheduledReminders.put(playerKey, future);
    }

    /**
     * Handles a player leaving the server.
     *
     * <p>Cancels any pending reminder for the player.</p>
     *
     * @param username the display name of the player who left
     */
    public void onPlayerLeave(String username) {
        cancelReminder(username.toLowerCase());
    }

    /**
     * Records a vote for a player and cancels any pending reminder.
     *
     * @param username the player's username
     */
    public void recordVote(String username) {
        voteTracker.recordVote(username);

        // Cancel any pending reminder for this player
        cancelReminder(username.toLowerCase());

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log("Recorded vote for player %s", username);
        }
    }

    /**
     * Cancels a pending reminder for a player.
     *
     * @param playerKey the lowercase username of the player
     */
    private void cancelReminder(String playerKey) {
        ScheduledFuture<?> future = scheduledReminders.remove(playerKey);
        if (future != null) {
            future.cancel(false);
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log("Cancelled pending vote reminder for player %s", playerKey);
            }
        }
    }

    /**
     * Sends a reminder to a player if they are still online.
     *
     * @param playerKey the lowercase username key
     * @param username  the player's username (for logging and lookup)
     */
    private void sendReminderToPlayer(String playerKey, String username) {
        // Remove from scheduled map
        scheduledReminders.remove(playerKey);

        // Find the player (they might have logged off)
        PlayerRef playerRef = Universe.get().getPlayerByUsername(username, NameMatching.EXACT_IGNORE_CASE);
        if (playerRef == null) {
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log(
                        "Player %s is no longer online, skipping vote reminder", username);
            }
            return;
        }

        // Double-check they haven't voted in the meantime
        VoteReminderConfig config = plugin.getConfig().voteReminder();
        int voteExpiryInterval = config != null && config.voteExpiryInterval() != null ? config.voteExpiryInterval() : 24;
        if (voteTracker.hasVotedRecently(username, voteExpiryInterval)) {
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log(
                        "Player %s voted while waiting, skipping reminder", username);
            }
            return;
        }

        // Send the reminders using the PlayerRef
        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log(
                    "Sending vote reminders to player %s (playerRef: %s)", username, playerRef);
        }
        VoteReminderUtil.sendReminders(plugin, playerRef, username);
    }

    /**
     * Shuts down the reminder service and cancels all pending reminders.
     */
    public void shutdown() {
        scheduler.shutdown();
        scheduledReminders.clear();

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Gets the vote tracker used by this service.
     *
     * @return the vote tracker
     */
    public VoteTracker getVoteTracker() {
        return voteTracker;
    }
}
