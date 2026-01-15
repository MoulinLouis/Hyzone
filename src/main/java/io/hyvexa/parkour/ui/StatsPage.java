package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

public class StatsPage extends BaseParkourPage {

    private static final String BUTTON_CLOSE = "Close";

    private final ProgressStore progressStore;
    private final MapStore mapStore;

    public StatsPage(@Nonnull PlayerRef playerRef, ProgressStore progressStore, MapStore mapStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.progressStore = progressStore;
        this.mapStore = mapStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_Stats.ui");
        uiCommandBuilder.set("#BackButton.Text", "Close");
        populateFields(ref, store, uiCommandBuilder);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
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
        }
    }

    private void populateFields(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }
        long xp = progressStore.getXp(playerRef.getUuid());
        String rankName = progressStore.getRankName(playerRef.getUuid(), mapStore);
        int completed = progressStore.getCompletedMapCount(playerRef.getUuid());
        int totalMaps = mapStore.listMaps().size();

        commandBuilder.set("#StatsPlayerName.Text", playerRef.getUsername());
        commandBuilder.set("#StatsRankValue.Text", rankName);
        commandBuilder.set("#StatsXpValue.Text", xp + " XP");
        commandBuilder.set("#StatsMapsValue.Text", completed + "/" + totalMaps);
    }

    private static String formatList(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "None";
        }
        ArrayList<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", sorted);
    }
}
