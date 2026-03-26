package io.hyvexa.ascend.mine.system;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.CombatTextUpdate;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.EntityUpdate;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import io.hyvexa.ascend.mine.util.MinePositionUtils;
import io.hyvexa.common.npc.NPCHelper;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Per-player block crack visuals via WorldNotificationHandler.
 * Uses remaining-health convention: 1.0 = full, 0.0 = destroyed.
 */
public final class BlockVisualHelper {

    private BlockVisualHelper() {}

    /**
     * Send block crack visuals to a single player.
     *
     * @param remainingFraction remaining health 0.0–1.0 (1.0 = no damage)
     * @param delta             negative damage increment (e.g. -0.3 for 30% damage dealt)
     */
    public static void sendBlockCracks(World world, UUID playerId, int x, int y, int z,
                                        float remainingFraction, float delta) {
        world.getNotificationHandler().updateBlockDamage(
            x, y, z,
            remainingFraction,
            delta,
            playerRef -> playerId.equals(playerRef.getUuid())
        );
    }

    /**
     * Clear crack visuals for a player at a position (reset to full health).
     */
    public static void clearBlockCracks(World world, UUID playerId, int x, int y, int z) {
        world.getNotificationHandler().updateBlockDamage(
            x, y, z,
            1f, 0f,
            playerRef -> playerId.equals(playerRef.getUuid())
        );
    }

    // Positions with an active damage text NPC — prevents duplicate spawns
    private static final Set<Long> activeDamageTextPositions = ConcurrentHashMap.newKeySet();

    /**
     * Spawn a temporary invisible NPC at the block position and send a combat text packet
     * to the given player. Deduplicates by position so rapid hits don't stack NPCs.
     */
    public static void showDamageText(World world, PlayerRef playerRef, int bx, int by, int bz,
                                       double damage, double remainingHp, int maxHp) {
        if (playerRef == null) return;

        long posKey = MinePositionUtils.packPosition(bx, by, bz);
        if (!activeDamageTextPositions.add(posKey)) return;

        world.execute(() -> {
            try {
                NPCPlugin npcPlugin = NPCPlugin.get();
                Store<EntityStore> wStore = world.getEntityStore().getStore();
                Vector3d pos = new Vector3d(bx + 0.5, by + 1.2, bz + 0.5);
                Object result = npcPlugin.spawnNPC(wStore, "Kweebec_Seedling", "", pos, new Vector3f(0, 0, 0));
                Ref<EntityStore> npcRef = NPCHelper.extractEntityRef(result, null);
                if (npcRef == null) { activeDamageTextPositions.remove(posKey); return; }

                NPCHelper.setupNpcDefaults(wStore, npcRef, null);
                wStore.addComponent(npcRef, EntityScaleComponent.getComponentType(), new EntityScaleComponent(0.01f));

                boolean blockSurvives = remainingHp > 0 && maxHp > 1;

                if (blockSurvives) {
                    // Block survives: reduce NPC health to show HP bar
                    try {
                        EntityStatMap statMap = wStore.getComponent(npcRef, EntityStatMap.getComponentType());
                        if (statMap != null) {
                            int healthIdx = DefaultEntityStatTypes.getHealth();
                            EntityStatValue healthStat = statMap.get(healthIdx);
                            if (healthStat != null) {
                                float nativeMax = healthStat.getMax();
                                float targetHp = nativeMax * (float) (remainingHp / maxHp);
                                statMap.setStatValue(healthIdx, Math.max(0.1f, targetHp));
                            }
                        }
                    } catch (Exception ignored) {}
                } else {
                    // Block dies: remove UIComponentList to hide health bar entirely
                    try {
                        wStore.removeComponent(npcRef, UIComponentList.getComponentType());
                    } catch (Exception ignored) {}
                }

                NPCEntity npcEntity = wStore.getComponent(npcRef, NPCEntity.getComponentType());
                if (npcEntity == null) { activeDamageTextPositions.remove(posKey); return; }
                int networkId = npcEntity.getNetworkId();

                try {
                    HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                        try {
                            // Combat text: "-X"
                            String text = "-" + (int) Math.floor(damage);
                            CombatTextUpdate ctu = new CombatTextUpdate(0f, text);

                            EntityUpdate eu = new EntityUpdate(networkId, null,
                                new ComponentUpdate[]{ctu});
                            playerRef.getPacketHandler().writeNoCache(new EntityUpdates(null, new EntityUpdate[]{eu}));
                        } catch (Exception ignored) {}

                        // Despawn NPC + unlock position after 1.5s (text animation ~1s)
                        try {
                            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                                activeDamageTextPositions.remove(posKey);
                                world.execute(() -> NPCHelper.despawnEntity(npcRef, null));
                            }, 1500, TimeUnit.MILLISECONDS);
                        } catch (Exception e) {
                            activeDamageTextPositions.remove(posKey);
                            world.execute(() -> NPCHelper.despawnEntity(npcRef, null));
                        }
                    }, 100, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    activeDamageTextPositions.remove(posKey);
                    NPCHelper.despawnEntity(npcRef, null);
                }
            } catch (Exception e) {
                activeDamageTextPositions.remove(posKey);
            }
        });
    }
}
