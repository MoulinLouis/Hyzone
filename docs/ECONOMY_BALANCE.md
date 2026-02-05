# Ascend Mode - Economy Balance Documentation

Last updated: 2026-02-05

This document provides a factual overview of the economy balancing in Ascend mode, including all costs, multipliers, and progression formulas.

---

## Table of Contents

1. [Multiplier System](#multiplier-system)
2. [Runner Speed Upgrades](#runner-speed-upgrades)
3. [Runner Upgrade Costs](#runner-upgrade-costs)
4. [Elevation System](#elevation-system)
5. [Map Economics](#map-economics)
6. [Expected Progression Timeline](#expected-progression-timeline)
7. [Strategic Evolution System](#strategic-evolution-system)
8. [Design Philosophy](#design-philosophy)

---

## Multiplier System

### Manual Run Multiplier
- **Gain per completion:** +0.1
- **Applies to:** Manual completions only
- **Not affected by:** Stars, map level

### Runner Multiplier (Automatic)
- **Base gain per completion:** +0.1 (0★), +0.2 (1★+)
- **Evolution bonus:** ×2 multiplier gain when evolved (stars > 0)
- **Star scaling:**
  - 0★: +0.1 per completion (base)
  - 1★+: +0.2 per completion (×2 after evolution)
- **With Summit Multiplier Gain bonus:** Base increment × bonus
  - Example at level 10 (×4.16 bonus): 0★ = +0.42/run, 1★+ = +0.83/run

### Multiplier Slots
- **Total slots:** 5 (one per map)
- **Product formula:** Total multiplier = slot₁ × slot₂ × slot₃ × slot₄ × slot₅

### Elevation Multiplier (Linear)
- **Formula:** `level` (direct multiplier)
- **Scaling:**
  - Level 0 → ×1
  - Level 1 → ×1
  - Level 10 → ×10
  - Level 100 → ×100
  - Level 500 → ×500
- **Applied after:** Map multipliers (multiplicative)
- **Design intent:** Simple, predictable multiplier that scales linearly with investment

---

## Runner Speed Upgrades

### Speed Bonus Per Level (Uniform)

Speed upgrade effectiveness is the same for all maps:

| Map | Display Order | Speed Bonus per Level | Level 20 Total |
|-----|---------------|----------------------|----------------|
| Rouge (Red) | 0 | +10% | +200% |
| Orange | 1 | +10% | +200% |
| Jaune (Yellow) | 2 | +10% | +200% |
| Vert (Green) | 3 | +10% | +200% |
| Bleu (Blue) | 4 | +10% | +200% |

**Formula:** `speedMultiplier = 1.0 + (speedLevel × mapSpeedBonus)`

**Maximum speed level:** 20

**Design intent:** Reward investment in higher-difficulty maps with faster completion times.

---

## Runner Upgrade Costs

### Base Formula (Continuous Progression)

```
totalLevel = stars × 20 + speedLevel
effectiveLevel = totalLevel + mapOffset
baseCost = 5 × 2^effectiveLevel + effectiveLevel × 10
finalCost = baseCost × mapMultiplier × earlyLevelBoost
```

**Key concept:** Costs are based on **total levels purchased** across all evolutions, not just current speed level. This prevents cost resets after evolution.

**Growth rate:** ~2× per level (smooth, consistent progression with no artificial jumps).

### Early-Level Boost (Unlock Pacing)

To create more time between map unlocks without affecting late-game, a decaying cost boost applies to levels 0-9 on maps 2+ during the first evolution cycle (0★):

| Map | Max Boost (Level 0) | Threshold |
|-----|---------------------|-----------|
| Rouge | ×1.0 (no boost) | - |
| Orange | ×1.5 | Level 10 |
| Jaune | ×2.0 | Level 10 |
| Vert | ×2.5 | Level 10 |
| Bleu | ×3.0 | Level 10 |

**Decay formula:**
```
if stars == 0 and speedLevel < 10:
    decayFactor = (10 - speedLevel) / 10
    earlyLevelBoost = 1 + (maxBoost - 1) × decayFactor
else:
    earlyLevelBoost = 1.0
```

**Example (Map 2 - Orange, maxBoost = 1.5):**

| Level | Decay Factor | Boost | Base Cost | Final Cost |
|-------|--------------|-------|-----------|------------|
| 0 | 1.0 | ×1.50 | 28 | 42 |
| 1 | 0.9 | ×1.45 | 56 | 81 |
| 2 | 0.8 | ×1.40 | 98 | 137 |
| 3 | 0.7 | ×1.35 | 168 | 227 |
| 4 | 0.6 | ×1.30 | 294 | 382 |
| 5 | 0.5 | ×1.25 | 518 | 648 |
| 9 | 0.1 | ×1.05 | 2968 | 3116 |
| 10+ | 0.0 | ×1.00 | normal | normal |

**Design goals:**
- More time between early map unlocks (feels better in first 10 minutes)
- Costs always increase with level (no jarring drops)
- Late-game unaffected (boost is 1.0 after level 10 or any evolution)

### Map-Specific Scaling

Each map has an offset and multiplier applied to upgrade costs:

| Map | Display Order | Level Offset | Cost Multiplier |
|-----|---------------|--------------|-----------------|
| Rouge | 0 | +0 | ×1.0 |
| Orange | 1 | +1 | ×1.4 |
| Jaune | 2 | +2 | ×1.9 |
| Vert | 3 | +3 | ×2.6 |
| Bleu | 4 | +4 | ×3.5 |

### Cost Progression Table (Map 0, Rouge)

Smooth ~2× growth per level, no artificial boosts or jumps:

| Stars | Speed Level | Total Level | Upgrade Cost | Ratio |
|-------|-------------|-------------|--------------|-------|
| 0★ | 0 | 0 | **5** | - |
| 0★ | 1 | 1 | **20** | 4× |
| 0★ | 2 | 2 | **40** | 2× |
| 0★ | 3 | 3 | **70** | 1.75× |
| 0★ | 4 | 4 | **120** | 1.7× |
| 0★ | 5 | 5 | **210** | 1.75× |
| 0★ | 10 | 10 | **5,220** | ~2× |
| 0★ | 15 | 15 | **164K** | ~2× |
| 0★ | 19 | 19 | **2.6M** | ~2× |
| 1★ | 0 | 20 | **5.2M** | 2× |
| 1★ | 10 | 30 | **5.4B** | ~2× |
| 2★ | 0 | 40 | **5.5T** | ~2× |
| 3★ | 0 | 60 | **5.8 Quadrillion** | ~2× |
| 5★ | 0 | 100 | **6.3 Nonillion** | ~2× |

**Design rationale:**
- **No cost reset after evolution:** At 1★ Lv.0, the cost is what 0★ Lv.20 would have cost
- **Smooth ~2× growth:** Each level roughly doubles the cost (consistent, predictable)
- **No artificial boosts or jumps:** The formula is clean with no special cases
- **Evolution value comes from multiplier gains:** Stars double the multiplier gain (0.1 → 0.2 → 0.4...) without affecting cost progression

---

## Elevation System

### Cost Formula

```
cost = 30,000 × 1.15^currentLevel
```

**Base cost:** 30,000 coins
**Growth rate:** +15% per level (1.15 exponent)

### Example Costs

| Current Level | Next Level Cost | Cumulative Total |
|--------------|-----------------|------------------|
| 0 | 34,500 | 34,500 |
| 1 | 39,675 | 74,175 |
| 5 | 60,341 | ~235K |
| 10 | 121,364 | ~810K |
| 20 | 491,652 | ~6.1M |
| 50 | 33.0M | ~2.2B |
| 100 | 3.55B | ~2.4T |

**Overflow protection:** Costs capped at `Long.MAX_VALUE` to prevent integer overflow.

**Discount support:** Formula accepts a `costMultiplier` parameter for Summit/Ascension skill tree discounts.

---

## Map Economics

### Base Coin Rewards (Per Manual Completion)

| Map | Display Order | Base Reward | With ×100 Multiplier |
|-----|---------------|-------------|---------------------|
| Rouge | 0 | 1 coin | 100 coins |
| Orange | 1 | 5 coins | 500 coins |
| Jaune | 2 | 25 coins | 2,500 coins |
| Vert | 3 | 100 coins | 10,000 coins |
| Bleu | 4 | 500 coins | 50,000 coins |

**Actual reward formula:**
```
reward = baseReward × digitsProduct × elevation × (1 + coinFlowBonus)
```

Where:
- `digitsProduct` = Product of all 5 map multipliers
- `elevation` = Current elevation multiplier
- `coinFlowBonus` = Summit/Ascension bonuses

### Map Unlock Prices

| Map | Display Order | Unlock Cost |
|-----|---------------|-------------|
| Rouge | 0 | Free (starting map) |
| Orange | 1 | 100 coins |
| Jaune | 2 | 500 coins |
| Vert | 3 | 2,500 coins |
| Bleu | 4 | 10,000 coins |

**Auto-unlock condition:** Maps unlock automatically when any runner reaches level 5.

### Runner Purchase Prices

**All maps:** Free (0 coins)

**Requirements:**
- Map must be unlocked
- Map must be completed manually at least once
- Ghost recording must exist (player's personal best time)

### Base Run Times (For Runner AI)

| Map | Display Order | Base Time | With +200% Speed |
|-----|---------------|-----------|------------------|
| Rouge | 0 | 5 seconds | 1.67 seconds |
| Orange | 1 | 10 seconds | 3.33 seconds |
| Jaune | 2 | 16 seconds | 5.33 seconds |
| Vert | 3 | 26 seconds | 8.67 seconds |
| Bleu | 4 | 42 seconds | 14 seconds |

**Actual run time:** Uses player's personal best (ghost recording) as baseline, modified by speed upgrades.

---

## Expected Progression Timeline

### Early Game (0-5 minutes)

**Phase:** Initial runner acquisition and first upgrades

- Complete Rouge map manually (~30 seconds)
- Buy first runner (free)
- Earn ~50-100 coins from automatic completions
- Purchase first 3 speed upgrades (~5 + 15 + 35 = 55 coins)
- Multiplier reaches ~2-4×

**Design goal:** Smooth onboarding, visible progress every 30-60 seconds.

### Mid Game (5-20 minutes)

**Phase:** Map unlocks and multiplier acceleration

- Unlock Orange map (runner level 5 auto-unlocks)
- Numbers reach thousands (5-10 min mark)
- Begin working toward first elevation
- Multiple runners operating simultaneously
- Multiplier reaches ~10-50×

**Design goal:** Exponential feel starts to kick in, "big numbers" become visible.

### Late Game (20-30 minutes)

**Phase:** Elevation grinding and exponential growth

- Numbers reach millions (15-20 min mark)
- Elevation ×100-500 range
- Payouts per run: 1M-10M+ coins
- Upgrade costs: hundreds of thousands to millions
- Multiple map multipliers at 50-100+

**Design goal:** Full exponential idle game experience, satisfying number growth.

### Summit/Ascension (Late game)

**Phase:** Prestige systems and meta progression

- Summit threshold: 1 trillion coins minimum for first level
- Ascension threshold: 1 trillion total coins
- Permanent bonuses and skill trees
- Long-term progression hooks

---

## Summit System

Summit converts coins into XP for permanent category bonuses, resetting coins, elevation, multipliers, runners, and map unlocks (preserves best times only).

### XP System

**Coin to XP conversion:** `sqrt(coins) / 1,000,000` (diminishing returns)

| Coins | XP Gained |
|-------|-----------|
| 1T | 1 |
| 2T | 1 |
| 10T | 3 |
| 100T | 10 |
| 1 Quadrillion | 32 |
| 10 Quadrillion | 100 |

**Minimum coins for 1 XP:** 1 trillion coins (1T)

**XP per level formula:** `level^4`

| Level | XP Required | Cumulative XP |
|-------|-------------|---------------|
| 1 | 1 | 1 |
| 2 | 16 | 17 |
| 3 | 81 | 98 |
| 4 | 256 | 354 |
| 5 | 625 | 979 |
| 10 | 10,000 | ~25,333 |

**Expected level gains by coins:**

| Coins | XP | Levels Gained |
|-------|-----|---------------|
| 1T | 1 | 1 |
| 10T | 3 | 1 |
| 100T | 10 | 2 |
| 1 Quadrillion | 32 | 2 |
| 100 Quadrillion | 316 | 4 |

### What Summit Resets

Summit performs a full reset similar to Elevation:

**Reset (lost):**
- Coins → 0
- Elevation → 1
- Map multipliers → 1
- Runners → removed
- Map unlocks → only first map stays unlocked
- Completed manually flags → reset

**Preserved:**
- Best times (ghost recordings)
- Summit XP (permanent progression)

### Categories

| Category | Formula | Level 0 | Level 10 |
|----------|---------|---------|----------|
| **Runner Speed** | 1 + 0.45 × √level | ×1.00 | ×2.42 |
| **Multiplier Gain** | 1 + 0.5 × level^0.8 | ×1.00 | ×4.16 |
| **Evolution Power** | *(unused)* | - | - |

### Runner Speed

**Formula:** `1 + 0.45 × sqrt(level)`

Multiplies runner completion speed (inversely affects run time).

| Level | Speed Multiplier |
|-------|------------------|
| 0 | ×1.00 |
| 5 | ×2.01 |
| 10 | ×2.42 |
| 20 | ×3.01 |

### Multiplier Gain

**Formula:** `1 + 0.5 × level^0.8`

Multiplies the per-run multiplier increment for runners.

| Level | Gain Multiplier | 0★ Increment | 1★+ Increment |
|-------|-----------------|--------------|---------------|
| 0 | ×1.00 | +0.1/run | +0.2/run |
| 5 | ×2.81 | +0.28/run | +0.56/run |
| 10 | ×4.16 | +0.42/run | +0.83/run |

### Evolution Power

**Status:** Currently unused (kept for future use)

Evolution now provides a flat ×2 multiplier gain per-run (see Runner Multiplier section).

---

## Strategic Evolution System

Evolution provides a clear benefit with continuous cost progression.

### The Evolution Benefit

**When you evolve a runner:**
- ✅ **Multiplier gain ×2** (0.1 → 0.2 per run)
- ✅ **Speed level resets to 0** (visual reset, but costs continue)
- ✅ **Costs continue from total level** (no reset, no penalty)

### Evolution Value Analysis

| Stars | Multiplier Gain | Total Levels |
|-------|-----------------|--------------|
| 0★ | +0.1/run | 0-19 |
| 1★+ | +0.2/run (×2) | 20+ |

**Key insight:** Evolution is always beneficial - you get ×2 multiplier gains for the same cost progression you would have had anyway. All evolved runners (1★ to 5★) earn the same per-run increment.

### Strategic Decisions

**The core choice is not "whether to evolve" but "which runner to prioritize":**

**Focus Strategy:**
- Push one runner to 5★ as fast as possible
- Maximizes multiplier growth on that map
- Higher star runners earn more per completion
- Best for maps with fast completion times

**Spread Strategy:**
- Evolve multiple runners evenly
- Balanced income across all maps
- More visual variety (different NPC types)
- Better if you manually play different maps

### Example Scenario

**Situation:** You have massive coins, multiple runners at different levels

**Option A: Focus on Map 0 (Rouge)**
- Fast completion time = more runs = more multiplier gains
- Evolve to 1★ for +0.2 multiplier per run (×2 boost)
- **Best for:** Pure idle optimization

**Option B: Spread across all maps**
- Evolve each runner to at least 1★ for the ×2 boost
- More balanced income sources
- Different visual NPCs running around
- **Best for:** Visual variety and engagement

### Design Goals Achieved

✅ **Evolution is always worthwhile**
- ×2 multiplier gains with no cost penalty
- Continuous progression feels natural

✅ **Strategic depth from map choice**
- Which map to prioritize?
- Focus vs. spread investment

✅ **No complexity or traps**
- Simple rule: evolve when you can
- Costs just keep growing naturally
- No hidden penalties or inefficiencies

---

## Design Philosophy

### Core Principles

1. **Exponential Growth:** Numbers should feel like they're exploding, not crawling
2. **Strategic Depth:** Multiple valid approaches (focus one map vs. spread investment)
3. **Smooth Onboarding:** First 2-3 minutes should feel immediately rewarding
4. **No Dead Ends:** Every upgrade path remains viable throughout progression
5. **Prestige Value:** Higher-tier systems (elevation, summit, ascension) feel meaningful

### Balance Constraints

- **Minimum coin generation:** Even idle players make progress
- **Maximum speed:** Runners capped at level 20 per evolution cycle
- **Cost scaling:** Exponential but continuous - no resets, no surprises
- **Evolution benefit:** ×2 multiplier gain with no cost penalty
  - Costs continue from total levels purchased (stars × 20 + speedLevel)
  - Evolution is always beneficial - strategic choice is which map to prioritize

### Inflation Strategy

- **Income:** ×10 inflation on runner multiplier gains (0.01 → 0.1)
- **Costs:** ×2 base inflation on upgrades, ×6 on elevations
- **Net effect:** ~50% faster progression than pre-rebalance, much bigger numbers

---

## Technical Notes

### Overflow Protection

- Elevation costs capped at `Long.MAX_VALUE`
- Coin balances stored as `double` for high precision
- All UI displays use abbreviated notation (K, M, B, T)

### Precision

- Speed multipliers: `double` (floating-point)
- Costs: `long` (64-bit integer)
- Rewards: `double` (to handle large elevation multipliers)

### Formula Consistency

All formulas use consistent growth rates:
- **Elevation multiplier:** level (direct linear multiplier)
- **Elevation cost:** 1.15^level (15% growth)
- **Upgrades:** 2^totalLevel (100% growth per level, smooth and predictable)
- **Evolution gain:** ×2 per-run multiplier gain (flat boost when evolved)
- **Summit XP:** sqrt(coins) / 1,000,000 (very late-game, requires 1T coins for 1 XP)
- **Summit levels:** level^4 (steep late-game scaling)

Runner upgrade costs use `totalLevel = stars × 20 + speedLevel` to ensure continuous progression after evolution.

---

## Version History

- **2026-02-05 (v8):** Summit late-game rebalance
  - Summit XP conversion: `sqrt(coins) / 1,000,000` (was `/ 100`)
  - Minimum coins for Summit: 1T (was 10K)
  - Summit is now a true late-game system (requires 1T coins for level 1)
  - Summit now resets everything like elevation (multipliers, runners, map unlocks), keeps only best times

- **2026-02-05 (v7):** Linear elevation multiplier
  - Reverted elevation from exponential (`level × 1.02^level`) to linear (`level`)
  - Level now equals multiplier directly (level 210 = ×210)

- **2026-02-05 (v6):** Evolution and Summit XP rebalance
  - Evolution now grants ×10 per-run multiplier gain (instead of one-time map multiplier boost)
  - Removed Evolution Power one-shot bonus (category kept for future use)
  - Summit XP conversion: `sqrt(coins) / 100` (diminishing returns, was linear)
  - Summit XP per level: `level^4` (steep scaling, was `100 × level^1.5`)
  - Minimum coins for Summit: 10,000 (for 1 XP)
  - Late-game Summit scaling: 2T coins → ~9 levels (was ~1,500+ levels)

- **2026-02-05 (v5):** XP-based Summit system
  - Summit now uses XP instead of coin thresholds (1000 coins = 1 XP)
  - XP per level: 100 × level^1.5 (gradual scaling)
  - Replaced Coin Flow with Multiplier Gain (1 + 0.5 × level^0.8)
  - Runner Speed: 1 + 0.45 × sqrt(level) (diminishing returns)
  - Evolution Power: 2 + 0.5 × level^0.8 (applied on runner evolution, multiplies map multiplier)
  - Summit no longer resets multipliers, runners, or map unlocks (only coins and elevation)

- **2026-02-05 (v4):** Early-game unlock pacing
  - Added decaying cost boost for levels 0-9 on maps 2+ (first evolution only)
  - Boost scales by map: Orange ×1.5, Jaune ×2.0, Vert ×2.5, Bleu ×3.0
  - Creates more time between early map unlocks without affecting late-game
  - Smooth decay ensures costs always increase (no jarring price drops)

- **2026-02-04 (v3):** Exponential elevation and Summit refactoring
  - Elevation multiplier changed from `level` to `level × 1.02^level` *(reverted in v7)*
  - Summit Coin Flow changed from additive (+20%/level) to multiplicative (×1.20/level)
  - Summit Manual Mastery renamed to Evolution Power (+0.20 evolution base/level)
  - Evolution Power affects runner multiplier gains via formula: `0.1 × (2 + bonus)^stars`
  - Simplified upgrade cost formula to `5 × 2^level + level × 10` (smooth ~2× growth, no boosts)

- **2026-02-04 (v2):** Continuous cost progression after evolution
  - **Cost formula now uses total levels:** `totalLevel = stars × 20 + speedLevel`
  - Costs no longer reset after evolution (1★ Lv.0 costs same as what 0★ Lv.20 would cost)
  - Removed star multiplier (×2.2 per star) - progression is now inherent to total level
  - Early-game boost (÷4) now only applies to first evolution cycle (0★ levels 0-4)
  - Evolution is now always beneficial: double gains with no cost penalty

- **2026-02-04 (v1):** Major economy rebalance
  - Runner multiplier gains: 0.01 → 0.1 (×10)
  - Elevation base cost: 5,000 → 30,000 (×6)
  - Upgrade base formula: 60× → 20× (reduced from ×6 to ×2 inflation)
  - Added map-specific speed multipliers (10-30% per level)
  - Early-game boost: ÷4 for levels 0-4

- **Previous:** Original balance (slower progression, smaller numbers)
