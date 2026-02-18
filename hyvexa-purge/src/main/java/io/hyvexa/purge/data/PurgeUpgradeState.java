package io.hyvexa.purge.data;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class PurgeUpgradeState {

    private final Map<PurgeUpgradeType, Integer> stacks =
            Collections.synchronizedMap(new EnumMap<>(PurgeUpgradeType.class));

    public int getStacks(PurgeUpgradeType type) {
        return stacks.getOrDefault(type, 0);
    }

    public void addStack(PurgeUpgradeType type) {
        stacks.merge(type, 1, Integer::sum);
    }
}
