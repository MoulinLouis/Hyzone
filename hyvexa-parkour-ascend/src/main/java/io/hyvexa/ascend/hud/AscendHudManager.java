package io.hyvexa.ascend.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ElevationConstants;
import io.hyvexa.ascend.RunnerEconomyConstants;
import io.hyvexa.ascend.SummitConstants;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import io.hyvexa.common.hud.AbstractHudManager;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.util.MultiHudBridge;
import io.hyvexa.core.economy.CurrencyStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AscendHudManager extends AbstractHudManager<AscendHud> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long ECONOMY_CACHE_TTL_MS = 1000L;
    private static final long RUNNER_BAR_CACHE_TTL_MS = 150L;
    private static volatile AscendHudManager toastHudManager;

    private final AscendPlayerStore playerStore;
    private final AscendMapStore mapStore;
    private final AscendRunTracker runTracker;
    private final SummitManager summitManager;
    private final CurrencyStore vexaStore;
    private final ConcurrentHashMap<UUID, Boolean> hudAttached = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, HiddenAscendHud> hiddenHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> hudHidden = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> previewPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, CachedEconomyData> economyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, RunnerBarCache> runnerBarCache = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> mineModeActive = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> mineModePendingApply = ConcurrentHashMap.newKeySet();
    private volatile RobotManager robotManager;

    public AscendHudManager(AscendPlayerStore playerStore, AscendMapStore mapStore, AscendRunTracker runTracker, SummitManager summitManager, CurrencyStore vexaStore) {
        super(250L);
        this.playerStore = playerStore;
        this.mapStore = mapStore;
        this.runTracker = runTracker;
        this.summitManager = summitManager;
        this.vexaStore = vexaStore;
        toastHudManager = this;
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
        AscendHud hud = getHud(playerId);
        boolean needsAttach = !Boolean.TRUE.equals(hudAttached.get(playerId));
        if (needsAttach || hud == null) {
            attach(playerRef, player);
            return;
        }
        // Always ensure HUD is set on player (in case they came from another world)
        MultiHudBridge.setCustomHud(player, playerRef, hud);
        if (!isReady(playerId)) {
            return;
        }
        hud.applyStaticText();
        hud.updatePlayerCount();
        // Apply deferred mine mode visibility if it was set before the HUD was ready
        if (mineModePendingApply.remove(playerId)) {
            applyMineModeVisibility(hud, true);
        }
        // In mine mode, only update the info panel — skip economy/elevation/prestige
        if (mineModeActive.contains(playerId)) {
            try {
                CachedEconomyData cached = getOrRefreshEconomyCache(playerId);
                hud.updateVexa(cached.vexa);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to update Ascend HUD (mine mode) for player " + playerId);
            }
            return;
        }
        try {
            CachedEconomyData cached = getOrRefreshEconomyCache(playerId);
            hud.updateEconomy(cached.volt, cached.product, cached.digits, cached.elevationLevel, cached.potentialElevation, cached.showElevation);
            hud.updatePrestige(cached.summitLevels, cached.ascensionCount, cached.skillPoints, cached.multPreview, cached.speedPreview, cached.evoPreview);
            hud.updateAscension(cached.ascensionCount, cached.skillPoints);
            hud.updateAscensionQuest(cached.volt);
            hud.updateVexa(cached.vexa);
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
        AscendHud hud = getHud(playerId);
        if (hud == null) {
            return;
        }
        if (!isReady(playerId)) {
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
        AscendHud hud = getHud(playerId);
        if (hud == null) {
            return;
        }
        if (!isReady(playerId)) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            RunnerBarCache cachedBars = runnerBarCache.get(playerId);
            if (cachedBars != null && now - cachedBars.computedAtMs < RUNNER_BAR_CACHE_TTL_MS) {
                hud.updateRunnerBars(cachedBars.bars);
                return;
            }
            RobotManager robotManager = this.robotManager;
            if (robotManager == null) {
                return;
            }
            List<AscendMap> maps = mapStore.listMapsSorted();
            float[] bars = new float[RunnerEconomyConstants.MULTIPLIER_SLOTS];
            for (int i = 0; i < maps.size() && i < bars.length; i++) {
                double progress = robotManager.getRunnerProgress(playerId, maps.get(i).getId());
                if (progress >= 0) {
                    bars[i] = (float) progress;
                }
            }
            runnerBarCache.put(playerId, new RunnerBarCache(now, bars));
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
        UUID playerId = playerRef.getUuid();
        AscendHud hud = huds.computeIfAbsent(playerId, id -> new AscendHud(playerRef));
        MultiHudBridge.setCustomHud(player, playerRef, hud);
        player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass, HudComponent.Health, HudComponent.Stamina);
        hud.resetCache();
        MultiHudBridge.showIfNeeded(hud);
        hudAttached.put(playerId, true);
        registerHud(playerId, hud);
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

    /**
     * Loads HUD hidden state from AscendPlayerStore (DB-backed).
     * Call on player join before the first updateFull.
     */
    public void loadHudHiddenFromStore(UUID playerId) {
        if (playerId == null || playerStore == null) {
            return;
        }
        if (playerStore.settings().isHudHidden(playerId)) {
            hudHidden.put(playerId, true);
        }
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

    public void setMineMode(UUID playerId, boolean enabled) {
        AscendHud hud = getHud(playerId);
        if (hud == null) {
            return;
        }
        if (enabled) {
            mineModeActive.add(playerId);
            // If HUD not ready yet (client still loading UI), defer the visibility commands —
            // updateFull will apply them once the HUD is ready
            if (!isReady(playerId)) {
                mineModePendingApply.add(playerId);
                return;
            }
            applyMineModeVisibility(hud, true);
        } else {
            mineModeActive.remove(playerId);
            mineModePendingApply.remove(playerId);
            if (!isReady(playerId)) {
                return;
            }
            applyMineModeVisibility(hud, false);
            hud.resetCache();
        }
    }

    @Override
    public void removePlayer(UUID playerId) {
        super.removePlayer(playerId);
        hudAttached.remove(playerId);
        hiddenHuds.remove(playerId);
        hudHidden.remove(playerId);
        previewPlayers.remove(playerId);
        mineModeActive.remove(playerId);
        mineModePendingApply.remove(playerId);
        economyCache.remove(playerId);
        runnerBarCache.remove(playerId);
    }

    private void applyMineModeVisibility(AscendHud hud, boolean enterMine) {
        UICommandBuilder cb = new UICommandBuilder();
        if (enterMine) {
            cb.set("#TopBar.Visible", false);
            cb.set("#TopVoltHud.Visible", false);
            cb.set("#RunTimerHud.Visible", false);
            cb.set("#ElevationHud.Visible", false);
            cb.set("#PrestigeHud.Visible", false);
            cb.set("#AscensionHud.Visible", false);
            cb.set("#AscensionQuestHud.Visible", false);
            cb.set("#ToastContainer.Visible", false);
        } else {
            cb.set("#TopBar.Visible", true);
            cb.set("#TopVoltHud.Visible", true);
            cb.set("#ToastContainer.Visible", true);
        }
        hud.update(false, cb);
    }

    public void showScreenFade(UUID playerId, boolean visible) {
        UICommandBuilder cb = new UICommandBuilder();
        cb.set("#ScreenFade.Visible", visible);
        AscendHud hud = getHud(playerId);
        if (hud != null) {
            hud.update(false, cb);
            return;
        }
        HiddenAscendHud hidden = hiddenHuds.get(playerId);
        if (hidden != null) {
            hidden.update(false, cb);
        }
    }

    public void updateScreenFadeBar(UUID playerId, String text, float progress) {
        UICommandBuilder cb = new UICommandBuilder();
        cb.set("#FadeText.Text", text);
        cb.set("#FadeBar.Value", progress);
        AscendHud hud = getHud(playerId);
        if (hud != null) {
            hud.update(false, cb);
            return;
        }
        HiddenAscendHud hidden = hiddenHuds.get(playerId);
        if (hidden != null) {
            hidden.update(false, cb);
        }
    }

    public static void showToastSafe(UUID playerId, ToastType type, String message) {
        AscendHudManager hudManager = toastHudManager;
        if (hudManager != null) {
            hudManager.showToast(playerId, type, message);
        }
    }

    public void setRobotManager(RobotManager robotManager) {
        this.robotManager = robotManager;
    }

    public void showToast(UUID playerId, ToastType type, String message) {
        AscendHud hud = getHud(playerId);
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
        AscendHud hud = getHud(playerId);
        if (hud == null) {
            return;
        }
        if (!isReady(playerId)) {
            return;
        }
        hud.updateToasts();
    }

    /**
     * Invalidate the economy cache for a player, forcing a fresh computation on next tick.
     * Call this after actions that change multipliers, volt, summit, or elevation.
     */
    public void invalidateEconomyCache(UUID playerId) {
        economyCache.remove(playerId);
    }

    private CachedEconomyData getOrRefreshEconomyCache(UUID playerId) {
        long now = System.currentTimeMillis();
        CachedEconomyData cached = economyCache.get(playerId);
        if (cached != null && now - cached.timestamp < ECONOMY_CACHE_TTL_MS) {
            return cached;
        }

        List<AscendMap> mapList = mapStore != null ? mapStore.listMapsSorted() : List.of();

        // Single-pass multiplier computation
        AscendPlayerStore.MultiplierResult mr = playerStore.progression().getMultiplierProductAndValues(playerId, mapList, RunnerEconomyConstants.MULTIPLIER_SLOTS);

        BigNumber volt = playerStore.volt().getVolt(playerId);
        int elevationLevel = playerStore.progression().getElevationLevel(playerId);
        BigNumber accumulatedVolt = playerStore.progression().getElevationAccumulatedVolt(playerId);
        ElevationConstants.ElevationPurchaseResult purchase = ElevationConstants.calculateElevationPurchase(elevationLevel, accumulatedVolt);
        int potentialElevation = elevationLevel + purchase.levels;
        boolean showElevation = elevationLevel > 1 || purchase.levels > 0;

        Map<SummitConstants.SummitCategory, Integer> summitLevels = playerStore.progression().getSummitLevels(playerId);
        int ascensionCount = playerStore.progression().getAscensionCount(playerId);
        int skillPoints = playerStore.gameplay().getAvailableSkillPoints(playerId);
        SummitManager.SummitPreview multPreview = summitManager != null ? summitManager.previewSummit(playerId, SummitConstants.SummitCategory.MULTIPLIER_GAIN) : null;
        SummitManager.SummitPreview speedPreview = summitManager != null ? summitManager.previewSummit(playerId, SummitConstants.SummitCategory.RUNNER_SPEED) : null;
        SummitManager.SummitPreview evoPreview = summitManager != null ? summitManager.previewSummit(playerId, SummitConstants.SummitCategory.EVOLUTION_POWER) : null;

        long vexa = vexaStore.getBalance(playerId);

        cached = new CachedEconomyData(now, volt, mr.product, mr.values,
                elevationLevel, potentialElevation, showElevation,
                summitLevels, ascensionCount, skillPoints,
                multPreview, speedPreview, evoPreview, vexa);
        economyCache.put(playerId, cached);
        return cached;
    }

    private static final class CachedEconomyData {
        final long timestamp;
        final BigNumber volt;
        final BigNumber product;
        final BigNumber[] digits;
        final int elevationLevel;
        final int potentialElevation;
        final boolean showElevation;
        final Map<SummitConstants.SummitCategory, Integer> summitLevels;
        final int ascensionCount;
        final int skillPoints;
        final SummitManager.SummitPreview multPreview;
        final SummitManager.SummitPreview speedPreview;
        final SummitManager.SummitPreview evoPreview;
        final long vexa;

        CachedEconomyData(long timestamp, BigNumber volt, BigNumber product, BigNumber[] digits,
                          int elevationLevel, int potentialElevation, boolean showElevation,
                          Map<SummitConstants.SummitCategory, Integer> summitLevels,
                          int ascensionCount, int skillPoints,
                          SummitManager.SummitPreview multPreview,
                          SummitManager.SummitPreview speedPreview,
                          SummitManager.SummitPreview evoPreview,
                          long vexa) {
            this.timestamp = timestamp;
            this.volt = volt;
            this.product = product;
            this.digits = digits;
            this.elevationLevel = elevationLevel;
            this.potentialElevation = potentialElevation;
            this.showElevation = showElevation;
            this.summitLevels = summitLevels;
            this.ascensionCount = ascensionCount;
            this.skillPoints = skillPoints;
            this.multPreview = multPreview;
            this.speedPreview = speedPreview;
            this.evoPreview = evoPreview;
            this.vexa = vexa;
        }
    }

    private static final class RunnerBarCache {
        final long computedAtMs;
        final float[] bars;

        RunnerBarCache(long computedAtMs, float[] bars) {
            this.computedAtMs = computedAtMs;
            this.bars = bars;
        }
    }
}
