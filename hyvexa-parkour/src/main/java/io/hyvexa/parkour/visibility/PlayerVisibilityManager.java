package io.hyvexa.parkour.visibility;

import io.hyvexa.common.visibility.EntityVisibilityManager;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;

/**
 * Legacy wrapper for backward compatibility.
 * Delegates to EntityVisibilityManager in core.
 */
public final class PlayerVisibilityManager {

    private static final PlayerVisibilityManager INSTANCE = new PlayerVisibilityManager();

    private PlayerVisibilityManager() {
    }

    @Nonnull
    public static PlayerVisibilityManager get() {
        return INSTANCE;
    }

    public void hidePlayer(@Nonnull UUID viewerId, @Nonnull UUID targetId) {
        EntityVisibilityManager.get().hideEntity(viewerId, targetId);
    }

    public void showPlayer(@Nonnull UUID viewerId, @Nonnull UUID targetId) {
        EntityVisibilityManager.get().showEntity(viewerId, targetId);
    }

    @Nonnull
    public Set<UUID> getHiddenTargets(@Nonnull UUID viewerId) {
        return EntityVisibilityManager.get().getHiddenTargets(viewerId);
    }

    public void clearHidden(@Nonnull UUID viewerId) {
        EntityVisibilityManager.get().clearHidden(viewerId);
    }

    public void sweepStalePlayers(@Nonnull Set<UUID> onlinePlayers) {
        EntityVisibilityManager.get().sweepStaleViewers(onlinePlayers);
    }
}
