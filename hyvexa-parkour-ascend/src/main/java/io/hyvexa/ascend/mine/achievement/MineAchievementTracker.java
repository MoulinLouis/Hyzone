package io.hyvexa.ascend.mine.achievement;

import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Tracks mining achievements and stats per player.
 * Uses in-memory cache with deferred DB writes (same pattern as MinePlayerStore).
 */
public class MineAchievementTracker {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private static final long LEADERBOARD_CACHE_TTL_MS = 30_000;

    private final ConnectionProvider db;
    private final Map<UUID, PlayerAchievementState> states = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyStats = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    private final MinePlayerStore minePlayerStore;
    private final AscendPlayerStore playerStore;
    private final Function<UUID, PlayerRef> playerRefLookup;

    private volatile List<MineLeaderboardEntry> leaderboardCache = List.of();
    private volatile long leaderboardCacheTimestamp;

    public record MineLeaderboardEntry(UUID playerId, String playerName,
                                       long totalCrystalsEarned, long manualBlocksMined) {}

    public MineAchievementTracker(MinePlayerStore minePlayerStore, AscendPlayerStore playerStore,
                                  Function<UUID, PlayerRef> playerRefLookup, ConnectionProvider db) {
        this.db = db;
        this.minePlayerStore = minePlayerStore;
        this.playerStore = playerStore;
        this.playerRefLookup = playerRefLookup;
    }

    // ── State class ──────────────────────────────────────────────────────

    private static class PlayerAchievementState {
        final Set<String> completed = ConcurrentHashMap.newKeySet();
        final AtomicLong totalBlocksMined = new AtomicLong();
        final AtomicLong totalCrystalsEarned = new AtomicLong();
        final AtomicLong manualBlocksMined = new AtomicLong();
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
     * Increment manual blocks mined (excludes robot miners).
     */
    public void incrementManualBlocksMined(UUID playerId, int count) {
        PlayerAchievementState state = getOrLoadState(playerId);
        state.manualBlocksMined.addAndGet(count);
        dirtyStats.add(playerId);
        queueSave();
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

    public long getManualBlocksMined(UUID playerId) {
        return getOrLoadState(playerId).manualBlocksMined.get();
    }

    /**
     * Get the set of completed achievement IDs for a player.
     */
    public Set<String> getCompletedIds(UUID playerId) {
        return Set.copyOf(getOrLoadState(playerId).completed);
    }

    /**
     * Pre-load player state asynchronously on join so that later calls
     * to getOrLoadState don't block the game thread with synchronous DB reads.
     */
    public void onPlayerJoin(UUID playerId) {
        if (playerId == null) return;
        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> getOrLoadState(playerId));
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
        if (minePlayerStore != null) {
            MinePlayerProgress progress = minePlayerStore.getPlayer(playerId);
            if (progress != null) {
                progress.addCrystals(achievement.getCrystalReward());
                minePlayerStore.markDirty(playerId);
            }
        }

        // Save completion to DB
        saveAchievementCompletion(playerId, achievement.getId());

        // Send toast notification via chat (MineToastManager is for block toasts only)
        sendAchievementMessage(playerId, achievement);
    }

    private void sendAchievementMessage(UUID playerId, MineAchievement achievement) {
        PlayerRef playerRef = playerRefLookup != null ? playerRefLookup.apply(playerId) : null;
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

    // ── Leaderboard ────────────────────────────────────────────────────────

    public List<MineLeaderboardEntry> getMineLeaderboardEntries() {
        long now = System.currentTimeMillis();
        if (now - leaderboardCacheTimestamp > LEADERBOARD_CACHE_TTL_MS) {
            List<MineLeaderboardEntry> dbEntries = fetchLeaderboardFromDatabase();
            if (dbEntries != null) {
                leaderboardCache = dbEntries;
                leaderboardCacheTimestamp = now;
            }
        }

        // Merge online players' fresh data on top of the DB snapshot
        Map<UUID, MineLeaderboardEntry> merged = new LinkedHashMap<>();
        for (MineLeaderboardEntry entry : leaderboardCache) {
            merged.put(entry.playerId(), entry);
        }
        for (Map.Entry<UUID, PlayerAchievementState> e : states.entrySet()) {
            UUID id = e.getKey();
            PlayerAchievementState state = e.getValue();
            String name = resolvePlayerName(id);
            merged.put(id, new MineLeaderboardEntry(id, name,
                state.totalCrystalsEarned.get(), state.manualBlocksMined.get()));
        }
        return new ArrayList<>(merged.values());
    }

    private String resolvePlayerName(UUID playerId) {
        String name = playerStore != null ? playerStore.getPlayerName(playerId) : null;
        if (name != null) return name;
        PlayerRef playerRef = playerRefLookup != null ? playerRefLookup.apply(playerId) : null;
        if (playerRef != null) return playerRef.getUsername();
        return null;
    }

    private List<MineLeaderboardEntry> fetchLeaderboardFromDatabase() {
        if (!this.db.isInitialized()) return List.of();
        String sql = """
            SELECT s.player_uuid, p.player_name, s.total_crystals_earned, s.manual_blocks_mined
            FROM mine_player_stats s
            LEFT JOIN ascend_players p ON s.player_uuid = p.uuid
            WHERE s.total_crystals_earned > 0 OR s.manual_blocks_mined > 0
            """;
        List<MineLeaderboardEntry> entries = new ArrayList<>();
        try (Connection conn = this.db.getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                        String name = rs.getString("player_name");
                        long crystals = rs.getLong("total_crystals_earned");
                        long blocks = rs.getLong("manual_blocks_mined");
                        entries.add(new MineLeaderboardEntry(playerId, name, crystals, blocks));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to fetch mine leaderboard: %s", e.getMessage());
            return null;
        }
        return entries;
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
        if (!this.db.isInitialized()) return null;
        try (Connection conn = this.db.getConnection()) {
            if (conn == null) return null;

            PlayerAchievementState state = new PlayerAchievementState();

            // Load stats
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT total_blocks_mined, total_crystals_earned, manual_blocks_mined FROM mine_player_stats WHERE player_uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        state.totalBlocksMined.set(rs.getLong("total_blocks_mined"));
                        state.totalCrystalsEarned.set(rs.getLong("total_crystals_earned"));
                        state.manualBlocksMined.set(rs.getLong("manual_blocks_mined"));
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
        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> saveAchievementCompletionSync(playerId, achievementId));
    }

    private void saveAchievementCompletionSync(UUID playerId, String achievementId) {
        DatabaseManager.execute(this.db,
            "INSERT IGNORE INTO mine_achievements (player_uuid, achievement_id) VALUES (?, ?)",
            ps -> {
                ps.setString(1, playerId.toString());
                ps.setString(2, achievementId);
            });
    }

    private void queueSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                saveScheduled.set(false);
                flushAll();
            }, 5, TimeUnit.SECONDS);
        }
    }

    private void flushPlayer(UUID playerId) {
        if (!dirtyStats.remove(playerId)) return;
        PlayerAchievementState state = states.get(playerId);
        if (state == null) return;
        saveStatsSync(playerId, state);
    }

    private void saveStatsSync(UUID playerId, PlayerAchievementState state) {
        DatabaseManager.execute(this.db, """
                INSERT INTO mine_player_stats (player_uuid, total_blocks_mined, total_crystals_earned, manual_blocks_mined)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE total_blocks_mined = VALUES(total_blocks_mined),
                                        total_crystals_earned = VALUES(total_crystals_earned),
                                        manual_blocks_mined = VALUES(manual_blocks_mined)
                """,
            ps -> {
                ps.setString(1, playerId.toString());
                ps.setLong(2, state.totalBlocksMined.get());
                ps.setLong(3, state.totalCrystalsEarned.get());
                ps.setLong(4, state.manualBlocksMined.get());
            });
    }
}
