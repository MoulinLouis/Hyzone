# Ascend Mode - Current Implementation Status

Last updated: 2026-01-27

This document summarizes what has been implemented so far for Hyvexa: Parkour Ascend.

## Module Structure

- Module: `hyvexa-parkour-ascend`
- Entry point: `io.hyvexa.ascend.ParkourAscendPlugin`
- Packages created: `command`, `data`, `robot`, `tracker`, `util`

## Data Layer (Phase 1)

### Constants
- File: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java`
- Economy constants: storage size, upgrade multipliers, max levels.
- Robot constants: entity type, tick interval, base speed, jump force, waypoint reach distance.
- Persistence timing: save debounce interval.

### Data Models
- `AscendMap` with all map metadata, start/finish transforms, and waypoint list.
- `AscendPlayerProgress` tracking coins and per-map progress (unlock, manual completion, robot levels, pending coins).

### Stores
- `AscendMapStore`:
  - In-memory map storage with MySQL load/save/delete.
  - Waypoints stored as JSON in `waypoints_json`.
- `AscendPlayerStore`:
  - In-memory player progress with MySQL load/save.
  - Debounced async save (5s default).
  - Supports coins, map progress, and pending coins.

### Database Schema Setup
- `AscendDatabaseSetup` creates:
  - `ascend_players`
  - `ascend_maps`
  - `ascend_player_maps`
  - `ascend_upgrade_costs`

## Plugin Bootstrap (Phase 2)

- `ParkourAscendPlugin.setup()`:
  - Ensures Ascend tables exist.
  - Loads `AscendMapStore` and `AscendPlayerStore`.
  - Registers `/ascend` command.
  - Registers ModeEnter handler for Ascend inventory setup.

## Commands (Phase 3)

- Command: `/ascend`
  - Default: shows current coin count.
  - `/ascend stats`: shows current coin count.
  - `/ascend collect`: collects all pending coins from map progress into wallet.
- Command is gated to Ascend mode only. If used in Hub/Parkour:
  - Message: `Use /menu to enter Ascend.`

## Manual Run Tracking (Phase 4)

- `AscendRunTracker`:
  - Tracks active manual runs by player UUID.
  - Detects finish by radius around map finish coords.
  - On completion:
    - Marks map as manually completed.
    - Grants base reward coins.
    - Sends completion/coin messages.
    - Teleports player back to map start.
  - Includes helpers to start runs and teleport to map start.
- Note: Tracker is implemented but not yet wired into a scheduled tick in the plugin.

## Robot System (Phase 5)

- `RobotState`:
  - Owner UUID, map ID, entity ref, waypoint index, timing, waiting flag, run count.
- `RobotManager` skeleton:
  - Tick loop scheduled at 200ms.
  - Spawn/despawn robot bookkeeping only (no entity spawn yet).
  - Movement/earnings logic is TODO.
- Note: Robot manager is not yet wired into the plugin.

## Hub Routing / Mode Integration

- Hub menu Ascend button now routes to the Ascend world.
- On routing to Ascend:
  - Inventory is cleared.
  - Server Selector (golem) is placed in the last hotbar slot.
- Ascend plugin also applies the same inventory reset + golem in ModeEnter (ASCEND).

## Inventory Behavior in Ascend

- Inventory is fully cleared when entering Ascend mode.
- The golem Server Selector item (`Hub_Server_Selector`) is placed in the last hotbar slot.
- Future Ascend-only items can be added after this step.

## Pending / Not Yet Implemented

- Wire `AscendRunTracker` tick loop to online players.
- Wire `RobotManager` startup/shutdown in plugin.
- Robot entity spawning, waypoint movement, and earnings accumulation.
- Collection UI and central chest interaction.
- Upgrades UI, upgrade cost system, and admin map tools.
- Mode exit cleanup for Ascend (if needed beyond hub routing).

