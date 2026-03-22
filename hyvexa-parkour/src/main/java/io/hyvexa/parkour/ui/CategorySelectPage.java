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
import io.hyvexa.common.ui.AccentOverlayUtils;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.util.ParkourUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CategorySelectPage extends BaseParkourPage {

    private final PlayerRef playerRef;
    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final RunTracker runTracker;
    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_SELECT_PREFIX = "Select:";

    public CategorySelectPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                     ProgressStore progressStore, RunTracker runTracker) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerRef = playerRef;
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.runTracker = runTracker;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_CategorySelect.ui");
        uiCommandBuilder.set("#BackButton.Text", "Close");
        buildCategoryList(uiCommandBuilder, uiEventBuilder);
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
        if (BUTTON_CLOSE.equals(data.getButton()) || BUTTON_BACK.equals(data.getButton())) {
            this.close();
            return;
        }
        if (!data.getButton().startsWith(BUTTON_SELECT_PREFIX)) {
            return;
        }
        String category = data.getButton().substring(BUTTON_SELECT_PREFIX.length());
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new MapSelectPage(playerRef, mapStore, progressStore, runTracker, category));
    }

    private void buildCategoryList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#CategoryCards");
        Set<String> categories = new LinkedHashSet<>();
        List<Map> maps = mapStore.listMaps();
        UUID playerId = playerRef.getUuid();
        java.util.Map<String, Integer> totalByCategory = new HashMap<>();
        java.util.Map<String, Integer> completedByCategory = new HashMap<>();
        for (Map map : maps) {
            String category = FormatUtils.normalizeCategory(map.getCategory());
            categories.add(category);
            totalByCategory.merge(category, 1, Integer::sum);
            if (progressStore.isMapCompleted(playerId, map.getId())) {
                completedByCategory.merge(category, 1, Integer::sum);
            }
        }
        List<String> orderedCategories = applyCategoryMapOrder(new ArrayList<>(categories), maps);
        int index = 0;
        for (String category : orderedCategories) {
            commandBuilder.append("#CategoryCards", "Pages/Parkour_CategoryEntry.ui");
            commandBuilder.set("#CategoryCards[" + index + "] #CategoryName.Text", category);
            int total = totalByCategory.getOrDefault(category, 0);
            int completed = completedByCategory.getOrDefault(category, 0);
            commandBuilder.set("#CategoryCards[" + index + "] #CategoryProgress.Text",
                    completed + "/" + total + " Completed");
            String accentColor = UIColorUtils.getCategoryAccentColor(category);
            AccentOverlayUtils.applyAccent(commandBuilder, "#CategoryCards[" + index + "] #AccentBar",
                    accentColor, AccentOverlayUtils.CATEGORY_ACCENTS);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#CategoryCards[" + index + "] #SelectButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + category), false);
            index++;
        }
    }

    private List<String> applyCategoryMapOrder(List<String> categories, List<Map> maps) {
        List<String> ordered = new ArrayList<>(categories);
        ordered.sort(Comparator.comparingInt((String category) -> ParkourUtils.getCategoryOrder(category, maps))
                .thenComparing(String.CASE_INSENSITIVE_ORDER));
        return ordered;
    }
}
