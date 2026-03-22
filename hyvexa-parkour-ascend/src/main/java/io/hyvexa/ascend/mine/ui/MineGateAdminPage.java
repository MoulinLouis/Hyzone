package io.hyvexa.ascend.mine.ui;

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
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.ui.AscendAdminNavigator;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MineGateAdminPage extends InteractiveCustomUIPage<MineGateAdminPage.GateData> {

    private static final Map<UUID, double[]> entryPos1 = new ConcurrentHashMap<>();
    private static final Map<UUID, double[]> entryPos2 = new ConcurrentHashMap<>();
    private static final Map<UUID, double[]> exitPos1 = new ConcurrentHashMap<>();
    private static final Map<UUID, double[]> exitPos2 = new ConcurrentHashMap<>();

    public static void clearPlayer(UUID playerId) {
        entryPos1.remove(playerId);
        entryPos2.remove(playerId);
        exitPos1.remove(playerId);
        exitPos2.remove(playerId);
    }

    private final PlayerRef playerRef;
    private final MineConfigStore mineConfigStore;
    private final AscendAdminNavigator adminNavigator;

    public MineGateAdminPage(@Nonnull PlayerRef playerRef,
                             MineConfigStore mineConfigStore,
                             AscendAdminNavigator adminNavigator) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, GateData.CODEC);
        this.playerRef = playerRef;
        this.mineConfigStore = mineConfigStore;
        this.adminNavigator = adminNavigator;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_MineGateAdmin.ui");
        bindEvents(eventBuilder);
        populateFields(commandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull GateData data) {
        super.handleDataEvent(ref, store, data);
        if (data.button == null) return;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        switch (data.button) {
            case GateData.BUTTON_CLOSE -> this.close();
            // Entry gate
            case GateData.BUTTON_ENTRY_POS1 -> handleSetPos(ref, store, player, entryPos1, "Entry Pos1");
            case GateData.BUTTON_ENTRY_POS2 -> handleSetPos(ref, store, player, entryPos2, "Entry Pos2");
            case GateData.BUTTON_SAVE_ENTRY -> handleSaveEntry(player);
            case GateData.BUTTON_SET_ENTRY_DEST -> handleSetDest(ref, store, player, true);
            // Exit gate
            case GateData.BUTTON_EXIT_POS1 -> handleSetPos(ref, store, player, exitPos1, "Exit Pos1");
            case GateData.BUTTON_EXIT_POS2 -> handleSetPos(ref, store, player, exitPos2, "Exit Pos2");
            case GateData.BUTTON_SAVE_EXIT -> handleSaveExit(player);
            case GateData.BUTTON_SET_EXIT_DEST -> handleSetDest(ref, store, player, false);
            case GateData.BUTTON_BACK -> handleBack(ref, store);
            default -> {}
        }
    }

    private void handleSetPos(Ref<EntityStore> ref, Store<EntityStore> store, Player player,
                              Map<UUID, double[]> posMap, String label) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("Unable to read player position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        posMap.put(playerRef.getUuid(), new double[]{pos.getX(), pos.getY(), pos.getZ()});
        player.sendMessage(Message.raw(label + " set: " + formatPos(pos.getX(), pos.getY(), pos.getZ())));
        updateAllLabels();
    }

    private void handleSaveEntry(Player player) {
        UUID uuid = playerRef.getUuid();
        double[] p1 = entryPos1.get(uuid);
        double[] p2 = entryPos2.get(uuid);
        if (p1 == null || p2 == null) {
            player.sendMessage(Message.raw("Set both Entry Pos1 and Pos2 first."));
            return;
        }
        double minX = Math.min(p1[0], p2[0]), minY = Math.min(p1[1], p2[1]), minZ = Math.min(p1[2], p2[2]);
        double maxX = Math.max(p1[0], p2[0]), maxY = Math.max(p1[1], p2[1]), maxZ = Math.max(p1[2], p2[2]);
        mineConfigStore.saveEntryGate(minX, minY, minZ, maxX, maxY, maxZ,
            mineConfigStore.getEntryDestX(), mineConfigStore.getEntryDestY(), mineConfigStore.getEntryDestZ(),
            mineConfigStore.getEntryDestRotX(), mineConfigStore.getEntryDestRotY(), mineConfigStore.getEntryDestRotZ());
        player.sendMessage(Message.raw("Entry gate AABB saved!"));
        updateAllLabels();
    }

    private void handleSaveExit(Player player) {
        UUID uuid = playerRef.getUuid();
        double[] p1 = exitPos1.get(uuid);
        double[] p2 = exitPos2.get(uuid);
        if (p1 == null || p2 == null) {
            player.sendMessage(Message.raw("Set both Exit Pos1 and Pos2 first."));
            return;
        }
        double minX = Math.min(p1[0], p2[0]), minY = Math.min(p1[1], p2[1]), minZ = Math.min(p1[2], p2[2]);
        double maxX = Math.max(p1[0], p2[0]), maxY = Math.max(p1[1], p2[1]), maxZ = Math.max(p1[2], p2[2]);
        mineConfigStore.saveExitGate(minX, minY, minZ, maxX, maxY, maxZ,
            mineConfigStore.getExitDestX(), mineConfigStore.getExitDestY(), mineConfigStore.getExitDestZ(),
            mineConfigStore.getExitDestRotX(), mineConfigStore.getExitDestRotY(), mineConfigStore.getExitDestRotZ());
        player.sendMessage(Message.raw("Exit gate AABB saved!"));
        updateAllLabels();
    }

    private void handleSetDest(Ref<EntityStore> ref, Store<EntityStore> store, Player player, boolean isEntry) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("Unable to read player position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3f bodyRot = transform.getRotation();
        PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
        Vector3f headRot = pRef != null ? pRef.getHeadRotation() : null;
        Vector3f rot = headRot != null ? headRot : bodyRot;

        if (isEntry) {
            mineConfigStore.saveEntryGate(
                mineConfigStore.getEntryMinX(), mineConfigStore.getEntryMinY(), mineConfigStore.getEntryMinZ(),
                mineConfigStore.getEntryMaxX(), mineConfigStore.getEntryMaxY(), mineConfigStore.getEntryMaxZ(),
                pos.getX(), pos.getY(), pos.getZ(),
                rot.getX(), rot.getY(), rot.getZ());
            player.sendMessage(Message.raw("Entry destination set: " + formatPos(pos.getX(), pos.getY(), pos.getZ())));
        } else {
            mineConfigStore.saveExitGate(
                mineConfigStore.getExitMinX(), mineConfigStore.getExitMinY(), mineConfigStore.getExitMinZ(),
                mineConfigStore.getExitMaxX(), mineConfigStore.getExitMaxY(), mineConfigStore.getExitMaxZ(),
                pos.getX(), pos.getY(), pos.getZ(),
                rot.getX(), rot.getY(), rot.getZ());
            player.sendMessage(Message.raw("Exit destination set: " + formatPos(pos.getX(), pos.getY(), pos.getZ())));
        }
        updateAllLabels();
    }

    private void handleBack(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pRef == null) return;
        player.getPageManager().openCustomPage(ref, store, new MineAdminPage(pRef, mineConfigStore, adminNavigator));
    }

    private void bindEvents(UIEventBuilder eventBuilder) {
        // Entry gate
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#EntryPos1Button",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_ENTRY_POS1), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#EntryPos2Button",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_ENTRY_POS2), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveEntryButton",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_SAVE_ENTRY), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetEntryDestButton",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_SET_ENTRY_DEST), false);
        // Exit gate
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ExitPos1Button",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_EXIT_POS1), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ExitPos2Button",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_EXIT_POS2), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveExitButton",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_SAVE_EXIT), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetExitDestButton",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_SET_EXIT_DEST), false);
        // Navigation
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_BACK), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_CLOSE), false);
    }

    private void populateFields(UICommandBuilder cmd) {
        // Entry gate
        populateGateSection(cmd, "#EntryBoundsText", "#EntryDestText", "#EntryStatusText",
            mineConfigStore.getEntryMinX(), mineConfigStore.getEntryMinY(), mineConfigStore.getEntryMinZ(),
            mineConfigStore.getEntryMaxX(), mineConfigStore.getEntryMaxY(), mineConfigStore.getEntryMaxZ(),
            mineConfigStore.getEntryDestX(), mineConfigStore.getEntryDestY(), mineConfigStore.getEntryDestZ(),
            "Entry");

        // Exit gate
        populateGateSection(cmd, "#ExitBoundsText", "#ExitDestText", "#ExitStatusText",
            mineConfigStore.getExitMinX(), mineConfigStore.getExitMinY(), mineConfigStore.getExitMinZ(),
            mineConfigStore.getExitMaxX(), mineConfigStore.getExitMaxY(), mineConfigStore.getExitMaxZ(),
            mineConfigStore.getExitDestX(), mineConfigStore.getExitDestY(), mineConfigStore.getExitDestZ(),
            "Exit");
    }

    private void populateGateSection(UICommandBuilder cmd, String boundsId, String destId, String statusId,
                                     double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ,
                                     double destX, double destY, double destZ, String label) {
        boolean hasBounds = minX != 0 || minY != 0 || minZ != 0 || maxX != 0 || maxY != 0 || maxZ != 0;
        boolean hasDest = destX != 0 || destY != 0 || destZ != 0;

        cmd.set(boundsId + ".Text", hasBounds
            ? String.format("AABB: (%.1f, %.1f, %.1f) to (%.1f, %.1f, %.1f)", minX, minY, minZ, maxX, maxY, maxZ)
            : "AABB: Not set");
        cmd.set(destId + ".Text", hasDest
            ? String.format("Dest: (%.1f, %.1f, %.1f)", destX, destY, destZ)
            : "Dest: Not set");
        cmd.set(statusId + ".Text", (hasBounds && hasDest)
            ? label + ": CONFIGURED"
            : label + ": NOT CONFIGURED");
    }

    private void updateAllLabels() {
        UICommandBuilder cmd = new UICommandBuilder();
        populateFields(cmd);
        sendUpdate(cmd, null, false);
    }

    private static String formatPos(double x, double y, double z) {
        return String.format("%.2f, %.2f, %.2f", x, y, z);
    }

    public static class GateData {
        static final String KEY_BUTTON = "Button";
        static final String BUTTON_ENTRY_POS1 = "EntryPos1";
        static final String BUTTON_ENTRY_POS2 = "EntryPos2";
        static final String BUTTON_SAVE_ENTRY = "SaveEntry";
        static final String BUTTON_SET_ENTRY_DEST = "SetEntryDest";
        static final String BUTTON_EXIT_POS1 = "ExitPos1";
        static final String BUTTON_EXIT_POS2 = "ExitPos2";
        static final String BUTTON_SAVE_EXIT = "SaveExit";
        static final String BUTTON_SET_EXIT_DEST = "SetExitDest";
        static final String BUTTON_BACK = "Back";
        static final String BUTTON_CLOSE = "Close";

        public static final BuilderCodec<GateData> CODEC = BuilderCodec.<GateData>builder(GateData.class, GateData::new)
            .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, value) -> data.button = value, data -> data.button)
            .build();

        private String button;
    }
}
