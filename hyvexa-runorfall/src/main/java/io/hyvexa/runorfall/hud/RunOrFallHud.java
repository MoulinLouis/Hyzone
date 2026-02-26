package io.hyvexa.runorfall.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

public class RunOrFallHud extends CustomUIHud {

    private int lastPlayerCount = -1;
    private long lastVexa = -1;
    private long lastFeathers = -1;
    private int lastBrokenBlocks = -1;
    private boolean lastBrokenBlocksVisible = false;
    private boolean brokenBlocksInitialized = false;
    private int lastBlinkCharges = -1;
    private boolean lastBlinkChargesVisible = false;
    private boolean blinkChargesInitialized = false;
    private String lastCountdownText = null;
    private boolean lastCountdownVisible = false;
    private boolean countdownInitialized = false;

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

    public void updateFeathers(long feathers) {
        if (feathers == lastFeathers) {
            return;
        }
        lastFeathers = feathers;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#PlayerFeatherValue.Text", String.valueOf(feathers));
        update(false, commandBuilder);
    }

    public void updateCountdownText(String text) {
        String safeText = text == null ? "" : text.trim();
        boolean visible = !safeText.isEmpty();
        if (countdownInitialized && safeText.equals(lastCountdownText) && visible == lastCountdownVisible) {
            return;
        }
        countdownInitialized = true;
        lastCountdownText = safeText;
        lastCountdownVisible = visible;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#StartingInText.Visible", visible);
        commandBuilder.set("#StartingInText.Text", safeText);
        update(false, commandBuilder);
    }

    public void updateBrokenBlocks(int brokenBlocks, boolean visible) {
        int safeValue = Math.max(0, brokenBlocks);
        if (brokenBlocksInitialized && safeValue == lastBrokenBlocks && visible == lastBrokenBlocksVisible) {
            return;
        }
        brokenBlocksInitialized = true;
        lastBrokenBlocks = safeValue;
        lastBrokenBlocksVisible = visible;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#BrokenBlocksRow.Visible", visible);
        commandBuilder.set("#BrokenBlocksValue.Text", String.format("%,d", safeValue));
        update(false, commandBuilder);
    }

    public void updateBlinkCharges(int blinkCharges, boolean visible) {
        int safeValue = Math.max(0, blinkCharges);
        if (blinkChargesInitialized && safeValue == lastBlinkCharges && visible == lastBlinkChargesVisible) {
            return;
        }
        blinkChargesInitialized = true;
        lastBlinkCharges = safeValue;
        lastBlinkChargesVisible = visible;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#BlinkChargesRow.Visible", visible);
        commandBuilder.set("#BlinkChargesValue.Text", String.format("%,d", safeValue));
        update(false, commandBuilder);
    }
}
