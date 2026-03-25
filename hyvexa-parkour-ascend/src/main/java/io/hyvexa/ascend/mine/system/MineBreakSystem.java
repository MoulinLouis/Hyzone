package io.hyvexa.ascend.mine.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineUpgradeType;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.ascend.mine.hud.MineHudManager;
import io.hyvexa.common.util.PermissionUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MineBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private final MineManager mineManager;
    private final MinePlayerStore minePlayerStore;
    private final MineHudManager mineHudManager;
    private final MineAchievementTracker mineAchievementTracker;
    private final Map<UUID, Long> lastBagFullMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRegenMessage = new ConcurrentHashMap<>();

    public MineBreakSystem(MineManager mineManager, MinePlayerStore minePlayerStore,
                           MineHudManager mineHudManager, MineAchievementTracker mineAchievementTracker) {
        super(BreakBlockEvent.class);
        this.mineManager = mineManager;
        this.minePlayerStore = minePlayerStore;
        this.mineHudManager = mineHudManager;
        this.mineAchievementTracker = mineAchievementTracker;
    }

    @Override
    public void handle(int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> buffer, BreakBlockEvent event) {
        Player player = chunk.getComponent(entityId, Player.getComponentType());
        if (player == null) {
            return;
        }
        boolean isOp = PermissionUtils.isOp(player);

        Vector3i target = event.getTargetBlock();
        if (target == null) {
            return;
        }
        int bx = target.getX();
        int by = target.getY();
        int bz = target.getZ();

        MineZone zone = mineManager.findZoneAt(bx, by, bz);
        if (zone == null) {
            return;
        }

        // Block is in a mining zone — check if player can mine
        PlayerRef playerRef = chunk.getComponent(entityId, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }

        // Check if zone is regenerating
        if (mineManager.isZoneInCooldown(zone.getId())) {
            MineRewardHelper.sendRegenMessageIfNeeded(playerId, player, mineManager, zone.getId(), lastRegenMessage);
            return;
        }

        MinePlayerProgress mineProgress = minePlayerStore.getOrCreatePlayer(playerId);
        if (!mineProgress.isHoldingExpectedPickaxe(event.getItemInHand() != null ? event.getItemInHand().getItemId() : null)) {
            event.setCancelled(true);
            return;
        }

        // Resolve block type name
        String blockTypeName = event.getBlockType() != null ? event.getBlockType().getId() : null;
        if (blockTypeName == null) {
            return;
        }

        // Atomically claim this block — if a miner or another player already broke it, skip
        if (!mineManager.tryClaimBlock(zone.getId(), bx, by, bz)) {
            return;
        }

        if (store.getExternalData() == null) return;
        World world = store.getExternalData().getWorld();

        // For non-OP: manually remove the block (set to air) — we can't rely on setCancelled(false)
        // because NoBreakSystem may run after us and re-cancel the event.
        // For OP: NoBreakSystem already allows the break, no manual removal needed.
        if (!isOp) {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(bx, bz);
            var worldChunk = world.getChunkIfInMemory(chunkIndex);
            if (worldChunk == null) worldChunk = world.loadChunkIfInMemory(chunkIndex);
            if (worldChunk == null) {
                return;
            }
            worldChunk.setBlock(bx, by, bz, 0);
        }

        // Fortune roll
        int fortuneLevel = mineProgress.getUpgradeLevel(MineUpgradeType.FORTUNE);
        int blocksGained = MineRewardHelper.rollFortune(fortuneLevel);

        boolean bagFull = MineRewardHelper.rewardBlock(playerId, mineProgress, blockTypeName, blocksGained,
                zone.getMineId(), mineManager, minePlayerStore, mineHudManager, mineAchievementTracker);
        if (bagFull) {
            MineRewardHelper.sendBagFullMessageIfNeeded(playerId, player, lastBagFullMessage);
        }

        // Momentum combo
        MineRewardHelper.handleMomentumCombo(playerId, mineProgress, mineHudManager);

        // Egg drop chance
        EggDropHelper.tryDropEgg(playerId, player, zone, by, mineProgress, minePlayerStore,
            mineHudManager, mineAchievementTracker);

        // AoE upgrades (Jackhammer, Stomp, Blast)
        if (world != null) {
            MineAoEBreaker.triggerAoE(playerId, mineProgress, zone, world, bx, by, bz, mineManager,
                mineHudManager, mineAchievementTracker, minePlayerStore);
        }
    }

    public void evict(UUID playerId) {
        lastBagFullMessage.remove(playerId);
        lastRegenMessage.remove(playerId);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
