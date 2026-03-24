package io.hyvexa.ascend.data;

import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.common.math.BigNumber;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class GameplayState {

    private final Map<String, MapProgress> mapProgress = new ConcurrentHashMap<>();

    // Ascension System
    private final AtomicInteger ascensionCount = new AtomicInteger(0);
    private final AtomicInteger skillTreePoints = new AtomicInteger(0);
    private final Set<AscendConstants.SkillTreeNode> unlockedSkillNodes = ConcurrentHashMap.newKeySet();

    // Achievement System
    private final Set<AscendConstants.AchievementType> unlockedAchievements = ConcurrentHashMap.newKeySet();
    private final AtomicInteger totalManualRuns = new AtomicInteger(0);
    private final AtomicInteger consecutiveManualRuns = new AtomicInteger(0); // For chain bonus tracking

    // Ascension Timer (for stats tracking)
    private volatile Long ascensionStartedAt; // Timestamp when current ascension run started
    private volatile Long fastestAscensionMs; // Best ascension time in milliseconds

    // Challenge system (in-memory only, persisted via ChallengeManager)
    private volatile AscendConstants.ChallengeType activeChallenge;
    private volatile long challengeStartedAtMs;

    // Permanent challenge rewards (never reset by ascension/challenge)
    private final Set<AscendConstants.ChallengeType> completedChallengeRewards = ConcurrentHashMap.newKeySet();

    // Transcendence System (4th Prestige)
    private final AtomicInteger transcendenceCount = new AtomicInteger(0);

    // Tutorial tracking (bitmask)
    private final AtomicInteger seenTutorials = new AtomicInteger(0);

    // Easter Egg - Cat Collector
    private final Set<String> foundCats = ConcurrentHashMap.newKeySet();

    // ========================================
    // Map Progress
    // ========================================

    public Map<String, MapProgress> getMapProgress() {
        return mapProgress;
    }

    public MapProgress getOrCreateMapProgress(String mapId) {
        return mapProgress.computeIfAbsent(mapId, k -> new MapProgress());
    }

    /**
     * Clears all map progress (multipliers, unlocks, robots) while preserving personal best times.
     */
    public void resetMapProgressPreservingPBs() {
        Map<String, Long> savedPBs = new HashMap<>();
        for (Map.Entry<String, MapProgress> entry : mapProgress.entrySet()) {
            Long bestTime = entry.getValue().getBestTimeMs();
            if (bestTime != null) {
                savedPBs.put(entry.getKey(), bestTime);
            }
        }
        mapProgress.clear();
        for (Map.Entry<String, Long> entry : savedPBs.entrySet()) {
            MapProgress mp = getOrCreateMapProgress(entry.getKey());
            mp.setBestTimeMs(entry.getValue());
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
        return skillTreePoints.get();
    }

    public void setSkillTreePoints(int skillTreePoints) {
        this.skillTreePoints.set(Math.max(0, skillTreePoints));
    }

    public int addSkillTreePoints(int amount) {
        return skillTreePoints.updateAndGet(v -> Math.max(0, v + amount));
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
        return Math.max(0, skillTreePoints.get() - getSpentSkillPoints());
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

    // ========================================
    // Manual Runs
    // ========================================

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

    public int getCompletedChallengeCount() {
        return completedChallengeRewards.size();
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

    // ========================================
    // Transcendence System (4th Prestige)
    // ========================================

    public int getTranscendenceCount() {
        return transcendenceCount.get();
    }

    public void setTranscendenceCount(int count) {
        this.transcendenceCount.set(Math.max(0, count));
    }

    public int incrementTranscendenceCount() {
        return transcendenceCount.incrementAndGet();
    }

    // ========================================
    // Tutorial Tracking
    // ========================================

    public int getSeenTutorials() {
        return seenTutorials.get();
    }

    public void setSeenTutorials(int seenTutorials) {
        this.seenTutorials.set(seenTutorials);
    }

    public boolean hasSeenTutorial(int bit) {
        return (seenTutorials.get() & bit) != 0;
    }

    public void markTutorialSeen(int bit) {
        seenTutorials.getAndUpdate(v -> v | bit);
    }

    // ========================================
    // Easter Egg - Cat Collector
    // ========================================

    public boolean hasFoundCat(String token) {
        return foundCats.contains(token);
    }

    public boolean addFoundCat(String token) {
        return foundCats.add(token);
    }

    public int getFoundCatCount() {
        return foundCats.size();
    }

    public Set<String> getFoundCats() {
        return Set.copyOf(foundCats);
    }

    public void setFoundCats(Set<String> cats) {
        foundCats.clear();
        if (cats != null) {
            foundCats.addAll(cats);
        }
    }

    // ========================================
    // MapProgress Inner Class
    // ========================================

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
