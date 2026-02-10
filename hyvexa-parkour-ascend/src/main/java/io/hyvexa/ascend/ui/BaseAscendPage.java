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

    private static final Map<UUID, Long> currentPageIds = new ConcurrentHashMap<>();
    private static final AtomicLong pageIdCounter = new AtomicLong(0);

    private final long pageId;
    private final UUID playerId;

    protected BaseAscendPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, ButtonEventData.CODEC);
        this.pageId = pageIdCounter.incrementAndGet();
        this.playerId = playerRef.getUuid();
        currentPageIds.put(playerId, pageId);
    }

    protected boolean isCurrentPage() {
        Long current = currentPageIds.get(playerId);
        return current != null && current == pageId;
    }

    public static void removeCurrentPage(UUID playerId) {
        if (playerId == null) {
            return;
        }
        currentPageIds.remove(playerId);
    }

    public void shutdown() {
        this.close();
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        currentPageIds.remove(playerId, pageId);
        stopBackgroundTasks();
        super.onDismiss(ref, store);
    }

    protected void stopBackgroundTasks() {
    }
}
