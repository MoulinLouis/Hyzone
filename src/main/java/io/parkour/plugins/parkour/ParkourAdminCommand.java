package io.parkour.plugins.parkour;

import io.parkour.plugins.parkour.data.ParkourMapStore;
import io.parkour.plugins.parkour.data.ParkourProgressStore;

/**
 * Compatibility shim for legacy package references.
 */
public class ParkourAdminCommand extends io.hyvexa.parkour.command.ParkourAdminCommand {

    public ParkourAdminCommand(ParkourMapStore mapStore, ParkourProgressStore progressStore) {
        super(mapStore, progressStore);
    }
}
