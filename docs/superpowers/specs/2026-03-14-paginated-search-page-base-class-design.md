# Paginated Search Page Base Class (TECH_DEBT 2.7)

## Problem

7 paginated pages repeat identical `PaginationState` lifecycle, prev/next handling, search filtering, page label rendering, and event data classes. ~580 lines of duplicated boilerplate.

| Page | Module | Lines |
|------|--------|-------|
| `LeaderboardPage` | parkour | 192 |
| `MapLeaderboardPage` | parkour | 217 |
| `AdminPlayersPage` | parkour | 224 |
| `PlaytimeAdminPage` | parkour | 179 |
| `AscendLeaderboardPage` | ascend | 303 |
| `AscendMapLeaderboardPage` | ascend | 288 |
| `ChallengeLeaderboardPage` | ascend | 261 |

### Duplicated patterns (per page)

| Pattern | Lines | Description |
|---------|-------|-------------|
| Inner `*Data extends ButtonEventData` class | ~15 | Identical `button` + `search` fields, identical `BuilderCodec` |
| `PaginationState` + `searchText` fields | ~2 | Same declaration in every page |
| `handleDataEvent()` search/pagination logic | ~25 | Identical search change detection, prev/next dispatch |
| `sendRefresh()` template | ~6 | Identical create-builders, bind, build, sendUpdate |
| Prev/Next/Search event binding | ~6 | Same 3 bindings (only search field ID varies) |
| Search filter loop | ~10 | Same `toLowerCase().startsWith(filter)` pattern |
| Empty state handling | ~3 | Same `#EmptyText.Text` + `#PageLabel.Text` pattern |
| Button constants (`BUTTON_PREV`, `BUTTON_NEXT`) | ~2 | Identical strings |
| **Total** | **~72** | |

### Variation points (per-page differences)

| Concern | Examples |
|---------|----------|
| Page path | `"Pages/Parkour_Leaderboard.ui"`, `"Pages/Ascend_MapLeaderboard.ui"` |
| Search field element ID | `#LeaderboardSearchField`, `#PlayerSearchField`, `#PlaytimeSearchField`, `#SearchField` |
| Page size | 30, 40, 50 |
| Custom button handling | Back navigation, tab switching, player selection |
| Custom event bindings | Back button, tab buttons, per-row select buttons |
| Data source + row model | MedalStore, ProgressStore, AscendPlayerStore, etc. |
| Row rendering | Each page renders different columns into different card templates |
| One-time setup | Tab label initialization (3 Ascend pages) |

## Solution

### New files

#### `hyvexa-core/.../ui/SearchPaginatedData.java` (~25 lines)

Shared event data class replacing 7 identical inner classes.

```java
public class SearchPaginatedData extends ButtonEventData {
    public static final String KEY_SEARCH = "@Search";

    public static final BuilderCodec<SearchPaginatedData> CODEC =
        BuilderCodec.<SearchPaginatedData>builder(SearchPaginatedData.class, SearchPaginatedData::new)
            .addField(new KeyedCodec<>(ButtonEventData.KEY_BUTTON, Codec.STRING),
                    (data, value) -> data.button = value, data -> data.button)
            .addField(new KeyedCodec<>(KEY_SEARCH, Codec.STRING),
                    (data, value) -> data.search = value, data -> data.search)
            .build();

    private String button;
    private String search;

    @Override
    public String getButton() { return button; }
    public String getSearch() { return search; }
}
```

#### `hyvexa-core/.../ui/AbstractSearchablePaginatedPage.java` (~95 lines)

Base class using template method pattern. Extends `InteractiveCustomUIPage<SearchPaginatedData>`.

**Fields:**
- `private final PaginationState pagination` — constructed with subclass-provided page size
- `private String searchText = ""` — tracks current search input

**Abstract methods (5):**

| Method | Purpose |
|--------|---------|
| `String getPagePath()` | UI file path, e.g. `"Pages/Parkour_Leaderboard.ui"` |
| `String getSearchFieldId()` | Search field element ID, e.g. `"#LeaderboardSearchField"` |
| `void bindCustomEvents(UIEventBuilder)` | Bind page-specific events (back, tabs, per-row) |
| `void buildContent(UICommandBuilder, UIEventBuilder)` | Load data, filter, render rows |
| `void handleCustomButton(String, Ref<EntityStore>, Store<EntityStore>)` | Handle non-pagination buttons |

**Optional hook (1):**

| Method | Purpose | Default |
|--------|---------|---------|
| `void onPageSetup(UICommandBuilder, UIEventBuilder)` | One-time build setup (tab labels) | No-op |

**Final methods (lifecycle — subclasses cannot override):**

`build()`:
1. Appends `getPagePath()` to command builder
2. Calls `bindAllEvents()` (search + prev + next + custom)
3. Calls `onPageSetup()` (optional hook)
4. Calls `buildContent()`

`handleDataEvent()`:
1. Calls `super.handleDataEvent()`
2. Tracks search text changes — resets pagination and refreshes on change
3. Handles `PrevPage` / `NextPage` buttons
4. Delegates all other buttons to `handleCustomButton()`

**Protected methods (subclasses can call):**

| Method | Purpose |
|--------|---------|
| `sendRefresh()` | Re-bind all events (common + custom) and rebuild content via `buildContent()`, then send UI update. Subclasses call after tab switches, category changes, etc. |
| `<T> List<T> filterBySearch(List<T>, Function<T, String>)` | Filter items by search text prefix match. Returns full list if search is empty. |
| `void showEmpty(UICommandBuilder, String)` | Set `#EmptyText.Text` and clear `#PageLabel.Text` |
| `String getSearchText()` | Access current search text |
| `PaginationState getPagination()` | Access pagination state for `slice()`, `reset()` |
| `void resetSearchAndPagination()` | Clear search text and reset to page 0. For tab-switching pages that clear search on tab change (`AscendMapLeaderboardPage`, `ChallengeLeaderboardPage`). Pages that preserve search across tabs (`AscendLeaderboardPage`) should call `getPagination().reset()` directly. |

**Private method:**

`bindAllEvents(UIEventBuilder)`:
1. Binds `ValueChanged` on `getSearchFieldId()` with `KEY_SEARCH`
2. Binds `Activating` on `#PrevPageButton` with `BUTTON_PREV`
3. Binds `Activating` on `#NextPageButton` with `BUTTON_NEXT`
4. Calls `bindCustomEvents()` for page-specific bindings

**Constants:**
- `protected static final String BUTTON_PREV = "PrevPage"`
- `protected static final String BUTTON_NEXT = "NextPage"`

### Migration examples

#### LeaderboardPage (192 -> ~95 lines)

Removed: inner `LeaderboardData` class, `handleDataEvent()`, `sendRefresh()`, `bindEvents()`, pagination/search fields, button constants.

Retained: constructor, `getPagePath()`, `getSearchFieldId()`, `bindCustomEvents()` (back button only), `handleCustomButton()` (back navigation), `buildContent()` (data loading + rendering), inner `LeaderboardRow` class.

#### AscendMapLeaderboardPage (288 -> ~170 lines)

Same removals. Additionally uses:
- `onPageSetup()` for initial tab label setup
- `resetSearchAndPagination()` in `switchTab()`
- Tab-specific methods (`updateTabStyles`, `setupTabs`) remain as private helpers within the subclass

#### AdminPlayersPage (224 -> ~115 lines)

Same removals. `buildContent(commandBuilder, eventBuilder)` uses the eventBuilder for per-row `#SelectButton` dynamic bindings — this is why `buildContent` takes both builders.

### Design decisions

1. **`build()` and `handleDataEvent()` are final** — prevents subclasses from accidentally reimplementing the shared lifecycle. All page-specific behavior flows through the 5 abstract methods and 1 optional hook.

2. **`buildContent` takes both `UICommandBuilder` and `UIEventBuilder`** — `AdminPlayersPage` binds per-row select button events dynamically during content building. Other pages ignore the eventBuilder parameter. Note: `sendRefresh()` creates fresh builders and passes them through `bindAllEvents()` + `buildContent()`, so dynamic per-row bindings are correctly re-established on every refresh.

7. **`buildContent` owns all UI state** — subclasses set everything in `buildContent()`: row rendering, headers (e.g. `MapLeaderboardPage`'s `#MapTitle.Text`), tab styles (e.g. `updateTabStyles()`), search field values. Since `sendRefresh()` creates fresh builders, any state not re-set in `buildContent()` is lost. `onPageSetup()` is only for truly one-time setup that doesn't need refreshing (e.g. tab label text and visibility).

3. **Single shared `SearchPaginatedData`** — all 7 pages define structurally identical inner Data classes (same fields, same codec). A shared class eliminates ~105 lines and the risk of drift.

4. **`filterBySearch` uses `Function<T, String>`** — pages have different row types (records, inner classes, raw UUIDs). A function parameter extracts the searchable name from any type without requiring a shared interface.

5. **No generic type parameter on the base class** — the TECH_DEBT item suggested `AbstractSearchablePaginatedPage<D>` but since all pages use identical event data (button + search), a generic is unnecessary. Row types vary but they're local to each subclass's `buildContent`.

6. **`handleCustomButton` is void, not boolean** — the base class has already handled all known buttons (prev/next) before delegating. The subclass handles its own buttons unconditionally; there's no fallback behavior that needs a return value.

8. **`SearchPaginatedData.getSearch()` can return null** — the `search` field is only populated when a `ValueChanged` event fires on the search field. Button-only events leave it null. The base class `handleDataEvent()` guards with `if (data.getSearch() != null)` before updating `searchText`, matching the existing pattern in all 7 pages.

### Files changed

| File | Action | Before | After |
|------|--------|--------|-------|
| `hyvexa-core/.../ui/SearchPaginatedData.java` | NEW | — | ~25 |
| `hyvexa-core/.../ui/AbstractSearchablePaginatedPage.java` | NEW | — | ~95 |
| `hyvexa-parkour/.../ui/LeaderboardPage.java` | MIGRATE | 192 | ~95 |
| `hyvexa-parkour/.../ui/MapLeaderboardPage.java` | MIGRATE | 217 | ~110 |
| `hyvexa-parkour/.../ui/AdminPlayersPage.java` | MIGRATE | 224 | ~115 |
| `hyvexa-parkour/.../ui/PlaytimeAdminPage.java` | MIGRATE | 179 | ~90 |
| `hyvexa-parkour-ascend/.../ui/AscendLeaderboardPage.java` | MIGRATE | 303 | ~180 |
| `hyvexa-parkour-ascend/.../ui/AscendMapLeaderboardPage.java` | MIGRATE | 288 | ~170 |
| `hyvexa-parkour-ascend/.../ui/ChallengeLeaderboardPage.java` | MIGRATE | 261 | ~155 |

**Totals:** +120 new lines, -650 removed = **~530 lines eliminated**.

### Risk assessment

- **Low risk:** Pure refactor — no behavioral changes. Each page's public API (constructor, page behavior) is unchanged.
- **Migration is mechanical:** For each page, delete the Data class, remove handled methods, extend base class, implement 5 abstract methods.
- **Testing:** Cannot unit test (pages depend on Hytale API types). Verify by opening each page in-game: search, paginate, back/close, tab switch (Ascend pages).
