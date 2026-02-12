package io.hyvexa.hub.ui;

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
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.common.whitelist.AscendWhitelistManager;
import io.hyvexa.common.whitelist.WhitelistRegistry;
import io.hyvexa.hub.routing.HubRouter;

import javax.annotation.Nonnull;

public class HubMenuPage extends InteractiveCustomUIPage<ButtonEventData> {

    private static final String BUTTON_PARKOUR = "Parkour";
    private static final String BUTTON_ASCEND = "Parkour Ascend";
    private static final String BUTTON_DISCORD = "Discord";
    private static final String BUTTON_STORE = "Store";
    private static final String BUTTON_CLOSE = "Close";
    private static final String LINK_COLOR = "#8ab4f8";
    private static final Message MESSAGE_DISCORD = Message.join(
            Message.raw("Discord: "),
            Message.raw("discord.gg/2PAygkyFnK").color(LINK_COLOR).link("https://discord.gg/2PAygkyFnK")
    );
    private static final Message MESSAGE_STORE = Message.join(
            Message.raw("Store: "),
            Message.raw("store.hyvexa.com").color(LINK_COLOR).link("https://store.hyvexa.com")
    );
    private static final Message MESSAGE_ASCEND_RESTRICTED = Message.join(
            Message.raw("Hyvexa: ").color("#ff8a3d"),
            Message.raw("Parkour Ascend access is currently restricted to staff and whitelisted players. See "),
            Message.raw("/discord").color(LINK_COLOR),
            Message.raw(" for updates.")
    );

    private final HubRouter router;

    public HubMenuPage(@Nonnull PlayerRef playerRef, @Nonnull HubRouter router) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ButtonEventData.CODEC);
        this.router = router;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Hub_Menu.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ParkourButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PARKOUR), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AscendButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ASCEND), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DiscordBannerButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_DISCORD), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#StoreBannerButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_STORE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (BUTTON_CLOSE.equals(data.getButton())) {
            this.close();
            return;
        }
        if (BUTTON_PARKOUR.equals(data.getButton())) {
            if (playerRef != null) {
                router.routeToParkour(playerRef);
            }
            this.close();
            return;
        }
        if (BUTTON_ASCEND.equals(data.getButton())) {
            AscendWhitelistManager whitelistManager = WhitelistRegistry.getInstance();
            boolean isAllowed = whitelistManager != null && whitelistManager.isPublicMode();
            if (!isAllowed) {
                isAllowed = PermissionUtils.isOp(player);
            }
            if (!isAllowed && playerRef != null && whitelistManager != null && whitelistManager.isEnabled()) {
                String username = playerRef.getUsername();
                if (username != null && whitelistManager.contains(username)) {
                    isAllowed = true;
                }
            }
            if (player != null && !isAllowed) {
                player.sendMessage(MESSAGE_ASCEND_RESTRICTED);
                this.close();
                return;
            }
            if (playerRef != null) {
                router.routeToAscend(playerRef);
            }
            this.close();
            return;
        }
        if (BUTTON_DISCORD.equals(data.getButton())) {
            if (player != null) {
                player.sendMessage(MESSAGE_DISCORD);
            }
            return;
        }
        if (BUTTON_STORE.equals(data.getButton())) {
            if (player != null) {
                player.sendMessage(MESSAGE_STORE);
            }
            return;
        }
    }
}
