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
        accumulated.merge(type, amount, Integer::sum);
    }

    public int getLuck() {
        return getAccumulated(PurgeUpgradeType.LUCK);
    }
}
