# Parkour Module

Quick reference for `hyvexa-parkour`.

## Scope
- Main parkour gameplay loop (start trigger -> checkpoints -> finish).
- Player progression (XP, rank, best times, checkpoint splits).
- Parkour UI (player + admin pages).
- Parkour duel mode, ghost playback/recording, cosmetics.

Entry point: `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java`

## Startup And Runtime
`HyvexaPlugin.setup()` does the core wiring:
- Initializes shared services (`DatabaseManager`, `VexaStore`, `DiscordLinkStore`, `CosmeticStore`, `AnalyticsStore`).
- Loads parkour stores (`MapStore`, `SettingsStore`, `ProgressStore`, `PlayerCountStore`, `GlobalMessageStore`).
- Creates gameplay trackers (`RunTracker`, duel tracker, ghost systems).
- Creates managers (HUD, perks, playtime, announcements, collision, cleanup, holograms).
- Registers commands, interaction codecs, and ECS systems.
- Starts scheduled ticks (HUD, playtime, duel, cleanup, player count sampling).

Important tick timings are in `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ParkourTimingConstants.java`.

## Run Lifecycle
Main tick entry is `RunTrackerTickSystem` -> `RunTracker.checkPlayer(...)`.

1. No active run:
- Detect start trigger from `MapStore`.
- Start run and teleport to map start.

2. Active run:
- Track jumps, fall state, checkpoints, finish checks.
- Handle run/practice respawns.
- Enforce practice fly-zone restrictions if configured.

3. Finish:
- `RunValidator.checkFinish(...)` verifies all checkpoints were touched.
- Calls `ProgressStore.recordMapCompletion(...)`.
- Updates leaderboard state/holograms.
- Handles ghost recording and run-end teleport/inventory reset.

Core files:
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunTracker.java`
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunValidator.java`
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunTeleporter.java`

## Data Ownership
All state is MySQL-backed and loaded in-memory on startup.

- `MapStore`: map definitions, triggers, checkpoints.
- `ProgressStore`: players, completions, best times, checkpoint splits, XP/rank.
- `SettingsStore`: respawn timing, spawn, debug flags, category order.
- `GlobalMessageStore`: rotating global announcement messages.
- `PlayerCountStore`: sampled online-count history.
- Duel stores: duel stats/preferences/match history.

`ProgressStore` uses debounced saves (`5s`) for player state writes.

## Where To Edit What
- Run logic/checkpoint behavior:
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/`
- HUD behavior:
  `hyvexa-parkour/src/main/java/io/hyvexa/manager/HudManager.java`
- Inventory item layout:
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/util/InventoryUtils.java`
- Commands:
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/command/`
  `hyvexa-parkour/src/main/java/io/hyvexa/duel/command/`
- UI logic:
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/`
- UI files:
  `hyvexa-parkour/src/main/resources/Common/UI/Custom/Pages/`
- Item interactions:
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/interaction/`
  `hyvexa-parkour/src/main/resources/Server/Item/Interactions/Item/`
  `hyvexa-parkour/src/main/resources/Server/Item/RootInteractions/`

## Commands Snapshot
Player-facing:
- `/pk` (map/category UI), `/pk leaderboard`, `/pk stats`
- `/duel`, `/cp`, `/shop`, `/discord`, `/store`, `/rules`, `/vote`, `/link`

Staff/admin-heavy:
- `/pk admin ...`, `/vexa`, `/analytics`, `/dbtest`, `/dbreload`, `/dbclear`
- `/unlink`, `/spec`, `/pkmusic`, `/cosmetic`, `/messagetest`

## Common Change Patterns
### Add/Change a map behavior
1. Update map data via map admin UI / `MapStore` model fields.
2. If run logic changes, update `RunTracker`/`RunValidator`.
3. If XP/rank behavior changes, update `ProgressStore`.

### Add a new parkour item interaction
1. Add interaction class in `parkour/interaction`.
2. Register codec in `HyvexaPlugin.registerInteractionCodecs()`.
3. Add JSON under `Server/Item/Interactions/Item/` and root interaction mapping.
4. If item is in hotbar flow, update `ParkourConstants` + `InventoryUtils`.

## Notes
- Mode-gating is strict: parkour commands/features should only run in Parkour world.
- Non-OP safety systems block drop/break and combat behaviors.
- Shutdown flushes pending progress writes and stops scheduled systems.

## Related Docs
- Architecture overview: `docs/ARCHITECTURE.md`
- Database schema details: `docs/DATABASE.md`
- UI rules/pitfalls: `CLAUDE.md` + `docs/hytale-custom-ui/`
