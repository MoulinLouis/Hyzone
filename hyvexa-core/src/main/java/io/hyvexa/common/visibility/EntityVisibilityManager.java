package io.hyvexa.common.visibility;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages entity visibility per viewer.
 * Any entity with a UUIDComponent can be hidden from specific viewers.
 */
public final class EntityVisibilityManager {

    private static final EntityVisibilityManager INSTANCE = new EntityVisibilityManager();

    private final ConcurrentHashMap<UUID, Set<UUID>> hiddenByViewer = new ConcurrentHashMap<>();

    private EntityVisibilityManager() {
    }

    @Nonnull
    public static EntityVisibilityManager get() {
        return INSTANCE;
    }

    public void hideEntity(@Nonnull UUID viewerId, @Nonnull UUID targetId) {
        hiddenByViewer.computeIfAbsent(viewerId, id -> ConcurrentHashMap.newKeySet()).add(targetId);
    }

    public void showEntity(@Nonnull UUID viewerId, @Nonnull UUID targetId) {
        Set<UUID> hidden = hiddenByViewer.get(viewerId);
        if (hidden == null) {
            return;
        }
        hidden.remove(targetId);
        if (hidden.isEmpty()) {
            hiddenByViewer.remove(viewerId, hidden);
        }
    }

    @Nonnull
    public Set<UUID> getHiddenTargets(@Nonnull UUID viewerId) {
        Set<UUID> hidden = hiddenByViewer.get(viewerId);
        if (hidden == null) {
            return Set.of();
        }
        return hidden;
    }

    public void clearHidden(@Nonnull UUID viewerId) {
        hiddenByViewer.remove(viewerId);
    }

    public void sweepStaleViewers(@Nonnull Set<UUID> onlinePlayers) {
        if (onlinePlayers.isEmpty()) {
            hiddenByViewer.clear();
            return;
        }
        for (var entry : hiddenByViewer.entrySet()) {
            UUID viewerId = entry.getKey();
            if (!onlinePlayers.contains(viewerId)) {
                hiddenByViewer.remove(viewerId);
                continue;
            }
            Set<UUID> hidden = entry.getValue();
            if (hidden == null) {
                hiddenByViewer.remove(viewerId);
                continue;
            }
            hidden.removeIf(id -> !onlinePlayers.contains(id));
            if (hidden.isEmpty()) {
                hiddenByViewer.remove(viewerId, hidden);
            }
        }
    }
}
