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
import io.hyvexa.purge.data.PurgeVariantConfig;
import io.hyvexa.purge.manager.PurgeInstanceManager;
import io.hyvexa.purge.manager.PurgeVariantConfigManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;

import javax.annotation.Nonnull;
import java.util.List;

public class PurgeVariantAdminPage extends InteractiveCustomUIPage<PurgeVariantAdminPage.PurgeVariantAdminData> {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_ADD = "Add";
    private static final String BUTTON_DELETE_PREFIX = "Delete:";
    private static final String BUTTON_EDIT_PREFIX = "Edit:";
    private static final String BUTTON_ADJUST_PREFIX = "Adjust:";
    private static final String BUTTON_NPC_TYPE_PREFIX = "NpcType:";
    private static final String BUTTON_SAVE_NAME = "SaveName";
    private static final String[] NPC_TYPES = {
            "Zombie",
            "Zombie_Burnt",
            "Zombie_Frost",
            "Zombie_Sand",
            "Zombie_Aberrant",
            "Wolf_Outlander_Priest",
            "Wolf_Outlander_Sorcerer"
    };

    private final PurgeVariantConfigManager variantConfigManager;
    private final PurgeWaveConfigManager waveConfigManager;
    private final PurgeInstanceManager instanceManager;
    private final PurgeWeaponConfigManager weaponConfigManager;
    private String editingVariantKey = "";
    private String variantNameInput = "";

    public PurgeVariantAdminPage(@Nonnull PlayerRef playerRef,
                                  PurgeVariantConfigManager variantConfigManager,
                                  PurgeWaveConfigManager waveConfigManager,
                                  PurgeInstanceManager instanceManager,
                                  PurgeWeaponConfigManager weaponConfigManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeVariantAdminData.CODEC);
        this.variantConfigManager = variantConfigManager;
        this.waveConfigManager = waveConfigManager;
        this.instanceManager = instanceManager;
        this.weaponConfigManager = weaponConfigManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder,
                      @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_VariantAdmin.ui");
        bindStaticEvents(uiEventBuilder);
        buildVariantList(uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PurgeVariantAdminData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getVariantName() != null) {
            variantNameInput = data.getVariantName();
        }
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
        if (button.startsWith(BUTTON_DELETE_PREFIX)) {
            handleDelete(button.substring(BUTTON_DELETE_PREFIX.length()), ref, store);
            return;
        }
        if (button.startsWith(BUTTON_EDIT_PREFIX)) {
            handleEdit(button.substring(BUTTON_EDIT_PREFIX.length()));
            return;
        }
        if (button.startsWith(BUTTON_ADJUST_PREFIX)) {
            handleAdjust(button.substring(BUTTON_ADJUST_PREFIX.length()), ref, store);
            return;
        }
        if (button.startsWith(BUTTON_NPC_TYPE_PREFIX)) {
            handleNpcTypeCycle(button.substring(BUTTON_NPC_TYPE_PREFIX.length()));
            return;
        }
        if (BUTTON_SAVE_NAME.equals(button)) {
            handleSaveName(ref, store);
        }
    }

    private void handleAdd(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (!variantConfigManager.isPersistenceAvailable()) {
            if (player != null) {
                player.sendMessage(Message.raw("Database unavailable."));
            }
            return;
        }
        // Generate unique key
        int index = variantConfigManager.getVariantCount() + 1;
        String key = "CUSTOM_" + index;
        while (variantConfigManager.getVariant(key) != null) {
            index++;
            key = "CUSTOM_" + index;
        }
        String label = "Custom " + index;
        boolean added = variantConfigManager.addVariant(key, label, 50, 20f, 1.0);
        if (player != null) {
            if (added) {
                player.sendMessage(Message.raw("Added variant: " + label + " (" + key + ")"));
            } else {
                player.sendMessage(Message.raw("Failed to add variant."));
            }
        }
        sendRefresh();
    }

    private void handleDelete(String key, Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (variantConfigManager.getVariantCount() <= 1) {
            if (player != null) {
                player.sendMessage(Message.raw("Cannot delete the last variant."));
            }
            return;
        }
        boolean removed = variantConfigManager.removeVariant(key);
        if (removed && key.equals(editingVariantKey)) {
            editingVariantKey = "";
            variantNameInput = "";
        }
        if (player != null) {
            if (removed) {
                player.sendMessage(Message.raw("Removed variant: " + key));
            } else {
                player.sendMessage(Message.raw("Failed to remove variant."));
            }
        }
        sendRefresh();
    }

    private void handleNpcTypeCycle(String key) {
        PurgeVariantConfig config = variantConfigManager.getVariant(key);
        if (config == null) {
            return;
        }
        String current = config.effectiveNpcType();
        int currentIndex = -1;
        for (int i = 0; i < NPC_TYPES.length; i++) {
            if (NPC_TYPES[i].equals(current)) {
                currentIndex = i;
                break;
            }
        }
        String next = NPC_TYPES[(currentIndex + 1) % NPC_TYPES.length];
        if (variantConfigManager.setNpcType(key, next)) {
            sendRefresh();
        }
    }

    private void handleEdit(String key) {
        PurgeVariantConfig config = variantConfigManager.getVariant(key);
        if (config == null) {
            return;
        }
        editingVariantKey = key;
        variantNameInput = config.label();
        sendRefresh();
    }

    private void handleSaveName(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (editingVariantKey.isEmpty()) {
            if (player != null) {
                player.sendMessage(Message.raw("Select a variant to rename first."));
            }
            return;
        }
        String trimmed = variantNameInput != null ? variantNameInput.trim() : "";
        if (trimmed.isEmpty()) {
            if (player != null) {
                player.sendMessage(Message.raw("Variant name cannot be empty."));
            }
            return;
        }
        if (trimmed.length() > 64) {
            if (player != null) {
                player.sendMessage(Message.raw("Variant name max length is 64."));
            }
            return;
        }
        if (variantConfigManager.setLabel(editingVariantKey, trimmed)) {
            variantNameInput = trimmed;
            if (player != null) {
                player.sendMessage(Message.raw("Renamed variant " + editingVariantKey + " to " + trimmed + "."));
            }
            sendRefresh();
            return;
        }
        if (player != null) {
            player.sendMessage(Message.raw("Failed to rename variant."));
        }
    }

    private void handleAdjust(String payload, Ref<EntityStore> ref, Store<EntityStore> store) {
        // Format: <key>:<field>:<delta>
        String[] parts = payload.split(":");
        if (parts.length != 3) {
            return;
        }
        String key = parts[0];
        String field = parts[1];
        boolean updated = false;
        try {
            switch (field) {
                case "hp" -> updated = variantConfigManager.adjustHealth(key, Integer.parseInt(parts[2]));
                case "dmg" -> updated = variantConfigManager.adjustDamage(key, Integer.parseInt(parts[2]));
                case "speed" -> updated = variantConfigManager.adjustSpeed(key, Double.parseDouble(parts[2]));
                case "scrap" -> updated = variantConfigManager.adjustScrap(key, Integer.parseInt(parts[2]));
            }
        } catch (NumberFormatException ignored) {
        }
        if (updated) {
            sendRefresh();
        }
    }

    private void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PurgeAdminIndexPage(playerRef, waveConfigManager, instanceManager, weaponConfigManager, variantConfigManager));
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindStaticEvents(eventBuilder);
        buildVariantList(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindStaticEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddVariantButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ADD), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#VariantNameField",
                EventData.of(PurgeVariantAdminData.KEY_VARIANT_NAME, "#VariantNameField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveVariantNameButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SAVE_NAME), false);
    }

    private void buildVariantList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#VariantCards");

        List<PurgeVariantConfig> variants = variantConfigManager.getAllVariants();
        PurgeVariantConfig editingVariant = !editingVariantKey.isEmpty()
                ? variantConfigManager.getVariant(editingVariantKey)
                : null;
        if (editingVariantKey.isEmpty() || editingVariant == null) {
            editingVariantKey = "";
            variantNameInput = "";
            commandBuilder.set("#EditingVariantText.Text", "Select a variant below, then edit and save its name.");
        } else {
            commandBuilder.set("#EditingVariantText.Text", "Editing: " + editingVariant.label() + " (" + editingVariant.key() + ")");
            if (variantNameInput == null || variantNameInput.isBlank()) {
                variantNameInput = editingVariant.label();
            }
        }
        commandBuilder.set("#VariantNameField.Value", variantNameInput != null ? variantNameInput : "");

        commandBuilder.set("#VariantCount.Text", variants.size() + " variant" + (variants.size() == 1 ? "" : "s"));

        if (variants.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No variants configured.");
            return;
        }
        commandBuilder.set("#EmptyText.Text", "");

        for (int i = 0; i < variants.size(); i++) {
            PurgeVariantConfig v = variants.get(i);
            String root = "#VariantCards[" + i + "]";

            commandBuilder.append("#VariantCards", "Pages/Purge_VariantEntry.ui");
            commandBuilder.set(root + " #VariantLabel.Text", v.label());
            commandBuilder.set(root + " #VariantKey.Text", v.key());
            commandBuilder.set(root + " #HpValue.Text", String.valueOf(v.baseHealth()));
            commandBuilder.set(root + " #DmgValue.Text", String.valueOf(Math.round(v.baseDamage())));
            commandBuilder.set(root + " #SpeedValue.Text", Math.round(v.speedMultiplier() * 100) + "%");
            commandBuilder.set(root + " #ScrapValue.Text", String.valueOf(v.scrapReward()));

            // Delete
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #DeleteButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_DELETE_PREFIX + v.key()), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #EditNameButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_EDIT_PREFIX + v.key()), false);

            // HP adjust
            bindAdjust(eventBuilder, root + " #HpMinusButton", v.key(), "hp", "-10");
            bindAdjust(eventBuilder, root + " #HpPlusButton", v.key(), "hp", "10");

            // DMG adjust
            bindAdjust(eventBuilder, root + " #DmgMinusButton", v.key(), "dmg", "-10");
            bindAdjust(eventBuilder, root + " #DmgPlusButton", v.key(), "dmg", "10");

            // Speed adjust
            bindAdjust(eventBuilder, root + " #SpeedMinusButton", v.key(), "speed", "-0.1");
            bindAdjust(eventBuilder, root + " #SpeedPlusButton", v.key(), "speed", "0.1");

            // Scrap adjust
            bindAdjust(eventBuilder, root + " #ScrapMinusButton", v.key(), "scrap", "-10");
            bindAdjust(eventBuilder, root + " #ScrapPlusButton", v.key(), "scrap", "10");

            // NPC type
            commandBuilder.set(root + " #NpcTypeButton.Text", v.effectiveNpcType());
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #NpcTypeButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NPC_TYPE_PREFIX + v.key()), false);
        }
    }

    private void bindAdjust(UIEventBuilder eventBuilder, String target,
                             String key, String field, String delta) {
        String payload = BUTTON_ADJUST_PREFIX + key + ":" + field + ":" + delta;
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                target, EventData.of(ButtonEventData.KEY_BUTTON, payload), false);
    }

    public static class PurgeVariantAdminData extends ButtonEventData {
        static final String KEY_VARIANT_NAME = "@VariantName";

        public static final BuilderCodec<PurgeVariantAdminData> CODEC =
                BuilderCodec.<PurgeVariantAdminData>builder(PurgeVariantAdminData.class, PurgeVariantAdminData::new)
                        .addField(new KeyedCodec<>(ButtonEventData.KEY_BUTTON, Codec.STRING),
                                (data, value) -> data.button = value, data -> data.button)
                        .addField(new KeyedCodec<>(KEY_VARIANT_NAME, Codec.STRING),
                                (data, value) -> data.variantName = value, data -> data.variantName)
                        .build();

        private String button;
        private String variantName;

        @Override
        public String getButton() {
            return button;
        }

        public String getVariantName() {
            return variantName;
        }
    }
}
