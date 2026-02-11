package io.hyvexa.ascend.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.ui.AscendLeaderboardPage;
import io.hyvexa.ascend.ui.AscendMapLeaderboardPage;
import io.hyvexa.ascend.ui.AscendChallengePage;
import io.hyvexa.ascend.ui.AscendMapSelectPage;
import io.hyvexa.ascend.ui.AscensionPage;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.ascend.ui.ElevationPage;
import io.hyvexa.ascend.ui.AscendHelpPage;
import io.hyvexa.ascend.ui.AscendProfilePage;
import io.hyvexa.ascend.ui.AscendSettingsPage;
import io.hyvexa.ascend.ui.AutomationPage;
import io.hyvexa.ascend.ui.SkillTreePage;
import io.hyvexa.ascend.ui.StatsPage;
import io.hyvexa.ascend.ui.SummitPage;
import io.hyvexa.ascend.util.AscendModeGate;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AscendCommand extends AbstractAsyncCommand {

    // Track active pages per player to ensure proper cleanup when switching pages
    private static final Map<UUID, BaseAscendPage> activePages = new ConcurrentHashMap<>();
    private static final String UNKNOWN_SUBCOMMAND_MESSAGE =
            "Unknown subcommand. Use: /ascend, /ascend stats, /ascend elevate, /ascend summit, "
                    + "/ascend ascension, /ascend skills, /ascend automation, /ascend settings, "
                    + "/ascend achievements, /ascend profile, /ascend leaderboard, /ascend maplb, /ascend help";

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
     * Get the plugin instance, sending an error message to the player if unavailable.
     * Returns null if the plugin or its core systems are not ready.
     */
    private static ParkourAscendPlugin requirePlugin(Player player) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return null;
        }
        return plugin;
    }

    public AscendCommand() {
        super("ascend", "Open the Ascend menu");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
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
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            if (AscendModeGate.denyIfNotAscend(ctx, world)) {
                return;
            }

            String[] args = CommandUtils.getArgs(ctx);

            if (args.length == 0) {
                openMapMenu(player, playerRef, ref, store);
                return;
            }

            String subCommand = args[0].toLowerCase();
            Runnable handler = buildSubCommandHandlers(player, playerRef, ref, store, args).get(subCommand);
            if (handler != null) {
                handler.run();
                return;
            }
            ctx.sendMessage(Message.raw(UNKNOWN_SUBCOMMAND_MESSAGE));
        }, world);
    }

    private Map<String, Runnable> buildSubCommandHandlers(Player player, PlayerRef playerRef,
                                                          Ref<EntityStore> ref, Store<EntityStore> store,
                                                          String[] args) {
        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("stats", () -> openStatsPage(player, playerRef, ref, store));
        handlers.put("elevate", () -> openElevationPage(player, playerRef, ref, store));
        handlers.put("summit", () -> openSummitPage(player, playerRef, ref, store, args));
        handlers.put("ascension", () -> openAscensionPage(player, playerRef, ref, store));
        handlers.put("ascend", () -> openAscensionPage(player, playerRef, ref, store));
        handlers.put("skills", () -> openSkillTreePage(player, playerRef, ref, store));
        handlers.put("automation", () -> openAutomationPage(player, playerRef, ref, store));
        handlers.put("achievements", () -> showAchievements(player, playerRef));
        handlers.put("leaderboard", () -> openLeaderboardPage(player, playerRef, ref, store));
        handlers.put("maplb", () -> openMapLeaderboardPage(player, playerRef, ref, store));
        handlers.put("settings", () -> openSettingsPage(player, playerRef, ref, store));
        handlers.put("profile", () -> openProfilePage(player, playerRef, ref, store));
        handlers.put("help", () -> openHelpPage(player, playerRef, ref, store));
        handlers.put("challenge", () -> openChallengePage(player, playerRef, ref, store));
        return handlers;
    }

    private void openTrackedPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store,
                                 BaseAscendPage page) {
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
        ParkourAscendPlugin plugin = requirePlugin(player);
        if (plugin == null || plugin.getPlayerStore() == null) return;
        StatsPage page = new StatsPage(playerRef, plugin.getPlayerStore(), plugin.getMapStore(), plugin.getGhostStore());
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openElevationPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = requirePlugin(player);
        if (plugin == null || plugin.getPlayerStore() == null) return;
        ElevationPage page = new ElevationPage(playerRef, plugin.getPlayerStore());
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openSummitPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, String[] args) {
        ParkourAscendPlugin plugin = requirePlugin(player);
        if (plugin == null || plugin.getSummitManager() == null) return;
        SummitPage page = new SummitPage(playerRef, plugin.getPlayerStore(), plugin.getSummitManager());
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openAscensionPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = requirePlugin(player);
        if (plugin == null || plugin.getAscensionManager() == null) return;
        AscensionPage page = new AscensionPage(playerRef, plugin.getPlayerStore(), plugin.getAscensionManager());
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openSkillTreePage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = requirePlugin(player);
        if (plugin == null || plugin.getAscensionManager() == null) return;
        SkillTreePage page = new SkillTreePage(playerRef, plugin.getPlayerStore(), plugin.getAscensionManager());
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openAutomationPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = requirePlugin(player);
        if (plugin == null || plugin.getAscensionManager() == null) return;
        AutomationPage page = new AutomationPage(playerRef, plugin.getPlayerStore(), plugin.getAscensionManager());
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openProfilePage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = requirePlugin(player);
        if (plugin == null || plugin.getPlayerStore() == null || plugin.getRobotManager() == null) return;
        AscendProfilePage page = new AscendProfilePage(playerRef, plugin.getPlayerStore(), plugin.getRobotManager());
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openSettingsPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = requirePlugin(player);
        if (plugin == null || plugin.getPlayerStore() == null || plugin.getRobotManager() == null) return;
        AscendSettingsPage page = new AscendSettingsPage(playerRef, plugin.getPlayerStore(), plugin.getRobotManager());
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openChallengePage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = requirePlugin(player);
        if (plugin == null || plugin.getChallengeManager() == null) return;
        if (!plugin.getAscensionManager().hasAscensionChallenges(playerRef.getUuid())) {
            player.sendMessage(Message.raw("[Ascend] You need the Ascension Challenges skill to access this.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }
        AscendChallengePage page = new AscendChallengePage(playerRef, plugin.getPlayerStore(), plugin.getChallengeManager());
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openHelpPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        AscendHelpPage page = new AscendHelpPage(playerRef);
        openTrackedPage(player, playerRef, ref, store, page);
    }

    private void openLeaderboardPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = requirePlugin(player);
        if (plugin == null || plugin.getPlayerStore() == null) return;
        AscendLeaderboardPage page = new AscendLeaderboardPage(playerRef, plugin.getPlayerStore());
        openUntrackedPage(player, playerRef, ref, store, page);
    }

    private void openMapLeaderboardPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = requirePlugin(player);
        if (plugin == null || plugin.getPlayerStore() == null || plugin.getMapStore() == null) return;
        AscendMapLeaderboardPage page = new AscendMapLeaderboardPage(playerRef, plugin.getPlayerStore(), plugin.getMapStore());
        openUntrackedPage(player, playerRef, ref, store, page);
    }

    private void showAchievements(Player player, PlayerRef playerRef) {
        ParkourAscendPlugin plugin = requirePlugin(player);
        if (plugin == null || plugin.getAchievementManager() == null) return;

        AchievementManager achievementManager = plugin.getAchievementManager();
        var unlocked = plugin.getPlayerStore().getUnlockedAchievements(playerRef.getUuid());

        player.sendMessage(Message.raw("[Achievements] " + unlocked.size() + "/" + AscendConstants.AchievementType.values().length + " unlocked")
            .color(SystemMessageUtils.PRIMARY_TEXT));

        for (var achievement : AscendConstants.AchievementType.values()) {
            var progress = achievementManager.getProgress(playerRef.getUuid(), achievement);
            String status = progress.unlocked() ? "[X]" : "[ ]";
            String progressText = progress.unlocked() ? "" : " (" + progress.current() + "/" + progress.required() + ")";
            player.sendMessage(Message.raw("  " + status + " " + achievement.getName() + progressText)
                .color(progress.unlocked() ? SystemMessageUtils.SUCCESS : SystemMessageUtils.SECONDARY));
        }
    }

    private void openMapMenu(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = requirePlugin(player);
        if (plugin == null) return;
        AscendMapStore mapStore = plugin.getMapStore();
        AscendPlayerStore playerStore = plugin.getPlayerStore();
        AscendRunTracker runTracker = plugin.getRunTracker();
        RobotManager robotManager = plugin.getRobotManager();
        GhostStore ghostStore = plugin.getGhostStore();
        if (mapStore == null || playerStore == null || runTracker == null || ghostStore == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        AscendMapSelectPage page = new AscendMapSelectPage(playerRef, mapStore, playerStore, runTracker, robotManager, ghostStore);
        openTrackedPage(player, playerRef, ref, store, page);
    }

}
