package io.hyvexa.parkour.visibility;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerVisibilityManager {

    private static final PlayerVisibilityManager INSTANCE = new PlayerVisibilityManager();

    private final ConcurrentHashMap<UUID, Set<UUID>> hiddenByViewer = new ConcurrentHashMap<>();

    private PlayerVisibilityManager() {
    }

    @Nonnull
    public static PlayerVisibilityManager get() {
        return INSTANCE;
    }

    public void hidePlayer(@Nonnull UUID viewerId, @Nonnull UUID targetId) {
        hiddenByViewer.computeIfAbsent(viewerId, id -> ConcurrentHashMap.newKeySet()).add(targetId);
    }

    public void showPlayer(@Nonnull UUID viewerId, @Nonnull UUID targetId) {
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

    public void sweepStalePlayers(@Nonnull Set<UUID> onlinePlayers) {
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
