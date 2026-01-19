package io.hyvexa.parkour.system;

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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Set;
import java.util.function.BooleanSupplier;

public class NoPlayerKnockbackSystem extends EntityTickingSystem<EntityStore> {

    private final BooleanSupplier enabledSupplier;
    private volatile Query<EntityStore> query;
    private volatile SystemGroup<EntityStore> cachedGroup;

    public NoPlayerKnockbackSystem(BooleanSupplier enabledSupplier) {
        this.enabledSupplier = enabledSupplier;
    }

    @Override
    public void tick(float delta, int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> buffer) {
        if (enabledSupplier != null && !enabledSupplier.getAsBoolean()) {
            return;
        }
        if (chunk.getComponent(entityId, KnockbackComponent.getComponentType()) == null) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
        buffer.tryRemoveComponent(ref, KnockbackComponent.getComponentType());
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        SystemGroup<EntityStore> group = cachedGroup;
        if (group != null) {
            return group;
        }
        group = DamageModule.get().getInspectDamageGroup();
        cachedGroup = group;
        return group;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        if (EntityStore.REGISTRY.hasSystemClass(KnockbackSystems.ApplyPlayerKnockback.class)) {
            return Set.of(new SystemDependency<>(Order.BEFORE, KnockbackSystems.ApplyPlayerKnockback.class));
        }
        return Set.of();
    }

    @Override
    public Query<EntityStore> getQuery() {
        Query<EntityStore> current = query;
        if (current != null) {
            return current;
        }
        var playerType = Player.getComponentType();
        var knockbackType = KnockbackComponent.getComponentType();
        if (playerType == null || knockbackType == null) {
            return Query.any();
        }
        current = com.hypixel.hytale.component.Archetype.of(playerType, knockbackType);
        query = current;
        return current;
    }
}
