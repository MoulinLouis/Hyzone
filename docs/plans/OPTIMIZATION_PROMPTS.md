# Optimization Prompts - Hyvexa Codebase

Concise prompts to send one at a time. Each one is a self-contained optimization task.

---

## Critical - Correctness & Safety

### 1. RunOrFallGameManager Synchronization Fix
> RunOrFallGameManager (~1994 lines) uses `synchronized` on every public method despite all fields being ConcurrentHashMap or volatile. The synchronized keyword is redundant when all fields are concurrent, and volatile is redundant under synchronized. Pick one strategy and apply it consistently. Also audit compound check-then-act operations across multiple maps.

---

## High Impact - God Class Splits

### 2. Split RunOrFallGameManager
> RunOrFallGameManager.java (~1994 lines) handles game logic, block management, player state, HUD updates, and scoring all in one class. Split into: `GameStateManager` (state machine + transitions), `BlockManager` (block spawning/breaking), `PlayerStateTracker` (lobby/game player tracking), `ScoreManager` (scoring + HUD sync). Combine with the synchronization fix (prompt 1).

### 3. Split ProgressStore
> ProgressStore.java (~1247 lines) manages player progress caching, leaderboard caching, save debouncing, and dirty tracking with 8 concurrent collections. Split into: `PlayerProgressCache` (in-memory progress), `LeaderboardCache` (leaderboard queries + versioning), `ProgressPersistence` (save/load/debounce logic).

### 4. Split RunTracker & DuelTracker
> RunTracker (~1104 lines, 78 methods) and DuelTracker (~1100 lines) share similar structures. Extract shared `ActiveSessionTracker<T>` base with common player tracking, idle detection, and cleanup. Split RunTracker's `checkPlayer()` (130 lines) into sub-checkers.

### 5. Split RobotManager & MineRobotManager
> RobotManager (~1612 lines) and MineRobotManager (~993 lines) duplicate spawn/despawn/visibility/orphan-cleanup patterns. Extract `NPCLifecycleManager` base class with shared entity lifecycle, then have both extend it with domain-specific logic (runner movement vs mining behavior).

### 9. Split RunTracker & DuelTracker
> RunTracker (1089 lines, 78 methods, 12 ConcurrentHashMaps) and DuelTracker (1083 lines) share similar structures. Extract shared `ActiveSessionTracker<T>` base with common player tracking, idle detection, and cleanup. Split RunTracker's `checkPlayer()` (130 lines) into sub-checkers.

### 10. Split AnalyticsStore
> AnalyticsStore.java (527 lines, 20+ public methods) mixes event logging, daily aggregation, retention analytics, and data purging. Split into: `EventLogger`, `DailyAnalyticsAggregator`, `RetentionAnalyzer`.

### 11. ~~Split RobotManager & MineRobotManager~~ ✅
> ~~RobotManager (1685 lines) and MineRobotManager (999 lines) duplicate spawn/despawn/visibility/orphan-cleanup patterns. Extract `NPCLifecycleManager` base class with shared entity lifecycle, then have both extend it with domain-specific logic (runner movement vs mining behavior).~~
>
> **Done**: Chose composition over inheritance. Created `NPCEntityState` interface + `NPCHelper` utility class in hyvexa-core with shared `extractEntityRef`, `setupNpcDefaults`, `initNpcPlugin`, `despawnEntity`. Deduplicated RobotManager by delegating to RobotSpawner. Updated MineRobotManager + EggRouletteAnimation to use NPCHelper. Net -204 lines across 4 files.

### 12. ~~Split AscendPlayerProgress~~ ✅
> Done. Split into 4 focused state classes: `EconomyState` (volt, elevation, summit XP), `GameplayState` (map progress, ascension, challenges, transcendence), `AutomationConfig` (auto-upgrade/elevation/summit settings), `SessionState` (passive earnings, UI settings). AscendPlayerProgress reduced from 774 to 14 lines, now a thin composition class with `economy()`, `gameplay()`, `automation()`, `session()` accessors. All 24 consumers migrated.

### 13. ~~Merge/Refactor AscendPlayerStore + AscendPlayerPersistence~~ ✅
> Done. Chose Option B (clear contract). Moved inline SQL from Store into Persistence.deleteAllPlayerData(), consolidated duplicated table lists into CHILD_TABLES constant, removed unused SQL imports from Store, added contract javadoc. Store = cache + business logic + public API. Persistence = SQL + dirty tracking + save scheduling.

---

## Medium Impact - Pattern Extraction

### 14. ~~Extract JDBC Template Pattern~~ ✅ DONE
> ~~47 occurrences of `try (Connection conn = ... PreparedStatement stmt = ...)` across all Store classes. Create `DatabaseManager.query(sql, mapper)` and `DatabaseManager.update(sql, binder)` template methods. This cuts ~50% of database boilerplate across the entire codebase.~~
>
> Implemented: `queryOne`, `queryList`, `execute`, `executeCount`, `executeBatch` on `DatabaseManager` + `RowMapper`, `ParamBinder`, `SQLBiConsumer` interfaces. 37 files migrated, ~1044 lines removed.

### 8. Consolidate Build Configuration
> JAR signing exclusions, duplicatesStrategy, and `updatePluginManifest` task are copy-pasted identically across 3+ module build.gradle files (parkour, parkour-ascend, purge). Extract into a shared `buildSrc` convention plugin.

### 9. Centralize Player Cleanup
> Every module implements player cleanup on disconnect independently. `PlayerCleanupManager` exists in parkour but each other module (Hub, Purge, RunOrFall, Wardrobe) has its own scattered eviction logic. Create `GlobalPlayerCleanupRegistry` in hyvexa-core that modules register their eviction callbacks into.

### 10. Extract HudLifecycleManager to Core
> Three separate HUD lifecycle implementations exist: HyvexaHubPlugin (HudLifecycle record + tickHubHudRecovery), HyvexaRunOrFallPlugin (separate state tracking maps), PurgeHudManager. Extract `HudLifecycleManager<T>` to hyvexa-core with generic attach/detach/refresh/recovery logic. ~150 lines of duplication eliminated.

### 11. Extract Reflection Bridge Base
> HylogramsBridge and MultiHudBridge both use reflection + METHOD_CACHE with identical patterns. Extract `ReflectionBridge` base class with `invokeStatic()`, cached method lookup, and init logic.

---

## Performance

### ~~21. Batch Analytics Events~~ ✅
> ~~AnalyticsStore does individual INSERTs per event. Collect events in a buffer and batch-insert (100+ per round-trip). Reduces DB round-trips ~99% for analytics-heavy loads.~~
>
> **Done**: Events buffered in ConcurrentLinkedQueue, flushed every 5s via addBatch/executeBatch (up to 500 per batch). Added shutdown() for graceful drain.

### 22. Add Background Cache Eviction
> CachedCurrencyStore has 30-min TTL but no background cleaner — stale entries stay in cache until next access. Add a ScheduledExecutor task to evict entries past TTL. Apply same pattern to all TTL-based caches.

### 15. Move Hardcoded JSON to Resources
> CosmeticConfigLoader.generateDefault() has ~125 lines of hardcoded JSON string concatenation. Move to `cosmetics-default.json` resource file loaded at startup.

---

## Code Quality

### 16. Message Constants Extraction
> 240 occurrences of `.sendMessage(Message.raw(` with hardcoded strings across 15 command files in parkour module. Extract to `Messages` constants class per module. Also standardize color usage.

### 17. Command Handler Cleanup
> 24 command files in parkour with inconsistent patterns: some use `CommandUtils.findPlayerByName()`, others use `ctx.sender()` directly. OP validation is duplicated everywhere. Standardize player resolution and extract OP check to decorator or base class.

### 18. Null Contract Documentation
> 140+ null/validity checks in parkour module alone (68 in MapAdminPage). Add `@Nullable`/`@Nonnull` annotations to document nullability contracts on public APIs. Remove redundant defensive checks where contracts guarantee non-null.

### 19. Map.java Value Objects
> Map.java (288 lines, 32 fields) has fragmented data: 6 transform locations, 6 fly zone coords, 4 medal times, 5 ability flags. Consolidate into value objects: `BoundingBox` for fly zones, `EnumMap<Medal, Long>` for medal times, `AbilitySet` for flags.

### 20. Logging Consistency
> 82% of parkour module files have zero logging. Critical paths (ProgressStore, RunTracker) log nothing on failure. Add structured logging to all Store save/load methods and Tracker state transitions. Standardize LOGGER field naming.

---

## Database

### 21. Centralize Migrations
> Migrations are scattered: VexaStore has `migratePlayerGemsToVexa()`, DiscordLinkStore has `migrateGemsRewardedToVexa()`, AscendDatabaseSetup has 5+ migration methods inline. Create a `MigrationRunner` with ordered, idempotent migration entries that run at startup.

### 30. Modularize AscendDatabaseSetup
> AscendDatabaseSetup.java (1669 lines) handles ALL Ascend table creation in one monolithic file. Split by subsystem: `AscendSchemaSetup`, `MineSchemaSetup`, `ChallengeSchemaSetup`, each responsible for their own tables.

### ~~31. Replace LIKE JSON Queries~~ ✅
> ~~AnalyticsStore uses `LIKE '%"is_new":true%'` for JSON searching — fragile, depends on serialization format. Replace with `JSON_EXTRACT()` and proper type coercion.~~
>
> **Done**: All 3 LIKE queries in computeDailyAggregates replaced with JSON_EXTRACT/JSON_UNQUOTE. Replaced countEventsWithFilter (LIKE-based) with countEventsWithJsonFilter(path, value) using typed JSON extraction. Updated all callers in AnalyticsCommand.

---

## Documentation

### 23. Document Threading Model
> No documentation exists for the threading model. Document: which executors exist, what runs on world thread vs IO thread vs scheduled executor, which operations require `world.execute()`, and synchronization contracts for each Manager/Store.

---

## Testing

### 24. Add Pure Logic Unit Tests
> Only 16 test files exist for 511 Java files. Identify all pure-logic classes with zero Hytale imports and add tests. Priority targets: economy calculations, leaderboard sorting, BigNumber operations, format utilities, pagination logic, challenge condition evaluation, medal time calculations.

### 25. Discord Bot Tests
> discord-bot has zero tests, no devDependencies, no test script. Add Jest, mock mysql2 connections, test: code validation, link creation, rank sync batching, error handling paths.

---

## Ascend-Specific

### 26. AscendConstants Organization
> AscendConstants.java (~959 lines) mixes Parkour, Mine, Summit, and Challenge constants with no clear organization. Split into domain-specific constant classes or at minimum add clear section headers and document formulas.

### 27. Consolidate Ascend Collection Groups
> RobotManager has 10 separate collections, AscendPlayerPersistence has 8+. Create wrapper objects: `RobotVisibilityState`, `PlayerTrackingState`, `LeaderboardCacheState` to group related collections and ensure consistent updates.

---

## Purge-Specific

### 28. Split PurgeWaveManager
> PurgeWaveManager (~873 lines) handles wave spawning, NPC management, entity manipulation, and difficulty scaling. Split into: `WaveSpawner` (entity creation), `WaveDifficultyCalculator` (scaling logic), `WaveStateTracker` (active wave state).

### 29. Split PurgeSessionManager
> PurgeSessionManager (~774 lines) mixes session lifecycle, state transitions, player scoring, and HUD sync. Split into: `SessionLifecycle` (start/stop/transitions), `SessionScorer` (scoring logic), `SessionStateSync` (HUD/client sync).

---

## UI

### 30. Extract Shared UI Components
> 410 .ui files across the project with no shared component system. Identify repeated patterns (pagination bars, search inputs, leaderboard entries, tab bars) and extract reusable .ui templates. Priority: leaderboard display (used in Parkour, Ascend, Mine, RunOrFall, Purge).

### 31. Split Large UI Pages
> MapAdminPage (~975 lines, 68 null checks), AscendMapSelectPage (~1270 lines), RunOrFallAdminPage (~918 lines), AutomationPage (~727 lines). Each handles 5+ UI concerns. Split rendering, event handling, and state management into separate collaborating classes.

---

## Summary Priority Matrix

| Priority | Prompts | Theme |
|----------|---------|-------|
| **P0 - Ship blockers** | 1 | RunOrFallGameManager over-synchronization |
| **P1 - Major refactors** | 2-6 | God class splits (biggest maintenance wins) |
| **P2 - Pattern extraction** | 7-11 | JDBC template, build config, cleanup registry |
| **P3 - Performance** | 12-15 | Batching, JSON queries, caching, resource loading |
| **P4 - Code quality** | 16-20 | Constants, commands, nullability, logging |
| **P5 - Data layer** | 21-22 | Migrations, schema modularization |
| **P6 - Documentation** | 23 | Threading model |
| **P7 - Testing** | 24-25 | Unit tests, bot tests |
| **P8 - Domain-specific** | 26-31 | Ascend, Purge, UI optimizations |
