package io.hyvexa.ascend.ui;

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

import javax.annotation.Nonnull;
import java.util.Map;

public class AscendHelpPage extends BaseAscendPage {

    private static final String BUTTON_WELCOME = "Welcome";
    private static final String BUTTON_FIRST_COMPLETION = "FirstCompletion";
    private static final String BUTTON_MAP_UNLOCK = "MapUnlock";
    private static final String BUTTON_EVOLUTION = "Evolution";
    private static final String BUTTON_ELEVATION = "Elevation";
    private static final String BUTTON_SUMMIT = "Summit";
    private static final String BUTTON_ASCENSION = "Ascension";
    private static final String BUTTON_CHALLENGES = "Challenges";
    private static final String BUTTON_CLOSE = "Close";

    private static final Map<String, AscendTutorialPage.Tutorial> BUTTON_TO_TUTORIAL = Map.of(
        BUTTON_FIRST_COMPLETION, AscendTutorialPage.Tutorial.FIRST_COMPLETION,
        BUTTON_MAP_UNLOCK, AscendTutorialPage.Tutorial.MAP_UNLOCK,
        BUTTON_EVOLUTION, AscendTutorialPage.Tutorial.EVOLUTION,
        BUTTON_ELEVATION, AscendTutorialPage.Tutorial.ELEVATION,
        BUTTON_SUMMIT, AscendTutorialPage.Tutorial.SUMMIT,
        BUTTON_ASCENSION, AscendTutorialPage.Tutorial.ASCENSION,
        BUTTON_CHALLENGES, AscendTutorialPage.Tutorial.CHALLENGES
    );

    public AscendHelpPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Ascend_Help.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#WelcomeButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_WELCOME), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FirstCompletionButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_FIRST_COMPLETION), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MapUnlockButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_MAP_UNLOCK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#EvolutionButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_EVOLUTION), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ElevationButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ELEVATION), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SummitButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SUMMIT), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AscensionButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ASCENSION), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ChallengesButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CHALLENGES), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        // Set subtitle labels from centralized copy (prevents drift)
        uiCommandBuilder.set("#WelcomeSubtitle.Text", AscendOnboardingCopy.HELP_SUBTITLE_WELCOME);
        uiCommandBuilder.set("#FirstCompletionSubtitle.Text", AscendOnboardingCopy.HELP_SUBTITLE_FIRST_COMPLETION);
        uiCommandBuilder.set("#MapUnlockSubtitle.Text", AscendOnboardingCopy.HELP_SUBTITLE_MAP_UNLOCK);
        uiCommandBuilder.set("#EvolutionSubtitle.Text", AscendOnboardingCopy.HELP_SUBTITLE_EVOLUTION);
        uiCommandBuilder.set("#ElevationSubtitle.Text", AscendOnboardingCopy.HELP_SUBTITLE_ELEVATION);
        uiCommandBuilder.set("#SummitSubtitle.Text", AscendOnboardingCopy.HELP_SUBTITLE_SUMMIT);
        uiCommandBuilder.set("#AscensionSubtitle.Text", AscendOnboardingCopy.HELP_SUBTITLE_ASCENSION);
        uiCommandBuilder.set("#ChallengesSubtitle.Text", AscendOnboardingCopy.HELP_SUBTITLE_CHALLENGES);
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

        String button = data.getButton();
        if (BUTTON_WELCOME.equals(button)) {
            player.getPageManager().openCustomPage(ref, store, new AscendWelcomePage(playerRef));
        } else if (BUTTON_CLOSE.equals(button)) {
            this.close();
        } else {
            AscendTutorialPage.Tutorial tutorial = BUTTON_TO_TUTORIAL.get(button);
            if (tutorial != null) {
                player.getPageManager().openCustomPage(ref, store, new AscendTutorialPage(playerRef, tutorial));
            }
        }
    }
}
