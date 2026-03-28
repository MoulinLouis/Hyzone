package io.hyvexa.ascend.mine.quest;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.core.db.ConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persistence layer for quest progress.
 * In-memory cache with dirty-tracking and deferred 5-second flush.
 */
public class MineQuestStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long FLUSH_DELAY_SECONDS = 5;

    private final ConnectionProvider db;
    private final ConcurrentHashMap<UUID, MineQuestProgress> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

    public MineQuestStore(ConnectionProvider db) {
        this.db = db;
    }

    public MineQuestProgress getProgress(UUID playerId) {
        return cache.get(playerId);
    }

    public MineQuestProgress getOrLoad(UUID playerId) {
        return cache.computeIfAbsent(playerId, this::loadFromDb);
    }

    public void markDirty(UUID playerId) {
        dirty.add(playerId);
        queueSave();
    }

    public void evict(UUID playerId) {
        if (dirty.remove(playerId)) {
            MineQuestProgress progress = cache.get(playerId);
            if (progress != null) {
                savePlayer(playerId, progress);
            }
        }
        cache.remove(playerId);
    }

    public void flushAll() {
        for (UUID playerId : dirty) {
            MineQuestProgress progress = cache.get(playerId);
            if (progress != null) {
                savePlayer(playerId, progress);
            }
        }
        dirty.clear();
    }

    private void queueSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                saveScheduled.set(false);
                Set<UUID> toSave = Set.copyOf(dirty);
                dirty.removeAll(toSave);
                for (UUID playerId : toSave) {
                    MineQuestProgress progress = cache.get(playerId);
                    if (progress != null) {
                        savePlayer(playerId, progress);
                    }
                }
            }, FLUSH_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    private MineQuestProgress loadFromDb(UUID playerId) {
        MineQuestProgress progress = new MineQuestProgress(playerId);
        if (!db.isInitialized()) return progress;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT chain_id, quest_index, objective_progress FROM mine_quest_progress WHERE player_uuid = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String chain = rs.getString("chain_id");
                    progress.setQuestIndex(chain, rs.getInt("quest_index"));
                    progress.setObjectiveProgress(chain, rs.getLong("objective_progress"));
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().log("Failed to load quest progress for " + playerId + ": " + e.getMessage());
        }
        return progress;
    }

    private void savePlayer(UUID playerId, MineQuestProgress progress) {
        if (!db.isInitialized()) return;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO mine_quest_progress (player_uuid, chain_id, quest_index, objective_progress) " +
                 "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE quest_index = VALUES(quest_index), " +
                 "objective_progress = VALUES(objective_progress)")) {
            // Save all chains the player has progress in
            MineQuest[] allQuests = MineQuest.values();
            Set<String> chains = new java.util.HashSet<>();
            for (MineQuest q : allQuests) chains.add(q.getChain());

            for (String chain : chains) {
                int questIndex = progress.getQuestIndex(chain);
                long objectiveProg = progress.getObjectiveProgress(chain);
                if (questIndex == 0 && objectiveProg == 0) continue;

                ps.setString(1, playerId.toString());
                ps.setString(2, chain);
                ps.setInt(3, questIndex);
                ps.setLong(4, objectiveProg);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            LOGGER.atWarning().log("Failed to save quest progress for " + playerId + ": " + e.getMessage());
        }
    }
}
