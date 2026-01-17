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
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;
import io.hyvexa.parkour.tracker.RunTracker;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class CategorySelectPage extends BaseParkourPage {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final RunTracker runTracker;
    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_SELECT_PREFIX = "Select:";

    public CategorySelectPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                     ProgressStore progressStore, RunTracker runTracker) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
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
        for (Map map : maps) {
            String category = FormatUtils.normalizeCategory(map.getCategory());
            categories.add(category);
        }
        List<String> orderedCategories = applyCategoryOrder(categories);
        orderedCategories = applyCategoryMapOrder(orderedCategories, maps);
        int index = 0;
        for (String category : orderedCategories) {
            commandBuilder.append("#CategoryCards", "Pages/Parkour_CategoryEntry.ui");
            commandBuilder.set("#CategoryCards[" + index + "] #CategoryName.Text", category);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#CategoryCards[" + index + "]",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + category), false);
            index++;
        }
    }

    private List<String> applyCategoryOrder(Set<String> categories) {
        List<String> ordered = new LinkedList<>();
        SettingsStore settingsStore = HyvexaPlugin.getInstance() != null
                ? HyvexaPlugin.getInstance().getSettingsStore()
                : null;
        if (settingsStore != null) {
            for (String entry : settingsStore.getCategoryOrder()) {
                String normalized = FormatUtils.normalizeCategory(entry);
                if (categories.remove(normalized)) {
                    ordered.add(normalized);
                }
            }
        }
        ordered.addAll(categories);
        return ordered;
    }

    private List<String> applyCategoryMapOrder(List<String> categories, List<Map> maps) {
        List<String> ordered = new ArrayList<>(categories);
        ordered.sort(Comparator.comparingInt((String category) -> getCategoryOrder(category, maps))
                .thenComparing(String.CASE_INSENSITIVE_ORDER));
        return ordered;
    }

    private int getCategoryOrder(String category, List<Map> maps) {
        int minOrder = Integer.MAX_VALUE;
        for (Map map : maps) {
            String mapCategory = FormatUtils.normalizeCategory(map.getCategory());
            if (mapCategory.equalsIgnoreCase(category)) {
                minOrder = Math.min(minOrder, map.getOrder());
            }
        }
        return minOrder == Integer.MAX_VALUE ? Integer.MAX_VALUE : minOrder;
    }
}
