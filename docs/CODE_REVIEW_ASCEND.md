# Code Review: hyvexa-parkour-ascend

Senior dev review of the Ascend module. Organized by severity.

---

## Critical Issues

### 1. God Class: `ParkourAscendPlugin` (714 lines)

This class is the plugin entry point, event bus, HUD manager, inventory manager, tick scheduler, and service locator all rolled into one. It holds 14 fields of managers/stores, 3 `ConcurrentHashMap`s for HUD state, and has event handlers with inline business logic.

The `setup()` method (lines 98-303) is a 200-line sequential init block that's impossible to test in isolation. Every subsystem reaches back into it via `ParkourAscendPlugin.getInstance()`.

**Concrete problems:**
- If any manager init fails (e.g., `ghostStore.syncLoad()` throws), the remaining managers get null dependencies and the plugin is in an inconsistent half-initialized state. There's no rollback or health check.
- `tickRunTracker()` and `tickTimerUpdate()` both call `collectPlayersByWorld()` independently every 200ms and 50ms respectively. That's 25 iterations/sec of the full player list, each creating fresh `HashMap` + `ArrayList` instances.

### 2. Inconsistent Concurrency Model in `AscendPlayerProgress`

`coins`, `totalCoinsEarned`, `summitAccumulatedCoins`, and `MapProgress.multiplier` use `AtomicReference<BigDecimal>` for thread-safe updates. But `elevationMultiplier`, `ascensionCount`, `skillTreePoints`, `totalManualRuns`, `consecutiveManualRuns`, `robotSpeedLevel`, `robotStars`, and every boolean field are plain fields with no synchronization.

These fields are mutated from multiple threads:
- `RobotManager.tick()` on the scheduled executor reads speed levels and stars
- `AscendRunTracker.completeRun()` writes `totalManualRuns`, `consecutiveManualRuns`
- UI pages can trigger writes from world thread handlers

This is a data race. On x86 you'll likely never see corruption, but on ARM (or if the JIT gets creative) you could get stale reads or torn writes on the `Long` boxed fields (`ascensionStartedAt`, `fastestAscensionMs`).

### 3. Silent Schema Migration via `catch (SQLException ignored)` (AscendPlayerStore:167)

```java
try {
    progress.setAscensionCount(rs.getInt("ascension_count"));
    // ...15 more column reads...
    progress.setSeenTutorials(rs.getInt("seen_tutorials"));
} catch (SQLException ignored) {
    // Columns may not exist yet (old schema)
}
```

If *any* column read after `elevation_multiplier` throws (e.g., because a column was renamed, or there's a connection timeout mid-read), the entire block is silently swallowed. You lose `ascensionCount`, `skillTreePoints`, `totalCoinsEarned`, `totalManualRuns`, `activeTitle`, timers, passive earnings, tutorials -- all reset to defaults. The player's data looks like a fresh account.

This isn't migration handling. It's data loss with no logging.

### 4. Memory/DB Divergence in Atomic Operations

The pattern in `atomicAddCoins()` (line 500-540) is: update memory first, then write to DB, revert memory on failure. But between the memory update and the DB write, any concurrent reader (HUD tick, passive earnings calc, auto-upgrade check) sees the already-updated in-memory value.

If the DB write fails and reverts, those concurrent readers already acted on stale data. For `atomicSpendCoins()` the inverse applies: the DB updates first, then memory. So there's a window where the DB says coins were spent but in-memory still shows the old balance.

This dual-source-of-truth with no locking means the cache and DB can permanently diverge under load (e.g., if a DB timeout coincides with a runner completion batch).

---

## Significant Issues

### 5. Dead Code in `AscensionManager` (lines 136-197)

17 methods that all return hardcoded defaults (`0.0`, `1.0`, `false`, `20`). These were clearly a skill tree system that got stripped down to a single `AUTO_RUNNERS` node, but the empty shells remain. Every caller still calls these methods and processes the return values:

- `AscendRunTracker.completeRun()` computes `ascensionBonus`, `chainBonus`, `sessionBonus` from these dead methods, multiplies them through BigDecimal math, and builds UI strings for bonuses that are always zero.
- `RobotManager.tickRobot()` checks `hasDoubleLap()` which always returns false.

This is ~200 lines of dead logic across the codebase that runs on every manual completion and every robot tick.

### 6. O(n) Player Lookup by UUID (ParkourAscendPlugin:387-397)

```java
public PlayerRef getPlayerRef(UUID playerId) {
    for (PlayerRef playerRef : Universe.get().getPlayers()) {
        if (playerRef != null && playerId.equals(playerRef.getUuid())) {
            return playerRef;
        }
    }
    return null;
}
```

Linear scan of all online players. Called from `PassiveEarningsManager.checkPassiveEarningsOnJoin()`. Also `RobotManager.isPlayerInAscendWorld()` (line 819-842) and `isPlayerNearMapSpawn()` (line 852-884) both do the same linear scan pattern. `isPlayerInAscendWorld` is called once per robot per refresh cycle, and `refreshRobots` iterates all players * all maps. For 50 players with 5 maps each, that's 250 linear scans every second.

### 7. `calculateLevelFromXp()` is an Unbounded Loop (AscendConstants:508-520)

```java
public static int calculateLevelFromXp(long xp) {
    int level = 0;
    long cumulative = 0;
    while (true) {
        long nextLevelXp = getXpForLevel(level + 1);
        if (cumulative + nextLevelXp > xp) break;
        cumulative += nextLevelXp;
        level++;
    }
    return level;
}
```

With the XP formula being `level^2.0`, this loop runs ~O(sqrt(xp)) iterations. At very high XP values (long overflow territory from exploits/admin commands), this could spin for a long time. `getCumulativeXpForLevel()` has the same issue. Both are called from `SummitManager.previewSummit()`, HUD updates, and the Summit page -- hot paths.

A closed-form approximation or binary search would be constant-time.

### 8. Passive Earnings Calculated at Stale Multiplier State

`PassiveEarningsManager.calculateAndApplyPassiveEarnings()` uses the player's *current* multiplier to compute what their runners earned while they were offline. But while offline, the multiplier was lower (or at whatever state it was when they left). The calculation should use the multiplier state at the time the player disconnected, not the current state.

Since reconnection doesn't change multipliers before this method runs, it's "accidentally correct" most of the time. But if an admin grants coins or another system mutates state before this runs, the calculation would be wrong.

### 9. No Transaction for Ascension Reset (AscensionManager:42-83)

`performAscension()` does:
1. Mutates in-memory progress (coins, elevation, summit, map progress)
2. Calls `playerStore.markDirty()` to queue a debounced save

But `resetPlayerProgress()` in `AscendPlayerStore` cancels the pending save and does an immediate `DELETE` + fresh save. `performAscension()` does NOT use `resetPlayerProgress()` -- it manually clears fields and relies on the debounced upsert.

If the server crashes between the `markDirty()` and the debounced save (5 seconds later), the DB still has the pre-ascension data with 10^33 coins, but the skill point was already granted in memory and the in-memory map progress was cleared. On restart, the player has their old data *without* the ascension -- or worse, if partial saves happened, a mix of old and new state.

### 10. `extractEntityRef` via Reflection (RobotManager:241-262)

```java
for (String methodName : List.of("getFirst", "getLeft", "getKey", "first", "left")) {
    try {
        java.lang.reflect.Method method = pairResult.getClass().getMethod(methodName);
        Object value = method.invoke(pairResult);
        if (value instanceof Ref<?> ref) {
            return (Ref<EntityStore>) ref;
        }
    } catch (NoSuchMethodException ignored) {}
}
```

Brute-forcing 5 different accessor names via reflection on every NPC spawn because the return type of `NPCPlugin.spawnNPC()` isn't known at compile time. This works, but it's fragile (Hytale API update changes the Pair class = silent breakage), slow (reflection + exception control flow), and hard to debug.

---

## Design Concerns

### 11. `ParkourAscendPlugin.getInstance()` Used Everywhere

Almost every class reaches into the singleton to get its dependencies:
- `RobotManager.tickRobot()` → `ParkourAscendPlugin.getInstance().getSummitManager()`
- `RobotManager.calculateSpeedMultiplier()` (a `static` method) → `ParkourAscendPlugin.getInstance()`
- `AscendRunTracker.completeRun()` → `ParkourAscendPlugin.getInstance().getAchievementManager()`
- `PassiveEarningsManager` → `ParkourAscendPlugin.getInstance().getSummitManager()`

Dependencies should be injected via constructors, not pulled from a global. This makes testing impossible and couples everything to the plugin lifecycle.

### 12. Duplicated Inventory Clear/Set Logic

Three methods doing the same thing with slight variations:
- `resetAscendInventory()` (line 577-604) - clears all, sets 4 menu items + hub selector
- `giveRunItems()` (line 666-687) - clears all, sets reset + leave + cindercloth
- `giveMenuItems()` (line 689-713) - clears all, sets 4 menu items + hub selector (identical to `resetAscendInventory`)

`resetAscendInventory` and `giveMenuItems` are literally the same operation. Both clear every container and set the same 5 items. The clear-all-containers pattern is duplicated 3 times with the same 6 lines.

### 13. `SummitManager` Javadoc Disagrees with Code

The Javadoc on `getRunnerSpeedBonus()` says "Formula: 1 + 0.45 * sqrt(level)" but the actual implementation delegates to `SummitCategory.RUNNER_SPEED.getBonusForLevel(level)` which computes `1.0 + 0.15 * level` (linear, not sqrt). Similarly `getMultiplierGainBonus()` documents "1 + 0.5 * level^0.8" but the code does `1.0 + 0.30 * level`.

The Javadoc appears to be from an earlier formula iteration and was never updated.

### 14. `AscendPlayerStore` is 1474 Lines of Mixed Concerns

This single class handles:
- In-memory cache management
- Lazy loading from DB
- Debounced write-back
- 5 separate SQL load methods
- 1 giant batch save method
- 4 atomic SQL operations
- Full reset with DELETE cascade
- 40+ getter/setter pass-through methods
- Multiplier product calculation
- Completion payout calculation
- Map unlock logic
- Tutorial tracking

The multiplier/payout calculation especially doesn't belong in the store -- it's business logic, not data access.

### 15. Robot Key as String Concatenation (RobotManager:779-781)

```java
private String robotKey(UUID ownerId, String mapId) {
    return ownerId.toString() + ":" + mapId;
}
```

If a map ID ever contains `:`, keys collide. A record or proper composite key would be safer and avoid string allocation on every lookup.

---

## Minor Issues

### 16. Constants Duplication

`PassiveEarningsManager` re-declares `OFFLINE_RATE_PERCENT = 25`, `MAX_OFFLINE_TIME_MS`, and `MIN_AWAY_TIME_MS` as local constants despite identical values existing in `AscendConstants.PASSIVE_*`.

### 17. `CALC_CTX` Declared in Two Places

`AscendConstants` and `AscendPlayerStore` both declare `private static final MathContext CALC_CTX = new MathContext(30, RoundingMode.HALF_UP)`. Should be a single shared constant.

### 18. Deprecated Methods Without Removal Timeline

`getElevationMultiplier()`, `addElevationMultiplier()`, `addSummitLevel()`, `clearSummitLevels()` are all `@Deprecated` but actively called. No `@deprecated since` or removal plan. If they're not going away, remove the annotation. If they are, fix the callers.

### 19. `checkWalkOnStart` Treats (0,0,0) as "No Start Set" (AscendRunTracker:403)

```java
if (map.getStartX() == 0 && map.getStartY() == 0 && map.getStartZ() == 0) {
    continue;
}
```

A map with its start at world origin would be silently skipped.

### 20. `SummitPreview.coinsToSpend` Uses `double` (SummitManager:69)

```java
coins.doubleValue()
```

The coins field is `BigDecimal` everywhere else precisely because it can exceed `double` precision. Truncating to double in the preview data class loses precision for display.

### 21. Unused `startPosArr` (RobotManager:658)

```java
Vector3d startPos = new Vector3d(map.getStartX(), map.getStartY(), map.getStartZ());
double[] startPosArr = {map.getStartX(), map.getStartY(), map.getStartZ()};
```

`startPos` is constructed but never used. Only `startPosArr` is passed to the teleport method.

---

## What's Done Well

- **Lazy loading with cache eviction** -- loading players on-demand and evicting on disconnect is the right call for memory management. Most idle games load everything eagerly.
- **Debounced saves with dirty tracking** -- avoids hammering the DB on every multiplier increment while ensuring data isn't lost.
- **Atomic SQL for high-contention operations** -- the `coins = coins + ?` pattern prevents lost updates from concurrent runner completions.
- **Deferred orphan cleanup** -- queuing entity removals during ECS ticks and processing them outside avoids the store-locked IllegalStateException. This is a non-obvious Hytale-specific gotcha handled correctly.
- **Ghost-based runner movement** -- using actual recorded player paths for NPC replay is far more engaging than waypoint navigation.
- **Runner UUID persistence across restarts** -- saving entity UUIDs to file for cleanup on next boot prevents entity leaks across server restarts.
- **Teleport-based freeze** -- clever solution for post-completion freeze without needing a state component.

---

*Reviewed: 2026-02-08*
