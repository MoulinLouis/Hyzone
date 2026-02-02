package io.hyvexa.ascend.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.AscendSettingsStore;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;

public class AscendAdminCoinsPage extends InteractiveCustomUIPage<AscendAdminCoinsPage.CoinsData> {

    private String amountInput = "";

    public AscendAdminCoinsPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, CoinsData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_AdminCoins.ui");
        bindEvents(eventBuilder);
        populateFields(commandBuilder, store, ref);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull CoinsData data) {
        if (data.amount != null) {
            amountInput = data.amount;
        }
        if (data.button == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        switch (data.button) {
            case CoinsData.BUTTON_CLOSE -> this.close();
            case CoinsData.BUTTON_BACK -> player.getPageManager().openCustomPage(ref, store, new AscendAdminPanelPage(playerRef));
            case CoinsData.BUTTON_ADD -> applyCoins(player, playerRef, store, true);
            case CoinsData.BUTTON_REMOVE -> applyCoins(player, playerRef, store, false);
            case CoinsData.BUTTON_RESET_PROGRESS -> resetProgress(player, playerRef, store);
            case CoinsData.BUTTON_SET_SPAWN_LOCATION -> setSpawnLocation(ref, store, player);
            case CoinsData.BUTTON_SET_NPC_LOCATION -> setNpcLocation(ref, store, player);
            default -> {
            }
        }
    }

    private void bindEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CoinsAmountField",
            EventData.of(CoinsData.KEY_AMOUNT, "#CoinsAmountField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddButton",
            EventData.of(CoinsData.KEY_BUTTON, CoinsData.BUTTON_ADD), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RemoveButton",
            EventData.of(CoinsData.KEY_BUTTON, CoinsData.BUTTON_REMOVE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ResetProgressButton",
            EventData.of(CoinsData.KEY_BUTTON, CoinsData.BUTTON_RESET_PROGRESS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetSpawnLocationButton",
            EventData.of(CoinsData.KEY_BUTTON, CoinsData.BUTTON_SET_SPAWN_LOCATION), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetNpcLocationButton",
            EventData.of(CoinsData.KEY_BUTTON, CoinsData.BUTTON_SET_NPC_LOCATION), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of(CoinsData.KEY_BUTTON, CoinsData.BUTTON_BACK), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(CoinsData.KEY_BUTTON, CoinsData.BUTTON_CLOSE), false);
    }

    private void resetProgress(Player player, PlayerRef playerRef, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        AscendPlayerStore playerStore = plugin != null ? plugin.getPlayerStore() : null;
        AscendMapStore mapStore = plugin != null ? plugin.getMapStore() : null;
        if (playerStore == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        playerStore.resetPlayerProgress(playerRef.getUuid());

        // Unlock the first map (lowest displayOrder) like a new player would have
        if (mapStore != null) {
            AscendMap firstMap = null;
            for (AscendMap map : mapStore.listMaps()) {
                if (firstMap == null || map.getDisplayOrder() < firstMap.getDisplayOrder()) {
                    firstMap = map;
                }
            }
            if (firstMap != null) {
                playerStore.setMapUnlocked(playerRef.getUuid(), firstMap.getId(), true);
            }
        }

        player.sendMessage(Message.raw("[Ascend] Your Ascend progress has been reset (first map unlocked).")
            .color(SystemMessageUtils.SECONDARY));
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#CurrentCoinsValue.Text", "0");
        sendUpdate(commandBuilder, null, false);
    }

    private void populateFields(UICommandBuilder commandBuilder, Store<EntityStore> store, Ref<EntityStore> ref) {
        commandBuilder.set("#CoinsAmountField.Value", amountInput != null ? amountInput : "");
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            AscendPlayerStore playerStore = ParkourAscendPlugin.getInstance().getPlayerStore();
            double coins = playerStore != null ? playerStore.getCoins(playerRef.getUuid()) : 0.0;
            commandBuilder.set("#CurrentCoinsValue.Text", FormatUtils.formatCoinsForHudDecimal(coins));
        }

        // Location statuses
        AscendSettingsStore settingsStore = ParkourAscendPlugin.getInstance().getSettingsStore();
        if (settingsStore != null && settingsStore.hasSpawnPosition()) {
            Vector3d pos = settingsStore.getSpawnPosition();
            String status = String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ());
            commandBuilder.set("#SpawnLocationStatus.Text", status);
        } else {
            commandBuilder.set("#SpawnLocationStatus.Text", "Not set");
        }
        if (settingsStore != null && settingsStore.hasNpcPosition()) {
            Vector3d pos = settingsStore.getNpcPosition();
            String status = String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ());
            commandBuilder.set("#NpcLocationStatus.Text", status);
        } else {
            commandBuilder.set("#NpcLocationStatus.Text", "Not set");
        }
    }

    private void applyCoins(Player player, PlayerRef playerRef, Store<EntityStore> store, boolean add) {
        long amount = parseAmount(player);
        if (amount <= 0L) {
            return;
        }
        AscendPlayerStore playerStore = ParkourAscendPlugin.getInstance().getPlayerStore();
        if (playerStore == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        if (add) {
            playerStore.addCoins(playerRef.getUuid(), amount);
            player.sendMessage(Message.raw("[Ascend] Added " + amount + " coins.")
                .color(SystemMessageUtils.SUCCESS));
        } else {
            playerStore.addCoins(playerRef.getUuid(), -amount);
            player.sendMessage(Message.raw("[Ascend] Removed " + amount + " coins.")
                .color(SystemMessageUtils.SECONDARY));
        }
        UICommandBuilder commandBuilder = new UICommandBuilder();
        double coins = playerStore.getCoins(playerRef.getUuid());
        commandBuilder.set("#CurrentCoinsValue.Text", FormatUtils.formatCoinsForHudDecimal(coins));
        sendUpdate(commandBuilder, null, false);
    }

    private long parseAmount(Player player) {
        String raw = amountInput != null ? amountInput.trim() : "";
        if (raw.isEmpty()) {
            player.sendMessage(Message.raw("Enter a coin amount."));
            return -1L;
        }
        try {
            long value = Long.parseLong(raw);
            if (value <= 0) {
                player.sendMessage(Message.raw("Amount must be positive."));
                return -1L;
            }
            return value;
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Amount must be a number."));
            return -1L;
        }
    }

    private void setSpawnLocation(Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
        AscendSettingsStore settingsStore = ParkourAscendPlugin.getInstance().getSettingsStore();
        if (settingsStore == null) {
            player.sendMessage(Message.raw("[Ascend] Settings store not available."));
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("[Ascend] Unable to read player position."));
            return;
        }

        Vector3d pos = transform.getPosition();
        Vector3f bodyRot = transform.getRotation();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Vector3f headRot = playerRef != null ? playerRef.getHeadRotation() : null;
        Vector3f rot = headRot != null ? headRot : bodyRot;

        settingsStore.setSpawnPosition(pos.getX(), pos.getY(), pos.getZ(), rot.getX(), rot.getY(), rot.getZ());

        player.sendMessage(Message.raw("[Ascend] Spawn location set!")
            .color(SystemMessageUtils.SUCCESS));
        player.sendMessage(Message.raw("  Pos: " + String.format("%.2f, %.2f, %.2f", pos.getX(), pos.getY(), pos.getZ())));

        // Update UI
        UICommandBuilder commandBuilder = new UICommandBuilder();
        String status = String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ());
        commandBuilder.set("#SpawnLocationStatus.Text", status);
        sendUpdate(commandBuilder, null, false);
    }

    private void setNpcLocation(Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
        AscendSettingsStore settingsStore = ParkourAscendPlugin.getInstance().getSettingsStore();
        if (settingsStore == null) {
            player.sendMessage(Message.raw("[Ascend] Settings store not available."));
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("[Ascend] Unable to read player position."));
            return;
        }

        Vector3d pos = transform.getPosition();
        Vector3f bodyRot = transform.getRotation();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Vector3f headRot = playerRef != null ? playerRef.getHeadRotation() : null;
        Vector3f rot = headRot != null ? headRot : bodyRot;

        settingsStore.setNpcPosition(pos.getX(), pos.getY(), pos.getZ(), rot.getX(), rot.getY(), rot.getZ());

        player.sendMessage(Message.raw("[Ascend] NPC location set!")
            .color(SystemMessageUtils.SUCCESS));
        player.sendMessage(Message.raw("  Pos: " + String.format("%.2f, %.2f, %.2f", pos.getX(), pos.getY(), pos.getZ())));

        // Update UI
        UICommandBuilder commandBuilder = new UICommandBuilder();
        String status = String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ());
        commandBuilder.set("#NpcLocationStatus.Text", status);
        sendUpdate(commandBuilder, null, false);
    }

    public static class CoinsData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_AMOUNT = "@CoinsAmount";
        static final String BUTTON_ADD = "AddCoins";
        static final String BUTTON_REMOVE = "RemoveCoins";
        static final String BUTTON_RESET_PROGRESS = "ResetProgress";
        static final String BUTTON_SET_SPAWN_LOCATION = "SetSpawnLocation";
        static final String BUTTON_SET_NPC_LOCATION = "SetNpcLocation";
        static final String BUTTON_BACK = "Back";
        static final String BUTTON_CLOSE = "Close";

        public static final BuilderCodec<CoinsData> CODEC = BuilderCodec.<CoinsData>builder(CoinsData.class, CoinsData::new)
            .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, value) -> data.button = value, data -> data.button)
            .addField(new KeyedCodec<>(KEY_AMOUNT, Codec.STRING), (data, value) -> data.amount = value, data -> data.amount)
            .build();

        private String button;
        private String amount;
    }
}
