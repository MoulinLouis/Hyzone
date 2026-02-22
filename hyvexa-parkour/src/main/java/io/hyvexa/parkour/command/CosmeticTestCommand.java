package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.modelvfx.config.ModelVFX;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.AsyncExecutionHelper;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.PermissionUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test command for experimenting with player cosmetics (effects, model particles, VFX).
 * Temporary R&D command -- will be removed once cosmetic system is finalized.
 */
public class CosmeticTestCommand extends AbstractAsyncCommand {

    private static final Set<Integer> activeCycles = ConcurrentHashMap.newKeySet();

    /** Effects that have gameplay side-effects (damage, slow, poison, screen overlay) and persist after clearEffects. */
    private static final Set<String> DANGEROUS_EFFECTS = Set.of(
            "status", "food", "burn", "freeze", "poison"
    );

    private static boolean isDangerousEffect(String key) {
        String lower = key.toLowerCase();
        for (String keyword : DANGEROUS_EFFECTS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    public CosmeticTestCommand() {
        super("cosmetic", "Cosmetic test commands");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            ctx.sendMessage(Message.raw("Players only."));
            return CompletableFuture.completedFuture(null);
        }
        if (!PermissionUtils.isOp(player)) {
            ctx.sendMessage(Message.raw("You must be OP to use this command."));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;

            PacketHandler ph = playerRef.getPacketHandler();
            if (ph == null) {
                player.sendMessage(Message.raw("[Cosmetic] No packet handler."));
                return;
            }

            String[] args = CommandUtils.getArgs(ctx);
            if (args.length == 0) {
                showHelp(player);
                return;
            }

            switch (args[0].toLowerCase()) {
                case "effect" -> handleEffect(player, ph, ref, store, args);
                case "listeffects" -> listEffects(player, args);
                case "particle" -> handleParticle(player, ph, args);
                case "listparticles" -> listParticles(player, args);
                case "listvfx" -> listVfx(player, args);
                case "cycle" -> handleCycle(player, ph, ref, store, world, args);
                case "preset" -> handlePreset(player, ph, ref, store, args);
                case "help" -> showHelp(player);
                default -> player.sendMessage(Message.raw("[Cosmetic] Unknown: " + args[0] + ". Use /cosmetic help"));
            }
        }, world).exceptionally(ex -> {
            AsyncExecutionHelper.logThrottledWarning("cosmetic.execute", "cosmetic command execution",
                    "player=" + player.getNetworkId(), ex);
            return null;
        });
    }

    // ── Effect subcommand ────────────────────────────────────────────────

    private void handleEffect(Player player, PacketHandler ph, Ref<EntityStore> ref, Store<EntityStore> store, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw("[Cosmetic] Usage: /cosmetic effect <name> [duration]"));
            player.sendMessage(Message.raw("[Cosmetic]   /cosmetic effect clear - Remove all effects"));
            return;
        }

        if (args[1].equalsIgnoreCase("clear")) {
            clearEffects(player, ph, ref, store);
            return;
        }

        if (args[1].equalsIgnoreCase("purge")) {
            purgeEffects(player, ph, ref, store);
            return;
        }

        String name = args[1];
        float duration = 0f; // 0 = infinite
        if (args.length > 2) {
            try { duration = Float.parseFloat(args[2]); } catch (NumberFormatException ignored) {}
        }

        EntityEffect effect = resolveEffect(name);
        if (effect == null) {
            player.sendMessage(Message.raw("[Cosmetic] Effect '" + name + "' not found. Use /cosmetic listeffects"));
            return;
        }

        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl == null) {
            player.sendMessage(Message.raw("[Cosmetic] Player has no EffectControllerComponent."));
            return;
        }

        // Get the correct asset index -- the client uses this to look up which effect to render
        int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
        if (effectIndex < 0) {
            player.sendMessage(Message.raw("[Cosmetic] Effect '" + effect.getId() + "' has no asset index."));
            return;
        }

        if (duration <= 0) {
            ctrl.addInfiniteEffect(ref, effectIndex, effect, store);
            player.sendMessage(Message.raw("[Cosmetic] Applied '" + effect.getId() + "' [idx=" + effectIndex + "] (infinite). Use /cosmetic effect clear to remove."));
        } else {
            ctrl.addEffect(ref, effectIndex, effect, duration, OverlapBehavior.OVERWRITE, store);
            player.sendMessage(Message.raw("[Cosmetic] Applied '" + effect.getId() + "' [idx=" + effectIndex + "] (duration=" + duration + "s)."));
        }

        // Send effect update directly to the player -- entity tracker only syncs to OTHER players
        sendEffectSyncToSelf(ph, player, ctrl);
    }

    private EntityEffect resolveEffect(String name) {
        var assetMap = EntityEffect.getAssetMap();
        var map = assetMap.getAssetMap();

        // Try exact match first
        EntityEffect effect = map.get(name);
        if (effect != null) return effect;

        // Try case-insensitive match on keys
        for (var entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }

        // Try partial match (key ends with the name)
        for (var entry : map.entrySet()) {
            String key = entry.getKey();
            String keyLower = key.toLowerCase();
            String nameLower = name.toLowerCase();
            if (keyLower.endsWith("/" + nameLower) || keyLower.endsWith(nameLower)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private void clearEffects(Player player, PacketHandler ph, Ref<EntityStore> ref, Store<EntityStore> store) {
        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl == null) {
            player.sendMessage(Message.raw("[Cosmetic] Player has no EffectControllerComponent."));
            return;
        }
        ctrl.clearEffects(ref, store);
        sendEffectSyncToSelf(ph, player, ctrl);
        player.sendMessage(Message.raw("[Cosmetic] All effects cleared."));
    }

    private void purgeEffects(Player player, PacketHandler ph, Ref<EntityStore> ref, Store<EntityStore> store) {
        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl == null) {
            player.sendMessage(Message.raw("[Cosmetic] Player has no EffectControllerComponent."));
            return;
        }

        // Overwrite every known effect slot with a tiny duration to expire stuck infinite effects
        var allEffects = EntityEffect.getAssetMap().getAssetMap();
        int count = 0;
        for (var entry : allEffects.entrySet()) {
            int idx = EntityEffect.getAssetMap().getIndex(entry.getValue().getId());
            if (idx >= 0) {
                ctrl.addEffect(ref, idx, entry.getValue(), 0.01f, OverlapBehavior.OVERWRITE, store);
                count++;
            }
        }

        ctrl.clearEffects(ref, store);
        sendEffectSyncToSelf(ph, player, ctrl);
        player.sendMessage(Message.raw("[Cosmetic] Purged " + count + " effect slots. Reconnect to clear any remaining particles."));
    }

    // ── Cycle subcommand ──────────────────────────────────────────────────

    private void handleCycle(Player player, PacketHandler ph, Ref<EntityStore> ref, Store<EntityStore> store, World world, String[] args) {
        int networkId = player.getNetworkId();

        if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
            if (activeCycles.remove(networkId)) {
                player.sendMessage(Message.raw("[Cosmetic] Cycle stopping..."));
            } else {
                player.sendMessage(Message.raw("[Cosmetic] No cycle running."));
            }
            return;
        }

        if (activeCycles.contains(networkId)) {
            player.sendMessage(Message.raw("[Cosmetic] Cycle already running. Use /cosmetic cycle stop"));
            return;
        }

        String filter = args.length > 1 ? args[1].toLowerCase() : "";
        int delay = 4;
        if (args.length > 2) {
            try { delay = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
            delay = Math.max(1, Math.min(30, delay));
        }

        var map = EntityEffect.getAssetMap().getAssetMap();
        List<Map.Entry<String, EntityEffect>> effects = new ArrayList<>();
        int skipped = 0;
        for (var entry : map.entrySet()) {
            if (!filter.isEmpty() && !entry.getKey().toLowerCase().contains(filter)) continue;
            if (isDangerousEffect(entry.getKey())) { skipped++; continue; }
            effects.add(entry);
        }

        if (effects.isEmpty()) {
            player.sendMessage(Message.raw("[Cosmetic] No effects found" + (filter.isEmpty() ? "" : " matching '" + filter + "'") + "."));
            return;
        }

        activeCycles.add(networkId);
        int total = effects.size();
        int delayMs = delay * 1000;
        float effectDuration = delay + 1.0f;

        player.sendMessage(Message.raw("[Cosmetic] Cycling " + total + " effects (" + delay + "s each)" + (skipped > 0 ? ", skipped " + skipped + " dangerous" : "") + ". /cosmetic cycle stop to cancel.").color("#FFD700"));

        Thread cycleThread = new Thread(() -> {
            try {
                for (int i = 0; i < effects.size(); i++) {
                    if (!activeCycles.contains(networkId) || !ref.isValid()) break;

                    var entry = effects.get(i);
                    String key = entry.getKey();
                    EntityEffect effect = entry.getValue();
                    int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
                    if (effectIndex < 0) continue;

                    int num = i + 1;
                    CompletableFuture.runAsync(() -> {
                        if (!activeCycles.contains(networkId) || !ref.isValid()) return;
                        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
                        if (ctrl == null) return;
                        // Use timed duration instead of infinite -- effects auto-expire so they don't
                        // accumulate in the component (which causes stuck effects like poison)
                        ctrl.addEffect(ref, effectIndex, effect, effectDuration, OverlapBehavior.OVERWRITE, store);
                        sendEffectSyncToSelf(ph, player, ctrl);
                        player.sendMessage(Message.raw("[Cosmetic] [" + num + "/" + total + "] " + key).color("#FFD700"));
                    }, world).join();

                    Thread.sleep(delayMs);
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                AsyncExecutionHelper.logThrottledWarning("cosmetic.cycle", "cosmetic cycle",
                        "player=" + networkId, e);
            } finally {
                if (activeCycles.remove(networkId)) {
                    CompletableFuture.runAsync(() -> {
                        if (!ref.isValid()) return;
                        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
                        if (ctrl != null) {
                            ctrl.clearEffects(ref, store);
                            sendEffectSyncToSelf(ph, player, ctrl);
                        }
                        player.sendMessage(Message.raw("[Cosmetic] Cycle complete.").color("#FFD700"));
                    }, world);
                }
            }
        }, "cosmetic-cycle-" + networkId);
        cycleThread.setDaemon(true);
        cycleThread.start();
    }

    // ── List effects ─────────────────────────────────────────────────────

    private void listEffects(Player player, String[] args) {
        try {
            var assetMap = EntityEffect.getAssetMap();
            var map = assetMap.getAssetMap();

            String filter = args.length > 1 ? args[1].toLowerCase() : "";
            int count = 0;
            int max = 20;

            player.sendMessage(Message.raw("[Cosmetic] Entity effects" + (filter.isEmpty() ? "" : " matching '" + filter + "'") + ":").color("#FFD700"));
            for (var key : map.keySet()) {
                if (!filter.isEmpty() && !key.toLowerCase().contains(filter)) continue;
                player.sendMessage(Message.raw("  " + key));
                count++;
                if (count >= max) {
                    player.sendMessage(Message.raw("  ... and more. Use /cosmetic listeffects <filter>"));
                    break;
                }
            }
            if (count == 0) {
                player.sendMessage(Message.raw("  (none found)"));
            }
        } catch (Exception e) {
            player.sendMessage(Message.raw("[Cosmetic] Error listing effects: " + e.getMessage()));
        }
    }

    // ── Particle subcommand ──────────────────────────────────────────────

    private void handleParticle(Player player, PacketHandler ph, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw("[Cosmetic] Usage: /cosmetic particle <systemId> [scale] [hexColor]"));
            player.sendMessage(Message.raw("[Cosmetic]   /cosmetic particle clear - Note: no API to remove model particles"));
            return;
        }

        if (args[1].equalsIgnoreCase("clear")) {
            player.sendMessage(Message.raw("[Cosmetic] No API to remove model particles. Reconnect to clear them."));
            return;
        }

        String systemId = args[1];
        float scale = 1.0f;
        Color color = null;

        if (args.length > 2) {
            try { scale = Float.parseFloat(args[2]); } catch (NumberFormatException ignored) {}
        }
        if (args.length > 3) {
            color = parseHexColor(args[3]);
        }

        ModelParticle mp = new ModelParticle(
                systemId,
                scale,
                color,
                EntityPart.Entity,
                null,
                new Vector3f(0f, 0.5f, 0f),
                null,
                false // attached to model
        );

        int entityId = player.getNetworkId();
        ph.writeNoCache(new SpawnModelParticles(entityId, new ModelParticle[]{mp}));
        player.sendMessage(Message.raw("[Cosmetic] Model particle '" + systemId + "' attached (scale=" + scale + "). Only visible to you."));
    }

    private Color parseHexColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            int rgb = Integer.parseInt(hex, 16);
            return new Color((byte) ((rgb >> 16) & 0xFF), (byte) ((rgb >> 8) & 0xFF), (byte) (rgb & 0xFF));
        } catch (Exception e) {
            return null;
        }
    }

    // ── List particles ───────────────────────────────────────────────────

    private void listParticles(Player player, String[] args) {
        try {
            var assetMap = ParticleSystem.getAssetMap();
            var map = assetMap.getAssetMap();

            String filter = args.length > 1 ? args[1].toLowerCase() : "";
            int count = 0;
            int max = 20;

            player.sendMessage(Message.raw("[Cosmetic] Particle systems" + (filter.isEmpty() ? "" : " matching '" + filter + "'") + ":").color("#FFD700"));
            for (var key : map.keySet()) {
                if (!filter.isEmpty() && !key.toLowerCase().contains(filter)) continue;
                player.sendMessage(Message.raw("  " + key));
                count++;
                if (count >= max) {
                    player.sendMessage(Message.raw("  ... and more. Use /cosmetic listparticles <filter>"));
                    break;
                }
            }
            if (count == 0) {
                player.sendMessage(Message.raw("  (none found)"));
            }
        } catch (Exception e) {
            player.sendMessage(Message.raw("[Cosmetic] Error listing particles: " + e.getMessage()));
        }
    }

    // ── List VFX ─────────────────────────────────────────────────────────

    private void listVfx(Player player, String[] args) {
        try {
            var assetMap = ModelVFX.getAssetMap();
            var map = assetMap.getAssetMap();

            String filter = args.length > 1 ? args[1].toLowerCase() : "";
            int count = 0;
            int max = 20;

            player.sendMessage(Message.raw("[Cosmetic] ModelVFX" + (filter.isEmpty() ? "" : " matching '" + filter + "'") + ":").color("#FFD700"));
            for (var key : map.keySet()) {
                if (!filter.isEmpty() && !key.toLowerCase().contains(filter)) continue;
                player.sendMessage(Message.raw("  " + key));
                count++;
                if (count >= max) {
                    player.sendMessage(Message.raw("  ... and more. Use /cosmetic listvfx <filter>"));
                    break;
                }
            }
            if (count == 0) {
                player.sendMessage(Message.raw("  (none found)"));
            }
        } catch (Exception e) {
            player.sendMessage(Message.raw("[Cosmetic] Error listing VFX: " + e.getMessage()));
        }
    }

    // ── Preset subcommand ────────────────────────────────────────────────

    private void handlePreset(Player player, PacketHandler ph, Ref<EntityStore> ref, Store<EntityStore> store, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw("[Cosmetic] Usage: /cosmetic preset <name>"));
            player.sendMessage(Message.raw("[Cosmetic] Presets: fire, ice, gold, legendary, creative, intangible, heal"));
            return;
        }

        String preset = args[1].toLowerCase();
        switch (preset) {
            case "fire" -> applyEffectPreset(player, ph, ref, store, "Burn", "fire");
            case "ice" -> applyEffectPreset(player, ph, ref, store, "Freeze", "ice");
            case "gold" -> applyEffectPreset(player, ph, ref, store, "Crown_Gold", "gold");
            case "legendary" -> applyEffectPreset(player, ph, ref, store, "Drop_Legendary", "legendary");
            case "creative" -> applyEffectPreset(player, ph, ref, store, "Creative", "creative");
            case "intangible" -> applyEffectPreset(player, ph, ref, store, "Intangible_Dark", "intangible");
            case "heal" -> {
                // Heal uses a model particle instead of an entity effect
                ModelParticle mp = new ModelParticle(
                        "Status_Effect/Heal/Aura_Heal",
                        1.0f,
                        null,
                        EntityPart.Entity,
                        null,
                        new Vector3f(0f, 0.5f, 0f),
                        null,
                        false
                );
                int entityId = player.getNetworkId();
                ph.writeNoCache(new SpawnModelParticles(entityId, new ModelParticle[]{mp}));
                player.sendMessage(Message.raw("[Cosmetic] Preset 'heal' applied (model particle). Only visible to you."));
            }
            default -> {
                player.sendMessage(Message.raw("[Cosmetic] Unknown preset: " + preset));
                player.sendMessage(Message.raw("[Cosmetic] Presets: fire, ice, gold, legendary, creative, intangible, heal"));
            }
        }
    }

    private void applyEffectPreset(Player player, PacketHandler ph, Ref<EntityStore> ref, Store<EntityStore> store,
                                    String effectName, String presetLabel) {
        EntityEffect effect = resolveEffect(effectName);
        if (effect == null) {
            player.sendMessage(Message.raw("[Cosmetic] Preset '" + presetLabel + "': effect '" + effectName + "' not found. Use /cosmetic listeffects to discover keys."));
            return;
        }

        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl == null) {
            player.sendMessage(Message.raw("[Cosmetic] Player has no EffectControllerComponent."));
            return;
        }

        int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
        if (effectIndex < 0) {
            player.sendMessage(Message.raw("[Cosmetic] Preset '" + presetLabel + "': no asset index for '" + effect.getId() + "'."));
            return;
        }

        ctrl.addInfiniteEffect(ref, effectIndex, effect, store);
        sendEffectSyncToSelf(ph, player, ctrl);
        player.sendMessage(Message.raw("[Cosmetic] Preset '" + presetLabel + "' applied (effect=" + effect.getId() + "). Use /cosmetic effect clear to remove."));
    }

    // ── Self-sync: send effect state directly to player ────────────────

    private void sendEffectSyncToSelf(PacketHandler ph, Player player, EffectControllerComponent ctrl) {
        try {
            EntityEffectUpdate[] updates = ctrl.createInitUpdates();
            if (updates == null || updates.length == 0) {
                // No active effects -- send empty update to clear client state
                updates = new EntityEffectUpdate[0];
            }

            EntityEffectsUpdate cu = new EntityEffectsUpdate(updates);

            EntityUpdate eu = new EntityUpdate(
                    player.getNetworkId(),
                    null, // no removed component types
                    new EntityEffectsUpdate[]{cu}
            );

            ph.writeNoCache(new EntityUpdates(null, new EntityUpdate[]{eu}));
        } catch (Exception e) {
            player.sendMessage(Message.raw("[Cosmetic] Warning: effect applied but visual sync failed: " + e.getMessage()));
        }
    }

    // ── Help ─────────────────────────────────────────────────────────────

    private void showHelp(Player player) {
        player.sendMessage(Message.raw("[Cosmetic] === Effects ===").color("#FFD700"));
        player.sendMessage(Message.raw("  /cosmetic effect <name> [duration] - Apply EntityEffect (0=infinite)"));
        player.sendMessage(Message.raw("  /cosmetic effect clear             - Clear all effects"));
        player.sendMessage(Message.raw("  /cosmetic effect purge             - Force-clear stuck effects (all slots)"));
        player.sendMessage(Message.raw("  /cosmetic listeffects [filter]     - List EntityEffect IDs"));
        player.sendMessage(Message.raw("[Cosmetic] === Cycle ===").color("#FFD700"));
        player.sendMessage(Message.raw("  /cosmetic cycle [filter] [delay]   - Auto-browse effects (default 4s)"));
        player.sendMessage(Message.raw("  /cosmetic cycle stop               - Stop cycling"));
        player.sendMessage(Message.raw("[Cosmetic] === Particles ===").color("#FFD700"));
        player.sendMessage(Message.raw("  /cosmetic particle <id> [scale] [hex] - Attach to player model"));
        player.sendMessage(Message.raw("  /cosmetic particle clear           - (no API - reconnect to clear)"));
        player.sendMessage(Message.raw("  /cosmetic listparticles [filter]   - List particle system IDs"));
        player.sendMessage(Message.raw("[Cosmetic] === VFX ===").color("#FFD700"));
        player.sendMessage(Message.raw("  /cosmetic listvfx [filter]         - List ModelVFX IDs"));
        player.sendMessage(Message.raw("[Cosmetic] === Presets ===").color("#FFD700"));
        player.sendMessage(Message.raw("  /cosmetic preset <name>            - Apply curated combo"));
        player.sendMessage(Message.raw("  Presets: fire, ice, gold, legendary, creative, intangible, heal"));
    }
}
