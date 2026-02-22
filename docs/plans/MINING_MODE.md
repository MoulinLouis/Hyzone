# Mining Mode — Design Document

Horizontal progression system for parkour-ascend. A second incremental farming loop alongside the existing parkour/runners system, sharing the same idle/automation DNA.

## Access

- Unlocked after a few ascensions (vertical gate before horizontal content)
- Mines are physically located inside the mountains on the existing map — no world teleport
- Player walks into the mountain entrance to reach the mine

## Structure: 3 Mine Rooms

- Unlockable progressively (by mining level or currency purchase)
- Each room contains different/better ore types
- UI menu follows the same visual identity as the existing maps/runners interface

## Core Mining Mechanic

### Per-Player Block State (no instances)

Everything happens in the shared world. Each player sees their own ore state via client-side packets.

**Technical approach:**
- Predefined zones with ore blocks on the walls/floor of each room
- When a player mines a block: send `ServerSetBlock` packet **only to that player** via `packetHandler.writeNoCache(new ServerSetBlock(x, y, z, stoneBlockId, filler, rotation))`
- The actual world blocks never change — other players still see the original ore
- After a configurable respawn timer: send another `ServerSetBlock` back to the player with a random ore blockId from the room's ore table
- On player reconnect: resend all `ServerSetBlock` packets to restore the player's current mine state

**Key APIs:**
- `packetHandler.writeNoCache(new ServerSetBlock(...))` — per-player visual block change (same pattern as camera/sound packets)
- `BlockTypeAssetMap.getIndex(key)` — resolve block type name to integer blockId
- `ServerSetBlock(int x, int y, int z, int blockId, short filler, byte rotation)` — the packet structure

### Mining Flow

1. Player hits an ore block at position (x, y, z)
2. Server tracks: "this player mined this block", awards the drop
3. Server sends `ServerSetBlock` to that player only — ore visually becomes stone for them
4. After respawn timer expires — server sends `ServerSetBlock` with a random ore blockId — player sees a new ore
5. Other players see no change, they interact with their own independent state

## Ores

- Use base Hytale ore types initially (no custom models/textures needed)
- Each room has a weighted ore table defining which ores can spawn and at what probability
- Higher rooms = rarer/more valuable ores in the table

## Economy

### Inventory / Backpack

- Mined ores go into a dedicated mining inventory (separate from parkour)
- Player chooses: **keep** specific ores (for crafting upgrades) or **sell** (for immediate currency)
- Strategic choice: quick cash vs. stockpiling for expensive upgrades
- Backpack capacity is upgradable

### Currency

- Selling ores produces mining currency (TBD: same coins as parkour or separate currency)
- Currency funds both mining upgrades and parkour cross-upgrades

## Progression

### Manual Upgrades

| Upgrade | Effect |
|---------|--------|
| Pickaxe efficiency | Mine blocks faster (reduced mining time) |
| Pickaxe yield | More drops per block mined |
| Respawn timer | Ores respawn faster after being mined |
| Backpack capacity | Carry more ores before needing to sell |

### Automation: Miners

The equivalent of runners for parkour.

- Unlockable after reaching a mining progression threshold
- Visible ghost entities that walk around the mine room and auto-mine ores
- **Visible to all players** — everyone sees each other's miners (social/satisfying element)
- Miners collect ores into the player's inventory automatically

**Miner upgrades:**

| Upgrade | Effect |
|---------|--------|
| Movement speed | Miners move between ore nodes faster |
| Mining speed | Miners break ores faster |
| Carry capacity | Miners collect more before depositing |
| Miner count | Unlock additional miners per room |

## Cross-Loop Integration

The mining loop feeds back into the parkour loop:

- Certain mining upgrades improve parkour systems (runner speed, coin multipliers, etc.)
- Player alternates between both loops based on preference
- Both systems benefit from each other, creating a reason to engage with both

## Server-Side State Tracking

Per-player mine state stored in memory:

```
PlayerMineState {
    Map<BlockPosition, MinedBlock> minedBlocks;  // blocks currently "empty" for this player
    // MinedBlock tracks: original ore type, time mined, respawn timer

    MiningStats stats;          // pickaxe level, yield multiplier, etc.
    MinerList miners;           // active miners and their upgrades
    Inventory miningInventory;  // current ore inventory
}
```

- State persisted to database for cross-session continuity
- On disconnect: pause respawn timers
- On reconnect: resume timers, resend all `ServerSetBlock` packets to restore visual state

## Open Questions

- [ ] Same coins as parkour or separate mining currency?
- [ ] Exact ascension count required to unlock mining
- [ ] Room unlock costs and progression curve
- [ ] Ore types and their values per room
- [ ] Miner pathfinding approach (predefined waypoints vs. dynamic navigation to nearest ore)
- [ ] How miners interact with per-player block state (do miners trigger `ServerSetBlock` too?)
- [ ] Specific parkour upgrades unlockable through mining
- [ ] Mining inventory UI layout
