# Singleton Decoupling — Progressive Refactor Plan

## Status Update — 2026-03-23

This plan is partially implemented. Phases 1–4 are complete. Phase 5 has remaining work.

### Phase Status At A Glance

| Phase | Status | Handoff note |
|-------|--------|--------------|
| Phase 1 — Ascend plugin decoupling | **Completed** | `ParkourAscendPlugin.getInstance()` → 0 external calls (76 classes migrated) |
| Phase 2 — Interface propagation | **Completed** | All gameplay consumers typed against `CurrencyStore`, `PlayerAnalytics`, `ConnectionProvider`. `.getInstance()` only in composition roots |
| Phase 3 — Store `DatabaseManager` migration | **Completed** | All stores use `ConnectionProvider` field. `DatabaseManager.getInstance()` only in no-arg constructors, composition roots, and setup classes |
| Phase 4 — Module plugin singleton cleanup | **Completed** | All 6 plugin singletons decoupled. Only 1 trivial call remains (`HubMenuInteraction`) |
| Phase 5 — Cleanup & docs | **Partial** | CODE_PATTERNS/ARCHITECTURE docs updated. Deprecated getters and no-arg constructors not yet removed |

### Completed so far

- **Phase 1: completed**
  - `ParkourAscendPlugin.setup()` now wires a shared `RunnerSpeedCalculator`, injects explicit dependencies into `AscendRunTracker`, `RobotManager`, `SummitManager`, mine managers/helpers, tutorial scheduling, and command/page boundary classes, and closes the tracker/robot and HUD/robot ordering gaps with explicit setter injection instead of plugin lookups.
  - Added `AscendMenuNavigator` to keep profile/settings/music/stats/achievement page construction at the boundary instead of inside page classes.
  - Added `AscendAdminNavigator` to keep admin/whitelist/mine admin page construction and back-navigation at the boundary instead of inside admin pages.
  - Added `AscendInteractionBridge` so codec-instantiated no-arg interactions can use a narrow static bootstrap without reaching back into `ParkourAscendPlugin`.
  - The following classes no longer call `ParkourAscendPlugin.getInstance()`:
    - `AscendMapSelectPage`
    - `AscendMapLeaderboardPage`
    - `AscendCommand`
    - `AscendAdminCommand`
    - `AscendAdminVoltPage`
    - `AscendAdminPage`
    - `AscendAdminPanelPage`
    - `AscendWhitelistPage`
    - `AscendHudManager`
    - `MinePage`
    - `MineBagPage`
    - `MineSellPage`
    - `MineAchievementsPage`
    - `MineAchievementTracker`
    - `MineHudManager`
    - `MineRewardHelper`
    - `MineAoEBreaker`
    - `MineDamageSystem`
    - `MineRobotManager`
    - `StatsPage`
    - `PassiveEarningsManager`
    - `BaseAscendPage`
    - `AscendProfilePage`
    - `AscendSettingsPage`
    - `AscensionPage`
    - `ElevationPage`
    - `SummitPage`
    - `AscendRunTracker`
    - `RobotManager`
    - `SummitManager`
    - `MineGateChecker`
    - `AutoRunnerUpgradeEngine`
    - `TutorialTriggerService`
    - `PrestigeHelper`
    - `AscendPlayerStore`
    - `MapUnlockHelper`
    - `ElevateCommand`
    - `SummitCommand`
    - `SkillCommand`
    - `TranscendCommand`
    - `HudPreviewCommand`
    - `CatCommand`
    - `MineCommand`
    - `AscendAscensionExplainerPage`
    - `MineManager`
    - `EggDropHelper`
    - `EggOpenService`
    - `GhostRecorder`
    - `MineZoneAdminPage`
    - `RobotSpawner`
    - `RobotRefreshSystem`
  - Commands / interaction bootstrap points were updated to pass those dependencies in from the plugin boundary.
  - Mine admin sub-pages now carry the injected admin navigator through their back-navigation flow instead of reconstructing admin pages ad hoc.
  - Mine reward / achievement / HUD flows now receive `MineHudManager`, `MineAchievementTracker`, `MinePlayerStore`, `MineConfigStore`, and player lookup callbacks explicitly instead of reaching back into the plugin singleton at runtime.
  - Prestige reset flows now receive `RobotManager` and `AchievementManager` explicitly through page constructors instead of using a static helper fallback to the plugin singleton.
  - Ghost capture, mine egg opening/drops, ascension explainer continuation, mine zone regeneration, and mine player teleport safety now all receive their runtime collaborators explicitly from the composition root or parent page instead of lazily querying `ParkourAscendPlugin`.
  - Codec-instantiated interaction handlers (`AbstractAscendPageInteraction`, reset/leave/mine egg handlers, and derived page-open interactions) now use `AscendInteractionBridge` instead of `ParkourAscendPlugin.getInstance()`.
  - `ParkourAscendPlugin` getters used as service-locator accessors were marked `@Deprecated` to signal the transition.

- **Phase 2: partial propagation completed**
  - Added `CurrencyStore`
  - Added `PlayerAnalytics`
  - Added `ConnectionProvider`
  - `CurrencyBridge` now uses `CurrencyStore`
  - `DatabaseManager` now implements `ConnectionProvider`
  - `AnalyticsStore` now implements `PlayerAnalytics`
  - `AscendRunTracker` now depends on `PlayerAnalytics` instead of `AnalyticsStore.getInstance()`

- **Phase 3: first `ConnectionProvider` migrations completed**
  - `ConnectionProvider` now provides transaction helper defaults so stores can use the interface without falling back to `DatabaseManager`.
  - `BasePlayerStore` now accepts an injected `ConnectionProvider` while keeping a backwards-compatible no-arg constructor for incremental adoption.
  - `RunOrFallStatsStore`, `VoteStore`, `MedalStore`, and `MedalRewardStore` now accept `ConnectionProvider` via constructor injection and no longer reach directly into `DatabaseManager` during normal runtime paths.
  - `HyvexaRunOrFallPlugin` now constructs `RunOrFallStatsStore` with an explicit `ConnectionProvider` at the composition root.

- **Phase 4: all plugin singletons decoupled**
  - `ParkourInteractionBridge` handles all 11 codec-instantiated parkour interactions
  - `ParkourAdminNavigator` handles all admin page creation and back-navigation
  - `AdminPageUtils` delegates to the navigator instead of `HyvexaPlugin.getInstance()`
  - `PlayerSettingsPage` reads from `ParkourInteractionBridge` (ghostNpcManager, hudManager, VIP speed)
  - `WelcomeTutorialScreen2Page` reads from `ParkourInteractionBridge`
  - `AnalyticsStore` stored as field in `HyvexaPlugin`, injected into `AnalyticsCommand`
  - `DuelTracker`, `RunValidator`, `ParkourCommand` receive all deps via constructor
  - Purge, RunOrFall, Wardrobe module plugins were decoupled in earlier commits
  - `HyvexaHubPlugin.getInstance()` has 1 remaining call in `HubMenuInteraction` (trivial)
  - Per-module plugin singleton status:
    - `ParkourAscendPlugin.getInstance()`: **0 external calls** (done)
    - `HyvexaPlugin.getInstance()`: **0 external calls** (done)
    - `HyvexaPurgePlugin.getInstance()`: **0 external calls** (done)
    - `HyvexaRunOrFallPlugin.getInstance()`: **0 external calls** (done)
    - `HyvexaHubPlugin.getInstance()`: **1 external call** (HubMenuInteraction — trivial)
    - `WardrobePlugin.getInstance()`: **0 external calls** (done)

- **Phase 5: partial**
  - `docs/CODE_PATTERNS.md`, `docs/ARCHITECTURE.md`, `docs/Ascend/README.md`, and `docs/Core/README.md` now document the composition-root / constructor-injection rule.

### Remaining work

#### Phase 5 — Final cleanup

- Remove `@Deprecated` getters from `ParkourAscendPlugin`
- Remove no-arg backwards-compat constructors from migrated stores
- Clean up minor singletons: `VoteManager.getInstance()` (8 calls, HyvexaPlugin only), `PlayerSettingsPersistence.getInstance()` (6 calls), `TrailManager.getInstance()` (9 calls), `ModelParticleTrailManager.getInstance()` (4 calls), `CosmeticManager.getInstance()` (7 calls), `WardrobeBridge.getInstance()` (10 calls), `CosmeticStore.getInstance()` (18 calls), `DiscordLinkStore.getInstance()` (16 calls)
- Document final patterns

### Recommended next steps

1. **Phase 5** — Clean up deprecated getters, no-arg constructors, minor singletons. Quick wins now that Phases 1–4 are complete.

### Files changed in this slice

- Latest store migration / parkour cleanup slice completed on 2026-03-23:
  - `hyvexa-core/src/main/java/io/hyvexa/core/db/ConnectionProvider.java`
  - `hyvexa-core/src/main/java/io/hyvexa/core/db/BasePlayerStore.java`
  - `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/manager/RunOrFallStatsStore.java`
  - `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/HyvexaRunOrFallPlugin.java`
  - `hyvexa-core/src/main/java/io/hyvexa/core/vote/VoteStore.java`
  - `hyvexa-core/src/main/java/io/hyvexa/core/vote/VoteManager.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/MedalStore.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/MedalRewardStore.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/command/ParkourCommand.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/interaction/MenuInteraction.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/interaction/LeaderboardInteraction.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunTracker.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunValidator.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/manager/LeaderboardHologramManager.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/CategorySelectPage.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/MapSelectPage.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/LeaderboardMenuPage.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/LeaderboardMapSelectPage.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/LeaderboardPage.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/MapLeaderboardPage.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/AdminIndexPage.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/AdminPageUtils.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/MedalRewardAdminPage.java`
  - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/WelcomeTutorialScreen2Page.java`

- Core abstractions:
  - `hyvexa-core/src/main/java/io/hyvexa/core/db/ConnectionProvider.java`
  - `hyvexa-core/src/main/java/io/hyvexa/core/economy/CurrencyStore.java`
  - `hyvexa-core/src/main/java/io/hyvexa/core/analytics/PlayerAnalytics.java`
- Ascend shared service:
  - `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RunnerSpeedCalculator.java`
- Main Ascend wiring / page refactors:
  - `ParkourAscendPlugin`
  - `AscendCommand`
  - `AscendMenuNavigator`
  - `BaseAscendPage`
  - `MineCommand`
  - `AscendMapSelectPage`
  - `AscendAdminVoltPage`
  - `AscendMapLeaderboardPage`
  - `AscendAdminPanelPage`
  - `AscendAchievementPage`
  - `AscendMusicPage`
  - `StatsPage`
  - `AscendProfilePage`
  - `AscendSettingsPage`
  - `PassiveEarningsManager`
  - `MinePage`
  - `MineBagPage`
  - `MineSellPage`
  - `MineAchievementsPage`
- Latest Ascend singleton-decoupling slice:
  - `ParkourAscendPlugin`
  - `AscendCommand`
  - `AscendAdminCommand`
  - `AscendAdminNavigator`
  - `AscensionPage`
  - `ElevationPage`
  - `SummitPage`
  - `AscendAdminPanelPage`
  - `AscendWhitelistPage`
  - `AscendRunTracker`
  - `RobotManager`
  - `MineAdminPage`
  - `MineZoneAdminPage`
  - `MineBlockPickerPage`
  - `MineBlockHpPage`
  - `MineGateAdminPage`
- Additional Ascend singleton-decoupling slices completed on 2026-03-22:
  - `SummitManager`
  - `PassiveEarningsManager`
  - `AscendMapLeaderboardPage`
  - `MineGateChecker`
  - `AutoRunnerUpgradeEngine`
  - `MineAchievementTracker`
  - `MineHudManager`
  - `MineRewardHelper`
  - `MineAoEBreaker`
  - `MineBreakSystem`
  - `MineDamageSystem`
  - `MineRobotManager`
  - `TutorialTriggerService`
  - `AscendAdminPage`
  - `AscendHudManager`
  - `PrestigeHelper`
  - `TranscendencePage`
  - `AscendChallengePage`
  - `ChallengeLeaderboardPage`
  - `AscendPlayerStore`
  - `MapUnlockHelper`
  - `ElevateCommand`
  - `SummitCommand`
  - `SkillCommand`
  - `TranscendCommand`
  - `HudPreviewCommand`
  - `CatCommand`
  - `MineCommand`
  - `AscendAscensionExplainerPage`
  - `MineManager`
  - `EggDropHelper`
  - `EggOpenService`
  - `GhostRecorder`
  - `MineAdminPage`
  - `MineZoneAdminPage`
  - `MineBlockPickerPage`
  - `MineBlockHpPage`
  - `MineGateAdminPage`
  - `AscendAdminNavigator`
  - `RobotSpawner`
  - `RobotRefreshSystem`
  - `AscendInteractionBridge`
  - `AbstractAscendPageInteraction`
  - `AscendDevInteraction`
  - `ConveyorChestInteraction`
  - `AscendTranscendenceInteraction`
  - `AscendLeaveInteraction`
  - `AscendResetInteraction`
  - `MineEggChestInteraction`

## Audit Summary

This section is the original baseline audit snapshot from before the completed slices above. Use the phase/status sections as the current source of truth; the raw counts/examples below are directionally useful but no longer exact after the 2026-03-22 and 2026-03-23 migrations.

### Inventory

| Category | Count | Key Examples |
|----------|-------|--------------|
| Plugin singletons | 6 | HyvexaPlugin, ParkourAscendPlugin, HyvexaHubPlugin, etc. |
| Core shared singletons | 16 | DatabaseManager, VexaStore, AnalyticsStore, CosmeticStore, etc. |
| Module-specific singletons | 10 | MedalStore, PurgePlayerStore, RunOrFallStatsStore, etc. |
| **Total** | **32** | |

### Coupling Heat Map

| Singleton | getInstance() calls | Files affected | Severity |
|-----------|---------------------|----------------|----------|
| ParkourAscendPlugin | **150** | 53 | Critical — acts as service locator for 20+ managers |
| DatabaseManager | **99** | ~60 | High — every store depends on it |
| AnalyticsStore | **39** | ~20 | Medium — fire-and-forget, low coupling risk |
| VexaStore | **32** | ~15 | Medium — has interface (CurrencyProvider) but rarely used |
| Other core stores | 3-11 each | 5-10 | Low |

### What's Actually Broken

1. **ParkourAscendPlugin is a god-object service locator.** 20+ getters, 150 calls. Any class can pull any manager at any time. No way to know a class's real dependencies without reading every line.

2. **Untestable.** Zero classes can be unit-tested without a running plugin instance. Even pure-logic classes like `MineBonusCalculator` or `AscendRunTracker` reach for `getInstance()` mid-execution.

3. **Hidden dependencies.** RobotManager takes 3 stores in its constructor (good), then calls `getInstance()` 12 more times for 5 additional managers during runtime (bad). The constructor signature lies about what it needs.

4. **No interfaces.** Only `CurrencyProvider` exists. Everything else is concrete class access — swapping implementations or mocking is impossible.

### What's Not Broken

- **Cross-module isolation is excellent.** Modules never call each other's `getInstance()`. All cross-module communication goes through hyvexa-core stores. This is a strong boundary worth preserving.
- **DatabaseManager as a singleton is fine.** A single connection pool shared globally is the correct design. The issue is direct `getConnection()` calls in business logic, not the singleton itself.
- **Fire-and-forget stores (AnalyticsStore, VoteStore) are low-risk.** They're write-only from consumers, so coupling is superficial.

---

## Strategy

**Principle: Constructor injection everywhere, no DI framework.**

No Guice, no Spring, no service locator registry. The plugin class already creates all managers — it just needs to pass them down instead of exposing them globally. This keeps the Hytale plugin lifecycle in control and avoids framework overhead.

**Priority order: highest coupling × highest testability payoff first.**

---

## Phase 1 — Break ParkourAscendPlugin as Service Locator

**Target:** Eliminate `ParkourAscendPlugin.getInstance().getXxxManager()` calls.
**Impact:** 150 calls across 53 files. Largest single source of coupling.
**Risk:** Low — mechanical refactor, no behavior change.

### Step 1.1 — Audit each consumer's real dependencies

For each of the 53 files calling `ParkourAscendPlugin.getInstance()`:
- List which getters it actually calls
- Determine if they can be constructor-injected

Expected: most classes need 1-3 managers, not 20.

### Step 1.2 — Constructor-inject managers into consumers

Starting with the highest-call files:

| File | Current calls | Inject via constructor |
|------|---------------|-----------------------|
| MinePage | 14 | mineConfigStore, minePlayerStore |
| RobotManager | 12 | Add ascensionManager, runTracker, challengeManager, summitManager to existing constructor |
| AscendAdminVoltPage | 12 | playerStore, settingsStore, mapStore |
| AscendMapSelectPage | 8 | mapStore, playerStore, settingsStore |
| AscendRunTracker | 8 | mapStore, playerStore, hudManager |

**Pattern:**
```java
// BEFORE
public class MinePage {
    public MinePage(PlayerRef playerRef, MineProgress progress) {
        this.configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
    }
}

// AFTER
public class MinePage {
    public MinePage(PlayerRef playerRef, MineProgress progress,
                    MineConfigStore configStore, MinePlayerStore playerStore) {
        this.configStore = configStore;
        this.playerStore = playerStore;
    }
}
```

The caller (typically a manager or another page) receives these from its own constructor. Chain propagates up to the plugin's `setup()` method, which is the composition root.

### Step 1.3 — Update plugin setup() as composition root

```java
// ParkourAscendPlugin.setup()
var mapStore = new AscendMapStore();
var playerStore = new AscendPlayerStore();
var configStore = new MineConfigStore();
// ...
var robotManager = new RobotManager(mapStore, playerStore, ghostStore,
                                     ascensionManager, runTracker, challengeManager, summitManager);
var minePage = new MinePage(playerRef, progress, configStore, minePlayerStore);
```

### Step 1.4 — Deprecate getters, keep getInstance() temporarily

Mark all `getXxxManager()` getters as `@Deprecated` with a comment pointing to constructor injection. Keep `getInstance()` alive for the transition period — it's still needed by event handlers that Hytale instantiates.

**Deliverable:** ParkourAscendPlugin.getInstance() calls drop from 150 to <20 (event handlers and Hytale callbacks only).

---

## Phase 2 — Extract Interfaces for Core Stores

**Target:** DatabaseManager, VexaStore, FeatherStore, AnalyticsStore, CosmeticStore.
**Impact:** Enables mocking in tests. Decouples modules from concrete implementations.
**Risk:** Low — additive change, no behavior modification.

### Step 2.1 — Extract interfaces

```java
// NEW: hyvexa-core/src/main/java/io/hyvexa/core/economy/CurrencyStore.java
public interface CurrencyStore {
    long getBalance(UUID playerId);
    void setBalance(UUID playerId, long amount);
    long add(UUID playerId, long amount);
    long remove(UUID playerId, long amount);
}

// VexaStore implements CurrencyStore
// FeatherStore implements CurrencyStore
```

Priority interfaces:
1. `CurrencyStore` — replaces and supersedes `CurrencyProvider` (which only has 2 methods)
2. `PlayerAnalytics` — extract from AnalyticsStore (logEvent, logPurchase — the write-side)
3. `ConnectionProvider` — extract from DatabaseManager (just `getConnection()`)

### Step 2.2 — Type consumers against interfaces

```java
// BEFORE
private final VexaStore vexaStore;

// AFTER
private final CurrencyStore vexaStore;
```

### Step 2.3 — Expand CurrencyBridge to use new interface

CurrencyBridge already exists with `CurrencyProvider`. Migrate it to use the richer `CurrencyStore` interface and route all currency access through it.

**Deliverable:** 3 core interfaces extracted. Consumers typed against interfaces. Test mocks possible.

---

## Phase 3 — Wrap DatabaseManager Access

**Target:** 99 direct `DatabaseManager.getInstance().getConnection()` calls.
**Impact:** Hides connection pool behind store boundaries. Stores own their SQL.
**Risk:** Medium — touches every store. Must be done store-by-store.

### Step 3.1 — Inject ConnectionProvider into stores

```java
// BEFORE (in every store)
try (var conn = DatabaseManager.getInstance().getConnection()) {

// AFTER
public class AscendPlayerStore {
    private final ConnectionProvider db;

    public AscendPlayerStore(ConnectionProvider db) {
        this.db = db;
    }

    public void load(UUID playerId) {
        try (var conn = db.getConnection()) {
```

### Step 3.2 — Migrate one store at a time

Order by risk (lowest usage stores first):
1. RunOrFallStatsStore (isolated, simple)
2. VoteStore (3 calls)
3. MedalStore, MedalRewardStore
4. AscendPlayerStore
5. VexaStore, FeatherStore
6. PurgePlayerStore and related purge stores
7. CosmeticStore, DiscordLinkStore
8. AnalyticsStore (largest surface, last)

### Step 3.3 — Remove DatabaseManager.getInstance() from non-store code

After all stores are migrated, `DatabaseManager.getInstance()` should only be called in the composition root (plugin setup methods). Any remaining direct calls in managers or pages indicate leaked persistence logic — extract to the appropriate store.

**Deliverable:** DatabaseManager.getInstance() calls drop from 99 to 6 (one per plugin setup).

---

## Phase 4 — Repeat for Other Plugins

Apply the same pattern to HyvexaPlugin (parkour), HyvexaPurgePlugin, etc. These are smaller — HyvexaPlugin has fewer external `getInstance()` calls because parkour managers are mostly self-contained.

**Order:**
1. HyvexaPurgePlugin (smallest, ~8 managers)
2. HyvexaRunOrFallPlugin (3 stores)
3. HyvexaHubPlugin (minimal managers)
4. HyvexaPlugin/parkour (largest but least externally coupled)
5. WardrobePlugin (mostly delegates to core)

---

## Phase 5 — Final Cleanup

### Step 5.1 — Remove @Deprecated getters

Once all consumers use constructor injection, remove the deprecated getters from plugin classes.

### Step 5.2 — Make getInstance() package-private where possible

Plugin `getInstance()` is still needed for Hytale event handler registration. But core stores that no longer need global access can have their `getInstance()` removed entirely — they're injected everywhere.

### Step 5.3 — Document the composition root pattern

Update `docs/CODE_PATTERNS.md` with:
- "New managers receive dependencies via constructor"
- "Plugin.setup() is the composition root — all wiring happens there"
- "Never call getInstance() from business logic"

---

## Execution Guidelines

### Batch size
Do 5-10 files per commit. Each commit should compile and run. Never refactor more than one module at a time.

### Testing strategy
After Phase 2 (interfaces), write unit tests for the first few migrated classes to prove the pattern works. Focus on classes with actual logic worth testing (calculators, trackers), not thin wrappers.

### What NOT to refactor
- **Hytale callbacks and event handlers** — These are instantiated by the framework, not by us. They may still need `getInstance()` to bootstrap. That's acceptable.
- **Fire-and-forget singletons** (AnalyticsStore for logging) — Low priority. The coupling is superficial since consumers only call `logEvent()` and don't depend on return values.
- **DatabaseManager as a singleton** — The connection pool should remain a singleton. We're only wrapping access behind an interface, not eliminating the singleton.

### Rollback plan
Each phase is independently valuable. If Phase 1 is done but Phase 2 never happens, the codebase is still better. No phase depends on a future phase being completed.
