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
import io.hyvexa.ascend.mine.data.MineUpgradeType;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.hud.MineHudManager;
import io.hyvexa.common.math.BigNumber;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

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

        // Multi-HP: record hit with Momentum damage bonus
        double damageMultiplier = mineProgress.getMomentumMultiplier();
        BlockDamageTracker.HitResult hitResult = damageTracker.recordHit(playerId, bx, by, bz, blockTypeName, zone, damageMultiplier);

        MineHudManager mineHudManager = ParkourAscendPlugin.getInstance().getMineHudManager();

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
        int blocksGained = rollFortune(fortuneLevel);

        // Reward
        int stored = mineProgress.addToInventoryUpTo(blockTypeName, blocksGained);
        if (stored < blocksGained) {
            MineConfigStore configStore = mineManager.getConfigStore();
            BigNumber blockPrice = configStore.getBlockPrice(zone.getMineId(), blockTypeName);
            int overflow = blocksGained - stored;
            long fallbackCrystals = blockPrice.multiply(BigNumber.of(overflow, 0)).toLong();
            mineProgress.addCrystals(fallbackCrystals);
            if (stored == 0) {
                sendBagFullMessage(playerId, player);
            }
        }
        minePlayerStore.markDirty(playerId);

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

        // Momentum combo
        int momentumLevel = mineProgress.getUpgradeLevel(MineUpgradeType.MOMENTUM);
        if (momentumLevel > 0) {
            mineProgress.checkComboExpired();
            int maxCombo = mineProgress.getMaxCombo();
            if (mineProgress.getComboCount() < maxCombo) {
                mineProgress.incrementCombo();
            } else {
                mineProgress.incrementCombo(); // refresh timer even at max
            }
            if (mineHudManager != null) {
                mineHudManager.showCombo(playerId, mineProgress.getComboCount(), 1.0f);
            }
        }

        // AoE upgrades (Jackhammer, Stomp, Blast)
        World aoeWorld = store.getExternalData().getWorld();
        if (aoeWorld != null) {
            MineAoEBreaker.triggerAoE(playerId, mineProgress, zone, aoeWorld, bx, by, bz, mineManager);
        }
    }

    private static int rollFortune(int fortuneLevel) {
        if (fortuneLevel <= 0) return 1;
        double tripleChance = fortuneLevel * 0.4 / 100.0;
        double doubleChance = fortuneLevel * 2.0 / 100.0;
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < tripleChance) return 3;
        if (roll < tripleChance + doubleChance) return 2;
        return 1;
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
        damageTracker.evict(playerId);
    }

    public BlockDamageTracker getDamageTracker() {
        return damageTracker;
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
