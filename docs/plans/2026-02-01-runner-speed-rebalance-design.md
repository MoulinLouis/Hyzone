# Runner Speed Rebalance Design

**Date:** 2026-02-01
**Status:** Approved for implementation

## Problem Statement

Current runner movement speeds in parkour-ascend feel unsatisfying:
- Map 1 (Orange): Mostly correct but could be slightly faster
- Maps 2-4: Far too slow and unsatisfying for the "looping runner" gameplay

Need a global balance pass to increase runner speed across the board.

## Solution: Hybrid Speed Rebalancing

Combine two approaches for maximum impact:
1. **Reduce base run times** - Make runners faster at all levels
2. **Increase speed multiplier** - Make speed upgrades more impactful

## Parameter Changes

### Base Run Time Adjustments

```java
// MAP_BASE_RUN_TIMES_MS - Fibonacci-inspired smooth progression
Map 0 (Rouge):  5000L  → 5000L  (unchanged - starting map)
Map 1 (Orange): 15000L → 10000L (-33%)
Map 2 (Jaune):  30000L → 16000L (-47%)
Map 3 (Vert):   60000L → 26000L (-57%)
Map 4 (Bleu):   120000L → 42000L (-65%)
```

**Progression Curve:**
- Map 0→1: 2.0x (unchanged)
- Map 1→2: 1.6x (smooth, avoids 2x jumps)
- Map 2→3: 1.625x (smooth)
- Map 3→4: 1.62x (smooth)

### Speed Upgrade Multiplier

```java
// SPEED_UPGRADE_MULTIPLIER
Current: 0.10 (10% per level)
New:     0.15 (15% per level)
```

## Gameplay Impact

### At Base Level (Speed Level 0, no bonuses)
```
Map 1 (Orange): 15s → 10s  (1.5x faster)
Map 2 (Jaune):  30s → 16s  (1.875x faster)
Map 3 (Vert):   60s → 26s  (2.3x faster)
Map 4 (Bleu):   120s → 42s (2.86x faster)
```

### At Speed Level 10 (2.5x speed multiplier)
```
Map 1: 10s → 4s
Map 2: 16s → 6.4s
Map 3: 26s → 10.4s
Map 4: 42s → 16.8s
```

### At Max Speed Level 20 (4.0x speed multiplier)
```
Map 1: 10s → 2.5s
Map 2: 16s → 4s
Map 3: 26s → 6.5s
Map 4: 42s → 10.5s
```

### Key Improvements
- Early game runners feel much more satisfying (nearly 2-3x faster at base)
- Speed upgrades remain valuable (4x speed at max vs 3x previously)
- Higher maps get proportionally bigger improvements, fixing the "too slow" issue
- Smooth progression curve avoids jarring difficulty spikes between maps

## Implementation

### Files to Modify
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java`

### Change 1: Speed Multiplier (line 17)

```java
// Current:
public static final double SPEED_UPGRADE_MULTIPLIER = 0.10; // +10% speed per level

// New:
public static final double SPEED_UPGRADE_MULTIPLIER = 0.15; // +15% speed per level
```

### Change 2: Base Run Times (lines 24-30)

```java
// Current:
public static final long[] MAP_BASE_RUN_TIMES_MS = {
    5000L,    // Level 0 (Rouge)  - 5 sec
    15000L,   // Level 1 (Orange) - 15 sec
    30000L,   // Level 2 (Jaune)  - 30 sec
    60000L,   // Level 3 (Vert)   - 1 min
    120000L   // Level 4 (Bleu)   - 2 min
};

// New:
public static final long[] MAP_BASE_RUN_TIMES_MS = {
    5000L,    // Level 0 (Rouge)  - 5 sec
    10000L,   // Level 1 (Orange) - 10 sec
    16000L,   // Level 2 (Jaune)  - 16 sec
    26000L,   // Level 3 (Vert)   - 26 sec
    42000L    // Level 4 (Bleu)   - 42 sec
};
```

### Automatic Propagation

No other code changes needed. The `computeCompletionIntervalMs` method in `RobotManager.java:781-810` already uses these constants, so changes automatically apply to:
- Runner completion timing
- Visual movement speed (tickRobotMovement)
- Coin generation rates
- All speed bonuses (Summit, Ascension) apply multiplicatively

## Testing Recommendations

1. Verify runner movement speed feels satisfying at base level (no upgrades)
2. Test progression from speed level 0 → 20 feels rewarding
3. Confirm higher maps (3-4) no longer feel "too slow"
4. Validate coin generation rates scale appropriately with faster completion times

## Rationale

**Why Fibonacci-inspired progression?**
- Avoids jarring 2x jumps between maps
- Creates smooth difficulty curve
- Maintains distinct feel between map tiers

**Why 15% multiplier vs higher?**
- Combined with aggressive base time reduction, provides 4x speed at max level
- Keeps speed upgrades valuable throughout progression
- Prevents runners from completing runs too quickly at max level

**Why leave Map 0 unchanged?**
- Starting map provides baseline tutorial experience
- 5 seconds already very fast for shortest/easiest map
- Preserves introductory pacing
