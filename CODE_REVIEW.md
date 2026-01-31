# Code Review: Hyvexa Plugin Suite

**Reviewer:** Senior Software Engineer
**Date:** 2026-01-31
**Verdict:** NOT READY FOR PRODUCTION

---

## Executive Summary

After a thorough review of this codebase, I must be direct: **this project has fundamental architectural problems that will cause production incidents**. While the feature set is impressive, the implementation suffers from:

- **God classes** exceeding 1,400 lines with 15+ responsibilities
- **Thread safety violations** throughout concurrent data structures
- **Silent failure patterns** that will lose player data
- **Copy-paste programming** instead of proper abstractions
- **Magic numbers everywhere** with zero documentation

This is not a maintainable codebase. It's a time bomb waiting for the right race condition.

---

## Critical Issues

### 1. God Classes Are Out of Control

| Class | Lines | Responsibilities | Verdict |
|-------|-------|------------------|---------|
| `HyvexaPlugin.java` | 1,402 | 17+ (stores, managers, holograms, events, scheduling) | UNACCEPTABLE |
| `RunTracker.java` | 1,379 | 12+ (runs, checkpoints, physics, sounds, teleports) | UNACCEPTABLE |
| `ProgressStore.java` | 925 | 8+ (progress, leaderboards, XP, caching, persistence) | NEEDS SPLIT |
| `AscendMapSelectPage.java` | 424 | UI + business logic + state management | NEEDS SPLIT |

**HyvexaPlugin.java** is the worst offender. It's doing:
- Store instantiation and lifecycle
- Event registration (190 lines of try-catch blocks)
- Hologram building and formatting
- Leaderboard string generation
- Player collision management
- Inventory synchronization
- World map disabling
- Scheduled task management (8 different futures)

This violates every principle of clean code. When everything lives in one class, everything breaks together.

```java
// Lines 108-161: 17 field dependencies in one class
private MapStore mapStore;
private RunTracker runTracker;
private ProgressStore progressStore;
private SettingsStore settingsStore;
private GlobalMessageStore globalMessageStore;
private PlayerCountStore playerCountStore;
private DuelMatchStore duelMatchStore;
private DuelPreferenceStore duelPreferenceStore;
private DuelStatsStore duelStatsStore;
private DuelQueue duelQueue;
private DuelTracker duelTracker;
private HudManager hudManager;
private AnnouncementManager announcementManager;
private PlayerPerksManager playerPerksManager;
private PlaytimeManager playtimeManager;
private PlayerCleanupManager playerCleanupManager;
private RunTrackerTickSystem runTrackerTickSystem;
```

**Recommendation:** This class needs to be broken into at least 6 separate classes: `HologramManager`, `InventorySyncManager`, `WorldMapManager`, `ScheduledTaskManager`, `EventRegistrar`, and a slim `HyvexaPlugin` that only wires dependencies.

---

### 2. Thread Safety Is Fundamentally Broken

#### 2.1 DatabaseManager Singleton Race Condition

**File:** `DatabaseManager.java:34-43, 48-97`

```java
private static final DatabaseManager INSTANCE = new DatabaseManager();
private HikariDataSource dataSource;  // MUTABLE, NOT VOLATILE
private DatabaseConfig config;         // MUTABLE, NOT VOLATILE

public void initialize() {
    config = DatabaseConfig.load();  // Thread-unsafe assignment
    initialize(config.getHost(), ...);
}
```

Multiple threads can call `initialize()` concurrently. There's no synchronization. The `dataSource` field can be replaced while active connections exist, causing connection pool leaks.

**This is a textbook example of unsafe publication.**

#### 2.2 RobotState Has Zero Synchronization

**File:** `RobotState.java:1-116`

```java
public class RobotState {
    private int currentWaypointIndex;  // Accessed from multiple threads
    private long runsCompleted;         // Accessed from multiple threads
    private int robotCount;             // Accessed from multiple threads

    public void incrementWaypoint() {
        this.currentWaypointIndex++;  // NOT ATOMIC
    }
}
```

This class is accessed from:
- Plugin tick thread (200ms intervals)
- RobotManager tick thread (200ms intervals)
- Command handlers

With zero synchronization. The `++` operator is NOT atomic. Race conditions are guaranteed.

#### 2.3 ProgressStore Double-Checked Locking Failure

**File:** `ProgressStore.java:708-717`

```java
private volatile long cachedTotalXp = -1L;

private long getCachedTotalXp(MapStore mapStore) {
    long cached = cachedTotalXp;
    if (cached >= 0L) return cached;
    cached = getTotalPossibleXp(mapStore);  // Two threads compute simultaneously
    cachedTotalXp = cached;
    return cached;
}
```

The `volatile` keyword prevents visibility issues but doesn't prevent duplicate computation. Under load, multiple threads will compute the same value.

#### 2.4 ConcurrentHashMap Misuse Pattern

Throughout the codebase, `ConcurrentHashMap` is used as if it provides atomicity for compound operations:

```java
// RunTracker.java - Pattern repeated everywhere
if (activeRuns.containsKey(playerId)) {
    return;  // TOCTOU: Time-of-check to time-of-use race
}
activeRuns.put(playerId, new ActiveRun(...));
```

`ConcurrentHashMap` only guarantees thread safety for individual operations. The check-then-act pattern above is inherently racy.

**Correct pattern:**
```java
activeRuns.computeIfAbsent(playerId, k -> new ActiveRun(...));
```

---

### 3. Error Handling Will Lose Player Data

#### 3.1 Silent Failures Are Everywhere

**File:** `HyvexaPlugin.java:247-435`

```java
this.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> {
    try {
        disablePlayerCollision(event.getPlayerRef());
    } catch (Exception e) {
        LOGGER.at(Level.WARNING).log("Exception: " + e.getMessage());
        // Stack trace LOST - you will never debug this
    }
});
```

This pattern repeats 7 times. Exception messages are logged, but stack traces are discarded. When something breaks in production, you'll have no idea where.

#### 3.2 Database Failures Are Swallowed

**File:** `ProgressStore.java:292-315`

```java
private boolean saveCompletion(UUID playerId, String mapId, long timeMs) {
    try (Connection conn = ...) {
        // ... execute SQL
        return true;
    } catch (SQLException e) {
        LOGGER.at(Level.WARNING).log("Failed to save: " + e.getMessage());
        return false;  // No retry, no queue, data LOST
    }
}
```

And in `RunTracker.java:649-651`, the return value is checked but the failure is not handled:

```java
boolean saved = progressStore.recordCompletion(...);
if (!saved) {
    player.sendMessage("Warning: save failed");  // Player warned, data still lost
}
```

**Players will lose their completion times.** This is unacceptable for a competitive parkour server.

#### 3.3 Schema Migration Failures Are Silent

**File:** `DatabaseManager.java:196-215`

```java
private void ensureCheckpointTimesTable() {
    try (Connection conn = getConnection()) {
        createPlayerCheckpointTimesTable(conn);
    } catch (SQLException e) {
        LOGGER.at(Level.WARNING).log("Failed to ensure table: " + e.getMessage());
        // Server continues with missing schema
    }
}
```

If table creation fails, the server keeps running. Later, when you try to insert into the missing table, you'll get cryptic SQL errors.

---

### 4. Copy-Paste Programming Is Rampant

#### 4.1 Map Unlock Logic Duplicated 3 Times

**File:** `AscendMapSelectPage.java`

The exact same 8-line block appears at:
- Lines 152-160
- Lines 326-334
- Lines 369-376

```java
boolean unlocked = map.getPrice() <= 0;
if (mapProgress != null && mapProgress.isUnlocked()) {
    unlocked = true;
}
if (map.getPrice() <= 0 && (mapProgress == null || !mapProgress.isUnlocked())) {
    playerStore.setMapUnlocked(playerRef.getUuid(), map.getId(), true);
    mapProgress = playerStore.getMapProgress(playerRef.getUuid(), map.getId());
    unlocked = true;
}
```

This is a maintenance nightmare. When the unlock logic changes, you must find and update all three copies. Someone will miss one.

#### 4.2 Argument Parsing Duplicated Across Commands

**Files:** `AscendCommand.java:79-102`, `AscendAdminCommand.java:118-141`

Identical 24-line method:

```java
private String[] getArgs(CommandContext ctx) {
    String input = ctx.getInputString();
    if (input == null || input.trim().isEmpty()) {
        return new String[0];
    }
    // ... 20 more identical lines
}
```

This should be a utility method in `hyvexa-core`.

#### 4.3 Checkpoint Detection Logic Not Extracted

**File:** `RunTracker.java:536-554`

The radius-based checkpoint detection pattern is used for:
- Start trigger detection
- Checkpoint detection
- Finish detection
- Leave trigger detection

Each implementation is slightly different but shares 80% of the logic. This should be a single `TriggerDetector` class.

---

### 5. Magic Numbers Plague the Codebase

I counted **50+ hardcoded constants** with no documentation. Examples:

```java
// HyvexaPlugin.java
private static final long STALE_PLAYER_SWEEP_SECONDS = 120L;  // Why 120?
private static final int LEADERBOARD_NAME_MAX = 16;           // Why 16?
private static final int MAP_HOLOGRAM_TOP_LIMIT = 5;          // Why 5?

// RunTracker.java
private static final double START_MOVE_THRESHOLD_SQ = 0.0025;  // What is this?
private static final long PING_DELTA_THRESHOLD_MS = 50L;       // Why 50?
private static final String CHECKPOINT_HUD_BG_FAST = "#1E4A7A"; // What color is this?

// HudManager.java
runHudReadyAt.putIfAbsent(uuid, System.currentTimeMillis() + 250L);  // Why 250ms?

// ProgressStore.java - Rank percentages with no explanation
if (percent >= 90.0) return 11;
if (percent >= 80.0) return 10;
if (percent >= 70.0) return 9;
// ... continues without any documentation
```

Every one of these numbers will cause confusion when someone needs to tune the system.

---

### 6. The Ascend Module Is Incomplete

The Ascend mode shipped with **incomplete implementations**:

#### 6.1 Robots Don't Actually Exist

**File:** `RobotManager.java:64-73`

```java
public void spawnRobot(UUID ownerId, String mapId) {
    RobotState state = new RobotState(ownerId, mapId);
    robots.put(key, state);
    // TODO: Actually spawn the entity in the world
    LOGGER.atInfo().log("Robot spawned for " + ownerId);
}
```

Robots are tracked in memory but **never rendered in the world**. The entire robot visual system is a stub.

#### 6.2 Waypoint Movement Is TODO

```java
public void despawnRobot(UUID ownerId, String mapId) {
    RobotState state = robots.remove(key);
    if (state != null && state.getEntityRef() != null) {
        // TODO: Remove entity from world
    }
}
```

The core Ascend feature—watching robots run maps—doesn't work.

---

### 7. Defensive Copying Is Pathological

**File:** `MapStore.java:481-510`

Every call to `listMaps()` triggers a deep copy of every map:

```java
public List<Map> listMaps() {
    fileLock.readLock().lock();
    try {
        List<Map> copies = new ArrayList<>(this.maps.size());
        for (Map map : this.maps.values()) {
            Map copy = copyMap(map);  // 30 lines of field-by-field copying
            if (copy != null) copies.add(copy);
        }
        return Collections.unmodifiableList(copies);
    } finally {
        fileLock.readLock().unlock();
    }
}
```

With 100 maps, each with 10 checkpoints, this creates 1,100 objects per call. Called every time the UI opens, every leaderboard query, every admin operation.

**Solution:** Return immutable views or accept controlled mutation risk.

---

### 8. SQL Injection Mitigation Is Inconsistent

**File:** `DatabaseManager.java:181-194`

```java
private int countRows(Connection conn, String table) throws SQLException {
    if (!ALLOWED_COUNT_TABLES.contains(table)) {
        throw new SQLException("Invalid table name");
    }
    // String concatenation despite whitelist
    try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT COUNT(*) FROM " + table)) {
        // ...
    }
}
```

Yes, there's a whitelist. But string concatenation in SQL statements sets a bad precedent. Someone will copy this pattern without the whitelist.

---

### 9. Reflection Bridge Is A Performance Problem

**File:** `HylogramsBridge.java:369-376`

```java
private Object invoke(String methodName, Class<?>[] paramTypes, Object... args) {
    try {
        Method method = handle.getClass().getMethod(methodName, paramTypes);
        return method.invoke(handle, args);
    } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Failed to invoke: " + methodName, e);
    }
}
```

Every single Hylograms API call performs:
1. A `getMethod()` lookup (expensive)
2. A reflective `invoke()` (expensive)

With no caching. If holograms update every 100ms, that's 10 reflection lookups per second per hologram.

**Solution:** Cache `Method` objects in a static map.

---

### 10. Tight Coupling Creates Circular Dependencies

```
HyvexaPlugin → ProgressStore → HyvexaPlugin
     ↓
RunTracker → HyvexaPlugin
     ↓
HudManager → ProgressStore, MapStore, RunTracker, DuelTracker, PlayerPerksManager
```

**File:** `RunTracker.java:289-293`

```java
HyvexaPlugin plugin = HyvexaPlugin.getInstance();
if (plugin != null && plugin.getDuelTracker() != null
        && plugin.getDuelTracker().isInMatch(playerRef.getUuid())) {
    return;
}
```

`RunTracker` cannot be unit tested without `HyvexaPlugin`. `ProgressStore` calls back into `HyvexaPlugin` to invalidate caches. This is a dependency graph nightmare.

**Solution:** Dependency injection. Pass interfaces, not singletons.

---

## Issue Summary

| Category | Count | Severity |
|----------|-------|----------|
| God Classes | 4 | CRITICAL |
| Thread Safety Violations | 12+ | CRITICAL |
| Silent Data Loss Risks | 6 | CRITICAL |
| Copy-Paste Code Blocks | 10+ | HIGH |
| Magic Numbers | 50+ | MEDIUM |
| Incomplete Features | 3 | HIGH |
| Performance Anti-Patterns | 4 | MEDIUM |
| Missing Null Checks | 15+ | MEDIUM |
| Tight Coupling | 5 | HIGH |

---

## Recommendations

### Immediate (Before Next Release)

1. **Add stack traces to all exception logging**
   ```java
   // Bad
   LOGGER.log("Error: " + e.getMessage());
   // Good
   LOGGER.log("Error processing player", e);
   ```

2. **Implement database retry logic** for all write operations

3. **Make `DatabaseManager.initialize()` thread-safe** with proper synchronization

4. **Extract common utilities** (argument parsing, unlock logic) to `hyvexa-core`

### Short-Term (Next 2 Weeks)

1. **Split `HyvexaPlugin`** into focused manager classes
2. **Add `volatile` or synchronization** to `RobotState` fields
3. **Use `computeIfAbsent`** instead of check-then-put patterns
4. **Document all magic numbers** and move to a constants file

### Medium-Term (Next Month)

1. **Implement dependency injection** to break singleton dependencies
2. **Add Method caching** to `HylogramsBridge`
3. **Replace deep copying** with immutable map views
4. **Complete the robot spawning system** or remove the feature

### Long-Term

1. **Write unit tests** - current code is untestable due to tight coupling
2. **Add integration tests** for database operations
3. **Implement proper transaction management** with rollback
4. **Consider a message queue** for async persistence with guaranteed delivery

---

## Conclusion

This codebase works by accident. It's held together by the JVM's generous handling of race conditions and the low traffic of early deployment. As player count grows, these issues will manifest as:

- Lost completion times
- Corrupted leaderboards
- Random NullPointerExceptions
- Mysterious "it worked yesterday" bugs

The developers clearly understand the domain and have built impressive features. But the engineering fundamentals—thread safety, error handling, separation of concerns—are severely lacking.

**My recommendation:** Freeze feature development and spend 2-3 weeks on technical debt. The alternative is firefighting production bugs indefinitely.

---

*"Any fool can write code that a computer can understand. Good programmers write code that humans can understand."* — Martin Fowler
