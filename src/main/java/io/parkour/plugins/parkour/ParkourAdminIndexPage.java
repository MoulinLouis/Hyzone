package io.parkour.plugins.parkour;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.parkour.ui.AdminIndexPage;
import io.parkour.plugins.parkour.data.ParkourMapStore;
import io.parkour.plugins.parkour.data.ParkourProgressStore;

import javax.annotation.Nonnull;

/**
 * Compatibility shim for legacy package references.
 */
public class ParkourAdminIndexPage extends AdminIndexPage {

    public ParkourAdminIndexPage(@Nonnull PlayerRef playerRef, @Nonnull ParkourMapStore mapStore,
                                 @Nonnull ParkourProgressStore progressStore) {
        super(playerRef, mapStore, progressStore);
    }
}
