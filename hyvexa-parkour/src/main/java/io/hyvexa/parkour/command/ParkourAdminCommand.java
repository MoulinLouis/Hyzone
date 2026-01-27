package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;
import io.hyvexa.parkour.ui.AdminIndexPage;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.parkour.util.ParkourModeGate;
import javax.annotation.Nonnull;

import java.util.concurrent.CompletableFuture;

import static com.hypixel.hytale.server.core.command.commands.player.inventory.InventorySeeCommand.MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD;

public class ParkourAdminCommand extends AbstractAsyncCommand {

    private static final Message MESSAGE_OP_REQUIRED = Message.raw("You must be OP to use /pkadmin.");

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final SettingsStore settingsStore;

    public ParkourAdminCommand(MapStore mapStore, ProgressStore progressStore) {
        this(mapStore, progressStore, HyvexaPlugin.getInstance() != null
                ? HyvexaPlugin.getInstance().getSettingsStore()
                : null);
    }

    public ParkourAdminCommand(MapStore mapStore, ProgressStore progressStore, SettingsStore settingsStore) {
        super("pkadmin", "Open the parkour admin UI.");
        this.setPermissionGroup(GameMode.Adventure);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.settingsStore = settingsStore;
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        if (!(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(null);
        }
        if (ParkourModeGate.denyIfNotParkour(commandContext, ParkourModeGate.resolvePlayerId(player))) {
            return CompletableFuture.completedFuture(null);
        }
        if (!PermissionUtils.isOp(player)) {
            commandContext.sendMessage(MESSAGE_OP_REQUIRED);
            return CompletableFuture.completedFuture(null);
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref != null && ref.isValid()) {
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            return CompletableFuture.runAsync(() -> {
                PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRefComponent != null) {
                    player.getPageManager().openCustomPage(ref, store,
                            new AdminIndexPage(playerRefComponent, mapStore, progressStore, settingsStore,
                                    HyvexaPlugin.getInstance() != null
                                            ? HyvexaPlugin.getInstance().getPlayerCountStore()
                                            : null));
                }
            }, world);
        }
        commandContext.sendMessage(MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
        return CompletableFuture.completedFuture(null);
    }
}
