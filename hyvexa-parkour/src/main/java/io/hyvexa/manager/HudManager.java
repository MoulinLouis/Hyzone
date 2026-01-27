package io.hyvexa.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.parkour.tracker.HiddenRunHud;
import io.hyvexa.parkour.tracker.RunHud;
import io.hyvexa.parkour.tracker.RunRecordsHud;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.duel.DuelTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Manages player run HUDs: timer display, checkpoint progress, leaderboard overlay. */
public class HudManager {

    private static final String SERVER_IP_DISPLAY = "play.hyvexa.com";

    private final ConcurrentHashMap<UUID, RunHud> runHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, RunRecordsHud> runRecordHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, HiddenRunHud> hiddenRunHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> runHudIsRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> runHudReadyAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> runHudWasRunning = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> runHudHidden = new ConcurrentHashMap<>();

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

    public void ensureRunHud(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        runHuds.remove(playerId);
        runHudReadyAt.remove(playerId);
        runHudWasRunning.remove(playerId);
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> ensureRunHudNow(ref, store, playerRef), world);
    }

    public void ensureRunHudNow(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (playerRef == null || isRunHudHidden(playerRef.getUuid())) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        RunHud hud = getOrCreateHud(playerRef, false);
        attachHud(playerRef, player, hud, false);
        runHudReadyAt.putIfAbsent(playerRef.getUuid(), System.currentTimeMillis() + 250L);
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
        boolean duelActive = duelTracker != null && duelTracker.isInMatch(playerId);
        Long elapsedMs = duelActive ? duelTracker.getElapsedTimeMs(playerId) : runTracker.getElapsedTimeMs(playerId);
        RunHud hud = getActiveHud(playerRef);
        long readyAt = runHudReadyAt.getOrDefault(playerId, Long.MAX_VALUE);
        if (hud == null || System.currentTimeMillis() < readyAt) {
            return;
        }
        boolean running = elapsedMs != null;
        boolean wasRunning = runHudWasRunning.getOrDefault(playerId, false);
        if (!running && wasRunning) {
            RunHud baseHud = getOrCreateHud(playerRef, false);
            attachHud(playerRef, player, baseHud, false);
            hud = baseHud;
        } else if (running && !Boolean.TRUE.equals(runHudIsRecords.get(playerId))) {
            RunHud recordsHud = getOrCreateHud(playerRef, true);
            attachHud(playerRef, player, recordsHud, true);
            hud = recordsHud;
        }
        int completedMaps = progressStore.getCompletedMapCount(playerRef.getUuid());
        int totalMaps = mapStore.getMapCount();
        hud.updateInfo(playerRef.getUsername(), rankName, completedMaps, totalMaps, SERVER_IP_DISPLAY);
        if (!running) {
            if (wasRunning) {
                hud.updateText("");
                hud.updateCheckpointText("");
                runHudWasRunning.put(playerRef.getUuid(), false);
            }
            if (hud instanceof RunRecordsHud recordsHud) {
                recordsHud.updateTopTimes(List.of());
            }
            return;
        }
        runHudWasRunning.put(playerId, true);
        String mapId = duelActive ? duelTracker.getActiveMapId(playerId) : runTracker.getActiveMapId(playerId);
        String mapName = mapId;
        if (mapId != null) {
            var map = mapStore.getMap(mapId);
            if (map != null && map.getName() != null && !map.getName().isBlank()) {
                mapName = map.getName();
            }
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
        } else {
            hud.updateText(timeText);
        }
        hud.updateCheckpointText(checkpointText);
    }

    public void hideRunHud(PlayerRef playerRef) {
        setRunHudHidden(playerRef, true);
    }

    public void showRunHud(PlayerRef playerRef) {
        setRunHudHidden(playerRef, false);
    }

    public boolean isRunHudHidden(UUID playerId) {
        return playerId != null && Boolean.TRUE.equals(runHudHidden.get(playerId));
    }

    RunHud getActiveHud(PlayerRef playerRef) {
        if (playerRef == null) {
            return null;
        }
        UUID playerId = playerRef.getUuid();
        if (Boolean.TRUE.equals(runHudIsRecords.get(playerId))) {
            return runRecordHuds.get(playerId);
        }
        return runHuds.get(playerId);
    }

    RunHud getOrCreateHud(PlayerRef playerRef, boolean records) {
        UUID playerId = playerRef.getUuid();
        if (records) {
            return runRecordHuds.computeIfAbsent(playerId, ignored -> new RunRecordsHud(playerRef));
        }
        return runHuds.computeIfAbsent(playerId, ignored -> new RunHud(playerRef));
    }

    void attachHud(PlayerRef playerRef, Player player, RunHud hud, boolean records) {
        runHudIsRecords.put(playerRef.getUuid(), records);
        player.getHudManager().setCustomHud(playerRef, hud);
        hud.resetCache();
        hud.show();
    }

    private void setRunHudHidden(PlayerRef playerRef, boolean hidden) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        if (hidden) {
            runHudHidden.put(playerId, true);
        } else {
            runHudHidden.remove(playerId);
        }
        runHudReadyAt.remove(playerId);
        runHudWasRunning.remove(playerId);
        runHudIsRecords.remove(playerId);
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
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
        }, world);
    }

    private void attachHiddenHud(PlayerRef playerRef, Player player) {
        if (playerRef == null || player == null) {
            return;
        }
        if (playerRef.getPacketHandler() == null) {
            return;
        }
        HiddenRunHud hud = getOrCreateHiddenHud(playerRef);
        player.getHudManager().setCustomHud(playerRef, hud);
    }

    private HiddenRunHud getOrCreateHiddenHud(PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        return hiddenRunHuds.computeIfAbsent(playerId, ignored -> new HiddenRunHud(playerRef));
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
        runHuds.remove(playerId);
        runRecordHuds.remove(playerId);
        hiddenRunHuds.remove(playerId);
        runHudReadyAt.remove(playerId);
        runHudWasRunning.remove(playerId);
        runHudIsRecords.remove(playerId);
        runHudHidden.remove(playerId);
    }

    public void sweepStalePlayers(Set<UUID> onlinePlayers) {
        runHuds.keySet().removeIf(id -> !onlinePlayers.contains(id));
        runRecordHuds.keySet().removeIf(id -> !onlinePlayers.contains(id));
        hiddenRunHuds.keySet().removeIf(id -> !onlinePlayers.contains(id));
        runHudIsRecords.keySet().removeIf(id -> !onlinePlayers.contains(id));
        runHudReadyAt.keySet().removeIf(id -> !onlinePlayers.contains(id));
        runHudWasRunning.keySet().removeIf(id -> !onlinePlayers.contains(id));
        runHudHidden.keySet().removeIf(id -> !onlinePlayers.contains(id));
    }
}
