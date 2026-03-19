# Conveyor Belt System — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** When the miner breaks a block, spawn a miniature item entity that travels along an admin-defined conveyor path to a collection point, where the player physically walks to pick up accumulated loot.

**Architecture:** Each mine's `MinerSlot` is extended with a conveyor endpoint + speed. When the miner produces a block, an item entity spawns at the block position and is tick-moved along a straight path to the endpoint. On arrival the entity is despawned and the block is added to a transient conveyor buffer in `MinePlayerProgress`. A proximity check in the existing player tick transfers the buffer to the player's mine inventory when they enter the collection zone. If no conveyor is configured, the mine falls back to direct-to-inventory behavior (v2 default).

**Tech Stack:** Hytale server API (`ItemComponent.generateItemDrop`, `TransformComponent.setPosition`, `Velocity.setZero`, `EntityScaleComponent`), MySQL persistence for config, existing MineRobotManager tick loop.

---

## Assumptions & Constraints

- **No TDD** — Classes import Hytale types; unit tests require `HytaleServer.jar` on classpath (not configured).
- **Transient buffer** — The conveyor buffer is not persisted to DB. When the player disconnects, the miner despawns and no new items are produced. Any uncollected buffer items are lost (a few seconds of production at most).
- **v1 scope** — Straight-line path only (start = block position, end = admin-defined point). No waypoints, no curves.
- **EntityScaleComponent** — Exists in the API (confirmed via conveyor mod analysis). If it fails at runtime, fall back gracefully to normal-sized items.
- **Item entities** — Spawned via `ItemComponent.generateItemDrop(store, itemStack, position, Vector3f.ZERO, 0, 0, 0)` then `store.addEntity(holder, AddReason.SPAWN)`. Confirmed working from the PixelComet conveyor mod.

## Key API Reference

```java
// Spawn item entity (from PixelComet conveyor mod analysis)
ItemStack itemStack = new ItemStack(blockType, 1);
Object holder = ItemComponent.generateItemDrop(store, itemStack, spawnPos, Vector3f.ZERO, 0, 0, 0);
Ref<EntityStore> itemRef = store.addEntity(holder, AddReason.SPAWN);

// Prevent pickup/merge (reflection — fields are private)
Field pickupDelayField = ItemComponent.class.getDeclaredField("pickupDelay");
pickupDelayField.setAccessible(true);
pickupDelayField.setFloat(itemComponent, 999f);
Field mergeDelayField = ItemComponent.class.getDeclaredField("mergeDelay");
mergeDelayField.setAccessible(true);
mergeDelayField.setFloat(itemComponent, 999f);

// Move item (no Teleport — direct setPosition like the mod does)
TransformComponent transform = store.getComponent(itemRef, TransformComponent.getComponentType());
transform.setPosition(new Vector3d(x, y, z));
Velocity velocity = store.getComponent(itemRef, Velocity.getComponentType());
velocity.setZero();

// Scale entity (untested but class exists)
EntityScaleComponent scale = new EntityScaleComponent(0.4f); // or similar
store.addComponent(itemRef, EntityScaleComponent.getComponentType(), scale);

// Despawn
store.removeEntity(itemRef, RemoveReason.REMOVE);
```

---

## Task 1: Extend `MinerSlot` with conveyor fields

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/data/MinerSlot.java`

**Step 1:** Add conveyor fields to MinerSlot:

```java
// After the existing fields (line 8):
private double conveyorEndX, conveyorEndY, conveyorEndZ;
private double conveyorSpeed = 2.0; // blocks per second
private boolean conveyorConfigured;
```

**Step 2:** Add getters and a grouped setter:

```java
public double getConveyorEndX() { return conveyorEndX; }
public double getConveyorEndY() { return conveyorEndY; }
public double getConveyorEndZ() { return conveyorEndZ; }
public double getConveyorSpeed() { return conveyorSpeed; }
public boolean isConveyorConfigured() { return conveyorConfigured; }

public void setConveyorEnd(double x, double y, double z) {
    this.conveyorEndX = x;
    this.conveyorEndY = y;
    this.conveyorEndZ = z;
    this.conveyorConfigured = true;
}

public void setConveyorSpeed(double speed) { this.conveyorSpeed = speed; }
```

**Step 3:** Commit: `feat(ascend): add conveyor fields to MinerSlot`

---

## Task 2: DB migration + MineConfigStore load/save

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendDatabaseSetup.java`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/data/MineConfigStore.java`

**Step 1:** In `AscendDatabaseSetup.ensureTables()`, add an `ensureConveyorColumns` call after the `mine_miner_slots` CREATE TABLE (at the end of the mine section, before `LOGGER.atInfo().log("Ascend database tables ensured")`):

```java
ensureConveyorColumns(conn);
```

**Step 2:** Add the migration method (same pattern as `ensureMineUpgradeColumns`):

```java
private static void ensureConveyorColumns(Connection conn) {
    if (conn == null) return;
    String[][] columns = {
        {"conveyor_end_x", "DOUBLE NOT NULL DEFAULT 0"},
        {"conveyor_end_y", "DOUBLE NOT NULL DEFAULT 0"},
        {"conveyor_end_z", "DOUBLE NOT NULL DEFAULT 0"},
        {"conveyor_speed", "DOUBLE NOT NULL DEFAULT 2.0"},
    };
    for (String[] col : columns) {
        if (!columnExists(conn, "mine_miner_slots", col[0])) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE mine_miner_slots ADD COLUMN " + col[0] + " " + col[1]);
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add " + col[0] + " column: " + e.getMessage());
            }
        }
    }
}
```

**Step 3:** Update `MineConfigStore.loadMinerSlots()` to read the new columns. In the existing SELECT, add the columns and populate:

```java
// In loadMinerSlots, after slot.setIntervalSeconds(...):
double cEndX = rs.getDouble("conveyor_end_x");
double cEndY = rs.getDouble("conveyor_end_y");
double cEndZ = rs.getDouble("conveyor_end_z");
if (cEndX != 0 || cEndY != 0 || cEndZ != 0) {
    slot.setConveyorEnd(cEndX, cEndY, cEndZ);
}
slot.setConveyorSpeed(rs.getDouble("conveyor_speed"));
```

**Step 4:** Update `MineConfigStore.saveMinerSlot()` — add the 4 new columns to both INSERT and UPDATE. The SQL becomes:

```sql
INSERT INTO mine_miner_slots (mine_id, npc_x, npc_y, npc_z, npc_yaw,
    block_x, block_y, block_z, interval_seconds,
    conveyor_end_x, conveyor_end_y, conveyor_end_z, conveyor_speed)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
    npc_x = VALUES(npc_x), npc_y = VALUES(npc_y), npc_z = VALUES(npc_z), npc_yaw = VALUES(npc_yaw),
    block_x = VALUES(block_x), block_y = VALUES(block_y), block_z = VALUES(block_z),
    interval_seconds = VALUES(interval_seconds),
    conveyor_end_x = VALUES(conveyor_end_x), conveyor_end_y = VALUES(conveyor_end_y),
    conveyor_end_z = VALUES(conveyor_end_z), conveyor_speed = VALUES(conveyor_speed)
```

Add the 4 new `stmt.set*` calls after the existing ones.

**Step 5:** Commit: `feat(ascend): add conveyor columns to mine_miner_slots`

---

## Task 3: Admin "Set Conv End" button

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/ui/MineAdminPage.java`
- Modify: `hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_MineAdmin.ui`

**Step 1:** Add button constant in `MineData` inner class:

```java
static final String BUTTON_SET_CONVEYOR_END = "SetConvEnd";
```

**Step 2:** Add case in `handleDataEvent` switch:

```java
case MineData.BUTTON_SET_CONVEYOR_END -> handleSetConveyorEnd(ref, store);
```

**Step 3:** Implement handler (same pattern as `handleSetMinerPos`):

```java
private void handleSetConveyorEnd(Ref<EntityStore> ref, Store<EntityStore> store) {
    Player player = store.getComponent(ref, Player.getComponentType());
    if (player == null) return;
    Mine mine = resolveSelectedMine(player);
    if (mine == null) return;

    MinerSlot slot = mineConfigStore.getMinerSlot(mine.getId());
    if (slot == null || !slot.isConfigured()) {
        player.sendMessage(Message.raw("Set miner position first."));
        return;
    }

    TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
    if (transform == null) {
        player.sendMessage(Message.raw("Unable to read position."));
        return;
    }
    Vector3d pos = transform.getPosition();

    slot.setConveyorEnd(pos.getX(), pos.getY(), pos.getZ());
    mineConfigStore.saveMinerSlot(slot);

    player.sendMessage(Message.raw("Conveyor endpoint set for mine: " + mine.getId()));
    player.sendMessage(Message.raw("  End: " + String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ())));
    player.sendMessage(Message.raw("  Speed: " + slot.getConveyorSpeed() + " blocks/sec"));
}
```

**Step 4:** Add event binding in `bindEvents`:

```java
eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetConvEndButton",
    EventData.of(MineData.KEY_BUTTON, MineData.BUTTON_SET_CONVEYOR_END), false);
```

**Step 5:** Add button in `Ascend_MineAdmin.ui` in the `#SubPages` group (after `#SetMinerPosButton`, before the flex spacer). Copy the exact same TextButton style:

```
TextButton #SetConvEndButton {
    Anchor: (Left: 6, Width: 96, Height: 32);
    Text: "Conv End";
    Style: TextButtonStyle(
        Default: (
            Background: #ffffff(0.06),
            LabelStyle: (FontSize: 13, TextColor: #cfd7dc, HorizontalAlignment: Center, VerticalAlignment: Center)
        ),
        Hovered: (
            Background: #ffffff(0.1),
            LabelStyle: (FontSize: 13, TextColor: #f0f4f8, HorizontalAlignment: Center, VerticalAlignment: Center)
        ),
        Pressed: (
            Background: #ffffff(0.04),
            LabelStyle: (FontSize: 13, TextColor: #cfd7dc, HorizontalAlignment: Center, VerticalAlignment: Center)
        ),
        Sounds: $C.@ButtonSounds,
    );
}
```

**Step 6:** Commit: `feat(ascend): add admin button to set conveyor endpoint`

---

## Task 4: `ConveyorItemState` data class

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/robot/ConveyorItemState.java`

**Step 1:** Create the runtime POJO for tracking each item entity in transit:

```java
package io.hyvexa.ascend.mine.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class ConveyorItemState {
    private final String blockType;        // for loot tracking
    private final long spawnTimeMs;        // when this item was spawned
    private final long travelTimeMs;       // total travel time in ms
    private Ref<EntityStore> entityRef;    // the item entity

    // Start and end positions (cached from MinerSlot at spawn time)
    private final double startX, startY, startZ;
    private final double endX, endY, endZ;

    public ConveyorItemState(String blockType, long travelTimeMs,
                              double startX, double startY, double startZ,
                              double endX, double endY, double endZ) {
        this.blockType = blockType;
        this.spawnTimeMs = System.currentTimeMillis();
        this.travelTimeMs = travelTimeMs;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.endX = endX;
        this.endY = endY;
        this.endZ = endZ;
    }

    /** Returns 0.0 to 1.0+ based on elapsed time. */
    public double getProgress(long now) {
        long elapsed = now - spawnTimeMs;
        return (double) elapsed / travelTimeMs;
    }

    public boolean isComplete(long now) {
        return getProgress(now) >= 1.0;
    }

    /** Linearly interpolate position based on progress. */
    public double getX(long now) { double t = Math.min(1.0, getProgress(now)); return startX + (endX - startX) * t; }
    public double getY(long now) { double t = Math.min(1.0, getProgress(now)); return startY + (endY - startY) * t; }
    public double getZ(long now) { double t = Math.min(1.0, getProgress(now)); return startZ + (endZ - startZ) * t; }

    public String getBlockType() { return blockType; }
    public Ref<EntityStore> getEntityRef() { return entityRef; }
    public void setEntityRef(Ref<EntityStore> entityRef) { this.entityRef = entityRef; }
}
```

**Step 2:** Commit: `feat(ascend): add ConveyorItemState data class`

---

## Task 5: Conveyor buffer in `MinePlayerProgress`

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/data/MinePlayerProgress.java`

**Step 1:** Add the conveyor buffer field (after the `comboCount`/`lastBreakTimeMs` fields, around line 26):

```java
// Conveyor buffer (transient, not persisted)
private final Map<String, Integer> conveyorBuffer = new HashMap<>();
private int conveyorBufferCount;
```

**Step 2:** Add methods:

```java
/** Add block to conveyor buffer (no capacity limit). */
public synchronized void addToConveyorBuffer(String blockTypeId, int amount) {
    if (blockTypeId == null || amount <= 0) return;
    conveyorBuffer.merge(blockTypeId, amount, Integer::sum);
    conveyorBufferCount += amount;
}

/**
 * Transfer conveyor buffer to mine inventory, respecting bag capacity.
 * Returns total items transferred.
 */
public synchronized int transferBufferToInventory() {
    if (conveyorBuffer.isEmpty()) return 0;
    int transferred = 0;
    var it = conveyorBuffer.entrySet().iterator();
    while (it.hasNext()) {
        var entry = it.next();
        int space = getRemainingBagSpaceLocked();
        if (space <= 0) break;
        int toMove = Math.min(entry.getValue(), space);
        inventory.merge(entry.getKey(), toMove, Integer::sum);
        inventoryCount += toMove;
        transferred += toMove;
        int remaining = entry.getValue() - toMove;
        if (remaining <= 0) {
            it.remove();
        } else {
            entry.setValue(remaining);
        }
    }
    conveyorBufferCount -= transferred;
    return transferred;
}

public synchronized int getConveyorBufferCount() {
    return conveyorBufferCount;
}

public synchronized boolean hasConveyorItems() {
    return conveyorBufferCount > 0;
}
```

**Step 3:** Commit: `feat(ascend): add conveyor buffer to MinePlayerProgress`

---

## Task 6: Conveyor item spawning and tick in `MineRobotManager`

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/robot/MineRobotManager.java`

This is the core task. It changes the loot flow and adds a conveyor tick.

### Step 1: Add new imports

```java
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
```

### Step 2: Add conveyor item tracking

New field alongside the existing `miners` map:

```java
// Active conveyor items per world (all items share one list per world for tick efficiency)
private final Map<String, List<ConveyorItemState>> conveyorItems = new ConcurrentHashMap<>();

// Reflection fields for item pickup/merge delay (set once in start())
private volatile java.lang.reflect.Field pickupDelayField;
private volatile java.lang.reflect.Field mergeDelayField;
```

### Step 3: Initialize reflection fields in `start()`

After the `npcPlugin = NPCPlugin.get()` block in `start()`:

```java
try {
    pickupDelayField = ItemComponent.class.getDeclaredField("pickupDelay");
    pickupDelayField.setAccessible(true);
    mergeDelayField = ItemComponent.class.getDeclaredField("mergeDelay");
    mergeDelayField.setAccessible(true);
} catch (Exception e) {
    LOGGER.atWarning().log("Conveyor reflection setup failed: " + e.getMessage());
}
```

### Step 4: Modify `tickMiner` — branch on conveyor config

Replace lines 608-612 (the direct inventory add) with a conveyor-aware branch:

```java
// Loot = the block currently displayed
String lootType = state.getCurrentBlockType();
if (lootType != null) {
    MinerSlot convSlot = configStore.getMinerSlot(mineId);
    if (convSlot != null && convSlot.isConveyorConfigured()) {
        // Conveyor mode: buffer + spawn visual item
        progress.addToConveyorBuffer(lootType, 1);
        spawnConveyorItem(state, convSlot, lootType);
    } else {
        // Direct mode (no conveyor): add straight to inventory
        progress.addToInventory(lootType, 1);
    }
    playerStore.markDirty(state.getOwnerId());
}
```

### Step 5: Add `spawnConveyorItem` method

```java
private void spawnConveyorItem(MinerRobotState minerState, MinerSlot slot, String blockType) {
    String worldName = minerState.getWorldName();
    World world = worldName != null ? Universe.get().getWorld(worldName) : null;
    if (world == null) return;

    double startX = slot.getBlockX() + 0.5;
    double startY = slot.getBlockY() + 0.5;
    double startZ = slot.getBlockZ() + 0.5;
    double endX = slot.getConveyorEndX();
    double endY = slot.getConveyorEndY();
    double endZ = slot.getConveyorEndZ();

    double dx = endX - startX, dy = endY - startY, dz = endZ - startZ;
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    long travelTimeMs = (long) (distance / slot.getConveyorSpeed() * 1000);
    if (travelTimeMs < 100) travelTimeMs = 100; // minimum 100ms

    ConveyorItemState itemState = new ConveyorItemState(blockType, travelTimeMs,
            startX, startY, startZ, endX, endY, endZ);

    conveyorItems.computeIfAbsent(worldName, k -> new ArrayList<>()).add(itemState);

    world.execute(() -> {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store == null) return;

            ItemStack itemStack = new ItemStack(blockType, 1);
            Object holder = ItemComponent.generateItemDrop(store, itemStack,
                    new Vector3d(startX, startY, startZ), Vector3f.ZERO, 0, 0, 0);
            if (holder == null) return;

            Ref<EntityStore> itemRef = store.addEntity(holder, AddReason.SPAWN);
            if (itemRef == null || !itemRef.isValid()) return;

            itemState.setEntityRef(itemRef);

            // Prevent player pickup and item merging
            ItemComponent itemComp = store.getComponent(itemRef, ItemComponent.getComponentType());
            if (itemComp != null) {
                if (pickupDelayField != null) pickupDelayField.setFloat(itemComp, 999f);
                if (mergeDelayField != null) mergeDelayField.setFloat(itemComp, 999f);
            }

            // Zero velocity to prevent physics/gravity
            Velocity vel = store.getComponent(itemRef, Velocity.getComponentType());
            if (vel != null) vel.setZero();
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to spawn conveyor item: " + e.getMessage());
        }
    });
}
```

### Step 6: Add conveyor tick in the main `tick()` method

After the miner tick loop (after line 555 `orphanCleanup.processPendingRemovals()`), add:

```java
tickConveyorItems(now);
```

Implement:

```java
private void tickConveyorItems(long now) {
    for (var entry : conveyorItems.entrySet()) {
        String worldName = entry.getKey();
        List<ConveyorItemState> items = entry.getValue();
        if (items.isEmpty()) continue;

        World world = Universe.get().getWorld(worldName);
        if (world == null) continue;

        var it = items.iterator();
        while (it.hasNext()) {
            ConveyorItemState item = it.next();
            Ref<EntityStore> ref = item.getEntityRef();

            if (item.isComplete(now)) {
                // Arrived at endpoint — despawn entity
                if (ref != null && ref.isValid()) {
                    world.execute(() -> {
                        if (ref.isValid()) {
                            Store<EntityStore> store = ref.getStore();
                            if (store != null) store.removeEntity(ref, RemoveReason.REMOVE);
                        }
                    });
                }
                it.remove();
                continue;
            }

            // Move entity along path
            if (ref != null && ref.isValid()) {
                double x = item.getX(now);
                double y = item.getY(now);
                double z = item.getZ(now);

                world.execute(() -> {
                    if (!ref.isValid()) return;
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    if (store == null) return;

                    TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                    if (transform != null) {
                        transform.setPosition(new Vector3d(x, y, z));
                    }

                    Velocity vel = store.getComponent(ref, Velocity.getComponentType());
                    if (vel != null) vel.setZero();

                    // Refresh pickup/merge delay
                    ItemComponent itemComp = store.getComponent(ref, ItemComponent.getComponentType());
                    if (itemComp != null) {
                        try {
                            if (pickupDelayField != null) pickupDelayField.setFloat(itemComp, 999f);
                            if (mergeDelayField != null) mergeDelayField.setFloat(itemComp, 999f);
                        } catch (Exception ignored) {}
                    }
                });
            }
        }
    }
}
```

### Step 7: Cleanup conveyor items on player leave / miner despawn

In `onPlayerLeave`, after the existing loop that calls `despawnNpc` and `clearMinerBlock`:

```java
// Also cleanup any conveyor items for this player's miners
for (MinerRobotState state : playerMiners.values()) {
    cleanupConveyorItems(state.getWorldName());
}
```

Add helper:

```java
private void cleanupConveyorItems(String worldName) {
    List<ConveyorItemState> items = conveyorItems.get(worldName);
    if (items == null || items.isEmpty()) return;

    World world = worldName != null ? Universe.get().getWorld(worldName) : null;

    var it = items.iterator();
    while (it.hasNext()) {
        ConveyorItemState item = it.next();
        Ref<EntityStore> ref = item.getEntityRef();
        if (ref != null && ref.isValid() && world != null) {
            world.execute(() -> {
                if (ref.isValid()) {
                    Store<EntityStore> store = ref.getStore();
                    if (store != null) store.removeEntity(ref, RemoveReason.REMOVE);
                }
            });
        }
        it.remove();
    }
}
```

Also call `cleanupConveyorItems` in `despawnAll()` and `despawnMiner()`.

### Step 8: Commit

`feat(ascend): add conveyor item spawning and tick-based movement`

---

## Task 7: Collection zone check in player tick

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/MineGateChecker.java`

The collection check piggybacks on the existing `checkPlayer()` method that already reads the player's position every 50ms.

### Step 1: Add dependencies

Add field to `MineGateChecker`:

```java
private final MineConfigStore configStore;  // already exists
// Add:
private static final double COLLECTION_RADIUS_SQ = 4.0; // 2 blocks squared
```

### Step 2: Add collection check at the end of `checkPlayer`

After the exit gate check (line 92), add:

```java
// Conveyor collection: transfer buffer when player is near any conveyor endpoint
checkConveyorCollection(playerId, player, x, y, z);
```

Where `player` is obtained from `store.getComponent(ref, Player.getComponentType())` — but only if we need it. Optimize: only get Player if there's a buffer to collect.

### Step 3: Implement the collection check

```java
private void checkConveyorCollection(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store,
                                      double x, double y, double z) {
    if (minePlayerStore == null) return;
    MinePlayerProgress progress = minePlayerStore.getPlayer(playerId);
    if (progress == null || !progress.hasConveyorItems()) return;

    // Check proximity to any configured conveyor endpoint
    for (Mine mine : configStore.listMinesSorted()) {
        MinerSlot slot = configStore.getMinerSlot(mine.getId());
        if (slot == null || !slot.isConveyorConfigured()) continue;

        double dx = x - slot.getConveyorEndX();
        double dy = y - slot.getConveyorEndY();
        double dz = z - slot.getConveyorEndZ();
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq <= COLLECTION_RADIUS_SQ) {
            int transferred = progress.transferBufferToInventory();
            if (transferred > 0) {
                minePlayerStore.markDirty(playerId);
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.sendMessage(Message.raw("Collected " + transferred + " blocks from conveyor."));
                }
            }
            return; // only collect from one zone per tick
        }
    }
}
```

### Step 4: Wire the call in `checkPlayer`

After the exit gate check block (line 92), before the method ends:

```java
// Conveyor collection zone
checkConveyorCollection(playerId, ref, store, x, y, z);
```

Note: this needs the `x, y, z` values that are already computed on line 71-73. The check must go AFTER the cooldown/gate checks but it uses the same position data.

### Step 5: Add import for `MinerSlot` and `Mine`

```java
import io.hyvexa.ascend.mine.data.MinerSlot;
import io.hyvexa.ascend.mine.data.Mine;
```

### Step 6: Commit

`feat(ascend): add conveyor collection zone check`

---

## Task 8: Verify and adjust

**No code changes** — manual testing checklist.

1. Start server, open admin page, navigate to a mine
2. Set miner position (existing "Miner Pos" button)
3. Walk to where you want the conveyor to end, click "Conv End" — verify chat confirms
4. Purchase a miner → verify:
   - [ ] Kweebec spawns and mines as before
   - [ ] When block breaks, a small item entity appears at the block position
   - [ ] Item moves smoothly in a straight line toward the conveyor endpoint
   - [ ] Item despawns when it reaches the endpoint
   - [ ] Items do NOT get picked up by the player walking over them mid-transit
5. Walk to the conveyor endpoint → verify:
   - [ ] Chat message: "Collected X blocks from conveyor."
   - [ ] Items appear in mine inventory
   - [ ] Bag capacity is respected (if bag is full, uncollected items stay in buffer)
   - [ ] Walking away and back collects newly arrived items
6. **No conveyor configured test:** Remove conveyor endpoint from a mine → verify miner adds directly to inventory (fallback behavior)
7. Disconnect while items are in transit → verify:
   - [ ] All item entities despawn (no floating items)
   - [ ] Reconnect → miner respawns, conveyor works again

**Tuning knobs if needed:**
- `conveyorSpeed` (2.0 blocks/sec) — adjust travel speed
- `COLLECTION_RADIUS_SQ` (4.0 = 2 blocks) — adjust pickup zone size
- Item Y offset — items may need to float at `blockY + 1.0` instead of `blockY + 0.5`
- `EntityScaleComponent` — try adding in a future iteration if items look too big

---

## What's NOT in this plan (future iterations)

- EntityScaleComponent for miniature items (needs runtime testing first)
- Conveyor waypoints (curves, multi-segment paths)
- Visual conveyor belt blocks (decorative)
- Conveyor speed upgrades
- Collection point NPC or UI
- Sound effects / particles on collection
- Buffer persistence across sessions
- Multiple conveyors per mine
