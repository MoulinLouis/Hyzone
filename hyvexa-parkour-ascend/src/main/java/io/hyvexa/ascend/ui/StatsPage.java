package io.hyvexa.ascend.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.ghost.GhostRecording;
import io.hyvexa.ascend.ghost.GhostStore;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StatsPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";

    // Accent colors for each stat
    private static final String COLOR_INCOME = "#10b981";      // Green
    private static final String COLOR_MULTIPLIER = "#7c3aed";  // Violet
    private static final String COLOR_LIFETIME = "#ffd166";    // Gold
    private static final String COLOR_RUNS = "#3b82f6";        // Blue
    private static final String COLOR_ASCENSIONS = "#06b6d4";  // Cyan
    private static final String COLOR_FASTEST = "#f59e0b";     // Orange

    private final AscendPlayerStore playerStore;
    private final AscendMapStore mapStore;
    private final GhostStore ghostStore;
    private ScheduledFuture<?> refreshTask;

    public StatsPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore,
                     AscendMapStore mapStore, GhostStore ghostStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.mapStore = mapStore;
        this.ghostStore = ghostStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Stats.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        buildStatCards(ref, store, commandBuilder, eventBuilder);
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

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (BUTTON_CLOSE.equals(data.getButton())) {
            this.close();
        }
    }

    private void buildStatCards(Ref<EntityStore> ref, Store<EntityStore> store,
                                 UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();

        // Clear existing cards
        commandBuilder.clear("#StatsCards");

        // 1. Combined Income
        commandBuilder.append("#StatsCards", "Pages/Ascend_StatsEntry.ui");
        commandBuilder.set("#StatsCards[0] #AccentBar.Background", COLOR_INCOME);
        commandBuilder.set("#StatsCards[0] #StatLabel.Text", "Combined Income");
        commandBuilder.set("#StatsCards[0] #StatValue.Text", formatCombinedIncome(playerId));

        // 2. Multiplier Breakdown
        commandBuilder.append("#StatsCards", "Pages/Ascend_StatsEntry.ui");
        commandBuilder.set("#StatsCards[1] #AccentBar.Background", COLOR_MULTIPLIER);
        commandBuilder.set("#StatsCards[1] #StatLabel.Text", "Multiplier Breakdown");
        commandBuilder.set("#StatsCards[1] #StatValue.Text", formatMultiplierBreakdown(playerId));

        // 3. Lifetime Earnings
        commandBuilder.append("#StatsCards", "Pages/Ascend_StatsEntry.ui");
        commandBuilder.set("#StatsCards[2] #AccentBar.Background", COLOR_LIFETIME);
        commandBuilder.set("#StatsCards[2] #StatLabel.Text", "Lifetime Earnings");
        double totalEarned = playerStore.getTotalCoinsEarned(playerId);
        commandBuilder.set("#StatsCards[2] #StatValue.Text", FormatUtils.formatCoinsForHudDecimal(totalEarned) + " coins");

        // 4. Manual Runs
        commandBuilder.append("#StatsCards", "Pages/Ascend_StatsEntry.ui");
        commandBuilder.set("#StatsCards[3] #AccentBar.Background", COLOR_RUNS);
        commandBuilder.set("#StatsCards[3] #StatLabel.Text", "Manual Runs");
        int manualRuns = playerStore.getTotalManualRuns(playerId);
        commandBuilder.set("#StatsCards[3] #StatValue.Text", manualRuns + " runs");

        // 5. Ascensions
        commandBuilder.append("#StatsCards", "Pages/Ascend_StatsEntry.ui");
        commandBuilder.set("#StatsCards[4] #AccentBar.Background", COLOR_ASCENSIONS);
        commandBuilder.set("#StatsCards[4] #StatLabel.Text", "Ascensions");
        int ascensions = playerStore.getAscensionCount(playerId);
        commandBuilder.set("#StatsCards[4] #StatValue.Text", String.valueOf(ascensions));

        // 6. Fastest Ascension (special card with timer)
        commandBuilder.append("#StatsCards", "Pages/Ascend_StatsTimerEntry.ui");
        commandBuilder.set("#StatsCards[5] #AccentBar.Background", COLOR_FASTEST);
        updateFastestAscension(commandBuilder, playerId);
    }

    private void updateFastestAscension(UICommandBuilder commandBuilder, UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            commandBuilder.set("#StatsCards[5] #BestValue.Text", "\u2014");
            commandBuilder.set("#StatsCards[5] #CurrentValue.Text", "0s");
            return;
        }

        // Best time
        Long fastestMs = progress.getFastestAscensionMs();
        if (fastestMs != null) {
            commandBuilder.set("#StatsCards[5] #BestValue.Text", formatDuration(fastestMs));
        } else {
            commandBuilder.set("#StatsCards[5] #BestValue.Text", "\u2014");
        }

        // Current time
        Long startedAt = progress.getAscensionStartedAt();
        if (startedAt != null) {
            long current = System.currentTimeMillis() - startedAt;
            commandBuilder.set("#StatsCards[5] #CurrentValue.Text", formatDuration(current));
        } else {
            commandBuilder.set("#StatsCards[5] #CurrentValue.Text", "0s");
        }
    }

    private String formatCombinedIncome(UUID playerId) {
        if (mapStore == null || ghostStore == null) {
            return "0 coins/sec";
        }

        List<AscendMap> maps = mapStore.listMapsSorted();
        double totalCoinsPerSec = 0.0;

        // Get multipliers
        double digitsProduct = playerStore.getMultiplierProductDecimal(playerId, maps, AscendConstants.MULTIPLIER_SLOTS);
        double elevation = playerStore.getCalculatedElevationMultiplier(playerId);

        // Get summit coin flow bonus
        double coinFlowBonus = 0.0;
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            SummitManager summitManager = plugin.getSummitManager();
            if (summitManager != null) {
                coinFlowBonus = summitManager.getCoinFlowBonus(playerId);
            }
        }

        for (AscendMap map : maps) {
            AscendPlayerProgress.MapProgress mapProgress = playerStore.getMapProgress(playerId, map.getId());
            if (mapProgress == null || !mapProgress.hasRobot()) {
                continue;
            }

            // Get ghost for PB time
            GhostRecording ghost = ghostStore.getRecording(playerId, map.getId());
            if (ghost == null || ghost.getCompletionTimeMs() <= 0) {
                continue;
            }

            int speedLevel = mapProgress.getRobotSpeedLevel();
            double speedMultiplier = 1.0 + (speedLevel * AscendConstants.SPEED_UPGRADE_MULTIPLIER);

            // Add summit runner speed bonus
            if (plugin != null) {
                SummitManager summitManager = plugin.getSummitManager();
                if (summitManager != null) {
                    speedMultiplier += summitManager.getRunnerSpeedBonus(playerId);
                }
            }

            long intervalMs = (long) (ghost.getCompletionTimeMs() / speedMultiplier);
            intervalMs = Math.max(1L, intervalMs);
            double runsPerSec = 1000.0 / intervalMs;

            // Base reward with multipliers
            long baseReward = map.getEffectiveBaseReward();
            double coinsPerRun = baseReward * digitsProduct * elevation * (1.0 + coinFlowBonus);

            totalCoinsPerSec += runsPerSec * coinsPerRun;
        }

        if (totalCoinsPerSec < 0.01) {
            return "0 coins/sec";
        }
        return FormatUtils.formatCoinsForHudDecimal(totalCoinsPerSec) + " coins/sec";
    }

    private String formatMultiplierBreakdown(UUID playerId) {
        List<AscendMap> maps = mapStore != null ? mapStore.listMapsSorted() : List.of();
        double[] digits = playerStore.getMultiplierDisplayValues(playerId, maps, AscendConstants.MULTIPLIER_SLOTS);
        double elevation = playerStore.getCalculatedElevationMultiplier(playerId);

        // Get summit coin flow bonus
        double coinFlowBonus = 0.0;
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getSummitManager() != null) {
            coinFlowBonus = plugin.getSummitManager().getCoinFlowBonus(playerId);
        }

        // Calculate digits product
        double digitsProduct = 1.0;
        for (double d : digits) {
            digitsProduct *= Math.max(1.0, d);
        }

        double summitMultiplier = 1.0 + coinFlowBonus;
        double total = digitsProduct * elevation * summitMultiplier;

        return String.format(Locale.US, "%.1fx \u00d7 %.1fx \u00d7 %.1fx = %.1fx",
            digitsProduct, elevation, summitMultiplier, total);
    }

    private String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.US, "%dh %02dm %02ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format(Locale.US, "%dm %02ds", minutes, seconds);
        } else {
            return seconds + "s";
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
            try {
                CompletableFuture.runAsync(() -> refreshTimer(ref, store), world);
            } catch (Exception e) {
                stopAutoRefresh();
            }
        }, 1000L, 1000L, TimeUnit.MILLISECONDS);
    }

    private void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
    }

    private void refreshTimer(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!isCurrentPage()) {
            stopAutoRefresh();
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UICommandBuilder commandBuilder = new UICommandBuilder();
        updateFastestAscension(commandBuilder, playerRef.getUuid());
        if (!isCurrentPage()) {
            return;
        }
        try {
            sendUpdate(commandBuilder, null, false);
        } catch (Exception e) {
            // UI was replaced by external dialog (e.g., NPCDialog) - stop refreshing
            stopAutoRefresh();
        }
    }
}
