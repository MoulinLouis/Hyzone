package io.hyvexa.purge.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.HyvexaPurgePlugin;
import io.hyvexa.purge.ui.PurgeWeaponSelectPage;

import javax.annotation.Nonnull;
import java.util.UUID;

public class PurgeOrangeOrbInteraction extends SimpleInteraction {

    public static final BuilderCodec<PurgeOrangeOrbInteraction> CODEC =
            BuilderCodec.builder(PurgeOrangeOrbInteraction.class, PurgeOrangeOrbInteraction::new).build();

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        if (!ref.isValid()) {
            return;
        }
        var store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
        if (plugin == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PurgeWeaponSelectPage(playerRef, PurgeWeaponSelectPage.Mode.LOADOUT, playerId,
                        plugin.getWeaponConfigManager(), null, null, null));
    }
}
