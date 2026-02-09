package io.hyvexa.ascend.ui;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.hyvexa.common.ui.ButtonEventData;

public class AscendTutorialPage extends BaseAscendPage {

    private static final String BUTTON_NEXT = "Next";
    private static final String BUTTON_BACK = "Back";

    public enum Tutorial {
        FIRST_COMPLETION(
            "Pages/Ascend_Tutorial_FirstCompletion.ui",
            new String[]{"Nice Run!", "Automate It"},
            new String[]{
                "You earned vexa and your map multiplier went up! Manual runs give 2x the runner's multiplier gain.",
                "Open /ascend and click Buy Runner. It replays the map automatically, earning multiplier while you're away."
            },
            new String[][]{
                {"Vexa earned on every manual completion", "Multiplier gain = 2x the runner's gain", "Higher multiplier means bigger rewards"},
                {"Runners replay maps automatically for you", "They earn multiplier even while you're offline", "Buy runners from the map select menu"}
            },
            new String[][]{
                {"#10b981", "#3b82f6", "#a855f7"},
                {"#f59e0b", "#10b981", "#3b82f6"}
            }
        ),
        MAP_UNLOCK(
            "Pages/Ascend_Tutorial_MapUnlock.ui",
            new String[]{"New Map Unlocked!"},
            new String[]{
                "Runner level 5 unlocks the next map. All map multipliers are multiplied together - more maps = way more vexa."
            },
            new String[][]{
                {"Runner level 5 unlocks new maps", "Map multipliers multiply together", "More maps means exponential vexa growth"}
            },
            new String[][]{
                {"#10b981", "#3b82f6", "#a855f7"}
            }
        ),
        EVOLUTION(
            "Pages/Ascend_Tutorial_Evolution.ui",
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
        ),
        ELEVATION(
            "Pages/Ascend_Tutorial_Elevation.ui",
            new String[]{"Elevation", "Elevate Often"},
            new String[]{
                "Spend your vexa to gain elevation levels. Your level is your multiplier: level 10 = x10, level 100 = x100. Open with /ascend elevate.",
                "Elevation resets vexa, runners, multipliers, and map unlocks. You keep your best times and your new elevation level. Elevate often to grow faster."
            },
            new String[][]{
                {"Spend vexa to gain elevation levels", "Your level equals your global multiplier", "Level 10 = x10, level 100 = x100"},
                {"Resets vexa, runners, and multipliers", "Keeps best times and elevation level", "Elevate often to grow faster"}
            },
            new String[][]{
                {"#10b981", "#f59e0b", "#3b82f6"},
                {"#a855f7", "#10b981", "#f59e0b"}
            }
        ),
        SUMMIT(
            "Pages/Ascend_Tutorial_Summit.ui",
            new String[]{"Summit", "The Reset"},
            new String[]{
                "Convert vexa into permanent upgrades: Runner Speed, Multiplier Gain, and Evolution Power. These stay forever. Open with /ascend summit.",
                "Summit resets vexa, elevation, runners, and maps. You keep your best times and Summit upgrades. Each cycle you'll progress faster."
            },
            new String[][]{
                {"Permanent Runner Speed upgrades", "Permanent Multiplier Gain boosts", "Permanent Evolution Power bonuses"},
                {"Resets vexa, elevation, and runners", "Keeps best times and Summit upgrades", "Each cycle you progress faster"}
            },
            new String[][]{
                {"#ef4444", "#f59e0b", "#a855f7"},
                {"#3b82f6", "#10b981", "#ef4444"}
            }
        ),
        ASCENSION(
            "Pages/Ascend_Tutorial_Ascension.ui",
            new String[]{"Ascension", "Skill Tree"},
            new String[]{
                "The ultimate prestige. Resets everything including Summit - but grants a Skill Tree point for powerful permanent abilities. Open with /ascend ascension.",
                "8 skill nodes to unlock: Auto-Runners, Auto-Evolution, Persistence, Runner Speed, Offline Boost, Summit Memory, Evolution Power, and more. Skill points are permanent across all future Ascensions."
            },
            new String[][]{
                {"Resets everything including Summit", "Grants a Skill Tree point each time", "Unlocks powerful permanent abilities"},
                {"8 skill nodes to unlock", "Auto-Runners, Speed, Persistence, and more", "Skill points persist across Ascensions"}
            },
            new String[][]{
                {"#ef4444", "#a855f7", "#f59e0b"},
                {"#3b82f6", "#10b981", "#a855f7"}
            }
        );

        public final String uiPath;
        public final String[] stepTitles;
        public final String[] stepDescriptions;
        public final String[][] stepFeatures;
        public final String[][] stepFeatureColors;
        public final int totalSteps;

        Tutorial(String uiPath, String[] stepTitles, String[] stepDescriptions,
                 String[][] stepFeatures, String[][] stepFeatureColors) {
            this.uiPath = uiPath;
            this.stepTitles = stepTitles;
            this.stepDescriptions = stepDescriptions;
            this.stepFeatures = stepFeatures;
            this.stepFeatureColors = stepFeatureColors;
            this.totalSteps = stepTitles.length;
        }
    }

    private final Tutorial tutorial;
    private final int step;

    public AscendTutorialPage(@Nonnull PlayerRef playerRef, @Nonnull Tutorial tutorial) {
        this(playerRef, tutorial, 0);
    }

    public AscendTutorialPage(@Nonnull PlayerRef playerRef, @Nonnull Tutorial tutorial, int step) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.tutorial = tutorial;
        this.step = Math.max(0, Math.min(step, tutorial.totalSteps - 1));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        cmd.append(tutorial.uiPath);

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NEXT), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);

        // Set image visibility per step
        for (int i = 0; i < tutorial.totalSteps; i++) {
            cmd.set("#StepImage" + (i + 1) + ".Visible", i == step);
        }

        // Set progress bar
        float progress = (float) (step + 1) / tutorial.totalSteps;
        cmd.set("#StepProgress.Value", progress);

        // Set step indicator
        cmd.set("#StepIndicator.Text", "STEP " + (step + 1) + " OF " + tutorial.totalSteps);

        // Set title and description
        cmd.set("#MainTitle.Text", tutorial.stepTitles[step]);
        cmd.set("#Description.Text", tutorial.stepDescriptions[step]);

        // Set feature labels and dot colors
        for (int i = 0; i < 3; i++) {
            cmd.set("#Feature" + (i + 1) + "Label.Text", tutorial.stepFeatures[step][i]);
            cmd.set("#Feature" + (i + 1) + "Dot.Background", tutorial.stepFeatureColors[step][i]);
        }

        // Set button text
        cmd.set("#NextButton.Text", step == tutorial.totalSteps - 1 ? "Got It!" : "Next");

        // Tip boxes
        if (tutorial == Tutorial.ELEVATION) {
            cmd.set("#TipBox.Visible", step == 0);
            cmd.set("#TipText.Text", "Don't elevate right away! Let your runners build up multipliers first - you'll earn much more when you do.");
        } else if (tutorial == Tutorial.FIRST_COMPLETION) {
            cmd.set("#TipBox.Visible", step == 1);
            cmd.set("#TipText.Text", "Use the item in slot 1 to open /ascend anytime!");
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }

        if (BUTTON_NEXT.equals(data.getButton())) {
            if (step < tutorial.totalSteps - 1) {
                player.getPageManager().openCustomPage(ref, store,
                        new AscendTutorialPage(playerRef, tutorial, step + 1));
            } else {
                this.close();
            }
        } else if (BUTTON_BACK.equals(data.getButton())) {
            if (step > 0) {
                player.getPageManager().openCustomPage(ref, store,
                        new AscendTutorialPage(playerRef, tutorial, step - 1));
            } else {
                player.getPageManager().openCustomPage(ref, store,
                        new AscendHelpPage(playerRef));
            }
        }
    }
}
