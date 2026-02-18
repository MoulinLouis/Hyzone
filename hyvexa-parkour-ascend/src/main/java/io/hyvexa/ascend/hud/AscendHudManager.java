package io.hyvexa.ascend.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.util.MultiHudBridge;
import io.hyvexa.core.economy.GemStore;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AscendHudManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AscendPlayerStore playerStore;
    private final AscendMapStore mapStore;
    private final AscendRunTracker runTracker;
    private final SummitManager summitManager;
    private final ConcurrentHashMap<UUID, AscendHud> ascendHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> hudAttached = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> ascendHudReadyAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, HiddenAscendHud> hiddenHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> hudHidden = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> previewPlayers = ConcurrentHashMap.newKeySet();

    public AscendHudManager(AscendPlayerStore playerStore, AscendMapStore mapStore, AscendRunTracker runTracker, SummitManager summitManager) {
        this.playerStore = playerStore;
        this.mapStore = mapStore;
        this.runTracker = runTracker;
        this.summitManager = summitManager;
    }

    public void updateFull(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (previewPlayers.contains(playerId)) {
            return;
        }
        if (isHudHidden(playerId)) {
            attachHiddenHud(playerRef, player);
            return;
        }
        AscendHud hud = ascendHuds.get(playerId);
        boolean needsAttach = !Boolean.TRUE.equals(hudAttached.get(playerId));
        if (needsAttach || hud == null) {
            attach(playerRef, player);
            return;
        }
        // Always ensure HUD is set on player (in case they came from another world)
        MultiHudBridge.setCustomHud(player, playerRef, hud);
        long readyAt = ascendHudReadyAt.getOrDefault(playerId, Long.MAX_VALUE);
        if (System.currentTimeMillis() < readyAt) {
            return;
        }
        hud.applyStaticText();
        hud.updatePlayerCount();
        if (playerStore == null) {
            LOGGER.atFine().log("Skipping HUD update for %s: playerStore is null", playerId);
            return;
        }
        try {
            BigNumber vexa = playerStore.getVexa(playerId);
            List<AscendMap> mapList = mapStore != null ? mapStore.listMapsSorted() : List.of();
            BigNumber product = playerStore.getMultiplierProduct(playerId, mapList, AscendConstants.MULTIPLIER_SLOTS);
            BigNumber[] digits = playerStore.getMultiplierDisplayValues(playerId, mapList, AscendConstants.MULTIPLIER_SLOTS);
            int elevationLevel = playerStore.getElevationLevel(playerId);
            BigNumber accumulatedVexa = playerStore.getElevationAccumulatedVexa(playerId);
            AscendConstants.ElevationPurchaseResult purchase = AscendConstants.calculateElevationPurchase(elevationLevel, accumulatedVexa);
            int potentialElevation = elevationLevel + purchase.levels;
            boolean showElevation = elevationLevel > 1 || purchase.levels > 0;
            hud.updateEconomy(vexa, product, digits, elevationLevel, potentialElevation, showElevation);

            // Update prestige HUD
            var summitLevels = playerStore.getSummitLevels(playerId);
            int ascensionCount = playerStore.getAscensionCount(playerId);
            int skillPoints = playerStore.getAvailableSkillPoints(playerId);
            SummitManager.SummitPreview multPreview = summitManager != null ? summitManager.previewSummit(playerId, AscendConstants.SummitCategory.MULTIPLIER_GAIN) : null;
            SummitManager.SummitPreview speedPreview = summitManager != null ? summitManager.previewSummit(playerId, AscendConstants.SummitCategory.RUNNER_SPEED) : null;
            SummitManager.SummitPreview evoPreview = summitManager != null ? summitManager.previewSummit(playerId, AscendConstants.SummitCategory.EVOLUTION_POWER) : null;
            hud.updatePrestige(summitLevels, ascensionCount, skillPoints, multPreview, speedPreview, evoPreview);

            // Update ascension HUD card
            hud.updateAscension(ascensionCount, skillPoints);

            // Update ascension quest progress bar
            hud.updateAscensionQuest(vexa);

            // Update gems display
            hud.updateGems(GemStore.getInstance().getGems(playerId));

            // Runner bars are updated at higher frequency by updateRunnerBar()
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to update Ascend HUD for player " + playerId);
        }
    }

    public void updateTimer(PlayerRef playerRef) {
        if (playerRef == null || runTracker == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (previewPlayers.contains(playerId) || isHudHidden(playerId)
                || !Boolean.TRUE.equals(hudAttached.get(playerId))) {
            return;
        }
        AscendHud hud = ascendHuds.get(playerId);
        if (hud == null) {
            return;
        }
        long readyAt = ascendHudReadyAt.getOrDefault(playerId, Long.MAX_VALUE);
        if (System.currentTimeMillis() < readyAt) {
            return;
        }
        try {
            boolean isRunning = runTracker.isRunActive(playerId);
            boolean isPending = runTracker.isPendingRun(playerId);
            boolean showTimer = isRunning || isPending;
            Long elapsedMs = isRunning ? runTracker.getElapsedTimeMs(playerId) : 0L;
            hud.updateTimer(elapsedMs, showTimer);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to update timer for player " + playerId);
        }
    }

    public void updateRunnerBars(PlayerRef playerRef) {
        if (playerRef == null || playerStore == null || mapStore == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (previewPlayers.contains(playerId) || isHudHidden(playerId)
                || !Boolean.TRUE.equals(hudAttached.get(playerId))) {
            return;
        }
        AscendHud hud = ascendHuds.get(playerId);
        if (hud == null) {
            return;
        }
        long readyAt = ascendHudReadyAt.getOrDefault(playerId, Long.MAX_VALUE);
        if (System.currentTimeMillis() < readyAt) {
            return;
        }
        try {
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            RobotManager robotManager = plugin != null ? plugin.getRobotManager() : null;
            if (robotManager == null) {
                return;
            }
            List<AscendMap> maps = mapStore.listMapsSorted();
            float[] bars = new float[AscendConstants.MULTIPLIER_SLOTS];
            for (int i = 0; i < maps.size() && i < bars.length; i++) {
                double progress = robotManager.getRunnerProgress(playerId, maps.get(i).getId());
                if (progress >= 0) {
                    bars[i] = (float) progress;
                }
            }
            hud.updateRunnerBars(bars);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to update runner bars for player " + playerId);
        }
    }

    public void attach(PlayerRef playerRef, Player player) {
        if (playerRef == null || player == null) {
            return;
        }
        if (isHudHidden(playerRef.getUuid())) {
            attachHiddenHud(playerRef, player);
            return;
        }
        AscendHud hud = ascendHuds.computeIfAbsent(playerRef.getUuid(), id -> new AscendHud(playerRef));
        MultiHudBridge.setCustomHud(player, playerRef, hud);
        player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass, HudComponent.Health, HudComponent.Stamina);
        hud.resetCache();
        MultiHudBridge.showIfNeeded(hud);
        hud.applyStaticText();
        hudAttached.put(playerRef.getUuid(), true);
        ascendHudReadyAt.put(playerRef.getUuid(), System.currentTimeMillis() + 250L);
    }

    public void hideHud(UUID playerId) {
        hudHidden.put(playerId, true);
        hudAttached.remove(playerId);
    }

    public void showHud(UUID playerId) {
        hudHidden.remove(playerId);
        hudAttached.remove(playerId);
    }

    public boolean isHudHidden(UUID playerId) {
        return playerId != null && Boolean.TRUE.equals(hudHidden.get(playerId));
    }

    private void attachHiddenHud(PlayerRef playerRef, Player player) {
        if (playerRef == null || player == null) {
            return;
        }
        HiddenAscendHud hud = hiddenHuds.computeIfAbsent(playerRef.getUuid(), id -> new HiddenAscendHud(playerRef));
        MultiHudBridge.setCustomHud(player, playerRef, hud);
        player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass, HudComponent.Health, HudComponent.Stamina);
    }

    public void setPreviewMode(UUID playerId, boolean enabled) {
        if (enabled) {
            previewPlayers.add(playerId);
        } else {
            previewPlayers.remove(playerId);
        }
    }

    public void removePlayer(UUID playerId) {
        ascendHuds.remove(playerId);
        hudAttached.remove(playerId);
        ascendHudReadyAt.remove(playerId);
        hiddenHuds.remove(playerId);
        hudHidden.remove(playerId);
        previewPlayers.remove(playerId);
    }

    public void showToast(UUID playerId, ToastType type, String message) {
        AscendHud hud = ascendHuds.get(playerId);
        if (hud == null) {
            return;
        }
        hud.showToast(type, message);
    }

    public void updateToasts(UUID playerId) {
        if (previewPlayers.contains(playerId) || isHudHidden(playerId)
                || !Boolean.TRUE.equals(hudAttached.get(playerId))) {
            return;
        }
        AscendHud hud = ascendHuds.get(playerId);
        if (hud == null) {
            return;
        }
        long readyAt = ascendHudReadyAt.getOrDefault(playerId, Long.MAX_VALUE);
        if (System.currentTimeMillis() < readyAt) {
            return;
        }
        hud.updateToasts();
    }

    public AscendHud getHud(UUID playerId) {
        return ascendHuds.get(playerId);
    }
}
