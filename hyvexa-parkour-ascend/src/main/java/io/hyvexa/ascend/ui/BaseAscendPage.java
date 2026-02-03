package io.hyvexa.ascend.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
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

    /**
     * Called automatically by Hytale when the page is dismissed or replaced
     * (including when external UIs like NPCDialog open).
     * This is the proper lifecycle hook to clean up background tasks.
     * NOTE: Do NOT call close() here to avoid recursion - Hytale calls both onDismiss() and close()
     */
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        // Unregister this page from tracking
        currentPageIds.remove(playerId);
        // Subclasses should override this to stop their scheduled tasks
        stopBackgroundTasks();
        // Call parent implementation
        super.onDismiss(ref, store);
    }

    /**
     * Override this method in subclasses to stop scheduled tasks.
     * Called both from close() and onDismiss() to ensure cleanup.
     */
    protected void stopBackgroundTasks() {
        // Default: no background tasks to stop
    }
}
