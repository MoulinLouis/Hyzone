package io.hyvexa.runorfall.interaction;

import io.hyvexa.runorfall.manager.RunOrFallGameManager;
import io.hyvexa.runorfall.manager.RunOrFallStatsStore;

import java.util.UUID;

/**
 * Narrow static bootstrap for codec-instantiated interactions.
 * Hytale requires no-arg handlers, so these interactions cannot receive
 * constructor injection directly from the plugin composition root.
 * Also used by settings/profile pages that need HUD control without
 * accessing the plugin singleton.
 */
public final class RunOrFallInteractionBridge {

    private static volatile Services services;

    private RunOrFallInteractionBridge() {}

    public static void configure(Services services) {
        RunOrFallInteractionBridge.services = services;
    }

    public static Services get() {
        return services;
    }

    public static void clear() {
        services = null;
    }

    public record Services(
        RunOrFallGameManager gameManager,
        RunOrFallStatsStore statsStore,
        HudController hudController
    ) {}

    public interface HudController {
        void hideHud(UUID playerId);
        void showHud(UUID playerId);
        boolean isHudHidden(UUID playerId);
    }
}
