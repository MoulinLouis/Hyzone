package io.hyvexa.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.CompletableFuture;

public class WorldMapManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final boolean disableWorldMap;

    public WorldMapManager(boolean disableWorldMap) {
        this.disableWorldMap = disableWorldMap;
    }

    public void disableWorldMapForPlayer(Ref<EntityStore> ref) {
        if (!disableWorldMap) {
            return;
        }
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null) {
                return;
            }
            WorldMapTracker tracker = player.getWorldMapTracker();
            if (tracker != null) {
                tracker.setViewRadiusOverride(0);
                tracker.clear();
                String name = playerRef != null ? playerRef.getUsername() : "unknown";
                LOGGER.atInfo().log("Disabled world map for player: " + name);
            }
        }, world);
    }
}
