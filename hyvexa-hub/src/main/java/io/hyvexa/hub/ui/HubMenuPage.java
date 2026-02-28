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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.common.whitelist.AscendWhitelistManager;
import io.hyvexa.common.whitelist.WhitelistRegistry;
import io.hyvexa.core.queue.RunOrFallQueueStore;
import io.hyvexa.hub.routing.HubRouter;

import javax.annotation.Nonnull;
import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

public class HubMenuPage extends InteractiveCustomUIPage<ButtonEventData> {

    private static final String BUTTON_PARKOUR = "Parkour";
    private static final String BUTTON_ASCEND = "Parkour Ascend";
    private static final String BUTTON_PURGE = "Purge";
    private static final String BUTTON_RUN_OR_FALL = "RunOrFall";
    private static final String BUTTON_QUEUE_ROF = "QueueRoF";
    private static final String BUTTON_LEAVE_QUEUE_ROF = "LeaveQueueRoF";
    private static final String BUTTON_DISCORD = "Discord";
    private static final String BUTTON_STORE = "Store";
    private static final String BUTTON_HUB = "Hub";
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
    private static final Message MESSAGE_PURGE_RESTRICTED = Message.join(
            Message.raw("Hyvexa: ").color("#ff8a3d"),
            Message.raw("Purge is currently restricted to staff only.")
    );
    private static final Message MESSAGE_RUN_OR_FALL_RESTRICTED = Message.join(
            Message.raw("Hyvexa: ").color("#ff8a3d"),
            Message.raw("RunOrFall is work in progress and currently restricted to staff only.")
    );

    private static volatile AscendWhitelistManager cachedWhitelistManager;
    private static volatile long cachedWhitelistModified;

    private final HubRouter router;

    public HubMenuPage(@Nonnull PlayerRef playerRef, @Nonnull HubRouter router) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ButtonEventData.CODEC);
        this.router = router;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Hub_Menu.ui");
        Player player = store.getComponent(ref, Player.getComponentType());
        boolean showStaffModes = PermissionUtils.isOp(player);

        uiCommandBuilder.set("#BottomModeRow.Visible", showStaffModes);
        uiCommandBuilder.set("#PurgeCard.Visible", showStaffModes);
        uiCommandBuilder.set("#RunOrFallCard.Visible", showStaffModes);

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ParkourButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PARKOUR), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AscendButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ASCEND), false);
        if (showStaffModes) {
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PurgeButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PURGE), false);
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RunOrFallButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_RUN_OR_FALL), false);
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#QueueRofButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_QUEUE_ROF), false);
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LeaveQueueRofButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_LEAVE_QUEUE_ROF), false);

            // Queue button visibility
            PlayerRef buildPlayerRef = store.getComponent(ref, PlayerRef.getComponentType());
            UUID buildPlayerId = buildPlayerRef != null ? buildPlayerRef.getUuid() : null;
            boolean isQueued = buildPlayerId != null && RunOrFallQueueStore.getInstance().isQueued(buildPlayerId);
            uiCommandBuilder.set("#QueueRofButton.Visible", !isQueued);
            uiCommandBuilder.set("#LeaveQueueRofButton.Visible", isQueued);
        }
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DiscordBannerButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_DISCORD), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#StoreBannerButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_STORE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#HubButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_HUB), false);
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
        if (BUTTON_HUB.equals(data.getButton())) {
            if (playerRef != null) {
                router.routeToHub(playerRef);
            }
            this.close();
            return;
        }
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
            if (whitelistManager == null) {
                // Plugins use separate classloaders, so the registry singleton set by the
                // Ascend plugin is not visible here. Read the whitelist file directly instead.
                File whitelistFile = Path.of("mods", "Parkour", "ascend_whitelist.json").toFile();
                if (whitelistFile.exists()) {
                    long lastModified = whitelistFile.lastModified();
                    if (cachedWhitelistManager == null || lastModified != cachedWhitelistModified) {
                        cachedWhitelistManager = new AscendWhitelistManager(whitelistFile);
                        cachedWhitelistModified = lastModified;
                    }
                    whitelistManager = cachedWhitelistManager;
                }
            }
            boolean isAllowed;
            if (whitelistManager != null && whitelistManager.isPublicMode()) {
                isAllowed = true;
            } else if (PermissionUtils.isOp(player)) {
                isAllowed = true;
            } else if (whitelistManager != null && whitelistManager.isEnabled() && playerRef != null) {
                String username = playerRef.getUsername();
                isAllowed = username != null && whitelistManager.contains(username);
            } else if (whitelistManager == null) {
                // No whitelist configured at all — default to OP-only
                isAllowed = false;
            } else {
                isAllowed = false;
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
        if (BUTTON_PURGE.equals(data.getButton())) {
            if (!PermissionUtils.isOp(player)) {
                if (player != null) {
                    player.sendMessage(MESSAGE_PURGE_RESTRICTED);
                }
                this.close();
                return;
            }
            if (playerRef != null) {
                router.routeToPurge(playerRef);
            }
            this.close();
            return;
        }
        if (BUTTON_RUN_OR_FALL.equals(data.getButton())) {
            if (!PermissionUtils.isOp(player)) {
                if (player != null) {
                    player.sendMessage(MESSAGE_RUN_OR_FALL_RESTRICTED);
                }
                this.close();
                return;
            }
            if (playerRef != null) {
                router.routeToRunOrFall(playerRef);
            }
            this.close();
            return;
        }
        if (BUTTON_QUEUE_ROF.equals(data.getButton())) {
            if (playerRef != null && playerRef.getUuid() != null) {
                World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
                String worldName = world != null && world.getName() != null ? world.getName() : "unknown";
                RunOrFallQueueStore.getInstance().enqueue(playerRef.getUuid(), worldName);
                if (player != null) {
                    player.sendMessage(Message.raw("[RunOrFall] Queued! You'll be teleported when the game starts."));
                }
            }
            this.close();
            return;
        }
        if (BUTTON_LEAVE_QUEUE_ROF.equals(data.getButton())) {
            if (playerRef != null && playerRef.getUuid() != null) {
                RunOrFallQueueStore.getInstance().dequeue(playerRef.getUuid());
                if (player != null) {
                    player.sendMessage(Message.raw("[RunOrFall] Removed from queue."));
                }
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
