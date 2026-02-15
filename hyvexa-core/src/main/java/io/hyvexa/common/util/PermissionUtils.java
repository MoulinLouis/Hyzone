package io.hyvexa.common.util;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;

import java.util.UUID;

public final class PermissionUtils {

    private static final String OP_GROUP = "OP";

    private PermissionUtils() {
    }

    /**
     * Check if a player has OP permissions.
     * Uses deprecated PermissionsModule API — no replacement available as of Hytale server 0.x.
     * Revisit when a non-deprecated permissions API is introduced.
     */
    @SuppressWarnings("removal")
    public static boolean isOp(Player player) {
        if (player == null) {
            return false;
        }
        return isOp(player.getUuid());
    }

    @SuppressWarnings("removal")
    public static boolean isOp(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        PermissionsModule permissions = PermissionsModule.get();
        if (permissions == null) {
            return false;
        }
        return permissions.getGroupsForUser(uuid).contains(OP_GROUP);
    }
}
