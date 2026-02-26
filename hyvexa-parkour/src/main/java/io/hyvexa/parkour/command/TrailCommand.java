package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.AsyncExecutionHelper;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.core.trail.TrailManager;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * OP-only R&D command to prototype particle trails behind players as they move.
 * Uses TrailManager to spawn repeating SpawnParticleSystem packets at the player's feet.
 */
public class TrailCommand extends AbstractAsyncCommand {

    private static final Map<String, TrailPreset> PRESETS = new LinkedHashMap<>();

    static {
        // Custom trail particles (short lifespan, tight area)
        PRESETS.put("fireflies", new TrailPreset("Trail/Trail_Fireflies", 0.5f, 200));
        PRESETS.put("sparks", new TrailPreset("Trail/Trail_Sparks", 0.8f, 150));
        PRESETS.put("embers", new TrailPreset("Trail/Trail_Embers", 0.5f, 120));
        PRESETS.put("fire", new TrailPreset("Trail/Trail_Flames", 0.6f, 100));
        PRESETS.put("frost", new TrailPreset("Trail/Trail_Frost", 0.6f, 150));
        // Vanilla particles (longer lifespan, bigger spread)
        PRESETS.put("spiral", new TrailPreset("Azure_Spiral", 0.8f, 300));
        PRESETS.put("heal", new TrailPreset("Aura_Heal", 0.6f, 250));
        PRESETS.put("gold", new TrailPreset("Firework_GS", 0.5f, 200));
    }

    public TrailCommand() {
        super("trail", "Trail test commands");
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
            String[] args = CommandUtils.getArgs(ctx);
            if (args.length == 0) {
                showHelp(player);
                return;
            }

            switch (args[0].toLowerCase()) {
                case "start" -> handleStart(player, ref, store, world, args);
                case "stop" -> handleStop(player);
                case "list" -> handleList(player, args);
                case "preset" -> handlePreset(player, ref, store, world, args);
                case "help" -> showHelp(player);
                default -> player.sendMessage(Message.raw("[Trail] Unknown: " + args[0] + ". Use /trail help"));
            }
        }, world).exceptionally(ex -> {
            AsyncExecutionHelper.logThrottledWarning("trail.execute", "trail command execution",
                    "player=" + player.getNetworkId(), ex);
            return null;
        });
    }

    // -- Start subcommand -------------------------------------------------

    private void handleStart(Player player, Ref<EntityStore> ref, Store<EntityStore> store,
                             World world, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw("[Trail] Usage: /trail start <particleId> [scale] [intervalMs]"));
            return;
        }

        String particleId = args[1];
        float scale = 0.5f;
        long intervalMs = 150;

        if (args.length > 2) {
            try { scale = Float.parseFloat(args[2]); } catch (NumberFormatException ignored) {}
        }
        if (args.length > 3) {
            try { intervalMs = Long.parseLong(args[3]); } catch (NumberFormatException ignored) {}
        }
        scale = Math.max(0.1f, Math.min(5.0f, scale));
        intervalMs = Math.max(50, Math.min(2000, intervalMs));

        TrailManager.getInstance().startTrail(player.getUuid(), ref, store, world, particleId, scale, intervalMs);
        player.sendMessage(Message.raw("[Trail] Started trail: " + particleId + " (scale=" + scale + ", interval=" + intervalMs + "ms)").color("#FFD700"));
    }

    // -- Stop subcommand --------------------------------------------------

    private void handleStop(Player player) {
        if (!TrailManager.getInstance().hasTrail(player.getUuid())) {
            player.sendMessage(Message.raw("[Trail] No active trail."));
            return;
        }
        TrailManager.getInstance().stopTrail(player.getUuid());
        player.sendMessage(Message.raw("[Trail] Trail stopped.").color("#FFD700"));
    }

    // -- List subcommand --------------------------------------------------

    private void handleList(Player player, String[] args) {
        String filter = args.length > 1 ? args[1].toLowerCase() : "";
        var map = ParticleSystem.getAssetMap().getAssetMap();
        int count = 0;
        int max = 30;

        player.sendMessage(Message.raw("[Trail] Particle systems" + (filter.isEmpty() ? "" : " matching '" + filter + "'") + ":").color("#FFD700"));
        for (String key : map.keySet()) {
            if (!filter.isEmpty() && !key.toLowerCase().contains(filter)) continue;
            player.sendMessage(Message.raw("  " + key));
            count++;
            if (count >= max) {
                player.sendMessage(Message.raw("  ... and more. Use /trail list <filter>"));
                break;
            }
        }
        if (count == 0) {
            player.sendMessage(Message.raw("  (none found)"));
        }
    }

    // -- Preset subcommand ------------------------------------------------

    private void handlePreset(Player player, Ref<EntityStore> ref, Store<EntityStore> store,
                              World world, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw("[Trail] Usage: /trail preset <name>"));
            player.sendMessage(Message.raw("[Trail] Presets: " + String.join(", ", PRESETS.keySet())));
            return;
        }

        String name = args[1].toLowerCase();
        TrailPreset preset = PRESETS.get(name);
        if (preset == null) {
            player.sendMessage(Message.raw("[Trail] Unknown preset: " + name));
            player.sendMessage(Message.raw("[Trail] Presets: " + String.join(", ", PRESETS.keySet())));
            return;
        }

        TrailManager.getInstance().startTrail(player.getUuid(), ref, store, world,
                preset.particleId, preset.scale, preset.intervalMs);
        player.sendMessage(Message.raw("[Trail] Preset '" + name + "' active: " + preset.particleId
                + " (scale=" + preset.scale + ", interval=" + preset.intervalMs + "ms)").color("#FFD700"));
    }

    // -- Help -------------------------------------------------------------

    private void showHelp(Player player) {
        player.sendMessage(Message.raw("[Trail] === Trail Commands ===").color("#FFD700"));
        player.sendMessage(Message.raw("  /trail start <particleId> [scale] [intervalMs]"));
        player.sendMessage(Message.raw("    Start a trail (default: scale=0.5, interval=150ms)"));
        player.sendMessage(Message.raw("  /trail stop"));
        player.sendMessage(Message.raw("    Stop your active trail"));
        player.sendMessage(Message.raw("  /trail list [filter]"));
        player.sendMessage(Message.raw("    List available particle system IDs"));
        player.sendMessage(Message.raw("  /trail preset <name>"));
        player.sendMessage(Message.raw("    Apply a curated preset"));
        player.sendMessage(Message.raw("  Presets: " + String.join(", ", PRESETS.keySet())));
    }

    // -- Preset data ------------------------------------------------------

    private record TrailPreset(String particleId, float scale, long intervalMs) {}
}
