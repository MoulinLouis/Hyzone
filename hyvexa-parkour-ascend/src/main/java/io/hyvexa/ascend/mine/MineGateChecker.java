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
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.mine.data.GateConfigStore;
import io.hyvexa.ascend.mine.data.MineHierarchyStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineZoneLayer;
import io.hyvexa.ascend.mine.hud.MineHudManager;
import io.hyvexa.ascend.mine.system.EggDropHelper;
import org.bson.BsonDocument;
import org.bson.BsonString;
import io.hyvexa.ascend.util.AscendInventoryUtils;
import io.hyvexa.common.util.InventoryUtils;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.protocol.MovementSettings;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MineGateChecker {

    private static final long COOLDOWN_MS = 2000;
    private static final long LOADING_MS = 1500;
    private static final long FADE_OUT_MS = 400;

    private static final String TEXT_ENTERING = "Entering the Mines...";
    private static final String TEXT_EXITING = "Returning to Surface...";

    private final GateConfigStore gateConfigStore;
    private final MineHierarchyStore hierarchyStore;
    private final AscendPlayerStore playerStore;
    private final MinePlayerStore minePlayerStore;
    private final Map<UUID, Long> lastTeleport = new ConcurrentHashMap<>();
    private final Map<UUID, GateTransition> pendingTransitions = new ConcurrentHashMap<>();
    private volatile AscendHudManager ascendHudManager;
    private volatile MineHudManager mineHudManager;

    public MineGateChecker(GateConfigStore gateConfigStore, MineHierarchyStore hierarchyStore, AscendPlayerStore playerStore, MinePlayerStore minePlayerStore) {
        this.gateConfigStore = gateConfigStore;
        this.hierarchyStore = hierarchyStore;
        this.playerStore = playerStore;
        this.minePlayerStore = minePlayerStore;
    }

    public void setHudManagers(AscendHudManager ascendHudManager, MineHudManager mineHudManager) {
        this.ascendHudManager = ascendHudManager;
        this.mineHudManager = mineHudManager;
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
        if (gateConfigStore.isInsideEntryGate(x, y, z)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (denyMineAccess(playerId, player)) return;
            startTransition(playerId, true,
                gateConfigStore.getEntryDestX(), gateConfigStore.getEntryDestY(), gateConfigStore.getEntryDestZ(),
                gateConfigStore.getEntryDestRotX(), gateConfigStore.getEntryDestRotY(), gateConfigStore.getEntryDestRotZ());
            return;
        }

        // Exit gate: start fade -> teleport outside mine + restore menu items
        if (gateConfigStore.isInsideExitGate(x, y, z)) {
            startTransition(playerId, false,
                gateConfigStore.getExitDestX(), gateConfigStore.getExitDestY(), gateConfigStore.getExitDestZ(),
                gateConfigStore.getExitDestRotX(), gateConfigStore.getExitDestRotY(), gateConfigStore.getExitDestRotZ());
            return;
        }
    }

    private void startTransition(UUID playerId, boolean entering,
                                  double destX, double destY, double destZ,
                                  float destRotX, float destRotY, float destRotZ) {
        String text = entering ? TEXT_ENTERING : TEXT_EXITING;
        GateTransition transition = new GateTransition(entering, text, destX, destY, destZ, destRotX, destRotY, destRotZ);
        pendingTransitions.put(playerId, transition);
        markCooldown(playerId);

        // Show loading screen on current HUD
        boolean onAscendHud = entering;
        showFade(playerId, onAscendHud, true);
        updateFadeBar(playerId, onAscendHud, text, 0f);
    }

    private void tickTransition(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store, GateTransition transition) {
        long elapsed = System.currentTimeMillis() - transition.phaseStartMs;

        if (transition.phase == TransitionPhase.LOADING) {
            // Animate loading bar each tick
            float progress = Math.min(1.0f, (float) elapsed / LOADING_MS);
            boolean onAscendHud = transition.entering;
            updateFadeBar(playerId, onAscendHud, transition.loadingText, progress);

            if (elapsed >= LOADING_MS) {
                // Loading complete: do the actual teleport + swap
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (transition.entering) {
                    enterMine(playerId, playerRef, ref, store,
                        transition.destX, transition.destY, transition.destZ,
                        transition.destRotX, transition.destRotY, transition.destRotZ,
                        false);
                } else {
                    exitMine(playerId, playerRef, ref, store, false);
                }

                // Show black screen briefly on the NEW HUD (no text)
                showFade(playerId, !transition.entering, true);

                transition.phase = TransitionPhase.FADE_OUT;
                transition.phaseStartMs = System.currentTimeMillis();
            }
            return;
        }

        if (transition.phase == TransitionPhase.FADE_OUT && elapsed >= FADE_OUT_MS) {
            // Reveal the new scene — hide fade on both HUDs since the Ascend HUD persists
            showFade(playerId, true, false);
            showFade(playerId, false, false);
            pendingTransitions.remove(playerId);
        }
    }

    /**
     * Show or hide the fullscreen black overlay on the appropriate HUD.
     * @param onAscendHud true to target the Ascend HUD, false for the Mine HUD
     */
    private void showFade(UUID playerId, boolean onAscendHud, boolean visible) {
        if (onAscendHud) {
            if (ascendHudManager != null) {
                ascendHudManager.showScreenFade(playerId, visible);
            }
        } else {
            if (mineHudManager != null) {
                mineHudManager.showScreenFade(playerId, visible);
            }
        }
    }

    /**
     * Update the loading text and progress bar on the appropriate HUD.
     */
    private void updateFadeBar(UUID playerId, boolean onAscendHud, String text, float progress) {
        if (onAscendHud) {
            if (ascendHudManager != null) {
                ascendHudManager.updateScreenFadeBar(playerId, text, progress);
            }
        } else {
            if (mineHudManager != null) {
                mineHudManager.updateScreenFadeBar(playerId, text, progress);
            }
        }
    }

    public boolean canAccessMine(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        return progress != null && progress.gameplay().getAscensionCount() >= 1;
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
        if (store.getExternalData() == null) return false;
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
            applyHasteSpeed(progress, ref, store, playerRef);
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
        if (store.getExternalData() == null) return false;
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return false;
        }

        teleportPlayer(ref, store, world,
            gateConfigStore.getExitDestX(), gateConfigStore.getExitDestY(), gateConfigStore.getExitDestZ(),
            gateConfigStore.getExitDestRotX(), gateConfigStore.getExitDestRotY(), gateConfigStore.getExitDestRotZ());
        markCooldown(playerId);

        resetSpeed(ref, store, playerRef);

        Player player = store.getComponent(ref, Player.getComponentType());
        swapToAscendHud(playerId, playerRef, player);
        if (player != null) {
            AscendInventoryUtils.giveMenuItems(player);
        }
        if (minePlayerStore != null) {
            MinePlayerProgress progress = minePlayerStore.getOrCreatePlayer(playerId);
            progress.setInMine(false);
            progress.clearChestSlots();
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
        if (player == null || ascendHudManager == null) {
            return;
        }
        // Keep the Ascend HUD visible but hide economy elements
        ascendHudManager.setMineMode(playerId, true);
        // Add the mine HUD alongside it
        if (mineHudManager != null && playerRef != null) {
            mineHudManager.attachHud(playerRef, player);
        }
    }

    private void swapToAscendHud(UUID playerId, PlayerRef playerRef, Player player) {
        if (player == null || ascendHudManager == null) {
            return;
        }
        // Detach the mine HUD and restore full Ascend HUD
        if (mineHudManager != null) {
            mineHudManager.detachHud(playerId, playerRef, player);
        }
        ascendHudManager.setMineMode(playerId, false);
    }

    public void applyHasteSpeed(MinePlayerProgress progress, Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        double multiplier = progress.getHasteMultiplier();
        if (multiplier <= 1.0) return;
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) return;
        movementManager.refreshDefaultSettings(ref, store);
        movementManager.applyDefaultSettings();
        MovementSettings settings = movementManager.getSettings();
        if (settings == null) return;
        float m = (float) multiplier;
        settings.maxSpeedMultiplier *= m;
        settings.forwardRunSpeedMultiplier *= m;
        settings.backwardRunSpeedMultiplier *= m;
        settings.strafeRunSpeedMultiplier *= m;
        settings.forwardSprintSpeedMultiplier *= m;
        var packetHandler = playerRef != null ? playerRef.getPacketHandler() : null;
        if (packetHandler != null) {
            movementManager.update(packetHandler);
        }
    }

    private void resetSpeed(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (ref == null || !ref.isValid() || store == null) return;
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) return;
        movementManager.refreshDefaultSettings(ref, store);
        movementManager.applyDefaultSettings();
        var packetHandler = playerRef != null ? playerRef.getPacketHandler() : null;
        if (packetHandler != null) {
            movementManager.update(packetHandler);
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
        hotbar.setItemStackForSlot((short) 2, new ItemStack(AscendConstants.ITEM_MINE_LEADERBOARD, 1), false);

        // Restore egg chests from virtual inventory with per-layer item ID + metadata
        // Sort by layerId for deterministic slot assignment across exit/enter cycles
        if (minePlayerStore != null) {
            MinePlayerProgress progress = minePlayerStore.getOrCreatePlayer(playerId);
            progress.clearChestSlots();
            Map<String, Integer> eggs = new java.util.TreeMap<>(progress.getEggInventory());
            short nextSlot = 3;
            for (var entry : eggs.entrySet()) {
                if (nextSlot > 6) break;
                int count = entry.getValue();
                if (count <= 0) continue;
                int capped = Math.min(count, 64);
                String layerId = entry.getKey();
                MineZoneLayer layer = hierarchyStore.getLayerById(layerId);
                String itemId = EggDropHelper.resolveEggItemId(layer);
                BsonDocument meta = new BsonDocument("EggLayerId", new BsonString(layerId));
                hotbar.setItemStackForSlot(nextSlot, new ItemStack(itemId, capped, meta), false);
                progress.assignChestSlot(nextSlot, layerId);
                nextSlot++;
            }
        }

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
    }

    // --- Fade transition state ---

    private enum TransitionPhase { LOADING, FADE_OUT }

    private static class GateTransition {
        final boolean entering;
        final String loadingText;
        final double destX, destY, destZ;
        final float destRotX, destRotY, destRotZ;
        TransitionPhase phase;
        long phaseStartMs;

        GateTransition(boolean entering, String loadingText,
                       double destX, double destY, double destZ,
                       float destRotX, float destRotY, float destRotZ) {
            this.entering = entering;
            this.loadingText = loadingText;
            this.destX = destX;
            this.destY = destY;
            this.destZ = destZ;
            this.destRotX = destRotX;
            this.destRotY = destRotY;
            this.destRotZ = destRotZ;
            this.phase = TransitionPhase.LOADING;
            this.phaseStartMs = System.currentTimeMillis();
        }
    }
}
