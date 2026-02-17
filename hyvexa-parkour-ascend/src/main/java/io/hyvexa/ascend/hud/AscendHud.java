package io.hyvexa.ascend.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.util.FormatUtils;

import java.util.Map;

public class AscendHud extends CustomUIHud {

    private final HudEffectManager effectManager = new HudEffectManager();
    private final ToastManager toastManager = new ToastManager();

    private String lastStaticKey;
    private String lastVexaText;
    private String lastVexaPerRunText;
    private String lastDigitsKey;
    private String lastElevationValueText;
    private String lastElevationText;
    private Boolean lastElevationVisible;
    private String lastPrestigeKey;
    private Boolean lastPrestigeVisible;
    private String lastAscensionKey;
    private Boolean lastAscensionVisible;
    private String lastTimerText;
    private Boolean lastTimerVisible;
    private String lastAscensionProgressKey;
    private String lastRunnerBarKey;
    private int lastPlayerCount = -1;
    private long lastGems = -1;

    // Track previous values for effect triggering (converted to double for comparison)
    private double[] lastDigits;
    private BigNumber lastVexa;

    // Ascension quest bar constants
    private static final double ASCENSION_COST = 1e33; // 1 Decillion (1Dc)
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
        commandBuilder.set("#PlayerNameText.Text", "Ascend");
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

    public void updateEconomy(BigNumber vexa, BigNumber product, BigNumber[] digits, int currentElevation, int potentialElevation, boolean showElevation) {
        String vexaText = FormatUtils.formatBigNumber(vexa);
        String vexaPerRunText = FormatUtils.formatBigNumber(product) + "/run";
        String digitsKey = buildDigitsKey(digits);
        String elevationText;
        if (showElevation && potentialElevation > currentElevation) {
            elevationText = AscendConstants.formatElevationMultiplier(currentElevation)
                + " -> " + AscendConstants.formatElevationMultiplier(potentialElevation);
        } else {
            elevationText = AscendConstants.formatElevationMultiplier(currentElevation);
        }
        String elevationValueText = formatMultiplier(AscendConstants.getElevationMultiplier(currentElevation));
        // Check if values changed OR if we have active effects to process
        boolean valuesChanged = !vexaText.equals(lastVexaText)
            || !vexaPerRunText.equals(lastVexaPerRunText)
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
            lastVexaText = vexaText;
            lastVexaPerRunText = vexaPerRunText;
            lastDigitsKey = digitsKey;
            lastElevationValueText = elevationValueText;
            lastElevationText = elevationText;
            lastElevationVisible = showElevation;
            lastDigits = safeDigits.clone();
            lastVexa = vexa;
        }

        // Create command builder
        UICommandBuilder commandBuilder = new UICommandBuilder();

        // If values changed, set all UI text values
        if (valuesChanged) {
            commandBuilder.set("#TopVexaValue.Text", vexaText);
            commandBuilder.set("#TopVexaPerRunValue.Text", vexaPerRunText);
            commandBuilder.set("#TopRedValue.Text", formatMultiplier(safeDigits[0]));
            commandBuilder.set("#TopOrangeValue.Text", formatMultiplier(safeDigits[1]));
            commandBuilder.set("#TopYellowValue.Text", formatMultiplier(safeDigits[2]));
            commandBuilder.set("#TopGreenValue.Text", formatMultiplier(safeDigits[3]));
            commandBuilder.set("#TopBlueValue.Text", formatMultiplier(safeDigits[4]));
            commandBuilder.set("#TopElevationValue.Text", elevationValueText);
            commandBuilder.set("#ElevationHud.Visible", showElevation);
            if (showElevation) {
                commandBuilder.set("#ElevationStatusText.Text", elevationText);
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

    public void updateRunnerBars(float[] progress) {
        StringBuilder keyBuilder = new StringBuilder();
        for (int i = 0; i < progress.length; i++) {
            int quantized = (int) (progress[i] * 1000);
            if (i > 0) keyBuilder.append('|');
            keyBuilder.append(quantized);
        }
        String key = keyBuilder.toString();
        if (key.equals(lastRunnerBarKey)) {
            return;
        }
        lastRunnerBarKey = key;
        UICommandBuilder commandBuilder = new UICommandBuilder();
        for (int i = 0; i < progress.length; i++) {
            boolean active = progress[i] > 0;
            commandBuilder.set("#MomentumBar" + i + ".Visible", active);
            if (active) {
                commandBuilder.set("#MomentumBar" + i + ".Value", progress[i]);
            }
        }
        update(false, commandBuilder);
    }

    public void showToast(ToastType type, String message) {
        toastManager.showToast(type, message);
    }

    public void updateToasts() {
        if (!toastManager.hasActiveToasts()) {
            return;
        }
        UICommandBuilder commandBuilder = new UICommandBuilder();
        toastManager.update(commandBuilder);
        update(false, commandBuilder);
    }

    public void resetCache() {
        lastStaticKey = null;
        lastVexaText = null;
        lastVexaPerRunText = null;
        lastDigitsKey = null;
        lastElevationValueText = null;
        lastElevationText = null;
        lastElevationVisible = null;
        lastPrestigeKey = null;
        lastPrestigeVisible = null;
        lastAscensionKey = null;
        lastAscensionVisible = null;
        lastTimerText = null;
        lastTimerVisible = null;
        lastAscensionProgressKey = null;
        lastRunnerBarKey = null;
        lastPlayerCount = -1;
        lastGems = -1;
        lastDigits = null;
        lastVexa = null;
        effectManager.clearEffects();
        toastManager.clear();
    }

    public void updatePrestige(Map<SummitCategory, Integer> summitLevels, int ascensionCount, int skillPoints,
                               SummitManager.SummitPreview multPreview, SummitManager.SummitPreview speedPreview, SummitManager.SummitPreview evoPreview) {
        boolean showPrestige = hasSummitLevels(summitLevels) || hasAnyPreviewGain(multPreview, speedPreview, evoPreview);
        String prestigeKey = buildPrestigeKey(summitLevels, ascensionCount, skillPoints, multPreview, speedPreview, evoPreview);

        if (prestigeKey.equals(lastPrestigeKey) && Boolean.valueOf(showPrestige).equals(lastPrestigeVisible)) {
            return;
        }

        lastPrestigeKey = prestigeKey;
        lastPrestigeVisible = showPrestige;

        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#PrestigeHud.Visible", showPrestige);

        if (showPrestige) {
            commandBuilder.set("#SummitMultText.Text", formatSummitLine("Multiplier Gain", multPreview));
            commandBuilder.set("#SummitSpeedText.Text", formatSummitLine("Runner Speed", speedPreview));
            commandBuilder.set("#SummitEvoText.Text", formatSummitLine("Evolution Power", evoPreview));
        }

        update(false, commandBuilder);
    }

    private static String formatSummitLine(String name, SummitManager.SummitPreview preview) {
        if (preview == null) {
            return name + " Lv.0";
        }
        if (preview.hasGain()) {
            return name + " Lv." + FormatUtils.formatLong(preview.currentLevel()) + " -> Lv." + FormatUtils.formatLong(preview.newLevel());
        }
        return name + " Lv." + FormatUtils.formatLong(preview.currentLevel());
    }

    public void updateAscension(int ascensionCount, int availableAP) {
        boolean show = ascensionCount > 0;
        String key = ascensionCount + "|" + availableAP;

        if (key.equals(lastAscensionKey) && Boolean.valueOf(show).equals(lastAscensionVisible)) {
            return;
        }

        lastAscensionKey = key;
        lastAscensionVisible = show;

        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#AscensionHud.Visible", show);

        if (show) {
            commandBuilder.set("#AscensionApText.Text", "AP: " + availableAP + " available");
            commandBuilder.set("#AscensionCountText.Text", "Ascendance: " + ascensionCount);
        }

        update(false, commandBuilder);
    }

    public void updateAscensionQuest(BigNumber vexa) {
        // Calculate logarithmic progress (0 to 1)
        // Using log10 scale: log10(vexa + 1) / log10(1Dc + 1) ≈ log10(vexa + 1) / 33
        double progress = 0.0;
        if (vexa.gt(BigNumber.ZERO)) {
            // Convert BigNumber to double for logarithmic calculation
            double vexaDouble = vexa.toDouble();
            progress = Math.log10(vexaDouble + 1) / Math.log10(ASCENSION_COST + 1);
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

    private static boolean hasAnyPreviewGain(SummitManager.SummitPreview... previews) {
        for (SummitManager.SummitPreview p : previews) {
            if (p != null && p.hasGain()) {
                return true;
            }
        }
        return false;
    }

    private String buildPrestigeKey(Map<SummitCategory, Integer> summitLevels, int ascensionCount, int skillPoints,
                                    SummitManager.SummitPreview multPreview, SummitManager.SummitPreview speedPreview, SummitManager.SummitPreview evoPreview) {
        int speedLevel = summitLevels != null ? summitLevels.getOrDefault(SummitCategory.RUNNER_SPEED, 0) : 0;
        int multLevel = summitLevels != null ? summitLevels.getOrDefault(SummitCategory.MULTIPLIER_GAIN, 0) : 0;
        int evolveLevel = summitLevels != null ? summitLevels.getOrDefault(SummitCategory.EVOLUTION_POWER, 0) : 0;
        int multNew = multPreview != null ? multPreview.newLevel() : multLevel;
        int speedNew = speedPreview != null ? speedPreview.newLevel() : speedLevel;
        int evoNew = evoPreview != null ? evoPreview.newLevel() : evolveLevel;
        return multLevel + ">" + multNew + "|" + speedLevel + ">" + speedNew + "|" + evolveLevel + ">" + evoNew;
    }

    private static double[] normalizeDigits(BigNumber[] digits) {
        double[] normalized = new double[] {1, 1, 1, 1, 1};
        if (digits == null) {
            return normalized;
        }
        int limit = Math.min(digits.length, normalized.length);
        for (int i = 0; i < limit; i++) {
            // Convert BigNumber to double for display
            normalized[i] = Math.max(1.0, digits[i].toDouble());
        }
        return normalized;
    }

    private static String buildDigitsKey(BigNumber[] digits) {
        if (digits == null || digits.length == 0) {
            return "1|1|1|1|1";
        }
        StringBuilder key = new StringBuilder();
        int limit = Math.min(digits.length, 5);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                key.append('|');
            }
            BigNumber value = digits[i].max(BigNumber.ONE);
            key.append(formatMultiplier(value.toDouble()));
        }
        while (limit < 5) {
            key.append("|1");
            limit++;
        }
        return key.toString();
    }

    private static String formatMultiplier(double value) {
        double safeValue = Math.max(1.0, value);
        if (safeValue < 1e6) {
            return String.format(java.util.Locale.US, "%.2f", safeValue);
        }
        return FormatUtils.formatBigNumber(BigNumber.fromDouble(safeValue));
    }
}
