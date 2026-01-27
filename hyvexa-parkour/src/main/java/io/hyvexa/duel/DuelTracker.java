package io.hyvexa.duel;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.InventoryUtils;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.parkour.ParkourConstants;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.SettingsStore;
import io.hyvexa.parkour.data.TransformData;
import io.hyvexa.parkour.ui.PlayerMusicPage;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.visibility.PlayerVisibilityManager;
import io.hyvexa.duel.data.DuelMatchStore;
import io.hyvexa.duel.data.DuelPreferenceStore;
import io.hyvexa.duel.data.DuelPreferenceStore.DuelCategory;
import io.hyvexa.duel.data.DuelStatsStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DuelTracker {

    private final DuelQueue duelQueue;
    private final DuelMatchStore matchStore;
    private final DuelStatsStore statsStore;
    private final DuelPreferenceStore preferenceStore;
    private final MapStore mapStore;
    private final ConcurrentHashMap<String, DuelMatch> activeMatches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> matchByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DuelPlayerState> playerStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<UUID>> hiddenBeforeDuel = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Float> speedMultiplierBeforeDuel = new ConcurrentHashMap<>();
    private static final float DUEL_SPEED_RESET_MULTIPLIER = 1.0f;
    private final Random random = new Random();

    public DuelTracker(DuelQueue duelQueue, DuelMatchStore matchStore, DuelStatsStore statsStore,
                       DuelPreferenceStore preferenceStore, MapStore mapStore) {
        this.duelQueue = duelQueue;
        this.matchStore = matchStore;
        this.statsStore = statsStore;
        this.preferenceStore = preferenceStore;
        this.mapStore = mapStore;
    }

    public boolean isInMatch(@Nullable UUID playerId) {
        return playerId != null && matchByPlayer.containsKey(playerId);
    }

    public boolean isQueued(@Nullable UUID playerId) {
        return playerId != null && duelQueue.isQueued(playerId);
    }

    public int getQueuePosition(@Nonnull UUID playerId) {
        return duelQueue.getPosition(playerId);
    }

    @Nonnull
    public List<UUID> getQueueSnapshot() {
        return duelQueue.getWaitingPlayers();
    }

    @Nonnull
    public List<DuelMatch> getActiveMatches() {
        return new ArrayList<>(activeMatches.values());
    }

    public DuelStatsStore getStatsStore() {
        return statsStore;
    }

    @Nullable
    public DuelMatch getMatch(@Nonnull UUID playerId) {
        String matchId = matchByPlayer.get(playerId);
        if (matchId == null) {
            return null;
        }
        return activeMatches.get(matchId);
    }

    @Nullable
    public Map getActiveMap(@Nonnull UUID playerId) {
        DuelMatch match = getMatch(playerId);
        if (match == null) {
            return null;
        }
        return mapStore != null ? mapStore.getMap(match.getMapId()) : null;
    }

    @Nullable
    public String getActiveMapId(@Nonnull UUID playerId) {
        DuelMatch match = getMatch(playerId);
        return match != null ? match.getMapId() : null;
    }

    @Nullable
    public Long getElapsedTimeMs(@Nonnull UUID playerId) {
        DuelMatch match = getMatch(playerId);
        if (match == null) {
            return null;
        }
        if (match.getState() == DuelState.STARTING) {
            return 0L;
        }
        if (match.getState() != DuelState.RACING) {
            return null;
        }
        return Math.max(0L, System.currentTimeMillis() - match.getRaceStartMs());
    }

    @Nullable
    public RunTracker.CheckpointProgress getCheckpointProgress(@Nonnull UUID playerId) {
        DuelPlayerState state = playerStates.get(playerId);
        if (state == null) {
            return null;
        }
        Map map = mapStore.getMap(state.mapId);
        if (map == null) {
            return null;
        }
        int total = map.getCheckpoints().size();
        int touched = Math.min(state.touchedCheckpoints.size(), total);
        return new RunTracker.CheckpointProgress(touched, total);
    }

    public boolean enqueue(@Nonnull UUID playerId) {
        return duelQueue.join(playerId);
    }

    public boolean dequeue(@Nonnull UUID playerId) {
        return duelQueue.leave(playerId);
    }

    public void tryMatch() {
        List<UUID> queued = duelQueue.getWaitingPlayers();
        if (queued.size() < 2) {
            return;
        }
        for (int i = 0; i < queued.size(); i++) {
            UUID player1 = queued.get(i);
            for (int j = i + 1; j < queued.size(); j++) {
                UUID player2 = queued.get(j);
                Map map = selectRandomMapForPlayers(player1, player2);
                if (map == null) {
                    continue;
                }
                if (!duelQueue.removePair(player1, player2)) {
                    return;
                }
                if (!createMatch(player1, player2, map)) {
                    duelQueue.addToFront(player2);
                    duelQueue.addToFront(player1);
                }
                return;
            }
        }
    }

    public boolean forceMatch(@Nonnull UUID player1, @Nonnull UUID player2) {
        duelQueue.leave(player1);
        duelQueue.leave(player2);
        Map map = selectRandomMapForPlayers(player1, player2);
        return map != null && createMatch(player1, player2, map);
    }

    public void refreshOpponentVisibility(@Nonnull UUID playerId) {
        DuelMatch match = getMatch(playerId);
        if (match == null) {
            return;
        }
        UUID opponentId = match.getOpponent(playerId);
        if (opponentId == null) {
            return;
        }
        PlayerVisibilityManager visibility = PlayerVisibilityManager.get();
        applyOpponentVisibility(playerId, opponentId, visibility);
    }

    public void sweepQueuedPlayersInRun(@Nullable RunTracker runTracker) {
        if (runTracker == null) {
            return;
        }
        List<UUID> queued = duelQueue.getWaitingPlayers();
        for (UUID playerId : queued) {
            if (runTracker.getActiveMapId(playerId) != null) {
                duelQueue.leave(playerId);
                PlayerRef playerRef = Universe.get().getPlayer(playerId);
                if (playerRef != null) {
                    playerRef.sendMessage(SystemMessageUtils.duelWarn(
                            "You left the duel queue because you started a parkour run."
                    ));
                }
            }
        }
    }

    public boolean createMatch(@Nonnull UUID player1, @Nonnull UUID player2, @Nonnull Map map) {
        if (player1.equals(player2)) {
            return false;
        }
        if (isInMatch(player1) || isInMatch(player2)) {
            return false;
        }
        if (map == null || map.getStart() == null || map.getFinish() == null) {
            return false;
        }
        PlayerRef ref1 = Universe.get().getPlayer(player1);
        PlayerRef ref2 = Universe.get().getPlayer(player2);
        if (ref1 == null || ref2 == null) {
            if (ref1 != null) {
                duelQueue.addToFront(player1);
            }
            if (ref2 != null) {
                duelQueue.addToFront(player2);
            }
            return false;
        }
        String matchId = UUID.randomUUID().toString();
        DuelMatch match = new DuelMatch(matchId, player1, player2, map.getId());
        activeMatches.put(matchId, match);
        matchByPlayer.put(player1, matchId);
        matchByPlayer.put(player2, matchId);
        playerStates.put(player1, new DuelPlayerState(player1, matchId, map.getId()));
        playerStates.put(player2, new DuelPlayerState(player2, matchId, map.getId()));

        applyDuelVisibility(player1, player2);

        String name1 = ref1 != null && ref1.getUsername() != null ? ref1.getUsername() : "Player";
        String name2 = ref2 != null && ref2.getUsername() != null ? ref2.getUsername() : "Player";
        String mapName = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
        Message matchMessage1 = SystemMessageUtils.duelSuccess(
                String.format(DuelConstants.MSG_MATCH_FOUND, name2, mapName)
        );
        Message matchMessage2 = SystemMessageUtils.duelSuccess(
                String.format(DuelConstants.MSG_MATCH_FOUND, name1, mapName)
        );
        if (ref1 != null) {
            ref1.sendMessage(matchMessage1);
        }
        if (ref2 != null) {
            ref2.sendMessage(matchMessage2);
        }

        preparePlayerForMatch(ref1, map);
        preparePlayerForMatch(ref2, map);
        return true;
    }

    public void handleForfeit(@Nonnull UUID playerId) {
        DuelMatch match = getMatch(playerId);
        if (match == null || match.getState() == DuelState.FINISHED) {
            return;
        }
        UUID opponent = match.getOpponent(playerId);
        if (opponent != null) {
            match.trySetWinner(opponent);
        }
        match.setFinishReason(FinishReason.FORFEIT);
        endMatch(match, FinishReason.FORFEIT, opponent, playerId);
    }

    public void handleDisconnect(@Nonnull UUID playerId) {
        duelQueue.leave(playerId);
        DuelMatch match = getMatch(playerId);
        if (match == null || match.getState() == DuelState.FINISHED) {
            return;
        }
        UUID opponent = match.getOpponent(playerId);
        PlayerRef opponentRef = opponent != null ? Universe.get().getPlayer(opponent) : null;
        if (match.getState() == DuelState.STARTING) {
            if (opponentRef != null) {
                duelQueue.addToFront(opponent);
                opponentRef.sendMessage(SystemMessageUtils.duelWarn("Opponent disconnected. Returning you to the queue."));
                tryMatch();
            }
            cancelMatch(match);
            return;
        }
        if (opponent == null || opponentRef == null) {
            cancelMatch(match);
            return;
        }
        match.trySetWinner(opponent);
        match.setFinishReason(FinishReason.DISCONNECT);
        endMatch(match, FinishReason.DISCONNECT, opponent, playerId);
    }

    public void cancelMatch(@Nonnull String matchId) {
        DuelMatch match = activeMatches.get(matchId);
        if (match == null) {
            return;
        }
        cancelMatch(match);
    }

    public void tick() {
        if (activeMatches.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (DuelMatch match : new ArrayList<>(activeMatches.values())) {
            if (match.getState() != DuelState.STARTING) {
                continue;
            }
            long elapsedMs = now - match.getCountdownStartMs();
            int secondsLeft = DuelConstants.COUNTDOWN_SECONDS - (int) (elapsedMs / 1000L);
            if (secondsLeft > 0 && secondsLeft != match.getLastCountdownSecond()) {
                match.setLastCountdownSecond(secondsLeft);
                sendMatchMessage(match, Message.raw(String.valueOf(secondsLeft)));
            }
            if (elapsedMs >= DuelConstants.COUNTDOWN_SECONDS * 1000L) {
                match.setState(DuelState.RACING);
                match.setRaceStartMs(now);
                sendMatchMessage(match, Message.raw("GO!"));
            }
        }

        java.util.Map<World, List<DuelPlayerContext>> playersByWorld = collectMatchPlayersByWorld();
        for (java.util.Map.Entry<World, List<DuelPlayerContext>> entry : playersByWorld.entrySet()) {
            World world = entry.getKey();
            List<DuelPlayerContext> players = entry.getValue();
            CompletableFuture.runAsync(() -> {
                for (DuelPlayerContext context : players) {
                    if (context.ref == null || !context.ref.isValid()) {
                        continue;
                    }
                    processPlayer(context, System.currentTimeMillis());
                }
            }, world);
        }
    }

    public boolean resetRunToStart(Ref<EntityStore> ref, Store<EntityStore> store, Player player, PlayerRef playerRef) {
        if (playerRef == null || player == null) {
            return false;
        }
        DuelMatch match = getMatch(playerRef.getUuid());
        if (match == null || match.getState() != DuelState.RACING) {
            player.sendMessage(SystemMessageUtils.duelError("No active duel match."));
            return false;
        }
        Map map = mapStore.getMap(match.getMapId());
        if (map == null || map.getStart() == null) {
            player.sendMessage(SystemMessageUtils.duelError("Map start not available."));
            return false;
        }
        DuelPlayerState state = playerStates.get(playerRef.getUuid());
        if (state != null) {
            state.resetForRestart();
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            player.sendMessage(SystemMessageUtils.duelError("World not available."));
            return false;
        }
        Vector3d position = new Vector3d(map.getStart().getX(), map.getStart().getY(), map.getStart().getZ());
        Vector3f rotation = new Vector3f(map.getStart().getRotX(), map.getStart().getRotY(), map.getStart().getRotZ());
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, position, rotation));
        return true;
    }

    public boolean teleportToLastCheckpoint(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (playerRef == null) {
            return false;
        }
        DuelMatch match = getMatch(playerRef.getUuid());
        if (match == null || match.getState() != DuelState.RACING) {
            return false;
        }
        DuelPlayerState state = playerStates.get(playerRef.getUuid());
        if (state == null) {
            return false;
        }
        Map map = mapStore.getMap(match.getMapId());
        if (map == null) {
            return false;
        }
        int checkpointIndex = resolveCheckpointIndex(state, map);
        if (checkpointIndex < 0 || checkpointIndex >= map.getCheckpoints().size()) {
            return false;
        }
        TransformData checkpoint = map.getCheckpoints().get(checkpointIndex);
        if (checkpoint == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return false;
        }
        Vector3d position = new Vector3d(checkpoint.getX(), checkpoint.getY(), checkpoint.getZ());
        Vector3f rotation = new Vector3f(checkpoint.getRotX(), checkpoint.getRotY(), checkpoint.getRotZ());
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, position, rotation));
        state.resetFallTracking();
        return true;
    }

    private void processPlayer(DuelPlayerContext context, long now) {
        DuelMatch match = context.match;
        if (match.getState() == DuelState.STARTING) {
            ensureCountdownPosition(context);
            return;
        }
        if (match.getState() != DuelState.RACING) {
            return;
        }
        Player player = context.store.getComponent(context.ref, Player.getComponentType());
        TransformComponent transform = context.store.getComponent(context.ref, TransformComponent.getComponentType());
        if (player == null || transform == null) {
            return;
        }
        MovementStatesComponent movementStatesComponent = context.store.getComponent(context.ref,
                MovementStatesComponent.getComponentType());
        MovementStates movementStates = movementStatesComponent != null
                ? movementStatesComponent.getMovementStates()
                : null;
        DuelPlayerState state = playerStates.get(context.playerRef.getUuid());
        if (state == null) {
            return;
        }
        Map map = mapStore.getMap(match.getMapId());
        if (map == null) {
            cancelMatch(match);
            return;
        }
        Vector3d position = transform.getPosition();
        if (shouldTeleportFromVoid(position.getY())) {
            teleportToRespawn(context.ref, context.store, state, map);
            state.resetFallTracking();
            return;
        }
        checkCheckpoints(state, context.playerRef, player, position, map);
        long fallTimeoutMs = getFallRespawnTimeoutMs();
        if (map.isFreeFallEnabled()) {
            state.resetFallTracking();
            fallTimeoutMs = 0L;
        }
        if (fallTimeoutMs > 0 && shouldRespawnFromFall(state, position.getY(), movementStates, fallTimeoutMs)) {
            teleportToRespawn(context.ref, context.store, state, map);
            state.resetFallTracking();
            return;
        }
        checkFinish(state, context.playerRef, player, position, map, match, now);
    }

    private void ensureCountdownPosition(DuelPlayerContext context) {
        Map map = mapStore.getMap(context.match.getMapId());
        if (map == null || map.getStart() == null) {
            return;
        }
        TransformComponent transform = context.store.getComponent(context.ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        Vector3d position = transform.getPosition();
        double dx = position.getX() - map.getStart().getX();
        double dy = position.getY() - map.getStart().getY();
        double dz = position.getZ() - map.getStart().getZ();
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq <= 0.04) {
            return;
        }
        World world = context.store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        Vector3d startPos = new Vector3d(map.getStart().getX(), map.getStart().getY(), map.getStart().getZ());
        Vector3f startRot = new Vector3f(map.getStart().getRotX(), map.getStart().getRotY(), map.getStart().getRotZ());
        context.store.addComponent(context.ref, Teleport.getComponentType(), new Teleport(world, startPos, startRot));
    }

    private void checkCheckpoints(DuelPlayerState state, PlayerRef playerRef, Player player, Vector3d position,
                                  Map map) {
        List<TransformData> checkpoints = map.getCheckpoints();
        for (int i = 0; i < checkpoints.size(); i++) {
            if (state.touchedCheckpoints.contains(i)) {
                continue;
            }
            TransformData checkpoint = checkpoints.get(i);
            if (checkpoint == null) {
                continue;
            }
            if (distanceSqWithVerticalBonus(position, checkpoint) <= DuelConstants.TOUCH_RADIUS_SQ) {
                state.touchedCheckpoints.add(i);
                state.lastCheckpointIndex = i;
                playCheckpointSound(playerRef);
                player.sendMessage(SystemMessageUtils.duelInfo("Checkpoint reached."));
            }
        }
    }

    private void checkFinish(DuelPlayerState state, PlayerRef playerRef, Player player, Vector3d position,
                             Map map, DuelMatch match, long now) {
        if (state.finishTouched || map.getFinish() == null) {
            return;
        }
        if (distanceSqWithVerticalBonus(position, map.getFinish()) <= DuelConstants.TOUCH_RADIUS_SQ) {
            int checkpointCount = map.getCheckpoints().size();
            if (checkpointCount > 0 && state.touchedCheckpoints.size() < checkpointCount) {
                if (now - state.lastFinishWarningMs >= 2000L) {
                    state.lastFinishWarningMs = now;
                    player.sendMessage(SystemMessageUtils.duelWarn("You did not reach all checkpoints."));
                }
                return;
            }
            state.finishTouched = true;
            playFinishSound(playerRef);
            long elapsedMs = Math.max(0L, now - match.getRaceStartMs());
            match.setFinishTimeFor(playerRef.getUuid(), elapsedMs);
            if (match.trySetWinner(playerRef.getUuid())) {
                match.setFinishReason(FinishReason.COMPLETED);
                endMatch(match, FinishReason.COMPLETED, playerRef.getUuid(), match.getOpponent(playerRef.getUuid()));
            }
        }
    }

    private void playFinishSound(PlayerRef playerRef) {
        if (playerRef == null || !PlayerMusicPage.isVictorySfxEnabled(playerRef.getUuid())) {
            return;
        }
        int soundIndex = SoundEvent.getAssetMap().getIndex("SFX_Parkour_Victory");
        if (soundIndex <= SoundEvent.EMPTY_ID) {
            return;
        }
        SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, com.hypixel.hytale.protocol.SoundCategory.SFX);
    }

    private void playCheckpointSound(PlayerRef playerRef) {
        if (playerRef == null || !PlayerMusicPage.isCheckpointSfxEnabled(playerRef.getUuid())) {
            return;
        }
        int soundIndex = SoundEvent.getAssetMap().getIndex("SFX_Parkour_Checkpoint");
        if (soundIndex <= SoundEvent.EMPTY_ID) {
            return;
        }
        SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, com.hypixel.hytale.protocol.SoundCategory.SFX);
    }

    private void teleportToRespawn(Ref<EntityStore> ref, Store<EntityStore> store, DuelPlayerState state, Map map) {
        TransformData spawn = null;
        int checkpointIndex = resolveCheckpointIndex(state, map);
        if (checkpointIndex >= 0 && checkpointIndex < map.getCheckpoints().size()) {
            spawn = map.getCheckpoints().get(checkpointIndex);
        }
        if (spawn == null) {
            spawn = map.getStart();
        }
        if (spawn == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        Vector3d position = new Vector3d(spawn.getX(), spawn.getY(), spawn.getZ());
        Vector3f rotation = new Vector3f(spawn.getRotX(), spawn.getRotY(), spawn.getRotZ());
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, position, rotation));
    }

    private void preparePlayerForMatch(@Nullable PlayerRef playerRef, Map map) {
        if (playerRef == null) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            resetVipSpeedForDuel(ref, store, playerRef);
            if (map.getStart() != null) {
                Vector3d position = new Vector3d(map.getStart().getX(), map.getStart().getY(), map.getStart().getZ());
                Vector3f rotation = new Vector3f(map.getStart().getRotX(), map.getStart().getRotY(),
                        map.getStart().getRotZ());
                store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, position, rotation));
            }
            InventoryUtils.clearAllItems(player);
            InventoryUtils.giveDuelItems(player, map);
        }, world);
    }

    public boolean hasAvailableMaps(@Nonnull UUID playerId) {
        EnumSet<DuelCategory> enabled = preferenceStore != null
                ? preferenceStore.getEnabled(playerId)
                : EnumSet.allOf(DuelCategory.class);
        return pickRandomDuelMap(enabled) != null;
    }

    public boolean hasAvailableMaps(@Nonnull UUID player1, @Nonnull UUID player2) {
        return selectRandomMapForPlayers(player1, player2) != null;
    }

    @Nullable
    private Map selectRandomMapForPlayers(@Nonnull UUID player1, @Nonnull UUID player2) {
        EnumSet<DuelCategory> common = preferenceStore != null
                ? preferenceStore.getCommonEnabled(player1, player2)
                : EnumSet.allOf(DuelCategory.class);
        if (common.isEmpty()) {
            return null;
        }
        return pickRandomDuelMap(common);
    }

    @Nullable
    private Map pickRandomDuelMap(@Nonnull EnumSet<DuelCategory> allowedCategories) {
        if (mapStore == null) {
            return null;
        }
        List<Map> maps = mapStore.listDuelEnabledMaps();
        if (maps.isEmpty()) {
            return null;
        }
        List<Map> eligible = new ArrayList<>();
        for (Map map : maps) {
            if (map == null || map.getStart() == null || map.getFinish() == null) {
                continue;
            }
            if (!categoryMatches(map, allowedCategories)) {
                continue;
            }
            eligible.add(map);
        }
        if (eligible.isEmpty()) {
            return null;
        }
        return eligible.get(random.nextInt(eligible.size()));
    }

    private boolean categoryMatches(@Nonnull Map map, @Nonnull EnumSet<DuelCategory> allowedCategories) {
        DuelCategory category = toDuelCategory(map.getCategory());
        return category != null && allowedCategories.contains(category);
    }

    @Nullable
    private DuelCategory toDuelCategory(@Nullable String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "easy" -> DuelCategory.EASY;
            case "medium" -> DuelCategory.MEDIUM;
            case "hard" -> DuelCategory.HARD;
            case "insane" -> DuelCategory.INSANE;
            default -> null;
        };
    }

    private java.util.Map<World, List<DuelPlayerContext>> collectMatchPlayersByWorld() {
        java.util.Map<World, List<DuelPlayerContext>> playersByWorld = new HashMap<>();
        for (DuelMatch match : activeMatches.values()) {
            addMatchPlayer(playersByWorld, match, match.getPlayer1());
            addMatchPlayer(playersByWorld, match, match.getPlayer2());
        }
        return playersByWorld;
    }

    private void addMatchPlayer(java.util.Map<World, List<DuelPlayerContext>> playersByWorld,
                                DuelMatch match, UUID playerId) {
        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null) {
            handleDisconnect(playerId);
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        playersByWorld.computeIfAbsent(world, ignored -> new ArrayList<>())
                .add(new DuelPlayerContext(match, playerRef, ref, store));
    }

    private void sendMatchMessage(DuelMatch match, Message message) {
        PlayerRef player1 = Universe.get().getPlayer(match.getPlayer1());
        PlayerRef player2 = Universe.get().getPlayer(match.getPlayer2());
        if (player1 != null) {
            player1.sendMessage(message);
        }
        if (player2 != null) {
            player2.sendMessage(message);
        }
    }

    private void endMatch(DuelMatch match, FinishReason reason, UUID winnerId, UUID loserId) {
        if (match.getState() == DuelState.FINISHED) {
            return;
        }
        match.setState(DuelState.FINISHED);
        match.setFinishReason(reason);
        if (winnerId != null) {
            match.trySetWinner(winnerId);
        }

        PlayerRef winnerRef = winnerId != null ? Universe.get().getPlayer(winnerId) : null;
        PlayerRef loserRef = loserId != null ? Universe.get().getPlayer(loserId) : null;

        if (reason == FinishReason.COMPLETED) {
            long winnerTime = match.getFinishTimeFor(winnerId != null ? winnerId : match.getWinnerId());
            if (winnerTime <= 0L) {
                winnerTime = Math.max(0L, System.currentTimeMillis() - match.getRaceStartMs());
            }
            if (winnerId != null) {
                match.setFinishTimeFor(winnerId, winnerTime);
            }
            if (loserId != null && match.getFinishTimeFor(loserId) <= 0L) {
                match.setFinishTimeFor(loserId, Math.max(0L, System.currentTimeMillis() - match.getRaceStartMs()));
            }
            if (winnerRef != null) {
                String opponentName = loserRef != null ? loserRef.getUsername() : "Opponent";
                String winText = String.format(DuelConstants.MSG_WIN,
                        FormatUtils.formatDuration(winnerTime), opponentName);
                winnerRef.sendMessage(SystemMessageUtils.duelSuccess(winText));
            }
            if (loserRef != null) {
                String loseText = String.format(DuelConstants.MSG_LOSE,
                        FormatUtils.formatDuration(winnerTime),
                        winnerRef != null ? winnerRef.getUsername() : "Opponent");
                loserRef.sendMessage(SystemMessageUtils.duelWarn(loseText));
            }
        } else if (reason == FinishReason.FORFEIT) {
            if (winnerRef != null) {
                String name = loserRef != null ? loserRef.getUsername() : "Opponent";
                winnerRef.sendMessage(SystemMessageUtils.duelSuccess(
                        String.format(DuelConstants.MSG_WIN_FORFEIT, name)
                ));
            }
            if (loserRef != null) {
                loserRef.sendMessage(SystemMessageUtils.duelWarn(DuelConstants.MSG_FORFEITED));
            }
        } else if (reason == FinishReason.DISCONNECT) {
            if (winnerRef != null) {
                String name = loserRef != null ? loserRef.getUsername() : "Opponent";
                winnerRef.sendMessage(SystemMessageUtils.duelSuccess(
                        String.format(DuelConstants.MSG_WIN_DISCONNECT, name)
                ));
            }
        }

        if (winnerId != null && loserId != null && statsStore != null) {
            String winnerName = winnerRef != null ? winnerRef.getUsername() : winnerId.toString();
            String loserName = loserRef != null ? loserRef.getUsername() : loserId.toString();
            statsStore.recordWin(winnerId, winnerName != null ? winnerName : "Player");
            statsStore.recordLoss(loserId, loserName != null ? loserName : "Player");
        }

        if (winnerId != null && matchStore != null) {
            matchStore.saveMatch(match);
        }

        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> cleanupMatch(match),
                DuelConstants.POST_MATCH_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelMatch(DuelMatch match) {
        if (match == null || match.getState() == DuelState.FINISHED) {
            return;
        }
        match.setState(DuelState.FINISHED);
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> cleanupMatch(match),
                DuelConstants.POST_MATCH_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void cleanupMatch(DuelMatch match) {
        UUID player1 = match.getPlayer1();
        UUID player2 = match.getPlayer2();
        restoreVisibility(player1);
        restoreVisibility(player2);

        cleanupPlayer(player1);
        cleanupPlayer(player2);

        activeMatches.remove(match.getMatchId());
        matchByPlayer.remove(player1);
        matchByPlayer.remove(player2);
        playerStates.remove(player1);
        playerStates.remove(player2);
    }

    private void cleanupPlayer(UUID playerId) {
        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            teleportToSpawn(ref, store, transform);
            InventoryUtils.clearAllItems(player);
            InventoryUtils.giveMenuItems(player);
            restoreVipSpeedAfterDuel(ref, store, playerRef);
        }, world);
    }

    private void resetVipSpeedForDuel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                      @Nonnull PlayerRef playerRef) {
        io.hyvexa.HyvexaPlugin plugin = io.hyvexa.HyvexaPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        float current = plugin.getVipSpeedMultiplier(playerId);
        speedMultiplierBeforeDuel.put(playerId, current);
        if (current > DUEL_SPEED_RESET_MULTIPLIER) {
            plugin.applyVipSpeedMultiplier(ref, store, playerRef, DUEL_SPEED_RESET_MULTIPLIER, false);
        }
    }

    private void restoreVipSpeedAfterDuel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                          @Nonnull PlayerRef playerRef) {
        io.hyvexa.HyvexaPlugin plugin = io.hyvexa.HyvexaPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        Float previous = speedMultiplierBeforeDuel.remove(playerId);
        if (previous == null || previous <= DUEL_SPEED_RESET_MULTIPLIER) {
            return;
        }
        plugin.applyVipSpeedMultiplier(ref, store, playerRef, previous, false);
    }

    private void applyDuelVisibility(@Nonnull UUID player1, @Nonnull UUID player2) {
        PlayerVisibilityManager visibility = PlayerVisibilityManager.get();
        saveHiddenState(player1, visibility);
        saveHiddenState(player2, visibility);
        hideAllExcept(player1, player2, visibility);
        hideAllExcept(player2, player1, visibility);
        applyOpponentVisibility(player1, player2, visibility);
        applyOpponentVisibility(player2, player1, visibility);
    }

    private void saveHiddenState(@Nonnull UUID viewerId, @Nonnull PlayerVisibilityManager visibility) {
        Set<UUID> currentHidden = visibility.getHiddenTargets(viewerId);
        if (currentHidden.isEmpty()) {
            hiddenBeforeDuel.put(viewerId, Set.of());
            return;
        }
        hiddenBeforeDuel.put(viewerId, new HashSet<>(currentHidden));
    }

    private void hideAllExcept(@Nonnull UUID viewerId, @Nonnull UUID opponentId,
                               @Nonnull PlayerVisibilityManager visibility) {
        visibility.clearHidden(viewerId);
        for (PlayerRef ref : Universe.get().getPlayers()) {
            if (ref == null) {
                continue;
            }
            UUID targetId = ref.getUuid();
            if (targetId == null || targetId.equals(viewerId) || targetId.equals(opponentId)) {
                continue;
            }
            visibility.hidePlayer(viewerId, targetId);
        }
    }

    private void applyOpponentVisibility(@Nonnull UUID viewerId, @Nonnull UUID opponentId,
                                         @Nonnull PlayerVisibilityManager visibility) {
        boolean hideOpponent = io.hyvexa.parkour.util.PlayerSettingsStore.isDuelOpponentHidden(viewerId);
        if (hideOpponent) {
            visibility.hidePlayer(viewerId, opponentId);
        } else {
            visibility.showPlayer(viewerId, opponentId);
        }
    }

    private void restoreVisibility(@Nonnull UUID viewerId) {
        PlayerVisibilityManager visibility = PlayerVisibilityManager.get();
        Set<UUID> saved = hiddenBeforeDuel.remove(viewerId);
        visibility.clearHidden(viewerId);
        if (saved == null || saved.isEmpty()) {
            return;
        }
        for (UUID targetId : saved) {
            if (targetId == null || Universe.get().getPlayer(targetId) == null) {
                continue;
            }
            if (targetId.equals(viewerId)) {
                continue;
            }
            visibility.hidePlayer(viewerId, targetId);
        }
    }

    private void teleportToSpawn(Ref<EntityStore> ref, Store<EntityStore> store, TransformComponent transform) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        com.hypixel.hytale.math.vector.Transform spawnTransform = null;
        var worldConfig = world.getWorldConfig();
        if (worldConfig != null && worldConfig.getSpawnProvider() != null) {
            spawnTransform = worldConfig.getSpawnProvider().getSpawnPoint(world, playerRef.getUuid());
        }
        Vector3d position = spawnTransform != null
                ? spawnTransform.getPosition()
                : (transform != null ? transform.getPosition() : new Vector3d(0.0, 0.0, 0.0));
        Vector3f rotation = spawnTransform != null
                ? spawnTransform.getRotation()
                : (transform != null ? transform.getRotation() : new Vector3f(0f, 0f, 0f));
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, position, rotation));
    }

    private double getVoidY() {
        io.hyvexa.HyvexaPlugin plugin = io.hyvexa.HyvexaPlugin.getInstance();
        SettingsStore settings = plugin != null ? plugin.getSettingsStore() : null;
        if (settings == null) {
            return ParkourConstants.FALL_FAILSAFE_VOID_Y;
        }
        return settings.getFallFailsafeVoidY();
    }

    private long getFallRespawnTimeoutMs() {
        io.hyvexa.HyvexaPlugin plugin = io.hyvexa.HyvexaPlugin.getInstance();
        SettingsStore settings = plugin != null ? plugin.getSettingsStore() : null;
        if (settings == null) {
            return (long) (ParkourConstants.DEFAULT_FALL_RESPAWN_SECONDS * 1000L);
        }
        return (long) (settings.getFallRespawnSeconds() * 1000L);
    }

    private boolean shouldTeleportFromVoid(double currentY) {
        double voidY = getVoidY();
        return Double.isFinite(voidY) && currentY <= voidY;
    }

    private boolean shouldRespawnFromFall(DuelPlayerState state, double currentY, MovementStates movementStates,
                                          long fallTimeoutMs) {
        if (state == null || fallTimeoutMs <= 0L) {
            return false;
        }
        if (movementStates != null && (movementStates.climbing || movementStates.onGround)) {
            state.fallStartTime = null;
            state.lastY = currentY;
            return false;
        }
        Double lastY = state.lastY;
        long now = System.currentTimeMillis();
        if (lastY == null) {
            state.lastY = currentY;
            return false;
        }
        if (currentY < lastY) {
            if (state.fallStartTime == null) {
                state.fallStartTime = now;
            }
            if (now - state.fallStartTime >= fallTimeoutMs) {
                state.fallStartTime = null;
                state.lastY = currentY;
                return true;
            }
        } else {
            state.fallStartTime = null;
        }
        state.lastY = currentY;
        return false;
    }

    private int resolveCheckpointIndex(DuelPlayerState state, Map map) {
        if (state == null || map == null) {
            return -1;
        }
        int index = state.lastCheckpointIndex;
        if (index >= 0 && index < map.getCheckpoints().size()) {
            return index;
        }
        int best = -1;
        for (Integer touched : state.touchedCheckpoints) {
            if (touched == null) {
                continue;
            }
            int candidate = touched;
            if (candidate >= 0 && candidate < map.getCheckpoints().size()) {
                best = Math.max(best, candidate);
            }
        }
        return best;
    }

    private static double distanceSqWithVerticalBonus(Vector3d position, TransformData target) {
        double dx = position.getX() - target.getX();
        double dy = position.getY() - target.getY();
        if (dy > 0) {
            dy = Math.max(0.0, dy - DuelConstants.TOUCH_VERTICAL_BONUS);
        }
        double dz = position.getZ() - target.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static final class DuelPlayerState {
        private final UUID playerId;
        private final String matchId;
        private final String mapId;
        private final Set<Integer> touchedCheckpoints = new HashSet<>();
        private int lastCheckpointIndex = -1;
        private boolean finishTouched;
        private Long fallStartTime;
        private Double lastY;
        private long lastFinishWarningMs;

        private DuelPlayerState(UUID playerId, String matchId, String mapId) {
            this.playerId = playerId;
            this.matchId = matchId;
            this.mapId = mapId;
        }

        private void resetForRestart() {
            touchedCheckpoints.clear();
            lastCheckpointIndex = -1;
            finishTouched = false;
            resetFallTracking();
        }

        private void resetFallTracking() {
            fallStartTime = null;
            lastY = null;
        }
    }

    private static final class DuelPlayerContext {
        private final DuelMatch match;
        private final PlayerRef playerRef;
        private final Ref<EntityStore> ref;
        private final Store<EntityStore> store;

        private DuelPlayerContext(DuelMatch match, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
            this.match = match;
            this.playerRef = playerRef;
            this.ref = ref;
            this.store = store;
        }
    }
}
