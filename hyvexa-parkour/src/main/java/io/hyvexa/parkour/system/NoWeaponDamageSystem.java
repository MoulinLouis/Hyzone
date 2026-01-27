package io.hyvexa.parkour.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.function.BooleanSupplier;

public class NoWeaponDamageSystem extends DamageEventSystem {

    private final BooleanSupplier enabledSupplier;
    private volatile SystemGroup<EntityStore> cachedGroup;

    public NoWeaponDamageSystem(BooleanSupplier enabledSupplier) {
        this.enabledSupplier = enabledSupplier;
    }

    @Override
    public void handle(int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> buffer, Damage event) {
        if (enabledSupplier != null && !enabledSupplier.getAsBoolean()) {
            return;
        }
        Player target = chunk.getComponent(entityId, Player.getComponentType());
        if (target == null) {
            return;
        }
        Damage.Source source = event.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) {
            return;
        }
        Ref<EntityStore> sourceRef = entitySource.getRef();
        if (sourceRef == null || !sourceRef.isValid()) {
            return;
        }
        Player attacker = sourceRef.getStore().getComponent(sourceRef, Player.getComponentType());
        if (attacker == null) {
            return;
        }
        if (event.hasMetaObject(Damage.KNOCKBACK_COMPONENT)) {
            event.removeMetaObject(Damage.KNOCKBACK_COMPONENT);
        }
        event.setCancelled(true);
        event.setAmount(0f);
        buffer.tryRemoveComponent(chunk.getReferenceTo(entityId), KnockbackComponent.getComponentType());
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        SystemGroup<EntityStore> group = cachedGroup;
        if (group != null) {
            return group;
        }
        group = DamageModule.get().getFilterDamageGroup();
        cachedGroup = group;
        return group;
    }
}
