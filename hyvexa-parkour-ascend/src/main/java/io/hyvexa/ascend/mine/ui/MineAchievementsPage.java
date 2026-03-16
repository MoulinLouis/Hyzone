package io.hyvexa.ascend.mine.ui;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.mine.achievement.MineAchievement;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.common.ui.ButtonEventData;

public class MineAchievementsPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final int PROGRESS_SEGMENTS = 10;

    private final PlayerRef playerRef;

    public MineAchievementsPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Ascend_MineAchievements.ui");

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        populateAchievements(cmd);
    }

    private void populateAchievements(UICommandBuilder cmd) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineAchievementTracker tracker = plugin.getMineAchievementTracker();
        if (tracker == null) return;

        UUID playerId = playerRef.getUuid();
        Set<String> completed = tracker.getCompletedIds(playerId);

        MineAchievement[] achievements = MineAchievement.values();
        int completedCount = 0;
        for (MineAchievement a : achievements) {
            if (completed.contains(a.getId())) completedCount++;
        }
        cmd.set("#ProgressLabel.Text", completedCount + " / " + achievements.length + " completed");

        for (int i = 0; i < achievements.length; i++) {
            MineAchievement achievement = achievements[i];
            boolean done = completed.contains(achievement.getId());

            cmd.append("#AchievementList", "Pages/Ascend_MineAchievementEntry.ui");
            String sel = "#AchievementList[" + i + "]";

            cmd.set(sel + " #AchName.Text", achievement.getDisplayName());
            cmd.set(sel + " #AchDesc.Text", achievement.getDescription());
            cmd.set(sel + " #AchReward.Text", "+" + achievement.getCrystalReward() + " crystals");

            if (done) {
                // Toggle accent bar overlays
                cmd.set(sel + " #AchAccentLocked.Visible", false);
                cmd.set(sel + " #AchAccentDone.Visible", true);
                cmd.set(sel + " #AchStatus.Text", "Completed");
                cmd.set(sel + " #AchStatus.Style.TextColor", "#4ade80");
                cmd.set(sel + " #AchReward.Style.TextColor", "#4ade80");
            } else {
                // Show progress for counter-based achievements
                MineAchievement.StatType statType = achievement.getStatType();
                if (statType != null) {
                    long current = tracker.getStatValue(playerId, statType);
                    long threshold = achievement.getThreshold();
                    long displayCurrent = Math.min(current, threshold);
                    cmd.set(sel + " #AchStatus.Text", displayCurrent + " / " + threshold);

                    // Show progress bar with segment visibility
                    cmd.set(sel + " #ProgressBar.Visible", true);
                    int filledSegments = (int) (Math.min(1.0, (double) current / threshold) * PROGRESS_SEGMENTS);
                    for (int seg = 1; seg <= filledSegments; seg++) {
                        cmd.set(sel + " #PSeg" + seg + ".Visible", true);
                    }
                } else {
                    cmd.set(sel + " #AchStatus.Text", "Locked");
                }
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (BUTTON_CLOSE.equals(button)) {
            this.close();
        }
    }
}
