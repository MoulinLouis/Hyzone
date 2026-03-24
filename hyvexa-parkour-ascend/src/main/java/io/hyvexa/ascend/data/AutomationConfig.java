package io.hyvexa.ascend.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AutomationConfig {

    private volatile boolean autoUpgradeEnabled;
    private volatile boolean autoEvolutionEnabled;
    private volatile boolean hideOtherRunners;
    private volatile boolean breakAscensionEnabled;
    private volatile boolean autoAscendEnabled;

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

    // ========================================
    // Automation Toggles
    // ========================================

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
    // Auto-Ascend
    // ========================================

    public boolean isAutoAscendEnabled() {
        return autoAscendEnabled;
    }

    public void setAutoAscendEnabled(boolean enabled) {
        this.autoAscendEnabled = enabled;
    }

    // ========================================
    // Inner Classes
    // ========================================

    public static class AutoSummitCategoryConfig {
        private boolean enabled;
        private int targetLevel;

        public AutoSummitCategoryConfig(boolean enabled, int targetLevel) {
            this.enabled = enabled;
            this.targetLevel = Math.max(0, targetLevel);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTargetLevel() {
            return targetLevel;
        }

        public void setTargetLevel(int targetLevel) {
            this.targetLevel = Math.max(0, targetLevel);
        }
    }
}
