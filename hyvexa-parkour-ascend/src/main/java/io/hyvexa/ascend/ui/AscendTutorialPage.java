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
        FIRST_COMPLETION("Pages/Ascend_Tutorial_FirstCompletion.ui"),
        MAP_UNLOCK("Pages/Ascend_Tutorial_MapUnlock.ui"),
        EVOLUTION("Pages/Ascend_Tutorial_Evolution.ui"),
        ELEVATION("Pages/Ascend_Tutorial_Elevation.ui"),
        SUMMIT("Pages/Ascend_Tutorial_Summit.ui"),
        ASCENSION("Pages/Ascend_Tutorial_Ascension.ui"),
        CHALLENGES("Pages/Ascend_Tutorial_Challenges.ui");

        public final String uiPath;

        Tutorial(String uiPath) {
            this.uiPath = uiPath;
        }

        public AscendOnboardingCopy.TutorialCopy getCopy() {
            return switch (this) {
                case FIRST_COMPLETION -> AscendOnboardingCopy.firstCompletionCopy();
                case MAP_UNLOCK -> AscendOnboardingCopy.mapUnlockCopy();
                case EVOLUTION -> AscendOnboardingCopy.evolutionCopy();
                case ELEVATION -> AscendOnboardingCopy.elevationCopy();
                case SUMMIT -> AscendOnboardingCopy.summitCopy();
                case ASCENSION -> AscendOnboardingCopy.ascensionCopy();
                case CHALLENGES -> AscendOnboardingCopy.challengesCopy();
            };
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
        AscendOnboardingCopy.TutorialCopy copy = tutorial.getCopy();
        this.step = Math.max(0, Math.min(step, copy.stepTitles().length - 1));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        AscendOnboardingCopy.TutorialCopy copy = tutorial.getCopy();
        int totalSteps = copy.stepTitles().length;

        cmd.append(tutorial.uiPath);

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NEXT), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);

        // Set image visibility per step
        for (int i = 0; i < totalSteps; i++) {
            cmd.set("#StepImage" + (i + 1) + ".Visible", i == step);
        }

        // Set progress bar
        float progress = (float) (step + 1) / totalSteps;
        cmd.set("#StepProgress.Value", progress);

        // Set step indicator
        cmd.set("#StepIndicator.Text", "STEP " + (step + 1) + " OF " + totalSteps);

        // Set title and description
        cmd.set("#MainTitle.Text", copy.stepTitles()[step]);
        cmd.set("#Description.Text", copy.stepDescriptions()[step]);

        // Set feature labels and dot colors
        for (int i = 0; i < 3; i++) {
            cmd.set("#Feature" + (i + 1) + "Label.Text", copy.stepFeatures()[step][i]);
            cmd.set("#Feature" + (i + 1) + "Dot.Background", copy.stepFeatureColors()[step][i]);
        }

        // Set button text
        cmd.set("#NextButton.Text", step == totalSteps - 1 ? "Got It!" : "Next");

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

        AscendOnboardingCopy.TutorialCopy copy = tutorial.getCopy();
        int totalSteps = copy.stepTitles().length;

        if (BUTTON_NEXT.equals(data.getButton())) {
            if (step < totalSteps - 1) {
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
