# Skill Tree Expansion Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add 6 new high-cost skill tree nodes to the Ascend skill tree to incentivize long-term replay.

**Architecture:** New enum entries in SkillTreeNode, effect application in existing consumption points (RobotManager, SummitManager, AscendRunTracker, elevation cost callers), new UI nodes following the existing pattern in Ascend_SkillTree.ui.

**Tech Stack:** Java (Hytale server plugin), Hytale custom UI (.ui files)

---

### Task 1: Add enum entries + constants

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java`

**Step 1: Add new SkillTreeNode enum entries after EVOLUTION_POWER_2**

```java
RUNNER_SPEED_4("Runner Speed IV", "x1.5 global runner speed", 15, RUNNER_SPEED_3, EVOLUTION_POWER_2),
EVOLUTION_POWER_3("Evolution Power III", "+2 base evolution power", 15, RUNNER_SPEED_3, EVOLUTION_POWER_2),
MOMENTUM_MASTERY("Momentum Mastery", "Momentum x3.0 + 120s duration", 25, RUNNER_SPEED_4, EVOLUTION_POWER_3),
MULTIPLIER_BOOST_2("Multiplier Boost II", "+0.25 base multiplier gain", 40, MOMENTUM_MASTERY),
ELEVATION_BOOST("Elevation Boost", "Elevation cost -30%", 40, MOMENTUM_MASTERY),
RUNNER_SPEED_5("Runner Speed V", "x2.0 global runner speed", 75, MULTIPLIER_BOOST_2, ELEVATION_BOOST);
```

**Step 2: Add momentum mastery constants (after line 181)**

```java
public static final double MOMENTUM_MASTERY_MULTIPLIER = 3.0;
public static final long MOMENTUM_MASTERY_DURATION_MS = 120_000L;
```

---

### Task 2: Add accessor methods in AscensionManager

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ascension/AscensionManager.java`

**Step 1: Add 6 new accessor methods (after line 242)**

```java
public boolean hasRunnerSpeedBoost4(UUID playerId) {
    return hasSkillNode(playerId, SkillTreeNode.RUNNER_SPEED_4);
}

public boolean hasEvolutionPowerBoost3(UUID playerId) {
    return hasSkillNode(playerId, SkillTreeNode.EVOLUTION_POWER_3);
}

public boolean hasMomentumMastery(UUID playerId) {
    return hasSkillNode(playerId, SkillTreeNode.MOMENTUM_MASTERY);
}

public boolean hasMultiplierBoost2(UUID playerId) {
    return hasSkillNode(playerId, SkillTreeNode.MULTIPLIER_BOOST_2);
}

public boolean hasElevationBoost(UUID playerId) {
    return hasSkillNode(playerId, SkillTreeNode.ELEVATION_BOOST);
}

public boolean hasRunnerSpeedBoost5(UUID playerId) {
    return hasSkillNode(playerId, SkillTreeNode.RUNNER_SPEED_5);
}
```

---

### Task 3: Apply Runner Speed 4 + 5 effects

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotManager.java` (~line 1017)

**Step 1: Add after the RUNNER_SPEED_3 block (line 1017)**

```java
// Skill tree: Runner Speed IV (×1.5 global runner speed)
if (ascensionManager.hasRunnerSpeedBoost4(ownerId)) {
    speedMultiplier *= 1.5;
}
// Skill tree: Runner Speed V (×2.0 global runner speed)
if (ascensionManager.hasRunnerSpeedBoost5(ownerId)) {
    speedMultiplier *= 2.0;
}
```

---

### Task 4: Apply Evolution Power 3 effect

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/summit/SummitManager.java` (~line 234)

**Step 1: Add after the EVOLUTION_POWER_2 block (line 233)**

```java
// Skill tree: Evolution Power III adds +2.0 to base evolution power
if (plugin != null && plugin.getAscensionManager() != null
        && plugin.getAscensionManager().hasEvolutionPowerBoost3(playerId)) {
    fullBonus += 2.0;
}
```

---

### Task 5: Apply Momentum Mastery effect

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotManager.java` (~line 1028)
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/tracker/AscendRunTracker.java` (~line 359)

**Step 1: Update momentum multiplier in RobotManager (replace lines 1028-1030)**

Current:
```java
double momentumMultiplier = (ascensionManager != null && ascensionManager.hasMomentumSurge(ownerId))
    ? AscendConstants.MOMENTUM_SURGE_MULTIPLIER
    : AscendConstants.MOMENTUM_SPEED_MULTIPLIER;
```

New:
```java
double momentumMultiplier;
if (ascensionManager != null && ascensionManager.hasMomentumMastery(ownerId)) {
    momentumMultiplier = AscendConstants.MOMENTUM_MASTERY_MULTIPLIER;
} else if (ascensionManager != null && ascensionManager.hasMomentumSurge(ownerId)) {
    momentumMultiplier = AscendConstants.MOMENTUM_SURGE_MULTIPLIER;
} else {
    momentumMultiplier = AscendConstants.MOMENTUM_SPEED_MULTIPLIER;
}
```

**Step 2: Update momentum duration in AscendRunTracker (replace lines 359-362)**

Current:
```java
boolean hasEndurance = plugin.getAscensionManager().hasMomentumEndurance(playerId);
long momentumDuration = hasEndurance
    ? AscendConstants.MOMENTUM_ENDURANCE_DURATION_MS
    : AscendConstants.MOMENTUM_DURATION_MS;
```

New:
```java
long momentumDuration;
if (plugin.getAscensionManager().hasMomentumMastery(playerId)) {
    momentumDuration = AscendConstants.MOMENTUM_MASTERY_DURATION_MS;
} else if (plugin.getAscensionManager().hasMomentumEndurance(playerId)) {
    momentumDuration = AscendConstants.MOMENTUM_ENDURANCE_DURATION_MS;
} else {
    momentumDuration = AscendConstants.MOMENTUM_DURATION_MS;
}
```

**Step 3: Update toast text (same area in AscendRunTracker, ~line 366)**

Current:
```java
boolean hasSurge = plugin.getAscensionManager().hasMomentumSurge(playerId);
showToast(playerId, ToastType.ECONOMY, "Momentum: x" + (hasSurge ? "2.5" : "2") + " speed on " + mapName);
```

New:
```java
boolean hasMastery = plugin.getAscensionManager().hasMomentumMastery(playerId);
boolean hasSurge = plugin.getAscensionManager().hasMomentumSurge(playerId);
String momentumText = hasMastery ? "3" : (hasSurge ? "2.5" : "2");
showToast(playerId, ToastType.ECONOMY, "Momentum: x" + momentumText + " speed on " + mapName);
```

---

### Task 6: Apply Multiplier Boost 2 effect

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/summit/SummitManager.java` (~line 195)

**Step 1: Update getBaseMultiplierBonus() to stack both boosts**

Current returns 0.10 or 0.0. New returns sum:

```java
public double getBaseMultiplierBonus(UUID playerId) {
    double bonus = 0.0;
    ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
    if (plugin != null && plugin.getAscensionManager() != null) {
        if (plugin.getAscensionManager().hasMultiplierBoost(playerId)) {
            bonus += 0.10;
        }
        if (plugin.getAscensionManager().hasMultiplierBoost2(playerId)) {
            bonus += 0.25;
        }
    }
    return bonus;
}
```

---

### Task 7: Apply Elevation Boost effect

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/summit/SummitManager.java` (add method)
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/ElevationPage.java` (update costMultiplier calls)
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotManager.java` (autoElevateIfPossible)
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/ElevateCommand.java` (if exists)

**Step 1: Add helper method in SummitManager**

```java
/**
 * Get elevation cost multiplier based on skill tree.
 * @return 0.70 if ELEVATION_BOOST unlocked, 1.0 otherwise
 */
public BigNumber getElevationCostMultiplier(UUID playerId) {
    ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
    if (plugin != null && plugin.getAscensionManager() != null
            && plugin.getAscensionManager().hasElevationBoost(playerId)) {
        return BigNumber.fromDouble(0.70);
    }
    return BigNumber.ONE;
}
```

**Step 2: Update all elevation cost call sites to use this multiplier instead of hardcoded 1.0/BigNumber.ONE**

Find all places that call `getElevationLevelUpCost()` or `calculateElevationPurchase()` and pass the multiplier from `summitManager.getElevationCostMultiplier(playerId)`.

---

### Task 8: Add new nodes to Ascend_SkillTree.ui

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_SkillTree.ui`

**Layout positions (extending existing 130px tier spacing):**

| Tier | Node | Position |
|------|------|----------|
| 10 left | RUNNER_SPEED_4 | Left: 80, Top: 1190 |
| 10 right | EVOLUTION_POWER_3 | Left: 420, Top: 1190 |
| 11 center | MOMENTUM_MASTERY | Left: 250, Top: 1320 |
| 12 left | MULTIPLIER_BOOST_2 | Left: 80, Top: 1450 |
| 12 right | ELEVATION_BOOST | Left: 420, Top: 1450 |
| 13 center | RUNNER_SPEED_5 | Left: 250, Top: 1580 |

**Step 1: Update TreeContent dimensions**
- `ContentHeight: 1320` → `ContentHeight: 1840`
- `TreeContent Height: 1190` → `TreeContent Height: 1710`

**Step 2: Add connectors (same pattern as existing)**

T9→T10 connectors:
```
// T9 left/right connect to T10 left/right (OR logic: horizontal bar)
Group #ConnT8Left { Anchor: (Left: 160, Top: 1170, Width: 4, Height: 8); Background: #4b5563; }
Group #ConnT8Right { Anchor: (Left: 500, Top: 1170, Width: 4, Height: 8); Background: #4b5563; }
Group #ConnT8H { Anchor: (Left: 160, Top: 1178, Width: 344, Height: 4); Background: #4b5563; }
Group #ConnT8SplitLeft { Anchor: (Left: 160, Top: 1182, Width: 4, Height: 8); Background: #4b5563; }
Group #ConnT8SplitRight { Anchor: (Left: 500, Top: 1182, Width: 4, Height: 8); Background: #4b5563; }
```

T10→T11 connectors:
```
Group #ConnT9Left { Anchor: (Left: 160, Top: 1300, Width: 4, Height: 8); Background: #4b5563; }
Group #ConnT9Right { Anchor: (Left: 500, Top: 1300, Width: 4, Height: 8); Background: #4b5563; }
Group #ConnT9H { Anchor: (Left: 160, Top: 1308, Width: 340, Height: 4); Background: #4b5563; }
Group #ConnT9Merge { Anchor: (Left: 328, Top: 1312, Width: 4, Height: 8); Background: #4b5563; }
```

T11→T12 connectors:
```
Group #ConnT10Split { Anchor: (Left: 328, Top: 1430, Width: 4, Height: 8); Background: #4b5563; }
Group #ConnT10H { Anchor: (Left: 160, Top: 1438, Width: 340, Height: 4); Background: #4b5563; }
Group #ConnT10Left { Anchor: (Left: 160, Top: 1442, Width: 4, Height: 8); Background: #4b5563; }
Group #ConnT10Right { Anchor: (Left: 500, Top: 1442, Width: 4, Height: 8); Background: #4b5563; }
```

T12→T13 connectors:
```
Group #ConnT11Left { Anchor: (Left: 160, Top: 1560, Width: 4, Height: 8); Background: #4b5563; }
Group #ConnT11Right { Anchor: (Left: 500, Top: 1560, Width: 4, Height: 8); Background: #4b5563; }
Group #ConnT11H { Anchor: (Left: 160, Top: 1568, Width: 340, Height: 4); Background: #4b5563; }
Group #ConnT11Merge { Anchor: (Left: 328, Top: 1572, Width: 4, Height: 8); Background: #4b5563; }
```

**Step 3: Add 6 node Button elements (same structure as existing nodes)**

Each node follows the exact same pattern: Button with @NodeStyle, Border group, Revealed group (name+desc+status), Locked group (lock icon). Use existing nodes as template — copy the pattern and change:
- Anchor position (see table above)
- IDs (Border/Revealed/Locked/Name/Desc/Status + PascalCase node name)
- Coordinate label text (10:1, 10:2, 11:1, 12:1, 12:2, 13:1)
- Name/Description/Status text

---

### Task 9: Update SkillTreePage.java NODE_COORDINATES

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/SkillTreePage.java`

**Step 1: Add new entries to NODE_COORDINATES map (after line 40)**

```java
NODE_COORDINATES.put(SkillTreeNode.RUNNER_SPEED_4, "10:1");
NODE_COORDINATES.put(SkillTreeNode.EVOLUTION_POWER_3, "10:2");
NODE_COORDINATES.put(SkillTreeNode.MOMENTUM_MASTERY, "11:1");
NODE_COORDINATES.put(SkillTreeNode.MULTIPLIER_BOOST_2, "12:1");
NODE_COORDINATES.put(SkillTreeNode.ELEVATION_BOOST, "12:2");
NODE_COORDINATES.put(SkillTreeNode.RUNNER_SPEED_5, "13:1");
```

No other changes needed — the existing `SkillTreeNode.values()` iteration, `toPascalCase()`, and event binding logic will auto-discover the new nodes.

---

### Task 10: Update docs

**Files:**
- Modify: `docs/ECONOMY_BALANCE.md` (Ascension section, skill tree table)
- Modify: `CHANGELOG.md`

**ECONOMY_BALANCE.md:** Add new nodes to the Ascension/Skill Tree section with costs and effects.

**CHANGELOG.md:** Add entry like:
```
- feat(ascend): add 6 new skill tree nodes (Runner Speed IV/V, Evolution Power III, Momentum Mastery, Multiplier Boost II, Elevation Boost) — costs 15-75 AP
```
