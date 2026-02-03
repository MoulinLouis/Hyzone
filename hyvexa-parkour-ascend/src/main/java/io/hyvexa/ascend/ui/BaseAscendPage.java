package io.hyvexa.ascend.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.common.ui.ButtonEventData;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public abstract class BaseAscendPage extends InteractiveCustomUIPage<ButtonEventData> {

    // Track current page per player - simple way to know if this page is still active
    private static final Map<UUID, Long> currentPageIds = new ConcurrentHashMap<>();
    private static final AtomicLong pageIdCounter = new AtomicLong(0);

    private final long pageId;
    private final UUID playerId;

    protected BaseAscendPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, ButtonEventData.CODEC);
        this.pageId = pageIdCounter.incrementAndGet();
        this.playerId = playerRef.getUuid();
        // Register this page as the current one for this player
        currentPageIds.put(playerId, pageId);
    }

    /**
     * Check if this page is still the current page for the player.
     * Use this before sending any UI updates.
     */
    protected boolean isCurrentPage() {
        Long current = currentPageIds.get(playerId);
        return current != null && current == pageId;
    }

    /**
     * Public method to close this page and stop any background tasks.
     * Called when switching to a different page to ensure cleanup.
     */
    public void shutdown() {
        this.close();
    }
}
