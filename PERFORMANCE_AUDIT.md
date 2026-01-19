# Performance Audit Report: Hyvexa Parkour Plugin

**Date:** 2026-01-19
**Scope:** Production stability analysis for long-running server with dozens of concurrent players

---

## Executive Summary

This plugin has **no obvious catastrophic flaws** but does have **several systemic patterns** that could cause memory growth, CPU spikes, or contribute to player disconnects under sustained load. The most critical issues center on **lifecycle cleanup reliability** and **hot-path object allocation**.

---

## CRITICAL: Memory Leak Potential

### 1. No Stale Player Cleanup

**Severity:** HIGH
**Impact:** Memory growth over long uptimes
**Category:** PLUGIN-LEVEL

The plugin relies **entirely** on `PlayerDisconnectEvent` to clean up per-player state. If this event fails to fire (network partition, client crash, engine bug), the following maps grow unbounded:

**HyvexaPlugin.java:106-114:**
```java
ConcurrentHashMap<UUID, RunHud> runHuds
ConcurrentHashMap<UUID, RunRecordsHud> runRecordHuds
ConcurrentHashMap<UUID, Boolean> runHudIsRecords
ConcurrentHashMap<UUID, Long> runHudReadyAt
ConcurrentHashMap<UUID, Boolean> runHudWasRunning
ConcurrentHashMap<UUID, ConcurrentLinkedDeque<Announcement>> announcements
ConcurrentHashMap<UUID, Long> playtimeSessionStart
ConcurrentHashMap<UUID, String> cachedRankNames
```

**RunTracker.java:44-45:**
```java
ConcurrentHashMap<UUID, ActiveRun> activeRuns
ConcurrentHashMap<UUID, FallState> idleFalls
```

**PlayerVisibilityManager.java:12:**
```java
ConcurrentHashMap<UUID, Set<UUID>> hiddenByViewer
```

**Recommendation:** Add a periodic sweep (every 60-300s) that checks these maps against `Universe.get().getPlayers()` and removes orphaned entries:

```java
private void sweepStalePlayerState() {
    Set<UUID> onlinePlayers = Universe.get().getPlayers().stream()
        .map(PlayerRef::getUuid).collect(Collectors.toSet());

    runHuds.keySet().removeIf(id -> !onlinePlayers.contains(id));
    runRecordHuds.keySet().removeIf(id -> !onlinePlayers.contains(id));
    runHudIsRecords.keySet().removeIf(id -> !onlinePlayers.contains(id));
    runHudReadyAt.keySet().removeIf(id -> !onlinePlayers.contains(id));
    runHudWasRunning.keySet().removeIf(id -> !onlinePlayers.contains(id));
    announcements.keySet().removeIf(id -> !onlinePlayers.contains(id));
    playtimeSessionStart.keySet().removeIf(id -> !onlinePlayers.contains(id));
    cachedRankNames.keySet().removeIf(id -> !onlinePlayers.contains(id));
    runTracker.sweepStalePlayers(onlinePlayers);
    PlayerVisibilityManager.get().sweepStalePlayers(onlinePlayers);
}
```

---

### 2. ProgressStore Unbounded Growth

**Severity:** MEDIUM
**Impact:** Memory growth over server lifetime
**Category:** PLUGIN-LEVEL

**ProgressStore.java:36-38:**
```java
Map<UUID, PlayerProgress> progress = new ConcurrentHashMap<>();
Map<UUID, String> lastKnownNames = new ConcurrentHashMap<>();
Map<String, LeaderboardCache> leaderboardCache = new ConcurrentHashMap<>();
```

Every unique player who has ever connected stays in memory forever. With thousands of unique players over months, this can become substantial (each `PlayerProgress` holds sets, maps, and primitives).

**Mitigation Options:**
- Implement an LRU eviction policy for inactive players (keep last N days of activity)
- Or accept this as intentional for historical data preservation, but monitor heap usage

---

## HIGH: Hot-Path Performance Issues

### 3. Map Iteration + Deep Copy Every 200ms

**Severity:** HIGH
**Impact:** CPU spikes, GC pressure
**Category:** PLUGIN-LEVEL

**RunTracker.java:149-160 - `findStartTriggerMap()`:**
```java
private Map findStartTriggerMap(Vector3d position) {
    for (Map map : mapStore.listMaps()) {  // Deep copies ALL maps
        TransformData trigger = map.getStartTrigger();
        if (trigger == null) continue;
        if (distanceSq(position, trigger) <= TOUCH_RADIUS_SQ) {
            return map;
        }
    }
    return null;
}
```

**MapStore.java:69-78 - `listMaps()`:**
```java
public List<Map> listMaps() {
    List<Map> copies = new ArrayList<>(this.maps.size());
    for (Map map : this.maps.values()) {
        Map copy = copyMap(map);  // Allocates new Map + TransformData objects
        // ...
    }
}
```

**Called from:** `tickMapDetection()` every 200ms, for **every player** not currently running.

**Impact calculation:** With 50 players idle and 30 maps with 10 checkpoints each:
- 50 × 30 = 1,500 Map objects copied
- 50 × 30 × ~8 TransformData = 12,000 TransformData objects
- **Every 200ms** = ~75,000 small object allocations/second

**Recommendations:**
1. Add a read-only iteration method that doesn't copy
2. Build a spatial index for trigger proximity checks
3. Or cache the trigger positions in a flat array for O(n) traversal without allocation

---

### 4. HUD Updates Every 100ms

**Severity:** MEDIUM
**Impact:** Network bandwidth, CPU
**Category:** PLUGIN-LEVEL

**HyvexaPlugin.java:159:**
```java
hudUpdateTask = scheduleTick("hud updates", this::tickHudUpdates, 100, 100, TimeUnit.MILLISECONDS);
```

Each HUD update creates `UICommandBuilder` objects and potentially sends network packets.

**RunHud.java:20-24 - Every update allocates:**
```java
public void updateText(String timeText) {
    UICommandBuilder commandBuilder = new UICommandBuilder();  // New allocation
    commandBuilder.set("#RunTimerText.Text", timeText);
    update(false, commandBuilder);  // Potentially sends network packet
}
```

With 50 players, that's 500 HUD update cycles per second. If the engine doesn't batch these network packets, this could saturate connections.

**Recommendation:** Consider 250-500ms update intervals for non-critical HUD elements, or implement dirty-checking to skip unchanged updates.

---

### 5. Leaderboard Data Allocation During Active Runs

**Severity:** MEDIUM
**Impact:** GC pressure during gameplay
**Category:** PLUGIN-LEVEL

**HyvexaPlugin.java:758-805 - `buildTopTimes()`:**
```java
private List<RunRecordsHud.RecordLine> buildTopTimes(String mapId, UUID playerId) {
    Map<UUID, Long> times = progressStore.getBestTimesForMap(mapId);  // New ConcurrentHashMap
    List<Map.Entry<UUID, Long>> entries = new ArrayList<>(times.entrySet());  // Another allocation
    entries.sort(...);  // Sort allocations
    List<RunRecordsHud.RecordLine> lines = new ArrayList<>();  // More allocations
    // ...
}
```

**ProgressStore.java:132-141 - `getBestTimesForMap()` allocates new map:**
```java
public Map<UUID, Long> getBestTimesForMap(String mapId) {
    Map<UUID, Long> times = new ConcurrentHashMap<>();  // Allocated every call
    for (Map.Entry<UUID, PlayerProgress> entry : progress.entrySet()) {
        // Iterates ALL players
    }
    return times;
}
```

Called every 100ms for every player with an active run. With many players competing, this iterates the entire progress store repeatedly.

**Recommendation:** Use the existing `leaderboardCache` infrastructure, or pre-compute top times when records change rather than on-demand.

---

## MEDIUM: Async/Threading Patterns

### 6. Unbatched World Thread Dispatch

**Category:** ENGINE-LEVEL concern, PLUGIN-LEVEL mitigation

**HyvexaPlugin.java:619-635 - `tickMapDetection()`:**
```java
private void tickMapDetection() {
    for (PlayerRef playerRef : Universe.get().getPlayers()) {
        // ...
        CompletableFuture.runAsync(() -> {
            runTracker.checkPlayer(ref, store);
            // ...
        }, world);  // Dispatches to world executor
    }
}
```

Each player tick dispatches a separate task to the world executor. If the engine's world executor is single-threaded (likely), these execute sequentially anyway but with task scheduling overhead.

**Recommendation:** Consider batching player checks into a single task per world:
```java
Map<World, List<PlayerRef>> playersByWorld = groupByWorld(players);
for (Map.Entry<World, List<PlayerRef>> entry : playersByWorld.entrySet()) {
    CompletableFuture.runAsync(() -> {
        for (PlayerRef ref : entry.getValue()) {
            checkPlayer(ref);
        }
    }, entry.getKey());
}
```

---

### 7. Blocking Disk I/O During Save

**Category:** PLUGIN-LEVEL

**ProgressStore.java:551-564 - `queueSave()`:**
```java
ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
    try {
        syncSave();  // Blocking file write
    } finally {
        // ...
    }
}, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
```

While debounced saves are good, `syncSave()` performs blocking I/O on the shared `SCHEDULED_EXECUTOR`. If the progress file grows large (thousands of players), this could block other scheduled tasks.

**Note:** This is likely acceptable at current scale but worth monitoring.

---

## ENGINE-LEVEL Risks

These issues cannot be definitively attributed to the plugin but could contribute to disconnects.

### 8. PlayerDisconnectEvent Reliability

**Question:** Does Hytale's `PlayerDisconnectEvent` fire reliably in all scenarios?
- Normal disconnect: Yes (assumed)
- Client crash/force quit: Unknown
- Network timeout: Unknown
- Server-initiated kick: Unknown

**If the engine sometimes fails to fire disconnect events**, your plugin will leak memory. The stale player sweep (Recommendation #1) protects against this.

### 9. Scheduled Executor Thread Pool

The plugin schedules 6 recurring tasks on `HytaleServer.SCHEDULED_EXECUTOR`:
- Map detection (200ms)
- HUD updates (100ms)
- Playtime tracking (60s)
- Collision removal (2s)
- Player count sampling (300s)
- Chat announcements (configurable)

**Unknown:** What is the thread pool size for this executor? If it's small and your tasks take too long, other engine tasks could be delayed.

### 10. HUD Manager Lifecycle

**HyvexaPlugin.java:871-875:**
```java
private void attachHud(PlayerRef playerRef, Player player, RunHud hud, boolean records) {
    runHudIsRecords.put(playerRef.getUuid(), records);
    player.getHudManager().setCustomHud(playerRef, hud);
    hud.show();
}
```

**Unknown:** Does `setCustomHud` properly dispose of the previous HUD? If not, switching between `RunHud` and `RunRecordsHud` could leak HUD references inside the engine.

---

## Disconnect Investigation Checklist

To determine if disconnects are plugin-caused or engine-caused:

### Plugin Metrics to Add

1. **Count active state maps** - Log size of `runHuds`, `activeRuns`, etc. periodically
2. **Measure tick durations** - Time how long `tickMapDetection()` and `tickHudUpdates()` take
3. **Track scheduled task health** - Log if scheduled tasks are running late or skipping

### Engine-Side Investigation

1. Check if disconnects correlate with specific player actions (starting runs, completing maps)
2. Monitor server CPU and memory during disconnect incidents
3. Check Hytale server logs for errors preceding disconnects
4. Determine if disconnects affect all players or specific players

---

## Priority Remediation Order

| Priority | Issue | Effort | Risk Reduction |
|----------|-------|--------|----------------|
| **P0** | Add stale player sweep | Low | High - Guarantees cleanup |
| **P1** | Avoid `listMaps()` deep copy in hot path | Medium | High - Major GC reduction |
| **P2** | Reduce HUD update frequency | Low | Medium - Less network/CPU |
| **P3** | Cache leaderboard data properly | Medium | Medium - Less GC during runs |
| **P4** | Batch world thread dispatches | Medium | Low-Medium - Cleaner execution |
| **P5** | Monitor progress store size | Low | Informational |

---

## Summary

**Most likely contributors to your production issues:**

1. **If you're seeing memory growth:** The lack of stale player cleanup and the aggressive object allocation in hot paths (`listMaps()` deep copy) are the primary suspects.

2. **If you're seeing intermittent disconnects:** The 100ms HUD update rate combined with per-player async task dispatch could be overwhelming either the network layer or the engine's internal queues. The plugin cannot directly cause network disconnects, but it can overload systems that do.

3. **What you can confidently rule out as plugin issues:**
   - Data persistence is well-handled (debounced saves, proper locks)
   - Thread safety looks correct (appropriate use of ConcurrentHashMap)
   - No obvious deadlock patterns
   - Clean shutdown sequence

4. **What requires engine investigation:**
   - Whether `PlayerDisconnectEvent` fires reliably in all edge cases
   - HUD system cleanup when switching HUD types
   - Scheduled executor thread pool behavior under load
