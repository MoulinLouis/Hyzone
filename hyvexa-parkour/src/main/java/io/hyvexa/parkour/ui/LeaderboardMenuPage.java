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
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.tracker.RunTracker;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LeaderboardMenuPage extends BaseParkourPage {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final RunTracker runTracker;
    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_GLOBAL = "Global";
    private static final String BUTTON_CATEGORY_PREFIX = "Category:";

    public LeaderboardMenuPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                      ProgressStore progressStore, RunTracker runTracker) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.runTracker = runTracker;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_LeaderboardMenu.ui");
        uiCommandBuilder.set("#BackButton.Text", "Close");
        buildMenu(uiCommandBuilder, uiEventBuilder);
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
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if (BUTTON_CLOSE.equals(data.getButton()) || BUTTON_BACK.equals(data.getButton())) {
            this.close();
            return;
        }
        if (BUTTON_GLOBAL.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store,
                    new LeaderboardPage(playerRef, mapStore, progressStore, runTracker));
            return;
        }
        if (data.getButton().startsWith(BUTTON_CATEGORY_PREFIX)) {
            String category = data.getButton().substring(BUTTON_CATEGORY_PREFIX.length());
            player.getPageManager().openCustomPage(ref, store,
                    new LeaderboardMapSelectPage(playerRef, mapStore, progressStore, runTracker, category));
        }
    }

    private void buildMenu(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#MenuCards");
        commandBuilder.append("#MenuCards", "Pages/Parkour_LeaderboardMenuEntry.ui");
        commandBuilder.set("#MenuCards[0] #EntryName.Text", "Global");
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#MenuCards[0] #SelectButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_GLOBAL), false);

        Set<String> categories = new LinkedHashSet<>();
        List<Map> maps = mapStore.listMaps();
        for (Map map : maps) {
            categories.add(FormatUtils.normalizeCategory(map.getCategory()));
        }
        List<String> orderedCategories = orderCategories(categories, maps);
        int index = 1;
        for (String category : orderedCategories) {
            commandBuilder.append("#MenuCards", "Pages/Parkour_LeaderboardMenuEntry.ui");
            commandBuilder.set("#MenuCards[" + index + "] #EntryName.Text", category);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#MenuCards[" + index + "] #SelectButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CATEGORY_PREFIX + category), false);
            index++;
        }
    }

    private List<String> orderCategories(Set<String> categories, List<Map> maps) {
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
