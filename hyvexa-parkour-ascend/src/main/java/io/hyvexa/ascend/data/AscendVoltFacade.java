package io.hyvexa.ascend.data;

import io.hyvexa.common.math.BigNumber;

import java.util.Map;
import java.util.UUID;

/**
 * Facade for volt (currency) operations on Ascend player data.
 * Delegates to the shared player cache with dirty-marking for persistence.
 */
public class AscendVoltFacade {

    private final Map<UUID, AscendPlayerProgress> players;
    private final AscendPlayerStore store;

    AscendVoltFacade(Map<UUID, AscendPlayerProgress> players, AscendPlayerStore store) {
        this.players = players;
        this.store = store;
    }

    public BigNumber getVolt(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.economy().getVolt() : BigNumber.ZERO;
    }

    public void setVolt(UUID playerId, BigNumber volt) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.economy().setVolt(volt.max(BigNumber.ZERO));
        store.markDirty(playerId);
    }

    public void addVolt(UUID playerId, BigNumber amount) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.economy().addVolt(amount);
        store.markDirty(playerId);
    }

    /**
     * Returns [oldBalance, newBalance] for callers that need to detect threshold crossings.
     */
    public BigNumber[] atomicAddVolt(UUID playerId, BigNumber amount) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        BigNumber[] result = progress.economy().addVoltAndCapture(amount);
        store.markDirty(playerId);
        return result;
    }

    /**
     * Spend volt with CAS loop to prevent negative balance.
     */
    public boolean atomicSpendVolt(UUID playerId, BigNumber amount) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        BigNumber current;
        BigNumber updated;
        do {
            current = progress.economy().getVolt();
            if (current.lt(amount)) {
                return false;
            }
            updated = current.subtract(amount);
        } while (!progress.economy().casVolt(current, updated));
        store.markDirty(playerId);
        return true;
    }

    public BigNumber getTotalVoltEarned(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.economy().getTotalVoltEarned() : BigNumber.ZERO;
    }

    public void addTotalVoltEarned(UUID playerId, BigNumber amount) {
        if (amount.lte(BigNumber.ZERO)) {
            return;
        }
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        progress.economy().addTotalVoltEarned(amount);
        progress.economy().addSummitAccumulatedVolt(amount);
        progress.economy().addElevationAccumulatedVolt(amount);
        store.markDirty(playerId);
    }
}
