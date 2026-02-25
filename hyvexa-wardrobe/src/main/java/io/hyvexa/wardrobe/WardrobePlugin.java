package io.hyvexa.wardrobe;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.shop.ShopTabRegistry;
import io.hyvexa.core.cosmetic.CosmeticStore;
import io.hyvexa.core.wardrobe.WardrobeBridge;
import io.hyvexa.wardrobe.command.WardrobeBuyCommand;
import io.hyvexa.wardrobe.command.WardrobeResetCommand;

import javax.annotation.Nonnull;
import java.util.UUID;

public class WardrobePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static WardrobePlugin INSTANCE;
    private WardrobeShopTab wardrobeShopTab;

    public WardrobePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    public static WardrobePlugin getInstance() {
        return INSTANCE;
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up " + this.getName());

        try {
            CosmeticStore.getInstance().initialize();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize CosmeticStore");
        }

        wardrobeShopTab = new WardrobeShopTab();
        ShopTabRegistry.register(wardrobeShopTab);

        this.getCommandRegistry().registerCommand(new WardrobeBuyCommand());
        this.getCommandRegistry().registerCommand(new WardrobeResetCommand());

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            if (ref == null || !ref.isValid()) return;
            Store<EntityStore> store = ref.getStore();
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;
            try {
                WardrobeBridge.getInstance().regrantPermissions(playerRef.getUuid());
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Wardrobe permission re-grant failed");
            }
        });

        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            if (event.getPlayerRef() == null) return;
            UUID playerId = event.getPlayerRef().getUuid();

            try {
                wardrobeShopTab.evictPlayer(playerId);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Disconnect cleanup: WardrobeShopTab");
            }

            try {
                CosmeticStore.getInstance().evictPlayer(playerId);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Disconnect cleanup: CosmeticStore");
            }
        });

        LOGGER.atInfo().log(this.getName() + " setup complete");
    }

    @Override
    protected void shutdown() {
        LOGGER.atInfo().log("Shutting down " + this.getName());
        super.shutdown();
    }
}
