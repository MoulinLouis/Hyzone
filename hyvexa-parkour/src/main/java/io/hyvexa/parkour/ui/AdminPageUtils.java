package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.parkour.data.GlobalMessageStore;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.MedalRewardStore;
import io.hyvexa.parkour.data.PlayerCountStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared utility for admin pages that navigate back to the admin index.
 */
public final class AdminPageUtils {

    private AdminPageUtils() {
    }

    /**
     * Opens the {@link AdminIndexPage} for the given player with explicitly provided stores.
     */
    public static void openIndex(Ref<EntityStore> ref, Store<EntityStore> store,
                                 MapStore mapStore, ProgressStore progressStore,
                                 SettingsStore settingsStore, PlayerCountStore playerCountStore,
                                 MedalRewardStore medalRewardStore, GlobalMessageStore globalMessageStore,
                                 Runnable refreshAnnouncementsCallback,
                                 BiConsumer<String, PlayerRef> broadcastAnnouncement,
                                 Consumer<UUID> invalidateRankCache,
                                 Function<String, List<String>> hologramLinesBuilder) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new AdminIndexPage(playerRef, mapStore, progressStore, settingsStore, playerCountStore,
                        medalRewardStore, globalMessageStore, refreshAnnouncementsCallback,
                        broadcastAnnouncement, invalidateRankCache, hologramLinesBuilder));
    }

    /**
     * Creates a callback that, when invoked with a ref and store, opens the admin index page.
     * Admin sub-pages can store this callback and use it for back-navigation without
     * needing to carry all the admin stores themselves.
     */
    public static BiConsumer<Ref<EntityStore>, Store<EntityStore>> createOpenIndexCallback(
            MapStore mapStore, ProgressStore progressStore,
            SettingsStore settingsStore, PlayerCountStore playerCountStore,
            MedalRewardStore medalRewardStore, GlobalMessageStore globalMessageStore,
            Runnable refreshAnnouncementsCallback,
            BiConsumer<String, PlayerRef> broadcastAnnouncement,
            Consumer<UUID> invalidateRankCache,
            Function<String, List<String>> hologramLinesBuilder) {
        return (ref, store) -> openIndex(ref, store, mapStore, progressStore, settingsStore,
                playerCountStore, medalRewardStore, globalMessageStore,
                refreshAnnouncementsCallback, broadcastAnnouncement,
                invalidateRankCache, hologramLinesBuilder);
    }
}
