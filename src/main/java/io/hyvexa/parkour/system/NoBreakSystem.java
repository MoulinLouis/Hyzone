package io.hyvexa.parkour.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.PermissionUtils;

public class NoBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public NoBreakSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> buffer, BreakBlockEvent event) {
        Player player = chunk.getComponent(entityId, Player.getComponentType());
        if (player != null && !PermissionUtils.isOp(player)) {
            event.setCancelled(true);
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
