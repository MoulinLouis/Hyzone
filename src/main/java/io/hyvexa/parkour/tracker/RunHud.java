package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class RunHud extends CustomUIHud {

    public RunHud(PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(UICommandBuilder commandBuilder) {
        commandBuilder.append("Pages/Parkour_RunHud.ui");
    }

    public void updateText(String timeText) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#RunTimerText.Text", timeText);
        update(false, commandBuilder);
    }

    public void updateInfo(String playerName, String rankName, int completedMaps, int totalMaps,
                           String serverIp) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#PlayerNameText.Text", "PARKOUR");
        commandBuilder.set("#PlayerXpValue.Text", rankName);
        String rankColor = io.hyvexa.common.util.FormatUtils.getRankColor(rankName);
        commandBuilder.set("#PlayerXpValue.Style.TextColor", rankColor);
        commandBuilder.set("#PlayerMapsValue.Text", completedMaps + "/" + totalMaps);
        commandBuilder.set("#ServerDateText.Text", "Music: Zelda OST");
        commandBuilder.set("#ServerIpText.Text", "Server: " + serverIp);
        update(false, commandBuilder);
    }
}
