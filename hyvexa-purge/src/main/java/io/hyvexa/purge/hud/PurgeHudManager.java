package io.hyvexa.purge.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.MultiHudBridge;
import io.hyvexa.core.economy.VexaStore;
import io.hyvexa.purge.data.PurgeScrapStore;
import io.hyvexa.purge.data.PurgeUpgradeState;
import io.hyvexa.purge.data.PurgeUpgradeType;
import io.hyvexa.purge.data.WeaponXpStore;
import io.hyvexa.purge.manager.WeaponXpManager;
import io.hyvexa.purge.mission.DailyMissionRotation;
import io.hyvexa.purge.manager.PurgeManagerRegistry;
import io.hyvexa.purge.mission.PurgeMissionManager;

import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeSessionPlayerState;
import io.hyvexa.purge.util.PurgePlayerNameResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeHudManager {

    private static final long HUD_READY_DELAY_MS = 1500L;
    private static final long STREAK_WINDOW_MS = 3000L;
    private final ConcurrentHashMap<UUID, PurgeHud> purgeHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> hudReadyAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PurgeSessionPlayerState> comboPlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PurgeSession> killMeterPlayers = new ConcurrentHashMap<>();
    private volatile long lastKillMeterTickMs;
    private PurgeManagerRegistry registry;

    public void initRegistry(PurgeManagerRegistry registry) {
        this.registry = registry;
    }

    public void attach(PlayerRef playerRef, Player player) {
        if (playerRef == null || player == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        PurgeHud hud = purgeHuds.computeIfAbsent(playerId, id -> new PurgeHud(playerRef));
        hud.resetCache();
        hudReadyAt.put(playerId, System.currentTimeMillis() + HUD_READY_DELAY_MS);
        MultiHudBridge.setCustomHud(player, playerRef, hud);
        player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass);
        player.getHudManager().showHudComponents(playerRef, HudComponent.Health, HudComponent.Stamina);
        MultiHudBridge.showIfNeeded(hud);
    }

    public PurgeHud getHud(UUID playerId) {
        return playerId != null ? purgeHuds.get(playerId) : null;
    }

    public Ref<EntityStore> getPlayerRef(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef != null) {
            return playerRef.getReference();
        }
        return null;
    }

    public void removePlayer(UUID playerId) {
        if (playerId != null) {
            purgeHuds.remove(playerId);
            hudReadyAt.remove(playerId);
            comboPlayers.remove(playerId);
            killMeterPlayers.remove(playerId);
        }
    }

    public void registerComboPlayer(UUID playerId, PurgeSessionPlayerState state) {
        if (playerId != null && state != null) {
            comboPlayers.put(playerId, state);
        }
    }

    public void unregisterComboPlayer(UUID playerId) {
        if (playerId != null) {
            comboPlayers.remove(playerId);
            PurgeHud hud = getHud(playerId);
            if (hud != null) {
                hud.updateCombo(0, 0f);
            }
        }
    }

    public void registerKillMeter(UUID playerId, PurgeSession session) {
        if (playerId != null && session != null) {
            killMeterPlayers.put(playerId, session);
        }
    }

    public void unregisterKillMeter(UUID playerId) {
        if (playerId != null) {
            killMeterPlayers.remove(playerId);
            PurgeHud hud = getHud(playerId);
            if (hud != null) {
                hud.hideKillMeter();
            }
        }
    }

    public void updateUpgradeLevels(UUID playerId, PurgeUpgradeState state) {
        PurgeHud hud = getHud(playerId);
        if (hud == null || state == null) return;
        hud.updateUpgradeLevels(
                state.getLevel(PurgeUpgradeType.HP),
                state.getLevel(PurgeUpgradeType.AMMO),
                state.getLevel(PurgeUpgradeType.SPEED),
                state.getLevel(PurgeUpgradeType.LUCK)
        );
    }

    public void showRunHud(UUID playerId) {
        PurgeHud hud = getHud(playerId);
        if (hud != null) {
            hud.setMissionPanelVisible(false);
            hud.setWaveStatusVisible(true);
            hud.setPlayerHealthVisible(true);
        }
    }

    public void hideRunHud(UUID playerId) {
        PurgeHud hud = getHud(playerId);
        if (hud != null) {
            hud.updateUpgradeLevels(0, 0, 0, 0);
            hud.setPlayerHealthVisible(false);
            hud.setWaveStatusVisible(false);
            hud.hideWeaponXp();
            hud.hideMeleeXp();
            hud.setMissionPanelVisible(true);
            hud.resetCache();
        }
    }

    public void updateWeaponXpHud(UUID playerId, String weaponId, String displayName) {
        updateXpHud(playerId, weaponId, displayName, PurgeHud::updateWeaponXp);
    }

    public void updateMeleeXpHud(UUID playerId, String weaponId, String displayName) {
        updateXpHud(playerId, weaponId, displayName, PurgeHud::updateMeleeXp);
    }

    private void updateXpHud(UUID playerId, String weaponId, String displayName, XpHudUpdater updater) {
        PurgeHud hud = getHud(playerId);
        if (hud == null || weaponId == null) return;
        int[] xpData = WeaponXpStore.getInstance().getXpData(playerId, weaponId);
        int xp = xpData[0];
        int level = xpData[1];

        String nameText;
        String xpText;
        float barProgress;
        if (level >= WeaponXpManager.MAX_LEVEL) {
            nameText = displayName + " Lv MAX";
            xpText = "MAX";
            barProgress = 1.0f;
        } else {
            nameText = displayName + " Lv " + level;
            int cumCurrent = WeaponXpManager.cumulativeXp(level);
            int cumNext = WeaponXpManager.cumulativeXp(level + 1);
            int xpInLevel = xp - cumCurrent;
            int xpNeeded = cumNext - cumCurrent;
            barProgress = xpNeeded > 0 ? (float) xpInLevel / xpNeeded : 0f;
            xpText = xpInLevel + "/" + xpNeeded;
        }
        updater.update(hud, nameText, xpText, barProgress);
    }

    @FunctionalInterface
    private interface XpHudUpdater {
        void update(PurgeHud hud, String nameText, String xpText, float barProgress);
    }

    public void updateWaveStatus(UUID playerId, int wave, int alive, int total) {
        PurgeHud hud = getHud(playerId);
        if (hud != null) {
            hud.updateWaveStatus(wave, alive, total);
        }
    }

    public void updatePlayerHealth(UUID playerId, int current, int max) {
        PurgeHud hud = getHud(playerId);
        if (hud != null) {
            hud.updatePlayerHealth(current, max);
        }
    }

    public void updateIntermission(UUID playerId, int seconds) {
        PurgeHud hud = getHud(playerId);
        if (hud != null) {
            hud.updateIntermission(seconds);
        }
    }

    public void tickComboBars() {
        tickKillMeters();
        long now = System.currentTimeMillis();
        for (var entry : comboPlayers.entrySet()) {
            UUID playerId = entry.getKey();
            PurgeHud hud = getHud(playerId);
            if (hud == null) continue;
            PurgeSessionPlayerState state = entry.getValue();
            long lastKill = state.getLastKillTimeMs();
            int streak = state.getKillStreak();
            if (streak < 2 || lastKill == 0) {
                hud.updateCombo(0, 0f);
                continue;
            }
            long elapsed = now - lastKill;
            if (elapsed >= STREAK_WINDOW_MS) {
                hud.updateCombo(0, 0f);
                continue;
            }
            float progress = 1f - (float) elapsed / STREAK_WINDOW_MS;
            hud.updateCombo(streak, progress);
        }
    }

    private static final long KILL_METER_INTERVAL_MS = 500L;

    public void tickKillMeters() {
        long now = System.currentTimeMillis();
        if (now - lastKillMeterTickMs < KILL_METER_INTERVAL_MS) {
            return;
        }
        lastKillMeterTickMs = now;
        for (var entry : killMeterPlayers.entrySet()) {
            UUID playerId = entry.getKey();
            PurgeHud hud = getHud(playerId);
            if (hud == null) continue;
            PurgeSession session = entry.getValue();

            // Collect all participants' soloKills
            List<UUID> participants = new ArrayList<>(session.getParticipants());
            participants.sort((a, b) -> {
                PurgeSessionPlayerState sa = session.getPlayerState(a);
                PurgeSessionPlayerState sb = session.getPlayerState(b);
                int ka = sa != null ? sa.getSoloKills() : 0;
                int kb = sb != null ? sb.getSoloKills() : 0;
                return Integer.compare(kb, ka); // desc
            });

            int count = Math.min(participants.size(), 5);
            String[] names = new String[count];
            int[] kills = new int[count];
            for (int i = 0; i < count; i++) {
                UUID pid = participants.get(i);
                PurgeSessionPlayerState ps = session.getPlayerState(pid);
                names[i] = PurgePlayerNameResolver.resolve(pid, PurgePlayerNameResolver.FallbackStyle.SHORT_UUID);
                kills[i] = ps != null ? ps.getSoloKills() : 0;
            }
            hud.updateKillMeter(names, kills, count);
        }
    }

    public void tickSlowUpdates() {
        long now = System.currentTimeMillis();
        int playerCount = Universe.get().getPlayers().size();
        List<UUID> toRemove = new ArrayList<>();
        for (var entry : purgeHuds.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null || !playerRef.isValid()) {
                toRemove.add(playerId);
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                toRemove.add(playerId);
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
            if (!ModeGate.isPurgeWorld(world)) {
                toRemove.add(playerId);
                continue;
            }
            long readyAt = hudReadyAt.getOrDefault(playerId, Long.MAX_VALUE);
            if (now < readyAt) {
                continue;
            }
            PurgeHud hud = entry.getValue();
            hud.updatePlayerCount(playerCount);
            hud.updateVexa(VexaStore.getInstance().getCachedVexa(playerId));
            hud.updateScrap(PurgeScrapStore.getInstance().getScrap(playerId));
            // Update mission panel for idle players (not in a session)
            if (!comboPlayers.containsKey(playerId)) {
                updateMissionHud(hud, registry.getMissionManager(), playerId);
            }
        }
        for (UUID playerId : toRemove) {
            purgeHuds.remove(playerId);
            hudReadyAt.remove(playerId);
            comboPlayers.remove(playerId);
            killMeterPlayers.remove(playerId);
        }
    }

    private void updateMissionHud(PurgeHud hud, PurgeMissionManager mm, UUID playerId) {
        PurgeMissionManager.MissionStatus[] statuses = mm.getMissionStatus(playerId);
        String[] descs = new String[3];
        String[] progress = new String[3];
        String[] rewards = new String[3];
        for (int i = 0; i < 3; i++) {
            PurgeMissionManager.MissionStatus s = statuses[i];
            descs[i] = s.mission().description();
            if (s.claimed()) {
                progress[i] = "DONE";
                rewards[i] = "";
            } else {
                int current = Math.min(s.currentProgress(), s.mission().target());
                progress[i] = current + " / " + s.mission().target();
                rewards[i] = "+" + s.mission().scrapReward() + "s";
            }
        }
        long seconds = DailyMissionRotation.getSecondsUntilReset();
        String timer = DailyMissionRotation.formatTimeRemaining(seconds);
        hud.updateMissions(descs, progress, rewards, timer);
    }
}
