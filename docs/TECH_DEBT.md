# Tech Debt

Prioritized list of known issues and improvements. Sourced from a full codebase audit (2026-03-13, 466 files, ~94K LOC).

**What's done well:** Zero silent exception swallowing, proper try-with-resources everywhere, PreparedStatements only (no SQL injection), correct ConcurrentHashMap usage, universal null/ref safety checks, proper world-thread dispatch, correct AtomicReference for BigNumber values.

---

## Tier 1: Quick Wins (hours each)

### ~~1.1 Replace RunOrFall reflection bridge with direct calls~~ (DONE)
- **Module:** runorfall
- **Issue:** `RunOrFallBlinkInteraction` uses reflection to call `RestartCheckpointInteraction` from parkour. This is unnecessary fragility since interfaces in core could bridge the modules.
- **Fix:** Created `GameModeBridge` registry in core with `InteractionHandler` interface. `RunOrFallBlinkInteraction` now uses `GameModeBridge.invoke()` instead of reflection.

### ~~1.2 Extract CheckpointDetector from RunValidator + DuelTracker~~ (DONE)
- **Module:** parkour
- **Issue:** Checkpoint detection logic is duplicated between `RunValidator` and `DuelTracker`. Bug fixes in one don't propagate to the other.
- **Fix:** Extracted shared `CheckpointDetector` class with `CheckpointState` interface. Both `ActiveRun` and `DuelPlayerState` implement the interface.

### ~~1.3 Fix AnalyticsStore SQL (use GROUP BY)~~ (DONE)
- **Module:** core
- **Fixed:** `getTopJsonValues` now uses `JSON_EXTRACT` + `GROUP BY` / `COUNT(*)` / `ORDER BY DESC LIMIT` in SQL. `sumJsonLongField` now uses `SUM(JSON_EXTRACT(...))`. Removed 3 orphaned Java-side JSON helper methods.

### ~~1.4 Add DatabaseManager.withTransaction() helper~~ (DONE)
- **Module:** core
- **Fixed:** `withTransaction()` helper already existed from prior work. Migrated the last remaining manual transaction site in `AscendPlayerPersistence.doSyncSave()`.

### ~~1.5 Fix check-then-act race in PurgeSessionManager~~ (DONE)
- **Module:** purge
- **Fixed:** All 9 callers migrated from `hasActiveSession()` + `getSessionByPlayer()` to single `getSessionByPlayer()` with null check. Removed the now-unused `hasActiveSession()` method.

### ~~1.6 Fix volatile array element race in RobotState~~ (DONE)
- **Module:** ascend
- **Fixed:** Setter changed to `setPreviousPosition(double x, double y, double z)` which creates a new array internally, enforcing copy-on-write at the API level. Added `clearPreviousPosition()` for null resets.

---

## Tier 2: High Impact (days each)

### ~~2.1 Fix Purge circular dependencies (PurgeManagerRegistry)~~ (DONE)
- **Module:** purge
- **Issue:** 10 setter-injected fields across `PurgeSessionManager`, `PurgeWaveManager`, `PurgePartyManager`, `PurgeHudManager`. Volatile fields with no null guards — risk of NPE if setter not called before first tick. The core cycle: WaveManager and SessionManager each need a reference to the other.
- **Fix:** Create `PurgeManagerRegistry` with a builder that wires all cross-references atomically. All fields become final, no null checks needed, compile-time safety.

### ~~2.2 Centralize Purge DB schema (PurgeDatabaseSetup)~~ (DONE)
- **Module:** purge
- **Issue:** 14 tables scattered across 9 individual store/manager classes. No single view of full schema. Compare with Ascend's centralized `AscendDatabaseSetup` (the good pattern).
- **Fix:** Created `PurgeDatabaseSetup.java` modeled after `AscendDatabaseSetup`. Single source of truth for all 14 Purge tables and 5 migrations.

### ~~2.3 Create BasePlayerStore abstraction~~ (DONE)
- **Module:** core
- **Issue:** 3 simple player stores (`PurgePlayerStore`, `DuelStatsStore`, `RunOrFallStatsStore`) shared 70-80% identical code — cache lookup, DB fallback, upsert, evict.
- **Fix:** Extracted `BasePlayerStore<V>` abstract class in core with template methods (`loadSql()`, `upsertSql()`, `parseRow()`, `bindUpsertParams()`, `defaultValue()`). Migrated all 3 stores. `WeaponXpStore` and `PurgeScrapStore` excluded (nested key / dirty tracking patterns don't fit).

### ~~2.4 Externalize WardrobeBridge cosmetics to JSON~~ (DONE)
- **Module:** core/wardrobe
- **Issue:** Shop cosmetics were hardcoded in `WardrobeBridge.COSMETICS`. Adding/removing cosmetics required recompilation.
- **Fix:** Created `CosmeticConfigLoader` that loads cosmetic definitions from `mods/Parkour/cosmetics.json` at startup. Generates default file from the original 101 entries on first run.

### ~~2.5 Consolidate ad-hoc column migrations~~ (DONE)
- **Module:** core, parkour, runorfall
- **Issue:** 19 calls to `DatabaseManager.addColumnIfMissing()` scattered across `MapStore` (4), `MedalRewardStore` (2), `RunOrFallConfigStore` (13). No centralized migration registry or audit trail.
- **Fix:** Created `ParkourDatabaseSetup` and `RunOrFallDatabaseSetup` following the `PurgeDatabaseSetup` pattern. Each has a migrations tracking table and named migration methods. Removed all ad-hoc calls from individual stores.

### ~~2.6 Eliminate unnecessary reflection bridges~~ (DONE)
- **Module:** parkour, runorfall
- **Issue:** 5 interaction files in parkour use reflection to access RunOrFall classes (leaderboard, stats, fly toggle, join, feather bridge). `RunOrFallFeatherBridge` is the clearest — FeatherStore is already on the classpath via core.
- **Fix:** `RunOrFallFeatherBridge` → direct `FeatherStore.getInstance()` calls. For the other 4, registered `GameModeBridge` handlers in runorfall startup; parkour interactions now use `GameModeBridge.invoke()`.

### ~~2.7 Paginated search page base class~~ (DONE)
- **Module:** core (used by parkour, ascend)
- **Issue:** 7+ paginated pages repeat identical `PaginationState` lifecycle, prev/next handling, search filtering, page label rendering (~150 LOC boilerplate per page).
- **Pages:** `LeaderboardPage`, `MapLeaderboardPage`, `AdminPlayersPage`, `PlaytimeAdminPage`, `AscendLeaderboardPage`, `AscendMapLeaderboardPage`, `ChallengeLeaderboardPage`
- **Fix:** Extracted `AbstractSearchablePaginatedPage` base class in core with `SearchPaginatedData` shared event data. Template method pattern with 5 abstract methods. All 7 pages migrated.

---

## Tier 3: Major Refactors (weeks each)

### 3.1 Split RunOrFallGameManager
- **Module:** runorfall
- **Issue:** ~2,000 LOC, 10 concerns mixed: game state machine, player management, block breaking, platform query index, HUD updates, feather rewards, spectator management, configuration, blink system.
- **Extraction order:** `BlockBreakSystem` first (self-contained), then `PlatformGeometryIndex`, then `FeatherRewardSystem`.

### 3.2 Split RobotManager
- **Module:** ascend
- **Issue:** ~1,700 LOC, 11 concerns: robot lifecycle, tick loop, refresh, viewer context, map/ghost caching, auto-upgrade runners, auto-elevation, auto-summit, teleport warnings, visibility, orphan cleanup.
- **Extraction order:** `AutoRunnerUpgradeEngine` first (self-contained, ~280 LOC), then `RobotSpawner`, then `RobotRefreshSystem`.

### 3.3 Split PurgeWaveManager
- **Module:** purge
- **Issue:** ~1,640 LOC, 10 concerns including sun.misc.Unsafe reflection for zombie AI modification. Mixes wave lifecycle, NPC spawning, death detection, HUD updates, zombie aggro boosting, victory/defeat, zombie removal.
- **Extraction order:** `ZombieAggroBooster` + `UnsafeReflectionHelper` first (isolates dangerous code), then `WaveDeathTracker`, then `WaveProgressionController`.

### 3.4 Plan Unsafe migration
- **Module:** purge
- **Issue:** `PurgeWaveManager` uses `sun.misc.Unsafe` to modify final fields on Hytale NPC AI classes (sensor range, pathfinder distances, view cones, action delays, speed). 8 `ConcurrentHashMap<Class<?>, Long>` offset caches. No version detection or offset validation. Requires `--add-opens` JVM flag. Will break on JVM or Hytale updates.
- **Mitigation (short-term):** Add offset validation (read-back after write), log field names and offsets on startup, tie cache to Hytale version.
- **Fix (long-term):** Migrate to Hytale API alternatives when available.

### 3.5 Extract UI base classes
- **Module:** core (used by all UI modules)
- **Issue:** 84 page classes, 47 (56%) extend `InteractiveCustomUIPage` directly with no shared base. 6 duplicated patterns: pagination (9 pages), sendRefresh boilerplate (36 pages), button event binding (25 pages), auto-refresh scaffolding (4 pages), admin back navigation (20 pages), data field trimming (30+ pages).
- **Estimated savings:** ~2,000 LOC across all UI pages.
- **Proposed classes:** `AbstractPaginatedPage<D>`, `AutoRefreshingPage<D>`, `UIEventBinder` (fluent builder), per-module admin utils.

---

## Low Priority / Nice-to-Have

| Issue | Module | Notes |
|-------|--------|-------|
| Config load/save boilerplate (3 core classes) | core | `DatabaseConfig`, `TebexConfig`, `VoteConfig` share identical load/save. Works fine as-is. |
| Shared `ButtonEventData` classes (14+ pages) | all UI | Near-identical inner `static class XxxData extends ButtonEventData`. Create shared `SimpleButtonEventData`. |
| `PurgeVariantConfigManager` adjust method dedup | purge | 3 adjust methods follow same pattern. Only 3-4 methods. |
| `AscensionManager` thin wrapper methods (~15) | ascend | One-liner `hasXxx(UUID)` methods delegating to `hasSkillNode()`. Readability vs indirection tradeoff. |
| `DailyShopRotation` pass-through wrapper | core | Pure delegation to `DailyResetUtils`. Few callers. |
| `FallbackHttpServer` servlet duplication | votifier | Different HTTP APIs (Servlet vs HttpExchange) make sharing harder. |
| Auto-refresh page boilerplate (4 ascend pages) | ascend | `SummitPage`, `StatsPage`, `AutomationPage` + 1 more share identical AtomicBoolean refresh pattern. |
| Interaction handler boilerplate (19 files) | parkour, purge, runorfall | All follow identical pattern: get plugin, get store, get player, null check. Could extract shared context. |

---

## Concurrency Notes

Overall concurrency is **sound**: ConcurrentHashMap used correctly, volatile only for immutable refs/primitives, AtomicReference/Integer/Long for lock-free counters, ReadWriteLock for read-heavy config, zero deadlocks detected, no unbounded thread pools, proper CompletableFuture exception handling.

Bugs #1.5 and #1.6 above are the only concurrency issues found. The stale cache race in `CachedCurrencyStore` (extra DB query, not data corruption) is too low-severity to fix.

## Reflection Classification

| Category | Files | Action |
|----------|-------|--------|
| DANGEROUS (sun.misc.Unsafe) | 1 (`PurgeWaveManager`) | See item 3.4 |
| NECESSARY (cross-plugin bridges) | 6 (`HylogramsBridge`, `MultiHudBridge`, `EntityUtils`, `RunOrFallBlinkInteraction`, Votifier detection, JDBC) | Keep — graceful degradation on failure |
| ~~UNNECESSARY (parkour-to-runorfall)~~ | ~~5 interaction files~~ | ~~Eliminated in item 2.6~~ |

## Store Pattern Taxonomy

For reference when working on item 2.5:

| Pattern | Stores | Characteristics |
|---------|--------|----------------|
| CachedCurrencyStore | `VexaStore`, `FeatherStore` | 30-min TTL cache, async refresh |
| BasePlayerStore | `PurgePlayerStore`, `DuelStatsStore`, `RunOrFallStatsStore` | Extend `BasePlayerStore<V>` — cache + lazy load + upsert |
| Simple Player Store | `WeaponXpStore`, `PurgeScrapStore` | Cache + lazy load + upsert (not BasePlayerStore — nested key / dirty tracking) |
| Transaction Batch Store | `PurgeWaveConfigManager`, `PurgeClassStore`, `PurgeWeaponUpgradeStore`, `RunOrFallConfigStore` | Manual transactions, batch DELETE+INSERT |
| Complex Multi-Table Store | `AscendPlayerStore`, `ProgressStore`, `DiscordLinkStore` | Multiple tables, dirty tracking, leaderboard cache |
