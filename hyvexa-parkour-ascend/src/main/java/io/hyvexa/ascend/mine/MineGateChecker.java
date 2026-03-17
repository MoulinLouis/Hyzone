package io.hyvexa.ascend.mine;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineUpgradeType;
import io.hyvexa.ascend.mine.hud.MineHudManager;
import io.hyvexa.ascend.util.AscendInventoryUtils;
import io.hyvexa.common.util.InventoryUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MineGateChecker {

    private static final long COOLDOWN_MS = 2000;
    private static final long FADE_DURATION_MS = 300;

    private final MineConfigStore configStore;
    private final AscendPlayerStore playerStore;
    private final MinePlayerStore minePlayerStore;
    private final Map<UUID, Long> lastTeleport = new ConcurrentHashMap<>();
    private final Map<UUID, GateTransition> pendingTransitions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerHasteLevels = new ConcurrentHashMap<>();

    public MineGateChecker(MineConfigStore configStore, AscendPlayerStore playerStore, MinePlayerStore minePlayerStore) {
        this.configStore = configStore;
        this.playerStore = playerStore;
        this.minePlayerStore = minePlayerStore;
    }

    public void checkPlayer(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;

        // Handle pending fade transitions
        GateTransition transition = pendingTransitions.get(playerId);
        if (transition != null) {
            tickTransition(playerId, ref, store, transition);
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        Vector3d pos = transform.getPosition();
        if (pos == null) return;

        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();

        if (isOnCooldown(playerId)) return;

        // Entry gate: start fade -> teleport inside mine + give pickaxe
        if (configStore.isInsideEntryGate(x, y, z)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (denyMineAccess(playerId, player)) return;
            startTransition(playerId, true,
                configStore.getEntryDestX(), configStore.getEntryDestY(), configStore.getEntryDestZ(),
                configStore.getEntryDestRotX(), configStore.getEntryDestRotY(), configStore.getEntryDestRotZ());
            return;
        }

        // Exit gate: start fade -> teleport outside mine + restore menu items
        if (configStore.isInsideExitGate(x, y, z)) {
            startTransition(playerId, false,
                configStore.getExitDestX(), configStore.getExitDestY(), configStore.getExitDestZ(),
                configStore.getExitDestRotX(), configStore.getExitDestRotY(), configStore.getExitDestRotZ());
        }
    }

    private void startTransition(UUID playerId, boolean entering,
                                  double destX, double destY, double destZ,
                                  float destRotX, float destRotY, float destRotZ) {
        GateTransition transition = new GateTransition(entering, destX, destY, destZ, destRotX, destRotY, destRotZ);
        pendingTransitions.put(playerId, transition);
        markCooldown(playerId);

        // Show black screen on current HUD
        showFade(playerId, entering, true);
    }

    private void tickTransition(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store, GateTransition transition) {
        long elapsed = System.currentTimeMillis() - transition.phaseStartMs;

        if (transition.phase == TransitionPhase.FADE_IN && elapsed >= FADE_DURATION_MS) {
            // Fade-in complete: do the actual teleport + swap
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (transition.entering) {
                enterMine(playerId, playerRef, ref, store,
                    transition.destX, transition.destY, transition.destZ,
                    transition.destRotX, transition.destRotY, transition.destRotZ,
                    false);
            } else {
                exitMine(playerId, playerRef, ref, store, false);
            }

            // Show fade on the NEW HUD (after swap)
            showFade(playerId, !transition.entering, true);

            transition.phase = TransitionPhase.FADE_OUT;
            transition.phaseStartMs = System.currentTimeMillis();
            return;
        }

        if (transition.phase == TransitionPhase.FADE_OUT && elapsed >= FADE_DURATION_MS) {
            // Fade-out complete: hide black screen
            showFade(playerId, !transition.entering, false);
            pendingTransitions.remove(playerId);
        }
    }

    /**
     * Show or hide the fullscreen black overlay on the appropriate HUD.
     * @param onAscendHud true to target the Ascend HUD, false for the Mine HUD
     */
    private void showFade(UUID playerId, boolean onAscendHud, boolean visible) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;

        if (onAscendHud) {
            AscendHudManager ascendHud = plugin.getHudManager();
            if (ascendHud != null) {
                ascendHud.showScreenFade(playerId, visible);
            }
        } else {
            MineHudManager mineHud = plugin.getMineHudManager();
            if (mineHud != null) {
                mineHud.showScreenFade(playerId, visible);
            }
        }
    }

    public boolean canAccessMine(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        return progress != null && progress.getAscensionCount() >= 1;
    }

    public boolean denyMineAccess(UUID playerId, Player player) {
        if (canAccessMine(playerId)) {
            return false;
        }
        if (player != null) {
            player.sendMessage(Message.raw("[Mine] You need at least 1 ascension to access mines."));
        }
        return true;
    }

    public boolean enterMine(UUID playerId, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store,
                             double x, double y, double z, float rotX, float rotY, float rotZ,
                             boolean respectCooldown) {
        if (ref == null || !ref.isValid() || store == null) {
            return false;
        }
        if (respectCooldown && isOnCooldown(playerId)) {
            return false;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (denyMineAccess(playerId, player)) {
            return false;
        }

        World world = store.getExternalData().getWorld();
        if (world == null) {
            return false;
        }

        teleportPlayer(ref, store, world, x, y, z, rotX, rotY, rotZ);
        markCooldown(playerId);

        if (player != null) {
            giveMineItems(player, playerId);
        }
        swapToMineHud(playerId, playerRef, player);
        if (minePlayerStore != null) {
            MinePlayerProgress progress = minePlayerStore.getOrCreatePlayer(playerId);
            progress.setInMine(true);
            minePlayerStore.markDirty(playerId);

            // Apply haste speed boost
            int hasteLevel = progress.getUpgradeLevel(MineUpgradeType.HASTE);
            if (hasteLevel > 0) {
                playerHasteLevels.put(playerId, hasteLevel);
                applyHasteSpeed(player, hasteLevel);
            }
        }
        return true;
    }

    public boolean exitMine(UUID playerId, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store,
                            boolean respectCooldown) {
        if (ref == null || !ref.isValid() || store == null) {
            return false;
        }
        if (respectCooldown && isOnCooldown(playerId)) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return false;
        }

        teleportPlayer(ref, store, world,
            configStore.getExitDestX(), configStore.getExitDestY(), configStore.getExitDestZ(),
            configStore.getExitDestRotX(), configStore.getExitDestRotY(), configStore.getExitDestRotZ());
        markCooldown(playerId);

        Player player = store.getComponent(ref, Player.getComponentType());
        swapToAscendHud(playerId, playerRef, player);
        if (player != null) {
            AscendInventoryUtils.giveMenuItems(player);
        }
        // Remove haste speed boost
        playerHasteLevels.remove(playerId);
        removeHasteSpeed(player);

        if (minePlayerStore != null) {
            MinePlayerProgress progress = minePlayerStore.getOrCreatePlayer(playerId);
            progress.setInMine(false);
            minePlayerStore.markDirty(playerId);
        }
        return true;
    }

    private void teleportPlayer(Ref<EntityStore> ref, Store<EntityStore> store, World world,
                                double x, double y, double z,
                                float rotX, float rotY, float rotZ) {
        Vector3d destPos = new Vector3d(x, y, z);
        Vector3f destRot = new Vector3f(rotX, rotY, rotZ);
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, destPos, destRot));
    }

    private void swapToMineHud(UUID playerId, PlayerRef playerRef, Player player) {
        if (player == null) {
            return;
        }
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        plugin.getHudManager().removePlayer(playerId);
        MineHudManager mineHud = plugin.getMineHudManager();
        if (mineHud != null && playerRef != null) {
            mineHud.attachHud(playerRef, player);
        }
    }

    private void swapToAscendHud(UUID playerId, PlayerRef playerRef, Player player) {
        if (player == null) {
            return;
        }
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        MineHudManager mineHud = plugin.getMineHudManager();
        if (mineHud != null) {
            mineHud.detachHud(playerId);
        }
        if (playerRef != null) {
            plugin.getHudManager().attach(playerRef, player);
        }
    }

    public void giveMineItems(Player player, UUID playerId) {
        if (player.getWorld() == null) return;
        InventoryUtils.clearAllContainers(player);
        Inventory inventory = player.getInventory();
        if (inventory == null) return;
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null || hotbar.getCapacity() <= 0) return;

        String pickaxeItemId = AscendConstants.ITEM_MINE_PICKAXE;
        if (minePlayerStore != null) {
            MinePlayerProgress progress = minePlayerStore.getOrCreatePlayer(playerId);
            pickaxeItemId = progress.getPickaxeTierEnum().getItemId();
        }

        hotbar.setItemStackForSlot((short) 0, new ItemStack(pickaxeItemId, 1), false);
        hotbar.setItemStackForSlot((short) 1, new ItemStack(AscendConstants.ITEM_MINE_CHEST, 1), false);
        InventoryUtils.giveGlobalItems(hotbar);
    }

    private boolean isOnCooldown(UUID playerId) {
        Long last = lastTeleport.get(playerId);
        return last != null && (System.currentTimeMillis() - last) < COOLDOWN_MS;
    }

    private void markCooldown(UUID playerId) {
        lastTeleport.put(playerId, System.currentTimeMillis());
    }

    public void evict(UUID playerId) {
        lastTeleport.remove(playerId);
        pendingTransitions.remove(playerId);
        playerHasteLevels.remove(playerId);
    }

    private void applyHasteSpeed(Player player, int hasteLevel) {
        if (player == null) return;
        double multiplier = 1.0 + (hasteLevel * 0.05); // +5% per level
        // TODO: Validate Unsafe approach for persistent speed modification.
        // For now, set horizontalSpeedMultiplier (resets each tick but applies for 1 tick).
        // A tick-based reapplication system should be added if Unsafe doesn't work.
        try {
            player.setHorizontalSpeedMultiplier((float) multiplier);
        } catch (Exception e) {
            // Fallback: speed multiplier may not be available
        }
    }

    private void removeHasteSpeed(Player player) {
        if (player == null) return;
        try {
            player.setHorizontalSpeedMultiplier(1.0f);
        } catch (Exception e) {
            // Fallback: speed multiplier may not be available
        }
    }

    /**
     * Called each tick to reapply haste speed for all players in the mine.
     * Required because horizontalSpeedMultiplier resets every tick.
     */
    public void tickHaste(Map<UUID, Player> onlinePlayers) {
        for (var entry : playerHasteLevels.entrySet()) {
            Player player = onlinePlayers.get(entry.getKey());
            if (player != null) {
                applyHasteSpeed(player, entry.getValue());
            }
        }
    }

    // --- Fade transition state ---

    private enum TransitionPhase { FADE_IN, FADE_OUT }

    private static class GateTransition {
        final boolean entering; // true = entering mine, false = exiting
        final double destX, destY, destZ;
        final float destRotX, destRotY, destRotZ;
        TransitionPhase phase;
        long phaseStartMs;

        GateTransition(boolean entering, double destX, double destY, double destZ,
                       float destRotX, float destRotY, float destRotZ) {
            this.entering = entering;
            this.destX = destX;
            this.destY = destY;
            this.destZ = destZ;
            this.destRotX = destRotX;
            this.destRotY = destRotY;
            this.destRotZ = destRotZ;
            this.phase = TransitionPhase.FADE_IN;
            this.phaseStartMs = System.currentTimeMillis();
        }
    }
}
