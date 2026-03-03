package io.hyvexa.purge.mission;

import java.util.List;

public record MissionDefinition(String id, MissionCategory category, String description, int target, int scrapReward) {

    public enum MissionCategory {
        WAVE, KILL, COMBO
    }

    private static final List<MissionDefinition> POOL = List.of(
            // Wave missions
            new MissionDefinition("wave_easy", MissionCategory.WAVE, "Reach wave 5", 5, 15),
            new MissionDefinition("wave_medium", MissionCategory.WAVE, "Reach wave 10", 10, 30),
            new MissionDefinition("wave_hard", MissionCategory.WAVE, "Reach wave 15", 15, 60),
            // Kill missions
            new MissionDefinition("kill_easy", MissionCategory.KILL, "Kill 25 zombies", 25, 15),
            new MissionDefinition("kill_medium", MissionCategory.KILL, "Kill 50 zombies", 50, 30),
            new MissionDefinition("kill_hard", MissionCategory.KILL, "Kill 100 zombies", 100, 60),
            // Combo missions
            new MissionDefinition("combo_easy", MissionCategory.COMBO, "Get a 3x combo", 3, 15),
            new MissionDefinition("combo_medium", MissionCategory.COMBO, "Get a 5x combo", 5, 30),
            new MissionDefinition("combo_hard", MissionCategory.COMBO, "Get a 7x combo", 7, 60)
    );

    public static List<MissionDefinition> getPool() {
        return POOL;
    }

    public static List<MissionDefinition> getByCategory(MissionCategory category) {
        return POOL.stream().filter(m -> m.category == category).toList();
    }
}
