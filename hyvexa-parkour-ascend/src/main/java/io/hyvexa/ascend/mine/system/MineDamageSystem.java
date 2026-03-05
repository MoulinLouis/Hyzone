package io.hyvexa.ascend.mine.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineZone;

import java.util.UUID;

public class MineDamageSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {
    private final MineManager mineManager;
    private final MinePlayerStore minePlayerStore;

    public MineDamageSystem(MineManager mineManager, MinePlayerStore minePlayerStore) {
        super(DamageBlockEvent.class);
        this.mineManager = mineManager;
        this.minePlayerStore = minePlayerStore;
    }

    @Override
    public void handle(int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> buffer, DamageBlockEvent event) {
        Vector3i target = event.getTargetBlock();
        if (target == null) return;

        MineZone zone = mineManager.findZoneAt(target.getX(), target.getY(), target.getZ());
        if (zone == null) return;

        PlayerRef playerRef = chunk.getComponent(entityId, PlayerRef.getComponentType());
        if (playerRef == null) return;
        UUID playerId = playerRef.getUuid();
        if (playerId == null) return;

        MinePlayerProgress progress = minePlayerStore.getOrCreatePlayer(playerId);
        double speedMult = progress.getMiningSpeedMultiplier();
        if (speedMult > 1.0) {
            event.setDamage(event.getDamage() * (float) speedMult);
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
