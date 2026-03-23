# Phase 4 Remaining — Parkour Page Navigator + Cleanup

Parent plan: [singleton-decoupling.md](singleton-decoupling.md)

## Status: **COMPLETED** (2026-03-23)

Implemented on branch `feat/singleton-phase4-remaining` in 5 commits:
1. `PlayerSettingsPage` — reads from `ParkourInteractionBridge` (ghostNpcManager, hudManager, VIP speed callbacks)
2. `ParkourAdminNavigator` — all admin pages receive deps via navigator, `AdminPageUtils` delegates to it
3. `WelcomeTutorialScreen2Page` — reads from `ParkourInteractionBridge`
4. `AnalyticsStore` — stored as field in `HyvexaPlugin`, replacing 6 getInstance() calls
5. `AnalyticsCommand` — receives `AnalyticsStore` via constructor injection

## Goal

Eliminate the remaining 16 `HyvexaPlugin.getInstance()` calls in parkour UI pages by creating a `ParkourMenuNavigator` / `ParkourAdminNavigator` (following the existing `AscendMenuNavigator` / `AscendAdminNavigator` pattern).

Also clean up remaining `AnalyticsStore.getInstance()` calls in `HyvexaPlugin` lifecycle code and `AnalyticsCommand`.

## Current State (post Phase 4a)

### HyvexaPlugin.getInstance() — 16 calls in 8 files

All remaining calls are in parkour UI pages that have complex self-navigation (pages that create other pages or recreate themselves after toggling settings):

| File | Calls | What it fetches |
|------|-------|----------------|
| `AdminIndexPage` | 8 | settingsStore, playerCountStore, medalRewardStore, globalMessageStore, broadcastAnnouncement |
| `PlayerSettingsPage` | 2 | progressStore, runTracker, mapStore, ghostNpcManager, hudManager, applyVipSpeedMultiplier |
| `ProgressAdminPage` | 2 | mapStore, invalidateRankCache |
| `AdminPageUtils` | 1 | creates AdminIndexPage with all deps |
| `GlobalMessageAdminPage` | 1 | refreshChatAnnouncements |
| `MapAdminPage` | 1 | progressStore, buildMapLeaderboardHologramLines |
| `WelcomeTutorialScreen2Page` | 1 | mapStore, progressStore, runTracker, medalStore |

### AnalyticsStore.getInstance() — 23 calls in 4 files

| File | Calls | Notes |
|------|-------|-------|
| `HyvexaPlugin` | 8 | Lifecycle: initialize, player_join/leave, timestamps, aggregation, shutdown. Uses non-interface methods (updatePlayerTimestamps, computeDailyAggregates, purgeOldEvents) |
| `AnalyticsCommand` | 10 | Admin tool: uses full AnalyticsStore API (getRecentStats, getRetention, countEvents, etc.) |
| `ParkourAscendPlugin` | 2 | Composition root: initialize + getInstance for injection |
| `HyvexaHubPlugin` | 2 | Composition root: initialize + getInstance for HubRouter |
| `HyvexaPurgePlugin` | 1 | Composition root: initialize |

## Approach

### Task 1 — ParkourAdminNavigator

Create `ParkourAdminNavigator` (same pattern as `AscendAdminNavigator`) to decouple admin pages:

```java
public class ParkourAdminNavigator {
    // Constructor receives all admin dependencies
    public ParkourAdminNavigator(MapStore mapStore, ProgressStore progressStore,
                                  SettingsStore settingsStore, PlayerCountStore playerCountStore,
                                  MedalRewardStore medalRewardStore, GlobalMessageStore globalMessageStore,
                                  Consumer<UUID> invalidateRankCache,
                                  Runnable refreshAnnouncements,
                                  BiConsumer<String, PlayerRef> broadcastAnnouncement,
                                  Function<String, List<String>> hologramLinesBuilder) { ... }

    // Factory methods for each admin page
    public AdminIndexPage createIndexPage(PlayerRef playerRef) { ... }
    public MapAdminPage createMapAdminPage(PlayerRef playerRef) { ... }
    public ProgressAdminPage createProgressAdminPage(PlayerRef playerRef) { ... }
    public GlobalMessageAdminPage createGlobalMessageAdminPage(PlayerRef playerRef) { ... }
    // ... etc
}
```

Wire from `HyvexaPlugin.setup()`. Each admin page receives the navigator instead of calling `HyvexaPlugin.getInstance()`. Back-navigation goes through the navigator's factory methods.

### Task 2 — PlayerSettingsPage decoupling

`PlayerSettingsPage` recreates itself many times for settings toggles. Two approaches:
- **Option A**: Pass all deps via constructor, have the page carry them through self-recreation
- **Option B**: Add PlayerSettingsPage deps to `ParkourInteractionBridge` since it's opened from a codec interaction (`PlayerSettingsInteraction`)

Option B is simpler. Add to `ParkourInteractionBridge.Services`: progressStore, runTracker, mapStore, ghostNpcManager, hudManager, and a VipSpeedApplier callback. PlayerSettingsPage reads from the bridge.

### Task 3 — WelcomeTutorialScreen2Page

Simple — add mapStore, progressStore, runTracker, medalStore to the constructor. Update callers (WelcomeTutorialScreen1Page, WelcomeTutorialScreen3Page) to pass them through.

### Task 4 — AnalyticsStore in HyvexaPlugin lifecycle

The lifecycle calls in `HyvexaPlugin` (player_join, player_leave, updatePlayerTimestamps) use non-interface methods. Options:
- Store the `AnalyticsStore` instance in a field (like `ParkourAscendPlugin` does with `analytics = AnalyticsStore.getInstance()`)
- This reduces the `getInstance()` calls from 8 to 2 (initialize + field assignment)

### Task 5 — AnalyticsCommand

Admin tool — acceptable to keep `AnalyticsStore.getInstance()` since it uses the full concrete API. Alternatively, inject via constructor from `HyvexaPlugin.setup()`.

## Other Remaining Cleanup

### Deprecated getters removal

After all Phase 4 work is done, remove `@Deprecated` getters from:
- `ParkourAscendPlugin` (already has deprecated getters from Phase 1)
- `HyvexaPlugin` (getters that are no longer needed externally)

### Backwards-compat no-arg constructors

All stores have `this(DatabaseManager.getInstance())` no-arg constructors for backwards compatibility. These can be removed once all composition roots pass `ConnectionProvider` explicitly. Low priority — they don't hurt and make testing easier.

### Minor singletons

| Singleton | Calls | Priority |
|-----------|-------|----------|
| `VoteManager.getInstance()` | ~3 | Low — only in composition roots |
| `CurrencyBridge` static methods | ~10 | Low — static utility, not a service locator |
| `PlayerSettingsPersistence.getInstance()` | ~4 | Low — could inject from composition root |
| `TrailManager.getInstance()` | ~4 | Low — in-memory manager |
| `ModelParticleTrailManager.getInstance()` | ~4 | Low — in-memory manager |
| `CosmeticManager.getInstance()` | ~4 | Low — in-memory manager |

## Execution Order

1. Task 2 (PlayerSettingsPage via bridge) — quick win, unblocks interaction decoupling
2. Task 1 (ParkourAdminNavigator) — largest, most value
3. Task 3 (WelcomeTutorial) — simple
4. Task 4 (AnalyticsStore lifecycle) — quick win
5. Task 5 (AnalyticsCommand) — optional

## Completion Criteria

- `HyvexaPlugin.getInstance()` only appears in `HyvexaPlugin` itself
- All admin pages receive dependencies from `ParkourAdminNavigator`
- `PlayerSettingsPage` reads from `ParkourInteractionBridge`
- Build passes, all modules compile
