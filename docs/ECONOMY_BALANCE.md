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
9. [Gems (Global Currency)](#gems-global-currency)

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

### Multiplier Formula

**Elevation multiplier = level^1.05** (slightly super-linear)

Elevation uses a level-based prestige system where higher levels give progressively better multipliers:
- Level 1 → ×1 multiplier
- Level 10 → ×11 multiplier
- Level 50 → ×61 multiplier
- Level 100 → ×126 multiplier
- Level 200 → ×261 multiplier
- Level 500 → ×695 multiplier

**Note:** This rewards players for pushing higher elevation levels rather than stopping early.

### Cost Formula

```
cost = 30,000 × 1.15^(currentLevel^0.72)
```

**Base cost:** 30,000 vexa
**Growth rate:** Non-linear — `1.15^(level^0.72)` flattens the curve at high levels
**Cost curve exponent:** 0.72

At low levels this behaves almost identically to `1.15^level`. At high levels the effective exponent grows much slower, so each subsequent level is proportionally cheaper. This keeps elevation attractive throughout the game.

### Example Costs

| Current Level | Next Level Cost | vs Pure Exponential (1.15^level) |
|--------------|-----------------|----------------------------------|
| 1 | 34,500 | 1.00× (same) |
| 5 | 46,800 | 0.78× |
| 10 | 62,500 | 0.51× |
| 20 | 100,400 | 0.20× |
| 50 | 310,500 | 0.01× |
| 100 | 1.4M | ~0× |
| 200 | 17M | ~0× |

**Discount support:** Formula accepts a `costMultiplier` parameter for Summit/Ascension skill tree discounts.

### Accumulated Vexa

Elevation uses **accumulated vexa** (total vexa earned since last reset) instead of the player's current vexa balance. This means spending vexa on runner upgrades does NOT reduce your elevation potential — only earning matters.

**Increment:** Every time vexa is earned (manual runs, runner completions, passive earnings, admin adds), the accumulated counter increases.

**Reset to 0 on:**
- Elevation (after purchasing levels)
- Summit (full reset)
- Ascension (full reset)

---

## Map Economics

### Base Vexa Rewards (Per Manual Completion)

| Map | Display Order | Base Reward | With ×100 Multiplier |
|-----|---------------|-------------|---------------------|
| Rouge | 0 | 1 vexa | 100 vexa |
| Orange | 1 | 5 vexa | 500 vexa |
| Jaune | 2 | 25 vexa | 2,500 vexa |
| Vert | 3 | 100 vexa | 10,000 vexa |
| Bleu | 4 | 500 vexa | 50,000 vexa |

**Actual reward formula:**
```
reward = baseReward × digitsProduct × elevation × (1 + vexaFlowBonus)
```

Where:
- `digitsProduct` = Product of all 5 map multipliers
- `elevation` = Current elevation multiplier
- `vexaFlowBonus` = Summit/Ascension bonuses

### Map Unlock System

Maps unlock progressively based on runner level, not vexa price.

**Unlock condition:** Maps unlock automatically when the runner on the **previous map** reaches level 5.

| Map | Display Order | Unlock Requirement |
|-----|---------------|--------------------|
| Rouge | 0 | Free (starting map, always unlocked) |
| Orange | 1 | Map 0 runner reaches level 5 |
| Jaune | 2 | Map 1 runner reaches level 5 |
| Vert | 3 | Map 2 runner reaches level 5 |
| Bleu | 4 | Map 3 runner reaches level 5 |

**Constant:** `MAP_UNLOCK_REQUIRED_RUNNER_LEVEL = 5`

**Once unlocked:** Maps stay permanently unlocked (even if runner is evolved/reset to level 0)

### Runner Purchase Prices

**All maps:** Free (0 vexa)

Runners have no vexa cost after map unlock. They can be purchased for free once:
- Map is unlocked (via runner level requirement)
- Map has been completed manually at least once
- Ghost recording exists (player's personal best time)

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

## Passive Earnings

### Offline Production

When a player disconnects or switches worlds, their runners continue to earn vexa at a reduced rate.

**Base offline rate:** 10% of normal production
**Maximum accumulation time:** 24 hours
**Minimum offline time:** 1 minute

**How it works:**
- Passive earnings are calculated based on runner production rates at the time of disconnect
- Vexa and map multiplier gains accumulate at the reduced rate
- Players receive a summary popup on reconnect showing total passive earnings
- There is no Ascendancy Tree node that boosts offline rate. Base rate is 10%.

**Design rationale:**
- Rewards consistent play without punishing casual players
- 10% base rate is generous enough to feel worthwhile but not so high that it competes with active play
- 24-hour cap prevents extreme accumulation from long absences

---

## Expected Progression Timeline

### Early Game (0-5 minutes)

**Phase:** Initial runner acquisition and first upgrades

- Complete Rouge map manually (~30 seconds)
- Buy first runner (free)
- Earn ~50-100 vexa from automatic completions
- Purchase first 3 speed upgrades (~5 + 15 + 35 = 55 vexa)
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
- Payouts per run: 1M-10M+ vexa
- Upgrade costs: hundreds of thousands to millions
- Multiple map multipliers at 50-100+

**Design goal:** Full exponential idle game experience, satisfying number growth.

### Summit/Ascension (Late game)

**Phase:** Prestige systems and meta progression

- Summit threshold: 1 billion vexa minimum for first level
- Ascension threshold: 1 Decillion (1Dc) vexa
- Permanent bonuses and skill trees
- Long-term progression hooks

---

## Summit System

Summit converts vexa into XP for permanent category bonuses, resetting vexa, elevation, multipliers, runners, and map unlocks (preserves best times only).

### XP System

**Vexa to XP conversion:** `(vexa / 1B)^power` where power is calibrated so 1Dc = level 1000

| Vexa | XP Gained |
|-------|-----------|
| 1B | 1 |
| 10B | 2 |
| 100B | 5 |
| 1T | 12 |
| 10T | 26 |
| 100T | 60 |
| 1Qa | 135 |
| 1Qi | 1,572 |
| 1Sx | 18,271 |
| 1Sp | 212,425 |
| 1Oc | 2,469,718 |
| 1No | 28,713,668 |
| 1Dc | 333,833,500 |

**Minimum vexa for 1 XP:** 1 billion vexa (1B)
**Level 1000 target:** 1 Decillion (10^33) accumulated vexa

**XP per level formula:** `level^2`

| Level | XP Required | Cumulative XP |
|-------|-------------|---------------|
| 1 | 1 | 1 |
| 2 | 4 | 5 |
| 3 | 9 | 14 |
| 5 | 25 | 55 |
| 10 | 100 | 385 |
| 20 | 400 | 2,870 |
| 50 | 2,500 | 42,925 |

**Expected level gains by vexa (from level 0):**

| Vexa | XP | Approx Level |
|-------|-----|--------------|
| 1B | 1 | 1 |
| 10B | 2 | 1 |
| 100B | 5 | 2 |
| 1T | 12 | 2 |
| 10T | 26 | 3 |
| 100T | 60 | 5 |
| 1Qa | 135 | 6 |
| 1Qi | 1,572 | 16 |
| 1Sx | 18,271 | 37 |
| 1Sp | 212,425 | 85 |
| 1Oc | 2.5M | 194 |
| 1No | 28.7M | 441 |
| 1Dc | 333.8M | 1000 |

### What Summit Resets

Summit performs a full reset similar to Elevation:

**Reset (lost):**
- Vexa → 0
- Elevation → 1
- Map multipliers → 1
- Runners → removed
- Map unlocks → only first map stays unlocked
- Completed manually flags → reset

**Preserved:**
- Best times (ghost recordings)
- Summit XP (permanent progression)

### Categories

| Category | Formula | Per Level | Level 0 | Level 10 | Level 25 |
|----------|---------|-----------|---------|----------|----------|
| **Runner Speed** | 1 + 0.15 × level | +0.15 | ×1.00 | ×2.50 | ×4.75 |
| **Multiplier Gain** | 1 + 0.30 × level | +0.30 | ×1.00 | ×4.00 | ×8.50 |
| **Evolution Power** | 3 + 0.10 × level | +0.10 | ×3.00 | ×4.00 | ×5.50 |

**Three bonus growth zones:**
- **Level 0-25 (soft cap):** Linear growth — full increment per level
- **Level 25-500 (deep cap):** √ growth — diminishing returns
- **Level 500+ (deep cap):** ⁴√ growth — heavy diminishing returns

**XP softcap at level 1000:** Levels above 1000 cost progressively more XP (exponent rises from level^2 to level^3). With the same vexa, old level 5000 now reaches ~3591, old level 10000 now reaches ~6042. The XP calibration targets level 1000 at 1Dc accumulated vexa (unchanged).

### Runner Speed

**Formula:** `1 + 0.15 × level` (linear 0-25, √ growth 25-500, ⁴√ growth 500+)

Multiplies runner completion speed (inversely affects run time).

| Level | Speed Multiplier | Zone |
|-------|------------------|------|
| 0 | ×1.00 | linear |
| 10 | ×2.50 | linear |
| 25 | ×4.75 | soft cap |
| 50 | ×5.50 | √ growth |
| 100 | ×6.05 | √ growth |
| 500 | ×8.02 | deep cap |
| 1000 | ×8.73 | ⁴√ growth |

### Multiplier Gain

**Formula:** `1 + 0.30 × level` (linear 0-25, √ growth 25-500, ⁴√ growth 500+)

Multiplies the per-run multiplier increment for runners.

| Level | Gain Multiplier | 0★ Increment | 1★ Increment | Zone |
|-------|-----------------|--------------|---------------|------|
| 0 | ×1.00 | +0.10/run | +0.30/run | linear |
| 10 | ×4.00 | +0.40/run | +1.20/run | linear |
| 25 | ×8.50 | +0.85/run | +2.55/run | soft cap |
| 50 | ×10.00 | +1.00/run | +3.00/run | √ growth |
| 100 | ×11.10 | +1.11/run | +3.33/run | √ growth |
| 500 | ×15.04 | +1.50/run | +4.51/run | deep cap |
| 1000 | ×16.46 | +1.65/run | +4.94/run | ⁴√ growth |

*Note: 1★+ increments shown assume base Evolution Power (×3). Higher Evolution Power increases per star.*

### Evolution Power

**Formula:** `3 + 0.10 × level` (linear 0-25, √ growth 25-500, ⁴√ growth 500+)

Each Summit level gives a flat EP boost up to level 25, then transitions to slower growth.

| Level | Evolution Power | 0★ | 3★ | 5★ | Zone |
|-------|-----------------|------|------|------|------|
| 0 | ×3.00 | 0.10 | 2.70 | 24.3 | linear |
| 10 | ×4.00 | 0.10 | 6.40 | 102.4 | linear |
| 25 | ×5.50 | 0.10 | 16.64 | 503.3 | soft cap |
| 50 | ×6.00 | 0.10 | 21.60 | 777.6 | √ growth |
| 100 | ×6.37 | 0.10 | 25.82 | 1,047.5 | √ growth |
| 500 | ×7.68 | 0.10 | 45.31 | 2,555.8 | deep cap |
| 1000 | ×8.15 | 0.10 | 54.19 | 3,593.2 | ⁴√ growth |

**Formula:** `increment = 0.1 × evolutionPower^stars × multiplierGainBonus`

---

## Ascension System

Ascension is the ultimate prestige. Requires 1 Decillion (1Dc = 10^33) vexa. Resets everything except best times, skill tree, achievements, and challenge completions.

### AP (Ascension Points)

**Formula:** `AP per ascension = 1 + completed_challenges`

| Challenges Completed | AP Multiplier | AP per Ascension |
|---------------------|---------------|------------------|
| 0 | x1 | 1 |
| 4 | x5 | 5 |
| 7 (all) | x8 | 8 |

### Challenges

7 challenges, each granting +1 to the AP multiplier when completed. Sequential unlock (must complete 1-N before N+1).

| Challenge | Malus | Color |
|-----------|-------|-------|
| 1 | Map 5 locked | Green |
| 2 | Runner Speed at 50% | Orange |
| 3 | Multiplier Gain at 50% | Blue |
| 4 | Evolution Power at 50% | Red |
| 5 | Runner Speed + Multiplier Gain at 50% | Violet |
| 6 | All Summit bonuses at 50% | Pink |
| 7 | Maps 4 & 5 locked | Orange |

**Break Ascension:** Unlocked after completing all 7 challenges. Suppresses auto-ascension at 1Dc.

### Ascendancy Tree

Permanent skill nodes purchased with AP. Prerequisites use OR logic (any one parent unlocks the child).

| Tier | Node | Cost | Effect | Prerequisites |
|------|------|------|--------|---------------|
| 1 | Runner Speed+ | 1 | x1.25 global runner speed | — |
| 2L | Runner Speed II | 2 | x1.25 global runner speed | Runner Speed+ |
| 2R | Evolution Power+ | 2 | +1 base evolution power | Runner Speed+ |
| 3 | Momentum Speed | 2 | x2 speed during momentum | Runner Speed II OR Evolution Power+ |
| 4L | Runner Speed III | 3 | x1.25 global runner speed | Momentum Speed |
| 4R | Momentum Endurance | 3 | Momentum duration 90s | Momentum Speed |
| 5 | Multiplier Boost | 3 | +0.10 base multiplier gain | Runner Speed III OR Momentum Endurance |
| 6L | Momentum Surge | 5 | Momentum x2.5 speed | Multiplier Boost |
| 6R | Evolution Power II | 5 | +1 base evolution power | Multiplier Boost |
| 7 | Auto-Elevation | 7 | Automatic elevation | Momentum Surge OR Evolution Power II |
| 8L | Runner Speed III | 7 | x1.25 global runner speed | Auto-Elevation |
| 8R | Evolution Power II | 7 | +1 base evolution power | Auto-Elevation |
| 9 | Automation | 10 | Auto-summit, auto-ascension | Runner Speed III OR Evolution Power II |
| 10L | Runner Speed IV | 15 | x1.5 global runner speed | Runner Speed III OR Evolution Power II |
| 10R | Evolution Power III | 15 | +2 base evolution power | Runner Speed III OR Evolution Power II |
| 11 | Momentum Mastery | 25 | Momentum x3.0 + 120s duration | Runner Speed IV OR Evolution Power III |
| 12L | Multiplier Boost II | 40 | +0.25 base multiplier gain | Momentum Mastery |
| 12R | Elevation Boost | 40 | Elevation cost -30% | Momentum Mastery |
| 13 | Runner Speed V | 75 | x2.0 global runner speed | Multiplier Boost II OR Elevation Boost |

**Total AP for all nodes:** 286 AP

---

## Strategic Evolution System

Evolution provides a clear benefit with continuous cost progression.

### The Evolution Benefit

**When you evolve a runner:**
- ✅ **Multiplier gain ×evolutionPower** per star (EP applied cleanly per evolution)
- ✅ **Speed level resets to 0** (visual reset, but costs continue)
- ✅ **Costs continue from total level** (no reset, no penalty)

### Evolution Value Analysis (base Evolution Power ×3)

| Stars | Multiplier Gain | Total Levels |
|-------|-----------------|--------------|
| 0★ | +0.10/run | 0-19 |
| 1★ | +0.30/run (×3) | 20-39 |
| 2★ | +0.90/run (×9) | 40-59 |
| 3★ | +2.70/run (×27) | 60-79 |
| 4★ | +8.10/run (×81) | 80-99 |
| 5★ | +24.3/run (×243) | 100+ |

**Key insight:** Each evolution multiplies the multiplier gain by the Evolution Power value. EP is linear up to level 25, then √ growth — always progressing, never capped.

### Strategic Decisions

**Key insight:** Each evolution multiplies the multiplier gain by the Evolution Power value. EP is linear up to level 25 (+0.10/level), then transitions to √ growth — always progressing, never capped.

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

**Situation:** You have massive vexa, multiple runners at different levels

**Option A: Focus on Map 0 (Rouge)**
- Fast completion time = more runs = more multiplier gains
- Push to higher stars for exponential multiplier growth
- **Best for:** Pure idle optimization

**Option B: Spread across all maps**
- Evolve each runner to at least 1★ for the initial boost
- More balanced income sources
- Different visual NPCs running around
- **Best for:** Visual variety and engagement

### Design Goals Achieved

✅ **Evolution scales exponentially**
- Each star multiplies gains by Evolution Power
- Higher Summit Evolution Power makes later stars dramatically stronger

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

- **Minimum vexa generation:** Even idle players make progress
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

## Gems (Global Currency)

Gems are a **global cross-mode currency** stored in `hyvexa-core` and displayed on all HUDs (Parkour, Ascend, Hub).

- **Storage:** `player_gems` table, managed by `GemStore` singleton
- **Display:** Green gem icon + count on right-side info box of every HUD
- **Admin command:** `/gems <set|add|remove|check> <player> [amount]` (OP only)
- **Current sources:** Admin-granted only (future: vote rewards, cosmetics shop)
- **Floor:** Balance cannot go below 0

---

## Technical Notes

### Overflow Protection

- Elevation costs capped at `Long.MAX_VALUE`
- Vexa balances stored as `double` for high precision
- All UI displays use abbreviated notation (K, M, B, T)

### Precision

- Speed multipliers: `double` (floating-point)
- Costs: `long` (64-bit integer)
- Rewards: `double` (to handle large elevation multipliers)

### Formula Consistency

All formulas use consistent growth rates:
- **Elevation multiplier:** level (direct linear multiplier)
- **Elevation cost:** 1.15^(level^0.72) for level<=300, then 1.15^(300^0.72 + (level-300)^0.58) (soft cap at 300)
- **Upgrades:** 2^totalLevel (100% growth per level, smooth and predictable)
- **Evolution gain:** EP^stars, where EP = 3 + 0.10 × level (linear, no ceiling)
- **Summit XP:** (accumulatedVexa / 1B)^power, calibrated for 1Dc = level 1000
- **Summit levels:** level^2 (manageable numbers at all levels)
- **Summit bonuses:** linear (0-25), √ (25-500), ⁴√ (500+)
- **Summit XP cost:** level^2 (below 1000), level^3/1000 (above 1000, continuous at boundary)

Runner upgrade costs use `totalLevel = stars × 20 + speedLevel` to ensure continuous progression after evolution.

---

## Version History

- **2026-02-16 (v18):** Summit XP softcap at level 1000
  - XP cost per level: level^2 (below 1000) -> level^3/1000 (above 1000, continuous)
  - Same vexa reaches fewer levels: old 5000 -> ~3591, old 10000 -> ~6042
  - Summit level hard cap removed (was 1000, now unlimited)
  - Bonus growth zones unchanged: linear (0-25), √ (25-500), ⁴√ (500+)

- **2026-02-09 (v18):** Passive offline rate reduction
  - Offline earnings rate: 25% → 10% (base rate)
  - No Ascendancy Tree node boosts offline rate
  - More balanced offline vs. active play incentives

- **2026-02-15 (v18):** Summit XP recalibration for 1Dc = level 1000
  - Vexa→XP power: 3/7 (~0.4286) → ~0.3552 (derived from 1Dc target)
  - 1 Decillion accumulated vexa now reaches exactly level 1000 (max)
  - Early/mid game slower: 1Qa gives ~135 XP (was 372), 1Qi gives ~1,571 XP (was ~7,196)
  - Progression stretched to fill the full 1B → 1Dc range evenly

- **2026-02-08 (v17):** Elevation accumulated vexa system
  - Elevation now uses accumulated vexa (total earned since last reset) instead of current balance
  - Spending vexa on upgrades no longer reduces elevation potential
  - Accumulated vexa reset on: Elevation, Summit, Ascension
  - Same pattern as Summit accumulated vexa (parallel tracking)

- **2026-02-16 (v17):** Ascend Verticality v1
  - AP multiplier: `1 + completed_challenges` AP per ascension (max x8 with 7 challenges)
  - 3 new challenges: mixed summit malus (5), all summit malus (6), maps 4+5 blocked (7)
  - Old per-challenge permanent rewards removed (x1.5 map5, +10% speed, +20% multi, +1 evo power)
  - Summit level cap removed (was 1000, now unlimited)
  - 6 new skill tree nodes: Runner Speed IV (15 AP), Evolution Power III (15 AP), Momentum Mastery (25 AP), Multiplier Boost II (40 AP), Elevation Boost (40 AP), Runner Speed V (75 AP)

- **2026-02-08 (v16):** Elevation cost reduction
  - Cost curve exponent: 0.77 → 0.72 (early), 0.63 → 0.58 (late, above level 300)
  - Level 1 cost unchanged (34.5K), progressively cheaper at higher levels
  - Level 50: 514K → 311K (-40%), Level 100: 3.8M → 1.4M (-63%), Level 200: 116M → 17M (-85%)
  - Same early game pacing, more accessible mid/late elevation

- **2026-02-07 (v15):** Linear Summit formulas with soft cap
  - All categories: linear up to level 25, then √(excess) growth — no ceiling, just slower
  - Runner Speed: `1 + 0.45 × √level` → `1 + 0.15 × level` (+0.15/level, √ after 25)
  - Multiplier Gain: `1 + 0.5 × level^0.8` → `1 + 0.30 × level` (+0.30/level, √ after 25)
  - Evolution Power: `3 + 1.5 × level/(level+10)` → `3 + 0.10 × level` (+0.10/level, √ after 25)
  - Normal play (level 0-25) fully linear: each level gives exactly the same bonus
  - Past soft cap: growth continues but can't reach absurd values at extreme levels
  - Categories much more balanced: marginal gains within ~25% of each other

- **2026-02-06 (v14):** Summit XP number compression
  - Level exponent: 2.5 → 2.0 (level 50: 17,678 → 2,500 XP per level)
  - Vexa→XP: sqrt → power 3/7 (compensates to preserve same vexa→level mapping)
  - Same vexa still reach the same levels (within ~5%)
  - DB migration: existing XP converted via `old_xp^(6/7)`

- **2026-02-06 (v13):** Evolution Power asymptotic growth
  - EP formula changed from `2 + 0.5 × level^0.8` to `2 + 1.5 × level / (level + 10)`
  - Asymptotic growth toward ~3.5 (was unbounded, reached ×5.15 at level 10, ×21.5 at level 100)
  - EP^stars kept clean (no star dampening) — player sees EP value, each evolution multiplies by it
  - 5★ at Summit 10: ×152 (was ×3,627), at Summit 50: ×354 (was ×448K)

- **2026-02-06 (v12):** Elevation soft cap at level 300
  - Below 300: identical cost curve (1.15^(level^0.77))
  - Above 300: flatter late-game curve (exponent 0.63 instead of 0.77)
  - Removes glass ceiling at high elevation — 100T vexa from level 600 gives +127 levels instead of +12

- **2026-02-06 (v11):** Summit XP based on accumulated vexa + reduced level cost
  - Summit XP now based on vexa accumulated since last Summit/Elevation, not current balance
  - Runner upgrades no longer reduce Summit XP potential (only earning matters)
  - Elevation resets the accumulator (strategic choice preserved)
  - Summit level cost: `level^2.5` (was `level^4`) — reduces gap between levels

- **2026-02-06 (v10):** Flattened elevation cost curve
  - Elevation cost: `30000 × 1.15^(level^0.77)` (was `30000 × 1.15^level`)
  - Cost curve exponent 0.77 (~1/1.3) makes high-level elevation proportionally cheaper
  - Elevation stays attractive throughout the game (~30% gain per elevation consistently)
  - Level = multiplier (1:1) unchanged — only the cost formula changed

- **2026-02-06 (v9):** Summit and Ascension threshold rebalance
  - Summit XP conversion: `sqrt(vexa / 1B)` (was `sqrt(vexa) / 1M`)
  - Minimum vexa for Summit: 1B (was 1T)
  - Ascension threshold: 1Dc (was 10Q)

- **2026-02-05 (v8):** Summit late-game rebalance
  - Summit XP conversion: `sqrt(vexa) / 1,000,000` (was `/ 100`)
  - Minimum vexa for Summit: 1T (was 10K)
  - Summit is now a true late-game system (requires 1T vexa for level 1)
  - Summit now resets everything like elevation (multipliers, runners, map unlocks), keeps only best times

- **2026-02-05 (v7):** Linear elevation multiplier
  - Reverted elevation from exponential (`level × 1.02^level`) to linear (`level`)
  - Level now equals multiplier directly (level 210 = ×210)

- **2026-02-05 (v6):** Evolution and Summit XP rebalance
  - Evolution now grants ×10 per-run multiplier gain (instead of one-time map multiplier boost)
  - Removed Evolution Power one-shot bonus (category kept for future use)
  - Summit XP conversion: `sqrt(vexa) / 100` (diminishing returns, was linear)
  - Summit XP per level: `level^4` (steep scaling, was `100 × level^1.5`)
  - Minimum vexa for Summit: 10,000 (for 1 XP)
  - Late-game Summit scaling: 2T vexa → ~9 levels (was ~1,500+ levels)

- **2026-02-05 (v5):** XP-based Summit system
  - Summit now uses XP instead of vexa thresholds (1000 vexa = 1 XP)
  - XP per level: 100 × level^1.5 (gradual scaling)
  - Replaced Vexa Flow with Multiplier Gain (1 + 0.5 × level^0.8)
  - Runner Speed: 1 + 0.45 × sqrt(level) (diminishing returns)
  - Evolution Power: 2 + 0.5 × level^0.8 (applied on runner evolution, multiplies map multiplier)
  - Summit no longer resets multipliers, runners, or map unlocks (only vexa and elevation)

- **2026-02-05 (v4):** Early-game unlock pacing
  - Added decaying cost boost for levels 0-9 on maps 2+ (first evolution only)
  - Boost scales by map: Orange ×1.5, Jaune ×2.0, Vert ×2.5, Bleu ×3.0
  - Creates more time between early map unlocks without affecting late-game
  - Smooth decay ensures costs always increase (no jarring price drops)

- **2026-02-04 (v3):** Exponential elevation and Summit refactoring
  - Elevation multiplier changed from `level` to `level × 1.02^level` *(reverted in v7)*
  - Summit Vexa Flow changed from additive (+20%/level) to multiplicative (×1.20/level)
  - Summit Manual Mastery renamed to Evolution Power (+0.20 evolution base/level)
  - Evolution Power affects runner multiplier gains via formula: `0.1 × (2 + bonus)^stars`
  - Simplified upgrade cost formula to `5 × 2^level + level × 10` (smooth ~2× growth, no boosts)

- **2026-02-04 (v2):** Continuous cost progression after evolution
  - **Cost formula now uses total levels:** `totalLevel = stars × 20 + speedLevel`
  - Costs no longer reset after evolution (1★ Lv.0 costs same as what 0★ Lv.20 would cost)
  - Removed star multiplier (×2.2 per star) — progression is now inherent to total level
  - Early-game boost (÷4) now only applies to first evolution cycle (0★ levels 0-4)
  - Evolution is now always beneficial: double gains with no cost penalty

- **2026-02-04 (v1):** Major economy rebalance
  - Runner multiplier gains: 0.01 → 0.1 (×10)
  - Elevation base cost: 5,000 → 30,000 (×6)
  - Upgrade base formula: 60× → 20× (reduced from ×6 to ×2 inflation)
  - Added map-specific speed multipliers (10-30% per level)
  - Early-game boost: ÷4 for levels 0-4

- **Previous:** Original balance (slower progression, smaller numbers)
