package io.hyvexa.duel.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.parkour.data.DatabaseManager;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/** MySQL-backed storage for duel category preferences. */
public class DuelPreferenceStore {

    public enum DuelCategory {
        EASY,
        MEDIUM,
        HARD,
        INSANE;

        public String key() {
            return name().toLowerCase();
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

    private final ConcurrentHashMap<UUID, EnumSet<DuelCategory>> enabledByPlayer = new ConcurrentHashMap<>();

    private static EnumSet<DuelCategory> defaultEnabled() {
        return EnumSet.of(DuelCategory.EASY, DuelCategory.MEDIUM);
    }

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, DuelPreferenceStore will be empty");
            return;
        }
        ensureTable();
        String sql = """
            SELECT player_uuid, easy_enabled, medium_enabled, hard_enabled, insane_enabled
            FROM duel_category_prefs
            """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load DuelPreferenceStore: " + e.getMessage());
        }
    }

    private void ensureTable() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE_SQL)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to create duel_category_prefs table: " + e.getMessage());
        }
    }

    @Nonnull
    public EnumSet<DuelCategory> getEnabled(@Nonnull UUID playerId) {
        EnumSet<DuelCategory> enabled = enabledByPlayer.get(playerId);
        return enabled != null ? EnumSet.copyOf(enabled) : defaultEnabled();
    }

    public boolean isEnabled(@Nonnull UUID playerId, @Nonnull DuelCategory category) {
        EnumSet<DuelCategory> enabled = enabledByPlayer.get(playerId);
        return enabled == null || enabled.contains(category);
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
                .map(this::labelFor)
                .collect(Collectors.joining("/"));
    }

    private String labelFor(DuelCategory category) {
        return switch (category) {
            case EASY -> "Easy";
            case MEDIUM -> "Medium";
            case HARD -> "Hard";
            case INSANE -> "Insane";
        };
    }

    private void save(@Nonnull UUID playerId, @Nonnull Set<DuelCategory> enabled) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        Map<DuelCategory, Boolean> flags = toFlags(enabled);
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
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setBoolean(2, flags.get(DuelCategory.EASY));
            stmt.setBoolean(3, flags.get(DuelCategory.MEDIUM));
            stmt.setBoolean(4, flags.get(DuelCategory.HARD));
            stmt.setBoolean(5, flags.get(DuelCategory.INSANE));
            stmt.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save duel preferences: " + e.getMessage());
        }
    }

    @Nonnull
    private Map<DuelCategory, Boolean> toFlags(@Nonnull Set<DuelCategory> enabled) {
        EnumMap<DuelCategory, Boolean> flags = new EnumMap<>(DuelCategory.class);
        for (DuelCategory category : DuelCategory.values()) {
            flags.put(category, enabled.contains(category));
        }
        return flags;
    }
}
