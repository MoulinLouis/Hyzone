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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.ChallengeType;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AscendChallengePage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_QUIT = "Quit";
    private static final String BUTTON_START_PREFIX = "Start:";

    // Maps ChallengeType accent color hex to UI group ID for visibility toggling
    private static final Map<String, String> ACCENT_COLOR_MAP = Map.of(
        "#ef4444", "AccentRed",
        "#3b82f6", "AccentBlue",
        "#10b981", "AccentGreen",
        "#f59e0b", "AccentOrange"
    );
    private static final String[] ALL_ACCENT_IDS = {"AccentRed", "AccentBlue", "AccentGreen", "AccentOrange"};

    private final AscendPlayerStore playerStore;
    private final ChallengeManager challengeManager;
    private ScheduledFuture<?> timerTask;

    public AscendChallengePage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore,
                               ChallengeManager challengeManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.challengeManager = challengeManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Challenge.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#QuitButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_QUIT), false);

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();

        boolean inChallenge = challengeManager.isInChallenge(playerId);

        // Show/hide active banner
        if (inChallenge) {
            commandBuilder.set("#ActiveBanner.Visible", true);
            updateActiveTimer(commandBuilder, playerId);
        }

        // Build challenge entries dynamically
        for (ChallengeType type : ChallengeType.values()) {
            commandBuilder.append("#ChallengeCards", "Pages/Ascend_ChallengeEntry.ui");

            // Set challenge info
            commandBuilder.set("#ChallengeName.Text", type.getDisplayName());
            commandBuilder.set("#ChallengeDesc.Text", type.getDescription());

            // Set accent bar color via visibility toggle (dynamic Background doesn't work)
            String accentId = ACCENT_COLOR_MAP.getOrDefault(type.getAccentColor(), "AccentRed");
            for (String id : ALL_ACCENT_IDS) {
                commandBuilder.set("#" + id + ".Visible", id.equals(accentId));
            }

            // Malus description
            StringBuilder malusText = new StringBuilder("Malus: ");
            for (AscendConstants.SummitCategory blocked : type.getBlockedSummitCategories()) {
                malusText.append(blocked.getDisplayName()).append(" locked to base");
            }
            commandBuilder.set("#MalusLabel.Text", malusText.toString());

            // Load record
            ChallengeManager.ChallengeRecord record = challengeManager.getChallengeRecord(playerId, type);
            if (record.completions() > 0) {
                String bestStr = record.bestTimeMs() != null ? FormatUtils.formatDurationLong(record.bestTimeMs()) : "N/A";
                commandBuilder.set("#StatusLabel.Text", "Best: " + bestStr + " | x" + record.completions() + " completions");
            } else {
                commandBuilder.set("#StatusLabel.Text", "Not attempted");
            }

            // Button state
            ChallengeType activeType = challengeManager.getActiveChallenge(playerId);
            if (activeType == type) {
                commandBuilder.set("#StartButton.Visible", false);
                commandBuilder.set("#InProgressLabel.Visible", true);
            } else if (inChallenge) {
                // Another challenge is active, disable start
                commandBuilder.set("#StartButton.Visible", false);
            }

            // Bind start button
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#StartButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_START_PREFIX + type.name()), false);
        }

        // Start timer refresh if in challenge
        if (inChallenge) {
            startTimerRefresh(playerId);
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

        if (BUTTON_QUIT.equals(data.getButton())) {
            handleQuit(ref, store);
            return;
        }

        if (data.getButton().startsWith(BUTTON_START_PREFIX)) {
            String typeName = data.getButton().substring(BUTTON_START_PREFIX.length());
            try {
                ChallengeType type = ChallengeType.valueOf(typeName);
                handleStart(ref, store, type);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void handleStart(Ref<EntityStore> ref, Store<EntityStore> store, ChallengeType type) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();

        if (challengeManager.isInChallenge(playerId)) {
            player.sendMessage(Message.raw("[Challenge] You already have an active challenge. Quit first.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        List<String> mapsWithRunners = challengeManager.startChallenge(playerId, type);
        if (mapsWithRunners == null) {
            player.sendMessage(Message.raw("[Challenge] Failed to start challenge.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        // Despawn runners
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            RobotManager robotManager = plugin.getRobotManager();
            if (robotManager != null) {
                for (String mapId : mapsWithRunners) {
                    robotManager.despawnRobot(playerId, mapId);
                }
            }
        }

        player.sendMessage(Message.raw("[Challenge] " + type.getDisplayName() + " started! All progress has been reset.")
            .color(SystemMessageUtils.SUCCESS));
        player.sendMessage(Message.raw("[Challenge] Reach " + io.hyvexa.common.util.FormatUtils.formatBigNumber(AscendConstants.ASCENSION_VEXA_THRESHOLD)
            + " vexa to complete the challenge.")
            .color(SystemMessageUtils.SECONDARY));

        this.close();
    }

    private void handleQuit(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();

        if (!challengeManager.isInChallenge(playerId)) {
            return;
        }

        challengeManager.quitChallenge(playerId);

        // Robots will auto-respawn via RobotManager's 1s refresh tick since hasRobot is restored

        player.sendMessage(Message.raw("[Challenge] Challenge abandoned. Your progress has been restored.")
            .color(SystemMessageUtils.SECONDARY));

        this.close();
    }

    private void updateActiveTimer(UICommandBuilder commandBuilder, UUID playerId) {
        ChallengeType type = challengeManager.getActiveChallenge(playerId);
        long startedAt = challengeManager.getChallengeStartedAtMs(playerId);
        if (type == null || startedAt == 0) {
            return;
        }
        long elapsed = System.currentTimeMillis() - startedAt;
        String timeStr = FormatUtils.formatDurationLong(elapsed);
        commandBuilder.set("#ActiveLabel.Text", "In Progress: " + type.getDisplayName() + " - " + timeStr);
    }

    private void startTimerRefresh(UUID playerId) {
        stopTimerRefresh();
        timerTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            if (!isCurrentPage()) {
                stopTimerRefresh();
                return;
            }
            UICommandBuilder updateBuilder = new UICommandBuilder();
            updateActiveTimer(updateBuilder, playerId);
            sendUpdate(updateBuilder, null, false);
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopTimerRefresh() {
        if (timerTask != null) {
            timerTask.cancel(false);
            timerTask = null;
        }
    }

    @Override
    public void shutdown() {
        stopTimerRefresh();
        super.shutdown();
    }
}
