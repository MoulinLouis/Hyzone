package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;

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

    public void updateAnnouncements(List<String> lines) {
        String line1 = "";
        String line2 = "";
        String line3 = "";
        boolean hasLines = false;
        if (lines != null) {
            if (lines.size() > 0) {
                line1 = lines.get(0);
                hasLines = !line1.isBlank();
            }
            if (lines.size() > 1) {
                line2 = lines.get(1);
                hasLines = hasLines || !line2.isBlank();
            }
            if (lines.size() > 2) {
                line3 = lines.get(2);
                hasLines = hasLines || !line3.isBlank();
            }
        }
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#AnnouncementLine1.Text", line1);
        commandBuilder.set("#AnnouncementLine2.Text", line2);
        commandBuilder.set("#AnnouncementLine3.Text", line3);
        update(false, commandBuilder);
    }
}
