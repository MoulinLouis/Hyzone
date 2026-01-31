package io.hyvexa.ascend.tracker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.util.SystemMessageUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AscendRunTracker {

    private static final double FINISH_RADIUS_SQ = 2.25; // 1.5^2

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final Map<UUID, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    public AscendRunTracker(AscendMapStore mapStore, AscendPlayerStore playerStore) {
        this.mapStore = mapStore;
        this.playerStore = playerStore;
    }

    public void startRun(UUID playerId, String mapId) {
        AscendMap map = mapStore.getMap(mapId);
        if (map == null) {
            return;
        }

        activeRuns.put(playerId, new ActiveRun(mapId, System.currentTimeMillis()));
    }

    public void cancelRun(UUID playerId) {
        activeRuns.remove(playerId);
    }

    public String getActiveMapId(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        return run != null ? run.mapId : null;
    }

    public void checkPlayer(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());

        if (playerRef == null || player == null || transform == null) {
            return;
        }

        ActiveRun run = activeRuns.get(playerRef.getUuid());
        if (run == null) {
            return;
        }

        AscendMap map = mapStore.getMap(run.mapId);
        if (map == null) {
            activeRuns.remove(playerRef.getUuid());
            return;
        }

        Vector3d pos = transform.getPosition();
        double dx = pos.getX() - map.getFinishX();
        double dy = pos.getY() - map.getFinishY();
        double dz = pos.getZ() - map.getFinishZ();

        if (dx * dx + dy * dy + dz * dz <= FINISH_RADIUS_SQ) {
            completeRun(playerRef, player, run, map, ref, store);
        }
    }

    private void completeRun(PlayerRef playerRef, Player player, ActiveRun run,
                             AscendMap map, Ref<EntityStore> ref, Store<EntityStore> store) {
        UUID playerId = playerRef.getUuid();
        activeRuns.remove(playerId);

        AscendPlayerProgress.MapProgress mapProgress = playerStore.getOrCreateMapProgress(playerId, run.mapId);

        boolean firstCompletion = !mapProgress.isCompletedManually();

        mapProgress.setCompletedManually(true);
        mapProgress.setUnlocked(true);
        playerStore.markDirty(playerId);

        playerStore.incrementMapMultiplier(playerId, run.mapId);
        List<AscendMap> multiplierMaps = mapStore.listMapsSorted();
        long payout = playerStore.getMultiplierProduct(playerId, multiplierMaps, AscendConstants.MULTIPLIER_SLOTS);
        playerStore.addCoins(playerId, payout);

        if (firstCompletion) {
            player.sendMessage(Message.raw("[Ascend] Map completed! You can now buy a robot for this map.")
                .color(SystemMessageUtils.SUCCESS));
        }
        player.sendMessage(Message.raw("[Ascend] +" + payout + " coins.")
            .color(SystemMessageUtils.PRIMARY_TEXT));

        Vector3d startPos = new Vector3d(map.getStartX(), map.getStartY(), map.getStartZ());
        Vector3f startRot = new Vector3f(map.getStartRotX(), map.getStartRotY(), map.getStartRotZ());
        store.addComponent(ref, Teleport.getComponentType(),
            new Teleport(store.getExternalData().getWorld(), startPos, startRot));
    }

    public void teleportToMapStart(Ref<EntityStore> ref, Store<EntityStore> store,
                                   PlayerRef playerRef, String mapId) {
        AscendMap map = mapStore.getMap(mapId);
        if (map == null) {
            return;
        }

        Vector3d pos = new Vector3d(map.getStartX(), map.getStartY(), map.getStartZ());
        Vector3f rot = new Vector3f(map.getStartRotX(), map.getStartRotY(), map.getStartRotZ());
        store.addComponent(ref, Teleport.getComponentType(),
            new Teleport(store.getExternalData().getWorld(), pos, rot));

        startRun(playerRef.getUuid(), mapId);
    }

    private static class ActiveRun {
        final String mapId;
        final long startTimeMs;

        ActiveRun(String mapId, long startTimeMs) {
            this.mapId = mapId;
            this.startTimeMs = startTimeMs;
        }
    }
}
