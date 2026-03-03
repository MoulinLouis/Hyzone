package io.hyvexa.purge.mission;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return java.time.Duration.between(now, midnight).getSeconds();
    }

    public static String formatTimeRemaining(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }
}
