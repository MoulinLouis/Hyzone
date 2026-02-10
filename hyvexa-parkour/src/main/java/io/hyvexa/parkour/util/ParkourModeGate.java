package io.hyvexa.parkour.util;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import io.hyvexa.core.state.ModeMessages;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.util.PlayerUtils;

import java.util.UUID;

public final class ParkourModeGate {

    private ParkourModeGate() {
    }

    public static boolean isParkourWorld(World world) {
        if (world == null || world.getName() == null) {
            return false;
        }
        return HyvexaPlugin.PARKOUR_WORLD_NAME.equalsIgnoreCase(world.getName());
    }

    public static boolean denyIfNotParkour(CommandContext context, World world) {
        if (!isParkourWorld(world)) {
            if (context != null) {
                context.sendMessage(ModeMessages.MESSAGE_ENTER_PARKOUR);
            }
            return true;
        }
        return false;
    }

    public static UUID resolvePlayerId(Player player) {
        return PlayerUtils.resolvePlayerId(player);
    }
}
