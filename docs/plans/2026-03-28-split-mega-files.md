# Plan: Split Mega-Files (excluding RunOrFall)

**Date:** 2026-03-28
**Goal:** Reduce the largest files to ~300-600 lines each, with clean extraction boundaries.
**Scope:** All modules except `hyvexa-runorfall`.

---

## Guiding Principles

1. **Only split where the boundary is real** — if two groups of methods share the same state and call each other, they belong together.
2. **Extracted classes own their state** — no passing 10 maps as constructor args to an extracted "helper". The new class holds the data it needs.
3. **Package-private by default** — extracted classes should be invisible outside their package unless there's a reason to expose them.
4. **One commit per file split** — each split is atomic and independently revertable.

---

## Target Files

| File | Lines | Module | Priority |
|------|-------|--------|----------|
| `AscendDatabaseSetup` | 1668 | ascend | P3 |
| `AscendPlayerPersistence` | 1296 | ascend | P1 |
| `ProgressStore` | 1182 | parkour | P1 |
| `MineRobotManager` | 1022 | ascend | P2 |
| `ParkourAscendPlugin` | 1139 | ascend | P2 |

### Not Splitting

| File | Lines | Why |
|------|-------|-----|
| `AscendMapSelectPage` | 1276 | UI page — all methods share 14 injected stores. Splitting creates helpers that each take 5-10 store params. The page is one screen's behavior; the 14 deps are a composition problem, not a file-size problem. |
| `RunTracker` | 1135 | Already has 5 extracted helpers (PingTracker, JumpTracker, RunValidator, RunSessionTracker, RunTeleporter). The remaining code is the run state machine — practice mode, checkpoints, teleports, finish detection. These are intertwined through `ActiveRun` state. Further splits would fragment the state machine. |
| `HyvexaPlugin` | 920 | Composition root at 920 lines. Under threshold, and splitting wiring code means wiring the wired wiring. Marginal benefit. |

---

## Phase 1: AscendPlayerPersistence → Extract Leaderboard + Save System

**Current state:** 1296 lines, package-private. Primary accessor is `AscendPlayerStore`; `AscendRunnerFacade` also directly calls `loadBestTimeFromDatabase()`.

The file mixes three distinct subsystems that share minimal state:

### Split A: `AscendLeaderboardQueries` (~350 lines)

**What moves:**
- Fields: `leaderboardCache`, `leaderboardCacheTimestamp`, `mapLeaderboardCache`, `mapLeaderboardCacheTimestamps`, `LEADERBOARD_CACHE_TTL_MS`
- Methods: `getLeaderboardEntries()`, `fetchLeaderboardFromDatabase()`, `clearLeaderboardCaches()`, `invalidateLeaderboardCache()`, `invalidateMapLeaderboardCache()`, `getMapLeaderboard()`, `fetchMapLeaderboardFromDatabase()`, `buildMapLeaderboardMergeKey()`

**Constructor receives:**
```java
AscendLeaderboardQueries(ConnectionProvider db,
                         Map<UUID, AscendPlayerProgress> players,
                         Map<UUID, String> playerNames)
```

**Why this boundary works:** Leaderboard queries read from `players` and `playerNames` maps (shared via reference, no copy) + hit the database. They never write to player progress, never participate in save scheduling, never touch dirty tracking. The cache state (`leaderboardCache`, `mapLeaderboardCache`, timestamps) is fully owned by the leaderboard subsystem.

**Callers in AscendPlayerPersistence:** Only `clearLeaderboardCaches()` is called from save methods (after reset). This becomes `leaderboardQueries.clearLeaderboardCaches()`.

### Split B: `AscendSaveScheduler` (~250 lines)

**What moves:**
- Fields: `dirtyPlayerVersions`, `detachedDirtyPlayers`, `saveQueued`, `saveFuture`, `saveLock`, `CHILD_TABLES`
- Methods: `markDirty()`, `isDirty()`, `snapshotForSave()`, `markTranscendenceResetPending()`, `clearDirtyState()`, `queueSave()`, `flushPendingSave()`, `cancelPendingSave()`, `hasDirtyVersion()`, `syncSave()`, `doSyncSave()`, `savePlayerIfDirty()`, `resolveProgressForSave()`, `closeQuietly()`
- Also: `transcendenceResetPending`, `resetPendingPlayers` (ref from parent)

**Constructor receives:**
```java
AscendSaveScheduler(ConnectionProvider db,
                    Map<UUID, AscendPlayerProgress> players,
                    Map<UUID, String> playerNames,
                    Set<UUID> resetPendingPlayers)
```

**Why this boundary works:** The save system is self-contained — it tracks dirty versions, schedules debounced saves, and writes to the database. It reads from `players`/`playerNames` maps but never mutates player progress objects (only reads their current state to build SQL). The `resetPendingPlayers` set is shared with the parent store (which adds entries), and the scheduler consumes them during save.

### What stays in `AscendPlayerPersistence` (~700 lines)

- `loadPlayerFromDatabase()` + all `load*ForPlayer()` methods
- `loadBestTimeFromDatabase()`
- `deletePlayerDataFromDatabase()`, `deleteAllPlayerData()`
- `safeGet*()` helpers
- `serialize*()` / `parse*()` helpers
- Delegates to `AscendLeaderboardQueries` and `AscendSaveScheduler`

### Result

| Class | Lines | Responsibility |
|-------|-------|----------------|
| `AscendPlayerPersistence` | ~700 | Load + delete + serialization |
| `AscendLeaderboardQueries` | ~350 | Leaderboard cache + DB queries |
| `AscendSaveScheduler` | ~250 | Dirty tracking + debounced saves |

---

## Phase 2: ProgressStore → Extract Leaderboard Cache

**Current state:** 1182 lines, public store for parkour player progress.

### Split A: `ParkourLeaderboardCache` (~300 lines)

**What moves:**
- Fields: `leaderboardCache`, `leaderboardVersions`
- Methods: `invalidateLeaderboardCache()`, `buildLeaderboardCache()`, `buildTopRows()`, `getLeaderboardEntries()`, `getLeaderboardPosition()`, `getWorldRecordTimeMs()`, `getLeaderboardHudSnapshot()`, `toDisplayedCentiseconds()`, `getDisplayPlayerName()`
- Inner classes: `LeaderboardCache`, `LeaderboardHudSnapshot`, `LeaderboardHudRow`

**Constructor receives:**
```java
ParkourLeaderboardCache(Map<UUID, PlayerProgress> progress,
                        Map<UUID, String> lastKnownNames)
```

**Why this boundary works:** Same pattern as the Ascend leaderboard extraction. The cache reads from `progress` and `lastKnownNames` maps. It never writes player data. Its own state (cache maps, versions) is fully self-contained. `ProgressStore` calls `invalidateLeaderboardCache()` when data changes — that becomes a delegation call.

**Note:** `PlayerProgress` stays package-private in ProgressStore. The leaderboard cache accesses it because it's in the same package.

### Split B: Extract DTOs to own files (~120 lines saved)

Move to top-level classes (same package):
- `ProgressionResult` (public, used by RunValidator)
- `MapPurgeResult` (public, used by admin commands)
- `CompletionPersistenceRequest` (package-private, used by ProgressStore)

These are plain data carriers with no logic. Having them as inner classes of a 1182-line file just makes the file longer.

### What stays in `ProgressStore` (~760 lines)

- `syncLoad()` + all load methods
- `recordMapCompletion()` + persistence
- Player data accessors (welcome, teleport count, playtime, jumps, rank)
- XP calculations (calculateLevel, getCompletionXp, etc.)
- Save management (queueSave, syncSave, etc.)
- Cleanup (clearProgress, purgeMapProgress, etc.)
- Delegates to `ParkourLeaderboardCache`

### Result

| Class | Lines | Responsibility |
|-------|-------|----------------|
| `ProgressStore` | ~760 | Player data, XP, persistence |
| `ParkourLeaderboardCache` | ~300 | Leaderboard cache + queries |
| `ProgressionResult` | ~25 | DTO |
| `MapPurgeResult` | ~10 | DTO |
| `CompletionPersistenceRequest` | ~15 | DTO |

---

## Phase 3: MineRobotManager → Extract Conveyor System

**Current state:** 1022 lines, manages both mining robots and the conveyor belt system.

### Split: `MineConveyorManager` (~250 lines)

**What moves:**
- Fields: `conveyorItems`, `conveyorTickTask`, `pickupDelayField`, `mergeDelayField`
- Methods: `spawnConveyorItem()`, `cleanupConveyorItems()`, `isConveyorFull()`, `getInFlightCount()`, `tickConveyorItems()`
- Reflection setup for item pickup/merge delay fields

**Constructor receives:**
```java
MineConveyorManager(ConveyorConfigStore conveyorConfigStore,
                    MinePlayerStore playerStore,
                    MineAchievementTracker achievementTracker)
```

**Why this boundary works:** The conveyor system is already a distinct subsystem:
- Its own scheduled tick task (`conveyorTickTask`, separate from the miner tick at a different interval)
- Its own state (`conveyorItems` map, keyed by owner UUID)
- Its own config store (`ConveyorConfigStore`)

**Interaction points with MineRobotManager** (these become delegation calls):
- `tickMiner()` → `spawnConveyorItem()` when a block is broken
- `tickMiner()` → `isConveyorFull()` to check before continuing mining
- `onPlayerLeave()` → `cleanupConveyorItems()` when player disconnects
- `despawnMiner()` → `cleanupConveyorItems()` when a miner slot is despawned
- `despawnAll()` → `cleanupConveyorItems()` during shutdown

Public API of extracted class: `spawnConveyorItem()`, `cleanupConveyorItems()`, `isConveyorFull()`, `tickConveyorItems()`, `start()`, `stop()`.

**Lifecycle:** `MineRobotManager.start()` creates and starts the conveyor manager. `stop()` stops it.

### What stays in `MineRobotManager` (~770 lines)

Everything miner-related: spawn/despawn, ticking, animation, block breaking, orphan cleanup, state queries.

### Result

| Class | Lines | Responsibility |
|-------|-------|----------------|
| `MineRobotManager` | ~770 | Miner NPC lifecycle + ticking |
| `MineConveyorManager` | ~250 | Conveyor belt item spawning + ticking |

---

## Phase 4: ParkourAscendPlugin → Extract Tick Handler

**Current state:** 1139 lines. Composition root with embedded tick logic.

### Split: `AscendTickHandler` (~175 lines)

**What moves:**
- Fields: `tickCounter`, `worldTickInFlight`, `tickPlayersByWorld`, `playerTickWorlds`, `playersInAscendWorld`, `FULL_TICK_INTERVAL`
- Methods: `tick()`, `applyHiddenStateForPlayer()`, `cacheTickPlayer()`, `syncTickPlayerWorld()`, `removeTickPlayer()`, `removeTickPlayerFromWorld()`
- The per-player-per-world ticking infrastructure

**Constructor receives:**
```java
AscendTickHandler(AscendPlayerStore playerStore,
                  AscendRunTracker runTracker,
                  AscendHudManager hudManager,
                  MineHudManager mineHudManager,
                  MineGateChecker mineGateChecker,
                  RobotManager robotManager,
                  AscendSettingsStore settingsStore,
                  Supplier<PlayerRef> playerRefLookup)
```

**Why this boundary works:** The tick system is a runtime concern, not a setup concern. It has its own state (player-by-world maps, tick counters, inflight atomics) that has nothing to do with the composition root's job of wiring dependencies. The plugin creates the tick handler after setup and delegates `tick()` calls to it.

### What stays in `ParkourAscendPlugin` (~965 lines)

- All fields for stores/managers
- `setup()` — full initialization sequence (~568 lines)
- `shutdown()` — cleanup
- All accessor methods (6 public)
- Event handler registrations
- Interaction codec registration
- `getInstance()`, `getPlayerRef()`, `runSafe()`

### Honest assessment

965 lines is still large for the plugin, but the remaining bulk is `setup()` (~568 lines of sequential wiring) + 6 public accessors + private helpers. The setup method is inherently sequential and doesn't benefit from extraction — moving it to a "SetupHelper" class just displaces it without reducing cognitive load. This is an acceptable size for a composition root of this complexity.

### Result

| Class | Lines | Responsibility |
|-------|-------|----------------|
| `ParkourAscendPlugin` | ~965 | Composition root: wiring + lifecycle |
| `AscendTickHandler` | ~175 | Per-tick player updates by world |

---

## Phase 5: AscendDatabaseSetup → Domain Split

**Current state:** 1668 lines, all static methods. `ensureTables()` orchestrates ~40 independent migration methods.

### Why lowest priority

Each migration method is self-contained — you never need to understand the full 1668 lines to add a new migration. The cognitive load is low despite the size. (Note: the file is not strictly append-only — some migrations include `DROP COLUMN`/`DROP TABLE` for schema transformations — but each migration is still independent of the others.)

### Split approach

Split into domain-specific static utility classes. `ensureTables()` stays as orchestrator.

| New class | Content | ~Lines |
|-----------|---------|--------|
| `AscendDatabaseSetup` | `ensureTables()` orchestrator + `columnExists()` shared utility | ~80 |
| `AscendCoreSchema` | Core tables (ascend_players, ascend_maps, ascend_player_maps) + their migrations (coin formats, elevation, multiplier schema, scientific notation). Note: `migrateToScientificNotation()` touches both `ascend_players` and `ascend_player_maps` — it stays here since both are core tables. | ~500 |
| `AscendFeatureSchema` | Feature tables (summit, skills, achievements, cats, challenges, ghost recordings) + settings columns + progress columns + tutorial | ~400 |
| `AscendMineSchema` | Mine-related columns (upgrade, block HP, pickaxe, conveyor, multi-slot) | ~350 |
| `AscendAutomationSchema` | Automation columns (robot time/stars, auto-elevation, auto-summit, auto-ascend, break-ascension) | ~350 |

Each receives a `Connection` parameter. Methods that use their own transactions (`DatabaseManager.withTransaction()`) keep doing so. `columnExists()` stays in `AscendDatabaseSetup` as a package-private shared utility (used 81 times across all groups).

```java
// AscendDatabaseSetup.java (orchestrator)
public static void ensureTables() {
    try (Connection conn = DatabaseManager.get().getConnection(); Statement stmt = conn.createStatement()) {
        AscendCoreSchema.createAndMigrate(conn, stmt);
        AscendFeatureSchema.createAndMigrate(conn, stmt);
        AscendMineSchema.ensureColumns(conn);
        AscendAutomationSchema.ensureColumns(conn);
    }
}
```

---

## Execution Order

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5
  ↑ highest impact,              lowest priority ↓
    most edit frequency           append-only file
```

Each phase is one atomic commit. No phase depends on another — they can be done in any order or in parallel (different modules, no conflicts).

## Estimated line count after all phases

| File (before) | Lines before | Files after | Max lines |
|---------------|-------------|-------------|-----------|
| AscendPlayerPersistence | 1296 | 3 files | ~700 |
| ProgressStore | 1182 | 5 files | ~760 |
| MineRobotManager | 1022 | 2 files | ~770 |
| ParkourAscendPlugin | 1139 | 2 files | ~965 |
| AscendDatabaseSetup | 1668 | 5 files | ~500 |

No file above 965 lines. All extraction boundaries are real subsystem boundaries, not artificial cuts.
