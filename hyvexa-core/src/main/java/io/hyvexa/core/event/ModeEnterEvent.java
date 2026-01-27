package io.hyvexa.core.event;

import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.core.state.PlayerMode;

public class ModeEnterEvent implements IEvent<Void> {
    private final PlayerRef playerRef;
    private final PlayerMode mode;
    private final PlayerMode previousMode;

    public ModeEnterEvent(PlayerRef playerRef, PlayerMode mode, PlayerMode previousMode) {
        this.playerRef = playerRef;
        this.mode = mode;
        this.previousMode = previousMode;
    }

    public PlayerRef getPlayerRef() {
        return playerRef;
    }

    public PlayerMode getMode() {
        return mode;
    }

    public PlayerMode getPreviousMode() {
        return previousMode;
    }
}
