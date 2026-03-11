# hyvexa-parkour Refactoring Plan

Verified issues only. False positives removed after source verification.

---

## 1. MedalStore: Replace ordinal-based indexing with EnumMap

**File:** `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/MedalStore.java`

**Problem:** `medal.ordinal()` used as array index at lines 183-184 and 206-210. If the `Medal` enum (5 values: BRONZE, SILVER, GOLD, EMERALD, INSANE) is reordered or gets new entries in the middle, all aggregated data silently maps to wrong medals.

**Plan:**
1. Change the aggregation data structure from `Map<UUID, int[]>` to `Map<UUID, EnumMap<Medal, Integer>>`
2. At line 183-184, replace:
   ```java
   // Before:
   counts[medal.ordinal()] = cnt;
   // After:
   counts.put(medal, cnt);
   ```
3. At lines 206-210, replace:
   ```java
   // Before:
   int[] counts = new int[Medal.values().length];
   // ...
   counts[m.ordinal()]++;
   // After:
   EnumMap<Medal, Integer> counts = new EnumMap<>(Medal.class);
   // ...
   counts.merge(m, 1, Integer::sum);
   ```
4. Update all consumers of the aggregated data to use `counts.getOrDefault(medal, 0)` instead of `counts[medal.ordinal()]`.

---

## 2. PlaytimeManager: Replace array capture with AtomicLong

**File:** `hyvexa-parkour/src/main/java/io/hyvexa/manager/PlaytimeManager.java`

**Problem:** Lines 57-68 use `long[] deltaMs = new long[1]` to capture a value from inside a `compute()` lambda. Non-idiomatic.

**Plan:**
1. Replace:
   ```java
   // Before:
   long[] deltaMs = new long[1];
   playtimeSessionStart.compute(playerId, (key, start) -> {
       if (start == null) { return now; }
       long delta = Math.max(0L, now - start);
       if (delta > 0L) { deltaMs[0] = delta; }
       return now;
   });
   if (deltaMs[0] > 0L) {

   // After:
   AtomicLong deltaMs = new AtomicLong();
   playtimeSessionStart.compute(playerId, (key, start) -> {
       if (start == null) { return now; }
       long delta = Math.max(0L, now - start);
       if (delta > 0L) { deltaMs.set(delta); }
       return now;
   });
   if (deltaMs.get() > 0L) {
   ```
2. Add `import java.util.concurrent.atomic.AtomicLong;` if not present.

---

## 3. LeaderboardHologramManager: Delegate constructor

**File:** `hyvexa-parkour/src/main/java/io/hyvexa/manager/LeaderboardHologramManager.java`

**Problem:** Two constructors (lines 32-42) with duplicated field assignments.

**Plan:**
1. Replace the 2-arg constructor (lines 32-36):
   ```java
   // Before:
   public LeaderboardHologramManager(ProgressStore progressStore, MapStore mapStore) {
       this.progressStore = progressStore;
       this.mapStore = mapStore;
       this.parkourWorldName = "Parkour";
   }

   // After:
   public LeaderboardHologramManager(ProgressStore progressStore, MapStore mapStore) {
       this(progressStore, mapStore, "Parkour");
   }
   ```

---

## 4. RunSessionTracker: Remove dead method

**File:** `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunSessionTracker.java`
**Call site:** `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunTracker.java:571`

**Problem:** `checkRecommendations()` at lines 44-48 has an empty body with comment "Practice/recommendation popups intentionally disabled." Still called from RunTracker line 571.

**Plan:**
1. Delete the method body at `RunSessionTracker.java:44-48`
2. Delete the call at `RunTracker.java:571`
3. Verify no other callers exist: `grep -rn "checkRecommendations" hyvexa-parkour/`
