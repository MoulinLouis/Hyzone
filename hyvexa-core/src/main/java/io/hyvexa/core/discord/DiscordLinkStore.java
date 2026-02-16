package io.hyvexa.core.discord;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.economy.GemStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages Discord-Minecraft account linking via shared MySQL database.
 * Singleton shared across all modules. Players generate a code in-game,
 * enter it on Discord, and receive a one-time gem reward on next login.
 */
public class DiscordLinkStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final DiscordLinkStore INSTANCE = new DiscordLinkStore();

    private static final int CODE_LENGTH = 6;
    private static final long CODE_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes
    private static final long GEM_REWARD = 100;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I/O/0/1 for clarity

    private final ConcurrentHashMap<UUID, DiscordLink> cache = new ConcurrentHashMap<>();

    private DiscordLinkStore() {
    }

    public static DiscordLinkStore getInstance() {
        return INSTANCE;
    }

    /**
     * Create the discord_link_codes and discord_links tables if they don't exist.
     * Also cleans up expired codes. Safe to call multiple times (idempotent).
     */
    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, DiscordLinkStore will not function");
            return;
        }

        String createCodes = "CREATE TABLE IF NOT EXISTS discord_link_codes ("
                + "code VARCHAR(7) NOT NULL PRIMARY KEY, "
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "expires_at TIMESTAMP NOT NULL"
                + ") ENGINE=InnoDB";

        String createLinks = "CREATE TABLE IF NOT EXISTS discord_links ("
                + "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "discord_id VARCHAR(20) NOT NULL UNIQUE, "
                + "linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "gems_rewarded BOOLEAN NOT NULL DEFAULT FALSE, "
                + "current_rank VARCHAR(20) DEFAULT 'Unranked', "
                + "last_synced_rank VARCHAR(20) DEFAULT NULL"
                + ") ENGINE=InnoDB";

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(createCodes)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(createLinks)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            LOGGER.atInfo().log("DiscordLinkStore initialized (tables ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create discord link tables");
        }

        // Migration: add rank sync columns to existing installs
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "ALTER TABLE discord_links ADD COLUMN current_rank VARCHAR(20) DEFAULT 'Unranked'")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
        } catch (SQLException ignored) {
            // Column already exists — expected on repeat startup
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "ALTER TABLE discord_links ADD COLUMN last_synced_rank VARCHAR(20) DEFAULT NULL")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
        } catch (SQLException ignored) {
            // Column already exists — expected on repeat startup
        }

        cleanExpiredCodes();
    }

    /**
     * Generate a link code for a player. If the player already has a non-expired
     * code, returns that instead of creating a new one.
     * Format: XXX-XXX (e.g., X7K-9M2)
     */
    public String generateCode(UUID playerId) {
        if (playerId == null || !DatabaseManager.getInstance().isInitialized()) {
            return null;
        }

        // Check for existing non-expired code
        String existing = getActiveCode(playerId);
        if (existing != null) {
            return existing;
        }

        // Generate a new code
        String code = createRandomCode();
        Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + CODE_EXPIRY_MS);

        // Delete any old codes for this player first
        String deleteSql = "DELETE FROM discord_link_codes WHERE player_uuid = ?";
        String insertSql = "INSERT INTO discord_link_codes (code, player_uuid, expires_at) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, code);
                stmt.setString(2, playerId.toString());
                stmt.setTimestamp(3, expiresAt);
                stmt.executeUpdate();
            }
            return formatCode(code);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to generate link code for " + playerId);
            return null;
        }
    }

    /**
     * Check if a player has a linked Discord account.
     * Checks cache first, then DB.
     */
    public boolean isLinked(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        DiscordLink cached = cache.get(playerId);
        if (cached != null) {
            return true;
        }
        return loadLink(playerId) != null;
    }

    /**
     * Returns the existing non-expired code for a player, or null if none exists.
     * Format: XXX-XXX
     */
    public String getActiveCode(UUID playerId) {
        if (playerId == null || !DatabaseManager.getInstance().isInitialized()) {
            return null;
        }
        String sql = "SELECT code FROM discord_link_codes WHERE player_uuid = ? AND expires_at > NOW()";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return formatCode(rs.getString("code"));
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to check active code for " + playerId);
        }
        return null;
    }

    /**
     * Check if the player has a pending gem reward for linking their Discord account.
     * If so, awards the gems and sends a congratulation message.
     * Returns true if a reward was given.
     */
    public boolean checkAndRewardGems(UUID playerId, Player player) {
        if (playerId == null || player == null || !DatabaseManager.getInstance().isInitialized()) {
            return false;
        }

        String selectSql = "SELECT gems_rewarded FROM discord_links WHERE player_uuid = ? AND gems_rewarded = FALSE";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to check gem reward for " + playerId);
            return false;
        }

        // Award gems
        GemStore.getInstance().addGems(playerId, GEM_REWARD);

        // Mark as rewarded
        String updateSql = "UPDATE discord_links SET gems_rewarded = TRUE WHERE player_uuid = ? AND gems_rewarded = FALSE";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                player.sendMessage(Message.join(
                        Message.raw("Discord linked! You received ").color("#a3e635"),
                        Message.raw(GEM_REWARD + " gems").color("#4ade80").bold(true),
                        Message.raw(" as a reward!").color("#a3e635")
                ));
                LOGGER.atInfo().log("Awarded " + GEM_REWARD + " gems to " + playerId + " for Discord link");
                try {
                    io.hyvexa.core.analytics.AnalyticsStore.getInstance().logEvent(playerId, "discord_link", "{}");
                } catch (Exception e) { /* silent */ }
                return true;
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to mark gems rewarded for " + playerId);
        }
        return false;
    }

    /**
     * Write the player's current parkour rank to discord_links for bot sync.
     * Only updates if the player has a linked Discord account.
     */
    public void updateRank(UUID playerId, String rankName) {
        if (playerId == null || rankName == null || !DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "UPDATE discord_links SET current_rank = ? WHERE player_uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, rankName);
            stmt.setString(2, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to update rank for " + playerId);
        }
    }

    /**
     * Remove a player's Discord link entirely (link + pending codes).
     * Returns true if a link was deleted.
     */
    public boolean unlinkPlayer(UUID playerId) {
        if (playerId == null || !DatabaseManager.getInstance().isInitialized()) {
            return false;
        }
        cache.remove(playerId);
        boolean deleted = false;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM discord_links WHERE player_uuid = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                deleted = stmt.executeUpdate() > 0;
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM discord_link_codes WHERE player_uuid = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to unlink player " + playerId);
        }
        return deleted;
    }

    /**
     * Evict a player from cache. Called on disconnect.
     */
    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    /**
     * Delete expired codes from the database.
     */
    private void cleanExpiredCodes() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "DELETE FROM discord_link_codes WHERE expires_at < NOW()";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                LOGGER.atInfo().log("Cleaned " + deleted + " expired link codes");
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to clean expired link codes");
        }
    }

    private DiscordLink loadLink(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return null;
        }
        DiscordLink cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }
        String sql = "SELECT discord_id, linked_at, gems_rewarded FROM discord_links WHERE player_uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    DiscordLink link = new DiscordLink(
                            rs.getString("discord_id"),
                            rs.getTimestamp("linked_at").getTime(),
                            rs.getBoolean("gems_rewarded")
                    );
                    cache.put(playerId, link);
                    return link;
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load discord link for " + playerId);
        }
        return null;
    }

    private String createRandomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Format a raw 6-char code as XXX-XXX.
     */
    private String formatCode(String raw) {
        if (raw == null || raw.length() < CODE_LENGTH) {
            return raw;
        }
        // Strip any existing dash
        String clean = raw.replace("-", "");
        if (clean.length() == CODE_LENGTH) {
            return clean.substring(0, 3) + "-" + clean.substring(3);
        }
        return raw;
    }

    /**
     * Represents a linked Discord account.
     */
    public record DiscordLink(String discordId, long linkedAt, boolean gemsRewarded) {
    }
}
