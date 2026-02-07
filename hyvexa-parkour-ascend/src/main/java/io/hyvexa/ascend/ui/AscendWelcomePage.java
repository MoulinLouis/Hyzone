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

public class AscendWelcomePage extends BaseAscendPage {

    private static final String BUTTON_NEXT = "Next";
    private static final String BUTTON_BACK = "Back";

    private static final int TOTAL_STEPS = 3;

    private static final String[] STEP_IMAGES = {
        "../Textures/help/welcome_step1.png",
        "../Textures/help/welcome_step2.png",
        "../Textures/help/welcome_step3.png"
    };

    private static final String[] STEP_TITLES = {
        "Welcome to Ascend",
        "Coins & Elevation",
        "Prestige & Beyond"
    };

    private static final String[] STEP_DESCRIPTIONS = {
        "Ascend is an idle parkour mode where you buy runners that complete maps for you. Each runner earns coins based on their speed and the map difficulty.",
        "Coins are your main currency. Spend them to buy new runners, upgrade their speed, or invest in Elevation which boosts ALL your earnings with a global multiplier.",
        "Once you've grown powerful enough, you can Summit to reset your progress in exchange for permanent bonuses. Unlock the Skill Tree and climb the leaderboards!"
    };

    private static final String[][] STEP_FEATURES = {
        {
            "Buy runners to automatically complete maps",
            "Upgrade runner speed for faster coin earnings",
            "Evolve runners to unlock multiplier bonuses"
        },
        {
            "Coins accumulate even while you're offline",
            "Elevation multiplies all your coin income",
            "Stack multipliers for exponential growth"
        },
        {
            "Summit resets progress for permanent power",
            "Spend ascension points in the Skill Tree",
            "Compete for the highest rank on leaderboards"
        }
    };

    private static final String[][] STEP_FEATURE_COLORS = {
        {"#10b981", "#3b82f6", "#a855f7"},
        {"#f59e0b", "#10b981", "#3b82f6"},
        {"#ef4444", "#a855f7", "#f59e0b"}
    };

    private final int step;

    public AscendWelcomePage(@Nonnull PlayerRef playerRef) {
        this(playerRef, 0);
    }

    public AscendWelcomePage(@Nonnull PlayerRef playerRef, int step) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.step = Math.max(0, Math.min(step, TOTAL_STEPS - 1));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Ascend_Welcome.ui");

        // Bind buttons
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NEXT), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);

        // Set image visibility per step
        for (int i = 0; i < TOTAL_STEPS; i++) {
            cmd.set("#StepImage" + (i + 1) + ".Visible", i == step);
        }

        // Set progress bar
        float progress = (float) (step + 1) / TOTAL_STEPS;
        cmd.set("#StepProgress.Value", progress);

        // Set step indicator
        cmd.set("#StepIndicator.Text", "STEP " + (step + 1) + " OF " + TOTAL_STEPS);

        // Set title and description
        cmd.set("#MainTitle.Text", STEP_TITLES[step]);
        cmd.set("#Description.Text", STEP_DESCRIPTIONS[step]);

        // Set feature labels and dot colors
        for (int i = 0; i < 3; i++) {
            cmd.set("#Feature" + (i + 1) + "Label.Text", STEP_FEATURES[step][i]);
            cmd.set("#Feature" + (i + 1) + "Dot.Background", STEP_FEATURE_COLORS[step][i]);
        }

        // Set button text
        cmd.set("#NextButton.Text", step == TOTAL_STEPS - 1 ? "Got It!" : "Next");
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
            if (step < TOTAL_STEPS - 1) {
                player.getPageManager().openCustomPage(ref, store,
                        new AscendWelcomePage(playerRef, step + 1));
            } else {
                this.close();
            }
        } else if (BUTTON_BACK.equals(data.getButton())) {
            if (step > 0) {
                player.getPageManager().openCustomPage(ref, store,
                        new AscendWelcomePage(playerRef, step - 1));
            } else {
                player.getPageManager().openCustomPage(ref, store,
                        new AscendHelpPage(playerRef));
            }
        }
    }
}
