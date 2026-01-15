package io.hyvexa.parkour.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.common.ui.ButtonEventData;

import javax.annotation.Nonnull;

public abstract class BaseParkourPage extends InteractiveCustomUIPage<ButtonEventData> {

    protected BaseParkourPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, ButtonEventData.CODEC);
    }
}
