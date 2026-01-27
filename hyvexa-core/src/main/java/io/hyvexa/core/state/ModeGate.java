package io.hyvexa.core.state;

import java.util.UUID;

public final class ModeGate {

    private ModeGate() {
    }

    public static boolean isMode(UUID playerId, PlayerMode mode) {
        if (playerId == null || mode == null) {
            return true;
        }
        PlayerModeStateStore store = PlayerModeStateStore.getInstance();
        if (!store.isReady()) {
            return true;
        }
        return store.getCurrentMode(playerId) == mode;
    }
}
