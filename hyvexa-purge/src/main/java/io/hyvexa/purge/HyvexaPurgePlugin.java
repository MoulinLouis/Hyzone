package io.hyvexa.purge;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.common.util.InventoryUtils;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.core.analytics.AnalyticsStore;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.discord.DiscordLinkStore;
import io.hyvexa.core.economy.GemStore;
import io.hyvexa.purge.hud.PurgeHud;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class HyvexaPurgePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final short SLOT_SERVER_SELECTOR = 8;
    private static HyvexaPurgePlugin INSTANCE;

    private final Set<UUID> playersInPurgeWorld = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, PurgeHud> purgeHuds = new ConcurrentHashMap<>();
    private ScheduledFuture<?> hudUpdateTask;

    public HyvexaPurgePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    public static HyvexaPurgePlugin getInstance() {
        return INSTANCE;
    }

    @Override
    protected void setup() {
        try {
            DatabaseManager.getInstance().initialize();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize database for Purge");
        }
        try {
            GemStore.getInstance().initialize();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize GemStore for Purge");
        }
        try {
            DiscordLinkStore.getInstance().initialize();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize DiscordLinkStore for Purge");
        }
        try {
            AnalyticsStore.getInstance().initialize();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize AnalyticsStore for Purge");
        }

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            if (world == null || !ModeGate.isPurgeWorld(world)) {
                return;
            }
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            UUID playerId = playerRef.getUuid();
            if (playerId != null) {
                playersInPurgeWorld.add(playerId);
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            InventoryUtils.clearAllContainers(player);
            giveServerSelector(player);
            attachHud(playerRef, player);
            LOGGER.atInfo().log("Player entered Purge: " + (playerId != null ? playerId : "unknown"));
            try {
                DiscordLinkStore.getInstance().checkAndRewardGems(playerId, player);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Discord link check failed (purge)");
            }
        });

        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            World world = event.getWorld();
            if (world == null) {
                return;
            }
            var holder = event.getHolder();
            if (holder == null) {
                return;
            }
            PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
            UUID playerId = playerRef != null ? playerRef.getUuid() : null;
            if (ModeGate.isPurgeWorld(world)) {
                if (playerId != null) {
                    playersInPurgeWorld.add(playerId);
                }
                return;
            }
            if (playerId != null) {
                playersInPurgeWorld.remove(playerId);
                purgeHuds.remove(playerId);
            }
        });

        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            if (playerRef == null) {
                return;
            }
            UUID playerId = playerRef.getUuid();
            if (playerId == null) {
                return;
            }
            playersInPurgeWorld.remove(playerId);
            purgeHuds.remove(playerId);
            try { GemStore.getInstance().evictPlayer(playerId); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: GemStore"); }
            try { DiscordLinkStore.getInstance().evictPlayer(playerId); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: DiscordLinkStore"); }
        });

        hudUpdateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                this::tickHudUpdates, 5000L, 5000L, TimeUnit.MILLISECONDS
        );

        LOGGER.atInfo().log("HyvexaPurge plugin loaded");
    }

    @Override
    protected void shutdown() {
        if (hudUpdateTask != null) {
            hudUpdateTask.cancel(false);
            hudUpdateTask = null;
        }
        playersInPurgeWorld.clear();
        purgeHuds.clear();
    }

    private void attachHud(PlayerRef playerRef, Player player) {
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        PurgeHud hud = new PurgeHud(playerRef);
        purgeHuds.put(playerId, hud);
        player.getHudManager().setCustomHud(playerRef, hud);
        player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass);
        hud.show();
    }

    private void tickHudUpdates() {
        for (var entry : purgeHuds.entrySet()) {
            PurgeHud hud = entry.getValue();
            hud.updatePlayerCount();
            hud.updateGems(GemStore.getInstance().getGems(entry.getKey()));
        }
    }

    private void giveServerSelector(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        inventory.getHotbar().setItemStackForSlot(SLOT_SERVER_SELECTOR,
                new ItemStack(WorldConstants.ITEM_SERVER_SELECTOR, 1), false);
    }
}
