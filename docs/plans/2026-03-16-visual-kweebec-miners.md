# Visual Kweebec Miners Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make miner Kweebecs visually walk to blocks in the mine zone, pause to "mine" them, then break the block — synced to the existing production timer.

**Architecture:** Replace the static frozen Kweebec + numerical-only production with a state machine (`IDLE -> MOVING -> MINING -> block break -> repeat`). The Kweebec moves via `Frozen` + periodic `Teleport` (same pattern as `PetManager`). Block breaking sets the world block to air and feeds into the existing `MineManager.markBlockBroken()` / `MinePlayerProgress.addToInventory()` pipeline. When bag is full or no blocks remain, the Kweebec stops.

**Tech Stack:** Hytale ECS (Frozen, Teleport, TransformComponent), ChunkUtil for block ops, existing MineManager/MinePlayerStore.

---

### Task 1: Add `MinerPhase` enum to `MinerRobotState`

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/robot/MinerRobotState.java`

**Step 1: Add the phase enum and new state fields**

Add at the top of `MinerRobotState`:

```java
public enum MinerPhase { IDLE, MOVING, MINING, STOPPED }
```

Add new fields after existing ones:

```java
private MinerPhase phase = MinerPhase.IDLE;
private long phaseStartTime = 0;
private long cycleStartTime = 0; // Tracks when the current production cycle began (IDLE entry)

// Target block to mine
private int targetBlockX, targetBlockY, targetBlockZ;
private boolean hasTarget = false;

// Current interpolated position (for smooth movement)
private double currentX, currentY, currentZ;
private boolean positionInitialized = false;

// World ref name for resolving world each tick
private String worldName;
```

Add getters/setters for all new fields. Add convenience methods:

```java
public void setTargetBlock(int x, int y, int z) {
    this.targetBlockX = x;
    this.targetBlockY = y;
    this.targetBlockZ = z;
    this.hasTarget = true;
}

public void clearTarget() {
    this.hasTarget = false;
}

public void setCurrentPosition(double x, double y, double z) {
    this.currentX = x;
    this.currentY = y;
    this.currentZ = z;
    this.positionInitialized = true;
}

/** Reset phase/target state for evolution respawn. Preserves cycleStartTime so the
 *  production timer continues from where it was, matching current behavior where
 *  syncMinerEvolution keeps the existing lastProductionTick (MineRobotManager.java:247). */
public void resetPhaseForEvolution() {
    this.phase = MinerPhase.IDLE;
    this.phaseStartTime = 0;
    // cycleStartTime intentionally preserved — evolution doesn't reset the production timer
    this.hasTarget = false;
}
```

**Step 2: Add movement speed calculation**

The full cycle duration = `productionIntervalMs`. Split: 40% moving, 50% mining, 10% buffer.

```java
/** Milliseconds allocated for walking to the target block. */
public long getMoveDurationMs() {
    return (long) (getProductionIntervalMs() * 0.40);
}

/** Milliseconds allocated for standing at the block before breaking it. */
public long getMineDurationMs() {
    return (long) (getProductionIntervalMs() * 0.50);
}
```

**Step 3: Commit**

```
feat(mine): add MinerPhase state machine to MinerRobotState
```

---

### Task 2: Add block-picking helper to `MineManager`

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/MineManager.java`

**Step 1: Add atomic `tryClaimBlock` to prevent duplicate payouts**

Both `MineBreakSystem` (player) and `MineRobotManager` (miner) can race to break the same block.
Add an atomic claim that marks-if-unbroken in one step. `brokenBlocks` uses `ConcurrentHashMap.newKeySet()`
which delegates to `ConcurrentHashMap`, so `Set.add()` is atomic — returns `true` only for the first caller.

```java
/**
 * Atomically claims a block position as broken. Returns true if this caller is the first
 * to break it (and should receive the reward). Returns false if already broken.
 */
public boolean tryClaimBlock(String zoneId, int x, int y, int z) {
    return brokenBlocks.computeIfAbsent(zoneId, k -> ConcurrentHashMap.newKeySet())
        .add(encodePosition(x, y, z));
}

/**
 * Rolls back a claim (e.g. when bag is full after claiming but before breaking).
 */
public void unclaimBlock(String zoneId, int x, int y, int z) {
    Set<Long> broken = brokenBlocks.get(zoneId);
    if (broken != null) {
        broken.remove(encodePosition(x, y, z));
    }
}
```

Also update `MineBreakSystem.java` to use `tryClaimBlock` instead of the separate `markBlockBroken`.
**Critical**: the claim must happen BEFORE any `setBlock(air)` call, not just before the reward.
Current code does `setBlock(air)` at line 133 before `markBlockBroken` at line 152. Reorder to:

1. Move `tryClaimBlock` to right after the zone-in-cooldown check (before line 125). If it
   returns false, return early (block already claimed by a miner — don't break or reward).
2. Keep `setBlock(air)` after the claim succeeds (line 133 stays where it is).
3. Remove the old `markBlockBroken` call at line 152 (now redundant — `tryClaimBlock` already marked it).

**Step 2: Add method to pick a random non-broken block position in a zone**

```java
/**
 * Returns a random non-broken block position in the zone as int[3] {x, y, z},
 * or null if all blocks are broken.
 */
public int[] pickRandomUnbrokenBlock(MineZone zone) {
    if (zone == null) return null;

    Set<Long> broken = brokenBlocks.get(zone.getId());
    int totalBlocks = zone.getTotalBlocks();
    int brokenCount = broken != null ? broken.size() : 0;

    if (brokenCount >= totalBlocks) return null; // All broken

    // Random attempts — try up to 20 times, then linear scan fallback
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int attempt = 0; attempt < 20; attempt++) {
        int x = random.nextInt(zone.getMinX(), zone.getMaxX() + 1);
        int y = random.nextInt(zone.getMinY(), zone.getMaxY() + 1);
        int z = random.nextInt(zone.getMinZ(), zone.getMaxZ() + 1);
        if (!isBlockBroken(zone.getId(), x, y, z)) {
            return new int[]{x, y, z};
        }
    }

    // Fallback: linear scan from a random start
    int rangeX = zone.getMaxX() - zone.getMinX() + 1;
    int rangeY = zone.getMaxY() - zone.getMinY() + 1;
    int rangeZ = zone.getMaxZ() - zone.getMinZ() + 1;
    int startIdx = random.nextInt(totalBlocks);
    for (int i = 0; i < totalBlocks; i++) {
        int idx = (startIdx + i) % totalBlocks;
        int x = zone.getMinX() + (idx / (rangeY * rangeZ));
        int y = zone.getMinY() + ((idx / rangeZ) % rangeY);
        int z = zone.getMinZ() + (idx % rangeZ);
        if (!isBlockBroken(zone.getId(), x, y, z)) {
            return new int[]{x, y, z};
        }
    }

    return null; // Truly all broken
}
```

**Step 2: Commit**

```
feat(mine): add pickRandomUnbrokenBlock helper to MineManager
```

---

### Task 3: Rewrite `MineRobotManager.tickMiner()` as a state machine

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/robot/MineRobotManager.java`

**Context:** This is the core change. The tick method currently just checks a timer and adds items. It needs to become a state machine that moves the NPC, waits, then breaks a block.

**Step 1: Add `MineManager` dependency**

Add `MineManager` as a constructor parameter alongside existing `configStore` and `playerStore`. Store it as a field. Update `ParkourAscendPlugin` where `MineRobotManager` is constructed to pass `mineManager`.

In `MineRobotManager`:
```java
private final MineManager mineManager;

public MineRobotManager(MineConfigStore configStore, MinePlayerStore playerStore, MineManager mineManager) {
    this.configStore = configStore;
    this.playerStore = playerStore;
    this.mineManager = mineManager;
    this.orphanCleanup = new OrphanedEntityCleanup(LOGGER,
            Path.of("mods", "Parkour", MINER_UUIDS_FILE));
}
```

In `ParkourAscendPlugin` (around line 226), update:
```java
mineRobotManager = new MineRobotManager(mineConfigStore, minePlayerStore, mineManager);
```

**Step 2: Rewrite `tickMiner()` as a state machine dispatcher**

Replace the existing `tickMiner` method:

```java
private void tickMiner(MinerRobotState state, long now) {
    switch (state.getPhase()) {
        case IDLE -> tickIdle(state, now);
        case MOVING -> tickMoving(state, now);
        case MINING -> tickMining(state, now);
        case STOPPED -> tickStopped(state, now);
    }
}
```

**Step 3: Implement `tickIdle` — pick a target block and start moving**

```java
private void tickIdle(MinerRobotState state, long now) {
    // Enforce production interval: don't start a new cycle until the previous one's
    // full productionIntervalMs has elapsed. cycleStartTime is seeded on spawn/evolution
    // so the first cycle also waits, matching the original lastProductionTick behavior.
    long cycleStart = state.getCycleStartTime();
    if (now - cycleStart < state.getProductionIntervalMs()) {
        return; // Wait for the full interval before starting next cycle
    }

    // Check bag full
    MinePlayerProgress progress = playerStore.getPlayer(state.getOwnerId());
    if (progress == null || progress.isInventoryFull()) {
        state.setPhase(MinerRobotState.MinerPhase.STOPPED);
        state.setPhaseStartTime(now);
        return;
    }

    Mine mine = configStore.getMine(state.getMineId());
    if (mine == null || mine.getZones().isEmpty()) return;

    MineZone zone = mine.getZones().get(0);

    // Check zone in cooldown (all blocks regen'd, wait)
    if (mineManager.isZoneInCooldown(zone.getId())) {
        return; // Stay idle, retry next tick
    }

    int[] target = mineManager.pickRandomUnbrokenBlock(zone);
    if (target == null) {
        // All blocks broken, wait for regen
        state.setPhase(MinerRobotState.MinerPhase.STOPPED);
        state.setPhaseStartTime(now);
        return;
    }

    // Mark the start of this production cycle
    state.setCycleStartTime(now);
    state.setTargetBlock(target[0], target[1], target[2]);
    state.setPhase(MinerRobotState.MinerPhase.MOVING);
    state.setPhaseStartTime(now);
}
```

**Step 4: Implement `tickMoving` — interpolate position toward target block**

```java
private void tickMoving(MinerRobotState state, long now) {
    if (!state.hasTarget() || !state.isPositionInitialized()) {
        state.setPhase(MinerRobotState.MinerPhase.IDLE);
        return;
    }

    long elapsed = now - state.getPhaseStartTime();
    long moveDuration = state.getMoveDurationMs();

    // Target position: stand next to the block (at block center X/Z, at block Y level)
    double targetX = state.getTargetBlockX() + 0.5;
    double targetY = state.getTargetBlockY();
    double targetZ = state.getTargetBlockZ() + 0.5;

    if (elapsed >= moveDuration) {
        // Arrived — snap to position and switch to MINING
        state.setCurrentPosition(targetX, targetY, targetZ);
        state.setPhase(MinerRobotState.MinerPhase.MINING);
        state.setPhaseStartTime(now);
        teleportMiner(state, targetX, targetY, targetZ, true);
        return;
    }

    // Interpolate
    double t = (double) elapsed / moveDuration;
    // Ease-in-out for smoother motion
    t = t * t * (3.0 - 2.0 * t);

    double startX = state.getCurrentX();
    double startY = state.getCurrentY();
    double startZ = state.getCurrentZ();

    double interpX = startX + (targetX - startX) * t;
    double interpY = startY + (targetY - startY) * t;
    double interpZ = startZ + (targetZ - startZ) * t;

    teleportMiner(state, interpX, interpY, interpZ, true);
}
```

**Step 5: Implement `tickMining` — wait at the block, then break it**

```java
private void tickMining(MinerRobotState state, long now) {
    long elapsed = now - state.getPhaseStartTime();
    if (elapsed < state.getMineDurationMs()) {
        return; // Still mining, wait
    }

    // Break the block and produce item
    Mine mine = configStore.getMine(state.getMineId());
    if (mine == null || mine.getZones().isEmpty()) {
        state.setPhase(MinerRobotState.MinerPhase.IDLE);
        return;
    }

    MineZone zone = mine.getZones().get(0);

    // Atomically claim the block. If another miner or a player already broke it,
    // tryClaimBlock returns false and we skip the reward.
    if (!state.hasTarget()
            || !mineManager.tryClaimBlock(zone.getId(),
                state.getTargetBlockX(), state.getTargetBlockY(), state.getTargetBlockZ())) {
        // Block was already claimed — skip reward, go back to IDLE to pick a new target
        state.setCurrentPosition(
            state.getTargetBlockX() + 0.5,
            state.getTargetBlockY(),
            state.getTargetBlockZ() + 0.5
        );
        state.clearTarget();
        state.setPhase(MinerRobotState.MinerPhase.IDLE);
        return;
    }

    // Read the actual block type at the target coordinate.
    // This dispatches to the world thread (see readBlockTypeAndReward),
    // which reads the chunk, awards inventory, and breaks the block — all on world thread.
    readBlockTypeAndReward(state, zone);

    // Update current position to where we are now (at the block)
    state.setCurrentPosition(
        state.getTargetBlockX() + 0.5,
        state.getTargetBlockY(),
        state.getTargetBlockZ() + 0.5
    );
    state.clearTarget();

    // Cycle back to IDLE to pick next block (production interval enforced there)
    state.setPhase(MinerRobotState.MinerPhase.IDLE);
}
```

**Step 6: Implement `tickStopped` — check periodically if bag has space or blocks are available again**

```java
private static final long STOPPED_RECHECK_INTERVAL_MS = 2000L;

private void tickStopped(MinerRobotState state, long now) {
    if (now - state.getPhaseStartTime() < STOPPED_RECHECK_INTERVAL_MS) {
        return;
    }
    state.setPhaseStartTime(now); // Reset check timer

    MinePlayerProgress progress = playerStore.getPlayer(state.getOwnerId());
    if (progress != null && !progress.isInventoryFull()) {
        // Check if blocks available
        Mine mine = configStore.getMine(state.getMineId());
        if (mine != null && !mine.getZones().isEmpty()) {
            MineZone zone = mine.getZones().get(0);
            if (!mineManager.isZoneInCooldown(zone.getId())
                    && mineManager.pickRandomUnbrokenBlock(zone) != null) {
                state.setPhase(MinerRobotState.MinerPhase.IDLE);
            }
        }
    }
}
```

**Step 7: Implement `teleportMiner` helper**

Per `HYTALE_API.md` line 489-490: resolve entity ref fresh each tick via `getRefFromUUID` on
the world thread. Never use a cached `Ref<EntityStore>` for teleport — refs expire between ticks.
The miner's `entityUuid` (stable across ticks) is used to resolve a fresh ref inside `world.execute()`.

```java
private void teleportMiner(MinerRobotState state, double x, double y, double z, boolean faceTarget) {
    UUID entityUuid = state.getEntityUuid();
    if (entityUuid == null) return;

    String worldName = state.getWorldName();
    if (worldName == null) return;

    World world = Universe.get().getWorld(worldName);
    if (world == null) return;

    float yaw = 0f;
    if (faceTarget && state.hasTarget()) {
        double dx = (state.getTargetBlockX() + 0.5) - x;
        double dz = (state.getTargetBlockZ() + 0.5) - z;
        if (dx * dx + dz * dz > 0.001) {
            yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        }
    }

    final float finalYaw = yaw;
    world.execute(() -> {
        try {
            // Resolve ref fresh on world thread — never use cached ref for teleport
            Ref<EntityStore> entityRef = world.getEntityStore().getRefFromUUID(entityUuid);
            if (entityRef == null || !entityRef.isValid()) return;
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store == null) return;
            store.addComponent(entityRef, Teleport.getComponentType(),
                new Teleport(world, new Vector3d(x, y, z), new Vector3f(0, finalYaw, 0)));
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to teleport miner: " + e.getMessage());
        }
    });
}
```

**Step 8: Implement `readBlockTypeAndReward` helper**

Reads the actual block type AND awards the inventory item AND breaks the block — all on the
world thread, as required by `HYTALE_API.md` (all world/chunk reads and writes must run inside
`world.execute()`). The block claim was already done atomically in `tickMining` via `tryClaimBlock`,
so this step is guaranteed to be the sole owner of this block position.

Falls back to `pickRandomBlock(blockTable)` if chunk is unavailable.

Matches current behavior: both the original miner (`MineRobotManager.java:384` skips when bag full)
and `MineBreakSystem` (`MineBreakSystem.java:117` refuses to break when bag full) never destroy a
block without awarding it. If the bag becomes full between claim and world execution, we roll back
the claim via `unclaimBlock` and leave the block intact.

```java
private void readBlockTypeAndReward(MinerRobotState state, MineZone zone) {
    String worldName = state.getWorldName();
    if (worldName == null) return;

    World world = Universe.get().getWorld(worldName);
    if (world == null) return;

    int bx = state.getTargetBlockX();
    int by = state.getTargetBlockY();
    int bz = state.getTargetBlockZ();
    UUID ownerId = state.getOwnerId();

    world.execute(() -> {
        try {
            // Check bag space before touching the world — match current behavior
            MinePlayerProgress progress = playerStore.getPlayer(ownerId);
            if (progress == null || progress.isInventoryFull()) {
                // Roll back the claim so the block can be mined later
                mineManager.unclaimBlock(zone.getId(), bx, by, bz);
                return;
            }

            // Read actual block type from chunk
            String blockType = null;
            long chunkIndex = ChunkUtil.indexChunkFromBlock(bx, bz);
            var chunk = world.getChunkIfInMemory(chunkIndex);
            if (chunk == null) chunk = world.loadChunkIfInMemory(chunkIndex);

            if (chunk != null) {
                int blockId = chunk.getBlock(bx, by, bz);
                if (blockId > 0) {
                    blockType = BlockType.getAssetMap().getName(blockId);
                }
            }

            // Fallback if chunk unavailable or block was air
            if (blockType == null) {
                blockType = pickRandomBlock(zone.getBlockTable());
            }
            if (blockType == null) {
                mineManager.unclaimBlock(zone.getId(), bx, by, bz);
                return;
            }

            // Try to add to inventory — if bag filled between check and here, roll back
            boolean added = progress.addToInventory(blockType, 1);
            if (!added) {
                mineManager.unclaimBlock(zone.getId(), bx, by, bz);
                return;
            }

            playerStore.markDirty(ownerId);

            // Only break the block after successful reward
            if (chunk != null) {
                chunk.setBlock(bx, by, bz, 0);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Miner readBlockTypeAndReward error: " + e.getMessage());
        }
    });
}
```

**Step 9: Add required imports to `MineRobotManager`**

```java
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.Universe;
import io.hyvexa.ascend.mine.data.MineZone;
```

**Step 10: Commit**

```
feat(mine): rewrite miner tick as visual state machine with block breaking
```

---

### Task 4: Initialize miner position on spawn

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/robot/MineRobotManager.java`

**Step 1: Set initial position and world name in `spawnMiner`**

In `spawnMiner()`, after computing `cx`, `cy`, `cz` and creating the `MinerRobotState`, set the initial position, world name, and seed the cycle timer so the first block waits a full production interval (matching the original `lastProductionTick` seeding at line 153):

```java
state.setCurrentPosition(cx, cy, cz);
state.setWorldName(world.getName());
state.setCycleStartTime(System.currentTimeMillis()); // First cycle waits full interval
```

**Step 2: Also reset phase/position/world in `syncMinerEvolution` respawn path**

In `syncMinerEvolution()`, after the respawn section where position is recomputed:

```java
state.setCurrentPosition(cx, cy, cz);
state.setWorldName(world.getName());
state.resetPhaseForEvolution(); // Reset to IDLE, clear stale target — preserves cycleStartTime
```

**Step 3: Commit**

```
feat(mine): initialize miner position and world name on spawn
```

---

### Task 5: Handle interpolation start position for MOVING phase

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/robot/MineRobotManager.java`

**Context:** The `tickMoving` interpolation uses `state.getCurrentX/Y/Z()` as the start point. This is set on spawn (Task 4) and updated after each mining cycle (end of `tickMining`). But if the miner was STOPPED and resumes, its start position is already correct (last known position). No additional changes needed — this task is verification only.

**Step 1: Verify the position chain**

Walk through the flow mentally:
1. Spawn -> `setCurrentPosition(cx, cy, cz)` (Task 4)
2. IDLE -> picks target -> MOVING starts. `tickMoving` reads `getCurrentX/Y/Z` as start -> interpolates to target.
3. MOVING -> arrives -> MINING. Position snapped to target via `setCurrentPosition`.
4. MINING -> breaks block -> IDLE. Position already at block location.
5. IDLE -> picks next target -> MOVING from block position to new target. Correct.
6. STOPPED -> resumes -> IDLE -> picks target -> MOVING from last position. Correct.

No code changes needed. Move to next task.

---

### Task 6: Update `ParkourAscendPlugin` constructor call

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java` (around line 226)

**Step 1: Pass `mineManager` to `MineRobotManager` constructor**

Change:
```java
mineRobotManager = new MineRobotManager(mineConfigStore, minePlayerStore);
```
To:
```java
mineRobotManager = new MineRobotManager(mineConfigStore, minePlayerStore, mineManager);
```

**Step 2: Commit**

```
feat(mine): wire MineManager into MineRobotManager
```

---

### Task 7: Integration testing — manual verification checklist

**Context:** Automated tests can't run Hytale entity systems (no `HytaleServer.jar` on test classpath). Manual verification is required.

**Verification steps after build:**

1. **Spawn check**: Join server, enter mine. Verify Kweebec spawns at zone center.
2. **Movement check**: Watch the Kweebec — it should start walking toward a block after a few seconds (depends on production interval at level 0: ~10s total cycle).
3. **Mining check**: Kweebec stops at block, pauses, then the block disappears (set to air).
4. **Inventory check**: Run `/mine` — bag count should increase by 1 after each block break.
5. **Speed scaling**: Upgrade miner speed and verify the Kweebec moves/mines faster.
6. **Bag full check**: Fill the bag. Verify the Kweebec stops moving and stands still.
7. **Bag empty resume**: Sell blocks with `/mine sell`. Verify the Kweebec resumes mining.
8. **Zone regen**: Let the Kweebec break enough blocks to trigger regen threshold. Verify it stops during cooldown and resumes after regen.
9. **Disconnect/reconnect**: Leave and rejoin. Verify the Kweebec respawns and resumes its cycle.
10. **Evolution**: Evolve the miner. Verify the Kweebec model changes and continues mining.

---

### Summary of files changed

| File | Change |
|------|--------|
| `MinerRobotState.java` | Add `MinerPhase` enum, phase/target/position fields, `cycleStartTime`, `resetPhaseForEvolution()` |
| `MineManager.java` | Add `tryClaimBlock()` (atomic), `unclaimBlock()` (rollback), `pickRandomUnbrokenBlock()` |
| `MineRobotManager.java` | Add `MineManager` dependency, rewrite `tickMiner` as state machine, `teleportMiner` resolves ref fresh via UUID, `readBlockTypeAndReward` on world thread |
| `MineBreakSystem.java` | Replace `markBlockBroken` with `tryClaimBlock` BEFORE `setBlock(air)` to gate both break and reward on first-claimer |
| `ParkourAscendPlugin.java` | Pass `mineManager` to `MineRobotManager` constructor |

### Risks and edge cases

- **Interpolation start on phase re-entry**: If the miner is STOPPED for a long time, its `currentX/Y/Z` still holds the last mined block position. When resuming IDLE -> MOVING, it will interpolate from there. This is correct behavior (walks from where it stopped).
- **Concurrent zone regen**: If zone regens while miner is MOVING toward a block, the target block gets replaced. The miner will still "break" it (set to air + markBroken). This is acceptable — the block was just regen'd, so breaking it again is fine.
- **Entity ref resolution**: `teleportMiner` resolves the entity ref fresh each tick via `getRefFromUUID(entityUuid)` inside `world.execute()`, per `HYTALE_API.md` line 489. The cached `entityRef` on `MinerRobotState` is only used for spawn/despawn bookkeeping, never for teleport.
- **Multiple `world.execute()` per tick**: `teleportMiner` dispatches to world thread every 50ms tick during MOVING. This is the same pattern as `PetManager` — proven safe.
- **No animation**: Kweebec won't have a mining arm swing. It will walk to the block, stand there, then the block disappears. Acceptable per design decision.
- **Target collision (player or other miner breaks the same block)**: `tickMining` uses `mineManager.tryClaimBlock()` — atomic CAS via `ConcurrentHashMap.Set.add()`. Only the first caller gets `true` and receives the reward. `MineBreakSystem` also uses this, with the claim gating BEFORE any `setBlock(air)` to prevent a losing player from destroying the block. `unclaimBlock()` rolls back claims when bag fills between claim and break.
- **Production rate pacing**: `tickIdle` enforces `productionIntervalMs` via `cycleStartTime`. `cycleStartTime` is seeded on spawn so the first cycle waits a full interval, matching the original `lastProductionTick` seeding.
- **Loot-visual consistency**: `readBlockTypeAndReward` reads the actual block type from the chunk on the world thread (per `HYTALE_API.md` requirements). Block is only set to air AFTER successful inventory add. If bag is full, claim is rolled back and block stays intact — matching current behavior where both miner and player refuse to break when bag is full.
- **Evolution mid-cycle**: `syncMinerEvolution` calls `state.resetPhaseForEvolution()` after respawn to clear stale MOVING/MINING state and target. `cycleStartTime` is intentionally preserved so the production timer continues from where it was, matching current code where `lastProductionTick` survives evolution.
