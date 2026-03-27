# Ascend Module

Quick reference for `hyvexa-parkour-ascend`.

## Scope

Ascend is an idle-progression parkour mode. Players run parkour maps to earn volt (the Ascend currency), level up runners, unlock new maps, and progress through prestige layers (elevation, summit, ascension, transcendence). The module also includes a Mine subsystem where players break blocks to earn crystals.

Entry point: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java`

## Key Managers

| Manager | Purpose |
|---------|---------|
| `AscendRunTracker` | Per-player run detection, checkpoint tracking, finish validation |
| `AscendHudManager` | Attaches/updates the Ascend HUD (volt, multiplier, timer, runner bars) |
| `RobotManager` | Spawns and manages ghost-replay runner NPCs per player |
| `SummitManager` | Summit prestige logic (volt -> summit tokens) |
| `AscensionManager` | Ascension prestige logic (resets summit, grants ascension level) |
| `TranscendenceManager` | Transcendence prestige (4th layer, resets ascension) |
| `ChallengeManager` | Timed parkour challenges with leaderboard |
| `AchievementManager` | Tracks and grants progression achievements |
| `RunnerSpeedCalculator` | Shared runner speed formula used by robot simulation and UI projections |
| `TutorialTriggerService` | Shows progressive tutorials based on player milestones |
| `AscendHologramManager` | In-world leaderboard holograms per map |
| `MineManager` | Zone generation, block respawn, mine area lifecycle |
| `MineConfigStore` | DB-backed mine definitions, zones, gates, block prices |
| `MinePlayerStore` | Per-player mine progress (crystals, inventory, upgrades, miners) |
| `MineRobotManager` | Automated miner NPCs that mine while the player is online |
| `MineHudManager` | HUD for the mine area (crystals, inventory, toasts) |
| `MineBonusCalculator` | Calculates passive bonuses from mining milestones that apply to parkour systems (runner speed, multiplier gain, volt gain) |
| `MineGateChecker` | Per-tick gate enforcement preventing unauthorized mine entry |

## Key Stores

| Store | Purpose |
|-------|---------|
| `AscendMapStore` | Map definitions, triggers, checkpoints, finish areas |
| `AscendPlayerStore` | Coordinator: player cache lifecycle, persistence, cross-domain resets, leaderboards. Delegates to domain facades |
| `AscendVoltFacade` | Volt (currency) operations — via `playerStore.volt()` |
| `AscendProgressionFacade` | Elevation, summit, accumulated volt, ascension/transcendence, multiplier calculations — via `playerStore.progression()` |
| `AscendRunnerFacade` | Map progress, multipliers, unlocks, robots — via `playerStore.runners()` |
| `AscendGameplayFacade` | Skill tree, achievements, run tracking, tutorials — via `playerStore.gameplay()` |
| `AscendSettingsFacade` | Automation toggles, visibility, session state — via `playerStore.settings()` |
| `AscendPlayerEventHandler` | Gameplay side-effects: ascension flow, tutorial triggers, transcendence notifications |
| `AscendSettingsStore` | Global settings (spawn, admin config) |
| `GhostStore` | Ghost recording storage (table: `ascend_ghost_recordings`) |

## Runtime Flow

1. `ParkourAscendPlugin.setup()` initializes database tables (`AscendDatabaseSetup.ensureTables()`), shared stores (DB, Vexa, Discord), whitelist, and runtime config.
2. Core stores load synchronously: `AscendMapStore`, `AscendPlayerStore`, `AscendSettingsStore`.
3. Mine subsystem initializes: `MineConfigStore` -> `MineBonusCalculator` + `MineGateChecker` -> `MinePlayerStore` + `MineManager` -> `MineHudManager` -> `MineRobotManager`.
4. Ghost system, run tracker, summit/ascension/transcendence/challenge/achievement managers, and tutorials are wired.
5. Holograms initialize if `HylogramsBridge` is available.
6. Commands and interaction codecs are registered.
7. Event handlers: `PlayerReadyEvent` (cache player, spawn robots/miners, attach HUD), `AddPlayerToWorldEvent` (track ascend world membership), `PlayerDisconnectEvent` (cleanup all state).
8. A 50ms scheduled tick iterates online players per-world: gate checks, HUD updates, run tracking. Mine HUD ticks separately at 1s intervals.

Composition root note:
- `ParkourAscendPlugin.setup()` owns dependency wiring for pages, managers, and helper services.
- Commands are registered with injected dependencies from `ParkourAscendPlugin.setup()`.
- Interactions still bootstrap through `AbstractAscendPageInteraction` because Hytale codec instantiation requires no-arg handlers today.
- `AscendInteractionBridge` is the narrow static bootstrap for those codec-instantiated handlers; interactions should depend on that bridge rather than on `ParkourAscendPlugin`.
- Page/business logic and helper services should prefer constructor/setter injection instead of calling back into `ParkourAscendPlugin`.
- `AscendMenuNavigator` is the focused page-construction helper for profile/settings/music/stats/achievement flows; page classes should use it instead of reaching back into the plugin singleton to open sibling pages.
- `AscendAdminNavigator` plays the same role for admin/whitelist/mine admin pages, including back-navigation between admin sub-pages.
- As of `2026-03-22`, Ascend runtime uses `0` `ParkourAscendPlugin.getInstance()` calls in Java code; the only remaining static bootstrap is the narrower `AscendInteractionBridge` used by codec-instantiated interactions.

## Commands

Player-facing:
- `/ascend` -- opens map select page (default)
- `/ascend stats` / `leaderboard` / `maplb` / `profile` / `settings` / `help` / `achievements` / `automation` / `ascension` / `summit` / `skills` / `transcend` / `challenge` / `challengelb` / `tutorial`
- `/mine` -- opens mine bag page
- `/mine sell` / `upgrades` / `achievements`
- `/elevate` / `/summit` / `/skill` / `/transcend` -- NPC-gated prestige commands
- `/cat` -- cat companion command

Admin/staff:
- `/ascendadmin` -- admin panel (map CRUD, mine admin, spawn/settings, whitelist)
- `/mine addcrystals <amount>` -- debug: add crystals
- `/hudpreview` / `/cinematictest` -- test commands (gated by runtime config)

## Owned Tables

Ascend core:
- `ascend_players` -- player progress (volt, elevation multiplier)
- `ascend_maps` -- map definitions
- `ascend_player_maps` -- per-player per-map progress (runner level, best time, completions)
- `ascend_upgrade_costs` -- configurable upgrade cost overrides
- `ascend_player_summit` -- summit prestige state
- `ascend_player_skills` -- skill tree unlocks
- `ascend_player_achievements` -- achievement progress
- `ascend_player_cats` -- cat companion state
- `ascend_settings` -- global settings
- `ascend_ghost_recordings` -- ghost replay data
- `ascend_challenges` -- active challenge state
- `ascend_challenge_records` -- challenge leaderboard records

Mine subsystem:
- `mine_definitions` -- mine type definitions
- `mine_zones` -- physical zone coordinates per mine
- `mine_gate` -- gate position and access rules
- `mine_players` -- per-player mine progress (crystals, selected mine)
- `mine_player_inventory` -- mined block inventory
- `block_prices` -- global sell prices per block type
- `mine_player_mines` -- per-player mine unlock state
- `mine_player_miners` -- automated miner state
- `mine_zone_layers` -- depth layers per zone (Y-range + block distribution)

### Mine Layers

Zones can have depth layers defined by Y-ranges. Each layer has its own block probability table. When generating blocks or rewarding miners, the system picks the block table from the matching layer based on the block's Y coordinate. Zones without layers use the existing zone-level block table (backward compatible). Layers are non-overlapping and kept in ascending Y order. Admins configure layers via the zone admin page.

## Key Files

- Plugin entry: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java`
- Run tracker: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/tracker/AscendRunTracker.java`
- DB setup: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendDatabaseSetup.java`
- Player store: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java`
- Mine manager: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/MineManager.java`
- Mine upgrades: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/data/MineUpgradeType.java`
- Commands: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/`
- UI pages: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/`
- Mine UI: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/ui/`

## Related Docs

- Economy and balancing: `docs/Ascend/ECONOMY_BALANCE.md`
- Tutorial flow: `docs/Ascend/TUTORIAL_FLOW.md`
- Economy simulation: `docs/Ascend/economy_sim.py`
- Architecture overview: `docs/ARCHITECTURE.md`
- Database schema: `docs/Ascend/DATABASE.md`
