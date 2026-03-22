# Mine Mode - Production Balance Plan

Proposed values for all tunable mine parameters, ready for a production launch. Values marked **(admin)** are configured via admin UI, not hardcoded. Values marked **(code)** are hardcoded constants.

---

## 1. Zone Regen

| Parameter | Current | Proposed | Source |
|-----------|---------|----------|--------|
| Regen interval | `600s` (10min default) | **120s** (2min) | admin |
| Regen behavior | Full reset, teleport players out | Same | code |

**Rationale:** 10 minutes is far too long — players will strip-mine a zone in 2-3 minutes with a decent pickaxe. 120s keeps the cycle tight: mine for ~90s, zone regens while you sell/manage, come back to fresh blocks.

---

## 2. Block Tables (per layer, admin-configured)

Suggested 3-layer mine setup (top to bottom):

### Layer 1 — Surface (easy, tutorial-friendly)

| Block | Weight | Sell Price | HP |
|-------|--------|------------|-----|
| Stone | 40 | 1 | 1 |
| Copper Ore | 25 | 3 | 1 |
| Iron Ore | 20 | 6 | 2 |
| Silver Ore | 10 | 12 | 2 |
| Gold Ore | 5 | 25 | 3 |

### Layer 2 — Caverns (mid-game)

| Block | Weight | Sell Price | HP |
|-------|--------|------------|-----|
| Stone | 25 | 1 | 1 |
| Iron Ore | 25 | 6 | 2 |
| Silver Ore | 20 | 12 | 2 |
| Gold Ore | 15 | 25 | 3 |
| Crystal Ore | 10 | 60 | 4 |
| Mithril Ore | 5 | 150 | 6 |

### Layer 3 — Depths (late-game, high reward)

| Block | Weight | Sell Price | HP |
|-------|--------|------------|-----|
| Iron Ore | 15 | 6 | 2 |
| Gold Ore | 25 | 25 | 3 |
| Crystal Ore | 25 | 60 | 4 |
| Mithril Ore | 20 | 150 | 6 |
| Ancient Ore | 10 | 400 | 10 |
| Prismatic Ore | 5 | 1,000 | 15 |

**Price philosophy:** Exponential jumps between tiers. Layer 1 averages ~5 crystals/block, Layer 2 ~18, Layer 3 ~90. A player mining Layer 3 earns ~18x more per block than Layer 1 — meaningful progression.

---

## 3. Egg Drop Chances (per layer, admin)

| Layer | Current | Proposed |
|-------|---------|----------|
| Layer 1 (Surface) | `0.5` (50%!) | **0.02** (2%) |
| Layer 2 (Caverns) | `0.5` | **0.035** (3.5%) |
| Layer 3 (Depths) | `0.5` | **0.05** (5%) |

**Rationale:** 50% is a dev-test value. At 2-5%, eggs feel like a real discovery. A player breaking ~200 blocks should find 4-10 eggs per session — enough to feel rewarding without flooding the collection.

---

## 4. Gacha Rarity Weights (code: `EggOpenService`)

| Rarity | Current | Proposed |
|--------|---------|----------|
| Common | 20% | **40%** |
| Uncommon | 20% | **28%** |
| Rare | 20% | **18%** |
| Epic | 20% | **10%** |
| Legendary | 20% | **4%** |

**Rationale:** Equal 20% weights make Legendary trivial. At 4%, a player opening 25 eggs has a ~64% chance of getting at least one Legendary — exciting but not guaranteed. This is standard gacha distribution.

---

## 5. Pickaxe Tiers (code: `PickaxeTier`)

| Tier | Base Damage | Enhance +1 | Enhance +5 | Total at +5 |
|------|-------------|------------|------------|-------------|
| Wood | 1 | 2 | 6 | 6 |
| Stone | 12 | 13 | 17 | 17 |
| Iron | 34 | 35 | 39 | 39 |
| Crystal | 78 | 79 | 83 | 83 |
| Void | 166 | 167 | 171 | 171 |
| Prismatic | 342 | 343 | 347 | 347 |

**Current formula:** `damage = baseDamage + enhancement` — enhancement adds flat +1 per level.

**Verdict:** The base damage scaling is solid (~2.2x per tier). Enhancement is flat +1 which is negligible on higher tiers (+5 on a 342-damage pickaxe is +1.5%). This is fine — enhancement is a stepping stone to the next tier, not a major power source.

### Pickaxe Enhancement Costs (admin)

| Enhancement Level | Suggested Cost |
|-------------------|---------------|
| +1 | 100 crystals |
| +2 | 300 crystals |
| +3 | 800 crystals |
| +4 | 2,000 crystals |
| +5 | 5,000 crystals |

Scale per tier: multiply by tier index (Stone = x1, Iron = x2, Crystal = x4, Void = x8, Prismatic = x16).

### Pickaxe Tier Upgrade Recipes (admin)

| Target Tier | Suggested Recipe |
|-------------|-----------------|
| Stone | 50 Copper Ore + 30 Stone |
| Iron | 40 Iron Ore + 20 Silver Ore |
| Crystal | 30 Crystal Ore + 15 Gold Ore |
| Void | 25 Mithril Ore + 10 Crystal Ore |
| Prismatic | 20 Ancient Ore + 10 Prismatic Ore |

**Design:** Recipes use blocks from the layer where you'd naturally be mining when you're ready for that tier. Forces actual mining investment, not just crystal hoarding.

---

## 6. Block HP vs Pickaxe Damage — Layer Gating

The HP/damage ratio naturally gates layers:

| Block | HP | Wood (1) | Stone (12) | Iron (34) | Crystal (78) |
|-------|-----|----------|-----------|-----------|-------------|
| Stone | 1 | 1 hit | 1 hit | 1 hit | 1 hit |
| Copper | 1 | 1 hit | 1 hit | 1 hit | 1 hit |
| Iron Ore | 2 | 2 hits | 1 hit | 1 hit | 1 hit |
| Silver Ore | 2 | 2 hits | 1 hit | 1 hit | 1 hit |
| Gold Ore | 3 | 3 hits | 1 hit | 1 hit | 1 hit |
| Crystal Ore | 4 | 4 hits | 1 hit | 1 hit | 1 hit |
| Mithril Ore | 6 | 6 hits | 1 hit | 1 hit | 1 hit |
| Ancient Ore | 10 | 10 hits | 1 hit | 1 hit | 1 hit |
| Prismatic Ore | 15 | 15 hits | 2 hits | 1 hit | 1 hit |

**Result:** Wood pickaxe can mine Layer 1 comfortably but Layer 2+ is painful. Stone (12 damage) one-shots everything except Prismatic Ore. This is a good soft-gating — no hard lock, just efficiency difference.

---

## 7. Upgrade Costs & Effects

### Progression Pacing

A new player breaks ~60 blocks/minute (casual pace). Average Layer 1 value = ~5 crystals/block = **~300 crystals/minute**.

| Upgrade | Lv 1 Cost | Lv 5 Cost | Lv 10 Cost | Lv Max Cost | Minutes to afford Lv 1 |
|---------|-----------|-----------|------------|-------------|----------------------|
| Bag Capacity | 25 | 62 | 155 | 23,437 | <1 min |
| Haste | 40 | 82 | 248 | 1,533 | <1 min |
| Conveyor Cap | 30 | 57 | 157 | 1,621 | <1 min |
| Momentum | 50 | 134 | 364 | 7,695 | <1 min |
| Fortune | 60 | 161 | 437 | 9,234 | <1 min |
| Cashback | 80 | 215 | 583 | 5,723 | <1 min |
| Jackhammer | 150 | 514 | — | 1,611 | <1 min |
| Stomp | 200 | 743 | 2,759 | 25,853 | <1 min |
| Blast | 250 | 929 | 3,449 | 32,317 | <1 min |

**Verdict:** Current cost formulas are well-scaled. At ~300 crystals/min (Layer 1), early upgrades are affordable within the first 1-2 minutes. Max-level upgrades require transitioning to deeper layers. No changes needed to the cost formulas — they scale naturally with progression.

### Upgrade Effect Summary (current values, no changes needed)

| Upgrade | Key Effect at Max | Impact |
|---------|-------------------|--------|
| Bag Capacity (50) | 550 slots | Long mining sessions without selling |
| Momentum (25) | 80 combo, +160% damage at max combo | Strong DPS boost for active players |
| Fortune (25) | 50% double drop chance | ~1.5x effective income |
| Haste (20) | +100% mining speed | 2x faster block breaking |
| Jackhammer (10) | 10 block column, ~8.3% proc | Vertical clear bursts |
| Stomp (15) | 4 radius AoE, ~8.3% proc | Layer-clearing bursts |
| Blast (15) | 4 radius sphere, ~8.3% proc | Sphere-clearing bursts |
| Conveyor Cap (25) | 6,000 block buffer | Miners run for hours unattended |
| Cashback (20) | 10% crystal return on every block | Passive income stream |

---

## 8. Miner Slots (admin)

| Parameter | Current Default | Proposed |
|-----------|----------------|----------|
| Number of miner slots | Configurable | **3 slots** |
| Base interval per slot | `5.0s` | **5.0s** (keep) |
| Conveyor speed | `2.0 blocks/s` | **2.0 blocks/s** (keep) |

**Miner interval** is configured per-slot. 5s = 12 blocks/minute base. With 3 miners, that's 36 blocks/minute passive income at base speed.

### Miner Purchase Costs (admin, suggested)

| Slot | Cost |
|------|------|
| Slot 1 | 500 crystals |
| Slot 2 | 3,000 crystals |
| Slot 3 | 15,000 crystals |

**Rationale:** Slot 1 is affordable within 2 minutes of mining. Slot 3 requires significant Layer 2+ investment.

---

## 9. Miner Speed Upgrades (admin, via miner page)

Miners use `speedLevel` and `stars` (from miner rarity) to calculate production rate. The interval per slot is the base; rarity affects which blocks they mine (via rarity block tables).

Since the miner production is driven by the slot's `intervalSeconds` and not directly by rarity speed stats, the key lever is **slot interval** and **rarity block tables**.

### Rarity Block Tables (admin, per layer)

Configure higher-rarity miners to produce more valuable blocks:

| Rarity | Layer 1 Bias | Layer 2 Bias | Layer 3 Bias |
|--------|-------------|-------------|-------------|
| Common | Normal table | Normal table | Normal table |
| Uncommon | +5% Silver | +5% Gold | +5% Crystal |
| Rare | +10% Silver, +5% Gold | +10% Gold, +5% Crystal | +10% Crystal, +5% Mithril |
| Epic | +15% Gold | +15% Crystal | +10% Mithril, +5% Ancient |
| Legendary | +20% Gold | +10% Crystal, +10% Mithril | +10% Ancient, +5% Prismatic |

This makes rarity meaningful — a Legendary miner in Layer 3 produces Ancient/Prismatic Ore significantly more often.

---

## 10. Achievements (code: `MineAchievement`)

| Achievement | Threshold | Reward | Pacing |
|-------------|-----------|--------|--------|
| Novice Miner | 100 blocks | 500 cryst | ~2 min |
| Seasoned Miner | 1,000 blocks | 2,500 cryst | ~15 min |
| Expert Miner | 10,000 blocks | 10,000 cryst | ~2 hours |
| Legendary Miner | 100,000 blocks | 50,000 cryst | ~1 week casual |
| Crystal Collector | 1,000 cryst earned | 500 cryst | ~3 min |
| Crystal Hoarder | 10,000 cryst | 2,500 cryst | ~30 min |
| Crystal Magnate | 100,000 cryst | 10,000 cryst | ~3 hours |
| Crystal Tycoon | 1,000,000 cryst | 50,000 cryst | ~1 week casual |
| Egg Hunter | First egg | 500 cryst | ~3-5 min |
| Golden Discovery | First Legendary | 2,500 cryst | ~25+ eggs (4% chance) |
| Completionist | Max all upgrades | 50,000 cryst | Long-term goal |
| Explorer | Unlock all mines | 10,000 cryst | N/A (single mine) |

**Verdict:** Achievement thresholds and rewards look well-paced. The crystal rewards act as nice progression boosts at the right moments. No changes needed.

---

## 11. Cross-Progression Bonus (code: `MineBonusCalculator`)

| Bonus | Current | Note |
|-------|---------|------|
| All miners placed | +20% multiplier gain | Only active bonus in single-mine mode |
| Runner speed | 1.0 (inactive) | Would need multi-mine system |
| Volt gain | 1.0 (inactive) | Would need multi-mine system |

**Verdict:** +20% for filling all miner slots is a good incentive. Keeps mine engagement relevant to parkour without being mandatory.

---

## 12. Momentum System (code)

| Parameter | Value | Note |
|-----------|-------|------|
| Combo timeout | 3 seconds | Time before combo resets |
| Damage per combo hit | +2% | At 80 combo (max Momentum), +160% damage |
| Block damage timeout | 3 seconds | Multi-HP damage progress resets |

**Verdict:** 3-second timeout is tight enough to reward active play without being punishing. +2% per combo at max 80 combo = 2.6x damage multiplier is strong and satisfying.

---

## Admin Setup Checklist

Things you need to configure via `/ascendadmin` before launch:

1. **Block Prices** — Set sell prices for each block type per the table in Section 2
2. **Block HP** — Set HP for each block type per the table in Section 2
3. **Zone Layers** — Create 3 layers with Y-ranges and block distribution weights
4. **Layer Egg Drop Chances** — Set per-layer egg chance (2%, 3.5%, 5%)
5. **Regen Interval** — Set zone regen to 120 seconds
6. **Miner Slots** — Create 3 slots with positions, intervals (5s), and conveyor speed (2.0)
7. **Miner Slot Costs** — Set purchase prices (500, 3000, 15000)
8. **Pickaxe Tier Recipes** — Set block recipes for each tier upgrade
9. **Pickaxe Enhancement Costs** — Set crystal costs for +1 through +5 per tier
10. **Rarity Block Tables** — Configure per-layer per-rarity block distribution biases

### Code Changes Needed

1. **Gacha weights** (`EggOpenService.java`) — Change from 20/20/20/20/20 to 40/28/18/10/4
2. Everything else is admin-configurable, no code changes required

---

## Expected Player Flow (First 30 Minutes)

| Time | Activity | Crystal Income |
|------|----------|---------------|
| 0-2 min | Mine Layer 1 with Wood pickaxe, learn basics | ~300/min |
| 2-5 min | Buy first upgrades (Bag, Haste), find first egg | ~400/min |
| 5-10 min | Craft Stone pickaxe, start Layer 2 | ~800/min |
| 10-15 min | Buy miner slot 1, assign first miner | ~1,200/min (active + passive) |
| 15-20 min | Craft Iron pickaxe, Layer 2 efficient | ~2,500/min |
| 20-30 min | Push into Layer 3, buy miner slot 2 | ~5,000/min |
| 30+ min | Crystal pickaxe, AoE upgrades kick in, Layer 3 farming | ~10,000+/min |

This creates a smooth ramp where the player always has a clear next goal and feels a power spike every 5-10 minutes.
