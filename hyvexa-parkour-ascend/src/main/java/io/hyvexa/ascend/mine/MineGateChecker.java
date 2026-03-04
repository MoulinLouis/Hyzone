package io.hyvexa.ascend.mine;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.mine.data.MineConfigStore;

import java.util.UUID;

public class MineGateChecker {

    private final MineConfigStore configStore;
    private final AscendPlayerStore playerStore;

    public MineGateChecker(MineConfigStore configStore, AscendPlayerStore playerStore) {
        this.configStore = configStore;
        this.playerStore = playerStore;
    }

    public void checkPlayer(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!configStore.isGateConfigured()) return;
        if (ref == null || !ref.isValid()) return;

        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress != null && progress.getAscensionCount() >= 1) return;

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        Vector3d pos = transform.getPosition();
        if (pos == null) return;

        if (configStore.isInsideGate(pos.getX(), pos.getY(), pos.getZ())) {
            World world = store.getExternalData().getWorld();
            if (world == null) return;

            Vector3d fallbackPos = new Vector3d(
                configStore.getFallbackX(),
                configStore.getFallbackY(),
                configStore.getFallbackZ()
            );
            Vector3f fallbackRot = new Vector3f(
                configStore.getFallbackRotX(),
                configStore.getFallbackRotY(),
                configStore.getFallbackRotZ()
            );
            store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, fallbackPos, fallbackRot));
        }
    }
}
