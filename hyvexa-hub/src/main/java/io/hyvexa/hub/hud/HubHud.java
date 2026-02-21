package io.hyvexa.hub.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

public class HubHud extends CustomUIHud {

    private int lastPlayerCount = -1;
    private long lastVexa = -1;

    public HubHud(PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(UICommandBuilder commandBuilder) {
        commandBuilder.append("Pages/Hub_RunHud.ui");
    }

    public void updateVexa(long vexa) {
        if (vexa == lastVexa) {
            return;
        }
        lastVexa = vexa;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#PlayerVexaValue.Text", String.valueOf(vexa));
        update(false, commandBuilder);
    }

    public void updatePlayerCount() {
        int count = Universe.get().getPlayers().size();
        if (count == lastPlayerCount) {
            return;
        }
        lastPlayerCount = count;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#PlayerCountText.Text", String.format("%,d", count));
        update(false, commandBuilder);
    }
}
