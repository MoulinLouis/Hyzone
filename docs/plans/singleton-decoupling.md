# Singleton Decoupling — Progressive Refactor Plan

## Status Update — 2026-03-22

This plan is now partially implemented.

### Phase Status At A Glance

| Phase | Status | Handoff note |
|-------|--------|--------------|
| Phase 1 — Ascend plugin decoupling | In progress | Multiple page/manager/helper/command consumers migrated; **30** `ParkourAscendPlugin.getInstance()` calls still remain in `hyvexa-parkour-ascend/src/main/java` across **21** files |
| Phase 2 — core interfaces | Partially completed | `CurrencyStore`, `PlayerAnalytics`, and `ConnectionProvider` exist; `AscendRunTracker` now consumes `PlayerAnalytics`, but broader adoption is still incomplete |
| Phase 3 — store `DatabaseManager` migration | Not started | No broad `ConnectionProvider` propagation yet |
| Phase 4 — other module singleton cleanup | Not started | Purge, RunOrFall, Hub, Parkour, and Wardrobe are untouched |
| Phase 5 — docs | Partially completed | Architecture/pattern docs updated for the completed slices; remaining docs cleanup is mostly progress/status refreshes as more stores move off singletons |

### Completed so far

- **Phase 1: partial**
  - `ParkourAscendPlugin.setup()` now wires a shared `RunnerSpeedCalculator`, injects explicit dependencies into `AscendRunTracker`, `RobotManager`, `SummitManager`, mine managers/helpers, tutorial scheduling, and command/page boundary classes, and closes the tracker/robot and HUD/robot ordering gaps with explicit setter injection instead of plugin lookups.
  - Added `AscendMenuNavigator` to keep profile/settings/music/stats/achievement page construction at the boundary instead of inside page classes.
  - Added `AscendAdminNavigator` to keep admin/whitelist/mine admin page construction and back-navigation at the boundary instead of inside admin pages.
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
  - Commands / interaction bootstrap points were updated to pass those dependencies in from the plugin boundary.
  - Mine admin sub-pages now carry the injected admin navigator through their back-navigation flow instead of reconstructing admin pages ad hoc.
  - Mine reward / achievement / HUD flows now receive `MineHudManager`, `MineAchievementTracker`, `MinePlayerStore`, `MineConfigStore`, and player lookup callbacks explicitly instead of reaching back into the plugin singleton at runtime.
  - Prestige reset flows now receive `RobotManager` and `AchievementManager` explicitly through page constructors instead of using a static helper fallback to the plugin singleton.
  - `ParkourAscendPlugin` getters used as service-locator accessors were marked `@Deprecated` to signal the transition.

- **Phase 2: partial propagation completed**
  - Added `CurrencyStore`
  - Added `PlayerAnalytics`
  - Added `ConnectionProvider`
  - `CurrencyBridge` now uses `CurrencyStore`
  - `DatabaseManager` now implements `ConnectionProvider`
  - `AnalyticsStore` now implements `PlayerAnalytics`
  - `AscendRunTracker` now depends on `PlayerAnalytics` instead of `AnalyticsStore.getInstance()`

- **Phase 5: partial**
  - `docs/CODE_PATTERNS.md`, `docs/ARCHITECTURE.md`, `docs/Ascend/README.md`, and `docs/Core/README.md` now document the composition-root / constructor-injection rule.

### Not completed yet

- **Phase 1 still in progress**
  - `ParkourAscendPlugin.getInstance()` calls remain in Ascend.
  - Current count after this slice: **30** remaining calls under `hyvexa-parkour-ascend/src/main/java` across **21** files.
  - The largest remaining service-locator consumers are now:
    - `AscendPlayerStore`
    - `AbstractAscendPageInteraction`
    - `MapUnlockHelper`
    - one-off command wrappers (`ElevateCommand`, `SummitCommand`, `SkillCommand`, `TranscendCommand`, `HudPreviewCommand`)
    - one-off interaction / helper classes (`AscendAscensionExplainerPage`, `AscendLeaveInteraction`, `AscendResetInteraction`, `MineEggChestInteraction`, `MineZoneAdminPage`, `EggDropHelper`, `EggOpenService`, `MineCommand`, `MineManager`, `RobotSpawner`, `RobotRefreshSystem`, `GhostRecorder`)

- **Phase 2 not fully propagated**
  - Consumers are not yet broadly typed against `CurrencyStore`, `PlayerAnalytics`, or `ConnectionProvider`.
  - The interfaces exist, but most store consumers still use concrete singleton-backed classes.

- **Phase 3 not started in earnest**
  - Direct `DatabaseManager.getInstance()` usage still exists across core/module stores.
  - No store-by-store `ConnectionProvider` migration has been completed yet.

- **Phase 4 not started**
  - No equivalent singleton cleanup has been applied yet to Purge, RunOrFall, Hub, Parkour, or Wardrobe.

### Recommended next tasks for another agent

1. Finish **Phase 1 in Ascend manager/store layer**:
   - Inject remaining hidden dependencies into `AscendPlayerStore`
   - Remove the `MapUnlockHelper` challenge lookup fallback by threading `ChallengeManager` explicitly
   - Convert one-off command wrappers and helper pages to constructor-injected command/page factories where practical

2. Reduce the **codec-instantiated interaction** singleton boundary:
   - Revisit `AbstractAscendPageInteraction`
   - If Hytale codec constraints still force no-arg handlers, isolate the remaining access behind a narrower injected/factory-style bridge instead of the full plugin singleton

3. Continue **Phase 2 propagation** on low-risk gameplay consumers:
   - Replace direct `AnalyticsStore.getInstance()` usage outside Ascend with `PlayerAnalytics`
   - Replace remaining concrete currency store injections with `CurrencyStore`

4. Start **Phase 3 store migration** with low-risk targets:
   - `RunOrFallStatsStore`
   - `VoteStore`
   - `MedalStore`
   - `MedalRewardStore`

5. After the next `ConnectionProvider` migrations, update this plan with:
   - exact classes completed
   - remaining call counts
   - any constructor cycles that require setter injection or small helper services

### Files changed in this slice

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

## Audit Summary

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
