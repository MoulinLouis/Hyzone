package io.hyvexa.purge.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import io.hyvexa.common.util.CommandUtils;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Test command: /setammo <maxAmmo>
 * Sets Hyguns_MaxAmmo and Hyguns_Ammo on the weapon in hotbar slot 0.
 * Use this to verify that Hyguns respects ItemStack metadata overrides.
 */
public class SetAmmoCommand extends AbstractAsyncCommand {

    private static final short SLOT_WEAPON = 0;

    public SetAmmoCommand() {
        super("setammo", "Set max ammo on held Hyguns weapon");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            ctx.sendMessage(Message.raw("Player only."));
            return CompletableFuture.completedFuture(null);
        }

        String[] args = CommandUtils.getArgs(ctx);
        if (args.length < 1) {
            player.sendMessage(Message.raw("Usage: /setammo <maxAmmo>"));
            return CompletableFuture.completedFuture(null);
        }

        int newMax;
        try {
            newMax = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Invalid number: " + args[0]));
            return CompletableFuture.completedFuture(null);
        }

        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            player.sendMessage(Message.raw("No inventory."));
            return CompletableFuture.completedFuture(null);
        }

        ItemStack weapon = inventory.getHotbar().getItemStack(SLOT_WEAPON);
        if (weapon == null || weapon.isEmpty()) {
            player.sendMessage(Message.raw("No item in slot 0."));
            return CompletableFuture.completedFuture(null);
        }

        // Read current values
        String itemId = weapon.getItemId();
        Integer oldMaxAmmo = com.thescar.hygunsplugin.ItemStackUtils.getCustomInt(weapon, "Hyguns_MaxAmmo");
        Integer oldAmmo = com.thescar.hygunsplugin.ItemStackUtils.getCustomInt(weapon, "Hyguns_Ammo");
        Integer registryMax = com.thescar.hygunsplugin.GunRegistry.getDefaultMaxAmmo(itemId);
        boolean isGun = com.thescar.hygunsplugin.GunRegistry.isGunItem(itemId);

        player.sendMessage(Message.raw("[setammo] Item: " + itemId
                + " | isGun: " + isGun
                + " | registryMax: " + registryMax
                + " | meta MaxAmmo: " + oldMaxAmmo
                + " | meta Ammo: " + oldAmmo));

        // Set new values
        ItemStack modified = com.thescar.hygunsplugin.ItemStackUtils.setCustomInt(weapon, "Hyguns_MaxAmmo", newMax);
        modified = com.thescar.hygunsplugin.ItemStackUtils.setCustomInt(modified, "Hyguns_Ammo", newMax);

        inventory.getHotbar().setItemStackForSlot(SLOT_WEAPON, modified, false);

        // Verify the write
        ItemStack verify = inventory.getHotbar().getItemStack(SLOT_WEAPON);
        Integer verifyMax = verify != null
                ? com.thescar.hygunsplugin.ItemStackUtils.getCustomInt(verify, "Hyguns_MaxAmmo") : null;
        Integer verifyAmmo = verify != null
                ? com.thescar.hygunsplugin.ItemStackUtils.getCustomInt(verify, "Hyguns_Ammo") : null;

        player.sendMessage(Message.raw("[setammo] SET -> MaxAmmo=" + newMax
                + " | VERIFY read-back: MaxAmmo=" + verifyMax + " Ammo=" + verifyAmmo));

        return CompletableFuture.completedFuture(null);
    }
}
