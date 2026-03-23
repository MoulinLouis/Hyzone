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

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ParkourAdminNavigator {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final SettingsStore settingsStore;
    private final PlayerCountStore playerCountStore;
    private final MedalRewardStore medalRewardStore;
    private final GlobalMessageStore globalMessageStore;
    private final Consumer<UUID> invalidateRankCache;
    private final Runnable refreshAnnouncements;
    private final BiConsumer<String, PlayerRef> broadcastAnnouncement;
    private final Function<String, List<String>> hologramLinesBuilder;

    public ParkourAdminNavigator(MapStore mapStore,
                                 ProgressStore progressStore,
                                 SettingsStore settingsStore,
                                 PlayerCountStore playerCountStore,
                                 MedalRewardStore medalRewardStore,
                                 GlobalMessageStore globalMessageStore,
                                 Consumer<UUID> invalidateRankCache,
                                 Runnable refreshAnnouncements,
                                 BiConsumer<String, PlayerRef> broadcastAnnouncement,
                                 Function<String, List<String>> hologramLinesBuilder) {
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.settingsStore = settingsStore;
        this.playerCountStore = playerCountStore;
        this.medalRewardStore = medalRewardStore;
        this.globalMessageStore = globalMessageStore;
        this.invalidateRankCache = invalidateRankCache;
        this.refreshAnnouncements = refreshAnnouncements;
        this.broadcastAnnouncement = broadcastAnnouncement;
        this.hologramLinesBuilder = hologramLinesBuilder;
    }

    public AdminIndexPage createIndexPage(@Nonnull PlayerRef playerRef) {
        return new AdminIndexPage(playerRef, this);
    }

    public MapAdminPage createMapAdminPage(@Nonnull PlayerRef playerRef) {
        return new MapAdminPage(playerRef, mapStore, progressStore, hologramLinesBuilder, this);
    }

    public ProgressAdminPage createProgressAdminPage(@Nonnull PlayerRef playerRef) {
        return new ProgressAdminPage(playerRef, progressStore, mapStore, invalidateRankCache, this);
    }

    public GlobalMessageAdminPage createGlobalMessageAdminPage(@Nonnull PlayerRef playerRef) {
        return new GlobalMessageAdminPage(playerRef, globalMessageStore, refreshAnnouncements, this);
    }

    public SettingsAdminPage createSettingsAdminPage(@Nonnull PlayerRef playerRef) {
        return new SettingsAdminPage(playerRef, settingsStore, mapStore);
    }

    public AdminPlayersPage createPlayersPage(@Nonnull PlayerRef playerRef) {
        return new AdminPlayersPage(playerRef, mapStore, progressStore);
    }

    public PlaytimeAdminPage createPlaytimePage(@Nonnull PlayerRef playerRef) {
        return new PlaytimeAdminPage(playerRef, progressStore);
    }

    public PlayerCountAdminPage createPlayerCountPage(@Nonnull PlayerRef playerRef) {
        return playerCountStore != null ? new PlayerCountAdminPage(playerRef, playerCountStore) : null;
    }

    public MedalRewardAdminPage createMedalRewardPage(@Nonnull PlayerRef playerRef) {
        return new MedalRewardAdminPage(playerRef, mapStore, progressStore, medalRewardStore);
    }

    public void openIndex(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, createIndexPage(playerRef));
    }

    public MapStore getMapStore() {
        return mapStore;
    }

    public ProgressStore getProgressStore() {
        return progressStore;
    }

    public SettingsStore getSettingsStore() {
        return settingsStore;
    }

    public PlayerCountStore getPlayerCountStore() {
        return playerCountStore;
    }

    public MedalRewardStore getMedalRewardStore() {
        return medalRewardStore;
    }

    public GlobalMessageStore getGlobalMessageStore() {
        return globalMessageStore;
    }

    public Consumer<UUID> getInvalidateRankCache() {
        return invalidateRankCache;
    }

    public Runnable getRefreshAnnouncements() {
        return refreshAnnouncements;
    }

    public BiConsumer<String, PlayerRef> getBroadcastAnnouncement() {
        return broadcastAnnouncement;
    }

    public Function<String, List<String>> getHologramLinesBuilder() {
        return hologramLinesBuilder;
    }
}
