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
│  parkour_* tables + future ascend_* tables (shared DB)              │
└─────────────────────────────────────────────────────────────────────┘
```

## Module Layout

- `hyvexa-core`: shared APIs/utilities, shared DB connection, mode events/state, shared mode gating/messages
- `hyvexa-parkour`: current Parkour gameplay plugin
- `hyvexa-parkour-ascend`: placeholder for Parkour Ascend mode (to be implemented)
- `hyvexa-hub`: hub routing + UI (mode switching, teleport routing)
- Hub routing targets the capitalized world names: `Hub`, `Parkour`, `Ascend`

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

| Task | Interval | Purpose |
|------|----------|---------|
| `tickMapDetection` | 200ms | Check player positions for triggers |
| `tickHudUpdates` | 100ms | Update run timer display |
| `tickPlaytime` | 60s | Accumulate player session time |
| `tickCollisionRemoval` | 2s | Re-disable player collision |
| `tickPlayerCounts` | 600s | Sample online player count |

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

| Scenario | Current Behavior | Notes |
|----------|------------------|-------|
| Disconnect mid-run | Run state remains in `activeRuns` | Cleaned up eventually by GC |
| Disconnect at finish | Completion recorded before disconnect | Works correctly |
| Reconnect after disconnect | New run state; old run lost | No run persistence |

**Mitigation**: Add disconnect event handler to clear `activeRuns` entry.

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
