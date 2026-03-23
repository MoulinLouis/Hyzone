package io.hyvexa.parkour.interaction;

import io.hyvexa.duel.DuelTracker;
import io.hyvexa.duel.data.DuelPreferenceStore;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.MedalStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.tracker.RunTracker;

/**
 * Narrow static bootstrap for codec-instantiated interactions.
 * Hytale requires no-arg handlers, so these interactions cannot receive
 * constructor injection directly from the plugin composition root.
 */
public final class ParkourInteractionBridge {

    private static volatile Services services;

    private ParkourInteractionBridge() {}

    public static void configure(Services services) {
        ParkourInteractionBridge.services = services;
    }

    public static Services get() {
        return services;
    }

    public static void clear() {
        services = null;
    }

    public record Services(
        MapStore mapStore,
        ProgressStore progressStore,
        RunTracker runTracker,
        DuelTracker duelTracker,
        MedalStore medalStore,
        DuelPreferenceStore duelPreferenceStore
    ) {}
}
