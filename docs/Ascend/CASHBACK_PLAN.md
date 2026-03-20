# Plan: Add Cashback Upgrade + Decimal Crystals

## Context
Add a "Cashback" pickaxe upgrade to Ascend mine that gives players a percentage of a mined block's crystal value as immediate crystals. Since cashback produces fractional amounts (e.g., 5% of a block worth 1 = 0.05), crystals must change from `long` to `double` with max 2 decimal places.

**Cashback params:** max level 20, +0.5%/level (max 10%), cost: `Math.round(80 * Math.pow(1.22, currentLevel))`

---

## Changes

### 1. `MineUpgradeType.java` — Add CASHBACK enum
**File:** `hyvexa-parkour-ascend/.../mine/data/MineUpgradeType.java`

- Add `CASHBACK(20)` after HASTE
- Add to all switches:
  - `getCost()`: `Math.round(80 * Math.pow(1.22, currentLevel))`
  - `getEffect()`: `level * 0.5` (percentage)
  - `getColumnName()`: `"upgrade_cashback"`
  - `getDisplayName()`: `"Cashback"`
  - `getDescription()`: `"Earn crystals equal to a percentage of each block's value."`
  - `getChance()`: `-1` (not AoE)

### 2. `MinePlayerProgress.java` — Crystals `long` -> `double`
**File:** `hyvexa-parkour-ascend/.../mine/data/MinePlayerProgress.java`

- `private long crystals` -> `private double crystals`
- `getCrystals()` -> returns `double`
- `setCrystals(double value)`
- `addCrystals(double amount)` -> returns `double`. No rounding here — callers round before calling.
- `trySpendCrystals(long cost)` — keep `long` param (costs are always whole numbers), comparison `crystals < cost` works fine
- `sellBlock`, `sellAll`, `sellAllExcept` — internal `crystals += earned` works (long auto-widens to double), return types stay `long`
- `PlayerSaveSnapshot` record: `long crystals` -> `double crystals`

### 3. `FormatUtils.java` — Add `formatDouble` method
**File:** `hyvexa-core/.../common/util/FormatUtils.java`

Add new method:
```java
public static String formatDouble(double value) {
    if (value < 1_000.0) {
        if (value == Math.floor(value) && value < 1e15) {
            return String.valueOf((long) value); // "500"
        }
        return stripTrailingZeros(String.format(Locale.ROOT, "%.2f", value)); // "0.05", "150.5"
    }
    return formatLong(Math.round(value)); // suffix notation for large values
}
```

### 4. `MineHudManager.java` — Update crystal display
**File:** `hyvexa-parkour-ascend/.../mine/hud/MineHudManager.java`

- `MineHudState.lastCrystals`: `long` -> `double`
- `updateCrystals()`: `long crystals` -> `double crystals`, use `FormatUtils.formatDouble(crystals)`

### 5. `MinePlayerStore.java` — Update load/save
**File:** `hyvexa-parkour-ascend/.../mine/data/MinePlayerStore.java`

- **Load:** `rs.getLong("crystals")` -> `rs.getDouble("crystals")`
- **Load:** Add `progress.setUpgradeLevel(MineUpgradeType.CASHBACK, rs.getInt("upgrade_cashback"))` + add column to SELECT
- **Save:** `ps.setLong(2, snapshot.crystals())` -> `ps.setDouble(2, snapshot.crystals())`
- **Save:** Add `upgrade_cashback` column to INSERT/UPDATE SQL + `ps.setInt(13, ...)`

### 6. `AscendDatabaseSetup.java` — DB migration
**File:** `hyvexa-parkour-ascend/.../ascend/data/AscendDatabaseSetup.java`

- Add `{"upgrade_cashback", "INT NOT NULL DEFAULT 0"}` to `ensureMineUpgradeColumns()` array
- Add `migrateCrystalsToDecimal(conn)` method (same pattern as `migrateCoinsToDecimal` at line 1035): check if crystals column type contains "BIGINT" or "DOUBLE", ALTER to `DECIMAL(20,2)`
- Call it after `migrateCrystalsToBigint(conn)` (~line 305)
- Update CREATE TABLE: `crystals BIGINT` -> `crystals DECIMAL(20,2)`

### 7. `MineRewardHelper.java` — Cashback on manual breaks
**File:** `hyvexa-parkour-ascend/.../mine/system/MineRewardHelper.java`

In `rewardBlock()`, after the existing reward logic (before return), add:
```java
int cashbackLevel = mineProgress.getUpgradeLevel(MineUpgradeType.CASHBACK);
if (cashbackLevel > 0) {
    MineConfigStore configStore = mineManager.getConfigStore();
    long blockPrice = configStore.getBlockPrice(blockTypeName);
    double cashbackPercent = MineUpgradeType.CASHBACK.getEffect(cashbackLevel);
    double cashbackAmount = Math.floor(blockPrice * blocksGained * cashbackPercent / 100.0 * 100.0) / 100.0;
    if (cashbackAmount > 0) {
        mineProgress.addCrystals(cashbackAmount);
    }
}
```
Note: `Math.floor` truncates to 2 decimal places. At level 1 (0.5%) on a 1-crystal block: `floor(1*1*0.5/100*100)/100 = floor(0.5)/100 = 0` — correctly gives 0 (too small to represent). At level 2 (1%) on a 1-crystal block: gives 0.01. At level 1 on a 10-crystal block: gives 0.05.

### 8. `MineAoEBreaker.java` — Cashback on AoE breaks
**File:** `hyvexa-parkour-ascend/.../mine/system/MineAoEBreaker.java`

In the AoE block loop (~line 120), after overflow auto-sell, add same cashback logic. Hoist `cashbackLevel` and `cashbackPercent` outside the loop for performance.

### 9. `MinePage.java` — Display cashback as entry bar
**File:** `hyvexa-parkour-ascend/.../mine/ui/MinePage.java`

The UI grid is 2x3 (Slot0-5), no room for a 7th card. Use the entry-bar pattern (like BAG_CAPACITY):
- Do NOT add CASHBACK to `GRID_UPGRADE_ORDER`
- Add `populateCashbackEntry()` method modeled on `populateBagCapacityEntry()`, appending to `#SlotCashback`
- Call it from `populateUpgradeTab()` after `populateBagCapacityEntry()`
- Add to `sendRefresh()`: `cmd.clear("#SlotCashback")`
- Add CASHBACK case to `getEffectDescription()`: `level == 0 ? "No cashback" : String.format("%.1f", effect) + "% crystal return"`
- Add 8th entry to `UPGRADE_ACCENT_COLORS` and `UPGRADE_ACCENT_HEX` (e.g., `"Gold"` / `"#f59e0b"`)

### 9b. `Ascend_MinePage.ui` — Add cashback slot container
**File:** `hyvexa-parkour-ascend/.../resources/Common/UI/Custom/Pages/Ascend_MinePage.ui`

After `#SlotBag` group (~line 326), add:
```
Group { Anchor: (Height: 10); }
Group #SlotCashback {
  Anchor: (Left: 0, Right: 0);
  LayoutMode: Top;
}
```

### 10. `MineCommand.java` — Admin command update
**File:** `hyvexa-parkour-ascend/.../mine/command/MineCommand.java`

- `Long.parseLong(args[1])` -> `Double.parseDouble(args[1])`
- Add `if (!Double.isFinite(amount))` validation after parsing (reject NaN/Infinity)
- Update message to use `FormatUtils.formatDouble(progress.getCrystals())`

### 11. `MineSellPage.java` — Crystal display fix
**File:** `hyvexa-parkour-ascend/.../mine/ui/MineSellPage.java`

- Line 61: `String.valueOf(mineProgress.getCrystals())` -> `FormatUtils.formatDouble(mineProgress.getCrystals())`

### 12. Docs & Changelog
- `docs/Ascend/ECONOMY_BALANCE.md` — Add Cashback upgrade details
- `docs/DATABASE.md` — Update mine_players.crystals type from BIGINT to DECIMAL(20,2)
- `CHANGELOG.md` — Add entry

---

## Design Decisions

### Why DECIMAL(20,2) in DB, not DOUBLE
The repo already migrated `ascend_players.coins` from DOUBLE to DECIMAL(65,2) (see `migrateCoinsToDecimal` at line 1035). Using DOUBLE for crystals would be a regression. DECIMAL(20,2) guarantees exact 2-decimal storage. Java `double` + JDBC `getDouble()`/`setDouble()` works perfectly with MySQL DECIMAL columns.

### Why Math.floor for cashback truncation
`Math.round` would inflate small cashback amounts (0.5% of 1 crystal = 0.005, rounded = 0.01 which is effectively 1%). `Math.floor` truncates correctly — if the amount is too small to represent in 2 decimal places, the player gets 0. This keeps the economy accurate.

### Cashback and "crystals earned" stats
`incrementCrystalsEarned()` is only called from sell pages (MineSellPage, MineBagPage). Cashback bypasses selling entirely — it's passive bonus income. Not tracking it in the stat is intentional (consistent with how the stat represents sell income only).

---

## Verification
1. Build compiles (`./gradlew build`)
2. Existing upgrade purchases still work (integer costs deduct correctly from double crystals)
3. Cashback at level 1 (0.5%) on block worth 1 -> 0 crystals (too small, truncated)
4. Cashback at level 2 (1%) on block worth 1 -> 0.01 crystals
5. Cashback at level 10 (5%) on block worth 1 -> 0.05 crystals
6. HUD shows decimals correctly: "0.05", "150.5", "1.5K"
7. DB migration runs cleanly (BIGINT -> DECIMAL(20,2) preserves existing values)
8. Sell operations still produce correct integer crystal amounts
9. `/mine addcrystals NaN` and `/mine addcrystals Infinity` are rejected
