package io.hyvexa.ascend.mine.achievement;

import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks mining achievements and stats per player.
 * Uses in-memory cache with deferred DB writes (same pattern as MinePlayerStore).
 */
public class MineAchievementTracker {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final Map<UUID, PlayerAchievementState> states = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyStats = ConcurrentHashMap.newKeySet();
    private volatile ScheduledFuture<?> pendingSave;

    // ── State class ──────────────────────────────────────────────────────

    private static class PlayerAchievementState {
        final Set<String> completed = ConcurrentHashMap.newKeySet();
        final AtomicLong totalBlocksMined = new AtomicLong();
        final AtomicLong totalCrystalsEarned = new AtomicLong();
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Increment blocks mined and check block milestone achievements.
     */
    public void incrementBlocksMined(UUID playerId, int count) {
        PlayerAchievementState state = getOrLoadState(playerId);
        long newValue = state.totalBlocksMined.addAndGet(count);
        dirtyStats.add(playerId);
        queueSave();

        checkStatAchievements(playerId, state, MineAchievement.StatType.TOTAL_BLOCKS_MINED, newValue);
    }

    /**
     * Increment crystals earned and check economy milestone achievements.
     */
    public void incrementCrystalsEarned(UUID playerId, long amount) {
        PlayerAchievementState state = getOrLoadState(playerId);
        long newValue = state.totalCrystalsEarned.addAndGet(amount);
        dirtyStats.add(playerId);
        queueSave();

        checkStatAchievements(playerId, state, MineAchievement.StatType.TOTAL_CRYSTALS_EARNED, newValue);
    }

    /**
     * Check and grant a specific event-based achievement (miner purchase, evolution, etc.).
     */
    public void checkAchievement(UUID playerId, MineAchievement achievement) {
        PlayerAchievementState state = getOrLoadState(playerId);
        if (state.completed.contains(achievement.getId())) return;
        grantAchievement(playerId, state, achievement);
    }

    /**
     * Check if the player has completed a specific achievement.
     */
    public boolean isCompleted(UUID playerId, MineAchievement achievement) {
        PlayerAchievementState state = getOrLoadState(playerId);
        return state.completed.contains(achievement.getId());
    }

    /**
     * Get the current stat value for progress display.
     */
    public long getStatValue(UUID playerId, MineAchievement.StatType statType) {
        PlayerAchievementState state = getOrLoadState(playerId);
        return switch (statType) {
            case TOTAL_BLOCKS_MINED -> state.totalBlocksMined.get();
            case TOTAL_CRYSTALS_EARNED -> state.totalCrystalsEarned.get();
        };
    }

    /**
     * Get the set of completed achievement IDs for a player.
     */
    public Set<String> getCompletedIds(UUID playerId) {
        return Set.copyOf(getOrLoadState(playerId).completed);
    }

    /**
     * Evict player from cache (on disconnect).
     */
    public void evict(UUID playerId) {
        flushPlayer(playerId);
        states.remove(playerId);
        dirtyStats.remove(playerId);
    }

    public void flushAll() {
        for (UUID playerId : dirtyStats) {
            flushPlayer(playerId);
        }
    }

    // ── Achievement checking ─────────────────────────────────────────────

    private void checkStatAchievements(UUID playerId, PlayerAchievementState state,
                                       MineAchievement.StatType statType, long currentValue) {
        for (MineAchievement achievement : MineAchievement.values()) {
            if (achievement.getStatType() != statType) continue;
            if (state.completed.contains(achievement.getId())) continue;
            if (currentValue >= achievement.getThreshold()) {
                grantAchievement(playerId, state, achievement);
            }
        }
    }

    private void grantAchievement(UUID playerId, PlayerAchievementState state, MineAchievement achievement) {
        if (!state.completed.add(achievement.getId())) return;

        // Grant crystal reward
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            MinePlayerStore mineStore = plugin.getMinePlayerStore();
            if (mineStore != null) {
                MinePlayerProgress progress = mineStore.getPlayer(playerId);
                if (progress != null) {
                    progress.addCrystals(achievement.getCrystalReward());
                    mineStore.markDirty(playerId);
                }
            }
        }

        // Save completion to DB
        saveAchievementCompletion(playerId, achievement.getId());

        // Send toast notification via chat (MineToastManager is for block toasts only)
        sendAchievementMessage(playerId, achievement);
    }

    private void sendAchievementMessage(UUID playerId, MineAchievement achievement) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        PlayerRef playerRef = plugin.getPlayerRef(playerId);
        if (playerRef == null) return;
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        var store = ref.getStore();
        if (store == null) return;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        player.sendMessage(Message.raw("[Achievement] " + achievement.getDisplayName()
            + " - +" + achievement.getCrystalReward() + " crystals!"));
    }

    // ── Loading / Saving ─────────────────────────────────────────────────

    private PlayerAchievementState getOrLoadState(UUID playerId) {
        PlayerAchievementState state = states.get(playerId);
        if (state != null) return state;

        state = loadFromDatabase(playerId);
        if (state == null) {
            state = new PlayerAchievementState();
        }
        PlayerAchievementState existing = states.putIfAbsent(playerId, state);
        return existing != null ? existing : state;
    }

    private PlayerAchievementState loadFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) return null;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return null;

            PlayerAchievementState state = new PlayerAchievementState();

            // Load stats
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT total_blocks_mined, total_crystals_earned FROM mine_player_stats WHERE player_uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        state.totalBlocksMined.set(rs.getLong("total_blocks_mined"));
                        state.totalCrystalsEarned.set(rs.getLong("total_crystals_earned"));
                    }
                }
            }

            // Load completed achievements
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT achievement_id FROM mine_achievements WHERE player_uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        state.completed.add(rs.getString("achievement_id"));
                    }
                }
            }

            return state;
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to load mine achievements for %s: %s", playerId, e.getMessage());
            return null;
        }
    }

    private void saveAchievementCompletion(UUID playerId, String achievementId) {
        if (!DatabaseManager.getInstance().isInitialized()) return;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO mine_achievements (player_uuid, achievement_id) VALUES (?, ?)")) {
                ps.setString(1, playerId.toString());
                ps.setString(2, achievementId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save mine achievement %s for %s: %s", achievementId, playerId, e.getMessage());
        }
    }

    private void queueSave() {
        if (pendingSave != null && !pendingSave.isDone()) return;
        pendingSave = HytaleServer.SCHEDULED_EXECUTOR.schedule(
            this::flushAll, 5, TimeUnit.SECONDS
        );
    }

    private void flushPlayer(UUID playerId) {
        if (!dirtyStats.remove(playerId)) return;
        PlayerAchievementState state = states.get(playerId);
        if (state == null) return;
        saveStatsSync(playerId, state);
    }

    private void saveStatsSync(UUID playerId, PlayerAchievementState state) {
        if (!DatabaseManager.getInstance().isInitialized()) return;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO mine_player_stats (player_uuid, total_blocks_mined, total_crystals_earned)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE total_blocks_mined = VALUES(total_blocks_mined),
                                            total_crystals_earned = VALUES(total_crystals_earned)
                    """)) {
                ps.setString(1, playerId.toString());
                ps.setLong(2, state.totalBlocksMined.get());
                ps.setLong(3, state.totalCrystalsEarned.get());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save mine player stats for %s: %s", playerId, e.getMessage());
        }
    }
}
