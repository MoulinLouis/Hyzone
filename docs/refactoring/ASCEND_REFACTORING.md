# hyvexa-parkour-ascend Refactoring Plan

Verified issues only. False positives removed after source verification.

---

## 1. AchievementManager: Data-driven achievement system (large)

**File:** `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/achievement/AchievementManager.java`

**Problem:** Two 28-case switch statements (`isAchievementEarned` at lines 71-116, `getProgress` at lines 242-372) duplicate the same achievement logic. 11 private helper methods (lines 118-230) are mostly redundant single-predicate iterators. Adding one achievement requires editing 2 switch blocks + potentially a new helper.

**Plan:**

### Step 1: Define achievement descriptors

Add a static registry that encodes each achievement's evaluation logic:

```java
private record AchievementDef(
    AchievementType type,
    ToLongFunction<AscendPlayerProgress> currentValue,
    long threshold
) {}

private static final List<AchievementDef> DEFINITIONS = List.of(
    new AchievementDef(FIRST_STEPS,   p -> p.getTotalManualRuns(), 1),
    new AchievementDef(WARMING_UP,    p -> p.getTotalManualRuns(), AscendConstants.ACHIEVEMENT_MANUAL_RUNS_10),
    new AchievementDef(DEDICATED,     p -> p.getTotalManualRuns(), AscendConstants.ACHIEVEMENT_MANUAL_RUNS_100),
    // ... all 28 achievements
);

private static final Map<AchievementType, AchievementDef> BY_TYPE =
    DEFINITIONS.stream().collect(Collectors.toMap(AchievementDef::type, d -> d));
```

For achievements that need complex evaluation (like "all other achievements unlocked"), use a sentinel threshold (e.g., -1) and override in the evaluator.

### Step 2: Replace isAchievementEarned

```java
private boolean isAchievementEarned(UUID playerId, AscendPlayerProgress progress, AchievementType achievement) {
    AchievementDef def = BY_TYPE.get(achievement);
    if (def == null) return false;
    return def.currentValue().applyAsLong(progress) >= def.threshold();
}
```

### Step 3: Replace getProgress

```java
// For most achievements:
AchievementDef def = BY_TYPE.get(achievement);
current = Math.min(def.threshold(), def.currentValue().applyAsLong(progress));
required = def.threshold();
```

### Step 4: Consolidate helper methods

Replace the 11 helpers with 2-3 generic ones:

```java
private boolean anyRobotMatches(AscendPlayerProgress p, Predicate<MapProgress> pred) {
    return p.getMapProgress().values().stream().anyMatch(pred);
}

private long maxRobotValue(AscendPlayerProgress p, ToLongFunction<MapProgress> fn) {
    return p.getMapProgress().values().stream().mapToLong(fn).max().orElse(0);
}

private long countRobots(AscendPlayerProgress p, Predicate<MapProgress> pred) {
    return p.getMapProgress().values().stream().filter(pred).count();
}
```

Use these in the `currentValue` lambdas of robot-related `AchievementDef` entries.

### Step 5: Delete unused helpers

After migration, delete: `hasAnyRobot`, `countRobots`, `hasEvolvedRobot`, `hasMaxStarRobot`, `getMaxRobotStars`, `hasAnySummitLevel`, `hasAnySummitLevelAbove`, `allMapsMaxStars`, `allOtherAchievementsUnlocked`, `getMaxSummitLevel`, `countOtherUnlockedAchievements`.

---

## 2. AscendHudManager: Consolidate 8 maps into PlayerHudState

**File:** `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/hud/AscendHudManager.java`

**Problem:** 8 parallel `ConcurrentHashMap<UUID, ...>` fields (lines 37-44) track per-player HUD state independently. Cleanup at lines 217-226 requires 8 `.remove()` calls. Easy to forget one when adding new state.

**Plan:**

### Step 1: Create PlayerHudState class

```java
private static class PlayerHudState {
    AscendHud hud;
    boolean attached;
    boolean hidden;
    long readyAt;
    HiddenAscendHud hiddenHud;
    CachedEconomyData economyData;
    RunnerBarCache runnerBar;
    boolean previewing;
}
```

### Step 2: Replace 8 maps with 1

```java
private final ConcurrentHashMap<UUID, PlayerHudState> playerStates = new ConcurrentHashMap<>();
```

### Step 3: Add accessor helper

```java
private PlayerHudState getOrCreate(UUID playerId) {
    return playerStates.computeIfAbsent(playerId, k -> new PlayerHudState());
}
```

### Step 4: Migrate all reads/writes

Find every usage of the 8 maps and redirect to `playerStates.get(id).fieldName` or `getOrCreate(id).fieldName`. This is mechanical — search for each old map name.

### Step 5: Simplify cleanup

```java
public void removePlayer(UUID playerId) {
    playerStates.remove(playerId);
}
```

**Note:** This touches many lines in AscendHudManager. Do a find/replace for each map name. Test that HUD still attaches, hides, and cleans up correctly.

---

## 3. MineGateChecker: Fix potential NPE in enterMine

**File:** `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/MineGateChecker.java`

**Problem:** At line 99, `Player player = store.getComponent(ref, Player.getComponentType())` can return null. It's passed to `denyMineAccess()` at line 100 and to `swapToMineHud()` at line 115 without null check.

**Plan:**
1. After line 99, add:
   ```java
   if (player == null) return false;
   ```
2. This makes the later `if (player != null)` check at line 112 redundant — remove it and dedent the `giveMineItems(player)` call.
