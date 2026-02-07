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
| 0     | Violet | Always unlocked   | 50     | 1      | 10s      |
| 1     | Blue   | Map 0 runner Lv.3 | 200    | 5      | 16s      |
| 2     | Cyan   | Map 1 runner Lv.3 | 1000   | 25     | 26s      |
| 3     | Amber  | Map 2 runner Lv.3 | 5000   | 100    | 42s      |
| 4     | Red    | Map 3 runner Lv.3 | 20000  | 500    | 68s      |

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
| 0 | Kweebec_Seedling | +0.01 |
| 1 | Kweebec_Sapling | +0.02 |
| 2 | Kweebec_Sproutling | +0.04 |
| 3 | Kweebec_Sapling_Pink | +0.08 |
| 4 | Kweebec_Razorleaf | +0.16 |
| 5 | Kweebec_Rootling | +0.32 |

### Progressive map unlock system
- Map 1 (displayOrder 0) is always unlocked for all players
- Maps 2-5 unlock automatically when the runner on the previous map reaches level 5
- Runners are **FREE to purchase** after map unlock (no coin cost)
- Once unlocked, maps stay permanently unlocked (even if runner is evolved/reset to level 0)
- Locked maps are completely hidden from the `/ascend` menu
- Instant notification when reaching level 5: "🎉 New map unlocked: [Map Name]!"
- `MapUnlockHelper.meetsUnlockRequirement()` checks if previous map's runner is level 5+
- `MapUnlockHelper.checkAndEnsureUnlock()` handles auto-unlock for maps that meet requirements
- `AscendPlayerStore.checkAndUnlockEligibleMaps()` batch checks all maps after runner upgrade
- Retrocompatibility: Existing players auto-unlock eligible maps based on current runner levels
- Reset progress unlocks only the first map (lowest displayOrder) automatically
- **Constant:** `MAP_UNLOCK_REQUIRED_RUNNER_LEVEL = 5`

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
- Manager: `ElevationPage` with real-time UI updates (1s refresh, auto-cleanup via `onDismiss()` lifecycle)

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
- Tables created: `ascend_players`, `ascend_maps`, `ascend_player_maps`, `ascend_upgrade_costs`, `ascend_player_summit`, `ascend_player_skills`, `ascend_player_achievements`, `ascend_ghost_recordings`, `ascend_settings`.
- `AscendMap` stores map metadata + start/finish coords + display order.
  - Price/reward/timing are calculated from displayOrder, not stored per-map.
- `AscendMapStore` loads maps from MySQL, caches in memory, and saves updates.
- `AscendPlayerProgress` stores coins (as double for fractional precision), elevation level (multiplier calculated via formula), and per-map progress:
  - `unlocked`: boolean
  - `completedManually`: boolean
  - `hasRobot`: boolean (single runner per map)
  - `robotSpeedLevel`: int (speed upgrade level, 0-20)
  - `robotStars`: int (evolution level, 0-5)
  - `multiplier`: double (accumulates from manual/runner completions)
  - `bestTimeMs`: long (personal best time for ghost recording)
  - Summit levels per category (Map<SummitCategory, Integer>)
  - Ascension count, skill tree points, unlocked skill nodes
  - Achievement unlocks, active title
  - Lifetime stats: totalCoinsEarned, totalManualRuns
- `AscendPlayerStore` loads players + per-map progress + Summit/Ascension/Achievement data, writes back with debounced saves.

## Commands
- `/ascend`:
  - No args: opens map select UI and teleports to map start on selection.
  - `stats`: opens Stats page showing detailed player statistics (coins/sec, multipliers, lifetime earnings, runs, ascensions, fastest ascension timer).
  - `leaderboard`: opens Leaderboard UI with rankings by coins, ascensions, and manual runs.
  - `elevate`: opens Elevation UI with level-based prestige (exponential costs, diminishing multiplier returns).
  - `summit [category]`: opens Summit UI to invest coins into category bonuses. Categories: `coin`, `speed`, `manual`.
  - `ascension`: opens Ascension UI to perform ultimate prestige (requires 1 trillion coins).
  - `skills`: shows skill tree status and available nodes to unlock.
  - `automation`: opens Automation UI for skill tree automation nodes.
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
  - `/as admin whitelist <add|remove|list|enable|disable|status> [username]` - manage Ascend mode access whitelist
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
- **Ascension quest progress bar**: Modern card-style horizontal bar positioned above player inventory.
  - Left and right accent bars in violet (#7c3aed) matching the Ascension theme.
  - Dark glass container (#1a2530 at 95% opacity) for a clean, modern look.
  - Logarithmic scale progression (log10) so early progress feels meaningful.
  - Progress bar fill with subtle glow layer effect.
  - Right accent bar fills vertically to mirror horizontal progress.
  - Displays "ASCENSION" label and percentage (minimalist design).
  - Updates in real-time with other HUD elements via `updateAscensionQuest()`.
- **Health/stamina HUD hidden**: Default health and stamina bars are hidden in Ascend mode for cleaner UI.
- Ascend UI files are in `Common/UI/Custom/Pages/` (single source of truth).
- Code references `Pages/Ascend_*.ui` (Hytale resolves this to `Common/UI/Custom/Pages/`).

## Run items system
During manual runs, players receive run-specific hotbar items:
- **Reset item**: Restart the current map from the beginning
- **Leave item**: Exit the map and return to spawn
- Items are given when starting a manual run and removed on completion/cancel
- Run timer HUD shows elapsed time during manual runs

## UI pages

**Technical note:** Pages with background tasks (`ElevationPage`, `AscendMapSelectPage`, `StatsPage`) implement `onDismiss()` lifecycle callback for proper cleanup when replaced by external UIs (e.g., NPCDialog). See `CODE_PATTERNS.md` and `HYTALE_API.md` for implementation details.

- `Ascend_MapSelect.ui` + `Ascend_MapSelectEntry.ui`: modern map selection with:
  - 620x620px container with "A S C E N D" header
  - Card layout (580x90px) with 3 zones:
    - Left accent bar (8px): colored per map position
    - Info zone (~436px): map name, 10-segment progress bar, run time status
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
- `Ascend_Leaderboard.ui` + `Ascend_LeaderboardEntry.ui`: leaderboard system with:
  - Three tab categories: Coins, Ascensions, Manual Runs
  - Pagination: 50 entries per page
  - Search field: filter by player name (prefix search)
  - Rank colors: gold (#1), silver (#2), bronze (#3), grey (default)
  - Tab accent colors: orange (coins), violet (ascensions), green (runs)
  - Dynamic sorting by selected category
- `Ascend_Stats.ui` + `Ascend_StatsEntry.ui` + `Ascend_StatsTimerEntry.ui`: comprehensive stats page with:
  - Six stat cards with colored accent bars
  - Combined Income (coins/sec calculation from all runners)
  - Multiplier Breakdown (digits product × elevation)
  - Lifetime Earnings (total coins earned)
  - Manual Runs (total run count)
  - Ascensions (total ascension count)
  - Fastest Ascension (personal best + live timer)
  - Auto-refresh at 1s intervals for live timer
- `Ascend_PassiveEarnings.ui` + `Ascend_PassiveEarningsEntry.ui`: passive earnings summary popup showing:
  - Time away (formatted as days/hours/minutes, capped at 24 hours)
  - Total coins earned while offline
  - Total multiplier gained while offline
  - Per-runner breakdown with stars, speed level, runs completed, coins, and multiplier
  - Displays on rejoin to Ascend world if unclaimed earnings exist
- `Ascend_Whitelist.ui` + `Ascend_WhitelistEntry.ui`: admin whitelist management UI:
  - Add/remove players by username
  - List all whitelisted players (sorted alphabetically)
  - Enable/disable whitelist toggle
  - Real-time status display (enabled/disabled + player count)
  - Accessed via admin panel

## Passive Earnings System
Managed by `PassiveEarningsManager`. Players earn coins and multiplier from their active runners while offline.

**Offline Rate**: 25% of normal production (`PASSIVE_OFFLINE_RATE_PERCENT`)
**Max Offline Time**: 24 hours (`PASSIVE_MAX_TIME_MS`)
**Minimum Away Time**: 1 minute (`MIN_AWAY_TIME_MS`)

### How it works
- Timestamp saved when player leaves Ascend world (`onPlayerLeaveAscend`)
- On rejoin, calculates time away (capped at 24 hours)
- For each active runner:
  - Calculates theoretical runs based on runner completion time and time away
  - Applies 25% offline rate to both coins and multiplier gains
  - Uses same formulas as online runners (speed upgrades, elevation, Summit bonuses)
- Earnings applied atomically to player account
- Passive earnings popup displays breakdown per runner
- Flag `hasUnclaimedPassive` prevents duplicate claims

**Database**: Uses existing `ascend_settings` table to track `lastActiveTimestamp` and `hasUnclaimedPassive` per player.

## Whitelist System
Managed by `AscendWhitelistManager` (hyvexa-core). Controls access to Ascend mode via Hub menu.

**Config File**: `run/mods/Parkour/ascend_whitelist.json` (gitignored)

**Access Logic**:
- **Whitelist disabled** (default): Only OPs can access Ascend mode
- **Whitelist enabled**: Whitelisted players + OPs can access Ascend mode
- Admin commands remain OP-only regardless of whitelist state

**Features**:
- Add/remove players by username (case-insensitive)
- Enable/disable whitelist toggle
- List all whitelisted players (sorted alphabetically)
- Status command shows enabled/disabled state and player count
- Persistent JSON storage with auto-reload

**Admin Commands**: `/as admin whitelist <add|remove|list|enable|disable|status> [username]`

**UI Access**: Admin panel includes Whitelist page for GUI-based management

## Leaderboard System
Managed by `AscendLeaderboardPage`. Players can view global rankings across three categories.

**Categories**:
- **Coins**: Total lifetime coins earned (`totalCoinsEarned`)
- **Ascensions**: Total ascension count (`ascensionCount`)
- **Manual Runs**: Total manual run count (`totalManualRuns`)

**Features**:
- Tab navigation between categories
- Search field: filter by player name (prefix search, case-insensitive)
- Pagination: 50 entries per page
- Rank colors: gold (#1), silver (#2), bronze (#3), grey (default)
- Real-time sorting based on category
- Username resolution via `Universe.get().getPlayer(uuid)`

**UI Files**: `Ascend_Leaderboard.ui` + `Ascend_LeaderboardEntry.ui`

**Command**: `/ascend leaderboard`

## Stats Page
Managed by `StatsPage`. Shows comprehensive player statistics with live updates.

**Stat Cards** (with colored accent bars):
1. **Combined Income** (green): Total coins/sec from all active runners
2. **Multiplier Breakdown** (violet): Digits product × elevation = total multiplier
3. **Lifetime Earnings** (gold): Total coins earned across all time
4. **Manual Runs** (blue): Total manual run count
5. **Ascensions** (cyan): Total ascension count
6. **Fastest Ascension** (orange): Personal best time + live timer

**Auto-Refresh**:
- Background task refreshes fastest ascension timer every 1 second
- Implements `onDismiss()` lifecycle for cleanup when UI replaced
- Task automatically stops when page is no longer active

**UI Files**: `Ascend_Stats.ui` + `Ascend_StatsEntry.ui` + `Ascend_StatsTimerEntry.ui`

**Command**: `/ascend stats`

## Holograms (Hylograms)
- Map info holograms only (in-world text for map name, reward, price, and a prompt).
- Managed via `AscendHologramManager` using `HylogramsBridge` (no direct Hylograms API imports).

## Runner system
Runners are auto-completers that passively earn multiplier for a map.

- One runner per map (not multiple)
- Runner follows the player's ghost recording (personal best path)
- Each completion adds multiplier based on star level (0.01 × 2^stars)
- Speed upgrades compress playback time by +15% per level (max level 20)
- Speed upgrade cost: 100 × 2^level coins
- At max speed level (20), runner can evolve to gain a star (max 5 stars)
- Evolution is free, resets speed to 0, and changes runner appearance
- Runner purchase gated behind manual completion (guarantees ghost exists)

### Ghost replay system
Runners follow the player's exact path from their personal best manual completion:

- Player position and rotation sampled every 50ms during manual runs (20 samples/sec)
- Sampling executes on world thread for proper entity component access
- Ghost recordings saved only when achieving a new personal best time
- Smooth 60fps playback through linear interpolation between samples
- Speed upgrades time-compress playback (e.g., 10x speed = complete in ~10% of recorded time)
- Recordings stored as GZIP-compressed BLOB in `ascend_ghost_recordings` table (~5-10 KB per map)
- Maximum ~12,000 samples allowed (10 minutes max recording) to prevent DoS
- `GhostRecorder` handles recording during manual runs
- `GhostStore` manages persistence and retrieval of ghost data

### Runner visibility
- Runners are hidden from players during their active manual runs
- When a player starts a run, all runners on that map are hidden from that player
- Runners are shown again when the run completes or is cancelled
- New runners spawning during an active run are automatically hidden
- Managed by `EntityVisibilityManager` and `EntityVisibilityFilterSystem` in hyvexa-core

### Implementation
- `RobotManager` manages runner state and schedules a tick loop (16ms interval for ~60fps movement).
- `RobotState` tracks owner/map, speed level, stars, entity UUID, and ghost playback state.
- `computeCompletionIntervalMs()` calculates time based on ghost duration and speed upgrades.
- Runners only spawn for online players and despawn when player disconnects.
- `respawnRobot()` despawns and respawns runner with new entity type after evolution.
- Orphaned runners (from server restart) are automatically cleaned up.
- Runner duplication on chunk unload/reload is prevented via entity tracking.

### Visual NPC system
- Runners spawn as Kweebec NPCs via `NPCPlugin.spawnNPC()`.
- Entity type alternates between `Kweebec_Sapling` (green) and `Kweebec_Sapling_Orange` by star level.
- NPCs are made `Invulnerable` (can't be killed) and `Frozen` (AI disabled).
- Movement is controlled via `Teleport` component at 60fps for smooth animation.
- NPC rotation is calculated from ghost recording data for natural-looking movement.
