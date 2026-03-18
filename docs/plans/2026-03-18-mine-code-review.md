# Mining Mode Code Review

**Date**: 2026-03-18
**Scope**: All 35 Java files in `hyvexa-parkour-ascend/.../mine/` + `MineChestInteraction.java`
**Goal**: Identify AI slop, optimization opportunities, simplifications, and polish items. No breaking changes.

---

## Table of Contents

1. [Duplicated Code (Extract & Reuse)](#1-duplicated-code)
2. [AI Slop / Over-Engineering](#2-ai-slop--over-engineering)
3. [Performance Optimizations](#3-performance-optimizations)
4. [Simplification Opportunities](#4-simplification-opportunities)
5. [Dead Code & Unused Features](#5-dead-code--unused-features)
6. [Thread Safety & Correctness](#6-thread-safety--correctness)
7. [Polish & Minor Issues](#7-polish--minor-issues)

---

## 1. Duplicated Code

### 1.1 `rollFortune()` is copy-pasted 3 times

**Files**:
- `MineBreakSystem.java:154-161`
- `MineDamageSystem.java:152-159`
- `MineAoEBreaker.java:131-138`

All three are identical static methods.

**Fix**: Extract to a shared utility, e.g. a static method on `MineUpgradeType` or a new `MineFortuneRoll` utility class.

```java
// Add to MineUpgradeType or new utility:
public static int rollFortune(int fortuneLevel) {
    if (fortuneLevel <= 0) return 1;
    double tripleChance = fortuneLevel * 0.4 / 100.0;
    double doubleChance = fortuneLevel * 2.0 / 100.0;
    double roll = ThreadLocalRandom.current().nextDouble();
    if (roll < tripleChance) return 3;
    if (roll < tripleChance + doubleChance) return 2;
    return 1;
}
```

**How to update**: Create the method in one place (e.g. `MineUpgradeType.rollFortune(int)`), then replace all three call sites with the shared method.

---

### 1.2 Block reward + inventory + overflow logic duplicated across 3 systems

**Files**:
- `MineBreakSystem.java:131-145` (manual break reward)
- `MineDamageSystem.java:131-149` (damage-based break reward)
- `MineAoEBreaker.java:100-114` (AoE break reward)

All three do: fortune roll -> `addToInventoryUpTo` -> overflow auto-sell -> `markDirty` -> toast -> achievement tracking.

**Fix**: Extract a shared `MineRewardHelper.rewardBlock(UUID, MinePlayerProgress, String blockTypeName, int blocksGained, String mineId, MineManager)` method that handles inventory add, overflow sell, toast, and achievement tracking.

**How to update**:
1. Create `MineRewardHelper` (or add a static method to an existing class like `MineManager`)
2. The method takes `playerId`, `progress`, `blockTypeName`, `blocksGained`, `mineId`, `mineManager`
3. Returns the number of blocks actually stored (for callers that need it)
4. Replace all 3 inline reward sequences with a single call

---

### 1.3 Momentum combo logic duplicated in `MineBreakSystem` and `MineDamageSystem`

**Files**:
- `MineBreakSystem.java:157-170`
- `MineDamageSystem.java:163-178`

Both do: `checkComboExpired()` -> check max combo -> `incrementCombo()` -> show HUD combo.

**Fix**: Include momentum combo handling in the shared `MineRewardHelper` from 1.2, or extract as a separate `handleMomentumCombo(playerId, progress, hudManager)` method.

**How to update**: Add combo handling to the reward helper, or create a `MineComboHelper.tick(...)` called from both systems.

---

### 1.4 `isExpectedPickaxe()` duplicated in `MineBreakSystem` and `MineDamageSystem`

**Files**:
- `MineBreakSystem.java:176-181`
- `MineDamageSystem.java:185-190`

Identical method.

**Fix**: Move to `MinePlayerProgress` as an instance method: `progress.isHoldingExpectedPickaxe(String heldItemId)`.

**How to update**: Add the method to `MinePlayerProgress`, delete from both systems, update call sites.

---

### 1.5 `sendBagFullMessage()` duplicated in `MineBreakSystem` and `MineDamageSystem`

**Files**:
- `MineBreakSystem.java:168-174`
- `MineDamageSystem.java:174-180`

Both have the same cooldown-throttled message pattern with `lastBagFullMessage` maps.

**Fix**: If using the shared reward helper from 1.2, the bag-full message can be handled there. Alternatively, a small `ThrottledMessage` utility could be used.

**How to update**: Fold into the reward helper, passing the `Player` reference.

---

### 1.6 Zone cooldown / regen message duplicated in `MineBreakSystem` and `MineDamageSystem`

**Files**:
- `MineBreakSystem.java:103-112`
- `MineDamageSystem.java:94-103`

Same pattern: check cooldown -> throttle message -> compute remaining seconds.

**Fix**: Extract a `MineZoneMessages.sendRegenMessage(UUID, Player, MineManager, String zoneId, Map<UUID,Long> throttleMap)` helper.

**How to update**: Create the helper, replace both inline blocks.

---

### 1.7 `getBlockTypeAt()` reverse block map in `MineAoEBreaker`

**File**: `MineAoEBreaker.java:127-145`

The `reverseBlockMap` cache uses a `volatile` static field with lazy initialization that is not thread-safe (double-check locking without synchronization — but effectively harmless since the map is immutable once built and repeated building is just wasteful, not incorrect).

**Fix**: Use a proper holder pattern or just accept the benign race. No functional issue, but the `volatile` + null-check pattern looks like AI slop attempting thread safety without understanding the pattern. Consider using a `Lazy<>` wrapper or a simple `synchronized` block.

**How to update**: Either:
- Use a holder class: `private static class Holder { static final Map<...> MAP = buildMap(); }`
- Or add `synchronized` to `getReverseBlockMap()`
- The volatile non-synchronized pattern works in practice here since the map content is deterministic, so building it twice is just wasted work, not a bug.

---

## 2. AI Slop / Over-Engineering

### 2.1 `MineBlockDisplay.getDisplayName()` fallback is overly defensive

**File**: `MineBlockDisplay.java:15-34`

The fallback that splits by `_` and title-cases each word is fine as a fallback, but the `stripNamespace` + null checks suggest an AI generated "handle every edge case" pattern. The namespace stripping is done in both `getItemId()` and `getDisplayName()`.

**Verdict**: Actually functional and reasonable. Low priority. No change needed.

---

### 2.2 `MineBonusCalculator` uses `Set.of("mine2", "mine_2")` for ID matching

**File**: `MineBonusCalculator.java:25-27`

Hardcoded sets like `MINE2_IDS = Set.of("mine2", "mine_2")` are a classic AI hedge — supporting both naming conventions "just in case." In practice, the mine IDs come from the database and will be one format.

**Fix**: Pick one convention. If the DB uses `mine2`, just match `mine2`. The `findMineId()` method already falls back to name matching (`"Mine 2"`) which is the real safety net.

**How to update**: Replace `MINE2_IDS` etc. with simple string constants. Keep the name-based fallback in `findMineId()`.

```java
private static final String MINE2_ID = "mine2";
// In findMineId: first check mineId.equals(MINE2_ID), then name match
```

---

### 2.3 `MineConfigStore` — 24 volatile fields for gate coordinates

**File**: `MineConfigStore.java:32-43`

Two gates (entry/exit) × 12 coordinate fields each = 24 `volatile` fields. This is very verbose.

**Fix**: Create a small `GateConfig` record:

```java
private record GateConfig(
    double minX, double minY, double minZ,
    double maxX, double maxY, double maxZ,
    double destX, double destY, double destZ,
    float destRotX, float destRotY, float destRotZ
) {
    boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
private volatile GateConfig entryGate;
private volatile GateConfig exitGate;
```

**How to update**:
1. Add `GateConfig` record to `MineConfigStore`
2. Replace 24 volatile fields with 2 `volatile GateConfig` fields
3. Update `loadGate()`, `saveEntryGate()`, `saveExitGate()`, `isInsideEntryGate()`, `isInsideExitGate()`, and all getter methods
4. The 24+ getter methods (`getEntryMinX()`, etc.) that `MineGateChecker` calls can either be kept as delegates or `MineGateChecker` can access the record directly
5. **Non-breaking**: All callers just get the same values through different plumbing

---

### 2.4 `MineRobotManager.extractEntityRef()` — reflection-based pair extraction

**File**: `MineRobotManager.java:796-814`

Tries `getFirst`, `getLeft`, `getKey`, `first`, `left` via reflection to extract an entity ref from an unknown pair type. This is a necessary workaround for unknown Hytale API return types, so it's not really slop — it's a pragmatic hack.

**Verdict**: Acceptable workaround. Add a single-line comment explaining *why* reflection is used (the NPC API returns an opaque pair type). No change needed.

---

### 2.5 `MineAchievement.MAX_UPGRADES` description says "3 global upgrades" but there are 7

**File**: `MineAchievement.java:12`

`MAX_UPGRADES("max_upgrades", "Completionist", "Max all 3 global upgrades", 50_000)` — description says "3" but there are 7 `MineUpgradeType` values.

**Fix**: Update the description to "Max all upgrades" or the correct count.

**How to update**: Change the string literal in the enum. No code logic change.

---

## 3. Performance Optimizations

### 3.1 `MineManager.findZoneAt()` — linear scan on every block break

**File**: `MineManager.java:31-39`

Every block break (could be hundreds per second with multiple players) iterates ALL mines and ALL zones to find which zone contains the position.

**Fix**: Since mines/zones are loaded once and rarely change, build a spatial index on startup. Given that zones are axis-aligned boxes, a simple approach is:
- Cache zones in a flat list (they're already small — likely < 20 zones)
- Short-circuit: check the most-recently-hit zone first (per-player cache)

For the current scale (few mines, few zones each), this is fine. But if it ever becomes a bottleneck:

**How to update**: Add a `private volatile MineZone[] flatZoneCache` built on `syncLoad()`. The flat array avoids the outer mine loop and inner zone list iteration. Alternatively, cache the last-hit zone per player in `MineDamageSystem`/`MineBreakSystem`.

---

### 3.2 `MinePlayerProgress.getInventoryTotalLocked()` — streams on every check

**File**: `MinePlayerProgress.java:58-60`

```java
private int getInventoryTotalLocked() {
    return inventory.values().stream().mapToInt(Integer::intValue).sum();
}
```

Called frequently during mining (every block break checks bag capacity). With a small inventory this is fine, but it creates stream overhead each time.

**Fix**: Maintain a running `inventoryCount` field that's incremented/decremented when items are added/removed.

**How to update**:
1. Add `private int inventoryCount = 0;` field
2. In `addToInventoryUpTo()`: `inventoryCount += toAdd;`
3. In `sellAll()`, `sellBlock()`, `sellAllExcept()`, `clearInventory()`: update accordingly
4. In `loadInventoryItem()`: `inventoryCount += amount;`
5. Replace `getInventoryTotalLocked()` with `return inventoryCount;`
6. Keep `synchronized` on all mutators (already the case)

---

### 3.3 `MineHudManager.updateInventory()` — sorts inventory every tick

**File**: `MineHudManager.java:116-146`

Every HUD update (every 50ms per player), it:
1. Gets a copy of the inventory map
2. Sorts it by count descending
3. Builds a string key for change detection

The string key comparison means the sort + key build happen even when nothing changed.

**Fix**: The `buildInventoryKey` change-detection already prevents redundant HUD updates. The cost is the sort + key build — acceptable for < 20 block types. But could be optimized by tracking a dirty flag on `MinePlayerProgress` that's set when inventory changes and cleared when HUD reads it.

**How to update**: Add a `volatile long inventoryVersion` counter to `MinePlayerProgress`. Increment on any inventory mutation. In `MineHudManager.updateInventory()`, check if version changed before rebuilding. Low priority — current approach works fine.

---

### 3.4 `MineAoEBreaker.getReverseBlockMap()` — rebuilt from scratch if null

**File**: `MineAoEBreaker.java:127-140`

The reverse block map is rebuilt by iterating all block types every time it's null. After first call it's cached. Fine for normal operation, but the lazy init with `volatile` (no synchronization) means it could be built multiple times on first use from different threads. Benign but wasteful.

**Fix**: Use lazy holder pattern.

**How to update**:
```java
private static class ReverseBlockMapHolder {
    static final Map<Integer, String> MAP;
    static {
        Map<Integer, String> map = new HashMap<>();
        var assetMap = BlockType.getAssetMap().getAssetMap();
        for (var entry : assetMap.entrySet()) {
            int index = BlockType.getAssetMap().getIndex(entry.getKey());
            if (index >= 0) map.put(index, entry.getKey());
        }
        MAP = Map.copyOf(map);
    }
}
```
**Caveat**: This only works if `BlockType.getAssetMap()` is available at class-load time. If it requires runtime initialization, keep the current pattern but use `synchronized`.

---

## 4. Simplification Opportunities

### 4.1 `MineBreakSystem` and `MineDamageSystem` — two parallel systems for one concern

**Files**: `MineBreakSystem.java` (241 lines), `MineDamageSystem.java` (191 lines)

These two systems handle `BreakBlockEvent` and `DamageBlockEvent` respectively, but share ~80% of their logic. `MineBreakSystem` handles the case where Hytale's vanilla break system fires (for OP players?), while `MineDamageSystem` handles the `DamageBlockEvent` path with multi-HP support.

**Current overlap**:
- Zone lookup
- Unlock check
- Pickaxe validation
- Cooldown/regen check
- Block claim
- Fortune roll
- Inventory add + overflow sell
- Toast + achievement tracking
- Momentum combo
- AoE trigger

**Fix**: Extract the shared post-break reward pipeline (1.2 above). This alone reduces both files significantly. Full unification into one class is possible but higher risk — the two event types have different entry points (`BreakBlockEvent` vs `DamageBlockEvent`).

**How to update**: After extracting the reward helper (finding 1.2), both systems become much thinner:
- `MineBreakSystem`: validates -> claims block -> removes block -> calls `MineRewardHelper.reward()` -> triggers AoE
- `MineDamageSystem`: validates -> records damage hit -> if broken: claims + removes + calls reward -> triggers AoE

---

### 4.2 `MineGateChecker` — `applyHasteSpeed` / `removeHasteSpeed` / `tickHaste` are all no-ops

**File**: `MineGateChecker.java:272-291`

Three methods that do nothing, plus a `playerHasteLevels` map that's maintained but never produces any effect. The TODO says "awaiting Hytale API for player speed modification."

**Fix**: Remove the no-op methods AND the `playerHasteLevels` map. The haste upgrade level is already stored in `MinePlayerProgress` — when the API becomes available, the speed can be read from there directly.

**How to update**:
1. Delete `applyHasteSpeed()`, `removeHasteSpeed()`, `tickHaste()` methods
2. Delete `playerHasteLevels` field
3. Remove the `playerHasteLevels.put()` in `enterMine()` and `playerHasteLevels.remove()` in `exitMine()` and `evict()`
4. Remove calls to `tickHaste()` from wherever the tick loop calls it
5. Keep `MineUpgradeType.HASTE` in the enum — it's still purchasable and stored. Just doesn't have a runtime effect yet.

---

### 4.3 `MineGateChecker` — large class doing too many things

**File**: `MineGateChecker.java` (310 lines)

This class handles:
- AABB gate detection
- Teleportation
- Loading screen transitions (fade in/out state machine)
- HUD swapping
- Inventory swapping (give pickaxe, give menu items)
- Haste speed application
- Mine access permission checks

**Fix**: No refactor needed at current size, but if it grows further, the transition state machine (phases, pending transitions) could be extracted to a `GateTransitionManager`. Low priority.

---

### 4.4 `MineSellPage.gatherAllPrices()` merges ALL mine prices into one map

**File**: `MineSellPage.java:145-156`

`putAll` iterates every mine's price map and merges them. If the same block type has different prices in different mines, the last mine's price wins (arbitrary, depends on sort order).

**Fix**: This is actually a design issue more than a code issue. The sell page should ideally know which mine the player is currently in and use that mine's prices. If the intent is "best price across all mines", the merge should use `Math.max` logic instead.

**How to update**:
- Option A (simple): Track which mine the player is in (already tracked via `MinePlayerProgress.inMine` + current zone). Use only that mine's prices.
- Option B (if intentional): Document that the highest display-order mine's price wins for shared block types.

---

### 4.5 `MineBlockRegistry.getDisplayName()` — linear scan

**File**: `MineBlockRegistry.java:88-93`

Iterates `ALL_BLOCKS` (50+ entries) to find a display name. Called per block break (via toast) and per HUD update.

**Fix**: Add a `Map<String, String>` lookup indexed by `blockTypeId`.

**How to update**:
```java
private static final Map<String, String> DISPLAY_NAME_MAP = new HashMap<>();
// In register(): DISPLAY_NAME_MAP.put(blockTypeId, displayName);
// Replace getDisplayName() body with:
//   return DISPLAY_NAME_MAP.getOrDefault(blockTypeId, blockTypeId);
```

---

## 5. Dead Code & Unused Features

### 5.1 `PickaxeTier.speedMultiplier` — stored but never used for actual speed

**File**: `PickaxeTier.java:4-11`

Each tier has a `speedMultiplier` (1.0x to 5.0x) but this value is never read by `MineDamageSystem` or `MineBreakSystem` to affect mining speed. The `MineUpgradeType.HASTE` upgrade also tracks speed but is a no-op (see 4.2).

**Verdict**: Not dead code — it's displayed in the UI (`MinePage.java:337` shows "Speed: X.Xx"). The value is intentionally set up for when the Hytale API supports speed modification. No change needed, but worth noting that pickaxe speed is cosmetic-only right now.

---

### 5.2 `MinePlayerProgress.PickaxeUpgradeResult.REQUIREMENT_NOT_MET` — never returned

**File**: `MinePlayerProgress.java:204`

The `purchasePickaxeTier()` method at line 191-198 only returns `ALREADY_MAXED`, `INSUFFICIENT_CRYSTALS`, or `SUCCESS`. `REQUIREMENT_NOT_MET` is never returned — the requirement check happens in `MinePage.handleBuyPickaxe()` before calling `purchasePickaxeTier()`.

**Fix**: Either:
- Remove the unused enum value
- Or move the requirement check into `purchasePickaxeTier()` and return it there (would require passing mine data into the method)

**How to update**: Simplest is to just delete `REQUIREMENT_NOT_MET` from the enum. It's not referenced anywhere.

---

### 5.3 `MinePlayerProgress.MineProgress.completedManually` — unclear usage

**File**: `MinePlayerProgress.java:260-261`

The `completedManually` field is persisted to DB and loaded, but I couldn't find any code that reads it to make a gameplay decision. It's set in the DB schema and loaded/saved, but no logic checks `isCompletedManually()`.

**Verdict**: May be intended for future use. Flag it but don't remove — it's persisted data that could be relied on by admin tools or future features.

---

## 6. Thread Safety & Correctness

### 6.1 `MinePlayerProgress` — mixed synchronization strategy

**File**: `MinePlayerProgress.java`

The class uses `synchronized` on methods that touch `inventory` and `crystals`, but uses `ConcurrentHashMap` for `upgradeLevels`, `mineStates`, and `minerStates`. The `volatile` fields `comboCount` and `lastBreakTimeMs` are read/written without synchronization despite being related (a race between read and write of these two fields is possible).

**Concrete risk**: `incrementCombo()` (line 211-213) writes `comboCount++` and `lastBreakTimeMs = now` as two separate non-atomic operations on `volatile` fields. A concurrent read could see a new `comboCount` but old `lastBreakTimeMs`, causing premature combo expiry.

**Fix**: Since combo state is transient (not persisted) and only read/written during active mining by a single player, the practical risk is very low. But if you want correctness:

**How to update**: Either:
- Make `incrementCombo()` and `checkComboExpired()` `synchronized`
- Or combine `comboCount` and `lastBreakTimeMs` into a single immutable record stored in an `AtomicReference`

Low priority — the race would just cause a combo to display incorrectly for one tick.

---

### 6.2 `BlockPositionKey` record in `MineManager` — memory overhead for `ConcurrentHashMap.newKeySet()`

**File**: `MineManager.java:225`

Each broken block creates a `BlockPositionKey` record object. For a mine zone of 10,000 blocks, that's 10,000 small objects in a `ConcurrentHashMap`.

**Fix**: Pack x/y/z into a `long` key (like `BlockDamageTracker` and `MineAoEBreaker` already do with `packPosition`). This eliminates the record allocation and hashing overhead.

**How to update**:
1. Change `brokenBlocks` type from `Map<String, Set<BlockPositionKey>>` to `Map<String, Set<Long>>`
2. Replace `blockKey(x, y, z)` with the pack formula: `((long)(x & 0x3FFFFFF) << 38) | ((long)(y & 0xFFF) << 26) | (z & 0x3FFFFFF)`
3. Delete the `BlockPositionKey` record

**Note**: The pack formula is already used in `BlockDamageTracker.java:69` and `MineAoEBreaker.java:174` — just reuse it.

---

### 6.3 `MineAchievementTracker.queueSave()` — potential missed saves

**File**: `MineAchievementTracker.java:224-227`

```java
private void queueSave() {
    if (pendingSave != null && !pendingSave.isDone()) return;
    pendingSave = HytaleServer.SCHEDULED_EXECUTOR.schedule(...);
}
```

Race condition: if `pendingSave` transitions from not-done to done between the check and the schedule, a save could be skipped. Same pattern in `MinePlayerStore.java:76-79`.

**Practical risk**: Very low — a dirty flag would be picked up on the next queue cycle (player eviction or next markDirty). But the pattern is subtly wrong.

**Fix**: Use `compareAndSet` on an `AtomicBoolean` to guard scheduling:
```java
private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
private void queueSave() {
    if (saveScheduled.compareAndSet(false, true)) {
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            saveScheduled.set(false);
            flushAll();
        }, 5, TimeUnit.SECONDS);
    }
}
```

**How to update**: Replace `pendingSave` field with `AtomicBoolean` in both `MineAchievementTracker` and `MinePlayerStore`.

---

## 7. Polish & Minor Issues

### 7.1 `MinePage.UPGRADE_DISPLAY_NAMES` array is error-prone

**File**: `MinePage.java:81-83`

```java
private static final String[] UPGRADE_DISPLAY_NAMES = {
    "Bag Capacity", "Momentum", "Fortune", "Jackhammer", "Stomp", "Blast", "Haste"
};
```

This array is indexed by `MineUpgradeType.ordinal()`. If the enum order changes, the display names silently break. Used in `getEffectDescription()`, `getUpgradeDescription()`, `buildUpgradeTooltip()`, and `handleBuyUpgrade()`.

**Fix**: Add a `getDisplayName()` method to `MineUpgradeType` enum directly.

**How to update**:
```java
// In MineUpgradeType:
public String getDisplayName() {
    return switch (this) {
        case BAG_CAPACITY -> "Bag Capacity";
        case MOMENTUM -> "Momentum";
        case FORTUNE -> "Fortune";
        case JACKHAMMER -> "Jackhammer";
        case STOMP -> "Stomp";
        case BLAST -> "Blast";
        case HASTE -> "Haste";
    };
}
```
Then replace `UPGRADE_DISPLAY_NAMES[type.ordinal()]` with `type.getDisplayName()` everywhere in `MinePage`. Delete the array.

---

### 7.2 `MinePage` — miner cost formulas are in the UI class, not the data layer

**File**: `MinePage.java:737-748`

```java
private static long getMinerBuyCost() { return 1000L; }
private static long getMinerSpeedCost(int speedLevel, int stars) { ... }
private static long getMinerEvolveCost(int stars) { ... }
```

These economy formulas are only accessible from the UI page. If any other code needs them (e.g., admin tools, balance display), they'd have to duplicate them.

**Fix**: Move to `MinerRobotState` or a `MinerCosts` utility class.

**How to update**: Move the 3 methods to `MinerRobotState` (which already has `getProductionRate`). Update `MinePage` to call `MinerRobotState.getMinerBuyCost()` etc.

---

### 7.3 `MinePage` — `MINER_MAX_SPEED_PER_STAR` and `MINER_MAX_STARS` constants are in the UI

**File**: `MinePage.java:55-56`

These gameplay constants should live in the data layer, not the UI.

**Fix**: Move to `MinerRobotState` or a shared constants class. They're already implicitly used in `MinePlayerProgress.upgradeMinerSpeed()` and `evolveMiner()` which receive them as parameters.

**How to update**: Add constants to `MinerRobotState`, reference from `MinePage` and `MinePlayerProgress` methods that accept them as parameters.

---

### 7.4 `MineCommand` — missing help text for default case

**File**: `MineCommand.java:134`

```java
default -> player.sendMessage(Message.raw("Unknown subcommand. Use: /mine, /mine sell, /mine upgrades, /mine select, /mine achievements"));
```

The `addcrystals` admin command is not listed in the help text. Intentional (hidden admin command), so no change needed. Just noting it.

---

### 7.5 `MineConfigStore` — `loadGate()` uses magic numbers 1 and 2

**File**: `MineConfigStore.java:200-238`

Gate ID 1 = entry, 2 = exit. These are referenced as magic numbers in the SQL query and the `if/else` block.

**Fix**: Add constants:
```java
private static final int GATE_ENTRY = 1;
private static final int GATE_EXIT = 2;
```

**How to update**: Replace `1` and `2` literals in `loadGate()` and `saveGateToDatabase()` with the constants.

---

### 7.6 `MineToastManager` — toast stacking doesn't update `itemId`/`displayName`

**File**: `MineToastManager.java:13-21`

When stacking onto an existing toast (same `blockTypeId`), only `count` and `createdAt` are updated. The `itemId` and `displayName` are final and set from the first occurrence. This is correct since the `blockTypeId` is the same — but worth confirming the `final` fields are intentionally immutable.

**Verdict**: Correct behavior. No change needed.

---

### 7.7 `MineSellPage` doesn't clear `#SellItems` before re-populating on refresh

**File**: `MineSellPage.java:136-143`

`sendRefresh()` calls `populateContent()` which appends entries to `#SellItems`, but never clears the existing entries first. This would cause duplicate entries on refresh.

**Fix**: Add `cmd.clear("#SellItems");` at the start of `sendRefresh()`.

**How to update**:
```java
private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
    UICommandBuilder commandBuilder = new UICommandBuilder();
    UIEventBuilder eventBuilder = new UIEventBuilder();
    commandBuilder.clear("#SellItems");  // Add this line
    populateContent(commandBuilder);
    // ... rest unchanged
}
```

---

## Summary — Priority Ranking

### High Priority (significant code quality improvement)
1. **1.1 + 1.2 + 1.3 + 1.4 + 1.5 + 1.6**: Extract shared reward/combo/fortune/pickaxe-check logic — eliminates ~150 lines of duplication across 3 files
2. **7.7**: Fix MineSellPage duplicate entries on refresh — actual bug
3. **4.2**: Remove haste no-ops — dead code that misleads readers

### Medium Priority (cleanliness, maintainability)
4. **7.1**: Move display names into `MineUpgradeType` enum — prevents ordinal mismatch bugs
5. **7.2 + 7.3**: Move miner costs/constants to data layer — proper separation of concerns
6. **2.3**: Replace 24 gate volatile fields with `GateConfig` record — reduces `MineConfigStore` by ~60 lines
7. **3.2**: Add running `inventoryCount` — eliminates stream overhead per block break
8. **6.2**: Pack block positions as `long` — eliminates record allocation per broken block
9. **2.5**: Fix achievement description ("3 global upgrades" -> correct count)

### Low Priority (nice-to-have polish)
10. **4.5**: Add display name lookup map to `MineBlockRegistry`
11. **6.3**: Fix queueSave race with AtomicBoolean
12. **2.2**: Simplify mine ID sets in `MineBonusCalculator`
13. **7.5**: Add gate ID constants
14. **3.4**: Use lazy holder for reverse block map
15. **4.4**: Clarify sell page price resolution (design decision needed)
16. **5.2**: Remove unused `REQUIREMENT_NOT_MET` enum value
