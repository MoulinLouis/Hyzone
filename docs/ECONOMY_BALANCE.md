# Ascend Mode - Economy Balance Documentation

Last updated: 2026-02-04

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
- **Base gain per completion:** +0.1
- **Star scaling:** Gain doubles with each star evolution
  - 0★: +0.1 per completion
  - 1★: +0.2 per completion
  - 2★: +0.4 per completion
  - 3★: +0.8 per completion
  - 4★: +1.6 per completion
  - 5★: +3.2 per completion

### Multiplier Slots
- **Total slots:** 5 (one per map)
- **Product formula:** Total multiplier = slot₁ × slot₂ × slot₃ × slot₄ × slot₅

### Elevation Multiplier
- **Direct multiplier:** Elevation level = multiplier value
- **Example:** Elevation ×100 = 100× coin multiplier
- **Applied after:** Map multipliers (multiplicative)

---

## Runner Speed Upgrades

### Speed Bonus Per Level (Map-Dependent)

Speed upgrade effectiveness varies by map difficulty:

| Map | Display Order | Speed Bonus per Level | Level 20 Total |
|-----|---------------|----------------------|----------------|
| Rouge (Red) | 0 | +10% | +200% |
| Orange | 1 | +15% | +300% |
| Jaune (Yellow) | 2 | +20% | +400% |
| Vert (Green) | 3 | +25% | +500% |
| Bleu (Blue) | 4 | +30% | +600% |

**Formula:** `speedMultiplier = 1.0 + (speedLevel × mapSpeedBonus)`

**Maximum speed level:** 20

**Design intent:** Reward investment in higher-difficulty maps with faster completion times.

---

## Runner Upgrade Costs

### Base Formula (Continuous Progression)

```
totalLevel = stars × 20 + speedLevel
effectiveLevel = totalLevel + mapOffset
baseCost = 20 × 2.4^effectiveLevel + effectiveLevel × 12
finalCost = baseCost × mapMultiplier
```

**Key concept:** Costs are based on **total levels purchased** across all evolutions, not just current speed level. This prevents cost resets after evolution.

### Map-Specific Scaling

Each map has an offset and multiplier applied to upgrade costs:

| Map | Display Order | Level Offset | Cost Multiplier |
|-----|---------------|--------------|-----------------|
| Rouge | 0 | +0 | ×1.0 |
| Orange | 1 | +1 | ×1.4 |
| Jaune | 2 | +2 | ×1.9 |
| Vert | 3 | +3 | ×2.6 |
| Bleu | 4 | +4 | ×3.5 |

### Early-Game Boost

**0★ Levels 0-4 only:** Cost divided by 4

This ensures smooth onboarding during the first 2-3 minutes of gameplay. The boost does NOT apply after evolution.

### Continuous Cost Progression Table (Map 0, Rouge)

Costs continue to increase after evolution - no reset:

| Stars | Speed Level | Total Level | Upgrade Cost |
|-------|-------------|-------------|--------------|
| 0★ | 0 | 0 | **5** (÷4 early boost) |
| 0★ | 5 | 5 | **1,653** |
| 0★ | 10 | 10 | **127K** |
| 0★ | 15 | 15 | **10.1M** |
| 0★ | 19 | 19 | **318M** |
| 1★ | 0 | 20 | **804M** |
| 1★ | 10 | 30 | **5.1T** |
| 1★ | 19 | 39 | **160T** |
| 2★ | 0 | 40 | **405T** |
| 2★ | 10 | 50 | **2.6 Quadrillion** |
| 3★ | 0 | 60 | **1.0 Quintillion** |
| 4★ | 0 | 80 | **260 Quintillion** |
| 5★ | 0 | 100 | **66 Sextillion** |

**Design rationale:**
- **No cost reset after evolution:** At 1★ Lv.0, the cost is what 0★ Lv.20 would have cost
- **Pure exponential growth:** Each level adds ×2.4 to the cost regardless of stars
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

### Summit/Ascension (30+ minutes)

**Phase:** Prestige systems and meta progression

- Summit thresholds: 10K, 50K, 200K, 1M, 5M... coins spent
- Ascension threshold: 1 trillion total coins
- Permanent bonuses and skill trees
- Long-term progression hooks

---

## Strategic Evolution System

Evolution provides a clear benefit with continuous cost progression.

### The Evolution Benefit

**When you evolve a runner:**
- ✅ **Multiplier gain doubles** (0.1 → 0.2 → 0.4 → 0.8 → 1.6 → 3.2)
- ✅ **Speed level resets to 0** (visual reset, but costs continue)
- ✅ **Costs continue from total level** (no reset, no penalty)

### Evolution Value Analysis

| Stars | Multiplier Gain | Cumulative Gain | Total Levels |
|-------|-----------------|-----------------|--------------|
| 0★ | +0.1/run | +0.1/run | 0-19 |
| 1★ | +0.2/run | +0.3/run total | 20-39 |
| 2★ | +0.4/run | +0.7/run total | 40-59 |
| 3★ | +0.8/run | +1.5/run total | 60-79 |
| 4★ | +1.6/run | +3.1/run total | 80-99 |
| 5★ | +3.2/run | +6.3/run total | 100-119 |

**Key insight:** Evolution is always beneficial - you get double the multiplier gains for the same cost progression you would have had anyway.

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
- Push to 5★ for +3.2 multiplier per run
- **Best for:** Pure idle optimization

**Option B: Spread across all maps**
- Each map at 2-3★
- More balanced income sources
- Different visual NPCs running around
- **Best for:** Visual variety and engagement

### Design Goals Achieved

✅ **Evolution is always worthwhile**
- Double multiplier gains with no cost penalty
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
- **Evolution benefit:** Double multiplier gain with no cost penalty
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

All exponential formulas use consistent growth rates:
- **Elevation:** 1.15^level (15% growth)
- **Upgrades:** 2.4^totalLevel (140% growth per level, continuous across evolutions)
- **Star gain:** 2^stars (100% growth per evolution)

Runner upgrade costs use `totalLevel = stars × 20 + speedLevel` to ensure continuous progression after evolution.

---

## Version History

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
