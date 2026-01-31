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
- PlayerReady: if in Ascend world, clears inventory, gives the Ascend dev items + hub selector, and attaches the Ascend HUD.
- AddPlayerToWorld: re-ensures Ascend dev items + hub selector when entering the Ascend world (e.g., respawn).
- PlayerDisconnect: clears HUD caches and cancels active run.
- Schedules a 200ms tick that runs run tracking + HUD updates on the Ascend world thread.

## Data model + persistence
- Tables created: `ascend_players`, `ascend_maps`, `ascend_player_maps`, `ascend_upgrade_costs`.
- `AscendMap` stores map metadata + start/finish coords + waypoint list + display order + price/reward.
- `AscendMapStore` loads maps from MySQL, caches in memory, and saves updates.
- `AscendPlayerProgress` stores coins, rebirth multiplier, and per-map progress (unlock, completion, robot count, robot upgrades, multiplier digits).
- `ascend_player_maps` now tracks `robot_count` (keeps `has_robot` in sync for legacy reads).
- `AscendPlayerStore` loads players + per-map progress, writes back with debounced saves and exposes helpers for
  map unlocks, per-map multiplier digits, and digit-product payout calculations.
  Also exposes robot count getters/updates and upgrades legacy robot flags on load.

## Commands
- `/ascend`:
  - No args: opens map select UI and teleports to map start on selection.
  - `stats`: shows current coin balance, digit product, rebirth multiplier, and the current digit string.
  - `rebirth`: converts all coins into rebirth multiplier (+1 per 1000 coins) and resets coins to 0.
  - All subcommands are gated to Ascend world via `AscendModeGate`.
- `/as admin`:
  - Opens Ascend admin landing page with Maps and Admin Panel buttons.
  - Maps: opens the map admin UI for creation/updates.
  - Admin Panel: opens the coins admin page to add/remove your own coins by input value.
  - `/as admin map <create|setstart|setfinish|addwaypoint|clearwaypoints|setreward|setprice|setorder|list>` for map setup.
  - `/as admin holo map <id>` and `/as admin holo delete map <id>` to place/remove map info holograms.
  - OP-only (via `PermissionUtils.isOp`).

## Run tracking
- `AscendRunTracker` starts runs when a player selects a map.
- Run completion is a radius check around the map finish position.
- Completion:
  - Marks manual completion, unlocks the map, increments that map's digit, and pays out the digit product.
  - Teleports the player back to map start using `Teleport`.

## HUD
- `AscendHud` loads `Pages/Ascend_RunHud.ui` and overwrites the HUD label text with "HYVEXA ASCEND".
- HUD reattaches on Ascend tick if needed and waits briefly before applying static text.
- HUD now shows a centered coins banner under the top bar and the 5 colored digit slots updated from the player store.
- Coin display uses compact scientific notation over 1,000,000,000 to prevent HUD cropping.
- When coins >= 1000, a rebirth panel appears with current->next multiplier and a `/ascend rebirth` prompt.
- A full-width top banner bar shows colored number slots with "x" separators for future multipliers and is scaled up for visibility.
- Ascend run HUD layout now mirrors the Hub HUD info box (placeholder hint text + store info), with the product row removed.
- Rebirth panel is positioned under the top banner on the upper-right.
- Run HUD UI is duplicated in multiple resource paths for lookup compatibility:
  - `Common/UI/Custom/Pages/Ascend_RunHud.ui`
  - `Pages/Ascend_RunHud.ui`
  - `Custom/Pages/Ascend_RunHud.ui`

## UI pages
- `Ascend_MapSelect.ui` + `Ascend_MapSelectEntry.ui`: map selection list with digit value, payout, lock/price status,
  per-map robot counts, and a robot purchase button showing cost and +1 robot label (layout tuned for readability).
- `Ascend_MapAdmin.ui` + `Ascend_MapAdminEntry.ui`: admin map management UI with price/order fields, selectable map list highlighting, and map holo controls.
- `Ascend_AdminPanel.ui`: admin landing page with Maps/Admin Panel navigation.
- `Ascend_AdminCoins.ui`: admin coins page to add/remove your own coins.

## Holograms (Hylograms)
- Map info holograms only (in-world text for map name, reward, price, and a prompt).
- Managed via `AscendHologramManager` using `HylogramsBridge` (no direct Hylograms API imports).

## Robot system (skeleton)
- `RobotManager` manages robot state and schedules a tick loop.
- `RobotState` tracks owner/map, waypoint index, waiting timers, and run count.
- Spawn/despawn and waypoint movement are TODOs (no world entity operations yet).
