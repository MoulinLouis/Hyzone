package io.hyvexa.ascend.data;

import io.hyvexa.ascend.AscendConstants;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class AscendPlayerProgress {

    private final AtomicReference<BigDecimal> coins = new AtomicReference<>(BigDecimal.ZERO);
    private volatile int elevationMultiplier = 1;
    private final Map<String, MapProgress> mapProgress = new ConcurrentHashMap<>();

    // Summit System - XP per category (level calculated from XP)
    private final Map<AscendConstants.SummitCategory, Long> summitXp = new ConcurrentHashMap<>();
    private final AtomicReference<BigDecimal> totalCoinsEarned = new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicReference<BigDecimal> summitAccumulatedCoins = new AtomicReference<>(BigDecimal.ZERO);

    // Ascension System
    private volatile int ascensionCount;
    private volatile int skillTreePoints;
    private final Set<AscendConstants.SkillTreeNode> unlockedSkillNodes = ConcurrentHashMap.newKeySet();

    // Achievement System
    private final Set<AscendConstants.AchievementType> unlockedAchievements = ConcurrentHashMap.newKeySet();
    private volatile String activeTitle;
    private volatile int totalManualRuns;
    private volatile int consecutiveManualRuns; // For chain bonus tracking
    private volatile boolean sessionFirstRunClaimed;

    // Ascension Timer (for stats tracking)
    private volatile Long ascensionStartedAt; // Timestamp when current ascension run started
    private volatile Long fastestAscensionMs; // Best ascension time in milliseconds

    // Passive earnings tracking
    private volatile Long lastActiveTimestamp;
    private volatile boolean hasUnclaimedPassive;

    // Automation toggle
    private volatile boolean autoUpgradeEnabled;

    // Tutorial tracking (bitmask)
    private volatile int seenTutorials;

    public BigDecimal getCoins() {
        return coins.get();
    }

    public void setCoins(BigDecimal value) {
        this.coins.set(value);
    }

    public void addCoins(BigDecimal amount) {
        coins.updateAndGet(c -> c.add(amount).max(BigDecimal.ZERO));
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
    // Summit System (XP-based)
    // ========================================

    public long getSummitXp(AscendConstants.SummitCategory category) {
        return summitXp.getOrDefault(category, 0L);
    }

    public void setSummitXp(AscendConstants.SummitCategory category, long xp) {
        summitXp.put(category, Math.max(0, xp));
    }

    public long addSummitXp(AscendConstants.SummitCategory category, long amount) {
        long current = getSummitXp(category);
        long newXp = Math.max(0, current + amount);
        summitXp.put(category, newXp);
        return newXp;
    }

    public int getSummitLevel(AscendConstants.SummitCategory category) {
        return AscendConstants.calculateLevelFromXp(getSummitXp(category));
    }

    public Map<AscendConstants.SummitCategory, Long> getSummitXpMap() {
        return new EnumMap<>(AscendConstants.SummitCategory.class) {{
            for (AscendConstants.SummitCategory cat : AscendConstants.SummitCategory.values()) {
                put(cat, getSummitXp(cat));
            }
        }};
    }

    public void clearSummitXp() {
        summitXp.clear();
    }

    /**
     * @deprecated Use {@link #clearSummitXp()} instead.
     */
    @Deprecated
    public void clearSummitLevels() {
        clearSummitXp();
    }

    /**
     * Get all summit levels as a map.
     */
    public Map<AscendConstants.SummitCategory, Integer> getSummitLevels() {
        Map<AscendConstants.SummitCategory, Integer> levels = new EnumMap<>(AscendConstants.SummitCategory.class);
        for (AscendConstants.SummitCategory cat : AscendConstants.SummitCategory.values()) {
            levels.put(cat, getSummitLevel(cat));
        }
        return levels;
    }

    public BigDecimal getTotalCoinsEarned() {
        return totalCoinsEarned.get();
    }

    public void setTotalCoinsEarned(BigDecimal value) {
        this.totalCoinsEarned.set(value.max(BigDecimal.ZERO));
    }

    public void addTotalCoinsEarned(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            totalCoinsEarned.updateAndGet(c -> c.add(amount).max(BigDecimal.ZERO));
        }
    }

    public BigDecimal getSummitAccumulatedCoins() {
        return summitAccumulatedCoins.get();
    }

    public void setSummitAccumulatedCoins(BigDecimal value) {
        this.summitAccumulatedCoins.set(value.max(BigDecimal.ZERO));
    }

    public void addSummitAccumulatedCoins(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            summitAccumulatedCoins.updateAndGet(c -> c.add(amount).max(BigDecimal.ZERO));
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

    // ========================================
    // Ascension Timer (Stats)
    // ========================================

    public Long getAscensionStartedAt() {
        return ascensionStartedAt;
    }

    public void setAscensionStartedAt(Long ascensionStartedAt) {
        this.ascensionStartedAt = ascensionStartedAt;
    }

    public Long getFastestAscensionMs() {
        return fastestAscensionMs;
    }

    public void setFastestAscensionMs(Long fastestAscensionMs) {
        this.fastestAscensionMs = fastestAscensionMs;
    }

    // ========================================
    // Passive Earnings
    // ========================================

    public Long getLastActiveTimestamp() {
        return lastActiveTimestamp;
    }

    public void setLastActiveTimestamp(Long timestamp) {
        this.lastActiveTimestamp = timestamp;
    }

    public boolean hasUnclaimedPassive() {
        return hasUnclaimedPassive;
    }

    public void setHasUnclaimedPassive(boolean hasUnclaimed) {
        this.hasUnclaimedPassive = hasUnclaimed;
    }

    public boolean isAutoUpgradeEnabled() {
        return autoUpgradeEnabled;
    }

    public void setAutoUpgradeEnabled(boolean enabled) {
        this.autoUpgradeEnabled = enabled;
    }

    // ========================================
    // Tutorial Tracking
    // ========================================

    public int getSeenTutorials() {
        return seenTutorials;
    }

    public void setSeenTutorials(int seenTutorials) {
        this.seenTutorials = seenTutorials;
    }

    public boolean hasSeenTutorial(int bit) {
        return (seenTutorials & bit) != 0;
    }

    public void markTutorialSeen(int bit) {
        seenTutorials |= bit;
    }

    public static class MapProgress {
        private volatile boolean unlocked;
        private volatile boolean completedManually;
        private volatile boolean hasRobot;
        private volatile int robotSpeedLevel;
        private volatile int robotStars;
        private final AtomicReference<BigDecimal> multiplier = new AtomicReference<>(BigDecimal.ONE);
        private volatile Long bestTimeMs;

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

        public BigDecimal getMultiplier() {
            return multiplier.get();
        }

        public void setMultiplier(BigDecimal value) {
            this.multiplier.set(value.max(BigDecimal.ONE));
        }

        public BigDecimal addMultiplier(BigDecimal amount) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return multiplier.get();
            }
            return multiplier.updateAndGet(m -> m.add(amount).max(BigDecimal.ONE));
        }

        public Long getBestTimeMs() {
            return bestTimeMs;
        }

        public void setBestTimeMs(Long bestTimeMs) {
            this.bestTimeMs = bestTimeMs;
        }
    }
}
