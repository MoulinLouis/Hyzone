package io.hyvexa.runorfall.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.runorfall.data.RunOrFallPlayerStats;
import io.hyvexa.runorfall.manager.RunOrFallStatsStore;

import javax.annotation.Nonnull;
import java.util.Locale;

public class RunOrFallAchievementsPage extends InteractiveCustomUIPage<ButtonEventData> {

    private static final String BUTTON_CLOSE = "Close";
    private static final String COLOR_UNLOCKED = "#10b981";
    private static final String COLOR_LOCKED = "#4b5563";
    private static final String COLOR_UNLOCKED_TEXT = "#f0f4f8";
    private static final String COLOR_LOCKED_TEXT = "#6b7280";

    private final RunOrFallStatsStore statsStore;
    private final boolean fromProfile;

    public RunOrFallAchievementsPage(@Nonnull PlayerRef playerRef, RunOrFallStatsStore statsStore) {
        this(playerRef, statsStore, true);
    }

    public RunOrFallAchievementsPage(@Nonnull PlayerRef playerRef, RunOrFallStatsStore statsStore, boolean fromProfile) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ButtonEventData.CODEC);
        this.statsStore = statsStore;
        this.fromProfile = fromProfile;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/RunOrFall_Achievements.ui");
        if (fromProfile) {
            commandBuilder.set("#CloseButton.Text", "Back");
        }
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        PlayerRef currentPlayerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (currentPlayerRef == null || currentPlayerRef.getUuid() == null || statsStore == null) {
            commandBuilder.set("#ProgressText.Text", "0 / 16 unlocked");
            return;
        }
        RunOrFallPlayerStats stats = statsStore.getStats(currentPlayerRef.getUuid(), currentPlayerRef.getUsername());
        buildAchievementCards(stats, commandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (!BUTTON_CLOSE.equals(data.getButton())) {
            return;
        }
        if (!fromProfile) {
            this.close();
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            this.close();
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new RunOrFallProfilePage(playerRef, statsStore));
    }

    private void buildAchievementCards(RunOrFallPlayerStats stats, UICommandBuilder commandBuilder) {
        commandBuilder.clear("#AchievementCards");
        int unlockedCount = 0;
        int cardIndex = 0;
        AchievementCategory lastCategory = null;

        for (AchievementDefinition achievement : AchievementDefinition.values()) {
            if (achievement.category != lastCategory) {
                lastCategory = achievement.category;
                commandBuilder.append("#AchievementCards", "Pages/RunOrFall_AchievementCategoryHeader.ui");
                String headerPrefix = "#AchievementCards[" + cardIndex + "] ";
                commandBuilder.set(headerPrefix + "#CategoryName.Text", achievement.category.displayName);
                cardIndex++;
            }

            commandBuilder.append("#AchievementCards", "Pages/RunOrFall_AchievementEntry.ui");
            String prefix = "#AchievementCards[" + cardIndex + "] ";
            long currentValue = achievement.currentValue(stats);
            long requiredValue = achievement.required;
            boolean unlocked = currentValue >= requiredValue;
            if (unlocked) {
                unlockedCount++;
            }

            commandBuilder.set(prefix + "#Name.Text", achievement.displayName);
            commandBuilder.set(prefix + "#Description.Text", achievement.description);
            commandBuilder.set(prefix + "#Progress.Visible", true);
            commandBuilder.set(prefix + "#Progress.Text",
                    achievement.progressText(Math.max(0L, currentValue), requiredValue));

            if (unlocked) {
                commandBuilder.set(prefix + "#AccentBar.Background", COLOR_UNLOCKED);
                commandBuilder.set(prefix + "#UnlockedTint.Visible", true);
                commandBuilder.set(prefix + "#UnlockedIcon.Visible", true);
                commandBuilder.set(prefix + "#LockIcon.Visible", false);
                commandBuilder.set(prefix + "#Name.Style.TextColor", COLOR_UNLOCKED_TEXT);
                commandBuilder.set(prefix + "#Description.Style.TextColor", "#cfd7dc");
                commandBuilder.set(prefix + "#Progress.Style.TextColor", "#9fe8c9");
            } else {
                commandBuilder.set(prefix + "#AccentBar.Background", COLOR_LOCKED);
                commandBuilder.set(prefix + "#UnlockedTint.Visible", false);
                commandBuilder.set(prefix + "#UnlockedIcon.Visible", false);
                commandBuilder.set(prefix + "#LockIcon.Visible", true);
                commandBuilder.set(prefix + "#Name.Style.TextColor", COLOR_LOCKED_TEXT);
                commandBuilder.set(prefix + "#Description.Style.TextColor", COLOR_LOCKED_TEXT);
                commandBuilder.set(prefix + "#Progress.Style.TextColor", "#9ca3af");
            }

            cardIndex++;
        }

        commandBuilder.set("#ProgressText.Text",
                unlockedCount + " / " + AchievementDefinition.values().length + " unlocked");
    }

    private enum AchievementCategory {
        WINS("Wins"),
        BLOCKS_BROKEN("Blocks Broken"),
        TIME_SURVIVED("Time Survived"),
        BLINK_USAGE("Blink Usage");

        private final String displayName;

        AchievementCategory(String displayName) {
            this.displayName = displayName;
        }
    }

    private enum ProgressMetric {
        COUNT,
        TIME_MS
    }

    private enum AchievementDefinition {
        FIRST_DROP_SURVIVOR(AchievementCategory.WINS, ProgressMetric.COUNT, 1L,
                "First Drop Survivor", "Win 1 round."),
        LAST_ONE_STANDING(AchievementCategory.WINS, ProgressMetric.COUNT, 10L,
                "Last One Standing", "Win 10 rounds."),
        ARENA_DOMINATOR(AchievementCategory.WINS, ProgressMetric.COUNT, 100L,
                "Arena Dominator", "Win 100 rounds."),
        FLOORBREAKING_LEGEND(AchievementCategory.WINS, ProgressMetric.COUNT, 1000L,
                "Floorbreaking Legend", "Win 1,000 rounds."),

        LIGHT_STEPPER(AchievementCategory.BLOCKS_BROKEN, ProgressMetric.COUNT, 500L,
                "Light Stepper", "Break 500 blocks."),
        FLOOR_MELTER(AchievementCategory.BLOCKS_BROKEN, ProgressMetric.COUNT, 5_000L,
                "Floor Melter", "Break 5,000 blocks."),
        GROUND_ERASER(AchievementCategory.BLOCKS_BROKEN, ProgressMetric.COUNT, 50_000L,
                "Ground Eraser", "Break 50,000 blocks."),
        VOID_ARCHITECT(AchievementCategory.BLOCKS_BROKEN, ProgressMetric.COUNT, 500_000L,
                "Void Architect", "Break 500,000 blocks."),

        STILL_STANDING(AchievementCategory.TIME_SURVIVED, ProgressMetric.TIME_MS, 300_000L,
                "Still Standing", "Survive 5 minutes in one round."),
        ENDURANCE_RUNNER(AchievementCategory.TIME_SURVIVED, ProgressMetric.TIME_MS, 600_000L,
                "Endurance Runner", "Survive 10 minutes in one round."),
        RELENTLESS(AchievementCategory.TIME_SURVIVED, ProgressMetric.TIME_MS, 900_000L,
                "Relentless", "Survive 15 minutes in one round."),
        UNCATCHABLE(AchievementCategory.TIME_SURVIVED, ProgressMetric.TIME_MS, 1_200_000L,
                "Uncatchable", "Survive 20 minutes in one round."),

        BLINK_ROOKIE(AchievementCategory.BLINK_USAGE, ProgressMetric.COUNT, 5L,
                "Blink Rookie", "Use blink 5 times."),
        PHASE_WALKER(AchievementCategory.BLINK_USAGE, ProgressMetric.COUNT, 50L,
                "Phase Walker", "Use blink 50 times."),
        DIMENSION_BREAKER(AchievementCategory.BLINK_USAGE, ProgressMetric.COUNT, 500L,
                "Dimension Breaker", "Use blink 500 times."),
        BLINK_OVERLORD(AchievementCategory.BLINK_USAGE, ProgressMetric.COUNT, 5_000L,
                "Blink Overlord", "Use blink 5,000 times.");

        private final AchievementCategory category;
        private final ProgressMetric metric;
        private final long required;
        private final String displayName;
        private final String description;

        AchievementDefinition(AchievementCategory category, ProgressMetric metric, long required,
                              String displayName, String description) {
            this.category = category;
            this.metric = metric;
            this.required = required;
            this.displayName = displayName;
            this.description = description;
        }

        private long currentValue(RunOrFallPlayerStats stats) {
            return switch (this) {
                case FIRST_DROP_SURVIVOR, LAST_ONE_STANDING, ARENA_DOMINATOR, FLOORBREAKING_LEGEND ->
                        stats != null ? stats.getWins() : 0L;
                case LIGHT_STEPPER, FLOOR_MELTER, GROUND_ERASER, VOID_ARCHITECT ->
                        stats != null ? stats.getTotalBlocksBroken() : 0L;
                case STILL_STANDING, ENDURANCE_RUNNER, RELENTLESS, UNCATCHABLE ->
                        stats != null ? stats.getLongestSurvivedMs() : 0L;
                case BLINK_ROOKIE, PHASE_WALKER, DIMENSION_BREAKER, BLINK_OVERLORD ->
                        stats != null ? stats.getTotalBlinksUsed() : 0L;
            };
        }

        private String progressText(long current, long required) {
            if (metric == ProgressMetric.TIME_MS) {
                return formatTimeProgress(current, required);
            }
            return String.format(Locale.US, "%,d / %,d", Math.min(current, required), required);
        }

        private static String formatTimeProgress(long currentMs, long requiredMs) {
            long safeCurrent = Math.max(0L, currentMs);
            long shownCurrent = Math.min(safeCurrent, requiredMs);
            return formatMinutesSeconds(shownCurrent) + " / " + formatMinutesSeconds(requiredMs);
        }

        private static String formatMinutesSeconds(long millis) {
            long totalSeconds = Math.max(0L, millis / 1000L);
            long minutes = totalSeconds / 60L;
            long seconds = totalSeconds % 60L;
            return String.format(Locale.US, "%dm %02ds", minutes, seconds);
        }
    }
}
