package io.hyvexa.ascend.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants.AchievementType;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.achievement.AchievementManager.AchievementProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.ui.ButtonEventData;

import javax.annotation.Nonnull;
import java.util.UUID;

public class AscendAchievementPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";

    private final AscendPlayerStore playerStore;
    private final AchievementManager achievementManager;

    public AscendAchievementPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore,
                                  AchievementManager achievementManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.achievementManager = achievementManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Achievement.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        buildAchievementCards(playerId, commandBuilder);
    }

    private void buildAchievementCards(UUID playerId, UICommandBuilder commandBuilder) {
        AchievementType[] achievements = AchievementType.values();
        int unlockedCount = 0;

        for (int i = 0; i < achievements.length; i++) {
            AchievementType achievement = achievements[i];
            AchievementProgress progress = achievementManager.getProgress(playerId, achievement);

            commandBuilder.append("#AchievementCards", "Pages/Ascend_AchievementEntry.ui");

            String prefix = "#AchievementCards[" + i + "] ";

            // Set name, description, title
            commandBuilder.set(prefix + "#Name.Text", achievement.getName());
            commandBuilder.set(prefix + "#Description.Text", achievement.getDescription());

            if (progress.unlocked()) {
                unlockedCount++;

                // Unlocked styling: show golem icon, hide lock
                commandBuilder.set(prefix + "#AccentBar.Background", "#10b981");
                commandBuilder.set(prefix + "#UnlockedTint.Visible", true);
                commandBuilder.set(prefix + "#UnlockedIcon.Visible", true);
                commandBuilder.set(prefix + "#LockIcon.Visible", false);
                commandBuilder.set(prefix + "#Name.Style.TextColor", "#f0f4f8");
            } else {
                // Locked styling: show lock icon, hide golem
                commandBuilder.set(prefix + "#AccentBar.Background", "#4b5563");
                commandBuilder.set(prefix + "#UnlockedTint.Visible", false);
                commandBuilder.set(prefix + "#UnlockedIcon.Visible", false);
                commandBuilder.set(prefix + "#LockIcon.Visible", true);
            }
        }

        // Update progress counter
        commandBuilder.set("#ProgressText.Text", unlockedCount + " / " + achievements.length + " unlocked");
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }

        if (BUTTON_CLOSE.equals(data.getButton())) {
            this.close();
        }
    }
}
