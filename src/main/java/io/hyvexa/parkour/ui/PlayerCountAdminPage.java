package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.PlayerCountStore;
import io.hyvexa.parkour.data.ProgressStore;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class PlayerCountAdminPage extends BaseParkourPage {

    private static final String BUTTON_BACK = "BackButton";
    private static final int MAX_ENTRIES = 200;
    private static final long WINDOW_MS = TimeUnit.HOURS.toMillis(24);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM dd HH:mm")
            .withLocale(Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private final PlayerCountStore playerCountStore;
    private String summaryText = "No player count history yet.";

    public PlayerCountAdminPage(@Nonnull PlayerRef playerRef, PlayerCountStore playerCountStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerCountStore = playerCountStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_PlayerCountAdmin.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);

        List<PlayerCountStore.Sample> samples = getRecentSamples();
        updateSummary(samples);
        populateFields(uiCommandBuilder, samples);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }
        if (BUTTON_BACK.equals(data.getButton())) {
            openIndex(ref, store);
        }
    }

    private void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        MapStore mapStore = plugin.getMapStore();
        ProgressStore progressStore = plugin.getProgressStore();
        player.getPageManager().openCustomPage(ref, store,
                new AdminIndexPage(playerRef, mapStore, progressStore, plugin.getSettingsStore(),
                        plugin.getPlayerCountStore()));
    }

    private List<PlayerCountStore.Sample> getRecentSamples() {
        if (playerCountStore == null) {
            return List.of();
        }
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        List<PlayerCountStore.Sample> samples = playerCountStore.getSamplesSince(cutoff);
        if (samples.size() > MAX_ENTRIES) {
            return new ArrayList<>(samples.subList(samples.size() - MAX_ENTRIES, samples.size()));
        }
        return new ArrayList<>(samples);
    }

    private void updateSummary(List<PlayerCountStore.Sample> samples) {
        if (samples.isEmpty()) {
            summaryText = "No player count history yet.\nSamples are recorded every "
                    + (PlayerCountStore.DEFAULT_SAMPLE_INTERVAL_SECONDS / 60L) + " minutes.";
            return;
        }
        int min = Integer.MAX_VALUE;
        int max = 0;
        int total = 0;
        for (PlayerCountStore.Sample sample : samples) {
            int count = sample.getCount();
            min = Math.min(min, count);
            max = Math.max(max, count);
            total += count;
        }
        PlayerCountStore.Sample latest = samples.get(samples.size() - 1);
        double average = samples.isEmpty() ? 0.0 : (double) total / samples.size();
        summaryText = "Latest: " + latest.getCount() + " players @ " + formatTime(latest.getTimestampMs())
                + "\nPeak: " + max + " | Avg: " + String.format(Locale.ROOT, "%.1f", average) + " | Min: " + min
                + "\nWindow: last 24h, sample every "
                + (PlayerCountStore.DEFAULT_SAMPLE_INTERVAL_SECONDS / 60L) + "m";
    }

    private void populateFields(UICommandBuilder commandBuilder, List<PlayerCountStore.Sample> samples) {
        commandBuilder.set("#SummaryText.Text", summaryText);
        commandBuilder.clear("#SampleCards");
        if (samples.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No samples captured yet.");
            return;
        }
        commandBuilder.set("#EmptyText.Text", "");
        int index = 0;
        for (PlayerCountStore.Sample sample : samples) {
            commandBuilder.append("#SampleCards", "Pages/Parkour_PlayerCountEntry.ui");
            commandBuilder.set("#SampleCards[" + index + "] #SampleTime.Text",
                    formatTime(sample.getTimestampMs()));
            commandBuilder.set("#SampleCards[" + index + "] #SampleBar.Text",
                    String.valueOf(sample.getCount()));
            index++;
        }
    }

    private static String formatTime(long timestampMs) {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(timestampMs));
    }

}
