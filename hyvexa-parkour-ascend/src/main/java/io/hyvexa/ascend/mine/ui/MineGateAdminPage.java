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

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MineGateAdminPage extends InteractiveCustomUIPage<MineGateAdminPage.GateData> {

    private static final Map<UUID, double[]> gatePos1 = new ConcurrentHashMap<>();
    private static final Map<UUID, double[]> gatePos2 = new ConcurrentHashMap<>();

    private final PlayerRef playerRef;
    private final MineConfigStore mineConfigStore;

    public MineGateAdminPage(@Nonnull PlayerRef playerRef, MineConfigStore mineConfigStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, GateData.CODEC);
        this.playerRef = playerRef;
        this.mineConfigStore = mineConfigStore;
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
        if (data.button == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        switch (data.button) {
            case GateData.BUTTON_CLOSE -> this.close();
            case GateData.BUTTON_POS1 -> handlePos1(ref, store, player);
            case GateData.BUTTON_POS2 -> handlePos2(ref, store, player);
            case GateData.BUTTON_SAVE_GATE -> handleSaveGate(player);
            case GateData.BUTTON_SET_FALLBACK -> handleSetFallback(ref, store, player);
            case GateData.BUTTON_BACK -> handleBack(ref, store);
            default -> {
            }
        }
    }

    private void handlePos1(Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("Unable to read player position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        gatePos1.put(playerRef.getUuid(), new double[]{pos.getX(), pos.getY(), pos.getZ()});
        player.sendMessage(Message.raw("Gate Pos1 set: " + String.format("%.2f, %.2f, %.2f", pos.getX(), pos.getY(), pos.getZ())));
        updateGateBoundsLabel();
    }

    private void handlePos2(Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("Unable to read player position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        gatePos2.put(playerRef.getUuid(), new double[]{pos.getX(), pos.getY(), pos.getZ()});
        player.sendMessage(Message.raw("Gate Pos2 set: " + String.format("%.2f, %.2f, %.2f", pos.getX(), pos.getY(), pos.getZ())));
        updateGateBoundsLabel();
    }

    private void handleSaveGate(Player player) {
        UUID uuid = playerRef.getUuid();
        double[] p1 = gatePos1.get(uuid);
        double[] p2 = gatePos2.get(uuid);
        if (p1 == null || p2 == null) {
            player.sendMessage(Message.raw("Set both Pos1 and Pos2 first."));
            return;
        }
        double minX = Math.min(p1[0], p2[0]);
        double minY = Math.min(p1[1], p2[1]);
        double minZ = Math.min(p1[2], p2[2]);
        double maxX = Math.max(p1[0], p2[0]);
        double maxY = Math.max(p1[1], p2[1]);
        double maxZ = Math.max(p1[2], p2[2]);
        mineConfigStore.saveGate(minX, minY, minZ, maxX, maxY, maxZ,
            mineConfigStore.getFallbackX(), mineConfigStore.getFallbackY(), mineConfigStore.getFallbackZ(),
            mineConfigStore.getFallbackRotX(), mineConfigStore.getFallbackRotY(), mineConfigStore.getFallbackRotZ());
        player.sendMessage(Message.raw("Gate saved!"));
        player.sendMessage(Message.raw("  Bounds: " + String.format("(%.1f, %.1f, %.1f) to (%.1f, %.1f, %.1f)",
            minX, minY, minZ, maxX, maxY, maxZ)));
        updateStatusLabels();
    }

    private void handleSetFallback(Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
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
        mineConfigStore.saveGate(
            mineConfigStore.getGateMinX(), mineConfigStore.getGateMinY(), mineConfigStore.getGateMinZ(),
            mineConfigStore.getGateMaxX(), mineConfigStore.getGateMaxY(), mineConfigStore.getGateMaxZ(),
            pos.getX(), pos.getY(), pos.getZ(),
            rot.getX(), rot.getY(), rot.getZ());
        player.sendMessage(Message.raw("Fallback position set!"));
        player.sendMessage(Message.raw("  Pos: " + String.format("%.2f, %.2f, %.2f", pos.getX(), pos.getY(), pos.getZ())));
        updateStatusLabels();
    }

    private void handleBack(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pRef == null) return;
        player.getPageManager().openCustomPage(ref, store, new MineAdminPage(pRef, mineConfigStore));
    }

    private void bindEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GatePos1Button",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_POS1), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GatePos2Button",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_POS2), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveGateButton",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_SAVE_GATE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetFallbackButton",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_SET_FALLBACK), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_BACK), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(GateData.KEY_BUTTON, GateData.BUTTON_CLOSE), false);
    }

    private void populateFields(UICommandBuilder commandBuilder) {
        // Gate bounds
        double gMinX = mineConfigStore.getGateMinX();
        double gMinY = mineConfigStore.getGateMinY();
        double gMinZ = mineConfigStore.getGateMinZ();
        double gMaxX = mineConfigStore.getGateMaxX();
        double gMaxY = mineConfigStore.getGateMaxY();
        double gMaxZ = mineConfigStore.getGateMaxZ();
        boolean hasGate = gMinX != 0 || gMinY != 0 || gMinZ != 0 || gMaxX != 0 || gMaxY != 0 || gMaxZ != 0;
        if (hasGate) {
            commandBuilder.set("#GateBoundsText.Text", String.format("Gate: (%.1f, %.1f, %.1f) to (%.1f, %.1f, %.1f)",
                gMinX, gMinY, gMinZ, gMaxX, gMaxY, gMaxZ));
        } else {
            commandBuilder.set("#GateBoundsText.Text", "Gate: Not set");
        }

        // Fallback
        double fbX = mineConfigStore.getFallbackX();
        double fbY = mineConfigStore.getFallbackY();
        double fbZ = mineConfigStore.getFallbackZ();
        boolean hasFallback = fbX != 0 || fbY != 0 || fbZ != 0;
        if (hasFallback) {
            commandBuilder.set("#FallbackText.Text", String.format("Fallback: (%.1f, %.1f, %.1f)", fbX, fbY, fbZ));
        } else {
            commandBuilder.set("#FallbackText.Text", "Fallback: Not set");
        }

        // Status
        String status = (hasGate && hasFallback) ? "Gate: CONFIGURED" : "Gate: NOT CONFIGURED";
        commandBuilder.set("#GateStatusText.Text", status);
    }

    private void updateGateBoundsLabel() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UUID uuid = playerRef.getUuid();
        double[] p1 = gatePos1.get(uuid);
        double[] p2 = gatePos2.get(uuid);
        StringBuilder sb = new StringBuilder("Gate: ");
        if (p1 != null) {
            sb.append(String.format("Pos1(%.1f, %.1f, %.1f)", p1[0], p1[1], p1[2]));
        } else {
            sb.append("Pos1(-)");
        }
        sb.append(" ");
        if (p2 != null) {
            sb.append(String.format("Pos2(%.1f, %.1f, %.1f)", p2[0], p2[1], p2[2]));
        } else {
            sb.append("Pos2(-)");
        }
        commandBuilder.set("#GateBoundsText.Text", sb.toString());
        sendUpdate(commandBuilder, null, false);
    }

    private void updateStatusLabels() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        populateFields(commandBuilder);
        sendUpdate(commandBuilder, null, false);
    }

    public static class GateData {
        static final String KEY_BUTTON = "Button";
        static final String BUTTON_POS1 = "GatePos1";
        static final String BUTTON_POS2 = "GatePos2";
        static final String BUTTON_SAVE_GATE = "SaveGate";
        static final String BUTTON_SET_FALLBACK = "SetFallback";
        static final String BUTTON_BACK = "Back";
        static final String BUTTON_CLOSE = "Close";

        public static final BuilderCodec<GateData> CODEC = BuilderCodec.<GateData>builder(GateData.class, GateData::new)
            .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, value) -> data.button = value, data -> data.button)
            .build();

        private String button;
    }
}
