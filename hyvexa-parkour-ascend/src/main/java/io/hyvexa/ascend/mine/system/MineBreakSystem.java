package io.hyvexa.ascend.mine.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.ascend.mine.hud.MineHudManager;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.util.PermissionUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MineBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private final MineManager mineManager;
    private final MinePlayerStore minePlayerStore;
    private final Map<UUID, Long> lastBagFullMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRegenMessage = new ConcurrentHashMap<>();

    public MineBreakSystem(MineManager mineManager, MinePlayerStore minePlayerStore) {
        super(BreakBlockEvent.class);
        this.mineManager = mineManager;
        this.minePlayerStore = minePlayerStore;
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

        // Check mine unlock state
        MinePlayerProgress unlockCheck = minePlayerStore.getOrCreatePlayer(playerId);
        if (!unlockCheck.getMineState(zone.getMineId()).isUnlocked()) {
            return;
        }

        // Check if zone is regenerating
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

        AscendPlayerProgress ascendProgress = ParkourAscendPlugin.getInstance()
            .getPlayerStore().getPlayer(playerId);
        if (ascendProgress == null) {
            return;
        }
        if (ascendProgress.getAscensionCount() < 1) {
            return;
        }

        MinePlayerProgress mineProgress = minePlayerStore.getOrCreatePlayer(playerId);
        // Resolve block type name
        String blockTypeName = event.getBlockType() != null ? event.getBlockType().getId() : null;
        if (blockTypeName == null) {
            return;
        }

        int blocksGained = 1;
        double multiBreakChance = mineProgress.getMultiBreakChance();
        if (multiBreakChance > 0 && ThreadLocalRandom.current().nextDouble() < multiBreakChance) {
            blocksGained = 2;
        }

        if (!mineProgress.canAddToInventory(blocksGained)) {
            sendBagFullMessage(playerId, player);
            return;
        }

        // Atomically claim this block — if a miner or another player already broke it, skip
        if (!mineManager.tryClaimBlock(zone.getId(), bx, by, bz)) {
            return;
        }

        // For non-OP: manually remove the block (set to air) — we can't rely on setCancelled(false)
        // because NoBreakSystem may run after us and re-cancel the event.
        // For OP: NoBreakSystem already allows the break, no manual removal needed.
        if (!isOp) {
            World world = store.getExternalData().getWorld();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(bx, bz);
            var worldChunk = world.getChunkIfInMemory(chunkIndex);
            if (worldChunk == null) worldChunk = world.loadChunkIfInMemory(chunkIndex);
            if (worldChunk == null) {
                return;
            }
            worldChunk.setBlock(bx, by, bz, 0);
        }

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

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
