package io.hyvexa.common.hud;

import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class HudLifecycleManager<T extends CustomUIHud> {

    private final ConcurrentHashMap<UUID, T> huds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> attached = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> readyAt = new ConcurrentHashMap<>();
    private final Function<PlayerRef, T> hudFactory;
    private final long attachDelayMs;

    public HudLifecycleManager(Function<PlayerRef, T> hudFactory) {
        this(hudFactory, 250L);
    }

    public HudLifecycleManager(Function<PlayerRef, T> hudFactory, long attachDelayMs) {
        this.hudFactory = hudFactory;
        this.attachDelayMs = attachDelayMs;
    }

    public T attach(PlayerRef playerRef, Player player) {
        return attach(playerRef, player, true);
    }

    public T attach(PlayerRef playerRef, Player player, boolean hideCompass) {
        if (playerRef == null || player == null) {
            return null;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return null;
        }

        T hud = huds.computeIfAbsent(playerId, id -> hudFactory.apply(playerRef));
        player.getHudManager().setCustomHud(playerRef, hud);

        if (hideCompass) {
            player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass);
        }

        hud.show();
        attached.put(playerId, true);
        readyAt.put(playerId, System.currentTimeMillis() + attachDelayMs);

        return hud;
    }

    public void detach(UUID playerId) {
        if (playerId == null) {
            return;
        }
        huds.remove(playerId);
        attached.remove(playerId);
        readyAt.remove(playerId);
    }

    public T getHud(UUID playerId) {
        return playerId != null ? huds.get(playerId) : null;
    }

    public boolean isAttached(UUID playerId) {
        return Boolean.TRUE.equals(attached.get(playerId));
    }

    public boolean needsAttach(UUID playerId) {
        return !isAttached(playerId);
    }

    public boolean isReady(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        long ready = readyAt.getOrDefault(playerId, Long.MAX_VALUE);
        return System.currentTimeMillis() >= ready;
    }

    public T ensureAttached(PlayerRef playerRef, Player player) {
        if (playerRef == null || player == null) {
            return null;
        }
        UUID playerId = playerRef.getUuid();
        T hud = huds.get(playerId);

        if (needsAttach(playerId) || hud == null) {
            return attach(playerRef, player);
        }

        player.getHudManager().setCustomHud(playerRef, hud);
        return hud;
    }

    public void clear() {
        huds.clear();
        attached.clear();
        readyAt.clear();
    }

    public int size() {
        return huds.size();
    }
}
