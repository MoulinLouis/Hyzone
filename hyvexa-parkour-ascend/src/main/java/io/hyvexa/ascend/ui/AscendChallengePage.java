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
import io.hyvexa.ascend.data.AscendPlayerProgress;
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
    private static final String BUTTON_LEADERBOARD = "Leaderboard";
    private static final String BUTTON_START_PREFIX = "Start:";
    private static final String BUTTON_BREAK = "BreakToggle";

    // Maps ChallengeType accent color hex to UI group ID for visibility toggling
    private static final Map<String, String> ACCENT_COLOR_MAP = Map.of(
        "#ef4444", "AccentRed",
        "#3b82f6", "AccentBlue",
        "#10b981", "AccentGreen",
        "#f59e0b", "AccentOrange",
        "#8b5cf6", "AccentViolet",
        "#ec4899", "AccentPink"
    );
    private static final String[] ALL_ACCENT_IDS = {"AccentRed", "AccentBlue", "AccentGreen", "AccentOrange", "AccentViolet", "AccentPink"};

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
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LeaderboardButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_LEADERBOARD), false);
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
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);

        // AP Multiplier display
        int apMultiplier = 1 + (progress != null ? progress.getCompletedChallengeCount() : 0);
        commandBuilder.set("#ApMultiplier.Text", "AP Multiplier: x" + apMultiplier);
        ChallengeType activeType = challengeManager.getActiveChallenge(playerId);
        ChallengeType[] types = ChallengeType.values();

        for (int i = 0; i < types.length; i++) {
            ChallengeType type = types[i];
            String p = "#ChallengeCards[" + i + "] ";
            commandBuilder.append("#ChallengeCards", "Pages/Ascend_ChallengeEntry.ui");

            // Set challenge info
            commandBuilder.set(p + "#ChallengeName.Text", type.getDisplayName());

            // Set accent bar color via visibility toggle (dynamic Background doesn't work)
            String accentId = ACCENT_COLOR_MAP.getOrDefault(type.getAccentColor(), "AccentRed");
            for (String id : ALL_ACCENT_IDS) {
                commandBuilder.set(p + "#" + id + ".Visible", id.equals(accentId));
            }

            // Malus description
            commandBuilder.set(p + "#MalusLabel.Text", buildMalusDescription(type));

            // Reward description
            commandBuilder.set(p + "#RewardLabel.Text", buildRewardDescription(type));

            // Reward status (claimed or not)
            boolean rewardClaimed = progress != null && progress.hasChallengeReward(type);
            if (rewardClaimed) {
                commandBuilder.set(p + "#RewardStatus.Text", "Reward: Claimed");
                commandBuilder.set(p + "#RewardStatus.Style.TextColor", "#10b981");
            } else {
                commandBuilder.set(p + "#RewardStatus.Text", "Reward: Not yet earned");
                commandBuilder.set(p + "#RewardStatus.Style.TextColor", "#9fb0ba");
            }

            // Load record
            ChallengeManager.ChallengeRecord record = challengeManager.getChallengeRecord(playerId, type);
            if (record.completions() > 0) {
                String bestStr = record.bestTimeMs() != null ? FormatUtils.formatDurationLong(record.bestTimeMs()) : "N/A";
                commandBuilder.set(p + "#StatusLabel.Text", "Best: " + bestStr + " | x" + record.completions() + " completions");
            } else {
                commandBuilder.set(p + "#StatusLabel.Text", "Not attempted");
            }

            // Button state
            boolean challengeUnlocked = challengeManager.isChallengeUnlocked(playerId, type);
            if (!challengeUnlocked) {
                commandBuilder.set(p + "#StartButtonWrap.Visible", false);
                commandBuilder.set(p + "#LockedLabel.Visible", true);
            } else if (activeType == type) {
                commandBuilder.set(p + "#StartButtonWrap.Visible", false);
                commandBuilder.set(p + "#InProgressLabel.Visible", true);
            } else if (inChallenge) {
                commandBuilder.set(p + "#StartButtonWrap.Visible", false);
            }

            // Bind start button
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                p + "#StartButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_START_PREFIX + type.name()), false);
        }

        // Break Ascension card
        buildBreakCard(commandBuilder, eventBuilder, progress);

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

        if (BUTTON_LEADERBOARD.equals(data.getButton())) {
            handleLeaderboard(ref, store);
            return;
        }

        if (BUTTON_QUIT.equals(data.getButton())) {
            handleQuit(ref, store);
            return;
        }

        if (BUTTON_BREAK.equals(data.getButton())) {
            handleBreakToggle(ref, store);
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

        if (!challengeManager.isChallengeUnlocked(playerId, type)) {
            player.sendMessage(Message.raw("[Challenge] Complete the previous challenge first.")
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

    private void handleLeaderboard(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
            new ChallengeLeaderboardPage(playerRef, playerStore, challengeManager));
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

    private void buildBreakCard(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder,
                                AscendPlayerProgress progress) {
        commandBuilder.set("#BreakCard.Visible", true);

        if (progress != null && progress.hasAllChallengeRewards()) {
            // Unlocked — show toggle button
            commandBuilder.set("#BreakButton.Visible", true);
            commandBuilder.set("#BreakLocked.Visible", false);

            boolean active = progress.isBreakAscensionEnabled();
            commandBuilder.set("#BreakButton.Text", active ? "Disable" : "Break");
            commandBuilder.set("#BreakStatus.Text", active ? "ACTIVE" : "OFF");
            commandBuilder.set("#BreakStatus.Style.TextColor", active ? "#a855f7" : "#64748b");

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BreakButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BREAK), false);
        } else {
            // Locked — show progress bar segments
            commandBuilder.set("#BreakButton.Visible", false);
            commandBuilder.set("#BreakLocked.Visible", true);

            ChallengeType[] types = ChallengeType.values();
            for (int i = 0; i < types.length; i++) {
                boolean completed = progress != null && progress.hasChallengeReward(types[i]);
                commandBuilder.set("#Bar" + (i + 1) + ".Visible", completed);
            }
        }
    }

    private void handleBreakToggle(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();

        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null || !progress.hasAllChallengeRewards()) {
            player.sendMessage(Message.raw("[Challenge] Complete all challenges first.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        boolean newState = !progress.isBreakAscensionEnabled();
        playerStore.setBreakAscensionEnabled(playerId, newState);

        if (newState) {
            player.sendMessage(Message.raw("[Challenge] Break Ascension enabled. Auto-ascension at 1Dc is now suppressed.")
                .color(SystemMessageUtils.SUCCESS));
        } else {
            player.sendMessage(Message.raw("[Challenge] Break Ascension disabled.")
                .color(SystemMessageUtils.SECONDARY));
        }

        // Live UI update
        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateBuilder.set("#BreakButton.Text", newState ? "Disable" : "Break");
        updateBuilder.set("#BreakStatus.Text", newState ? "ACTIVE" : "OFF");
        updateBuilder.set("#BreakStatus.Style.TextColor", newState ? "#a855f7" : "#64748b");
        sendUpdate(updateBuilder, null, false);
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

    private String buildMalusDescription(ChallengeType type) {
        List<String> parts = new java.util.ArrayList<>();

        if (!type.getBlockedMapDisplayOrders().isEmpty()) {
            if (type.getBlockedMapDisplayOrders().size() > 1) {
                parts.add("Maps 4 & 5 locked");
            } else {
                parts.add("Map 5 locked");
            }
        }
        if (type.getSpeedDivisor() > 1.0) {
            parts.add("Runner Speed /" + formatDivisor(type.getSpeedDivisor()));
        }
        if (type.getMultiplierGainDivisor() > 1.0) {
            parts.add("Multiplier Gain /" + formatDivisor(type.getMultiplierGainDivisor()));
        }
        if (type.getEvolutionPowerDivisor() > 1.0) {
            parts.add("Evolution Power /" + formatDivisor(type.getEvolutionPowerDivisor()));
        }

        if (parts.isEmpty()) return "Malus: None";
        return "Malus: " + String.join(" + ", parts);
    }

    private static String formatDivisor(double divisor) {
        if (divisor == (long) divisor) {
            return String.valueOf((long) divisor);
        }
        return String.valueOf(divisor);
    }

    private String buildRewardDescription(ChallengeType type) {
        return "Reward: " + switch (type) {
            case CHALLENGE_1 -> "Map 5 base x1.5";
            case CHALLENGE_2 -> "Runner Speed +10%";
            case CHALLENGE_3 -> "Multiplier Gain +10%";
            case CHALLENGE_4 -> "Evolution Power +0.5";
            case CHALLENGE_5 -> "Speed +5% + Mult Gain +5%";
            case CHALLENGE_6 -> "Speed/Mult/Evo +5%/+5%/+0.25";
            case CHALLENGE_7 -> "Map 4&5 base x1.5";
        };
    }
}
