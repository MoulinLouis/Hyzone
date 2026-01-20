package io.hyvexa.parkour.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.parkour.data.DatabaseManager;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;
import io.hyvexa.parkour.data.TransformData;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseMigrateCommand extends AbstractAsyncCommand {

    private static final Message MESSAGE_OP_REQUIRED = Message.raw("You must be OP to use /dbmigrate.");

    public DatabaseMigrateCommand() {
        super("dbmigrate", "Migrate JSON data to MySQL database.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        if (!(sender instanceof Player player)) {
            commandContext.sendMessage(Message.raw("This command must be run by a player."));
            return CompletableFuture.completedFuture(null);
        }
        if (!PermissionUtils.isOp(player)) {
            commandContext.sendMessage(MESSAGE_OP_REQUIRED);
            return CompletableFuture.completedFuture(null);
        }

        DatabaseManager db = DatabaseManager.getInstance();
        if (!db.isInitialized()) {
            try {
                db.initialize();
            } catch (Exception e) {
                commandContext.sendMessage(Message.raw("Failed to initialize database: " + e.getMessage()).color("#ff4444"));
                return CompletableFuture.completedFuture(null);
            }
        }

        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin == null) {
            commandContext.sendMessage(Message.raw("Plugin not available.").color("#ff4444"));
            return CompletableFuture.completedFuture(null);
        }

        commandContext.sendMessage(Message.raw("Starting migration from JSON to MySQL...").color("#ffaa00"));

        try {
            // Migrate in order of dependencies
            int settingsCount = migrateSettings(commandContext, plugin.getSettingsStore());
            int mapsCount = migrateMaps(commandContext, plugin.getMapStore());
            int playersCount = migratePlayers(commandContext, plugin.getProgressStore());
            int completionsCount = migrateCompletions(commandContext, plugin.getProgressStore());
            int titlesCount = migrateTitles(commandContext, plugin.getProgressStore());

            commandContext.sendMessage(Message.raw("Migration complete!").color("#44ff44"));
            commandContext.sendMessage(Message.raw(String.format(
                    "Migrated: %d settings, %d maps, %d players, %d completions, %d titles",
                    settingsCount, mapsCount, playersCount, completionsCount, titlesCount)));

        } catch (Exception e) {
            commandContext.sendMessage(Message.raw("Migration failed: " + e.getMessage()).color("#ff4444"));
            e.printStackTrace();
        }

        return CompletableFuture.completedFuture(null);
    }

    private int migrateSettings(CommandContext ctx, SettingsStore settingsStore) throws SQLException {
        if (settingsStore == null) {
            ctx.sendMessage(Message.raw("SettingsStore not available, skipping...").color("#ffaa00"));
            return 0;
        }

        ctx.sendMessage(Message.raw("Migrating settings..."));

        String sql = """
            INSERT INTO settings (id, fall_respawn_seconds, void_y_failsafe, weapon_damage_disabled, debug_mode,
                spawn_x, spawn_y, spawn_z, spawn_rot_x, spawn_rot_y, spawn_rot_z)
            VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                fall_respawn_seconds = VALUES(fall_respawn_seconds),
                void_y_failsafe = VALUES(void_y_failsafe),
                weapon_damage_disabled = VALUES(weapon_damage_disabled),
                debug_mode = VALUES(debug_mode),
                spawn_x = VALUES(spawn_x),
                spawn_y = VALUES(spawn_y),
                spawn_z = VALUES(spawn_z),
                spawn_rot_x = VALUES(spawn_rot_x),
                spawn_rot_y = VALUES(spawn_rot_y),
                spawn_rot_z = VALUES(spawn_rot_z)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, settingsStore.getFallRespawnSeconds());
            stmt.setDouble(2, settingsStore.getFallFailsafeVoidY());
            stmt.setBoolean(3, settingsStore.isWeaponDamageDisabled());
            stmt.setBoolean(4, settingsStore.isTeleportDebugEnabled());

            TransformData spawn = settingsStore.getSpawnPosition();
            if (spawn != null) {
                stmt.setDouble(5, spawn.getX());
                stmt.setDouble(6, spawn.getY());
                stmt.setDouble(7, spawn.getZ());
                stmt.setFloat(8, spawn.getRotX());
                stmt.setFloat(9, spawn.getRotY());
                stmt.setFloat(10, spawn.getRotZ());
            } else {
                stmt.setNull(5, java.sql.Types.DOUBLE);
                stmt.setNull(6, java.sql.Types.DOUBLE);
                stmt.setNull(7, java.sql.Types.DOUBLE);
                stmt.setNull(8, java.sql.Types.FLOAT);
                stmt.setNull(9, java.sql.Types.FLOAT);
                stmt.setNull(10, java.sql.Types.FLOAT);
            }

            stmt.executeUpdate();
        }

        ctx.sendMessage(Message.raw("Settings migrated.").color("#44ff44"));
        return 1;
    }

    private int migrateMaps(CommandContext ctx, MapStore mapStore) throws SQLException {
        if (mapStore == null) {
            ctx.sendMessage(Message.raw("MapStore not available, skipping...").color("#ffaa00"));
            return 0;
        }

        List<Map> maps = mapStore.listMaps();
        ctx.sendMessage(Message.raw("Migrating " + maps.size() + " maps..."));

        String mapSql = """
            INSERT INTO maps (id, name, category, world, difficulty, display_order, first_completion_xp, mithril_sword_enabled,
                start_x, start_y, start_z, start_rot_x, start_rot_y, start_rot_z,
                finish_x, finish_y, finish_z, finish_rot_x, finish_rot_y, finish_rot_z,
                start_trigger_x, start_trigger_y, start_trigger_z, start_trigger_rot_x, start_trigger_rot_y, start_trigger_rot_z,
                leave_trigger_x, leave_trigger_y, leave_trigger_z, leave_trigger_rot_x, leave_trigger_rot_y, leave_trigger_rot_z,
                leave_teleport_x, leave_teleport_y, leave_teleport_z, leave_teleport_rot_x, leave_teleport_rot_y, leave_teleport_rot_z,
                created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name), category = VALUES(category), world = VALUES(world),
                difficulty = VALUES(difficulty), display_order = VALUES(display_order),
                first_completion_xp = VALUES(first_completion_xp), mithril_sword_enabled = VALUES(mithril_sword_enabled),
                start_x = VALUES(start_x), start_y = VALUES(start_y), start_z = VALUES(start_z),
                start_rot_x = VALUES(start_rot_x), start_rot_y = VALUES(start_rot_y), start_rot_z = VALUES(start_rot_z),
                finish_x = VALUES(finish_x), finish_y = VALUES(finish_y), finish_z = VALUES(finish_z),
                finish_rot_x = VALUES(finish_rot_x), finish_rot_y = VALUES(finish_rot_y), finish_rot_z = VALUES(finish_rot_z),
                updated_at = VALUES(updated_at)
            """;

        String checkpointSql = """
            INSERT INTO map_checkpoints (map_id, checkpoint_index, x, y, z, rot_x, rot_y, rot_z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                x = VALUES(x), y = VALUES(y), z = VALUES(z),
                rot_x = VALUES(rot_x), rot_y = VALUES(rot_y), rot_z = VALUES(rot_z)
            """;

        String deleteCheckpointsSql = "DELETE FROM map_checkpoints WHERE map_id = ?";

        int count = 0;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement mapStmt = conn.prepareStatement(mapSql);
                 PreparedStatement cpStmt = conn.prepareStatement(checkpointSql);
                 PreparedStatement deleteStmt = conn.prepareStatement(deleteCheckpointsSql)) {

                for (Map map : maps) {
                    // Insert/update map
                    mapStmt.setString(1, map.getId());
                    mapStmt.setString(2, map.getName());
                    mapStmt.setString(3, map.getCategory());
                    mapStmt.setString(4, map.getWorld());
                    mapStmt.setInt(5, map.getDifficulty());
                    mapStmt.setInt(6, map.getOrder());
                    mapStmt.setLong(7, map.getFirstCompletionXp());
                    mapStmt.setBoolean(8, map.isMithrilSwordEnabled());

                    setTransform(mapStmt, 9, map.getStart());
                    setTransform(mapStmt, 15, map.getFinish());
                    setTransform(mapStmt, 21, map.getStartTrigger());
                    setTransform(mapStmt, 27, map.getLeaveTrigger());
                    setTransform(mapStmt, 33, map.getLeaveTeleport());

                    mapStmt.setTimestamp(39, new java.sql.Timestamp(map.getCreatedAt()));
                    mapStmt.setTimestamp(40, new java.sql.Timestamp(map.getUpdatedAt()));

                    mapStmt.addBatch();

                    // Delete existing checkpoints and re-insert
                    deleteStmt.setString(1, map.getId());
                    deleteStmt.addBatch();

                    List<TransformData> checkpoints = map.getCheckpoints();
                    if (checkpoints != null) {
                        for (int i = 0; i < checkpoints.size(); i++) {
                            TransformData cp = checkpoints.get(i);
                            cpStmt.setString(1, map.getId());
                            cpStmt.setInt(2, i);
                            cpStmt.setDouble(3, cp.getX());
                            cpStmt.setDouble(4, cp.getY());
                            cpStmt.setDouble(5, cp.getZ());
                            cpStmt.setFloat(6, cp.getRotX());
                            cpStmt.setFloat(7, cp.getRotY());
                            cpStmt.setFloat(8, cp.getRotZ());
                            cpStmt.addBatch();
                        }
                    }

                    count++;
                }

                deleteStmt.executeBatch();
                mapStmt.executeBatch();
                cpStmt.executeBatch();
                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }

        ctx.sendMessage(Message.raw("Maps migrated: " + count).color("#44ff44"));
        return count;
    }

    private void setTransform(PreparedStatement stmt, int startIndex, TransformData transform) throws SQLException {
        if (transform != null) {
            stmt.setDouble(startIndex, transform.getX());
            stmt.setDouble(startIndex + 1, transform.getY());
            stmt.setDouble(startIndex + 2, transform.getZ());
            stmt.setFloat(startIndex + 3, transform.getRotX());
            stmt.setFloat(startIndex + 4, transform.getRotY());
            stmt.setFloat(startIndex + 5, transform.getRotZ());
        } else {
            stmt.setNull(startIndex, java.sql.Types.DOUBLE);
            stmt.setNull(startIndex + 1, java.sql.Types.DOUBLE);
            stmt.setNull(startIndex + 2, java.sql.Types.DOUBLE);
            stmt.setNull(startIndex + 3, java.sql.Types.FLOAT);
            stmt.setNull(startIndex + 4, java.sql.Types.FLOAT);
            stmt.setNull(startIndex + 5, java.sql.Types.FLOAT);
        }
    }

    private int migratePlayers(CommandContext ctx, ProgressStore progressStore) throws SQLException {
        if (progressStore == null) {
            ctx.sendMessage(Message.raw("ProgressStore not available, skipping...").color("#ffaa00"));
            return 0;
        }

        var playerIds = progressStore.getPlayerIds();
        ctx.sendMessage(Message.raw("Migrating " + playerIds.size() + " players..."));

        String sql = """
            INSERT INTO players (uuid, name, xp, level, welcome_shown, playtime_ms)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name), xp = VALUES(xp), level = VALUES(level),
                welcome_shown = VALUES(welcome_shown), playtime_ms = VALUES(playtime_ms)
            """;

        int count = 0;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (UUID playerId : playerIds) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, progressStore.getPlayerName(playerId));
                stmt.setLong(3, progressStore.getXp(playerId));
                stmt.setInt(4, progressStore.getLevel(playerId));
                stmt.setBoolean(5, !progressStore.shouldShowWelcome(playerId));
                stmt.setLong(6, progressStore.getPlaytimeMs(playerId));
                stmt.addBatch();
                count++;

                if (count % 100 == 0) {
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
        }

        ctx.sendMessage(Message.raw("Players migrated: " + count).color("#44ff44"));
        return count;
    }

    private int migrateCompletions(CommandContext ctx, ProgressStore progressStore) throws SQLException {
        if (progressStore == null) {
            return 0;
        }

        ctx.sendMessage(Message.raw("Migrating player completions..."));

        String sql = """
            INSERT INTO player_completions (player_uuid, map_id, best_time_ms)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE best_time_ms = VALUES(best_time_ms)
            """;

        // Get valid map IDs from database to avoid foreign key errors
        java.util.Set<String> validMapIds = new java.util.HashSet<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id FROM maps");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                validMapIds.add(rs.getString(1));
            }
        }

        int count = 0;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (UUID playerId : progressStore.getPlayerIds()) {
                // For each map, check if player has a best time
                for (String mapId : validMapIds) {
                    Long bestTime = progressStore.getBestTimeMs(playerId, mapId);
                    if (bestTime != null) {
                        stmt.setString(1, playerId.toString());
                        stmt.setString(2, mapId);
                        stmt.setLong(3, bestTime);
                        stmt.addBatch();
                        count++;

                        if (count % 100 == 0) {
                            stmt.executeBatch();
                        }
                    }
                }
            }
            stmt.executeBatch();
        }

        ctx.sendMessage(Message.raw("Completions migrated: " + count).color("#44ff44"));
        return count;
    }

    private int migrateTitles(CommandContext ctx, ProgressStore progressStore) throws SQLException {
        if (progressStore == null) {
            return 0;
        }

        ctx.sendMessage(Message.raw("Migrating player titles..."));

        String sql = """
            INSERT INTO player_titles (player_uuid, title)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE title = VALUES(title)
            """;

        int count = 0;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (UUID playerId : progressStore.getPlayerIds()) {
                for (String title : progressStore.getTitles(playerId)) {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, title);
                    stmt.addBatch();
                    count++;
                }
            }
            stmt.executeBatch();
        }

        ctx.sendMessage(Message.raw("Titles migrated: " + count).color("#44ff44"));
        return count;
    }
}
