package io.hyvexa.ascend.mine.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
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
import io.hyvexa.ascend.mine.data.MineUpgradeType;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.ascend.mine.hud.MineHudManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles block breaking inside mine zones on DamageBlockEvent.
 * Blocks with 1 HP are broken instantly (current behavior).
 * Blocks with >1 HP require multiple hits — a health bar HUD is shown to the player.
 */
public class MineDamageSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {
    private final MineManager mineManager;
    private final MinePlayerStore minePlayerStore;
    private final BlockDamageTracker damageTracker = new BlockDamageTracker();
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
        if (!mineProgress.isHoldingExpectedPickaxe(event.getItemInHand() != null ? event.getItemInHand().getItemId() : null)) {
            return;
        }

        if (mineManager.isZoneInCooldown(zone.getId())) {
            MineRewardHelper.sendRegenMessageIfNeeded(playerId, player, mineManager, zone.getId(), lastRegenMessage);
            return;
        }

        String blockTypeName = event.getBlockType() != null ? event.getBlockType().getId() : null;
        if (blockTypeName == null) return;

        // Multi-HP: record hit with Momentum damage bonus
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        MineConfigStore configStore = plugin.getMineConfigStore();
        int blockHp = configStore.getBlockHp(blockTypeName);
        double damageMultiplier = mineProgress.getMomentumMultiplier();
        BlockDamageTracker.HitResult hitResult = damageTracker.recordHit(playerId, bx, by, bz, blockTypeName, blockHp, damageMultiplier);

        MineHudManager mineHudManager = plugin.getMineHudManager();

        if (!hitResult.shouldBreak()) {
            // Block still has HP — update the health bar HUD
            if (mineHudManager != null) {
                mineHudManager.showBlockHealth(playerId, blockTypeName, hitResult.remainingHp(), hitResult.maxHp());
            }
            return;
        }

        // Block HP depleted (or was 1 HP) — break it
        if (!mineManager.tryClaimBlock(zone.getId(), bx, by, bz)) {
            return;
        }

        // Remove the block (set to air)
        World world = store.getExternalData().getWorld();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(bx, bz);
        var worldChunk = world.getChunkIfInMemory(chunkIndex);
        if (worldChunk == null) worldChunk = world.loadChunkIfInMemory(chunkIndex);
        if (worldChunk == null) return;
        worldChunk.setBlock(bx, by, bz, 0);

        // Hide block health HUD
        if (mineHudManager != null) {
            mineHudManager.hideBlockHealth(playerId);
        }

        // Fortune roll
        int fortuneLevel = mineProgress.getUpgradeLevel(MineUpgradeType.FORTUNE);
        int blocksGained = MineRewardHelper.rollFortune(fortuneLevel);

        // Reward
        boolean bagFull = MineRewardHelper.rewardBlock(playerId, mineProgress, blockTypeName, blocksGained,
                zone.getMineId(), mineManager, minePlayerStore);
        if (bagFull) {
            MineRewardHelper.sendBagFullMessageIfNeeded(playerId, player, lastBagFullMessage);
        }

        // Momentum combo
        MineRewardHelper.handleMomentumCombo(playerId, mineProgress);

        // AoE upgrades (Jackhammer, Stomp, Blast)
        if (world != null) {
            MineAoEBreaker.triggerAoE(playerId, mineProgress, zone, world, bx, by, bz, mineManager);
        }
    }

    public void evict(UUID playerId) {
        lastBagFullMessage.remove(playerId);
        lastRegenMessage.remove(playerId);
        damageTracker.evict(playerId);
    }

    public BlockDamageTracker getDamageTracker() {
        return damageTracker;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
