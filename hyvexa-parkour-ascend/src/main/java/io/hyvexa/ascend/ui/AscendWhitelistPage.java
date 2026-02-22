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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.common.whitelist.AscendWhitelistManager;

import javax.annotation.Nonnull;
import java.util.List;

public class AscendWhitelistPage extends InteractiveCustomUIPage<AscendWhitelistPage.WhitelistData> {

    private static final String BUTTON_BACK = "BackButton";
    private static final String BUTTON_ADD = "AddPlayer";
    private static final String BUTTON_TOGGLE = "ToggleWhitelist";
    private static final String BUTTON_PUBLIC_TOGGLE = "TogglePublic";
    private static final String BUTTON_REMOVE_PREFIX = "Remove:";

    private final AscendWhitelistManager whitelistManager;
    private String usernameInput = "";

    public AscendWhitelistPage(@Nonnull PlayerRef playerRef, AscendWhitelistManager whitelistManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, WhitelistData.CODEC);
        this.whitelistManager = whitelistManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Ascend_Whitelist.ui");
        bindEvents(uiEventBuilder);
        populateFields(uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull WhitelistData data) {
        super.handleDataEvent(ref, store, data);
        if (data.username != null) {
            usernameInput = data.username.trim();
        }
        if (data.button == null) {
            return;
        }
        if (BUTTON_BACK.equals(data.button)) {
            openAdminPanel(ref, store);
            return;
        }
        if (BUTTON_PUBLIC_TOGGLE.equals(data.button)) {
            handlePublicToggle(ref, store);
            return;
        }
        if (BUTTON_TOGGLE.equals(data.button)) {
            handleToggle(ref, store);
            return;
        }
        if (BUTTON_ADD.equals(data.button)) {
            handleAdd(ref, store);
            return;
        }
        if (data.button.startsWith(BUTTON_REMOVE_PREFIX)) {
            handleRemove(ref, store, data.button.substring(BUTTON_REMOVE_PREFIX.length()));
        }
    }

    private void handlePublicToggle(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || whitelistManager == null) {
            return;
        }
        boolean newState = !whitelistManager.isPublicMode();
        whitelistManager.setPublicMode(newState);
        String msg = newState
                ? "Public mode enabled. Any player can access Ascend mode."
                : "Public mode disabled. Whitelist restrictions are back in effect.";
        player.sendMessage(Message.raw(msg));
        sendRefresh();
    }

    private void handleToggle(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || whitelistManager == null) {
            return;
        }
        boolean newState = !whitelistManager.isEnabled();
        whitelistManager.setEnabled(newState);
        String statusMessage = newState
                ? "Whitelist enabled. Whitelisted players and OPs can access Ascend mode."
                : "Whitelist disabled. Only OPs can access Ascend mode.";
        player.sendMessage(Message.raw(statusMessage));
        sendRefresh();
    }

    private void handleAdd(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || whitelistManager == null) {
            return;
        }
        String trimmed = usernameInput.trim();
        if (trimmed.isEmpty()) {
            player.sendMessage(Message.raw("Enter a username to add."));
            return;
        }
        if (!whitelistManager.add(trimmed)) {
            player.sendMessage(Message.raw("Player \"" + trimmed + "\" is already whitelisted."));
            return;
        }
        player.sendMessage(Message.raw("Added \"" + trimmed + "\" to the whitelist."));
        usernameInput = "";
        sendRefresh();
    }

    private void handleRemove(Ref<EntityStore> ref, Store<EntityStore> store, String username) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || whitelistManager == null) {
            return;
        }
        if (!whitelistManager.remove(username)) {
            player.sendMessage(Message.raw("Player \"" + username + "\" was not found in the whitelist."));
            return;
        }
        player.sendMessage(Message.raw("Removed \"" + username + "\" from the whitelist."));
        sendRefresh();
    }

    private void openAdminPanel(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new AscendAdminPanelPage(playerRef));
    }

    private void populateFields(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.set("#UsernameField.Value", usernameInput);
        updateToggleButton(commandBuilder);
        buildWhitelistEntries(commandBuilder, eventBuilder);
    }

    private void updateToggleButton(UICommandBuilder commandBuilder) {
        if (whitelistManager == null) {
            return;
        }
        boolean publicMode = whitelistManager.isPublicMode();
        commandBuilder.set("#PublicToggleButton.Text", publicMode ? "PUBLIC" : "WHITELIST");

        boolean enabled = whitelistManager.isEnabled();
        commandBuilder.set("#ToggleButton.Text", enabled ? "ENABLED" : "DISABLED");
    }

    private void buildWhitelistEntries(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#WhitelistEntries");
        List<String> whitelisted = whitelistManager != null ? whitelistManager.list() : List.of();
        if (whitelisted.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No players whitelisted yet.");
            return;
        }
        commandBuilder.set("#EmptyText.Text", "");
        int index = 0;
        for (String username : whitelisted) {
            commandBuilder.append("#WhitelistEntries", "Pages/Ascend_WhitelistEntry.ui");
            commandBuilder.set("#WhitelistEntries[" + index + "] #Username.Text", username);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#WhitelistEntries[" + index + "] #RemoveButton",
                    EventData.of(WhitelistData.KEY_BUTTON, BUTTON_REMOVE_PREFIX + username), false);
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
                EventData.of(WhitelistData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PublicToggleButton",
                EventData.of(WhitelistData.KEY_BUTTON, BUTTON_PUBLIC_TOGGLE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleButton",
                EventData.of(WhitelistData.KEY_BUTTON, BUTTON_TOGGLE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#UsernameField",
                EventData.of(WhitelistData.KEY_USERNAME, "#UsernameField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddButton",
                EventData.of(WhitelistData.KEY_BUTTON, BUTTON_ADD), false);
    }

    public static class WhitelistData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_USERNAME = "@Username";

        public static final BuilderCodec<WhitelistData> CODEC = BuilderCodec
                .<WhitelistData>builder(WhitelistData.class, WhitelistData::new)
                .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING),
                        (data, value) -> data.button = value, data -> data.button)
                .addField(new KeyedCodec<>(KEY_USERNAME, Codec.STRING),
                        (data, value) -> data.username = value, data -> data.username)
                .build();

        private String button;
        private String username;
    }
}
