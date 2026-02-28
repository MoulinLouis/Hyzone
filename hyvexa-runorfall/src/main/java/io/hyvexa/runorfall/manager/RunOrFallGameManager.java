package io.hyvexa.runorfall.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.runorfall.HyvexaRunOrFallPlugin;
import io.hyvexa.runorfall.data.RunOrFallConfig;
import io.hyvexa.runorfall.data.RunOrFallLocation;
import io.hyvexa.runorfall.data.RunOrFallMapConfig;
import io.hyvexa.runorfall.data.RunOrFallPlatform;
import io.hyvexa.runorfall.util.RunOrFallFeatherBridge;
import io.hyvexa.runorfall.util.RunOrFallUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// Thread safety: all public methods are synchronized. Concurrent collections
// are used as a safety net in case callbacks run outside the monitor.
public class RunOrFallGameManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PREFIX = "[RunOrFall] ";
    private static final int FORCED_COUNTDOWN_SECONDS = 10;
    private static final long GAME_TICK_MS = 50L;
    private static final long START_BLOCK_BREAK_GRACE_MS = 3000L;
    private static final long FEATHER_SURVIVAL_INTERVAL_MS = 60_000L;
    private static final String FEATHER_WORD_COLOR = "#f0c040";
    private static final double PLAYER_FOOTPRINT_RADIUS = 0.37d;
    private static final double PLAYER_BLOCK_DETECTION_Y_OFFSET = 1d;
    private static final String SFX_BLINK_CHARGE_EARNED = "SFX_Avatar_Powers_Enable_Local";
    private static final String SFX_ROUND_WIN = "SFX_Parkour_Victory";
    private static final File BROKEN_BLOCKS_FILE = new File("mods/RunOrFall/broken_blocks.json");
    private static final Gson GSON = new Gson();
    private static final Type BROKEN_BLOCKS_LIST_TYPE = new TypeToken<List<BrokenBlockEntry>>() {}.getType();
    private static final long SAVE_DEBOUNCE_MS = 2000L;

    private enum GameState {
        IDLE,
        COUNTDOWN,
        RUNNING
    }

    private final RunOrFallConfigStore configStore;
    private final RunOrFallStatsStore statsStore;
    private final Set<UUID> lobbyPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> alivePlayers = ConcurrentHashMap.newKeySet();
    private final Map<BlockKey, PendingBlock> pendingBlocks = new ConcurrentHashMap<>();
    private final PriorityQueue<PendingBlockQueueEntry> pendingBlockQueue =
            new PriorityQueue<>(Comparator.comparingLong(entry -> entry.nextAttemptAtMs));
    private final Map<BlockKey, Integer> removedBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, BlockKey> playerLastFootBlock = new ConcurrentHashMap<>();
    private final Map<UUID, Long> roundStartTimesMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextAliveFeatherRewardAtMs = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> brokenBlocksByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> blinkChargesByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> blinksUsedByPlayer = new ConcurrentHashMap<>();
    private final Map<String, Integer> blockItemIdCache = new ConcurrentHashMap<>();

    private volatile GameState state = GameState.IDLE;
    private volatile int countdownRemaining = FORCED_COUNTDOWN_SECONDS;
    private volatile int countdownRequiredPlayers = 2;
    private volatile int countdownOptimalPlayers = 0;
    private volatile int countdownOptimalTimeSeconds = 0;
    private volatile boolean countdownForced = false;
    private volatile RunOrFallMapConfig countdownSelectedMap;
    private volatile boolean soloTestRound = false;
    private volatile ScheduledFuture<?> countdownTask;
    private volatile ScheduledFuture<?> gameTickTask;
    private volatile RunOrFallConfig activeRoundConfig;
    private volatile World activeWorld;
    private volatile long blockBreakEnabledAtMs;
    private volatile int blockBreakCountdownLastAnnounced = -1;
    private final AtomicBoolean brokenBlocksDirty = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> brokenBlocksSaveTask;

    public RunOrFallGameManager(RunOrFallConfigStore configStore, RunOrFallStatsStore statsStore) {
        this.configStore = configStore;
        this.statsStore = statsStore;
    }

    public synchronized boolean isJoined(UUID playerId) {
        return playerId != null && lobbyPlayers.contains(playerId);
    }

    public synchronized boolean isInActiveRound(UUID playerId) {
        return playerId != null && state == GameState.RUNNING && alivePlayers.contains(playerId);
    }

    public synchronized int getBlinkDistanceBlocks() {
        return configStore.getBlinkDistanceBlocks();
    }

    public synchronized boolean canBlinkPassThrough(int x, int y, int z, int blockId) {
        if (blockId == RunOrFallUtils.AIR_BLOCK_ID) {
            return true;
        }
        RunOrFallConfig config = activeRoundConfig;
        if (config == null) {
            config = configStore.snapshot();
        }
        RunOrFallMapConfig selectedMap = resolveSelectedMap(config);
        if (selectedMap == null || selectedMap.platforms == null || selectedMap.platforms.isEmpty()) {
            return false;
        }
        return isInsidePlatform(selectedMap.platforms, x, y, z, blockId);
    }

    public synchronized int getBrokenBlocksCount(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        Integer count = brokenBlocksByPlayer.get(playerId);
        if (count == null) {
            return 0;
        }
        return Math.max(0, count);
    }

    public synchronized int getBlinkCharges(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return Math.max(0, blinkChargesByPlayer.getOrDefault(playerId, 0));
    }

    public synchronized boolean tryConsumeBlinkCharge(UUID playerId) {
        if (playerId == null || state != GameState.RUNNING || !alivePlayers.contains(playerId)) {
            return false;
        }
        int currentCharges = Math.max(0, blinkChargesByPlayer.getOrDefault(playerId, 0));
        if (currentCharges <= 0) {
            return false;
        }
        int nextCharges = currentCharges - 1;
        blinkChargesByPlayer.put(playerId, nextCharges);
        int nextUsedCount = Math.max(0, blinksUsedByPlayer.getOrDefault(playerId, 0)) + 1;
        blinksUsedByPlayer.put(playerId, nextUsedCount);
        updateBlinkChargesHudForPlayer(playerId, nextCharges);
        return true;
    }

    public synchronized String statusLine() {
        String mapId = activeRoundConfig != null ? activeRoundConfig.selectedMapId : configStore.getSelectedMapId();
        return "state=" + state.name()
                + ", map=" + mapId
                + ", lobby=" + lobbyPlayers.size()
                + ", alive=" + alivePlayers.size()
                + ", countdown=" + countdownRemaining
                + "s";
    }

    public synchronized void joinLobby(UUID playerId, World world) {
        if (playerId == null || world == null) {
            return;
        }
        if (activeWorld == null) {
            activeWorld = world;
        } else if (!activeWorld.getName().equalsIgnoreCase(world.getName())) {
            sendToPlayer(playerId, "You must join from world " + activeWorld.getName() + ".");
            return;
        }

        if (!lobbyPlayers.add(playerId)) {
            if (state == GameState.RUNNING) {
                if (alivePlayers.contains(playerId)) {
                    sendToPlayer(playerId, "You are already in the current round.");
                    return;
                }
                ensureBrokenBlocksEntry(playerId);
                teleportPlayerToLobby(playerId);
                refreshPlayerHotbar(playerId);
                updateBrokenBlocksHudForPlayer(playerId);
                updateBlinkChargesHudForPlayer(playerId);
                sendToPlayer(playerId, "Round already running. You are spectating from the lobby.");
                return;
            }
            sendToPlayer(playerId, "You are already in the RunOrFall lobby.");
            ensureBrokenBlocksEntry(playerId);
            updateBrokenBlocksHudForPlayer(playerId);
            updateBlinkChargesHudForPlayer(playerId);
            return;
        }
        ensureBrokenBlocksEntry(playerId);
        sendToPlayer(playerId, "Joined the RunOrFall lobby.");
        teleportPlayerToLobby(playerId);
        refreshPlayerHotbar(playerId);
        updateCountdownHudForPlayer(playerId);
        updateBrokenBlocksHudForPlayer(playerId);
        updateBlinkChargesHudForPlayer(playerId);
        if (state == GameState.RUNNING) {
            sendToPlayer(playerId, "Round already running. You are spectating from the lobby.");
        }
        broadcastLobby("Lobby: " + lobbyPlayers.size() + " player(s).");
        if (state == GameState.IDLE) {
            startAutoCountdownIfPossible();
        } else if (state == GameState.COUNTDOWN && !countdownForced) {
            reduceAutoCountdownForOptimalPopulationIfNeeded();
            updateCountdownMapSelection();
        }
    }

    public synchronized void leaveLobby(UUID playerId, boolean notify) {
        if (playerId == null) {
            return;
        }
        PlayerRemovalResult result = removePlayerInternal(playerId);
        if (!result.wasInLobby() && !result.wasAlive()) {
            return;
        }
        if (notify) {
            sendToPlayer(playerId, "You left the RunOrFall lobby.");
            teleportPlayerToWorldSpawn(playerId);
        }
        updateCountdownHudForPlayer(playerId, null);
        updateBrokenBlocksHudForPlayer(playerId, 0);
        updateBlinkChargesHudForPlayer(playerId, 0);
        if (state == GameState.COUNTDOWN && lobbyPlayers.size() < countdownRequiredPlayers) {
            cancelCountdownInternal("Countdown cancelled: not enough players.");
        } else if (state == GameState.COUNTDOWN) {
            updateCountdownMapSelection();
        }
        if (state == GameState.RUNNING && result.wasAlive()) {
            statsStore.recordLoss(playerId, resolvePlayerName(playerId), result.survivedMs(),
                    result.brokenBlocks(), result.blinksUsed());
            rewardAlivePlayersForEliminationInternal(playerId);
            broadcastEliminationInternal(playerId, "left the round");
            checkWinnerInternal();
        }
        refreshPlayerHotbar(playerId);
        if (lobbyPlayers.isEmpty()) {
            activeWorld = null;
        }
    }

    public synchronized void requestStart() {
        requestStart(false);
    }

    public synchronized void requestStart(boolean allowSolo) {
        if (state == GameState.COUNTDOWN) {
            int requiredPlayers = allowSolo ? 1 : 2;
            if (lobbyPlayers.size() < requiredPlayers) {
                return;
            }
            countdownForced = true;
            countdownRequiredPlayers = requiredPlayers;
            countdownOptimalPlayers = 0;
            countdownOptimalTimeSeconds = 0;
            if (countdownRemaining > FORCED_COUNTDOWN_SECONDS) {
                countdownRemaining = FORCED_COUNTDOWN_SECONDS;
                broadcastLobby("Admin start: countdown forced to " + FORCED_COUNTDOWN_SECONDS + "s.");
                updateCountdownHudForLobbyPlayers();
            }
            return;
        }
        startForcedCountdownIfPossible(allowSolo);
    }

    public synchronized void requestStop(String reason) {
        if (state == GameState.COUNTDOWN) {
            cancelCountdownInternal("Countdown stopped: " + reason);
            return;
        }
        if (state == GameState.RUNNING) {
            endGameInternal("Game stopped: " + reason);
        }
    }

    public synchronized void shutdown() {
        cancelCountdownTask();
        cancelGameTickTask();
        flushBrokenBlocksSave();
        World world = activeWorld;
        if (world != null) {
            try {
                world.execute(this::restoreAllBlocksInternal);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to restore RunOrFall blocks on shutdown.");
                restoreAllBlocksInternal();
            }
        } else {
            restoreAllBlocksInternal();
        }
        activeRoundConfig = null;
        resetCountdownState();
        alivePlayers.clear();
        clearPendingBlocksInternal();
        playerLastFootBlock.clear();
        roundStartTimesMs.clear();
        nextAliveFeatherRewardAtMs.clear();
        brokenBlocksByPlayer.clear();
        blinkChargesByPlayer.clear();
        blinksUsedByPlayer.clear();
        clearCountdownHudForLobbyPlayers();
        lobbyPlayers.clear();
        activeWorld = null;
    }

    public synchronized void handleDisconnect(UUID playerId) {
        if (playerId == null) {
            return;
        }
        PlayerRemovalResult result = removePlayerInternal(playerId);
        if (!result.wasInLobby() && !result.wasAlive()) {
            return;
        }
        if (state == GameState.COUNTDOWN && lobbyPlayers.size() < countdownRequiredPlayers) {
            cancelCountdownInternal("Countdown cancelled: not enough players.");
        } else if (state == GameState.COUNTDOWN) {
            updateCountdownMapSelection();
        }
        if (state == GameState.RUNNING && result.wasAlive()) {
            statsStore.recordLoss(playerId, resolvePlayerName(playerId), result.survivedMs(),
                    result.brokenBlocks(), result.blinksUsed());
            rewardAlivePlayersForEliminationInternal(playerId);
            broadcastEliminationInternal(playerId, "disconnected");
            checkWinnerInternal();
        }
        updateCountdownHudForPlayer(playerId, null);
        updateBrokenBlocksHudForPlayer(playerId, 0);
        updateBlinkChargesHudForPlayer(playerId, 0);
        if (lobbyPlayers.isEmpty()) {
            activeWorld = null;
        }
    }

    private record PlayerRemovalResult(boolean wasInLobby, boolean wasAlive, long survivedMs,
                                       int brokenBlocks, int blinksUsed) {}

    private PlayerRemovalResult removePlayerInternal(UUID playerId) {
        boolean wasInLobby = lobbyPlayers.remove(playerId);
        boolean wasAlive = alivePlayers.remove(playerId);
        playerLastFootBlock.remove(playerId);
        nextAliveFeatherRewardAtMs.remove(playerId);
        int brokenBlocks = getRoundBrokenBlocksCount(playerId);
        int blinksUsed = getRoundBlinksUsedCount(playerId);
        brokenBlocksByPlayer.remove(playerId);
        blinkChargesByPlayer.remove(playerId);
        blinksUsedByPlayer.remove(playerId);
        long survivedMs = takeSurvivedMs(playerId);
        return new PlayerRemovalResult(wasInLobby, wasAlive, survivedMs, brokenBlocks, blinksUsed);
    }

    private void startForcedCountdownIfPossible(boolean allowSoloStart) {
        if (state != GameState.IDLE) {
            return;
        }
        int requiredPlayers = allowSoloStart ? 1 : 2;
        if (lobbyPlayers.size() < requiredPlayers) {
            return;
        }
        World world = activeWorld;
        if (world == null) {
            return;
        }
        state = GameState.COUNTDOWN;
        countdownForced = true;
        countdownRemaining = FORCED_COUNTDOWN_SECONDS;
        countdownRequiredPlayers = requiredPlayers;
        countdownOptimalPlayers = 0;
        countdownOptimalTimeSeconds = 0;
        updateCountdownMapSelection();
        updateCountdownHudForLobbyPlayers();
        startCountdownTask();
    }

    private void startAutoCountdownIfPossible() {
        if (state != GameState.IDLE) {
            return;
        }
        CountdownSettings settings = resolveCountdownSettings(configStore.snapshot());
        if (lobbyPlayers.size() < settings.minPlayers) {
            return;
        }
        World world = activeWorld;
        if (world == null) {
            return;
        }
        state = GameState.COUNTDOWN;
        countdownForced = false;
        countdownRemaining = settings.minPlayersTimeSeconds;
        countdownRequiredPlayers = settings.minPlayers;
        countdownOptimalPlayers = settings.optimalPlayers;
        countdownOptimalTimeSeconds = settings.optimalPlayersTimeSeconds;
        reduceAutoCountdownForOptimalPopulationIfNeeded();
        updateCountdownMapSelection();
        updateCountdownHudForLobbyPlayers();
        startCountdownTask();
    }

    private void startCountdownTask() {
        countdownTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> dispatchToWorld(this::tickCountdownInternal),
                1000L, 1000L, TimeUnit.MILLISECONDS
        );
    }

    private void reduceAutoCountdownForOptimalPopulationIfNeeded() {
        if (countdownForced || state != GameState.COUNTDOWN) {
            return;
        }
        if (lobbyPlayers.size() < countdownOptimalPlayers) {
            return;
        }
        if (countdownRemaining <= countdownOptimalTimeSeconds) {
            return;
        }
        countdownRemaining = countdownOptimalTimeSeconds;
        updateCountdownHudForLobbyPlayers();
        broadcastLobby("Optimal players reached. Countdown reduced to " + countdownRemaining + "s.");
    }

    private void updateCountdownMapSelection() {
        if (state != GameState.COUNTDOWN) {
            return;
        }
        RunOrFallConfig config = configStore.snapshot();
        RunOrFallMapConfig bestMap = resolveAutoSelectedMap(config, lobbyPlayers.size());
        if (bestMap == null || bestMap.lobby == null) {
            return;
        }
        RunOrFallMapConfig currentMap = countdownSelectedMap;
        if (currentMap != null && bestMap.id.equalsIgnoreCase(currentMap.id)) {
            return;
        }
        countdownSelectedMap = bestMap;
        broadcastLobby("Map selected: " + bestMap.id);
        RunOrFallLocation mapLobby = bestMap.lobby.copy();
        for (UUID playerId : lobbyPlayers) {
            teleportPlayer(playerId, mapLobby);
        }
    }

    private void tickCountdownInternal() {
        synchronized (this) {
            if (state != GameState.COUNTDOWN) {
                return;
            }
            if (lobbyPlayers.size() < countdownRequiredPlayers) {
                cancelCountdownInternal("Countdown cancelled: not enough players.");
                return;
            }
            reduceAutoCountdownForOptimalPopulationIfNeeded();
            countdownRemaining--;
            if (countdownRemaining <= 0) {
                cancelCountdownTask();
                startGameInternal();
                return;
            }
            updateCountdownHudForLobbyPlayers();
        }
    }

    private void startGameInternal() {
        if (state != GameState.COUNTDOWN) {
            return;
        }
        List<UUID> onlinePlayers = new ArrayList<>();
        for (UUID playerId : lobbyPlayers) {
            if (resolvePlayer(playerId) != null) {
                onlinePlayers.add(playerId);
            }
        }
        if (onlinePlayers.size() < countdownRequiredPlayers) {
            state = GameState.IDLE;
            activeRoundConfig = null;
            broadcastLobby("Not enough online players to start.");
            return;
        }
        RunOrFallConfig config = configStore.snapshot();
        RunOrFallMapConfig selectedMap = resolveAutoSelectedMap(config, onlinePlayers.size());
        if (selectedMap == null) {
            state = GameState.IDLE;
            activeRoundConfig = null;
            broadcastLobby("No playable map available for lobby size " + onlinePlayers.size() + ".");
            return;
        }
        config.selectedMapId = selectedMap.id;
        activeRoundConfig = config;
        int startingBlinkCharges = resolveBlinkStartCharges(config);

        resetBrokenBlocksForLobbyPlayers();

        alivePlayers.clear();
        alivePlayers.addAll(onlinePlayers);
        long roundStartMs = System.currentTimeMillis();
        roundStartTimesMs.clear();
        for (UUID onlinePlayerId : onlinePlayers) {
            roundStartTimesMs.put(onlinePlayerId, roundStartMs);
            nextAliveFeatherRewardAtMs.put(onlinePlayerId, roundStartMs + FEATHER_SURVIVAL_INTERVAL_MS);
            blinkChargesByPlayer.put(onlinePlayerId, startingBlinkCharges);
        }
        soloTestRound = countdownRequiredPlayers == 1 && onlinePlayers.size() == 1;
        clearPendingBlocksInternal();
        removedBlocks.clear();
        deleteBrokenBlocksFile();
        playerLastFootBlock.clear();

        for (int i = 0; i < onlinePlayers.size(); i++) {
            UUID playerId = onlinePlayers.get(i);
            RunOrFallLocation spawn = selectedMap.spawns.get(i % selectedMap.spawns.size());
            teleportPlayer(playerId, spawn);
        }

        state = GameState.RUNNING;
        clearCountdownHudForLobbyPlayers();
        blockBreakEnabledAtMs = System.currentTimeMillis() + START_BLOCK_BREAK_GRACE_MS;
        blockBreakCountdownLastAnnounced = (int) Math.ceil(START_BLOCK_BREAK_GRACE_MS / 1000.0d);
        for (UUID onlinePlayerId : onlinePlayers) {
            refreshPlayerHotbar(onlinePlayerId);
            updateBrokenBlocksHudForPlayer(onlinePlayerId, 0);
            updateBlinkChargesHudForPlayer(onlinePlayerId, startingBlinkCharges);
        }
        gameTickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> dispatchToWorld(this::tickGameInternal),
                GAME_TICK_MS, GAME_TICK_MS, TimeUnit.MILLISECONDS
        );
        if (soloTestRound) {
            broadcastLobby("RunOrFall solo test started.");
        } else {
            broadcastLobby("RunOrFall started. Last player alive wins.");
        }
        broadcastLobby("Blocks breaking in " + blockBreakCountdownLastAnnounced + "...");
    }

    private void tickGameInternal() {
        synchronized (this) {
            if (state != GameState.RUNNING) {
                return;
            }
            long nowMs = System.currentTimeMillis();
            boolean canBreakBlocks = nowMs >= blockBreakEnabledAtMs;
            if (!canBreakBlocks) {
                broadcastBlockBreakCountdownIfNeeded(nowMs);
            }
            RunOrFallConfig config = activeRoundConfig;
            if (config == null) {
                config = configStore.snapshot();
            }
            RunOrFallMapConfig selectedMap = resolveSelectedMap(config);
            List<RunOrFallPlatform> platforms = selectedMap != null ? selectedMap.platforms : List.of();
            List<UUID> toEliminate = null;
            List<UUID> disconnected = null;
            for (UUID playerId : alivePlayers) {
                PlayerRef playerRef = resolvePlayer(playerId);
                if (playerRef == null) {
                    if (disconnected == null) {
                        disconnected = new ArrayList<>();
                    }
                    disconnected.add(playerId);
                    continue;
                }
                var ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) {
                    if (disconnected == null) {
                        disconnected = new ArrayList<>();
                    }
                    disconnected.add(playerId);
                    continue;
                }
                var store = ref.getStore();
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null || transform.getPosition() == null) {
                    continue;
                }
                Vector3d position = transform.getPosition();
                if (position.getY() < config.voidY) {
                    if (toEliminate == null) {
                        toEliminate = new ArrayList<>();
                    }
                    toEliminate.add(playerId);
                    continue;
                }
                rewardAliveTimeFeathersInternal(playerId, nowMs);

                if (canBreakBlocks) {
                    int blockY = (int) Math.floor(position.getY() - PLAYER_BLOCK_DETECTION_Y_OFFSET);
                    queueFootprintBlocksInternal(playerId, position.getX(), position.getZ(), blockY,
                            platforms, config.blockBreakDelaySeconds);
                }
            }
            if (toEliminate != null) {
                for (UUID playerId : toEliminate) {
                    eliminatePlayerInternal(playerId, "fell into the void");
                }
            }
            processPendingBlocksInternal();
            if (disconnected != null) {
                for (UUID playerId : disconnected) {
                    if (alivePlayers.remove(playerId)) {
                        long survivedMs = takeSurvivedMs(playerId);
                        statsStore.recordLoss(playerId, resolvePlayerName(playerId), survivedMs,
                                getRoundBrokenBlocksCount(playerId), getRoundBlinksUsedCount(playerId));
                        rewardAlivePlayersForEliminationInternal(playerId);
                        broadcastEliminationInternal(playerId, "disconnected");
                    }
                }
            }
            checkWinnerInternal();
        }
    }

    private void checkWinnerInternal() {
        if (state != GameState.RUNNING) {
            return;
        }
        if (soloTestRound) {
            if (!alivePlayers.isEmpty()) {
                return;
            }
            broadcastLobby("Solo test finished.");
            endGameInternal("Round finished.");
            return;
        }
        if (alivePlayers.size() > 1) {
            return;
        }
        UUID winner = alivePlayers.stream().findFirst().orElse(null);
        if (winner != null) {
            PlayerRef winnerRef = resolvePlayer(winner);
            long survivedMs = takeSurvivedMs(winner);
            String winnerName = resolvePlayerName(winner);
            statsStore.recordWin(winner, winnerName, survivedMs,
                    getRoundBrokenBlocksCount(winner), getRoundBlinksUsedCount(winner));
            grantFeathersToPlayer(winner, resolveFeathersForWin(), "for winning the round");
            playSfxForPlayer(winner, SFX_ROUND_WIN);
            if (winnerRef != null && winnerRef.getUsername() != null && !winnerRef.getUsername().isBlank()) {
                winnerName = winnerRef.getUsername();
            }
            teleportPlayerToLobby(winner);
            broadcastLobby("Winner: " + winnerName + " wins the round.");
        } else {
            broadcastLobby("No winner this round.");
        }
        endGameInternal("Round finished.");
    }

    private void eliminatePlayerInternal(UUID playerId, String reason) {
        if (!alivePlayers.remove(playerId)) {
            return;
        }
        long survivedMs = takeSurvivedMs(playerId);
        statsStore.recordLoss(playerId, resolvePlayerName(playerId), survivedMs,
                getRoundBrokenBlocksCount(playerId), getRoundBlinksUsedCount(playerId));
        rewardAlivePlayersForEliminationInternal(playerId);
        playerLastFootBlock.remove(playerId);
        teleportPlayerToLobby(playerId);
        refreshPlayerHotbar(playerId);
        updateBrokenBlocksHudForPlayer(playerId);
        updateBlinkChargesHudForPlayer(playerId);
        sendToPlayer(playerId, "Eliminated: " + reason + ". You are now spectating from the lobby.");
        broadcastEliminationInternal(playerId, reason);
    }

    private void endGameInternal(String reason) {
        cancelGameTickTask();
        activeRoundConfig = null;
        restoreAllBlocksInternal();
        alivePlayers.clear();
        playerLastFootBlock.clear();
        roundStartTimesMs.clear();
        nextAliveFeatherRewardAtMs.clear();
        blinkChargesByPlayer.clear();
        blinksUsedByPlayer.clear();
        resetCountdownState();
        clearCountdownHudForLobbyPlayers();
        resetBrokenBlocksForLobbyPlayers();
        refreshLobbyHotbars();
        broadcastLobby(reason);
        startAutoCountdownIfPossible();
    }

    private void cancelCountdownInternal(String reason) {
        cancelCountdownTask();
        resetCountdownState();
        clearCountdownHudForLobbyPlayers();
        resetBrokenBlocksForLobbyPlayers();
        broadcastLobby(reason);
    }

    private void broadcastBlockBreakCountdownIfNeeded(long nowMs) {
        long remainingMs = blockBreakEnabledAtMs - nowMs;
        if (remainingMs <= 0L) {
            return;
        }
        int remainingSeconds = (int) Math.ceil(remainingMs / 1000.0d);
        if (remainingSeconds <= 0 || remainingSeconds == blockBreakCountdownLastAnnounced) {
            return;
        }
        blockBreakCountdownLastAnnounced = remainingSeconds;
        broadcastLobby("Blocks breaking in " + remainingSeconds + "...");
    }

    private boolean queueBlockRemovalInternal(UUID playerId, int x, int y, int z, double delaySeconds) {
        BlockKey key = new BlockKey(x, y, z);
        if (removedBlocks.containsKey(key) || pendingBlocks.containsKey(key)) {
            return false;
        }
        World world = activeWorld;
        if (world == null) {
            return false;
        }
        Integer currentId = RunOrFallUtils.readBlockId(world, x, y, z);
        if (currentId == null || currentId == RunOrFallUtils.AIR_BLOCK_ID) {
            return false;
        }
        long delayMs = Math.max(0L, Math.round(delaySeconds * 1000.0d));
        long dueAtMs = System.currentTimeMillis() + delayMs;
        PendingBlock pending = new PendingBlock(currentId, dueAtMs, playerId);
        pendingBlocks.put(key, pending);
        pendingBlockQueue.add(new PendingBlockQueueEntry(key, pending, dueAtMs));
        return true;
    }

    private void processPendingBlocksInternal() {
        if (pendingBlocks.isEmpty()) {
            pendingBlockQueue.clear();
            return;
        }
        World world = activeWorld;
        if (world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        while (true) {
            PendingBlockQueueEntry entry = pendingBlockQueue.peek();
            if (entry == null || entry.nextAttemptAtMs > now) {
                return;
            }
            pendingBlockQueue.poll();
            PendingBlock pending = pendingBlocks.get(entry.key);
            if (pending == null || pending != entry.pending) {
                continue;
            }
            BlockKey key = entry.key;
            Integer currentId = RunOrFallUtils.readBlockId(world, key.x, key.y, key.z);
            if (currentId == null) {
                pendingBlockQueue.add(entry.retryAt(now + GAME_TICK_MS));
                continue;
            }
            if (currentId == RunOrFallUtils.AIR_BLOCK_ID) {
                pendingBlocks.remove(key, pending);
                continue;
            }
            if (writeBlockId(world, key.x, key.y, key.z, RunOrFallUtils.AIR_BLOCK_ID)) {
                removedBlocks.put(key, pending.originalBlockId);
                pendingBlocks.remove(key, pending);
                incrementBrokenBlocksCountInternal(pending.playerId);
                scheduleBrokenBlocksSave();
                continue;
            }
            pendingBlockQueue.add(entry.retryAt(now + GAME_TICK_MS));
        }
    }

    private void queueFootprintBlocksInternal(UUID playerId, double centerX, double centerZ, int blockY,
                                              List<RunOrFallPlatform> platforms, double delaySeconds) {
        if (playerId == null) {
            return;
        }
        World world = activeWorld;
        if (world == null) {
            return;
        }

        int minX = (int) Math.floor(centerX - PLAYER_FOOTPRINT_RADIUS);
        int maxX = (int) Math.floor(centerX + PLAYER_FOOTPRINT_RADIUS);
        int minZ = (int) Math.floor(centerZ - PLAYER_FOOTPRINT_RADIUS);
        int maxZ = (int) Math.floor(centerZ + PLAYER_FOOTPRINT_RADIUS);
        BlockKey previousKey = playerLastFootBlock.get(playerId);
        if (previousKey != null
                && previousKey.y == blockY
                && previousKey.x >= minX && previousKey.x <= maxX
                && previousKey.z >= minZ && previousKey.z <= maxZ) {
            Integer previousBlockId = RunOrFallUtils.readBlockId(world, previousKey.x, previousKey.y, previousKey.z);
            if (previousBlockId != null
                    && previousBlockId != RunOrFallUtils.AIR_BLOCK_ID
                    && isInsidePlatform(platforms, previousKey.x, previousKey.y, previousKey.z, previousBlockId)) {
                return;
            }
        }

        BlockKey closestKey = null;
        double closestDistanceSq = Double.MAX_VALUE;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockKey key = new BlockKey(x, blockY, z);
                if (removedBlocks.containsKey(key)) {
                    continue;
                }
                Integer currentId = RunOrFallUtils.readBlockId(world, x, blockY, z);
                if (currentId == null || currentId == RunOrFallUtils.AIR_BLOCK_ID) {
                    continue;
                }
                if (!isInsidePlatform(platforms, x, blockY, z, currentId)) {
                    continue;
                }
                double dx = centerX - (x + 0.5d);
                double dz = centerZ - (z + 0.5d);
                double distanceSq = (dx * dx) + (dz * dz);
                if (distanceSq < closestDistanceSq) {
                    closestDistanceSq = distanceSq;
                    closestKey = key;
                }
            }
        }
        if (closestKey == null) {
            playerLastFootBlock.remove(playerId);
            return;
        }
        playerLastFootBlock.put(playerId, closestKey);
        if (pendingBlocks.containsKey(closestKey)) {
            return;
        }
        queueBlockRemovalInternal(playerId, closestKey.x, closestKey.y, closestKey.z, delaySeconds);
    }

    private void restoreAllBlocksInternal() {
        World world = activeWorld;
        if (world == null) {
            clearPendingBlocksInternal();
            removedBlocks.clear();
            playerLastFootBlock.clear();
            roundStartTimesMs.clear();
            nextAliveFeatherRewardAtMs.clear();
            brokenBlocksByPlayer.clear();
            blinkChargesByPlayer.clear();
            blinksUsedByPlayer.clear();
            return;
        }
        for (Map.Entry<BlockKey, Integer> entry : removedBlocks.entrySet()) {
            BlockKey key = entry.getKey();
            Integer original = entry.getValue();
            if (original == null) {
                continue;
            }
            writeBlockId(world, key.x, key.y, key.z, original);
        }
        clearPendingBlocksInternal();
        removedBlocks.clear();
        playerLastFootBlock.clear();
        roundStartTimesMs.clear();
        nextAliveFeatherRewardAtMs.clear();
        brokenBlocksByPlayer.clear();
        blinkChargesByPlayer.clear();
        blinksUsedByPlayer.clear();
        deleteBrokenBlocksFile();
    }

    private void clearPendingBlocksInternal() {
        pendingBlocks.clear();
        pendingBlockQueue.clear();
    }

    private void scheduleBrokenBlocksSave() {
        brokenBlocksDirty.set(true);
        if (brokenBlocksSaveTask != null) {
            return;
        }
        brokenBlocksSaveTask = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            brokenBlocksSaveTask = null;
            if (!brokenBlocksDirty.compareAndSet(true, false)) {
                return;
            }
            saveBrokenBlocksToFile();
        }, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    private void saveBrokenBlocksToFile() {
        Map<BlockKey, Integer> snapshot = Map.copyOf(removedBlocks);
        if (snapshot.isEmpty()) {
            deleteBrokenBlocksFile();
            return;
        }
        List<BrokenBlockEntry> entries = new ArrayList<>(snapshot.size());
        for (Map.Entry<BlockKey, Integer> entry : snapshot.entrySet()) {
            BlockKey key = entry.getKey();
            entries.add(new BrokenBlockEntry(key.x, key.y, key.z, entry.getValue()));
        }
        try {
            String json = GSON.toJson(entries);
            Files.writeString(BROKEN_BLOCKS_FILE.toPath(), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save broken blocks file.");
        }
    }

    private void flushBrokenBlocksSave() {
        ScheduledFuture<?> task = brokenBlocksSaveTask;
        if (task != null) {
            task.cancel(false);
            brokenBlocksSaveTask = null;
        }
        if (brokenBlocksDirty.compareAndSet(true, false)) {
            saveBrokenBlocksToFile();
        }
    }

    private void deleteBrokenBlocksFile() {
        brokenBlocksDirty.set(false);
        ScheduledFuture<?> task = brokenBlocksSaveTask;
        if (task != null) {
            task.cancel(false);
            brokenBlocksSaveTask = null;
        }
        try {
            Files.deleteIfExists(BROKEN_BLOCKS_FILE.toPath());
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to delete broken blocks file.");
        }
    }

    public void restoreBrokenBlocksFromFile(World world) {
        if (!BROKEN_BLOCKS_FILE.exists()) {
            return;
        }
        List<BrokenBlockEntry> entries;
        try {
            String json = Files.readString(BROKEN_BLOCKS_FILE.toPath(), StandardCharsets.UTF_8);
            entries = GSON.fromJson(json, BROKEN_BLOCKS_LIST_TYPE);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to read broken blocks file.");
            deleteBrokenBlocksFile();
            return;
        }
        if (entries == null || entries.isEmpty()) {
            deleteBrokenBlocksFile();
            return;
        }
        LOGGER.atInfo().log("Crash recovery: %d broken blocks to restore.", entries.size());
        restoreBrokenBlocksWithRetry(world, entries, 0);
    }

    private void restoreBrokenBlocksWithRetry(World world, List<BrokenBlockEntry> entries, int attempt) {
        List<BrokenBlockEntry> failed = new ArrayList<>();
        int restored = 0;
        for (BrokenBlockEntry entry : entries) {
            if (writeBlockId(world, entry.x, entry.y, entry.z, entry.blockId)) {
                restored++;
            } else {
                failed.add(entry);
            }
        }
        if (restored > 0) {
            LOGGER.atInfo().log("Crash recovery: restored %d blocks (attempt %d).", restored, attempt + 1);
        }
        if (failed.isEmpty() || attempt >= 5) {
            if (!failed.isEmpty()) {
                LOGGER.atWarning().log("Crash recovery: %d blocks could not be restored after %d attempts (chunks not loaded).",
                        failed.size(), attempt + 1);
            }
            deleteBrokenBlocksFile();
            return;
        }
        long delayMs = attempt == 0 ? 3000L : 5000L;
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                world.execute(() -> restoreBrokenBlocksWithRetry(world, failed, attempt + 1));
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Crash recovery: retry failed.");
                deleteBrokenBlocksFile();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private long takeSurvivedMs(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        nextAliveFeatherRewardAtMs.remove(playerId);
        Long roundStartMs = roundStartTimesMs.remove(playerId);
        if (roundStartMs == null) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        return Math.max(0L, now - roundStartMs);
    }

    private String resolvePlayerName(UUID playerId) {
        PlayerRef playerRef = resolvePlayer(playerId);
        if (playerRef != null && playerRef.getUsername() != null && !playerRef.getUsername().isBlank()) {
            return playerRef.getUsername();
        }
        return "Unknown";
    }

    private boolean isInsidePlatform(List<RunOrFallPlatform> platforms, int x, int y, int z, int blockId) {
        for (RunOrFallPlatform platform : platforms) {
            if (platform != null && platform.contains(x, y, z) && matchesPlatformBlockId(platform, blockId)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPlatformBlockId(RunOrFallPlatform platform, int blockId) {
        if (platform == null) {
            return false;
        }
        String configuredItemId = platform.targetBlockItemId;
        if (configuredItemId == null || configuredItemId.isBlank()) {
            return true;
        }
        int configuredBlockId = resolveConfiguredBlockId(configuredItemId);
        return configuredBlockId >= 0 && configuredBlockId == blockId;
    }

    private int resolveConfiguredBlockId(String blockItemId) {
        if (blockItemId == null) {
            return -1;
        }
        String key = blockItemId.trim();
        if (key.isEmpty()) {
            return -1;
        }
        return blockItemIdCache.computeIfAbsent(key, ignored -> BlockType.getAssetMap().getIndex(key));
    }

    private static CountdownSettings resolveCountdownSettings(RunOrFallConfig config) {
        CountdownSettings settings = new CountdownSettings();
        int minPlayers = config != null ? config.minPlayers : 2;
        int minPlayersTimeSeconds = config != null ? config.minPlayersTimeSeconds : 300;
        int optimalPlayers = config != null ? config.optimalPlayers : 4;
        int optimalPlayersTimeSeconds = config != null ? config.optimalPlayersTimeSeconds : 60;

        settings.minPlayers = Math.max(1, minPlayers);
        settings.minPlayersTimeSeconds = Math.max(1, minPlayersTimeSeconds);
        settings.optimalPlayers = Math.max(settings.minPlayers, Math.max(1, optimalPlayers));
        settings.optimalPlayersTimeSeconds = Math.max(1, optimalPlayersTimeSeconds);
        return settings;
    }

    private int resolveBlinkStartCharges(RunOrFallConfig config) {
        int configured = config != null ? config.blinkStartCharges : configStore.getBlinkStartCharges();
        return Math.max(0, configured);
    }

    private int resolveBlinkChargeEveryBlocksBroken(RunOrFallConfig config) {
        int configured = config != null ? config.blinkChargeEveryBlocksBroken : configStore.getBlinkChargeEveryBlocksBroken();
        return Math.max(1, configured);
    }

    private static RunOrFallMapConfig resolveSelectedMap(RunOrFallConfig config) {
        if (config == null || config.maps == null || config.maps.isEmpty()) {
            return null;
        }
        String selectedMapId = config.selectedMapId;
        if (selectedMapId != null && !selectedMapId.isBlank()) {
            for (RunOrFallMapConfig map : config.maps) {
                if (map != null && selectedMapId.equalsIgnoreCase(map.id)) {
                    return map;
                }
            }
        }
        return config.maps.get(0);
    }

    private static RunOrFallMapConfig resolveAutoSelectedMap(RunOrFallConfig config, int playerCount) {
        if (config == null || config.maps == null || config.maps.isEmpty()) {
            return null;
        }
        int eligiblePlayers = Math.max(0, playerCount);
        int bestMinPlayers = Integer.MIN_VALUE;
        List<RunOrFallMapConfig> candidates = new ArrayList<>();
        for (RunOrFallMapConfig map : config.maps) {
            if (!isMapPlayable(map)) {
                continue;
            }
            int mapMinPlayers = sanitizeMapMinPlayers(map.minPlayers);
            if (mapMinPlayers > eligiblePlayers) {
                continue;
            }
            if (mapMinPlayers > bestMinPlayers) {
                bestMinPlayers = mapMinPlayers;
                candidates.clear();
            }
            if (mapMinPlayers == bestMinPlayers) {
                candidates.add(map);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        int randomIndex = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(randomIndex);
    }

    private static boolean isMapPlayable(RunOrFallMapConfig map) {
        if (map == null || map.id == null || map.id.isBlank()) {
            return false;
        }
        if (map.spawns == null || map.spawns.isEmpty()) {
            return false;
        }
        return map.platforms != null && !map.platforms.isEmpty();
    }

    private static int sanitizeMapMinPlayers(int value) {
        return Math.max(1, value);
    }

    private boolean writeBlockId(World world, int x, int y, int z, int blockId) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        var chunk = world.getChunkIfInMemory(chunkIndex);
        if (chunk == null) {
            chunk = world.loadChunkIfInMemory(chunkIndex);
        }
        if (chunk == null) {
            return false;
        }
        return chunk.setBlock(x, y, z, blockId);
    }

    private void teleportPlayerToLobby(UUID playerId) {
        RunOrFallLocation lobby = null;
        RunOrFallConfig roundConfig = activeRoundConfig;
        if (roundConfig != null) {
            RunOrFallMapConfig selectedMap = resolveSelectedMap(roundConfig);
            if (selectedMap != null && selectedMap.lobby != null) {
                lobby = selectedMap.lobby.copy();
            }
        } else {
            RunOrFallMapConfig countdownMap = countdownSelectedMap;
            if (countdownMap != null && countdownMap.lobby != null) {
                lobby = countdownMap.lobby.copy();
            } else {
                lobby = configStore.getLobby();
            }
        }
        if (lobby == null) {
            return;
        }
        teleportPlayer(playerId, lobby);
    }

    private void teleportPlayerToWorldSpawn(UUID playerId) {
        PlayerRef playerRef = resolvePlayer(playerId);
        if (playerRef == null) {
            return;
        }
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        var store = ref.getStore();
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null) {
            return;
        }

        com.hypixel.hytale.math.vector.Transform spawnTransform = null;
        var worldConfig = world.getWorldConfig();
        if (worldConfig != null && worldConfig.getSpawnProvider() != null) {
            try {
                spawnTransform = worldConfig.getSpawnProvider().getSpawnPoint(world, playerId);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to resolve RunOrFall world spawn.");
            }
        }

        Vector3d position;
        Vector3f rotation;
        if (spawnTransform != null) {
            position = spawnTransform.getPosition();
            rotation = spawnTransform.getRotation();
        } else {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            position = transform != null && transform.getPosition() != null
                    ? transform.getPosition()
                    : new Vector3d(0.0d, 0.0d, 0.0d);
            rotation = transform != null && transform.getRotation() != null
                    ? transform.getRotation()
                    : new Vector3f(0.0f, 0.0f, 0.0f);
        }
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, position, rotation));
    }

    private void teleportPlayer(UUID playerId, RunOrFallLocation location) {
        if (playerId == null || location == null) {
            return;
        }
        PlayerRef playerRef = resolvePlayer(playerId);
        if (playerRef == null) {
            return;
        }
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        var store = ref.getStore();
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null) {
            return;
        }
        store.addComponent(ref, Teleport.getComponentType(),
                new Teleport(world,
                        new Vector3d(location.x, location.y, location.z),
                        new Vector3f(location.rotX, location.rotY, location.rotZ)));
    }

    private PlayerRef resolvePlayer(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return Universe.get().getPlayer(playerId);
    }

    private void broadcastLobby(String text) {
        for (UUID playerId : lobbyPlayers) {
            sendToPlayer(playerId, text);
        }
    }

    private void sendToPlayer(UUID playerId, String text) {
        PlayerRef playerRef = resolvePlayer(playerId);
        if (playerRef == null) {
            return;
        }
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        var store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(PREFIX + text));
        }
    }

    private void rewardAliveTimeFeathersInternal(UUID playerId, long nowMs) {
        if (playerId == null || state != GameState.RUNNING || !alivePlayers.contains(playerId)) {
            return;
        }
        long nextRewardAt = nextAliveFeatherRewardAtMs
                .computeIfAbsent(playerId, ignored -> nowMs + FEATHER_SURVIVAL_INTERVAL_MS);
        if (nowMs < nextRewardAt) {
            return;
        }
        long elapsedIntervals = 1L + ((nowMs - nextRewardAt) / FEATHER_SURVIVAL_INTERVAL_MS);
        if (elapsedIntervals <= 0L) {
            return;
        }
        nextAliveFeatherRewardAtMs.put(playerId, nextRewardAt + (elapsedIntervals * FEATHER_SURVIVAL_INTERVAL_MS));
        long rewardAmount = elapsedIntervals * resolveFeathersPerMinuteAlive();
        grantFeathersToPlayer(playerId, rewardAmount, "for staying alive");
    }

    private void rewardAlivePlayersForEliminationInternal(UUID eliminatedPlayerId) {
        if (state != GameState.RUNNING || alivePlayers.isEmpty()) {
            return;
        }
        for (UUID survivorId : alivePlayers) {
            if (survivorId == null || survivorId.equals(eliminatedPlayerId)) {
                continue;
            }
            grantFeathersToPlayer(survivorId, resolveFeathersPerPlayerEliminated(), "for a player elimination");
        }
    }

    private long resolveFeathersPerMinuteAlive() {
        RunOrFallConfig config = activeRoundConfig;
        if (config == null) {
            return configStore.getFeathersPerMinuteAlive();
        }
        return Math.max(0L, config.feathersPerMinuteAlive);
    }

    private long resolveFeathersPerPlayerEliminated() {
        RunOrFallConfig config = activeRoundConfig;
        if (config == null) {
            return configStore.getFeathersPerPlayerEliminated();
        }
        return Math.max(0L, config.feathersPerPlayerEliminated);
    }

    private long resolveFeathersForWin() {
        RunOrFallConfig config = activeRoundConfig;
        if (config == null) {
            return configStore.getFeathersForWin();
        }
        return Math.max(0L, config.feathersForWin);
    }

    private void grantFeathersToPlayer(UUID playerId, long amount, String reason) {
        if (playerId == null || amount <= 0L) {
            return;
        }
        if (!RunOrFallFeatherBridge.addFeathers(playerId, amount)) {
            return;
        }
        sendFeatherGainMessage(playerId, amount, reason);
    }

    private void sendFeatherGainMessage(UUID playerId, long amount, String reason) {
        PlayerRef playerRef = resolvePlayer(playerId);
        if (playerRef == null) {
            return;
        }
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        var store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        Message reasonMessage = (reason == null || reason.isBlank())
                ? Message.raw(".")
                : Message.raw(" " + reason + ".");
        Message featherWord = amount == 1L
                ? Message.raw("feather").color(FEATHER_WORD_COLOR)
                : Message.raw("feathers").color(FEATHER_WORD_COLOR);
        Message message = Message.join(
                Message.raw(PREFIX + "+" + amount + " "),
                featherWord,
                reasonMessage
        );
        player.sendMessage(message);
    }

    private void playSfxForPlayer(UUID playerId, String soundEventId) {
        if (playerId == null || soundEventId == null || soundEventId.isBlank()) {
            return;
        }
        PlayerRef playerRef = resolvePlayer(playerId);
        if (playerRef == null) {
            return;
        }
        int soundIndex = SoundEvent.getAssetMap().getIndex(soundEventId);
        if (soundIndex <= SoundEvent.EMPTY_ID) {
            return;
        }
        SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, com.hypixel.hytale.protocol.SoundCategory.SFX);
    }

    private void dispatchToWorld(Runnable action) {
        World world = activeWorld;
        if (world == null) {
            return;
        }
        try {
            world.execute(action);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("RunOrFall world dispatch failed.");
        }
    }

    private void refreshLobbyHotbars() {
        for (UUID playerId : lobbyPlayers) {
            refreshPlayerHotbar(playerId);
        }
    }

    private void refreshPlayerHotbar(UUID playerId) {
        if (playerId == null) {
            return;
        }
        HyvexaRunOrFallPlugin plugin = HyvexaRunOrFallPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        plugin.refreshRunOrFallHotbar(playerId);
    }

    private void ensureBrokenBlocksEntry(UUID playerId) {
        brokenBlocksByPlayer.putIfAbsent(playerId, 0);
        blinkChargesByPlayer.putIfAbsent(playerId, 0);
        blinksUsedByPlayer.putIfAbsent(playerId, 0);
    }

    private void resetBrokenBlocksForLobbyPlayers() {
        for (UUID playerId : lobbyPlayers) {
            brokenBlocksByPlayer.put(playerId, 0);
            blinkChargesByPlayer.put(playerId, 0);
            blinksUsedByPlayer.put(playerId, 0);
            updateBrokenBlocksHudForPlayer(playerId, 0);
            updateBlinkChargesHudForPlayer(playerId, 0);
        }
    }

    private void incrementBrokenBlocksCountInternal(UUID playerId) {
        int previousCount = Math.max(0, brokenBlocksByPlayer.getOrDefault(playerId, 0));
        int nextCount = previousCount + 1;
        brokenBlocksByPlayer.put(playerId, nextCount);
        updateBrokenBlocksHudForPlayer(playerId, nextCount);
        int blocksPerExtraBlink = resolveBlinkChargeEveryBlocksBroken(activeRoundConfig);
        int rewardsBefore = previousCount / blocksPerExtraBlink;
        int rewardsAfter = nextCount / blocksPerExtraBlink;
        int rewardedCharges = Math.max(0, rewardsAfter - rewardsBefore);
        if (rewardedCharges <= 0) {
            return;
        }
        int reachedMilestoneBlocks = rewardsAfter * blocksPerExtraBlink;
        int nextCharges = Math.max(0, blinkChargesByPlayer.getOrDefault(playerId, 0)) + rewardedCharges;
        blinkChargesByPlayer.put(playerId, nextCharges);
        updateBlinkChargesHudForPlayer(playerId, nextCharges);
        playSfxForPlayer(playerId, SFX_BLINK_CHARGE_EARNED);
        if (rewardedCharges == 1) {
            sendToPlayer(playerId, "Blink charge earned: " + reachedMilestoneBlocks + " blocks broken.");
        } else {
            sendToPlayer(playerId, "Blink charges earned: +" + rewardedCharges + " (" + reachedMilestoneBlocks + " blocks broken).");
        }
    }

    private int getRoundBrokenBlocksCount(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return Math.max(0, brokenBlocksByPlayer.getOrDefault(playerId, 0));
    }

    private int getRoundBlinksUsedCount(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return Math.max(0, blinksUsedByPlayer.getOrDefault(playerId, 0));
    }

    private int getRoundBlinkChargesCount(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return Math.max(0, blinkChargesByPlayer.getOrDefault(playerId, 0));
    }

    private void updateCountdownHudForLobbyPlayers() {
        String countdownText = buildCountdownHudText();
        for (UUID playerId : lobbyPlayers) {
            updateCountdownHudForPlayer(playerId, countdownText);
        }
    }

    private void clearCountdownHudForLobbyPlayers() {
        for (UUID playerId : lobbyPlayers) {
            updateCountdownHudForPlayer(playerId, null);
        }
    }

    private void updateCountdownHudForPlayer(UUID playerId) {
        updateCountdownHudForPlayer(playerId, buildCountdownHudText());
    }

    private void updateCountdownHudForPlayer(UUID playerId, String countdownText) {
        HyvexaRunOrFallPlugin plugin = HyvexaRunOrFallPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        plugin.updateCountdownHud(playerId, countdownText);
    }

    private void updateBrokenBlocksHudForPlayer(UUID playerId) {
        updateBrokenBlocksHudForPlayer(playerId, getBrokenBlocksCount(playerId));
    }

    private void updateBrokenBlocksHudForPlayer(UUID playerId, int brokenBlocks) {
        HyvexaRunOrFallPlugin plugin = HyvexaRunOrFallPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        plugin.updateBrokenBlocksHud(playerId, Math.max(0, brokenBlocks));
    }

    private void updateBlinkChargesHudForPlayer(UUID playerId) {
        updateBlinkChargesHudForPlayer(playerId, getRoundBlinkChargesCount(playerId));
    }

    private void updateBlinkChargesHudForPlayer(UUID playerId, int blinkCharges) {
        HyvexaRunOrFallPlugin plugin = HyvexaRunOrFallPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        plugin.updateBlinkChargesHud(playerId, Math.max(0, blinkCharges));
    }

    private String buildCountdownHudText() {
        if (state != GameState.COUNTDOWN || countdownRemaining <= 0) {
            return null;
        }
        return "Starting in " + countdownRemaining + "s";
    }

    private void broadcastEliminationInternal(UUID eliminatedPlayerId, String reason) {
        String eliminatedName = resolvePlayerName(eliminatedPlayerId);
        if (eliminatedName == null || eliminatedName.isBlank() || "Unknown".equalsIgnoreCase(eliminatedName)) {
            eliminatedName = "A player";
        }
        if (reason == null || reason.isBlank()) {
            broadcastLobby("Elimination: " + eliminatedName + ". " + alivePlayers.size() + " remaining.");
            return;
        }
        broadcastLobby("Elimination: " + eliminatedName + " " + reason + ". " + alivePlayers.size() + " remaining.");
    }

    private void resetCountdownState() {
        state = GameState.IDLE;
        countdownRemaining = FORCED_COUNTDOWN_SECONDS;
        countdownRequiredPlayers = 2;
        countdownOptimalPlayers = 0;
        countdownOptimalTimeSeconds = 0;
        countdownForced = false;
        countdownSelectedMap = null;
        soloTestRound = false;
        blockBreakEnabledAtMs = 0L;
        blockBreakCountdownLastAnnounced = -1;
    }

    private void cancelCountdownTask() {
        ScheduledFuture<?> task = countdownTask;
        if (task != null) {
            task.cancel(false);
            countdownTask = null;
        }
    }

    private void cancelGameTickTask() {
        ScheduledFuture<?> task = gameTickTask;
        if (task != null) {
            task.cancel(false);
            gameTickTask = null;
        }
    }

    private static final class PendingBlock {
        private final int originalBlockId;
        private final long dueAtMs;
        private final UUID playerId;

        private PendingBlock(int originalBlockId, long dueAtMs, UUID playerId) {
            this.originalBlockId = originalBlockId;
            this.dueAtMs = dueAtMs;
            this.playerId = playerId;
        }
    }

    private static final class PendingBlockQueueEntry {
        private final BlockKey key;
        private final PendingBlock pending;
        private final long nextAttemptAtMs;

        private PendingBlockQueueEntry(BlockKey key, PendingBlock pending, long nextAttemptAtMs) {
            this.key = key;
            this.pending = pending;
            this.nextAttemptAtMs = nextAttemptAtMs;
        }

        private PendingBlockQueueEntry retryAt(long nextAttemptAtMs) {
            return new PendingBlockQueueEntry(key, pending, nextAttemptAtMs);
        }
    }

    private static final class CountdownSettings {
        private int minPlayers;
        private int minPlayersTimeSeconds;
        private int optimalPlayers;
        private int optimalPlayersTimeSeconds;
    }

    private static final class BlockKey {
        private final int x;
        private final int y;
        private final int z;

        private BlockKey(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof BlockKey other)) {
                return false;
            }
            return x == other.x && y == other.y && z == other.z;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(x);
            result = 31 * result + Integer.hashCode(y);
            result = 31 * result + Integer.hashCode(z);
            return result;
        }
    }

    private static final class BrokenBlockEntry {
        int x;
        int y;
        int z;
        int blockId;

        BrokenBlockEntry() {}

        BrokenBlockEntry(int x, int y, int z, int blockId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockId = blockId;
        }
    }
}
