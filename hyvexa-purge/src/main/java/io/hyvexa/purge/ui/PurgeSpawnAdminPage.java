package io.hyvexa.purge.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.purge.data.PurgeSpawnPoint;
import io.hyvexa.purge.manager.PurgeSettingsManager;
import io.hyvexa.purge.manager.PurgeSpawnPointManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PurgeSpawnAdminPage extends InteractiveCustomUIPage<PurgeSpawnAdminPage.PurgeSpawnAdminData> {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_ADD = "Add";
    private static final String BUTTON_DELETE_PREFIX = "Delete:";

    private final PurgeSettingsManager settingsManager;
    private final PurgeSpawnPointManager spawnPointManager;
    private final PurgeWaveConfigManager waveConfigManager;

    public PurgeSpawnAdminPage(@Nonnull PlayerRef playerRef,
                               PurgeSpawnPointManager spawnPointManager,
                               PurgeWaveConfigManager waveConfigManager,
                               PurgeSettingsManager settingsManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeSpawnAdminData.CODEC);
        this.settingsManager = settingsManager;
        this.spawnPointManager = spawnPointManager;
        this.waveConfigManager = waveConfigManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_SpawnAdmin.ui");
        bindStaticEvents(uiEventBuilder);
        buildSpawnList(uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PurgeSpawnAdminData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }
        if (BUTTON_BACK.equals(data.getButton())) {
            openIndex(ref, store);
            return;
        }
        if (BUTTON_ADD.equals(data.getButton())) {
            handleAdd(ref, store);
            return;
        }
        if (data.getButton().startsWith(BUTTON_DELETE_PREFIX)) {
            handleDelete(data.getButton().substring(BUTTON_DELETE_PREFIX.length()), ref, store);
        }
    }

    private void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PurgeAdminIndexPage(playerRef, spawnPointManager, waveConfigManager, settingsManager));
    }

    private void handleAdd(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("Could not read position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        float yaw = rot != null ? rot.getY() : 0f;
        int id = spawnPointManager.addSpawnPoint(pos.getX(), pos.getY(), pos.getZ(), yaw);
        player.sendMessage(Message.raw("Added spawn point #" + id + " at "
                + formatCoord(pos.getX()) + ", " + formatCoord(pos.getY()) + ", " + formatCoord(pos.getZ())));
        sendRefresh();
    }

    private void handleDelete(String rawId, Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        int id;
        try {
            id = Integer.parseInt(rawId);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Invalid spawn ID."));
            return;
        }
        boolean removed = spawnPointManager.removeSpawnPoint(id);
        if (removed) {
            player.sendMessage(Message.raw("Removed spawn point #" + id + "."));
        } else {
            player.sendMessage(Message.raw("Spawn point #" + id + " not found."));
        }
        sendRefresh();
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindStaticEvents(eventBuilder);
        buildSpawnList(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindStaticEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddSpawnButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ADD), false);
    }

    private void buildSpawnList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#SpawnCards");
        List<PurgeSpawnPoint> spawns = new ArrayList<>(spawnPointManager.getAll());
        spawns.sort(Comparator.comparingInt(PurgeSpawnPoint::id));

        commandBuilder.set("#SpawnCount.Text", spawns.size() + " spawn point" + (spawns.size() != 1 ? "s" : ""));

        if (spawns.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No spawn points configured. Add one below.");
            return;
        }
        commandBuilder.set("#EmptyText.Text", "");

        for (int i = 0; i < spawns.size(); i++) {
            PurgeSpawnPoint sp = spawns.get(i);
            commandBuilder.append("#SpawnCards", "Pages/Purge_SpawnEntry.ui");
            commandBuilder.set("#SpawnCards[" + i + "] #SpawnId.Text", "#" + sp.id());
            commandBuilder.set("#SpawnCards[" + i + "] #SpawnCoords.Text",
                    formatCoord(sp.x()) + ", " + formatCoord(sp.y()) + ", " + formatCoord(sp.z())
                            + "  yaw: " + String.format("%.0f", sp.yaw()));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#SpawnCards[" + i + "] #DeleteButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_DELETE_PREFIX + sp.id()), false);
        }
    }

    private static String formatCoord(double v) {
        return String.format("%.1f", v);
    }

    public static class PurgeSpawnAdminData extends ButtonEventData {
        public static final BuilderCodec<PurgeSpawnAdminData> CODEC =
                BuilderCodec.<PurgeSpawnAdminData>builder(PurgeSpawnAdminData.class, PurgeSpawnAdminData::new)
                        .addField(new KeyedCodec<>(ButtonEventData.KEY_BUTTON, Codec.STRING),
                                (data, value) -> data.button = value, data -> data.button)
                        .build();

        private String button;

        @Override
        public String getButton() {
            return button;
        }
    }
}
