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
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.ui.AscendMapSelectPage;
import io.hyvexa.ascend.ui.AscensionPage;
import io.hyvexa.ascend.ui.ElevationPage;
import io.hyvexa.ascend.ui.SummitPage;
import io.hyvexa.ascend.util.AscendModeGate;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AscendCommand extends AbstractAsyncCommand {

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
                case "stats" -> showStatus(player, playerRef);
                case "elevate" -> openElevationPage(player, playerRef, ref, store);
                case "summit" -> openSummitPage(player, playerRef, ref, store, args);
                case "ascension", "ascend" -> openAscensionPage(player, playerRef, ref, store);
                case "skills" -> showSkillTree(player, playerRef);
                case "achievements" -> showAchievements(player, playerRef);
                case "title" -> handleTitle(player, playerRef, args);
                default -> ctx.sendMessage(Message.raw("Unknown subcommand. Use: /ascend, /ascend stats, /ascend elevate, /ascend summit, /ascend ascension, /ascend skills, /ascend achievements, /ascend title"));
            }
        }, world);
    }

    private void showStatus(Player player, PlayerRef playerRef) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getPlayerStore() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        AscendPlayerStore playerStore = plugin.getPlayerStore();
        UUID playerId = playerRef.getUuid();
        long coins = playerStore.getCoins(playerId);
        AscendMapStore mapStore = plugin.getMapStore();
        List<AscendMap> maps = mapStore != null ? mapStore.listMapsSorted() : List.of();
        long product = playerStore.getMultiplierProduct(playerId, maps, AscendConstants.MULTIPLIER_SLOTS);
        int elevationMultiplier = playerStore.getElevationMultiplier(playerId);
        double[] digits = playerStore.getMultiplierDisplayValues(playerId, maps, AscendConstants.MULTIPLIER_SLOTS);
        StringBuilder digitsText = new StringBuilder();
        for (int i = 0; i < digits.length; i++) {
            if (i > 0) {
                digitsText.append('x');
            }
            digitsText.append(String.format(Locale.US, "%.2f", Math.max(1.0, digits[i])));
        }

        player.sendMessage(Message.raw("[Ascend] Coins: " + formatLargeNumber(coins) + " | Product: " + product
            + " | Elevation: x" + elevationMultiplier)
            .color(SystemMessageUtils.PRIMARY_TEXT));
        player.sendMessage(Message.raw("[Ascend] Digits: " + digitsText)
            .color(SystemMessageUtils.SECONDARY));

        // Show Summit levels
        Map<SummitCategory, Integer> summitLevels = playerStore.getSummitLevels(playerId);
        StringBuilder summitText = new StringBuilder("[Summit]");
        for (SummitCategory cat : SummitCategory.values()) {
            int level = summitLevels.getOrDefault(cat, 0);
            if (level > 0) {
                summitText.append(" ").append(cat.getDisplayName()).append(": Lv.").append(level);
            }
        }
        if (summitText.length() > 8) {
            player.sendMessage(Message.raw(summitText.toString())
                .color(SystemMessageUtils.SECONDARY));
        }

        // Show Ascension info
        int ascensionCount = playerStore.getAscensionCount(playerId);
        int skillPoints = playerStore.getSkillTreePoints(playerId);
        int availablePoints = playerStore.getAvailableSkillPoints(playerId);
        if (ascensionCount > 0 || skillPoints > 0) {
            player.sendMessage(Message.raw("[Ascension] Count: " + ascensionCount
                + " | Skill Points: " + availablePoints + "/" + skillPoints)
                .color(SystemMessageUtils.SECONDARY));
        }

        // Show lifetime stats
        long totalEarned = playerStore.getTotalCoinsEarned(playerId);
        int totalRuns = playerStore.getTotalManualRuns(playerId);
        player.sendMessage(Message.raw("[Stats] Total Earned: " + formatLargeNumber(totalEarned)
            + " | Manual Runs: " + totalRuns)
            .color(SystemMessageUtils.SECONDARY));
    }

    private void openElevationPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getPlayerStore() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
            new ElevationPage(playerRef, plugin.getPlayerStore()));
    }

    private void openSummitPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, String[] args) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getSummitManager() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
            new SummitPage(playerRef, plugin.getPlayerStore(), plugin.getSummitManager()));
    }

    private void openAscensionPage(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getAscensionManager() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
            new AscensionPage(playerRef, plugin.getPlayerStore(), plugin.getAscensionManager()));
    }

    private void showSkillTree(Player player, PlayerRef playerRef) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getAscensionManager() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }

        AscensionManager ascensionManager = plugin.getAscensionManager();
        var summary = ascensionManager.getSkillTreeSummary(playerRef.getUuid());

        player.sendMessage(Message.raw("[Skill Tree] Points: " + summary.availablePoints() + " available / "
            + summary.totalPoints() + " total")
            .color(SystemMessageUtils.PRIMARY_TEXT));

        if (summary.unlockedNodes().isEmpty()) {
            player.sendMessage(Message.raw("[Skill Tree] No skills unlocked yet. Ascend to earn points!")
                .color(SystemMessageUtils.SECONDARY));
        } else {
            player.sendMessage(Message.raw("[Skill Tree] Unlocked:")
                .color(SystemMessageUtils.SECONDARY));
            for (var node : summary.unlockedNodes()) {
                player.sendMessage(Message.raw("  - " + node.getName() + ": " + node.getDescription())
                    .color(SystemMessageUtils.SECONDARY));
            }
        }

        // Show available nodes to unlock
        if (summary.availablePoints() > 0) {
            player.sendMessage(Message.raw("[Skill Tree] Available to unlock:")
                .color(SystemMessageUtils.SECONDARY));
            for (var node : AscendConstants.SkillTreeNode.values()) {
                if (ascensionManager.canUnlockSkillNode(playerRef.getUuid(), node)) {
                    player.sendMessage(Message.raw("  * " + node.getName() + " (" + node.getPath().name() + ")")
                        .color(SystemMessageUtils.SECONDARY));
                }
            }
        }
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

    private static String formatLargeNumber(long number) {
        if (number >= 1_000_000_000_000L) {
            return String.format(Locale.US, "%.2fT", number / 1_000_000_000_000.0);
        } else if (number >= 1_000_000_000L) {
            return String.format(Locale.US, "%.2fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000L) {
            return String.format(Locale.US, "%.2fM", number / 1_000_000.0);
        } else if (number >= 1_000L) {
            return String.format(Locale.US, "%.2fK", number / 1_000.0);
        }
        return String.valueOf(number);
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
        if (mapStore == null || playerStore == null || runTracker == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
            new AscendMapSelectPage(playerRef, mapStore, playerStore, runTracker, robotManager));
    }

}
