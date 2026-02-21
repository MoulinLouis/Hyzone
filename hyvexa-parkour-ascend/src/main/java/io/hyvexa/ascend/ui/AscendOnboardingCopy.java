package io.hyvexa.ascend.ui;

import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.common.util.FormatUtils;

/**
 * Single source of truth for all onboarding/tutorial text in Ascend mode.
 * Keeps copy centralized so UI, tutorials, and help pages stay in sync.
 */
public final class AscendOnboardingCopy {

    private AscendOnboardingCopy() {}

    // --- Dynamic values ---

    public static int nodeCount() {
        return AscendConstants.SkillTreeNode.values().length;
    }

    public static String ascensionThresholdDisplay() {
        return FormatUtils.formatBigNumber(AscendConstants.ASCENSION_VOLT_THRESHOLD);
    }

    // --- Tutorial copy record ---

    public record TutorialCopy(
        String[] stepTitles,
        String[] stepDescriptions,
        String[][] stepFeatures,
        String[][] stepFeatureColors
    ) {}

    // --- First Completion tutorial ---

    public static TutorialCopy firstCompletionCopy() {
        return new TutorialCopy(
            new String[]{"Nice Run!", "Automate It"},
            new String[]{
                "You earned volt and your map multiplier went up! Manual runs give 5x the runner's multiplier gain.",
                "Open /ascend and click Buy Runner. It replays the map automatically, earning multiplier while you're away."
            },
            new String[][]{
                {"Volt earned on every manual completion", "Multiplier gain = 5x the runner's gain", "Higher multiplier means bigger rewards"},
                {"Runners replay maps automatically for you", "They earn multiplier even while you're offline", "Buy runners from the map select menu"}
            },
            new String[][]{
                {"#10b981", "#3b82f6", "#a855f7"},
                {"#f59e0b", "#10b981", "#3b82f6"}
            }
        );
    }

    // --- Map Unlock tutorial ---

    public static TutorialCopy mapUnlockCopy() {
        return new TutorialCopy(
            new String[]{"New Map Unlocked!"},
            new String[]{
                "Runner level 5 unlocks the next map. All map multipliers are multiplied together - more maps = way more volt."
            },
            new String[][]{
                {"Runner level 5 unlocks new maps", "Map multipliers multiply together", "More maps means exponential volt growth"}
            },
            new String[][]{
                {"#10b981", "#3b82f6", "#a855f7"}
            }
        );
    }

    // --- Evolution tutorial ---

    public static TutorialCopy evolutionCopy() {
        return new TutorialCopy(
            new String[]{"Evolution", "Star Power"},
            new String[]{
                "Your runner hit max speed. Evolve it to earn a star - each star triples the multiplier it earns per lap. Speed resets, but the gains are massive.",
                "0 -> +0.10 | 1 -> +0.30 | 2 -> +0.90 | 3 -> +2.70 | 4 -> +8.10 | 5 -> +24.3 per lap. Always evolve when you can."
            },
            new String[][]{
                {"Evolve runners when they hit max speed", "Each star triples multiplier per lap", "Speed resets but gains are massive"},
                {"Stars multiply earnings exponentially", "5-star runners earn 243x base rate", "Always evolve as soon as possible"}
            },
            new String[][]{
                {"#a855f7", "#10b981", "#3b82f6"},
                {"#f59e0b", "#a855f7", "#10b981"}
            }
        );
    }

    // --- Elevation tutorial ---

    public static TutorialCopy elevationCopy() {
        return new TutorialCopy(
            new String[]{"Elevation", "Elevate Often"},
            new String[]{
                "Spend your volt to gain elevation levels. Higher levels give bigger multipliers: level 10 = x11, level 100 = x126. Open with /ascend elevate.",
                "Elevation resets volt, runners, multipliers, and map unlocks. You keep your best times and your new elevation level. Elevate often to grow faster."
            },
            new String[][]{
                {"Spend volt to gain elevation levels", "Higher levels give bigger multipliers", "Level 10 = x11, level 100 = x126"},
                {"Resets volt, runners, and multipliers", "Keeps best times and elevation level", "Elevate often to grow faster"}
            },
            new String[][]{
                {"#10b981", "#f59e0b", "#3b82f6"},
                {"#a855f7", "#10b981", "#f59e0b"}
            }
        );
    }

    // --- Summit tutorial ---

    public static TutorialCopy summitCopy() {
        return new TutorialCopy(
            new String[]{"Summit", "The Reset"},
            new String[]{
                "Convert volt into permanent upgrades: Runner Speed, Multiplier Gain, and Evolution Power. These stay forever. Open with /ascend summit.",
                "Summit resets volt, elevation, runners, and maps. You keep your best times and Summit upgrades. Each cycle you'll progress faster."
            },
            new String[][]{
                {"Permanent Runner Speed upgrades", "Permanent Multiplier Gain boosts", "Permanent Evolution Power bonuses"},
                {"Resets volt, elevation, and runners", "Keeps best times and Summit upgrades", "Each cycle you progress faster"}
            },
            new String[][]{
                {"#ef4444", "#f59e0b", "#a855f7"},
                {"#3b82f6", "#10b981", "#ef4444"}
            }
        );
    }

    // --- Ascension tutorial (dynamic node count) ---

    public static TutorialCopy ascensionCopy() {
        int count = nodeCount();
        return new TutorialCopy(
            new String[]{"Ascension", "Ascendancy Tree"},
            new String[]{
                "The ultimate prestige. Resets everything including Summit - but grants an AP for powerful permanent abilities. Open with /ascend ascension.",
                count + " ascendancy nodes to unlock: Auto-Upgrade, Auto-Evolution, Runner Speed, Evolution Power, Momentum Surge, Elevation Remnant, and more. AP are permanent across all future Ascensions."
            },
            new String[][]{
                {"Resets everything including Summit", "Grants 1 AP each time", "Unlocks powerful permanent abilities"},
                {count + " ascendancy nodes to unlock", "Auto-Upgrade, Evolution, Speed, and more", "AP persist across Ascensions"}
            },
            new String[][]{
                {"#ef4444", "#a855f7", "#f59e0b"},
                {"#3b82f6", "#10b981", "#a855f7"}
            }
        );
    }

    // --- Challenges tutorial ---

    public static TutorialCopy challengesCopy() {
        return new TutorialCopy(
            new String[]{"Ascension Challenges", "How It Works"},
            new String[]{
                "Test your skills with timed challenge runs. There are 7 progressive challenges, each with a handicap, and each completion permanently increases your AP multiplier.",
                "Starting a challenge snapshots your progress and resets you. Reach " + ascensionThresholdDisplay() + " volt to complete it. Every completed challenge adds +1 AP multiplier, so each future Ascension grants more AP."
            },
            new String[][]{
                {"7 progressive challenges to complete", "Each applies a unique handicap", "Each completion increases AP multiplier"},
                {"Progress is snapshot and restored", "Quit anytime without losing progress", "Higher AP multiplier = more AP per Ascension"}
            },
            new String[][]{
                {"#10b981", "#f59e0b", "#a855f7"},
                {"#3b82f6", "#10b981", "#ef4444"}
            }
        );
    }

    // --- Welcome page arrays ---

    public static String[] welcomeTitles() {
        return new String[]{
            "Welcome to Ascend",
            "Your Shortcuts",
            "Play Your First Map"
        };
    }

    public static String[] welcomeDescriptions() {
        return new String[]{
            "Ascend is a parkour idle game. Run maps, earn volt, and build up an army of automated runners that play for you - even while you're offline.",
            "You have 5 items in your inventory that open menus instantly - no need to type commands. Here's what each one does:",
            "Open the map menu and pick a map. Complete it to earn your first volt and unlock new features along the way!"
        };
    }

    public static String[][] welcomeFeatures() {
        return new String[][]{
            {
                "Run parkour maps to earn volt",
                "Buy runners that replay maps for you",
                "Progress and unlock new content over time"
            },
            {
                "/ascend - Map menu, runners, and upgrades",
                "/ascend leaderboard - Rankings and stats",
                "/ascend automation - Runner speed controls"
            },
            {
                "Use the first item in your inventory",
                "Pick any map and complete the parkour",
                "Your first completion unlocks runners"
            }
        };
    }

    public static String[][] welcomeFeatureColors() {
        return new String[][]{
            {"#10b981", "#3b82f6", "#f59e0b"},
            {"#f59e0b", "#3b82f6", "#10b981"},
            {"#f59e0b", "#10b981", "#a855f7"}
        };
    }

    // Welcome step 2 extra features (4th and 5th)
    public static final String WELCOME_FEATURE4_TEXT = "/ascend help - Tutorials and guides";
    public static final String WELCOME_FEATURE4_COLOR = "#a855f7";
    public static final String WELCOME_FEATURE5_TEXT = "/ascend profile - Your stats and progress";
    public static final String WELCOME_FEATURE5_COLOR = "#ef4444";

    // --- Help page subtitles ---

    public static final String HELP_SUBTITLE_WELCOME = "Learn the basics of Ascend mode";
    public static final String HELP_SUBTITLE_FIRST_COMPLETION = "Volt, multipliers, and buying runners";
    public static final String HELP_SUBTITLE_MAP_UNLOCK = "How new maps unlock and stack";
    public static final String HELP_SUBTITLE_EVOLUTION = "Evolve runners for star multipliers";
    public static final String HELP_SUBTITLE_ELEVATION = "Spend volt for a global multiplier";
    public static final String HELP_SUBTITLE_SUMMIT = "Reset for permanent upgrades";
    public static final String HELP_SUBTITLE_ASCENSION = "Ultimate prestige and Ascendancy Tree";
    public static final String HELP_SUBTITLE_CHALLENGES = "Timed handicap runs for permanent rewards";

    // --- Ascension explainer (pre-reset modal) ---

    public static final String EXPLAINER_TITLE = "Ascending";
    public static final String EXPLAINER_DESCRIPTION =
        "You've reached " + ascensionThresholdDisplay() + " volt - Ascension is triggered automatically. "
        + "All progress (volt, elevation, summit, runners) will be reset, but you'll earn 1 AP "
        + "to spend on permanent Ascendancy Tree abilities. Your best times are always kept.";
    public static final String EXPLAINER_BUTTON = "Continue";
}
