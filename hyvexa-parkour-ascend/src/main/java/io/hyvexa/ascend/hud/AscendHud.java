package io.hyvexa.ascend.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class AscendHud extends CustomUIHud {

    private String lastStaticKey;
    private String lastCoinsText;
    private String lastProductText;
    private String lastDigitsKey;

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

    public void updateEconomy(long coins, long product, int[] digits) {
        String coinsText = String.valueOf(Math.max(0L, coins));
        String productText = String.valueOf(Math.max(0L, product));
        String digitsKey = buildDigitsKey(digits);
        if (coinsText.equals(lastCoinsText)
            && productText.equals(lastProductText)
            && digitsKey.equals(lastDigitsKey)) {
            return;
        }
        lastCoinsText = coinsText;
        lastProductText = productText;
        lastDigitsKey = digitsKey;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#CoinsValue.Text", coinsText);
        commandBuilder.set("#MultiplierValue.Text", productText);
        int[] safeDigits = normalizeDigits(digits);
        commandBuilder.set("#TopRedValue.Text", String.valueOf(safeDigits[0]));
        commandBuilder.set("#TopOrangeValue.Text", String.valueOf(safeDigits[1]));
        commandBuilder.set("#TopYellowValue.Text", String.valueOf(safeDigits[2]));
        commandBuilder.set("#TopGreenValue.Text", String.valueOf(safeDigits[3]));
        commandBuilder.set("#TopBlueValue.Text", String.valueOf(safeDigits[4]));
        update(false, commandBuilder);
    }

    public void resetCache() {
        lastStaticKey = null;
        lastCoinsText = null;
        lastProductText = null;
        lastDigitsKey = null;
    }

    private static int[] normalizeDigits(int[] digits) {
        int[] normalized = new int[] {1, 1, 1, 1, 1};
        if (digits == null) {
            return normalized;
        }
        int limit = Math.min(digits.length, normalized.length);
        for (int i = 0; i < limit; i++) {
            normalized[i] = Math.max(1, digits[i]);
        }
        return normalized;
    }

    private static String buildDigitsKey(int[] digits) {
        if (digits == null || digits.length == 0) {
            return "1|1|1|1|1";
        }
        StringBuilder key = new StringBuilder();
        int limit = Math.min(digits.length, 5);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                key.append('|');
            }
            key.append(Math.max(1, digits[i]));
        }
        while (limit < 5) {
            key.append("|1");
            limit++;
        }
        return key.toString();
    }
}
