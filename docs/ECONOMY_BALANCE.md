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

### Base Formula

```
baseCost = 20 × 2.4^(level + mapOffset) + (level + mapOffset) × 12
```

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

**Levels 0-4:** Cost divided by 4

This ensures smooth onboarding during the first 2-3 minutes of gameplay.

### Star-Based Progression

Upgrade costs scale with evolution level:

**Star multiplier:** `×2.2^stars`

| Stars | Cost Multiplier | Gain Multiplier | Cost/Gain Ratio |
|-------|-----------------|-----------------|-----------------|
| 0★ | ×1.00 | ×1 (0.1 base) | 1.00 |
| 1★ | ×2.20 | ×2 (0.2) | 1.10 |
| 2★ | ×4.84 | ×4 (0.4) | 1.21 |
| 3★ | ×10.65 | ×8 (0.8) | 1.33 |
| 4★ | ×23.43 | ×16 (1.6) | 1.46 |
| 5★ | ×51.54 | ×32 (3.2) | 1.61 |

**Design rationale:** Evolution is **strategically inefficient** but never punitive:
- **Cost scales faster than gain** (×2.2 vs ×2), creating a 10-61% premium per evolution
- **Break-even:** ~10-15 completions to recoup investment (2-5 minutes depending on map)
- **Long-term profitable:** Higher gain rate always compensates over time
- **Strategic depth:** Players choose between evolving (patience) vs. maxing current level (immediate gains)

### Example Costs (Map 0, Rouge)

**0★ Progression:**
- Level 0→1: ~5 coins
- Level 1→2: ~15 coins
- Level 2→3: ~35 coins
- Level 3→4: ~77 coins (early-game boost ends)
- Level 4→5: ~168 coins
- Level 5→6: ~738 coins (normal formula)
- Level 10→11: ~49,000 coins

**1★ Progression (same levels, ×2.2 cost):**
- Level 0→1: ~11 coins (+10% vs 0★)
- Level 5→6: ~1,624 coins (+120% vs 0★)
- Level 10→11: ~108,000 coins (+120% vs 0★)

**3★ Progression (×10.65 cost):**
- Level 0→1: ~53 coins
- Level 10→11: ~522,000 coins

**5★ Progression (×51.54 cost):**
- Level 0→1: ~258 coins
- Level 10→11: ~2.5M coins

**Evolution Reset:** Speed level resets to 0 when evolving, but costs continue scaling with star count.

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

Evolution creates meaningful decisions rather than being an automatic upgrade path.

### The Evolution Tradeoff

**When you evolve a runner:**
- ✅ **Multiplier gain doubles** (0.1 → 0.2 → 0.4 → 0.8 → 1.6 → 3.2)
- ✅ **Speed level resets to 0** (must rebuild from scratch)
- ⚠️ **Upgrade costs increase ×2.2** (not ×2, creating 10-61% inefficiency)

### Cost/Gain Analysis

| Evolution | Gain Increase | Cost Increase | Efficiency | Break-Even |
|-----------|---------------|---------------|------------|------------|
| 0★ → 1★ | ×2 (0.1→0.2) | ×2.2 | 90.9% | ~10 runs |
| 1★ → 2★ | ×2 (0.2→0.4) | ×2.2 | 82.6% | ~12 runs |
| 2★ → 3★ | ×2 (0.4→0.8) | ×2.2 | 75.2% | ~14 runs |
| 3★ → 4★ | ×2 (0.8→1.6) | ×2.2 | 68.5% | ~15 runs |
| 4★ → 5★ | ×2 (1.6→3.2) | ×2.2 | 62.1% | ~15 runs |

**Efficiency formula:** `gainIncrease / costIncrease = 2.0 / 2.2 = 0.909` (decreasing as stars increase)

### Strategic Decisions

**Early Stars (0★ → 2★):**
- **Low inefficiency** (~10-20%)
- **Fast break-even** (~10-12 completions)
- **Optimal strategy:** Evolve as soon as possible
- **No meaningful choice:** Evolution is clearly superior

**Mid Stars (2★ → 3★):**
- **Moderate inefficiency** (~33%)
- **Medium break-even** (~14 completions)
- **Meaningful choice emerges:**
  - **Evolve now:** Higher long-term gain, requires patience
  - **Max 2★ first:** Immediate power, delays long-term scaling
- **Strategy matters:** Different maps at different star levels

**Late Stars (3★ → 5★):**
- **High inefficiency** (~46-61%)
- **Longer break-even** (~15 completions, 5-10 minutes)
- **Deep strategic choice:**
  - **Focus evolution:** Push one map to 5★ for maximum multiplier
  - **Spread investment:** Multiple maps at 3-4★ for balanced income
  - **Map priority:** Which map deserves 5★ first?

### Example Scenario

**Situation:** You have 1M coins, Map 0 at 2★ Lv.20

**Option A: Evolve to 3★**
- Cost to reach 3★ Lv.5: ~150K coins
- Multiplier gain after 20 runs: +16 (0.8 × 20)
- Time to break-even: ~3-5 minutes
- **Long-term:** Best multiplier growth

**Option B: Stay at 2★, upgrade other maps**
- Use 1M to level up Maps 1-4
- Immediate multiplier gains across board
- No waiting for break-even
- **Short-term:** More immediate power

**Verdict:** No clear winner - depends on current elevation, other maps' levels, and player patience.

### Design Goals Achieved

✅ **Evolution is strategic, not automatic**
- Early game: still mostly straightforward (ratio ~1.1)
- Mid/late game: meaningful choices with tradeoffs

✅ **Never punitive**
- Break-even always achievable within minutes
- Long-term profitability guaranteed
- No "trap" upgrades

✅ **Adds depth without complexity**
- Simple rule: cost grows faster than gain
- Players naturally discover optimal strategies
- No hidden mechanics or confusing interactions

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
- **Maximum speed:** Runners capped at level 20 to prevent overflow
- **Cost scaling:** Exponential but predictable, no sudden walls
- **Evolution tradeoff:** Reset speed level but double multiplier gain at 10-61% cost premium
  - Short-term inefficiency creates strategic decision points
  - Long-term profitability ensures evolution is never a trap
  - Break-even occurs within 10-15 completions (2-5 minutes)

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
- **Upgrades:** 2.4^level (140% growth)
- **Star gain:** 2^stars (100% growth per evolution)
- **Star cost:** 2.2^stars (120% growth per evolution)

The intentional desynchronization (2.2 vs 2.0) creates strategic depth while maintaining predictable scaling.

---

## Version History

- **2026-02-04:** Major economy rebalance
  - Runner multiplier gains: 0.01 → 0.1 (×10)
  - Elevation base cost: 5,000 → 30,000 (×6)
  - Upgrade base formula: 60× → 20× (reduced from ×6 to ×2 inflation)
  - Added star-based cost progression (×2.2 per star, desynchronized from ×2 gain)
  - Added map-specific speed multipliers (10-30% per level)
  - Early-game boost: ÷4 for levels 0-4
  - **Strategic evolution system:** Cost/gain ratio 1.10→1.61 makes evolution a meaningful choice

- **Previous:** Original balance (slower progression, smaller numbers)
