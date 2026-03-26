package io.hyvexa.ascend.mine.system;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.util.MinePositionUtils;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineUpgradeType;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.ascend.mine.hud.MineHudManager;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Static utility for AoE block breaking: Jackhammer (column), Stomp (layer), Blast (sphere).
 * Called after a block break to process additional blocks from pickaxe upgrades.
 */
public final class MineAoEBreaker {

    private MineAoEBreaker() {}

    public static void triggerAoE(UUID playerId, MinePlayerProgress progress, MineZone zone,
                                   World world, int centerX, int centerY, int centerZ,
                                   MineManager mineManager, MineHudManager mineHudManager,
                                   MineAchievementTracker achievementTracker, MinePlayerStore minePlayerStore,
                                   BlockDamageTracker damageTracker) {
        int fortuneLevel = progress.getUpgradeLevel(MineUpgradeType.FORTUNE);

        // Collect all AoE positions (deduplicated)
        Set<Long> seen = new HashSet<>();
        List<Vector3i> allPositions = new ArrayList<>();

        // Jackhammer: column below (probability-based)
        int jackhammerLevel = progress.getUpgradeLevel(MineUpgradeType.JACKHAMMER);
        if (jackhammerLevel > 0 && ThreadLocalRandom.current().nextDouble() < MineUpgradeType.JACKHAMMER.getChance(jackhammerLevel)) {
            int depth = jackhammerLevel; // depth = level
            for (Vector3i pos : buildJackhammerPositions(centerX, centerY, centerZ, depth)) {
                long key = MinePositionUtils.packPosition(pos.getX(), pos.getY(), pos.getZ());
                if (seen.add(key)) allPositions.add(pos);
            }
        }

        // Stomp: horizontal layer (probability-based)
        int stompLevel = progress.getUpgradeLevel(MineUpgradeType.STOMP);
        if (stompLevel > 0 && ThreadLocalRandom.current().nextDouble() < MineUpgradeType.STOMP.getChance(stompLevel)) {
            int radius = (int) MineUpgradeType.STOMP.getEffect(stompLevel);
            for (Vector3i pos : buildStompPositions(centerX, centerY, centerZ, radius)) {
                long key = MinePositionUtils.packPosition(pos.getX(), pos.getY(), pos.getZ());
                if (seen.add(key)) allPositions.add(pos);
            }
        }

        // Blast: sphere (probability-based)
        int blastLevel = progress.getUpgradeLevel(MineUpgradeType.BLAST);
        if (blastLevel > 0 && ThreadLocalRandom.current().nextDouble() < MineUpgradeType.BLAST.getChance(blastLevel)) {
            int radius = (int) MineUpgradeType.BLAST.getEffect(blastLevel);
            for (Vector3i pos : buildBlastPositions(centerX, centerY, centerZ, radius)) {
                long key = MinePositionUtils.packPosition(pos.getX(), pos.getY(), pos.getZ());
                if (seen.add(key)) allPositions.add(pos);
            }
        }

        if (allPositions.isEmpty()) return;

        breakBlocksAt(allPositions, zone, progress, playerId, world, mineManager, fortuneLevel,
            mineHudManager, achievementTracker, minePlayerStore, damageTracker);
    }

    private static int breakBlocksAt(List<Vector3i> positions, MineZone zone, MinePlayerProgress progress,
                                      UUID playerId, World world, MineManager mineManager, int fortuneLevel,
                                      MineHudManager mineHudManager, MineAchievementTracker achievementTracker,
                                      MinePlayerStore minePlayerStore, BlockDamageTracker damageTracker) {
        int totalBroken = 0;
        MineConfigStore configStore = mineManager.getConfigStore();
        int cashbackLevel = progress.getUpgradeLevel(MineUpgradeType.CASHBACK);
        double cashbackPercent = cashbackLevel > 0 ? MineUpgradeType.CASHBACK.getEffect(cashbackLevel) : 0;
        double aoeDamage = progress.getPickaxeDamage() * progress.getMomentumMultiplier();

        for (Vector3i pos : positions) {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();

            // Must be in zone bounds
            if (!zone.contains(x, y, z)) continue;

            // Get block type before any modification
            String blockTypeId = getBlockTypeAt(world, x, y, z);
            if (blockTypeId == null) continue; // already air or unloaded

            int blockHp = configStore.getBlockHp(blockTypeId);

            // Multi-HP blocks: apply damage via tracker
            if (blockHp > 1) {
                BlockDamageTracker.HitResult hitResult = damageTracker.recordHit(
                    playerId, x, y, z, blockTypeId, blockHp, aoeDamage);

                // Send crack visuals to this player only
                BlockVisualHelper.sendBlockCracks(world, playerId, x, y, z,
                    hitResult.healthFraction(), -(float) (aoeDamage / blockHp));

                if (!hitResult.shouldBreak()) {
                    continue; // block survives — cracks shown, no reward
                }
                // Fall through to break logic
            }

            // Atomically claim
            if (!mineManager.tryClaimBlock(zone.getId(), x, y, z)) continue;

            // Set to air
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            var worldChunk = world.getChunkIfInMemory(chunkIndex);
            if (worldChunk == null) worldChunk = world.loadChunkIfInMemory(chunkIndex);
            if (worldChunk == null) {
                mineManager.unclaimBlock(zone.getId(), x, y, z);
                continue;
            }
            worldChunk.setBlock(x, y, z, 0);

            // Fortune roll for each AoE block
            int blocksGained = MineRewardHelper.rollFortune(fortuneLevel);

            // Add to inventory, auto-sell overflow
            int stored = progress.addToInventoryUpTo(blockTypeId, blocksGained);
            long blockPrice = configStore.getBlockPrice(blockTypeId);
            if (stored < blocksGained) {
                int overflow = blocksGained - stored;
                long fallbackCrystals = blockPrice * overflow;
                progress.addCrystals(fallbackCrystals);
            }

            // Cashback
            if (cashbackLevel > 0) {
                double cashbackAmount = MineRewardHelper.calculateCashbackAmount(blockPrice, blocksGained, cashbackPercent);
                if (cashbackAmount > 0) {
                    progress.addCrystals(cashbackAmount);
                }
            }

            // Feed Momentum combo
            int momentumLevel = progress.getUpgradeLevel(MineUpgradeType.MOMENTUM);
            if (momentumLevel > 0) {
                int maxCombo = progress.getMaxCombo();
                if (progress.getComboCount() < maxCombo) {
                    progress.incrementCombo();
                }
            }

            totalBroken += blocksGained;
        }

        // Show toast for total AoE blocks
        if (totalBroken > 0) {
            if (mineHudManager != null) {
                mineHudManager.showMineToast(playerId, "AoE", totalBroken);
            }

            if (achievementTracker != null) {
                achievementTracker.incrementBlocksMined(playerId, totalBroken);
                achievementTracker.incrementManualBlocksMined(playerId, totalBroken);
            }

            if (minePlayerStore != null) {
                minePlayerStore.markDirty(playerId);
            }
        }

        return totalBroken;
    }

    // Cached reverse lookup: block runtime ID -> block type string ID
    private static Map<Integer, String> reverseBlockMap;

    private static synchronized Map<Integer, String> getReverseBlockMap() {
        Map<Integer, String> map = reverseBlockMap;
        if (map != null) return map;
        map = new HashMap<>();
        var assetMap = BlockType.getAssetMap().getAssetMap();
        for (var entry : assetMap.entrySet()) {
            String id = entry.getKey();
            int index = BlockType.getAssetMap().getIndex(id);
            if (index >= 0) {
                map.put(index, id);
            }
        }
        reverseBlockMap = map;
        return map;
    }

    private static String getBlockTypeAt(World world, int x, int y, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        var worldChunk = world.getChunkIfInMemory(chunkIndex);
        if (worldChunk == null) worldChunk = world.loadChunkIfInMemory(chunkIndex);
        if (worldChunk == null) return null;
        int blockId = worldChunk.getBlock(x, y, z);
        if (blockId == 0) return null; // already air
        return getReverseBlockMap().get(blockId);
    }

    private static List<Vector3i> buildJackhammerPositions(int centerX, int centerY, int centerZ, int depth) {
        List<Vector3i> positions = new ArrayList<>();
        for (int dy = 1; dy <= depth; dy++) {
            positions.add(new Vector3i(centerX, centerY - dy, centerZ));
        }
        return positions;
    }

    private static List<Vector3i> buildStompPositions(int centerX, int centerY, int centerZ, int radius) {
        List<Vector3i> positions = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue; // skip center (already broken)
                positions.add(new Vector3i(centerX + dx, centerY, centerZ + dz));
            }
        }
        return positions;
    }

    private static List<Vector3i> buildBlastPositions(int centerX, int centerY, int centerZ, int radius) {
        List<Vector3i> positions = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue; // skip center
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist <= radius) {
                        positions.add(new Vector3i(centerX + dx, centerY + dy, centerZ + dz));
                    }
                }
            }
        }
        return positions;
    }

}
