# Optimization Prompts - Hyvexa Codebase

Concise prompts to send one at a time. Each one is a self-contained optimization task.

---

## Critical - Correctness & Safety

### 1. Cache Eviction Audit
> Audit all ConcurrentHashMap per-player caches across the codebase. Many stores (CosmeticStore, DiscordLinkStore, PurgeSkinStore, VoteStore) have no `evictPlayer()` method and grow unbounded. Add eviction to every store missing it, and verify all disconnect handlers call eviction for every relevant store. Check RunOrFall module specifically — it's missing several evictions compared to Purge.

### 2. Thread Safety Audit - ProgressStore & RunTracker
> ProgressStore (1231 lines) uses 5 different concurrency primitives (ReadWriteLock, AtomicBoolean, AtomicReference, AtomicLong, ConcurrentHashMap) with unclear synchronization boundaries. RunTracker has player state fragmented across 4 separate ConcurrentHashMaps where state transitions can race. Audit both classes for race conditions and fix the synchronization strategy.

### 3. Thread Safety Audit - VoteManager & RobotManager
> VoteManager: `ioExecutor`/`rewardExecutor` are volatile but read unsynchronized in `checkAndRewardAsync()` while `initialize()` replaces them under `synchronized`. RobotManager: 8+ volatile timestamp fields should use AtomicLong. AbstractTrailManager has a static `tickTask` shared across all instances — one manager stopping can cancel ticks for others. Fix all three.

### 4. RunOrFallGameManager Synchronization Fix
> RunOrFallGameManager (1999 lines) uses `synchronized` methods on top of ConcurrentHashMap fields + volatile fields. The synchronized keyword is redundant when all fields are concurrent, and volatile is redundant under synchronized. Pick one strategy and apply it consistently. Also audit compound check-then-act operations across multiple maps.

---

## High Impact - God Class Splits

### 5. Split HyvexaPlugin (Parkour Main)
> HyvexaPlugin.java (1332 lines) is a god object managing 20+ managers, 8 ScheduledFuture fields, event registration, database setup, and store initialization. Extract: `ManagerRegistry` (manager lifecycle), `SystemRegistrar` (event system registration), `ScheduledTaskManager` (scheduled futures). Apply the PurgeManagerRegistry pattern that already exists in the purge module.

### 6. Split ParkourAscendPlugin
> ParkourAscendPlugin.java (1057 lines) has 50+ injected dependencies. Same treatment as HyvexaPlugin — extract ManagerRegistry, SystemRegistrar, ScheduledTaskManager. Both main plugin classes should follow the same pattern.

### 7. Split RunOrFallGameManager
> RunOrFallGameManager.java (1999 lines) handles game logic, block management, player state, HUD updates, and scoring all in one class. Split into: `GameStateManager` (state machine + transitions), `BlockManager` (block spawning/breaking), `PlayerStateTracker` (lobby/game player tracking), `ScoreManager` (scoring + HUD sync).

### 8. Split ProgressStore
> ProgressStore.java (1231 lines) manages player progress caching, leaderboard caching, save debouncing, and dirty tracking with 8 concurrent collections. Split into: `PlayerProgressCache` (in-memory progress), `LeaderboardCache` (leaderboard queries + versioning), `ProgressPersistence` (save/load/debounce logic).

### 9. Split RunTracker & DuelTracker
> RunTracker (1089 lines, 78 methods, 12 ConcurrentHashMaps) and DuelTracker (1083 lines) share similar structures. Extract shared `ActiveSessionTracker<T>` base with common player tracking, idle detection, and cleanup. Split RunTracker's `checkPlayer()` (130 lines) into sub-checkers.

### 10. Split AnalyticsStore
> AnalyticsStore.java (527 lines, 20+ public methods) mixes event logging, daily aggregation, retention analytics, and data purging. Split into: `EventLogger`, `DailyAnalyticsAggregator`, `RetentionAnalyzer`.

### 11. Split RobotManager & MineRobotManager
> RobotManager (1685 lines) and MineRobotManager (999 lines) duplicate spawn/despawn/visibility/orphan-cleanup patterns. Extract `NPCLifecycleManager` base class with shared entity lifecycle, then have both extend it with domain-specific logic (runner movement vs mining behavior).

### 12. ~~Split AscendPlayerProgress~~ ✅
> Done. Split into 4 focused state classes: `EconomyState` (volt, elevation, summit XP), `GameplayState` (map progress, ascension, challenges, transcendence), `AutomationConfig` (auto-upgrade/elevation/summit settings), `SessionState` (passive earnings, UI settings). AscendPlayerProgress reduced from 774 to 14 lines, now a thin composition class with `economy()`, `gameplay()`, `automation()`, `session()` accessors. All 24 consumers migrated.

### 13. Merge/Refactor AscendPlayerStore + AscendPlayerPersistence
> AscendPlayerStore (1590 lines) and AscendPlayerPersistence (1281 lines) are 2871 lines total for one concept (player data). The boundary between them is unclear. Either merge into one clean class with internal separation, or establish a clear contract: Store = cache/API surface, Persistence = SQL only.

---

## Medium Impact - Pattern Extraction

### 14. Extract JDBC Template Pattern
> 47 occurrences of `try (Connection conn = ... PreparedStatement stmt = ...)` across all Store classes. Create `DatabaseManager.query(sql, mapper)` and `DatabaseManager.update(sql, binder)` template methods. This cuts ~50% of database boilerplate across the entire codebase.

### 15. Extract HudLifecycleManager to Core
> Three separate HUD lifecycle implementations exist: HyvexaHubPlugin (HudLifecycle record + tickHubHudRecovery), HyvexaRunOrFallPlugin (separate state tracking maps), PurgeHudManager. Extract `HudLifecycleManager<T>` to hyvexa-core with generic attach/detach/refresh/recovery logic. ~150 lines of duplication eliminated.

### 16. Centralize Player Cleanup
> Every module implements nearly identical player cleanup on disconnect with different eviction orders and missing stores. Create `GlobalPlayerCleanupRegistry` in hyvexa-core that modules register their eviction callbacks into. Single disconnect handler iterates them all. Fix RunOrFall's incomplete evictions.

### 17. Extract ManagerRegistry to Core
> PurgeManagerRegistry (76 lines) is a good pattern that only Purge uses. Extract to hyvexa-core as `ManagerRegistry` with lifecycle methods (init, shutdown, evict). Apply to all modules to replace scattered manager fields in plugin classes.

### 18. Extract Reflection Bridge Base
> HylogramsBridge (395 lines) and MultiHudBridge (201 lines) both use reflection + METHOD_CACHE with identical patterns. Extract `ReflectionBridge` base class with `invokeStatic()`, cached method lookup, and init logic.

### 19. Extract AbstractStore Base
> 9 Store classes repeat: `initialize()` (create table), per-player `load()`/`loadAndCache()`, `save()`/`update()` with async fallback, `evict()`. Create `AbstractStore<K, V>` template. Reduces ~200 lines of duplication.

### 20. Consolidate Build Configuration
> JAR signing exclusions, duplicatesStrategy, and `updatePluginManifest` task are copy-pasted identically across 7 module build.gradle files. Extract into a shared Gradle plugin or `buildSrc` convention plugin.

---

## Performance

### 21. Batch Analytics Events
> AnalyticsStore does individual INSERTs per event. Collect events in a buffer and batch-insert (100+ per round-trip). Reduces DB round-trips ~99% for analytics-heavy loads.

### 22. Add Background Cache Eviction
> CachedCurrencyStore has 30-min TTL but no background cleaner — stale entries stay in cache until next access. Add a ScheduledExecutor task to evict entries past TTL. Apply same pattern to all TTL-based caches.

### 23. Move Hardcoded JSON to Resources
> CosmeticConfigLoader.generateDefault() has ~130 lines of hardcoded JSON string concatenation. Move to `cosmetics-default.json` resource file loaded at startup.

---

## Code Quality

### 24. Message Constants Extraction
> 240 occurrences of `.sendMessage(Message.raw(` with hardcoded strings across 15 command files in parkour module. Extract to `Messages` constants class per module. Also standardize color usage.

### 25. Command Handler Cleanup
> 24 command files in parkour with inconsistent patterns: some use `CommandUtils.findPlayerByName()`, others use `ctx.sender()` directly. OP validation is duplicated everywhere. Standardize player resolution and extract OP check to decorator or base class.

### 26. Null Contract Documentation
> 140+ null/validity checks in parkour module alone (68 in MapAdminPage). Add `@Nullable`/`@Nonnull` annotations to document nullability contracts on public APIs. Remove redundant defensive checks where contracts guarantee non-null.

### 27. Map.java Value Objects
> Map.java (288 lines, 32 fields) has fragmented data: 6 transform locations, 6 fly zone coords, 4 medal times, 5 ability flags. Consolidate into value objects: `BoundingBox` for fly zones, `EnumMap<Medal, Long>` for medal times, `AbilitySet` for flags.

### 28. Logging Consistency
> 82% of parkour module files have zero logging. Critical paths (ProgressStore, RunTracker) log nothing on failure. Add structured logging to all Store save/load methods and Tracker state transitions. Standardize LOGGER field naming.

---

## Database

### 29. Centralize Migrations
> Migrations are scattered: VexaStore has `migratePlayerGemsToVexa()`, DiscordLinkStore has `migrateGemsRewardedToVexa()`, AscendDatabaseSetup has 5+ migration methods inline. Create a `MigrationRunner` with ordered, idempotent migration entries that run at startup.

### 30. Modularize AscendDatabaseSetup
> AscendDatabaseSetup.java (1669 lines) handles ALL Ascend table creation in one monolithic file. Split by subsystem: `AscendSchemaSetup`, `MineSchemaSetup`, `ChallengeSchemaSetup`, each responsible for their own tables.

### 31. Replace LIKE JSON Queries
> AnalyticsStore uses `LIKE '%"is_new":true%'` for JSON searching — fragile, depends on serialization format. Replace with `JSON_EXTRACT()` and proper type coercion.

---

## Documentation

### 32. ~~Fix Critical Doc Errors~~ (DONE — 2026-03-22 documentation rework)

### 33. Document Threading Model
> No documentation exists for the threading model. Document: which executors exist, what runs on world thread vs IO thread vs scheduled executor, which operations require `world.execute()`, and synchronization contracts for each Manager/Store.

---

## Testing

### 34. Add Pure Logic Unit Tests
> Only 16 test files exist for 511 Java files. Identify all pure-logic classes with zero Hytale imports and add tests. Priority targets: economy calculations, leaderboard sorting, BigNumber operations, format utilities, pagination logic, challenge condition evaluation, medal time calculations.

### 35. Discord Bot Tests
> discord-bot has zero tests, no devDependencies, no test script. Add Jest, mock mysql2 connections, test: code validation, link creation, rank sync batching, error handling paths.

---

## Ascend-Specific

### 36. AscendConstants Organization
> AscendConstants.java (958 lines) mixes Parkour, Mine, Summit, and Challenge constants with no clear organization. Split into domain-specific constant classes or at minimum add clear section headers and document formulas.

### 37. Consolidate Ascend Collection Groups
> RobotManager has 10 separate collections, AscendPlayerPersistence has 8+. Create wrapper objects: `RobotVisibilityState`, `PlayerTrackingState`, `LeaderboardCacheState` to group related collections and ensure consistent updates.

---

## Purge-Specific

### 38. Split PurgeWaveManager
> PurgeWaveManager (870 lines) handles wave spawning, NPC management, entity manipulation, and difficulty scaling. Split into: `WaveSpawner` (entity creation), `WaveDifficultyCalculator` (scaling logic), `WaveStateTracker` (active wave state).

### 39. Split PurgeSessionManager
> PurgeSessionManager (771 lines) mixes session lifecycle, state transitions, player scoring, and HUD sync. Split into: `SessionLifecycle` (start/stop/transitions), `SessionScorer` (scoring logic), `SessionStateSync` (HUD/client sync).

---

## UI

### 40. Extract Shared UI Components
> 410 .ui files across the project with no shared component system. Identify repeated patterns (pagination bars, search inputs, leaderboard entries, tab bars) and extract reusable .ui templates. Priority: leaderboard display (used in Parkour, Ascend, Mine, RunOrFall, Purge).

### 41. Split Large UI Pages
> MapAdminPage (970 lines, 68 null checks), AscendMapSelectPage (1250 lines), RunOrFallAdminPage (918 lines), AutomationPage (727 lines). Each handles 5+ UI concerns. Split rendering, event handling, and state management into separate collaborating classes.

---

## Summary Priority Matrix

| Priority | Prompts | Theme |
|----------|---------|-------|
| **P0 - Ship blockers** | 1-4 | Race conditions, memory leaks, thread safety |
| **P1 - Major refactors** | 5-13 | God class splits (biggest maintenance wins) |
| **P2 - Pattern extraction** | 14-20 | DRY, shared abstractions, build cleanup |
| **P3 - Performance** | 21-23 | Batching, caching, resource loading |
| **P4 - Code quality** | 24-28 | Constants, commands, nullability, logging |
| **P5 - Data layer** | 29-31 | Migrations, schema, queries |
| **P6 - Documentation** | 32-33 | Fix errors, document threading |
| **P7 - Testing** | 34-35 | Unit tests, bot tests |
| **P8 - Domain-specific** | 36-41 | Ascend, Purge, UI optimizations |
