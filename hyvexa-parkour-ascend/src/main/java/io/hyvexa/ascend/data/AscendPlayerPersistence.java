package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.ascend.AscensionConstants.AchievementType;
import io.hyvexa.ascend.AscensionConstants.SkillTreeNode;
import io.hyvexa.ascend.SummitConstants.SummitCategory;
import io.hyvexa.ascend.data.AutomationConfig.AutoSummitCategoryConfig;
import io.hyvexa.ascend.data.GameplayState.MapProgress;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


/**
 * Handles all database I/O for Ascend player data.
 * Package-private — accessed only through {@link AscendPlayerStore}.
 *
 * <p><b>Contract:</b> This class owns load/delete SQL operations and serialization.
 * Save scheduling and dirty tracking are delegated to {@link AscendSaveScheduler}.
 * Leaderboard queries are delegated to {@link AscendLeaderboardQueries}.</p>
 */
class AscendPlayerPersistence {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Child tables deleted during per-player reset. Order does not matter (no FK constraints). */
    static final String[] CHILD_TABLES = {
        "ascend_player_maps",
        "ascend_player_summit",
        "ascend_player_skills",
        "ascend_player_achievements",
        "ascend_player_cats",
        "ascend_ghost_recordings",
        "ascend_challenges",
        "ascend_challenge_records"
    };

    private final ConnectionProvider db;
    private final Map<UUID, AscendPlayerProgress> players;
    private final Map<UUID, String> playerNames;

    private final AscendLeaderboardQueries leaderboardQueries;
    private final AscendSaveScheduler saveScheduler;

    AscendPlayerPersistence(ConnectionProvider db,
                            Map<UUID, AscendPlayerProgress> players,
                            Map<UUID, String> playerNames,
                            Set<UUID> resetPendingPlayers) {
        this.db = db;
        this.players = players;
        this.playerNames = playerNames;
        this.leaderboardQueries = new AscendLeaderboardQueries(db, players, playerNames);
        this.saveScheduler = new AscendSaveScheduler(db, players, playerNames, resetPendingPlayers);
    }

    void markDirty(UUID playerId) {
        saveScheduler.markDirty(playerId);
    }

    boolean isDirty(UUID playerId) {
        return saveScheduler.isDirty(playerId);
    }

    void snapshotForSave(UUID playerId, AscendPlayerProgress progress) {
        saveScheduler.snapshotForSave(playerId, progress);
    }

    void markTranscendenceResetPending(UUID playerId) {
        saveScheduler.markTranscendenceResetPending(playerId);
    }

    void clearDirtyState() {
        saveScheduler.clearDirtyState();
    }

    void queueSave() {
        saveScheduler.queueSave();
    }

    void flushPendingSave() {
        saveScheduler.flushPendingSave();
    }

    void savePlayerIfDirty(UUID playerId) {
        saveScheduler.savePlayerIfDirty(playerId);
    }

    void cancelPendingSave() {
        saveScheduler.cancelPendingSave();
    }

    boolean hasDirtyVersion(UUID playerId) {
        return saveScheduler.hasDirtyVersion(playerId);
    }

    boolean savePlayerSync(UUID playerId) {
        return saveScheduler.savePlayerSync(playerId);
    }

    List<AscendPlayerStore.LeaderboardEntry> getLeaderboardEntries() {
        return leaderboardQueries.getLeaderboardEntries();
    }

    void invalidateLeaderboardCache() {
        leaderboardQueries.invalidateLeaderboardCache();
    }

    void invalidateMapLeaderboardCache(String mapId) {
        leaderboardQueries.invalidateMapLeaderboardCache(mapId);
    }

    void clearLeaderboardCaches() {
        leaderboardQueries.clearLeaderboardCaches();
    }

    List<AscendPlayerStore.MapLeaderboardEntry> getMapLeaderboard(String mapId) {
        return leaderboardQueries.getMapLeaderboard(mapId);
    }

    // ========================================
    // Load from Database
    // ========================================

    Long loadBestTimeFromDatabase(UUID playerId, String mapId) {
        if (playerId == null || mapId == null) {
            return null;
        }

        String sql = """
            SELECT best_time_ms
            FROM ascend_player_maps
            WHERE player_uuid = ? AND map_id = ? AND best_time_ms IS NOT NULL
            LIMIT 1
            """;

        return DatabaseManager.queryOne(this.db, sql,
            stmt -> {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, mapId);
            },
            rs -> {
                long value = rs.getLong("best_time_ms");
                return rs.wasNull() ? null : value;
            }, null);
    }

    /**
     * Load a single player's data from the database (lazy loading).
     * Returns null if player doesn't exist in database.
     */
    AscendPlayerProgress loadPlayerFromDatabase(UUID playerId) {
        if (!this.db.isInitialized()) {
            return null;
        }

        // If this player has a pending detached dirty snapshot (e.g. disconnect save in-flight),
        // use it instead of reloading an older DB row.
        AscendPlayerProgress detached = saveScheduler.getDetachedIfDirty(playerId);
        if (detached != null) {
            return detached;
        }

        AscendPlayerProgress progress = null;
        String playerIdStr = playerId.toString();

        // Load base player data
        String playerSql = """
            SELECT player_name, volt_mantissa, volt_exp10, elevation_multiplier, ascension_count, skill_tree_points,
                   total_volt_earned_mantissa, total_volt_earned_exp10, total_manual_runs, active_title,
                   ascension_started_at, fastest_ascension_ms,
                   summit_accumulated_volt_mantissa, summit_accumulated_volt_exp10,
                   elevation_accumulated_volt_mantissa, elevation_accumulated_volt_exp10,
                   auto_upgrade_enabled, auto_evolution_enabled, seen_tutorials, hide_other_runners,
                   break_ascension_enabled,
                   auto_elevation_enabled, auto_elevation_timer_seconds, auto_elevation_targets, auto_elevation_target_index,
                   auto_summit_enabled, auto_summit_timer_seconds, auto_summit_config, auto_summit_rotation_index,
                   transcendence_count, auto_ascend_enabled,
                   hud_hidden, players_hidden
            FROM ascend_players
            WHERE uuid = ?
            """;

        try (Connection conn = this.db.getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return null;
            }
            try (PreparedStatement stmt = conn.prepareStatement(playerSql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerIdStr);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String dbName = safeGetString(rs, "player_name", null);
                        if (dbName != null) {
                            playerNames.put(playerId, dbName);
                        }
                        progress = new AscendPlayerProgress();
                        progress.economy().setVolt(BigNumber.of(rs.getDouble("volt_mantissa"), rs.getInt("volt_exp10")));
                        progress.economy().setElevationMultiplier(rs.getInt("elevation_multiplier"));

                        progress.gameplay().setAscensionCount(safeGetInt(rs, "ascension_count", 0));
                        progress.gameplay().setSkillTreePoints(safeGetInt(rs, "skill_tree_points", 0));
                        progress.economy().setTotalVoltEarned(safeGetBigNumber(rs, "total_volt_earned_mantissa", "total_volt_earned_exp10"));
                        progress.gameplay().setTotalManualRuns(safeGetInt(rs, "total_manual_runs", 0));

                        Long ascensionStartedAt = safeGetLong(rs, "ascension_started_at");
                        if (ascensionStartedAt != null) {
                            progress.gameplay().setAscensionStartedAt(ascensionStartedAt);
                        }

                        Long fastestAscensionMs = safeGetLong(rs, "fastest_ascension_ms");
                        if (fastestAscensionMs != null) {
                            progress.gameplay().setFastestAscensionMs(fastestAscensionMs);
                        }

                        BigNumber summitAccumulated = safeGetBigNumber(rs, "summit_accumulated_volt_mantissa", "summit_accumulated_volt_exp10");
                        if (!summitAccumulated.isZero()) {
                            progress.economy().setSummitAccumulatedVolt(summitAccumulated);
                        }

                        BigNumber elevationAccumulated = safeGetBigNumber(rs, "elevation_accumulated_volt_mantissa", "elevation_accumulated_volt_exp10");
                        if (!elevationAccumulated.isZero()) {
                            progress.economy().setElevationAccumulatedVolt(elevationAccumulated);
                        }

                        progress.automation().setAutoUpgradeEnabled(safeGetBoolean(rs, "auto_upgrade_enabled", false));
                        progress.automation().setAutoEvolutionEnabled(safeGetBoolean(rs, "auto_evolution_enabled", false));
                        progress.gameplay().setSeenTutorials(safeGetInt(rs, "seen_tutorials", 0));
                        progress.automation().setHideOtherRunners(safeGetBoolean(rs, "hide_other_runners", false));
                        progress.session().setHudHidden(safeGetBoolean(rs, "hud_hidden", false));
                        progress.session().setPlayersHidden(safeGetBoolean(rs, "players_hidden", false));
                        progress.automation().setBreakAscensionEnabled(safeGetBoolean(rs, "break_ascension_enabled", false));

                        progress.automation().setAutoElevationEnabled(safeGetBoolean(rs, "auto_elevation_enabled", false));
                        progress.automation().setAutoElevationTimerSeconds(safeGetInt(rs, "auto_elevation_timer_seconds", 0));
                        progress.automation().setAutoElevationTargets(parseTargets(safeGetString(rs, "auto_elevation_targets", "[]")));
                        progress.automation().setAutoElevationTargetIndex(safeGetInt(rs, "auto_elevation_target_index", 0));

                        progress.automation().setAutoSummitEnabled(safeGetBoolean(rs, "auto_summit_enabled", false));
                        progress.automation().setAutoSummitTimerSeconds(safeGetInt(rs, "auto_summit_timer_seconds", 0));
                        progress.automation().setAutoSummitConfig(parseAutoSummitConfig(safeGetString(rs, "auto_summit_config", "[]")));
                        progress.automation().setAutoSummitRotationIndex(safeGetInt(rs, "auto_summit_rotation_index", 0));

                        progress.gameplay().setTranscendenceCount(safeGetInt(rs, "transcendence_count", 0));
                        progress.automation().setAutoAscendEnabled(safeGetBoolean(rs, "auto_ascend_enabled", false));
                    }
                }
            }

            // If player doesn't exist in database, return null
            if (progress == null) {
                return null;
            }

            // Load related data on the same connection
            loadMapProgressForPlayer(conn, playerId, progress);
            loadSummitLevelsForPlayer(conn, playerId, progress);
            loadSkillNodesForPlayer(conn, playerId, progress);
            compensateSkillTreeCostChanges(playerId, progress);
            loadAchievementsForPlayer(conn, playerId, progress);
            loadCatsForPlayer(conn, playerId, progress);
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to load player " + playerId + ": " + e.getMessage());
            return null;
        }

        return progress;
    }

    private void loadMapProgressForPlayer(Connection conn, UUID playerId, AscendPlayerProgress progress) throws SQLException {
        String sql = """
            SELECT map_id, unlocked, completed_manually, has_robot,
                   robot_speed_level, robot_stars, multiplier_mantissa, multiplier_exp10, best_time_ms
            FROM ascend_player_maps
            WHERE player_uuid = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String mapId = rs.getString("map_id");
                    MapProgress mapProgress = progress.gameplay().getOrCreateMapProgress(mapId);
                    mapProgress.setUnlocked(rs.getBoolean("unlocked"));
                    mapProgress.setCompletedManually(rs.getBoolean("completed_manually"));
                    mapProgress.setHasRobot(rs.getBoolean("has_robot"));
                    mapProgress.setRobotSpeedLevel(rs.getInt("robot_speed_level"));
                    mapProgress.setRobotStars(rs.getInt("robot_stars"));
                    mapProgress.setMultiplier(BigNumber.of(rs.getDouble("multiplier_mantissa"), rs.getInt("multiplier_exp10")));
                    long bestTime = rs.getLong("best_time_ms");
                    if (!rs.wasNull()) {
                        mapProgress.setBestTimeMs(bestTime);
                    }
                }
            }
        }
    }

    private void loadSummitLevelsForPlayer(Connection conn, UUID playerId, AscendPlayerProgress progress) throws SQLException {
        String sql = "SELECT category, xp FROM ascend_player_summit WHERE player_uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String categoryName = rs.getString("category");
                    double xp = rs.getDouble("xp");
                    try {
                        SummitCategory category = SummitCategory.valueOf(categoryName);
                        progress.economy().setSummitXp(category, xp);
                    } catch (IllegalArgumentException ignored) {
                        // Unknown category
                    }
                }
            }
        }
    }

    private void loadSkillNodesForPlayer(Connection conn, UUID playerId, AscendPlayerProgress progress) throws SQLException {
        String sql = "SELECT skill_node FROM ascend_player_skills WHERE player_uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String nodeName = rs.getString("skill_node");
                    // Migration: ELEVATION_REMNANT replaced by AUTO_ELEVATION
                    if ("ELEVATION_REMNANT".equals(nodeName)) {
                        nodeName = "AUTO_ELEVATION";
                    }
                    // Migration: ELEVATION_BOOST replaced by AUTO_ASCEND
                    if ("ELEVATION_BOOST".equals(nodeName)) {
                        nodeName = "AUTO_ASCEND";
                    }
                    try {
                        SkillTreeNode node = SkillTreeNode.valueOf(nodeName);
                        progress.gameplay().unlockSkillNode(node);
                    } catch (IllegalArgumentException ignored) {
                        // Unknown node
                    }
                }
            }
        }
    }

    /**
     * Compensates players whose unlocked skill nodes now cost more after a tree rebalance.
     * If spent points exceed total points, grants bonus AP to cover the deficit.
     */
    private void compensateSkillTreeCostChanges(UUID playerId, AscendPlayerProgress progress) {
        int spent = progress.gameplay().getSpentSkillPoints();
        int total = progress.gameplay().getSkillTreePoints();
        if (spent > total) {
            int deficit = spent - total;
            progress.gameplay().setSkillTreePoints(spent);
            LOGGER.atInfo().log("[SkillTree] Compensated player " + playerId
                + " with +" + deficit + " AP for tree rebalance (was " + total + ", now " + spent + ")");
        }
    }

    private void loadAchievementsForPlayer(Connection conn, UUID playerId, AscendPlayerProgress progress) throws SQLException {
        String sql = "SELECT achievement FROM ascend_player_achievements WHERE player_uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String achievementName = rs.getString("achievement");
                    try {
                        AchievementType achievement = AchievementType.valueOf(achievementName);
                        progress.gameplay().unlockAchievement(achievement);
                    } catch (IllegalArgumentException ignored) {
                        // Unknown achievement
                    }
                }
            }
        }
    }

    private void loadCatsForPlayer(Connection conn, UUID playerId, AscendPlayerProgress progress) throws SQLException {
        String sql = "SELECT cat_token FROM ascend_player_cats WHERE player_uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    progress.gameplay().addFoundCat(rs.getString("cat_token"));
                }
            }
        }
    }

    // ========================================
    // Delete
    // ========================================

    void deletePlayerDataFromDatabase(UUID playerId) {
        if (!this.db.isInitialized()) {
            return;
        }

        String playerIdStr = playerId.toString();
        this.db.withTransaction(conn -> {
            for (String table : CHILD_TABLES) {
                try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM " + table + " WHERE player_uuid = ?")) {
                    DatabaseManager.applyQueryTimeout(stmt);
                    stmt.setString(1, playerIdStr);
                    stmt.executeUpdate();
                }
            }
        });
    }

    /**
     * Delete ALL player data from every Ascend table (server-wide wipe).
     * Deletes child tables first, then the parent ascend_players table.
     *
     * @return true if the wipe committed successfully
     */
    boolean deleteAllPlayerData() {
        if (!this.db.isInitialized()) {
            return false;
        }

        boolean wiped = this.db.withTransaction(conn -> {
            for (String table : CHILD_TABLES) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + table)) {
                    DatabaseManager.applyQueryTimeout(stmt);
                    stmt.executeUpdate();
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM ascend_players")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
        });
        if (wiped) {
            LOGGER.atInfo().log("All player progress wiped from database (%d tables cleared)",
                    CHILD_TABLES.length + 1);
        }
        return wiped;
    }

    // ========================================
    // Safe ResultSet Helpers
    // ========================================

    private static int safeGetInt(ResultSet rs, String column, int defaultValue) {
        try {
            return rs.getInt(column);
        } catch (SQLException e) {
            LOGGER.atWarning().log("Column '" + column + "' not available: " + e.getMessage());
            return defaultValue;
        }
    }

    private static boolean safeGetBoolean(ResultSet rs, String column, boolean defaultValue) {
        try {
            return rs.getBoolean(column);
        } catch (SQLException e) {
            LOGGER.atWarning().log("Column '" + column + "' not available: " + e.getMessage());
            return defaultValue;
        }
    }

    private static String safeGetString(ResultSet rs, String column, String defaultValue) {
        try {
            return rs.getString(column);
        } catch (SQLException e) {
            LOGGER.atWarning().log("Column '" + column + "' not available: " + e.getMessage());
            return defaultValue;
        }
    }

    private static Long safeGetLong(ResultSet rs, String column) {
        try {
            long value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        } catch (SQLException e) {
            LOGGER.atWarning().log("Column '" + column + "' not available: " + e.getMessage());
            return null;
        }
    }

    private static BigNumber safeGetBigNumber(ResultSet rs, String mantissaCol, String exp10Col) {
        try {
            double m = rs.getDouble(mantissaCol);
            int e = rs.getInt(exp10Col);
            return BigNumber.of(m, e);
        } catch (SQLException ex) {
            LOGGER.atWarning().log("Columns '" + mantissaCol + "'/'" + exp10Col + "' not available: " + ex.getMessage());
            return BigNumber.ZERO;
        }
    }

    // ========================================
    // Serialization Helpers
    // ========================================

    static String serializeTargets(List<Long> targets) {
        if (targets == null || targets.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < targets.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(targets.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    static List<Long> parseTargets(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return Collections.emptyList();
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.isBlank()) {
            return Collections.emptyList();
        }
        List<Long> result = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            try {
                result.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException e) {
                // Skip invalid entries
            }
        }
        return result;
    }

    /**
     * Serialize auto-summit config to JSON.
     * Format: [{"enabled":true,"target":10},{"enabled":false,"target":5},...]
     */
    static String serializeAutoSummitConfig(List<AutoSummitCategoryConfig> config) {
        if (config == null || config.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < config.size(); i++) {
            if (i > 0) sb.append(",");
            AutoSummitCategoryConfig c = config.get(i);
            sb.append("{\"enabled\":").append(c.isEnabled())
              .append(",\"target\":").append(c.getTargetLevel())
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Parse auto-summit config from JSON.
     * Expects: [{"enabled":true,"target":10},...] (also accepts legacy "increment" key)
     */
    static List<AutoSummitCategoryConfig> parseAutoSummitConfig(String json) {
        List<AutoSummitCategoryConfig> result = new ArrayList<>();
        if (json == null || json.isBlank() || json.equals("[]")) {
            // Return defaults for 3 categories
            for (int i = 0; i < 3; i++) {
                result.add(new AutoSummitCategoryConfig(false, 0));
            }
            return result;
        }

        // Simple JSON parsing for {"enabled":bool,"target":int} objects
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);

        // Split by "},{"
        String[] parts = trimmed.split("\\},\\s*\\{");
        for (String part : parts) {
            String cleaned = part.replace("{", "").replace("}", "").trim();
            if (cleaned.isBlank()) continue;

            boolean enabled = false;
            int targetLevel = 0;

            for (String field : cleaned.split(",")) {
                String[] kv = field.split(":");
                if (kv.length != 2) continue;
                String key = kv[0].trim().replace("\"", "");
                String val = kv[1].trim().replace("\"", "");
                if ("enabled".equals(key)) {
                    enabled = "true".equalsIgnoreCase(val);
                } else if ("target".equals(key) || "increment".equals(key)) {
                    try {
                        targetLevel = Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                        targetLevel = 0;
                    }
                }
            }
            result.add(new AutoSummitCategoryConfig(enabled, targetLevel));
        }

        // Ensure exactly 3 entries
        while (result.size() < 3) {
            result.add(new AutoSummitCategoryConfig(false, 0));
        }
        if (result.size() > 3) {
            result = new ArrayList<>(result.subList(0, 3));
        }

        return result;
    }
}
