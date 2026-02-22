package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.protocol.packets.connection.PongType;
import com.hypixel.hytale.metrics.metric.HistoricMetric;
import io.hyvexa.common.util.SystemMessageUtils;

import java.util.concurrent.TimeUnit;

/** Handles ping snapshot recording and latency warnings for parkour runs. */
class PingTracker {

    private static final long PING_DELTA_THRESHOLD_MS = 50L;

    void recordStartPing(RunTracker.ActiveRun run, PlayerRef playerRef) {
        if (run == null || playerRef == null || run.startPingMs != null) {
            return;
        }
        run.startPingMs = readPingMs(playerRef);
    }

    void recordFinishPing(RunTracker.ActiveRun run, PlayerRef playerRef) {
        if (run == null || playerRef == null || run.finishPingMs != null) {
            return;
        }
        run.finishPingMs = readPingMs(playerRef);
    }

    void resetPingSnapshots(RunTracker.ActiveRun run) {
        if (run == null) {
            return;
        }
        run.startPingMs = null;
        run.finishPingMs = null;
    }

    void sendLatencyWarning(RunTracker.ActiveRun run, Player player) {
        if (player == null || run == null) {
            return;
        }
        Long startPingMs = run.startPingMs;
        Long finishPingMs = run.finishPingMs;
        String startText = startPingMs != null ? startPingMs + "ms" : "N/A";
        String finishText = finishPingMs != null ? finishPingMs + "ms" : "N/A";
        Message message = SystemMessageUtils.withParkourPrefix(
                Message.raw("Ping ").color(SystemMessageUtils.SECONDARY),
                Message.raw("(start ").color(SystemMessageUtils.SECONDARY),
                Message.raw(startText).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(", finish ").color(SystemMessageUtils.SECONDARY),
                Message.raw(finishText).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(")").color(SystemMessageUtils.SECONDARY)
        );
        player.sendMessage(message);
        if (startPingMs == null || finishPingMs == null) {
            return;
        }
        long deltaMs = Math.abs(finishPingMs - startPingMs);
        if (deltaMs <= PING_DELTA_THRESHOLD_MS) {
            return;
        }
        player.sendMessage(SystemMessageUtils.withParkourPrefix(
                Message.raw("Notice: ").color(SystemMessageUtils.WARN),
                Message.raw("Latency changed significantly during your run; timing may be less accurate.")
                        .color(SystemMessageUtils.WARN)
        ));
    }

    private Long readPingMs(PlayerRef playerRef) {
        if (playerRef == null) {
            return null;
        }
        PacketHandler handler = playerRef.getPacketHandler();
        if (handler == null) {
            return null;
        }
        PacketHandler.PingInfo pingInfo = handler.getPingInfo(PongType.Tick);
        if (pingInfo == null) {
            return null;
        }
        HistoricMetric metric = pingInfo.getPingMetricSet();
        if (metric == null) {
            return null;
        }
        double avg = metric.getAverage(0);
        double avgMs = convertPingToMs(avg, PacketHandler.PingInfo.TIME_UNIT);
        if (!Double.isFinite(avgMs) || avgMs <= 0.0) {
            return null;
        }
        return Math.round(avgMs);
    }

    private static double convertPingToMs(double value, TimeUnit unit) {
        if (unit == null) {
            return value;
        }
        double unitToMs = unit.toNanos(1L) / 1_000_000.0;
        return value * unitToMs;
    }
}
