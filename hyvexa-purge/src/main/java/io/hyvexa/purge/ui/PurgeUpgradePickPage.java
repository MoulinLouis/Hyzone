package io.hyvexa.purge.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeUpgradeOffer;
import io.hyvexa.purge.data.PurgeUpgradeRarity;
import io.hyvexa.purge.data.PurgeUpgradeState;
import io.hyvexa.purge.data.PurgeUpgradeType;
import io.hyvexa.purge.manager.PurgeUpgradeManager;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class PurgeUpgradePickPage extends InteractiveCustomUIPage<PurgeUpgradePickPage.PurgeUpgradePickData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String CHOICE_SKIP = "skip";

    private final PurgeSession session;
    private final UUID playerId;
    private final PurgeUpgradeManager upgradeManager;
    private final List<PurgeUpgradeOffer> offered;
    private final Runnable onComplete;
    private final AtomicBoolean alreadyChosen = new AtomicBoolean(false);

    public PurgeUpgradePickPage(@Nonnull PlayerRef playerRef,
                                UUID playerId,
                                PurgeSession session,
                                PurgeUpgradeManager upgradeManager,
                                List<PurgeUpgradeOffer> offered,
                                Runnable onComplete) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeUpgradePickData.CODEC);
        this.playerId = playerId;
        this.session = session;
        this.upgradeManager = upgradeManager;
        this.offered = offered;
        this.onComplete = onComplete;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_UpgradePick.ui");

        // Set wave label
        uiCommandBuilder.set("#WaveLabel.Text", "Wave " + session.getCurrentWave() + " Complete");

        // Bind skip button
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SkipButton",
                EventData.of(PurgeUpgradePickData.KEY_CHOICE, CHOICE_SKIP), false);

        // Set up each upgrade card
        PurgeUpgradeState upgradeState = session.getUpgradeState(playerId);
        for (int i = 0; i < offered.size() && i < 3; i++) {
            PurgeUpgradeOffer offer = offered.get(i);
            int cardNum = i + 1;

            // Name and description
            uiCommandBuilder.set("#Card" + cardNum + "Name.Text", offer.type().getDisplayName());
            uiCommandBuilder.set("#Card" + cardNum + "Desc.Text", "+" + offer.value() + " " + offer.type().getUnit());

            // Rarity label
            uiCommandBuilder.set("#Card" + cardNum + "Rarity.Text", offer.rarity().getDisplayName());
            uiCommandBuilder.set("#Card" + cardNum + "Rarity.Style.TextColor", offer.rarity().getColor());

            // Accent bar color based on rarity
            applyCardAccent(uiCommandBuilder, cardNum, offer.rarity());

            // Show accumulated total if player already has this type
            if (upgradeState != null) {
                int existing = upgradeState.getAccumulated(offer.type());
                if (existing > 0) {
                    uiCommandBuilder.set("#Card" + cardNum + "Stacks.Text",
                            "Current: +" + existing + " " + offer.type().getUnit());
                }
            }

            // Encode type:rarity in choice string
            String choiceKey = offer.type().name() + ":" + offer.rarity().name();
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Card" + cardNum + "Button",
                    EventData.of(PurgeUpgradePickData.KEY_CHOICE, choiceKey), false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PurgeUpgradePickData data) {
        super.handleDataEvent(ref, store, data);
        if (data.choice == null || !alreadyChosen.compareAndSet(false, true)) {
            return;
        }

        if (!CHOICE_SKIP.equals(data.choice)) {
            try {
                String[] parts = data.choice.split(":", 2);
                if (parts.length == 2) {
                    PurgeUpgradeType type = PurgeUpgradeType.valueOf(parts[0]);
                    PurgeUpgradeRarity rarity = PurgeUpgradeRarity.valueOf(parts[1]);
                    PurgeUpgradeOffer offer = new PurgeUpgradeOffer(type, rarity);
                    Player player = store.getComponent(ref, Player.getComponentType());
                    upgradeManager.applyUpgrade(session, playerId, offer, ref, store, player);
                } else {
                    LOGGER.atWarning().log("Invalid upgrade choice format: " + data.choice);
                }
            } catch (IllegalArgumentException e) {
                LOGGER.atWarning().log("Invalid upgrade choice: " + data.choice);
            }
        }

        close();
        onComplete.run();
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (alreadyChosen.compareAndSet(false, true)) {
            onComplete.run();
        }
        super.onDismiss(ref, store);
    }

    private static void applyCardAccent(UICommandBuilder uiCommandBuilder, int cardNum, PurgeUpgradeRarity rarity) {
        String[] variants = {"Gray", "Green", "Blue", "Violet", "Gold"};
        for (String variant : variants) {
            uiCommandBuilder.set("#Card" + cardNum + "Accent" + variant + ".Visible", false);
        }
        String selected = switch (rarity) {
            case COMMON -> "Gray";
            case UNCOMMON -> "Green";
            case RARE -> "Blue";
            case EPIC -> "Violet";
            case LEGENDARY -> "Gold";
        };
        uiCommandBuilder.set("#Card" + cardNum + "Accent" + selected + ".Visible", true);
    }

    public static class PurgeUpgradePickData {
        static final String KEY_CHOICE = "Choice";

        public static final BuilderCodec<PurgeUpgradePickData> CODEC =
                BuilderCodec.<PurgeUpgradePickData>builder(PurgeUpgradePickData.class, PurgeUpgradePickData::new)
                        .addField(new KeyedCodec<>(KEY_CHOICE, Codec.STRING),
                                (data, value) -> data.choice = value, data -> data.choice)
                        .build();

        String choice;
    }
}
