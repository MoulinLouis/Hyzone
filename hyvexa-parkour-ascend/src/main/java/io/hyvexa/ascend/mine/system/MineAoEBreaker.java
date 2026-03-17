package io.hyvexa.ascend.mine.system;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineUpgradeType;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.ascend.mine.hud.MineHudManager;
import io.hyvexa.common.math.BigNumber;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
                                   MineManager mineManager) {
        int fortuneLevel = progress.getUpgradeLevel(MineUpgradeType.FORTUNE);

        // Collect all AoE positions (deduplicated)
        Set<Long> seen = new HashSet<>();
        List<Vector3i> allPositions = new ArrayList<>();

        // Jackhammer: column below
        int jackhammerLevel = progress.getUpgradeLevel(MineUpgradeType.JACKHAMMER);
        if (jackhammerLevel > 0) {
            int depth = jackhammerLevel; // depth = level
            for (Vector3i pos : buildJackhammerPositions(centerX, centerY, centerZ, depth)) {
                long key = packPos(pos.getX(), pos.getY(), pos.getZ());
                if (seen.add(key)) allPositions.add(pos);
            }
        }

        // Stomp: horizontal layer
        int stompLevel = progress.getUpgradeLevel(MineUpgradeType.STOMP);
        if (stompLevel > 0) {
            int radius = 1 + stompLevel / 5; // r1 at 1-4, r2 at 5-9, etc.
            for (Vector3i pos : buildStompPositions(centerX, centerY, centerZ, radius)) {
                long key = packPos(pos.getX(), pos.getY(), pos.getZ());
                if (seen.add(key)) allPositions.add(pos);
            }
        }

        // Blast: sphere
        int blastLevel = progress.getUpgradeLevel(MineUpgradeType.BLAST);
        if (blastLevel > 0) {
            int radius = 1 + blastLevel / 5;
            for (Vector3i pos : buildBlastPositions(centerX, centerY, centerZ, radius)) {
                long key = packPos(pos.getX(), pos.getY(), pos.getZ());
                if (seen.add(key)) allPositions.add(pos);
            }
        }

        if (allPositions.isEmpty()) return;

        breakBlocksAt(allPositions, zone, progress, playerId, world, mineManager, fortuneLevel);
    }

    private static int breakBlocksAt(List<Vector3i> positions, MineZone zone, MinePlayerProgress progress,
                                      UUID playerId, World world, MineManager mineManager, int fortuneLevel) {
        int totalBroken = 0;
        MineConfigStore configStore = mineManager.getConfigStore();

        for (Vector3i pos : positions) {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();

            // Must be in zone bounds
            if (!zone.contains(x, y, z)) continue;

            // Get block type before any modification
            String blockTypeId = getBlockTypeAt(world, x, y, z);
            if (blockTypeId == null) continue; // already air or unloaded

            // Skip multi-HP blocks (AoE only breaks 1-HP blocks)
            int blockHp = zone.getBlockHpForY(blockTypeId, y);
            if (blockHp > 1) continue;

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
            int blocksGained = rollFortune(fortuneLevel);

            // Add to inventory, auto-sell overflow
            int stored = progress.addToInventoryUpTo(blockTypeId, blocksGained);
            if (stored < blocksGained) {
                int overflow = blocksGained - stored;
                BigNumber blockPrice = configStore.getBlockPrice(zone.getMineId(), blockTypeId);
                long fallbackCrystals = blockPrice.multiply(BigNumber.of(overflow, 0)).toLong();
                progress.addCrystals(fallbackCrystals);
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
            MineHudManager mineHudManager = ParkourAscendPlugin.getInstance().getMineHudManager();
            if (mineHudManager != null) {
                mineHudManager.showMineToast(playerId, "AoE", totalBroken);
            }

            // Track achievements
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin != null) {
                MineAchievementTracker tracker = plugin.getMineAchievementTracker();
                if (tracker != null) {
                    tracker.incrementBlocksMined(playerId, totalBroken);
                }
            }

            // Mark dirty for save
            MinePlayerStore store = ParkourAscendPlugin.getInstance().getMinePlayerStore();
            if (store != null) {
                store.markDirty(playerId);
            }
        }

        return totalBroken;
    }

    private static String getBlockTypeAt(World world, int x, int y, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        var worldChunk = world.getChunkIfInMemory(chunkIndex);
        if (worldChunk == null) worldChunk = world.loadChunkIfInMemory(chunkIndex);
        if (worldChunk == null) return null;
        int blockId = worldChunk.getBlock(x, y, z);
        if (blockId == 0) return null; // already air
        BlockType blockType = BlockType.getByRuntimeId(blockId);
        return blockType != null ? blockType.getId() : null;
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

    private static long packPos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }
}
