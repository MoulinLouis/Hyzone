package io.hyvexa.duel.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DuelPreferenceStore {

    public enum DuelCategory {
        EASY,
        MEDIUM,
        HARD,
        INSANE;

        public String key() {
            return name().toLowerCase();
        }

        public static String labelFor(DuelCategory category) {
            if (category == null) return "";
            String name = category.name();
            return name.charAt(0) + name.substring(1).toLowerCase();
        }

        public static DuelCategory fromKey(String key) {
            if (key == null || key.isBlank()) {
                return null;
            }
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "easy" -> EASY;
                case "medium" -> MEDIUM;
                case "hard" -> HARD;
                case "insane" -> INSANE;
                default -> null;
            };
        }
    }

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS duel_category_prefs (
            player_uuid VARCHAR(36) PRIMARY KEY,
            easy_enabled BOOLEAN DEFAULT TRUE,
            medium_enabled BOOLEAN DEFAULT TRUE,
            hard_enabled BOOLEAN DEFAULT FALSE,
            insane_enabled BOOLEAN DEFAULT FALSE,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        )
        """;

    private final ConnectionProvider db;
    private final ConcurrentHashMap<UUID, EnumSet<DuelCategory>> enabledByPlayer = new ConcurrentHashMap<>();

    public DuelPreferenceStore() {
        this(DatabaseManager.getInstance());
    }

    public DuelPreferenceStore(ConnectionProvider db) {
        this.db = db;
    }

    private static EnumSet<DuelCategory> defaultEnabled() {
        return EnumSet.of(DuelCategory.EASY, DuelCategory.MEDIUM);
    }

    public void syncLoad() {
        if (!this.db.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, DuelPreferenceStore will be empty");
            return;
        }
        ensureTable();
        String sql = """
            SELECT player_uuid, easy_enabled, medium_enabled, hard_enabled, insane_enabled
            FROM duel_category_prefs
            """;
        try (Connection conn = this.db.getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                        EnumSet<DuelCategory> enabled = EnumSet.noneOf(DuelCategory.class);
                        if (rs.getBoolean("easy_enabled")) {
                            enabled.add(DuelCategory.EASY);
                        }
                        if (rs.getBoolean("medium_enabled")) {
                            enabled.add(DuelCategory.MEDIUM);
                        }
                        if (rs.getBoolean("hard_enabled")) {
                            enabled.add(DuelCategory.HARD);
                        }
                        if (rs.getBoolean("insane_enabled")) {
                            enabled.add(DuelCategory.INSANE);
                        }
                        if (enabled.isEmpty()) {
                            enabled = defaultEnabled();
                        }
                        enabledByPlayer.put(playerId, enabled);
                    }
                    LOGGER.atInfo().log("DuelPreferenceStore loaded " + enabledByPlayer.size() + " player preferences");
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to load DuelPreferenceStore: " + e.getMessage());
        }
    }

    private void ensureTable() {
        try (Connection conn = this.db.getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE_SQL)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to create duel_category_prefs table: " + e.getMessage());
        }
    }

    @Nonnull
    public EnumSet<DuelCategory> getEnabled(@Nonnull UUID playerId) {
        EnumSet<DuelCategory> enabled = enabledByPlayer.get(playerId);
        return enabled != null ? EnumSet.copyOf(enabled) : defaultEnabled();
    }

    public boolean isEnabled(@Nonnull UUID playerId, @Nonnull DuelCategory category) {
        EnumSet<DuelCategory> enabled = enabledByPlayer.get(playerId);
        if (enabled == null) {
            return defaultEnabled().contains(category);
        }
        return enabled.contains(category);
    }

    public void setEnabled(@Nonnull UUID playerId, @Nonnull DuelCategory category, boolean enabled) {
        EnumSet<DuelCategory> updated = enabledByPlayer.compute(playerId, (id, current) -> {
            EnumSet<DuelCategory> next = current != null ? EnumSet.copyOf(current) : defaultEnabled();
            if (enabled) {
                next.add(category);
            } else {
                next.remove(category);
            }
            if (next.isEmpty()) {
                next.add(category);
            }
            return next;
        });
        save(playerId, updated);
    }

    public void toggle(@Nonnull UUID playerId, @Nonnull DuelCategory category) {
        boolean currentlyEnabled = isEnabled(playerId, category);
        setEnabled(playerId, category, !currentlyEnabled);
    }

    @Nonnull
    public EnumSet<DuelCategory> getCommonEnabled(@Nonnull UUID player1, @Nonnull UUID player2) {
        EnumSet<DuelCategory> common = getEnabled(player1);
        common.retainAll(getEnabled(player2));
        return common;
    }

    @Nonnull
    public String formatEnabledLabel(@Nonnull UUID playerId) {
        EnumSet<DuelCategory> enabled = getEnabled(playerId);
        if (enabled.size() == DuelCategory.values().length) {
            return "Easy/Medium/Hard/Insane";
        }
        return enabled.stream()
                .map(DuelCategory::labelFor)
                .collect(Collectors.joining("/"));
    }

    private void save(@Nonnull UUID playerId, @Nonnull Set<DuelCategory> enabled) {
        if (!this.db.isInitialized()) {
            return;
        }
        String sql = """
            INSERT INTO duel_category_prefs (player_uuid, easy_enabled, medium_enabled, hard_enabled, insane_enabled, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                easy_enabled = VALUES(easy_enabled),
                medium_enabled = VALUES(medium_enabled),
                hard_enabled = VALUES(hard_enabled),
                insane_enabled = VALUES(insane_enabled),
                updated_at = VALUES(updated_at)
            """;
        try (Connection conn = this.db.getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                stmt.setBoolean(2, enabled.contains(DuelCategory.EASY));
                stmt.setBoolean(3, enabled.contains(DuelCategory.MEDIUM));
                stmt.setBoolean(4, enabled.contains(DuelCategory.HARD));
                stmt.setBoolean(5, enabled.contains(DuelCategory.INSANE));
                stmt.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save duel preferences: " + e.getMessage());
        }
    }
}
