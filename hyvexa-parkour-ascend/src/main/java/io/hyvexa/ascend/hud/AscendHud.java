package io.hyvexa.ascend.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.common.util.FormatUtils;

public class AscendHud extends CustomUIHud {

    private String lastStaticKey;
    private String lastCoinsText;
    private String lastDigitsKey;
    private String lastElevationValueText;
    private String lastElevationText;
    private Boolean lastElevationVisible;

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

    public void updateEconomy(long coins, long product, double[] digits, int elevationMultiplier, boolean showElevation) {
        String coinsText = FormatUtils.formatCoinsForHud(coins);
        String digitsKey = buildDigitsKey(digits);
        int currentElevation = Math.max(1, elevationMultiplier);
        int elevationGain = showElevation ? (int) (Math.max(0L, coins) / 1000L) : 0;
        int nextElevation = currentElevation + elevationGain;
        String elevationText = showElevation ? ("x" + currentElevation + " -> x" + nextElevation) : "";
        String elevationValueText = formatMultiplier(currentElevation);
        if (coinsText.equals(lastCoinsText)
            && digitsKey.equals(lastDigitsKey)
            && elevationValueText.equals(lastElevationValueText)
            && elevationText.equals(lastElevationText)
            && Boolean.valueOf(showElevation).equals(lastElevationVisible)) {
            return;
        }
        lastCoinsText = coinsText;
        lastDigitsKey = digitsKey;
        lastElevationValueText = elevationValueText;
        lastElevationText = elevationText;
        lastElevationVisible = showElevation;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#TopCoinsValue.Text", coinsText);
        double[] safeDigits = normalizeDigits(digits);
        commandBuilder.set("#TopRedValue.Text", formatMultiplier(safeDigits[0]));
        commandBuilder.set("#TopOrangeValue.Text", formatMultiplier(safeDigits[1]));
        commandBuilder.set("#TopYellowValue.Text", formatMultiplier(safeDigits[2]));
        commandBuilder.set("#TopGreenValue.Text", formatMultiplier(safeDigits[3]));
        commandBuilder.set("#TopBlueValue.Text", formatMultiplier(safeDigits[4]));
        commandBuilder.set("#TopElevationValue.Text", elevationValueText);
        commandBuilder.set("#ElevationHud.Visible", showElevation);
        if (showElevation) {
            commandBuilder.set("#ElevationStatusText.Text", "Current Multiplier " + elevationText);
        }
        update(false, commandBuilder);
    }

    public void resetCache() {
        lastStaticKey = null;
        lastCoinsText = null;
        lastDigitsKey = null;
        lastElevationValueText = null;
        lastElevationText = null;
        lastElevationVisible = null;
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
