package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.parkour.ParkourConstants;
import io.hyvexa.parkour.util.ParkourModeGate;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

import static com.hypixel.hytale.server.core.command.commands.player.inventory.InventorySeeCommand.MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD;

public class ParkourItemCommand extends AbstractAsyncCommand {

    public ParkourItemCommand() {
        super("pkitem", "Give the parkour menu block.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        if (!(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(null);
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            commandContext.sendMessage(MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
            return CompletableFuture.completedFuture(null);
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        return CompletableFuture.runAsync(() -> {
            giveItem(commandContext, store, ref, world);
        }, world);
    }

    private void giveItem(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if (ParkourModeGate.denyIfNotParkour(ctx, world)) {
            return;
        }
        var inventory = player.getInventory();
        if (inventory == null) {
            ctx.sendMessage(Message.raw("Inventory not available."));
            return;
        }
        var hotbar = inventory.getHotbar();
        if (hotbar == null) {
            ctx.sendMessage(Message.raw("Hotbar not available."));
            return;
        }
        hotbar.setItemStackForSlot((short) 0, new ItemStack(ParkourConstants.ITEM_MENU, 1));
        hotbar.setItemStackForSlot((short) 1, new ItemStack(ParkourConstants.ITEM_LEADERBOARD, 1));
        hotbar.setItemStackForSlot((short) 2, new ItemStack(ParkourConstants.ITEM_STATS, 1));
        hotbar.setItemStackForSlot((short) 3, new ItemStack(ParkourConstants.ITEM_ADMIN_REMOTE, 1));
        hotbar.setItemStackForSlot((short) 8, new ItemStack(ParkourConstants.ITEM_HUB_MENU, 1));
        player.sendMessage(Message.raw("Parkour items added to your inventory."));
    }
}
