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

## Automatic balancing system
All map balancing is calculated from `displayOrder` (0-4) using constants in `AscendConstants`:

| Level | Color  | Unlock | Runner | Reward | Run Time |
|-------|--------|--------|--------|--------|----------|
| 0     | Rouge  | 0      | 50     | 1      | 5s       |
| 1     | Orange | 100    | 200    | 5      | 15s      |
| 2     | Jaune  | 500    | 1000   | 25     | 30s      |
| 3     | Vert   | 2500   | 5000   | 100    | 1m       |
| 4     | Bleu   | 10000  | 20000  | 500    | 2m       |

- `AscendMap.getEffectivePrice()` → unlock price from displayOrder
- `AscendMap.getEffectiveRobotPrice()` → runner price from displayOrder
- `AscendMap.getEffectiveBaseReward()` → coin reward from displayOrder
- `AscendMap.getEffectiveBaseRunTimeMs()` → runner completion time from displayOrder

### Multiplier increments
- Manual run: +1.0 multiplier per completion (`MANUAL_MULTIPLIER_INCREMENT`)
- Runner run: +0.01 × 2^stars multiplier per completion (see star evolution below)
- Speed upgrade: +10% per level (`SPEED_UPGRADE_MULTIPLIER`)
- Speed upgrade cost: 100 × 2^level

### Star evolution system
Runners can evolve up to 5 stars. When a runner reaches max speed level (20), the player can evolve it:
- Speed level resets to 0
- Star count increments by 1
- Multiplier increment doubles (0.01 → 0.02 → 0.04 → 0.08 → 0.16 → 0.32)
- Runner entity changes appearance

| Stars | Entity Type | Multiplier/Run |
|-------|-------------|----------------|
| 0 | Kweebec_Sapling (green) | +0.01 |
| 1 | Kweebec_Sapling_Orange | +0.02 |
| 2 | Kweebec_Sapling (green) | +0.04 |
| 3 | Kweebec_Sapling_Orange | +0.08 |
| 4 | Kweebec_Sapling (green) | +0.16 |
| 5 | Kweebec_Sapling_Orange | +0.32 |

### First map auto-unlock
- Maps with price = 0 (displayOrder 0) are automatically unlocked
- `MapUnlockHelper.checkAndEnsureUnlock()` handles auto-unlock for free maps and first map
- Reset progress unlocks the first map (lowest displayOrder) automatically

## Data model + persistence
- Tables created: `ascend_players`, `ascend_maps`, `ascend_player_maps`, `ascend_upgrade_costs`.
- `AscendMap` stores map metadata + start/finish coords + waypoint list + display order.
  - Price/reward/timing are calculated from displayOrder, not stored per-map.
- `AscendMapStore` loads maps from MySQL, caches in memory, and saves updates.
- `AscendPlayerProgress` stores coins, rebirth multiplier, and per-map progress:
  - `unlocked`: boolean
  - `completedManually`: boolean
  - `hasRobot`: boolean (single runner per map)
  - `robotSpeedLevel`: int (speed upgrade level, 0-20)
  - `robotStars`: int (evolution level, 0-5)
  - `multiplier`: double (accumulates from manual/runner completions)
- `AscendPlayerStore` loads players + per-map progress, writes back with debounced saves.

## Commands
- `/ascend`:
  - No args: opens map select UI and teleports to map start on selection.
  - `stats`: shows current coin balance, digit product, rebirth multiplier, and the current digit string.
  - `rebirth`: converts all coins into rebirth multiplier (+1 per 1000 coins) and resets coins to 0.
  - All subcommands are gated to Ascend world via `AscendModeGate`.
- `/as admin`:
  - Opens Ascend admin landing page with Maps and Admin Panel buttons.
  - Maps: opens the map admin UI for creation/updates.
  - Admin Panel: opens the coins admin page to add/remove coins and reset progress.
  - `/as admin map create <id> <name> [order]` - create map with automatic balancing
  - `/as admin map setstart <id>` - set spawn point at current position
  - `/as admin map setfinish <id>` - set finish point at current position
  - `/as admin map setorder <id> <0-4>` - change level (affects all prices/rewards)
  - `/as admin map setname <id> <name>` - rename map
  - `/as admin map list` - list all maps with balancing info
  - `/as admin holo map <id>` and `/as admin holo delete map <id>` to place/remove map info holograms.
  - OP-only (via `PermissionUtils.isOp`).

## Run tracking
- `AscendRunTracker` starts runs when a player selects a map.
- Run completion is a radius check around the map finish position.
- Completion:
  - Marks manual completion, unlocks the map
  - Adds +1.0 to map multiplier (`MANUAL_MULTIPLIER_INCREMENT`)
  - Pays coins: `baseReward × totalMultiplier` (all map multipliers × elevation multiplier)
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
- `Ascend_MapSelect.ui` + `Ascend_MapSelectEntry.ui`: modern map selection with:
  - 620x620px container with "A S C E N D" header
  - Card layout (580x90px) with 3 zones:
    - Left accent bar (8px): colored per map position
    - Info zone (~436px): map name, 10-segment progress bar, runs/sec status
    - Button zone (128px): accent bar + level/speed/action/price
  - Color palette by position: violet → blue → cyan → amber → red
  - Progress bar: 20 segments showing speed level (Lv.0 to Lv.20)
  - Level display shows stars: "★★★ Lv.5" format
  - Button states: "Buy Runner" → "Upgrade" → "Evolve" (at Lv.20) → "Maxed!" (5 stars + Lv.20)
- `Ascend_MapAdmin.ui` + `Ascend_MapAdminEntry.ui`: simplified admin map management:
  - Only configure: id, name, level (0-4), start, finish
  - Shows automatic balancing info (unlock/runner/reward/time) from code
  - Balancing table displays all level values for reference
  - Map list shows level color, start/finish status, and balancing per map
- `Ascend_AdminPanel.ui`: admin landing page with Maps/Admin Panel navigation.
- `Ascend_AdminCoins.ui`: admin coins page to add/remove coins and reset progress (unlocks first map).

## Holograms (Hylograms)
- Map info holograms only (in-world text for map name, reward, price, and a prompt).
- Managed via `AscendHologramManager` using `HylogramsBridge` (no direct Hylograms API imports).

## Runner system
Runners are auto-completers that passively earn multiplier for a map.

- One runner per map (not multiple)
- Runner completes map at intervals based on `getEffectiveBaseRunTimeMs()`
- Each completion adds multiplier based on star level (0.01 × 2^stars)
- Speed upgrades reduce completion time by +10% per level (max level 20)
- Speed upgrade cost: 100 × 2^level coins
- At max speed level (20), runner can evolve to gain a star (max 5 stars)
- Evolution is free, resets speed to 0, and changes runner appearance

### Implementation
- `RobotManager` manages runner state and schedules a tick loop (16ms interval for ~60fps movement).
- `RobotState` tracks owner/map, speed level, stars, last completion time, run count, and previous position.
- `computeCompletionIntervalMs()` calculates time based on map level and speed upgrades.
- Runners only spawn for online players and despawn when player disconnects.
- `respawnRobot()` despawns and respawns runner with new entity type after evolution.

### Visual NPC system
- Runners spawn as Kweebec NPCs via `NPCPlugin.spawnNPC()`.
- Entity type alternates between `Kweebec_Sapling` (green) and `Kweebec_Sapling_Orange` by star level.
- NPCs are made `Invulnerable` (can't be killed) and `Frozen` (AI disabled).
- Movement is controlled via `Teleport` component at 60fps for smooth animation.
- NPC rotation is calculated from movement direction: `yaw = atan2(dx, dz) + 180`.

### Jump animation system
Runners automatically jump when moving between waypoints based on geometry detection:

| Condition | Threshold | Triggers Jump |
|-----------|-----------|---------------|
| Climbing (Y increase) | ≥ 0.5 blocks | Yes |
| Falling (Y decrease) | ≥ 1.0 blocks | Yes |
| Horizontal gap | ≥ 2.0 blocks | Yes |

Jump interpolation uses parabolic formula: `y = linearY + 4 * jumpHeight * t * (1-t)`
- Creates smooth arc: 0 at start, max at midpoint (t=0.5), 0 at end
- Jump height auto-calculated based on vertical distance and clearance

### Path segments
- `PathSegment` record stores: start, end, length, isJump, jumpHeight
- `buildPathSegments()` constructs path from start → waypoints → finish
- `shouldAutoJump()` detects jumps from geometry
- `interpolateSegmentArray()` handles linear or parabolic interpolation
