package io.hyvexa.core.state;

import com.hypixel.hytale.math.vector.Location;

public class PlayerModeState {
    private PlayerMode currentMode = PlayerMode.NONE;
    private Location parkourReturnLocation;
    private Location ascendReturnLocation;

    public PlayerMode getCurrentMode() {
        return currentMode;
    }

    public void setCurrentMode(PlayerMode currentMode) {
        this.currentMode = currentMode;
    }

    public Location getParkourReturnLocation() {
        return parkourReturnLocation;
    }

    public void setParkourReturnLocation(Location parkourReturnLocation) {
        this.parkourReturnLocation = parkourReturnLocation;
    }

    public Location getAscendReturnLocation() {
        return ascendReturnLocation;
    }

    public void setAscendReturnLocation(Location ascendReturnLocation) {
        this.ascendReturnLocation = ascendReturnLocation;
    }
}
