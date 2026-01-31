package io.hyvexa.ascend.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class AscendHud extends CustomUIHud {

    private String lastStaticKey;
    private String lastCoinsText;
    private String lastPendingText;
    private String lastMultiplierText;

    public AscendHud(PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(UICommandBuilder commandBuilder) {
        commandBuilder.append("Pages/Ascend_RunHud.ui");
    }

    public void applyStaticText() {
        String key = "static";
        if (key.equals(lastStaticKey)) {
            return;
        }
        lastStaticKey = key;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#PlayerNameText.Text", "HYVEXA ASCEND");
        update(false, commandBuilder);
    }

    public void updateEconomy(long coins, long pending, String multiplierText) {
        String coinsText = String.valueOf(Math.max(0L, coins));
        String pendingText = String.valueOf(Math.max(0L, pending));
        String safeMultiplier = multiplierText != null ? multiplierText : "x1.00";
        if (coinsText.equals(lastCoinsText)
            && pendingText.equals(lastPendingText)
            && safeMultiplier.equals(lastMultiplierText)) {
            return;
        }
        lastCoinsText = coinsText;
        lastPendingText = pendingText;
        lastMultiplierText = safeMultiplier;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#CoinsValue.Text", coinsText);
        commandBuilder.set("#PendingValue.Text", pendingText);
        commandBuilder.set("#MultiplierValue.Text", safeMultiplier);
        update(false, commandBuilder);
    }

    public void resetCache() {
        lastStaticKey = null;
        lastCoinsText = null;
        lastPendingText = null;
        lastMultiplierText = null;
    }
}
