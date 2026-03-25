package io.hyvexa.purge.data;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class PurgeUpgradeState {

    private final Map<PurgeUpgradeType, Integer> accumulated =
            Collections.synchronizedMap(new EnumMap<>(PurgeUpgradeType.class));

    public int getAccumulated(PurgeUpgradeType type) {
        return accumulated.getOrDefault(type, 0);
    }

    public void addValue(PurgeUpgradeType type, int amount) {
        if (amount <= 0) {
            return;
        }
        accumulated.merge(type, amount, (old, val) -> Math.min(old + val, type.getMaxAccumulated()));
    }

    public int getRemaining(PurgeUpgradeType type) {
        return Math.max(0, type.getMaxAccumulated() - getAccumulated(type));
    }

    public boolean isAtCap(PurgeUpgradeType type) {
        return getRemaining(type) == 0;
    }

    public int getLevel(PurgeUpgradeType type) {
        int base = type.getBaseValue();
        return base > 0 ? getAccumulated(type) / base : 0;
    }

    public int getLuck() {
        return getAccumulated(PurgeUpgradeType.LUCK);
    }
}
