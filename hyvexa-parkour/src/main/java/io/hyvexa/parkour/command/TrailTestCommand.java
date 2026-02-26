package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.EntityEffectUpdate;
import com.hypixel.hytale.protocol.EntityEffectsUpdate;
import com.hypixel.hytale.protocol.EntityPart;
import com.hypixel.hytale.protocol.EntityUpdate;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.ModelParticle;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.trail.config.Trail;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.AsyncExecutionHelper;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.core.trail.TrailManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OP-only R&D command to validate trail-like visuals before production integration.
 */
public class TrailTestCommand extends AbstractAsyncCommand {

    private static final Set<Integer> activeCycles = ConcurrentHashMap.newKeySet();
    private static final List<String> PRESET_ORDER = List.of("dash", "arcane", "gold", "orb", "fire", "lunge");
    private static final Set<String> DANGEROUS_EFFECTS = Set.of("status", "food", "burn", "freeze", "poison");

    public TrailTestCommand() {
        super("trailtest", "Trail visual test commands");
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
                player.sendMessage(Message.raw("[TrailTest] No packet handler."));
                return;
            }

            String[] args = CommandUtils.getArgs(ctx);
            if (args.length == 0) {
                showHelp(player);
                return;
            }

            switch (args[0].toLowerCase()) {
                case "list" -> handleList(player, args);
                case "apply" -> handleApply(player, ph, ref, store, world, args);
                case "preset" -> handlePreset(player, ph, ref, store, world, args);
                case "cycle" -> handleCycle(player, ph, ref, store, world, args);
                case "clear" -> handleClear(player, ph, ref, store, world, args);
                case "help" -> showHelp(player);
                default -> player.sendMessage(Message.raw("[TrailTest] Unknown: " + args[0] + ". Use /trailtest help"));
            }
        }, world).exceptionally(ex -> {
            AsyncExecutionHelper.logThrottledWarning("trailtest.execute", "trailtest command execution",
                    "player=" + player.getNetworkId(), ex);
            return null;
        });
    }

    // -- List subcommand -------------------------------------------------

    private void handleList(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw("[TrailTest] Usage: /trailtest list <trails|particles|effects> [filter]"));
            return;
        }

        String type = args[1].toLowerCase();
        String filter = args.length > 2 ? args[2].toLowerCase() : "";
        switch (type) {
            case "trails" -> listTrails(player, filter);
            case "particles" -> listParticles(player, filter);
            case "effects" -> listEffects(player, filter);
            default -> player.sendMessage(Message.raw("[TrailTest] Usage: /trailtest list <trails|particles|effects> [filter]"));
        }
    }

    private void listTrails(Player player, String filter) {
        try {
            var map = Trail.getAssetMap().getAssetMap();
            int count = 0;
            int max = 30;
            player.sendMessage(Message.raw("[TrailTest] Trails" + (filter.isEmpty() ? "" : " matching '" + filter + "'") + ":").color("#FFD700"));
            for (String key : map.keySet()) {
                if (!filter.isEmpty() && !key.toLowerCase().contains(filter)) continue;
                player.sendMessage(Message.raw("  " + key));
                count++;
                if (count >= max) {
                    player.sendMessage(Message.raw("  ... and more. Use a tighter filter."));
                    break;
                }
            }
            if (count == 0) {
                player.sendMessage(Message.raw("  (none found)"));
            }
        } catch (Exception e) {
            player.sendMessage(Message.raw("[TrailTest] Error listing trails: " + e.getMessage()));
        }
    }

    private void listParticles(Player player, String filter) {
        try {
            var map = ParticleSystem.getAssetMap().getAssetMap();
            int count = 0;
            int max = 30;
            player.sendMessage(Message.raw("[TrailTest] Particle systems" + (filter.isEmpty() ? "" : " matching '" + filter + "'") + ":").color("#FFD700"));
            for (String key : map.keySet()) {
                if (!filter.isEmpty() && !key.toLowerCase().contains(filter)) continue;
                player.sendMessage(Message.raw("  " + key));
                count++;
                if (count >= max) {
                    player.sendMessage(Message.raw("  ... and more. Use a tighter filter."));
                    break;
                }
            }
            if (count == 0) {
                player.sendMessage(Message.raw("  (none found)"));
            }
        } catch (Exception e) {
            player.sendMessage(Message.raw("[TrailTest] Error listing particles: " + e.getMessage()));
        }
    }

    private void listEffects(Player player, String filter) {
        try {
            var map = EntityEffect.getAssetMap().getAssetMap();
            int count = 0;
            int max = 30;
            player.sendMessage(Message.raw("[TrailTest] Effects" + (filter.isEmpty() ? "" : " matching '" + filter + "'") + ":").color("#FFD700"));
            for (String key : map.keySet()) {
                if (!filter.isEmpty() && !key.toLowerCase().contains(filter)) continue;
                player.sendMessage(Message.raw("  " + key));
                count++;
                if (count >= max) {
                    player.sendMessage(Message.raw("  ... and more. Use a tighter filter."));
                    break;
                }
            }
            if (count == 0) {
                player.sendMessage(Message.raw("  (none found)"));
            }
        } catch (Exception e) {
            player.sendMessage(Message.raw("[TrailTest] Error listing effects: " + e.getMessage()));
        }
    }

    // -- Apply subcommand ------------------------------------------------

    private void handleApply(Player player, PacketHandler ph, Ref<EntityStore> ref, Store<EntityStore> store,
                             World world, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Message.raw("[TrailTest] Usage: /trailtest apply <effect|particle> ..."));
            return;
        }

        String type = args[1].toLowerCase();
        switch (type) {
            case "effect" -> {
                String effectId = args[2];
                float duration = 1.5f;
                if (args.length > 3) {
                    try {
                        duration = Float.parseFloat(args[3]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                duration = Math.max(0.05f, Math.min(20f, duration));
                applyTimedEffect(player, ph, ref, store, effectId, duration);
            }
            case "particle" -> {
                String systemId = args[2];
                float scale = 1.0f;
                Color color = null;
                float xOffset = 0.0f;
                float yOffset = 0.5f;
                float zOffset = 0.0f;
                if (args.length > 3) {
                    try {
                        scale = Float.parseFloat(args[3]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (args.length > 4) {
                    color = parseHexColor(args[4]);
                }
                if (args.length == 6) {
                    // Backwards-compatible syntax: [yOffset]
                    try {
                        yOffset = Float.parseFloat(args[5]);
                    } catch (NumberFormatException ignored) {
                    }
                } else if (args.length > 6) {
                    // Extended syntax: [xOffset] [yOffset] [zOffset]
                    try {
                        xOffset = Float.parseFloat(args[5]);
                    } catch (NumberFormatException ignored) {
                    }
                    try {
                        yOffset = Float.parseFloat(args[6]);
                    } catch (NumberFormatException ignored) {
                    }
                    if (args.length > 7) {
                        try {
                            zOffset = Float.parseFloat(args[7]);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                applyBroadcastParticle(player, world, systemId, scale, color, xOffset, yOffset, zOffset);
            }
            default -> player.sendMessage(Message.raw("[TrailTest] Usage: /trailtest apply <effect|particle> ..."));
        }
    }

    private void applyTimedEffect(Player player, PacketHandler ph, Ref<EntityStore> ref, Store<EntityStore> store,
                                  String effectName, float durationSeconds) {
        EntityEffect effect = resolveEffect(effectName);
        if (effect == null) {
            player.sendMessage(Message.raw("[TrailTest] Effect '" + effectName + "' not found. Use /trailtest list effects"));
            return;
        }

        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl == null) {
            player.sendMessage(Message.raw("[TrailTest] Player has no EffectControllerComponent."));
            return;
        }

        int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
        if (effectIndex < 0) {
            player.sendMessage(Message.raw("[TrailTest] Effect '" + effect.getId() + "' has no asset index."));
            return;
        }

        ctrl.addEffect(ref, effectIndex, effect, durationSeconds, OverlapBehavior.OVERWRITE, store);
        sendEffectSyncToSelf(ph, player, ctrl);
        player.sendMessage(Message.raw("[TrailTest] Applied effect '" + effect.getId() + "' (" + durationSeconds + "s)."));
    }

    private void applyBroadcastParticle(Player player, World world, String systemId, float scale, Color color,
                                        float xOffset, float yOffset, float zOffset) {
        String resolved = resolveParticleSystem(systemId);
        if (resolved == null) {
            player.sendMessage(Message.raw("[TrailTest] Particle system '" + systemId + "' not found. Use /trailtest list particles"));
            return;
        }

        ModelParticle mp = new ModelParticle(
                resolved,
                scale,
                color,
                EntityPart.Entity,
                null,
                new Vector3f(xOffset, yOffset, zOffset),
                null,
                false
        );

        int viewers = broadcastModelParticle(world, player.getNetworkId(), mp);
        player.sendMessage(Message.raw("[TrailTest] Broadcast model particle '" + resolved + "' to " + viewers + " viewer(s)."));
    }

    // -- Presets ---------------------------------------------------------

    private void handlePreset(Player player, PacketHandler ph, Ref<EntityStore> ref, Store<EntityStore> store,
                              World world,
                              String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw("[TrailTest] Usage: /trailtest preset <dash|arcane|gold|orb|fire|lunge>"));
            return;
        }

        String name = args[1].toLowerCase();
        if (!name.equals("gold")) {
            TrailManager.getInstance().stopTrail(player.getUuid());
        }
        switch (name) {
            case "dash" -> applyTimedEffect(player, ph, ref, store, "Dagger_Dash", 0.35f);
            case "arcane" -> applyTimedEffect(player, ph, ref, store, "Intangible_Dark", 2.0f);
            case "gold" -> {
                // Match existing /trail preset gold behavior exactly.
                TrailManager.getInstance().startTrail(player.getUuid(), ref, store, world, "Firework_GS", 0.5f, 200);
                player.sendMessage(Message.raw("[TrailTest] Preset 'gold' active: Firework_GS (scale=0.5, interval=200ms)").color("#FFD700"));
            }
            case "orb" -> applyTimedEffect(player, ph, ref, store, "Drop_Legendary", 2.0f);
            case "fire" -> applyBroadcastParticle(player, world, "Fire_AoE", 0.6f, null, 0.0f, 0.1f, 0.0f);
            case "lunge" -> applyBroadcastParticle(player, world, "Daggers_Lunge_Trail", 1.0f, null, 0.45f, 0.5f, 0.0f);
            default -> player.sendMessage(Message.raw("[TrailTest] Unknown preset '" + name + "'. Use: dash, arcane, gold, orb, fire, lunge"));
        }
    }

    // -- Cycle subcommand ------------------------------------------------

    private void handleCycle(Player player, PacketHandler ph, Ref<EntityStore> ref, Store<EntityStore> store,
                             World world, String[] args) {
        int networkId = player.getNetworkId();

        if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
            if (activeCycles.remove(networkId)) {
                player.sendMessage(Message.raw("[TrailTest] Cycle stopping..."));
            } else {
                player.sendMessage(Message.raw("[TrailTest] No cycle running."));
            }
            return;
        }

        if (activeCycles.contains(networkId)) {
            player.sendMessage(Message.raw("[TrailTest] Cycle already running. Use /trailtest cycle stop"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Message.raw("[TrailTest] Usage: /trailtest cycle <preset|effect|particle> [filter] [delaySec]"));
            return;
        }

        String mode = args[1].toLowerCase();
        String filter = args.length > 2 ? args[2].toLowerCase() : "";
        int delaySec = 3;
        if (args.length > 3) {
            try {
                delaySec = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
            }
        } else if (args.length > 2) {
            try {
                delaySec = Integer.parseInt(args[2]);
                filter = "";
            } catch (NumberFormatException ignored) {
            }
        }
        delaySec = Math.max(1, Math.min(20, delaySec));
        final int cycleDelaySec = delaySec;

        List<String> items = resolveCycleItems(mode, filter);
        if (items.isEmpty()) {
            player.sendMessage(Message.raw("[TrailTest] No entries found for cycle mode '" + mode + "'."));
            return;
        }

        activeCycles.add(networkId);
        player.sendMessage(Message.raw("[TrailTest] Cycling " + items.size() + " " + mode + " entries every " + cycleDelaySec + "s. Use /trailtest cycle stop").color("#FFD700"));
        int delayMs = cycleDelaySec * 1000;

        Thread cycleThread = new Thread(() -> {
            try {
                for (int i = 0; i < items.size(); i++) {
                    if (!activeCycles.contains(networkId) || !ref.isValid()) break;
                    final String item = items.get(i);
                    final int num = i + 1;
                    CompletableFuture.runAsync(() -> {
                        if (!activeCycles.contains(networkId) || !ref.isValid()) return;
                        switch (mode) {
                            case "preset" -> handlePreset(player, ph, ref, store, world, new String[]{"preset", item});
                            case "effect" -> applyTimedEffect(player, ph, ref, store, item, Math.max(0.25f, cycleDelaySec - 0.1f));
                            case "particle" -> applyBroadcastParticle(player, world, item, 1.0f, null, 0.0f, 0.5f, 0.0f);
                            default -> {
                            }
                        }
                        player.sendMessage(Message.raw("[TrailTest] [" + num + "/" + items.size() + "] " + mode + " -> " + item).color("#FFD700"));
                    }, world).join();
                    Thread.sleep(delayMs);
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                AsyncExecutionHelper.logThrottledWarning("trailtest.cycle", "trailtest cycle",
                        "player=" + networkId + ", mode=" + mode, e);
            } finally {
                if (activeCycles.remove(networkId)) {
                    CompletableFuture.runAsync(() -> {
                        if (!ref.isValid()) return;
                        clearEffectsForPlayer(player, ph, ref, store);
                        player.sendMessage(Message.raw("[TrailTest] Cycle complete.").color("#FFD700"));
                    }, world);
                }
            }
        }, "trailtest-cycle-" + networkId);
        cycleThread.setDaemon(true);
        cycleThread.start();
    }

    private List<String> resolveCycleItems(String mode, String filter) {
        List<String> items = new ArrayList<>();
        switch (mode) {
            case "preset" -> {
                for (String preset : PRESET_ORDER) {
                    if (!filter.isEmpty() && !preset.contains(filter)) continue;
                    items.add(preset);
                }
            }
            case "effect" -> {
                for (Map.Entry<String, EntityEffect> entry : EntityEffect.getAssetMap().getAssetMap().entrySet()) {
                    String key = entry.getKey();
                    if (!filter.isEmpty() && !key.toLowerCase().contains(filter)) continue;
                    if (isDangerousEffect(key)) continue;
                    items.add(key);
                    if (items.size() >= 30) break;
                }
            }
            case "particle" -> {
                for (String key : ParticleSystem.getAssetMap().getAssetMap().keySet()) {
                    if (!filter.isEmpty() && !key.toLowerCase().contains(filter)) continue;
                    items.add(key);
                    if (items.size() >= 30) break;
                }
            }
            default -> {
            }
        }
        return items;
    }

    // -- Clear subcommand ------------------------------------------------

    private void handleClear(Player player, PacketHandler ph, Ref<EntityStore> ref, Store<EntityStore> store,
                             World world, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw("[TrailTest] Usage: /trailtest clear <effects|session|all>"));
            return;
        }

        String mode = args[1].toLowerCase();
        switch (mode) {
            case "effects" -> clearEffectsForPlayer(player, ph, ref, store);
            case "session" -> {
                activeCycles.remove(player.getNetworkId());
                TrailManager.getInstance().stopTrail(player.getUuid());
                clearEffectsForPlayer(player, ph, ref, store);
                player.sendMessage(Message.raw("[TrailTest] Session cleared. Note: model particles may require reconnect to fully clear."));
            }
            case "all" -> {
                activeCycles.clear();
                stopTrailsForWorldPlayers(world);
                int cleared = clearEffectsForWorldPlayers(world);
                player.sendMessage(Message.raw("[TrailTest] Cleared active cycles and effects for " + cleared + " player(s) in this world."));
                player.sendMessage(Message.raw("[TrailTest] Note: model particles may require reconnect to fully clear."));
            }
            default -> player.sendMessage(Message.raw("[TrailTest] Usage: /trailtest clear <effects|session|all>"));
        }
    }

    private void clearEffectsForPlayer(Player player, PacketHandler ph, Ref<EntityStore> ref, Store<EntityStore> store) {
        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl == null) return;
        ctrl.clearEffects(ref, store);
        sendEffectSyncToSelf(ph, player, ctrl);
    }

    private int clearEffectsForWorldPlayers(World world) {
        int count = 0;
        for (PlayerRef targetRef : Universe.get().getPlayers()) {
            if (targetRef == null || !targetRef.isValid()) continue;
            Ref<EntityStore> targetEntityRef = targetRef.getReference();
            if (targetEntityRef == null || !targetEntityRef.isValid()) continue;
            Store<EntityStore> targetStore = targetEntityRef.getStore();
            World targetWorld = targetStore.getExternalData().getWorld();
            if (targetWorld != world) continue;

            EffectControllerComponent ctrl = targetStore.getComponent(targetEntityRef, EffectControllerComponent.getComponentType());
            Player targetPlayer = targetStore.getComponent(targetEntityRef, Player.getComponentType());
            if (ctrl == null || targetPlayer == null) continue;

            ctrl.clearEffects(targetEntityRef, targetStore);
            sendEffectSyncToSelf(targetRef.getPacketHandler(), targetPlayer, ctrl);
            count++;
        }
        return count;
    }

    private void stopTrailsForWorldPlayers(World world) {
        for (PlayerRef targetRef : Universe.get().getPlayers()) {
            if (targetRef == null || !targetRef.isValid()) continue;
            Ref<EntityStore> targetEntityRef = targetRef.getReference();
            if (targetEntityRef == null || !targetEntityRef.isValid()) continue;
            Store<EntityStore> targetStore = targetEntityRef.getStore();
            World targetWorld = targetStore.getExternalData().getWorld();
            if (targetWorld != world) continue;
            TrailManager.getInstance().stopTrail(targetRef.getUuid());
        }
    }

    // -- Shared helpers --------------------------------------------------

    private int broadcastModelParticle(World world, int entityId, ModelParticle mp) {
        int viewers = 0;
        for (PlayerRef viewer : Universe.get().getPlayers()) {
            if (viewer == null || !viewer.isValid()) continue;
            Ref<EntityStore> viewerRef = viewer.getReference();
            if (viewerRef == null || !viewerRef.isValid()) continue;
            Store<EntityStore> viewerStore = viewerRef.getStore();
            World viewerWorld = viewerStore.getExternalData().getWorld();
            if (viewerWorld != world) continue;
            PacketHandler viewerPh = viewer.getPacketHandler();
            if (viewerPh == null) continue;
            viewerPh.writeNoCache(new SpawnModelParticles(entityId, new ModelParticle[]{mp}));
            viewers++;
        }
        return viewers;
    }

    private EntityEffect resolveEffect(String name) {
        var map = EntityEffect.getAssetMap().getAssetMap();
        EntityEffect effect = map.get(name);
        if (effect != null) return effect;

        String nameLower = name.toLowerCase();
        for (Map.Entry<String, EntityEffect> entry : map.entrySet()) {
            String key = entry.getKey();
            String keyLower = key.toLowerCase();
            if (keyLower.equals(nameLower) || keyLower.endsWith("/" + nameLower) || keyLower.endsWith(nameLower)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isDangerousEffect(String key) {
        String lower = key.toLowerCase();
        for (String keyword : DANGEROUS_EFFECTS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    private String resolveParticleSystem(String name) {
        var map = ParticleSystem.getAssetMap().getAssetMap();
        if (map.containsKey(name)) return name;

        String nameLower = name.toLowerCase();
        for (String key : map.keySet()) {
            String keyLower = key.toLowerCase();
            if (keyLower.equals(nameLower) || keyLower.endsWith("/" + nameLower) || keyLower.endsWith(nameLower)) {
                return key;
            }
        }
        return null;
    }

    private Color parseHexColor(String hex) {
        try {
            if (hex == null || hex.isBlank()) return null;
            if (hex.startsWith("#")) hex = hex.substring(1);
            int rgb = Integer.parseInt(hex, 16);
            return new Color((byte) ((rgb >> 16) & 0xFF), (byte) ((rgb >> 8) & 0xFF), (byte) (rgb & 0xFF));
        } catch (Exception e) {
            return null;
        }
    }

    private void sendEffectSyncToSelf(PacketHandler ph, Player player, EffectControllerComponent ctrl) {
        if (ph == null) return;
        try {
            EntityEffectUpdate[] updates = ctrl.createInitUpdates();
            if (updates == null) {
                updates = new EntityEffectUpdate[0];
            }

            EntityEffectsUpdate cu = new EntityEffectsUpdate(updates);
            EntityUpdate eu = new EntityUpdate(player.getNetworkId(), null, new EntityEffectsUpdate[]{cu});
            ph.writeNoCache(new EntityUpdates(null, new EntityUpdate[]{eu}));
        } catch (Exception e) {
            player.sendMessage(Message.raw("[TrailTest] Warning: effect sync failed: " + e.getMessage()));
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(Message.raw("[TrailTest] === List ===").color("#FFD700"));
        player.sendMessage(Message.raw("  /trailtest list trails [filter]"));
        player.sendMessage(Message.raw("  /trailtest list particles [filter]"));
        player.sendMessage(Message.raw("  /trailtest list effects [filter]"));
        player.sendMessage(Message.raw("[TrailTest] === Apply ===").color("#FFD700"));
        player.sendMessage(Message.raw("  /trailtest apply effect <id> [durationSec]"));
        player.sendMessage(Message.raw("  /trailtest apply particle <id> [scale] [hexColor] [yOffset]"));
        player.sendMessage(Message.raw("  /trailtest apply particle <id> [scale] [hexColor] [xOffset] [yOffset] [zOffset]"));
        player.sendMessage(Message.raw("[TrailTest] === Presets ===").color("#FFD700"));
        player.sendMessage(Message.raw("  /trailtest preset <dash|arcane|gold|orb|fire|lunge>"));
        player.sendMessage(Message.raw("[TrailTest] === Cycle ===").color("#FFD700"));
        player.sendMessage(Message.raw("  /trailtest cycle <preset|effect|particle> [filter] [delaySec]"));
        player.sendMessage(Message.raw("  /trailtest cycle stop"));
        player.sendMessage(Message.raw("[TrailTest] === Clear ===").color("#FFD700"));
        player.sendMessage(Message.raw("  /trailtest clear effects"));
        player.sendMessage(Message.raw("  /trailtest clear session"));
        player.sendMessage(Message.raw("  /trailtest clear all"));
        player.sendMessage(Message.raw("[TrailTest] Note: model particles are broadcast to players in the same world."));
        player.sendMessage(Message.raw("[TrailTest] Note: model particle cleanup is limited by API and may require reconnect."));
    }
}
