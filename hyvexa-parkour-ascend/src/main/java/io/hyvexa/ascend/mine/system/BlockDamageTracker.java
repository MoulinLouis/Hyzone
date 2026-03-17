package io.hyvexa.ascend.mine.system;

import io.hyvexa.ascend.mine.data.MineZone;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player block damage for multi-HP blocks.
 * Each player has independent damage progress on each block position.
 * HP values are read from zone/layer config (block_hp_json).
 */
public class BlockDamageTracker {

    /** How long before damage progress resets if the player stops hitting (ms). */
    private static final long DAMAGE_TIMEOUT_MS = 3000;

    // playerId -> (packedPos -> state)
    private final Map<UUID, Map<Long, BlockDamageState>> playerDamage = new ConcurrentHashMap<>();

    /**
     * Records a hit on a block with 1 base damage.
     */
    public HitResult recordHit(UUID playerId, int x, int y, int z, String blockTypeId, MineZone zone) {
        return recordHit(playerId, x, y, z, blockTypeId, zone, 1.0);
    }

    /**
     * Records a hit on a block with a damage multiplier (e.g. Momentum combo bonus).
     * HP is resolved from the zone/layer config based on Y coordinate.
     */
    public HitResult recordHit(UUID playerId, int x, int y, int z, String blockTypeId, MineZone zone, double damageMultiplier) {
        int maxHp = zone.getBlockHpForY(blockTypeId, y);

        if (maxHp <= 1) {
            return HitResult.INSTANT_BREAK;
        }

        long packedPos = packPosition(x, y, z);
        long now = System.currentTimeMillis();

        Map<Long, BlockDamageState> blocks = playerDamage.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        BlockDamageState state = blocks.get(packedPos);

        if (state == null || now - state.lastHitMs > DAMAGE_TIMEOUT_MS || !blockTypeId.equals(state.blockTypeId)) {
            state = new BlockDamageState(blockTypeId, maxHp, maxHp, now);
            blocks.put(packedPos, state);
        }

        state.lastHitMs = now;
        int damage = Math.max(1, (int) Math.round(damageMultiplier));
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

    private static long packPosition(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    public static class BlockDamageState {
        public final String blockTypeId;
        public final int maxHp;
        public int currentHp;
        public long lastHitMs;

        BlockDamageState(String blockTypeId, int maxHp, int currentHp, long lastHitMs) {
            this.blockTypeId = blockTypeId;
            this.maxHp = maxHp;
            this.currentHp = currentHp;
            this.lastHitMs = lastHitMs;
        }
    }

    public record HitResult(int remainingHp, int maxHp, boolean shouldBreak) {
        public static final HitResult INSTANT_BREAK = new HitResult(0, 1, true);

        public float healthFraction() {
            return maxHp > 0 ? (float) remainingHp / maxHp : 0f;
        }
    }
}
