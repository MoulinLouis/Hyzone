package io.hyvexa.ascend.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;

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
                                          PlayerRef playerRef, ParkourAscendPlugin plugin);
    }

    private final PageFactory pageFactory;
    private final BiPredicate<ParkourAscendPlugin, Player> dependencyValidator;
    private final boolean ascendWorldRequired;
    private final boolean pluginRequired;

    public AscendDevInteraction(PageFactory pageFactory,
                                BiPredicate<ParkourAscendPlugin, Player> dependencyValidator,
                                boolean ascendWorldRequired,
                                boolean pluginRequired) {
        this.pageFactory = pageFactory;
        this.dependencyValidator = dependencyValidator;
        this.ascendWorldRequired = ascendWorldRequired;
        this.pluginRequired = pluginRequired;
    }

    @Override
    protected boolean requiresAscendWorld() {
        return ascendWorldRequired;
    }

    @Override
    protected boolean requiresPlugin() {
        return pluginRequired;
    }

    @Override
    protected boolean validateDependencies(ParkourAscendPlugin plugin, Player player) {
        return dependencyValidator.test(plugin, player);
    }

    @Override
    protected InteractiveCustomUIPage<?> createPage(Ref<EntityStore> ref, Store<EntityStore> store,
                                                     PlayerRef playerRef, ParkourAscendPlugin plugin) {
        return pageFactory.create(ref, store, playerRef, plugin);
    }

    /**
     * Create a BuilderCodec that instantiates an AscendDevInteraction with the given configuration.
     */
    public static BuilderCodec<AscendDevInteraction> codec(Supplier<AscendDevInteraction> supplier) {
        return BuilderCodec.builder(AscendDevInteraction.class, supplier).build();
    }
}
