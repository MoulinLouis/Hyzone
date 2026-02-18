package io.hyvexa.common.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.lang.reflect.Method;

/**
 * Bridge to MultipleHUD plugin. When MultipleHUD is present, routes HUD
 * registration through its API so multiple plugins' HUDs coexist (e.g. Hyguns ammo + Hyvexa).
 * Falls back to direct {@code player.getHudManager().setCustomHud()} when not available.
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
                // Skip if the composite already has our exact HUD object registered
                CustomUIHud current = player.getHudManager().getCustomHud();
                if (current != null && multipleCustomUIHudClass.isInstance(current)) {
                    Object existing = getHudMethod.invoke(current, KEY);
                    if (existing == hud) {
                        return;
                    }
                }

                Object instance = getInstanceMethod.invoke(null);
                setCustomHudMethod.invoke(instance, player, playerRef, KEY, hud);
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
        if (!available) {
            hud.show();
        }
    }
}
