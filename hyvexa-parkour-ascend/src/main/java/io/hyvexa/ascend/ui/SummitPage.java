package io.hyvexa.ascend.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;

public class SummitPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_SUMMIT_PREFIX = "Summit_";

    private final AscendPlayerStore playerStore;
    private final SummitManager summitManager;

    public SummitPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore, SummitManager summitManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.summitManager = summitManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Summit.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        buildCategoryCards(ref, store, commandBuilder, eventBuilder);
    }

    private void buildCategoryCards(Ref<EntityStore> ref, Store<EntityStore> store,
                                     UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#CategoryCards");
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        java.math.BigDecimal coins = playerStore.getCoins(playerId);

        // Update coins display with XP conversion preview
        long potentialXp = AscendConstants.coinsToXp(coins);
        String coinsText = FormatUtils.formatCoinsForHudDecimal(coins) + " → +" + potentialXp + " XP";
        commandBuilder.set("#CoinsValue.Text", coinsText);

        // Build 3 category cards
        SummitCategory[] categories = {
            SummitCategory.MULTIPLIER_GAIN,
            SummitCategory.RUNNER_SPEED,
            SummitCategory.EVOLUTION_POWER
        };

        for (int i = 0; i < categories.length; i++) {
            SummitCategory category = categories[i];
            commandBuilder.append("#CategoryCards", "Pages/Ascend_SummitEntry.ui");

            String accentColor = resolveCategoryAccentColor(i);
            SummitManager.SummitPreview preview = summitManager.previewSummit(playerId, category);

            // Apply accent color to bars
            commandBuilder.set("#CategoryCards[" + i + "] #AccentBar.Background", accentColor);
            commandBuilder.set("#CategoryCards[" + i + "] #ButtonAccent.Background", accentColor);
            commandBuilder.set("#CategoryCards[" + i + "] #CurrentLevel.Style.TextColor", accentColor);

            // Category name
            commandBuilder.set("#CategoryCards[" + i + "] #CategoryName.Text", category.getDisplayName());

            // Category description based on type
            String description = getCategoryDescription(category);
            commandBuilder.set("#CategoryCards[" + i + "] #CategoryDesc.Text", description);

            // Current bonus
            String bonusText = "Current Bonus: " + formatBonus(category, preview.currentBonus());
            commandBuilder.set("#CategoryCards[" + i + "] #CategoryBonus.Text", bonusText);

            // Level and bonus display
            String levelText = "[Lv. " + preview.currentLevel() + "] " + formatBonus(category, preview.currentBonus());
            commandBuilder.set("#CategoryCards[" + i + "] #CurrentLevel.Text", levelText);

            if (preview.hasGain()) {
                String previewText = "→ [Lv. " + preview.newLevel() + "] " + formatBonus(category, preview.newBonus());
                commandBuilder.set("#CategoryCards[" + i + "] #PreviewLevel.Text", previewText);
            } else {
                commandBuilder.set("#CategoryCards[" + i + "] #PreviewLevel.Text", "");
            }

            // XP progress text
            String xpText = String.format("Exp %d/%d (+%d)",
                preview.currentXpInLevel(),
                preview.currentXpRequired(),
                preview.xpToGain());
            commandBuilder.set("#CategoryCards[" + i + "] #XpProgress.Text", xpText);

            // XP progress bar fill - use Right anchor (0 = full, ~390 = empty)
            double progressPercent = preview.currentXpRequired() > 0
                ? (double) preview.currentXpInLevel() / preview.currentXpRequired()
                : 0;
            int rightOffset = (int)((1.0 - progressPercent) * 390);
            commandBuilder.set("#CategoryCards[" + i + "] #XpBarFill.Anchor.Right", rightOffset);

            // Event binding
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#CategoryCards[" + i + "] #SummitButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SUMMIT_PREFIX + category.name()), false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }

        if (BUTTON_CLOSE.equals(data.getButton())) {
            this.close();
            return;
        }

        if (data.getButton().startsWith(BUTTON_SUMMIT_PREFIX)) {
            String categoryName = data.getButton().substring(BUTTON_SUMMIT_PREFIX.length());
            try {
                SummitCategory category = SummitCategory.valueOf(categoryName);
                handleSummit(ref, store, category);
            } catch (IllegalArgumentException e) {
                // Invalid category name
            }
        }
    }

    private void handleSummit(Ref<EntityStore> ref, Store<EntityStore> store, SummitCategory category) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        if (!summitManager.canSummit(playerId)) {
            java.math.BigDecimal coins = playerStore.getCoins(playerId);
            long minCoins = io.hyvexa.ascend.AscendConstants.SUMMIT_MIN_COINS;
            player.sendMessage(Message.raw("[Summit] Need " + minCoins
                + " coins to Summit. You have: " + coins)
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        SummitManager.SummitPreview preview = summitManager.previewSummit(playerId, category);
        if (!preview.hasGain()) {
            player.sendMessage(Message.raw("[Summit] Insufficient coins for level gain.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        SummitManager.SummitResult result = summitManager.performSummit(playerId, category);
        if (!result.succeeded()) {
            player.sendMessage(Message.raw("[Summit] Summit failed.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        // Despawn all runners (player loses them on Summit, like elevation)
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            RobotManager robotManager = plugin.getRobotManager();
            if (robotManager != null && !result.mapsWithRunners().isEmpty()) {
                for (String mapId : result.mapsWithRunners()) {
                    robotManager.despawnRobot(playerId, mapId);
                }
            }
        }

        player.sendMessage(Message.raw("[Summit] " + category.getDisplayName() + " Lv." + preview.currentLevel()
            + " → Lv." + result.newLevel() + " (+" + preview.levelGain() + ")")
            .color(SystemMessageUtils.SUCCESS));
        player.sendMessage(Message.raw("[Summit] Bonus: " + formatBonus(category, preview.currentBonus())
            + " → " + formatBonus(category, preview.newBonus()))
            .color(SystemMessageUtils.SUCCESS));
        player.sendMessage(Message.raw("[Summit] Progress reset: elevation, multipliers, unlocks, runners")
            .color(SystemMessageUtils.SECONDARY));

        // Check achievements
        if (plugin != null && plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
        }

        // Rebuild the entire page to refresh all category cards
        UICommandBuilder updateBuilder = new UICommandBuilder();
        UIEventBuilder updateEventBuilder = new UIEventBuilder();
        buildCategoryCards(ref, store, updateBuilder, updateEventBuilder);
        sendUpdate(updateBuilder, updateEventBuilder, false);
    }

    private String resolveCategoryAccentColor(int index) {
        return switch (index) {
            case 0 -> "#5a6b3d";  // Green for Multiplier Gain
            case 1 -> "#2d5a7b";  // Blue for Runner Speed
            default -> "#5a3d6b"; // Purple for Evolution Power
        };
    }

    private String getCategoryDescription(SummitCategory category) {
        return switch (category) {
            case RUNNER_SPEED -> "Multiplies runner completion speed";
            case MULTIPLIER_GAIN -> "Multiplies multiplier gain per run";
            case EVOLUTION_POWER -> "Multiplies map multiplier on evolution";
        };
    }

    /**
     * Format a bonus value for display - all categories show as multipliers.
     */
    private String formatBonus(SummitCategory category, double value) {
        return String.format(Locale.US, "×%.2f", value);
    }
}
