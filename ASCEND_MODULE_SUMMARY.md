# Hyvexa Parkour Ascend Module Summary

## Module identity + build
- Module: `hyvexa-parkour-ascend`.
- Plugin manifest: `hyvexa-parkour-ascend/src/main/resources/manifest.json`.
- Entrypoint: `io.hyvexa.ascend.ParkourAscendPlugin`.
- Build: `hyvexa-parkour-ascend/build.gradle` shades core into `HyvexaParkourAscend-<version>.jar` and syncs manifest version/asset pack flags during `processResources`.

## Plugin setup and lifecycle
- Ensures Ascend DB tables on startup (`AscendDatabaseSetup`).
- Loads map store and player store into memory (`AscendMapStore`, `AscendPlayerStore`).
- Starts `AscendRunTracker` for manual completion detection.
- Starts `RobotManager` (skeleton logic with TODOs for spawning and movement).
- Registers `/ascend` and `/as` commands.
- PlayerReady: if in Ascend world, clears inventory and gives the hub selector item, then attaches the Ascend HUD.
- PlayerDisconnect: clears HUD caches and cancels active run.
- Schedules a 200ms tick that runs run tracking + HUD updates on the Ascend world thread.

## Data model + persistence
- Tables created: `ascend_players`, `ascend_maps`, `ascend_player_maps`, `ascend_upgrade_costs`.
- `AscendMap` stores map metadata + start/finish coords + waypoint list + display order + price/reward.
- `AscendMapStore` loads maps from MySQL, caches in memory, and saves updates.
- `AscendPlayerProgress` stores coins and per-map progress (unlock, completion, robot upgrades, pending coins).
- `AscendPlayerStore` loads players + per-map progress, writes back with debounced saves and exposes helpers for
  pending coin totals, map unlocks, and pending coin collection.

## Commands
- `/ascend`:
  - No args: opens map select UI and teleports to map start on selection.
  - `collect`: aggregates pending coins from all maps and adds to player balance.
  - `stats`: shows current coin + pending balance.
  - All subcommands are gated to Ascend world via `AscendModeGate`.
- `/as admin`:
  - Opens Ascend admin UI for map creation/updates.
  - `/as admin map <create|setstart|setfinish|addwaypoint|clearwaypoints|setreward|setprice|setorder|list>` for map setup.
  - `/as admin holo map <id>` and `/as admin holo delete map <id>` to place/remove map info holograms.
  - OP-only (via `PermissionUtils.isOp`).

## Run tracking
- `AscendRunTracker` starts runs when a player selects a map.
- Run completion is a radius check around the map finish position.
- Completion:
  - Marks manual completion, unlocks the map, adds pending coins, persists progress.
  - Teleports the player back to map start using `Teleport`.

## HUD
- `AscendHud` loads `Pages/Ascend_RunHud.ui` and overwrites the HUD label text with "HYVEXA ASCEND".
- HUD reattaches on Ascend tick if needed and waits briefly before applying static text.
- HUD now shows coins, pending coins, and a multiplier placeholder (text-only) updated from the player store.
- A full-width top banner bar shows colored number slots with "x" separators for future multipliers.
- Run HUD UI is duplicated in multiple resource paths for lookup compatibility:
  - `Common/UI/Custom/Pages/Ascend_RunHud.ui`
  - `Pages/Ascend_RunHud.ui`
  - `Custom/Pages/Ascend_RunHud.ui`

## UI pages
- `Ascend_MapSelect.ui` + `Ascend_MapSelectEntry.ui`: map selection list with reward, pending coins, and lock/price status.
- `Ascend_MapAdmin.ui` + `Ascend_MapAdminEntry.ui`: admin map management UI with price/order fields, selectable map list highlighting, and map holo controls.

## Holograms (Hylograms)
- Map info holograms only (in-world text for map name, reward, price, and a prompt).
- Managed via `AscendHologramManager` using `HylogramsBridge` (no direct Hylograms API imports).

## Robot system (skeleton)
- `RobotManager` manages robot state and schedules a tick loop.
- `RobotState` tracks owner/map, waypoint index, waiting timers, and run count.
- Spawn/despawn and waypoint movement are TODOs (no world entity operations yet).
