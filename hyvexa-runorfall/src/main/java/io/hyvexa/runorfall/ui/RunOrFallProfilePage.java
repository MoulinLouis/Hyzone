package io.hyvexa.runorfall.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.runorfall.manager.RunOrFallStatsStore;

import javax.annotation.Nonnull;

public class RunOrFallProfilePage extends InteractiveCustomUIPage<ButtonEventData> {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_STATS = "Stats";
    private static final String BUTTON_ACHIEVEMENTS = "Achievements";
    private static final String BUTTON_SETTINGS = "Settings";

    private final RunOrFallStatsStore statsStore;

    public RunOrFallProfilePage(@Nonnull PlayerRef playerRef, RunOrFallStatsStore statsStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ButtonEventData.CODEC);
        this.statsStore = statsStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/RunOrFall_Profile.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#StatsButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_STATS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AchievementsButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ACHIEVEMENTS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SettingsButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SETTINGS), false);
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
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if (BUTTON_STATS.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store,
                    new RunOrFallStatsPage(playerRef, statsStore));
            return;
        }
        if (BUTTON_ACHIEVEMENTS.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store,
                    new RunOrFallAchievementsPage(playerRef, statsStore));
            return;
        }
        if (BUTTON_SETTINGS.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store,
                    new RunOrFallSettingsPage(playerRef, statsStore, true));
        }
    }
}
