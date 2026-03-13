package io.hyvexa.parkour.util;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.TransformData;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ParkourUtils {

    private ParkourUtils() {
    }

    public static final Comparator<Map> MAP_DIFFICULTY_COMPARATOR =
            Comparator.comparingInt((Map map) -> {
                        int difficulty = map.getDifficulty();
                        return difficulty <= 0 ? Integer.MAX_VALUE : difficulty;
                    })
                    .thenComparing(map -> map.getName() != null ? map.getName() : map.getId(),
                            String.CASE_INSENSITIVE_ORDER);

    public static int getCategoryOrder(String category, List<Map> maps) {
        int minOrder = Integer.MAX_VALUE;
        for (Map map : maps) {
            String mapCategory = FormatUtils.normalizeCategory(map.getCategory());
            if (mapCategory.equalsIgnoreCase(category)) {
                minOrder = Math.min(minOrder, map.getOrder());
            }
        }
        return minOrder == Integer.MAX_VALUE ? Integer.MAX_VALUE : minOrder;
    }

    public static String resolveName(UUID playerId) {
        PlayerRef ref = Universe.get().getPlayer(playerId);
        return ref != null ? ref.getUsername() : playerId.toString();
    }

    public static String resolveName(UUID uuid, ProgressStore progressStore) {
        PlayerRef playerRef = Universe.get().getPlayer(uuid);
        if (playerRef != null) {
            return playerRef.getUsername();
        }
        if (progressStore != null) {
            String storedName = progressStore.getPlayerName(uuid);
            if (storedName != null && !storedName.isBlank()) {
                return storedName;
            }
        }
        return uuid.toString();
    }

    public static String formatMapName(Map map) {
        if (map == null) {
            return "";
        }
        String name = map.getName();
        if (name == null || name.isBlank()) {
            name = map.getId() != null ? map.getId() : "";
        }
        int difficulty = map.getDifficulty();
        if (difficulty <= 0) {
            return name;
        }
        return name + " - " + difficulty;
    }

    public static TransformData copyTransformData(TransformData source) {
        if (source == null) {
            return null;
        }
        return source.copy();
    }
}
