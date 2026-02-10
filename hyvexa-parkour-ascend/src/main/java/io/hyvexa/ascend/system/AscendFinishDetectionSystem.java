package io.hyvexa.ascend.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.util.AsyncExecutionHelper;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AscendFinishDetectionSystem extends EntityTickingSystem<EntityStore> {

    private final ParkourAscendPlugin plugin;
    private final AscendRunTracker runTracker;
    private final Set<UUID> finishChecksInFlight = ConcurrentHashMap.newKeySet();
    private volatile Query<EntityStore> query;

    public AscendFinishDetectionSystem(ParkourAscendPlugin plugin, AscendRunTracker runTracker) {
        this.plugin = plugin;
        this.runTracker = runTracker;
    }

    @Override
    public void tick(float delta, int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> buffer) {
        if (runTracker == null || plugin == null) {
            return;
        }
        // Read from chunk only (safe inside tick - no store access)
        PlayerRef playerRef = chunk.getComponent(entityId, PlayerRef.getComponentType());
        TransformComponent transform = chunk.getComponent(entityId, TransformComponent.getComponentType());
        if (playerRef == null || transform == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (!plugin.isAscendWorld(world)) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (!runTracker.isRunActive(playerId)) {
            return;
        }
        // Pure math check - no store access
        Vector3d pos = transform.getPosition();
        if (runTracker.isNearFinish(playerId, pos) && finishChecksInFlight.add(playerId)) {
            // Defer store-modifying work to world thread outside system processing.
            // Guard with in-flight set so we only queue one completion check per player.
            Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
            String playerIdText = playerId != null ? playerId.toString() : "unknown";
            String worldName = world != null && world.getName() != null ? world.getName() : "unknown";
            boolean queued = AsyncExecutionHelper.runBestEffort(world, () -> {
                try {
                    if (ref != null && ref.isValid()) {
                        runTracker.checkPlayer(ref, ref.getStore());
                    }
                } finally {
                    finishChecksInFlight.remove(playerId);
                }
            }, "ascend.finish.check", "ascend finish detection check",
                    "player=" + playerIdText + ", world=" + worldName);
            if (!queued) {
                finishChecksInFlight.remove(playerId);
            }
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        Query<EntityStore> current = query;
        if (current != null) {
            return current;
        }
        var playerType = Player.getComponentType();
        var playerRefType = PlayerRef.getComponentType();
        var transformType = TransformComponent.getComponentType();
        if (playerType == null || playerRefType == null || transformType == null) {
            return Query.any();
        }
        current = Archetype.of(playerType, playerRefType, transformType);
        query = current;
        return current;
    }
}
