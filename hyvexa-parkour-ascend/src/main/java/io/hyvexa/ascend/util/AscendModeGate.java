package io.hyvexa.ascend.util;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import io.hyvexa.core.state.ModeMessages;
import io.hyvexa.common.util.PlayerUtils;

import java.util.UUID;

public final class AscendModeGate {

    private static final String ASCEND_WORLD_NAME = "Ascend";

    private AscendModeGate() {
    }

    public static boolean isAscendWorld(World world) {
        if (world == null || world.getName() == null) {
            return false;
        }
        return ASCEND_WORLD_NAME.equalsIgnoreCase(world.getName());
    }

    public static boolean denyIfNotAscend(CommandContext context, World world) {
        if (!isAscendWorld(world)) {
            if (context != null) {
                context.sendMessage(ModeMessages.MESSAGE_ENTER_ASCEND);
            }
            return true;
        }
        return false;
    }

    public static UUID resolvePlayerId(Player player) {
        return PlayerUtils.resolvePlayerId(player);
    }
}
