package io.hyvexa.ascend.robot;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * System that cleans up orphaned runner entities after server restart.
 *
 * When the server shuts down, runner NPCs may persist in the world.
 * On restart, this system removes those orphaned entities to prevent
 * duplicates (frozen runners stuck in place alongside new active runners).
 *
 * Detection strategy: Runners are NPCs with Frozen + Invulnerable components.
 * If such an entity's UUID is not in the active robots list, it's orphaned.
 */
public class RunnerCleanupSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final RobotManager robotManager;
    private volatile Query<EntityStore> query;

    public RunnerCleanupSystem(RobotManager robotManager) {
        this.robotManager = robotManager;
    }

    @Override
    public void tick(float delta, int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> buffer) {
        // Skip if cleanup is complete
        if (robotManager == null || !robotManager.isCleanupPending()) {
            return;
        }

        // Check for runner signature: Frozen + Invulnerable
        Frozen frozen = chunk.getComponent(entityId, Frozen.getComponentType());
        if (frozen == null) {
            return;
        }
        Invulnerable invulnerable = chunk.getComponent(entityId, Invulnerable.getComponentType());
        if (invulnerable == null) {
            return;
        }

        // Get entity UUID
        UUIDComponent uuidComponent = chunk.getComponent(entityId, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return;
        }
        UUID entityUuid = uuidComponent.getUuid();
        if (entityUuid == null) {
            return;
        }

        // Check if this UUID belongs to an active robot
        if (robotManager.isActiveRunnerUuid(entityUuid)) {
            return; // This is a valid, active runner - don't remove
        }

        // This is an orphaned runner (Frozen + Invulnerable but not in active list)
        Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
        if (ref == null || !ref.isValid()) {
            return;
        }

        try {
            store.removeEntity(ref, RemoveReason.REMOVE);
            robotManager.markOrphanCleaned(entityUuid);
            LOGGER.atInfo().log("[RunnerCleanup] Removed orphaned runner: " + entityUuid);
        } catch (Exception e) {
            LOGGER.atWarning().log("[RunnerCleanup] Failed to remove orphaned runner " + entityUuid + ": " + e.getMessage());
        }
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        // Use entity tracker group - runs every tick for all entities
        return EntityTrackerSystems.FIND_VISIBLE_ENTITIES_GROUP;
    }

    @Override
    public Query<EntityStore> getQuery() {
        Query<EntityStore> current = query;
        if (current != null) {
            return current;
        }
        // Query for entities with Frozen + Invulnerable (runner signature)
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
