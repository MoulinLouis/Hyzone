package io.hyvexa.purge.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeSessionPlayerState;
import io.hyvexa.purge.data.PurgeUpgradeState;
import io.hyvexa.purge.data.PurgeUpgradeType;
import io.hyvexa.purge.data.PurgeVariantConfig;
import io.hyvexa.purge.data.PurgeWeaponUpgradeStore;
import io.hyvexa.purge.manager.PurgeSessionManager;
import io.hyvexa.purge.manager.PurgeVariantConfigManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;

import java.util.UUID;

public class PurgeDamageModifierSystem extends DamageEventSystem {

    private final PurgeSessionManager sessionManager;
    private final PurgeVariantConfigManager variantConfigManager;
    private final PurgeWeaponConfigManager weaponConfigManager;
    private volatile SystemGroup<EntityStore> cachedGroup;

    public PurgeDamageModifierSystem(PurgeSessionManager sessionManager,
                                      PurgeVariantConfigManager variantConfigManager,
                                      PurgeWeaponConfigManager weaponConfigManager) {
        this.sessionManager = sessionManager;
        this.variantConfigManager = variantConfigManager;
        this.weaponConfigManager = weaponConfigManager;
    }

    @Override
    public void handle(int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> buffer, Damage event) {
        // Check if target is a player
        Player target = chunk.getComponent(entityId, Player.getComponentType());
        if (target == null) {
            // Target is not a player (e.g. zombie) — check for player damage overrides
            applyPlayerDamageOverride(event, store, chunk.getReferenceTo(entityId));
            return;
        }
        PlayerRef targetPlayerRef = chunk.getComponent(entityId, PlayerRef.getComponentType());
        if (targetPlayerRef == null) {
            return;
        }
        UUID targetId = targetPlayerRef.getUuid();
        if (targetId == null) {
            return;
        }

        PurgeSession session = sessionManager.getSessionByPlayer(targetId);
        if (session == null) {
            return;
        }

        PurgeSessionPlayerState targetState = session.getPlayerState(targetId);
        if (targetState == null) {
            return;
        }

        // 1. Friendly fire: if source is a player in the same session -> cancel
        //    Also enforce zombie base damage when source is a session zombie
        Damage.Source source = event.getSource();
        if (source instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> sourceRef = entitySource.getRef();
            if (sourceRef != null && sourceRef.isValid()) {
                PlayerRef sourcePlayerRef = store.getComponent(sourceRef, PlayerRef.getComponentType());
                if (sourcePlayerRef != null) {
                    UUID sourceId = sourcePlayerRef.getUuid();
                    if (sourceId != null && session.getParticipants().contains(sourceId)) {
                        cancelDamage(event, buffer, chunk, entityId);
                        return;
                    }
                } else if (session.getAliveZombies().contains(sourceRef)) {
                    // Source is a Purge zombie — enforce per-variant damage
                    String variantKey = session.getZombieVariantKey(sourceRef);
                    float damage = 20f; // default
                    if (variantKey != null) {
                        PurgeVariantConfig config = variantConfigManager.getVariant(variantKey);
                        if (config != null) {
                            damage = config.baseDamage();
                        }
                    }
                    event.setAmount(damage);
                }
            }
        }

        // 2. Dead immunity: if target is dead this wave -> cancel
        if (targetState.isDeadThisWave()) {
            cancelDamage(event, buffer, chunk, entityId);
            return;
        }

        // 3. THICK_HIDE reduction: reduce damage for alive players
        if (targetState.isAlive()) {
            PurgeUpgradeState upgradeState = targetState.getUpgradeState();
            int stacks = upgradeState.getStacks(PurgeUpgradeType.THICK_HIDE);
            if (stacks > 0) {
                float multiplier = Math.max(0.60f, 1.0f - 0.08f * stacks);
                event.setAmount(event.getAmount() * multiplier);
            }
        }
    }

    private void applyPlayerDamageOverride(Damage event, Store<EntityStore> store, Ref<EntityStore> targetRef) {
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }
        Damage.Source source = event.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) {
            return;
        }
        Ref<EntityStore> sourceRef = entitySource.getRef();
        if (sourceRef == null || !sourceRef.isValid()) {
            return;
        }
        PlayerRef sourcePlayerRef = store.getComponent(sourceRef, PlayerRef.getComponentType());
        if (sourcePlayerRef == null) {
            return;
        }
        UUID sourceId = sourcePlayerRef.getUuid();
        if (sourceId == null) {
            return;
        }
        PurgeSession session = sessionManager.getSessionByPlayer(sourceId);
        if (session == null) {
            return;
        }
        if (!session.getAliveZombies().contains(targetRef)) {
            return;
        }
        PurgeSessionPlayerState playerState = session.getPlayerState(sourceId);
        String playerWeapon = (playerState != null && playerState.getCurrentWeaponId() != null)
                ? playerState.getCurrentWeaponId()
                : weaponConfigManager.getSessionWeaponId();
        int level = PurgeWeaponUpgradeStore.getInstance().getLevel(sourceId, playerWeapon);
        int effectiveLevel = Math.max(level, 1);
        int damage = weaponConfigManager.getDamage(playerWeapon, effectiveLevel);
        event.setAmount(damage);
        clearZombieNameplateOnLethalHit(store, targetRef, damage);
    }

    private void clearZombieNameplateOnLethalHit(Store<EntityStore> store, Ref<EntityStore> zombieRef, float incomingDamage) {
        if (zombieRef == null || !zombieRef.isValid() || incomingDamage <= 0f) {
            return;
        }
        EntityStatMap statMap = store.getComponent(zombieRef, EntityStatMap.getComponentType());
        Nameplate nameplate = store.getComponent(zombieRef, Nameplate.getComponentType());
        if (statMap == null || nameplate == null) {
            return;
        }
        EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
        if (health == null) {
            return;
        }
        if (health.get() - incomingDamage <= 0f) {
            nameplate.setText("");
        }
    }

    private void cancelDamage(Damage event, CommandBuffer<EntityStore> buffer,
                               ArchetypeChunk<EntityStore> chunk, int entityId) {
        if (event.hasMetaObject(Damage.KNOCKBACK_COMPONENT)) {
            event.removeMetaObject(Damage.KNOCKBACK_COMPONENT);
        }
        event.setCancelled(true);
        event.setAmount(0f);
        buffer.tryRemoveComponent(chunk.getReferenceTo(entityId), KnockbackComponent.getComponentType());
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        SystemGroup<EntityStore> group = cachedGroup;
        if (group != null) {
            return group;
        }
        group = DamageModule.get().getFilterDamageGroup();
        cachedGroup = group;
        return group;
    }
}
