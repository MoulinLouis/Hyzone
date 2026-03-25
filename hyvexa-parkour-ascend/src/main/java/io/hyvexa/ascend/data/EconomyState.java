package io.hyvexa.ascend.data;

import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.common.math.BigNumber;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Economy-related state for an Ascend player: volt balance, elevation multiplier, and summit XP.
 */
public class EconomyState {

    private final AtomicReference<BigNumber> volt = new AtomicReference<>(BigNumber.ZERO);
    private final AtomicReference<BigNumber> totalVoltEarned = new AtomicReference<>(BigNumber.ZERO);
    private final AtomicReference<BigNumber> summitAccumulatedVolt = new AtomicReference<>(BigNumber.ZERO);
    private final AtomicReference<BigNumber> elevationAccumulatedVolt = new AtomicReference<>(BigNumber.ZERO);
    private final AtomicInteger elevationMultiplier = new AtomicInteger(1);
    private final Map<AscendConstants.SummitCategory, Double> summitXp = new ConcurrentHashMap<>();

    // ── Volt ─────────────────────────────────────────────────────────────

    public BigNumber getVolt() { return volt.get(); }

    public void setVolt(BigNumber value) { this.volt.set(value); }

    public boolean casVolt(BigNumber expect, BigNumber update) { return this.volt.compareAndSet(expect, update); }

    public void addVolt(BigNumber amount) { volt.updateAndGet(c -> c.add(amount).max(BigNumber.ZERO)); }

    /**
     * Atomically adds the given amount to volt and returns both old and new values.
     * Uses a CAS loop to guarantee the returned old value is the true pre-update value.
     *
     * @return array of [oldValue, newValue]
     */
    public BigNumber[] addVoltAndCapture(BigNumber amount) {
        while (true) {
            BigNumber oldValue = volt.get();
            BigNumber newValue = oldValue.add(amount).max(BigNumber.ZERO);
            if (volt.compareAndSet(oldValue, newValue)) {
                return new BigNumber[] { oldValue, newValue };
            }
        }
    }

    // ── Total Volt Earned ────────────────────────────────────────────────

    public BigNumber getTotalVoltEarned() { return totalVoltEarned.get(); }

    public void setTotalVoltEarned(BigNumber value) { this.totalVoltEarned.set(value.max(BigNumber.ZERO)); }

    public void addTotalVoltEarned(BigNumber amount) {
        if (amount.gt(BigNumber.ZERO)) { totalVoltEarned.updateAndGet(c -> c.add(amount).max(BigNumber.ZERO)); }
    }

    // ── Summit Accumulated Volt ──────────────────────────────────────────

    public BigNumber getSummitAccumulatedVolt() { return summitAccumulatedVolt.get(); }

    public void setSummitAccumulatedVolt(BigNumber value) { this.summitAccumulatedVolt.set(value.max(BigNumber.ZERO)); }

    public void addSummitAccumulatedVolt(BigNumber amount) {
        if (amount.gt(BigNumber.ZERO)) { summitAccumulatedVolt.updateAndGet(c -> c.add(amount).max(BigNumber.ZERO)); }
    }

    // ── Elevation Accumulated Volt ───────────────────────────────────────

    public BigNumber getElevationAccumulatedVolt() { return elevationAccumulatedVolt.get(); }

    public void setElevationAccumulatedVolt(BigNumber value) { this.elevationAccumulatedVolt.set(value.max(BigNumber.ZERO)); }

    public void addElevationAccumulatedVolt(BigNumber amount) {
        if (amount.gt(BigNumber.ZERO)) { elevationAccumulatedVolt.updateAndGet(c -> c.add(amount).max(BigNumber.ZERO)); }
    }

    // ── Elevation Multiplier ─────────────────────────────────────────────

    public int getElevationMultiplier() { return elevationMultiplier.get(); }

    public void setElevationMultiplier(int elevationMultiplier) { this.elevationMultiplier.set(Math.max(1, elevationMultiplier)); }

    public int addElevationMultiplier(int amount) { return elevationMultiplier.updateAndGet(current -> Math.max(1, current + amount)); }

    // ── Summit XP ────────────────────────────────────────────────────────

    public double getSummitXp(AscendConstants.SummitCategory category) { return summitXp.getOrDefault(category, 0.0); }

    public void setSummitXp(AscendConstants.SummitCategory category, double xp) { summitXp.put(category, Math.max(0.0, xp)); }

    public double addSummitXp(AscendConstants.SummitCategory category, double amount) {
        return summitXp.compute(category, (cat, current) -> {
            double base = current != null ? current : 0.0;
            return Math.max(0.0, base + amount);
        });
    }

    public int getSummitLevel(AscendConstants.SummitCategory category) { return AscendConstants.calculateLevelFromXp(getSummitXp(category)); }

    public Map<AscendConstants.SummitCategory, Double> getSummitXpMap() {
        Map<AscendConstants.SummitCategory, Double> xpMap = new EnumMap<>(AscendConstants.SummitCategory.class);
        for (AscendConstants.SummitCategory cat : AscendConstants.SummitCategory.values()) { xpMap.put(cat, getSummitXp(cat)); }
        return xpMap;
    }

    public void clearSummitXp() { summitXp.clear(); }

    public Map<AscendConstants.SummitCategory, Integer> getSummitLevels() {
        Map<AscendConstants.SummitCategory, Integer> levels = new EnumMap<>(AscendConstants.SummitCategory.class);
        for (AscendConstants.SummitCategory cat : AscendConstants.SummitCategory.values()) { levels.put(cat, getSummitLevel(cat)); }
        return levels;
    }
}
