package io.hyvexa.ascend.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscensionConstants;
import io.hyvexa.ascend.AscendRuntimeConfig;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.interaction.AbstractAscendPageInteraction;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerEventHandler;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.robot.RunnerSpeedCalculator;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.transcendence.TranscendenceManager;
import io.hyvexa.ascend.tutorial.TutorialTriggerService;
import io.hyvexa.core.analytics.PlayerAnalytics;
import io.hyvexa.ascend.ui.AscendLeaderboardPage;
import io.hyvexa.ascend.ui.AscendMapLeaderboardPage;
import io.hyvexa.ascend.ui.AscendChallengePage;
import io.hyvexa.ascend.ui.AscendMapSelectPage;
import io.hyvexa.ascend.ui.AscendTutorialPage;
import io.hyvexa.ascend.ui.AscensionPage;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.ascend.ui.ElevationPage;
import io.hyvexa.ascend.ui.AscendHelpPage;
import io.hyvexa.ascend.ui.AscendMenuNavigator;
import io.hyvexa.ascend.ui.AscendProfilePage;
import io.hyvexa.ascend.ui.AscendSettingsPage;
import io.hyvexa.ascend.ui.AutomationPage;
import io.hyvexa.ascend.ui.SkillTreePage;
import io.hyvexa.ascend.ui.StatsPage;
import io.hyvexa.ascend.ui.SummitPage;
import io.hyvexa.ascend.ui.TranscendencePage;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.core.state.ModeMessages;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AscendCommand extends AbstractAsyncCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Track active pages per player to ensure proper cleanup when switching pages
    private static final Map<UUID, BaseAscendPage> activePages = new ConcurrentHashMap<>();
    private static final String UNKNOWN_SUBCOMMAND_MESSAGE =
            "Unknown subcommand. Use: /ascend, /ascend stats, /ascend ascension, /ascend automation, "
                    + "/ascend settings, /ascend achievements, /ascend profile, /ascend leaderboard, "
                    + "/ascend maplb, /ascend transcend, /ascend help";

    // NPC-gated commands: only executable with the correct token (passed by NPC dialog buttons)
    private static final String LEGACY_NPC_TOKEN = "hx7Kq9mW";
    private static final Set<String> NPC_GATED = Set.of("elevate", "summit", "skills");
    private final AscendPlayerStore playerStore;
    private final AscendMapStore mapStore;
    private final GhostStore ghostStore;
    private final AscendRunTracker runTracker;
    private final AscendHudManager hudManager;
    private final RobotManager robotManager;
    private final AchievementManager achievementManager;
    private final AscensionManager ascensionManager;
    private final ChallengeManager challengeManager;
    private final SummitManager summitManager;
    private final TranscendenceManager transcendenceManager;
    private final TutorialTriggerService tutorialTriggerService;
    private final RunnerSpeedCalculator runnerSpeedCalculator;
    private final PlayerAnalytics analytics;
    private final String npcToken;
    private volatile AscendPlayerEventHandler eventHandler;

    /**
     * Close any existing page for the player before opening a new one.
     * This ensures auto-refresh tasks are stopped properly.
     */
    private static void closeActivePage(UUID playerId) {
        BaseAscendPage oldPage = activePages.remove(playerId);
        if (oldPage != null) {
            oldPage.shutdown();
        }
    }

    /**
     * Register a new page as active for the player.
     */
    private static void registerActivePage(UUID playerId, BaseAscendPage page) {
        activePages.put(playerId, page);
    }

    /**
     * Called when a player disconnects to clean up their page.
     */
    public static void onPlayerDisconnect(UUID playerId) {
        closeActivePage(playerId);
    }

    /**
     * Force-close the player's active ascend page from an external trigger
     * (e.g., auto-elevation or auto-summit resetting progress).
     */
    public static void forceCloseActivePage(UUID playerId) {
        BaseAscendPage page = activePages.remove(playerId);
        if (page != null) {
            page.shutdown();
            page.forceClose();
        }
    }

    public AscendCommand(
            AscendPlayerStore playerStore,
            AscendMapStore mapStore,
            GhostStore ghostStore,
            AscendRunTracker runTracker,
            AscendHudManager hudManager,
            RobotManager robotManager,
            AchievementManager achievementManager,
            AscensionManager ascensionManager,
            ChallengeManager challengeManager,
            SummitManager summitManager,
            TranscendenceManager transcendenceManager,
            TutorialTriggerService tutorialTriggerService,
            RunnerSpeedCalculator runnerSpeedCalculator,
            PlayerAnalytics analytics,
            AscendRuntimeConfig runtimeConfig) {
        super("ascend", "Open the Ascend menu");
        this.playerStore = playerStore;
        this.mapStore = mapStore;
        this.ghostStore = ghostStore;
        this.runTracker = runTracker;
        this.hudManager = hudManager;
        this.robotManager = robotManager;
        this.achievementManager = achievementManager;
        this.ascensionManager = ascensionManager;
        this.challengeManager = challengeManager;
        this.summitManager = summitManager;
        this.transcendenceManager = transcendenceManager;
        this.tutorialTriggerService = tutorialTriggerService;
        this.runnerSpeedCalculator = runnerSpeedCalculator;
        this.analytics = analytics;
        this.npcToken = resolveNpcToken(runtimeConfig);
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    public void setEventHandler(AscendPlayerEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    private static String resolveNpcToken(AscendRuntimeConfig runtimeConfig) {
        if (runtimeConfig == null) {
            return LEGACY_NPC_TOKEN;
        }
        String token = runtimeConfig.getNpcCommandToken();
        if (token == null || token.isBlank()) {
            return LEGACY_NPC_TOKEN;
        }
        return token;
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            ctx.sendMessage(Message.raw("This command can only be used by players."));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Player not in world."));
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        if (store.getExternalData() == null) return CompletableFuture.completedFuture(null);
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            if (ModeGate.denyIfNot(ctx, world, WorldConstants.WORLD_ASCEND, ModeMessages.MESSAGE_ENTER_ASCEND)) {
                return;
            }

            String[] args = CommandUtils.tokenize(ctx);

            if (args.length == 0) {
                openMapMenu(player, playerRef, ref, store);
                return;
            }

            String subCommand = args[0].toLowerCase();

            // NPC-gated commands require a secret token as second arg
            if (NPC_GATED.contains(subCommand)) {
                String expectedToken = npcToken;
                if (args.length < 2 || !args[1].equals(expectedToken)) {
                    ctx.sendMessage(Message.raw(UNKNOWN_SUBCOMMAND_MESSAGE));
                    return;
                }
                if (LEGACY_NPC_TOKEN.equals(expectedToken)) {
                    LOGGER.atInfo().log("AscendCommand used legacy NPC token for subcommand '" + subCommand + "' by " + playerRef.getUuid());
                }
            }

            switch (subCommand) {
                case "stats" -> openStatsPage(player, playerRef, ref, store);
                case "elevate" -> openElevationPage(player, playerRef, ref, store);
                case "summit" -> openSummitPage(player, playerRef, ref, store, args);
                case "ascension", "ascend" -> openAscensionPage(player, playerRef, ref, store);
                case "skills" -> openSkillTreePage(player, playerRef, ref, store);
                case "automation" -> openAutomationPage(player, playerRef, ref, store);
                case "achievements" -> showAchievements(player, playerRef);
                case "leaderboard" -> openLeaderboardPage(player, playerRef, ref, store);
                case "maplb" -> openMapLeaderboardPage(player, playerRef, ref, store);
                case "settings" -> openSettingsPage(player, playerRef, ref, store);
                case "profile" -> openProfilePage(player, playerRef, ref, store);
                case "help" -> openHelpPage(player, playerRef, ref, store);
                case "challenge" -> openChallengePage(player, playerRef, ref, store);
                case "transcend", "transcendence" -> openTranscendencePage(player, playerRef, ref, store);
                default -> ctx.sendMessage(Message.raw(UNKNOWN_SUBCOMMAND_MESSAGE));
            }
        }, world);
    }

    private void openTrackedPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store,
                                 InteractiveCustomUIPage<?> page) {
        openPage(player, playerRef.getUuid(), ref, store, page, true);
    }

    private void openUntrackedPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store,
                                   InteractiveCustomUIPage<?> page) {
        openPage(player, playerRef.getUuid(), ref, store, page, false);
    }

    private void openPage(Player player, UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store,
                          InteractiveCustomUIPage<?> page, boolean trackActivePage) {
        closeActivePage(playerId);
        if (trackActivePage && page instanceof BaseAscendPage basePage) {
            registerActivePage(playerId, basePage);
        }
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void openStatsPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (playerStore == null) return;
        StatsPage page = createMenuNavigator().createStatsPage(playerRef);
        openTrackedPage(player, playerRef, ref, store, page);
    }

    /**
     * If the player hasn't seen the given tutorial, show it and return true.
     * Otherwise return false so the caller can proceed with the real page.
     */
    private boolean showTutorialIfNeeded(Player player, PlayerRef playerRef, Ref<EntityStore> ref,
                                         Store<EntityStore> store,
                                         int tutorialKey, AscendTutorialPage.Tutorial tutorial) {
        UUID playerId = playerRef.getUuid();
        if (!playerStore.gameplay().hasSeenTutorial(playerId, tutorialKey)) {
            playerStore.gameplay().markTutorialSeen(playerId, tutorialKey);
            player.getPageManager().openCustomPage(ref, store,
                new AscendTutorialPage(playerRef, tutorial));
            return true;
        }
        return false;
    }

    private void openElevationPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (playerStore == null) return;
        if (showTutorialIfNeeded(player, playerRef, ref, store,
                TutorialTriggerService.ELEVATION, AscendTutorialPage.Tutorial.ELEVATION)) {
            return;
        }
        ElevationPage page = new ElevationPage(
            playerRef,
            playerStore,
            mapStore,
            challengeManager,
            robotManager,
            achievementManager
        );
        openTrackedPage(player, playerRef, ref, store, page);
        player.sendMessage(Message.raw("[Ascend] Having trouble elevating? Contact Playfade on Discord.")
            .color(SystemMessageUtils.SECONDARY));
    }

    private void openSummitPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, String[] args) {
        if (summitManager == null) return;
        if (showTutorialIfNeeded(player, playerRef, ref, store,
                TutorialTriggerService.SUMMIT, AscendTutorialPage.Tutorial.SUMMIT)) {
            return;
        }
        SummitPage page = new SummitPage(
            playerRef,
            playerStore,
            summitManager,
            challengeManager,
            robotManager,
            achievementManager
        );
        openTrackedPage(player, playerRef, ref, store, page);
        player.sendMessage(Message.raw("[Ascend] Having trouble summiting? Contact Playfade on Discord.")
            .color(SystemMessageUtils.SECONDARY));
    }

    private void openAscensionPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ascensionManager == null) return;
        if (showTutorialIfNeeded(player, playerRef, ref, store,
                TutorialTriggerService.ASCENSION, AscendTutorialPage.Tutorial.ASCENSION)) {
            return;
        }
        AscensionPage page = new AscensionPage(
            playerRef,
            playerStore,
            ascensionManager,
            challengeManager,
            robotManager,
            achievementManager
        );
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openSkillTreePage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ascensionManager == null) return;
        SkillTreePage page = new SkillTreePage(playerRef, playerStore, ascensionManager);
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openAutomationPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ascensionManager == null) return;
        AutomationPage page = new AutomationPage(playerRef, playerStore, ascensionManager);
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openProfilePage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (playerStore == null || robotManager == null) return;
        AscendProfilePage page = createMenuNavigator().createProfilePage(playerRef);
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openSettingsPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (playerStore == null || robotManager == null) return;
        AscendSettingsPage page = createMenuNavigator().createSettingsPage(playerRef);
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private AscendMenuNavigator createMenuNavigator() {
        return new AscendMenuNavigator(
            playerStore,
            mapStore,
            ghostStore,
            runnerSpeedCalculator,
            achievementManager,
            robotManager,
            hudManager
        );
    }

    private void openChallengePage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (challengeManager == null || ascensionManager == null) return;
        if (!ascensionManager.hasAscensionChallenges(playerRef.getUuid())) {
            player.sendMessage(Message.raw("[Ascend] You need the Ascension Challenges skill to access this.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }
        if (showTutorialIfNeeded(player, playerRef, ref, store,
                TutorialTriggerService.CHALLENGES, AscendTutorialPage.Tutorial.CHALLENGES)) {
            return;
        }
        AscendChallengePage page = new AscendChallengePage(
            playerRef,
            playerStore,
            challengeManager,
            robotManager,
            eventHandler
        );
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openTranscendencePage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (transcendenceManager == null) return;
        TranscendencePage page = new TranscendencePage(
            playerRef,
            playerStore,
            transcendenceManager,
            robotManager,
            achievementManager
        );
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openHelpPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        AscendHelpPage page = new AscendHelpPage(playerRef);
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openLeaderboardPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (playerStore == null) return;
        AscendLeaderboardPage page = new AscendLeaderboardPage(playerRef, playerStore);
        openUntrackedPage(player, playerRef, ref, store, page);
    }

    private void openMapLeaderboardPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (playerStore == null || mapStore == null) return;
        AscendMapLeaderboardPage page = new AscendMapLeaderboardPage(playerRef, playerStore,
            mapStore, runTracker, robotManager, ghostStore,
            ascensionManager, challengeManager, summitManager, transcendenceManager,
            achievementManager, tutorialTriggerService, runnerSpeedCalculator,
            analytics, eventHandler);
        openUntrackedPage(player, playerRef, ref, store, page);
    }

    private void showAchievements(Player player, PlayerRef playerRef) {
        if (achievementManager == null) return;

        var unlocked = playerStore.gameplay().getUnlockedAchievements(playerRef.getUuid());

        player.sendMessage(Message.raw("[Achievements] " + unlocked.size() + "/" + AscensionConstants.AchievementType.values().length + " unlocked")
            .color(SystemMessageUtils.PRIMARY_TEXT));

        for (var achievement : AscensionConstants.AchievementType.values()) {
            var progress = achievementManager.getProgress(playerRef.getUuid(), achievement);
            String status = progress.unlocked() ? "[X]" : "[ ]";
            String progressText = progress.unlocked() ? "" : " (" + progress.current() + "/" + progress.required() + ")";
            player.sendMessage(Message.raw("  " + status + " " + achievement.getName() + progressText)
                .color(progress.unlocked() ? SystemMessageUtils.SUCCESS : SystemMessageUtils.SECONDARY));
        }
    }

    private void openMapMenu(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (mapStore == null || playerStore == null || runTracker == null || ghostStore == null) {
            player.sendMessage(AbstractAscendPageInteraction.LOADING_MESSAGE);
            return;
        }
        AscendMapSelectPage page = new AscendMapSelectPage(playerRef, mapStore, playerStore, runTracker,
            robotManager, ghostStore, ascensionManager, challengeManager,
            summitManager,
            transcendenceManager, achievementManager,
            tutorialTriggerService, runnerSpeedCalculator,
            analytics, eventHandler);
        openTrackedPage(player, playerRef, ref, store, page);
    }

}
