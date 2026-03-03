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
            new MissionDefinition("kill_easy", MissionCategory.KILL, "Kill 150 zombies", 150, 100),
            new MissionDefinition("kill_medium", MissionCategory.KILL, "Kill 300 zombies", 300, 200),
            new MissionDefinition("kill_hard", MissionCategory.KILL, "Kill 500 zombies", 500, 300),
            // Combo missions
            new MissionDefinition("combo_easy", MissionCategory.COMBO, "Get a 5x combo", 5, 100),
            new MissionDefinition("combo_medium", MissionCategory.COMBO, "Get a 10x combo", 10, 200),
            new MissionDefinition("combo_hard", MissionCategory.COMBO, "Get a 15x combo", 15, 300)
    );

    public static List<MissionDefinition> getPool() {
        return POOL;
    }

    public static List<MissionDefinition> getByCategory(MissionCategory category) {
        return POOL.stream().filter(m -> m.category == category).toList();
    }
}
