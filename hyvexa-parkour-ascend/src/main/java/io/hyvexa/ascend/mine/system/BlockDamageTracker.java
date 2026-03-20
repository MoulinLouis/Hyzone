package io.hyvexa.ascend.mine.system;

import io.hyvexa.ascend.mine.util.MinePositionUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player block damage for multi-HP blocks.
 * Each player has independent damage progress on each block position.
 * HP values are read from global block_hp config.
 */
public class BlockDamageTracker {

    /** How long before damage progress resets if the player stops hitting (ms). */
    private static final long DAMAGE_TIMEOUT_MS = 3000;

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
        long now = System.currentTimeMillis();

        Map<Long, BlockDamageState> blocks = playerDamage.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        BlockDamageState state = blocks.get(packedPos);

        if (state == null || now - state.lastHitMs > DAMAGE_TIMEOUT_MS || !blockTypeId.equals(state.blockTypeId)) {
            state = new BlockDamageState(blockTypeId, maxHp, maxHp, now);
            blocks.put(packedPos, state);
        }

        state.lastHitMs = now;
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
     * Cleans up expired entries for a player.
     */
    public void cleanupExpired(UUID playerId) {
        Map<Long, BlockDamageState> blocks = playerDamage.get(playerId);
        if (blocks == null) return;
        long now = System.currentTimeMillis();
        blocks.entrySet().removeIf(e -> now - e.getValue().lastHitMs > DAMAGE_TIMEOUT_MS);
        if (blocks.isEmpty()) {
            playerDamage.remove(playerId);
        }
    }

    public static class BlockDamageState {
        public final String blockTypeId;
        public final int maxHp;
        public double currentHp;
        public long lastHitMs;

        BlockDamageState(String blockTypeId, int maxHp, double currentHp, long lastHitMs) {
            this.blockTypeId = blockTypeId;
            this.maxHp = maxHp;
            this.currentHp = currentHp;
            this.lastHitMs = lastHitMs;
        }
    }

    public record HitResult(double remainingHp, int maxHp, boolean shouldBreak) {
        public static final HitResult INSTANT_BREAK = new HitResult(0, 1, true);

        public float healthFraction() {
            return maxHp > 0 ? (float) (remainingHp / maxHp) : 0f;
        }
    }
}
