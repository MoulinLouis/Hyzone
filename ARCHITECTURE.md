# Architecture

Technical design documentation for the Hyvexa Parkour Plugin.

## System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         HyvexaPlugin                                │
│  (Entrypoint - registers commands, events, scheduled tasks)         │
└─────────────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│   RunTracker    │  │   Data Stores   │  │   UI Pages      │
│ (Active runs,   │  │ (Persistence)   │  │ (Player menus)  │
│  checkpoints,   │  │                 │  │                 │
│  fall detection)│  │ - MapStore      │  │ - MapSelectPage │
└─────────────────┘  │ - ProgressStore │  │ - LeaderboardPage│
         │           │ - SettingsStore │  │ - AdminPages    │
         │           │ - PlayerCountStore│ └─────────────────┘
         │           └─────────────────┘
         │                    │
         ▼                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Parkour/ (Runtime Data Directory)                │
│  Maps.json  |  Progress.json  |  Settings.json  |  PlayerCounts.json│
└─────────────────────────────────────────────────────────────────────┘
```

## Threading Model

### Thread Types

| Thread | Purpose | Operations |
|--------|---------|------------|
| **Main/Calling** | Command execution, event handlers | Read-only queries, scheduling |
| **World Thread** | Entity/world modifications | Teleports, inventory, components |
| **Scheduled Executor** | Periodic tasks | Tick loops, delayed callbacks |
| **File I/O** | Persistence | JSON read/write (blocking) |

### Thread Safety Rules

1. **Entity modifications require world thread**:
   ```java
   CompletableFuture.runAsync(() -> {
       store.addComponent(ref, Teleport.getComponentType(), teleport);
   }, world);
   ```

2. **Data stores use file locks**:
   - `BlockingDiskFile` provides `fileLock.readLock()` and `fileLock.writeLock()`
   - In-memory maps use `ConcurrentHashMap`

3. **Scheduled tasks run on executor thread**:
   - Cannot directly modify entities
   - Must dispatch to world thread for entity operations

### Scheduled Tasks

| Task | Interval | Purpose |
|------|----------|---------|
| `tickMapDetection` | 200ms | Check player positions for triggers |
| `tickHudUpdates` | 20ms | Update run timer display |
| `tickPlaytime` | 60s | Accumulate player session time |
| `tickCollisionRemoval` | 2s | Re-disable player collision |
| `tickPlayerCounts` | 60s | Sample online player count |

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
    │   ├── Update completedMaps set
    │   ├── Update bestMapTimes if new best
    │   ├── Award XP (first completion only)
    │   ├── Check title unlocks
    │   └── syncSave() to Progress.json
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
1. `tickHudUpdates()` runs every 20ms
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

### JSON File Formats

#### Maps.json
```json
[
  {
    "id": "map_001",
    "name": "Tutorial",
    "category": "Easy",
    "order": 1,
    "firstCompletionXp": 100,
    "start": { "x": 0, "y": 64, "z": 0, "rotX": 0, "rotY": 0, "rotZ": 0 },
    "finish": { "x": 100, "y": 64, "z": 0, ... },
    "checkpoints": [ { "x": 50, "y": 64, "z": 0, ... } ],
    "startTrigger": { ... },
    "leaveTrigger": { ... },
    "leaveTeleport": { ... }
  }
]
```

#### Progress.json
```json
[
  {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "name": "PlayerName",
    "completedMaps": ["map_001", "map_002"],
    "bestTimes": { "map_001": 45230, "map_002": 123456 },
    "xp": 500,
    "level": 3,
    "titles": ["Novice"],
    "welcomeShown": true,
    "playtimeMs": 3600000
  }
]
```

### File Locking

All stores extend `BlockingDiskFile` which provides:
- `fileLock.readLock()` - Multiple concurrent readers
- `fileLock.writeLock()` - Exclusive write access
- `syncLoad()` - Blocking read from disk
- `syncSave()` - Blocking write to disk

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
- Configurable timeout in Settings.json (`fallRespawnSeconds`)

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

### File I/O

- `syncSave()` blocks calling thread
- Called after every progress update
- Could cause latency spikes under heavy load

**Mitigation**: Consider batching saves or async I/O for high-traffic deployments.

## Configuration

### Settings.json

```json
{
  "fallRespawnSeconds": 3.0,
  "idleFallRespawnForOp": false,
  "categoryOrder": ["Easy", "Medium", "Hard", "Insane"]
}
```

| Setting | Type | Description |
|---------|------|-------------|
| `fallRespawnSeconds` | double | Seconds of falling before respawn (0 = disabled) |
| `idleFallRespawnForOp` | boolean | Whether OPs get idle fall respawn |
| `categoryOrder` | string[] | Order of categories in selection UI |

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
3. **Async saves**: Background file I/O with dirty flag batching
4. **Leaderboard caching**: Maintain sorted structure for O(1) position lookup
5. **Admin locking**: Prevent concurrent edits to same map
6. **Data versioning**: Schema version field with migration support

### Scaling Considerations

- Current design supports ~100 concurrent players comfortably
- File-based persistence limits horizontal scaling
- Consider database backend for multi-server deployments
