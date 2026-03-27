package io.hyvexa.core.discord;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import io.hyvexa.core.analytics.PlayerAnalytics;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.economy.CurrencyStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages Discord-Minecraft account linking via shared MySQL database.
 * Shared instance across all modules. Players generate a code in-game,
 * enter it on Discord, and receive a one-time vexa reward on next login.
 */
public class DiscordLinkStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static volatile DiscordLinkStore instance;

    private static final int CODE_LENGTH = 6;
    private static final long CODE_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes
    private static final long VEXA_REWARD = 100;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I/O/0/1 for clarity

    private final ConcurrentHashMap<UUID, DiscordLink> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> rewardCheckedThisSession = new ConcurrentHashMap<>();
    private final ConnectionProvider db;
    private volatile PlayerAnalytics analytics;
    private volatile CurrencyStore vexaStore;

    private DiscordLinkStore(ConnectionProvider db) {
        this.db = db;
    }

    public static DiscordLinkStore createAndRegister(ConnectionProvider db) {
        if (instance != null) {
            throw new IllegalStateException("DiscordLinkStore already initialized");
        }
        instance = new DiscordLinkStore(db);
        return instance;
    }

    public static DiscordLinkStore get() {
        DiscordLinkStore ref = instance;
        if (ref == null) {
            throw new IllegalStateException("DiscordLinkStore not yet initialized — check plugin load order");
        }
        return ref;
    }

    public static void destroy() {
        instance = null;
    }

    public void setAnalytics(PlayerAnalytics analytics) {
        this.analytics = analytics;
    }

    public void setVexaStore(CurrencyStore vexaStore) {
        this.vexaStore = vexaStore;
    }

    /**
     * Create the discord_link_codes and discord_links tables if they don't exist.
     * Also cleans up expired codes. Safe to call multiple times (idempotent).
     */
    public void initialize() {
        if (!this.db.isInitialized()) {
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
                + "vexa_rewarded BOOLEAN NOT NULL DEFAULT FALSE, "
                + "current_rank VARCHAR(20) DEFAULT 'Unranked', "
                + "last_synced_rank VARCHAR(20) DEFAULT NULL"
                + ") ENGINE=InnoDB";

        try (Connection conn = this.db.getConnection()) {
            try (PreparedStatement stmt = DatabaseManager.prepare(conn, createCodes)) {
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = DatabaseManager.prepare(conn, createLinks)) {
                stmt.executeUpdate();
            }
            migrateGemsRewardedToVexa(conn);
            ensureIndexes(conn);
            LOGGER.atInfo().log("DiscordLinkStore initialized (tables ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create discord link tables");
        }

        cleanExpiredCodes();
    }

    /**
     * Generate a link code for a player. If the player already has a non-expired
     * code, returns that instead of creating a new one.
     * Format: XXX-XXX (e.g., X7K-9M2)
     */
    public String generateCode(UUID playerId) {
        if (playerId == null || !this.db.isInitialized()) {
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

        try (Connection conn = this.db.getConnection()) {
            try (PreparedStatement stmt = DatabaseManager.prepare(conn, deleteSql)) {
                stmt.setString(1, playerId.toString());
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = DatabaseManager.prepare(conn, insertSql)) {
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
        if (playerId == null) {
            return null;
        }
        String code = DatabaseManager.queryOne(this.db,
                "SELECT code FROM discord_link_codes WHERE player_uuid = ? AND expires_at > NOW()",
                stmt -> stmt.setString(1, playerId.toString()),
                rs -> rs.getString("code"),
                null);
        return code != null ? formatCode(code) : null;
    }

    /**
     * Check if the player has a pending vexa reward for linking their Discord account.
     * If so, awards the vexa and sends a congratulation message.
     * Returns true if a reward was given.
     */
    public boolean checkAndRewardVexa(UUID playerId, Player player) {
        if (playerId == null || player == null) {
            return false;
        }
        boolean rewarded = claimAndRewardVexa(playerId);
        if (rewarded) {
            sendRewardGrantedMessage(player);
        }
        return rewarded;
    }

    public CompletableFuture<Boolean> checkAndRewardVexaAsync(UUID playerId) {
        if (playerId == null || !this.db.isInitialized()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> claimAndRewardVexa(playerId), HytaleServer.SCHEDULED_EXECUTOR);
    }

    /**
     * Claims the Discord link reward at most once per connected session.
     * Mode transfers should use this instead of the raw async claim path.
     */
    public CompletableFuture<Boolean> checkAndRewardVexaOnLoginAsync(UUID playerId) {
        if (playerId == null || !this.db.isInitialized()) {
            return CompletableFuture.completedFuture(false);
        }
        if (rewardCheckedThisSession.putIfAbsent(playerId, Boolean.TRUE) != null) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> claimAndRewardVexa(playerId), HytaleServer.SCHEDULED_EXECUTOR);
    }

    public CompletableFuture<Boolean> updateRankIfLinkedAsync(UUID playerId, String rankName) {
        if (playerId == null || rankName == null || !this.db.isInitialized()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            if (!isLinked(playerId)) {
                return false;
            }
            updateRank(playerId, rankName);
            return true;
        }, HytaleServer.SCHEDULED_EXECUTOR);
    }

    public void sendRewardGrantedMessage(Player player) {
        if (player == null) {
            return;
        }
        player.sendMessage(Message.join(
                Message.raw("Discord linked! You received ").color("#a3e635"),
                Message.raw(VEXA_REWARD + " vexa").color("#4ade80").bold(true),
                Message.raw(" as a reward!").color("#a3e635")
        ));
    }

    private boolean claimAndRewardVexa(UUID playerId) {
        if (playerId == null || !this.db.isInitialized()) {
            return false;
        }
        // Atomic: claim flag + vexa grant in one transaction so neither can succeed alone
        String claimSql = "UPDATE discord_links SET vexa_rewarded = TRUE WHERE player_uuid = ? AND vexa_rewarded = FALSE";
        String awardSql = "INSERT INTO player_vexa (uuid, vexa) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE vexa = vexa + ?";

        Boolean claimed = this.db.withTransaction(conn -> {
            try (PreparedStatement claimStmt = DatabaseManager.prepare(conn, claimSql);
                 PreparedStatement awardStmt = DatabaseManager.prepare(conn, awardSql)) {

                claimStmt.setString(1, playerId.toString());
                if (claimStmt.executeUpdate() == 0) {
                    return false;
                }

                awardStmt.setString(1, playerId.toString());
                awardStmt.setLong(2, VEXA_REWARD);
                awardStmt.setLong(3, VEXA_REWARD);
                awardStmt.executeUpdate();

                return true;
            }
        }, false);
        if (!claimed) {
            return false;
        }

        // Evict VexaStore cache so next balance read picks up the committed DB value
        if (vexaStore != null) {
            vexaStore.evictPlayer(playerId);
        }

        LOGGER.atInfo().log("Awarded " + VEXA_REWARD + " vexa to " + playerId + " for Discord link");
        try {
            if (analytics != null) {
                analytics.logEvent(playerId, "discord_link", "{}");
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to log discord_link analytics event for " + playerId);
        }
        return true;
    }

    /**
     * Write the player's current parkour rank to discord_links for bot sync.
     * Only updates if the player has a linked Discord account.
     */
    public void updateRank(UUID playerId, String rankName) {
        if (playerId == null || rankName == null) {
            return;
        }
        DatabaseManager.execute(this.db,
                "UPDATE discord_links SET current_rank = ? WHERE player_uuid = ?",
                stmt -> {
                    stmt.setString(1, rankName);
                    stmt.setString(2, playerId.toString());
                });
    }

    private void migrateGemsRewardedToVexa(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            if (columnExists(conn, "discord_links", "gems_rewarded")
                    && !columnExists(conn, "discord_links", "vexa_rewarded")) {
                try (PreparedStatement stmt = DatabaseManager.prepare(conn,
                        "ALTER TABLE discord_links RENAME COLUMN gems_rewarded TO vexa_rewarded")) {
                    stmt.executeUpdate();
                    LOGGER.atInfo().log("Renamed discord_links.gems_rewarded -> discord_links.vexa_rewarded");
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to migrate discord_links reward column");
        }
    }

    private void ensureIndexes(Connection conn) {
        if (conn == null) {
            return;
        }
        try (PreparedStatement stmt = DatabaseManager.prepare(conn,
                "CREATE INDEX idx_discord_link_codes_player_uuid_expires_at ON discord_link_codes (player_uuid, expires_at)")) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            if (isDuplicateIndex(e)) {
                LOGGER.atFine().log("Migration: discord_link_codes player/expires index already exists");
            } else {
                LOGGER.atWarning().withCause(e).log("Failed to add discord_link_codes player/expires index");
            }
        }
    }

    private static boolean isDuplicateIndex(SQLException e) {
        return e.getErrorCode() == 1061 || "42000".equals(e.getSQLState());
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, tableName, columnName)) {
            return rs.next();
        }
    }

    /**
     * Remove a player's Discord link entirely (link + pending codes).
     * Returns true if a link was deleted.
     */
    public boolean unlinkPlayer(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        cache.remove(playerId);
        rewardCheckedThisSession.remove(playerId);
        int deleted = DatabaseManager.executeCount(this.db,
                "DELETE FROM discord_links WHERE player_uuid = ?",
                stmt -> stmt.setString(1, playerId.toString()));
        DatabaseManager.execute(this.db,
                "DELETE FROM discord_link_codes WHERE player_uuid = ?",
                stmt -> stmt.setString(1, playerId.toString()));
        return deleted > 0;
    }

    /**
     * Evict a player from cache. Called on disconnect.
     */
    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
            rewardCheckedThisSession.remove(playerId);
        }
    }

    /**
     * Delete expired codes from the database.
     */
    private void cleanExpiredCodes() {
        int deleted = DatabaseManager.executeCount(this.db,
                "DELETE FROM discord_link_codes WHERE expires_at < NOW()",
                stmt -> {});
        if (deleted > 0) {
            LOGGER.atInfo().log("Cleaned " + deleted + " expired link codes");
        }
    }

    private DiscordLink loadLink(UUID playerId) {
        DiscordLink cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }
        DiscordLink link = DatabaseManager.queryOne(this.db,
                "SELECT discord_id, linked_at, vexa_rewarded FROM discord_links WHERE player_uuid = ?",
                stmt -> stmt.setString(1, playerId.toString()),
                rs -> new DiscordLink(
                        rs.getString("discord_id"),
                        rs.getTimestamp("linked_at").getTime(),
                        rs.getBoolean("vexa_rewarded")),
                null);
        if (link != null) {
            cache.put(playerId, link);
        }
        return link;
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
    public record DiscordLink(String discordId, long linkedAt, boolean vexaRewarded) {
    }
}
