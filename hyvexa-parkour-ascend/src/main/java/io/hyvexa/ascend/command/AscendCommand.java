package io.hyvexa.ascend.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.ghost.GhostStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.ui.AscendLeaderboardPage;
import io.hyvexa.ascend.ui.AscendMapSelectPage;
import io.hyvexa.ascend.ui.AscensionPage;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.ascend.ui.ElevationPage;
import io.hyvexa.ascend.ui.AutomationPage;
import io.hyvexa.ascend.ui.SkillTreePage;
import io.hyvexa.ascend.ui.StatsPage;
import io.hyvexa.ascend.ui.SummitPage;
import io.hyvexa.ascend.util.AscendModeGate;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AscendCommand extends AbstractAsyncCommand {

    // Track active pages per player to ensure proper cleanup when switching pages
    private static final Map<UUID, BaseAscendPage> activePages = new ConcurrentHashMap<>();

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
            switch (subCommand) {
                case "stats" -> openStatsPage(player, playerRef, ref, store);
                case "elevate" -> openElevationPage(player, playerRef, ref, store);
                case "summit" -> openSummitPage(player, playerRef, ref, store, args);
                case "ascension", "ascend" -> openAscensionPage(player, playerRef, ref, store);
                case "skills" -> openSkillTreePage(player, playerRef, ref, store);
                case "automation" -> openAutomationPage(player, playerRef, ref, store);
                case "achievements" -> showAchievements(player, playerRef);
                case "title" -> handleTitle(player, playerRef, args);
                case "leaderboard" -> openLeaderboardPage(player, playerRef, ref, store);
                default -> ctx.sendMessage(Message.raw("Unknown subcommand. Use: /ascend, /ascend stats, /ascend elevate, /ascend summit, /ascend ascension, /ascend skills, /ascend automation, /ascend achievements, /ascend title, /ascend leaderboard"));
            }
        }, world);
    }

    private void openStatsPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getPlayerStore() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        UUID playerId = playerRef.getUuid();
        closeActivePage(playerId);
        StatsPage page = new StatsPage(playerRef, plugin.getPlayerStore(), plugin.getMapStore(), plugin.getGhostStore());
        registerActivePage(playerId, page);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void openElevationPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getPlayerStore() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        UUID playerId = playerRef.getUuid();
        closeActivePage(playerId);
        ElevationPage page = new ElevationPage(playerRef, plugin.getPlayerStore());
        registerActivePage(playerId, page);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void openSummitPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, String[] args) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getSummitManager() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        UUID playerId = playerRef.getUuid();
        closeActivePage(playerId);
        SummitPage page = new SummitPage(playerRef, plugin.getPlayerStore(), plugin.getSummitManager());
        registerActivePage(playerId, page);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void openAscensionPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getAscensionManager() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        UUID playerId = playerRef.getUuid();
        closeActivePage(playerId);
        AscensionPage page = new AscensionPage(playerRef, plugin.getPlayerStore(), plugin.getAscensionManager());
        registerActivePage(playerId, page);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void openSkillTreePage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getAscensionManager() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        UUID playerId = playerRef.getUuid();
        closeActivePage(playerId);
        SkillTreePage page = new SkillTreePage(playerRef, plugin.getPlayerStore(), plugin.getAscensionManager());
        registerActivePage(playerId, page);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void openAutomationPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getAscensionManager() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        UUID playerId = playerRef.getUuid();
        closeActivePage(playerId);
        AutomationPage page = new AutomationPage(playerRef, plugin.getPlayerStore(), plugin.getAscensionManager());
        registerActivePage(playerId, page);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void openLeaderboardPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getPlayerStore() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        UUID playerId = playerRef.getUuid();
        closeActivePage(playerId);
        AscendLeaderboardPage page = new AscendLeaderboardPage(playerRef, plugin.getPlayerStore());
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void showAchievements(Player player, PlayerRef playerRef) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getAchievementManager() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }

        AchievementManager achievementManager = plugin.getAchievementManager();
        var unlocked = plugin.getPlayerStore().getUnlockedAchievements(playerRef.getUuid());

        player.sendMessage(Message.raw("[Achievements] " + unlocked.size() + "/" + AscendConstants.AchievementType.values().length + " unlocked")
            .color(SystemMessageUtils.PRIMARY_TEXT));

        for (var achievement : AscendConstants.AchievementType.values()) {
            var progress = achievementManager.getProgress(playerRef.getUuid(), achievement);
            String status = progress.unlocked() ? "[X]" : "[ ]";
            String progressText = progress.unlocked() ? "" : " (" + progress.current() + "/" + progress.required() + ")";
            player.sendMessage(Message.raw("  " + status + " " + achievement.getName() + ": \"" + achievement.getTitle() + "\"" + progressText)
                .color(progress.unlocked() ? SystemMessageUtils.SUCCESS : SystemMessageUtils.SECONDARY));
        }
    }

    private void handleTitle(Player player, PlayerRef playerRef, String[] args) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getAchievementManager() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }

        AchievementManager achievementManager = plugin.getAchievementManager();

        // Show available titles if no argument
        if (args.length < 2) {
            var titles = achievementManager.getAvailableTitles(playerRef.getUuid());
            String currentTitle = achievementManager.getActiveTitle(playerRef.getUuid());

            player.sendMessage(Message.raw("[Title] Current: " + (currentTitle != null ? currentTitle : "None"))
                .color(SystemMessageUtils.PRIMARY_TEXT));

            if (titles.isEmpty()) {
                player.sendMessage(Message.raw("[Title] No titles unlocked. Complete achievements to earn titles!")
                    .color(SystemMessageUtils.SECONDARY));
            } else {
                player.sendMessage(Message.raw("[Title] Available: " + String.join(", ", titles))
                    .color(SystemMessageUtils.SECONDARY));
            }
            player.sendMessage(Message.raw("[Title] Use: /ascend title <name> or /ascend title clear")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        // Clear title
        if (args[1].equalsIgnoreCase("clear") || args[1].equalsIgnoreCase("none")) {
            achievementManager.setActiveTitle(playerRef.getUuid(), null);
            player.sendMessage(Message.raw("[Title] Title cleared.")
                .color(SystemMessageUtils.SUCCESS));
            return;
        }

        // Set title
        String title = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (achievementManager.setActiveTitle(playerRef.getUuid(), title)) {
            player.sendMessage(Message.raw("[Title] Title set to: " + title)
                .color(SystemMessageUtils.SUCCESS));
        } else {
            player.sendMessage(Message.raw("[Title] You haven't unlocked that title.")
                .color(SystemMessageUtils.SECONDARY));
        }
    }

    private void openMapMenu(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        AscendMapStore mapStore = plugin.getMapStore();
        AscendPlayerStore playerStore = plugin.getPlayerStore();
        AscendRunTracker runTracker = plugin.getRunTracker();
        RobotManager robotManager = plugin.getRobotManager();
        GhostStore ghostStore = plugin.getGhostStore();
        if (mapStore == null || playerStore == null || runTracker == null || ghostStore == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        UUID playerId = playerRef.getUuid();
        closeActivePage(playerId);
        AscendMapSelectPage page = new AscendMapSelectPage(playerRef, mapStore, playerStore, runTracker, robotManager, ghostStore);
        registerActivePage(playerId, page);
        player.getPageManager().openCustomPage(ref, store, page);
    }

}
