package io.hyvexa.purge.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
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
import io.hyvexa.purge.data.PurgeMapInstance;
import io.hyvexa.purge.data.PurgeSpawnPoint;
import io.hyvexa.purge.manager.PurgeInstanceManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;

import javax.annotation.Nonnull;
import java.util.List;

public class PurgeInstanceAdminPage extends InteractiveCustomUIPage<PurgeInstanceAdminPage.PurgeInstanceAdminData> {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_ADD = "Add";
    private static final String BUTTON_DELETE_PREFIX = "Delete:";
    private static final String BUTTON_SET_START_PREFIX = "SetStart:";
    private static final String BUTTON_SET_EXIT_PREFIX = "SetExit:";
    private static final String BUTTON_ADD_SPAWN_PREFIX = "AddSpawn:";
    private static final String BUTTON_CLEAR_SPAWNS_PREFIX = "ClearSpawns:";

    private final PurgeInstanceManager instanceManager;
    private final PurgeWaveConfigManager waveConfigManager;

    public PurgeInstanceAdminPage(@Nonnull PlayerRef playerRef,
                                   PurgeInstanceManager instanceManager,
                                   PurgeWaveConfigManager waveConfigManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeInstanceAdminData.CODEC);
        this.instanceManager = instanceManager;
        this.waveConfigManager = waveConfigManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_InstanceAdmin.ui");
        bindStaticEvents(uiEventBuilder);
        buildInstanceList(uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PurgeInstanceAdminData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());

        if (BUTTON_BACK.equals(button)) {
            openIndex(ref, store);
            return;
        }
        if (BUTTON_ADD.equals(button)) {
            String id = instanceManager.addInstance();
            if (player != null) {
                player.sendMessage(Message.raw("Added instance: " + id));
            }
            sendRefresh();
            return;
        }
        if (button.startsWith(BUTTON_DELETE_PREFIX)) {
            String instanceId = button.substring(BUTTON_DELETE_PREFIX.length());
            boolean removed = instanceManager.removeInstance(instanceId);
            if (player != null) {
                if (removed) {
                    player.sendMessage(Message.raw("Removed instance: " + instanceId));
                } else {
                    player.sendMessage(Message.raw("Cannot remove (in use or not found)."));
                }
            }
            sendRefresh();
            return;
        }
        if (button.startsWith(BUTTON_SET_START_PREFIX)) {
            String instanceId = button.substring(BUTTON_SET_START_PREFIX.length());
            PurgeLocation loc = readPlayerLocation(ref, store);
            if (loc != null && instanceManager.setStartPoint(instanceId, loc)) {
                if (player != null) {
                    player.sendMessage(Message.raw("Start point set for " + instanceId));
                }
            }
            sendRefresh();
            return;
        }
        if (button.startsWith(BUTTON_SET_EXIT_PREFIX)) {
            String instanceId = button.substring(BUTTON_SET_EXIT_PREFIX.length());
            PurgeLocation loc = readPlayerLocation(ref, store);
            if (loc != null && instanceManager.setExitPoint(instanceId, loc)) {
                if (player != null) {
                    player.sendMessage(Message.raw("Exit point set for " + instanceId));
                }
            }
            sendRefresh();
            return;
        }
        if (button.startsWith(BUTTON_ADD_SPAWN_PREFIX)) {
            String instanceId = button.substring(BUTTON_ADD_SPAWN_PREFIX.length());
            PurgeLocation loc = readPlayerLocation(ref, store);
            if (loc != null) {
                PurgeMapInstance inst = instanceManager.getInstance(instanceId);
                int nextId = inst != null ? inst.spawnPoints().size() : 0;
                PurgeSpawnPoint sp = new PurgeSpawnPoint(nextId, loc.x(), loc.y(), loc.z(), loc.rotY());
                if (instanceManager.addSpawnPoint(instanceId, sp)) {
                    if (player != null) {
                        player.sendMessage(Message.raw("Spawn point added to " + instanceId
                                + " (" + (nextId + 1) + " total)"));
                    }
                }
            }
            sendRefresh();
            return;
        }
        if (button.startsWith(BUTTON_CLEAR_SPAWNS_PREFIX)) {
            String instanceId = button.substring(BUTTON_CLEAR_SPAWNS_PREFIX.length());
            if (instanceManager.clearSpawnPoints(instanceId)) {
                if (player != null) {
                    player.sendMessage(Message.raw("Spawn points cleared for " + instanceId));
                }
            }
            sendRefresh();
        }
    }

    private PurgeLocation readPlayerLocation(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            return null;
        }
        Vector3d pos = transform.getPosition();
        float rotY = transform.getRotation() != null ? transform.getRotation().getY() : 0f;
        return new PurgeLocation(
                Math.round(pos.getX() * 100.0) / 100.0,
                Math.round(pos.getY() * 100.0) / 100.0,
                Math.round(pos.getZ() * 100.0) / 100.0,
                0f, rotY, 0f
        );
    }

    private void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PurgeAdminIndexPage(playerRef, waveConfigManager, instanceManager));
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindStaticEvents(eventBuilder);
        buildInstanceList(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindStaticEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddInstanceButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ADD), false);
    }

    private void buildInstanceList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#InstanceCards");

        List<PurgeMapInstance> instances = instanceManager.getAllInstances();

        commandBuilder.set("#InstanceCount.Text", instances.size() + " configured instance"
                + (instances.size() == 1 ? "" : "s"));

        if (instances.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No instances configured. Add at least one.");
            return;
        }
        commandBuilder.set("#EmptyText.Text", "");

        for (int i = 0; i < instances.size(); i++) {
            PurgeMapInstance inst = instances.get(i);
            String root = "#InstanceCards[" + i + "]";
            String id = inst.instanceId();

            commandBuilder.append("#InstanceCards", "Pages/Purge_InstanceEntry.ui");
            commandBuilder.set(root + " #InstanceIdLabel.Text", id);

            PurgeLocation start = inst.startPoint();
            commandBuilder.set(root + " #StartLabel.Text",
                    "Start: (" + fmt(start.x()) + ", " + fmt(start.y()) + ", " + fmt(start.z()) + ")");

            PurgeLocation exit = inst.exitPoint();
            commandBuilder.set(root + " #ExitLabel.Text",
                    "Exit: (" + fmt(exit.x()) + ", " + fmt(exit.y()) + ", " + fmt(exit.z()) + ")");

            int spawnCount = inst.spawnPoints().size();
            commandBuilder.set(root + " #SpawnCountLabel.Text",
                    spawnCount + " spawn point" + (spawnCount == 1 ? "" : "s"));

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #DeleteButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_DELETE_PREFIX + id), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #SetStartButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SET_START_PREFIX + id), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #SetExitButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SET_EXIT_PREFIX + id), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #AddSpawnButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ADD_SPAWN_PREFIX + id), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #ClearSpawnsButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLEAR_SPAWNS_PREFIX + id), false);
        }
    }

    private static String fmt(double val) {
        if (val == (long) val) {
            return String.valueOf((long) val);
        }
        return String.valueOf(Math.round(val * 100.0) / 100.0);
    }

    public static class PurgeInstanceAdminData extends ButtonEventData {
        public static final BuilderCodec<PurgeInstanceAdminData> CODEC =
                BuilderCodec.<PurgeInstanceAdminData>builder(PurgeInstanceAdminData.class, PurgeInstanceAdminData::new)
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
