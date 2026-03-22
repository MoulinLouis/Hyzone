package io.hyvexa.common.ghost;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GhostRecordingTest {

    @Test
    void getCompletionTimeMsReturnsConfiguredRecordingDuration() {
        GhostRecording recording = new GhostRecording(List.of(
            new GhostSample(0, 0, 0, 0f, 0),
            new GhostSample(10, 0, 0, 90f, 1_000)
        ), 1_250);

        assertEquals(1_250L, recording.getCompletionTimeMs());
    }

    @Test
    void interpolateAtDelegatesThroughGhostInterpolation() {
        GhostRecording recording = new GhostRecording(List.of(
            new GhostSample(0, 0, 0, 0f, 0),
            new GhostSample(10, 10, 10, 90f, 1_000)
        ), 1_000);

        GhostSample mid = recording.interpolateAt(0.5);

        assertEquals(5.0, mid.x(), 0.0001);
        assertEquals(5.0, mid.y(), 0.0001);
        assertEquals(5.0, mid.z(), 0.0001);
        assertEquals(45.0f, mid.yaw(), 0.0001f);
        assertEquals(500L, mid.timestampMs());
    }

    @Test
    void interpolateAtReturnsEmptySampleForEmptyRecordings() {
        GhostRecording recording = new GhostRecording(List.of(), 0);

        GhostSample sample = recording.interpolateAt(0.75);

        assertEquals(0.0, sample.x(), 0.0001);
        assertEquals(0.0, sample.y(), 0.0001);
        assertEquals(0.0, sample.z(), 0.0001);
        assertEquals(0.0f, sample.yaw(), 0.0001f);
        assertEquals(0L, sample.timestampMs());
    }
}
