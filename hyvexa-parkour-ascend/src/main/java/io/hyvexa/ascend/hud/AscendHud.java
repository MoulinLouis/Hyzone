package io.hyvexa.ascend.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.common.util.FormatUtils;

import java.util.Map;

public class AscendHud extends CustomUIHud {

    private final HudEffectManager effectManager = new HudEffectManager();

    private String lastStaticKey;
    private String lastCoinsText;
    private String lastCoinsPerRunText;
    private String lastDigitsKey;
    private String lastElevationValueText;
    private String lastElevationText;
    private Boolean lastElevationVisible;
    private String lastPrestigeKey;
    private Boolean lastPrestigeVisible;

    // Track previous values for effect triggering
    private double[] lastDigits;
    private double lastCoins;

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

    public void updateEconomy(double coins, double product, double[] digits, double elevation, boolean showElevation) {
        String coinsText = FormatUtils.formatCoinsForHudDecimal(coins);
        String coinsPerRunText = FormatUtils.formatCoinsForHudDecimal(product) + "/run";
        String digitsKey = buildDigitsKey(digits);
        String elevationText = showElevation ? ("x" + formatMultiplier(elevation)) : "";
        String elevationValueText = formatMultiplier(elevation);
        // Check if values changed OR if we have active effects to process
        boolean valuesChanged = !coinsText.equals(lastCoinsText)
            || !coinsPerRunText.equals(lastCoinsPerRunText)
            || !digitsKey.equals(lastDigitsKey)
            || !elevationValueText.equals(lastElevationValueText)
            || !elevationText.equals(lastElevationText)
            || !Boolean.valueOf(showElevation).equals(lastElevationVisible);

        boolean hasActiveEffects = effectManager.hasActiveEffects();

        // Only skip update if nothing changed AND no active effects
        if (!valuesChanged && !hasActiveEffects) {
            return;
        }

        // If values changed, detect increases and update cache
        double[] safeDigits = null;
        if (valuesChanged) {
            safeDigits = normalizeDigits(digits);

            // Detect value increases and trigger effects (multipliers only)
            if (lastDigits != null) {
                String[] elementIds = {"#TopRedValue", "#TopOrangeValue", "#TopYellowValue", "#TopGreenValue", "#TopBlueValue"};
                for (int i = 0; i < Math.min(safeDigits.length, lastDigits.length); i++) {
                    if (safeDigits[i] > lastDigits[i]) {
                        effectManager.triggerMultiplierEffect(elementIds[i], i);
                    }
                }
            }

            // Update cached values
            lastCoinsText = coinsText;
            lastCoinsPerRunText = coinsPerRunText;
            lastDigitsKey = digitsKey;
            lastElevationValueText = elevationValueText;
            lastElevationText = elevationText;
            lastElevationVisible = showElevation;
            lastDigits = safeDigits.clone();
            lastCoins = coins;
        }

        // Create command builder
        UICommandBuilder commandBuilder = new UICommandBuilder();

        // If values changed, set all UI text values
        if (valuesChanged) {
            commandBuilder.set("#TopCoinsValue.Text", coinsText);
            commandBuilder.set("#TopCoinsPerRunValue.Text", coinsPerRunText);
            commandBuilder.set("#TopRedValue.Text", formatMultiplier(safeDigits[0]));
            commandBuilder.set("#TopOrangeValue.Text", formatMultiplier(safeDigits[1]));
            commandBuilder.set("#TopYellowValue.Text", formatMultiplier(safeDigits[2]));
            commandBuilder.set("#TopGreenValue.Text", formatMultiplier(safeDigits[3]));
            commandBuilder.set("#TopBlueValue.Text", formatMultiplier(safeDigits[4]));
            commandBuilder.set("#TopElevationValue.Text", elevationValueText);
            commandBuilder.set("#ElevationHud.Visible", showElevation);
            if (showElevation) {
                commandBuilder.set("#ElevationStatusText.Text", "Elevation " + elevationText);
            }
        }

        // Always apply effects if we have any (whether values changed or not)
        effectManager.update(commandBuilder);

        // Send the update
        update(false, commandBuilder);
    }

    public void resetCache() {
        lastStaticKey = null;
        lastCoinsText = null;
        lastCoinsPerRunText = null;
        lastDigitsKey = null;
        lastElevationValueText = null;
        lastElevationText = null;
        lastElevationVisible = null;
        lastPrestigeKey = null;
        lastPrestigeVisible = null;
        lastDigits = null;
        lastCoins = 0;
        effectManager.clearEffects();
    }

    public void updatePrestige(Map<SummitCategory, Integer> summitLevels, int ascensionCount, int skillPoints) {
        boolean showPrestige = ascensionCount > 0 || hasSummitLevels(summitLevels);
        String prestigeKey = buildPrestigeKey(summitLevels, ascensionCount, skillPoints);

        if (prestigeKey.equals(lastPrestigeKey) && Boolean.valueOf(showPrestige).equals(lastPrestigeVisible)) {
            return;
        }

        lastPrestigeKey = prestigeKey;
        lastPrestigeVisible = showPrestige;

        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#PrestigeHud.Visible", showPrestige);

        if (showPrestige) {
            int coinLevel = summitLevels.getOrDefault(SummitCategory.COIN_FLOW, 0);
            int speedLevel = summitLevels.getOrDefault(SummitCategory.RUNNER_SPEED, 0);
            int manualLevel = summitLevels.getOrDefault(SummitCategory.MANUAL_MASTERY, 0);

            String summitText = "Summit: Coin " + coinLevel + " | Speed " + speedLevel + " | Manual " + manualLevel;

            commandBuilder.set("#SummitText.Text", summitText);
        }

        update(false, commandBuilder);
    }

    private boolean hasSummitLevels(Map<SummitCategory, Integer> summitLevels) {
        if (summitLevels == null || summitLevels.isEmpty()) {
            return false;
        }
        for (Integer level : summitLevels.values()) {
            if (level != null && level > 0) {
                return true;
            }
        }
        return false;
    }

    private String buildPrestigeKey(Map<SummitCategory, Integer> summitLevels, int ascensionCount, int skillPoints) {
        int coinLevel = summitLevels != null ? summitLevels.getOrDefault(SummitCategory.COIN_FLOW, 0) : 0;
        int speedLevel = summitLevels != null ? summitLevels.getOrDefault(SummitCategory.RUNNER_SPEED, 0) : 0;
        int manualLevel = summitLevels != null ? summitLevels.getOrDefault(SummitCategory.MANUAL_MASTERY, 0) : 0;
        return coinLevel + "|" + speedLevel + "|" + manualLevel;
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
