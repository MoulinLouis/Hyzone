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
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.AscendConstants.AchievementCategory;
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
    private final boolean fromProfile;

    public AscendAchievementPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore,
                                  AchievementManager achievementManager) {
        this(playerRef, playerStore, achievementManager, false);
    }

    public AscendAchievementPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore,
                                  AchievementManager achievementManager, boolean fromProfile) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.achievementManager = achievementManager;
        this.fromProfile = fromProfile;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Achievement.ui");

        if (fromProfile) {
            commandBuilder.set("#CloseButton.Text", "Back");
        }

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        // Check and unlock any pending achievements before displaying
        Player player = store.getComponent(ref, Player.getComponentType());
        achievementManager.checkAndUnlockAchievements(playerId, player);

        buildAchievementCards(playerId, commandBuilder);
    }

    private void buildAchievementCards(UUID playerId, UICommandBuilder commandBuilder) {
        AchievementType[] achievements = AchievementType.values();
        int unlockedCount = 0;
        int cardIndex = 0;
        AchievementCategory lastCategory = null;

        for (AchievementType achievement : achievements) {
            AchievementProgress progress = achievementManager.getProgress(playerId, achievement);

            // Insert category header when category changes
            if (achievement.getCategory() != lastCategory) {
                lastCategory = achievement.getCategory();
                commandBuilder.append("#AchievementCards", "Pages/Ascend_AchievementCategoryHeader.ui");
                String headerPrefix = "#AchievementCards[" + cardIndex + "] ";
                commandBuilder.set(headerPrefix + "#CategoryName.Text", lastCategory.getDisplayName());
                cardIndex++;
            }

            commandBuilder.append("#AchievementCards", "Pages/Ascend_AchievementEntry.ui");
            String prefix = "#AchievementCards[" + cardIndex + "] ";

            boolean isHiddenAndLocked = achievement.isHidden() && !progress.unlocked();

            // Set name, description (hidden achievements show ??? when locked)
            commandBuilder.set(prefix + "#Name.Text", isHiddenAndLocked ? "???" : achievement.getName());
            commandBuilder.set(prefix + "#Description.Text", isHiddenAndLocked ? "???" : achievement.getDescription());

            // Show progress for progressive achievements (required > 1)
            if (!isHiddenAndLocked && progress.required() > 1) {
                commandBuilder.set(prefix + "#Progress.Visible", true);
                commandBuilder.set(prefix + "#Progress.Text", progress.current() + " / " + progress.required());
            }

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

            cardIndex++;
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
            if (fromProfile) {
                navigateBackToProfile(ref, store);
            } else {
                this.close();
            }
        }
    }

    private void navigateBackToProfile(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (player != null && playerRef != null && plugin != null && plugin.getRobotManager() != null) {
            player.getPageManager().openCustomPage(ref, store,
                    new AscendProfilePage(playerRef, playerStore, plugin.getRobotManager()));
        } else {
            this.close();
        }
    }
}
