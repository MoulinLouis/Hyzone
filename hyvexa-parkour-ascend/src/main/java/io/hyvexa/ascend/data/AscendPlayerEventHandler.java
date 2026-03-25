package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.tutorial.TutorialTriggerService;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.core.analytics.PlayerAnalytics;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Handles gameplay side-effects triggered by player data changes.
 *
 * <p>Extracted from {@link AscendPlayerStore} to separate pure data operations
 * from gameplay orchestration (ascension flow, tutorial triggers, transcendence
 * notifications, analytics logging).</p>
 */
public class AscendPlayerEventHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AscendPlayerStore playerStore;
    private final ChallengeManager challengeManager;
    private final TutorialTriggerService tutorialTriggerService;
    private final AscensionManager ascensionManager;
    private final AscendHudManager hudManager;
    private final RobotManager robotManager;
    private final AchievementManager achievementManager;
    private final AscendMapStore runtimeMapStore;
    private final GhostStore ghostStore;
    private final Function<UUID, PlayerRef> playerRefLookup;
    private volatile PlayerAnalytics analytics;

    private final Set<UUID> ascensionCinematicActive = ConcurrentHashMap.newKeySet();

    public AscendPlayerEventHandler(AscendPlayerStore playerStore,
                                    ChallengeManager challengeManager,
                                    TutorialTriggerService tutorialTriggerService,
                                    AscensionManager ascensionManager,
                                    AscendHudManager hudManager,
                                    RobotManager robotManager,
                                    AchievementManager achievementManager,
                                    AscendMapStore runtimeMapStore,
                                    GhostStore ghostStore,
                                    Function<UUID, PlayerRef> playerRefLookup) {
        this.playerStore = playerStore;
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

    public void setAnalytics(PlayerAnalytics analytics) {
        this.analytics = analytics;
    }

    // ========================================
    // Public composite operations (data + side-effects)
    // ========================================

    /**
     * Add volt to a player with side-effects (tutorial thresholds, ascension triggers).
     */
    public void addVoltWithEffects(UUID playerId, BigNumber amount) {
        BigNumber[] result = playerStore.atomicAddVolt(playerId, amount);
        checkVoltTutorialThresholds(playerId, result[0], result[1]);
    }

    /**
     * Set break ascension with side-effects (trigger ascension if disabling while above threshold).
     */
    public void setBreakAscensionWithEffects(UUID playerId, boolean enabled) {
        playerStore.setBreakAscensionEnabled(playerId, enabled);

        // If disabling break mode while above threshold, trigger ascension
        if (!enabled) {
            AscendPlayerProgress progress = playerStore.getPlayer(playerId);
            if (progress != null && progress.economy().getVolt().gte(AscendConstants.ASCENSION_VOLT_THRESHOLD)) {
                if (ascensionManager != null
                        && ascensionManager.hasAutoAscend(playerId)
                        && playerStore.isAutoAscendEnabled(playerId)) {
                    performInstantAscension(playerId);
                } else {
                    triggerAscensionCinematic(playerId);
                }
            }
        }
    }

    /**
     * Log elevation analytics event.
     */
    public void logElevationAnalytics(UUID playerId, int newLevel) {
        if (analytics != null) {
            try {
                analytics.logEvent(playerId, "ascend_elevation_up",
                        "{\"new_level\":" + newLevel + "}");
            } catch (Exception e) { /* silent */ }
        }
    }

    /**
     * Remove player from ascension cinematic tracking.
     */
    public void cleanupPlayer(UUID playerId) {
        if (playerId != null) {
            ascensionCinematicActive.remove(playerId);
        }
    }

    /**
     * Called by AscendAscensionExplainerPage when the player clicks Continue (or dismisses).
     * Proceeds with the cinematic and auto-ascension.
     */
    public void proceedWithAscensionCinematic(UUID playerId) {
        // The player is already in ascensionCinematicActive from showAscensionExplainer
        triggerAscensionCinematic(playerId);
    }

    // ========================================
    // Moved side-effect methods (from AscendPlayerStore)
    // ========================================

    private void checkVoltTutorialThresholds(UUID playerId, BigNumber oldBalance, BigNumber newBalance) {
        boolean crossedAscension = oldBalance.lt(AscendConstants.ASCENSION_VOLT_THRESHOLD)
                && newBalance.gte(AscendConstants.ASCENSION_VOLT_THRESHOLD);

        // Mark the ascension tutorial as seen BEFORE the tutorial check,
        // so the tutorial popup is suppressed in favor of the cinematic
        if (crossedAscension) {
            playerStore.markTutorialSeen(playerId, TutorialTriggerService.ASCENSION);
        }

        if (tutorialTriggerService != null) {
            tutorialTriggerService.checkVoltThresholds(playerId, oldBalance, newBalance);
        }

        AscendPlayerProgress progress = playerStore.getPlayer(playerId);

        // Trigger ascension every time the threshold is crossed
        if (crossedAscension) {
            if (progress != null && progress.automation().isBreakAscensionEnabled() && progress.gameplay().hasAllChallengeRewards()) {
                // Check for transcendence eligibility notification
                checkTranscendenceNotification(playerId, oldBalance, newBalance, progress);
                return; // Break mode active — suppress auto-ascension
            }
            // Auto Ascend skill + toggle: skip popup and cinematic, ascend immediately
            if (ascensionManager != null && ascensionManager.hasAutoAscend(playerId)
                    && playerStore.isAutoAscendEnabled(playerId)) {
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
                                      Consumer<ResolvedPlayerContext> onResolved) {
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

        if (store.getExternalData() == null) {
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
                    playerStore.setMapUnlocked(playerId, firstMapId, true);
                    // Auto-buy runner if player has completed map 1 before (ghost or best time)
                    boolean hasGhost = ghostStore != null && ghostStore.getRecording(playerId, firstMapId) != null;
                    boolean hasBestTime = playerStore.getBestTimeMs(playerId, firstMapId) != null;
                    if (hasGhost || hasBestTime) {
                        playerStore.setHasRobot(playerId, firstMapId, true);
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
                    if (store.getExternalData() == null) return;
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
}
