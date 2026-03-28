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
import io.hyvexa.common.util.PlayerCleanupHelper;
import io.hyvexa.common.util.StoreInitializer;
import io.hyvexa.common.skin.PurgeSkinStore;
import io.hyvexa.core.cosmetic.CosmeticManager;
import io.hyvexa.core.cosmetic.CosmeticStore;
import io.hyvexa.core.analytics.AnalyticsStore;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.economy.FeatherStore;
import io.hyvexa.core.economy.VexaStore;
import io.hyvexa.core.wardrobe.CosmeticShopConfigStore;
import io.hyvexa.core.wardrobe.WardrobeBridge;
import io.hyvexa.wardrobe.command.ShopCommand;
import io.hyvexa.wardrobe.command.WardrobeBuyCommand;
import io.hyvexa.wardrobe.command.WardrobeResetCommand;

import javax.annotation.Nonnull;
import java.util.UUID;

public class WardrobePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static WardrobePlugin INSTANCE;
    private WardrobeShopTab wardrobeShopTab;
    private EffectsShopTab effectsShopTab;
    private ShopConfigTab shopConfigTab;
    private PurgeSkinShopTab purgeSkinShopTab;
    private CosmeticManager cosmeticManager;
    private WardrobeBridge wardrobeBridge;
    private CosmeticStore cosmeticStore;
    private CosmeticShopConfigStore cosmeticShopConfigStore;
    private io.hyvexa.core.economy.CurrencyStore vexaStore;
    private io.hyvexa.core.economy.CurrencyStore featherStore;
    private PurgeSkinStore purgeSkinStore;

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

        wardrobeBridge = new WardrobeBridge(DatabaseManager.get());
        wardrobeBridge.initialize();

        StoreInitializer.initialize(LOGGER,
                () -> DatabaseManager.get().initialize(),
                () -> VexaStore.get().initialize(),
                () -> FeatherStore.get().initialize(),
                () -> CosmeticStore.get().initialize(),
                () -> PurgeSkinStore.get().initialize(),
                () -> { cosmeticShopConfigStore = new CosmeticShopConfigStore(DatabaseManager.get()); cosmeticShopConfigStore.initialize(); },
                () -> AnalyticsStore.get().initialize()
        );

        cosmeticStore = CosmeticStore.get();
        vexaStore = VexaStore.get();
        featherStore = FeatherStore.get();
        purgeSkinStore = PurgeSkinStore.get();

        wardrobeBridge.setCosmeticStore(cosmeticStore);
        wardrobeBridge.setCosmeticShopConfigStore(cosmeticShopConfigStore);
        wardrobeBridge.setCurrencyStores(vexaStore, featherStore);
        wardrobeBridge.setAnalytics(AnalyticsStore.get());

        cosmeticManager = CosmeticManager.createAndRegister(
                new io.hyvexa.core.trail.TrailManager(),
                new io.hyvexa.core.trail.ModelParticleTrailManager());
        cosmeticManager.setCosmeticStore(cosmeticStore);

        wardrobeShopTab = new WardrobeShopTab(wardrobeBridge, cosmeticStore, cosmeticShopConfigStore);
        ShopTabRegistry.register(wardrobeShopTab);
        effectsShopTab = new EffectsShopTab(cosmeticManager, cosmeticStore);
        ShopTabRegistry.register(effectsShopTab);
        purgeSkinShopTab = new PurgeSkinShopTab(purgeSkinStore);
        ShopTabRegistry.register(purgeSkinShopTab);
        shopConfigTab = new ShopConfigTab(wardrobeBridge, cosmeticShopConfigStore);
        ShopTabRegistry.register(shopConfigTab);

        this.getCommandRegistry().registerCommand(new ShopCommand(vexaStore, featherStore));
        this.getCommandRegistry().registerCommand(new WardrobeBuyCommand(wardrobeBridge));
        this.getCommandRegistry().registerCommand(new WardrobeResetCommand(wardrobeBridge));

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            if (ref == null || !ref.isValid()) return;
            Store<EntityStore> store = ref.getStore();
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;
            try {
                wardrobeBridge.regrantPermissions(playerRef.getUuid());
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Wardrobe permission re-grant failed");
            }
            try {
                cosmeticManager.reapplyOnLogin(ref, store);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Cosmetic reapply on login failed");
            }
        });

        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            if (event.getPlayerRef() == null) return;
            UUID playerId = event.getPlayerRef().getUuid();

            PlayerCleanupHelper.cleanup(playerId, LOGGER,
                    id -> wardrobeShopTab.evictPlayer(id),
                    id -> shopConfigTab.evictPlayer(id),
                    id -> effectsShopTab.evictPlayer(id),
                    id -> { if (purgeSkinShopTab != null) purgeSkinShopTab.evictPlayer(id); },
                    id -> cosmeticManager.cleanupOnDisconnect(id),
                    id -> cosmeticStore.evictPlayer(id),
                    id -> { if (vexaStore != null) vexaStore.evictPlayer(id); },
                    id -> { if (featherStore != null) featherStore.evictPlayer(id); },
                    id -> { if (purgeSkinStore != null) purgeSkinStore.evictPlayer(id); }
            );
        });

        LOGGER.atInfo().log(this.getName() + " setup complete");
    }

    @Override
    protected void shutdown() {
        LOGGER.atInfo().log("Shutting down " + this.getName());
        try {
            cosmeticManager.shutdown();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Shutdown: CosmeticManager");
        }
        try { DatabaseManager.get().shutdown(); }
        catch (Exception e) { LOGGER.atWarning().withCause(e).log("Shutdown: DatabaseManager"); }
        super.shutdown();
    }
}
