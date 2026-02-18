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
import io.hyvexa.purge.data.PurgeLocation;
import io.hyvexa.purge.manager.PurgeSettingsManager;
import io.hyvexa.purge.manager.PurgeSpawnPointManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;

import javax.annotation.Nonnull;

public class PurgeSettingsAdminPage extends InteractiveCustomUIPage<PurgeSettingsAdminPage.PurgeSettingsAdminData> {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_SET_START = "SetStart";
    private static final String BUTTON_SET_EXIT = "SetExit";

    private final PurgeSpawnPointManager spawnPointManager;
    private final PurgeWaveConfigManager waveConfigManager;
    private final PurgeSettingsManager settingsManager;

    public PurgeSettingsAdminPage(@Nonnull PlayerRef playerRef,
                                  PurgeSpawnPointManager spawnPointManager,
                                  PurgeWaveConfigManager waveConfigManager,
                                  PurgeSettingsManager settingsManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeSettingsAdminData.CODEC);
        this.spawnPointManager = spawnPointManager;
        this.waveConfigManager = waveConfigManager;
        this.settingsManager = settingsManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder,
                      @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_SettingsAdmin.ui");
        bindStaticEvents(uiEventBuilder);
        applyValues(uiCommandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PurgeSettingsAdminData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) {
            return;
        }
        if (BUTTON_BACK.equals(button)) {
            openIndex(ref, store);
            return;
        }
        if (BUTTON_SET_START.equals(button)) {
            handleSetStart(ref, store);
            return;
        }
        if (BUTTON_SET_EXIT.equals(button)) {
            handleSetExit(ref, store);
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

    private void handleSetStart(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            player.sendMessage(Message.raw("Could not read position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        settingsManager.setSessionStartPoint(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                rot != null ? rot.getX() : 0f,
                rot != null ? rot.getY() : 0f,
                rot != null ? rot.getZ() : 0f
        );
        player.sendMessage(Message.raw("Purge start spawn set at "
                + formatCoord(pos.getX()) + ", "
                + formatCoord(pos.getY()) + ", "
                + formatCoord(pos.getZ()) + "."));
        sendRefresh();
    }

    private void handleSetExit(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            player.sendMessage(Message.raw("Could not read position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        settingsManager.setSessionExitPoint(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                rot != null ? rot.getX() : 0f,
                rot != null ? rot.getY() : 0f,
                rot != null ? rot.getZ() : 0f
        );
        player.sendMessage(Message.raw("Purge exit point set at "
                + formatCoord(pos.getX()) + ", "
                + formatCoord(pos.getY()) + ", "
                + formatCoord(pos.getZ()) + "."));
        sendRefresh();
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindStaticEvents(eventBuilder);
        applyValues(commandBuilder);
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindStaticEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetStartButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SET_START), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetExitButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SET_EXIT), false);
    }

    private void applyValues(UICommandBuilder commandBuilder) {
        commandBuilder.set("#StartPointValue.Text", formatLocation(settingsManager.getSessionStartPoint()));
        commandBuilder.set("#ExitPointValue.Text", formatLocation(settingsManager.getSessionExitPoint()));
    }

    private static String formatLocation(PurgeLocation location) {
        if (location == null) {
            return "Not configured";
        }
        return formatCoord(location.x()) + ", "
                + formatCoord(location.y()) + ", "
                + formatCoord(location.z())
                + "  yaw: " + String.format("%.0f", location.rotY());
    }

    private static String formatCoord(double value) {
        return String.format("%.1f", value);
    }

    public static class PurgeSettingsAdminData extends ButtonEventData {
        public static final BuilderCodec<PurgeSettingsAdminData> CODEC =
                BuilderCodec.<PurgeSettingsAdminData>builder(PurgeSettingsAdminData.class, PurgeSettingsAdminData::new)
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
