package io.hyvexa.core.event;

import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.core.state.PlayerMode;

public class ModeExitEvent implements IEvent<Void> {
    private final PlayerRef playerRef;
    private final PlayerMode mode;
    private final PlayerMode nextMode;

    public ModeExitEvent(PlayerRef playerRef, PlayerMode mode, PlayerMode nextMode) {
        this.playerRef = playerRef;
        this.mode = mode;
        this.nextMode = nextMode;
    }

    public PlayerRef getPlayerRef() {
        return playerRef;
    }

    public PlayerMode getMode() {
        return mode;
    }

    public PlayerMode getNextMode() {
        return nextMode;
    }
}
