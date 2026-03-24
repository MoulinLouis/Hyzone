# Test Coverage Plan: Core Module (hyvexa-core)

**Priority:** MEDIUM — Core is a shared library, so regressions here affect ALL modules.
**Module:** `hyvexa-core`
**Test dir:** `hyvexa-core/src/test/java/io/hyvexa/`
**Framework:** JUnit 5 (no Mockito — pure-logic tests only)

## Existing Tests (already done)

| Test Class | Status |
|-----------|--------|
| `BigNumberTest` | Done — factories, arithmetic, comparison, conversion, equality |
| `GhostInterpolationTest` | Done — binary search interpolation, angle wrapping |
| `GhostRecordingTest` | Done — sample management |
| `DailyShopRotationTest` | Done — deterministic rotation |
| `PurgeSkinDefinitionTest` | Done — data model |
| `PurgeSkinRegistryTest` | Done — registry operations |
| `PaginationStateTest` | Done — pagination math, clamping |
| `AssetPathUtilsTest` | Done — path normalization |
| `DailyResetUtilsTest` | Done — UTC midnight, time formatting |
| `DamageBypassRegistryTest` | Done — bypass registry |
| `FormatUtilsTest` | Done — duration/number formatting |
| `CosmeticDefinitionTest` | Done — cosmetic enum |

## New Test Classes — Status

| Test Class | Status |
|-----------|--------|
| `GhostSampleTest` | **Done** — record accessors, toPositionArray, equals/hashCode |
| `ShopTabResultTest` | **Done** — NONE, REFRESH, showConfirm, hideConfirm, null key |
| `WorldConstantsTest` | **Done** — non-blank world IDs, uniqueness, non-blank item IDs |
| `ShopTabRegistryTest` | **Done** — register, dedup, getTab, sorted getTabs, defensive copy |
| `WhitelistRegistryTest` | **Skipped** — 3-line volatile holder; AscendWhitelistManager can't be instantiated in tests (constructor requires HytaleLogger) |

## Original Plan Details

Core has good existing coverage. The remaining gaps are smaller but still worth filling.

---

### 1. `ShopTabRegistryTest`

**File:** `common/shop/ShopTabRegistryTest.java`
**Class under test:** `io.hyvexa.common.shop.ShopTabRegistry`
**Why:** Shop tabs are used in production for cosmetic/skin shops. Registry corruption = broken shop UI.

> **Important:** ShopTabRegistry is a static singleton. Tests must manage shared state carefully. Use `@BeforeEach` to clear the registry if a `clear()` method exists, or document test-order dependency.

> **Dependency check:** ShopTabRegistry stores `ShopTab` objects. Verify that `ShopTab` can be instantiated without Hytale imports. If ShopTab is an interface/abstract class with Hytale dependencies, this test may not be feasible. Check the class before implementing.

#### Test Methods

```
registerAddsTabToRegistry()
```
- Register a tab → `getTab(id)` returns it
- `getTabs()` contains it

```
registerDeduplicatesById()
```
- Register tab with id="shop1"
- Register another tab with id="shop1" (different instance)
- `getTabs().size()` → 1 (not 2)
- `getTab("shop1")` returns the latest registered instance

```
getTabReturnsNullForUnknownId()
```
- `getTab("nonexistent")` → null

```
getTabsReturnsSortedByOrder()
```
- Register tabs with orders 3, 1, 2
- `getTabs()` should return them in order 1, 2, 3

```
getTabsReturnsDefensiveCopy()
```
- Get tabs list, modify it
- Get tabs again → original ordering preserved

---

### 2. `ShopTabResultTest`

**File:** `common/shop/ShopTabResultTest.java`
**Class under test:** `io.hyvexa.common.shop.ShopTabResult`

#### Test Methods

```
noneInstanceHasCorrectTypeAndNullKey()
```
- `ShopTabResult.NONE.getType()` → `Type.NONE`
- `ShopTabResult.NONE.getConfirmKey()` → null

```
refreshInstanceHasCorrectTypeAndNullKey()
```
- `ShopTabResult.REFRESH.getType()` → `Type.REFRESH`
- `ShopTabResult.REFRESH.getConfirmKey()` → null

```
showConfirmHasCorrectTypeAndKey()
```
- `ShopTabResult.showConfirm("purchase_glow")` → type == SHOW_CONFIRM, key == "purchase_glow"

```
hideConfirmHasCorrectTypeAndNullKey()
```
- `ShopTabResult.hideConfirm()` → type == HIDE_CONFIRM, confirmKey == null

```
showConfirmWithNullKeyStillWorks()
```
- `ShopTabResult.showConfirm(null)` → type == SHOW_CONFIRM, key == null (verify no NPE)

---

### 3. `WhitelistRegistryTest`

**File:** `common/whitelist/WhitelistRegistryTest.java`
**Class under test:** `io.hyvexa.common.whitelist.WhitelistRegistry`
**Why:** Whitelist controls server access. Registry bugs = players locked out or unauthorized access.

> **Dependency check:** WhitelistRegistry stores a volatile `Services` holder. Verify what Services contains and whether it can be instantiated in tests.

#### Test Methods

```
configureAndGetRoundTrip()
```
- `configure(services)` then `get()` returns same instance

```
getBeforeConfigureReturnsNull()
```
- Fresh state → `get()` → null

```
clearResetsToNull()
```
- Configure, then `clear()` → `get()` returns null

---

### 4. `WorldConstantsTest`

**File:** `common/WorldConstantsTest.java`
**Class under test:** `io.hyvexa.common.WorldConstants`
**Why:** World IDs are used everywhere for world routing. A wrong constant = teleporting to wrong world.

#### Test Methods

```
allWorldIdsAreNonBlank()
```
- All world ID constants (HUB, PARKOUR, ASCEND, PURGE, RUN_OR_FALL) are non-null, non-empty

```
allWorldIdsAreUnique()
```
- Collect all world IDs into a Set, assert size matches count (no accidental duplicates)

```
allItemIdsAreNonBlank()
```
- All item ID constants are non-null, non-empty

---

### 5. `GhostSampleTest`

**File:** `common/ghost/GhostSampleTest.java`
**Class under test:** `io.hyvexa.common.ghost.GhostSample`

#### Test Methods

```
toPositionArrayReturnsXYZ()
```
- `new GhostSample(1.5, 2.5, 3.5, 90f, 1000L).toPositionArray()` → `[1.5, 2.5, 3.5]`

```
recordAccessorsReturnConstructorValues()
```
- Verify x(), y(), z(), yaw(), timestampMs() match constructor args

```
equalSamplesAreEqual()
```
- Two GhostSample with identical fields → equals() returns true

```
differentSamplesAreNotEqual()
```
- Different coordinates → equals() returns false

---

## Classes NOT Worth Testing (and why)

| Class | Reason |
|-------|--------|
| `ConnectionProvider` | Interface — no logic |
| `ParamBinder` | Functional interface — no logic |
| `RowMapper<T>` | Functional interface — no logic |
| `SQLFunction<T,R>` | Functional interface — no logic |
| `SQLConsumer<T>` | Functional interface — no logic |
| `SQLBiConsumer<T,U>` | Functional interface — no logic |
| `CurrencyStore` | Interface — no logic |
| `PlayerAnalytics` | Interface — no logic |
| `RunOrFallQueueStore` | Interface — no logic |

## Implementation Notes

### Test Conventions
- Same style as existing core tests (see `BigNumberTest`, `FormatUtilsTest`)
- Records use generated equals/hashCode — test those via `assertEquals`

### Testability Constraints
- **ShopTabRegistry static state:** This is a global static registry. Tests must either:
  1. Clear state in `@BeforeEach` (if a clear/reset method exists)
  2. Accept that test order matters (fragile — avoid if possible)
  3. Accept the risk if no cleanup is available, and test in isolation
  - Check if ShopTabRegistry has a `clear()` or package-private reset method before implementing.
- **ShopTab instantiation:** If `ShopTab` is an interface requiring Hytale implementations, create a minimal test stub (anonymous class or record) implementing just the needed methods (getId, getOrder).
- **WhitelistRegistry:** If the `Services` type has Hytale dependencies, use a dummy object or null where possible.

### Execution Order
1. `GhostSampleTest` — trivial record test
2. `ShopTabResultTest` — immutable value type, no state
3. `WorldConstantsTest` — constant validation
4. `WhitelistRegistryTest` — simple volatile holder
5. `ShopTabRegistryTest` — most complex due to static state management
