package io.hyvexa.ascend.mine.hud;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.mine.MineBlockDisplay;
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
        MineHudState state = huds.computeIfAbsent(playerId, id -> new MineHudState(playerId, new MineHud(playerRef)));
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
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(inventory.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        String invKey = buildInventoryKey(total, capacity, sorted);
        if (invKey.equals(state.lastInventoryKey)) {
            return;
        }
        state.lastInventoryKey = invKey;

        UICommandBuilder cb = new UICommandBuilder();
        cb.set("#BagCountLabel.Text", String.valueOf(total));
        cb.set("#BagCapLabel.Text", String.valueOf(capacity));
        cb.set("#BagCountLabel.Style.TextColor", total >= capacity ? "#ef4444" : "#f0f4f8");

        for (int i = 0; i < MAX_BLOCK_ENTRIES; i++) {
            if (i < sorted.size()) {
                Map.Entry<String, Integer> entry = sorted.get(i);
                cb.set("#BlockEntry" + i + ".Visible", true);
                cb.set("#BlockName" + i + ".Text", MineBlockDisplay.getDisplayName(entry.getKey()));
                cb.set("#BlockCount" + i + ".Text", String.valueOf(entry.getValue()));
            } else {
                cb.set("#BlockEntry" + i + ".Visible", false);
            }
        }

        state.hud.update(false, cb);
    }

    private void updateZoneCooldown(MineHudState state) {
        MineZone currentZone = findCurrentZone(state.playerId);

        // Update mine name
        String mineName = "";
        if (currentZone != null) {
            Mine mine = configStore.getMine(currentZone.getMineId());
            if (mine != null) {
                mineName = mine.getName();
            }
        }
        boolean mineNameChanged = !mineName.equals(state.lastMineName == null ? "" : state.lastMineName);
        if (mineNameChanged) {
            state.lastMineName = mineName;
            UICommandBuilder nameBuilder = new UICommandBuilder();
            boolean showName = !mineName.isEmpty();
            nameBuilder.set("#MineNameLabel.Visible", showName);
            nameBuilder.set("#MineNameSep.Visible", showName);
            if (showName) {
                nameBuilder.set("#MineNameLabel.Text", mineName);
            }
            state.hud.update(false, nameBuilder);
        }

        // Existing cooldown logic
        long remainingMs = 0;
        if (currentZone != null && mineManager.isZoneInCooldown(currentZone.getId())) {
            remainingMs = mineManager.getZoneCooldownRemainingMs(currentZone.getId());
        }

        boolean showCooldown = remainingMs > 0;
        int remainingSeconds = (int) Math.ceil(remainingMs / 1000.0);
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

    private MineZone findCurrentZone(UUID playerId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return null;
        }
        PlayerRef playerRef = plugin.getPlayerRef(playerId);
        if (playerRef == null) {
            return null;
        }
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        var store = ref.getStore();
        if (store == null) {
            return null;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            return null;
        }
        int x = (int) Math.floor(transform.getPosition().getX());
        int y = (int) Math.floor(transform.getPosition().getY());
        int z = (int) Math.floor(transform.getPosition().getZ());
        return mineManager.findZoneAt(x, y, z);
    }

    private static String buildInventoryKey(int total, int capacity, List<Map.Entry<String, Integer>> inventory) {
        StringBuilder sb = new StringBuilder();
        sb.append(total).append('/').append(capacity);
        for (Map.Entry<String, Integer> entry : inventory) {
            sb.append('|').append(entry.getKey()).append(':').append(entry.getValue());
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

    public void showBlockHealth(UUID playerId, String blockTypeId, int currentHp, int maxHp) {
        MineHudState state = huds.get(playerId);
        if (state == null) return;

        float fraction = maxHp > 0 ? (float) currentHp / maxHp : 0f;
        String displayName = MineBlockDisplay.getDisplayName(blockTypeId);

        state.blockHealthVisible = true;
        state.blockHealthLastUpdateMs = System.currentTimeMillis();

        UICommandBuilder cb = new UICommandBuilder();
        cb.set("#BlockHealthHud.Visible", true);
        cb.set("#BlockHealthName.Text", displayName);
        cb.set("#BlockHealthText.Text", currentHp + "/" + maxHp);
        cb.set("#BlockHealthBar.Value", fraction);

        // Color: green > yellow > red based on HP fraction
        String barColor;
        if (fraction > 0.6f) {
            barColor = "#22c55e";
        } else if (fraction > 0.3f) {
            barColor = "#f59e0b";
        } else {
            barColor = "#ef4444";
        }
        cb.set("#BlockHealthBar.Bar", barColor);

        state.hud.update(false, cb);
    }

    public void hideBlockHealth(UUID playerId) {
        MineHudState state = huds.get(playerId);
        if (state == null) return;
        if (!state.blockHealthVisible) return;
        state.blockHealthVisible = false;
        UICommandBuilder cb = new UICommandBuilder();
        cb.set("#BlockHealthHud.Visible", false);
        state.hud.update(false, cb);
    }

    private static final long BLOCK_HEALTH_TIMEOUT_MS = 3000;

    /**
     * Called each tick to auto-hide the block health bar after no hits for 3 seconds.
     */
    public void tickBlockHealth(UUID playerId) {
        MineHudState state = huds.get(playerId);
        if (state == null || !state.blockHealthVisible) return;
        if (System.currentTimeMillis() - state.blockHealthLastUpdateMs > BLOCK_HEALTH_TIMEOUT_MS) {
            hideBlockHealth(playerId);
        }
    }

    public void showMineToast(UUID playerId, String blockTypeId, int count) {
        MineHudState state = huds.get(playerId);
        if (state == null) {
            return;
        }
        state.toastManager.onBlockMined(blockTypeId, count);
    }

    public void updateToasts(UUID playerId) {
        MineHudState state = huds.get(playerId);
        if (state == null) {
            return;
        }
        if (System.currentTimeMillis() < state.readyAtMs) {
            return;
        }
        if (!state.toastManager.hasActiveToasts()) {
            return;
        }
        UICommandBuilder cb = new UICommandBuilder();
        state.toastManager.update(cb);
        state.hud.update(false, cb);
    }

    private static final class MineHudState {
        final UUID playerId;
        final MineHud hud;
        final MineToastManager toastManager = new MineToastManager();
        long readyAtMs;
        long lastCrystals = -1;
        String lastInventoryKey;
        String lastCooldownKey;
        String lastMineName;
        boolean blockHealthVisible;
        long blockHealthLastUpdateMs;

        MineHudState(UUID playerId, MineHud hud) {
            this.playerId = playerId;
            this.hud = hud;
        }

        void resetCache() {
            lastCrystals = -1;
            lastInventoryKey = null;
            lastCooldownKey = null;
            lastMineName = null;
            toastManager.clear();
        }
    }
}
