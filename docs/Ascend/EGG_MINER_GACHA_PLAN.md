# Plan: Egg-Based Miner Gacha System

## Context

The current Ascend miner system is fixed: 1 miner per mine, purchased for 1000 crystals, with speed_level + stars (evolution). This plan replaces it with a gacha system:
- Manually breaking blocks has a chance to drop an egg (tied to the layer)
- Opening the egg yields a miner of random rarity
- The miner's rarity determines its block probability table (each layer+rarity combo has its own table)
- Miners are collected in an unlimited inventory and assigned to global slots

**What changes:**
- REMOVE: stars/evolution, fixed miner purchase
- KEEP: speed_level (upgradeable with crystals), physical NPC slots, conveyor system
- ADD: egg drops, egg inventory, miner collection, per-rarity block tables, management UI

---

## Phase 1 — Data Model & Enums

### 1.1 New enum `MinerRarity.java`
**Create:** `ascend/mine/data/MinerRarity.java`

```java
public enum MinerRarity {
    COMMON("Common", "#aaaaaa"),
    UNCOMMON("Uncommon", "#10b981"),
    RARE("Rare", "#3b82f6"),
    EPIC("Epic", "#a855f7"),
    LEGENDARY("Legendary", "#f59e0b");

    private final String displayName;
    private final String color;
    // + getters, static fromName()
}
```

### 1.2 New class `CollectedMiner.java`
**Create:** `ascend/mine/data/CollectedMiner.java`

```java
public class CollectedMiner {
    long id;           // auto-increment DB ID (unique per miner instance)
    String layerId;    // origin layer
    MinerRarity rarity;
    int speedLevel;    // upgradeable with crystals
}
```

Production/cost formulas (simplified, no stars):
- `getProductionRate(speedLevel)`: `6.0 * (1.0 + speedLevel * 0.10)`
- `getSpeedUpgradeCost(speedLevel)`: `Math.round(50 * Math.pow(1.15, speedLevel))`

### 1.3 Modify `MineZoneLayer.java`
**File:** `ascend/mine/data/MineZoneLayer.java`

Add:
- `double eggDropChance = 0.5` — configurable per layer
- `String displayName` — human-readable name for UI
- `Map<MinerRarity, Map<String, Double>> rarityBlockTables` — block table per rarity
- `getBlockTableForRarity(MinerRarity)` — returns rarity-specific table, falls back to base `blockTable`

### 1.4 Modify `MinerRobotState.java`
**File:** `ascend/mine/robot/MinerRobotState.java`

- **Remove:** `stars`, `MAX_STARS`, `MAX_SPEED_PER_STAR`, `getMinerEvolveCost()`, `getMinerBuyCost()`, star-dependent formulas
- **Keep:** `mineId` — still required for `configStore.getMinerSlot(mineId, slotIndex)`, conveyor waypoints, and block cleanup
- **Add:** `long minerId`, `String originLayerId`, `MinerRarity rarity`
- **Modify constructor:** `(UUID ownerId, String mineId, int slotIndex, long minerId, String originLayerId, MinerRarity rarity)`
- **Simplify:** `getProductionRate()` without stars, `getMinerSpeedCost()` without stars

### 1.5 Modify `MinePlayerProgress.java`
**File:** `ascend/mine/data/MinePlayerProgress.java`

**Remove:**
- `MinerProgress` inner class, `minerStates` map
- `purchaseMiner()`, `evolveMiner()`, `MinerEvolutionResult`, `MinerPurchaseResult`
- `getMinerSnapshot()`, `loadMinerState()`, `getMinerStates()`
- `MinerProgressSnapshot(boolean hasMiner, int speedLevel, int stars)`

**Add:**
- `Map<String, Integer> eggInventory` — layerId -> count
- `List<CollectedMiner> minerCollection` — all collected miners
- `Map<Integer, Long> slotAssignments` — slotIndex -> minerId

Methods:
- `addEgg(layerId)` / `removeEgg(layerId)` / `getEggCount(layerId)` / `getEggInventory()`
- `addMiner(CollectedMiner)` / `getMinerById(long)` / `getMinerCollection()`
- `assignMinerToSlot(slotIndex, minerId)` / `unassignSlot(slotIndex)` / `getAssignedMinerId(slotIndex)` / `getAssignedMiner(slotIndex)` / `isSlotAssigned(slotIndex)`
- `upgradeMinerSpeed(long minerId, long cost)` — finds miner by ID, increments speed, spends crystals

**Modify `PlayerSaveSnapshot`:** Replace `minerStates` with `eggInventory`, `minerCollection`, `slotAssignments`

---

## Phase 2 — Database Schema

### 2.1 New tables in `AscendDatabaseSetup.java`
**File:** `ascend/data/AscendDatabaseSetup.java`

```sql
-- Player egg inventory
CREATE TABLE IF NOT EXISTS mine_player_eggs (
    player_uuid VARCHAR(36) NOT NULL,
    layer_id VARCHAR(64) NOT NULL,
    count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, layer_id)
) ENGINE=InnoDB;

-- Player miner collection
CREATE TABLE IF NOT EXISTS mine_player_miners_v2 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    layer_id VARCHAR(64) NOT NULL,
    rarity VARCHAR(16) NOT NULL,
    speed_level INT NOT NULL DEFAULT 0,
    INDEX idx_player (player_uuid)
) ENGINE=InnoDB;

-- Miner-to-slot assignments
CREATE TABLE IF NOT EXISTS mine_player_slot_assignments (
    player_uuid VARCHAR(36) NOT NULL,
    slot_index INT NOT NULL,
    miner_id BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, slot_index)
) ENGINE=InnoDB;

-- Per-layer per-rarity block tables (admin-configured)
CREATE TABLE IF NOT EXISTS mine_layer_rarity_blocks (
    layer_id VARCHAR(64) NOT NULL,
    rarity VARCHAR(16) NOT NULL,
    block_table_json TEXT NOT NULL DEFAULT '{}',
    PRIMARY KEY (layer_id, rarity)
) ENGINE=InnoDB;
```

### 2.2 Add columns to `mine_zone_layers`
```sql
ALTER TABLE mine_zone_layers ADD COLUMN IF NOT EXISTS egg_drop_chance DOUBLE DEFAULT 0.5;
ALTER TABLE mine_zone_layers ADD COLUMN IF NOT EXISTS display_name VARCHAR(64) DEFAULT '';
```

### 2.3 Migrate existing data

The old `mine_player_miners` table contains active miners that must be migrated:

```sql
-- For each player with has_miner=1, create a COMMON miner in v2
-- (origin layer unknown, assign first layer of the mine)
INSERT INTO mine_player_miners_v2 (player_uuid, layer_id, rarity, speed_level)
SELECT m.player_uuid, (SELECT id FROM mine_zone_layers ORDER BY min_y ASC LIMIT 1), 'COMMON', m.speed_level
FROM mine_player_miners m WHERE m.has_miner = 1;

-- Recreate slot assignments from old data
INSERT INTO mine_player_slot_assignments (player_uuid, slot_index, miner_id)
SELECT m.player_uuid, m.slot_index, v2.id
FROM mine_player_miners m
JOIN mine_player_miners_v2 v2 ON v2.player_uuid = m.player_uuid
WHERE m.has_miner = 1;
```

Implementation: `migrateOldMiners(Connection conn)` method in `AscendDatabaseSetup.java`, executed once (check if `mine_player_miners_v2` is empty before migrating). After migration, the old table is no longer read or written.

### 2.4 Seed default rarity block tables

Rarity block tables must be seeded on startup if absent. For each existing layer, generate 5 tables (one per rarity) based on the layer's base block table:
- **COMMON**: base table unchanged
- **UNCOMMON**: rare block weights x1.25, common block weights x0.90
- **RARE**: rare block weights x1.5, common block weights x0.75
- **EPIC**: rare block weights x2.0, common block weights x0.50
- **LEGENDARY**: rare block weights x3.0, common block weights x0.25

"Rare blocks" = the 2 blocks with the lowest weight in the base table. Implement in `MineConfigStore.seedRarityBlockTables(conn)`, called in `syncLoad()` after `loadLayerRarityBlocks()` if any layers are missing rarity tables.

---

## Phase 3 — Persistence

### 3.1 Modify `MinePlayerStore.java`
**File:** `ascend/mine/data/MinePlayerStore.java`

**`loadFromDatabase()`:**
- Replace loading from `mine_player_miners` with:
  - Load eggs from `mine_player_eggs`
  - Load collection from `mine_player_miners_v2`
  - Load assignments from `mine_player_slot_assignments`

**`savePlayerSync()`:**
- Replace saving to `mine_player_miners` with:
  - Eggs: delete + re-insert (same pattern as inventory)
  - Miners: UPDATE speed_level for each miner (INSERTs happen at egg opening time)
  - Slot assignments: delete + re-insert

**Add `insertMiner(UUID, CollectedMiner)`:**
- Synchronous insert into `mine_player_miners_v2`, returns auto-generated ID (RETURN_GENERATED_KEYS)
- Called only when opening an egg (not batched — we need the ID immediately)

### 3.2 Modify `MineConfigStore.java`
**File:** `ascend/mine/data/MineConfigStore.java`

- Modify `loadLayers()`: also load `egg_drop_chance` and `display_name`
- Add `loadLayerRarityBlocks()`: loads from `mine_layer_rarity_blocks` and populates `MineZoneLayer.rarityBlockTables`
- Add `getLayerById(String)`: iterates mines/zones/layers to find by ID

---

## Phase 4 — Egg Drop

### 4.1 Create `EggDropHelper.java`
**Create:** `ascend/mine/system/EggDropHelper.java`

```java
public static void tryDropEgg(UUID playerId, MineZone zone, int blockY,
                               MinePlayerProgress progress, MinePlayerStore store) {
    // 1. Find the layer for this Y
    MineZoneLayer layer = zone.getLayerForY(blockY); // add this method on MineZone
    if (layer == null) return; // no layer = no egg

    // 2. Roll against eggDropChance
    if (ThreadLocalRandom.current().nextDouble() >= layer.getEggDropChance()) return;

    // 3. Add egg
    progress.addEgg(layer.getId());
    store.markDirty(playerId);

    // 4. Toast notification (reuse MineRewardHelper pattern)
}
```

### 4.2 Hook into `MineBreakSystem.java`
**File:** `ascend/mine/system/MineBreakSystem.java`

After the `MineRewardHelper.rewardBlock(...)` call (~line 112), add:
```java
EggDropHelper.tryDropEgg(playerId, zone, by, mineProgress, minePlayerStore);
```

### 4.3 Hook into `MineDamageSystem.java`
**File:** `ascend/mine/system/MineDamageSystem.java`

Same addition after `rewardBlock()`. Both systems handle manual player mining (1-HP and multi-HP blocks).

**Do NOT** add in `MineRobotManager.tickMiner()` — NPC miners must not generate eggs.

---

## Phase 5 — Egg Opening

### 5.1 Create `EggOpenService.java`
**Create:** `ascend/mine/egg/EggOpenService.java`

```java
public class EggOpenService {
    // Rarity weights (20% each for testing)
    private static final Map<MinerRarity, Double> RARITY_WEIGHTS = Map.of(
        COMMON, 20.0, UNCOMMON, 20.0, RARE, 20.0, EPIC, 20.0, LEGENDARY, 20.0
    );

    public CollectedMiner openEgg(UUID playerId, String layerId,
                                   MinePlayerProgress progress, MinePlayerStore store) {
        if (progress.getEggCount(layerId) <= 0) return null;
        progress.removeEgg(layerId);

        MinerRarity rarity = rollRarity();
        CollectedMiner miner = new CollectedMiner(0, layerId, rarity, 0);
        long dbId = store.insertMiner(playerId, miner);
        miner.setId(dbId);
        progress.addMiner(miner);
        store.markDirty(playerId);
        return miner;
    }

    private MinerRarity rollRarity() {
        // Weighted random selection
    }
}
```

---

## Phase 6 — MineRobotManager Updates

### 6.1 Modify `MineRobotManager.java`
**File:** `ascend/mine/robot/MineRobotManager.java`

**`onPlayerJoin()`:**
- Instead of checking `minerProg.hasMiner()` per slot, check `progress.isSlotAssigned(slotIndex)`
- Get the assigned `CollectedMiner` and pass its data to `spawnMiner()`

**`spawnMiner()`:**
- Modify to accept `CollectedMiner` data (minerId, layerId, rarity)
- Build `MinerRobotState` with the new fields

**`tickMiner()` (critical change):**
- Replace:
  ```java
  Map<String, Double> blockTable = zone.getBlockTableForY(slot.getBlockY());
  ```
- With:
  ```java
  MineZoneLayer originLayer = configStore.getLayerById(state.getOriginLayerId());
  Map<String, Double> blockTable = (originLayer != null)
      ? originLayer.getBlockTableForRarity(state.getRarity())
      : zone.getBlockTableForY(slot.getBlockY());
  ```
- Same change in `placeInitialBlock()`

**`syncPurchasedMiner()` -> `syncAssignedMiner()`:**
- Rename. Spawns the miner when assigned to a slot (instead of purchased).

**`syncMinerEvolution()` -> remove** (no more evolution)

**`syncMinerSpeed()`:** Keep, simply updates speedLevel on the runtime state.

---

## Phase 7 — UI

### 7.1 Modify `Ascend_MinePage.ui`
**File:** `resources/Common/UI/Custom/Pages/Ascend_MinePage.ui`

- Add tabs: "Eggs", "Collection" (alongside "Upgrade", rename "Miner" to "Slots")
- Each tab toggles visibility of its content group

### 7.2 Create `Ascend_MineEggEntry.ui`
Entry template for the eggs tab. Shows: layer display name, egg count, "Open" button.

### 7.3 Create `Ascend_MineCollectionEntry.ui`
Entry template for the collection tab. Shows: layer name, rarity badge (colored label), speed level.

### 7.4 Replace `Ascend_MinePageMinerEntry.ui` -> `Ascend_MineSlotEntry.ui`
Slot entry template. Shows: "Slot #N", assigned miner info (layer + rarity) or "Empty", "Assign"/"Remove" button, "Upgrade Speed" button if assigned.

### 7.5 Create `Ascend_MineMinerPicker.ui`
Picker shown when clicking "Assign" on an empty slot. Lists available miners from collection. Similar pattern to `MineBlockPickerPage`.

### 7.6 Modify `MinePage.java`
**File:** `ascend/mine/ui/MinePage.java`

- Remove: `BUTTON_BUY_MINER_PREFIX`, `BUTTON_MINER_EVOLVE_PREFIX` and all stars/evolution logic
- Add handlers for: Eggs/Collection/Slots tabs, Open/Assign/Remove/UpgradeSpeed buttons
- Add rendering for the 3 new tabs

---

## Phase 8 — Missed Dependencies (MineBonusCalculator + Achievements)

### 8.1 Modify `MineBonusCalculator.java`
**File:** `ascend/mine/MineBonusCalculator.java`

The `allSlotsHaveMiners()` method uses `progress.getMinerSnapshot(slot.getSlotIndex()).hasMiner()`. Replace with `progress.isSlotAssigned(slot.getSlotIndex())`.

### 8.2 Modify `MineAchievement.java`
**File:** `ascend/mine/achievement/MineAchievement.java`

Replace achievements that become impossible:
- `FIRST_MINER` ("Buy your first miner") -> `FIRST_EGG` ("Find your first egg", 500 crystals)
- `EVOLVE_STAR1` ("Evolve a miner to Star 1") -> `FIRST_LEGENDARY` ("Obtain a Legendary miner", 2500 crystals)

Update `MineAchievementsPage.java` if rendering depends on specific names/IDs.

### 8.3 Trigger new achievements

- `FIRST_EGG`: trigger in `EggDropHelper.tryDropEgg()` after first egg added
- `FIRST_LEGENDARY`: trigger in `EggOpenService.openEgg()` when `rarity == LEGENDARY`

---

## Phase 9 — Admin (optional, can be done later)

- Add admin page to configure per-rarity block tables per layer
- Add debug commands: `/mine giveegg <player> <layerId> [count]`, `/mine giveminer <player> <layerId> <rarity>`

---

## Implementation Order

1. **Phase 1** — Data model (foundation)
2. **Phase 2** — DB schema + migration + rarity seed (tables must exist)
3. **Phase 3** — Persistence (load/save)
4. **Phase 4** — Egg drops (first testable feature)
5. **Phase 5** — Egg opening (depends on Phase 3)
6. **Phase 6** — Robot manager (depends on Phases 1, 3)
7. **Phase 7** — UI (depends on all above)
8. **Phase 8** — MineBonusCalculator + Achievements (compile-blockers)
9. **Phase 9** — Admin (after everything works)

## Critical Files

| File | Action |
|------|--------|
| `ascend/mine/data/MinerRarity.java` | CREATE |
| `ascend/mine/data/CollectedMiner.java` | CREATE |
| `ascend/mine/egg/EggOpenService.java` | CREATE |
| `ascend/mine/system/EggDropHelper.java` | CREATE |
| `ascend/mine/data/MineZoneLayer.java` | MODIFY |
| `ascend/mine/data/MinePlayerProgress.java` | MODIFY (large) |
| `ascend/mine/data/MinePlayerStore.java` | MODIFY (large) |
| `ascend/mine/robot/MinerRobotState.java` | MODIFY |
| `ascend/mine/robot/MineRobotManager.java` | MODIFY |
| `ascend/mine/system/MineBreakSystem.java` | MODIFY (small) |
| `ascend/mine/system/MineDamageSystem.java` | MODIFY (small) |
| `ascend/mine/ui/MinePage.java` | MODIFY (large) |
| `ascend/data/AscendDatabaseSetup.java` | MODIFY |
| `ascend/mine/data/MineConfigStore.java` | MODIFY |
| `ascend/mine/MineBonusCalculator.java` | MODIFY (small) |
| `ascend/mine/achievement/MineAchievement.java` | MODIFY (small) |
| UI files (.ui) | CREATE 4 + MODIFY 1 |

## Verification

1. **Phase 4:** Break blocks in a layer -> verify eggs appear in `eggInventory` (~50% of the time)
2. **Phase 5:** Open an egg -> verify a miner appears in `minerCollection` with random rarity
3. **Phase 6:** Assign a miner to a slot -> verify the NPC spawns and mines blocks from the correct table (layer+rarity)
4. **Phase 7:** UI -> verify tabs correctly display eggs/collection/slots and buttons work
5. **Regression:** Verify manual mining (rewards, fortune, momentum, AoE) still works normally
