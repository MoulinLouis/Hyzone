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

| Level | Color  | Unlock Requirement | Runner | Reward | Run Time |
|-------|--------|-------------------|--------|--------|----------|
| 0     | Rouge  | Always unlocked   | 50     | 1      | 5s       |
| 1     | Orange | Map 0 runner Lv.3 | 200    | 5      | 15s      |
| 2     | Jaune  | Map 1 runner Lv.3 | 1000   | 25     | 30s      |
| 3     | Vert   | Map 2 runner Lv.3 | 5000   | 100    | 1m       |
| 4     | Bleu   | Map 3 runner Lv.3 | 20000  | 500    | 2m       |

- `AscendMap.getEffectiveRobotPrice()` → runner price from displayOrder
- `AscendMap.getEffectiveBaseReward()` → coin reward from displayOrder
- `AscendMap.getEffectiveBaseRunTimeMs()` → runner completion time from displayOrder
- Maps unlock progressively based on previous map's runner level (not coin-based)

### Multiplier increments
- Manual run: +0.1 multiplier per completion (`MANUAL_MULTIPLIER_INCREMENT`)
- Runner run: +0.01 × 2^stars multiplier per completion (see star evolution below)
- Speed upgrade: +15% per level (`SPEED_UPGRADE_MULTIPLIER`)
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

### Progressive map unlock system
- Map 1 (displayOrder 0) is always unlocked for all players
- Maps 2-5 unlock automatically when the runner on the previous map reaches level 3
- Once unlocked, maps stay permanently unlocked (even if runner is evolved/reset to level 0)
- Locked maps are completely hidden from the `/ascend` menu
- Instant notification when reaching level 3: "🎉 New map unlocked: [Map Name]!"
- `MapUnlockHelper.meetsUnlockRequirement()` checks if previous map's runner is level 3+
- `MapUnlockHelper.checkAndEnsureUnlock()` handles auto-unlock for maps that meet requirements
- `AscendPlayerStore.checkAndUnlockEligibleMaps()` batch checks all maps after runner upgrade
- Retrocompatibility: Existing players auto-unlock eligible maps based on current runner levels
- Reset progress unlocks only the first map (lowest displayOrder) automatically

## Prestige System (3 Tiers)

The Ascend mode has a layered prestige system with three tiers:

```
TIER 1: ELEVATION   - coins → multiplier, resets coins
TIER 2: SUMMIT      - coins → category upgrades, resets coins + elevation
TIER 3: ASCENSION   - coins → skill tree point, resets everything
```

### Tier 1: Elevation
- Converts coins into permanent elevation multiplier using a **level-based prestige system**.
- **Cost formula**: `1000 × 1.08^level` (exponential growth, +8% per level)
- **Multiplier formula**: `1 + 0.1 × level^0.65` (diminishing returns)
- Skill tree node `COIN_T3_ELEVATION_COST` applies -20% discount to all level costs.
- **Bulk purchasing**: Automatically purchases all affordable levels at once.
- Resets: Coins only
- Manager: `ElevationPage` with real-time UI updates (1s refresh)

| Level | Cost | Multiplier |
|-------|------|------------|
| 1 | 1,000 | x1.10 |
| 10 | ~2.2K | x1.45 |
| 20 | ~4.7K | x1.73 |
| 50 | ~47K | x2.45 |
| 100 | ~2.2M | x3.27 |

### Tier 2: Summit System
Managed by `SummitManager`. Players invest coins into one of three categories:

| Category | Effect per Level | Bonus Type |
|----------|------------------|------------|
| **Coin Flow** | +20% base coin earnings | Passive income |
| **Runner Speed** | +15% runner completion speed | Automation |
| **Manual Mastery** | +25% manual run multiplier | Active play |

**Level thresholds** (cumulative coins required):
| Level | Coins Required | Cumulative |
|-------|----------------|------------|
| 1 | 10K | 10K |
| 2 | 50K | 60K |
| 3 | 200K | 260K |
| 4 | 1M | 1.26M |
| 5 | 5M | 6.26M |
| ... | exponential | up to 10 |

**Resets**: Coins + Elevation → 0

### Tier 3: Ascension System
Managed by `AscensionManager`. The ultimate prestige at 1 trillion coins.

**Requirements**: 1,000,000,000,000 (1T) coins

**Rewards**: +1 skill tree point per Ascension

**Resets**: Coins, Elevation, Summit levels, all map progress

**Skill Tree**: 18 nodes across 5 paths:

| Path | Nodes | Focus |
|------|-------|-------|
| **Coin** (5) | Starting coins, base reward +25%, elevation cost -20%, summit cost -15%, auto-elevation |
| **Speed** (5) | Base speed +10%, max level +5, evolution cost -50%, double lap, instant evolution |
| **Manual** (5) | Manual +50%, chain bonus, session bonus 3x, runner boost, personal best tracking |
| **Hybrid** (2) | Offline earnings, Summit persistence (50% retained) |
| **Ultimate** (1) | +100% to ALL systems |

**Unlock rules**:
- Nodes require prerequisite in same path
- Hybrid nodes require 3+ points in multiple paths
- Ultimate requires 12+ total points spent

### Achievement System
Managed by `AchievementManager`. Achievements grant titles for display.

| Achievement | Requirement | Title |
|-------------|-------------|-------|
| First Steps | 1 manual run | "Beginner" |
| Coin Hoarder | 100K total earned | "Collector" |
| Millionaire | 1M total earned | "Millionaire" |
| Dedicated | 100 manual runs | "Dedicated" |
| Marathon | 1000 manual runs | "Marathoner" |
| First Robot | Buy first runner | "Automator" |
| Army | 5+ active runners | "Commander" |
| Evolved | Evolve to 1+ stars | "Evolver" |
| Summit Seeker | First Summit | "Summiter" |
| Summit Master | Summit Lv.10 any category | "Summit Master" |
| Ascended | First Ascension | "Ascended" |
| Perfectionist | Max runner (5★ Lv.20) | "Perfectionist" |

**Title management**: `/ascend title [name|clear]`

## Data model + persistence
- Tables created: `ascend_players`, `ascend_maps`, `ascend_player_maps`, `ascend_upgrade_costs`, `ascend_player_summit`, `ascend_player_skills`, `ascend_player_achievements`.
- `AscendMap` stores map metadata + start/finish coords + waypoint list + display order.
  - Price/reward/timing are calculated from displayOrder, not stored per-map.
- `AscendMapStore` loads maps from MySQL, caches in memory, and saves updates.
- `AscendPlayerProgress` stores coins, elevation level (multiplier calculated via formula), and per-map progress:
  - `unlocked`: boolean
  - `completedManually`: boolean
  - `hasRobot`: boolean (single runner per map)
  - `robotSpeedLevel`: int (speed upgrade level, 0-20)
  - `robotStars`: int (evolution level, 0-5)
  - `multiplier`: double (accumulates from manual/runner completions)
  - Summit levels per category (Map<SummitCategory, Integer>)
  - Ascension count, skill tree points, unlocked skill nodes
  - Achievement unlocks, active title
  - Lifetime stats: totalCoinsEarned, totalManualRuns
- `AscendPlayerStore` loads players + per-map progress + Summit/Ascension/Achievement data, writes back with debounced saves.

## Commands
- `/ascend`:
  - No args: opens map select UI and teleports to map start on selection.
  - `stats`: shows current coin balance, digit product, elevation level + multiplier, Summit levels, Ascension count, and lifetime stats.
  - `elevate`: opens Elevation UI with level-based prestige (exponential costs, diminishing multiplier returns).
  - `summit [category]`: opens Summit UI to invest coins into category bonuses. Categories: `coin`, `speed`, `manual`.
  - `ascension`: opens Ascension UI to perform ultimate prestige (requires 1 trillion coins).
  - `skills`: shows skill tree status and available nodes to unlock.
  - `achievements`: shows achievement progress and unlocked titles.
  - `title [name|clear]`: shows or sets active title from unlocked achievements.
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
- When coins >= next level cost, an elevation panel appears showing `Lv.N (xM.MM)` and prompting `/ascend elevate`.
- A full-width top banner bar shows colored number slots with "x" separators for multipliers and is scaled up for visibility.
- Ascend run HUD layout now mirrors the Hub HUD info box (placeholder hint text + store info), with the product row removed.
- Elevation panel is positioned under the top banner on the upper-right.
- **Prestige HUD panel** (upper-left): Shows Summit levels per category and Ascension count when player has prestige progress.
  - Updates via `AscendHud.updatePrestige()` with caching to avoid redundant updates.
  - Hidden when player has no Summit levels and no Ascensions.
- Ascend UI files are in `Common/UI/Custom/Pages/` (single source of truth).
- Code references `Pages/Ascend_*.ui` (Hytale resolves this to `Common/UI/Custom/Pages/`).

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
- `Ascend_Elevation.ui` + `ElevationPage.java`: Elevation prestige UI:
  - Compact 340x380 container with real-time updates (1s refresh interval)
  - Shows coins, next level cost, and skill tree discount if active
  - Horizontal "Current -> New" display: `Lv.N (xM.MM) -> Lv.N+K (xM.MM)`
  - Gain indicator shows "+K levels" or "Need X more" coins
  - Dynamic colors: green for purchasable, grey for insufficient funds
  - Bulk purchasing: one click buys all affordable levels
- `Ascend_Summit.ui` + `SummitPage.java`: Summit prestige UI:
  - Displays coins at top with warning about reset
  - 3 category cards (Coin Flow, Runner Speed, Manual Mastery):
    - Colored accent bars (yellow, blue, purple)
    - Current level and bonus percentage
    - Projected new level if Summit performed
    - Per-category Summit button
  - Warning box about resetting coins + elevation
- `Ascend_Ascension.ui` + `AscensionPage.java`: Ascension prestige UI:
  - Progress bar showing coins vs 1T requirement
  - Current Ascension count and available skill points
  - Reward display (+1 SKILL POINT)
  - Red warning section listing all resets
  - Ascend button or locked message based on coin balance
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
