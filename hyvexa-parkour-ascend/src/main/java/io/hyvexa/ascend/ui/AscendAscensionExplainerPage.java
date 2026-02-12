package io.hyvexa.ascend.ui;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.common.ui.ButtonEventData;

import java.util.UUID;

public class AscendAscensionExplainerPage extends BaseAscendPage {

    private static final String BUTTON_CONTINUE = "Continue";

    private final UUID playerId;
    private volatile boolean proceeded = false;

    public AscendAscensionExplainerPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerId = playerRef.getUuid();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Ascend_AscensionExplainer.ui");

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ContinueButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CONTINUE), false);

        cmd.set("#ExplainerTitle.Text", AscendOnboardingCopy.EXPLAINER_TITLE);
        cmd.set("#ExplainerDescription.Text", AscendOnboardingCopy.EXPLAINER_DESCRIPTION);
        cmd.set("#ContinueButton.Text", AscendOnboardingCopy.EXPLAINER_BUTTON);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (BUTTON_CONTINUE.equals(data.getButton())) {
            proceed();
            this.close();
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        proceed();
        super.onDismiss(ref, store);
    }

    private void proceed() {
        if (proceeded) return;
        proceeded = true;
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getPlayerStore() != null) {
            plugin.getPlayerStore().proceedWithAscensionCinematic(playerId);
        }
    }
}
