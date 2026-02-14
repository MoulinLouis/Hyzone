package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.AchievementType;
import io.hyvexa.ascend.AscendConstants.SkillTreeNode;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.tutorial.TutorialTriggerService;
import io.hyvexa.ascend.util.MapUnlockHelper;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


public class AscendPlayerStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<UUID, AscendPlayerProgress> players = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();
    private final Set<UUID> resetPendingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> ascensionCinematicActive = ConcurrentHashMap.newKeySet();

    private final AscendPlayerPersistence persistence;

    public AscendPlayerStore() {
        this.persistence = new AscendPlayerPersistence(players, playerNames, resetPendingPlayers);
    }

    public record LeaderboardEntry(UUID playerId, String playerName,
            double totalVexaEarnedMantissa, int totalVexaEarnedExp10,
            int ascensionCount, int totalManualRuns, Long fastestAscensionMs) {}

    public record MapLeaderboardEntry(String playerName, long bestTimeMs) {}

    /**
     * Initialize the store. With lazy loading, we don't load all players upfront.
     * Players are loaded on-demand when they connect.
     */
    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
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
            loaded.setAscensionStartedAt(System.currentTimeMillis());
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
        if (progress.getAscensionStartedAt() == null) {
            progress.setAscensionStartedAt(System.currentTimeMillis());
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
        progress.setVexa(BigNumber.ZERO);
        progress.setElevationMultiplier(1);
        progress.getMapProgress().clear();

        // Reset Summit system
        progress.clearSummitXp();
        progress.setTotalVexaEarned(BigNumber.ZERO);
        progress.setSummitAccumulatedVexa(BigNumber.ZERO);
        progress.setElevationAccumulatedVexa(BigNumber.ZERO);

        // Reset Ascension/Skill Tree
        progress.setAscensionCount(0);
        progress.setSkillTreePoints(0);
        progress.setUnlockedSkillNodes(null);

        // Reset Achievements
        progress.setUnlockedAchievements(null);

        // Reset Statistics
        progress.setTotalManualRuns(0);
        progress.setConsecutiveManualRuns(0);
        progress.setSessionFirstRunClaimed(false);

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

    public void markDirty(UUID playerId) {
        if (playerId == null) {
            return;
        }
        persistence.markDirty(playerId);
        persistence.queueSave();
    }

    public BigNumber getVexa(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getVexa() : BigNumber.ZERO;
    }

    public void setVexa(UUID playerId, BigNumber vexa) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setVexa(vexa.max(BigNumber.ZERO));
        markDirty(playerId);
    }

    /**
     * Gets the raw elevation level (stored value).
     * For the actual multiplier value, use {@link #getCalculatedElevationMultiplier(UUID)}.
     */
    public int getElevationLevel(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getElevationMultiplier() : 0;
    }

    /**
     * Get the player's elevation multiplier.
     * Returns level^1.05 — slightly super-linear to reward higher elevation.
     */
    public double getCalculatedElevationMultiplier(UUID playerId) {
        return AscendConstants.getElevationMultiplier(getElevationLevel(playerId));
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
        int value = progress.addElevationMultiplier(amount);
        markDirty(playerId);
        return value;
    }

    public AscendPlayerProgress.MapProgress getMapProgress(UUID playerId, String mapId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return null;
        }
        return progress.getMapProgress().get(mapId);
    }

    public AscendPlayerProgress.MapProgress getOrCreateMapProgress(UUID playerId, String mapId) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        return progress.getOrCreateMapProgress(mapId);
    }

    public boolean setMapUnlocked(UUID playerId, String mapId, boolean unlocked) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        if (mapProgress.isUnlocked() == unlocked) {
            return false;
        }
        mapProgress.setUnlocked(unlocked);
        markDirty(playerId);
        return true;
    }

    public void addVexa(UUID playerId, BigNumber amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addVexa(amount);
        markDirty(playerId);
    }

    public boolean spendVexa(UUID playerId, BigNumber amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        if (progress.getVexa().lt(amount)) {
            return false;
        }
        progress.addVexa(amount.negate());
        markDirty(playerId);
        return true;
    }

    // ========================================
    // Vexa Operations (In-memory CAS + debounced save)
    // ========================================

    /**
     * Add vexa to a player. Updates in-memory state atomically
     * and marks dirty for debounced DB save.
     *
     * @param playerId the player's UUID
     * @param amount the amount to add (can be negative to subtract)
     * @return true if the operation succeeded
     */
    public boolean atomicAddVexa(UUID playerId, BigNumber amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        BigNumber oldBalance = progress.getVexa();
        progress.addVexa(amount);
        BigNumber newBalance = progress.getVexa();
        markDirty(playerId);
        checkVexaTutorialThresholds(playerId, oldBalance, newBalance);
        return true;
    }

    private void checkVexaTutorialThresholds(UUID playerId, BigNumber oldBalance, BigNumber newBalance) {
        boolean crossedAscension = oldBalance.lt(AscendConstants.ASCENSION_VEXA_THRESHOLD)
                && newBalance.gte(AscendConstants.ASCENSION_VEXA_THRESHOLD);

        // Mark the ascension tutorial as seen BEFORE the tutorial check,
        // so the tutorial popup is suppressed in favor of the cinematic
        if (crossedAscension) {
            markTutorialSeen(playerId, TutorialTriggerService.ASCENSION);
        }

        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        TutorialTriggerService triggerService = plugin.getTutorialTriggerService();
        if (triggerService != null) {
            triggerService.checkVexaThresholds(playerId, oldBalance, newBalance);
        }

        // Trigger ascension cinematic every time the threshold is crossed
        if (crossedAscension) {
            AscendPlayerProgress progress = getPlayer(playerId);
            if (progress != null && progress.isBreakAscensionEnabled() && progress.hasAllChallengeRewards()) {
                return; // Break mode active — suppress auto-ascension
            }
            showAscensionExplainer(playerId);
        }
    }

    private void showAscensionExplainer(UUID playerId) {
        if (!ascensionCinematicActive.add(playerId)) {
            return; // Already in progress for this player
        }
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin == null) {
                ascensionCinematicActive.remove(playerId);
                return;
            }

            com.hypixel.hytale.server.core.universe.PlayerRef playerRef = plugin.getPlayerRef(playerId);
            if (playerRef == null) {
                ascensionCinematicActive.remove(playerId);
                return;
            }

            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref =
                playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                ascensionCinematicActive.remove(playerId);
                return;
            }

            com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store =
                ref.getStore();
            com.hypixel.hytale.server.core.universe.world.World world = store.getExternalData().getWorld();
            if (world == null) {
                ascensionCinematicActive.remove(playerId);
                return;
            }

            java.util.concurrent.CompletableFuture.runAsync(() -> {
                if (!ref.isValid()) {
                    ascensionCinematicActive.remove(playerId);
                    return;
                }
                com.hypixel.hytale.server.core.entity.entities.Player player =
                    store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
                if (player == null) {
                    ascensionCinematicActive.remove(playerId);
                    return;
                }
                player.getPageManager().openCustomPage(ref, store,
                    new io.hyvexa.ascend.ui.AscendAscensionExplainerPage(playerRef));
            }, world);
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
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin == null) return;

            com.hypixel.hytale.server.core.universe.PlayerRef playerRef = plugin.getPlayerRef(playerId);
            if (playerRef == null) return;

            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref =
                playerRef.getReference();
            if (ref == null || !ref.isValid()) return;

            com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store =
                ref.getStore();
            com.hypixel.hytale.server.core.universe.world.World world = store.getExternalData().getWorld();
            if (world == null) return;

            java.util.concurrent.CompletableFuture.runAsync(() -> {
                if (!ref.isValid()) return;
                com.hypixel.hytale.server.core.entity.entities.Player player =
                    store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
                com.hypixel.hytale.server.core.io.PacketHandler ph = playerRef.getPacketHandler();
                if (player == null || ph == null) return;

                // Auto-ascend after cinematic completes
                Runnable onComplete = () -> {
                    performAutoAscension(playerId, player, playerRef, ref, store);
                };

                io.hyvexa.ascend.ascension.AscensionCinematic.play(player, ph, playerRef, store, ref, world, onComplete);
            }, world);
        }, 1500, TimeUnit.MILLISECONDS);
    }

    private void performAutoAscension(UUID playerId,
                                      com.hypixel.hytale.server.core.entity.entities.Player player,
                                      com.hypixel.hytale.server.core.universe.PlayerRef playerRef,
                                      com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref,
                                      com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store) {
        try {
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin == null) return;

            io.hyvexa.ascend.ascension.AscensionManager ascensionManager = plugin.getAscensionManager();
            if (ascensionManager == null) return;

            if (!ascensionManager.canAscend(playerId)) return;

            // Route to challenge completion if in a challenge
            io.hyvexa.ascend.ascension.ChallengeManager challengeManager = plugin.getChallengeManager();
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

            int newCount = ascensionManager.performAscension(playerId);
            if (newCount < 0) return;

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
            if (plugin.getAchievementManager() != null) {
                plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
            }

            // Show ascension tutorial page after a short delay
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
        } finally {
            ascensionCinematicActive.remove(playerId);
        }
    }

    /**
     * Spend vexa with balance check (prevents negative balance).
     * Uses in-memory CAS loop. Returns false if insufficient funds.
     *
     * @param playerId the player's UUID
     * @param amount the amount to spend (must be positive)
     * @return true if the purchase succeeded (sufficient balance)
     */
    public boolean atomicSpendVexa(UUID playerId, BigNumber amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        BigNumber current;
        BigNumber updated;
        do {
            current = progress.getVexa();
            if (current.lt(amount)) {
                return false;
            }
            updated = current.subtract(amount);
        } while (!progress.casVexa(current, updated));
        markDirty(playerId);
        return true;
    }

    /**
     * Add to total vexa earned (lifetime stat) + accumulated vexa trackers.
     * In-memory update + debounced save.
     *
     * @param playerId the player's UUID
     * @param amount the amount to add
     * @return true if the operation succeeded
     */
    public boolean atomicAddTotalVexaEarned(UUID playerId, BigNumber amount) {
        addTotalVexaEarned(playerId, amount);
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
     * Set elevation level and reset vexa to 0 (for elevation purchase).
     * In-memory update + debounced save.
     *
     * @param playerId the player's UUID
     * @param newElevation the new elevation level
     * @return true if the operation succeeded
     */
    public boolean atomicSetElevationAndResetVexa(UUID playerId, int newElevation) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setElevationMultiplier(newElevation);
        progress.setVexa(BigNumber.ZERO);
        progress.setSummitAccumulatedVexa(BigNumber.ZERO);
        progress.setElevationAccumulatedVexa(BigNumber.ZERO);
        markDirty(playerId);
        return true;
    }

    public BigNumber getMapMultiplier(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return BigNumber.ONE;
        }
        return mapProgress.getMultiplier().max(BigNumber.ONE);
    }

    public BigNumber addMapMultiplier(UUID playerId, String mapId, BigNumber amount) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        BigNumber value = mapProgress.addMultiplier(amount);
        markDirty(playerId);
        return value;
    }

    public boolean hasRobot(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        return mapProgress != null && mapProgress.hasRobot();
    }

    public void setHasRobot(UUID playerId, String mapId, boolean hasRobot) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        mapProgress.setHasRobot(hasRobot);
        markDirty(playerId);
    }

    public int getRobotSpeedLevel(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return 0;
        }
        return Math.max(0, mapProgress.getRobotSpeedLevel());
    }

    public int incrementRobotSpeedLevel(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        int value = mapProgress.incrementRobotSpeedLevel();
        markDirty(playerId);
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
            AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, map.getId());
            if (mapProgress != null && mapProgress.isUnlocked()) {
                continue;
            }

            // Skip maps blocked by active challenge
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin != null && plugin.getChallengeManager() != null
                    && plugin.getChallengeManager().isMapBlocked(playerId, map.getDisplayOrder())) {
                continue;
            }

            // Check if meets unlock requirement
            if (MapUnlockHelper.meetsUnlockRequirement(playerId, map, this, mapStore)) {
                setMapUnlocked(playerId, map.getId(), true);
                newlyUnlockedMapIds.add(map.getId());
            }
        }

        return newlyUnlockedMapIds;
    }

    public int getRobotStars(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return 0;
        }
        return Math.max(0, mapProgress.getRobotStars());
    }

    public int evolveRobot(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        mapProgress.setRobotSpeedLevel(0);
        int newStars = mapProgress.incrementRobotStars();
        // Evolution Power applied per star (handled in AscendConstants.getRunnerMultiplierIncrement)
        markDirty(playerId);
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
        return progress.getSummitLevel(category);
    }

    public long getSummitXp(UUID playerId, SummitCategory category) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return 0L;
        }
        return progress.getSummitXp(category);
    }

    public long addSummitXp(UUID playerId, SummitCategory category, long amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        long newXp = progress.addSummitXp(category, amount);
        markDirty(playerId);
        return newXp;
    }

    public Map<SummitCategory, Integer> getSummitLevels(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return Map.of();
        }
        return progress.getSummitLevels();
    }

    public double getSummitBonusDouble(UUID playerId, SummitCategory category) {
        int level = getSummitLevel(playerId, category);
        return category.getBonusForLevel(level);
    }

    public BigNumber getTotalVexaEarned(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getTotalVexaEarned() : BigNumber.ZERO;
    }

    public BigNumber getSummitAccumulatedVexa(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getSummitAccumulatedVexa() : BigNumber.ZERO;
    }

    public void addSummitAccumulatedVexa(UUID playerId, BigNumber amount) {
        if (amount.lte(BigNumber.ZERO)) {
            return;
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addSummitAccumulatedVexa(amount);
        markDirty(playerId);
    }

    public void addElevationAccumulatedVexa(UUID playerId, BigNumber amount) {
        if (amount.lte(BigNumber.ZERO)) {
            return;
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addElevationAccumulatedVexa(amount);
        markDirty(playerId);
    }

    public void addTotalVexaEarned(UUID playerId, BigNumber amount) {
        if (amount.lte(BigNumber.ZERO)) {
            return;
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addTotalVexaEarned(amount);
        progress.addSummitAccumulatedVexa(amount);
        progress.addElevationAccumulatedVexa(amount);
        markDirty(playerId);
    }

    public BigNumber getElevationAccumulatedVexa(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getElevationAccumulatedVexa() : BigNumber.ZERO;
    }

    // ========================================
    // Ascension System Methods
    // ========================================

    public int getAscensionCount(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getAscensionCount() : 0;
    }

    public int getSkillTreePoints(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getSkillTreePoints() : 0;
    }

    public int addSkillTreePoints(UUID playerId, int amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        int newPoints = progress.addSkillTreePoints(amount);
        markDirty(playerId);
        return newPoints;
    }

    public int getAvailableSkillPoints(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getAvailableSkillPoints() : 0;
    }

    public boolean hasSkillNode(UUID playerId, SkillTreeNode node) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.hasSkillNode(node);
    }

    public boolean unlockSkillNode(UUID playerId, SkillTreeNode node) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        if (progress.hasSkillNode(node)) {
            return false;
        }
        if (progress.getAvailableSkillPoints() < node.getCost()) {
            return false;
        }
        progress.unlockSkillNode(node);
        markDirty(playerId);
        return true;
    }

    public Set<SkillTreeNode> getUnlockedSkillNodes(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return EnumSet.noneOf(SkillTreeNode.class);
        }
        return progress.getUnlockedSkillNodes();
    }

    // ========================================
    // Achievement System Methods
    // ========================================

    public boolean hasAchievement(UUID playerId, AchievementType achievement) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.hasAchievement(achievement);
    }

    public boolean unlockAchievement(UUID playerId, AchievementType achievement) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        if (progress.hasAchievement(achievement)) {
            return false;
        }
        progress.unlockAchievement(achievement);
        markDirty(playerId);
        return true;
    }

    public Set<AchievementType> getUnlockedAchievements(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return EnumSet.noneOf(AchievementType.class);
        }
        return progress.getUnlockedAchievements();
    }

    public int getTotalManualRuns(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getTotalManualRuns() : 0;
    }

    public int incrementTotalManualRuns(UUID playerId) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        int count = progress.incrementTotalManualRuns();
        markDirty(playerId);
        return count;
    }

    public int getConsecutiveManualRuns(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getConsecutiveManualRuns() : 0;
    }

    public int incrementConsecutiveManualRuns(UUID playerId) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        int count = progress.incrementConsecutiveManualRuns();
        markDirty(playerId);
        return count;
    }

    public void resetConsecutiveManualRuns(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress != null) {
            progress.resetConsecutiveManualRuns();
            markDirty(playerId);
        }
    }

    /**
     * Shared reset logic for prestige operations.
     * Resets vexa, map unlocks (except first), multipliers, manual completion, and runners.
     * @param clearBestTimes whether to also clear best times (elevation does, summit doesn't)
     * @return list of map IDs that had runners (for despawn handling)
     */
    private List<String> resetMapProgress(AscendPlayerProgress progress, String firstMapId, boolean clearBestTimes, UUID playerId) {
        List<String> mapsWithRunners = new java.util.ArrayList<>();

        progress.setVexa(BigNumber.ZERO);
        progress.setSummitAccumulatedVexa(BigNumber.ZERO);
        progress.setElevationAccumulatedVexa(BigNumber.ZERO);

        for (Map.Entry<String, AscendPlayerProgress.MapProgress> entry : progress.getMapProgress().entrySet()) {
            String mapId = entry.getKey();
            AscendPlayerProgress.MapProgress mapProgress = entry.getValue();

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
            AscendPlayerProgress.MapProgress firstMapProgress = progress.getOrCreateMapProgress(firstMapId);
            firstMapProgress.setUnlocked(true);
        }

        return mapsWithRunners;
    }

    /**
     * Resets player progress for elevation: clears vexa, map unlocks (except first map),
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
        return mapsWithRunners;
    }

    /**
     * Reset progress for Summit: vexa, elevation, multipliers, runners, and map unlocks.
     * Keeps best times and summit XP.
     * @return list of map IDs that had runners (for despawn handling)
     */
    public List<String> resetProgressForSummit(UUID playerId, String firstMapId) {
        AscendPlayerProgress progress = getPlayer(playerId);
        if (progress == null) {
            return List.of();
        }

        // Elevation Remnant: retain 5% of elevation level
        int currentElevation = progress.getElevationMultiplier();
        if (progress.hasSkillNode(AscendConstants.SkillTreeNode.ELEVATION_REMNANT)) {
            int retained = (int) Math.floor(currentElevation * AscendConstants.ELEVATION_REMNANT_FRACTION);
            progress.setElevationMultiplier(Math.max(1, retained));
        } else {
            progress.setElevationMultiplier(1);
        }

        List<String> mapsWithRunners = resetMapProgress(progress, firstMapId, false, playerId);

        markDirty(playerId);
        return mapsWithRunners;
    }

    /**
     * Reset progress for a challenge: same as summit reset.
     * Resets vexa, elevation, multipliers, runners, and map unlocks.
     * Keeps best times.
     * @return list of map IDs that had runners (for despawn handling)
     */
    public List<String> resetProgressForChallenge(UUID playerId, String firstMapId) {
        AscendPlayerProgress progress = getPlayer(playerId);
        if (progress == null) {
            return List.of();
        }

        progress.setElevationMultiplier(1);
        progress.clearSummitXp();

        List<String> mapsWithRunners = resetMapProgress(progress, firstMapId, false, playerId);
        markDirty(playerId);
        return mapsWithRunners;
    }

    public boolean isSessionFirstRunClaimed(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.isSessionFirstRunClaimed();
    }

    public void setSessionFirstRunClaimed(UUID playerId, boolean claimed) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setSessionFirstRunClaimed(claimed);
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
            digits[index] = getMapMultiplier(playerId, map.getId());
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
            product = product.multiply(value.max(BigNumber.ONE));
            index++;
        }
        BigNumber elevation = BigNumber.fromDouble(getCalculatedElevationMultiplier(playerId));
        return product.multiply(elevation);
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
            if (map.getId().equals(mapId)) {
                value = value.add(bonusAmount);
            }
            product = product.multiply(value.max(BigNumber.ONE));
            index++;
        }
        BigNumber elevation = BigNumber.fromDouble(getCalculatedElevationMultiplier(playerId));
        return product.multiply(elevation);
    }

    // ========================================
    // Tutorial Tracking
    // ========================================

    public boolean hasSeenTutorial(UUID playerId, int bit) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.hasSeenTutorial(bit);
    }

    public void markTutorialSeen(UUID playerId, int bit) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.markTutorialSeen(bit);
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
        if (DatabaseManager.getInstance().isInitialized()) {
            String[] tables = {
                "ascend_player_maps",
                "ascend_player_summit",
                "ascend_player_skills",
                "ascend_player_achievements",
                "ascend_ghost_recordings",
                "ascend_challenges",
                "ascend_challenge_records",
                "ascend_players"
            };

            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                if (conn == null) {
                    LOGGER.atWarning().log("Failed to acquire database connection");
                    return;
                }
                conn.setAutoCommit(false);
                try {
                    for (String table : tables) {
                        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + table)) {
                            DatabaseManager.applyQueryTimeout(stmt);
                            stmt.executeUpdate();
                        }
                    }
                    conn.commit();
                    LOGGER.atInfo().log("All player progress wiped from database (%d tables cleared)", tables.length);
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to wipe all player data: " + e.getMessage());
            }
        }

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
        return progress != null ? progress.getLastActiveTimestamp() : null;
    }

    public void setLastActiveTimestamp(UUID playerId, Long timestamp) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setLastActiveTimestamp(timestamp);
        markDirty(playerId);
    }

    public boolean hasUnclaimedPassive(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.hasUnclaimedPassive();
    }

    public void setHasUnclaimedPassive(UUID playerId, boolean hasUnclaimed) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setHasUnclaimedPassive(hasUnclaimed);
        markDirty(playerId);
    }

    public boolean isAutoUpgradeEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.isAutoUpgradeEnabled();
    }

    public void setAutoUpgradeEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setAutoUpgradeEnabled(enabled);
        markDirty(playerId);
    }

    public boolean isAutoEvolutionEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.isAutoEvolutionEnabled();
    }

    public void setAutoEvolutionEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setAutoEvolutionEnabled(enabled);
        markDirty(playerId);
    }

    public boolean isHideOtherRunners(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.isHideOtherRunners();
    }

    public void setHideOtherRunners(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setHideOtherRunners(enabled);
        markDirty(playerId);
    }

    public boolean isBreakAscensionEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.isBreakAscensionEnabled();
    }

    public void setBreakAscensionEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setBreakAscensionEnabled(enabled);
        markDirty(playerId);

        // If disabling break mode while above threshold, trigger ascension
        if (!enabled && progress.getVexa().gte(AscendConstants.ASCENSION_VEXA_THRESHOLD)) {
            triggerAscensionCinematic(playerId);
        }
    }

}
