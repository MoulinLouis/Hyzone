package io.hyvexa.purge.ui;

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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.purge.data.PurgeWaveDefinition;
import io.hyvexa.purge.data.PurgeZombieVariant;
import io.hyvexa.purge.manager.PurgeSettingsManager;
import io.hyvexa.purge.manager.PurgeSpawnPointManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;

import javax.annotation.Nonnull;
import java.util.List;

public class PurgeWaveAdminPage extends InteractiveCustomUIPage<PurgeWaveAdminPage.PurgeWaveAdminData> {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_ADD = "Add";
    private static final String BUTTON_CLEAR = "Clear";
    private static final String BUTTON_DELETE_PREFIX = "Delete:";
    private static final String BUTTON_ADJUST_PREFIX = "Adjust:";
    private static final String BUTTON_SPAWN_PREFIX = "Spawn:";

    private final PurgeSettingsManager settingsManager;
    private final PurgeSpawnPointManager spawnPointManager;
    private final PurgeWaveConfigManager waveConfigManager;

    public PurgeWaveAdminPage(@Nonnull PlayerRef playerRef,
                              PurgeSpawnPointManager spawnPointManager,
                              PurgeWaveConfigManager waveConfigManager,
                              PurgeSettingsManager settingsManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeWaveAdminData.CODEC);
        this.settingsManager = settingsManager;
        this.spawnPointManager = spawnPointManager;
        this.waveConfigManager = waveConfigManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder,
                      @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_WaveAdmin.ui");
        bindStaticEvents(uiEventBuilder);
        buildWaveList(uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PurgeWaveAdminData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) {
            return;
        }

        if (BUTTON_BACK.equals(button)) {
            openIndex(ref, store);
            return;
        }
        if (BUTTON_ADD.equals(button)) {
            handleAdd(ref, store);
            return;
        }
        if (BUTTON_CLEAR.equals(button)) {
            handleClear(ref, store);
            return;
        }
        if (button.startsWith(BUTTON_DELETE_PREFIX)) {
            handleDelete(button.substring(BUTTON_DELETE_PREFIX.length()), ref, store);
            return;
        }
        if (button.startsWith(BUTTON_ADJUST_PREFIX)) {
            handleAdjust(button.substring(BUTTON_ADJUST_PREFIX.length()), ref, store);
            return;
        }
        if (button.startsWith(BUTTON_SPAWN_PREFIX)) {
            handleSpawnAdjust(button.substring(BUTTON_SPAWN_PREFIX.length()), ref, store);
        }
    }

    private void handleAdd(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        int waveNumber = waveConfigManager.addWave();
        if (player != null) {
            if (waveNumber > 0) {
                player.sendMessage(Message.raw("Added wave " + waveNumber + " (default: slow 0, normal 5, fast 0)."));
            } else if (!waveConfigManager.isPersistenceAvailable()) {
                player.sendMessage(Message.raw(waveConfigManager.getPersistenceDisabledMessage()));
            } else {
                player.sendMessage(Message.raw("Failed to add wave."));
            }
        }
        sendRefresh();
    }

    private void handleClear(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (!waveConfigManager.isPersistenceAvailable()) {
            if (player != null) {
                player.sendMessage(Message.raw(waveConfigManager.getPersistenceDisabledMessage()));
            }
            return;
        }
        waveConfigManager.clearAll();
        if (player != null) {
            player.sendMessage(Message.raw("Cleared all wave definitions."));
        }
        sendRefresh();
    }

    private void handleDelete(String rawWave, Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        int waveNumber;
        try {
            waveNumber = Integer.parseInt(rawWave);
        } catch (NumberFormatException e) {
            if (player != null) {
                player.sendMessage(Message.raw("Invalid wave number."));
            }
            return;
        }

        boolean removed = waveConfigManager.removeWave(waveNumber);
        if (player != null) {
            if (removed) {
                player.sendMessage(Message.raw("Removed wave " + waveNumber + "."));
            } else if (!waveConfigManager.isPersistenceAvailable()) {
                player.sendMessage(Message.raw(waveConfigManager.getPersistenceDisabledMessage()));
            } else {
                player.sendMessage(Message.raw("Wave " + waveNumber + " not found."));
            }
        }
        sendRefresh();
    }

    private void handleSpawnAdjust(String payload, Ref<EntityStore> ref, Store<EntityStore> store) {
        String[] parts = payload.split(":");
        if (parts.length != 3) {
            return;
        }

        int waveNumber;
        int delta;
        try {
            waveNumber = Integer.parseInt(parts[0]);
            delta = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }

        String field = parts[1];
        boolean updated = switch (field) {
            case "delay" -> waveConfigManager.adjustSpawnDelay(waveNumber, delta);
            case "batch" -> waveConfigManager.adjustBatchSize(waveNumber, delta);
            default -> false;
        };

        if (updated) {
            sendRefresh();
        } else if (!waveConfigManager.isPersistenceAvailable()) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(Message.raw(waveConfigManager.getPersistenceDisabledMessage()));
            }
        }
    }

    private void handleAdjust(String payload, Ref<EntityStore> ref, Store<EntityStore> store) {
        String[] parts = payload.split(":");
        if (parts.length != 3) {
            return;
        }

        int waveNumber;
        int delta;
        try {
            waveNumber = Integer.parseInt(parts[0]);
            delta = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }

        PurgeZombieVariant variant = PurgeZombieVariant.fromKey(parts[1]);
        if (variant == null) {
            return;
        }

        if (waveConfigManager.adjustVariantCount(waveNumber, variant, delta)) {
            sendRefresh();
        } else if (!waveConfigManager.isPersistenceAvailable()) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(Message.raw(waveConfigManager.getPersistenceDisabledMessage()));
            }
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

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindStaticEvents(eventBuilder);
        buildWaveList(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindStaticEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddWaveButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ADD), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClearButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLEAR), false);
    }

    private void buildWaveList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#WaveCards");

        List<PurgeWaveDefinition> waves = waveConfigManager.getAllWaves();
        int spawnCount = spawnPointManager.getAll().size();

        commandBuilder.set("#WaveCount.Text", waves.size() + " configured wave" + (waves.size() == 1 ? "" : "s"));
        commandBuilder.set("#SpawnCount.Text", spawnCount + " spawn point" + (spawnCount == 1 ? "" : "s"));

        if (waves.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No waves configured. Add at least one wave.");
            return;
        }
        commandBuilder.set("#EmptyText.Text", "");

        for (int i = 0; i < waves.size(); i++) {
            PurgeWaveDefinition wave = waves.get(i);
            String root = "#WaveCards[" + i + "]";

            commandBuilder.append("#WaveCards", "Pages/Purge_WaveEntry.ui");
            commandBuilder.set(root + " #WaveLabel.Text", "Wave " + wave.waveNumber());
            commandBuilder.set(root + " #TotalLabel.Text", "Total: " + wave.totalCount());
            commandBuilder.set(root + " #SlowCount.Text", String.valueOf(Math.max(0, wave.slowCount())));
            commandBuilder.set(root + " #NormalCount.Text", String.valueOf(Math.max(0, wave.normalCount())));
            commandBuilder.set(root + " #FastCount.Text", String.valueOf(Math.max(0, wave.fastCount())));
            commandBuilder.set(root + " #DelayValue.Text", wave.spawnDelayMs() + "ms");
            commandBuilder.set(root + " #BatchValue.Text", String.valueOf(wave.spawnBatchSize()));

            bindAdjust(eventBuilder, root + " #SlowMinusButton", wave.waveNumber(), PurgeZombieVariant.SLOW, -1);
            bindAdjust(eventBuilder, root + " #SlowPlusButton", wave.waveNumber(), PurgeZombieVariant.SLOW, 1);
            bindAdjust(eventBuilder, root + " #NormalMinusButton", wave.waveNumber(), PurgeZombieVariant.NORMAL, -1);
            bindAdjust(eventBuilder, root + " #NormalPlusButton", wave.waveNumber(), PurgeZombieVariant.NORMAL, 1);
            bindAdjust(eventBuilder, root + " #FastMinusButton", wave.waveNumber(), PurgeZombieVariant.FAST, -1);
            bindAdjust(eventBuilder, root + " #FastPlusButton", wave.waveNumber(), PurgeZombieVariant.FAST, 1);

            bindSpawnAdjust(eventBuilder, root + " #DelayMinusButton", wave.waveNumber(), "delay", -100);
            bindSpawnAdjust(eventBuilder, root + " #DelayPlusButton", wave.waveNumber(), "delay", 100);
            bindSpawnAdjust(eventBuilder, root + " #BatchMinusButton", wave.waveNumber(), "batch", -1);
            bindSpawnAdjust(eventBuilder, root + " #BatchPlusButton", wave.waveNumber(), "batch", 1);

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #DeleteButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_DELETE_PREFIX + wave.waveNumber()), false);
        }
    }

    private void bindSpawnAdjust(UIEventBuilder eventBuilder,
                              String target,
                              int waveNumber,
                              String field,
                              int delta) {
        String payload = BUTTON_SPAWN_PREFIX + waveNumber + ":" + field + ":" + delta;
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                target,
                EventData.of(ButtonEventData.KEY_BUTTON, payload), false);
    }

    private void bindAdjust(UIEventBuilder eventBuilder,
                            String target,
                            int waveNumber,
                            PurgeZombieVariant variant,
                            int delta) {
        String payload = BUTTON_ADJUST_PREFIX + waveNumber + ":" + variant.name() + ":" + delta;
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                target,
                EventData.of(ButtonEventData.KEY_BUTTON, payload), false);
    }

    public static class PurgeWaveAdminData extends ButtonEventData {
        public static final BuilderCodec<PurgeWaveAdminData> CODEC =
                BuilderCodec.<PurgeWaveAdminData>builder(PurgeWaveAdminData.class, PurgeWaveAdminData::new)
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
