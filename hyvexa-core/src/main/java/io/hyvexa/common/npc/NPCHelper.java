package io.hyvexa.common.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

import java.util.List;

/**
 * Shared NPC lifecycle operations used by RobotManager and MineRobotManager.
 * Eliminates duplication of entity ref extraction, component setup, and despawn logic.
 */
public final class NPCHelper {

    private static volatile java.lang.reflect.Method cachedPairMethod;

    private NPCHelper() {}

    /**
     * Extract entity ref from NPCPlugin.spawnNPC() result using reflection.
     * The result is a Pair-like object whose accessor varies by implementation.
     */
    @SuppressWarnings("unchecked")
    public static Ref<EntityStore> extractEntityRef(Object pairResult, HytaleLogger logger) {
        if (pairResult == null) return null;
        try {
            java.lang.reflect.Method method = cachedPairMethod;
            if (method != null && method.getDeclaringClass().isAssignableFrom(pairResult.getClass())) {
                Object value = method.invoke(pairResult);
                if (value instanceof Ref<?> ref) {
                    return (Ref<EntityStore>) ref;
                }
            }
            for (String methodName : List.of("getFirst", "getLeft", "getKey", "first", "left")) {
                try {
                    method = pairResult.getClass().getMethod(methodName);
                    Object value = method.invoke(pairResult);
                    if (value instanceof Ref<?> ref) {
                        cachedPairMethod = method;
                        return (Ref<EntityStore>) ref;
                    }
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception e) {
            logger.atWarning().log("Failed to extract entity ref from NPC result: " + e.getMessage());
        }
        return null;
    }

    /**
     * Apply default NPC components: Invulnerable (prevents player damage) and Frozen (disables AI movement).
     */
    public static void setupNpcDefaults(Store<EntityStore> store, Ref<EntityStore> entityRef, HytaleLogger logger) {
        try {
            store.addComponent(entityRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        } catch (Exception e) {
            logger.atWarning().log("Failed to make NPC invulnerable: " + e.getMessage());
        }
        try {
            store.addComponent(entityRef, Frozen.getComponentType(), Frozen.get());
        } catch (Exception e) {
            logger.atWarning().log("Failed to freeze NPC: " + e.getMessage());
        }
    }

    /**
     * Safely initialize NPCPlugin, returning null if unavailable.
     */
    public static NPCPlugin initNpcPlugin(HytaleLogger logger, String npcTypeName) {
        try {
            return NPCPlugin.get();
        } catch (Exception e) {
            logger.atWarning().log("NPCPlugin not available, " + npcTypeName + " will be invisible: " + e.getMessage());
            return null;
        }
    }

    /**
     * Remove an entity from its store. Returns true if removal succeeded.
     */
    public static boolean despawnEntity(Ref<EntityStore> entityRef, HytaleLogger logger) {
        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store != null) {
                store.removeEntity(entityRef, RemoveReason.REMOVE);
                return true;
            }
        } catch (Exception e) {
            logger.atWarning().log("Failed to despawn NPC: " + e.getMessage());
        }
        return false;
    }
}
