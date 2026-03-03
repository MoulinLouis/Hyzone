package io.hyvexa.purge.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class PurgeGameOverPage extends InteractiveCustomUIPage<PurgeGameOverPage.PurgeGameOverData> {

    private final int wave;
    private final int kills;
    private final int scrap;
    private final int bestCombo;
    private final String reason;

    public PurgeGameOverPage(@Nonnull PlayerRef playerRef,
                             int wave, int kills, int scrap, int bestCombo, String reason) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeGameOverData.CODEC);
        this.wave = wave;
        this.kills = kills;
        this.scrap = scrap;
        this.bestCombo = bestCombo;
        this.reason = reason;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_GameOver.ui");

        uiCommandBuilder.set("#ReasonLabel.Text", reason != null ? reason : "");
        uiCommandBuilder.set("#WaveValue.Text", String.valueOf(wave));
        uiCommandBuilder.set("#KillsValue.Text", String.valueOf(kills));
        uiCommandBuilder.set("#ScrapValue.Text", String.valueOf(scrap));
        uiCommandBuilder.set("#ComboValue.Text", bestCombo > 0 ? "x" + bestCombo : "-");

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(PurgeGameOverData.KEY_CLOSE, "close"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PurgeGameOverData data) {
        super.handleDataEvent(ref, store, data);
        close();
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        super.onDismiss(ref, store);
    }

    public static class PurgeGameOverData {
        static final String KEY_CLOSE = "Close";

        public static final BuilderCodec<PurgeGameOverData> CODEC =
                BuilderCodec.<PurgeGameOverData>builder(PurgeGameOverData.class, PurgeGameOverData::new)
                        .addField(new KeyedCodec<>(KEY_CLOSE, Codec.STRING),
                                (data, value) -> data.close = value, data -> data.close)
                        .build();

        String close;
    }
}
