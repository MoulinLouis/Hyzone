package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.AchievementType;
import io.hyvexa.ascend.AscendConstants.SkillTreeNode;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.tutorial.TutorialTriggerService;
import io.hyvexa.ascend.util.MapUnlockHelper;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.core.analytics.PlayerAnalytics;
import io.hyvexa.core.db.ConnectionProvider;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


/**
 * In-memory cache and public API surface for Ascend player data.
 *
 * <p><b>Contract:</b> This class owns the player cache, business logic
 * (volt operations, ascension triggers, multiplier calculations, reset flows),
 * and the public API consumed by the rest of the Ascend module.
 * All SQL is delegated to the package-private {@link AscendPlayerPersistence}.</p>
 */
public class AscendPlayerStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<UUID, AscendPlayerProgress> players = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();
    private final Set<UUID> resetPendingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> ascensionCinematicActive = ConcurrentHashMap.newKeySet();

    private final ConnectionProvider db;
    private final AscendPlayerPersistence persistence;
    private volatile ChallengeManager challengeManager;
    private volatile TutorialTriggerService tutorialTriggerService;
    private volatile AscensionManager ascensionManager;
    private volatile AscendHudManager hudManager;
    private volatile RobotManager robotManager;
    private volatile AchievementManager achievementManager;
    private volatile AscendMapStore runtimeMapStore;
    private volatile GhostStore ghostStore;
    private volatile Function<UUID, PlayerRef> playerRefLookup;
    private volatile PlayerAnalytics analytics;

    public AscendPlayerStore(ConnectionProvider db) {
        this.db = db;
        this.persistence = new AscendPlayerPersistence(db, players, playerNames, resetPendingPlayers);
    }

    public void setAnalytics(PlayerAnalytics analytics) {
        this.analytics = analytics;
    }

    public void setRuntimeServices(ChallengeManager challengeManager,
                                   TutorialTriggerService tutorialTriggerService,
                                   AscensionManager ascensionManager,
                                   AscendHudManager hudManager,
                                   RobotManager robotManager,
                                   AchievementManager achievementManager,
                                   AscendMapStore runtimeMapStore,
                                   GhostStore ghostStore,
                                   Function<UUID, PlayerRef> playerRefLookup) {
        this.challengeManager = challengeManager;
        this.tutorialTriggerService = tutorialTriggerService;
        this.ascensionManager = ascensionManager;
        this.hudManager = hudManager;
        this.robotManager = robotManager;
        this.achievementManager = achievementManager;
        this.runtimeMapStore = runtimeMapStore;
        this.ghostStore = ghostStore;
        this.playerRefLookup = playerRefLookup;
    }

    public record LeaderboardEntry(UUID playerId, String playerName,
            double totalVoltEarnedMantissa, int totalVoltEarnedExp10,
            int ascensionCount, int totalManualRuns, Long fastestAscensionMs) {}

    public record MapLeaderboardEntry(UUID playerId, String playerName, long bestTimeMs) {
        public MapLeaderboardEntry(String playerName, long bestTimeMs) {
            this(null, playerName, bestTimeMs);
        }
    }

    /**
     * Initialize the store. With lazy loading, we don't load all players upfront.
     * Players are loaded on-demand when they connect.
     */
    public void syncLoad() {
        if (!this.db.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, AscendPlayerStore will use in-memory mode");
            return;
        }

        players.clear();
        persistence.clearDirtyState();
        resetPendingPlayers.clear();
        LOGGER.atInfo().log("AscendPlayerStore initialized with lazy loading");
    }

    public AscendPlayerProgress getPlayer(UUID playerId) {
        return players.get(playerId);
    }

    public Map<UUID, AscendPlayerProgress> getPlayersSnapshot() {
        return new HashMap<>(players);
    }

    public AscendPlayerProgress getOrCreatePlayer(UUID playerId) {
        // Fast path: already cached
        AscendPlayerProgress progress = players.get(playerId);
        if (progress != null) {
            migrateAscensionTimer(playerId, progress);
            return progress;
        }

        // Slow path: load from DB or create new
        AscendPlayerProgress loaded = persistence.loadPlayerFromDatabase(playerId);
        if (loaded == null) {
            loaded = new AscendPlayerProgress();
            loaded.gameplay().setAscensionStartedAt(System.currentTimeMillis());
            markDirty(playerId);
        }

        // Atomic insert — if another thread won the race, use their instance
        AscendPlayerProgress existing = players.putIfAbsent(playerId, loaded);
        if (existing != null) {
            // Another thread already inserted — use their version
            migrateAscensionTimer(playerId, existing);
            return existing;
        }

        // We won the race — our instance is now in the cache
        migrateAscensionTimer(playerId, loaded);
        return loaded;
    }

    private void migrateAscensionTimer(UUID playerId, AscendPlayerProgress progress) {
        if (progress.gameplay().getAscensionStartedAt() == null) {
            progress.gameplay().setAscensionStartedAt(System.currentTimeMillis());
            markDirty(playerId);
        }
    }

    public void storePlayerName(UUID playerId, String name) {
        if (playerId == null || name == null) {
            return;
        }
        String trimmed = name.length() > 32 ? name.substring(0, 32) : name;
        playerNames.put(playerId, trimmed);
    }

    public String getPlayerName(UUID playerId) {
        return playerNames.get(playerId);
    }

    public void resetPlayerProgress(UUID playerId) {
        // Mark as reset so any concurrent/pending syncSave() will
        // DELETE child rows before re-inserting (prevents stale upserts)
        resetPendingPlayers.add(playerId);

        // Cancel any pending debounced save to prevent stale data from
        // being written after our DELETE
        persistence.cancelPendingSave();

        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        // Reset basic progression
        progress.economy().setVolt(BigNumber.ZERO);
        progress.economy().setElevationMultiplier(1);
        progress.gameplay().getMapProgress().clear();

        // Reset Summit system
        progress.economy().clearSummitXp();
        progress.economy().setTotalVoltEarned(BigNumber.ZERO);
        progress.economy().setSummitAccumulatedVolt(BigNumber.ZERO);
        progress.economy().setElevationAccumulatedVolt(BigNumber.ZERO);

        // Reset Ascension/Skill Tree
        progress.gameplay().setAscensionCount(0);
        progress.gameplay().setSkillTreePoints(0);
        progress.gameplay().setUnlockedSkillNodes(null);

        // Reset Achievements
        progress.gameplay().setUnlockedAchievements(null);

        // Reset Easter Egg cats
        progress.gameplay().setFoundCats(null);

        // Reset Statistics
        progress.gameplay().setTotalManualRuns(0);
        progress.gameplay().setConsecutiveManualRuns(0);
        progress.session().setSessionFirstRunClaimed(false);

        // Delete all database entries for this player
        persistence.deletePlayerDataFromDatabase(playerId);

        markDirty(playerId);
    }

    /**
     * Marks a player for full child-row deletion on the next syncSave.
     * Use before clearing in-memory collections (e.g. mapProgress.clear())
     * so stale DB rows are removed before re-insert.
     */
    public void markResetPending(UUID playerId) {
        if (playerId != null) {
            resetPendingPlayers.add(playerId);
        }
    }

    /**
     * Marks a player for challenge records deletion on the next syncSave (transcendence reset).
     * Must be combined with markResetPending for full transcendence wipe.
     */
    public void markTranscendenceResetPending(UUID playerId) {
        persistence.markTranscendenceResetPending(playerId);
    }

    public void markDirty(UUID playerId) {
        if (playerId == null) {
            return;
        }
        persistence.markDirty(playerId);
        persistence.queueSave();
    }

    public BigNumber getVolt(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.economy().getVolt() : BigNumber.ZERO;
    }

    public void setVolt(UUID playerId, BigNumber volt) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.economy().setVolt(volt.max(BigNumber.ZERO));
        markDirty(playerId);
    }

    /**
     * Gets the raw elevation level (stored value).
     * For the actual multiplier value, use {@link #getCalculatedElevationMultiplier(UUID)}.
     */
    public int getElevationLevel(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.economy().getElevationMultiplier() : 0;
    }

    /**
     * Get the player's effective elevation multiplier.
     * Base: level (1:1). C8 reward: x1.25 bonus.
     */
    public double getCalculatedElevationMultiplier(UUID playerId) {
        double base = AscendConstants.getElevationMultiplier(getElevationLevel(playerId));
        if (challengeManager != null) {
            base *= challengeManager.getChallengeElevationBonus(playerId);
        }
        return base;
    }

    /**
     * Add elevation levels to a player.
     * @return The new total elevation level
     */
    public int addElevationLevel(UUID playerId, int amount) {
        if (amount <= 0) {
            return getElevationLevel(playerId);
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        int value = progress.economy().addElevationMultiplier(amount);
        markDirty(playerId);
        if (analytics != null) {
            try {
                analytics.logEvent(playerId, "ascend_elevation_up",
                        "{\"new_level\":" + value + "}");
            } catch (Exception e) { /* silent */ }
        }
        return value;
    }

    public GameplayState.MapProgress getMapProgress(UUID playerId, String mapId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return null;
        }
        return progress.gameplay().getMapProgress().get(mapId);
    }

    public GameplayState.MapProgress getOrCreateMapProgress(UUID playerId, String mapId) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        return progress.gameplay().getOrCreateMapProgress(mapId);
    }

    /**
     * Returns authoritative best time for this player/map.
     * Uses in-memory value first, then DB fallback if memory is missing.
     */
    public Long getBestTimeMs(UUID playerId, String mapId) {
        if (playerId == null || mapId == null) {
            return null;
        }

        GameplayState.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress != null && mapProgress.getBestTimeMs() != null) {
            return mapProgress.getBestTimeMs();
        }

        Long dbBest = persistence.loadBestTimeFromDatabase(playerId, mapId);
        if (dbBest != null) {
            GameplayState.MapProgress target = getOrCreateMapProgress(playerId, mapId);
            Long current = target.getBestTimeMs();
            if (current == null || dbBest < current) {
                target.setBestTimeMs(dbBest);
            }
        }
        return dbBest;
    }

    public boolean setMapUnlocked(UUID playerId, String mapId, boolean unlocked) {
        GameplayState.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        if (mapProgress.isUnlocked() == unlocked) {
            return false;
        }
        mapProgress.setUnlocked(unlocked);
        markDirty(playerId);
        return true;
    }

    public void addVolt(UUID playerId, BigNumber amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.economy().addVolt(amount);
        markDirty(playerId);
    }

    public boolean spendVolt(UUID playerId, BigNumber amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        if (progress.economy().getVolt().lt(amount)) {
            return false;
        }
        progress.economy().addVolt(amount.negate());
        markDirty(playerId);
        return true;
    }

    // ========================================
    // Volt Operations (In-memory CAS + debounced save)
    // ========================================

    /**
     * Add volt to a player. Updates in-memory state atomically
     * and marks dirty for debounced DB save.
     *
     * @param playerId the player's UUID
     * @param amount the amount to add (can be negative to subtract)
     * @return true if the operation succeeded
     */
    public boolean atomicAddVolt(UUID playerId, BigNumber amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        BigNumber[] result = progress.economy().addVoltAndCapture(amount);
        markDirty(playerId);
        checkVoltTutorialThresholds(playerId, result[0], result[1]);
        // Always succeeds; return value is vestigial
        return true;
    }

    private void checkVoltTutorialThresholds(UUID playerId, BigNumber oldBalance, BigNumber newBalance) {
        boolean crossedAscension = oldBalance.lt(AscendConstants.ASCENSION_VOLT_THRESHOLD)
                && newBalance.gte(AscendConstants.ASCENSION_VOLT_THRESHOLD);

        // Mark the ascension tutorial as seen BEFORE the tutorial check,
        // so the tutorial popup is suppressed in favor of the cinematic
        if (crossedAscension) {
            markTutorialSeen(playerId, TutorialTriggerService.ASCENSION);
        }

        if (tutorialTriggerService != null) {
            tutorialTriggerService.checkVoltThresholds(playerId, oldBalance, newBalance);
        }

        AscendPlayerProgress progress = getPlayer(playerId);

        // Trigger ascension every time the threshold is crossed
        if (crossedAscension) {
            if (progress != null && progress.automation().isBreakAscensionEnabled() && progress.gameplay().hasAllChallengeRewards()) {
                // Check for transcendence eligibility notification
                checkTranscendenceNotification(playerId, oldBalance, newBalance, progress);
                return; // Break mode active — suppress auto-ascension
            }
            // Auto Ascend skill + toggle: skip popup and cinematic, ascend immediately
            if (ascensionManager != null && ascensionManager.hasAutoAscend(playerId)
                    && isAutoAscendEnabled(playerId)) {
                performInstantAscension(playerId);
            } else {
                showAscensionExplainer(playerId);
            }
        }

        // Also check transcendence threshold when break ascension is active
        if (progress != null && progress.automation().isBreakAscensionEnabled() && progress.gameplay().hasAllChallengeRewards()) {
            checkTranscendenceNotification(playerId, oldBalance, newBalance, progress);
        }
    }

    private void checkTranscendenceNotification(UUID playerId, BigNumber oldBalance, BigNumber newBalance, AscendPlayerProgress progress) {
        boolean crossedTranscendence = oldBalance.lt(AscendConstants.TRANSCENDENCE_VOLT_THRESHOLD)
                && newBalance.gte(AscendConstants.TRANSCENDENCE_VOLT_THRESHOLD);
        if (!crossedTranscendence) {
            return;
        }

        if (hudManager != null) {
            hudManager.showToast(playerId, io.hyvexa.ascend.hud.ToastType.ECONOMY,
                "Transcendence available! Talk to the NPC.");
        }
    }

    private void clearAscensionCinematicState(UUID playerId) {
        if (playerId != null) {
            ascensionCinematicActive.remove(playerId);
        }
    }

    private record ResolvedPlayerContext(
        com.hypixel.hytale.server.core.universe.PlayerRef playerRef,
        com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref,
        com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
        com.hypixel.hytale.server.core.universe.world.World world,
        com.hypixel.hytale.server.core.entity.entities.Player player
    ) {}

    private void resolvePlayerContext(UUID playerId,
                                      Runnable onFailure,
                                      java.util.function.Consumer<ResolvedPlayerContext> onResolved) {
        Function<UUID, PlayerRef> playerRefLookup = this.playerRefLookup;
        if (playerRefLookup == null) {
            onFailure.run();
            return;
        }

        com.hypixel.hytale.server.core.universe.PlayerRef playerRef = playerRefLookup.apply(playerId);
        if (playerRef == null) {
            onFailure.run();
            return;
        }

        com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref =
            playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            onFailure.run();
            return;
        }

        com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store =
            ref.getStore();
        if (store == null) {
            onFailure.run();
            return;
        }

        com.hypixel.hytale.server.core.universe.world.World world = store.getExternalData().getWorld();
        if (world == null) {
            onFailure.run();
            return;
        }

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) {
                onFailure.run();
                return;
            }
            com.hypixel.hytale.server.core.entity.entities.Player player =
                store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (player == null) {
                onFailure.run();
                return;
            }
            onResolved.accept(new ResolvedPlayerContext(playerRef, ref, store, world, player));
        }, world);
    }

    private void showAscensionExplainer(UUID playerId) {
        if (!ascensionCinematicActive.add(playerId)) {
            return; // Already in progress for this player
        }
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            resolvePlayerContext(
                playerId,
                () -> clearAscensionCinematicState(playerId),
                ctx -> ctx.player().getPageManager().openCustomPage(
                    ctx.ref(),
                    ctx.store(),
                    new io.hyvexa.ascend.ui.AscendAscensionExplainerPage(ctx.playerRef(), this)
                )
            );
        }, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Performs an instant ascension (no popup, no cinematic) for players with AUTO_ASCEND skill.
     */
    private void performInstantAscension(UUID playerId) {
        if (!ascensionCinematicActive.add(playerId)) {
            return; // Already in progress
        }
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            resolvePlayerContext(
                playerId,
                () -> clearAscensionCinematicState(playerId),
                ctx -> performAutoAscension(playerId, ctx.player(), ctx.playerRef(), ctx.ref(), ctx.store())
            );
        }, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Called by AscendAscensionExplainerPage when the player clicks Continue (or dismisses).
     * Proceeds with the cinematic and auto-ascension.
     */
    public void proceedWithAscensionCinematic(UUID playerId) {
        // The player is already in ascensionCinematicActive from showAscensionExplainer
        triggerAscensionCinematic(playerId);
    }

    private void triggerAscensionCinematic(UUID playerId) {
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            resolvePlayerContext(playerId, () -> clearAscensionCinematicState(playerId), ctx -> {
                com.hypixel.hytale.server.core.io.PacketHandler ph = ctx.playerRef().getPacketHandler();
                if (ph == null) {
                    clearAscensionCinematicState(playerId);
                    return;
                }

                // Auto-ascend after cinematic completes
                Runnable onComplete = () ->
                    performAutoAscension(playerId, ctx.player(), ctx.playerRef(), ctx.ref(), ctx.store());

                io.hyvexa.ascend.ascension.AscensionCinematic.play(
                    ctx.player(),
                    ph,
                    ctx.playerRef(),
                    ctx.store(),
                    ctx.ref(),
                    ctx.world(),
                    onComplete
                );
            });
        }, 1500, TimeUnit.MILLISECONDS);
    }

    private void performAutoAscension(UUID playerId,
                                      com.hypixel.hytale.server.core.entity.entities.Player player,
                                      com.hypixel.hytale.server.core.universe.PlayerRef playerRef,
                                      com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref,
                                      com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store) {
        try {
            if (ascensionManager == null) return;

            if (!ascensionManager.canAscend(playerId)) return;

            // Route to challenge completion if in a challenge
            if (challengeManager != null && challengeManager.isInChallenge(playerId)) {
                AscendConstants.ChallengeType type = challengeManager.getActiveChallenge(playerId);
                long elapsedMs = challengeManager.completeChallenge(playerId);
                if (elapsedMs >= 0) {
                    String timeStr = io.hyvexa.common.util.FormatUtils.formatDurationLong(elapsedMs);
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                        "[Challenge] " + (type != null ? type.getDisplayName() : "Challenge") + " completed in " + timeStr + "!")
                        .color(io.hyvexa.common.util.SystemMessageUtils.SUCCESS));
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                        "[Challenge] Your progress has been restored. Permanent reward unlocked!")
                        .color(io.hyvexa.common.util.SystemMessageUtils.SUCCESS));
                }
                return;
            }

            // Despawn all robots before resetting data to prevent completions with pre-reset multipliers
            if (robotManager != null) {
                robotManager.despawnRobotsForPlayer(playerId);
            }

            int newCount = ascensionManager.performAscension(playerId);
            if (newCount < 0) return;

            // Auto-buy map 1 runner so the player doesn't need any manual action
            if (runtimeMapStore != null) {
                List<AscendMap> maps = runtimeMapStore.listMapsSorted();
                if (!maps.isEmpty()) {
                    String firstMapId = maps.get(0).getId();
                    setMapUnlocked(playerId, firstMapId, true);
                    // Auto-buy runner if player has completed map 1 before (ghost or best time)
                    boolean hasGhost = ghostStore != null && ghostStore.getRecording(playerId, firstMapId) != null;
                    boolean hasBestTime = getBestTimeMs(playerId, firstMapId) != null;
                    if (hasGhost || hasBestTime) {
                        setHasRobot(playerId, firstMapId, true);
                    }
                }
            }

            // Chat messages
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                "[Ascension] You have Ascended! (x" + newCount + ")")
                .color(io.hyvexa.common.util.SystemMessageUtils.SUCCESS));
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                "[Ascension] +1 AP. All progress has been reset.")
                .color(io.hyvexa.common.util.SystemMessageUtils.SUCCESS));
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                "[Ascension] Use /ascend skills to unlock abilities.")
                .color(io.hyvexa.common.util.SystemMessageUtils.SECONDARY));

            // Check achievements
            if (achievementManager != null) {
                achievementManager.checkAndUnlockAchievements(playerId, player);
            }

            // Show ascension tutorial page only on first ascension
            if (newCount == 1) {
                HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                    if (ref == null || !ref.isValid()) return;
                    com.hypixel.hytale.server.core.universe.world.World world = store.getExternalData().getWorld();
                    if (world == null) return;
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        if (!ref.isValid()) return;
                        com.hypixel.hytale.server.core.entity.entities.Player p =
                            store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
                        if (p == null) return;
                        p.getPageManager().openCustomPage(ref, store,
                            new io.hyvexa.ascend.ui.AscendTutorialPage(playerRef,
                                io.hyvexa.ascend.ui.AscendTutorialPage.Tutorial.ASCENSION));
                    }, world);
                }, 500, TimeUnit.MILLISECONDS);
            }
        } finally {
            ascensionCinematicActive.remove(playerId);
        }
    }

    /**
     * Spend volt with balance check (prevents negative balance).
     * Uses in-memory CAS loop. Returns false if insufficient funds.
     *
     * @param playerId the player's UUID
     * @param amount the amount to spend (must be positive)
     * @return true if the purchase succeeded (sufficient balance)
     */
    public boolean atomicSpendVolt(UUID playerId, BigNumber amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        BigNumber current;
        BigNumber updated;
        do {
            current = progress.economy().getVolt();
            if (current.lt(amount)) {
                return false;
            }
            updated = current.subtract(amount);
        } while (!progress.economy().casVolt(current, updated));
        markDirty(playerId);
        return true;
    }

    /**
     * Add to total volt earned (lifetime stat) + accumulated volt trackers.
     * In-memory update + debounced save.
     *
     * @param playerId the player's UUID
     * @param amount the amount to add
     * @return true if the operation succeeded
     */
    public boolean atomicAddTotalVoltEarned(UUID playerId, BigNumber amount) {
        addTotalVoltEarned(playerId, amount);
        return true;
    }

    /**
     * Add to map multiplier. In-memory update + debounced save.
     *
     * @param playerId the player's UUID
     * @param mapId the map ID
     * @param amount the amount to add
     * @return true if the operation succeeded
     */
    public boolean atomicAddMapMultiplier(UUID playerId, String mapId, BigNumber amount) {
        addMapMultiplier(playerId, mapId, amount);
        return true;
    }

    /**
     * Set elevation level and reset volt to 0 (for elevation purchase).
     * In-memory update + debounced save.
     *
     * @param playerId the player's UUID
     * @param newElevation the new elevation level
     * @return true if the operation succeeded
     */
    public boolean atomicSetElevationAndResetVolt(UUID playerId, int newElevation) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.economy().setElevationMultiplier(newElevation);
        progress.economy().setVolt(BigNumber.ZERO);
        progress.economy().setSummitAccumulatedVolt(BigNumber.ZERO);
        progress.economy().setElevationAccumulatedVolt(BigNumber.ZERO);
        markDirty(playerId);
        return true;
    }

    public BigNumber getMapMultiplier(UUID playerId, String mapId) {
        GameplayState.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return BigNumber.ONE;
        }
        return mapProgress.getMultiplier().max(BigNumber.ONE);
    }

    public BigNumber addMapMultiplier(UUID playerId, String mapId, BigNumber amount) {
        GameplayState.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        BigNumber value = mapProgress.addMultiplier(amount);
        markDirty(playerId);
        return value;
    }

    public boolean hasRobot(UUID playerId, String mapId) {
        GameplayState.MapProgress mapProgress = getMapProgress(playerId, mapId);
        return mapProgress != null && mapProgress.hasRobot();
    }

    public void setHasRobot(UUID playerId, String mapId, boolean hasRobot) {
        GameplayState.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        mapProgress.setHasRobot(hasRobot);
        markDirty(playerId);
        notifyRobotManager(playerId);
    }

    public int getRobotSpeedLevel(UUID playerId, String mapId) {
        GameplayState.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return 0;
        }
        return Math.max(0, mapProgress.getRobotSpeedLevel());
    }

    public int incrementRobotSpeedLevel(UUID playerId, String mapId) {
        GameplayState.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        int value = mapProgress.incrementRobotSpeedLevel();
        markDirty(playerId);
        notifyRobotManager(playerId);
        return value;
    }

    /**
     * Check if any maps should be auto-unlocked based on runner levels.
     * Called after incrementing runner speed level.
     * Returns the list of newly unlocked map IDs for notification.
     */
    public List<String> checkAndUnlockEligibleMaps(UUID playerId, AscendMapStore mapStore) {
        if (playerId == null || mapStore == null) {
            return List.of();
        }

        List<String> newlyUnlockedMapIds = new java.util.ArrayList<>();
        List<AscendMap> allMaps = new java.util.ArrayList<>(mapStore.listMapsSorted());

        // Start from index 1 (skip first map which is always unlocked)
        for (int i = 1; i < allMaps.size(); i++) {
            AscendMap map = allMaps.get(i);
            if (map == null) {
                continue;
            }

            // Check if already unlocked
            GameplayState.MapProgress mapProgress = getMapProgress(playerId, map.getId());
            if (mapProgress != null && mapProgress.isUnlocked()) {
                continue;
            }

            // Check if meets unlock requirement
            if (MapUnlockHelper.meetsUnlockRequirement(playerId, map, this, mapStore, challengeManager)) {
                setMapUnlocked(playerId, map.getId(), true);
                newlyUnlockedMapIds.add(map.getId());
            }
        }

        return newlyUnlockedMapIds;
    }

    public int getRobotStars(UUID playerId, String mapId) {
        GameplayState.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return 0;
        }
        return Math.max(0, mapProgress.getRobotStars());
    }

    public int evolveRobot(UUID playerId, String mapId) {
        GameplayState.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        mapProgress.setRobotSpeedLevel(0);
        int newStars = mapProgress.incrementRobotStars();
        // Evolution Power applied per star (handled in AscendConstants.getRunnerMultiplierIncrement)
        markDirty(playerId);
        notifyRobotManager(playerId);
        return newStars;
    }

    // ========================================
    // Summit System Methods
    // ========================================

    public int getSummitLevel(UUID playerId, SummitCategory category) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return 0;
        }
        return progress.economy().getSummitLevel(category);
    }

    public double getSummitXp(UUID playerId, SummitCategory category) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return 0.0;
        }
        return progress.economy().getSummitXp(category);
    }

    public double addSummitXp(UUID playerId, SummitCategory category, double amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        double newXp = progress.economy().addSummitXp(category, amount);
        markDirty(playerId);
        return newXp;
    }

    public Map<SummitCategory, Integer> getSummitLevels(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return Map.of();
        }
        return progress.economy().getSummitLevels();
    }

    public double getSummitBonusDouble(UUID playerId, SummitCategory category) {
        int level = getSummitLevel(playerId, category);
        return category.getBonusForLevel(level);
    }

    public BigNumber getTotalVoltEarned(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.economy().getTotalVoltEarned() : BigNumber.ZERO;
    }

    public BigNumber getSummitAccumulatedVolt(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.economy().getSummitAccumulatedVolt() : BigNumber.ZERO;
    }

    public void addSummitAccumulatedVolt(UUID playerId, BigNumber amount) {
        if (amount.lte(BigNumber.ZERO)) {
            return;
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.economy().addSummitAccumulatedVolt(amount);
        markDirty(playerId);
    }

    public void addElevationAccumulatedVolt(UUID playerId, BigNumber amount) {
        if (amount.lte(BigNumber.ZERO)) {
            return;
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.economy().addElevationAccumulatedVolt(amount);
        markDirty(playerId);
    }

    public void addTotalVoltEarned(UUID playerId, BigNumber amount) {
        if (amount.lte(BigNumber.ZERO)) {
            return;
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.economy().addTotalVoltEarned(amount);
        progress.economy().addSummitAccumulatedVolt(amount);
        progress.economy().addElevationAccumulatedVolt(amount);
        markDirty(playerId);
    }

    public BigNumber getElevationAccumulatedVolt(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.economy().getElevationAccumulatedVolt() : BigNumber.ZERO;
    }

    // ========================================
    // Ascension System Methods
    // ========================================

    public int getAscensionCount(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.gameplay().getAscensionCount() : 0;
    }

    public int getTranscendenceCount(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.gameplay().getTranscendenceCount() : 0;
    }

    public int getSkillTreePoints(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.gameplay().getSkillTreePoints() : 0;
    }

    public int addSkillTreePoints(UUID playerId, int amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        int newPoints = progress.gameplay().addSkillTreePoints(amount);
        markDirty(playerId);
        return newPoints;
    }

    public int getAvailableSkillPoints(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.gameplay().getAvailableSkillPoints() : 0;
    }

    public boolean hasSkillNode(UUID playerId, SkillTreeNode node) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.gameplay().hasSkillNode(node);
    }

    public boolean unlockSkillNode(UUID playerId, SkillTreeNode node) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        if (progress.gameplay().hasSkillNode(node)) {
            return false;
        }
        if (progress.gameplay().getAvailableSkillPoints() < node.getCost()) {
            return false;
        }
        progress.gameplay().unlockSkillNode(node);
        markDirty(playerId);
        return true;
    }

    public Set<SkillTreeNode> getUnlockedSkillNodes(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return EnumSet.noneOf(SkillTreeNode.class);
        }
        return progress.gameplay().getUnlockedSkillNodes();
    }

    // ========================================
    // Achievement System Methods
    // ========================================

    public boolean hasAchievement(UUID playerId, AchievementType achievement) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.gameplay().hasAchievement(achievement);
    }

    public boolean unlockAchievement(UUID playerId, AchievementType achievement) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        if (progress.gameplay().hasAchievement(achievement)) {
            return false;
        }
        progress.gameplay().unlockAchievement(achievement);
        markDirty(playerId);
        return true;
    }

    public Set<AchievementType> getUnlockedAchievements(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return EnumSet.noneOf(AchievementType.class);
        }
        return progress.gameplay().getUnlockedAchievements();
    }

    public int getTotalManualRuns(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.gameplay().getTotalManualRuns() : 0;
    }

    public int incrementTotalManualRuns(UUID playerId) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        int count = progress.gameplay().incrementTotalManualRuns();
        markDirty(playerId);
        return count;
    }

    public int getConsecutiveManualRuns(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.gameplay().getConsecutiveManualRuns() : 0;
    }

    public int incrementConsecutiveManualRuns(UUID playerId) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        int count = progress.gameplay().incrementConsecutiveManualRuns();
        markDirty(playerId);
        return count;
    }

    public void resetConsecutiveManualRuns(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress != null) {
            progress.gameplay().resetConsecutiveManualRuns();
            markDirty(playerId);
        }
    }

    /**
     * Shared reset logic for prestige operations.
     * Resets volt, map unlocks (except first), multipliers, manual completion, and runners.
     * @param clearBestTimes whether to also clear best times (elevation does, summit doesn't)
     * @return list of map IDs that had runners (for despawn handling)
     */
    private List<String> resetMapProgress(AscendPlayerProgress progress, String firstMapId, boolean clearBestTimes, UUID playerId) {
        List<String> mapsWithRunners = new java.util.ArrayList<>();

        progress.economy().setVolt(BigNumber.ZERO);
        progress.economy().setSummitAccumulatedVolt(BigNumber.ZERO);
        progress.economy().setElevationAccumulatedVolt(BigNumber.ZERO);

        for (Map.Entry<String, GameplayState.MapProgress> entry : progress.gameplay().getMapProgress().entrySet()) {
            String mapId = entry.getKey();
            GameplayState.MapProgress mapProgress = entry.getValue();

            mapProgress.setUnlocked(mapId.equals(firstMapId));
            mapProgress.setMultiplier(BigNumber.ONE);
            mapProgress.setCompletedManually(false);

            if (clearBestTimes) {
                mapProgress.setBestTimeMs(null);
            }

            if (mapProgress.hasRobot()) {
                mapsWithRunners.add(mapId);
                mapProgress.setHasRobot(false);
                mapProgress.setRobotSpeedLevel(0);
                mapProgress.setRobotStars(0);
            }
        }

        if (firstMapId != null && !firstMapId.isEmpty()) {
            GameplayState.MapProgress firstMapProgress = progress.gameplay().getOrCreateMapProgress(firstMapId);
            firstMapProgress.setUnlocked(true);
        }

        return mapsWithRunners;
    }

    /**
     * Resets player progress for elevation: clears volt, map unlocks (except first map),
     * multipliers, and removes all runners. Best times are preserved.
     *
     * @param playerId the player's UUID
     * @param firstMapId the ID of the first map (stays unlocked)
     * @return list of map IDs that had runners (for despawn handling)
     */
    public List<String> resetProgressForElevation(UUID playerId, String firstMapId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return List.of();
        }

        List<String> mapsWithRunners = resetMapProgress(progress, firstMapId, false, playerId);
        markDirty(playerId);
        notifyRobotManager(playerId);
        return mapsWithRunners;
    }

    /**
     * Reset progress for Summit: volt, elevation, multipliers, runners, and map unlocks.
     * Keeps best times and summit XP.
     * @return list of map IDs that had runners (for despawn handling)
     */
    public List<String> resetProgressForSummit(UUID playerId, String firstMapId) {
        AscendPlayerProgress progress = getPlayer(playerId);
        if (progress == null) {
            return List.of();
        }

        progress.economy().setElevationMultiplier(1);
        progress.automation().setAutoElevationTargetIndex(0);

        List<String> mapsWithRunners = resetMapProgress(progress, firstMapId, false, playerId);

        markDirty(playerId);
        notifyRobotManager(playerId);
        return mapsWithRunners;
    }

    /**
     * Reset progress for a challenge: same as summit reset.
     * Resets volt, elevation, multipliers, runners, and map unlocks.
     * Keeps best times.
     * @return list of map IDs that had runners (for despawn handling)
     */
    public List<String> resetProgressForChallenge(UUID playerId, String firstMapId) {
        AscendPlayerProgress progress = getPlayer(playerId);
        if (progress == null) {
            return List.of();
        }

        progress.economy().setElevationMultiplier(1);
        progress.automation().setAutoElevationTargetIndex(0);
        progress.economy().clearSummitXp();

        List<String> mapsWithRunners = resetMapProgress(progress, firstMapId, false, playerId);
        markDirty(playerId);
        notifyRobotManager(playerId);
        return mapsWithRunners;
    }

    private void notifyRobotManager(UUID playerId) {
        if (robotManager == null) {
            return;
        }
        robotManager.markPlayerDirty(playerId);
    }

    public boolean isSessionFirstRunClaimed(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.session().isSessionFirstRunClaimed();
    }

    public void setSessionFirstRunClaimed(UUID playerId, boolean claimed) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.session().setSessionFirstRunClaimed(claimed);
        // Don't persist - this is session-only
    }

    public BigNumber[] getMultiplierDisplayValues(UUID playerId, List<AscendMap> maps, int slotCount) {
        int slots = Math.max(0, slotCount);
        BigNumber[] digits = new BigNumber[slots];
        for (int i = 0; i < slots; i++) {
            digits[i] = BigNumber.ONE;
        }
        if (maps == null || maps.isEmpty() || slots == 0) {
            return digits;
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            BigNumber value = getMapMultiplier(playerId, map.getId());
            double challengeMapBonus = getChallengeMapBonus(playerId, map.getDisplayOrder());
            if (challengeMapBonus > 1.0) {
                value = value.multiply(BigNumber.fromDouble(challengeMapBonus));
            }
            digits[index] = value;
            index++;
        }
        return digits;
    }

    public BigNumber getMultiplierProduct(UUID playerId, List<AscendMap> maps, int slotCount) {
        BigNumber product = BigNumber.ONE;
        int slots = Math.max(0, slotCount);
        if (maps == null || maps.isEmpty() || slots == 0) {
            BigNumber elevation = BigNumber.fromDouble(getCalculatedElevationMultiplier(playerId));
            return product.multiply(elevation);
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            BigNumber value = getMapMultiplier(playerId, map.getId());
            double challengeMapBonus = getChallengeMapBonus(playerId, map.getDisplayOrder());
            if (challengeMapBonus > 1.0) {
                value = value.multiply(BigNumber.fromDouble(challengeMapBonus));
            }
            product = product.multiply(value.max(BigNumber.ONE));
            index++;
        }
        BigNumber elevation = BigNumber.fromDouble(getCalculatedElevationMultiplier(playerId));
        return product.multiply(elevation);
    }

    /**
     * Computes both the multiplier product and per-slot display values in a single pass.
     */
    public MultiplierResult getMultiplierProductAndValues(UUID playerId, List<AscendMap> maps, int slotCount) {
        int slots = Math.max(0, slotCount);
        BigNumber[] digits = new BigNumber[slots];
        for (int i = 0; i < slots; i++) {
            digits[i] = BigNumber.ONE;
        }
        BigNumber product = BigNumber.ONE;
        if (maps != null && !maps.isEmpty() && slots > 0) {
            int index = 0;
            for (AscendMap map : maps) {
                if (index >= slots) {
                    break;
                }
                if (map == null || map.getId() == null) {
                    continue;
                }
                BigNumber value = getMapMultiplier(playerId, map.getId());
                double challengeMapBonus = getChallengeMapBonus(playerId, map.getDisplayOrder());
                if (challengeMapBonus > 1.0) {
                    value = value.multiply(BigNumber.fromDouble(challengeMapBonus));
                }
                digits[index] = value;
                product = product.multiply(value.max(BigNumber.ONE));
                index++;
            }
        }
        BigNumber elevation = BigNumber.fromDouble(getCalculatedElevationMultiplier(playerId));
        product = product.multiply(elevation);
        return new MultiplierResult(product, digits);
    }

    public static final class MultiplierResult {
        public final BigNumber product;
        public final BigNumber[] values;

        MultiplierResult(BigNumber product, BigNumber[] values) {
            this.product = product;
            this.values = values;
        }
    }

    public BigNumber getCompletionPayout(UUID playerId, List<AscendMap> maps, int slotCount, String mapId, BigNumber bonusAmount) {
        BigNumber product = BigNumber.ONE;
        int slots = Math.max(0, slotCount);
        if (maps == null || maps.isEmpty() || slots == 0) {
            BigNumber elevation = BigNumber.fromDouble(getCalculatedElevationMultiplier(playerId));
            return product.multiply(elevation);
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            BigNumber value = getMapMultiplier(playerId, map.getId());
            double challengeMapBonus = getChallengeMapBonus(playerId, map.getDisplayOrder());
            if (challengeMapBonus > 1.0) {
                value = value.multiply(BigNumber.fromDouble(challengeMapBonus));
            }
            if (map.getId().equals(mapId)) {
                value = value.add(bonusAmount);
            }
            product = product.multiply(value.max(BigNumber.ONE));
            index++;
        }
        BigNumber elevation = BigNumber.fromDouble(getCalculatedElevationMultiplier(playerId));
        return product.multiply(elevation);
    }

    /**
     * Get map base multiplier bonus from completed challenge rewards.
     * Delegates to ChallengeManager if available.
     */
    private double getChallengeMapBonus(UUID playerId, int displayOrder) {
        if (challengeManager != null) {
            return challengeManager.getChallengeMapBaseMultiplier(playerId, displayOrder);
        }
        return 1.0;
    }

    // ========================================
    // Tutorial Tracking
    // ========================================

    public boolean hasSeenTutorial(UUID playerId, int bit) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.gameplay().hasSeenTutorial(bit);
    }

    public void markTutorialSeen(UUID playerId, int bit) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.gameplay().markTutorialSeen(bit);
        markDirty(playerId);
    }

    /**
     * Delete ALL player data from the database and clear all in-memory caches.
     * This is a server-wide wipe for launching in a fresh state.
     */
    public void resetAllPlayersProgress() {
        // Cancel any pending debounced save to prevent stale data re-insertion
        persistence.cancelPendingSave();

        // Wipe DB FIRST — if we clear in-memory caches first, another thread
        // (passive earnings, run tracker) can call getOrCreatePlayer() which
        // re-loads old data from the not-yet-wiped DB back into memory.
        persistence.deleteAllPlayerData();

        // Now clear in-memory caches — any subsequent getOrCreatePlayer() will
        // find an empty DB and create fresh default progress
        players.clear();
        playerNames.clear();
        persistence.clearDirtyState();
        resetPendingPlayers.clear();

        // Invalidate leaderboard caches
        persistence.clearLeaderboardCaches();
    }

    public void removePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }

        // Snapshot dirty data before eviction so async save can find it
        AscendPlayerProgress progress = players.get(playerId);
        if (progress != null && persistence.isDirty(playerId)) {
            persistence.snapshotForSave(playerId, progress);
        }

        // Disconnect path: queue targeted async persistence and then evict cache.
        persistence.savePlayerIfDirty(playerId);

        // Remove from cache
        players.remove(playerId);
        resetPendingPlayers.remove(playerId);
        ascensionCinematicActive.remove(playerId);
    }

    public void flushPendingSave() {
        persistence.flushPendingSave();
    }

    /**
     * Synchronously save a single player's dirty data and return whether it succeeded.
     * Used for idempotency-sensitive operations where we must confirm persistence
     * before proceeding (e.g. passive earnings claim).
     */
    public boolean savePlayerSync(UUID playerId) {
        return persistence.savePlayerSync(playerId);
    }

    // ========================================
    // Leaderboard (delegated to persistence)
    // ========================================

    public List<LeaderboardEntry> getLeaderboardEntries() {
        return persistence.getLeaderboardEntries();
    }

    public void invalidateLeaderboardCache() {
        persistence.invalidateLeaderboardCache();
    }

    public void invalidateMapLeaderboardCache(String mapId) {
        persistence.invalidateMapLeaderboardCache(mapId);
    }

    public List<MapLeaderboardEntry> getMapLeaderboard(String mapId) {
        return persistence.getMapLeaderboard(mapId);
    }

    // ========================================
    // Passive Earnings
    // ========================================

    public Long getLastActiveTimestamp(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.session().getLastActiveTimestamp() : null;
    }

    public void setLastActiveTimestamp(UUID playerId, Long timestamp) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.session().setLastActiveTimestamp(timestamp);
        markDirty(playerId);
    }

    public boolean hasUnclaimedPassive(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.session().hasUnclaimedPassive();
    }

    public void setHasUnclaimedPassive(UUID playerId, boolean hasUnclaimed) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.session().setHasUnclaimedPassive(hasUnclaimed);
        markDirty(playerId);
    }

    public boolean isAutoUpgradeEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.automation().isAutoUpgradeEnabled();
    }

    public void setAutoUpgradeEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.automation().setAutoUpgradeEnabled(enabled);
        markDirty(playerId);
    }

    public boolean isAutoEvolutionEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.automation().isAutoEvolutionEnabled();
    }

    public void setAutoEvolutionEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.automation().setAutoEvolutionEnabled(enabled);
        markDirty(playerId);
    }

    public boolean isHideOtherRunners(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.automation().isHideOtherRunners();
    }

    public void setHideOtherRunners(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.automation().setHideOtherRunners(enabled);
        markDirty(playerId);
    }

    public boolean isHudHidden(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.session().isHudHidden();
    }

    public void setHudHidden(UUID playerId, boolean hidden) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.session().setHudHidden(hidden);
        markDirty(playerId);
    }

    public boolean isPlayersHidden(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.session().isPlayersHidden();
    }

    public void setPlayersHidden(UUID playerId, boolean hidden) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.session().setPlayersHidden(hidden);
        markDirty(playerId);
    }

    public boolean isBreakAscensionEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.automation().isBreakAscensionEnabled();
    }

    // ========================================
    // Auto-Elevation
    // ========================================

    public boolean isAutoElevationEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.automation().isAutoElevationEnabled();
    }

    public void setAutoElevationEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.automation().setAutoElevationEnabled(enabled);
        markDirty(playerId);
    }

    public int getAutoElevationTimerSeconds(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.automation().getAutoElevationTimerSeconds() : 0;
    }

    public void setAutoElevationTimerSeconds(UUID playerId, int seconds) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.automation().setAutoElevationTimerSeconds(seconds);
        markDirty(playerId);
    }

    public java.util.List<Long> getAutoElevationTargets(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.automation().getAutoElevationTargets() : java.util.Collections.emptyList();
    }

    public void setAutoElevationTargets(UUID playerId, java.util.List<Long> targets) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.automation().setAutoElevationTargets(targets);
        markDirty(playerId);
    }

    public int getAutoElevationTargetIndex(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.automation().getAutoElevationTargetIndex() : 0;
    }

    public void setAutoElevationTargetIndex(UUID playerId, int index) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        if (progress.automation().getAutoElevationTargetIndex() == index) {
            return;
        }
        progress.automation().setAutoElevationTargetIndex(index);
        markDirty(playerId);
    }

    // ========================================
    // Auto-Summit
    // ========================================

    public boolean isAutoSummitEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.automation().isAutoSummitEnabled();
    }

    public void setAutoSummitEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.automation().setAutoSummitEnabled(enabled);
        markDirty(playerId);
    }

    public int getAutoSummitTimerSeconds(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.automation().getAutoSummitTimerSeconds() : 0;
    }

    public void setAutoSummitTimerSeconds(UUID playerId, int seconds) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.automation().setAutoSummitTimerSeconds(seconds);
        markDirty(playerId);
    }

    public java.util.List<AutomationConfig.AutoSummitCategoryConfig> getAutoSummitConfig(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.automation().getAutoSummitConfig() : java.util.List.of();
    }

    public void setAutoSummitConfig(UUID playerId, java.util.List<AutomationConfig.AutoSummitCategoryConfig> config) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.automation().setAutoSummitConfig(config);
        markDirty(playerId);
    }

    public int getAutoSummitRotationIndex(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.automation().getAutoSummitRotationIndex() : 0;
    }

    public void setAutoSummitRotationIndex(UUID playerId, int index) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.automation().setAutoSummitRotationIndex(index);
        markDirty(playerId);
    }

    public void setBreakAscensionEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.automation().setBreakAscensionEnabled(enabled);
        markDirty(playerId);

        // If disabling break mode while above threshold, trigger ascension
        if (!enabled && progress.economy().getVolt().gte(AscendConstants.ASCENSION_VOLT_THRESHOLD)) {
            if (ascensionManager != null
                    && ascensionManager.hasAutoAscend(playerId)
                    && isAutoAscendEnabled(playerId)) {
                performInstantAscension(playerId);
            } else {
                triggerAscensionCinematic(playerId);
            }
        }
    }

    // ========================================
    // Auto-Ascend
    // ========================================

    public boolean isAutoAscendEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.automation().isAutoAscendEnabled();
    }

    public void setAutoAscendEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.automation().setAutoAscendEnabled(enabled);
        markDirty(playerId);
    }

}
