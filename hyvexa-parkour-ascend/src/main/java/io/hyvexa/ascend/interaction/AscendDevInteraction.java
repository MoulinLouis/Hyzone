package io.hyvexa.ascend.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Generic dev interaction that opens a UI page. Replaces the individual
 * AscendDevCindercloth/Cotton/Shadoweave/Silk/Stormsilk interactions.
 */
public class AscendDevInteraction extends AbstractAscendPageInteraction {

    @FunctionalInterface
    public interface PageFactory {
        InteractiveCustomUIPage<?> create(Ref<EntityStore> ref, Store<EntityStore> store,
                                          PlayerRef playerRef, AscendInteractionBridge.Services services);
    }

    private final PageFactory pageFactory;
    private final BiPredicate<AscendInteractionBridge.Services, Player> dependencyValidator;
    private final boolean ascendWorldRequired;
    private final boolean servicesRequired;

    public AscendDevInteraction(PageFactory pageFactory,
                                BiPredicate<AscendInteractionBridge.Services, Player> dependencyValidator,
                                boolean ascendWorldRequired,
                                boolean servicesRequired) {
        this.pageFactory = pageFactory;
        this.dependencyValidator = dependencyValidator;
        this.ascendWorldRequired = ascendWorldRequired;
        this.servicesRequired = servicesRequired;
    }

    @Override
    protected boolean requiresAscendWorld() {
        return ascendWorldRequired;
    }

    @Override
    protected boolean requiresServices() {
        return servicesRequired;
    }

    @Override
    protected boolean validateDependencies(AscendInteractionBridge.Services services, Player player) {
        return dependencyValidator.test(services, player);
    }

    @Override
    protected InteractiveCustomUIPage<?> createPage(Ref<EntityStore> ref, Store<EntityStore> store,
                                                     PlayerRef playerRef,
                                                     AscendInteractionBridge.Services services) {
        return pageFactory.create(ref, store, playerRef, services);
    }

    /**
     * Create a BuilderCodec that instantiates an AscendDevInteraction with the given configuration.
     */
    public static BuilderCodec<AscendDevInteraction> codec(Supplier<AscendDevInteraction> supplier) {
        return BuilderCodec.builder(AscendDevInteraction.class, supplier).build();
    }
}
