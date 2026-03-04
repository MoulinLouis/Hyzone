package io.hyvexa.ascend.mine.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.common.util.PermissionUtils;

import java.util.UUID;

public class MineBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private final MineManager mineManager;
    private final MinePlayerStore minePlayerStore;

    public MineBreakSystem(MineManager mineManager, MinePlayerStore minePlayerStore) {
        super(BreakBlockEvent.class);
        this.mineManager = mineManager;
        this.minePlayerStore = minePlayerStore;
    }

    @Override
    public void handle(int entityId, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> buffer, BreakBlockEvent event) {
        Player player = chunk.getComponent(entityId, Player.getComponentType());
        if (player == null) return;
        if (PermissionUtils.isOp(player)) return; // OPs handled by NoBreakSystem (allowed)

        int bx = event.getBlockX();
        int by = event.getBlockY();
        int bz = event.getBlockZ();

        MineZone zone = mineManager.findZoneAt(bx, by, bz);
        if (zone == null) return; // Not in a mining zone — let NoBreakSystem's cancel stand

        // Block is in a mining zone — check if player can mine
        PlayerRef playerRef = chunk.getComponent(entityId, PlayerRef.getComponentType());
        if (playerRef == null) return;
        UUID playerId = playerRef.getUuid();
        if (playerId == null) return;

        AscendPlayerProgress ascendProgress = ParkourAscendPlugin.getInstance()
            .getPlayerStore().getPlayer(playerId);
        if (ascendProgress == null || ascendProgress.getAscensionCount() < 1) {
            return; // No ascension — keep break cancelled
        }

        MinePlayerProgress mineProgress = minePlayerStore.getOrCreatePlayer(playerId);
        if (mineProgress.isInventoryFull()) {
            return; // Bag full — keep break cancelled
            // TODO Phase 3: send "bag full" message
        }

        // Allow the break — override NoBreakSystem's cancellation
        event.setCancelled(false);

        // Resolve block type name and add to inventory
        String blockTypeName = event.getBlockType() != null ? event.getBlockType().getId() : null;
        if (blockTypeName != null) {
            mineProgress.addToInventory(blockTypeName, 1);
            minePlayerStore.markDirty(playerId);
        }

        // Track broken block in zone
        mineManager.markBlockBroken(zone.getId(), bx, by, bz);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
