package io.hyvexa.purge.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

public class PurgeHud extends CustomUIHud {

    private int lastPlayerCount = -1;
    private long lastGems = -1;
    private int lastWave = -1;
    private int lastAlive = -1;
    private int lastTotal = -1;
    private long lastScrap = -1;
    private String lastIntermissionText = null;
    private int lastHpCurrent = -1;
    private int lastHpMax = -1;

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
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#PlayerGemsValue.Text", String.valueOf(gems));
        update(false, cmd);
    }

    public void updatePlayerCount() {
        int count = Universe.get().getPlayers().size();
        if (count == lastPlayerCount) {
            return;
        }
        lastPlayerCount = count;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#PlayerCountText.Text", String.format("%,d", count));
        update(false, cmd);
    }

    public void updateWaveStatus(int wave, int alive, int total) {
        if (wave == lastWave && alive == lastAlive && total == lastTotal) {
            return;
        }
        lastWave = wave;
        lastAlive = alive;
        lastTotal = total;
        lastIntermissionText = null;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#WaveLabel.Text", "WAVE " + wave);
        cmd.set("#ZombieCountLabel.Text", "Zombies: " + alive + "/" + total);
        update(false, cmd);
    }

    public void updateIntermission(int seconds) {
        String text = "Next wave in " + seconds + "...";
        if (text.equals(lastIntermissionText)) {
            return;
        }
        lastIntermissionText = text;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#ZombieCountLabel.Text", text);
        update(false, cmd);
    }

    public void updateScrap(long scrap) {
        if (scrap == lastScrap) {
            return;
        }
        lastScrap = scrap;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#PlayerScrapValue.Text", scrap + " scrap");
        update(false, cmd);
    }

    public void updatePlayerHealth(int current, int max) {
        if (current == lastHpCurrent && max == lastHpMax) {
            return;
        }
        lastHpCurrent = current;
        lastHpMax = max;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#PlayerHealthLabel.Text", "HP: " + current + " / " + max);
        update(false, cmd);
    }

    public void setWaveStatusVisible(boolean visible) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#WaveStatusRow.Visible", visible);
        update(false, cmd);
    }

    public void resetCache() {
        lastWave = -1;
        lastAlive = -1;
        lastTotal = -1;
        lastScrap = -1;
        lastPlayerCount = -1;
        lastGems = -1;
        lastIntermissionText = null;
        lastHpCurrent = -1;
        lastHpMax = -1;
    }
}
