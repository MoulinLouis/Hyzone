package io.hyvexa.ascend.data;

import io.hyvexa.ascend.ElevationConstants;
import io.hyvexa.ascend.SummitConstants;
import io.hyvexa.ascend.SummitConstants.SummitCategory;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.common.math.BigNumber;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facade for progression operations: elevation, summit, accumulated volt,
 * ascension/transcendence counts, and multiplier calculations.
 * Delegates to the shared player cache with dirty-marking for persistence.
 */
public class AscendProgressionFacade {

    private final Map<UUID, AscendPlayerProgress> players;
    private final AscendPlayerStore store;
    private ChallengeManager challengeManager;

    AscendProgressionFacade(Map<UUID, AscendPlayerProgress> players, AscendPlayerStore store) {
        this.players = players;
        this.store = store;
    }

    void setChallengeManager(ChallengeManager challengeManager) {
        this.challengeManager = challengeManager;
    }

    /**
     * Gets the raw elevation level (stored value).
     * For the actual multiplier value, use {@link #getCalculatedElevationMultiplier(UUID)}.
     */
    public int getElevationLevel(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.economy().getElevationMultiplier() : 0;
    }

    /**
     * Get the player's effective elevation multiplier.
     * Base: level (1:1). C8 reward: x1.25 bonus.
     */
    public double getCalculatedElevationMultiplier(UUID playerId) {
        double base = ElevationConstants.getElevationMultiplier(getElevationLevel(playerId));
        if (challengeManager != null) {
            base *= challengeManager.getChallengeElevationBonus(playerId);
        }
        return base;
    }

    /**
     * Add elevation levels to a player.
     * @return The new total elevation level
     */
    public int addElevationLevel(UUID playerId, int amount) {
        if (amount <= 0) {
            return getElevationLevel(playerId);
        }
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        int value = progress.economy().addElevationMultiplier(amount);
        store.markDirty(playerId);
        return value;
    }

    /**
     * Set elevation level and reset volt to 0 (for elevation purchase).
     */
    public void atomicSetElevationAndResetVolt(UUID playerId, int newElevation) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.economy().setElevationMultiplier(newElevation);
        progress.economy().setVolt(BigNumber.ZERO);
        progress.economy().setSummitAccumulatedVolt(BigNumber.ZERO);
        progress.economy().setElevationAccumulatedVolt(BigNumber.ZERO);
        store.markDirty(playerId);
    }

    public int getSummitLevel(UUID playerId, SummitCategory category) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.economy().getSummitLevel(category) : 0;
    }

    public double getSummitXp(UUID playerId, SummitCategory category) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.economy().getSummitXp(category) : 0.0;
    }

    public double addSummitXp(UUID playerId, SummitCategory category, double amount) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        double newXp = progress.economy().addSummitXp(category, amount);
        store.markDirty(playerId);
        return newXp;
    }

    public Map<SummitCategory, Integer> getSummitLevels(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.economy().getSummitLevels() : Map.of();
    }

    public double getSummitBonusDouble(UUID playerId, SummitCategory category) {
        int level = getSummitLevel(playerId, category);
        return category.getBonusForLevel(level);
    }

    public BigNumber getSummitAccumulatedVolt(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.economy().getSummitAccumulatedVolt() : BigNumber.ZERO;
    }

    public void addSummitAccumulatedVolt(UUID playerId, BigNumber amount) {
        if (amount.lte(BigNumber.ZERO)) {
            return;
        }
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.economy().addSummitAccumulatedVolt(amount);
        store.markDirty(playerId);
    }

    public BigNumber getElevationAccumulatedVolt(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.economy().getElevationAccumulatedVolt() : BigNumber.ZERO;
    }

    public void addElevationAccumulatedVolt(UUID playerId, BigNumber amount) {
        if (amount.lte(BigNumber.ZERO)) {
            return;
        }
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.economy().addElevationAccumulatedVolt(amount);
        store.markDirty(playerId);
    }

    public int getAscensionCount(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.gameplay().getAscensionCount() : 0;
    }

    public int getTranscendenceCount(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.gameplay().getTranscendenceCount() : 0;
    }

    /**
     * Computes multiplier product and per-slot display values in a single pass.
     * Resolves the player once and accesses map progress directly to avoid
     * per-slot map lookups through the runner facade.
     */
    public AscendPlayerStore.MultiplierResult getMultiplierProductAndValues(UUID playerId, List<AscendMap> maps, int slotCount) {
        int slots = Math.max(0, slotCount);
        BigNumber[] digits = new BigNumber[slots];
        for (int i = 0; i < slots; i++) {
            digits[i] = BigNumber.ONE;
        }
        BigNumber product = BigNumber.ONE;
        if (maps != null && !maps.isEmpty() && slots > 0) {
            AscendPlayerProgress progress = players.get(playerId);
            Map<String, GameplayState.MapProgress> mapProgressMap = progress != null
                    ? progress.gameplay().getMapProgress() : Map.of();
            int index = 0;
            for (AscendMap map : maps) {
                if (index >= slots) {
                    break;
                }
                if (map == null || map.getId() == null) {
                    continue;
                }
                BigNumber value = getMapMultiplierFromProgress(mapProgressMap, map.getId());
                double challengeMapBonus = getChallengeMapBonus(playerId, map.getDisplayOrder());
                if (challengeMapBonus > 1.0) {
                    value = value.multiply(BigNumber.fromDouble(challengeMapBonus));
                }
                digits[index] = value;
                product = product.multiply(value.max(BigNumber.ONE));
                index++;
            }
        }
        BigNumber elevation = BigNumber.fromDouble(getCalculatedElevationMultiplier(playerId));
        product = product.multiply(elevation);
        return new AscendPlayerStore.MultiplierResult(product, digits);
    }

    /**
     * Computes the multiplier product (with optional bonus on one map) in a single pass.
     * Used for payout calculations where a bonus amount is added to one specific map slot.
     */
    public BigNumber getCompletionPayout(UUID playerId, List<AscendMap> maps, int slotCount, String mapId, BigNumber bonusAmount) {
        BigNumber product = BigNumber.ONE;
        int slots = Math.max(0, slotCount);
        if (maps == null || maps.isEmpty() || slots == 0) {
            BigNumber elevation = BigNumber.fromDouble(getCalculatedElevationMultiplier(playerId));
            return product.multiply(elevation);
        }
        AscendPlayerProgress progress = players.get(playerId);
        Map<String, GameplayState.MapProgress> mapProgressMap = progress != null
                ? progress.gameplay().getMapProgress() : Map.of();
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            BigNumber value = getMapMultiplierFromProgress(mapProgressMap, map.getId());
            double challengeMapBonus = getChallengeMapBonus(playerId, map.getDisplayOrder());
            if (challengeMapBonus > 1.0) {
                value = value.multiply(BigNumber.fromDouble(challengeMapBonus));
            }
            if (map.getId().equals(mapId)) {
                value = value.add(bonusAmount);
            }
            product = product.multiply(value.max(BigNumber.ONE));
            index++;
        }
        BigNumber elevation = BigNumber.fromDouble(getCalculatedElevationMultiplier(playerId));
        return product.multiply(elevation);
    }

    private static BigNumber getMapMultiplierFromProgress(Map<String, GameplayState.MapProgress> mapProgressMap, String mapId) {
        GameplayState.MapProgress mapProgress = mapProgressMap.get(mapId);
        if (mapProgress == null) {
            return BigNumber.ONE;
        }
        return mapProgress.getMultiplier().max(BigNumber.ONE);
    }

    private double getChallengeMapBonus(UUID playerId, int displayOrder) {
        if (challengeManager != null) {
            return challengeManager.getChallengeMapBaseMultiplier(playerId, displayOrder);
        }
        return 1.0;
    }
}
