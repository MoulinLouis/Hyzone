package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.core.analytics.AnalyticsStore;

import javax.annotation.Nonnull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AnalyticsCommand extends AbstractAsyncCommand {

    public AnalyticsCommand() {
        super("analytics", "View server analytics");
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
        if (!PermissionUtils.isOp(player)) {
            ctx.sendMessage(Message.raw("You must be OP to use this command."));
            return CompletableFuture.completedFuture(null);
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Player not in world."));
            return CompletableFuture.completedFuture(null);
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        return CompletableFuture.runAsync(() -> handleCommand(ctx, player), world);
    }

    private void handleCommand(CommandContext ctx, Player player) {
        String[] args = CommandUtils.getArgs(ctx);
        if (args.length == 0) {
            showOverview(player, 7);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "parkour" -> showParkour(player, parseDays(args, 1));
            case "ascend" -> showAscend(player, parseDays(args, 1));
            case "economy" -> showEconomy(player, parseDays(args, 1));
            case "refresh" -> {
                AnalyticsStore.getInstance().computeDailyAggregates(LocalDate.now());
                player.sendMessage(Message.raw("[Analytics] Refreshed today's aggregates."));
            }
            case "purge" -> {
                AnalyticsStore.getInstance().purgeOldEvents(90);
                player.sendMessage(Message.raw("[Analytics] Purging events older than 90 days."));
            }
            default -> {
                int days = parseDaysOrDefault(args[0], -1);
                if (days > 0) {
                    showOverview(player, days);
                } else {
                    showUsage(player);
                }
            }
        }
    }

    private void showOverview(Player player, int days) {
        AnalyticsStore.getInstance().computeDailyAggregates(LocalDate.now());
        List<AnalyticsStore.DailyStats> stats = AnalyticsStore.getInstance().getRecentStats(days);

        if (stats.isEmpty()) {
            player.sendMessage(Message.raw("[Analytics] No data available for the last " + days + " days."));
            return;
        }

        int totalDau = 0;
        int totalNewPlayers = 0;
        long totalAvgSession = 0;
        int totalSessions = 0;
        float totalParkourPct = 0;
        float totalAscendPct = 0;
        int peakConcurrent = 0;

        for (AnalyticsStore.DailyStats day : stats) {
            totalDau += day.dau();
            totalNewPlayers += day.newPlayers();
            totalAvgSession += day.avgSessionMs();
            totalSessions += day.totalSessions();
            totalParkourPct += day.parkourTimePct();
            totalAscendPct += day.ascendTimePct();
            if (day.peakConcurrent() > peakConcurrent) {
                peakConcurrent = day.peakConcurrent();
            }
        }

        int count = stats.size();
        int avgDau = totalDau / count;
        long avgSessionMin = (totalAvgSession / count) / 60000;
        float avgParkour = totalParkourPct / count;
        float avgAscend = totalAscendPct / count;

        float d1 = AnalyticsStore.getInstance().getRetention(2, 1);
        float d7 = AnalyticsStore.getInstance().getRetention(8, 7);
        float d30 = AnalyticsStore.getInstance().getRetention(31, 30);

        player.sendMessage(Message.raw("--- Server Analytics (" + days + "d) ---"));
        player.sendMessage(Message.raw("DAU: " + avgDau + " avg | Peak: " + peakConcurrent));
        player.sendMessage(Message.raw("New players: " + totalNewPlayers
                + " | Retention D1: " + String.format("%.0f", d1)
                + "% D7: " + String.format("%.0f", d7)
                + "% D30: " + String.format("%.0f", d30) + "%"));
        player.sendMessage(Message.raw("Avg session: " + avgSessionMin + " min | Sessions: " + totalSessions));
        player.sendMessage(Message.raw("Mode split: Parkour " + String.format("%.0f", avgParkour)
                + "% | Ascend " + String.format("%.0f", avgAscend) + "%"));
    }

    private void showParkour(Player player, int days) {
        AnalyticsStore store = AnalyticsStore.getInstance();

        int mapStarts = store.countEvents("map_start", days);
        int mapCompletes = store.countEvents("map_complete", days);
        int pbs = store.countEventsWithFilter("map_complete", days, "%\"is_pb\":true%");
        int firstCompletions = store.countEventsWithFilter("map_complete", days, "%\"first_completion\":true%");
        int levelUps = store.countEvents("level_up", days);
        int uniquePlayers = store.countDistinctPlayers("map_start", days);

        int duels = store.countEvents("duel_finish", days);
        int duelCompleted = store.countEventsWithFilter("duel_finish", days, "%\"reason\":\"COMPLETED\"%");
        int duelForfeit = store.countEventsWithFilter("duel_finish", days, "%\"reason\":\"FORFEIT\"%");
        int duelDisconnect = store.countEventsWithFilter("duel_finish", days, "%\"reason\":\"DISCONNECT\"%");

        List<Map.Entry<String, Integer>> topMaps = store.getTopJsonValues("map_complete", "map_id", days, 5);

        int completionRate = mapStarts > 0 ? (mapCompletes * 100 / mapStarts) : 0;

        player.sendMessage(Message.raw("--- Parkour Analytics (" + days + "d) ---"));
        player.sendMessage(Message.raw("Map starts: " + mapStarts + " | Completions: " + mapCompletes
                + " (" + completionRate + "% rate)"));
        player.sendMessage(Message.raw("PBs set: " + pbs + " | First completions: " + firstCompletions));
        player.sendMessage(Message.raw("Level ups: " + levelUps + " | Unique players: " + uniquePlayers));
        player.sendMessage(Message.raw("Duels: " + duels + " (" + duelCompleted + " completed, "
                + duelForfeit + " forfeit, " + duelDisconnect + " disconnect)"));
        if (!topMaps.isEmpty()) {
            String topStr = topMaps.stream()
                    .map(e -> e.getKey() + "(" + e.getValue() + ")")
                    .collect(Collectors.joining(", "));
            player.sendMessage(Message.raw("Top maps: " + topStr));
        }
    }

    private void showAscend(Player player, int days) {
        AnalyticsStore store = AnalyticsStore.getInstance();

        int manualRuns = store.countEvents("ascend_manual_run", days);
        int uniqueRunners = store.countDistinctPlayers("ascend_manual_run", days);
        int elevations = store.countEvents("ascend_elevation_up", days);
        int summits = store.countEvents("ascend_summit_up", days);
        int ascensions = store.countEvents("ascend_ascension", days);
        int runnersBought = store.countEvents("ascend_runner_buy", days);
        int runnerEvolutions = store.countEvents("ascend_runner_evolve", days);
        int achievements = store.countEvents("ascend_achievement", days);

        int challengeStarts = store.countEvents("ascend_challenge_start", days);
        int challengeCompletes = store.countEvents("ascend_challenge_complete", days);
        int challengeRate = challengeStarts > 0 ? (challengeCompletes * 100 / challengeStarts) : 0;

        List<Map.Entry<String, Integer>> topSummits = store.getTopJsonValues("ascend_summit_up", "category", days, 5);

        player.sendMessage(Message.raw("--- Ascend Analytics (" + days + "d) ---"));
        player.sendMessage(Message.raw("Manual runs: " + manualRuns + " | Unique runners: " + uniqueRunners));
        player.sendMessage(Message.raw("Elevations: " + elevations + " | Summits: " + summits));
        player.sendMessage(Message.raw("Ascensions: " + ascensions + " | Runners bought: " + runnersBought));
        player.sendMessage(Message.raw("Runner evolutions: " + runnerEvolutions + " | Achievements: " + achievements));
        player.sendMessage(Message.raw("Challenges: " + challengeStarts + " started, " + challengeCompletes
                + " completed (" + challengeRate + "%)"));
        if (!topSummits.isEmpty()) {
            String topStr = topSummits.stream()
                    .map(e -> e.getKey() + "(" + e.getValue() + ")")
                    .collect(Collectors.joining(", "));
            player.sendMessage(Message.raw("Top summits: " + topStr));
        }
    }

    private void showEconomy(Player player, int days) {
        AnalyticsStore store = AnalyticsStore.getInstance();

        long gemsSpent = store.sumJsonLongField("gem_spend", "amount", days);
        int purchases = store.countEvents("gem_spend", days);
        int uniqueBuyers = store.countDistinctPlayers("gem_spend", days);
        int discordLinks = store.countEvents("discord_link", days);

        List<Map.Entry<String, Integer>> topItems = store.getTopJsonValues("gem_spend", "item", days, 5);

        player.sendMessage(Message.raw("--- Economy Analytics (" + days + "d) ---"));
        player.sendMessage(Message.raw("Gems spent: " + gemsSpent + " | Purchases: " + purchases
                + " | Unique buyers: " + uniqueBuyers));
        player.sendMessage(Message.raw("Discord links: " + discordLinks));
        if (!topItems.isEmpty()) {
            String topStr = topItems.stream()
                    .map(e -> e.getKey() + "(" + e.getValue() + ")")
                    .collect(Collectors.joining(", "));
            player.sendMessage(Message.raw("Top items: " + topStr));
        }
    }

    private void showUsage(Player player) {
        player.sendMessage(Message.raw("Usage: /analytics [days]"));
        player.sendMessage(Message.raw("  /analytics parkour [days]"));
        player.sendMessage(Message.raw("  /analytics ascend [days]"));
        player.sendMessage(Message.raw("  /analytics economy [days]"));
        player.sendMessage(Message.raw("  /analytics refresh | purge"));
    }

    private int parseDays(String[] args, int dayArgIndex) {
        if (args.length > dayArgIndex) {
            return parseDaysOrDefault(args[dayArgIndex], 7);
        }
        return 7;
    }

    private int parseDaysOrDefault(String value, int defaultVal) {
        try {
            int days = Integer.parseInt(value);
            if (days >= 1 && days <= 365) return days;
        } catch (NumberFormatException ignored) {}
        return defaultVal;
    }
}
