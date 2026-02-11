package io.hyvexa.manager;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.HylogramsBridge;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.parkour.ParkourTimingConstants;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.util.ParkourUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class LeaderboardHologramManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String LEADERBOARD_HOLOGRAM_NAME = "leaderboard";

    private final ProgressStore progressStore;
    private final MapStore mapStore;
    private String parkourWorldName;

    public LeaderboardHologramManager(ProgressStore progressStore, MapStore mapStore) {
        this.progressStore = progressStore;
        this.mapStore = mapStore;
        this.parkourWorldName = "Parkour";
    }

    public LeaderboardHologramManager(ProgressStore progressStore, MapStore mapStore, String parkourWorldName) {
        this.progressStore = progressStore;
        this.mapStore = mapStore;
        this.parkourWorldName = parkourWorldName != null ? parkourWorldName : "Parkour";
    }

    public void setParkourWorldName(String parkourWorldName) {
        this.parkourWorldName = parkourWorldName != null ? parkourWorldName : "Parkour";
    }

    public String getParkourWorldName() {
        return parkourWorldName;
    }

    public void refreshLeaderboardHologram() {
        if (!HylogramsBridge.isAvailable()) {
            return;
        }
        World world = resolveParkourWorld();
        if (world == null) {
            return;
        }
        world.execute(() -> updateLeaderboardHologram(world.getEntityStore().getStore()));
    }

    public void refreshLeaderboardHologram(Store<EntityStore> store) {
        if (store == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (!isParkourWorld(world)) {
            return;
        }
        world.execute(() -> updateLeaderboardHologram(store));
    }

    public void refreshMapLeaderboardHologram(String mapId, Store<EntityStore> store) {
        if (mapId == null || mapId.isBlank() || store == null || progressStore == null) {
            return;
        }
        World storeWorld = store.getExternalData().getWorld();
        String storeWorldName = storeWorld != null ? storeWorld.getName() : "unknown";
        logMapHologramDebug("Map holo refresh requested for '" + mapId + "' from world '" + storeWorldName + "'.");
        if (!HylogramsBridge.isAvailable()) {
            logMapHologramDebug("Map holo refresh skipped: Hylograms not available.");
            return;
        }
        String holoName = mapId + "_holo";
        if (!HylogramsBridge.exists(holoName)) {
            logMapHologramDebug("Map holo refresh skipped: '" + holoName + "' does not exist.");
            return;
        }
        HylogramsBridge.Hologram holo = HylogramsBridge.get(holoName);
        World targetWorld = null;
        Store<EntityStore> targetStore = null;
        if (holo != null && holo.getWorldName() != null) {
            targetWorld = Universe.get().getWorld(holo.getWorldName());
            if (targetWorld == null) {
                for (World candidate : Universe.get().getWorlds().values()) {
                    if (candidate != null && holo.getWorldName().equalsIgnoreCase(candidate.getName())) {
                        targetWorld = candidate;
                        break;
                    }
                }
            }
        }
        if (targetWorld == null) {
            targetWorld = store.getExternalData().getWorld();
        }
        if (targetWorld != null) {
            logMapHologramDebug("Map holo '" + holoName + "' target world resolved to '"
                    + targetWorld.getName() + "'.");
            targetStore = targetWorld.getEntityStore().getStore();
            Store<EntityStore> finalStore = targetStore != null ? targetStore : store;
            targetWorld.execute(() -> updateMapLeaderboardHologramLines(mapId, holoName, finalStore));
            return;
        }
        updateMapLeaderboardHologramLines(mapId, holoName, store);
    }

    private void updateLeaderboardHologram(Store<EntityStore> store) {
        if (store == null || progressStore == null || !HylogramsBridge.isAvailable()) {
            return;
        }
        List<String> lines = buildLeaderboardHologramLines();
        try {
            HylogramsBridge.updateHologramLines(LEADERBOARD_HOLOGRAM_NAME, lines, store);
            HylogramsBridge.delete("leaderboard_counts", store);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to update leaderboard hologram");
        }
    }

    private void updateMapLeaderboardHologramLines(String mapId, String holoName, Store<EntityStore> store) {
        if (store == null) {
            return;
        }
        List<String> lines = buildMapLeaderboardHologramLines(mapId);
        logMapHologramDebug("Updating map holo '" + holoName + "' with " + lines.size() + " lines.");
        try {
            HylogramsBridge.Hologram existing = HylogramsBridge.get(holoName);
            HylogramsBridge.updateHologramLines(holoName, lines, store);
            if (existing != null) {
                existing.respawn(store).save(store);
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to update map leaderboard hologram");
        }
    }

    private List<String> buildLeaderboardHologramLines() {
        List<String> lines = new ArrayList<>();
        lines.add(formatLeaderboardHeader());
        if (progressStore == null) {
            return lines;
        }
        Map<UUID, Integer> counts = progressStore.getMapCompletionCounts();
        if (counts.isEmpty()) {
            return lines;
        }
        List<CompletionRow> rows = buildLeaderboardRows(counts);
        int entries = Math.min(rows.size(), ParkourTimingConstants.LEADERBOARD_HOLOGRAM_ENTRIES);
        for (int i = 0; i < entries; i++) {
            CompletionRow row = rows.get(i);
            String name = formatLeaderboardName(row.name);
            String position = formatLeaderboardPosition(i + 1);
            String count = formatLeaderboardCount(row.count);
            lines.add(formatLeaderboardLine(position, name, count));
        }
        return lines;
    }

    private List<CompletionRow> buildLeaderboardRows(Map<UUID, Integer> counts) {
        List<CompletionRow> rows = new ArrayList<>(counts.size());
        for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            UUID playerId = entry.getKey();
            if (playerId == null) {
                continue;
            }
            int count = entry.getValue() != null ? entry.getValue() : 0;
            String name = ParkourUtils.resolveName(playerId, progressStore);
            rows.add(new CompletionRow(playerId, name, count));
        }
        rows.sort(CompletionRow.COMPARATOR);
        return rows;
    }

    public List<String> buildMapLeaderboardHologramLines(String mapId) {
        List<String> lines = new ArrayList<>();
        String mapName = mapId;
        if (mapStore != null && mapId != null) {
            io.hyvexa.parkour.data.Map map = mapStore.getMap(mapId);
            if (map != null && map.getName() != null && !map.getName().isBlank()) {
                mapName = map.getName().trim();
            }
        }
        if (mapName != null && !mapName.isBlank()) {
            lines.add(mapName);
        }
        lines.add(formatMapHologramHeader());
        if (progressStore == null || mapId == null) {
            return lines;
        }
        List<Map.Entry<UUID, Long>> entries = progressStore.getLeaderboardEntries(mapId);
        int limit = Math.min(entries.size(), ParkourTimingConstants.MAP_HOLOGRAM_TOP_LIMIT);
        for (int i = 0; i < limit; i++) {
            Map.Entry<UUID, Long> entry = entries.get(i);
            String name = ParkourUtils.resolveName(entry.getKey(), progressStore);
            String safeName = clampToWidth(name, ParkourTimingConstants.MAP_HOLOGRAM_NAME_MAX);
            String time = entry.getValue() != null ? FormatUtils.formatDuration(entry.getValue()) : "--";
            String position = String.valueOf(i + 1) + ".";
            lines.add(formatMapHologramLine(position, safeName, time));
        }
        if (lines.size() == 2) {
            lines.add("No completions yet.");
        }
        return lines;
    }

    private World resolveParkourWorld() {
        World world = Universe.get().getWorld(parkourWorldName);
        if (world != null) {
            return world;
        }
        for (World candidate : Universe.get().getWorlds().values()) {
            if (candidate != null && parkourWorldName.equalsIgnoreCase(candidate.getName())) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isParkourWorld(World world) {
        return ModeGate.isParkourWorld(world);
    }

    public void logMapHologramDebug(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        LOGGER.atFine().log(message);
    }

    private static String formatLeaderboardHeader() {
        return formatLeaderboardLine("Pos", "Name", "Maps");
    }

    private static String formatLeaderboardPosition(int position) {
        if (position < 10) {
            return position + ". ";
        }
        return position + ".";
    }

    private static String formatLeaderboardCount(int count) {
        return String.format(Locale.ROOT, "%" + ParkourTimingConstants.LEADERBOARD_COUNT_WIDTH + "d", count);
    }

    private static String formatLeaderboardLine(String position, String name, String count) {
        String safePosition = clampToWidth(position, ParkourTimingConstants.LEADERBOARD_POSITION_WIDTH);
        String safeName = clampToWidth(name, ParkourTimingConstants.LEADERBOARD_NAME_MAX);
        String safeCount = clampToWidth(count, ParkourTimingConstants.LEADERBOARD_COUNT_WIDTH);
        return String.format(Locale.ROOT, "%-" + ParkourTimingConstants.LEADERBOARD_POSITION_WIDTH + "s | %-"
                        + ParkourTimingConstants.LEADERBOARD_NAME_MAX + "s | %" + ParkourTimingConstants.LEADERBOARD_COUNT_WIDTH + "s",
                safePosition, safeName, safeCount);
    }

    private static String formatLeaderboardName(String name) {
        return clampToWidth(name, ParkourTimingConstants.LEADERBOARD_NAME_MAX);
    }

    private static String clampToWidth(String value, int width) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= width) {
            return trimmed;
        }
        if (width <= 3) {
            return trimmed.substring(0, width);
        }
        return trimmed.substring(0, width - 3) + "...";
    }

    private static String formatMapHologramHeader() {
        return formatMapHologramLine("Pos", "Name", "Time");
    }

    private static String formatMapHologramLine(String position, String name, String time) {
        String safePosition = clampToWidth(position, ParkourTimingConstants.MAP_HOLOGRAM_POS_WIDTH);
        String safeName = clampToWidth(name, ParkourTimingConstants.MAP_HOLOGRAM_NAME_MAX);
        return String.format(Locale.ROOT, "%-" + ParkourTimingConstants.MAP_HOLOGRAM_POS_WIDTH + "s | %-"
                        + ParkourTimingConstants.MAP_HOLOGRAM_NAME_MAX + "s | %s",
                safePosition, safeName, time);
    }

    private static final class CompletionRow {
        private static final Comparator<CompletionRow> COMPARATOR = Comparator
                .comparingInt((CompletionRow row) -> row.count).reversed()
                .thenComparing(row -> row.sortName)
                .thenComparing(row -> row.playerId.toString());

        private final UUID playerId;
        private final String name;
        private final String sortName;
        private final int count;

        private CompletionRow(UUID playerId, String name, int count) {
            this.playerId = playerId;
            this.name = name != null ? name : "";
            this.sortName = this.name.toLowerCase(Locale.ROOT);
            this.count = count;
        }
    }
}
