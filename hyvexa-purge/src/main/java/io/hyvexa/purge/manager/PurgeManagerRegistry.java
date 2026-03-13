package io.hyvexa.purge.manager;

import io.hyvexa.purge.hud.PurgeHudManager;
import io.hyvexa.purge.mission.PurgeMissionManager;

/**
 * Central registry that wires all Purge manager cross-references atomically.
 * Replaces scattered setter injection with a single build step.
 */
public class PurgeManagerRegistry {

    private final PurgeSessionManager sessionManager;
    private final PurgeWaveManager waveManager;
    private final PurgePartyManager partyManager;
    private final PurgeHudManager hudManager;
    private final PurgeUpgradeManager upgradeManager;
    private final WeaponXpManager weaponXpManager;
    private final PurgeClassManager classManager;
    private final PurgeMissionManager missionManager;

    private PurgeManagerRegistry(Builder builder) {
        this.sessionManager = builder.sessionManager;
        this.waveManager = builder.waveManager;
        this.partyManager = builder.partyManager;
        this.hudManager = builder.hudManager;
        this.upgradeManager = builder.upgradeManager;
        this.weaponXpManager = builder.weaponXpManager;
        this.classManager = builder.classManager;
        this.missionManager = builder.missionManager;
    }

    public PurgeSessionManager getSessionManager() { return sessionManager; }
    public PurgeWaveManager getWaveManager() { return waveManager; }
    public PurgePartyManager getPartyManager() { return partyManager; }
    public PurgeHudManager getHudManager() { return hudManager; }
    public PurgeUpgradeManager getUpgradeManager() { return upgradeManager; }
    public WeaponXpManager getWeaponXpManager() { return weaponXpManager; }
    public PurgeClassManager getClassManager() { return classManager; }
    public PurgeMissionManager getMissionManager() { return missionManager; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private PurgeSessionManager sessionManager;
        private PurgeWaveManager waveManager;
        private PurgePartyManager partyManager;
        private PurgeHudManager hudManager;
        private PurgeUpgradeManager upgradeManager;
        private WeaponXpManager weaponXpManager;
        private PurgeClassManager classManager;
        private PurgeMissionManager missionManager;

        public Builder sessionManager(PurgeSessionManager m) { this.sessionManager = m; return this; }
        public Builder waveManager(PurgeWaveManager m) { this.waveManager = m; return this; }
        public Builder partyManager(PurgePartyManager m) { this.partyManager = m; return this; }
        public Builder hudManager(PurgeHudManager m) { this.hudManager = m; return this; }
        public Builder upgradeManager(PurgeUpgradeManager m) { this.upgradeManager = m; return this; }
        public Builder weaponXpManager(WeaponXpManager m) { this.weaponXpManager = m; return this; }
        public Builder classManager(PurgeClassManager m) { this.classManager = m; return this; }
        public Builder missionManager(PurgeMissionManager m) { this.missionManager = m; return this; }

        public PurgeManagerRegistry build() {
            if (sessionManager == null || waveManager == null || partyManager == null
                    || hudManager == null || upgradeManager == null || weaponXpManager == null
                    || classManager == null || missionManager == null) {
                throw new IllegalStateException("All managers must be set before building PurgeManagerRegistry");
            }
            PurgeManagerRegistry registry = new PurgeManagerRegistry(this);
            sessionManager.initRegistry(registry);
            waveManager.initRegistry(registry);
            partyManager.initRegistry(registry);
            hudManager.initRegistry(registry);
            return registry;
        }
    }
}
