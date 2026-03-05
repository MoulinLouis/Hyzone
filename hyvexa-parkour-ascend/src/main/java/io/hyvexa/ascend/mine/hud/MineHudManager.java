package io.hyvexa.ascend.mine.hud;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.MultiHudBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MineHudManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MAX_BLOCK_ENTRIES = 5;

    private final MinePlayerStore playerStore;
    private final MineManager mineManager;
    private final MineConfigStore configStore;
    private final ConcurrentHashMap<UUID, MineHudState> huds = new ConcurrentHashMap<>();

    public MineHudManager(MinePlayerStore playerStore, MineManager mineManager, MineConfigStore configStore) {
        this.playerStore = playerStore;
        this.mineManager = mineManager;
        this.configStore = configStore;
    }

    public void attachHud(PlayerRef playerRef, Player player) {
        if (playerRef == null || player == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        MineHudState state = huds.computeIfAbsent(playerId, id -> new MineHudState(new MineHud(playerRef)));
        MultiHudBridge.setCustomHud(player, playerRef, state.hud);
        player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass, HudComponent.Health, HudComponent.Stamina);
        state.resetCache();
        MultiHudBridge.showIfNeeded(state.hud);
        state.readyAtMs = System.currentTimeMillis() + 250L;
    }

    public void detachHud(UUID playerId) {
        huds.remove(playerId);
    }

    public void updateFull(UUID playerId) {
        MineHudState state = huds.get(playerId);
        if (state == null) {
            return;
        }
        if (System.currentTimeMillis() < state.readyAtMs) {
            return;
        }
        MinePlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return;
        }

        try {
            updateCrystals(state, progress);
            updateInventory(state, progress);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to update Mine HUD for player " + playerId);
        }
    }

    public void updateCooldowns(UUID playerId) {
        MineHudState state = huds.get(playerId);
        if (state == null) {
            return;
        }
        if (System.currentTimeMillis() < state.readyAtMs) {
            return;
        }

        try {
            updateZoneCooldown(state);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to update Mine HUD cooldowns for player " + playerId);
        }
    }

    public void removePlayer(UUID playerId) {
        huds.remove(playerId);
    }

    public boolean hasHud(UUID playerId) {
        return huds.containsKey(playerId);
    }

    private void updateCrystals(MineHudState state, MinePlayerProgress progress) {
        long crystals = progress.getCrystals();
        if (crystals == state.lastCrystals) {
            return;
        }
        state.lastCrystals = crystals;
        UICommandBuilder cb = new UICommandBuilder();
        cb.set("#CrystalLabel.Text", FormatUtils.formatLong(crystals));
        state.hud.update(false, cb);
    }

    private void updateInventory(MineHudState state, MinePlayerProgress progress) {
        int total = progress.getInventoryTotal();
        int capacity = progress.getBagCapacity();
        Map<String, Integer> inventory = progress.getInventory();

        String invKey = buildInventoryKey(total, capacity, inventory);
        if (invKey.equals(state.lastInventoryKey)) {
            return;
        }
        state.lastInventoryKey = invKey;

        UICommandBuilder cb = new UICommandBuilder();
        cb.set("#BagCountLabel.Text", String.valueOf(total));
        cb.set("#BagCapLabel.Text", String.valueOf(capacity));

        // Sort entries by count descending, show top MAX_BLOCK_ENTRIES
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(inventory.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        for (int i = 0; i < MAX_BLOCK_ENTRIES; i++) {
            if (i < sorted.size()) {
                Map.Entry<String, Integer> entry = sorted.get(i);
                cb.set("#BlockEntry" + i + ".Visible", true);
                cb.set("#BlockName" + i + ".Text", formatBlockName(entry.getKey()));
                cb.set("#BlockCount" + i + ".Text", String.valueOf(entry.getValue()));
            } else {
                cb.set("#BlockEntry" + i + ".Visible", false);
            }
        }

        state.hud.update(false, cb);
    }

    private void updateZoneCooldown(MineHudState state) {
        // Find any zone currently in cooldown
        long maxRemainingMs = 0;
        for (Mine mine : configStore.listMinesSorted()) {
            for (MineZone zone : mine.getZones()) {
                if (mineManager.isZoneInCooldown(zone.getId())) {
                    long remaining = mineManager.getZoneCooldownRemainingMs(zone.getId());
                    if (remaining > maxRemainingMs) {
                        maxRemainingMs = remaining;
                    }
                }
            }
        }

        boolean showCooldown = maxRemainingMs > 0;
        int remainingSeconds = (int) Math.ceil(maxRemainingMs / 1000.0);
        String cooldownKey = showCooldown + "|" + remainingSeconds;

        if (cooldownKey.equals(state.lastCooldownKey)) {
            return;
        }
        state.lastCooldownKey = cooldownKey;

        UICommandBuilder cb = new UICommandBuilder();
        cb.set("#CooldownSection.Visible", showCooldown);
        if (showCooldown) {
            cb.set("#CooldownTimer.Text", remainingSeconds + "s");
        }
        state.hud.update(false, cb);
    }

    private static String buildInventoryKey(int total, int capacity, Map<String, Integer> inventory) {
        StringBuilder sb = new StringBuilder();
        sb.append(total).append('/').append(capacity);
        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            sb.append('|').append(entry.getKey()).append(':').append(entry.getValue());
        }
        return sb.toString();
    }

    private static String formatBlockName(String blockTypeId) {
        if (blockTypeId == null || blockTypeId.isEmpty()) {
            return "Unknown";
        }
        // Strip namespace prefix (e.g., "hytale:stone_block" -> "stone_block")
        String name = blockTypeId;
        int colonIndex = name.indexOf(':');
        if (colonIndex >= 0 && colonIndex < name.length() - 1) {
            name = name.substring(colonIndex + 1);
        }
        // Replace underscores with spaces and capitalize each word
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    sb.append(parts[i].substring(1));
                }
            }
        }
        return sb.toString();
    }

    // --- Inner HUD class ---

    static class MineHud extends CustomUIHud {

        MineHud(PlayerRef playerRef) {
            super(playerRef);
        }

        @Override
        protected void build(UICommandBuilder commandBuilder) {
            commandBuilder.append("Pages/Ascend_MineHud.ui");
        }
    }

    // --- Inner state class ---

    private static final class MineHudState {
        final MineHud hud;
        long readyAtMs;
        long lastCrystals = -1;
        String lastInventoryKey;
        String lastCooldownKey;

        MineHudState(MineHud hud) {
            this.hud = hud;
        }

        void resetCache() {
            lastCrystals = -1;
            lastInventoryKey = null;
            lastCooldownKey = null;
        }
    }
}
