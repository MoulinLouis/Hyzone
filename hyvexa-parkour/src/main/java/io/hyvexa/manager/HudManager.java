package io.hyvexa.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.AsyncExecutionHelper;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.MultiHudBridge;
import io.hyvexa.parkour.tracker.HiddenRunHud;
import io.hyvexa.parkour.tracker.RunHud;
import io.hyvexa.parkour.tracker.RunRecordsHud;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.util.PlayerSettingsStore;
import io.hyvexa.duel.DuelTracker;
import io.hyvexa.core.economy.VexaStore;
import io.hyvexa.parkour.data.FeatherStore;
import io.hyvexa.parkour.data.Medal;
import io.hyvexa.parkour.data.MedalStore;
import io.hyvexa.parkour.ParkourTimingConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Manages player run HUDs: timer display, checkpoint progress, leaderboard overlay. */
public class HudManager {

    private static final String SERVER_IP_DISPLAY = "play.hyvexa.com (/vote)";

    private final ConcurrentHashMap<UUID, PlayerHudState> playerStates = new ConcurrentHashMap<>();

    private final ProgressStore progressStore;
    private final MapStore mapStore;
    private final RunTracker runTracker;
    private final DuelTracker duelTracker;
    private final PlayerPerksManager perksManager;

    public HudManager(ProgressStore progressStore, MapStore mapStore, RunTracker runTracker, DuelTracker duelTracker,
                      PlayerPerksManager perksManager) {
        this.progressStore = progressStore;
        this.mapStore = mapStore;
        this.runTracker = runTracker;
        this.duelTracker = duelTracker;
        this.perksManager = perksManager;
    }

    private PlayerHudState getState(UUID playerId) {
        return playerStates.computeIfAbsent(playerId, k -> new PlayerHudState());
    }

    public void ensureRunHud(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        PlayerHudState state = getState(playerId);
        state.runHud = null;
        state.readyAt = 0;
        state.wasRunning = false;
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        String worldName = world.getName() != null ? world.getName() : "unknown";
        String playerIdText = playerId != null ? playerId.toString() : "unknown";
        AsyncExecutionHelper.runBestEffort(world, () -> ensureRunHudNow(ref, store, playerRef),
                "parkour.hud.ensure", "ensure run HUD",
                "player=" + playerIdText + ", world=" + worldName);
    }

    public void ensureRunHudNow(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (playerRef == null || isRunHudHidden(playerRef.getUuid())) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        PlayerHudState state = getState(playerRef.getUuid());
        // Respect the current HUD mode (base vs records) to avoid rebuilding the
        // MultipleHUD composite every tick. Only do full attach on first setup.
        RunHud hud = getOrCreateHud(playerRef, state.isRecords);
        if (state.readyAt == 0) {
            attachHud(playerRef, player, hud, state.isRecords);
            state.readyAt = System.currentTimeMillis() + 250L;
        } else {
            // Lightweight re-registration: handles engine HUD resets after teleport
            // without resetting cache or triggering composite rebuilds
            MultiHudBridge.setCustomHud(player, playerRef, hud);
        }
    }

    public void updateRunHud(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || store == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null || progressStore == null || mapStore == null || runTracker == null) {
            return;
        }
        String rankName = perksManager != null ? perksManager.getCachedRankName(playerRef.getUuid())
                : progressStore.getRankName(playerRef.getUuid(), mapStore);
        if (perksManager != null) {
            perksManager.updatePlayerNameplate(ref, store, playerRef, rankName);
        }
        if (isRunHudHidden(playerRef.getUuid())) {
            attachHiddenHud(playerRef, player);
            return;
        }
        UUID playerId = playerRef.getUuid();
        PlayerHudState state = getState(playerId);
        boolean duelActive = duelTracker != null && duelTracker.isInMatch(playerId);
        Long elapsedMs = duelActive ? duelTracker.getElapsedTimeMs(playerId) : runTracker.getElapsedTimeMs(playerId);
        RunHud hud = getActiveHud(playerRef);
        if (hud == null || (state.readyAt != 0 && System.currentTimeMillis() < state.readyAt)) {
            return;
        }
        boolean running = elapsedMs != null;
        boolean wasRunning = state.wasRunning;
        if (!running && wasRunning) {
            RunHud baseHud = getOrCreateHud(playerRef, false);
            attachHud(playerRef, player, baseHud, false);
            hud = baseHud;
        } else if (running && !state.isRecords) {
            RunHud recordsHud = getOrCreateHud(playerRef, true);
            attachHud(playerRef, player, recordsHud, true);
            hud = recordsHud;
        }
        int completedMaps = progressStore.getCompletedMapCount(playerRef.getUuid());
        int totalMaps = mapStore.getMapCount();
        hud.updateInfo(playerRef.getUsername(), rankName, completedMaps, totalMaps, SERVER_IP_DISPLAY);
        hud.updatePlayerCount();
        hud.updateVexa(VexaStore.getInstance().getVexa(playerId));
        hud.updateFeathers(FeatherStore.getInstance().getFeathers(playerId));
        updateAdvancedHudData(ref, store, playerRef, hud);
        if (!running) {
            if (wasRunning) {
                hud.updateText("");
                hud.updateCheckpointText("");
                state.wasRunning = false;
            }
            hud.updateCheckpointSplit("", null, false);
            if (hud instanceof RunRecordsHud recordsHud) {
                recordsHud.updateTopTimes(List.of());
                recordsHud.updateMedals(null, null);
            }
            state.checkpointSplit = null;
            updateMedalNotifHud(hud, playerId);
            return;
        }
        state.wasRunning = true;
        String mapId = duelActive ? duelTracker.getActiveMapId(playerId) : runTracker.getActiveMapId(playerId);
        io.hyvexa.parkour.data.Map map = mapId != null ? mapStore.getMap(mapId) : null;
        String mapName = mapId;
        if (map != null && map.getName() != null && !map.getName().isBlank()) {
            mapName = map.getName();
        }
        String timeText = (mapName == null ? "Map" : mapName) + " - " + FormatUtils.formatDuration(elapsedMs);
        RunTracker.CheckpointProgress checkpointProgress = duelActive
                ? duelTracker.getCheckpointProgress(playerId)
                : runTracker.getCheckpointProgress(playerId);
        String checkpointText = "";
        if (checkpointProgress != null && checkpointProgress.total > 0) {
            checkpointText = checkpointProgress.touched + "/" + checkpointProgress.total;
        }
        if (hud instanceof RunRecordsHud recordsHud) {
            recordsHud.updateRunDetails(timeText, buildTopTimes(mapId, playerRef.getUuid()));
            recordsHud.updateMedals(map, MedalStore.getInstance().getEarnedMedals(playerId, mapId));
        } else {
            hud.updateText(timeText);
        }
        hud.updateCheckpointText(checkpointText);
        updateCheckpointSplitHud(playerRef, hud, running, duelActive);
    }

    public void hideRunHud(PlayerRef playerRef) {
        setRunHudHidden(playerRef, true);
    }

    public void showRunHud(PlayerRef playerRef) {
        setRunHudHidden(playerRef, false);
    }

    public boolean isRunHudHidden(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        PlayerHudState s = playerStates.get(playerId);
        return s != null && s.hidden;
    }

    RunHud getActiveHud(PlayerRef playerRef) {
        if (playerRef == null) {
            return null;
        }
        PlayerHudState state = playerStates.get(playerRef.getUuid());
        if (state == null) {
            return null;
        }
        if (state.isRecords) {
            return state.runRecordHud;
        }
        return state.runHud;
    }

    RunHud getOrCreateHud(PlayerRef playerRef, boolean records) {
        PlayerHudState state = getState(playerRef.getUuid());
        if (records) {
            RunRecordsHud hud = state.runRecordHud;
            if (hud == null) {
                hud = new RunRecordsHud(playerRef);
                state.runRecordHud = hud;
            }
            return hud;
        }
        RunHud hud = state.runHud;
        if (hud == null) {
            hud = new RunHud(playerRef);
            state.runHud = hud;
        }
        return hud;
    }

    void attachHud(PlayerRef playerRef, Player player, RunHud hud, boolean records) {
        getState(playerRef.getUuid()).isRecords = records;
        MultiHudBridge.setCustomHud(player, playerRef, hud);
        hud.resetCache();
        MultiHudBridge.showIfNeeded(hud);
    }

    private void setRunHudHidden(PlayerRef playerRef, boolean hidden) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        PlayerHudState state = getState(playerId);
        state.hidden = hidden;
        state.readyAt = 0;
        state.wasRunning = false;
        state.isRecords = false;
        state.checkpointSplit = null;
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        String worldName = world.getName() != null ? world.getName() : "unknown";
        String playerIdText = playerId.toString();
        AsyncExecutionHelper.runBestEffort(world, () -> {
            if (!ref.isValid() || !playerRef.isValid()) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            if (hidden) {
                attachHiddenHud(playerRef, player);
                return;
            }
            ensureRunHudNow(ref, store, playerRef);
        }, "parkour.hud.toggle", "toggle run HUD visibility",
                "player=" + playerIdText + ", hidden=" + hidden + ", world=" + worldName);
    }

    private void attachHiddenHud(PlayerRef playerRef, Player player) {
        if (playerRef == null || player == null) {
            return;
        }
        if (playerRef.getPacketHandler() == null) {
            return;
        }
        HiddenRunHud hud = getOrCreateHiddenHud(playerRef);
        MultiHudBridge.setCustomHud(player, playerRef, hud);
    }

    private HiddenRunHud getOrCreateHiddenHud(PlayerRef playerRef) {
        PlayerHudState state = getState(playerRef.getUuid());
        HiddenRunHud hud = state.hiddenRunHud;
        if (hud == null) {
            hud = new HiddenRunHud(playerRef);
            state.hiddenRunHud = hud;
        }
        return hud;
    }

    private List<RunRecordsHud.RecordLine> buildTopTimes(String mapId, UUID playerId) {
        if (mapId == null || progressStore == null) {
            return List.of();
        }
        List<Map.Entry<UUID, Long>> entries = progressStore.getLeaderboardEntries(mapId);
        if (entries.isEmpty()) {
            return List.of();
        }
        List<RunRecordsHud.RecordLine> lines = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            if (i < entries.size()) {
                UUID entryPlayerId = entries.get(i).getKey();
                Long time = entries.get(i).getValue();
                String name = progressStore.getPlayerName(entryPlayerId);
                if (name == null || name.isBlank()) {
                    name = "Player";
                }
                String trimmed = trimName(name, 14);
                lines.add(new RunRecordsHud.RecordLine(String.valueOf(i + 1), trimmed,
                        FormatUtils.formatDuration(time)));
            } else {
                lines.add(RunRecordsHud.RecordLine.empty(i + 1));
            }
        }
        int selfIndex = -1;
        long selfTime = 0L;
        if (progressStore.getBestTimeMs(playerId, mapId) != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).getKey().equals(playerId)) {
                    selfIndex = i;
                    selfTime = entries.get(i).getValue();
                    break;
                }
            }
        }
        if (selfIndex >= 0) {
            String name = progressStore.getPlayerName(playerId);
            if (name == null || name.isBlank()) {
                name = "Player";
            }
            String trimmed = trimName(name, 14);
            lines.add(new RunRecordsHud.RecordLine(String.valueOf(selfIndex + 1), trimmed,
                    FormatUtils.formatDuration(selfTime)));
        } else {
            lines.add(RunRecordsHud.RecordLine.empty(0));
        }
        return lines;
    }

    private static String trimName(String name, int maxLength) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        if (maxLength <= 3) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed.substring(0, maxLength - 3) + "...";
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        playerStates.remove(playerId);
    }

    public void setAdvancedHudVisible(PlayerRef playerRef, boolean visible) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        if (!visible) {
            PlayerHudState state = playerStates.get(playerId);
            if (state != null) {
                state.lastPositionSample = null;
            }
        }
        RunHud hud = getActiveHud(playerRef);
        if (hud != null) {
            hud.updateAdvancedHud(visible, "", "", "", "");
        }
    }

    private void updateAdvancedHudData(Ref<EntityStore> ref, Store<EntityStore> store,
                                       PlayerRef playerRef, RunHud hud) {
        UUID playerId = playerRef.getUuid();
        boolean enabled = PlayerSettingsStore.isAdvancedHudEnabled(playerId);
        PlayerHudState state = getState(playerId);
        if (!enabled) {
            hud.updateAdvancedHud(false, "", "", "", "");
            state.lastPositionSample = null;
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            hud.updateAdvancedHud(true, "N/A", "N/A", "N/A", "N/A");
            return;
        }
        Vector3f headRotation = playerRef.getHeadRotation();
        float headPitch = headRotation != null ? headRotation.getX() : 0f;
        float headYaw = headRotation != null ? headRotation.getY() : 0f;
        double pitchDeg = Math.toDegrees(headPitch);
        double yawDeg = Math.toDegrees(headYaw);
        String direction = getCardinalDirection((float) yawDeg);
        String orientationText = String.format("(%.1f, %.1f, 0.0) %s", pitchDeg, yawDeg, direction);

        Vector3d position = transform.getPosition();
        double px = position != null ? position.getX() : 0.0;
        double py = position != null ? position.getY() : 0.0;
        double pz = position != null ? position.getZ() : 0.0;
        long now = System.currentTimeMillis();
        PositionSample prev = state.lastPositionSample;
        double vx = 0.0, vy = 0.0, vz = 0.0;
        if (prev != null) {
            long deltaMs = now - prev.timeMs;
            if (deltaMs > 0) {
                double deltaSec = deltaMs / 1000.0;
                vx = (px - prev.x) / deltaSec;
                vy = (py - prev.y) / deltaSec;
                vz = (pz - prev.z) / deltaSec;
            }
        }
        state.lastPositionSample = new PositionSample(px, py, pz, now);
        String velocityText = String.format("(%.3f, %.3f, %.3f)", vx, vy, vz);
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        String speedText = String.format("%.2f", speed);
        String positionText = String.format("(%.1f, %.1f, %.1f)", px, py, pz);
        hud.updateAdvancedHud(true, orientationText, velocityText, speedText, positionText);
    }

    private static String getCardinalDirection(float yaw) {
        float normalized = ((yaw % 360) + 360) % 360;
        if (normalized >= 315 || normalized < 45) {
            return "North";
        } else if (normalized < 135) {
            return "East";
        } else if (normalized < 225) {
            return "South";
        } else {
            return "West";
        }
    }

    public void sweepStalePlayers(Set<UUID> onlinePlayers) {
        playerStates.keySet().removeIf(id -> !onlinePlayers.contains(id));
    }

    public void showCheckpointSplit(PlayerRef playerRef, String splitText, String splitColor) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        PlayerHudState state = getState(playerId);
        if (splitText == null || splitText.isBlank()) {
            state.checkpointSplit = null;
            return;
        }
        long expiresAt = System.currentTimeMillis() + ParkourTimingConstants.CHECKPOINT_SPLIT_HUD_DURATION_MS;
        state.checkpointSplit = new CheckpointSplitHudState(splitText, splitColor, expiresAt);
    }

    private void updateCheckpointSplitHud(PlayerRef playerRef, RunHud hud, boolean running, boolean duelActive) {
        if (playerRef == null || hud == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        PlayerHudState state = playerStates.get(playerId);
        if (!running || duelActive) {
            if (state != null) {
                state.checkpointSplit = null;
            }
            hud.updateCheckpointSplit("", null, false);
            return;
        }
        CheckpointSplitHudState split = state != null ? state.checkpointSplit : null;
        if (split == null) {
            hud.updateCheckpointSplit("", null, false);
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= split.expiresAt) {
            state.checkpointSplit = null;
            hud.updateCheckpointSplit("", null, false);
            return;
        }
        hud.updateCheckpointSplit(split.text, split.color, true);
    }

    private static final class PlayerHudState {
        volatile RunHud runHud;
        volatile RunRecordsHud runRecordHud;
        volatile HiddenRunHud hiddenRunHud;
        volatile boolean isRecords;
        volatile long readyAt;
        volatile boolean wasRunning;
        volatile boolean hidden;
        volatile CheckpointSplitHudState checkpointSplit;
        volatile MedalNotifState medalNotif;
        volatile PositionSample lastPositionSample;
    }

    private static final class CheckpointSplitHudState {
        private final String text;
        private final String color;
        private final long expiresAt;

        private CheckpointSplitHudState(String text, String color, long expiresAt) {
            this.text = text;
            this.color = color;
            this.expiresAt = expiresAt;
        }
    }

    private static final class PositionSample {
        private final double x;
        private final double y;
        private final double z;
        private final long timeMs;

        private PositionSample(double x, double y, double z, long timeMs) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.timeMs = timeMs;
        }
    }

    private static final class MedalNotifState {
        final Medal medal;
        final int feathers;
        final long startedAt;

        MedalNotifState(Medal medal, int feathers, long startedAt) {
            this.medal = medal;
            this.feathers = feathers;
            this.startedAt = startedAt;
        }
    }

    public void showMedalNotification(UUID playerId, Medal medal, int feathers) {
        if (playerId == null || medal == null) {
            return;
        }
        PlayerHudState state = getState(playerId);
        state.medalNotif = new MedalNotifState(medal, feathers, System.currentTimeMillis());
    }

    private void updateMedalNotifHud(RunHud hud, UUID playerId) {
        if (hud == null || playerId == null) {
            return;
        }
        PlayerHudState state = playerStates.get(playerId);
        MedalNotifState notif = state != null ? state.medalNotif : null;

        if (notif == null) {
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#MedalNotif.Visible", false);
            hud.updateMedalNotif(null, cmd);
            return;
        }

        long elapsed = System.currentTimeMillis() - notif.startedAt;
        if (elapsed >= ParkourTimingConstants.MEDAL_NOTIF_DURATION_MS) {
            state.medalNotif = null;
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#MedalNotif.Visible", false);
            hud.updateMedalNotif(null, cmd);
            return;
        }

        boolean iconVisible = elapsed >= 200;
        boolean titleVisible = elapsed >= 500;
        boolean featherVisible = elapsed >= 800 && notif.feathers > 0;
        boolean barVisible = elapsed >= 800;

        // Flash: alternate white/medal color every 100ms during 500-1100ms
        String titleColor;
        if (titleVisible && elapsed < 1100) {
            boolean flashWhite = ((elapsed - 500) / 100) % 2 == 0;
            titleColor = flashWhite ? "#ffffff" : notif.medal.getColor();
        } else {
            titleColor = notif.medal.getColor();
        }

        // Progress bar drains from 800ms to DURATION
        float barValue = 0f;
        if (barVisible) {
            long barElapsed = elapsed - 800;
            long barTotal = ParkourTimingConstants.MEDAL_NOTIF_DURATION_MS - 800;
            barValue = Math.max(0f, 1f - (float) barElapsed / barTotal);
        }

        // Cache key uses elapsed ms directly so every tick sends a fresh bar value
        String cacheKey = notif.medal.name() + "|" + iconVisible + "|" + titleVisible + "|"
                + titleColor + "|" + featherVisible + "|" + barVisible + "|" + elapsed;

        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#MedalNotif.Visible", true);

        // Icon: show only the correct medal's icon
        cmd.set("#MedalNotifBronzeIcon.Visible", iconVisible && notif.medal == Medal.BRONZE);
        cmd.set("#MedalNotifSilverIcon.Visible", iconVisible && notif.medal == Medal.SILVER);
        cmd.set("#MedalNotifGoldIcon.Visible", iconVisible && notif.medal == Medal.GOLD);
        cmd.set("#MedalNotifAuthorIcon.Visible", iconVisible && notif.medal == Medal.AUTHOR);

        // Title
        cmd.set("#MedalNotifTitle.Visible", titleVisible);
        if (titleVisible) {
            cmd.set("#MedalNotifTitle.Text", notif.medal.name() + " MEDAL!");
            cmd.set("#MedalNotifTitle.Style.TextColor", titleColor);
        }

        // Feather row
        cmd.set("#MedalNotifFeatherRow.Visible", featherVisible);
        if (featherVisible) {
            cmd.set("#MedalNotifFeathers.Text", "+" + notif.feathers + " feathers");
        }

        // Progress bar: show only the correct medal's bar
        cmd.set("#MedalNotifBarBronze.Visible", barVisible && notif.medal == Medal.BRONZE);
        cmd.set("#MedalNotifBarSilver.Visible", barVisible && notif.medal == Medal.SILVER);
        cmd.set("#MedalNotifBarGold.Visible", barVisible && notif.medal == Medal.GOLD);
        cmd.set("#MedalNotifBarAuthor.Visible", barVisible && notif.medal == Medal.AUTHOR);
        if (barVisible) {
            String barId = "#MedalNotifBar" + capitalize(notif.medal.name()) + ".Value";
            cmd.set(barId, barValue);
        }

        hud.updateMedalNotif(cacheKey, cmd);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
