# Architecture

Technical design documentation for the Hyvexa multi-module plugin suite.

## System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                            hyvexa-core                              │
│  (Shared APIs/utilities + DatabaseManager + mode events/state)       │
└─────────────────────────────────────────────────────────────────────┘
             │                   │                     │
             ▼                   ▼                     ▼
┌────────────────────┐  ┌────────────────────┐  ┌────────────────────┐
│  hyvexa-parkour    │  │ hyvexa-parkour-ascend │  │     hyvexa-hub      │
│  (Parkour plugin)  │  │ (Parkour Ascend)   │  │ (Routing + UI)      │
└────────────────────┘  └────────────────────┘  └────────────────────┘
             │                   │                     │
             ▼                   ▼                     ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       DatabaseManager                               │
│ (HikariCP connection pool - mods/Parkour/database.json credentials) │
└─────────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         MySQL Database                              │
│  parkour_* tables + ascend_* tables (shared DB)                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Module Layout

- `hyvexa-core`: shared APIs/utilities, shared DB connection, mode events/state, shared mode gating/messages
- `hyvexa-parkour`: current Parkour gameplay plugin
- `hyvexa-parkour-ascend`: Parkour Ascend idle mode with prestige progression
- `hyvexa-hub`: hub routing + UI (mode switching, teleport routing)
- Hub routing targets the capitalized world names: `Hub`, `Parkour`, `Ascend`

### Ascend Plugin Lifecycle
- Ensures Ascend DB tables on startup (`AscendDatabaseSetup`).
- Loads map store and player store into memory (`AscendMapStore`, `AscendPlayerStore`).
- Starts `AscendRunTracker` for manual completion detection.
- Starts `RobotManager` for runner ghost replay.
- Registers `/ascend` and `/as` commands.
- PlayerReady: if in Ascend world, clears inventory, gives Ascend dev items + hub selector, attaches Ascend HUD.
- AddPlayerToWorld: re-ensures Ascend dev items + hub selector when entering the Ascend world (e.g., respawn).
- PlayerDisconnect: clears HUD caches and cancels active run.
- Schedules a 200ms tick that runs run tracking + HUD updates on the Ascend world thread.

Module boundaries:
- Parkour/Ascend/Hub depend on Core only
- No dependencies between Parkour and Parkour Ascend

## Threading Model

### Thread Types

| Thread | Purpose | Operations |
|--------|---------|------------|
| **Main/Calling** | Command execution, event handlers | Read-only queries, scheduling |
| **World Thread** | Entity/world modifications | Teleports, inventory, components |
| **Scheduled Executor** | Periodic tasks | Tick loops, delayed callbacks |
| **HikariCP Pool** | Database I/O | MySQL queries via connection pool |

### Thread Safety Rules

1. **Entity modifications require world thread**:
   ```java
   CompletableFuture.runAsync(() -> {
       store.addComponent(ref, Teleport.getComponentType(), teleport);
   }, world);
   ```

2. **Data stores use memory-first pattern**:
   - All reads from `ConcurrentHashMap` (fast, no database hit)
   - Writes update memory + persist to MySQL
   - `ReadWriteLock` protects complex operations

3. **Database connections**:
   - HikariCP manages connection pooling
   - Connections auto-returned via try-with-resources
   - Never hold connections across async boundaries

4. **Scheduled tasks run on executor thread**:
   - Cannot directly modify entities
   - Must dispatch to world thread for entity operations

### Scheduled Tasks

#### Parkour Module

| Task | Interval | Purpose |
|------|----------|---------|
| `tickMapDetection` | 200ms | Check player positions for triggers |
| `tickHudUpdates` | 100ms | Update run timer display |
| `tickPlaytime` | 60s | Accumulate player session time |
| `tickCollisionRemoval` | 2s | Re-disable player collision |
| `tickPlayerCounts` | 600s | Sample online player count |

#### Ascend Module

| Task | Interval | Purpose |
|------|----------|---------|
| `RUNNER_TICK_INTERVAL_MS` | 16ms | Ghost replay tick system (~60fps for smooth movement) |
| `RUNNER_REFRESH_INTERVAL_MS` | 1000ms | Runner state refresh and validation |

## Data Flow

### Map Completion Flow

```
Player touches finish trigger
         │
         ▼
RunTracker.checkFinish()
    │
    ├── Verify all checkpoints touched
    │
    ├── Calculate duration (System.currentTimeMillis() - startTimeMs)
    │
    ├── ProgressStore.recordMapCompletion()
    │   │
    │   ├── Update in-memory completedMaps set
    │   ├── Update in-memory bestMapTimes if new best
    │   ├── Award XP (first completion only)
    │   ├── INSERT INTO player_completions (immediate)
    │   └── Queue player XP/level save (debounced 5s)
    │
    ├── Broadcast completion message (if new best)
    │
    ├── Teleport to spawn
    │
    └── Give menu items
```

### Run State Machine

```
         ┌──────────────┐
         │   IDLE       │ (No active run)
         │ Menu items   │
         └──────┬───────┘
                │ Touch start trigger
                ▼
         ┌──────────────┐
         │   RUNNING    │ (Active run)
         │ Run items    │◄────────┐
         │ Timer shown  │         │
         └──────┬───────┘         │
                │                 │
    ┌───────────┼───────────┐     │
    │           │           │     │
    ▼           ▼           ▼     │
Touch      Touch       Fall for   │
checkpoint  finish     N seconds  │
    │           │           │     │
    │           │           └─────┤ Respawn at checkpoint/start
    │           │                 │
    │           ▼                 │
    │    ┌──────────────┐         │
    │    │  COMPLETED   │         │
    │    │ Save time    │         │
    │    │ Award XP     │         │
    │    └──────┬───────┘         │
    │           │                 │
    │           ▼                 │
    │    Back to IDLE             │
    │                             │
    └─────────────────────────────┘
```

## Ghost Replay Pipeline

Ghost replay records player movement during runs and plays it back as an NPC for visual comparison.

### Recording

```
Player starts run
  |
  v
RunTracker -> GhostRecorder.startRecording(playerId, mapId)
  |
  v
ScheduledExecutor every 50ms:
  samplePlayer() -> world.execute() -> read TransformComponent
  -> create GhostSample(x, y, z, yaw, timestampMs)
  -> add to List<GhostSample> (max 12,000 samples / ~10 min)
  |
  v
Run completes (first completion or new PB)
  |
  v
RunValidator -> GhostRecorder.stopRecording(playerId, durationMs, isPersonalBest=true)
  -> GhostStore.saveRecording()
     -> serialize to GZIP binary blob
     -> INSERT...ON DUPLICATE KEY UPDATE to MySQL
     -> update in-memory cache
```

### Playback

```
Player enters map -> GhostNpcManager.spawnGhost(playerId, mapId)
  -> GhostStore.getRecording() (cache hit)
  -> NPCPlugin.spawnNPC(Kweebec_Seedling) at start position
  -> Add Invulnerable + Frozen components
  -> Hide from all players except owner
  |
  v
Player starts running -> GhostNpcManager.startPlayback(playerId)
  |
  v
ScheduledExecutor every 50ms:
  progress = elapsed / completionTimeMs
  -> GhostInterpolation.interpolateAt(samples, progress)
     -> binary search for surrounding samples
     -> linear interpolation (x, y, z) + angle-aware yaw interpolation
  -> Teleport NPC to interpolated position
  |
  v
Progress >= 100% -> despawnGhost() -> store.removeEntity(ref, REMOVE)
```

### Key Classes

| Class | Module | Role |
|-------|--------|------|
| `AbstractGhostRecorder` | core | Sampling loop (50ms), max 12K samples |
| `GhostRecording` | core | Container for samples + interpolation |
| `GhostInterpolation` | core | Binary search + linear/angle interpolation |
| `AbstractGhostStore` | core | GZIP serialization + MySQL persistence |
| `GhostStore` | core | Concrete store (per-mode table name) |
| `GhostRecorder` | parkour/ascend | Module-specific player resolution |
| `GhostNpcManager` | parkour | NPC spawn/despawn + tick playback |

## Component Interactions

### Run Tracking (RunTracker.java)

**State per player**:
- `activeRuns: ConcurrentHashMap<UUID, ActiveRun>` - Current run state
- `idleFalls: ConcurrentHashMap<UUID, FallState>` - Fall tracking when not in run

**ActiveRun contains**:
- `mapId` - Current map being run
- `startTimeMs` - Run start timestamp
- `touchedCheckpoints` - Set of checkpoint indices touched
- `lastCheckpointIndex` - Most recent checkpoint (-1 if none)
- `fallStartTime` / `lastY` - Fall detection state
- `finishTouched` - Prevents double-completion

### HUD Management

**Components**:
- `RunHud` - Custom HUD showing timer, player info, announcements
- `runHuds: ConcurrentHashMap<UUID, RunHud>` - HUD instances per player
- `runHudReadyAt` - Delay before showing HUD (avoids flicker)

**Update flow**:
1. `tickHudUpdates()` runs every 100ms
2. Checks if player has active run
3. Updates timer text with elapsed time
4. Updates announcements from queue

### Inventory Management

**Two inventory states**:
1. **Menu state**: Select Level, Leaderboards, Stats items
2. **Run state**: Reset, Restart Checkpoint, Leave items

**Swap triggers**:
- Map start → Run items
- Map leave/finish → Menu items
- Player connect → Based on active run state

**Drop protection**:
- Non-OP players have `SlotFilter.DENY` on all slots
- OP players can drop freely
- Filters reapplied on inventory change events

## Persistence

### MySQL Database

All data is stored in MySQL with in-memory caching for performance.

#### Database Configuration
Credentials stored in `mods/Parkour/database.json` (gitignored, relative to server working dir):

**Note**: Code references use both `mods/Parkour/` and `run/mods/Parkour/` inconsistently. The runtime path is `run/mods/Parkour/` (relative to project root), but production servers use `mods/Parkour/` (relative to server working directory). This inconsistency should be standardized in future refactoring.

```json
{
  "host": "localhost",
  "port": 3306,
  "database": "hytale_parkour",
  "user": "parkour",
  "password": "secret"
}
```

#### Database Tables

| Table | Purpose |
|-------|---------|
| `players` | Player XP, level, playtime, welcome state |
| `maps` | Map definitions with all transform positions |
| `map_checkpoints` | Checkpoint positions per map |
| `player_completions` | Completed maps with best times per player |
| `player_mode_state` | Hub-owned mode state + return locations per player |
| `settings` | Global server settings (single row) |
| `global_messages` | Broadcast messages |
| `global_message_settings` | Message interval (single row) |
| `player_count_samples` | Analytics time-series data |
| `ascend_players` | Ascend player state + prestige progress |
| `ascend_maps` | Ascend map definitions |
| `ascend_player_maps` | Per-player map progress (unlocks, runners, multipliers) |
| `ascend_player_summit` | Summit levels per category per player |
| `ascend_player_skills` | Skill tree unlocks per player |
| `ascend_player_achievements` | Achievement unlocks per player |

#### Data Flow Pattern

```
Server Start
     │
     ▼
DatabaseManager.initialize()  ──►  HikariCP connection pool
     │
     ▼
Store.syncLoad()  ──►  SELECT * FROM table  ──►  ConcurrentHashMap (memory)
     │
     ▼
Runtime: All reads from memory (fast)
     │
     ▼
Writes: Update memory + INSERT/UPDATE to MySQL
     │
     ├── Immediate: completions, map changes
     └── Debounced (5s): player XP/level/playtime updates
     │
     ▼
Server Stop: flushPendingSave() + connection pool shutdown
```

### Thread Safety

- `ConcurrentHashMap` for all in-memory data
- `ReadWriteLock` for complex multi-step operations
- HikariCP handles connection thread safety
- Debounced saves use `ScheduledExecutor` with dirty tracking

## Edge Cases and Known Limitations

### Player Disconnect

#### Cleanup Strategy Comparison

| Aspect | Parkour | Ascend |
|--------|---------|--------|
| **State persistence** | Session-only (ephemeral) | Lazy-loaded from DB, evicted on disconnect |
| **Memory strategy** | Keeps all visited players in cache | Evicts player data to save memory |
| **Cleanup orchestration** | Centralized (`PlayerCleanupManager`) | Distributed (`cleanupAscendState` + per-manager cleanup) |
| **DB persistence on disconnect** | Not needed (session data) | Yes — snapshots dirty data, flushes to MySQL, then evicts cache |
| **World transition handling** | Single cleanup path | Two paths: `cleanupAscendState` (shared) + `removePlayer` (disconnect-only) |
| **Reconnect behavior** | Fresh session | Re-loads from DB on demand |

**Parkour** cleans up HUD, perks, announcements, playtime, settings, visibility, and run state via `PlayerCleanupManager.handleDisconnect()`. All state is ephemeral.

**Ascend** additionally evicts player data from memory after persisting dirty changes (`AscendPlayerStore.removePlayer()` snapshots, saves, then removes from cache). This lazy-load + evict pattern keeps memory bounded.

| Scenario | Current Behavior | Notes |
|----------|------------------|-------|
| Disconnect mid-run | Run cancelled, state cleaned up | Both modules handle this |
| Disconnect at finish | Completion recorded before disconnect | Works correctly |
| Reconnect after disconnect | Parkour: fresh session; Ascend: re-loads from DB | No run persistence in either |

### Timer Precision

- **Resolution**: Milliseconds (via `System.currentTimeMillis()`)
- **Clock source**: System clock (not monotonic)
- **TPS impact**: Timer unaffected by server TPS; measures wall-clock time
- **Clock skew**: If system clock changes mid-run, time will be wrong

**Known issue**: `Math.max(0L, elapsed)` masks negative times from clock adjustments.

### Checkpoint Ordering

- Checkpoints identified by array index, not ID
- Reordering checkpoints in admin UI invalidates in-progress runs
- Removing a checkpoint shifts all subsequent indices

**Workaround**: Don't modify checkpoints while players are running the map.

### Fall Detection

- Uses Y-coordinate comparison over time
- Blocked while `climbing` or `onGround` (movement states)
- Configurable timeout in the `settings` table (`fall_respawn_seconds`)

**Edge case**: Falling through a ladder resets fall timer on each rung touch.

### Inventory Race Conditions

- Brief window during inventory swap where drop filters are permissive
- OP-dropped items picked up by non-OPs may bypass protection

**Severity**: Low - requires precise timing to exploit.

### Concurrent Admin Edits

- Two admins editing the same map simultaneously will overwrite each other
- Last write wins; no conflict detection

**Mitigation**: Coordinate admin work; don't edit same map simultaneously.

### Leaderboard Performance

- Position calculated via O(n) scan of all player times
- Acceptable for <1000 players per map
- May need optimization for larger player bases

### Database I/O

- Most writes are non-blocking (HikariCP pool handles connections)
- ProgressStore uses 5-second debounced saves for XP/level updates
- Completions saved immediately for data integrity
- Connection pool sized for 10 concurrent connections (configurable)

**Note**: Database latency affects write operations but reads are always from memory.

## User Interface

### Parkour UI Pages

UI files are located in `hyvexa-parkour/src/main/resources/Common/UI/Custom/Pages/`. Code references use the path prefix `Pages/X.ui`.

#### Core Navigation
| Page | Description |
|------|-------------|
| `Parkour_MapSelect.ui` | Main map selection menu with category filtering |
| `Parkour_CategorySelect.ui` | Category browser for map filtering |
| `Parkour_PlayerSettings.ui` | Player preferences (music, ghost visibility, etc.) |
| `Parkour_Stats.ui` | Player statistics overview |
| `Parkour_Welcome.ui` | Welcome screen for new players |

#### In-Run HUD
| Page | Description |
|------|-------------|
| `Parkour_RunHud.ui` | Active run timer and checkpoint display |
| `Parkour_RunHudHidden.ui` | Minimized HUD for clean screenshots |
| `Parkour_RunCheckpointHud.ui` | Checkpoint notification overlay |
| `Parkour_RunRecordsHud.ui` | Personal best comparison during runs |

#### Leaderboards
| Page | Description |
|------|-------------|
| `Parkour_Leaderboard.ui` | Global leaderboard across all maps |
| `Parkour_MapLeaderboard.ui` | Per-map leaderboard with time rankings |
| `Parkour_LeaderboardMenu.ui` | Leaderboard navigation and filters |

#### Admin Tools
| Page | Description |
|------|-------------|
| `Parkour_AdminIndex.ui` | Admin dashboard and navigation |
| `Parkour_MapAdmin.ui` | Map creation and configuration |
| `Parkour_AdminPlayers.ui` | Player management and lookup |
| `Parkour_SettingsAdmin.ui` | Server-wide settings control |
| `Parkour_GlobalMessageAdmin.ui` | Global message broadcast system |

#### Tutorial
| Page | Description |
|------|-------------|
| `Parkour_WelcomeTutorial_Screen1.ui` | Tutorial page 1 (basics) |
| `Parkour_WelcomeTutorial_Screen2.ui` | Tutorial page 2 (features) |
| `Parkour_WelcomeTutorial_Screen3.ui` | Tutorial page 3 (Ascend intro) |

**Note**: See `docs/hytale-custom-ui/` for official Hytale UI element documentation.

## Configuration

### Settings (database-backed)

Settings are stored in the `settings` table (single row, `id = 1`) and loaded by `SettingsStore`.

| Setting | Type | Description |
|---------|------|-------------|
| `fall_respawn_seconds` | double | Seconds of falling before respawn (<= 0 resets to default) |
| `void_y_failsafe` | double | Void cutoff Y for fail-safe teleport |
| `weapon_damage_disabled` | boolean | Disable weapon damage/knockback |
| `debug_mode` | boolean | Enable teleport debug logging |
| `spawn_*` | doubles/floats | Optional spawn position/rotation override |

### Runtime-only toggles

These are currently held in memory and reset on restart:

| Setting | Source | Description |
|---------|--------|-------------|
| `idleFallRespawnForOp` | `SettingsStore` | Whether OPs get idle fall respawn |
| `categoryOrder` | `SettingsStore` | Category ordering hints for selection UI |

### ParkourConstants.java

Hardcoded values that may need tuning:

| Constant | Value | Purpose |
|----------|-------|---------|
| `TOUCH_RADIUS` | 1.5 | Distance for trigger detection |
| `DEFAULT_FALL_RESPAWN_SECONDS` | 3.0 | Fallback fall timeout |
| `MAP_XP_EASY` | 100 | XP for Easy map completion |
| `MAP_XP_MEDIUM` | 200 | XP for Medium map completion |
| `MAP_XP_HARD` | 400 | XP for Hard map completion |
| `MAP_XP_INSANE` | 800 | XP for Insane map completion |

### AscendConstants.java

Economy, progression, and system constants for Ascend mode. Key values:

| Constant | Value | Purpose |
|----------|-------|---------|
| `MAP_BASE_RUN_TIMES_MS` | 5s-68s (6 tiers) | Base run time per map level |
| `MAP_BASE_REWARDS` | 1-2,500 volt (6 tiers) | Base volt per manual completion |
| `MAP_UNLOCK_PRICES` | 0-50,000 volt (6 tiers) | Map unlock costs |
| `RUNNER_TICK_INTERVAL_MS` | 16ms | Ghost replay tick (~60fps) |
| `MAX_ROBOT_STARS` | 5 | Max evolution level |
| `MAX_SPEED_LEVEL` | 20 | Max runner speed level |
| `ELEVATION_MULTIPLIER_EXPONENT` | 1.05 | Elevation level curve |
| `ELEVATION_BASE_COST` | 30,000 | Starting elevation cost |
| `ASCENSION_VOLT_THRESHOLD` | 10^33 | Volt needed to Ascend |
| `PASSIVE_OFFLINE_RATE_PERCENT` | 10% | Offline earning rate |
| `PASSIVE_MAX_TIME_MS` | 24h | Max offline earning window |

Also defines `SkillTreeNode` enum (19 nodes with AP costs 1-1000) and `SummitCategory` enum (3 categories with scaling factors). See `docs/Ascend/ECONOMY_BALANCE.md` for full formulas.

## Future Considerations

### Potential Improvements

1. **Run persistence**: Save active runs to survive disconnects/restarts
2. **Checkpoint IDs**: Use UUIDs instead of array indices
3. **Leaderboard caching**: Maintain sorted structure for O(1) position lookup
4. **Admin locking**: Prevent concurrent edits to same map
5. **Data versioning**: Schema version field with migration support
6. **Read replicas**: Add read-only database replicas for analytics

### Scaling Considerations

- Current design supports ~100+ concurrent players comfortably
- MySQL backend enables horizontal scaling and multi-server deployments
- Connection pool can be tuned for higher concurrency
- Consider Redis caching layer for extremely high traffic
