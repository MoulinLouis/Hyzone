# Code Review Fixes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix critical issues identified in CODE_REVIEW.md: thread safety, error handling, code extraction, magic numbers, and God class splitting.

**Architecture:** Incremental refactoring maintaining backward compatibility. Each task is self-contained and can be verified independently. Changes follow existing patterns and coding style.

**Tech Stack:** Java 17+, Hytale Server API, HikariCP, MySQL

---

## Phase 1: Thread Safety Fixes

### Task 1: Make DatabaseManager Thread-Safe

**Files:**
- Modify: `hyvexa-core/src/main/java/io/hyvexa/core/db/DatabaseManager.java`

**Step 1: Add synchronization to DatabaseManager**

Replace the mutable fields with proper synchronization:

```java
// Replace lines 34-36 with:
private static final DatabaseManager INSTANCE = new DatabaseManager();
private static final Object INIT_LOCK = new Object();
private volatile HikariDataSource dataSource;
private volatile DatabaseConfig config;
private volatile boolean initialized = false;
```

**Step 2: Make initialize() thread-safe**

Wrap initialization in synchronized block:

```java
// Replace initialize() method (lines 48-56) with:
public void initialize() {
    synchronized (INIT_LOCK) {
        if (initialized) {
            LOGGER.atWarning().log("DatabaseManager already initialized, skipping");
            return;
        }
        config = DatabaseConfig.load();
        LOGGER.atInfo().log("DB config loaded. Host=" + config.getHost()
                + " Port=" + config.getPort()
                + " Database=" + config.getDatabase()
                + " User=" + config.getUser());
        initialize(config.getHost(), config.getPort(), config.getDatabase(),
                   config.getUser(), config.getPassword());
        initialized = true;
    }
}
```

**Step 3: Make overloaded initialize() thread-safe**

```java
// Replace initialize(String, int, String, String, String) method (lines 61-97) with:
public void initialize(String host, int port, String database, String user, String password) {
    synchronized (INIT_LOCK) {
        if (dataSource != null && !dataSource.isClosed()) {
            LOGGER.atWarning().log("Closing existing connection pool before reinitializing");
            dataSource.close();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.at(Level.SEVERE).log("MySQL driver not found on classpath", e);
            throw new RuntimeException("MySQL driver missing", e);
        }

        // Connection pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000L);      // 5 minutes
        config.setConnectionTimeout(10000L); // 10 seconds
        config.setMaxLifetime(1800000L);     // 30 minutes

        // MySQL optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        try {
            dataSource = new HikariDataSource(config);
            LOGGER.atInfo().log("Database connection pool initialized successfully");
            ensureCheckpointTimesTable();
            ensureDuelEnabledColumn();
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("Failed to initialize database connection pool", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
}
```

**Step 4: Commit**

```bash
git add hyvexa-core/src/main/java/io/hyvexa/core/db/DatabaseManager.java
git commit -m "fix: make DatabaseManager initialization thread-safe"
```

---

### Task 2: Make RobotState Thread-Safe

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotState.java`

**Step 1: Add synchronization to RobotState**

Add volatile and AtomicLong/AtomicInteger for fields accessed from multiple threads:

```java
package io.hyvexa.ascend.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RobotState {

    private final UUID ownerId;
    private final String mapId;
    private volatile Ref<EntityStore> entityRef;
    private final AtomicInteger currentWaypointIndex = new AtomicInteger(0);
    private final AtomicLong lastTickMs = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong waypointReachedMs = new AtomicLong(0);
    private volatile boolean waiting;
    private final AtomicLong runsCompleted = new AtomicLong(0);
    private final AtomicInteger robotCount = new AtomicInteger(0);
    private final AtomicLong lastCompletionMs = new AtomicLong(0);

    public RobotState(UUID ownerId, String mapId) {
        this.ownerId = ownerId;
        this.mapId = mapId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getMapId() {
        return mapId;
    }

    public Ref<EntityStore> getEntityRef() {
        return entityRef;
    }

    public void setEntityRef(Ref<EntityStore> entityRef) {
        this.entityRef = entityRef;
    }

    public int getCurrentWaypointIndex() {
        return currentWaypointIndex.get();
    }

    public void setCurrentWaypointIndex(int index) {
        this.currentWaypointIndex.set(index);
    }

    public void incrementWaypoint() {
        this.currentWaypointIndex.incrementAndGet();
    }

    public long getLastTickMs() {
        return lastTickMs.get();
    }

    public void setLastTickMs(long lastTickMs) {
        this.lastTickMs.set(lastTickMs);
    }

    public long getWaypointReachedMs() {
        return waypointReachedMs.get();
    }

    public void setWaypointReachedMs(long ms) {
        this.waypointReachedMs.set(ms);
    }

    public boolean isWaiting() {
        return waiting;
    }

    public void setWaiting(boolean waiting) {
        this.waiting = waiting;
    }

    public long getRunsCompleted() {
        return runsCompleted.get();
    }

    public void incrementRunsCompleted() {
        this.runsCompleted.incrementAndGet();
    }

    public void addRunsCompleted(long amount) {
        if (amount > 0L) {
            this.runsCompleted.addAndGet(amount);
        }
    }

    public int getRobotCount() {
        return robotCount.get();
    }

    public void setRobotCount(int robotCount) {
        this.robotCount.set(Math.max(0, robotCount));
    }

    public long getLastCompletionMs() {
        return lastCompletionMs.get();
    }

    public void setLastCompletionMs(long lastCompletionMs) {
        this.lastCompletionMs.set(lastCompletionMs);
    }

    public void resetForNewRun() {
        this.currentWaypointIndex.set(0);
        this.waypointReachedMs.set(0);
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotState.java
git commit -m "fix: make RobotState fields thread-safe with atomics"
```

---

### Task 3: Fix ProgressStore Cache Race Condition

**Files:**
- Modify: `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/ProgressStore.java`

**Step 1: Replace double-checked locking with proper synchronization**

Find the `getCachedTotalXp` method and replace it:

```java
// Replace the cachedTotalXp field and getCachedTotalXp method with:
private final AtomicLong cachedTotalXp = new AtomicLong(-1L);

private long getCachedTotalXp(MapStore mapStore) {
    long cached = cachedTotalXp.get();
    if (cached >= 0L) {
        return cached;
    }
    long computed = getTotalPossibleXp(mapStore);
    cachedTotalXp.compareAndSet(-1L, computed);
    return cachedTotalXp.get();
}

public void invalidateTotalXpCache() {
    cachedTotalXp.set(-1L);
}
```

**Step 2: Add AtomicLong import if not present**

```java
import java.util.concurrent.atomic.AtomicLong;
```

**Step 3: Commit**

```bash
git add hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/ProgressStore.java
git commit -m "fix: use AtomicLong.compareAndSet for total XP cache"
```

---

## Phase 2: Error Handling Improvements

### Task 4: Add Stack Traces to Exception Logging in HyvexaPlugin

**Files:**
- Modify: `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java`

**Step 1: Fix all exception logging to include stack traces**

Search for patterns like `e.getMessage()` and replace with proper logging:

```java
// Replace all patterns like:
// LOGGER.at(Level.WARNING).log("Exception in X: " + e.getMessage());
// With:
// LOGGER.at(Level.WARNING).withCause(e).log("Exception in X");

// Line 251: Replace
LOGGER.at(Level.WARNING).log("Exception in PlayerConnectEvent (collision): " + e.getMessage());
// With:
LOGGER.at(Level.WARNING).withCause(e).log("Exception in PlayerConnectEvent (collision)");

// Line 258:
LOGGER.at(Level.WARNING).withCause(e).log("Exception in PlayerConnectEvent (count)");

// Line 265:
LOGGER.at(Level.WARNING).withCause(e).log("Exception in PlayerReadyEvent (collision)");

// Line 286:
LOGGER.at(Level.WARNING).withCause(e).log("Exception in PlayerConnectEvent (hud/playtime)");

// Line 328:
LOGGER.at(Level.WARNING).withCause(e).log("Exception in PlayerReadyEvent (inventory)");

// Line 335:
LOGGER.at(Level.WARNING).withCause(e).log("Exception in AddPlayerToWorldEvent");

// Line 342:
LOGGER.at(Level.WARNING).withCause(e).log("Exception in PlayerConnectEvent (broadcast)");

// Line 357:
LOGGER.at(Level.WARNING).withCause(e).log("Exception in PlayerDisconnectEvent");

// Line 370:
LOGGER.at(Level.WARNING).withCause(e).log("Exception in LivingEntityInventoryChangeEvent");

// Line 433:
LOGGER.at(Level.WARNING).withCause(e).log("Exception in PlayerChatEvent");

// Line 570:
LOGGER.at(Level.WARNING).withCause(e).log("Failed to update map leaderboard hologram");

// Line 611:
LOGGER.at(Level.WARNING).withCause(e).log("Failed to refresh leaderboard hologram");

// Line 636:
LOGGER.at(Level.WARNING).withCause(e).log("Failed to update leaderboard hologram");

// Line 1227:
LOGGER.at(Level.SEVERE).withCause(error).log("Tick task failed (" + name + ")");
```

**Step 2: Commit**

```bash
git add hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java
git commit -m "fix: include stack traces in exception logging"
```

---

### Task 5: Add Database Retry Logic

**Files:**
- Create: `hyvexa-core/src/main/java/io/hyvexa/core/db/DatabaseRetry.java`

**Step 1: Create retry utility class**

```java
package io.hyvexa.core.db;

import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.logging.Level;

/**
 * Utility for retrying database operations with exponential backoff.
 */
public final class DatabaseRetry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Default number of retry attempts */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** Default initial delay between retries in milliseconds */
    public static final long DEFAULT_INITIAL_DELAY_MS = 100L;

    /** Maximum delay between retries in milliseconds */
    public static final long MAX_DELAY_MS = 5000L;

    private DatabaseRetry() {
    }

    /**
     * Execute a database operation with retry logic.
     *
     * @param operation The operation to execute
     * @param operationName Human-readable name for logging
     * @param <T> Return type
     * @return The result of the operation
     * @throws SQLException if all retries fail
     */
    public static <T> T executeWithRetry(Callable<T> operation, String operationName) throws SQLException {
        return executeWithRetry(operation, operationName, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_DELAY_MS);
    }

    /**
     * Execute a database operation with retry logic.
     *
     * @param operation The operation to execute
     * @param operationName Human-readable name for logging
     * @param maxRetries Maximum number of retry attempts
     * @param initialDelayMs Initial delay between retries
     * @param <T> Return type
     * @return The result of the operation
     * @throws SQLException if all retries fail
     */
    public static <T> T executeWithRetry(Callable<T> operation, String operationName,
                                          int maxRetries, long initialDelayMs) throws SQLException {
        SQLException lastException = null;
        long delay = initialDelayMs;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return operation.call();
            } catch (SQLException e) {
                lastException = e;
                if (attempt < maxRetries) {
                    LOGGER.at(Level.WARNING).log(operationName + " failed (attempt " + attempt
                            + "/" + maxRetries + "), retrying in " + delay + "ms: " + e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Retry interrupted", ie);
                    }
                    delay = Math.min(delay * 2, MAX_DELAY_MS);
                }
            } catch (Exception e) {
                throw new SQLException("Unexpected error during " + operationName, e);
            }
        }

        LOGGER.at(Level.SEVERE).log(operationName + " failed after " + maxRetries + " attempts");
        throw lastException;
    }

    /**
     * Execute a void database operation with retry logic.
     *
     * @param operation The operation to execute
     * @param operationName Human-readable name for logging
     * @throws SQLException if all retries fail
     */
    public static void executeWithRetryVoid(VoidCallable operation, String operationName) throws SQLException {
        executeWithRetry(() -> {
            operation.call();
            return null;
        }, operationName);
    }

    /**
     * Functional interface for void operations that can throw SQLException.
     */
    @FunctionalInterface
    public interface VoidCallable {
        void call() throws SQLException;
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-core/src/main/java/io/hyvexa/core/db/DatabaseRetry.java
git commit -m "feat: add DatabaseRetry utility for operation retries"
```

---

### Task 6: Apply Retry Logic to Critical Saves

**Files:**
- Modify: `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/ProgressStore.java`

**Step 1: Update saveCompletion to use retry logic**

```java
// Add import at top:
import io.hyvexa.core.db.DatabaseRetry;

// Replace saveCompletion method (around line 292) with:
private boolean saveCompletion(UUID playerId, String mapId, long timeMs) {
    if (!DatabaseManager.getInstance().isInitialized()) {
        return false;
    }

    String sql = """
        INSERT INTO player_completions (player_uuid, map_id, best_time_ms)
        VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE best_time_ms = LEAST(best_time_ms, VALUES(best_time_ms))
        """;

    try {
        DatabaseRetry.executeWithRetryVoid(() -> {
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                stmt.setString(2, mapId);
                stmt.setLong(3, timeMs);
                stmt.executeUpdate();
            }
        }, "save completion for " + playerId);
        return true;
    } catch (SQLException e) {
        LOGGER.at(Level.SEVERE).withCause(e).log("Failed to save completion after retries");
        return false;
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/ProgressStore.java
git commit -m "fix: add retry logic to completion saves"
```

---

## Phase 3: Code Extraction

### Task 7: Extract Command Argument Parsing to Core

**Files:**
- Create: `hyvexa-core/src/main/java/io/hyvexa/common/util/CommandUtils.java`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/AscendCommand.java`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/AscendAdminCommand.java`

**Step 1: Create CommandUtils**

```java
package io.hyvexa.common.util;

import com.hypixel.hytale.server.core.command.CommandContext;

/**
 * Utility methods for command argument parsing.
 */
public final class CommandUtils {

    private CommandUtils() {
    }

    /**
     * Parse command arguments from a CommandContext, stripping the command name.
     *
     * @param ctx The command context
     * @return Array of arguments (empty if none)
     */
    public static String[] getArgs(CommandContext ctx) {
        String input = ctx.getInputString();
        if (input == null || input.trim().isEmpty()) {
            return new String[0];
        }
        String[] tokens = input.trim().split("\\s+");
        if (tokens.length == 0) {
            return tokens;
        }
        String first = tokens[0];
        if (first.startsWith("/")) {
            first = first.substring(1);
        }
        String commandName = ctx.getCalledCommand().getName();
        if (first.equalsIgnoreCase(commandName)) {
            if (tokens.length == 1) {
                return new String[0];
            }
            String[] trimmed = new String[tokens.length - 1];
            System.arraycopy(tokens, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return tokens;
    }

    /**
     * Get a specific argument by index, or null if not present.
     *
     * @param args Arguments array
     * @param index Index to retrieve
     * @return The argument or null
     */
    public static String getArg(String[] args, int index) {
        if (args == null || index < 0 || index >= args.length) {
            return null;
        }
        return args[index];
    }

    /**
     * Get a specific argument by index, or a default value if not present.
     *
     * @param args Arguments array
     * @param index Index to retrieve
     * @param defaultValue Default value if not present
     * @return The argument or default value
     */
    public static String getArgOrDefault(String[] args, int index, String defaultValue) {
        String arg = getArg(args, index);
        return arg != null ? arg : defaultValue;
    }
}
```

**Step 2: Update AscendCommand to use CommandUtils**

```java
// Replace the getArgs method in AscendCommand.java with:
import io.hyvexa.common.util.CommandUtils;

// Then replace all calls to getArgs(ctx) with CommandUtils.getArgs(ctx)
// And remove the private getArgs method from the class
```

**Step 3: Update AscendAdminCommand to use CommandUtils**

```java
// Same as above - import CommandUtils and replace getArgs calls
```

**Step 4: Commit**

```bash
git add hyvexa-core/src/main/java/io/hyvexa/common/util/CommandUtils.java
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/AscendCommand.java
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/AscendAdminCommand.java
git commit -m "refactor: extract command argument parsing to CommandUtils"
```

---

### Task 8: Extract Map Unlock Logic

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/util/MapUnlockHelper.java`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java`

**Step 1: Create MapUnlockHelper**

```java
package io.hyvexa.ascend.util;

import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;

import java.util.UUID;

/**
 * Helper for map unlock logic to avoid duplication.
 */
public final class MapUnlockHelper {

    private MapUnlockHelper() {
    }

    /**
     * Result of checking/ensuring map unlock status.
     */
    public static final class UnlockResult {
        public final boolean unlocked;
        public final AscendPlayerProgress.MapProgress mapProgress;

        public UnlockResult(boolean unlocked, AscendPlayerProgress.MapProgress mapProgress) {
            this.unlocked = unlocked;
            this.mapProgress = mapProgress;
        }
    }

    /**
     * Check if a map is unlocked for a player, auto-unlocking free maps.
     *
     * @param playerId The player UUID
     * @param map The map to check
     * @param playerStore The player store
     * @return UnlockResult with status and updated progress
     */
    public static UnlockResult checkAndEnsureUnlock(UUID playerId, AscendMap map, AscendPlayerStore playerStore) {
        if (playerId == null || map == null || playerStore == null) {
            return new UnlockResult(false, null);
        }

        AscendPlayerProgress.MapProgress mapProgress = playerStore.getMapProgress(playerId, map.getId());
        boolean unlocked = map.getPrice() <= 0;

        if (mapProgress != null && mapProgress.isUnlocked()) {
            unlocked = true;
        }

        // Auto-unlock free maps
        if (map.getPrice() <= 0 && (mapProgress == null || !mapProgress.isUnlocked())) {
            playerStore.setMapUnlocked(playerId, map.getId(), true);
            mapProgress = playerStore.getMapProgress(playerId, map.getId());
            unlocked = true;
        }

        return new UnlockResult(unlocked, mapProgress);
    }

    /**
     * Check if a map is unlocked (without auto-unlock).
     *
     * @param mapProgress The map progress (may be null)
     * @param map The map
     * @return true if unlocked
     */
    public static boolean isUnlocked(AscendPlayerProgress.MapProgress mapProgress, AscendMap map) {
        if (map == null) {
            return false;
        }
        if (map.getPrice() <= 0) {
            return true;
        }
        return mapProgress != null && mapProgress.isUnlocked();
    }
}
```

**Step 2: Update AscendMapSelectPage to use MapUnlockHelper**

Replace the duplicated unlock logic blocks with calls to `MapUnlockHelper.checkAndEnsureUnlock()`.

**Step 3: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/util/MapUnlockHelper.java
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java
git commit -m "refactor: extract map unlock logic to MapUnlockHelper"
```

---

## Phase 4: Reflection Caching

### Task 9: Add Method Caching to HylogramsBridge

**Files:**
- Modify: `hyvexa-core/src/main/java/io/hyvexa/common/util/HylogramsBridge.java`

**Step 1: Add method cache**

```java
// Add at class level after line 22:
private static final java.util.concurrent.ConcurrentHashMap<String, Method> METHOD_CACHE =
    new java.util.concurrent.ConcurrentHashMap<>();

// Add import:
import java.util.concurrent.ConcurrentHashMap;
```

**Step 2: Create cached method lookup helper**

```java
// Add new method after resolveHylogramsClassLoader():
private static Method getCachedMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
    String cacheKey = clazz.getName() + "#" + methodName + "#" + java.util.Arrays.toString(paramTypes);
    return METHOD_CACHE.computeIfAbsent(cacheKey, k -> {
        try {
            return clazz.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Method not found: " + methodName, e);
        }
    });
}
```

**Step 3: Update invoke methods in Hologram and HologramBuilder classes**

```java
// In Hologram class, replace invoke method (around line 369):
private Object invoke(String methodName, Class<?>[] paramTypes, Object... args) {
    try {
        Method method = getCachedMethod(handle.getClass(), methodName, paramTypes);
        return method.invoke(handle, args);
    } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Failed to invoke Hylograms hologram method: " + methodName, e);
    }
}

// In HologramBuilder class, replace invoke method (around line 436):
private Object invoke(String methodName, Class<?>[] paramTypes, Object... args) {
    try {
        Method method = getCachedMethod(handle.getClass(), methodName, paramTypes);
        return method.invoke(handle, args);
    } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Failed to invoke Hylograms builder method: " + methodName, e);
    }
}
```

**Step 4: Update invokeStatic method**

```java
// Replace invokeStatic method (around line 446):
private static Object invokeStatic(String methodName, Class<?>[] paramTypes, Object... args) {
    Class<?> apiClass = resolveApiClass();
    try {
        Method method = getCachedMethod(apiClass, methodName, paramTypes);
        return method.invoke(null, args);
    } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Failed to invoke Hylograms API method: " + methodName, e);
    }
}
```

**Step 5: Commit**

```bash
git add hyvexa-core/src/main/java/io/hyvexa/common/util/HylogramsBridge.java
git commit -m "perf: cache reflection Method objects in HylogramsBridge"
```

---

## Phase 5: Constants Documentation

### Task 10: Create Documented Constants File

**Files:**
- Create: `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ParkourTimingConstants.java`
- Modify: `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java`

**Step 1: Create ParkourTimingConstants**

```java
package io.hyvexa.parkour;

/**
 * Timing and interval constants for the parkour plugin.
 * All values are documented with their purpose and rationale.
 */
public final class ParkourTimingConstants {

    private ParkourTimingConstants() {
    }

    // === Scheduled Task Intervals ===

    /**
     * Interval for HUD updates in milliseconds.
     * Set to 100ms for smooth timer display without excessive CPU usage.
     */
    public static final long HUD_UPDATE_INTERVAL_MS = 100L;

    /**
     * Interval for playtime accumulation in seconds.
     * Set to 60s as a balance between accuracy and database write frequency.
     */
    public static final long PLAYTIME_TICK_INTERVAL_SECONDS = 60L;

    /**
     * Interval for player collision removal re-application in seconds.
     * Set to 2s to ensure collision stays disabled even after respawns.
     */
    public static final long COLLISION_REMOVAL_INTERVAL_SECONDS = 2L;

    /**
     * Interval for stale player cleanup sweep in seconds.
     * Set to 120s (2 minutes) to avoid frequent HashMap iterations.
     */
    public static final long STALE_PLAYER_SWEEP_INTERVAL_SECONDS = 120L;

    /**
     * Interval for teleport debug logging in seconds.
     * Set to 120s to aggregate teleport stats without flooding logs.
     */
    public static final long TELEPORT_DEBUG_INTERVAL_SECONDS = 120L;

    /**
     * Interval for duel tick processing in milliseconds.
     * Set to 100ms for responsive duel state updates.
     */
    public static final long DUEL_TICK_INTERVAL_MS = 100L;

    /**
     * Delay before refreshing leaderboard hologram on startup in seconds.
     * Set to 2s to allow world and player data to initialize first.
     */
    public static final long LEADERBOARD_HOLOGRAM_REFRESH_DELAY_SECONDS = 2L;

    // === HUD Display Constants ===

    /**
     * Delay before HUD becomes ready after attachment in milliseconds.
     * Set to 250ms to allow UI elements to initialize before updates.
     */
    public static final long HUD_READY_DELAY_MS = 250L;

    /**
     * Duration to show checkpoint split overlay in milliseconds.
     * Set to 2500ms (2.5s) for readable split time display.
     */
    public static final long CHECKPOINT_SPLIT_HUD_DURATION_MS = 2500L;

    // === Run Detection Constants ===

    /**
     * Squared threshold for detecting player movement from start position.
     * Set to 0.0025 (0.05 blocks) to detect minimal movement while ignoring jitter.
     */
    public static final double START_MOVE_THRESHOLD_SQ = 0.0025;

    /**
     * Threshold for warning about start/finish ping delta in milliseconds.
     * Set to 50ms as the acceptable precision for competitive timing.
     */
    public static final long PING_DELTA_THRESHOLD_MS = 50L;

    /**
     * Duration to keep disconnected player runs before expiry in milliseconds.
     * Set to 30 minutes to allow reconnection without losing run state.
     */
    public static final long OFFLINE_RUN_EXPIRY_MS = 30L * 60L * 1000L;

    /**
     * Cooldown between missing-checkpoint warnings at finish in milliseconds.
     * Set to 2000ms to avoid spamming the player.
     */
    public static final long FINISH_WARNING_COOLDOWN_MS = 2000L;

    // === Leaderboard Display Constants ===

    /**
     * Number of entries to show in the global leaderboard hologram.
     */
    public static final int LEADERBOARD_HOLOGRAM_ENTRIES = 10;

    /**
     * Maximum character width for player names in leaderboard display.
     */
    public static final int LEADERBOARD_NAME_MAX = 16;

    /**
     * Character width for position column in leaderboard display.
     */
    public static final int LEADERBOARD_POSITION_WIDTH = 4;

    /**
     * Character width for count column in leaderboard display.
     */
    public static final int LEADERBOARD_COUNT_WIDTH = 4;

    /**
     * Number of entries to show in per-map leaderboard holograms.
     */
    public static final int MAP_HOLOGRAM_TOP_LIMIT = 5;

    /**
     * Maximum character width for names in map leaderboard display.
     */
    public static final int MAP_HOLOGRAM_NAME_MAX = 16;

    /**
     * Character width for position column in map leaderboard display.
     */
    public static final int MAP_HOLOGRAM_POS_WIDTH = 3;
}
```

**Step 2: Update HyvexaPlugin to use the constants**

Replace hardcoded values with references to `ParkourTimingConstants`.

**Step 3: Commit**

```bash
git add hyvexa-parkour/src/main/java/io/hyvexa/parkour/ParkourTimingConstants.java
git add hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java
git commit -m "docs: extract and document timing constants"
```

---

## Phase 6: God Class Splitting

### Task 11: Extract HologramManager from HyvexaPlugin

**Files:**
- Create: `hyvexa-parkour/src/main/java/io/hyvexa/manager/HologramManager.java`
- Modify: `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java`

**Step 1: Create HologramManager class**

Extract all hologram-related methods from HyvexaPlugin into a new HologramManager class:
- `refreshLeaderboardHologram()`
- `updateLeaderboardHologram()`
- `buildLeaderboardHologramLines()`
- `buildLeaderboardRows()`
- `refreshMapLeaderboardHologram()`
- `updateMapLeaderboardHologramLines()`
- `buildMapLeaderboardHologramLines()`
- All formatting helper methods

**Step 2: Update HyvexaPlugin to delegate to HologramManager**

**Step 3: Commit**

```bash
git add hyvexa-parkour/src/main/java/io/hyvexa/manager/HologramManager.java
git add hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java
git commit -m "refactor: extract HologramManager from HyvexaPlugin"
```

---

### Task 12: Extract CollisionManager from HyvexaPlugin

**Files:**
- Create: `hyvexa-parkour/src/main/java/io/hyvexa/manager/CollisionManager.java`
- Modify: `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java`

**Step 1: Create CollisionManager**

```java
package io.hyvexa.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollision;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.CompletableFuture;

/**
 * Manages player collision state (disabling player-player collision).
 */
public class CollisionManager {

    public CollisionManager() {
    }

    /**
     * Disable collision for all online players.
     */
    public void disableAllCollisions() {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            disablePlayerCollision(playerRef);
        }
    }

    /**
     * Disable collision for a specific player.
     */
    public void disablePlayerCollision(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        disablePlayerCollision(playerRef.getReference());
    }

    /**
     * Disable collision for a specific entity reference.
     */
    public void disablePlayerCollision(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> store.tryRemoveComponent(ref, HitboxCollision.getComponentType()), world);
    }
}
```

**Step 2: Update HyvexaPlugin to use CollisionManager**

**Step 3: Commit**

```bash
git add hyvexa-parkour/src/main/java/io/hyvexa/manager/CollisionManager.java
git add hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java
git commit -m "refactor: extract CollisionManager from HyvexaPlugin"
```

---

### Task 13: Extract InventorySyncManager from HyvexaPlugin

**Files:**
- Create: `hyvexa-parkour/src/main/java/io/hyvexa/manager/InventorySyncManager.java`
- Modify: `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java`

**Step 1: Create InventorySyncManager**

Extract inventory synchronization methods:
- `syncRunInventoryOnConnect()`
- `syncRunInventoryOnReady()`
- `updateDropProtection()`
- `applyDropFilter()`

**Step 2: Update HyvexaPlugin to use InventorySyncManager**

**Step 3: Commit**

```bash
git add hyvexa-parkour/src/main/java/io/hyvexa/manager/InventorySyncManager.java
git add hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java
git commit -m "refactor: extract InventorySyncManager from HyvexaPlugin"
```

---

### Task 14: Extract WorldMapManager from HyvexaPlugin

**Files:**
- Create: `hyvexa-parkour/src/main/java/io/hyvexa/manager/WorldMapManager.java`
- Modify: `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java`

**Step 1: Create WorldMapManager**

```java
package io.hyvexa.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.CompletableFuture;

/**
 * Manages world map generation settings for players.
 * Disables world map on parkour servers to save memory.
 */
public class WorldMapManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final boolean disableWorldMap;

    public WorldMapManager(boolean disableWorldMap) {
        this.disableWorldMap = disableWorldMap;
    }

    /**
     * Disable world map generation for a player if configured.
     */
    public void disableWorldMapForPlayer(Ref<EntityStore> ref) {
        if (!disableWorldMap) {
            return;
        }
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null) {
                return;
            }
            WorldMapTracker tracker = player.getWorldMapTracker();
            if (tracker != null) {
                tracker.setViewRadiusOverride(0);
                tracker.clear();
                String name = playerRef != null ? playerRef.getUsername() : "unknown";
                LOGGER.atInfo().log("Disabled world map for player: " + name);
            }
        }, world);
    }
}
```

**Step 2: Update HyvexaPlugin to use WorldMapManager**

**Step 3: Commit**

```bash
git add hyvexa-parkour/src/main/java/io/hyvexa/manager/WorldMapManager.java
git add hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java
git commit -m "refactor: extract WorldMapManager from HyvexaPlugin"
```

---

### Task 15: Extract ChatFormatter from HyvexaPlugin

**Files:**
- Create: `hyvexa-parkour/src/main/java/io/hyvexa/manager/ChatFormatter.java`
- Modify: `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java`

**Step 1: Create ChatFormatter**

Extract the chat formatting logic from the PlayerChatEvent handler into a dedicated class.

**Step 2: Update HyvexaPlugin to use ChatFormatter**

**Step 3: Commit**

```bash
git add hyvexa-parkour/src/main/java/io/hyvexa/manager/ChatFormatter.java
git add hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java
git commit -m "refactor: extract ChatFormatter from HyvexaPlugin"
```

---

### Task 16: Final Cleanup and Update CHANGELOG

**Files:**
- Modify: `CHANGELOG.md`

**Step 1: Add changelog entry**

```markdown
- Refactored HyvexaPlugin to extract manager classes: HologramManager, CollisionManager, InventorySyncManager, WorldMapManager, ChatFormatter.
- Made DatabaseManager initialization thread-safe with proper synchronization.
- Made RobotState thread-safe using AtomicInteger and AtomicLong.
- Fixed ProgressStore total XP cache race condition with AtomicLong.compareAndSet.
- Added stack traces to all exception logging for better debugging.
- Added DatabaseRetry utility for automatic retry of database operations.
- Extracted CommandUtils to hyvexa-core for shared argument parsing.
- Extracted MapUnlockHelper for consistent unlock logic in Ascend UI.
- Added method caching to HylogramsBridge for better reflection performance.
- Created ParkourTimingConstants with documented timing values.
```

**Step 2: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs: update CHANGELOG with code review fixes"
```

---

## Summary

This plan addresses the following issues from CODE_REVIEW.md:

| Issue | Tasks |
|-------|-------|
| Thread Safety - DatabaseManager | Task 1 |
| Thread Safety - RobotState | Task 2 |
| Thread Safety - ProgressStore | Task 3 |
| Error Handling - Stack traces | Task 4 |
| Error Handling - Retry logic | Tasks 5, 6 |
| Copy-Paste Code - Command parsing | Task 7 |
| Copy-Paste Code - Map unlock | Task 8 |
| Reflection Performance | Task 9 |
| Magic Numbers | Task 10 |
| God Class - HyvexaPlugin | Tasks 11-15 |
| Documentation | Task 16 |

**NOT addressed (as requested):**
- Section 6: Ascend Module Incomplete Features (robot spawning, waypoint movement)

---

Plan complete and saved to `docs/plans/2026-01-31-code-review-fixes.md`. Two execution options:

**1. Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints

Which approach?
