package io.hyvexa.ascend.interaction;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.util.AscendModeGate;
import io.hyvexa.core.state.ModeMessages;

import javax.annotation.Nonnull;

// NOTE: Interaction handlers are instantiated by Hytale's BuilderCodec framework
// (no-arg constructor required). Constructor injection is not possible.
// ParkourAscendPlugin.getInstance() is centralized here instead of in each handler.
abstract class AbstractAscendPageInteraction extends SimpleInteraction {

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if (requiresAscendWorld()) {
            World world = store.getExternalData().getWorld();
            if (!AscendModeGate.isAscendWorld(world)) {
                player.sendMessage(ModeMessages.MESSAGE_ENTER_ASCEND);
                return;
            }
        }
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (requiresPlugin() && plugin == null) {
            return;
        }
        if (plugin != null && !validateDependencies(plugin, player)) {
            return;
        }
        InteractiveCustomUIPage<?> page = createPage(ref, store, playerRef, plugin);
        if (page != null) {
            player.getPageManager().openCustomPage(ref, store, page);
        }
    }

    protected boolean requiresAscendWorld() {
        return true;
    }

    protected boolean requiresPlugin() {
        return true;
    }

    protected boolean validateDependencies(ParkourAscendPlugin plugin, Player player) {
        return true;
    }

    protected abstract InteractiveCustomUIPage<?> createPage(Ref<EntityStore> ref, Store<EntityStore> store,
                                                             PlayerRef playerRef, ParkourAscendPlugin plugin);
}
