# hyvexa-purge Refactoring Plan

Verified issues only. False positives removed after source verification.

---

## 1. Remove test commands from production

**Files:**
- `hyvexa-purge/src/main/java/io/hyvexa/purge/HyvexaPurgePlugin.java` (lines 175-176)
- `hyvexa-purge/src/main/java/io/hyvexa/purge/command/CamTestCommand.java` (entire file)
- `hyvexa-purge/src/main/java/io/hyvexa/purge/command/SetAmmoCommand.java` (entire file)

**Problem:** Two developer test commands (`CamTestCommand`, `SetAmmoCommand`) are registered as live server commands at lines 175-176. Comments in both files explicitly say "Test command."

**Plan:**
1. Delete line 175: `this.getCommandRegistry().registerCommand(new SetAmmoCommand());`
2. Delete line 176: `this.getCommandRegistry().registerCommand(new CamTestCommand());`
3. Delete `CamTestCommand.java` (216 lines)
4. Delete `SetAmmoCommand.java` (97 lines)

---

## 2. Fix silent exception swallowing on DB shutdown

**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/HyvexaPurgePlugin.java`

**Problem:** Line 314 catches Exception with an empty body: `catch (Exception e) { /* Purge DB shutdown */ }`. DB shutdown errors are completely invisible.

**Plan:**
1. Replace line 314:
   ```java
   // Before:
   catch (Exception e) { /* Purge DB shutdown */ }
   // After:
   catch (Exception e) { LOGGER.atWarning().withCause(e).log("Purge DB shutdown error"); }
   ```

---

## 3. Extract transactional purchase helper (3 duplicates)

**Files:**
- `hyvexa-purge/src/main/java/io/hyvexa/purge/data/PurgeWeaponUpgradeStore.java` (lines 184-207, 231-254)
- `hyvexa-purge/src/main/java/io/hyvexa/purge/data/PurgeClassStore.java` (lines 134-158)

**Problem:** Three methods follow identical pattern: get connection -> disable autocommit -> `selectScrapForUpdate` -> check balance -> deduct scrap -> do operation -> commit/rollback -> update cache. 25 lines duplicated 3 times.

**Plan:**

### Step 1: Define the transaction interface

Add to a new file or in `PurgeScrapStore.java`:

```java
@FunctionalInterface
public interface ScrapTransaction {
    void execute(Connection conn) throws SQLException;
}
```

### Step 2: Add helper method to PurgeScrapStore

```java
public <T> T executeScrapPurchase(UUID playerId, long cost, T successResult, T failResult,
                                   ScrapTransaction operation) {
    try (Connection conn = DatabaseManager.getInstance().getConnection()) {
        conn.setAutoCommit(false);
        try {
            long currentScrap = selectScrapForUpdate(conn, playerId);
            if (currentScrap < cost) {
                conn.rollback();
                return failResult;
            }
            updateScrap(conn, playerId, currentScrap - cost);
            operation.execute(conn);
            conn.commit();
            applyTransactionalScrapCommit(playerId, currentScrap, currentScrap - cost);
            return successResult;
        } catch (SQLException e) {
            conn.rollback();
            LOGGER.atWarning().withCause(e).log("Scrap purchase failed for " + playerId);
            return failResult;
        }
    } catch (SQLException e) {
        LOGGER.atWarning().withCause(e).log("Failed to get connection for scrap purchase");
        return failResult;
    }
}
```

### Step 3: Simplify callers

```java
// PurgeWeaponUpgradeStore.tryUpgrade():
UpgradeResult result = scrapStore.executeScrapPurchase(playerId, cost,
    UpgradeResult.SUCCESS, UpgradeResult.NOT_ENOUGH_SCRAP,
    conn -> upsertWeaponLevel(conn, playerId, weaponId, nextLevel));
if (result == UpgradeResult.SUCCESS) {
    cache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(weaponId, nextLevel);
}
return result;

// PurgeWeaponUpgradeStore.purchaseWeapon():
PurchaseResult result = scrapStore.executeScrapPurchase(playerId, cost,
    PurchaseResult.SUCCESS, PurchaseResult.NOT_ENOUGH_SCRAP,
    conn -> upsertWeaponLevel(conn, playerId, weaponId, 1));
if (result == PurchaseResult.SUCCESS) {
    cache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(weaponId, 1);
}
return result;

// PurgeClassStore.purchaseClass():
PurchaseResult result = scrapStore.executeScrapPurchase(playerId, cost,
    PurchaseResult.SUCCESS, PurchaseResult.NOT_ENOUGH_SCRAP,
    conn -> insertClassUnlock(conn, playerId, purgeClass));
if (result == PurchaseResult.SUCCESS) {
    unlockedCache.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(purgeClass);
}
return result;
```

**Note:** `selectScrapForUpdate`, `updateScrap`, and `applyTransactionalScrapCommit` must be package-private or public on `PurgeScrapStore` for the callers to use `executeScrapPurchase`. Check their current visibility.

---

## 4. PurgeScrapStore: Simplify double-checked locking

**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/data/PurgeScrapStore.java`

**Problem:** `getOrLoadBalance()` (lines 320-328) checks cache, synchronizes, then `getOrLoadBalanceLocked()` (lines 330-338) checks cache again. Since `loadFromDatabase()` always returns non-null (`ScrapBalance.ZERO` fallback), `computeIfAbsent` does the same thing atomically.

**Plan:**
1. Replace both methods:
   ```java
   // Before (lines 320-338):
   private ScrapBalance getOrLoadBalance(UUID playerId) {
       ScrapBalance cached = balanceCache.get(playerId);
       if (cached != null) { return cached; }
       synchronized (lockFor(playerId)) {
           return getOrLoadBalanceLocked(playerId);
       }
   }
   private ScrapBalance getOrLoadBalanceLocked(UUID playerId) {
       ScrapBalance cached = balanceCache.get(playerId);
       if (cached != null) { return cached; }
       ScrapBalance loaded = loadFromDatabase(playerId);
       balanceCache.put(playerId, loaded);
       return loaded;
   }

   // After:
   private ScrapBalance getOrLoadBalance(UUID playerId) {
       return balanceCache.computeIfAbsent(playerId, this::loadFromDatabase);
   }
   ```
2. Delete `getOrLoadBalanceLocked()` entirely.
3. Delete `lockFor()` method if it becomes unused after this change.

---

## 5. HyvexaPurgePlugin: Extract inventory helper

**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/HyvexaPurgePlugin.java`

**Problem:** 5 methods (lines 348, 362, 375, 399, 409) start with identical null-check boilerplate:
```java
Inventory inventory = player.getInventory();
if (inventory == null || inventory.getHotbar() == null) return;
```

**Plan:**
1. Add private helper:
   ```java
   private Hotbar getHotbarOrNull(Player player) {
       Inventory inv = player.getInventory();
       if (inv == null) return null;
       return inv.getHotbar();
   }
   ```
2. Replace each method's boilerplate:
   ```java
   // Before:
   Inventory inventory = player.getInventory();
   if (inventory == null || inventory.getHotbar() == null) return;
   Hotbar hotbar = inventory.getHotbar();
   // After:
   Hotbar hotbar = getHotbarOrNull(player);
   if (hotbar == null) return;
   ```
