package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import io.hyvexa.parkour.ui.PlayerMusicPage;

import java.util.List;

public class RunHud extends CustomUIHud {

    private String lastTimeText;
    private String lastCheckpointText;
    private String lastCheckpointSplitText;
    private String lastCheckpointSplitColor;
    private Boolean lastCheckpointSplitVisible;
    private String lastInfoKey;
    private String lastAnnouncementKey;
    private int lastPlayerCount = -1;
    private long lastGems = -1;
    private String lastAdvancedHudKey;
    private Boolean lastAdvancedHudVisible;

    public RunHud(PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(UICommandBuilder commandBuilder) {
        commandBuilder.append("Pages/Parkour_RunHud.ui");
        commandBuilder.append("Pages/Parkour_RunCheckpointHud.ui");
        commandBuilder.append("Pages/Parkour_AdvancedHud.ui");
    }

    public void updateText(String timeText) {
        String safeText = timeText != null ? timeText : "";
        if (safeText.equals(lastTimeText)) {
            return;
        }
        lastTimeText = safeText;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#RunTimerText.Text", safeText);
        update(false, commandBuilder);
    }

    public void updateCheckpointText(String checkpointText) {
        String safeText = checkpointText != null ? checkpointText : "";
        if (safeText.equals(lastCheckpointText)) {
            return;
        }
        lastCheckpointText = safeText;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#RunCheckpointText.Text", safeText);
        update(false, commandBuilder);
    }

    public void updateCheckpointSplit(String splitText, String splitColor, boolean visible) {
        String safeText = splitText != null ? splitText : "";
        String safeColor = splitColor != null ? splitColor : "#000000";
        if (safeText.equals(lastCheckpointSplitText)
                && safeColor.equals(lastCheckpointSplitColor)
                && Boolean.valueOf(visible).equals(lastCheckpointSplitVisible)) {
            return;
        }
        lastCheckpointSplitText = safeText;
        lastCheckpointSplitColor = safeColor;
        lastCheckpointSplitVisible = visible;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#CheckpointSplitHud.Visible", visible);
        commandBuilder.set("#CheckpointSplitHud.Background", safeColor);
        commandBuilder.set("#CheckpointSplitText.Text", safeText);
        commandBuilder.set("#CheckpointSplitText.Style.TextColor", "#FFFFFF");
        update(false, commandBuilder);
    }

    public void updateInfo(String playerName, String rankName, int completedMaps, int totalMaps,
                           String serverIp) {
        String safePlayerName = playerName != null ? playerName : "";
        String safeRankName = rankName != null ? rankName : "";
        String safeServerIp = serverIp != null ? serverIp : "";
        String infoKey = safePlayerName + "|" + safeRankName + "|" + completedMaps + "|" + totalMaps + "|" + safeServerIp;
        if (infoKey.equals(lastInfoKey)) {
            return;
        }
        lastInfoKey = infoKey;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#PlayerNameText.Text", "Parkour");
        String rankColor = io.hyvexa.common.util.FormatUtils.getRankColor(safeRankName);
        commandBuilder.set("#PlayerXpValue.Style.TextColor", rankColor);
        boolean isVexaGod = "VexaGod".equals(safeRankName);
        if (isVexaGod) {
            commandBuilder.set("#PlayerXpValue.Text", "");
            commandBuilder.set("#PlayerRankV.Text", "V");
            commandBuilder.set("#PlayerRankE.Text", "e");
            commandBuilder.set("#PlayerRankX.Text", "x");
            commandBuilder.set("#PlayerRankA.Text", "a");
            commandBuilder.set("#PlayerRankG.Text", "G");
            commandBuilder.set("#PlayerRankO.Text", "o");
            commandBuilder.set("#PlayerRankD.Text", "d");
        } else {
            commandBuilder.set("#PlayerXpValue.Text", safeRankName);
            commandBuilder.set("#PlayerRankV.Text", "");
            commandBuilder.set("#PlayerRankE.Text", "");
            commandBuilder.set("#PlayerRankX.Text", "");
            commandBuilder.set("#PlayerRankA.Text", "");
            commandBuilder.set("#PlayerRankG.Text", "");
            commandBuilder.set("#PlayerRankO.Text", "");
            commandBuilder.set("#PlayerRankD.Text", "");
        }
        commandBuilder.set("#PlayerMapsValue.Text", completedMaps + "/" + totalMaps);
        String musicLabel = PlayerMusicPage.getStoredMusicLabel(getPlayerRef().getUuid());
        commandBuilder.set("#ServerDateText.Text", "Music: " + musicLabel);
        commandBuilder.set("#ServerIpText.Text", "IP: " + safeServerIp);
        update(false, commandBuilder);
    }

    public void updateAnnouncements(List<String> lines) {
        String line1 = "";
        String line2 = "";
        String line3 = "";
        if (lines != null) {
            if (!lines.isEmpty()) {
                line1 = lines.get(0);
            }
            if (lines.size() > 1) {
                line2 = lines.get(1);
            }
            if (lines.size() > 2) {
                line3 = lines.get(2);
            }
        }
        String announcementKey = line1 + "\n" + line2 + "\n" + line3;
        if (announcementKey.equals(lastAnnouncementKey)) {
            return;
        }
        lastAnnouncementKey = announcementKey;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#AnnouncementLine1.Text", line1);
        commandBuilder.set("#AnnouncementLine2.Text", line2);
        commandBuilder.set("#AnnouncementLine3.Text", line3);
        update(false, commandBuilder);
    }

    public void updateAdvancedHud(boolean visible, String orientation, String velocity,
                                  String speed, String position) {
        if (!Boolean.valueOf(visible).equals(lastAdvancedHudVisible)) {
            lastAdvancedHudVisible = visible;
            UICommandBuilder commandBuilder = new UICommandBuilder();
            commandBuilder.set("#AdvancedHudRoot.Visible", visible);
            update(false, commandBuilder);
            if (!visible) {
                lastAdvancedHudKey = null;
                return;
            }
        }
        if (!visible) {
            return;
        }
        String safeOrientation = orientation != null ? orientation : "";
        String safeVelocity = velocity != null ? velocity : "";
        String safeSpeed = speed != null ? speed : "";
        String safePosition = position != null ? position : "";
        String key = safeOrientation + "|" + safeVelocity + "|" + safeSpeed + "|" + safePosition;
        if (key.equals(lastAdvancedHudKey)) {
            return;
        }
        lastAdvancedHudKey = key;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#OrientationValue.Text", safeOrientation);
        commandBuilder.set("#VelocityValue.Text", safeVelocity);
        commandBuilder.set("#SpeedValue.Text", safeSpeed);
        commandBuilder.set("#PositionValue.Text", safePosition);
        update(false, commandBuilder);
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
        commandBuilder.set("#PlayerCountText.Text", String.valueOf(count));
        update(false, commandBuilder);
    }

    public void resetCache() {
        lastTimeText = null;
        lastCheckpointText = null;
        lastCheckpointSplitText = null;
        lastCheckpointSplitColor = null;
        lastCheckpointSplitVisible = null;
        lastInfoKey = null;
        lastAnnouncementKey = null;
        lastPlayerCount = -1;
        lastGems = -1;
        lastAdvancedHudKey = null;
        lastAdvancedHudVisible = null;
    }
}
