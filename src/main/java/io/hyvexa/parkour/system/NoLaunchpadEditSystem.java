package io.hyvexa.parkour.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.PermissionUtils;

public class NoLaunchpadEditSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final String LAUNCHPAD_BLOCK_ID = "Launchpad";

    public NoLaunchpadEditSystem() {
        super(UseBlockEvent.Pre.class);
    }

    @Override
    public void handle(int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> buffer, UseBlockEvent.Pre event) {
        Player player = chunk.getComponent(entityId, Player.getComponentType());
        if (player == null || PermissionUtils.isOp(player)) {
            return;
        }

        BlockType blockType = event.getBlockType();
        if (blockType == null) {
            return;
        }

        String blockId = blockType.getId();
        if (blockId != null && LAUNCHPAD_BLOCK_ID.equalsIgnoreCase(blockId)) {
            event.setCancelled(true);
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
