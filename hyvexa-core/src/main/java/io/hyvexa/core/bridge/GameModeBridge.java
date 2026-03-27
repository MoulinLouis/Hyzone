package io.hyvexa.core.bridge;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-module bridge for game mode interactions. Modules register handlers here
 * so other modules can invoke them without direct dependencies or reflection.
 */
public final class GameModeBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<String, InteractionHandler> handlers = new ConcurrentHashMap<>();

    /** Key for the parkour restart-checkpoint handler. */
    public static final String PARKOUR_RESTART_CHECKPOINT = "parkour:restart_checkpoint";

    /** Key for opening the RunOrFall leaderboard page. */
    public static final String RUNORFALL_OPEN_LEADERBOARD = "runorfall:open_leaderboard";

    /** Key for opening the RunOrFall stats page. */
    public static final String RUNORFALL_OPEN_STATS = "runorfall:open_stats";

    /** Key for joining the RunOrFall lobby. */
    public static final String RUNORFALL_JOIN_LOBBY = "runorfall:join_lobby";

    /** Key for leaving the RunOrFall lobby. */
    public static final String RUNORFALL_LEAVE_LOBBY = "runorfall:leave_lobby";

    /**
     * Handler for a cross-module interaction.
     */
    @FunctionalInterface
    public interface InteractionHandler {
        void handle(Ref<EntityStore> ref, boolean firstRun, float time,
                    InteractionType type, InteractionContext interactionContext);
    }

    public static void register(String key, InteractionHandler handler) {
        handlers.put(key, handler);
        LOGGER.atInfo().log("GameModeBridge: registered handler '" + key + "'");
    }

    /**
     * Invokes a registered handler. Returns true if a handler was found and invoked.
     */
    public static boolean invoke(String key, Ref<EntityStore> ref, boolean firstRun, float time,
                                 InteractionType type, InteractionContext interactionContext) {
        InteractionHandler handler = handlers.get(key);
        if (handler == null) {
            LOGGER.atWarning().log("GameModeBridge: no handler registered for '" + key + "'");
            return false;
        }
        handler.handle(ref, firstRun, time, type, interactionContext);
        return true;
    }

    public static void unregister(String key) {
        if (handlers.remove(key) != null) {
            LOGGER.atInfo().log("GameModeBridge: unregistered handler '" + key + "'");
        }
    }

    public static void clear() {
        handlers.clear();
    }

    private GameModeBridge() {
    }
}
