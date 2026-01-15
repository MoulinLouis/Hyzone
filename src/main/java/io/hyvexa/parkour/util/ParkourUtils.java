package io.hyvexa.parkour.util;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import io.hyvexa.parkour.data.ProgressStore;

import java.util.UUID;

public final class ParkourUtils {

    private ParkourUtils() {
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
}
