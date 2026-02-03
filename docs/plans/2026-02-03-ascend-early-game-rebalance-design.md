# Ascend Early-Game Progression Rebalance

**Date:** 2026-02-03
**Status:** Approved for Implementation

## Problem Statement

Based on initial playtesting, early-game progression in Ascend mode is excessively slow:

1. **Slow multiplier growth**: Players start with 1.0x multiplier, earning only 1 coin per run initially (1.01, 1.02, etc.)
2. **High initial upgrade costs**: First runner upgrade costs 100 coins (~100 manual runs), making early progression feel glacial
3. **Uniform pricing across maps**: All runners use the same cost formula regardless of map level, which doesn't reflect map difficulty/progression

## Design Goals

1. **Fast early engagement**: First upgrade achievable in ~10 manual runs
2. **Quick ramp**: Cheap initial levels (0-2), then fast exponential growth
3. **Obscure scaling**: Non-obvious mathematical formula to prevent min-maxing and maintain discovery feel
4. **Map-based pricing**: Higher-level map runners should be significantly more expensive than lower-level map runners

## Solution

### 1. Obscure Quick-Ramp Base Formula

Replace the current simple doubling formula (`100 × 2^level`) with an obscure quick-ramp formula:

```java
baseCost = Math.round(10 × Math.pow(2.4, level) + level × 6)
```

**Characteristics:**
- Non-obvious 2.4 exponent (not clean doubling)
- Linear term (+6 per level) obscures the pattern
- Quick initial ramp, then aggressive exponential growth

**Sample Progression:**
- Level 0→1: 10 coins (~10 manual runs) ✓
- Level 1→2: 30 coins
- Level 2→3: 73 coins
- Level 3→4: 175 coins
- Level 4→5: 415 coins
- Level 5→6: 986 coins
- Level 10→11: ~24K coins

### 2. Map-Based Pricing (Offset + Multiplier)

Apply both an offset and multiplier based on map level to create compounding difficulty:

```java
finalCost = baseCost(level + mapOffset) × mapMultiplier
```

**Map Parameters (by displayOrder):**

| Map Level | Map Name | Offset | Multiplier |
|-----------|----------|--------|------------|
| 0 | Rouge | +0 | 1.0x |
| 1 | Orange | +1 | 1.4x |
| 2 | Jaune | +2 | 1.9x |
| 3 | Vert | +3 | 2.6x |
| 4 | Bleu | +4 | 3.5x |

**Cost Comparison Table:**

| Map | Level 0→1 | Level 5→6 | Level 10→11 |
|-----|-----------|-----------|-------------|
| Rouge | 10 | ~986 | ~24K |
| Orange | 42 | ~3.4K | ~88K |
| Jaune | 139 | ~10K | ~267K |
| Vert | 455 | ~30K | ~808K |
| Bleu | 1,452 | ~101K | ~2.5M |

**Effect:** Players can't spam-upgrade high-level runners early - creates natural progression gates.

## Implementation

### Files to Modify

**Primary:**
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java`
  - Modify `computeUpgradeCost(int currentLevel)` to `computeUpgradeCost(int currentLevel, int mapDisplayOrder)`
  - Update all call sites to pass map displayOrder

**Secondary (if storing costs in constants):**
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java`
  - Add map offset/multiplier arrays if centralizing configuration

### Testing Checklist

- [ ] First upgrade on Rouge costs ~10 coins
- [ ] Cost progression feels smooth but non-obvious
- [ ] Higher-level map runners are noticeably more expensive
- [ ] "Buy All" button correctly calculates costs per map
- [ ] "Evolve All" functionality unaffected (evolution is free)
- [ ] UI displays correct costs for all maps

## Success Metrics

**Before:**
- First upgrade: 100 coins (~100 manual runs)
- All maps: identical pricing

**After:**
- First upgrade (Rouge): 10 coins (~10 manual runs)
- Map-differentiated pricing with compounding difficulty
- Non-obvious cost scaling maintains discovery feel

## Notes

- Evolution remains free (already implemented as free when reaching max speed level)
- Map unlock requirements unchanged (runner level 3 on previous map)
- Runner purchase prices unchanged (separate from upgrade costs)
- This design will be playtested and may require further tuning
