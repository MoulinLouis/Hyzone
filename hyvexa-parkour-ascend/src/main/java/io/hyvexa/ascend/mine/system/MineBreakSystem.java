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
import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.util.PermissionUtils;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MineBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final MineManager mineManager;
    private final MinePlayerStore minePlayerStore;
    private final Map<UUID, Long> lastBagFullMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRegenMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBreakTime = new ConcurrentHashMap<>();

    public MineBreakSystem(MineManager mineManager, MinePlayerStore minePlayerStore) {
        super(BreakBlockEvent.class);
        this.mineManager = mineManager;
        this.minePlayerStore = minePlayerStore;
    }

    @Override
    public void handle(int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> buffer, BreakBlockEvent event) {
        LOGGER.atInfo().log("[MineBreak] handle() called, cancelled=%s", event.isCancelled());

        Player player = chunk.getComponent(entityId, Player.getComponentType());
        if (player == null) {
            LOGGER.atInfo().log("[MineBreak] player is null, skipping");
            return;
        }
        boolean isOp = PermissionUtils.isOp(player);

        Vector3i target = event.getTargetBlock();
        if (target == null) {
            LOGGER.atInfo().log("[MineBreak] targetBlock is null, skipping");
            return;
        }
        int bx = target.getX();
        int by = target.getY();
        int bz = target.getZ();
        LOGGER.atInfo().log("[MineBreak] block at (%d, %d, %d)", bx, by, bz);

        MineZone zone = mineManager.findZoneAt(bx, by, bz);
        if (zone == null) {
            if (!isOp) {
                LOGGER.atInfo().log("[MineBreak] not in any zone, keeping cancelled");
            }
            return;
        }
        LOGGER.atInfo().log("[MineBreak] in zone '%s'", zone.getId());

        // Block is in a mining zone — check if player can mine
        PlayerRef playerRef = chunk.getComponent(entityId, PlayerRef.getComponentType());
        if (playerRef == null) {
            LOGGER.atInfo().log("[MineBreak] playerRef is null");
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            LOGGER.atInfo().log("[MineBreak] playerId is null");
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
            LOGGER.atInfo().log("[MineBreak] ascendProgress is null, denied");
            return;
        }
        LOGGER.atInfo().log("[MineBreak] ascensionCount=%d", ascendProgress.getAscensionCount());
        if (ascendProgress.getAscensionCount() < 1) {
            LOGGER.atInfo().log("[MineBreak] no ascension, denied");
            return;
        }

        MinePlayerProgress mineProgress = minePlayerStore.getOrCreatePlayer(playerId);
        LOGGER.atInfo().log("[MineBreak] inventory %d/%d", mineProgress.getInventoryTotal(), mineProgress.getBagCapacity());
        if (!mineProgress.isAutoSellEnabled() && mineProgress.isInventoryFull()) {
            LOGGER.atInfo().log("[MineBreak] bag full, denied");
            long now = System.currentTimeMillis();
            Long last = lastBagFullMessage.get(playerId);
            if (last == null || now - last > 3000) {
                lastBagFullMessage.put(playerId, now);
                player.sendMessage(Message.raw("Bag full! Sell your blocks with /mine sell"));
            }
            return;
        }

        // Mining speed cooldown
        long now = System.currentTimeMillis();
        Long lastBreak = lastBreakTime.get(playerId);
        double speedMult = mineProgress.getMiningSpeedMultiplier();
        long cooldownMs = (long) (500 / speedMult);
        if (lastBreak != null && now - lastBreak < cooldownMs) {
            return;
        }
        lastBreakTime.put(playerId, now);

        // Resolve block type name
        String blockTypeName = event.getBlockType() != null ? event.getBlockType().getId() : null;
        LOGGER.atInfo().log("[MineBreak] blockType=%s", blockTypeName);

        // For non-OP: manually remove the block (set to air) — we can't rely on setCancelled(false)
        // because NoBreakSystem may run after us and re-cancel the event.
        // For OP: NoBreakSystem already allows the break, no manual removal needed.
        if (!isOp) {
            World world = store.getExternalData().getWorld();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(bx, bz);
            var worldChunk = world.getChunkIfInMemory(chunkIndex);
            if (worldChunk == null) worldChunk = world.loadChunkIfInMemory(chunkIndex);
            if (worldChunk == null) {
                LOGGER.atInfo().log("[MineBreak] chunk not loaded, cannot break");
                return;
            }
            worldChunk.setBlock(bx, by, bz, 0);
        }
        LOGGER.atInfo().log("[MineBreak] ALLOWED break (op=%s)", isOp);

        if (blockTypeName != null) {
            int blocksGained = 1;
            double multiBreakChance = mineProgress.getMultiBreakChance();
            if (multiBreakChance > 0 && ThreadLocalRandom.current().nextDouble() < multiBreakChance) {
                blocksGained = 2;
            }

            if (mineProgress.isAutoSellEnabled()) {
                MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
                if (configStore != null) {
                    HashMap<String, BigNumber> prices = new HashMap<>();
                    for (Mine mine : configStore.listMinesSorted()) {
                        prices.putAll(configStore.getBlockPrices(mine.getId()));
                    }
                    BigNumber price = prices.getOrDefault(blockTypeName, BigNumber.ONE);
                    long earned = price.multiply(BigNumber.of(blocksGained, 0)).toLong();
                    mineProgress.addCrystals(earned);
                }
            } else {
                mineProgress.addToInventory(blockTypeName, blocksGained);
            }
            minePlayerStore.markDirty(playerId);
        }

        // Track broken block in zone
        mineManager.markBlockBroken(zone.getId(), bx, by, bz);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
