<!-- Last verified against code: 2026-03-22 -->
# Ascend Mode - Economy Balance Documentation

Last updated: 2026-03-22

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
8. [Transcendence System](#transcendence-system-4th-prestige)
9. [Design Philosophy](#design-philosophy)
10. [Vexa (Global Currency)](#vexa-global-currency)
11. [Feathers (Parkour Currency)](#feathers-parkour-currency)
12. [Mine Economy](#mine-economy)

---

## Multiplier System

### Manual Run Multiplier
- **Gain per completion:** 5× the runner's multiplier increment for that map
- **Applies to:** Manual completions only
- **Affected by:** Stars, Evolution Power, Summit bonuses (same as runner, then ×5)

### Runner Multiplier (Automatic)
- **Base gain per completion:** +0.1
- **Evolution bonus:** ×Evolution Power per star (EP base = 3.0, + 0.10 per Summit level)
- **Star scaling (at base EP 3.0):**
  - 0★: +0.10 per completion (base)
  - 1★: +0.30 per completion (×3)
  - 2★: +0.90 per completion (×9)
  - 3★: +2.70 per completion (×27)
  - 4★: +8.10 per completion (×81)
  - 5★: +24.3 per completion (×243)
- **With Summit Multiplier Gain bonus:** Base increment × bonus
  - Example at level 10 (×4.00 bonus): 0★ = +0.40/run, 1★ = +1.20/run

### Multiplier Slots
- **Total slots:** 5 (first 5 maps by display order; Map 6 Gold has a runner but does not contribute to the multiplier product)
- **Product formula:** Total multiplier = product of all active map multipliers

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
| Gold | 5 | +10% | +200% |

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
| Gold | ×3.5 | Level 10 |

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
| Gold | 5 | +5 | ×4.7 |

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
- **Evolution value comes from multiplier gains:** Each star multiplies the gain by Evolution Power (base ×3: 0.1 → 0.3 → 0.9 → 2.7...) without affecting cost progression

---

## Elevation System

### Multiplier Formula

**Elevation multiplier = level** (direct linear, `max(1, level)`)

Elevation uses a level-based prestige system where higher levels give proportionally better multipliers:
- Level 1 → x1 multiplier
- Level 10 → x10 multiplier
- Level 50 → x50 multiplier
- Level 100 → x100 multiplier
- Level 200 → x200 multiplier
- Level 500 → x500 multiplier

**Note:** Simple and predictable. Double the level = double the multiplier.

### Cost Formula

```
For level <= 300:  cost = 30,000 × 1.15^(level^0.72)
For level > 300:   cost = 30,000 × 1.15^(300^0.72 + (level-300)^0.58)
```

**Base cost:** 30,000 volt
**Growth rate:** Non-linear — flattens the curve at high levels
**Cost curve exponent:** 0.72 (early game, level <= 300), 0.58 (late game, level > 300)
**Soft cap:** Level 300 — above this, the late-game exponent (0.58) kicks in for even flatter growth

At low levels this behaves almost identically to `1.15^level`. At high levels the effective exponent grows much slower, so each subsequent level is proportionally cheaper. The two-phase formula ensures identical early game while making late-game elevation much more accessible.

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

### Accumulated Volt

Elevation uses **accumulated volt** (total volt earned since last reset) instead of the player's current volt balance. This means spending volt on runner upgrades does NOT reduce your elevation potential — only earning matters.

**Increment:** Every time volt is earned (manual runs, runner completions, admin adds), the accumulated counter increases.

**Reset to 0 on:**
- Elevation (after purchasing levels)
- Summit (full reset)
- Ascension (full reset)

---

## Map Economics

### Base Volt Rewards (Per Manual Completion)

| Map | Display Order | Base Reward | With ×100 Multiplier |
|-----|---------------|-------------|---------------------|
| Rouge | 0 | 1 volt | 100 volt |
| Orange | 1 | 5 volt | 500 volt |
| Jaune | 2 | 25 volt | 2,500 volt |
| Vert | 3 | 100 volt | 10,000 volt |
| Bleu | 4 | 500 volt | 50,000 volt |
| Gold | 5 | 2,500 volt | 250,000 volt |

**Actual reward formula:**
```
reward = baseReward × digitsProduct × elevation
```

Where:
- `digitsProduct` = Product of first 5 map multipliers (includes C1/C7 permanent map base bonuses)
- `elevation` = Current elevation multiplier (includes C8 reward x1.25 if earned)

### Map Unlock System

Maps unlock progressively based on runner level, not volt price.

**Unlock condition:** Maps unlock automatically when the runner on the **previous map** reaches level 5.

| Map | Display Order | Unlock Requirement |
|-----|---------------|--------------------|
| Rouge | 0 | Free (starting map, always unlocked) |
| Orange | 1 | Map 0 runner reaches level 5 |
| Jaune | 2 | Map 1 runner reaches level 5 |
| Vert | 3 | Map 2 runner reaches level 5 |
| Bleu | 4 | Map 3 runner reaches level 5 |
| Gold | 5 | Map 4 runner reaches level 5 + Transcendence Milestone 1 |

**Constant:** `MAP_UNLOCK_REQUIRED_RUNNER_LEVEL = 5`

**Once unlocked:** Maps stay permanently unlocked (even if runner is evolved/reset to level 0)

### Runner Purchase Prices

**All maps:** Free (0 volt)

Runners have no volt cost after map unlock. They can be purchased for free once:
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
| Gold | 5 | 68 seconds | 22.7 seconds |

**Actual run time:** Uses player's personal best (ghost recording) as baseline, modified by speed upgrades.

---

## Expected Progression Timeline

### Early Game (0-5 minutes)

**Phase:** Initial runner acquisition and first upgrades

- Complete Rouge map manually (~30 seconds)
- Buy first runner (free)
- Earn ~50-100 volt from automatic completions
- Purchase first 3 speed upgrades (~5 + 15 + 35 = 55 volt)
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
- Payouts per run: 1M-10M+ volt
- Upgrade costs: hundreds of thousands to millions
- Multiple map multipliers at 50-100+

**Design goal:** Full exponential idle game experience, satisfying number growth.

### Summit/Ascension (Late game)

**Phase:** Prestige systems and meta progression

- Summit threshold: 1 billion volt minimum for first level
- Ascension threshold: 1 Decillion (1Dc) volt
- Permanent bonuses and skill trees
- Long-term progression hooks

---

## Summit System

Summit converts volt into XP for permanent category bonuses, resetting volt, elevation, multipliers, runners, and map unlocks (preserves best times only).

### XP System

**Volt to XP conversion:** `(volt / 1B)^power` where power is calibrated so 1Dc = level 1000

| Volt | XP Gained |
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

**Minimum volt for 1 XP:** 1 billion volt (1B)
**Level 1000 target:** 1 Decillion (10^33) accumulated volt

**XP per level formula:**
- Below level 1000: `level^2`
- Above level 1000: `level^4 / 1,000,000` (continuous at 1000, steeper curve)

| Level | XP Required | Cumulative XP |
|-------|-------------|---------------|
| 1 | 1 | 1 |
| 10 | 100 | 385 |
| 50 | 2,500 | 42,925 |
| 1000 | 1,000,000 | 333,833,500 |
| 2000 | 16,000,000 | 6.5B |
| 5000 | 625,000,000 | 625B |
| 10000 | 10,000,000,000 | 20T |

**Expected level gains by volt (from level 0):**

| Volt | XP | Approx Level |
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
| 1e36 | 580.1M | 1,173 |
| 1e40 | 1.2B | 1,400 |
| 1e50 | 7.6B | 2,064 |
| 1e60 | 48.3B | 2,993 |
| 1e70 | 304.5B | 4,329 |
| 1e80 | 1.9T | 6,258 |
| 1e100 | 76.5T | 13,076 |

### What Summit Resets

Summit performs a full reset similar to Elevation:

**Reset (lost):**
- Volt → 0
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

**Four bonus growth zones:**
- **Level 0-25 (soft cap):** Linear growth — full increment per level
- **Level 25-500 (deep cap):** √ growth — diminishing returns
- **Level 500-1000:** ⁴√ growth — heavy diminishing returns
- **Level 1000+ (post-cap):** `anchorAt1000 × (level / 1000)^0.3` — soft diminishing returns

**Piecewise volt-to-XP:** Below 1Dc, the original power formula applies (`(volt/1B)^0.3552`). Above 1Dc, XP growth slows drastically: `XP_at_1Dc + ((volt/1Dc)^0.08 - 1) × XP_at_1Dc`. This keeps post-1000 progression meaningful (level 13K at 1e100 volt) without runaway scaling. Combined with the ^0.3 bonus curve, a level 10000 player gets ~x33 Multiplier Gain vs x16.5 at level 1000 — a solid but bounded improvement.

### Runner Speed

**Formula:** `1 + 0.15 × level` (linear 0-25, √ growth 25-500, ⁴√ growth 500-1000, post-cap 1000+)

Multiplies runner completion speed (inversely affects run time).

| Level | Speed Multiplier | Zone |
|-------|------------------|------|
| 0 | ×1.00 | linear |
| 10 | ×2.50 | linear |
| 25 | ×4.75 | soft cap |
| 50 | ×5.50 | √ growth |
| 100 | ×6.05 | √ growth |
| 500 | ×8.02 | deep cap |
| 1000 | ×8.73 | ⁴√ / post-cap |
| 2000 | ×10.75 | post-cap |
| 5000 | ×14.15 | post-cap |
| 10000 | ×17.42 | post-cap |

### Multiplier Gain

**Formula:** `1 + 0.30 × level` (linear 0-25, √ growth 25-500, ⁴√ growth 500-1000, post-cap 1000+)

Multiplies the per-run multiplier increment for runners.

| Level | Gain Multiplier | 0★ Increment | 1★ Increment | Zone |
|-------|-----------------|--------------|---------------|------|
| 0 | ×1.00 | +0.10/run | +0.30/run | linear |
| 10 | ×4.00 | +0.40/run | +1.20/run | linear |
| 25 | ×8.50 | +0.85/run | +2.55/run | soft cap |
| 50 | ×10.00 | +1.00/run | +3.00/run | √ growth |
| 100 | ×11.10 | +1.11/run | +3.33/run | √ growth |
| 500 | ×15.04 | +1.50/run | +4.51/run | deep cap |
| 1000 | ×16.46 | +1.65/run | +4.94/run | ⁴√ / post-cap |
| 2000 | ×20.26 | +2.03/run | +6.08/run | post-cap |
| 5000 | ×26.67 | +2.67/run | +8.00/run | post-cap |
| 10000 | ×32.84 | +3.28/run | +9.85/run | post-cap |

*Note: 1★+ increments shown assume base Evolution Power (×3). Higher Evolution Power increases per star.*

### Evolution Power

**Formula:** `3 + 0.10 × level` (linear 0-25, √ growth 25-500, ⁴√ growth 500-1000, post-cap 1000+)

Each Summit level gives a flat EP boost up to level 25, then transitions to slower growth. Post-1000, soft scaling ensures each level adds meaningful but bounded evolution power.

| Level | Evolution Power | 0★ | 3★ | 5★ | Zone |
|-------|-----------------|------|------|------|------|
| 0 | ×3.00 | 0.10 | 2.70 | 24.3 | linear |
| 10 | ×4.00 | 0.10 | 25.60 | 409.6 | linear |
| 25 | ×5.50 | 0.10 | 141.42 | 4,278 | soft cap |
| 50 | ×6.00 | 0.10 | 216.00 | 7,776 | √ growth |
| 100 | ×6.37 | 0.10 | 286.32 | 11,604 | √ growth |
| 500 | ×7.68 | 0.10 | 681.07 | 40,165 | deep cap |
| 1000 | ×8.15 | 0.10 | 891.65 | 59,259 | ⁴√ / post-cap |
| 2000 | ×10.04 | 0.10 | 2,048 | 206,353 | post-cap |
| 5000 | ×13.21 | 0.10 | 6,151 | 1.1M | post-cap |
| 10000 | ×16.27 | 0.10 | 14,132 | 3.7M | post-cap |

**Formula:** `increment = 0.1 × evolutionPower^stars × multiplierGainBonus`

---

## Ascension System

Ascension is the ultimate prestige. Requires 1 Decillion (1Dc = 10^33) volt. Resets everything except best times, skill tree, achievements, and challenge completions.

### AP (Ascension Points)

**Formula:** `AP per ascension = 1 + completed_challenges`

| Challenges Completed | AP Multiplier | AP per Ascension |
|---------------------|---------------|------------------|
| 0 | x1 | 1 |
| 4 | x5 | 5 |
| 8 (all) | x9 | 9 |

### Challenges

8 challenges, each granting +1 to the AP multiplier when completed. Sequential unlock (must complete 1-N before N+1).

| Challenge | Malus | Permanent Reward | Color |
|-----------|-------|------------------|-------|
| 1 | Map 5 locked | Map 5 base multiplier x1.5 | Green |
| 2 | Runner Speed /3 | Runner Speed x1.10 | Orange |
| 3 | Multiplier Gain /4 | Multiplier Gain x1.10 | Blue |
| 4 | Evolution Power /5 | Evolution Power +0.50 | Red |
| 5 | Runner Speed /2 + Multiplier Gain /2 | Runner Speed x1.05 + Multiplier Gain x1.05 | Violet |
| 6 | Runner Speed /4 + Multiplier Gain /4 | Runner Speed x1.05 + Multiplier Gain x1.05 + Evolution Power +0.25 | Pink |
| 7 | All Summit bonuses /2 + Maps 4 & 5 locked | Maps 4 & 5 base multiplier x1.5 | Orange |
| 8 | No Elevation or Summit | Elevation x1.25 + All Summit bonuses x1.25 | Cyan |

**Break Ascension:** Unlocked after completing all 8 challenges. Suppresses auto-ascension at 1Dc.

### Ascendancy Tree

Permanent skill nodes purchased with AP. Prerequisites use OR logic (any one parent unlocks the child). 19 nodes total.

| Node | Cost | Effect | Prerequisites |
|------|------|--------|---------------|
| Auto-Upgrade + Momentum | 1 | Auto-upgrade runners & momentum speed boost on manual runs | — |
| Auto-Evolution | 1 | Runners auto-evolve at max speed level | Auto-Upgrade + Momentum |
| Runner Speed Boost | 1 | x1.1 global runner speed | Auto-Evolution |
| Evolution Power+ | 1 | +1 base evolution power | Auto-Evolution |
| Runner Speed II | 1 | x1.2 global runner speed | Runner Speed Boost OR Evolution Power+ |
| Auto-Summit | 1 | Automatic summit with per-category target levels | Runner Speed II |
| Auto-Elevation | 1 | Automatic elevation with configurable multiplier targets | Runner Speed II |
| Ascension Challenges | 1 | Unlock Ascension Challenges | Auto-Summit OR Auto-Elevation |
| Momentum Surge | 10 | Momentum boost x2 -> x2.5 | Ascension Challenges |
| Momentum Endurance | 10 | Momentum 60s -> 90s | Ascension Challenges |
| Multiplier Boost | 25 | +0.10 base multiplier gain | Momentum Surge OR Momentum Endurance |
| Runner Speed III | 50 | x1.3 global runner speed | Multiplier Boost |
| Evolution Power II | 50 | +1 base evolution power | Multiplier Boost |
| Runner Speed IV | 100 | x1.5 global runner speed | Runner Speed III OR Evolution Power II |
| Evolution Power III | 100 | +2 base evolution power | Runner Speed III OR Evolution Power II |
| Momentum Mastery | 200 | Momentum x3.0 + 120s duration | Runner Speed IV OR Evolution Power III |
| Multiplier Boost II | 400 | +0.25 base multiplier gain | Momentum Mastery |
| Auto Ascend | 400 | Automatically ascend at 1Dc | Momentum Mastery |
| Runner Speed V | 1000 | x2.0 global runner speed | Multiplier Boost II OR Auto Ascend |

**Total AP for all nodes:** 2,353 AP

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

**Situation:** You have massive volt, multiple runners at different levels

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

## Transcendence System (4th Prestige)

Transcendence is the ultimate endgame prestige. Requires 1 Googol (1e100 = 10^100) volt with BREAK_ASCENSION active and all 8 challenges completed.

### Trigger
- **Manual opt-in** via Transcendence NPC or `/transcend` command
- **Threshold:** 1e100 volt (1 Googol)
- **Requirements:** BREAK_ASCENSION active + all 8 challenge rewards completed

### What Gets Reset
- Volt, elevation, ascension count
- Skill tree (AP + all unlocked nodes)
- Summit XP (all categories)
- Challenge completions and records
- Map progress (runners, unlocks, multipliers)
- All automation toggles

### What Is Preserved
- Best times (ghost recordings)
- Achievements
- Transcendence count (permanent)
- Lifetime stats (totalVexaEarned, totalManualRuns)

### Milestones
| Count | Milestone | Reward |
|-------|-----------|--------|
| 1 | First Transcendence | Permanently unlock Map 6 (Gold) |

### Map 6 (Gold)
- **Base reward:** 2,500 volt
- **Base run time:** 68 seconds
- **Cost multiplier:** x4.7
- **Early-level boost:** x3.5
- **Accent color:** Gold (#f59e0b)
- **Unlock:** Transcendence Milestone 1 + Map 5 runner level 5

---

## Design Philosophy

### Core Principles

1. **Exponential Growth:** Numbers should feel like they're exploding, not crawling
2. **Strategic Depth:** Multiple valid approaches (focus one map vs. spread investment)
3. **Smooth Onboarding:** First 2-3 minutes should feel immediately rewarding
4. **No Dead Ends:** Every upgrade path remains viable throughout progression
5. **Prestige Value:** Higher-tier systems (elevation, summit, ascension) feel meaningful

### Balance Constraints

- **Minimum volt generation:** Even idle players make progress
- **Maximum speed:** Runners capped at level 20 per evolution cycle
- **Cost scaling:** Exponential but continuous - no resets, no surprises
- **Evolution benefit:** ×Evolution Power multiplier gain per star (EP base 3.0 + 0.10 per Summit level)
  - Costs continue from total levels purchased (stars × 20 + speedLevel)
  - Evolution is always beneficial - strategic choice is which map to prioritize

### Inflation Strategy

- **Income:** ×10 inflation on runner multiplier gains (0.01 → 0.1)
- **Costs:** ×2 base inflation on upgrades, ×6 on elevations
- **Net effect:** ~50% faster progression than pre-rebalance, much bigger numbers

---

## Vexa (Global Currency)

Vexa is a **global cross-mode currency** stored in `hyvexa-core` and displayed on all HUDs (Parkour, Ascend, Hub).

- **Storage:** `player_vexa` table, managed by `VexaStore` singleton
- **Display:** Green vexa icon + count on right-side info box of every HUD
- **Admin command:** `/vexa <set|add|remove|check> <player> [amount]` (OP only)
- **Current sources:** Admin-granted only (future: vote rewards, cosmetics shop)
- **Floor:** Balance cannot go below 0

---

## Technical Notes

### Overflow Protection

- All large values (volt, costs, rewards) use `BigNumber` (mantissa + base-10 exponent), no overflow
- Elevation costs computed in log10 space, capped at 10^(10^9) as practical limit
- All UI displays use abbreviated notation (K, M, B, T, ...)

### Precision

- Speed multipliers: `double` (floating-point)
- Costs: `BigNumber` (arbitrary magnitude)
- Rewards: `BigNumber` (handles large multiplier products × elevation)

### Formula Consistency

All formulas use consistent growth rates:
- **Elevation multiplier:** level (direct linear multiplier)
- **Elevation cost:** 1.15^(level^0.72) for level<=300, then 1.15^(300^0.72 + (level-300)^0.58) (soft cap at 300)
- **Upgrades:** 2^totalLevel (100% growth per level, smooth and predictable)
- **Evolution gain:** EP^stars, where EP = Summit Evolution Power bonus (same growth zones as other Summit bonuses)
- **Summit XP:** (accumulatedVolt / 1B)^power, calibrated for 1Dc = level 1000
- **Summit levels:** level^2 (manageable numbers at all levels)
- **Summit bonuses:** linear (0-25), √ (25-500), ⁴√ (500-1000), ^0.3 post-cap (1000+)
- **Summit XP cost:** level^2 (below 1000), level^4/1M (above 1000, continuous at boundary)
- **Summit XP storage:** `double` (no cap — unlimited progression)

Runner upgrade costs use `totalLevel = stars × 20 + speedLevel` to ensure continuous progression after evolution.
