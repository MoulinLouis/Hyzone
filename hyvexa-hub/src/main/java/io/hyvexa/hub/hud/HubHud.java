package io.hyvexa.hub.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class HubHud extends CustomUIHud {

    private boolean applied;

    public HubHud(PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(UICommandBuilder commandBuilder) {
        commandBuilder.append("Pages/Hub_RunHud.ui");
    }

    public void applyStaticText() {
        if (applied) {
            return;
        }
        applied = true;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#PlayerNameText.Text", "HYVEXA HUB");
        update(false, commandBuilder);
    }

    public void resetCache() {
        applied = false;
    }
}
