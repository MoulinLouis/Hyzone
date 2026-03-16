# Mine Reconnect Persistence

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Persist the player's "in mine" state so that reconnecting (or server restart) restores the correct items and HUD.

**Architecture:** Add an `in_mine` boolean to `MinePlayerProgress`, persisted as a column in `mine_players`. On enter/exit mine, flip the flag and mark dirty. On reconnect, check the flag before deciding which items/HUD to give. Two critical invariants: (1) disconnect must NOT clear the flag (only explicit exit-mine or world-transition-to-non-Ascend clears it), (2) `AddPlayerToWorldEvent` must skip `ensureMenuItemsWhenReady` for players flagged as in-mine, to avoid overwriting the restored pickaxe.

**Tech Stack:** Java, MySQL (existing `mine_players` table)

---

### Task 1: DB migration — add `in_mine` column

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendDatabaseSetup.java`

**Step 1: Add migration method**

In `AscendDatabaseSetup`, add after `ensureMineUpgradeColumns(conn)` call (~line 286):

```java
ensureMineInMineColumn(conn);
```

Then add the method (near the other `ensure*` methods):

```java
private static void ensureMineInMineColumn(Connection conn) {
    if (conn == null) return;
    if (!columnExists(conn, "mine_players", "in_mine")) {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE mine_players ADD COLUMN in_mine TINYINT(1) NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to add in_mine column to mine_players: %s", e.getMessage());
        }
    }
}
```

**Step 2: Commit**

```
feat(mine): add in_mine column migration to mine_players
```

---

### Task 2: Add `inMine` field to `MinePlayerProgress`

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/data/MinePlayerProgress.java`

**Step 1: Add field + getter/setter**

Add alongside the other fields (after `crystals`):

```java
private volatile boolean inMine;
```

Add getter/setter:

```java
public boolean isInMine() { return inMine; }
public void setInMine(boolean inMine) { this.inMine = inMine; }
```

**Step 2: Include `inMine` in save snapshot**

Update the `PlayerSaveSnapshot` record to include `inMine`:

```java
public record PlayerSaveSnapshot(long crystals,
                                 Map<MineUpgradeType, Integer> upgradeLevels,
                                 Map<String, Integer> inventory,
                                 Map<String, MineProgressSnapshot> mineStates,
                                 Map<String, MinerProgressSnapshot> minerStates,
                                 boolean inMine) {}
```

Update `createSaveSnapshot()` to pass it:

```java
public synchronized PlayerSaveSnapshot createSaveSnapshot() {
    Map<MineUpgradeType, Integer> upgradeSnapshot = new EnumMap<>(MineUpgradeType.class);
    upgradeSnapshot.putAll(upgradeLevels);
    return new PlayerSaveSnapshot(
        crystals,
        upgradeSnapshot,
        new LinkedHashMap<>(inventory),
        getMineStates(),
        getMinerStates(),
        inMine
    );
}
```

**Step 3: Commit**

```
feat(mine): add inMine field to MinePlayerProgress
```

---

### Task 3: Load/save `in_mine` in `MinePlayerStore`

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/data/MinePlayerStore.java`

**Step 1: Load `in_mine` from DB**

In `loadFromDatabase`, update the SELECT query (~line 88) to include `in_mine`:

```java
"SELECT crystals, mining_speed_level, bag_capacity_level, multi_break_level, auto_sell_level, in_mine FROM mine_players WHERE uuid = ?"
```

After loading upgrades (~line 98), add:

```java
progress.setInMine(rs.getBoolean("in_mine"));
```

**Step 2: Save `in_mine` to DB**

In `savePlayerSync`, update the INSERT/UPDATE query (~line 192) to include `in_mine`:

```java
try (PreparedStatement ps = conn.prepareStatement("""
        INSERT INTO mine_players (uuid, crystals,
            mining_speed_level, bag_capacity_level, multi_break_level, auto_sell_level, in_mine)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE crystals = VALUES(crystals),
                                mining_speed_level = VALUES(mining_speed_level),
                                bag_capacity_level = VALUES(bag_capacity_level),
                                multi_break_level = VALUES(multi_break_level),
                                auto_sell_level = VALUES(auto_sell_level),
                                in_mine = VALUES(in_mine)
        """)) {
    ps.setString(1, playerId.toString());
    ps.setLong(2, snapshot.crystals());
    ps.setInt(3, snapshot.upgradeLevels().getOrDefault(MineUpgradeType.MINING_SPEED, 0));
    ps.setInt(4, snapshot.upgradeLevels().getOrDefault(MineUpgradeType.BAG_CAPACITY, 0));
    ps.setInt(5, snapshot.upgradeLevels().getOrDefault(MineUpgradeType.MULTI_BREAK, 0));
    ps.setInt(6, snapshot.upgradeLevels().getOrDefault(MineUpgradeType.AUTO_SELL, 0));
    ps.setBoolean(7, snapshot.inMine());
    ps.executeUpdate();
}
```

**Step 3: Commit**

```
feat(mine): persist in_mine flag in MinePlayerStore
```

---

### Task 4: Set `inMine` flag on enter/exit

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/MineGateChecker.java`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java`

**Step 1: Add `MinePlayerStore` dependency to `MineGateChecker`**

Add field + update constructor:

```java
private final MinePlayerStore minePlayerStore;

public MineGateChecker(MineConfigStore configStore, AscendPlayerStore playerStore, MinePlayerStore minePlayerStore) {
    this.configStore = configStore;
    this.playerStore = playerStore;
    this.minePlayerStore = minePlayerStore;
}
```

**Step 2: Set flag in `enterMine()`**

After `swapToMineHud(playerId, playerRef, player)` (~line 116), add:

```java
if (minePlayerStore != null) {
    MinePlayerProgress progress = minePlayerStore.getOrCreatePlayer(playerId);
    progress.setInMine(true);
    minePlayerStore.markDirty(playerId);
}
```

**Step 3: Set flag in `exitMine()`**

After `AscendInventoryUtils.giveMenuItems(player)` (~line 141), add:

```java
if (minePlayerStore != null) {
    MinePlayerProgress progress = minePlayerStore.getOrCreatePlayer(playerId);
    progress.setInMine(false);
    minePlayerStore.markDirty(playerId);
}
```

**Step 4: Make `giveMineItems` public**

Change from `private` to `public` (needed by `ParkourAscendPlugin` in a different package):

```java
public void giveMineItems(Player player) {
```

**Step 5: Reorder initialization in `ParkourAscendPlugin.setup()`**

`minePlayerStore` is initialized at ~line 209, AFTER `mineGateChecker` at ~line 198. Move `mineGateChecker` creation into the `if (mineConfigStore != null)` block at ~line 207, after `minePlayerStore`:

```java
if (mineConfigStore != null) {
    try {
        minePlayerStore = new MinePlayerStore();
        mineManager = new MineManager(mineConfigStore);
        mineGateChecker = new MineGateChecker(mineConfigStore, playerStore, minePlayerStore);
    } catch (Exception e) {
        LOGGER.atWarning().withCause(e).log("Failed to initialize mine manager");
        minePlayerStore = null;
        mineManager = null;
        mineGateChecker = null;
    }
}
```

Remove the earlier standalone `mineGateChecker` construction block (~lines 194-204).

**Step 6: Commit**

```
feat(mine): set inMine flag on enter/exit mine
```

---

### Task 5: Restore mine state on reconnect

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java`

**Step 1: Check `inMine` flag in the `PlayerReadyEvent` async block**

Replace the unconditional item/HUD setup (~lines 394-395):

```java
AscendInventoryUtils.giveMenuItems(player);
hudManager.attach(playerRef, player);
```

With a conditional check:

```java
boolean restoreToMine = false;
if (minePlayerStore != null && mineGateChecker != null && mineGateChecker.canAccessMine(playerId)) {
    MinePlayerProgress mineProgress = minePlayerStore.getOrCreatePlayer(playerId);
    restoreToMine = mineProgress.isInMine();
}

if (restoreToMine) {
    mineGateChecker.giveMineItems(player);
    hudManager.removePlayer(playerId);
    MineHudManager mhm = getMineHudManager();
    if (mhm != null) {
        mhm.attachHud(playerRef, player);
    }
} else {
    AscendInventoryUtils.giveMenuItems(player);
    hudManager.attach(playerRef, player);
}
```

**Step 2: Gate `ensureMenuItemsWhenReady` in `AddPlayerToWorldEvent`**

The `AddPlayerToWorldEvent` handler (~line 440) unconditionally calls `ensureMenuItemsWhenReady(player, world, ...)` for all Ascend world joins. This fires BEFORE `PlayerReadyEvent`, so the mine player cache is not yet populated. This will overwrite the restored pickaxe via `setIfMissing` on slots 0-4.

Gate it with `getOrCreatePlayer` (which loads from DB if not cached) so it correctly detects persisted `in_mine = true` even on cold start / server restart:

Replace (~line 440):

```java
ensureMenuItemsWhenReady(player, world, MENU_SYNC_MAX_ATTEMPTS);
```

With:

```java
boolean playerInMine = false;
if (playerId != null && minePlayerStore != null) {
    MinePlayerProgress mp = minePlayerStore.getOrCreatePlayer(playerId);
    playerInMine = mp.isInMine();
}
if (!playerInMine) {
    ensureMenuItemsWhenReady(player, world, MENU_SYNC_MAX_ATTEMPTS);
}
```

Note: uses `getOrCreatePlayer` (not `getPlayer`) because `AddPlayerToWorldEvent` fires before `PlayerReadyEvent`, so the cache may not be populated yet. This ensures the DB-persisted `in_mine` flag is loaded and checked even after a server restart.

**Step 3: Commit**

```
feat(mine): restore mine items and HUD on reconnect
```

---

### Task 6: Clear `inMine` on world transition (not on disconnect)

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java`

**Critical distinction:** `cleanupAscendState` is called from BOTH `PlayerDisconnectEvent` AND `AddPlayerToWorldEvent` (Ascend -> non-Ascend transition). The `inMine` flag must ONLY be cleared on world transitions, NOT on disconnect (otherwise the flag is lost before reconnect can read it).

**Step 1: Move `inMine` cleanup out of `cleanupAscendState`**

Do NOT add any `inMine` clearing to `cleanupAscendState`.

Instead, clear the flag only in the `AddPlayerToWorldEvent` handler, inside the Ascend -> non-Ascend transition block (~line 444-447). After the existing `cleanupAscendState(playerId)` call, add:

```java
// Clean up Ascend state on true Ascend -> non-Ascend transitions
if (playerId != null && playersInAscendWorld.remove(playerId)) {
    cleanupAscendState(playerId);
    // Clear inMine flag — player explicitly left Ascend world
    if (minePlayerStore != null) {
        MinePlayerProgress mp = minePlayerStore.getPlayer(playerId);
        if (mp != null && mp.isInMine()) {
            mp.setInMine(false);
            minePlayerStore.markDirty(playerId);
        }
    }
}
```

This ensures:
- **Disconnect while in mine:** flag stays `true` in DB -> reconnect restores mine state
- **World transition to Hub:** flag is cleared -> next Ascend join uses normal items
- **Exit via gate:** already cleared in Task 4 (exitMine sets `inMine = false`)

**Step 2: Commit**

```
fix(mine): clear inMine only on world transition, not on disconnect
```

---

### Task 7: Update docs

**Files:**
- Modify: `docs/DATABASE.md` — add `in_mine` column to `mine_players` table docs

**Step 1: Commit**

```
docs: add in_mine column to DATABASE.md
```
