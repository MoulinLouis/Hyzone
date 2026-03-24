package io.hyvexa.ascend.data;

import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.common.math.BigNumber;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class AscendPlayerProgress {

    private final EconomyState economy = new EconomyState();
    private final GameplayState gameplay = new GameplayState();
    private final AutomationConfig automation = new AutomationConfig();
    private final SessionState session = new SessionState();

    // ── Sub-object accessors ─────────────────────────────────────────

    public EconomyState economy() { return economy; }
    public GameplayState gameplay() { return gameplay; }
    public AutomationConfig automation() { return automation; }
    public SessionState session() { return session; }

    // ========================================
    // Deprecated delegation methods — Economy
    // ========================================

    @Deprecated public BigNumber getVolt() { return economy.getVolt(); }
    @Deprecated public void setVolt(BigNumber value) { economy.setVolt(value); }
    @Deprecated public boolean casVolt(BigNumber expect, BigNumber update) { return economy.casVolt(expect, update); }
    @Deprecated public void addVolt(BigNumber amount) { economy.addVolt(amount); }

    @Deprecated public int getElevationMultiplier() { return economy.getElevationMultiplier(); }
    @Deprecated public void setElevationMultiplier(int elevationMultiplier) { economy.setElevationMultiplier(elevationMultiplier); }
    @Deprecated public int addElevationMultiplier(int amount) { return economy.addElevationMultiplier(amount); }

    @Deprecated public double getSummitXp(AscendConstants.SummitCategory category) { return economy.getSummitXp(category); }
    @Deprecated public void setSummitXp(AscendConstants.SummitCategory category, double xp) { economy.setSummitXp(category, xp); }
    @Deprecated public double addSummitXp(AscendConstants.SummitCategory category, double amount) { return economy.addSummitXp(category, amount); }
    @Deprecated public int getSummitLevel(AscendConstants.SummitCategory category) { return economy.getSummitLevel(category); }
    @Deprecated public Map<AscendConstants.SummitCategory, Double> getSummitXpMap() { return economy.getSummitXpMap(); }
    @Deprecated public void clearSummitXp() { economy.clearSummitXp(); }
    @Deprecated public Map<AscendConstants.SummitCategory, Integer> getSummitLevels() { return economy.getSummitLevels(); }

    @Deprecated public BigNumber getTotalVoltEarned() { return economy.getTotalVoltEarned(); }
    @Deprecated public void setTotalVoltEarned(BigNumber value) { economy.setTotalVoltEarned(value); }
    @Deprecated public void addTotalVoltEarned(BigNumber amount) { economy.addTotalVoltEarned(amount); }

    @Deprecated public BigNumber getSummitAccumulatedVolt() { return economy.getSummitAccumulatedVolt(); }
    @Deprecated public void setSummitAccumulatedVolt(BigNumber value) { economy.setSummitAccumulatedVolt(value); }
    @Deprecated public void addSummitAccumulatedVolt(BigNumber amount) { economy.addSummitAccumulatedVolt(amount); }

    @Deprecated public BigNumber getElevationAccumulatedVolt() { return economy.getElevationAccumulatedVolt(); }
    @Deprecated public void setElevationAccumulatedVolt(BigNumber value) { economy.setElevationAccumulatedVolt(value); }
    @Deprecated public void addElevationAccumulatedVolt(BigNumber amount) { economy.addElevationAccumulatedVolt(amount); }

    // ========================================
    // Deprecated delegation methods — Gameplay
    // ========================================

    @Deprecated public Map<String, GameplayState.MapProgress> getMapProgress() { return gameplay.getMapProgress(); }
    @Deprecated public GameplayState.MapProgress getOrCreateMapProgress(String mapId) { return gameplay.getOrCreateMapProgress(mapId); }
    @Deprecated public void resetMapProgressPreservingPBs() { gameplay.resetMapProgressPreservingPBs(); }

    @Deprecated public int getAscensionCount() { return gameplay.getAscensionCount(); }
    @Deprecated public void setAscensionCount(int ascensionCount) { gameplay.setAscensionCount(ascensionCount); }
    @Deprecated public int incrementAscensionCount() { return gameplay.incrementAscensionCount(); }

    @Deprecated public int getSkillTreePoints() { return gameplay.getSkillTreePoints(); }
    @Deprecated public void setSkillTreePoints(int skillTreePoints) { gameplay.setSkillTreePoints(skillTreePoints); }
    @Deprecated public int addSkillTreePoints(int amount) { return gameplay.addSkillTreePoints(amount); }

    @Deprecated public boolean hasSkillNode(AscendConstants.SkillTreeNode node) { return gameplay.hasSkillNode(node); }
    @Deprecated public boolean unlockSkillNode(AscendConstants.SkillTreeNode node) { return gameplay.unlockSkillNode(node); }
    @Deprecated public Set<AscendConstants.SkillTreeNode> getUnlockedSkillNodes() { return gameplay.getUnlockedSkillNodes(); }
    @Deprecated public void setUnlockedSkillNodes(Set<AscendConstants.SkillTreeNode> nodes) { gameplay.setUnlockedSkillNodes(nodes); }
    @Deprecated public int getSpentSkillPoints() { return gameplay.getSpentSkillPoints(); }
    @Deprecated public int getAvailableSkillPoints() { return gameplay.getAvailableSkillPoints(); }

    @Deprecated public boolean hasAchievement(AscendConstants.AchievementType achievement) { return gameplay.hasAchievement(achievement); }
    @Deprecated public boolean unlockAchievement(AscendConstants.AchievementType achievement) { return gameplay.unlockAchievement(achievement); }
    @Deprecated public Set<AscendConstants.AchievementType> getUnlockedAchievements() { return gameplay.getUnlockedAchievements(); }
    @Deprecated public void setUnlockedAchievements(Set<AscendConstants.AchievementType> achievements) { gameplay.setUnlockedAchievements(achievements); }

    @Deprecated public int getTotalManualRuns() { return gameplay.getTotalManualRuns(); }
    @Deprecated public void setTotalManualRuns(int totalManualRuns) { gameplay.setTotalManualRuns(totalManualRuns); }
    @Deprecated public int incrementTotalManualRuns() { return gameplay.incrementTotalManualRuns(); }

    @Deprecated public int getConsecutiveManualRuns() { return gameplay.getConsecutiveManualRuns(); }
    @Deprecated public void setConsecutiveManualRuns(int consecutiveManualRuns) { gameplay.setConsecutiveManualRuns(consecutiveManualRuns); }
    @Deprecated public int incrementConsecutiveManualRuns() { return gameplay.incrementConsecutiveManualRuns(); }
    @Deprecated public void resetConsecutiveManualRuns() { gameplay.resetConsecutiveManualRuns(); }

    @Deprecated public Long getAscensionStartedAt() { return gameplay.getAscensionStartedAt(); }
    @Deprecated public void setAscensionStartedAt(Long ascensionStartedAt) { gameplay.setAscensionStartedAt(ascensionStartedAt); }
    @Deprecated public Long getFastestAscensionMs() { return gameplay.getFastestAscensionMs(); }
    @Deprecated public void setFastestAscensionMs(Long fastestAscensionMs) { gameplay.setFastestAscensionMs(fastestAscensionMs); }

    @Deprecated public AscendConstants.ChallengeType getActiveChallenge() { return gameplay.getActiveChallenge(); }
    @Deprecated public void setActiveChallenge(AscendConstants.ChallengeType activeChallenge) { gameplay.setActiveChallenge(activeChallenge); }
    @Deprecated public long getChallengeStartedAtMs() { return gameplay.getChallengeStartedAtMs(); }
    @Deprecated public void setChallengeStartedAtMs(long challengeStartedAtMs) { gameplay.setChallengeStartedAtMs(challengeStartedAtMs); }

    @Deprecated public boolean hasChallengeReward(AscendConstants.ChallengeType type) { return gameplay.hasChallengeReward(type); }
    @Deprecated public void addChallengeReward(AscendConstants.ChallengeType type) { gameplay.addChallengeReward(type); }
    @Deprecated public Set<AscendConstants.ChallengeType> getCompletedChallengeRewards() { return gameplay.getCompletedChallengeRewards(); }
    @Deprecated public int getCompletedChallengeCount() { return gameplay.getCompletedChallengeCount(); }
    @Deprecated public void setCompletedChallengeRewards(Set<AscendConstants.ChallengeType> rewards) { gameplay.setCompletedChallengeRewards(rewards); }
    @Deprecated public boolean hasAllChallengeRewards() { return gameplay.hasAllChallengeRewards(); }

    @Deprecated public int getTranscendenceCount() { return gameplay.getTranscendenceCount(); }
    @Deprecated public void setTranscendenceCount(int count) { gameplay.setTranscendenceCount(count); }
    @Deprecated public int incrementTranscendenceCount() { return gameplay.incrementTranscendenceCount(); }

    @Deprecated public int getSeenTutorials() { return gameplay.getSeenTutorials(); }
    @Deprecated public void setSeenTutorials(int seenTutorials) { gameplay.setSeenTutorials(seenTutorials); }
    @Deprecated public boolean hasSeenTutorial(int bit) { return gameplay.hasSeenTutorial(bit); }
    @Deprecated public void markTutorialSeen(int bit) { gameplay.markTutorialSeen(bit); }

    @Deprecated public boolean hasFoundCat(String token) { return gameplay.hasFoundCat(token); }
    @Deprecated public boolean addFoundCat(String token) { return gameplay.addFoundCat(token); }
    @Deprecated public int getFoundCatCount() { return gameplay.getFoundCatCount(); }
    @Deprecated public Set<String> getFoundCats() { return gameplay.getFoundCats(); }
    @Deprecated public void setFoundCats(Set<String> cats) { gameplay.setFoundCats(cats); }

    // ========================================
    // Deprecated delegation methods — Automation
    // ========================================

    @Deprecated public boolean isAutoUpgradeEnabled() { return automation.isAutoUpgradeEnabled(); }
    @Deprecated public void setAutoUpgradeEnabled(boolean enabled) { automation.setAutoUpgradeEnabled(enabled); }

    @Deprecated public boolean isAutoEvolutionEnabled() { return automation.isAutoEvolutionEnabled(); }
    @Deprecated public void setAutoEvolutionEnabled(boolean enabled) { automation.setAutoEvolutionEnabled(enabled); }

    @Deprecated public boolean isHideOtherRunners() { return automation.isHideOtherRunners(); }
    @Deprecated public void setHideOtherRunners(boolean hideOtherRunners) { automation.setHideOtherRunners(hideOtherRunners); }

    @Deprecated public boolean isBreakAscensionEnabled() { return automation.isBreakAscensionEnabled(); }
    @Deprecated public void setBreakAscensionEnabled(boolean breakAscensionEnabled) { automation.setBreakAscensionEnabled(breakAscensionEnabled); }

    @Deprecated public boolean isAutoAscendEnabled() { return automation.isAutoAscendEnabled(); }
    @Deprecated public void setAutoAscendEnabled(boolean enabled) { automation.setAutoAscendEnabled(enabled); }

    @Deprecated public boolean isAutoElevationEnabled() { return automation.isAutoElevationEnabled(); }
    @Deprecated public void setAutoElevationEnabled(boolean enabled) { automation.setAutoElevationEnabled(enabled); }
    @Deprecated public int getAutoElevationTimerSeconds() { return automation.getAutoElevationTimerSeconds(); }
    @Deprecated public void setAutoElevationTimerSeconds(int seconds) { automation.setAutoElevationTimerSeconds(seconds); }
    @Deprecated public List<Long> getAutoElevationTargets() { return automation.getAutoElevationTargets(); }
    @Deprecated public void setAutoElevationTargets(List<Long> targets) { automation.setAutoElevationTargets(targets); }
    @Deprecated public int getAutoElevationTargetIndex() { return automation.getAutoElevationTargetIndex(); }
    @Deprecated public void setAutoElevationTargetIndex(int index) { automation.setAutoElevationTargetIndex(index); }

    @Deprecated public boolean isAutoSummitEnabled() { return automation.isAutoSummitEnabled(); }
    @Deprecated public void setAutoSummitEnabled(boolean enabled) { automation.setAutoSummitEnabled(enabled); }
    @Deprecated public int getAutoSummitTimerSeconds() { return automation.getAutoSummitTimerSeconds(); }
    @Deprecated public void setAutoSummitTimerSeconds(int seconds) { automation.setAutoSummitTimerSeconds(seconds); }
    @Deprecated public List<AutomationConfig.AutoSummitCategoryConfig> getAutoSummitConfig() { return automation.getAutoSummitConfig(); }
    @Deprecated public void setAutoSummitConfig(List<AutomationConfig.AutoSummitCategoryConfig> config) { automation.setAutoSummitConfig(config); }
    @Deprecated public int getAutoSummitRotationIndex() { return automation.getAutoSummitRotationIndex(); }
    @Deprecated public void setAutoSummitRotationIndex(int index) { automation.setAutoSummitRotationIndex(index); }

    // ========================================
    // Deprecated delegation methods — Session
    // ========================================

    @Deprecated public Long getLastActiveTimestamp() { return session.getLastActiveTimestamp(); }
    @Deprecated public void setLastActiveTimestamp(Long timestamp) { session.setLastActiveTimestamp(timestamp); }

    @Deprecated public boolean hasUnclaimedPassive() { return session.hasUnclaimedPassive(); }
    @Deprecated public void setHasUnclaimedPassive(boolean hasUnclaimed) { session.setHasUnclaimedPassive(hasUnclaimed); }

    @Deprecated public boolean isSessionFirstRunClaimed() { return session.isSessionFirstRunClaimed(); }
    @Deprecated public void setSessionFirstRunClaimed(boolean sessionFirstRunClaimed) { session.setSessionFirstRunClaimed(sessionFirstRunClaimed); }

    @Deprecated public boolean isHudHidden() { return session.isHudHidden(); }
    @Deprecated public void setHudHidden(boolean hudHidden) { session.setHudHidden(hudHidden); }

    @Deprecated public boolean isPlayersHidden() { return session.isPlayersHidden(); }
    @Deprecated public void setPlayersHidden(boolean playersHidden) { session.setPlayersHidden(playersHidden); }
}
