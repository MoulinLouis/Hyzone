package io.hyvexa.purge.system;

import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector4d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent2D;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.io.PacketHandler;
import io.hyvexa.purge.HyvexaPurgePlugin;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeSessionPlayerState;
import io.hyvexa.purge.data.PurgeVariantConfig;
import io.hyvexa.purge.data.PurgeWeaponUpgradeStore;
import io.hyvexa.purge.hud.PurgeHudManager;
import io.hyvexa.purge.manager.PurgeClassManager;
import io.hyvexa.purge.manager.PurgeSessionManager;
import io.hyvexa.purge.manager.PurgeVariantConfigManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;
import io.hyvexa.purge.manager.WeaponXpManager;

import java.util.Locale;
import java.util.UUID;

public class PurgeDamageModifierSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String KILL_SOUND_PREFIX = "SFX_Purge_Kill";
    private static final byte SLOT_MELEE_WEAPON = 1;
    private static final int MAX_STREAK = 9;
    private static final long STREAK_WINDOW_MS = 3000L;
    private static final int MAX_MOUNT_RESOLUTION_DEPTH = 4;
    private static final double MELEE_TARGET_RESOLVE_MAX_HORIZONTAL_DISTANCE_SQUARED = 2.25d;
    private static final double MELEE_TARGET_RESOLVE_MAX_VERTICAL_DISTANCE = 4.0d;

    private final PurgeSessionManager sessionManager;
    private final PurgeVariantConfigManager variantConfigManager;
    private final PurgeWeaponConfigManager weaponConfigManager;
    private final WeaponXpManager weaponXpManager;
    private volatile PurgeClassManager classManager;
    private volatile SystemGroup<EntityStore> cachedGroup;

    public PurgeDamageModifierSystem(PurgeSessionManager sessionManager,
                                      PurgeVariantConfigManager variantConfigManager,
                                      PurgeWeaponConfigManager weaponConfigManager,
                                      WeaponXpManager weaponXpManager) {
        this.sessionManager = sessionManager;
        this.variantConfigManager = variantConfigManager;
        this.weaponConfigManager = weaponConfigManager;
        this.weaponXpManager = weaponXpManager;
    }

    public void setClassManager(PurgeClassManager classManager) {
        this.classManager = classManager;
    }

    @Override
    public void handle(int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> buffer, Damage event) {
        Damage.Source src = event.getSource();
        String srcType = src != null ? src.getClass().getSimpleName() : "null";
        LOGGER.atInfo().log("[DMG-TRACE] handle called, amount=" + event.getAmount()
                + " source=" + srcType + " cause=" + event.getCause()
                + " cancelled=" + event.isCancelled());
        // Check if target is a player
        Player target = chunk.getComponent(entityId, Player.getComponentType());
        // Safety net: un-cancel damage for non-player targets (e.g. zombies).
        // Engine filters like FilterNPCWorldConfig may pre-cancel NPC damage events.
        if (event.isCancelled() && target == null) {
            event.setCancelled(false);
            event.setAmount(event.getInitialAmount());
            LOGGER.atInfo().log("[DMG-TRACE] un-cancelled non-player damage, restored amount=" + event.getInitialAmount());
        }
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
                Ref<EntityStore> sourcePlayerEntityRef = resolvePlayerEntityRef(store, sourceRef);
                if (sourcePlayerEntityRef != null) {
                    PlayerRef sourcePlayerRef = store.getComponent(sourcePlayerEntityRef, PlayerRef.getComponentType());
                    if (sourcePlayerRef != null) {
                        UUID sourceId = sourcePlayerRef.getUuid();
                        if (sourceId != null && session.getParticipants().contains(sourceId)) {
                            cancelDamage(event, buffer, chunk, entityId);
                            return;
                        }
                    }
                } else {
                    Ref<EntityStore> zombieSourceRef = resolveTrackedZombieRef(store, session, sourceRef);
                    if (zombieSourceRef != null) {
                        // Source is a Purge zombie — enforce per-variant damage
                        String variantKey = session.getZombieVariantKey(zombieSourceRef);
                        float damage = 20f; // default
                        if (variantKey != null) {
                            PurgeVariantConfig config = variantConfigManager.getVariant(variantKey);
                            if (config != null) {
                                damage = config.baseDamage();
                            }
                        }
                        // Apply Tank damage reduction
                        PurgeClassManager cm = classManager;
                        if (cm != null) {
                            damage *= (float) cm.getDamageReduction(targetState);
                        }
                        event.setAmount(damage);
                    }
                }
            }
        }

        // 2. Dead immunity: if target is dead this wave -> cancel
        if (targetState.isDeadThisWave()) {
            cancelDamage(event, buffer, chunk, entityId);
            return;
        }

    }

    private void applyPlayerDamageOverride(Damage event, Store<EntityStore> store, Ref<EntityStore> targetRef) {
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }
        Damage.Source source = event.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) {
            LOGGER.atInfo().log("[DMG-DBG] source not EntitySource: " + (source != null ? source.getClass().getSimpleName() : "null")
                    + " amount=" + event.getAmount());
            return;
        }
        Ref<EntityStore> sourceRef = entitySource.getRef();
        if (sourceRef == null || !sourceRef.isValid()) {
            return;
        }
        Ref<EntityStore> sourcePlayerEntityRef = resolvePlayerEntityRef(store, sourceRef);
        if (sourcePlayerEntityRef == null) {
            return;
        }
        PlayerRef sourcePlayerRef = store.getComponent(sourcePlayerEntityRef, PlayerRef.getComponentType());
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
        PurgeSessionPlayerState playerState = session.getPlayerState(sourceId);
        String playerWeapon = resolveActiveWeaponId(store, sourcePlayerEntityRef, playerState);
        boolean meleeAttack = weaponConfigManager.isMeleeWeapon(playerWeapon);
        Ref<EntityStore> zombieRef = resolveTrackedZombieTargetRef(
                store,
                session,
                targetRef,
                event.getIfPresentMetaObject(Damage.HIT_LOCATION),
                meleeAttack
        );
        if (zombieRef == null) {
            LOGGER.atInfo().log("[DMG-DBG] zombieRef null, weapon=" + playerWeapon
                    + " melee=" + meleeAttack + " aliveZombies=" + session.getAliveZombieCount());
            return;
        }
        int level = PurgeWeaponUpgradeStore.getInstance().getLevel(sourceId, playerWeapon);
        int effectiveLevel = Math.max(level, 1);
        int baseDamage = weaponConfigManager.getDamage(playerWeapon, effectiveLevel);
        float damage = (float) (baseDamage * weaponXpManager.getDamageMultiplier(sourceId, playerWeapon));
        // Apply Assault class damage multiplier
        PurgeClassManager cm = classManager;
        if (cm != null) {
            damage *= (float) cm.getDamageMultiplier(playerState);
        }
        LOGGER.atInfo().log("[DMG-DBG] setting damage=" + damage + " weapon=" + playerWeapon
                + " level=" + effectiveLevel + " base=" + baseDamage);
        event.setAmount(damage);
        if (clearZombieNameplateOnLethalHit(store, session, zombieRef, damage)) {
            if (playerState != null) {
                playerState.incrementSoloKills();
            }
            playKillSound(sourcePlayerRef, playerState);
            handleKillXp(sourceId, playerWeapon, store, sourcePlayerEntityRef);
            // Class kill effects (Medic heal, Scavenger streak scrap)
            if (cm != null) {
                cm.onZombieKill(playerState, sourcePlayerEntityRef, store);
            }
        }
    }

    public static Ref<EntityStore> resolvePlayerEntityRef(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        Ref<EntityStore> currentRef = entityRef;
        for (int depth = 0; depth < MAX_MOUNT_RESOLUTION_DEPTH; depth++) {
            if (currentRef == null || !currentRef.isValid()) {
                return null;
            }
            PlayerRef playerRef = store.getComponent(currentRef, PlayerRef.getComponentType());
            if (playerRef != null && playerRef.getUuid() != null) {
                return currentRef;
            }
            MountedComponent mounted = store.getComponent(currentRef, MountedComponent.getComponentType());
            if (mounted == null) {
                return null;
            }
            currentRef = mounted.getMountedToEntity();
        }
        return null;
    }

    public static Ref<EntityStore> resolveTrackedZombieRef(Store<EntityStore> store,
                                                           PurgeSession session,
                                                           Ref<EntityStore> entityRef) {
        Ref<EntityStore> currentRef = entityRef;
        for (int depth = 0; depth < MAX_MOUNT_RESOLUTION_DEPTH; depth++) {
            if (currentRef == null || !currentRef.isValid()) {
                return null;
            }
            if (session.getAliveZombies().contains(currentRef)) {
                return currentRef;
            }
            MountedComponent mounted = store.getComponent(currentRef, MountedComponent.getComponentType());
            if (mounted == null) {
                return null;
            }
            currentRef = mounted.getMountedToEntity();
        }
        return null;
    }

    public static Ref<EntityStore> resolveTrackedZombieTargetRef(Store<EntityStore> store,
                                                                 PurgeSession session,
                                                                 Ref<EntityStore> targetRef,
                                                                 Vector4d hitLocation,
                                                                 boolean allowNearestFallback) {
        Ref<EntityStore> resolvedRef = resolveTrackedZombieRef(store, session, targetRef);
        if (resolvedRef != null || !allowNearestFallback) {
            return resolvedRef;
        }

        if (hitLocation != null) {
            resolvedRef = findNearestTrackedZombie(store, session, hitLocation.x, hitLocation.y, hitLocation.z);
            if (resolvedRef != null) {
                return resolvedRef;
            }
        }

        if (targetRef == null || !targetRef.isValid()) {
            return null;
        }
        TransformComponent transform = store.getComponent(targetRef, TransformComponent.getComponentType());
        Vector3d targetPosition = transform != null ? transform.getPosition() : null;
        if (targetPosition == null) {
            return null;
        }
        return findNearestTrackedZombie(store, session, targetPosition.x, targetPosition.y, targetPosition.z);
    }

    private static Ref<EntityStore> findNearestTrackedZombie(Store<EntityStore> store,
                                                             PurgeSession session,
                                                             double x,
                                                             double y,
                                                             double z) {
        Ref<EntityStore> bestRef = null;
        double bestDistanceSquared = MELEE_TARGET_RESOLVE_MAX_HORIZONTAL_DISTANCE_SQUARED;
        for (Ref<EntityStore> zombieRef : session.getAliveZombies()) {
            if (zombieRef == null || !zombieRef.isValid()) {
                continue;
            }
            TransformComponent transform = store.getComponent(zombieRef, TransformComponent.getComponentType());
            Vector3d zombiePos = transform != null ? transform.getPosition() : null;
            if (zombiePos == null || Math.abs(zombiePos.y - y) > MELEE_TARGET_RESOLVE_MAX_VERTICAL_DISTANCE) {
                continue;
            }
            double dx = zombiePos.x - x;
            double dz = zombiePos.z - z;
            double horizontalDistanceSquared = dx * dx + dz * dz;
            if (horizontalDistanceSquared > bestDistanceSquared) {
                continue;
            }
            bestDistanceSquared = horizontalDistanceSquared;
            bestRef = zombieRef;
        }
        return bestRef;
    }

    private String resolveActiveWeaponId(Store<EntityStore> store,
                                         Ref<EntityStore> sourceRef,
                                         PurgeSessionPlayerState playerState) {
        boolean meleeSelected = false;
        Player player = store.getComponent(sourceRef, Player.getComponentType());
        if (player != null) {
            Inventory inventory = player.getInventory();
            if (inventory != null) {
                if (inventory.getActiveHotbarSlot() == SLOT_MELEE_WEAPON) {
                    meleeSelected = true;
                } else {
                    ItemStack heldItem = inventory.getItemInHand();
                    String heldItemId = heldItem != null && !heldItem.isEmpty() ? heldItem.getItemId() : null;
                    meleeSelected = weaponConfigManager.isMeleeWeapon(heldItemId);
                }
            }
        }

        if (meleeSelected) {
            String meleeWeaponId = playerState != null && playerState.getCurrentMeleeWeaponId() != null
                    ? playerState.getCurrentMeleeWeaponId()
                    : weaponConfigManager.getSessionMeleeWeaponId();
            return meleeWeaponId != null ? meleeWeaponId : "WoodSword";
        }
        String rangedWeaponId = playerState != null && playerState.getCurrentWeaponId() != null
                ? playerState.getCurrentWeaponId()
                : weaponConfigManager.getSessionWeaponId();
        return rangedWeaponId != null ? rangedWeaponId : "AK47";
    }

    private boolean clearZombieNameplateOnLethalHit(Store<EntityStore> store,
                                                    PurgeSession session,
                                                    Ref<EntityStore> zombieRef,
                                                    float incomingDamage) {
        if (zombieRef == null || !zombieRef.isValid() || incomingDamage <= 0f) {
            return false;
        }
        EntityStatMap statMap = store.getComponent(zombieRef, EntityStatMap.getComponentType());
        Nameplate nameplate = store.getComponent(zombieRef, Nameplate.getComponentType());
        if (statMap == null || nameplate == null) {
            return false;
        }
        EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
        if (health == null) {
            return false;
        }
        if (health.get() - incomingDamage <= 0f) {
            nameplate.setText("");
            session.markZombiePendingDeath(zombieRef);
            return true;
        }
        return false;
    }

    private void playKillSound(PlayerRef playerRef, PurgeSessionPlayerState playerState) {
        if (playerRef == null) return;
        int streak = (playerState != null) ? playerState.recordKillStreak(STREAK_WINDOW_MS) : 1;
        int soundStreak = Math.min(streak, MAX_STREAK);
        int index = SoundEvent.getAssetMap().getIndex(KILL_SOUND_PREFIX + soundStreak);
        if (index <= SoundEvent.EMPTY_ID) return;
        PacketHandler ph = playerRef.getPacketHandler();
        if (ph == null) return;
        ph.writeNoCache(new PlaySoundEvent2D(index, SoundCategory.SFX, 2.0f, 1.0f));
    }

    private void handleKillXp(UUID playerId, String weaponId, Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (weaponXpManager == null) return;
        int newLevel = weaponXpManager.addKillXp(playerId, weaponId);
        // Update HUD XP bar on every kill
        String displayName = weaponConfigManager.getDisplayName(weaponId);
        HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
        if (plugin != null) {
            PurgeHudManager hudManager = plugin.getHudManager();
            if (hudManager != null) {
                if (weaponConfigManager.isMeleeWeapon(weaponId)) {
                    hudManager.updateMeleeXpHud(playerId, weaponId, displayName);
                } else {
                    hudManager.updateWeaponXpHud(playerId, weaponId, displayName);
                }
            }
        }
        // Send level-up chat message
        if (newLevel > 0 && playerRef != null && playerRef.isValid()) {
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player != null) {
                double dmgBonus = 1.5 * newLevel;
                int scrapBonus = (int) (0.5 * newLevel);
                int ammoBonus = 5 * newLevel;
                String msg = displayName + " reached Mastery Lv " + newLevel + "! +"
                        + String.format(Locale.US, "%.1f", dmgBonus) + "% DMG, +"
                        + scrapBonus + " scrap/kill, +" + ammoBonus + "% ammo";
                player.sendMessage(Message.raw(msg));
            }
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
