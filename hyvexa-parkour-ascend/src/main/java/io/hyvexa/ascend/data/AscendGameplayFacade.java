package io.hyvexa.ascend.data;

import io.hyvexa.ascend.AscendConstants.AchievementType;
import io.hyvexa.ascend.AscendConstants.SkillTreeNode;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Facade for gameplay operations: skill tree, achievements, run tracking, and tutorials.
 * Delegates to the shared player cache with dirty-marking for persistence.
 */
public class AscendGameplayFacade {

    private final Map<UUID, AscendPlayerProgress> players;
    private final AscendPlayerStore store;

    AscendGameplayFacade(Map<UUID, AscendPlayerProgress> players, AscendPlayerStore store) {
        this.players = players;
        this.store = store;
    }

    // ========================================
    // Skill Tree
    // ========================================

    public int getSkillTreePoints(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.gameplay().getSkillTreePoints() : 0;
    }

    public int addSkillTreePoints(UUID playerId, int amount) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        int newPoints = progress.gameplay().addSkillTreePoints(amount);
        store.markDirty(playerId);
        return newPoints;
    }

    public int getAvailableSkillPoints(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.gameplay().getAvailableSkillPoints() : 0;
    }

    public boolean hasSkillNode(UUID playerId, SkillTreeNode node) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.gameplay().hasSkillNode(node);
    }

    public boolean unlockSkillNode(UUID playerId, SkillTreeNode node) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        if (progress.gameplay().hasSkillNode(node)) {
            return false;
        }
        if (progress.gameplay().getAvailableSkillPoints() < node.getCost()) {
            return false;
        }
        progress.gameplay().unlockSkillNode(node);
        store.markDirty(playerId);
        return true;
    }

    public Set<SkillTreeNode> getUnlockedSkillNodes(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return EnumSet.noneOf(SkillTreeNode.class);
        }
        return progress.gameplay().getUnlockedSkillNodes();
    }

    // ========================================
    // Achievements
    // ========================================

    public boolean hasAchievement(UUID playerId, AchievementType achievement) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.gameplay().hasAchievement(achievement);
    }

    public boolean unlockAchievement(UUID playerId, AchievementType achievement) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        if (progress.gameplay().hasAchievement(achievement)) {
            return false;
        }
        progress.gameplay().unlockAchievement(achievement);
        store.markDirty(playerId);
        return true;
    }

    public Set<AchievementType> getUnlockedAchievements(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return EnumSet.noneOf(AchievementType.class);
        }
        return progress.gameplay().getUnlockedAchievements();
    }

    // ========================================
    // Run Tracking
    // ========================================

    public int getTotalManualRuns(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.gameplay().getTotalManualRuns() : 0;
    }

    public int incrementTotalManualRuns(UUID playerId) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        int count = progress.gameplay().incrementTotalManualRuns();
        store.markDirty(playerId);
        return count;
    }

    public int getConsecutiveManualRuns(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.gameplay().getConsecutiveManualRuns() : 0;
    }

    public int incrementConsecutiveManualRuns(UUID playerId) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        int count = progress.gameplay().incrementConsecutiveManualRuns();
        store.markDirty(playerId);
        return count;
    }

    public void resetConsecutiveManualRuns(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress != null) {
            progress.gameplay().resetConsecutiveManualRuns();
            store.markDirty(playerId);
        }
    }

    // ========================================
    // Tutorials
    // ========================================

    public boolean hasSeenTutorial(UUID playerId, int bit) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.gameplay().hasSeenTutorial(bit);
    }

    public void markTutorialSeen(UUID playerId, int bit) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.gameplay().markTutorialSeen(bit);
        store.markDirty(playerId);
    }
}
