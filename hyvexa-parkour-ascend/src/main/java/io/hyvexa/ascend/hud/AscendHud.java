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
    private String lastTimerText;
    private Boolean lastTimerVisible;
    private String lastAscensionProgressKey;

    // Track previous values for effect triggering (converted to double for comparison)
    private double[] lastDigits;
    private java.math.BigDecimal lastCoins;

    // Ascension quest bar constants
    private static final double ASCENSION_COST = 1_000_000_000_000.0; // 1 trillion
    private static final int QUEST_BAR_SEGMENTS = 100; // Number of segments in the progress bar (1% each)
    private static final int QUEST_ACCENT_SEGMENTS = 16; // Number of segments in the right accent bar

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

    public void updateEconomy(java.math.BigDecimal coins, java.math.BigDecimal product, java.math.BigDecimal[] digits, double elevation, boolean showElevation) {
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

    public void updateTimer(Long elapsedMs, boolean visible) {
        String timerText = elapsedMs != null ? formatTimer(elapsedMs) : "0.000";

        if (timerText.equals(lastTimerText) && Boolean.valueOf(visible).equals(lastTimerVisible)) {
            return;
        }

        lastTimerText = timerText;
        lastTimerVisible = visible;

        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#RunTimerHud.Visible", visible);
        if (visible) {
            commandBuilder.set("#RunTimerValue.Text", timerText);
        }
        update(false, commandBuilder);
    }

    private static String formatTimer(long elapsedMs) {
        long seconds = elapsedMs / 1000;
        long millis = elapsedMs % 1000;
        return String.format(java.util.Locale.US, "%d.%03d", seconds, millis);
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
        lastTimerText = null;
        lastTimerVisible = null;
        lastAscensionProgressKey = null;
        lastDigits = null;
        lastCoins = null;
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

    public void updateAscensionQuest(java.math.BigDecimal coins) {
        // Calculate logarithmic progress (0 to 1)
        // Using log10 scale: log10(coins + 1) / log10(1T + 1) ≈ log10(coins + 1) / 12
        double progress = 0.0;
        if (coins.compareTo(java.math.BigDecimal.ZERO) > 0) {
            // Convert BigDecimal to double for logarithmic calculation
            double coinsDouble = coins.doubleValue();
            progress = Math.log10(coinsDouble + 1) / Math.log10(ASCENSION_COST + 1);
            progress = Math.min(1.0, Math.max(0.0, progress)); // Clamp between 0 and 1
        }

        // Calculate filled segments (visual bar updates every 1%)
        int filledBarSegments = (int) (progress * QUEST_BAR_SEGMENTS);
        int filledAccentSegments = (int) (progress * QUEST_ACCENT_SEGMENTS);
        // Precise percentage with 2 decimals for display
        double percentPrecise = progress * 100;
        String percentText = String.format(java.util.Locale.US, "%.2f%%", percentPrecise);

        String progressKey = filledBarSegments + "|" + filledAccentSegments + "|" + percentText;

        if (progressKey.equals(lastAscensionProgressKey)) {
            return;
        }
        lastAscensionProgressKey = progressKey;

        UICommandBuilder commandBuilder = new UICommandBuilder();

        // Update percentage text (precise to 2 decimals)
        commandBuilder.set("#AscensionQuestPercent.Text", percentText);

        // Update main progress bar segments
        for (int i = 1; i <= QUEST_BAR_SEGMENTS; i++) {
            commandBuilder.set("#AscensionQuestBarContainer #Seg" + i + ".Visible", i <= filledBarSegments);
        }

        // Update right accent bar segments
        for (int i = 1; i <= QUEST_ACCENT_SEGMENTS; i++) {
            commandBuilder.set("#AscensionAccentRight #AccentSeg" + i + ".Visible", i <= filledAccentSegments);
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

    private static double[] normalizeDigits(java.math.BigDecimal[] digits) {
        double[] normalized = new double[] {1, 1, 1, 1, 1};
        if (digits == null) {
            return normalized;
        }
        int limit = Math.min(digits.length, normalized.length);
        for (int i = 0; i < limit; i++) {
            // Convert BigDecimal to double for display
            normalized[i] = Math.max(1.0, digits[i].doubleValue());
        }
        return normalized;
    }

    private static String buildDigitsKey(java.math.BigDecimal[] digits) {
        if (digits == null || digits.length == 0) {
            return "1|1|1|1|1";
        }
        StringBuilder key = new StringBuilder();
        int limit = Math.min(digits.length, 5);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                key.append('|');
            }
            java.math.BigDecimal value = digits[i].max(java.math.BigDecimal.ONE);
            key.append(formatMultiplier(value.doubleValue()));
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
