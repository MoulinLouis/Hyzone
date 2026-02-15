package io.hyvexa.ascend.data;

import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.common.math.BigNumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AscendPlayerProgress {

    private final AtomicReference<BigNumber> vexa = new AtomicReference<>(BigNumber.ZERO);
    private final AtomicInteger elevationMultiplier = new AtomicInteger(1);
    private final Map<String, MapProgress> mapProgress = new ConcurrentHashMap<>();

    // Summit System - XP per category (level calculated from XP)
    private final Map<AscendConstants.SummitCategory, Long> summitXp = new ConcurrentHashMap<>();
    private final AtomicReference<BigNumber> totalVexaEarned = new AtomicReference<>(BigNumber.ZERO);
    private final AtomicReference<BigNumber> summitAccumulatedVexa = new AtomicReference<>(BigNumber.ZERO);
    private final AtomicReference<BigNumber> elevationAccumulatedVexa = new AtomicReference<>(BigNumber.ZERO);

    // Ascension System
    private final AtomicInteger ascensionCount = new AtomicInteger(0);
    private volatile int skillTreePoints;
    private final Set<AscendConstants.SkillTreeNode> unlockedSkillNodes = ConcurrentHashMap.newKeySet();

    // Achievement System
    private final Set<AscendConstants.AchievementType> unlockedAchievements = ConcurrentHashMap.newKeySet();
    private final AtomicInteger totalManualRuns = new AtomicInteger(0);
    private final AtomicInteger consecutiveManualRuns = new AtomicInteger(0); // For chain bonus tracking
    private volatile boolean sessionFirstRunClaimed;

    // Ascension Timer (for stats tracking)
    private volatile Long ascensionStartedAt; // Timestamp when current ascension run started
    private volatile Long fastestAscensionMs; // Best ascension time in milliseconds

    // Passive earnings tracking
    private volatile Long lastActiveTimestamp;
    private volatile boolean hasUnclaimedPassive;

    // Automation toggles
    private volatile boolean autoUpgradeEnabled;
    private volatile boolean autoEvolutionEnabled;
    private volatile boolean hideOtherRunners;
    private volatile boolean breakAscensionEnabled;

    // Auto-elevation config
    private volatile boolean autoElevationEnabled;
    private volatile int autoElevationTimerSeconds;
    private volatile List<Long> autoElevationTargets = Collections.emptyList();
    private volatile int autoElevationTargetIndex;

    // Auto-summit config
    private volatile boolean autoSummitEnabled;
    private volatile int autoSummitTimerSeconds;
    private volatile List<AutoSummitCategoryConfig> autoSummitConfig = List.of(
        new AutoSummitCategoryConfig(false, 0),
        new AutoSummitCategoryConfig(false, 0),
        new AutoSummitCategoryConfig(false, 0)
    );
    private volatile int autoSummitRotationIndex;

    // Tutorial tracking (bitmask)
    private volatile int seenTutorials;

    // Challenge system (in-memory only, persisted via ChallengeManager)
    private volatile AscendConstants.ChallengeType activeChallenge;
    private volatile long challengeStartedAtMs;

    // Permanent challenge rewards (never reset by ascension/challenge)
    private final Set<AscendConstants.ChallengeType> completedChallengeRewards = ConcurrentHashMap.newKeySet();

    public BigNumber getVexa() {
        return vexa.get();
    }

    public void setVexa(BigNumber value) {
        this.vexa.set(value);
    }

    public boolean casVexa(BigNumber expect, BigNumber update) {
        return this.vexa.compareAndSet(expect, update);
    }

    public void addVexa(BigNumber amount) {
        vexa.updateAndGet(c -> c.add(amount).max(BigNumber.ZERO));
    }

    public int getElevationMultiplier() {
        return elevationMultiplier.get();
    }

    public void setElevationMultiplier(int elevationMultiplier) {
        this.elevationMultiplier.set(Math.max(1, elevationMultiplier));
    }

    public int addElevationMultiplier(int amount) {
        return elevationMultiplier.updateAndGet(current -> Math.max(1, current + amount));
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
        return summitXp.compute(category, (cat, current) -> {
            long base = current != null ? current : 0L;
            long newXp = Math.max(0, AscendConstants.saturatingAdd(base, amount));
            return Math.min(newXp, AscendConstants.SUMMIT_MAX_XP);
        });
    }

    public int getSummitLevel(AscendConstants.SummitCategory category) {
        return AscendConstants.calculateLevelFromXp(getSummitXp(category));
    }

    public Map<AscendConstants.SummitCategory, Long> getSummitXpMap() {
        Map<AscendConstants.SummitCategory, Long> xpMap = new EnumMap<>(AscendConstants.SummitCategory.class);
        for (AscendConstants.SummitCategory cat : AscendConstants.SummitCategory.values()) {
            xpMap.put(cat, getSummitXp(cat));
        }
        return xpMap;
    }

    public void clearSummitXp() {
        summitXp.clear();
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

    public BigNumber getTotalVexaEarned() {
        return totalVexaEarned.get();
    }

    public void setTotalVexaEarned(BigNumber value) {
        this.totalVexaEarned.set(value.max(BigNumber.ZERO));
    }

    public void addTotalVexaEarned(BigNumber amount) {
        if (amount.gt(BigNumber.ZERO)) {
            totalVexaEarned.updateAndGet(c -> c.add(amount).max(BigNumber.ZERO));
        }
    }

    public BigNumber getSummitAccumulatedVexa() {
        return summitAccumulatedVexa.get();
    }

    public void setSummitAccumulatedVexa(BigNumber value) {
        this.summitAccumulatedVexa.set(value.max(BigNumber.ZERO));
    }

    public void addSummitAccumulatedVexa(BigNumber amount) {
        if (amount.gt(BigNumber.ZERO)) {
            summitAccumulatedVexa.updateAndGet(c -> c.add(amount).max(BigNumber.ZERO));
        }
    }

    public BigNumber getElevationAccumulatedVexa() {
        return elevationAccumulatedVexa.get();
    }

    public void setElevationAccumulatedVexa(BigNumber value) {
        this.elevationAccumulatedVexa.set(value.max(BigNumber.ZERO));
    }

    public void addElevationAccumulatedVexa(BigNumber amount) {
        if (amount.gt(BigNumber.ZERO)) {
            elevationAccumulatedVexa.updateAndGet(c -> c.add(amount).max(BigNumber.ZERO));
        }
    }

    // ========================================
    // Ascension System
    // ========================================

    public int getAscensionCount() {
        return ascensionCount.get();
    }

    public void setAscensionCount(int ascensionCount) {
        this.ascensionCount.set(Math.max(0, ascensionCount));
    }

    public int incrementAscensionCount() {
        return ascensionCount.incrementAndGet();
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
        int total = 0;
        for (AscendConstants.SkillTreeNode node : unlockedSkillNodes) {
            total += node.getCost();
        }
        return total;
    }

    public int getAvailableSkillPoints() {
        return Math.max(0, skillTreePoints - getSpentSkillPoints());
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


    public int getTotalManualRuns() {
        return totalManualRuns.get();
    }

    public void setTotalManualRuns(int totalManualRuns) {
        this.totalManualRuns.set(Math.max(0, totalManualRuns));
    }

    public int incrementTotalManualRuns() {
        return totalManualRuns.incrementAndGet();
    }

    public int getConsecutiveManualRuns() {
        return consecutiveManualRuns.get();
    }

    public void setConsecutiveManualRuns(int consecutiveManualRuns) {
        this.consecutiveManualRuns.set(Math.max(0, consecutiveManualRuns));
    }

    public int incrementConsecutiveManualRuns() {
        return consecutiveManualRuns.incrementAndGet();
    }

    public void resetConsecutiveManualRuns() {
        this.consecutiveManualRuns.set(0);
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

    public boolean isAutoEvolutionEnabled() {
        return autoEvolutionEnabled;
    }

    public void setAutoEvolutionEnabled(boolean enabled) {
        this.autoEvolutionEnabled = enabled;
    }

    public boolean isHideOtherRunners() {
        return hideOtherRunners;
    }

    public void setHideOtherRunners(boolean hideOtherRunners) {
        this.hideOtherRunners = hideOtherRunners;
    }

    public boolean isBreakAscensionEnabled() {
        return breakAscensionEnabled;
    }

    public void setBreakAscensionEnabled(boolean breakAscensionEnabled) {
        this.breakAscensionEnabled = breakAscensionEnabled;
    }

    // ========================================
    // Auto-Elevation
    // ========================================

    public boolean isAutoElevationEnabled() {
        return autoElevationEnabled;
    }

    public void setAutoElevationEnabled(boolean enabled) {
        this.autoElevationEnabled = enabled;
    }

    public int getAutoElevationTimerSeconds() {
        return autoElevationTimerSeconds;
    }

    public void setAutoElevationTimerSeconds(int seconds) {
        this.autoElevationTimerSeconds = Math.max(0, seconds);
    }

    public List<Long> getAutoElevationTargets() {
        return autoElevationTargets;
    }

    public void setAutoElevationTargets(List<Long> targets) {
        this.autoElevationTargets = targets != null ? new ArrayList<>(targets) : Collections.emptyList();
    }

    public int getAutoElevationTargetIndex() {
        return autoElevationTargetIndex;
    }

    public void setAutoElevationTargetIndex(int index) {
        this.autoElevationTargetIndex = Math.max(0, index);
    }

    // ========================================
    // Auto-Summit
    // ========================================

    public boolean isAutoSummitEnabled() {
        return autoSummitEnabled;
    }

    public void setAutoSummitEnabled(boolean enabled) {
        this.autoSummitEnabled = enabled;
    }

    public int getAutoSummitTimerSeconds() {
        return autoSummitTimerSeconds;
    }

    public void setAutoSummitTimerSeconds(int seconds) {
        this.autoSummitTimerSeconds = Math.max(0, seconds);
    }

    public List<AutoSummitCategoryConfig> getAutoSummitConfig() {
        return autoSummitConfig;
    }

    public void setAutoSummitConfig(List<AutoSummitCategoryConfig> config) {
        this.autoSummitConfig = config != null ? new ArrayList<>(config) : List.of(
            new AutoSummitCategoryConfig(false, 0),
            new AutoSummitCategoryConfig(false, 0),
            new AutoSummitCategoryConfig(false, 0)
        );
    }

    public int getAutoSummitRotationIndex() {
        return autoSummitRotationIndex;
    }

    public void setAutoSummitRotationIndex(int index) {
        this.autoSummitRotationIndex = Math.max(0, index);
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

    // ========================================
    // Challenge System
    // ========================================

    public AscendConstants.ChallengeType getActiveChallenge() {
        return activeChallenge;
    }

    public void setActiveChallenge(AscendConstants.ChallengeType activeChallenge) {
        this.activeChallenge = activeChallenge;
    }

    public long getChallengeStartedAtMs() {
        return challengeStartedAtMs;
    }

    public void setChallengeStartedAtMs(long challengeStartedAtMs) {
        this.challengeStartedAtMs = challengeStartedAtMs;
    }

    public boolean hasChallengeReward(AscendConstants.ChallengeType type) {
        return completedChallengeRewards.contains(type);
    }

    public void addChallengeReward(AscendConstants.ChallengeType type) {
        completedChallengeRewards.add(type);
    }

    public Set<AscendConstants.ChallengeType> getCompletedChallengeRewards() {
        return Set.copyOf(completedChallengeRewards);
    }

    public void setCompletedChallengeRewards(Set<AscendConstants.ChallengeType> rewards) {
        completedChallengeRewards.clear();
        if (rewards != null) {
            completedChallengeRewards.addAll(rewards);
        }
    }

    public boolean hasAllChallengeRewards() {
        for (AscendConstants.ChallengeType type : AscendConstants.ChallengeType.values()) {
            if (!completedChallengeRewards.contains(type)) {
                return false;
            }
        }
        return true;
    }

    public static class AutoSummitCategoryConfig {
        private boolean enabled;
        private int increment;

        public AutoSummitCategoryConfig(boolean enabled, int increment) {
            this.enabled = enabled;
            this.increment = Math.max(1, increment);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getIncrement() {
            return increment;
        }

        public void setIncrement(int increment) {
            this.increment = Math.max(1, increment);
        }
    }

    public static class MapProgress {
        private volatile boolean unlocked;
        private volatile boolean completedManually;
        private volatile boolean hasRobot;
        private final AtomicInteger robotSpeedLevel = new AtomicInteger(0);
        private final AtomicInteger robotStars = new AtomicInteger(0);
        private final AtomicReference<BigNumber> multiplier = new AtomicReference<>(BigNumber.ONE);
        private volatile Long bestTimeMs;
        private volatile long momentumExpireTimeMs; // 0 = inactive (ephemeral, not persisted)
        private volatile long momentumDurationMs = AscendConstants.MOMENTUM_DURATION_MS; // total duration for progress bar

        public boolean isMomentumActive() {
            return System.currentTimeMillis() < momentumExpireTimeMs;
        }

        public void activateMomentum(long durationMs) {
            this.momentumDurationMs = durationMs;
            this.momentumExpireTimeMs = System.currentTimeMillis() + durationMs;
        }

        public long getMomentumExpireTimeMs() {
            return momentumExpireTimeMs;
        }

        public double getMomentumProgress() {
            long remaining = momentumExpireTimeMs - System.currentTimeMillis();
            if (remaining <= 0) return 0.0;
            return Math.min(1.0, remaining / (double) momentumDurationMs);
        }

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
            return robotSpeedLevel.get();
        }

        public void setRobotSpeedLevel(int robotSpeedLevel) {
            this.robotSpeedLevel.set(Math.max(0, robotSpeedLevel));
        }

        public int incrementRobotSpeedLevel() {
            return robotSpeedLevel.incrementAndGet();
        }

        public int getRobotStars() {
            return robotStars.get();
        }

        public void setRobotStars(int robotStars) {
            this.robotStars.set(Math.max(0, robotStars));
        }

        public int incrementRobotStars() {
            return robotStars.incrementAndGet();
        }

        public BigNumber getMultiplier() {
            return multiplier.get();
        }

        public void setMultiplier(BigNumber value) {
            this.multiplier.set(value.max(BigNumber.ONE));
        }

        public BigNumber addMultiplier(BigNumber amount) {
            if (amount.lte(BigNumber.ZERO)) {
                return multiplier.get();
            }
            return multiplier.updateAndGet(m -> m.add(amount).max(BigNumber.ONE));
        }

        public Long getBestTimeMs() {
            return bestTimeMs;
        }

        public void setBestTimeMs(Long bestTimeMs) {
            this.bestTimeMs = bestTimeMs;
        }
    }
}
