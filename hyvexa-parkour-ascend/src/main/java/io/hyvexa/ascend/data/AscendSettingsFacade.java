package io.hyvexa.ascend.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facade for player settings, automation config, and visibility toggles.
 * Delegates to the shared player cache with dirty-marking for persistence.
 */
public class AscendSettingsFacade {

    private final Map<UUID, AscendPlayerProgress> players;
    private final AscendPlayerStore store;

    AscendSettingsFacade(Map<UUID, AscendPlayerProgress> players, AscendPlayerStore store) {
        this.players = players;
        this.store = store;
    }

    // ========================================
    // Auto-Upgrade / Auto-Evolution
    // ========================================

    public boolean isAutoUpgradeEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.automation().isAutoUpgradeEnabled();
    }

    public void setAutoUpgradeEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.automation().setAutoUpgradeEnabled(enabled);
        store.markDirty(playerId);
    }

    public boolean isAutoEvolutionEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.automation().isAutoEvolutionEnabled();
    }

    public void setAutoEvolutionEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.automation().setAutoEvolutionEnabled(enabled);
        store.markDirty(playerId);
    }

    // ========================================
    // Auto-Elevation
    // ========================================

    public boolean isAutoElevationEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.automation().isAutoElevationEnabled();
    }

    public void setAutoElevationEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.automation().setAutoElevationEnabled(enabled);
        store.markDirty(playerId);
    }

    public int getAutoElevationTimerSeconds(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.automation().getAutoElevationTimerSeconds() : 0;
    }

    public void setAutoElevationTimerSeconds(UUID playerId, int seconds) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.automation().setAutoElevationTimerSeconds(seconds);
        store.markDirty(playerId);
    }

    public List<Long> getAutoElevationTargets(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.automation().getAutoElevationTargets() : Collections.emptyList();
    }

    public void setAutoElevationTargets(UUID playerId, List<Long> targets) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.automation().setAutoElevationTargets(targets);
        store.markDirty(playerId);
    }

    public int getAutoElevationTargetIndex(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.automation().getAutoElevationTargetIndex() : 0;
    }

    public void setAutoElevationTargetIndex(UUID playerId, int index) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        if (progress.automation().getAutoElevationTargetIndex() == index) {
            return;
        }
        progress.automation().setAutoElevationTargetIndex(index);
        store.markDirty(playerId);
    }

    // ========================================
    // Auto-Summit
    // ========================================

    public boolean isAutoSummitEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.automation().isAutoSummitEnabled();
    }

    public void setAutoSummitEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.automation().setAutoSummitEnabled(enabled);
        store.markDirty(playerId);
    }

    public int getAutoSummitTimerSeconds(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.automation().getAutoSummitTimerSeconds() : 0;
    }

    public void setAutoSummitTimerSeconds(UUID playerId, int seconds) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.automation().setAutoSummitTimerSeconds(seconds);
        store.markDirty(playerId);
    }

    public List<AutomationConfig.AutoSummitCategoryConfig> getAutoSummitConfig(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.automation().getAutoSummitConfig() : List.of();
    }

    public void setAutoSummitConfig(UUID playerId, List<AutomationConfig.AutoSummitCategoryConfig> config) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.automation().setAutoSummitConfig(config);
        store.markDirty(playerId);
    }

    public int getAutoSummitRotationIndex(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.automation().getAutoSummitRotationIndex() : 0;
    }

    public void setAutoSummitRotationIndex(UUID playerId, int index) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.automation().setAutoSummitRotationIndex(index);
        store.markDirty(playerId);
    }

    // ========================================
    // Auto-Ascend
    // ========================================

    public boolean isAutoAscendEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.automation().isAutoAscendEnabled();
    }

    public void setAutoAscendEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.automation().setAutoAscendEnabled(enabled);
        store.markDirty(playerId);
    }

    // ========================================
    // Break Ascension
    // ========================================

    public boolean isBreakAscensionEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.automation().isBreakAscensionEnabled();
    }

    public void setBreakAscensionEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.automation().setBreakAscensionEnabled(enabled);
        store.markDirty(playerId);
    }

    // ========================================
    // Visibility
    // ========================================

    public boolean isHideOtherRunners(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.automation().isHideOtherRunners();
    }

    public void setHideOtherRunners(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.automation().setHideOtherRunners(enabled);
        store.markDirty(playerId);
    }

    public boolean isHudHidden(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.session().isHudHidden();
    }

    public void setHudHidden(UUID playerId, boolean hidden) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.session().setHudHidden(hidden);
        store.markDirty(playerId);
    }

    public boolean isPlayersHidden(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.session().isPlayersHidden();
    }

    public void setPlayersHidden(UUID playerId, boolean hidden) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.session().setPlayersHidden(hidden);
        store.markDirty(playerId);
    }

    // ========================================
    // Session
    // ========================================

    public boolean isSessionFirstRunClaimed(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.session().isSessionFirstRunClaimed();
    }

    public void setSessionFirstRunClaimed(UUID playerId, boolean claimed) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.session().setSessionFirstRunClaimed(claimed);
        // Don't persist - this is session-only
    }
}
