package io.hyvexa.ascend.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.hud.ToastType;
import io.hyvexa.ascend.util.AscendModeGate;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /cat <token> - Hidden easter egg command triggered via NPC dialog buttons.
 * Each token corresponds to a hidden cat NPC placed in the Ascend world.
 */
public class CatCommand extends AbstractAsyncCommand {

    private static final Map<String, String> VALID_CATS = Map.of(
        "WHK", "Whiskers",
        "PUR", "Shadow",
        "MRW", "Marble",
        "FLF", "Fluffball",
        "NKO", "Neko"
    );

    public CatCommand() {
        super("cat", "Interact with a cat");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(null);
        }

        String[] args = ctx.args();
        if (args.length < 1) {
            return CompletableFuture.completedFuture(null);
        }

        String token = args[0].toUpperCase();
        if (!VALID_CATS.containsKey(token)) {
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
            if (playerRef == null) {
                return;
            }
            if (AscendModeGate.denyIfNotAscend(ctx, world)) {
                return;
            }

            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin == null || plugin.getPlayerStore() == null) {
                return;
            }

            AscendPlayerStore playerStore = plugin.getPlayerStore();
            UUID playerId = playerRef.getUuid();
            AscendPlayerProgress progress = playerStore.getPlayer(playerId);
            if (progress == null) {
                return;
            }

            if (progress.hasFoundCat(token)) {
                AscendHudManager hm = plugin.getHudManager();
                if (hm != null) {
                    hm.showToast(playerId, ToastType.INFO, "You already found this cat!");
                }
                return;
            }

            progress.addFoundCat(token);
            playerStore.markDirty(playerId);

            int found = progress.getFoundCatCount();
            AscendHudManager hm = plugin.getHudManager();
            if (hm != null) {
                hm.showToast(playerId, ToastType.ECONOMY, "Cat found! (" + found + "/5)");
            }

            if (found >= 5 && plugin.getAchievementManager() != null) {
                plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
            }
        }, world);
    }
}
