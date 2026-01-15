package io.parkour.plugins.parkour;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.parkour.ui.ProgressAdminPage;
import io.parkour.plugins.parkour.data.ParkourProgressStore;

import javax.annotation.Nonnull;

/**
 * Compatibility shim for legacy package references.
 */
public class ParkourProgressAdminPage extends ProgressAdminPage {

    public ParkourProgressAdminPage(@Nonnull PlayerRef playerRef, @Nonnull ParkourProgressStore progressStore) {
        super(playerRef, progressStore);
    }
}
