package io.hyvexa.ascend.robot;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.UUID;

/**
 * System that detects orphaned runner entities after server restart.
 *
 * When the server shuts down, runner NPCs may persist in the world.
 * On restart, this system identifies those orphaned entities and queues
 * them for deferred removal via RobotManager (outside the ECS tick).
 *
 * Detection strategy: runners are NPCs with Frozen + Invulnerable components,
 * and only UUIDs explicitly persisted by RobotManager are eligible for removal.
 *
 * IMPORTANT: Entity removal cannot happen during a system tick (store is
 * processing). Detected orphans are queued and removed via world.execute().
 */
public class RunnerCleanupSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final RobotManager robotManager;
    private volatile Query<EntityStore> query;
    private volatile boolean queryFailed;

    public RunnerCleanupSystem(RobotManager robotManager) {
        this.robotManager = robotManager;
    }

    @Override
    public void tick(float delta, int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> buffer) {
        if (queryFailed) {
            return;
        }
        boolean runnerPending = robotManager != null && robotManager.isCleanupPending();
        if (!runnerPending) {
            return;
        }

        // Check for NPC signature: Frozen + Invulnerable
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

        if (robotManager != null && robotManager.isActiveRunnerUuid(entityUuid)) {
            return;
        }

        if (runnerPending && robotManager.isOrphanedRunner(entityUuid)) {
            Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
            if (ref != null && ref.isValid()) {
                robotManager.queueOrphanForRemoval(entityUuid, ref);
            }
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
            LOGGER.atWarning().log("RunnerCleanupSystem: component types not yet registered, disabling system");
            queryFailed = true;
            return Query.any();
        }
        current = Archetype.of(frozenType, invulnerableType, uuidType);
        query = current;
        return current;
    }
}
