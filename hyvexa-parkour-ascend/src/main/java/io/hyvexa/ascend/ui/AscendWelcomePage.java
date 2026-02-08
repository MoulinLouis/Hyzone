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

public class AscendWelcomePage extends BaseAscendPage {

    private static final String BUTTON_NEXT = "Next";
    private static final String BUTTON_BACK = "Back";

    private static final int TOTAL_STEPS = 3;

    private static final String[] STEP_TITLES = {
        "Welcome to Ascend",
        "Your Shortcuts",
        "Play Your First Map"
    };

    private static final String[] STEP_DESCRIPTIONS = {
        "Ascend is a parkour idle game. Run maps, earn coins, and build up an army of automated runners that play for you - even while you're offline.",
        "You have 4 items in your inventory that open menus instantly - no need to type commands. Here's what each one does:",
        "Open the map menu and pick a map. Complete it to earn your first coins and unlock new features along the way!"
    };

    private static final String[][] STEP_FEATURES = {
        {
            "Run parkour maps to earn coins",
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

    private static final String STEP2_FEATURE4_TEXT = "/ascend help - Tutorials and guides";
    private static final String STEP2_FEATURE4_COLOR = "#a855f7";

    private static final String[][] STEP_FEATURE_COLORS = {
        {"#10b981", "#3b82f6", "#f59e0b"},
        {"#f59e0b", "#3b82f6", "#10b981"},
        {"#f59e0b", "#10b981", "#a855f7"}
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

        // Show 4th feature on shortcuts step (step 2)
        boolean isShortcutsStep = step == 1;
        cmd.set("#Feature4Row.Visible", isShortcutsStep);
        if (isShortcutsStep) {
            cmd.set("#Feature4Label.Text", STEP2_FEATURE4_TEXT);
            cmd.set("#Feature4Dot.Background", STEP2_FEATURE4_COLOR);
        }

        // Show tip card on last step
        cmd.set("#TipCard.Visible", step == 2);

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
