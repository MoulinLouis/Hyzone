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
import io.hyvexa.purge.manager.PurgeSettingsManager;
import io.hyvexa.purge.manager.PurgeSpawnPointManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;

import javax.annotation.Nonnull;

public class PurgeAdminIndexPage extends InteractiveCustomUIPage<PurgeAdminIndexPage.PurgeAdminIndexData> {

    private static final String BUTTON_SETTINGS = "Settings";
    private static final String BUTTON_SPAWNS = "Spawns";
    private static final String BUTTON_WAVES = "Waves";

    private final PurgeSettingsManager settingsManager;
    private final PurgeSpawnPointManager spawnPointManager;
    private final PurgeWaveConfigManager waveConfigManager;

    public PurgeAdminIndexPage(@Nonnull PlayerRef playerRef,
                               PurgeSpawnPointManager spawnPointManager,
                               PurgeWaveConfigManager waveConfigManager,
                               PurgeSettingsManager settingsManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeAdminIndexData.CODEC);
        this.settingsManager = settingsManager;
        this.spawnPointManager = spawnPointManager;
        this.waveConfigManager = waveConfigManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_AdminIndex.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SettingsButton",
                EventData.of(PurgeAdminIndexData.KEY_BUTTON, BUTTON_SETTINGS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SpawnsButton",
                EventData.of(PurgeAdminIndexData.KEY_BUTTON, BUTTON_SPAWNS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#WavesButton",
                EventData.of(PurgeAdminIndexData.KEY_BUTTON, BUTTON_WAVES), false);
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
        if (BUTTON_SETTINGS.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store,
                    new PurgeSettingsAdminPage(playerRef, spawnPointManager, waveConfigManager, settingsManager));
            return;
        }
        if (BUTTON_SPAWNS.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store,
                    new PurgeSpawnAdminPage(playerRef, spawnPointManager, waveConfigManager, settingsManager));
            return;
        }
        if (BUTTON_WAVES.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store,
                    new PurgeWaveAdminPage(playerRef, spawnPointManager, waveConfigManager, settingsManager));
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
