package io.hyvexa.hub.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.hub.HyvexaHubPlugin;
import io.hyvexa.hub.ui.HubMenuPage;

import javax.annotation.Nonnull;

public class HubCommand extends AbstractPlayerCommand {

    private static final String HUB_WORLD_NAME = "Hub";

    private final HyvexaHubPlugin plugin;

    public HubCommand(HyvexaHubPlugin plugin) {
        super("menu", "Open the hub menu.");
        this.plugin = plugin;
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (plugin == null || plugin.getRouter() == null) {
            return;
        }
        if (HUB_WORLD_NAME.equalsIgnoreCase(world.getName())) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store, new HubMenuPage(playerRef, plugin.getRouter()));
            }
            return;
        }
        plugin.getRouter().routeToHub(playerRef);
    }
}
