package io.hyvexa.ascend.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.hud.ToastType;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SummitPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_SUMMIT_PREFIX = "Summit_";
    private static final long REFRESH_INTERVAL_MS = 1000L;

    private final AscendPlayerStore playerStore;
    private final SummitManager summitManager;
    private ScheduledFuture<?> refreshTask;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final AtomicBoolean refreshRequested = new AtomicBoolean(false);

    public SummitPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore, SummitManager summitManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.summitManager = summitManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Summit.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        buildCategoryCards(ref, store, commandBuilder, eventBuilder);
        startAutoRefresh(ref, store);
    }

    @Override
    public void close() {
        stopAutoRefresh();
        super.close();
    }

    @Override
    protected void stopBackgroundTasks() {
        stopAutoRefresh();
    }

    private void buildCategoryCards(Ref<EntityStore> ref, Store<EntityStore> store,
                                     UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#CategoryCards");
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        // Build 3 category cards
        SummitCategory[] categories = {
            SummitCategory.MULTIPLIER_GAIN,
            SummitCategory.RUNNER_SPEED,
            SummitCategory.EVOLUTION_POWER
        };

        for (int i = 0; i < categories.length; i++) {
            SummitCategory category = categories[i];
            commandBuilder.append("#CategoryCards", "Pages/Ascend_SummitEntry.ui");

            String accentColor = resolveCategoryAccentColor(i);

            // Apply accent color to accent bar and XP segments
            commandBuilder.set("#CategoryCards[" + i + "] #AccentBar.Background", accentColor);
            for (int seg = 1; seg <= 20; seg++) {
                commandBuilder.set("#CategoryCards[" + i + "] #XpSeg" + seg + ".Background", accentColor);
            }

            // Category description (static)
            String description = getCategoryDescription(category);
            commandBuilder.set("#CategoryCards[" + i + "] #CategoryDesc.Text", description);

            // Event binding
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#CategoryCards[" + i + "] #SummitButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SUMMIT_PREFIX + category.name()), false);
        }

        // Update dynamic content
        updateCategoryCards(ref, store, commandBuilder);
    }

    private void updateCategoryCards(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        // Update progress text (shared across all categories)
        BigNumber accumulatedVexa = playerStore.getSummitAccumulatedVexa(playerId);
        long currentXp = AscendConstants.vexaToXp(accumulatedVexa);
        long nextXp = currentXp + 1;
        BigNumber vexaForNextXp = AscendConstants.xpToVexa(nextXp);
        String progressText = "Progress to next EXP: " +
            FormatUtils.formatBigNumber(accumulatedVexa) + " / " +
            FormatUtils.formatBigNumber(vexaForNextXp) + " accumulated vexa";
        commandBuilder.set("#ProgressText.Text", progressText);

        SummitCategory[] categories = {
            SummitCategory.MULTIPLIER_GAIN,
            SummitCategory.RUNNER_SPEED,
            SummitCategory.EVOLUTION_POWER
        };

        for (int i = 0; i < categories.length; i++) {
            SummitCategory category = categories[i];
            SummitManager.SummitPreview preview = summitManager.previewSummit(playerId, category);
            // Category name with level progression
            String levelText;
            if (preview.hasGain()) {
                levelText = String.format(" (Lv.%d -> Lv.%d)", preview.currentLevel(), preview.newLevel());
            } else {
                levelText = String.format(" (Lv.%d)", preview.currentLevel());
            }
            commandBuilder.set("#CategoryCards[" + i + "] #CategoryName.Text", category.getDisplayName() + levelText);

            // Bonus text
            String bonusText = "Current: " + formatBonus(category, preview.currentBonus())
                + " -> Next: " + formatBonus(category, preview.newBonus());
            commandBuilder.set("#CategoryCards[" + i + "] #CategoryBonus.Text", bonusText);

            // XP progress text
            String xpText;
            long xpRemaining = preview.currentXpRequired() - preview.currentXpInLevel();
            long xpAfterSummit = xpRemaining - preview.xpToGain();
            if (preview.xpToGain() > 0 && xpAfterSummit <= 0) {
                // Player will level up - show what the NEXT level requires
                long nextLevelXpReq = AscendConstants.getXpForLevel(preview.newLevel() + 1);
                xpText = String.format("Exp %d/%d (+%d) | Needs %d XP to get Lv.%d",
                    preview.currentXpInLevel(), preview.currentXpRequired(), preview.xpToGain(),
                    nextLevelXpReq, preview.newLevel() + 1);
            } else {
                xpText = String.format("Exp %d/%d (+%d)",
                    preview.currentXpInLevel(), preview.currentXpRequired(), preview.xpToGain());
            }
            commandBuilder.set("#CategoryCards[" + i + "] #XpProgress.Text", xpText);

            // XP progress bar segments (20 segments = 5% each)
            double progressPercent = preview.currentXpRequired() > 0
                ? (double) preview.currentXpInLevel() / preview.currentXpRequired()
                : 0;
            int filledSegments = (int)(progressPercent * 20);
            for (int seg = 1; seg <= 20; seg++) {
                commandBuilder.set("#CategoryCards[" + i + "] #XpSeg" + seg + ".Visible", seg <= filledSegments);
            }

            // Show colored background if summit is possible, lock icon if not
            boolean blocked = false;
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin != null && plugin.getChallengeManager() != null) {
                blocked = plugin.getChallengeManager().isSummitBlocked(playerId, category);
            }
            boolean canSummit = !blocked && preview.hasGain() && summitManager.canSummit(playerId);
            commandBuilder.set("#CategoryCards[" + i + "] " + resolveCardBgElementId(i) + ".Visible", canSummit);
            commandBuilder.set("#CategoryCards[" + i + "] #LockIcon.Visible", !canSummit);
        }
    }

    private void startAutoRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (refreshTask != null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        refreshTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            if (!isCurrentPage()) {
                stopAutoRefresh();
                return;
            }
            if (ref == null || !ref.isValid()) {
                stopAutoRefresh();
                return;
            }
            PageRefreshScheduler.requestRefresh(
                world,
                refreshInFlight,
                refreshRequested,
                () -> refreshDisplay(ref, store),
                this::stopAutoRefresh,
                "SummitPage"
            );
        }, REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        refreshRequested.set(false);
    }

    private void refreshDisplay(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!isCurrentPage()) {
            stopAutoRefresh();
            return;
        }
        UICommandBuilder commandBuilder = new UICommandBuilder();
        updateCategoryCards(ref, store, commandBuilder);
        if (!isCurrentPage()) {
            return;
        }
        try {
            sendUpdate(commandBuilder, null, false);
        } catch (Exception e) {
            stopAutoRefresh();
        }
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
            return;
        }

        if (data.getButton().startsWith(BUTTON_SUMMIT_PREFIX)) {
            String categoryName = data.getButton().substring(BUTTON_SUMMIT_PREFIX.length());
            try {
                SummitCategory category = SummitCategory.valueOf(categoryName);
                handleSummit(ref, store, category);
            } catch (IllegalArgumentException e) {
                // Invalid category name
            }
        }
    }

    private void handleSummit(Ref<EntityStore> ref, Store<EntityStore> store, SummitCategory category) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        // Block summiting in categories locked by an active challenge
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getChallengeManager() != null
                && plugin.getChallengeManager().isSummitBlocked(playerId, category)) {
            player.sendMessage(Message.raw("[Summit] " + category.getDisplayName()
                + " is locked during your active challenge.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        if (!summitManager.canSummit(playerId)) {
            BigNumber vexa = playerStore.getVexa(playerId);
            String minVexa = FormatUtils.formatBigNumber(
                BigNumber.fromLong(AscendConstants.SUMMIT_MIN_VEXA));
            String currentVexa = FormatUtils.formatBigNumber(vexa);
            player.sendMessage(Message.raw("[Summit] Need " + minVexa
                + " vexa to Summit. You have: " + currentVexa)
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        SummitManager.SummitPreview preview = summitManager.previewSummit(playerId, category);
        if (!preview.hasGain()) {
            player.sendMessage(Message.raw("[Summit] Insufficient vexa for level gain.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        // Despawn all robots before resetting data to prevent completions with pre-reset multipliers
        if (plugin != null) {
            RobotManager robotManager = plugin.getRobotManager();
            if (robotManager != null) {
                robotManager.despawnRobotsForPlayer(playerId);
            }
        }

        SummitManager.SummitResult result = summitManager.performSummit(playerId, category);
        if (!result.succeeded()) {
            player.sendMessage(Message.raw("[Summit] Summit failed.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        showToast(playerId, ToastType.EVOLUTION,
            category.getDisplayName() + " Lv." + preview.currentLevel()
            + " -> Lv." + result.newLevel() + " | " + formatBonus(category, preview.currentBonus())
            + " -> " + formatBonus(category, preview.newBonus()));
        player.sendMessage(Message.raw("[Summit] Progress reset: vexa, elevation, multipliers, runners, map unlocks")
            .color(SystemMessageUtils.SECONDARY));

        // Check achievements
        if (plugin != null && plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
        }

        // Refresh display (auto-refresh will handle subsequent updates)
        if (!isCurrentPage()) {
            return;
        }
        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateCategoryCards(ref, store, updateBuilder);
        try {
            sendUpdate(updateBuilder, null, false);
        } catch (Exception e) {
            // UI was replaced - ignore
        }
    }

    private String resolveCategoryAccentColor(int index) {
        return switch (index) {
            case 0 -> "#10b981";  // Green for Multiplier Gain
            case 1 -> "#3b82f6";  // Blue for Runner Speed
            default -> "#8b5cf6"; // Purple for Evolution Power
        };
    }

    private String resolveCardBgElementId(int index) {
        return switch (index) {
            case 0 -> "#CardBgGreen";
            case 1 -> "#CardBgBlue";
            default -> "#CardBgPurple";
        };
    }

    private String getCategoryDescription(SummitCategory category) {
        return switch (category) {
            case RUNNER_SPEED -> "Multiplies runner completion speed";
            case MULTIPLIER_GAIN -> "Multiplies multiplier gain per run";
            case EVOLUTION_POWER -> "Increases evolution base multiplier";
        };
    }

    /**
     * Format a bonus value for display - all categories show as multipliers.
     */
    private String formatBonus(SummitCategory category, double value) {
        return String.format(Locale.US, "x%.2f", value);
    }

    private void showToast(UUID playerId, ToastType type, String message) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            AscendHudManager hm = plugin.getHudManager();
            if (hm != null) {
                hm.showToast(playerId, type, message);
            }
        }
    }
}
