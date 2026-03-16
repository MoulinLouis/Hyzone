package io.hyvexa.ascend.mine.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.hud.MineHudManager;
import io.hyvexa.common.math.BigNumber;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles instant block breaking inside mine zones on first hit (DamageBlockEvent).
 * Cancels normal damage, manually removes the block, and gives rewards.
 * This bypasses BreakBlockEvent entirely for mine blocks.
 */
public class MineDamageSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {
    private final MineManager mineManager;
    private final MinePlayerStore minePlayerStore;
    private final Map<UUID, Long> lastBagFullMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRegenMessage = new ConcurrentHashMap<>();

    public MineDamageSystem(MineManager mineManager, MinePlayerStore minePlayerStore) {
        super(DamageBlockEvent.class);
        this.mineManager = mineManager;
        this.minePlayerStore = minePlayerStore;
    }

    @Override
    public void handle(int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> buffer, DamageBlockEvent event) {
        Vector3i target = event.getTargetBlock();
        if (target == null) return;

        int bx = target.getX();
        int by = target.getY();
        int bz = target.getZ();

        MineZone zone = mineManager.findZoneAt(bx, by, bz);
        if (zone == null) return;

        // Block is in a mine zone — cancel normal damage regardless of outcome
        event.setCancelled(true);

        Player player = chunk.getComponent(entityId, Player.getComponentType());
        if (player == null) return;

        PlayerRef playerRef = chunk.getComponent(entityId, PlayerRef.getComponentType());
        if (playerRef == null) return;
        UUID playerId = playerRef.getUuid();
        if (playerId == null) return;

        MinePlayerProgress mineProgress = minePlayerStore.getOrCreatePlayer(playerId);
        if (!mineProgress.getMineState(zone.getMineId()).isUnlocked()) return;
        if (!isExpectedPickaxe(mineProgress, event.getItemInHand() != null ? event.getItemInHand().getItemId() : null)) {
            return;
        }

        if (mineManager.isZoneInCooldown(zone.getId())) {
            long now = System.currentTimeMillis();
            Long last = lastRegenMessage.get(playerId);
            if (last == null || now - last > 3000) {
                lastRegenMessage.put(playerId, now);
                long remaining = mineManager.getZoneCooldownRemainingMs(zone.getId());
                int secondsLeft = (int) Math.ceil(remaining / 1000.0);
                player.sendMessage(Message.raw("Zone regenerating... " + secondsLeft + "s remaining"));
            }
            return;
        }

        String blockTypeName = event.getBlockType() != null ? event.getBlockType().getId() : null;
        if (blockTypeName == null) return;

        int blocksGained = 1;
        double multiBreakChance = mineProgress.getMultiBreakChance();
        if (multiBreakChance > 0 && ThreadLocalRandom.current().nextDouble() < multiBreakChance) {
            blocksGained = 2;
        }

        if (!mineProgress.canAddToInventory(blocksGained)) {
            sendBagFullMessage(playerId, player);
            return;
        }

        if (!mineManager.tryClaimBlock(zone.getId(), bx, by, bz)) {
            return;
        }

        // Instantly remove the block (set to air)
        World world = store.getExternalData().getWorld();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(bx, bz);
        var worldChunk = world.getChunkIfInMemory(chunkIndex);
        if (worldChunk == null) worldChunk = world.loadChunkIfInMemory(chunkIndex);
        if (worldChunk == null) return;
        worldChunk.setBlock(bx, by, bz, 0);

        // Reward
        int stored = mineProgress.addToInventoryUpTo(blockTypeName, blocksGained);
        if (stored < blocksGained) {
            MineConfigStore configStore = mineManager.getConfigStore();
            BigNumber blockPrice = configStore.getBlockPrice(zone.getMineId(), blockTypeName);
            int overflow = blocksGained - stored;
            long fallbackCrystals = blockPrice.multiply(BigNumber.of(overflow, 0)).toLong();
            mineProgress.addCrystals(fallbackCrystals);
        }
        minePlayerStore.markDirty(playerId);

        MineHudManager mineHudManager = ParkourAscendPlugin.getInstance().getMineHudManager();
        if (mineHudManager != null) {
            mineHudManager.showMineToast(playerId, blockTypeName, blocksGained);
        }

        ParkourAscendPlugin achPlugin = ParkourAscendPlugin.getInstance();
        if (achPlugin != null) {
            MineAchievementTracker achievementTracker = achPlugin.getMineAchievementTracker();
            if (achievementTracker != null) {
                achievementTracker.incrementBlocksMined(playerId, blocksGained);
            }
        }
    }

    private void sendBagFullMessage(UUID playerId, Player player) {
        long now = System.currentTimeMillis();
        Long last = lastBagFullMessage.get(playerId);
        if (last == null || now - last > 3000) {
            lastBagFullMessage.put(playerId, now);
            player.sendMessage(Message.raw("Bag full! Sell your blocks with /mine sell"));
        }
    }

    public void evict(UUID playerId) {
        lastBagFullMessage.remove(playerId);
        lastRegenMessage.remove(playerId);
    }

    private boolean isExpectedPickaxe(MinePlayerProgress mineProgress, String heldItemId) {
        if (mineProgress == null || heldItemId == null || heldItemId.isEmpty()) {
            return false;
        }
        String expectedItemId = mineProgress.getPickaxeTierEnum().getItemId();
        return expectedItemId != null && expectedItemId.equals(heldItemId);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
