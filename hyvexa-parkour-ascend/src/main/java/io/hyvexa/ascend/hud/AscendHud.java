package io.hyvexa.ascend.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.common.util.FormatUtils;

public class AscendHud extends CustomUIHud {

    private String lastStaticKey;
    private String lastCoinsText;
    private String lastDigitsKey;
    private String lastRebirthValueText;
    private String lastRebirthText;
    private Boolean lastRebirthVisible;

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

    public void updateEconomy(long coins, long product, double[] digits, int rebirthMultiplier, boolean showRebirth) {
        String coinsText = FormatUtils.formatCoinsForHud(coins);
        String digitsKey = buildDigitsKey(digits);
        int currentRebirth = Math.max(1, rebirthMultiplier);
        int rebirthGain = showRebirth ? (int) (Math.max(0L, coins) / 1000L) : 0;
        int nextRebirth = currentRebirth + rebirthGain;
        String rebirthText = showRebirth ? ("x" + currentRebirth + " -> x" + nextRebirth) : "";
        String rebirthValueText = formatMultiplier(currentRebirth);
        if (coinsText.equals(lastCoinsText)
            && digitsKey.equals(lastDigitsKey)
            && rebirthValueText.equals(lastRebirthValueText)
            && rebirthText.equals(lastRebirthText)
            && Boolean.valueOf(showRebirth).equals(lastRebirthVisible)) {
            return;
        }
        lastCoinsText = coinsText;
        lastDigitsKey = digitsKey;
        lastRebirthValueText = rebirthValueText;
        lastRebirthText = rebirthText;
        lastRebirthVisible = showRebirth;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#TopCoinsValue.Text", coinsText);
        double[] safeDigits = normalizeDigits(digits);
        commandBuilder.set("#TopRedValue.Text", formatMultiplier(safeDigits[0]));
        commandBuilder.set("#TopOrangeValue.Text", formatMultiplier(safeDigits[1]));
        commandBuilder.set("#TopYellowValue.Text", formatMultiplier(safeDigits[2]));
        commandBuilder.set("#TopGreenValue.Text", formatMultiplier(safeDigits[3]));
        commandBuilder.set("#TopBlueValue.Text", formatMultiplier(safeDigits[4]));
        commandBuilder.set("#TopRebirthValue.Text", rebirthValueText);
        commandBuilder.set("#RebirthHud.Visible", showRebirth);
        if (showRebirth) {
            commandBuilder.set("#RebirthStatusText.Text", "Current Multiplier " + rebirthText);
        }
        update(false, commandBuilder);
    }

    public void resetCache() {
        lastStaticKey = null;
        lastCoinsText = null;
        lastDigitsKey = null;
        lastRebirthValueText = null;
        lastRebirthText = null;
        lastRebirthVisible = null;
    }

    private static double[] normalizeDigits(double[] digits) {
        double[] normalized = new double[] {1, 1, 1, 1, 1};
        if (digits == null) {
            return normalized;
        }
        int limit = Math.min(digits.length, normalized.length);
        for (int i = 0; i < limit; i++) {
            normalized[i] = Math.max(1.0, digits[i]);
        }
        return normalized;
    }

    private static String buildDigitsKey(double[] digits) {
        if (digits == null || digits.length == 0) {
            return "1|1|1|1|1";
        }
        StringBuilder key = new StringBuilder();
        int limit = Math.min(digits.length, 5);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                key.append('|');
            }
            key.append(formatMultiplier(Math.max(1.0, digits[i])));
        }
        while (limit < 5) {
            key.append("|1");
            limit++;
        }
        return key.toString();
    }

    private static String formatMultiplier(double value) {
        double safeValue = Math.max(1.0, value);
        return String.format(java.util.Locale.US, "%.2f", safeValue);
    }
}
