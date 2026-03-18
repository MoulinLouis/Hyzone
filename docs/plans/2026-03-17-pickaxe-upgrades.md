# Pickaxe Upgrades - Design Plan

Date: 2026-03-17

## Overview

6 leveled pickaxe upgrades purchased with crystals. Mix of power fantasy (visually breaking more blocks) and active play rewards. All upgrades affect manual mining only (no miner interaction).

Each upgrade scales with levels, uses exponential crystal costs, and hooks into the existing `MineBreakSystem` / `MineDamageSystem` pipeline.

---

## Upgrades

### 1. Momentum

**Concept:** Consecutive block breaks build a speed combo. Stop mining for 3 seconds and the combo drops. Rewards sustained active play.

| Property | Value |
|----------|-------|
| Max Level | 25 |
| Max Combo | `5 + level * 3` (8 at Lv1, 80 at Lv25) |
| Speed Bonus | +2% mining damage per combo hit |
| Peak Bonus | +160% at Lv25 full combo |
| Combo Timer | 3 seconds (resets each break) |
| Cost Formula | `50 * 1.22^level` |

**Feedback:**
- HUD: combo counter with timer bar (existing `MineHudManager`)
- Sound: pitch increases at milestones (10, 25, 50 combo) via `PlaySoundEvent2D`
- Particles: `Weather/Magic_Sparks/Magic_Sparks_GS` intensity at player position scales with combo tier

**State:** Transient only (no DB). Fields on `MinePlayerProgress`:
- `int comboCount` - current combo hits
- `long lastBreakTimeMs` - timestamp of last block break

**Hook:** Both `MineBreakSystem` and `MineDamageSystem` — on successful break, increment combo or reset if expired. In `MineDamageSystem`, apply combo speed multiplier to damage: `damage * (1.0 + comboCount * 0.02)`.

---

### 2. Fortune

**Concept:** Chance to get extra blocks from a single block break. You mine 1 block, you receive 2 or 3 in your bag.

| Property | Value |
|----------|-------|
| Max Level | 25 |
| Double drop chance | `level * 2`% (2% at Lv1, 50% at Lv25) |
| Triple drop chance | `level * 0.4`% (0.4% at Lv1, 10% at Lv25) |
| Cost Formula | `60 * 1.22^level` |

**Logic:** Roll triple first, then double. If triple procs, `blocksGained = 3`. Else if double procs, `blocksGained = 2`. Else `blocksGained = 1`.

**Feedback:**
- Toast: shows "x2" or "x3" next to the block icon
- Sound: sparkle SFX on multi-drop
- Particles: `Drop/Legendary/Drop_Legendary` at block position on triple

**State:** None. Pure RNG check on block break.

**Hook:** Both `MineBreakSystem` and `MineDamageSystem`, after block break confirmed, before adding to inventory — replace `blocksGained = 1` with the fortune roll result.

---

### 3. Jackhammer

**Concept:** Breaking a block also breaks the entire column below it (same X/Z, descending Y) within the mine zone. Visually satisfying — one hit carves a vertical shaft.

| Property | Value |
|----------|-------|
| Max Level | 10 |
| Column depth | `level` blocks below (1 at Lv1, 10 at Lv10) |
| Cost Formula | `150 * 1.28^level` |

**Logic:** After breaking a block at (x, y, z), iterate from y-1 down to y-depth. For each position: check in zone, not already broken, not in cooldown, then claim + break + add to inventory.

**Constraints:**
- Column blocks respect bag capacity (excess auto-sold for crystals)
- Multi-HP blocks in the column are skipped (only breaks 1HP blocks)
- Column blocks count for Momentum combo
- Column blocks can proc Fortune (each block rolls independently)
- Does NOT chain with itself (column blocks don't trigger another column)

**Feedback:**
- Particles: dust/debris particle cascade downward from break point
- Sound: rumble SFX scaling with depth
- Camera shake: light, proportional to blocks broken

**State:** None. Level check on break.

**Hook:** Both break systems, after successful block break — trigger column break downward.

---

### 4. Stomp

**Concept:** Breaking a block also breaks all blocks on the same Y-layer within a radius. One hit clears a flat disc around you.

| Property | Value |
|----------|-------|
| Max Level | 15 |
| Radius | `1 + floor(level / 5)` blocks (r1 at Lv1-4, r2 at Lv5-9, r3 at Lv10-14, r4 at Lv15) |
| Cost Formula | `200 * 1.30^level` |

**Radius breakdown (blocks broken, excluding center):**
- r1 = 3x3 = 8 blocks
- r2 = 5x5 = 24 blocks
- r3 = 7x7 = 48 blocks
- r4 = 9x9 = 80 blocks

**Constraints:**
- Same Y-layer only (horizontal spread, no vertical)
- Multi-HP blocks in radius are skipped
- AoE blocks respect bag capacity (excess auto-sold)
- AoE blocks count for Momentum combo
- AoE blocks can proc Fortune
- Does NOT chain with itself

**Feedback:**
- Camera shake: `CameraShakeEffect(0, 0.6f, AccumulationMode.Set)` (medium shake)
- Particles: `_Test/Cinematic/Cinematic/Cinematic_Portal_Appear` at epicenter + dust ring
- Sound: heavy impact/stomp SFX

**State:** None. Level check on break.

**Hook:** Both break systems, after successful block break — trigger horizontal AoE.

---

### 5. Blast

**Concept:** Breaking a block blasts all blocks in a 3D sphere radius around it. Unlike Stomp (flat layer), Blast goes in all directions — up, down, sideways.

| Property | Value |
|----------|-------|
| Max Level | 15 |
| Radius | `1 + floor(level / 5)` blocks (same scaling as Stomp) |
| Cost Formula | `250 * 1.30^level` |

**Radius breakdown (approximate sphere, excluding center):**
- r1 = 3x3x3 = 26 blocks
- r2 = 5x5x5 = 124 blocks (sphere-trimmed ~80)
- r3 = 7x7x7 = sphere-trimmed ~170
- r4 = 9x9x9 = sphere-trimmed ~300

**Constraints:**
- Spherical shape: only break blocks where `distance(block, center) <= radius`
- Multi-HP blocks in radius are skipped
- AoE blocks respect bag capacity (excess auto-sold)
- AoE blocks count for Momentum combo
- AoE blocks can proc Fortune
- Does NOT chain with itself
- More expensive than Stomp because 3D > 2D

**Feedback:**
- Camera shake: `CameraShakeEffect(0, 0.8f, AccumulationMode.Set)` (heavy shake)
- Particles: explosion burst at center
- Sound: explosion SFX

**State:** None. Level check on break.

**Hook:** Both break systems, after successful block break — trigger spherical AoE.

---

### 6. Haste

**Concept:** Increases player movement speed while inside the mine. Move between blocks faster, reach new areas quicker. Pure quality-of-life that scales into power.

| Property | Value |
|----------|-------|
| Max Level | 20 |
| Speed Bonus | `level * 5`% (5% at Lv1, 100% at Lv20) |
| Cost Formula | `40 * 1.20^level` |

**Implementation:** Apply speed modifier via Hytale's `MovementSpeed` component when player enters mine, remove when they leave.

**Feedback:**
- Particles: subtle speed lines at high levels (wind/dash particles at feet)
- No sound needed — the speed itself is the feedback

**State:** None. Applied/removed on mine enter/exit via `MineGateChecker`.

**Hook:** `MineGateChecker` (on mine entry) — set `MovementSpeed` component with multiplier. On mine exit — reset to default.

---

## Synergy Map

```
Momentum (mining speed) ──> more blocks/sec ──> more Fortune rolls
                                             ──> faster mining overall

Fortune (extra drops) ──> more blocks per break ──> more value per hit
                                                ──> fills bag faster (incentivizes Bag Capacity)

Jackhammer (column) ──> each column block procs Fortune independently
                    ──> column blocks feed Momentum combo

Stomp (layer AoE) ──> each AoE block procs Fortune
                  ──> AoE blocks feed Momentum combo

Blast (sphere AoE) ──> each AoE block procs Fortune
                   ──> AoE blocks feed Momentum combo
                   ──> clears more blocks than Stomp (3D vs 2D)

Haste (move speed) ──> reach new blocks faster ──> maintain Momentum combo
                                               ──> reposition for AoE breaks
```

**Player loop:** Move fast (Haste) -> mine block -> column drops below (Jackhammer) -> layer clears around (Stomp) -> sphere explodes (Blast) -> each broken block rolls Fortune -> combo builds (Momentum) -> mine faster -> repeat.

---

## Cost Table Summary

| Upgrade | Base | Growth | Max Lv | Cost Lv1 | Cost Lv10 | Cost Max |
|---------|------|--------|--------|----------|-----------|----------|
| Haste | 40 | 1.20x | 20 | 40 | 248 | 1,530 |
| Momentum | 50 | 1.22x | 25 | 50 | 367 | 8,610 |
| Fortune | 60 | 1.22x | 25 | 60 | 440 | 10,330 |
| Jackhammer | 150 | 1.28x | 10 | 150 | 1,665 | 3,140 |
| Stomp | 200 | 1.30x | 15 | 200 | 2,758 | 9,330 |
| Blast | 250 | 1.30x | 15 | 250 | 3,447 | 11,660 |
| *Bag Capacity* | *25* | *1.20x* | *50* | *25* | *155* | *230,000* |

Costs are provisional. Haste and Momentum are cheapest (utility/speed). Blast is most expensive (biggest AoE impact).

---

## Implementation Architecture

### DB Changes

Add columns to `mine_players` table via `AscendDatabaseSetup.ensureMineUpgradeColumns()`:

```sql
ALTER TABLE mine_players ADD upgrade_momentum INT NOT NULL DEFAULT 0;
ALTER TABLE mine_players ADD upgrade_fortune INT NOT NULL DEFAULT 0;
ALTER TABLE mine_players ADD upgrade_jackhammer INT NOT NULL DEFAULT 0;
ALTER TABLE mine_players ADD upgrade_stomp INT NOT NULL DEFAULT 0;
ALTER TABLE mine_players ADD upgrade_blast INT NOT NULL DEFAULT 0;
ALTER TABLE mine_players ADD upgrade_haste INT NOT NULL DEFAULT 0;
```

### Enum Extension

Extend `MineUpgradeType` with 6 new values. Each defines `maxLevel`, `getCost(level)`, and `getEffect(level)`.

### MinePlayerStore — Load/Save (review fix #1)

`MinePlayerStore` currently hardcodes `bag_capacity_level` in its SELECT and INSERT/UPDATE queries. Must be updated:

**Load (`loadFromDatabase`):** Change SELECT to include all 6 new columns:
```sql
SELECT crystals, bag_capacity_level, upgrade_momentum, upgrade_fortune,
       upgrade_jackhammer, upgrade_stomp, upgrade_blast, upgrade_haste,
       in_mine, pickaxe_tier
FROM mine_players WHERE uuid = ?
```
Then call `progress.setUpgradeLevel(MineUpgradeType.X, rs.getInt("upgrade_x"))` for each.

**Save (`savePlayerSync`):** Add all 6 columns to the INSERT...ON DUPLICATE KEY UPDATE statement and bind from `snapshot.upgradeLevels()`.

**Snapshot (`PlayerSaveSnapshot`):** Already uses `Map<MineUpgradeType, Integer>` — no change needed as long as new enum values are populated.

### MinePage — Upgrade UI (review fix #1)

`MinePage` hardcodes `UPGRADE_ACCENT_COLORS`, `UPGRADE_ACCENT_HEX`, and `UPGRADE_DISPLAY_NAMES` as single-element arrays indexed by enum ordinal. Adding more enum values causes `ArrayIndexOutOfBoundsException`.

**Fix:** Expand these arrays to 7 entries (Bag Capacity + 6 new upgrades):
```java
private static final String[] UPGRADE_ACCENT_COLORS = {
    "Green", "Blue", "Gold", "Red", "Orange", "Violet", "Blue"
};
private static final String[] UPGRADE_ACCENT_HEX = {
    "#10b981", "#3b82f6", "#f59e0b", "#ef4444", "#f97316", "#7c3aed", "#3b82f6"
};
private static final String[] UPGRADE_DISPLAY_NAMES = {
    "Bag Capacity", "Momentum", "Fortune", "Jackhammer", "Stomp", "Blast", "Haste"
};
```

Also update `getEffectDescription(type, level)` with display strings for each new type.

### Bag Overflow Handling (review fix #2)

Both `MineBreakSystem` and `MineDamageSystem` currently abort with "Bag full!" if `canAddToInventory(blocksGained)` fails. This breaks when `blocksGained > 1` (Fortune) or when AoE adds many blocks.

**Fix:** Replace the early-return pattern with partial-store + auto-sell:
```java
// BEFORE (current — blocks the break entirely):
if (!mineProgress.canAddToInventory(blocksGained)) {
    sendBagFullMessage(playerId, player);
    return;
}

// AFTER (new — always break, store what fits, auto-sell overflow):
// (move this AFTER block claim + air set)
int stored = mineProgress.addToInventoryUpTo(blockTypeName, blocksGained);
if (stored < blocksGained) {
    int overflow = blocksGained - stored;
    BigNumber blockPrice = configStore.getBlockPrice(zone.getMineId(), blockTypeName);
    long fallbackCrystals = blockPrice.multiply(BigNumber.of(overflow, 0)).toLong();
    mineProgress.addCrystals(fallbackCrystals);
    if (stored == 0) {
        sendBagFullMessage(playerId, player); // only show message when nothing stored at all
    }
}
```

Note: `addToInventoryUpTo` already exists on `MinePlayerProgress` and handles partial storage. The existing code already uses this pattern further down for the reward step — the fix is removing the early-return guard that prevents the break from happening.

**For AoE blocks (Jackhammer/Stomp/Blast):** The shared utility uses the same `addToInventoryUpTo` + auto-sell pattern. No early return — blocks always break, overflow is auto-sold.

### Haste — Speed Implementation (review fix #3)

`MovementSpeed` component does not exist in the Hytale API. Per `docs/HYTALE_API.md:110-115`, `horizontalSpeedMultiplier` is reset to 1.0 every tick by the engine.

**Correct approach:** Modify `maxHorizontalSpeed` directly via `Unsafe`:
```java
// On mine entry — apply haste
long offset = unsafe.objectFieldOffset(controllerClass.getDeclaredField("maxHorizontalSpeed"));
double baseSpeed = unsafe.getDouble(controller, offset);
double hasteMultiplier = 1.0 + (hasteLevel * 0.05); // +5% per level
unsafe.putDouble(controller, offset, baseSpeed * hasteMultiplier);

// On mine exit — restore original speed
unsafe.putDouble(controller, offset, baseSpeed);
```

**Implementation notes:**
- Store original `maxHorizontalSpeed` value per player on mine entry so it can be restored exactly on exit
- Apply in `MineGateChecker` on entry, remove on exit and on disconnect
- Need an implementation spike to confirm Unsafe field access works reliably on the player's movement controller (same pattern documented in HYTALE_API.md for NPC speed)
- If Unsafe approach proves unreliable on players, fallback: use a tick-based system that re-applies `horizontalSpeedMultiplier` every tick (ugly but guaranteed to work)

### Momentum HUD (review fix #4)

The plan requires a combo counter + timer bar in the mine HUD, but `MineHudManager` and `Ascend_MineHud.ui` don't have these elements.

**Required changes:**

1. **`Ascend_MineHud.ui`:** Add new elements:
   - `#ComboWrap` (Group, initially hidden) containing:
     - `#ComboCount` (Label) — displays "x15" combo number
     - `#ComboTimer` (Group with fill bar) — visual countdown of 3s timer
   - Position: near center of screen, below crosshair area

2. **`MineHudManager`:** Add methods:
   - `showCombo(UUID playerId, int comboCount, float timerPercent)` — shows/updates combo display
   - `hideCombo(UUID playerId)` — hides combo display when timer expires
   - Call `showCombo` from both break systems after incrementing combo
   - Schedule `hideCombo` via a 3-second delayed check (if no new break occurs)

3. **Update frequency:** Only update HUD on combo changes (not every tick). The timer bar can use a single update on break (set to full) + a hide after 3s timeout. No per-tick polling needed.

### Shared AoE Break Utility (review fix #5)

The pseudo-code must check block HP before breaking to avoid bypassing multi-HP durability:

```java
int breakBlocksAt(List<Vector3i> positions, MineZone zone, MinePlayerProgress progress,
                  UUID playerId, World world, int fortuneLevel) {
    int totalBroken = 0;
    for (Vector3i pos : positions) {
        if (!zone.contains(pos)) continue;

        // Check block HP — skip multi-HP blocks
        String blockTypeId = getBlockTypeAt(world, pos);
        if (blockTypeId == null) continue;
        int blockHp = zone.getBlockHp(blockTypeId, pos.getY());
        if (blockHp > 1) continue; // multi-HP blocks resist AoE

        if (!mineManager.tryClaimBlock(zone.getId(), pos.x, pos.y, pos.z)) continue;

        // Set to air
        setBlockToAir(world, pos);

        // Roll fortune
        int blocksGained = rollFortune(fortuneLevel); // 1, 2, or 3

        // Add to inventory — excess auto-sold
        int stored = progress.addToInventoryUpTo(blockTypeId, blocksGained);
        if (stored < blocksGained) {
            int overflow = blocksGained - stored;
            BigNumber price = configStore.getBlockPrice(zone.getMineId(), blockTypeId);
            long crystals = price.multiply(BigNumber.of(overflow, 0)).toLong();
            progress.addCrystals(crystals);
        }

        totalBroken += blocksGained;
    }
    return totalBroken;
}
```

Each upgrade builds a different position list:
- **Jackhammer:** vertical column below `(x, y-1..y-depth, z)`
- **Stomp:** horizontal disc at same Y `(x-r..x+r, y, z-r..z+r)`
- **Blast:** sphere `where distance(pos, center) <= radius`

### Transient State

Add to `MinePlayerProgress` (no DB persistence needed):
```java
// Momentum only
private int comboCount;
private long lastBreakTimeMs;
```

All other upgrades are stateless (level lookup + RNG or direct application).

### System Hooks

| System | Upgrades Applied |
|--------|-----------------|
| `MineDamageSystem` | Momentum (speed multiplier) |
| `MineBreakSystem` | Momentum (combo tracking) |
| Both (after break) | Fortune (drop multiplier), Jackhammer (column break), Stomp (layer AoE), Blast (sphere AoE), Momentum combo increment |
| `MineGateChecker` | Haste (speed on enter/exit) |

### Feedback Systems

All visual/audio feedback uses existing APIs:
- `CameraShakeEffect` - already used in project
- `SpawnParticleSystem` - documented in HYTALE_API.md
- `PlaySoundEvent2D` / `PlaySoundEvent3D` - documented in HYTALE_API.md

---

## Implementation Order

From simplest to most complex:

1. **Enum + DB + Store + UI wiring** — Add all 6 enum values, DB columns, load/save in `MinePlayerStore`, expand `MinePage` arrays. This is the foundation — nothing works without it.

2. **Fortune** — Pure RNG check + `blocksGained` modifier. Requires the bag overflow fix (review fix #2) in both break systems.

3. **Haste** — Speed via Unsafe on mine enter/exit. Needs implementation spike to validate approach on player entities.

4. **Momentum** — Transient combo state + timer + HUD elements (`Ascend_MineHud.ui` + `MineHudManager`).

5. **Shared AoE utility** — Extract `breakBlocksAt()` with HP check + Fortune roll + overflow handling.

6. **Jackhammer** — Column break (linear position list). Uses shared AoE utility.

7. **Stomp** — Layer AoE (2D position list). Uses shared AoE utility.

8. **Blast** — Sphere AoE (3D position list + distance check). Most blocks affected — needs performance testing.

---

## Open Questions (for playtesting)

1. **Stomp + Blast stacking:** Both trigger on every break. Should they stack (Stomp clears layer AND Blast clears sphere), or should only the bigger one trigger? Current design: both trigger independently — could be extremely powerful at high levels.
2. **Jackhammer + Stomp/Blast interaction:** Column-broken blocks trigger Stomp/Blast? Current design: no chaining — only the player's direct break triggers AoE.
3. **Fortune on AoE blocks:** Each AoE-broken block rolls Fortune independently. At Fortune Lv25 (50% double) + Blast r4 (~300 blocks), that's ~450 blocks per break. May need a cap or Fortune-on-AoE nerf.
4. **Haste speed cap:** +100% speed at Lv20 — is that too fast for the mine environment? Might need testing for collision/movement feel.
5. **Cost scaling:** Are upgrade costs balanced relative to crystal income at each progression stage?
6. **Haste Unsafe approach:** Needs implementation spike — if Unsafe field write on player movement controller doesn't stick, fallback to per-tick `horizontalSpeedMultiplier` re-application.
