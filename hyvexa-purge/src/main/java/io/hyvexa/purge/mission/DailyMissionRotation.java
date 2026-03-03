package io.hyvexa.purge.mission;

import io.hyvexa.common.util.DailyResetUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class DailyMissionRotation {

    private DailyMissionRotation() {}

    public static List<MissionDefinition> getTodaysMissions() {
        long daysSinceEpoch = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        List<MissionDefinition> result = new ArrayList<>(3);
        for (MissionDefinition.MissionCategory category : MissionDefinition.MissionCategory.values()) {
            List<MissionDefinition> pool = MissionDefinition.getByCategory(category);
            int index = new Random(daysSinceEpoch * 31 + category.ordinal()).nextInt(pool.size());
            result.add(pool.get(index));
        }
        return result;
    }

    public static long getSecondsUntilReset() {
        return DailyResetUtils.getSecondsUntilReset();
    }

    public static String formatTimeRemaining(long seconds) {
        return DailyResetUtils.formatTimeRemaining(seconds);
    }
}
