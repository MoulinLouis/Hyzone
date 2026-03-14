package io.hyvexa.common.ui;

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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public abstract class AbstractSearchablePaginatedPage extends InteractiveCustomUIPage<SearchPaginatedData> {

    protected static final String BUTTON_PREV = "PrevPage";
    protected static final String BUTTON_NEXT = "NextPage";

    private final PaginationState pagination;
    private String searchText = "";

    protected AbstractSearchablePaginatedPage(@Nonnull PlayerRef playerRef, int pageSize) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, SearchPaginatedData.CODEC);
        this.pagination = new PaginationState(pageSize);
    }

    // === Abstract methods ===

    protected abstract String getPagePath();

    protected abstract String getSearchFieldId();

    protected abstract void bindCustomEvents(UIEventBuilder eventBuilder);

    protected abstract void buildContent(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder);

    protected abstract void handleCustomButton(String button, Ref<EntityStore> ref, Store<EntityStore> store);

    // === Optional hook ===

    protected void onPageSetup(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
    }

    // === Final lifecycle methods ===

    @Override
    public final void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                            @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append(getPagePath());
        bindAllEvents(eventBuilder);
        onPageSetup(commandBuilder, eventBuilder);
        buildContent(commandBuilder, eventBuilder);
    }

    @Override
    public final void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                      @Nonnull SearchPaginatedData data) {
        super.handleDataEvent(ref, store, data);
        String previousSearch = searchText;
        if (data.getSearch() != null) {
            searchText = data.getSearch().trim();
        }
        if (data.getButton() == null) {
            if (!previousSearch.equals(searchText)) {
                pagination.reset();
                sendRefresh();
            }
            return;
        }
        if (BUTTON_PREV.equals(data.getButton())) {
            pagination.previous();
            sendRefresh();
            return;
        }
        if (BUTTON_NEXT.equals(data.getButton())) {
            pagination.next();
            sendRefresh();
            return;
        }
        handleCustomButton(data.getButton(), ref, store);
    }

    // === Protected methods ===

    protected void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindAllEvents(eventBuilder);
        buildContent(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    protected <T> List<T> filterBySearch(List<T> items, Function<T, String> nameExtractor) {
        String filter = searchText.trim().toLowerCase();
        if (filter.isEmpty()) {
            return items;
        }
        List<T> filtered = new ArrayList<>();
        for (T item : items) {
            String name = nameExtractor.apply(item);
            if (name != null && name.toLowerCase().startsWith(filter)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    protected void showEmpty(UICommandBuilder commandBuilder, String message) {
        commandBuilder.set("#EmptyText.Text", message);
        commandBuilder.set("#PageLabel.Text", "");
    }

    protected String getSearchText() {
        return searchText;
    }

    protected PaginationState getPagination() {
        return pagination;
    }

    protected void resetSearchAndPagination() {
        searchText = "";
        pagination.reset();
    }

    // === Private methods ===

    private void bindAllEvents(UIEventBuilder eventBuilder) {
        String searchId = getSearchFieldId();
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, searchId,
                EventData.of(SearchPaginatedData.KEY_SEARCH, searchId + ".Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PREV), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NEXT), false);
        bindCustomEvents(eventBuilder);
    }
}
