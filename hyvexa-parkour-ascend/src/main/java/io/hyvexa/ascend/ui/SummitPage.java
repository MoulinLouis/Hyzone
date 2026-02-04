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
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendPlayerStore;
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
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        java.math.BigDecimal coins = playerStore.getCoins(playerId);

        // Update coins display
        commandBuilder.set("#CoinsValue.Text", FormatUtils.formatCoinsForHudDecimal(coins));

        // Build 3 category cards
        SummitCategory[] categories = {
            SummitCategory.COIN_FLOW,
            SummitCategory.RUNNER_SPEED,
            SummitCategory.MANUAL_MASTERY
        };

        for (int i = 0; i < categories.length; i++) {
            SummitCategory category = categories[i];
            commandBuilder.append("#CategoryCards", "Pages/Ascend_SummitEntry.ui");

            String accentColor = resolveCategoryAccentColor(i);
            int currentLevel = playerStore.getSummitLevel(playerId, category);
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
            double currentBonus = category.getBonusForLevel(currentLevel);
            String bonusText = "Current Bonus: " + formatPercent(currentBonus);
            commandBuilder.set("#CategoryCards[" + i + "] #CategoryBonus.Text", bonusText);

            // Level and preview
            commandBuilder.set("#CategoryCards[" + i + "] #CurrentLevel.Text", "Lv." + currentLevel);

            if (preview.hasGain()) {
                commandBuilder.set("#CategoryCards[" + i + "] #PreviewLevel.Text", "→ Lv." + preview.newLevel());
            } else {
                commandBuilder.set("#CategoryCards[" + i + "] #PreviewLevel.Text", "");
            }

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

        int newLevel = summitManager.performSummit(playerId, category);
        if (newLevel < 0) {
            player.sendMessage(Message.raw("[Summit] Summit failed.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        player.sendMessage(Message.raw("[Summit] " + category.getDisplayName() + " Lv." + preview.currentLevel()
            + " → Lv." + newLevel + " (+" + preview.levelGain() + ")")
            .color(SystemMessageUtils.SUCCESS));
        player.sendMessage(Message.raw("[Summit] Bonus: " + formatPercent(preview.currentBonus())
            + " → " + formatPercent(preview.newBonus()))
            .color(SystemMessageUtils.SUCCESS));

        // Check achievements
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
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
            case 0 -> "#5a6b3d";  // Green for Coin Flow
            case 1 -> "#2d5a7b";  // Blue for Runner Speed
            default -> "#5a3d6b"; // Purple for Manual Mastery
        };
    }

    private String getCategoryDescription(SummitCategory category) {
        return switch (category) {
            case COIN_FLOW -> "Increases base coin earnings (" + formatBonusPerLevel(category) + " per level)";
            case RUNNER_SPEED -> "Increases runner completion speed (" + formatBonusPerLevel(category) + " per level)";
            case MANUAL_MASTERY -> "Increases manual run multiplier gain (" + formatBonusPerLevel(category) + " per level)";
        };
    }

    private String formatPercent(double value) {
        return String.format(Locale.US, "+%.0f%%", value * 100);
    }

    private String formatBonusPerLevel(SummitCategory category) {
        return String.format(Locale.US, "+%.0f%%", category.getBonusPerLevel() * 100);
    }
}
