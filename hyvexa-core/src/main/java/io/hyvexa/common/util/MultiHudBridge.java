package io.hyvexa.common.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridge to MultipleHUD plugin. When MultipleHUD is present, routes HUD
 * registration through its API so multiple plugins' HUDs coexist (e.g. Hyguns ammo + Hyvexa).
 * Falls back to direct {@code player.getHudManager().setCustomHud()} when not available.
 *
 * <p>Caches the last-known composite HUD per player so it can be restored after
 * world transitions (the engine resets the player's custom HUD on teleport, which
 * would otherwise discard other plugins' HUD slots like Hyguns).
 */
public final class MultiHudBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String KEY = "hyvexa";

    private static volatile boolean checked;
    private static volatile boolean available;

    // MultipleHUD.getInstance()
    private static Method getInstanceMethod;
    // MultipleHUD.setCustomHud(Player, PlayerRef, String, CustomUIHud)
    private static Method setCustomHudMethod;

    // MultipleCustomUIHud class and get(String) method for skip-if-same optimization
    private static Class<?> multipleCustomUIHudClass;
    private static Method getHudMethod;

    /**
     * Cache of the last-known MultipleCustomUIHud composite per player UUID.
     * When the engine clears the HUD on world switch, we restore this composite
     * so MultipleHUD.setCustomHud sees an existing composite and preserves all keys
     * (including Hyguns) instead of creating a fresh one.
     */
    private static final ConcurrentHashMap<UUID, CustomUIHud> compositeCache = new ConcurrentHashMap<>();

    private MultiHudBridge() {
    }

    private static void init() {
        if (checked) {
            return;
        }
        synchronized (MultiHudBridge.class) {
            if (checked) {
                return;
            }
            try {
                Class<?> multipleHudClass = Class.forName("com.buuz135.mhud.MultipleHUD");
                getInstanceMethod = multipleHudClass.getMethod("getInstance");
                setCustomHudMethod = multipleHudClass.getMethod("setCustomHud",
                        Player.class, PlayerRef.class, String.class, CustomUIHud.class);

                multipleCustomUIHudClass = Class.forName("com.buuz135.mhud.MultipleCustomUIHud");
                getHudMethod = multipleCustomUIHudClass.getMethod("get", String.class);

                available = true;
                LOGGER.atInfo().log("MultipleHUD detected — HUDs will coexist with other plugins");
            } catch (ClassNotFoundException e) {
                available = false;
            } catch (Exception e) {
                LOGGER.atWarning().log("MultipleHUD found but reflection failed: " + e.getMessage());
                available = false;
            }
            checked = true;
        }
    }

    /**
     * Returns true if MultipleHUD is installed and usable.
     */
    public static boolean isAvailable() {
        init();
        return available;
    }

    /**
     * Registers a custom HUD on the player. If MultipleHUD is present, the HUD is added
     * to the composite (preserving other plugins' HUDs like Hyguns). Otherwise falls back
     * to direct {@code setCustomHud()}.
     *
     * <p>When MultipleHUD is used, the HUD is automatically built and sent to the client
     * by {@code add()}, so callers should NOT call {@code hud.show()} afterwards.
     * Use {@link #showIfNeeded(CustomUIHud)} for that.
     */
    public static void setCustomHud(Player player, PlayerRef playerRef, CustomUIHud hud) {
        init();
        if (available) {
            try {
                CustomUIHud current = player.getHudManager().getCustomHud();

                // Skip if the composite already has our exact HUD object registered
                if (current != null && multipleCustomUIHudClass.isInstance(current)) {
                    Object existing = getHudMethod.invoke(current, KEY);
                    if (existing == hud) {
                        return;
                    }
                }

                // After a world switch the engine resets the player's custom HUD,
                // so the composite (with Hyguns etc.) is gone. Restore the cached
                // composite first so MultipleHUD sees it and just updates our key
                // instead of creating a brand-new composite without Hyguns.
                UUID playerId = playerRef.getUuid();
                if (playerId != null
                        && (current == null || !multipleCustomUIHudClass.isInstance(current))) {
                    CustomUIHud cached = compositeCache.get(playerId);
                    if (cached != null && multipleCustomUIHudClass.isInstance(cached)) {
                        player.getHudManager().setCustomHud(playerRef, cached);
                    }
                }

                Object instance = getInstanceMethod.invoke(null);
                setCustomHudMethod.invoke(instance, player, playerRef, KEY, hud);

                // Cache the (possibly new) composite for future world transitions
                if (playerId != null) {
                    CustomUIHud afterSet = player.getHudManager().getCustomHud();
                    if (afterSet != null && multipleCustomUIHudClass.isInstance(afterSet)) {
                        compositeCache.put(playerId, afterSet);
                    }
                }
                return;
            } catch (Exception e) {
                LOGGER.atWarning().log("MultipleHUD call failed, falling back to direct HUD: " + e.getMessage());
            }
        }
        player.getHudManager().setCustomHud(playerRef, hud);
    }

    /**
     * Calls {@code hud.show()} only when MultipleHUD is NOT handling the display.
     * With MultipleHUD, {@code add()} already builds and sends the HUD to the client.
     */
    public static void showIfNeeded(CustomUIHud hud) {
        init();
        if (!available) {
            hud.show();
        }
    }

    /**
     * Evicts cached composite for a disconnecting player to prevent memory leaks.
     */
    public static void evictPlayer(UUID playerId) {
        if (playerId != null) {
            compositeCache.remove(playerId);
        }
    }
}
