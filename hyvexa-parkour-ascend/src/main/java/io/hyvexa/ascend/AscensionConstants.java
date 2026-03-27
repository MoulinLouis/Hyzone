package io.hyvexa.ascend;

import io.hyvexa.common.math.BigNumber;

import java.util.Set;

public final class AscensionConstants {

    private AscensionConstants() {
    }

    public static final BigNumber ASCENSION_VOLT_THRESHOLD = BigNumber.of(1, 33); // 1 Decillion (10^33)
    public static final BigNumber TRANSCENDENCE_VOLT_THRESHOLD = BigNumber.of(1, 100); // 1 Googol (10^100)

    public enum SkillTreeNode {
        AUTO_RUNNERS("Auto-Upgrade + Momentum", "Auto-upgrade runners & momentum speed boost on manual runs"),
        AUTO_EVOLUTION("Auto-Evolution", "Runners auto-evolve at max speed level", AUTO_RUNNERS),
        RUNNER_SPEED("Runner Speed Boost", "x1.1 global runner speed", AUTO_EVOLUTION),
        EVOLUTION_POWER("Evolution Power+", "+1 base evolution power", AUTO_EVOLUTION),
        RUNNER_SPEED_2("Runner Speed II", "x1.2 global runner speed", RUNNER_SPEED, EVOLUTION_POWER),
        AUTO_SUMMIT("Auto-Summit", "Unlock automatic summit with per-category target levels.", RUNNER_SPEED_2),
        AUTO_ELEVATION("Auto-Elevation", "Unlock automatic elevation with configurable multiplier targets.", RUNNER_SPEED_2),
        ASCENSION_CHALLENGES("Ascension Challenges", "Unlock Ascension Challenges", 1, AUTO_SUMMIT, AUTO_ELEVATION),
        MOMENTUM_SURGE("Momentum Surge", "Momentum boost x2 -> x2.5", 10, ASCENSION_CHALLENGES),
        MOMENTUM_ENDURANCE("Momentum Endurance", "Momentum 60s -> 90s", 10, ASCENSION_CHALLENGES),
        MULTIPLIER_BOOST("Multiplier Boost", "+0.10 base multiplier gain", 25, MOMENTUM_SURGE, MOMENTUM_ENDURANCE),
        RUNNER_SPEED_3("Runner Speed III", "x1.3 global runner speed", 50, MULTIPLIER_BOOST),
        EVOLUTION_POWER_2("Evolution Power II", "+1 base evolution power", 50, MULTIPLIER_BOOST),
        RUNNER_SPEED_4("Runner Speed IV", "x1.5 global runner speed", 100, RUNNER_SPEED_3, EVOLUTION_POWER_2),
        EVOLUTION_POWER_3("Evolution Power III", "+2 base evolution power", 100, RUNNER_SPEED_3, EVOLUTION_POWER_2),
        MOMENTUM_MASTERY("Momentum Mastery", "Momentum x3.0 + 120s duration", 200, RUNNER_SPEED_4, EVOLUTION_POWER_3),
        MULTIPLIER_BOOST_2("Multiplier Boost II", "+0.25 base multiplier gain", 400, MOMENTUM_MASTERY),
        AUTO_ASCEND("Auto Ascend", "Automatically ascend at 1Dc", 400, MOMENTUM_MASTERY),
        RUNNER_SPEED_5("Runner Speed V", "x2.0 global runner speed", 1000, MULTIPLIER_BOOST_2, AUTO_ASCEND);

        private final String name;
        private final String description;
        private final int cost;
        private final SkillTreeNode[] prerequisites;

        SkillTreeNode(String name, String description, SkillTreeNode... prerequisites) {
            this(name, description, 1, prerequisites);
        }

        SkillTreeNode(String name, String description, int cost, SkillTreeNode... prerequisites) {
            this.name = name;
            this.description = description;
            this.cost = cost;
            this.prerequisites = prerequisites;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public int getCost() {
            return cost;
        }

        public SkillTreeNode[] getPrerequisites() {
            return prerequisites;
        }

        /**
         * Checks if prerequisites are satisfied (OR logic: at least one prerequisite must be unlocked).
         * Nodes with no prerequisites are always satisfiable.
         */
        public boolean hasPrerequisitesSatisfied(java.util.Set<SkillTreeNode> unlocked) {
            if (prerequisites.length == 0) {
                return true;
            }
            for (SkillTreeNode prereq : prerequisites) {
                if (unlocked.contains(prereq)) {
                    return true;
                }
            }
            return false;
        }
    }

    public enum ChallengeType {
        CHALLENGE_1(1, "Challenge 1",
            "Complete an Ascension without map 5",
            "#10b981",
            Set.of(), Set.of(4), 1.0, 1.0, 1.0),
        CHALLENGE_2(2, "Challenge 2",
            "Complete an Ascension with Runner Speed /3",
            "#f59e0b",
            Set.of(), Set.of(), 3.0, 1.0, 1.0),
        CHALLENGE_3(3, "Challenge 3",
            "Complete an Ascension with Multiplier Gain /4",
            "#3b82f6",
            Set.of(), Set.of(), 1.0, 4.0, 1.0),
        CHALLENGE_4(4, "Challenge 4",
            "Complete an Ascension with Evolution Power /5",
            "#ef4444",
            Set.of(), Set.of(), 1.0, 1.0, 5.0),
        CHALLENGE_5(5, "Challenge 5",
            "Complete an Ascension with Runner Speed /2 and Multiplier Gain /2",
            "#8b5cf6",
            Set.of(), Set.of(), 2.0, 2.0, 1.0),
        CHALLENGE_6(6, "Challenge 6",
            "Complete an Ascension with Runner Speed /4 and Multiplier Gain /4",
            "#ec4899",
            Set.of(), Set.of(), 4.0, 4.0, 1.0),
        CHALLENGE_7(7, "Challenge 7",
            "Complete an Ascension with all Summit bonuses /2",
            "#f59e0b",
            Set.of(), Set.of(3, 4), 2.0, 2.0, 2.0),
        CHALLENGE_8(8, "Challenge 8",
            "Complete an Ascension without Elevation or Summit",
            "#06b6d4",
            Set.of(), Set.of(), 1.0, 1.0, 1.0, true, true);

        private final int id;
        private final String displayName;
        private final String description;
        private final String accentColor;
        private final Set<SummitConstants.SummitCategory> blockedSummitCategories;
        private final Set<Integer> blockedMapDisplayOrders;
        private final double speedDivisor;
        private final double multiplierGainDivisor;
        private final double evolutionPowerDivisor;
        private final boolean blocksElevation;
        private final boolean blocksAllSummit;

        ChallengeType(int id, String displayName, String description, String accentColor,
                      Set<SummitConstants.SummitCategory> blockedSummitCategories,
                      Set<Integer> blockedMapDisplayOrders, double speedDivisor,
                      double multiplierGainDivisor, double evolutionPowerDivisor) {
            this(id, displayName, description, accentColor, blockedSummitCategories,
                 blockedMapDisplayOrders, speedDivisor, multiplierGainDivisor,
                 evolutionPowerDivisor, false, false);
        }

        ChallengeType(int id, String displayName, String description, String accentColor,
                      Set<SummitConstants.SummitCategory> blockedSummitCategories,
                      Set<Integer> blockedMapDisplayOrders, double speedDivisor,
                      double multiplierGainDivisor, double evolutionPowerDivisor,
                      boolean blocksElevation, boolean blocksAllSummit) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.accentColor = accentColor;
            this.blockedSummitCategories = blockedSummitCategories;
            this.blockedMapDisplayOrders = blockedMapDisplayOrders;
            this.speedDivisor = speedDivisor;
            this.multiplierGainDivisor = multiplierGainDivisor;
            this.evolutionPowerDivisor = evolutionPowerDivisor;
            this.blocksElevation = blocksElevation;
            this.blocksAllSummit = blocksAllSummit;
        }

        public int getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public String getAccentColor() { return accentColor; }
        public Set<SummitConstants.SummitCategory> getBlockedSummitCategories() { return blockedSummitCategories; }
        public Set<Integer> getBlockedMapDisplayOrders() { return blockedMapDisplayOrders; }
        public double getSpeedDivisor() { return speedDivisor; }
        public double getMultiplierGainDivisor() { return multiplierGainDivisor; }
        public double getEvolutionPowerDivisor() { return evolutionPowerDivisor; }
        public boolean blocksElevation() { return blocksElevation; }
        public boolean blocksAllSummit() { return blocksAllSummit; }

        public static ChallengeType fromId(int id) {
            for (ChallengeType type : values()) {
                if (type.id == id) return type;
            }
            return null;
        }
    }

    // ========================================
    // Achievement System
    // ========================================

    public enum AchievementCategory {
        MILESTONES("Milestones"),
        RUNNERS("Runners"),
        PRESTIGE("Prestige"),
        SKILLS("Skills"),
        CHALLENGES("Challenges"),
        EASTER_EGGS("Easter Eggs"),
        SECRET("Secret");

        private final String displayName;

        AchievementCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum AchievementType {
        // Milestones - Manual Runs
        FIRST_STEPS("First Steps", "Complete your first manual run", AchievementCategory.MILESTONES),
        WARMING_UP("Warming Up", "Complete 10 manual runs", AchievementCategory.MILESTONES),
        DEDICATED("Dedicated", "Complete 100 manual runs", AchievementCategory.MILESTONES),
        HALFWAY_THERE("Halfway There", "Complete 500 manual runs", AchievementCategory.MILESTONES),
        MARATHON("Marathon", "Complete 1000 manual runs", AchievementCategory.MILESTONES),
        UNSTOPPABLE("Unstoppable", "Complete 5000 manual runs", AchievementCategory.MILESTONES),
        LIVING_LEGEND("Living Legend", "Complete 10000 manual runs", AchievementCategory.MILESTONES),

        // Runners - Automation
        FIRST_ROBOT("First Robot", "Buy your first runner", AchievementCategory.RUNNERS),
        ARMY("Army", "Have 5+ active runners", AchievementCategory.RUNNERS),
        EVOLVED("Evolved", "Evolve a runner to 1+ stars", AchievementCategory.RUNNERS),
        STAR_COLLECTOR("Star Collector", "Evolve a runner to max stars", AchievementCategory.RUNNERS),

        // Prestige - Progression
        FIRST_ELEVATION("First Elevation", "Complete your first Elevation", AchievementCategory.PRESTIGE),
        GOING_UP("Going Up", "Reach x100 multiplier", AchievementCategory.PRESTIGE),
        SKY_HIGH("Sky High", "Reach x5,000 multiplier", AchievementCategory.PRESTIGE),
        STRATOSPHERE("Stratosphere", "Reach x20,000 multiplier", AchievementCategory.PRESTIGE),
        SUMMIT_SEEKER("Summit Seeker", "Complete your first Summit", AchievementCategory.PRESTIGE),
        PEAK_PERFORMER("Peak Performer", "Reach summit level 10", AchievementCategory.PRESTIGE),
        MOUNTAINEER("Mountaineer", "Reach summit level 100", AchievementCategory.PRESTIGE),
        SUMMIT_LEGEND("Summit Legend", "Reach summit level 1,000", AchievementCategory.PRESTIGE),
        ASCENDED("Ascended", "Complete your first Ascension", AchievementCategory.PRESTIGE),
        VETERAN("Veteran", "Complete 5 ascensions", AchievementCategory.PRESTIGE),
        TRANSCENDENT("Transcendent", "Complete 10 ascensions", AchievementCategory.PRESTIGE),

        // Skills - Skill Tree
        NEW_POWERS("New Powers", "Unlock your first skill", AchievementCategory.SKILLS),

        // Challenges
        CHALLENGER("Challenger", "Complete your first challenge", AchievementCategory.CHALLENGES),
        CHALLENGE_MASTER("Challenge Master", "Complete all challenges", AchievementCategory.CHALLENGES),

        // Easter Eggs
        CAT_COLLECTOR("Cat Collector", "Find all 5 hidden cats", AchievementCategory.EASTER_EGGS, true),

        // Secret - Hidden
        CHAIN_RUNNER("Chain Runner", "Complete 25 consecutive runs", AchievementCategory.SECRET, true),
        ALL_STARS("All Stars", "Max-star runners on all maps", AchievementCategory.SECRET, true),
        COMPLETIONIST("Completionist", "Unlock all other achievements", AchievementCategory.SECRET, true);

        private final String name;
        private final String description;
        private final AchievementCategory category;
        private final boolean hidden;

        AchievementType(String name, String description, AchievementCategory category) {
            this(name, description, category, false);
        }

        AchievementType(String name, String description, AchievementCategory category, boolean hidden) {
            this.name = name;
            this.description = description;
            this.category = category;
            this.hidden = hidden;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public AchievementCategory getCategory() {
            return category;
        }

        public boolean isHidden() {
            return hidden;
        }
    }

    // Achievement thresholds
    public static final int ACHIEVEMENT_MANUAL_RUNS_10 = 10;
    public static final int ACHIEVEMENT_MANUAL_RUNS_100 = 100;
    public static final int ACHIEVEMENT_MANUAL_RUNS_500 = 500;
    public static final int ACHIEVEMENT_MANUAL_RUNS_1000 = 1000;
    public static final int ACHIEVEMENT_MANUAL_RUNS_5000 = 5000;
    public static final int ACHIEVEMENT_MANUAL_RUNS_10000 = 10000;
    public static final int ACHIEVEMENT_RUNNER_COUNT = 5;
    public static final int ACHIEVEMENT_ELEVATION_100 = 100;
    public static final int ACHIEVEMENT_ELEVATION_5000 = 5000;
    public static final int ACHIEVEMENT_ELEVATION_20000 = 20000;
    public static final int ACHIEVEMENT_SUMMIT_LEVEL_10 = 10;
    public static final int ACHIEVEMENT_SUMMIT_LEVEL_100 = 100;
    public static final int ACHIEVEMENT_SUMMIT_LEVEL_1000 = 1000;
    public static final int ACHIEVEMENT_ASCENSION_5 = 5;
    public static final int ACHIEVEMENT_ASCENSION_10 = 10;
    public static final int ACHIEVEMENT_CONSECUTIVE_RUNS_25 = 25;
    public static final int ACHIEVEMENT_CATS_REQUIRED = 5;
}
