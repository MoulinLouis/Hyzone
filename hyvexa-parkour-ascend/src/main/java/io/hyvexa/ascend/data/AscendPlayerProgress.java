package io.hyvexa.ascend.data;

import io.hyvexa.ascend.AscendConstants;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AscendPlayerProgress {

    private long coins;
    private int elevationMultiplier = 1;
    private final Map<String, MapProgress> mapProgress = new ConcurrentHashMap<>();

    // Summit System - levels per category
    private final Map<AscendConstants.SummitCategory, Integer> summitLevels = new ConcurrentHashMap<>();
    private long totalCoinsEarned; // Lifetime coins for achievements

    // Ascension System
    private int ascensionCount;
    private int skillTreePoints;
    private final Set<AscendConstants.SkillTreeNode> unlockedSkillNodes = ConcurrentHashMap.newKeySet();

    // Achievement System
    private final Set<AscendConstants.AchievementType> unlockedAchievements = ConcurrentHashMap.newKeySet();
    private String activeTitle;
    private int totalManualRuns;
    private int consecutiveManualRuns; // For chain bonus tracking
    private boolean sessionFirstRunClaimed;

    public long getCoins() {
        return coins;
    }

    public void setCoins(long coins) {
        this.coins = coins;
    }

    public void addCoins(long amount) {
        this.coins = Math.max(0, this.coins + amount);
    }

    public int getElevationMultiplier() {
        return elevationMultiplier;
    }

    public void setElevationMultiplier(int elevationMultiplier) {
        this.elevationMultiplier = Math.max(1, elevationMultiplier);
    }

    public int addElevationMultiplier(int amount) {
        elevationMultiplier = Math.max(1, elevationMultiplier + amount);
        return elevationMultiplier;
    }

    public Map<String, MapProgress> getMapProgress() {
        return mapProgress;
    }

    public MapProgress getOrCreateMapProgress(String mapId) {
        return mapProgress.computeIfAbsent(mapId, k -> new MapProgress());
    }

    // ========================================
    // Summit System
    // ========================================

    public int getSummitLevel(AscendConstants.SummitCategory category) {
        return summitLevels.getOrDefault(category, 0);
    }

    public void setSummitLevel(AscendConstants.SummitCategory category, int level) {
        summitLevels.put(category, Math.max(0, level));
    }

    public int addSummitLevel(AscendConstants.SummitCategory category, int amount) {
        int current = getSummitLevel(category);
        int newLevel = Math.max(0, current + amount);
        summitLevels.put(category, newLevel);
        return newLevel;
    }

    public Map<AscendConstants.SummitCategory, Integer> getSummitLevels() {
        return new EnumMap<>(AscendConstants.SummitCategory.class) {{
            for (AscendConstants.SummitCategory cat : AscendConstants.SummitCategory.values()) {
                put(cat, getSummitLevel(cat));
            }
        }};
    }

    public void clearSummitLevels() {
        summitLevels.clear();
    }

    public long getTotalCoinsEarned() {
        return totalCoinsEarned;
    }

    public void setTotalCoinsEarned(long totalCoinsEarned) {
        this.totalCoinsEarned = Math.max(0, totalCoinsEarned);
    }

    public void addTotalCoinsEarned(long amount) {
        if (amount > 0) {
            this.totalCoinsEarned = Math.max(0, this.totalCoinsEarned + amount);
        }
    }

    // ========================================
    // Ascension System
    // ========================================

    public int getAscensionCount() {
        return ascensionCount;
    }

    public void setAscensionCount(int ascensionCount) {
        this.ascensionCount = Math.max(0, ascensionCount);
    }

    public int incrementAscensionCount() {
        return ++ascensionCount;
    }

    public int getSkillTreePoints() {
        return skillTreePoints;
    }

    public void setSkillTreePoints(int skillTreePoints) {
        this.skillTreePoints = Math.max(0, skillTreePoints);
    }

    public int addSkillTreePoints(int amount) {
        skillTreePoints = Math.max(0, skillTreePoints + amount);
        return skillTreePoints;
    }

    public boolean hasSkillNode(AscendConstants.SkillTreeNode node) {
        return unlockedSkillNodes.contains(node);
    }

    public boolean unlockSkillNode(AscendConstants.SkillTreeNode node) {
        return unlockedSkillNodes.add(node);
    }

    public Set<AscendConstants.SkillTreeNode> getUnlockedSkillNodes() {
        return EnumSet.copyOf(unlockedSkillNodes.isEmpty()
            ? EnumSet.noneOf(AscendConstants.SkillTreeNode.class)
            : unlockedSkillNodes);
    }

    public void setUnlockedSkillNodes(Set<AscendConstants.SkillTreeNode> nodes) {
        unlockedSkillNodes.clear();
        if (nodes != null) {
            unlockedSkillNodes.addAll(nodes);
        }
    }

    public int getSpentSkillPoints() {
        return unlockedSkillNodes.size();
    }

    public int getAvailableSkillPoints() {
        return Math.max(0, skillTreePoints - unlockedSkillNodes.size());
    }

    // ========================================
    // Achievement System
    // ========================================

    public boolean hasAchievement(AscendConstants.AchievementType achievement) {
        return unlockedAchievements.contains(achievement);
    }

    public boolean unlockAchievement(AscendConstants.AchievementType achievement) {
        return unlockedAchievements.add(achievement);
    }

    public Set<AscendConstants.AchievementType> getUnlockedAchievements() {
        return EnumSet.copyOf(unlockedAchievements.isEmpty()
            ? EnumSet.noneOf(AscendConstants.AchievementType.class)
            : unlockedAchievements);
    }

    public void setUnlockedAchievements(Set<AscendConstants.AchievementType> achievements) {
        unlockedAchievements.clear();
        if (achievements != null) {
            unlockedAchievements.addAll(achievements);
        }
    }

    public String getActiveTitle() {
        return activeTitle;
    }

    public void setActiveTitle(String activeTitle) {
        this.activeTitle = activeTitle;
    }

    public int getTotalManualRuns() {
        return totalManualRuns;
    }

    public void setTotalManualRuns(int totalManualRuns) {
        this.totalManualRuns = Math.max(0, totalManualRuns);
    }

    public int incrementTotalManualRuns() {
        return ++totalManualRuns;
    }

    public int getConsecutiveManualRuns() {
        return consecutiveManualRuns;
    }

    public void setConsecutiveManualRuns(int consecutiveManualRuns) {
        this.consecutiveManualRuns = Math.max(0, consecutiveManualRuns);
    }

    public int incrementConsecutiveManualRuns() {
        return ++consecutiveManualRuns;
    }

    public void resetConsecutiveManualRuns() {
        this.consecutiveManualRuns = 0;
    }

    public boolean isSessionFirstRunClaimed() {
        return sessionFirstRunClaimed;
    }

    public void setSessionFirstRunClaimed(boolean sessionFirstRunClaimed) {
        this.sessionFirstRunClaimed = sessionFirstRunClaimed;
    }

    public static class MapProgress {
        private boolean unlocked;
        private boolean completedManually;
        private boolean hasRobot;
        private int robotSpeedLevel;
        private int robotStars;
        private double multiplier = 1.0;
        private Long bestTimeMs;

        public boolean isUnlocked() {
            return unlocked;
        }

        public void setUnlocked(boolean unlocked) {
            this.unlocked = unlocked;
        }

        public boolean isCompletedManually() {
            return completedManually;
        }

        public void setCompletedManually(boolean completedManually) {
            this.completedManually = completedManually;
        }

        public boolean hasRobot() {
            return hasRobot;
        }

        public void setHasRobot(boolean hasRobot) {
            this.hasRobot = hasRobot;
        }

        public int getRobotSpeedLevel() {
            return robotSpeedLevel;
        }

        public void setRobotSpeedLevel(int robotSpeedLevel) {
            this.robotSpeedLevel = Math.max(0, robotSpeedLevel);
        }

        public int incrementRobotSpeedLevel() {
            robotSpeedLevel = Math.max(0, robotSpeedLevel) + 1;
            return robotSpeedLevel;
        }

        public int getRobotStars() {
            return robotStars;
        }

        public void setRobotStars(int robotStars) {
            this.robotStars = Math.max(0, robotStars);
        }

        public int incrementRobotStars() {
            robotStars = Math.max(0, robotStars) + 1;
            return robotStars;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = Math.max(1.0, multiplier);
        }

        public double addMultiplier(double amount) {
            if (amount <= 0.0) {
                return multiplier;
            }
            multiplier = Math.max(1.0, multiplier + amount);
            return multiplier;
        }

        public Long getBestTimeMs() {
            return bestTimeMs;
        }

        public void setBestTimeMs(Long bestTimeMs) {
            this.bestTimeMs = bestTimeMs;
        }
    }
}
