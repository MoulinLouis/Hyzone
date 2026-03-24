package io.hyvexa.common.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/**
 * Common accessors for NPC entity state, shared by RobotState and MinerRobotState.
 */
public interface NPCEntityState {
    Ref<EntityStore> getEntityRef();
    void setEntityRef(Ref<EntityStore> entityRef);
    UUID getEntityUuid();
    void setEntityUuid(UUID entityUuid);
}
