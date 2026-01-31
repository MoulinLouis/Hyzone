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
    private static final String BUTTON_CLEAR = "ClearButton";
    private static final int MAX_RAW_ENTRIES = 2000;
    private static final int MAX_DISPLAY_ENTRIES = 96;
    private static final int BAR_SEGMENTS = 10;
    private static final long WINDOW_MS = TimeUnit.HOURS.toMillis(24);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM dd HH:mm")
            .withLocale(Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME_ONLY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
            .withLocale(Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private final PlayerCountStore playerCountStore;
    private String summaryText = "No player count history yet.";
    private String windowText = "Last 24 hours";

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
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClearButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLEAR), false);

        List<PlayerCountStore.Sample> samples = getRecentSamples();
        DisplayBuckets buckets = buildDisplayBuckets(samples);
        updateSummary(samples, buckets);
        populateFields(uiCommandBuilder, buckets);
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
            return;
        }
        if (BUTTON_CLEAR.equals(data.getButton())) {
            if (playerCountStore != null) {
                playerCountStore.clearAll();
            }
            openSelf(ref, store);
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

    private void openSelf(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PlayerCountAdminPage(playerRef, playerCountStore));
    }

    private List<PlayerCountStore.Sample> getRecentSamples() {
        if (playerCountStore == null) {
            return List.of();
        }
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        List<PlayerCountStore.Sample> samples = playerCountStore.getSamplesSince(cutoff);
        if (samples.size() > MAX_RAW_ENTRIES) {
            return new ArrayList<>(samples.subList(samples.size() - MAX_RAW_ENTRIES, samples.size()));
        }
        return new ArrayList<>(samples);
    }

    private void updateSummary(List<PlayerCountStore.Sample> samples, DisplayBuckets buckets) {
        if (samples.isEmpty()) {
            summaryText = "No player count history yet.\nSamples are recorded every "
                    + (PlayerCountStore.DEFAULT_SAMPLE_INTERVAL_SECONDS / 60L) + " minutes.";
            windowText = "Last 24 hours";
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
                + "\nSamples: " + samples.size();
        windowText = buckets.bucketMs > buckets.sampleIntervalMs
                ? "Last 24 hours - bucketed to "
                + TimeUnit.MILLISECONDS.toMinutes(buckets.bucketMs) + "m (sample "
                + TimeUnit.MILLISECONDS.toMinutes(buckets.sampleIntervalMs) + "m)"
                : "Last 24 hours";
    }

    private void populateFields(UICommandBuilder commandBuilder, DisplayBuckets buckets) {
        commandBuilder.set("#SummaryText.Text", summaryText);
        commandBuilder.set("#WindowText.Text", windowText);
        commandBuilder.clear("#GraphBars");
        if (buckets.displaySamples.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No samples captured yet.");
            return;
        }
        commandBuilder.set("#EmptyText.Text", "");
        int maxValue = buckets.maxValue;
        int index = 0;
        for (DisplaySample sample : buckets.displaySamples) {
            commandBuilder.append("#GraphBars", "Pages/Parkour_PlayerCountBar.ui");
            commandBuilder.set("#GraphBars[" + index + "] #BarTime.Text", formatBarLabel(sample));
            boolean aggregated = buckets.bucketMs > buckets.sampleIntervalMs;
            commandBuilder.set("#GraphBars[" + index + "] #BarValue.Text", formatBarValue(sample, aggregated));
            int filled = computeFilledSegments(sample, maxValue, buckets.bucketMs > buckets.sampleIntervalMs);
            for (int seg = 1; seg <= BAR_SEGMENTS; seg++) {
                commandBuilder.set("#GraphBars[" + index + "] #Seg" + seg + ".Visible", seg <= filled);
            }
            index++;
        }
    }

    private DisplayBuckets buildDisplayBuckets(List<PlayerCountStore.Sample> samples) {
        long sampleIntervalMs = TimeUnit.SECONDS.toMillis(PlayerCountStore.DEFAULT_SAMPLE_INTERVAL_SECONDS);
        long bucketMs = sampleIntervalMs;
        if (samples.size() > MAX_DISPLAY_ENTRIES) {
            double desiredBuckets = Math.max(1, MAX_DISPLAY_ENTRIES);
            long desiredBucketMs = (long) Math.ceil((double) WINDOW_MS / desiredBuckets);
            long multiples = (long) Math.ceil((double) desiredBucketMs / sampleIntervalMs);
            bucketMs = Math.max(sampleIntervalMs, multiples * sampleIntervalMs);
        }

        if (samples.isEmpty()) {
            return new DisplayBuckets(List.of(), bucketMs, sampleIntervalMs, 0);
        }

        List<Bucket> buckets = new ArrayList<>();
        Bucket current = null;
        for (PlayerCountStore.Sample sample : samples) {
            long bucketStart = (sample.getTimestampMs() / bucketMs) * bucketMs;
            if (current == null || current.startMs != bucketStart) {
                current = new Bucket(bucketStart, bucketStart + bucketMs);
                buckets.add(current);
            }
            current.accept(sample);
        }

        List<DisplaySample> displaySamples = new ArrayList<>(buckets.size());
        int maxValue = 0;
        for (Bucket bucket : buckets) {
            DisplaySample displaySample = bucket.toDisplaySample();
            displaySamples.add(displaySample);
            maxValue = Math.max(maxValue, displaySample.max);
        }
        return new DisplayBuckets(displaySamples, bucketMs, sampleIntervalMs, maxValue);
    }

    private static String formatTime(long timestampMs) {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(timestampMs));
    }

    private static int computeFilledSegments(DisplaySample sample, int maxValue, boolean aggregated) {
        int barMax = Math.max(1, maxValue);
        double displayValue = aggregated ? sample.average : sample.latest;
        int filled = (int) Math.round((displayValue / barMax) * BAR_SEGMENTS);
        if (displayValue > 0 && filled == 0) {
            filled = 1;
        }
        return Math.min(BAR_SEGMENTS, Math.max(0, filled));
    }

    private static String formatBarValue(DisplaySample sample, boolean aggregated) {
        if (aggregated) {
            return String.valueOf(Math.round(sample.average));
        }
        return String.valueOf(sample.latest);
    }

    private static String formatBarLabel(DisplaySample sample) {
        return TIME_ONLY_FORMATTER.format(Instant.ofEpochMilli(sample.startMs));
    }

    private static final class DisplayBuckets {
        private final List<DisplaySample> displaySamples;
        private final long bucketMs;
        private final long sampleIntervalMs;
        private final int maxValue;

        private DisplayBuckets(List<DisplaySample> displaySamples, long bucketMs, long sampleIntervalMs, int maxValue) {
            this.displaySamples = displaySamples;
            this.bucketMs = bucketMs;
            this.sampleIntervalMs = sampleIntervalMs;
            this.maxValue = maxValue;
        }
    }

    private static final class DisplaySample {
        private final long startMs;
        private final long endMs;
        private final int min;
        private final int max;
        private final double average;
        private final int latest;
        private final long latestTimestampMs;
        private final boolean aggregated;

        private DisplaySample(long startMs, long endMs, int min, int max, double average, int latest,
                              long latestTimestampMs, boolean aggregated) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.min = min;
            this.max = max;
            this.average = average;
            this.latest = latest;
            this.latestTimestampMs = latestTimestampMs;
            this.aggregated = aggregated;
        }
    }

    private static final class Bucket {
        private final long startMs;
        private final long endMs;
        private int min = Integer.MAX_VALUE;
        private int max = 0;
        private int total = 0;
        private int count = 0;
        private int latest = 0;
        private long latestTimestampMs = 0L;

        private Bucket(long startMs, long endMs) {
            this.startMs = startMs;
            this.endMs = endMs;
        }

        private void accept(PlayerCountStore.Sample sample) {
            int value = sample.getCount();
            min = Math.min(min, value);
            max = Math.max(max, value);
            total += value;
            count++;
            if (sample.getTimestampMs() >= latestTimestampMs) {
                latestTimestampMs = sample.getTimestampMs();
                latest = value;
            }
        }

        private DisplaySample toDisplaySample() {
            int safeMin = min == Integer.MAX_VALUE ? 0 : min;
            double avg = count == 0 ? 0.0 : (double) total / count;
            boolean aggregated = count > 1;
            return new DisplaySample(startMs, endMs, safeMin, max, avg, latest, latestTimestampMs, aggregated);
        }
    }
}
