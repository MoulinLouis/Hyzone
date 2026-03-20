# Plan: Rework Pickaxe Enhancement & Tier Upgrade System

## Context

The current pickaxe system is a simple 6-tier ladder where each tier costs crystals. There's no granular progression within tiers, no damage scaling from pickaxes, and tier upgrades don't involve collecting blocks. The user wants a richer two-axis progression: **enhancement levels** (+0 to +5, costing crystals) within each tier, and **tier upgrades** (requiring specific mined blocks, configurable via admin UI). Damage follows a snowball formula where tier upgrades double total damage.

## Damage Formula (Snowball)

```
base(0) = 1
base(n) = (base(n-1) + 5) * 2

Damage = tierBaseDamage(tier) + enhancement

Tier progression:
  WOOD    base=1   -> +0=1   +5=6
  STONE   base=12  -> +0=12  +5=17
  IRON    base=34  -> +0=34  +5=39
  CRYSTAL base=78  -> +0=78  +5=83
  VOID    base=166 -> +0=166 +5=171
  PRISM   base=342 -> +0=342 +5=347
```

Enhancement is free by default; admin can set crystal costs per tier/level via admin UI.

---

## Step 1: Database Schema

**File:** `ascend/data/AscendDatabaseSetup.java`

Add after existing `ensurePickaxeTierColumn`, following the same idempotent pattern:

1. New column via `ensurePickaxeEnhancementColumn(conn)`:
   - Guard with `columnExists(conn, "mine_players", "pickaxe_enhancement")`
   - `ALTER TABLE mine_players ADD COLUMN pickaxe_enhancement INT NOT NULL DEFAULT 0`
2. New table `pickaxe_tier_recipes` (CREATE TABLE IF NOT EXISTS — idempotent):
   ```sql
   CREATE TABLE IF NOT EXISTS pickaxe_tier_recipes (
       tier INT NOT NULL,
       block_type_id VARCHAR(64) NOT NULL,
       amount INT NOT NULL DEFAULT 1,
       PRIMARY KEY (tier, block_type_id)
   )
   ```
3. New table `pickaxe_enhance_costs`:
   ```sql
   CREATE TABLE IF NOT EXISTS pickaxe_enhance_costs (
       tier INT NOT NULL,
       level INT NOT NULL,
       crystal_cost BIGINT NOT NULL DEFAULT 0,
       PRIMARY KEY (tier, level)
   )
   ```

---

## Step 2: PickaxeTier Enum Rework

**File:** `ascend/mine/data/PickaxeTier.java`

- Remove `speedMultiplier` and `unlockCost` fields (no longer used)
- Remove `meetsRequirement()` and `requirementDescription`
- Keep `tier`, `displayName`, `itemId`, `fromTier()`, `next()`
- Add `MAX_ENHANCEMENT = 5` constant
- Add `getBaseDamage()`: precomputed snowball values `[1, 12, 34, 78, 166, 342]`
- Add `getDisplayName(int enhancement)`: returns `"Wood Pickaxe +3"` (omit `+0`)

---

## Step 3: MineConfigStore — Load/Save Recipes & Enhance Costs

**File:** `ascend/mine/data/MineConfigStore.java`

Add two new maps following `blockPrices` pattern:

1. `Map<Integer, Map<String, Integer>> tierRecipes` — **targetTier** -> (blockTypeId -> amount)
   - Key convention: the `tier` key is the **target tier** (the tier you're upgrading TO). E.g. tier=1 means "recipe to reach STONE". Tier 0 (WOOD) has no recipe (it's the starter tier).
   - `loadTierRecipes(Connection)` called from `syncLoad()`
   - `saveTierRecipe(int targetTier, String blockTypeId, int amount)` — UPSERT + cache. Validates targetTier >= 1.
   - `removeTierRecipe(int targetTier, String blockTypeId)` — DELETE + cache
   - `getTierRecipe(int targetTier)` — returns map or empty

2. `Map<Integer, Map<Integer, Long>> enhanceCosts` — tier -> (level -> crystalCost)
   - `loadEnhanceCosts(Connection)` called from `syncLoad()`
   - `saveEnhanceCost(int tier, int level, long cost)` — UPSERT + cache
   - `removeEnhanceCost(int tier, int level)` — DELETE + cache
   - `getEnhanceCost(int tier, int level)` — returns cost (default 0 = free)

---

## Step 4: MinePlayerProgress — Enhancement Field & Damage

**File:** `ascend/mine/data/MinePlayerProgress.java`

1. Add field: `private volatile int pickaxeEnhancement;`
2. Getter/setter: `getPickaxeEnhancement()`, `setPickaxeEnhancement(int)`
3. `getPickaxeDamage()`:
   ```java
   public int getPickaxeDamage() {
       return getPickaxeTierEnum().getBaseDamage() + pickaxeEnhancement;
   }
   ```
4. New method `purchasePickaxeEnhancement(long cost)`:
   - Check enhancement < MAX_ENHANCEMENT
   - If cost > 0, call `trySpendCrystals(cost)`
   - Increment `pickaxeEnhancement`
   - Return `PickaxeEnhanceResult` enum: `SUCCESS`, `ALREADY_MAXED`, `INSUFFICIENT_CRYSTALS`

5. Rework `purchasePickaxeTier()` → `upgradePickaxeTier(Map<String, Integer> requiredBlocks)`:
   - Check enhancement == MAX_ENHANCEMENT (must be at +5)
   - Check next tier exists
   - If `requiredBlocks` is empty → block upgrade (return `NOT_CONFIGURED`)
   - Check `hasInventoryBlocks(requiredBlocks)`
   - Remove blocks from inventory via new `removeInventoryBlocks(Map<String, Integer>)`
   - Set `pickaxeTier = next.getTier()`, reset `pickaxeEnhancement = 0`
   - Return updated `PickaxeUpgradeResult`: `SUCCESS`, `ALREADY_MAXED`, `NOT_AT_MAX_ENHANCEMENT`, `MISSING_BLOCKS`, `NOT_CONFIGURED`

6. Add `removeInventoryBlocks(Map<String, Integer> required)` — synchronized, removes from `inventory` map **and decrements `inventoryCount`** for each removed block (critical: bag capacity is tracked separately via `inventoryCount`, same pattern as `sellBlock()` which does `inventoryCount -= count`)
7. Add `hasInventoryBlocks(Map<String, Integer> required)` — checks all blocks present in sufficient quantity

8. Update `PlayerSaveSnapshot` record: add `pickaxeEnhancement` field
9. Update `createSaveSnapshot()`: include `pickaxeEnhancement`

---

## Step 5: MinePlayerStore — Persist Enhancement

**File:** `ascend/mine/data/MinePlayerStore.java`

1. `loadFromDatabase()` (line 82): add `pickaxe_enhancement` to SELECT, call `progress.setPickaxeEnhancement(rs.getInt("pickaxe_enhancement"))`
2. `savePlayerSync()` (line 190): add `pickaxe_enhancement` to INSERT/UPDATE SQL, bind `snapshot.pickaxeEnhancement()`

---

## Step 6: MineDamageSystem — Integrate Pickaxe Damage

**File:** `ascend/mine/system/MineDamageSystem.java`

At line 87-88, change:
```java
// Before:
double damageMultiplier = mineProgress.getMomentumMultiplier();

// After:
int pickaxeDamage = mineProgress.getPickaxeDamage();
double damageMultiplier = pickaxeDamage * mineProgress.getMomentumMultiplier();
```

This feeds pickaxe damage into the existing `BlockDamageTracker.recordHit()` which already handles the multiplier.

---

## Step 7: MinePage — Rework Player Pickaxe Card

**File:** `ascend/mine/ui/MinePage.java`

### 7a: Rework `populatePickaxeCard()`

The card has two states:

**State A — Enhancement mode** (enhancement < 5):
- TierName: `"Wood Pickaxe +3"`
- SpeedText → DamageText: `"Damage: 4"`
- RequirementText: `"Next: +4"`
- ActionText: `"Enhance"` / ActionPrice: `"150 cryst"` (or `"Free"` if cost == 0)
- Button binds to `BUTTON_ENHANCE_PICKAXE`

**State B — Tier upgrade mode** (enhancement == 5, next tier exists):
- TierName: `"Wood Pickaxe +5"`
- DamageText: `"Damage: 6"`
- RequirementText: block requirements text `"3x Coal, 2x Iron"` (or `"Not configured"` if empty recipe)
- ActionText: `"Upgrade"` / ActionPrice: tier name
- Button binds to `BUTTON_BUY_PICKAXE`
- Disabled if missing blocks or recipe not configured

**State C — Maxed** (PRISMATIC +5):
- Show "Maxed!" as before

### 7b: Add `handleEnhancePickaxe()`

- Get cost from `configStore.getEnhanceCost(tier, enhancement + 1)`
- Call `mineProgress.purchasePickaxeEnhancement(cost)`
- Handle results, send chat message
- **Call `markDirty()` on success** (same pattern as existing `handleBuyPickaxe` at line 481)
- Refresh page

### 7c: Rework `handleBuyPickaxe()`

- Get recipe from `configStore.getTierRecipe(nextTier.getTier())` (target tier convention)
- Call `mineProgress.upgradePickaxeTier(recipe)`
- Handle `MISSING_BLOCKS`, `NOT_CONFIGURED`, etc.
- **Call `markDirty()` on success** (inventory blocks consumed + tier changed must be persisted)
- Call `swapPickaxeItem()` for new tier's item
- Refresh page

### 7d: Update `handleActionButton()`

- Add dispatch for `BUTTON_ENHANCE_PICKAXE` constant

### 7e: Update `handleResetAll()`

- Reset `pickaxeEnhancement` to 0 alongside `pickaxeTier`

---

## Step 8: Pickaxe Card UI Template

**File:** `resources/Common/UI/Custom/Pages/Ascend_MinePagePickaxeCard.ui`

- Rename `#SpeedText` to `#DamageText` (or reuse as-is, just change the text from Java)
- No structural changes needed — the existing layout (TierName, SpeedText, RequirementText, button with TierLabel/ActionText/ActionPrice) works for both enhance and upgrade modes

---

## Step 9: Admin UI — Pickaxe Config Page

### 9a: Add "Pickaxe" button to Admin Panel

**File:** `ui/AscendAdminPanelPage.java`
- Add `BUTTON_PICKAXE = "Pickaxe"` constant
- Add button binding for `#PickaxeButton`
- Add `openPickaxe()` handler that opens `PickaxeAdminPage`

**File:** `resources/Common/UI/Custom/Pages/Ascend_AdminPanel.ui`
- Add `#PickaxeButton` TextButton on a **second row** below the existing 4 buttons (the current panel is 480px wide with four 96px buttons + gaps, no room for a 5th on the same row)
- Increase panel Height to accommodate the new row

### 9b: Create PickaxeAdminPage

**New file:** `ascend/mine/ui/PickaxeAdminPage.java`

Admin page extending `InteractiveCustomUIPage` (same pattern as `MineAdminPage`). Shows:

- Tier selector (buttons for each tier 0-5, or prev/next navigation)
- Current tier info: name, base damage
- **Recipe section**: list of block requirements for upgrading TO the selected tier (targetTier convention — selecting STONE shows what blocks are needed to reach STONE, not what STONE costs to leave)
  - Each entry: `"Coal x3"` with a remove button
  - Text field + amount field + "Add" button to add recipe entries
- **Enhancement costs section**: list of costs for levels 1-5
  - Each entry: `"Level 1: 150 cryst"`
  - Text field for cost + "Set" button per level

Data operations: read/write via `MineConfigStore` methods from Step 3.

### 9c: Create PickaxeAdminPage UI template

**New file:** `resources/Common/UI/Custom/Pages/Ascend_PickaxeAdmin.ui`

Layout following `Ascend_MineAdmin.ui` style:
- Header with title "Pickaxe Config" + Close button
- Tier navigation (tabs or prev/next for tiers 0-5)
- Two sections: "Tier Recipe" and "Enhancement Costs"
- Text fields for input + action buttons

---

## Execution Order

1. AscendDatabaseSetup (schema)
2. PickaxeTier (enum rework)
3. MineConfigStore (load/save config)
4. MinePlayerProgress (enhancement + damage)
5. MinePlayerStore (persistence)
6. MineDamageSystem (damage integration)
7. Ascend_MinePagePickaxeCard.ui (minor UI template update)
8. MinePage (player UI rework)
9. Ascend_AdminPanel.ui + AscendAdminPanelPage (add Pickaxe button)
10. Ascend_PickaxeAdmin.ui + PickaxeAdminPage (new admin page)

---

## Key Files

| File | Change |
|------|--------|
| `ascend/data/AscendDatabaseSetup.java` | New column + 2 tables |
| `ascend/mine/data/PickaxeTier.java` | Remove speed/cost, add baseDamage |
| `ascend/mine/data/MineConfigStore.java` | Load/save recipes + enhance costs |
| `ascend/mine/data/MinePlayerProgress.java` | Enhancement field, damage calc, new purchase methods |
| `ascend/mine/data/MinePlayerStore.java` | Persist enhancement |
| `ascend/mine/system/MineDamageSystem.java` | Integrate pickaxe damage |
| `ascend/mine/ui/MinePage.java` | Rework pickaxe card for enhance/upgrade |
| `ascend/ui/AscendAdminPanelPage.java` | Add Pickaxe button |
| `ascend/mine/ui/PickaxeAdminPage.java` | **NEW** — admin config page |
| `resources/.../Ascend_MinePagePickaxeCard.ui` | Minor label reuse |
| `resources/.../Ascend_AdminPanel.ui` | Add Pickaxe button |
| `resources/.../Ascend_PickaxeAdmin.ui` | **NEW** — admin page template |

## Verification

1. **Compile check**: Owner runs build — verify no compile errors reported
2. **DB migration**: On first boot, new column + tables are created automatically (idempotent)
3. **Player flow**:
   - New player starts with WOOD +0, damage=1
   - Enhance 5 times (free by default) -> WOOD +5, damage=6
   - Admin configures recipe for STONE via admin UI
   - Player mines required blocks, clicks upgrade -> STONE +0, damage=12
   - Pickaxe item swaps in hotbar
4. **Admin flow**:
   - `/as admin` -> click "Pickaxe" -> PickaxeAdminPage opens
   - Select tier 1 (STONE), add recipe "Coal x5", "Iron x2"
   - Set enhancement costs: level 1=100, level 2=200, etc.
   - Config persists to DB and reloads on restart
5. **Damage**: Mine a block — damage output matches formula (visible via block HP depletion speed)
6. **Reset**: OP reset button resets both tier and enhancement to 0
