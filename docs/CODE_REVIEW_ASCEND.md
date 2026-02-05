# Code Review: hyvexa-parkour-ascend

**Reviewer**: Senior Dev (angry)
**Verdict**: I have concerns.

---

## 1. Architecture

### 1.1 ParkourAscendPlugin is a God Object

632 lines. Holds references to **13 managers/stores**. Handles events, HUD lifecycle, inventory management, tick scheduling, and interaction registration -- all in one class. This is a service locator masquerading as a plugin entry point.

```
mapStore, playerStore, settingsStore, ghostStore, ghostRecorder,
runTracker, robotManager, hologramManager, summitManager,
ascensionManager, achievementManager, passiveEarningsManager, whitelistManager
```

Every manager reaches back into the plugin via `ParkourAscendPlugin.getInstance()`. You've created a circular dependency graph where nothing can be instantiated or tested in isolation.

**Files**: `ParkourAscendPlugin.java` (entire file)

### 1.2 Static Singleton Everywhere

`ParkourAscendPlugin.getInstance()` is called from:
- `RobotManager.tickRobot()` (line 656)
- `RobotManager.calculateSpeedMultiplier()` (line 711)
- `RobotManager.hideFromActiveRunners()` (line 758)
- `AscendRunTracker.completeRun()` (line 229)
- `AscendRunTracker.checkWalkOnStart()` (line 393)
- `PassiveEarningsManager.calculateAndApplyPassiveEarnings()` (line 108)

If the plugin reinitializes, `INSTANCE` gets overwritten and anything holding the old reference silently breaks. This isn't dependency injection; this is a global variable with extra steps.

### 1.3 AscendConstants is Not a Constants File

661 lines containing:
- Database config
- Item IDs
- Economy constants and arrays
- Upgrade cost formulas with BigDecimal math
- Elevation purchase calculation with a `while(true)` loop
- Summit XP system with level calculation
- Ascension system
- **Two full enum definitions** (SkillTreeNode with 18 members, AchievementType with 12 members)
- HUD color arrays

This is an entire domain model crammed into a "constants" class. The SkillTreeNode enum alone has fields, methods, and inter-node dependency resolution. That's not a constant.

---

## 2. Concurrency

### 2.1 Atomic Operations Aren't Actually Atomic

`atomicAddCoins()` does a SQL UPDATE, then updates the in-memory cache:

```java
// AscendPlayerStore.java:446-461
stmt.executeUpdate();  // DB is now correct
// <-- another thread reads stale in-memory value here
progress.addCoins(amount);  // memory now correct
```

Between the DB write and cache update, any thread reading `getCoins()` sees stale data. This is especially bad because `RobotManager.tickRobot()` and `AscendRunTracker.completeRun()` both call `atomicAddCoins` concurrently from different threads.

### 2.2 Atomic Operations Silently Fail

`atomicAddCoins`, `atomicAddTotalCoinsEarned`, and `atomicAddMapMultiplier` all return `boolean`, but **no caller checks the return value**:

```java
// RobotManager.java:681-683
playerStore.atomicAddCoins(ownerId, totalPayout);         // return value ignored
playerStore.atomicAddTotalCoinsEarned(ownerId, totalPayout); // return value ignored
playerStore.atomicAddMapMultiplier(ownerId, mapId, totalMultiplierBonus); // return value ignored

// AscendRunTracker.java:262-264
playerStore.atomicAddCoins(playerId, payout);              // return value ignored
playerStore.atomicAddTotalCoinsEarned(playerId, payout);   // return value ignored
playerStore.atomicAddMapMultiplier(playerId, run.mapId, finalMultiplierIncrement); // ignored
```

If the DB goes down, coins are lost. No retry, no compensation, no error message to the player.

### 2.3 Debounced Save Races with Dirty Tracking

```java
// AscendPlayerStore.java:1143-1144
Set<UUID> toSave = Set.copyOf(dirtyPlayers);
dirtyPlayers.clear();
```

If another thread calls `markDirty(playerId)` between `copyOf` and `clear`, that player ID is cleared from `dirtyPlayers` but wasn't included in `toSave`. Their changes are silently dropped until the next save cycle -- if there is one. If this was the last modification, data is permanently lost.

### 2.4 Debounced Save Overwrites Atomic DB Values

The debounced `syncSave()` writes the in-memory coin value via `INSERT ... ON DUPLICATE KEY UPDATE coins = VALUES(coins)`. But `atomicAddCoins` already wrote the authoritative value directly to the DB. If another process (admin command, another server) modified the DB between the atomic write and the debounced save, the stale in-memory value overwrites the correct DB value.

You have two competing persistence strategies fighting each other.

### 2.5 Ghost Recorder Timestamp Skew

```java
// GhostRecorder.java:182-186
final long timestampMs = now - recording.startTimeMs;  // calculated on SCHEDULED_EXECUTOR thread
world.execute(() -> {
    // position read happens later on world thread
    Vector3d position = transform.getPosition();
```

The timestamp is captured before `world.execute()`. If the world thread is busy (TPS drop, heavy tick), the actual position could be sampled 50-200ms after the timestamp says it was. Over a 30-second run at 20 samples/sec, accumulated skew makes the ghost replay drift from the original run path.

### 2.6 Check-Then-Act Race in spawnRobot

```java
// RobotManager.java:140-141
if (robots.containsKey(key)) {  // check
    return;
}
// <-- another thread could put the same key here
robots.put(key, state);  // act
```

`ConcurrentHashMap` is thread-safe per-operation, but `containsKey` + `put` is not atomic. Use `putIfAbsent`.

---

## 3. Database

### 3.1 No Transactions Anywhere

`resetPlayerProgress()` makes **4 separate DELETE statements** across 4 tables:

```java
// AscendPlayerStore.java:285-316
DELETE FROM ascend_player_maps WHERE player_uuid = ?
DELETE FROM ascend_player_summit WHERE player_uuid = ?
DELETE FROM ascend_player_skills WHERE player_uuid = ?
DELETE FROM ascend_player_achievements WHERE player_uuid = ?
```

No transaction. No rollback. If the server crashes after deleting maps but before deleting skills, the player has orphaned skill data referencing maps that no longer exist. Same problem in `performAscension()` which resets in-memory state without any DB transaction.

### 3.2 Reset Doesn't Delete Main Player Record

`deletePlayerDataFromDatabase()` deletes from 4 child tables but **never deletes from `ascend_players`** or `ascend_ghost_recordings`. After a "full reset", the player still has ghost recordings and a stale player row. The debounced save will eventually overwrite the player row with zeroed values, but there's a window where the player row has old coins + new (empty) map data.

### 3.3 Ghost Recordings Survive All Resets

No reset operation (elevation, summit, ascension, admin reset) clears ghost recordings. A player who resets progress still has their old ghost running. If the old ghost was optimized for a completely different multiplier/speed tier, the runner behavior post-reset is nonsensical.

### 3.4 Deprecated MySQL Syntax

`ON DUPLICATE KEY UPDATE coins = VALUES(coins)` -- the `VALUES()` function in `ON DUPLICATE KEY UPDATE` is deprecated since MySQL 8.0.20 (2020). Should use row alias syntax. This will eventually produce warnings or break on MySQL 9+.

---

## 4. Economy Edge Cases

### 4.1 Elevation Cost Cap Creates Exploit

```java
// AscendConstants.java:331
int cappedLevel = Math.min(Math.max(0, currentLevel), 1000);
```

`getElevationLevelUpCost` caps at level 1000. But `calculateElevationPurchase` doesn't cap total levels. A player with enough coins can buy past level 1000, and every subsequent level costs the same as level 1000. With exponential coin growth from multipliers, this means unbounded elevation at flat cost.

### 4.2 Infinite Loop Risk in calculateElevationPurchase

```java
// AscendConstants.java:368
while (true) {
    BigDecimal nextCost = getElevationLevelUpCost(level, costMultiplier);
    ...
}
```

If `costMultiplier` is zero or negative (code doesn't validate), `nextCost` could be zero or negative, and `newTotal.compareTo(availableCoins) > 0` never triggers. Infinite loop, server hangs.

### 4.3 Summit XP Uses double, Losing BigDecimal Precision

```java
// AscendConstants.java:462
double sqrtCoins = Math.sqrt(coins.doubleValue());
```

`BigDecimal.doubleValue()` loses precision beyond ~2^53 (~9 quadrillion). For a game where coins can reach trillions and the entire prestige system depends on precise coin-to-XP conversion, this silently produces wrong Summit XP at high coin amounts. You went to all the trouble of migrating to BigDecimal for coins, then threw away the precision at the most critical conversion point.

### 4.4 Passive Earnings Use Snapshot Multipliers

Passive earnings use **current multipliers** to calculate what runners earned during the offline period:

```java
// PassiveEarningsManager.java:123-124
BigDecimal payoutPerRun = playerStore.getCompletionPayout(
    playerId, allMaps, AscendConstants.MULTIPLIER_SLOTS, mapId, BigDecimal.ZERO
);
```

But those multipliers include the gains from `mapMultiplierGain` (line 116) which is also being calculated for the offline period. The multiplier was growing during the offline window, so early runs should have earned less than late runs. The current code applies the *final* multiplier to *all* theoretical runs, inflating earnings.

### 4.5 Passive Earnings Multiplier Applied to Both Coins AND Multiplier Gain

The 25% offline rate reduces both coin earnings and multiplier gains. But multiplier gain is a second-order effect -- the multiplier increases *future* earnings. Reducing multiplier gain by 75% reduces future earning potential far more than 75% because it compounds. The intended "25% penalty" is actually closer to 90%+ over long offline periods.

---

## 5. Entity Management

### 5.1 Reflection-Based Entity Extraction

```java
// RobotManager.java:248
for (String methodName : List.of("getFirst", "getLeft", "getKey", "first", "left")) {
    java.lang.reflect.Method method = pairResult.getClass().getMethod(methodName);
```

You're guessing method names via reflection because you don't know the return type of `npcPlugin.spawnNPC()`. Five method names tried in sequence, catching `NoSuchMethodException` as flow control. If Hypixel changes their Pair class, this silently returns null and every robot spawn fails with no error. At minimum, log which accessor worked so you know when it breaks.

### 5.2 O(n^2) Player-World Checks

`isPlayerInAscendWorld()` iterates ALL online players to find one UUID:

```java
// RobotManager.java:794
for (PlayerRef playerRef : Universe.get().getPlayers()) {
    if (playerRef != null && playerId.equals(playerRef.getUuid())) {
```

This is called from `refreshRobots()` which already iterates all players. And `isPlayerNearMapSpawn()` does the exact same iteration. With 100 players and 5 maps each, `refreshRobots` does ~500+ full player-list scans per cycle.

### 5.3 Orphan File is a Single Point of Failure

`runner_uuids.txt` is the only mechanism to clean up orphaned entities across restarts. If the file write fails (disk full, permission error, crash during write), orphaned NPCs persist in the world forever, eating memory and confusing players. There's no periodic in-world scan to catch entities that slip through.

### 5.4 File Path Inconsistency

```java
// ParkourAscendPlugin.java:104
File modsFolder = new File("run/mods/Parkour");

// RobotManager.java:862
return Path.of("mods", "Parkour", RUNNER_UUIDS_FILE);
```

One uses `run/mods/Parkour`, the other uses `mods/Parkour`. These resolve to different directories depending on the server's working directory. If the server launches from the `run/` directory, the whitelist goes to `run/run/mods/Parkour/` and the runner UUIDs go to `run/mods/Parkour/`.

---

## 6. Dead Code & Unused Features

### 6.1 Dead Teleport Methods

`RobotManager` has three teleport methods:
- `teleportNpc()` (line 437) -- **never called**
- `teleportNpcWithRotation()` (line 457) -- **never called**
- `teleportNpcWithRecordedRotation()` (line 487) -- the only one actually used

Two entire methods rotting in the codebase.

### 6.2 Unused Constants

```java
RUNNER_BASE_SPEED = 5.0          // never used (line 56 & 290)
RUNNER_JUMP_FORCE = 8.0          // never used (line 291)
WAYPOINT_REACH_DISTANCE = 1.5    // never used (line 292)
RUNNER_DEFAULT_JUMP_HEIGHT        // never used (line 295)
RUNNER_JUMP_CLEARANCE             // never used (line 296)
RUNNER_JUMP_DISTANCE_FACTOR       // never used (line 297)
RUNNER_JUMP_DISTANCE_THRESHOLD    // never used (line 298)
RUNNER_JUMP_AUTO_UP_THRESHOLD     // never used (line 300)
RUNNER_JUMP_AUTO_DOWN_THRESHOLD   // never used (line 301)
RUNNER_JUMP_AUTO_HORIZ_THRESHOLD  // never used (line 302)
MAP_RUNNER_PRICES[]               // documented as "NOT USED" (line 86)
```

11+ unused constants. Looks like remnants of a removed waypoint-based movement system that was replaced by ghost replay. Clean it up.

### 6.3 Skill Tree Nodes Without Effects

- **`MANUAL_T4_RUNNER_BOOST`**: `AscensionManager.hasRunnerBoost()` exists but is **never called** anywhere. The skill does nothing.
- **`MANUAL_T5_PERSONAL_BEST`**: `AscensionManager.hasPersonalBestTracking()` exists but is **never called** during run completion. The skill does nothing.
- **`EVOLUTION_POWER`**: Summit category with a bonus formula (`10 + 0.5 * level^0.8`). `getEvolutionPowerBonus()` exists in SummitManager. **Never called**. Players can invest Summit XP into a stat that does absolutely nothing.

Players are spending skill points and Summit XP on effects that literally don't exist in the game. That's not a bug, that's fraud.

### 6.4 Deprecated Methods Still Present

- `getElevationMultiplier()` (deprecated, delegates to `getElevationLevel()`)
- `addElevationMultiplier()` (deprecated, delegates to `addElevationLevel()`)
- `addSummitLevel()` (deprecated, converts to XP)
- `getElevationCost()` (deprecated, hardcoded to 1000)

These suggest a messy migration history. Either callers have been updated (delete the methods) or they haven't (fix the callers).

---

## 7. Run Tracker Issues

### 7.1 Walk-On Start Has No Cooldown

```java
// AscendRunTracker.java:363
for (AscendMap map : mapStore.listMaps()) {
```

`checkWalkOnStart` runs every 200ms tick. If the player is standing on a map start and currently has no active/pending run, it calls `setPendingRun` every tick. `setPendingRun` shows runners, hides runners, cancels ghost recording, and sends a chat message. Every 200ms. The player gets spammed with "Ready: MapName - Move to start!" messages.

Actually, after setting a pending run, the next tick enters the `pendingRun != null` branch and skips `checkWalkOnStart`. So the spam only happens if something repeatedly clears the pending run. But if the pending run is cleared (e.g., by another system) and the player hasn't moved, it re-triggers. No explicit debounce.

### 7.2 Map Origin (0,0,0) Check

```java
// AscendRunTracker.java:368
if (map.getStartX() == 0 && map.getStartY() == 0 && map.getStartZ() == 0) {
    continue;
}
```

Using (0,0,0) as a sentinel for "no start configured" means you can never place a map start at the world origin. Floating-point equality comparison on doubles is also fragile.

### 7.3 completeRun Doesn't Verify World Thread

`completeRun` directly calls `store.addComponent(ref, Teleport...)`:

```java
// AscendRunTracker.java:327-328
store.addComponent(ref, Teleport.getComponentType(),
    new Teleport(store.getExternalData().getWorld(), startPos, startRot));
```

This works because `checkPlayer` is called inside `CompletableFuture.runAsync(..., world)` from `tickRunTracker`. But there's no assertion or documentation enforcing this. If anyone calls `checkPlayer` from a non-world thread (future refactor, test, etc.), it silently corrupts entity state.

### 7.4 No Ghost Sample Cap

`GhostRecorder` has no maximum sample limit. A player who starts a run and never finishes it accumulates samples at 20/sec indefinitely. Over 10 minutes that's 12,000 samples. Over an hour (AFK at start), 72,000 samples. The `ArrayList` grows unbounded.

---

## 8. Code Quality

### 8.1 Duplicated Reset Logic

`resetProgressForElevation()` and `resetProgressForSummit()` are ~95% identical. Both iterate map progress, reset unlocks/multipliers/robots, keep first map unlocked. The only difference is elevation clears `bestTimeMs` and summit doesn't. Extract a shared method.

### 8.2 BigDecimal Precision Overkill

`MathContext(30, RoundingMode.HALF_UP)` is used for every single arithmetic operation. 30 digits of precision for a game where coin displays truncate to 2 decimal places. This wastes CPU and memory on every BigDecimal operation across the entire economy tick loop (~60 times/second per robot).

### 8.3 Inconsistent Error Handling

- `RobotManager`: catches exceptions, logs warning, continues silently
- `AscendPlayerStore`: catches SQL exceptions, logs severe, re-adds to dirty set
- `GhostRecorder`: catches exceptions, logs warning with cause
- `AscendRunTracker.completeRun`: no try-catch at all -- if ghost recording fails, the whole completion explodes
- `ParkourAscendPlugin.setup`: catches exception on DB init, logs warning, continues as if nothing happened (all subsequent operations will NPE)

Pick a strategy and stick with it.

### 8.4 Magic Numbers

```java
// AscendRunTracker.java:44
private static final double FINISH_RADIUS_SQ = 2.25; // 1.5^2

// But then in the same file:
private static final long POST_COMPLETION_FREEZE_MS = 500; // named constant

// RobotManager.java:821
private static final double CHUNK_LOAD_DISTANCE = 128.0; // what determines this?

// ParkourAscendPlugin.java:489
ascendHudReadyAt.put(playerRef.getUuid(), System.currentTimeMillis() + 250L); // why 250?
```

Some magic numbers get named constants, others are inline with comments, others are just vibes.

---

## 9. Missing Edge Cases

| Scenario | What Happens | What Should Happen |
|----------|-------------|-------------------|
| Player disconnects mid-run | `cancelRun` called, ghost recording cancelled | OK, but ghost samples collected so far are leaked (ArrayList GC'd eventually) |
| Server crashes during `syncSave` | Data since last save lost, `dirtyPlayers` cleared | Should use write-ahead log or at minimum not clear dirty set on failure |
| Two players on overlapping map starts | First matching map wins per player | Could cause confusion; no priority system |
| Admin deletes a map while runner is active | `RobotManager.tickRobot` gets `null` from `mapStore.getMap`, returns silently | Runner NPC stays in world forever, never despawned |
| Player earns coins faster than DB can write | In-memory value diverges from DB; batch save overwrites DB with stale data | Atomic ops and batch save fight each other |
| `npcPlugin` becomes null after initialization | All robot spawns silently fail, existing robots stop respawning | Should detect and alert, or retry initialization |
| Player opens Ascend UI while in Hub world | UI renders but all data reads return defaults/nulls | No world-gate on UI page opening |
| Ghost recording has 0 samples (timing edge) | `GhostRecorder.stopRecording` logs warning, doesn't save | Runner for this map has no ghost, can't move, sits at start forever |
| Multiple rapid Summit/Ascension clicks | Each processes against current state, could double-spend | No optimistic locking or idempotency guard |
| Clock adjustment (NTP sync) during run | `System.currentTimeMillis()` jumps, run time becomes negative or huge | `Math.max(0L, elapsed)` masks it instead of detecting it |
| Passive earnings calculated before runners spawn on join | Passive earnings use snapshot of runner data which might not reflect current state | Calculate passive before spawning, or defer calculation |

---

## 10. Summary

The core game loop works. The economy math is mostly correct (BigDecimal migration was the right call). Ghost replay is a clever feature. But the codebase has:

- **3 concurrency bugs** that can cause data loss (dirty tracking race, atomic/batch save conflict, stale cache reads)
- **3 unimplemented skill effects** that players can invest progression currency into
- **11+ dead constants** from a removed feature
- **0 transactions** across any multi-table database operation
- **0 tests** (I assume, since there's no test directory)
- **1 reflection hack** that will break without warning on API updates

The architecture needs a real dependency injection pass. The persistence layer needs transactions and a reconciliation strategy between atomic ops and batch saves. And for the love of all that is holy, either implement `MANUAL_T4_RUNNER_BOOST`, `MANUAL_T5_PERSONAL_BEST`, and `EVOLUTION_POWER`, or remove them from the game before players notice they're spending currency on nothing.
