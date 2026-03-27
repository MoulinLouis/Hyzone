package io.hyvexa.purge.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.data.PurgeClass;
import io.hyvexa.purge.data.PurgeClassStore;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeSessionPlayerState;

import java.util.UUID;

public class PurgeClassManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PURGE_CLASS_HP_MODIFIER = "purge_class_hp";
    private static final float TANK_HP_BONUS = 40f;
    private static final float TANK_SPEED_FACTOR = 0.85f;
    private static final float TANK_DAMAGE_REDUCTION = 0.8f;
    private static final float ASSAULT_SPEED_FACTOR = 1.10f;
    private static final float ASSAULT_BASE_DAMAGE_MULT = 1.2f;
    private static final float ASSAULT_STREAK_DAMAGE_PER_LEVEL = 0.05f;
    private static final float SCAVENGER_SCRAP_MULT = 1.3f;
    private static final int SCAVENGER_STREAK_BONUS_SCRAP = 5;
    private static final float MEDIC_HEAL_PER_KILL = 5f;
    private static final float MEDIC_REGEN_AMOUNT = 2f;
    private static final int MEDIC_REGEN_TICKS = 15; // 15 * 200ms = 3s
    private static final String PURGE_HEAL_TEMP_MODIFIER = "purge_heal_temp";

    private final PurgeUpgradeManager upgradeManager;
    private final PurgeClassStore classStore;

    public PurgeClassManager(PurgeUpgradeManager upgradeManager, PurgeClassStore classStore) {
        this.upgradeManager = upgradeManager;
        this.classStore = classStore;
    }

    /**
     * Applies class stat modifiers on session start. Call after granting loadout and base health.
     */
    public void applyClassEffects(PurgeSession session, UUID playerId,
                                   Ref<EntityStore> ref, Store<EntityStore> store) {
        PurgeSessionPlayerState ps = session.getPlayerState(playerId);
        if (ps == null) return;

        PurgeClass activeClass = classStore.getSelectedClass(playerId);
        ps.setActiveClass(activeClass);
        if (activeClass == null) return;

        switch (activeClass) {
            case TANK -> {
                applyHpBonus(ref, store);
                ps.setClassSpeedFactor(TANK_SPEED_FACTOR);
                upgradeManager.applySpeedForClass(session, playerId, ref, store);
            }
            case ASSAULT -> {
                ps.setClassSpeedFactor(ASSAULT_SPEED_FACTOR);
                upgradeManager.applySpeedForClass(session, playerId, ref, store);
            }
            case SCAVENGER, MEDIC -> {
                // No stat modifiers on session start
            }
        }
    }

    /**
     * Reverts class stat modifiers on session end. Call before upgrade revert.
     */
    public void revertClassEffects(PurgeSession session, UUID playerId,
                                    Ref<EntityStore> ref, Store<EntityStore> store) {
        PurgeSessionPlayerState ps = session.getPlayerState(playerId);
        if (ps == null) return;
        PurgeClass activeClass = ps.getActiveClass();
        if (activeClass == null) return;

        if (activeClass == PurgeClass.TANK) {
            revertHpBonus(ref, store);
        }
        ps.setClassSpeedFactor(1.0f);
        // Speed reset is handled by upgradeManager.revertPlayerUpgrades
    }

    /**
     * Returns the scrap multiplier for the player's active class.
     */
    public double getScrapMultiplier(PurgeSessionPlayerState ps) {
        if (ps == null || ps.getActiveClass() != PurgeClass.SCAVENGER) return 1.0;
        return SCAVENGER_SCRAP_MULT;
    }

    /**
     * Returns the damage multiplier for player-to-zombie damage (Assault class).
     */
    public double getDamageMultiplier(PurgeSessionPlayerState ps) {
        if (ps == null || ps.getActiveClass() != PurgeClass.ASSAULT) return 1.0;
        int streak = ps.getKillStreak();
        return ASSAULT_BASE_DAMAGE_MULT + (streak * ASSAULT_STREAK_DAMAGE_PER_LEVEL);
    }

    /**
     * Returns the damage reduction multiplier for zombie-to-player damage (Tank class).
     * Multiply incoming damage by this value.
     */
    public double getDamageReduction(PurgeSessionPlayerState ps) {
        if (ps == null || ps.getActiveClass() != PurgeClass.TANK) return 1.0;
        return TANK_DAMAGE_REDUCTION;
    }

    /**
     * Called when a player lands a lethal hit on a zombie.
     * Handles Medic heal-on-kill and Scavenger streak scrap bonus.
     */
    public void onZombieKill(PurgeSessionPlayerState ps, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ps == null || ps.getActiveClass() == null) return;

        switch (ps.getActiveClass()) {
            case MEDIC -> healPlayer(ref, store, MEDIC_HEAL_PER_KILL);
            case SCAVENGER -> {
                if (ps.getKillStreak() >= 2) {
                    ps.addBonusScrap(SCAVENGER_STREAK_BONUS_SCRAP);
                }
            }
            default -> {}
        }
    }

    /**
     * Ticks Medic passive regen for all Medic players in the session.
     * Called from the wave tick (every 200ms). Heals every 3 seconds.
     */
    public void tickMedicRegen(PurgeSession session, Store<EntityStore> store) {
        session.forEachAliveConnectedPlayerState(ps -> {
            if (ps.getActiveClass() != PurgeClass.MEDIC) return;
            if (ps.incrementRegenTickCounter() >= MEDIC_REGEN_TICKS) {
                ps.resetRegenTickCounter();
                Ref<EntityStore> ref = ps.getPlayerRef();
                healPlayer(ref, store, MEDIC_REGEN_AMOUNT);
            }
        });
    }

    // --- HP modifier ---

    private void applyHpBonus(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) return;

        int healthIndex = DefaultEntityStatTypes.getHealth();
        statMap.putModifier(healthIndex, PURGE_CLASS_HP_MODIFIER,
                new StaticModifier(Modifier.ModifierTarget.MAX,
                        StaticModifier.CalculationType.ADDITIVE, TANK_HP_BONUS));
        statMap.update();
        statMap.maximizeStatValue(healthIndex);
        statMap.update();
    }

    private void revertHpBonus(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) return;

        int healthIndex = DefaultEntityStatTypes.getHealth();
        statMap.removeModifier(healthIndex, PURGE_CLASS_HP_MODIFIER);
        statMap.update();
    }

    // --- Healing ---

    /**
     * Heals a player by the given amount, capped at max HP.
     * Uses a temporary modifier trick since EntityStatValue.set() is protected:
     * temporarily lower max to target health, maximize (sets current = lowered max), then restore max.
     */
    private void healPlayer(Ref<EntityStore> ref, Store<EntityStore> store, float amount) {
        if (ref == null || !ref.isValid()) return;
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) return;

        int healthIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue health = statMap.get(healthIndex);
        if (health == null) return;

        float current = health.get();
        float max = health.getMax();
        if (current >= max) return;

        float targetHealth = Math.min(current + amount, max);
        float tempDelta = targetHealth - max; // negative: temporarily lowers max to targetHealth
        statMap.putModifier(healthIndex, PURGE_HEAL_TEMP_MODIFIER,
                new StaticModifier(Modifier.ModifierTarget.MAX,
                        StaticModifier.CalculationType.ADDITIVE, tempDelta));
        statMap.update();
        statMap.maximizeStatValue(healthIndex); // current = targetHealth
        statMap.update();
        statMap.removeModifier(healthIndex, PURGE_HEAL_TEMP_MODIFIER);
        statMap.update(); // max restored, current stays at targetHealth
    }
}
