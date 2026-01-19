package io.hyvexa.parkour.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.parkour.visibility.PlayerVisibilityManager;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public class PlayerVisibilityFilterSystem extends EntityTickingSystem<EntityStore> {

    private volatile Query<EntityStore> query;

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.FIND_VISIBLE_ENTITIES_GROUP;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        if (EntityStore.REGISTRY.hasSystemClass(EntityTrackerSystems.CollectVisible.class)) {
            return Set.of(new SystemDependency<>(Order.AFTER, EntityTrackerSystems.CollectVisible.class));
        }
        return Set.of();
    }

    @Override
    public void tick(float delta, int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> buffer) {
        EntityViewer viewer = chunk.getComponent(entityId, EntityViewer.getComponentType());
        PlayerRef viewerRef = chunk.getComponent(entityId, PlayerRef.getComponentType());
        if (viewer == null || viewerRef == null) {
            return;
        }
        Set<UUID> hiddenTargets = PlayerVisibilityManager.get().getHiddenTargets(viewerRef.getUuid());
        if (hiddenTargets.isEmpty()) {
            return;
        }
        Iterator<Ref<EntityStore>> iterator = viewer.visible.iterator();
        while (iterator.hasNext()) {
            Ref<EntityStore> targetRef = iterator.next();
            UUIDComponent uuidComponent = store.getComponent(targetRef, UUIDComponent.getComponentType());
            if (uuidComponent == null) {
                continue;
            }
            if (!hiddenTargets.contains(uuidComponent.getUuid())) {
                continue;
            }
            iterator.remove();
            viewer.hiddenCount++;
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        Query<EntityStore> current = query;
        if (current != null) {
            return current;
        }
        var viewerType = EntityViewer.getComponentType();
        var playerRefType = PlayerRef.getComponentType();
        var playerType = Player.getComponentType();
        if (viewerType == null || playerRefType == null || playerType == null) {
            return Query.any();
        }
        current = Archetype.of(viewerType, playerRefType, playerType);
        query = current;
        return current;
    }
}
