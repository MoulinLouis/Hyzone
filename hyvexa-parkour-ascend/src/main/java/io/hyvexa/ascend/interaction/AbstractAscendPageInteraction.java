package io.hyvexa.ascend.interaction;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.core.state.ModeMessages;

import javax.annotation.Nonnull;

// NOTE: Interaction handlers are instantiated by Hytale's BuilderCodec framework
// (no-arg constructor required). Constructor injection is not possible.
// A narrow static bridge is used instead of reaching into ParkourAscendPlugin directly.
public abstract class AbstractAscendPageInteraction extends SimpleInteraction {

    public static final Message LOADING_MESSAGE = Message.raw("[Ascend] Ascend systems are still loading.");

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
            if (!ModeGate.isAscendWorld(world)) {
                player.sendMessage(ModeMessages.MESSAGE_ENTER_ASCEND);
                return;
            }
        }
        AscendInteractionBridge.Services services = AscendInteractionBridge.get();
        if (requiresServices() && services == null) {
            return;
        }
        if (services != null && !validateDependencies(services, player)) {
            return;
        }
        InteractiveCustomUIPage<?> page = createPage(ref, store, playerRef, services);
        if (page != null) {
            player.getPageManager().openCustomPage(ref, store, page);
        }
    }

    protected boolean requiresAscendWorld() {
        return true;
    }

    protected boolean requiresServices() {
        return true;
    }

    protected boolean validateDependencies(AscendInteractionBridge.Services services, Player player) {
        return true;
    }

    protected abstract InteractiveCustomUIPage<?> createPage(Ref<EntityStore> ref, Store<EntityStore> store,
                                                             PlayerRef playerRef,
                                                             AscendInteractionBridge.Services services);
}
