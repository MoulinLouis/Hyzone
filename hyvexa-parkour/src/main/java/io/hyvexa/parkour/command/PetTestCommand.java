package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.parkour.pet.PetManager;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Test command for pet NPCs.
 * /pet spawn [type] [scale]  - Spawn a pet (default: Kweebec_Seedling at 0.7x)
 * /pet despawn               - Remove your pet
 * /pet scale <float>         - Resize your pet
 * /pet type <npc_type>       - Change NPC type (respawns)
 * /pet types                 - List known NPC types
 */
public class PetTestCommand extends AbstractAsyncCommand {

    private static final String DEFAULT_TYPE = "Kweebec_Seedling";
    private static final float DEFAULT_SCALE = 0.7f;

    private static final String[] KNOWN_TYPES = {
        "Kweebec_Seedling", "Kweebec_Sapling", "Kweebec_Sproutling",
        "Kweebec_Sapling_Pink", "Kweebec_Razorleaf", "Kweebec_Rootling",
        "Zombie", "Zombie_Burnt", "Zombie_Frost", "Zombie_Sand",
        "Wolf_Outlander_Priest", "Wolf_Outlander_Sorcerer"
    };

    public PetTestCommand() {
        super("pet", "Pet test commands");
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

        PetManager petManager = PetManager.getInstance();
        if (petManager == null) {
            ctx.sendMessage(Message.raw("PetManager not initialized."));
            return CompletableFuture.completedFuture(null);
        }

        String[] args = CommandUtils.getArgs(ctx);
        if (args.length == 0) {
            sendHelp(ctx);
            return CompletableFuture.completedFuture(null);
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "spawn" -> handleSpawn(ctx, ref, player, petManager, args);
            case "despawn" -> handleDespawn(ctx, player, petManager);
            case "scale" -> handleScale(ctx, player, petManager, args);
            case "type" -> handleType(ctx, ref, player, petManager, args);
            case "types" -> handleTypes(ctx);
            default -> sendHelp(ctx);
        }

        return CompletableFuture.completedFuture(null);
    }

    private void handleSpawn(CommandContext ctx, Ref<EntityStore> ref, Player player,
                              PetManager petManager, String[] args) {
        String type = args.length > 1 ? args[1] : DEFAULT_TYPE;
        float scale = DEFAULT_SCALE;
        if (args.length > 2) {
            try {
                scale = Float.parseFloat(args[2]);
                scale = Math.max(0.1f, Math.min(5.0f, scale));
            } catch (NumberFormatException e) {
                ctx.sendMessage(Message.raw("Invalid scale. Usage: /pet spawn [type] [scale]"));
                return;
            }
        }

        petManager.spawnPet(player.getUuid(), ref, type, scale);
        ctx.sendMessage(Message.raw("Pet spawned! Type: " + type + ", Scale: " + scale));
        ctx.sendMessage(Message.raw("Try: /pet scale 0.5 | /pet type Zombie | /pet types"));
    }

    private void handleDespawn(CommandContext ctx, Player player, PetManager petManager) {
        if (!petManager.hasPet(player.getUuid())) {
            ctx.sendMessage(Message.raw("You don't have a pet."));
            return;
        }
        petManager.despawnPet(player.getUuid());
        ctx.sendMessage(Message.raw("Pet despawned."));
    }

    private void handleScale(CommandContext ctx, Player player, PetManager petManager, String[] args) {
        if (!petManager.hasPet(player.getUuid())) {
            ctx.sendMessage(Message.raw("You don't have a pet. Use /pet spawn first."));
            return;
        }
        if (args.length < 2) {
            ctx.sendMessage(Message.raw("Usage: /pet scale <0.1-5.0>"));
            return;
        }

        try {
            float scale = Float.parseFloat(args[1]);
            scale = Math.max(0.1f, Math.min(5.0f, scale));
            petManager.setScale(player.getUuid(), scale);
            ctx.sendMessage(Message.raw("Pet scale set to " + scale));
        } catch (NumberFormatException e) {
            ctx.sendMessage(Message.raw("Invalid scale. Usage: /pet scale <0.1-5.0>"));
        }
    }

    private void handleType(CommandContext ctx, Ref<EntityStore> ref, Player player,
                             PetManager petManager, String[] args) {
        if (args.length < 2) {
            ctx.sendMessage(Message.raw("Usage: /pet type <npc_type>. See /pet types for list."));
            return;
        }

        String type = args[1];
        PetManager.PetState state = petManager.getPetState(player.getUuid());
        float scale = state != null ? state.scale : DEFAULT_SCALE;
        petManager.respawn(player.getUuid(), ref, type, scale);
        ctx.sendMessage(Message.raw("Pet respawned as: " + type));
    }

    private void handleTypes(CommandContext ctx) {
        StringBuilder sb = new StringBuilder("Known NPC types:\n");
        for (String type : KNOWN_TYPES) {
            sb.append("  - ").append(type).append("\n");
        }
        sb.append("You can try any NPC role name, these are just the known ones.");
        ctx.sendMessage(Message.raw(sb.toString()));
    }

    private void sendHelp(CommandContext ctx) {
        ctx.sendMessage(Message.raw(
            "/pet spawn [type] [scale] - Spawn a pet\n" +
            "/pet despawn - Remove your pet\n" +
            "/pet scale <0.1-5.0> - Resize\n" +
            "/pet type <npc_type> - Change type (respawns)\n" +
            "/pet types - List known types"
        ));
    }
}
