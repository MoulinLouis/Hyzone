package io.hyvexa.parkour.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class DamageUtils {

    private DamageUtils() {
    }

    static void cancelDamage(int entityId, ArchetypeChunk<EntityStore> chunk,
                             CommandBuffer<EntityStore> buffer, Damage event) {
        if (event.hasMetaObject(Damage.KNOCKBACK_COMPONENT)) {
            event.removeMetaObject(Damage.KNOCKBACK_COMPONENT);
        }
        event.setCancelled(true);
        event.setAmount(0f);
        buffer.tryRemoveComponent(chunk.getReferenceTo(entityId), KnockbackComponent.getComponentType());
    }
}
