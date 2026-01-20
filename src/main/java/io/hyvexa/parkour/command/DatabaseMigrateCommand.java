package io.hyvexa.parkour.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.parkour.data.DatabaseManager;
import io.hyvexa.parkour.data.GlobalMessageStore;
import io.hyvexa.parkour.data.TransformData;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseMigrateCommand extends AbstractAsyncCommand {

    private static final Message MESSAGE_OP_REQUIRED = Message.raw("You must be OP to use /dbmigrate.");
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type PROGRESS_LIST_TYPE = new TypeToken<List<ProgressEntry>>() {}.getType();

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

        commandContext.sendMessage(Message.raw("Starting migration from JSON to MySQL...").color("#ffaa00"));

        try {
            SettingsJson settingsJson = loadSettingsJson(commandContext);
            GlobalMessagesJson globalMessagesJson = loadGlobalMessagesJson(commandContext);
            PlayerCountsJson playerCountsJson = loadPlayerCountsJson(commandContext);
            List<ProgressEntry> progressEntries = loadProgressJson(commandContext);
            List<MapJson> mapEntries = loadMapsJson(commandContext);

            if (settingsJson == null || globalMessagesJson == null || playerCountsJson == null
                    || progressEntries == null || mapEntries == null) {
                commandContext.sendMessage(Message.raw(
                        "Migration aborted: missing required JSON files. Ensure Settings.json, GlobalMessages.json, " +
                                "PlayerCounts.json, Progress.json, and Maps.json are present.")
                        .color("#ff4444"));
                return CompletableFuture.completedFuture(null);
            }

            // Migrate in order of dependencies
            int settingsCount = migrateSettings(commandContext, settingsJson);
            int globalMessagesCount = migrateGlobalMessages(commandContext, globalMessagesJson);
            int mapsCount = migrateMaps(commandContext, mapEntries);
            int playersCount = migratePlayers(commandContext, progressEntries);
            int completionsCount = migrateCompletions(commandContext, progressEntries);
            int playerCounts = migratePlayerCounts(commandContext, playerCountsJson);

            commandContext.sendMessage(Message.raw("Migration complete!").color("#44ff44"));
            commandContext.sendMessage(Message.raw(String.format(
                    "Migrated: %d settings, %d global messages, %d maps, %d players, %d completions, %d player count samples",
                    settingsCount, globalMessagesCount, mapsCount, playersCount, completionsCount, playerCounts)));

        } catch (Exception e) {
            commandContext.sendMessage(Message.raw("Migration failed: " + e.getMessage()).color("#ff4444"));
            e.printStackTrace();
        }

        return CompletableFuture.completedFuture(null);
    }

    private int migrateSettings(CommandContext ctx, SettingsJson settingsJson) throws SQLException {
        if (settingsJson == null) {
            ctx.sendMessage(Message.raw("Settings.json not available, skipping...").color("#ffaa00"));
            return 0;
        }

        ctx.sendMessage(Message.raw("Migrating settings..."));

        String sql = """
            INSERT INTO settings (id, fall_respawn_seconds, void_y_failsafe, weapon_damage_disabled, debug_mode,
                spawn_x, spawn_y, spawn_z, spawn_rot_x, spawn_rot_y, spawn_rot_z,
                idle_fall_respawn_for_op, category_order_json)
            VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                spawn_rot_z = VALUES(spawn_rot_z),
                idle_fall_respawn_for_op = VALUES(idle_fall_respawn_for_op),
                category_order_json = VALUES(category_order_json)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            double fallRespawnSeconds = settingsJson.fallRespawnSeconds != null
                    ? settingsJson.fallRespawnSeconds
                    : 0.0;
            double voidY = settingsJson.fallFailsafeVoidY != null
                    ? settingsJson.fallFailsafeVoidY
                    : 0.0;
            boolean weaponDamageDisabled = settingsJson.disableWeaponDamage != null
                    ? settingsJson.disableWeaponDamage
                    : false;
            boolean teleportDebugEnabled = settingsJson.teleportDebugEnabled != null
                    ? settingsJson.teleportDebugEnabled
                    : false;
            boolean idleFallRespawnForOp = settingsJson.idleFallRespawnForOp != null
                    ? settingsJson.idleFallRespawnForOp
                    : false;

            stmt.setDouble(1, fallRespawnSeconds);
            stmt.setDouble(2, voidY);
            stmt.setBoolean(3, weaponDamageDisabled);
            stmt.setBoolean(4, teleportDebugEnabled);

            TransformData spawn = toTransform(settingsJson.spawn);
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
            stmt.setBoolean(11, idleFallRespawnForOp);
            stmt.setString(12, GSON.toJson(normalizeCategoryOrder(settingsJson)));

            stmt.executeUpdate();
        }

        ctx.sendMessage(Message.raw("Settings migrated.").color("#44ff44"));
        return 1;
    }

    private int migrateGlobalMessages(CommandContext ctx, GlobalMessagesJson json) throws SQLException {
        if (json == null) {
            ctx.sendMessage(Message.raw("GlobalMessages.json not available, skipping...").color("#ffaa00"));
            return 0;
        }

        List<String> messages = json.messages != null ? json.messages : List.of();
        long intervalMinutes = json.intervalMinutes != null
                ? json.intervalMinutes
                : GlobalMessageStore.DEFAULT_INTERVAL_MINUTES;

        ctx.sendMessage(Message.raw("Migrating global messages..."));

        String settingsSql = """
            INSERT INTO global_message_settings (id, interval_minutes) VALUES (1, ?)
            ON DUPLICATE KEY UPDATE interval_minutes = VALUES(interval_minutes)
            """;
        String deleteSql = "DELETE FROM global_messages";
        String insertSql = "INSERT INTO global_messages (message, display_order) VALUES (?, ?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement settingsStmt = conn.prepareStatement(settingsSql)) {
            settingsStmt.setLong(1, intervalMinutes);
            settingsStmt.executeUpdate();
        }

        int count = 0;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                deleteStmt.executeUpdate();
                for (int i = 0; i < messages.size(); i++) {
                    String message = messages.get(i);
                    if (message == null || message.isBlank()) {
                        continue;
                    }
                    insertStmt.setString(1, message.trim());
                    insertStmt.setInt(2, count);
                    insertStmt.addBatch();
                    count++;
                }
                insertStmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }

        ctx.sendMessage(Message.raw("Global messages migrated: " + count).color("#44ff44"));
        return count;
    }

    private int migratePlayerCounts(CommandContext ctx, PlayerCountsJson json) throws SQLException {
        List<PlayerCountSampleJson> samples = json != null && json.samples != null ? json.samples : List.of();
        if (samples.isEmpty()) {
            ctx.sendMessage(Message.raw("Player count samples not available, skipping...").color("#ffaa00"));
            return 0;
        }

        ctx.sendMessage(Message.raw("Migrating player count samples..."));

        String deleteSql = "DELETE FROM player_count_samples";
        String insertSql = "INSERT INTO player_count_samples (timestamp_ms, count) VALUES (?, ?)";

        int count = 0;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                deleteStmt.executeUpdate();
                for (PlayerCountSampleJson sample : samples) {
                    if (sample == null) {
                        continue;
                    }
                    insertStmt.setLong(1, sample.timestampMs);
                    insertStmt.setInt(2, Math.max(0, sample.count));
                    insertStmt.addBatch();
                    count++;
                    if (count % 250 == 0) {
                        insertStmt.executeBatch();
                    }
                }
                insertStmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }

        ctx.sendMessage(Message.raw("Player count samples migrated: " + count).color("#44ff44"));
        return count;
    }

    private int migrateMaps(CommandContext ctx, List<MapJson> mapEntries) throws SQLException {
        if (mapEntries == null) {
            ctx.sendMessage(Message.raw("Maps.json not available, skipping...").color("#ffaa00"));
            return 0;
        }

        ctx.sendMessage(Message.raw("Migrating " + mapEntries.size() + " maps..."));

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

                for (MapJson map : mapEntries) {
                    if (map == null || map.id == null || map.id.isBlank()) {
                        continue;
                    }
                    // Insert/update map
                    mapStmt.setString(1, map.id);
                    mapStmt.setString(2, map.name);
                    mapStmt.setString(3, map.category);
                    mapStmt.setString(4, map.world);
                    mapStmt.setInt(5, map.difficulty != null ? map.difficulty : 0);
                    mapStmt.setInt(6, map.order != null ? map.order : 0);
                    mapStmt.setLong(7, map.firstCompletionXp != null ? map.firstCompletionXp : 0L);
                    mapStmt.setBoolean(8, map.enableMithrilSword != null && map.enableMithrilSword);

                    setTransform(mapStmt, 9, toTransform(map.start));
                    setTransform(mapStmt, 15, toTransform(map.finish));
                    setTransform(mapStmt, 21, toTransform(map.startTrigger));
                    setTransform(mapStmt, 27, toTransform(map.leaveTrigger));
                    setTransform(mapStmt, 33, toTransform(map.leaveTeleport));

                    mapStmt.setTimestamp(39, new java.sql.Timestamp(map.createdAt != null ? map.createdAt : 0L));
                    mapStmt.setTimestamp(40, new java.sql.Timestamp(map.updatedAt != null ? map.updatedAt : 0L));

                    mapStmt.addBatch();

                    // Delete existing checkpoints and re-insert
                    deleteStmt.setString(1, map.id);
                    deleteStmt.addBatch();

                    List<TransformJson> checkpoints = map.checkpoints;
                    if (checkpoints != null) {
                        for (int i = 0; i < checkpoints.size(); i++) {
                            TransformData cp = toTransform(checkpoints.get(i));
                            if (cp == null) {
                                continue;
                            }
                            cpStmt.setString(1, map.id);
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

    private int migratePlayers(CommandContext ctx, List<ProgressEntry> progressEntries) throws SQLException {
        if (progressEntries == null) {
            ctx.sendMessage(Message.raw("Progress.json not available, skipping...").color("#ffaa00"));
            return 0;
        }

        ctx.sendMessage(Message.raw("Migrating " + progressEntries.size() + " players..."));

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

            for (ProgressEntry entry : progressEntries) {
                UUID playerId = parseUuid(entry.uuid);
                if (playerId == null) {
                    continue;
                }
                stmt.setString(1, playerId.toString());
                stmt.setString(2, entry.name);
                stmt.setLong(3, entry.xp != null ? Math.max(0L, entry.xp) : 0L);
                int level = entry.level != null ? Math.max(1, entry.level) : 1;
                stmt.setInt(4, level);
                stmt.setBoolean(5, entry.welcomeShown != null && entry.welcomeShown);
                stmt.setLong(6, entry.playtimeMs != null ? Math.max(0L, entry.playtimeMs) : 0L);
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

    private int migrateCompletions(CommandContext ctx, List<ProgressEntry> progressEntries) throws SQLException {
        if (progressEntries == null) {
            return 0;
        }

        ctx.sendMessage(Message.raw("Migrating player completions..."));

        String sql = """
            INSERT INTO player_completions (player_uuid, map_id, best_time_ms)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE best_time_ms = VALUES(best_time_ms)
            """;

        // Get valid map IDs from database to avoid foreign key errors
        Set<String> validMapIds = new HashSet<>();
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

            for (ProgressEntry entry : progressEntries) {
                UUID playerId = parseUuid(entry.uuid);
                if (playerId == null) {
                    continue;
                }
                Set<String> mapIds = new HashSet<>();
                if (entry.completedMaps != null) {
                    mapIds.addAll(entry.completedMaps);
                }
                if (entry.bestTimes != null) {
                    mapIds.addAll(entry.bestTimes.keySet());
                }
                for (String mapId : mapIds) {
                    if (!validMapIds.contains(mapId)) {
                        continue;
                    }
                    long bestTime = 0L;
                    if (entry.bestTimes != null) {
                        Long value = entry.bestTimes.get(mapId);
                        if (value != null) {
                            bestTime = value;
                        }
                    }
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
            stmt.executeBatch();
        }

        ctx.sendMessage(Message.raw("Completions migrated: " + count).color("#44ff44"));
        return count;
    }

    private SettingsJson loadSettingsJson(CommandContext ctx) {
        return loadJson(ctx, "Settings.json", SettingsJson.class,
                "Parkour/Settings.json", "run/Parkour/Settings.json");
    }

    private GlobalMessagesJson loadGlobalMessagesJson(CommandContext ctx) {
        return loadJson(ctx, "GlobalMessages.json", GlobalMessagesJson.class,
                "Parkour/GlobalMessages.json", "Parkour/GlobalMessage.json",
                "run/Parkour/GlobalMessages.json", "run/Parkour/GlobalMessage.json");
    }

    private PlayerCountsJson loadPlayerCountsJson(CommandContext ctx) {
        Path path = resolveJsonPath("Parkour/PlayerCounts.json", "run/Parkour/PlayerCounts.json");
        if (path == null) {
            ctx.sendMessage(Message.raw("PlayerCounts.json not found.").color("#ff4444"));
            return null;
        }
        try {
            return GSON.fromJson(Files.readString(path), PlayerCountsJson.class);
        } catch (IOException | RuntimeException e) {
            ctx.sendMessage(Message.raw("Failed to read PlayerCounts.json: " + e.getMessage()).color("#ff4444"));
            return null;
        }
    }

    private List<ProgressEntry> loadProgressJson(CommandContext ctx) {
        return loadJson(ctx, "Progress.json", PROGRESS_LIST_TYPE,
                "Parkour/Progress.json", "run/Parkour/Progress.json");
    }

    private List<MapJson> loadMapsJson(CommandContext ctx) {
        Type mapListType = new TypeToken<List<MapJson>>() {}.getType();
        return loadJson(ctx, "Maps.json", mapListType,
                "Parkour/Maps.json", "run/Parkour/Maps.json");
    }

    private <T> T loadJson(CommandContext ctx, String label, Class<T> type, String... candidates) {
        Path path = resolveJsonPath(candidates);
        if (path == null) {
            ctx.sendMessage(Message.raw(label + " not found.").color("#ff4444"));
            return null;
        }
        try {
            return GSON.fromJson(Files.readString(path), type);
        } catch (IOException | RuntimeException e) {
            ctx.sendMessage(Message.raw("Failed to read " + label + ": " + e.getMessage()).color("#ff4444"));
            return null;
        }
    }

    private <T> T loadJson(CommandContext ctx, String label, Type type, String... candidates) {
        Path path = resolveJsonPath(candidates);
        if (path == null) {
            ctx.sendMessage(Message.raw(label + " not found.").color("#ff4444"));
            return null;
        }
        try {
            return GSON.fromJson(Files.readString(path), type);
        } catch (IOException | RuntimeException e) {
            ctx.sendMessage(Message.raw("Failed to read " + label + ": " + e.getMessage()).color("#ff4444"));
            return null;
        }
    }

    private Path resolveJsonPath(String... candidates) {
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private TransformData toTransform(TransformJson json) {
        if (json == null || json.x == null || json.y == null || json.z == null) {
            return null;
        }
        TransformData data = new TransformData();
        data.setX(json.x);
        data.setY(json.y);
        data.setZ(json.z);
        data.setRotX(json.rotX != null ? json.rotX : 0.0f);
        data.setRotY(json.rotY != null ? json.rotY : 0.0f);
        data.setRotZ(json.rotZ != null ? json.rotZ : 0.0f);
        return data;
    }

    private List<String> normalizeCategoryOrder(SettingsJson settingsJson) {
        List<String> source = settingsJson != null && settingsJson.categoryOrder != null
                ? settingsJson.categoryOrder
                : List.of();
        if (source.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new java.util.ArrayList<>();
        for (String entry : source) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private static final class SettingsJson {
        Double fallRespawnSeconds;
        Double fallFailsafeVoidY;
        TransformJson spawn;
        Boolean idleFallRespawnForOp;
        Boolean disableWeaponDamage;
        Boolean teleportDebugEnabled;
        List<String> categoryOrder;
    }

    private static final class GlobalMessagesJson {
        Long intervalMinutes;
        List<String> messages;
    }

    private static final class PlayerCountsJson {
        List<PlayerCountSampleJson> samples;
    }

    private static final class PlayerCountSampleJson {
        long timestampMs;
        int count;
    }

    private static final class ProgressEntry {
        String uuid;
        List<String> completedMaps;
        java.util.Map<String, Long> bestTimes;
        String name;
        Long xp;
        Integer level;
        Boolean welcomeShown;
        Long playtimeMs;
    }

    private static final class MapJson {
        String id;
        String name;
        String category;
        String world;
        TransformJson start;
        TransformJson finish;
        TransformJson startTrigger;
        TransformJson leaveTrigger;
        TransformJson leaveTeleport;
        Long firstCompletionXp;
        Integer difficulty;
        Integer order;
        Boolean enableMithrilSword;
        Long createdAt;
        Long updatedAt;
        List<TransformJson> checkpoints;
    }

    private static final class TransformJson {
        Double x;
        Double y;
        Double z;
        Float rotX;
        Float rotY;
        Float rotZ;
    }
}
