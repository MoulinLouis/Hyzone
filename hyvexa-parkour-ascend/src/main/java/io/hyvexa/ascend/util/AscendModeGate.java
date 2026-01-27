package io.hyvexa.ascend.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.core.state.ModeGate;
import io.hyvexa.core.state.ModeMessages;
import io.hyvexa.core.state.PlayerMode;

import java.util.UUID;

public final class AscendModeGate {

    private AscendModeGate() {
    }

    public static boolean isAscendMode(UUID playerId) {
        return ModeGate.isMode(playerId, PlayerMode.ASCEND);
    }

    public static boolean denyIfNotAscend(CommandContext context, UUID playerId) {
        if (!isAscendMode(playerId)) {
            if (context != null) {
                context.sendMessage(ModeMessages.MESSAGE_ENTER_ASCEND);
            }
            return true;
        }
        return false;
    }

    public static UUID resolvePlayerId(Player player) {
        if (player == null) {
            return null;
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComponent != null ? uuidComponent.getUuid() : null;
    }
}
