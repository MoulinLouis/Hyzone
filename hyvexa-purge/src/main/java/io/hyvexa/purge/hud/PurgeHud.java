package io.hyvexa.purge.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

public class PurgeHud extends CustomUIHud {

    private int lastPlayerCount = -1;
    private long lastGems = -1;

    public PurgeHud(PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(UICommandBuilder commandBuilder) {
        commandBuilder.append("Pages/Purge_RunHud.ui");
    }

    public void updateGems(long gems) {
        if (gems == lastGems) {
            return;
        }
        lastGems = gems;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#PlayerGemsValue.Text", String.valueOf(gems));
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
