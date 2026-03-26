package io.hyvexa.ascend.mine.system;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.CombatTextUpdate;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.EntityStatOp;
import com.hypixel.hytale.protocol.EntityStatUpdate;
import com.hypixel.hytale.protocol.EntityStatsUpdate;
import com.hypixel.hytale.protocol.EntityUpdate;
import com.hypixel.hytale.protocol.UIComponentsUpdate;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.modules.entityui.asset.CombatTextUIComponent;
import com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import io.hyvexa.ascend.mine.util.MinePositionUtils;
import io.hyvexa.common.npc.NPCHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Per-player block crack visuals and floating damage text + HP bar via temporary NPCs.
 *
 * <h3>HP bar visibility strategy</h3>
 * Each Kweebec_Seedling NPC has a UIComponentList whose componentIds reference both
 * an EntityStatUIComponent (health bar) and a CombatTextUIComponent (floating "-X" text).
 * <ul>
 *   <li><b>Block survives</b>: componentIds = fullIds (bar + text), health set to remaining fraction</li>
 *   <li><b>Block dies</b>: componentIds = combatTextOnlyIds (text only, no bar)</li>
 * </ul>
 * For fresh spawns the reflection change happens before the entity tracker syncs to the client.
 * For reused NPCs (already tracked), a {@code UIComponentsUpdate} packet is sent directly.
 */
public final class BlockVisualHelper {

    private BlockVisualHelper() {}

    // --- Block crack visuals ---

    public static void sendBlockCracks(World world, UUID playerId, int x, int y, int z,
                                        float remainingFraction, float delta) {
        world.getNotificationHandler().updateBlockDamage(
            x, y, z, remainingFraction, delta,
            playerRef -> playerId.equals(playerRef.getUuid())
        );
    }

    public static void clearBlockCracks(World world, UUID playerId, int x, int y, int z) {
        world.getNotificationHandler().updateBlockDamage(
            x, y, z, 1f, 0f,
            playerRef -> playerId.equals(playerRef.getUuid())
        );
    }

    // --- Reflection fields ---

    private static final Field COMPONENT_IDS_FIELD;
    private static final Field REGEN_VALUES_FIELD;
    private static final java.lang.reflect.Method STAT_VALUE_SET;
    static {
        try {
            COMPONENT_IDS_FIELD = UIComponentList.class.getDeclaredField("componentIds");
            COMPONENT_IDS_FIELD.setAccessible(true);
            REGEN_VALUES_FIELD = EntityStatValue.class.getDeclaredField("regeneratingValues");
            REGEN_VALUES_FIELD.setAccessible(true);
            STAT_VALUE_SET = EntityStatValue.class.getDeclaredMethod("set", float.class);
            STAT_VALUE_SET.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access reflection fields", e);
        }
    }

    // --- Per-player NPC reuse ---

    private static record ActiveNpc(
        UUID playerId,
        Ref<EntityStore> ref,
        int networkId,
        int[] combatTextOnlyIds,
        int[] fullIds,
        long lastHitMs,
        boolean pendingDespawn
    ) {}

    private static final ConcurrentHashMap<Long, ActiveNpc> activeNpcs = new ConcurrentHashMap<>();

    private static long packPlayerPosition(UUID playerId, int x, int y, int z) {
        long posKey = MinePositionUtils.packPosition(x, y, z);
        return posKey ^ (playerId.getMostSignificantBits() * 31);
    }

    /**
     * Filter componentIds to keep only CombatTextUIComponent entries.
     * Falls back to the full array if filtering fails or yields nothing.
     */
    private static int[] buildCombatTextOnlyIds(int[] allIds) {
        try {
            var assetMap = EntityUIComponent.getAssetMap();
            List<Integer> combatOnly = new ArrayList<>();
            for (int id : allIds) {
                if (assetMap.getAsset(id) instanceof CombatTextUIComponent) {
                    combatOnly.add(id);
                }
            }
            if (!combatOnly.isEmpty()) {
                int[] result = new int[combatOnly.size()];
                for (int i = 0; i < combatOnly.size(); i++) result[i] = combatOnly.get(i);
                return result;
            }
        } catch (Exception ignored) {}
        return allIds.clone();
    }

    private static void setComponentIds(Store<EntityStore> wStore, Ref<EntityStore> npcRef, int[] ids) {
        try {
            UIComponentList uiList = wStore.getComponent(npcRef, UIComponentList.getComponentType());
            if (uiList != null) {
                COMPONENT_IDS_FIELD.set(uiList, ids);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Push a UIComponentsUpdate packet to the player so an already-tracked NPC
     * picks up the new componentIds on the client side.
     */
    private static void sendComponentIdsToClient(PlayerRef playerRef, int networkId, int[] ids) {
        UIComponentsUpdate ucu = new UIComponentsUpdate(ids);
        EntityUpdate eu = new EntityUpdate(networkId, null, new ComponentUpdate[]{ucu});
        playerRef.getPacketHandler().writeNoCache(new EntityUpdates(null, new EntityUpdate[]{eu}));
    }

    // --- Public API ---

    public static void showDamageText(World world, PlayerRef playerRef, int bx, int by, int bz,
                                       double damage, double remainingHp, int maxHp) {
        if (playerRef == null) return;

        UUID playerId = playerRef.getUuid();
        long key = packPlayerPosition(playerId, bx, by, bz);
        boolean blockSurvives = remainingHp > 0 && maxHp > 1;

        ActiveNpc existing = activeNpcs.get(key);
        if (existing != null && !existing.pendingDespawn) {
            reuseExistingNpc(world, playerRef, key, existing, damage, remainingHp, maxHp, blockSurvives);
            return;
        }

        world.execute(() -> {
            try {
                NPCPlugin npcPlugin = NPCPlugin.get();
                Store<EntityStore> wStore = world.getEntityStore().getStore();
                Vector3d pos = new Vector3d(bx + 0.5, by + 1.2, bz + 0.5);
                Object result = npcPlugin.spawnNPC(wStore, "Kweebec_Seedling", "", pos, new Vector3f(0, 0, 0));
                Ref<EntityStore> npcRef = NPCHelper.extractEntityRef(result, null);
                if (npcRef == null) return;

                NPCHelper.setupNpcDefaults(wStore, npcRef, null);
                wStore.addComponent(npcRef, EntityScaleComponent.getComponentType(), new EntityScaleComponent(0.01f));

                // Resolve UIComponentList into two variants
                UIComponentList uiList = wStore.getComponent(npcRef, UIComponentList.getComponentType());
                int[] fullIds;
                int[] combatTextOnlyIds;
                if (uiList != null) {
                    fullIds = uiList.getComponentIds().clone();
                    combatTextOnlyIds = buildCombatTextOnlyIds(fullIds);
                } else {
                    fullIds = new int[0];
                    combatTextOnlyIds = new int[0];
                }

                if (blockSurvives) {
                    updateNpcHealth(wStore, npcRef, remainingHp, maxHp);
                    // fullIds already set from spawn — entity tracker will sync them
                } else {
                    // Set before entity tracker syncs → client receives combat-text-only
                    setComponentIds(wStore, npcRef, combatTextOnlyIds);
                }

                NPCEntity npcEntity = wStore.getComponent(npcRef, NPCEntity.getComponentType());
                if (npcEntity == null) return;
                int networkId = npcEntity.getNetworkId();

                ActiveNpc activeNpc = new ActiveNpc(playerId, npcRef, networkId,
                    combatTextOnlyIds, fullIds, System.currentTimeMillis(), !blockSurvives);
                activeNpcs.put(key, activeNpc);

                // 100ms delay: entity tracker must sync the spawn to the client first
                HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                    try {
                        sendCombatText(playerRef, networkId, damage);
                    } catch (Exception ignored) {}

                    if (!blockSurvives) {
                        scheduleDespawn(key, npcRef, world);
                    }
                }, 100, TimeUnit.MILLISECONDS);

            } catch (Exception ignored) {}
        });
    }

    private static void reuseExistingNpc(World world, PlayerRef playerRef, long key, ActiveNpc npc,
                                          double damage, double remainingHp, int maxHp, boolean blockSurvives) {
        ActiveNpc updated = new ActiveNpc(npc.playerId, npc.ref, npc.networkId,
            npc.combatTextOnlyIds, npc.fullIds, System.currentTimeMillis(), !blockSurvives);
        activeNpcs.put(key, updated);

        world.execute(() -> {
            try {
                Store<EntityStore> wStore = world.getEntityStore().getStore();
                if (blockSurvives) {
                    setComponentIds(wStore, npc.ref, npc.fullIds);
                    updateNpcHealth(wStore, npc.ref, remainingHp, maxHp);
                    // Send health bar update manually (bypassed addChange in updateNpcHealth)
                    sendHealthToClient(playerRef, npc.networkId, wStore, npc.ref);
                } else {
                    setComponentIds(wStore, npc.ref, npc.combatTextOnlyIds);
                }
            } catch (Exception ignored) {}

            // NPC already tracked by client: push componentIds + combat text
            try {
                int[] ids = blockSurvives ? npc.fullIds : npc.combatTextOnlyIds;
                sendComponentIdsToClient(playerRef, npc.networkId, ids);
                sendCombatText(playerRef, npc.networkId, damage);
            } catch (Exception ignored) {}
        });

        if (!blockSurvives) {
            scheduleDespawn(key, npc.ref, world);
        }
    }

    // --- Helpers ---

    /**
     * Set NPC health silently via reflection. Bypasses EntityStatMap.setStatValue() which
     * calls addChange() and queues an EntityStatUpdate delta for the entity tracker.
     * That delta causes the client to show a native floating damage number on top of
     * our explicit CombatTextUpdate — resulting in duplicate text.
     *
     * By using EntityStatValue.set() directly, no delta is queued. The entity tracker
     * syncs the current value naturally on initial entity visibility.
     * For already-tracked NPCs, the caller must send an EntityStatsUpdate packet manually.
     */
    private static void updateNpcHealth(Store<EntityStore> wStore, Ref<EntityStore> npcRef,
                                         double remainingHp, int maxHp) {
        try {
            EntityStatMap statMap = wStore.getComponent(npcRef, EntityStatMap.getComponentType());
            if (statMap != null) {
                int healthIdx = DefaultEntityStatTypes.getHealth();
                EntityStatValue healthStat = statMap.get(healthIdx);
                if (healthStat != null) {
                    disableRegen(healthStat);
                    float nativeMax = healthStat.getMax();
                    float targetHp = Math.max(0.1f, nativeMax * (float) (remainingHp / maxHp));
                    // Set value directly — no addChange, no entity tracker delta
                    STAT_VALUE_SET.invoke(healthStat, targetHp);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void disableRegen(EntityStatValue stat) {
        try {
            REGEN_VALUES_FIELD.set(stat, null);
        } catch (Exception ignored) {}
    }

    /**
     * Send a manual EntityStatsUpdate packet to push the current NPC health to the client.
     * Only needed for already-tracked NPCs (reuse path) since we bypass addChange.
     */
    private static void sendHealthToClient(PlayerRef playerRef, int networkId,
                                            Store<EntityStore> wStore, Ref<EntityStore> npcRef) {
        try {
            EntityStatMap statMap = wStore.getComponent(npcRef, EntityStatMap.getComponentType());
            if (statMap == null) return;
            int healthIdx = DefaultEntityStatTypes.getHealth();
            EntityStatValue healthStat = statMap.get(healthIdx);
            if (healthStat == null) return;

            EntityStatUpdate esu = new EntityStatUpdate(
                EntityStatOp.Set, false, healthStat.get(), null, null, null);
            java.util.Map<Integer, EntityStatUpdate[]> updates = java.util.Map.of(
                healthIdx, new EntityStatUpdate[]{esu});
            EntityStatsUpdate statsUpdate = new EntityStatsUpdate(updates);

            EntityUpdate eu = new EntityUpdate(networkId, null, new ComponentUpdate[]{statsUpdate});
            playerRef.getPacketHandler().writeNoCache(new EntityUpdates(null, new EntityUpdate[]{eu}));
        } catch (Exception ignored) {}
    }

    private static void sendCombatText(PlayerRef playerRef, int networkId, double damage) {
        String text = "-" + (int) Math.floor(damage);
        CombatTextUpdate ctu = new CombatTextUpdate(0f, text);
        EntityUpdate eu = new EntityUpdate(networkId, null, new ComponentUpdate[]{ctu});
        playerRef.getPacketHandler().writeNoCache(new EntityUpdates(null, new EntityUpdate[]{eu}));
    }

    private static void scheduleDespawn(long key, Ref<EntityStore> npcRef, World world) {
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            activeNpcs.remove(key);
            world.execute(() -> NPCHelper.despawnEntity(npcRef, null));
        }, 1500, TimeUnit.MILLISECONDS);
    }

    // --- Cleanup ---

    public static void cleanupIdleNpcs(World world) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, ActiveNpc>> it = activeNpcs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ActiveNpc> entry = it.next();
            ActiveNpc npc = entry.getValue();
            if (now - npc.lastHitMs > 3000 && !npc.pendingDespawn) {
                it.remove();
                world.execute(() -> NPCHelper.despawnEntity(npc.ref, null));
            }
        }
    }

    public static void despawnAllNpcs(World world) {
        Iterator<Map.Entry<Long, ActiveNpc>> it = activeNpcs.entrySet().iterator();
        while (it.hasNext()) {
            ActiveNpc npc = it.next().getValue();
            it.remove();
            world.execute(() -> NPCHelper.despawnEntity(npc.ref, null));
        }
    }

    public static void evictPlayer(UUID playerId, World world) {
        Iterator<Map.Entry<Long, ActiveNpc>> it = activeNpcs.entrySet().iterator();
        while (it.hasNext()) {
            ActiveNpc npc = it.next().getValue();
            if (playerId.equals(npc.playerId)) {
                it.remove();
                world.execute(() -> NPCHelper.despawnEntity(npc.ref, null));
            }
        }
    }
}
