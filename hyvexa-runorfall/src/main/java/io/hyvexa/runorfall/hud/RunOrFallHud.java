package io.hyvexa.runorfall.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

public class RunOrFallHud extends CustomUIHud {

    private int lastPlayerCount = -1;
    private long lastVexa = -1;
    private int lastBrokenBlocks = -1;
    private Boolean lastBrokenBlocksVisible = null;
    private String lastCountdownText = null;
    private Boolean lastCountdownVisible = null;

    public RunOrFallHud(PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(UICommandBuilder commandBuilder) {
        commandBuilder.append("Pages/RunOrFall_RunHud.ui");
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

    public void updateCountdownText(String text) {
        String safeText = text == null ? "" : text.trim();
        boolean visible = !safeText.isEmpty();
        if (safeText.equals(lastCountdownText) && Boolean.valueOf(visible).equals(lastCountdownVisible)) {
            return;
        }
        lastCountdownText = safeText;
        lastCountdownVisible = visible;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#StartingInText.Visible", visible);
        commandBuilder.set("#StartingInText.Text", safeText);
        update(false, commandBuilder);
    }

    public void updateBrokenBlocks(int brokenBlocks, boolean visible) {
        int safeValue = Math.max(0, brokenBlocks);
        if (safeValue == lastBrokenBlocks && Boolean.valueOf(visible).equals(lastBrokenBlocksVisible)) {
            return;
        }
        lastBrokenBlocks = safeValue;
        lastBrokenBlocksVisible = visible;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#BrokenBlocksText.Visible", visible);
        commandBuilder.set("#BrokenBlocksText.Text", "Blocks broken: " + String.format("%,d", safeValue));
        update(false, commandBuilder);
    }
}
