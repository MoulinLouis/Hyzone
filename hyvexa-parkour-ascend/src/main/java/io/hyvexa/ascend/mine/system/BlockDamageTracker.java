package io.hyvexa.ascend.mine.system;

import io.hyvexa.ascend.mine.util.MinePositionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player block damage for multi-HP blocks.
 * Each player has independent damage progress on each block position.
 * Damage persists until the block breaks or the zone regenerates.
 */
public class BlockDamageTracker {

    // playerId -> (packedPos -> state)
    private final Map<UUID, Map<Long, BlockDamageState>> playerDamage = new ConcurrentHashMap<>();

    /**
     * Records a hit on a block with 1 base damage.
     */
    public HitResult recordHit(UUID playerId, int x, int y, int z, String blockTypeId, int maxHp) {
        return recordHit(playerId, x, y, z, blockTypeId, maxHp, 1.0);
    }

    /**
     * Records a hit on a block with a damage multiplier (e.g. Momentum combo bonus).
     */
    public HitResult recordHit(UUID playerId, int x, int y, int z, String blockTypeId, int maxHp, double damageMultiplier) {
        if (maxHp <= 1) {
            return HitResult.INSTANT_BREAK;
        }

        long packedPos = MinePositionUtils.packPosition(x, y, z);

        Map<Long, BlockDamageState> blocks = playerDamage.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        BlockDamageState state = blocks.get(packedPos);

        if (state == null || !blockTypeId.equals(state.blockTypeId)) {
            state = new BlockDamageState(blockTypeId, maxHp, maxHp);
            blocks.put(packedPos, state);
        }

        double damage = Math.max(1.0, damageMultiplier);
        state.currentHp -= damage;

        if (state.currentHp <= 0) {
            blocks.remove(packedPos);
            return new HitResult(0, maxHp, true);
        }

        return new HitResult(state.currentHp, maxHp, false);
    }

    /**
     * Removes all damage state for a player (disconnect).
     */
    public void evict(UUID playerId) {
        playerDamage.remove(playerId);
    }

    /**
     * Clear damage state for ALL players at a given position (zone regen).
     * Returns the set of player UUIDs that had damage state (for crack clear packets).
     */
    public Set<UUID> clearPosition(int x, int y, int z) {
        long packedPos = MinePositionUtils.packPosition(x, y, z);
        Set<UUID> affected = null;
        for (var entry : playerDamage.entrySet()) {
            if (entry.getValue().remove(packedPos) != null) {
                if (affected == null) affected = new HashSet<>();
                affected.add(entry.getKey());
            }
        }
        return affected;
    }

    /**
     * Clear ALL damage state and return player -> position mappings for crack visual clears.
     * Used during zone regen when all blocks are replaced.
     */
    public Map<UUID, List<int[]>> clearAllAndCollect() {
        Map<UUID, List<int[]>> result = null;
        var it = playerDamage.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID playerId = entry.getKey();
            Map<Long, BlockDamageState> blocks = entry.getValue();
            if (!blocks.isEmpty()) {
                if (result == null) result = new HashMap<>();
                List<int[]> positions = new ArrayList<>(blocks.size());
                for (long packed : blocks.keySet()) {
                    positions.add(MinePositionUtils.unpackPosition(packed));
                }
                result.put(playerId, positions);
            }
            it.remove();
        }
        return result;
    }

    public static class BlockDamageState {
        public final String blockTypeId;
        public final int maxHp;
        public double currentHp;

        BlockDamageState(String blockTypeId, int maxHp, double currentHp) {
            this.blockTypeId = blockTypeId;
            this.maxHp = maxHp;
            this.currentHp = currentHp;
        }
    }

    public record HitResult(double remainingHp, int maxHp, boolean shouldBreak) {
        public static final HitResult INSTANT_BREAK = new HitResult(0, 1, true);

        public float healthFraction() {
            return maxHp > 0 ? (float) (remainingHp / maxHp) : 0f;
        }
    }
}
