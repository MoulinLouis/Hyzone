package io.hyvexa.purge.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.manager.PurgeSpawnPointManager;

import javax.annotation.Nonnull;

public class PurgeAdminIndexPage extends InteractiveCustomUIPage<PurgeAdminIndexPage.PurgeAdminIndexData> {

    private static final String BUTTON_SPAWNS = "Spawns";

    private final PurgeSpawnPointManager spawnPointManager;

    public PurgeAdminIndexPage(@Nonnull PlayerRef playerRef, PurgeSpawnPointManager spawnPointManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeAdminIndexData.CODEC);
        this.spawnPointManager = spawnPointManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_AdminIndex.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SpawnsButton",
                EventData.of(PurgeAdminIndexData.KEY_BUTTON, BUTTON_SPAWNS), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PurgeAdminIndexData data) {
        super.handleDataEvent(ref, store, data);
        if (data.button == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if (BUTTON_SPAWNS.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store,
                    new PurgeSpawnAdminPage(playerRef, spawnPointManager));
        }
    }

    public static class PurgeAdminIndexData {
        static final String KEY_BUTTON = "Button";

        public static final BuilderCodec<PurgeAdminIndexData> CODEC =
                BuilderCodec.<PurgeAdminIndexData>builder(PurgeAdminIndexData.class, PurgeAdminIndexData::new)
                        .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING),
                                (data, value) -> data.button = value, data -> data.button)
                        .build();

        String button;
    }
}
