package io.hyvexa.parkour.ui;

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
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.parkour.data.GlobalMessageStore;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;

import javax.annotation.Nonnull;
import java.util.List;

public class GlobalMessageAdminPage extends InteractiveCustomUIPage<GlobalMessageAdminPage.GlobalMessageData> {

    private static final String BUTTON_BACK = "BackButton";
    private static final String BUTTON_ADD = "AddMessage";
    private static final String BUTTON_SAVE_INTERVAL = "SaveInterval";
    private static final String BUTTON_REMOVE_PREFIX = "Remove:";

    private final GlobalMessageStore globalMessageStore;
    private String messageInput = "";
    private String intervalInput = "";

    public GlobalMessageAdminPage(@Nonnull PlayerRef playerRef, GlobalMessageStore globalMessageStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, GlobalMessageData.CODEC);
        this.globalMessageStore = globalMessageStore;
        this.intervalInput = formatMinutes(getIntervalMinutes());
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_GlobalMessageAdmin.ui");
        bindEvents(uiEventBuilder);
        populateFields(uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull GlobalMessageData data) {
        super.handleDataEvent(ref, store, data);
        if (data.message != null) {
            messageInput = data.message.trim();
        }
        if (data.intervalMinutes != null) {
            intervalInput = data.intervalMinutes.trim();
        }
        if (data.button == null) {
            return;
        }
        if (BUTTON_BACK.equals(data.button)) {
            openIndex(ref, store);
            return;
        }
        if (BUTTON_ADD.equals(data.button)) {
            handleAdd(ref, store);
            return;
        }
        if (BUTTON_SAVE_INTERVAL.equals(data.button)) {
            handleSaveInterval(ref, store);
            return;
        }
        if (data.button.startsWith(BUTTON_REMOVE_PREFIX)) {
            handleRemove(ref, store, data.button.substring(BUTTON_REMOVE_PREFIX.length()));
        }
    }

    private void handleAdd(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || globalMessageStore == null) {
            return;
        }
        String trimmed = messageInput.trim();
        if (trimmed.isEmpty()) {
            player.sendMessage(Message.raw("Enter a message to add."));
            return;
        }
        if (!globalMessageStore.addMessage(trimmed)) {
            player.sendMessage(Message.raw("Could not add message."));
            return;
        }
        messageInput = "";
        refreshAnnouncements();
        sendRefresh();
    }

    private void handleSaveInterval(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || globalMessageStore == null) {
            return;
        }
        long minutes;
        try {
            minutes = Long.parseLong(intervalInput);
        } catch (NumberFormatException ex) {
            player.sendMessage(Message.raw("Enter a valid number of minutes."));
            return;
        }
        if (minutes <= 0) {
            player.sendMessage(Message.raw("Interval must be at least 1 minute."));
            return;
        }
        globalMessageStore.setIntervalMinutes(minutes);
        intervalInput = formatMinutes(globalMessageStore.getIntervalMinutes());
        refreshAnnouncements();
        player.sendMessage(Message.raw("Updated global message interval to " + intervalInput + " minutes."));
        sendRefresh();
    }

    private void handleRemove(Ref<EntityStore> ref, Store<EntityStore> store, String rawIndex) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || globalMessageStore == null) {
            return;
        }
        int index;
        try {
            index = Integer.parseInt(rawIndex);
        } catch (NumberFormatException ex) {
            player.sendMessage(Message.raw("Invalid message index."));
            return;
        }
        if (!globalMessageStore.removeMessage(index)) {
            player.sendMessage(Message.raw("Message not found."));
            return;
        }
        refreshAnnouncements();
        sendRefresh();
    }

    private void refreshAnnouncements() {
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin != null) {
            plugin.refreshChatAnnouncements();
        }
    }

    private void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        MapStore mapStore = plugin.getMapStore();
        ProgressStore progressStore = plugin.getProgressStore();
        player.getPageManager().openCustomPage(ref, store,
                new AdminIndexPage(playerRef, mapStore, progressStore, plugin.getSettingsStore(),
                        plugin.getPlayerCountStore()));
    }

    private void populateFields(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.set("#MessageField.Value", messageInput);
        commandBuilder.set("#IntervalField.Value", intervalInput);
        buildMessageList(commandBuilder, eventBuilder);
    }

    private void buildMessageList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#MessageCards");
        List<String> messages = globalMessageStore != null ? globalMessageStore.getMessages() : List.of();
        if (messages.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No global messages yet.");
            return;
        }
        commandBuilder.set("#EmptyText.Text", "");
        int index = 0;
        for (String message : messages) {
            commandBuilder.append("#MessageCards", "Pages/Parkour_GlobalMessageEntry.ui");
            commandBuilder.set("#MessageCards[" + index + "] #MessageIndex.Text", String.valueOf(index + 1));
            commandBuilder.set("#MessageCards[" + index + "] #MessageText.Text", message);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#MessageCards[" + index + "] #RemoveButton",
                    EventData.of(GlobalMessageData.KEY_BUTTON, BUTTON_REMOVE_PREFIX + index), false);
            index++;
        }
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindEvents(eventBuilder);
        populateFields(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(GlobalMessageData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MessageField",
                EventData.of(GlobalMessageData.KEY_MESSAGE, "#MessageField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#IntervalField",
                EventData.of(GlobalMessageData.KEY_INTERVAL, "#IntervalField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MessageAddButton",
                EventData.of(GlobalMessageData.KEY_BUTTON, BUTTON_ADD), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#IntervalSaveButton",
                EventData.of(GlobalMessageData.KEY_BUTTON, BUTTON_SAVE_INTERVAL), false);
    }

    private long getIntervalMinutes() {
        return globalMessageStore != null
                ? globalMessageStore.getIntervalMinutes()
                : GlobalMessageStore.DEFAULT_INTERVAL_MINUTES;
    }

    private static String formatMinutes(long minutes) {
        return Long.toString(minutes);
    }

    public static class GlobalMessageData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_MESSAGE = "@Message";
        static final String KEY_INTERVAL = "@IntervalMinutes";

        public static final BuilderCodec<GlobalMessageData> CODEC = BuilderCodec
                .<GlobalMessageData>builder(GlobalMessageData.class, GlobalMessageData::new)
                .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING),
                        (data, value) -> data.button = value, data -> data.button)
                .addField(new KeyedCodec<>(KEY_MESSAGE, Codec.STRING),
                        (data, value) -> data.message = value, data -> data.message)
                .addField(new KeyedCodec<>(KEY_INTERVAL, Codec.STRING),
                        (data, value) -> data.intervalMinutes = value, data -> data.intervalMinutes)
                .build();

        private String button;
        private String message;
        private String intervalMinutes;
    }
}
