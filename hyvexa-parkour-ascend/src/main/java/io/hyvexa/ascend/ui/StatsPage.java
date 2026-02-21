package io.hyvexa.ascend.ui;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

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

import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.ghost.GhostRecording;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;

public class StatsPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";

    private final AscendPlayerStore playerStore;
    private final AscendMapStore mapStore;
    private final GhostStore ghostStore;
    private final boolean fromProfile;
    private ScheduledFuture<?> refreshTask;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final AtomicBoolean refreshRequested = new AtomicBoolean(false);

    public StatsPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore,
                     AscendMapStore mapStore, GhostStore ghostStore) {
        this(playerRef, playerStore, mapStore, ghostStore, false);
    }

    public StatsPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore,
                     AscendMapStore mapStore, GhostStore ghostStore, boolean fromProfile) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.mapStore = mapStore;
        this.ghostStore = ghostStore;
        this.fromProfile = fromProfile;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Stats.ui");

        if (fromProfile) {
            commandBuilder.set("#CloseButton.Text", "Back");
        }

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            updateAllStats(commandBuilder, playerRef.getUuid());
        }

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
            if (fromProfile) {
                navigateBackToProfile(ref, store);
            } else {
                this.close();
            }
        }
    }

    private void navigateBackToProfile(Ref<EntityStore> ref, Store<EntityStore> store) {
        com.hypixel.hytale.server.core.entity.entities.Player player =
                store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (player != null && playerRef != null && plugin != null && plugin.getRobotManager() != null) {
            player.getPageManager().openCustomPage(ref, store,
                    new AscendProfilePage(playerRef, playerStore, plugin.getRobotManager()));
        } else {
            this.close();
        }
    }

    private void updateAllStats(UICommandBuilder commandBuilder, UUID playerId) {
        // 1. Combined Income
        commandBuilder.set("#IncomeValue.Text", formatCombinedIncome(playerId));

        // 2. Multiplier Breakdown
        List<AscendMap> maps = mapStore != null ? mapStore.listMapsSorted() : List.of();
        BigNumber[] digits = playerStore.getMultiplierDisplayValues(playerId, maps, AscendConstants.MULTIPLIER_SLOTS);
        double elevation = playerStore.getCalculatedElevationMultiplier(playerId);

        double digitsProduct = 1.0;
        for (BigNumber d : digits) {
            digitsProduct *= Math.max(1.0, d.toDouble());
        }
        double total = digitsProduct * elevation;

        commandBuilder.set("#MultiplierValue.Text", formatMultiplier(total));
        commandBuilder.set("#MultiplierDetail.Text",
            "Digits: " + formatMultiplier(digitsProduct) + "  |  Elevation: " + formatMultiplier(elevation));

        // 3. Lifetime Earnings
        BigNumber totalEarned = playerStore.getTotalVoltEarned(playerId);
        commandBuilder.set("#LifetimeValue.Text", FormatUtils.formatBigNumber(totalEarned) + " volt");

        // 4. Manual Runs
        int manualRuns = playerStore.getTotalManualRuns(playerId);
        commandBuilder.set("#RunsValue.Text", String.format(Locale.US, "%,d runs", manualRuns));

        // 5. Ascensions
        int ascensions = playerStore.getAscensionCount(playerId);
        commandBuilder.set("#AscensionsValue.Text", String.format(Locale.US, "%,d", ascensions));

        // 6. Fastest Ascension
        updateFastestAscension(commandBuilder, playerId);
    }

    private void updateFastestAscension(UICommandBuilder commandBuilder, UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            commandBuilder.set("#BestValue.Text", "-");
            commandBuilder.set("#CurrentValue.Text", "0s");
            return;
        }

        Long fastestMs = progress.getFastestAscensionMs();
        commandBuilder.set("#BestValue.Text", fastestMs != null ? FormatUtils.formatDurationLong(fastestMs) : "-");

        Long startedAt = progress.getAscensionStartedAt();
        if (startedAt != null) {
            long current = System.currentTimeMillis() - startedAt;
            commandBuilder.set("#CurrentValue.Text", FormatUtils.formatDurationLong(current));
        } else {
            commandBuilder.set("#CurrentValue.Text", "0s");
        }
    }

    private String formatCombinedIncome(UUID playerId) {
        if (mapStore == null || ghostStore == null) {
            return "0 volt/sec";
        }

        List<AscendMap> maps = mapStore.listMapsSorted();
        double totalVoltPerSec = 0.0;

        BigNumber digitsProduct = playerStore.getMultiplierProduct(playerId, maps, AscendConstants.MULTIPLIER_SLOTS);

        for (AscendMap map : maps) {
            AscendPlayerProgress.MapProgress mapProgress = playerStore.getMapProgress(playerId, map.getId());
            if (mapProgress == null || !mapProgress.hasRobot()) {
                continue;
            }

            GhostRecording ghost = ghostStore.getRecording(playerId, map.getId());
            if (ghost == null || ghost.getCompletionTimeMs() <= 0) {
                continue;
            }

            int speedLevel = mapProgress.getRobotSpeedLevel();
            double speedMultiplier = RobotManager.calculateSpeedMultiplier(map, speedLevel, playerId);

            long intervalMs = (long) (ghost.getCompletionTimeMs() / speedMultiplier);
            intervalMs = Math.max(1L, intervalMs);
            double runsPerSec = 1000.0 / intervalMs;

            long baseReward = map.getEffectiveBaseReward();
            double voltPerRun = baseReward * digitsProduct.toDouble();

            totalVoltPerSec += runsPerSec * voltPerRun;
        }

        if (totalVoltPerSec < 0.01) {
            return "0 volt/sec";
        }
        return FormatUtils.formatBigNumber(BigNumber.fromDouble(totalVoltPerSec)) + "/sec";
    }

    private String formatMultiplier(double value) {
        if (value < 10) {
            return String.format(Locale.US, "%.1fx", value);
        } else if (value < 1_000) {
            return String.format(Locale.US, "%.0fx", value);
        } else if (value < 1_000_000) {
            return String.format(Locale.US, "%,.0fx", value);
        } else {
            return FormatUtils.formatBigNumber(BigNumber.fromDouble(value)) + "x";
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
                () -> refreshStats(ref, store),
                this::stopAutoRefresh,
                "StatsPage"
            );
        }, 500L, 500L, TimeUnit.MILLISECONDS);
    }

    private void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        refreshRequested.set(false);
    }

    private void refreshStats(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!isCurrentPage()) {
            stopAutoRefresh();
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UICommandBuilder commandBuilder = new UICommandBuilder();
        updateAllStats(commandBuilder, playerRef.getUuid());
        if (!isCurrentPage()) {
            return;
        }
        try {
            sendUpdate(commandBuilder, null, false);
        } catch (Exception e) {
            stopAutoRefresh();
        }
    }
}
