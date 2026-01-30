package io.hyvexa.parkour.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.duel.DuelTracker;
import io.hyvexa.manager.PlayerPerksManager;
import io.hyvexa.parkour.tracker.RunTracker;

import java.util.UUID;

public class RunTrackerTickSystem extends EntityTickingSystem<EntityStore> {

    private final HyvexaPlugin plugin;
    private final RunTracker runTracker;
    private final PlayerPerksManager perksManager;
    private final DuelTracker duelTracker;
    private volatile Query<EntityStore> query;

    public RunTrackerTickSystem(HyvexaPlugin plugin,
                                RunTracker runTracker,
                                PlayerPerksManager perksManager,
                                DuelTracker duelTracker) {
        this.plugin = plugin;
        this.runTracker = runTracker;
        this.perksManager = perksManager;
        this.duelTracker = duelTracker;
    }

    @Override
    public void tick(float delta, int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> buffer) {
        if (runTracker == null || plugin == null) {
            return;
        }
        PlayerRef playerRef = chunk.getComponent(entityId, PlayerRef.getComponentType());
        Player player = chunk.getComponent(entityId, Player.getComponentType());
        TransformComponent transform = chunk.getComponent(entityId, TransformComponent.getComponentType());
        if (playerRef == null || player == null || transform == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        UUID playerId = playerRef.getUuid();
        if (!plugin.shouldApplyParkourMode(playerId, world)) {
            return;
        }
        if (duelTracker != null && duelTracker.isInMatch(playerId)) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
        if (perksManager != null) {
            String activeMapId = runTracker.getActiveMapId(playerId);
            if (activeMapId != null) {
                perksManager.disableVipSpeedBoost(ref, store, playerRef);
            } else if (perksManager.shouldDisableVipSpeedForStartTrigger(store, ref, playerRef)) {
                perksManager.disableVipSpeedBoost(ref, store, playerRef);
            }
        }
        runTracker.checkPlayer(ref, store, buffer, delta);
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
