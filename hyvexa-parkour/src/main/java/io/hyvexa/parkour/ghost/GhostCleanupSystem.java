package io.hyvexa.parkour.ghost;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Detects orphaned parkour ghost NPCs and queues them for deferred removal.
 * Removal itself happens outside ECS tick via GhostNpcManager.
 */
public class GhostCleanupSystem extends EntityTickingSystem<EntityStore> {

    private final GhostNpcManager ghostNpcManager;
    private volatile Query<EntityStore> query;

    public GhostCleanupSystem(GhostNpcManager ghostNpcManager) {
        this.ghostNpcManager = ghostNpcManager;
    }

    @Override
    public void tick(float delta, int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> buffer) {
        if (ghostNpcManager == null || ghostNpcManager.hasPendingSpawn()) {
            return;
        }

        UUIDComponent uuidComponent = chunk.getComponent(entityId, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return;
        }
        UUID entityUuid = uuidComponent.getUuid();
        if (entityUuid == null || ghostNpcManager.isActiveGhostUuid(entityUuid)) {
            return;
        }

        Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
        if (ref == null || !ref.isValid()) {
            return;
        }

        Nameplate nameplate = store.getComponent(ref, Nameplate.getComponentType());
        boolean namedGhost = nameplate != null && "Ghost".equals(nameplate.getText());
        if (!namedGhost && !ghostNpcManager.isOrphanedGhost(entityUuid)) {
            return;
        }

        ghostNpcManager.queueOrphanForRemoval(entityUuid, ref);
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.FIND_VISIBLE_ENTITIES_GROUP;
    }

    @Override
    public Query<EntityStore> getQuery() {
        Query<EntityStore> current = query;
        if (current != null) {
            return current;
        }
        var frozenType = Frozen.getComponentType();
        var invulnerableType = Invulnerable.getComponentType();
        var uuidType = UUIDComponent.getComponentType();
        if (frozenType == null || invulnerableType == null || uuidType == null) {
            return Query.any();
        }
        current = Archetype.of(frozenType, invulnerableType, uuidType);
        query = current;
        return current;
    }
}
