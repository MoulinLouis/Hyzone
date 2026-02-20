package io.hyvexa.runorfall.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.runorfall.HyvexaRunOrFallPlugin;
import io.hyvexa.runorfall.data.RunOrFallConfig;
import io.hyvexa.runorfall.data.RunOrFallLocation;
import io.hyvexa.runorfall.data.RunOrFallPlatform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RunOrFallGameManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PREFIX = "[RunOrFall] ";
    private static final int COUNTDOWN_SECONDS = 10;
    private static final long GAME_TICK_MS = 50L;
    private static final long START_BLOCK_BREAK_GRACE_MS = 3000L;
    private static final double PLAYER_FOOTPRINT_RADIUS = 0.37d;
    private static final long SURVIVAL_COIN_INTERVAL_MS = 30_000L;
    private static final long SURVIVAL_COIN_REWARD = 1L;
    private static final long ELIMINATION_COIN_REWARD = 5L;
    private static final long WIN_COIN_REWARD = 25L;

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
    private final Map<BlockKey, Integer> removedBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, BlockKey> playerPendingBlock = new ConcurrentHashMap<>();
    private final Map<UUID, Long> roundStartTimesMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextSurvivalCoinRewardAtMs = new ConcurrentHashMap<>();

    private volatile GameState state = GameState.IDLE;
    private volatile int countdownRemaining = COUNTDOWN_SECONDS;
    private volatile int countdownRequiredPlayers = 2;
    private volatile boolean soloTestRound = false;
    private volatile ScheduledFuture<?> countdownTask;
    private volatile ScheduledFuture<?> gameTickTask;
    private volatile World activeWorld;
    private volatile long blockBreakEnabledAtMs;
    private volatile int blockBreakCountdownLastAnnounced = -1;
    private final int airBlockId;

    public RunOrFallGameManager(RunOrFallConfigStore configStore, RunOrFallStatsStore statsStore) {
        this.configStore = configStore;
        this.statsStore = statsStore;
        int resolvedAir = BlockType.getAssetMap().getIndex("Air");
        this.airBlockId = resolvedAir >= 0 ? resolvedAir : 0;
    }

    public synchronized boolean isJoined(UUID playerId) {
        return playerId != null && lobbyPlayers.contains(playerId);
    }

    public synchronized boolean isInActiveRound(UUID playerId) {
        return playerId != null && state == GameState.RUNNING && alivePlayers.contains(playerId);
    }

    public synchronized String statusLine() {
        return "state=" + state.name()
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
            sendToPlayer(playerId, "You are already in the RunOrFall lobby.");
            return;
        }
        sendToPlayer(playerId, "Joined the RunOrFall lobby.");
        teleportPlayerToLobby(playerId);
        refreshPlayerHotbar(playerId);
        broadcastLobby("Lobby: " + lobbyPlayers.size() + " player(s).");
        startCountdownIfPossible(false);
    }

    public synchronized void leaveLobby(UUID playerId, boolean notify) {
        if (playerId == null) {
            return;
        }
        boolean wasInLobby = lobbyPlayers.remove(playerId);
        boolean wasAlive = alivePlayers.remove(playerId);
        playerPendingBlock.remove(playerId);
        nextSurvivalCoinRewardAtMs.remove(playerId);
        long survivedMs = takeSurvivedMs(playerId);
        if (!wasInLobby && !wasAlive) {
            return;
        }
        if (notify) {
            sendToPlayer(playerId, "You left the RunOrFall lobby.");
        }
        if (state == GameState.COUNTDOWN && lobbyPlayers.size() < countdownRequiredPlayers) {
            cancelCountdownInternal("Countdown cancelled: not enough players.");
        }
        if (state == GameState.RUNNING && wasAlive) {
            statsStore.recordLoss(playerId, resolvePlayerName(playerId), survivedMs);
            rewardAlivePlayersForEliminationInternal(playerId);
            broadcastLobby("A player was eliminated. " + alivePlayers.size() + " remaining.");
            checkWinnerInternal();
        }
        refreshPlayerHotbar(playerId);
        if (lobbyPlayers.isEmpty()) {
            activeWorld = null;
        }
    }

    public synchronized void requestStart() {
        startCountdownIfPossible(false);
    }

    public synchronized void requestStart(boolean allowSolo) {
        startCountdownIfPossible(allowSolo);
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
        state = GameState.IDLE;
        countdownRemaining = COUNTDOWN_SECONDS;
        countdownRequiredPlayers = 2;
        soloTestRound = false;
        blockBreakEnabledAtMs = 0L;
        blockBreakCountdownLastAnnounced = -1;
        alivePlayers.clear();
        pendingBlocks.clear();
        playerPendingBlock.clear();
        roundStartTimesMs.clear();
        nextSurvivalCoinRewardAtMs.clear();
        lobbyPlayers.clear();
        activeWorld = null;
    }

    public synchronized void handleDisconnect(UUID playerId) {
        leaveLobby(playerId, false);
    }

    private void startCountdownIfPossible(boolean allowSoloStart) {
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
        countdownRemaining = COUNTDOWN_SECONDS;
        countdownRequiredPlayers = requiredPlayers;
        if (requiredPlayers == 1 && lobbyPlayers.size() == 1) {
            broadcastLobby("Solo test starting in " + COUNTDOWN_SECONDS + "s.");
        } else {
            broadcastLobby("Game starting in " + COUNTDOWN_SECONDS + "s.");
        }
        countdownTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> dispatchToWorld(this::tickCountdownInternal),
                1000L, 1000L, TimeUnit.MILLISECONDS
        );
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
            countdownRemaining--;
            if (countdownRemaining <= 0) {
                cancelCountdownTask();
                startGameInternal();
                return;
            }
            broadcastLobby("Starting in " + countdownRemaining + "s...");
        }
    }

    private void startGameInternal() {
        if (state != GameState.COUNTDOWN) {
            return;
        }
        RunOrFallConfig config = configStore.snapshot();
        if (config.platforms.isEmpty()) {
            state = GameState.IDLE;
            broadcastLobby("No destructible platform configured. Use /rof platform ... first.");
            return;
        }
        if (config.spawns.isEmpty()) {
            state = GameState.IDLE;
            broadcastLobby("No game spawn configured. Use /rof spawn add first.");
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
            broadcastLobby("Not enough online players to start.");
            return;
        }

        alivePlayers.clear();
        alivePlayers.addAll(onlinePlayers);
        long roundStartMs = System.currentTimeMillis();
        roundStartTimesMs.clear();
        nextSurvivalCoinRewardAtMs.clear();
        for (UUID onlinePlayerId : onlinePlayers) {
            roundStartTimesMs.put(onlinePlayerId, roundStartMs);
            nextSurvivalCoinRewardAtMs.put(onlinePlayerId, roundStartMs + SURVIVAL_COIN_INTERVAL_MS);
        }
        soloTestRound = countdownRequiredPlayers == 1 && onlinePlayers.size() == 1;
        pendingBlocks.clear();
        removedBlocks.clear();
        playerPendingBlock.clear();

        for (int i = 0; i < onlinePlayers.size(); i++) {
            UUID playerId = onlinePlayers.get(i);
            RunOrFallLocation spawn = config.spawns.get(i % config.spawns.size());
            teleportPlayer(playerId, spawn);
        }

        state = GameState.RUNNING;
        blockBreakEnabledAtMs = System.currentTimeMillis() + START_BLOCK_BREAK_GRACE_MS;
        blockBreakCountdownLastAnnounced = (int) Math.ceil(START_BLOCK_BREAK_GRACE_MS / 1000.0d);
        for (UUID onlinePlayerId : onlinePlayers) {
            refreshPlayerHotbar(onlinePlayerId);
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
            processSurvivalCoinRewardsInternal(nowMs);
            RunOrFallConfig config = configStore.snapshot();
            Set<UUID> disconnected = new HashSet<>();
            for (UUID playerId : new ArrayList<>(alivePlayers)) {
                PlayerRef playerRef = resolvePlayer(playerId);
                if (playerRef == null) {
                    disconnected.add(playerId);
                    continue;
                }
                var ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) {
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
                    eliminatePlayerInternal(playerId, "fell into the void");
                    continue;
                }

                if (canBreakBlocks) {
                    int blockY = (int) Math.floor(position.getY() - 0.2d);
                    queueFootprintBlocksInternal(playerId, position.getX(), position.getZ(), blockY,
                            config.platforms, config.blockBreakDelaySeconds);
                }
            }
            processPendingBlocksInternal();
            for (UUID playerId : disconnected) {
                if (alivePlayers.remove(playerId)) {
                    nextSurvivalCoinRewardAtMs.remove(playerId);
                    long survivedMs = takeSurvivedMs(playerId);
                    statsStore.recordLoss(playerId, resolvePlayerName(playerId), survivedMs);
                    rewardAlivePlayersForEliminationInternal(playerId);
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
            statsStore.recordWin(winner, winnerName, survivedMs);
            awardCoinsToPlayerInternal(winner, WIN_COIN_REWARD, "for winning the round.");
            if (winnerRef != null && winnerRef.getUsername() != null && !winnerRef.getUsername().isBlank()) {
                winnerName = winnerRef.getUsername();
            }
            broadcastLobby("Winner: " + winnerName);
        } else {
            broadcastLobby("No winner this round.");
        }
        endGameInternal("Round finished.");
    }

    private void eliminatePlayerInternal(UUID playerId, String reason) {
        if (!alivePlayers.remove(playerId)) {
            return;
        }
        nextSurvivalCoinRewardAtMs.remove(playerId);
        long survivedMs = takeSurvivedMs(playerId);
        statsStore.recordLoss(playerId, resolvePlayerName(playerId), survivedMs);
        rewardAlivePlayersForEliminationInternal(playerId);
        playerPendingBlock.remove(playerId);
        teleportPlayerToLobby(playerId);
        refreshPlayerHotbar(playerId);
        sendToPlayer(playerId, "Eliminated: " + reason + ".");
        broadcastLobby("A player was eliminated. " + alivePlayers.size() + " remaining.");
    }

    private void endGameInternal(String reason) {
        cancelGameTickTask();
        restoreAllBlocksInternal();
        alivePlayers.clear();
        playerPendingBlock.clear();
        roundStartTimesMs.clear();
        nextSurvivalCoinRewardAtMs.clear();
        state = GameState.IDLE;
        countdownRemaining = COUNTDOWN_SECONDS;
        countdownRequiredPlayers = 2;
        soloTestRound = false;
        blockBreakEnabledAtMs = 0L;
        blockBreakCountdownLastAnnounced = -1;
        refreshLobbyHotbars();
        broadcastLobby(reason);
        startCountdownIfPossible(false);
    }

    private void cancelCountdownInternal(String reason) {
        cancelCountdownTask();
        state = GameState.IDLE;
        countdownRemaining = COUNTDOWN_SECONDS;
        countdownRequiredPlayers = 2;
        blockBreakEnabledAtMs = 0L;
        blockBreakCountdownLastAnnounced = -1;
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

    private boolean queueBlockRemovalInternal(int x, int y, int z, double delaySeconds) {
        BlockKey key = new BlockKey(x, y, z);
        if (removedBlocks.containsKey(key) || pendingBlocks.containsKey(key)) {
            return false;
        }
        World world = activeWorld;
        if (world == null) {
            return false;
        }
        Integer currentId = readBlockId(world, x, y, z);
        if (currentId == null || currentId == airBlockId) {
            return false;
        }
        long delayMs = Math.max(0L, Math.round(delaySeconds * 1000.0d));
        long dueAtMs = System.currentTimeMillis() + delayMs;
        pendingBlocks.put(key, new PendingBlock(currentId, dueAtMs));
        return true;
    }

    private void processPendingBlocksInternal() {
        if (pendingBlocks.isEmpty()) {
            return;
        }
        World world = activeWorld;
        if (world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<BlockKey, PendingBlock> entry : new ArrayList<>(pendingBlocks.entrySet())) {
            BlockKey key = entry.getKey();
            PendingBlock pending = entry.getValue();
            if (pending == null || now < pending.dueAtMs) {
                continue;
            }
            Integer currentId = readBlockId(world, key.x, key.y, key.z);
            if (currentId == null) {
                continue;
            }
            if (currentId == airBlockId) {
                pendingBlocks.remove(key);
                continue;
            }
            if (writeBlockId(world, key.x, key.y, key.z, airBlockId)) {
                removedBlocks.put(key, pending.originalBlockId);
                pendingBlocks.remove(key);
            }
        }
    }

    private void queueFootprintBlocksInternal(UUID playerId, double centerX, double centerZ, int blockY,
                                              List<RunOrFallPlatform> platforms, double delaySeconds) {
        if (playerId == null) {
            return;
        }
        BlockKey pendingForPlayer = playerPendingBlock.get(playerId);
        if (pendingForPlayer != null) {
            if (pendingBlocks.containsKey(pendingForPlayer)) {
                return;
            }
            playerPendingBlock.remove(playerId, pendingForPlayer);
        }

        World world = activeWorld;
        if (world == null) {
            return;
        }

        int minX = (int) Math.floor(centerX - PLAYER_FOOTPRINT_RADIUS);
        int maxX = (int) Math.floor(centerX + PLAYER_FOOTPRINT_RADIUS);
        int minZ = (int) Math.floor(centerZ - PLAYER_FOOTPRINT_RADIUS);
        int maxZ = (int) Math.floor(centerZ + PLAYER_FOOTPRINT_RADIUS);
        BlockKey closestKey = null;
        double closestDistanceSq = Double.MAX_VALUE;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!isInsidePlatform(platforms, x, blockY, z)) {
                    continue;
                }
                BlockKey key = new BlockKey(x, blockY, z);
                if (removedBlocks.containsKey(key) || pendingBlocks.containsKey(key)) {
                    continue;
                }
                Integer currentId = readBlockId(world, x, blockY, z);
                if (currentId == null || currentId == airBlockId) {
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
            return;
        }
        if (queueBlockRemovalInternal(closestKey.x, closestKey.y, closestKey.z, delaySeconds)) {
            playerPendingBlock.put(playerId, closestKey);
        }
    }

    private void restoreAllBlocksInternal() {
        World world = activeWorld;
        if (world == null) {
            pendingBlocks.clear();
            removedBlocks.clear();
            playerPendingBlock.clear();
            roundStartTimesMs.clear();
            nextSurvivalCoinRewardAtMs.clear();
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
        pendingBlocks.clear();
        removedBlocks.clear();
        playerPendingBlock.clear();
        roundStartTimesMs.clear();
        nextSurvivalCoinRewardAtMs.clear();
    }

    private long takeSurvivedMs(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
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

    private static boolean isInsidePlatform(List<RunOrFallPlatform> platforms, int x, int y, int z) {
        for (RunOrFallPlatform platform : platforms) {
            if (platform != null && platform.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private Integer readBlockId(World world, int x, int y, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        var chunk = world.getChunkIfInMemory(chunkIndex);
        if (chunk == null) {
            chunk = world.loadChunkIfInMemory(chunkIndex);
        }
        if (chunk == null) {
            return null;
        }
        return chunk.getBlock(x, y, z);
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
        RunOrFallLocation lobby = configStore.getLobby();
        if (lobby == null) {
            return;
        }
        teleportPlayer(playerId, lobby);
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
        for (UUID playerId : new HashSet<>(lobbyPlayers)) {
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
        for (UUID playerId : new ArrayList<>(lobbyPlayers)) {
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

    private void processSurvivalCoinRewardsInternal(long nowMs) {
        for (UUID playerId : new ArrayList<>(alivePlayers)) {
            Long nextRewardAt = nextSurvivalCoinRewardAtMs.get(playerId);
            if (nextRewardAt == null) {
                nextRewardAt = nowMs + SURVIVAL_COIN_INTERVAL_MS;
            }
            while (nowMs >= nextRewardAt && alivePlayers.contains(playerId)) {
                awardCoinsToPlayerInternal(playerId, SURVIVAL_COIN_REWARD, "for surviving 30 seconds.");
                nextRewardAt += SURVIVAL_COIN_INTERVAL_MS;
            }
            nextSurvivalCoinRewardAtMs.put(playerId, nextRewardAt);
        }
    }

    private void rewardAlivePlayersForEliminationInternal(UUID eliminatedPlayerId) {
        String eliminatedName = resolvePlayerName(eliminatedPlayerId);
        for (UUID alivePlayerId : new ArrayList<>(alivePlayers)) {
            awardCoinsToPlayerInternal(alivePlayerId, ELIMINATION_COIN_REWARD,
                    "for elimination of " + eliminatedName + ".");
        }
    }

    private void awardCoinsToPlayerInternal(UUID playerId, long amount, String reasonText) {
        if (playerId == null || amount <= 0L) {
            return;
        }
        RunOrFallCoinStore.getInstance().addCoins(playerId, amount);
        sendCoinRewardToPlayer(playerId, amount, reasonText);
        HyvexaRunOrFallPlugin plugin = HyvexaRunOrFallPlugin.getInstance();
        if (plugin != null) {
            plugin.refreshRunOrFallHudEconomy(playerId);
        }
    }

    private void sendCoinRewardToPlayer(UUID playerId, long amount, String reasonText) {
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
        String unit = amount == 1L ? "coin" : "coins";
        player.sendMessage(Message.join(
                Message.raw(PREFIX),
                Message.raw("+" + amount + " ").color("#fbbf24"),
                Message.raw(unit).color("#fbbf24"),
                Message.raw(" " + reasonText)
        ));
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

        private PendingBlock(int originalBlockId, long dueAtMs) {
            this.originalBlockId = originalBlockId;
            this.dueAtMs = dueAtMs;
        }
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
}
