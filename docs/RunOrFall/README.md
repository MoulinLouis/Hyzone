# RunOrFall Module

Quick reference for `hyvexa-runorfall`.

## Scope
- Last-player-standing minigame for the RunOrFall world.
- Players stand on configured platforms; stepped blocks break after a delay.
- Supports join/leave flow, blink ability, HUD, stats page, and leaderboard page.

Entry point: `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/HyvexaRunOrFallPlugin.java`

## Runtime Flow
1. Startup initializes DB-backed config/stats stores and registers `/rof` plus item interactions.
2. Players join lobby (`/rof join` or Life Essence item) and are tracked by `RunOrFallGameManager`.
3. Countdown starts automatically when minimum players is reached, or force-starts from admin.
4. Round starts by teleporting lobby players to map spawns and enabling a short grace period.
5. During round tick, players below `voidY` are eliminated, footprint blocks are queued/broken, and broken-block HUD counters are updated.
6. Round ends when one player remains (or solo test round has no alive player). Blocks are restored, stats are saved, and lobby state resets.

## Player UX
- Default: join, leaderboard, stats, server selector.
- Lobby: leave, leaderboard, stats.
- In-game: blink item only.
- Blink uses a collision-safe forward teleport with configurable distance (`blinkDistanceBlocks`).
- HUD (`RunOrFall_RunHud.ui`) shows player count, vexa, countdown text, and broken-block counter.
- Stats page shows wins, losses, winrate, best streak, and longest survival time.
- Leaderboard supports categories: total wins, best streak, longest survived.

## Admin Controls
- Main admin UI: `/rof admin` (`RunOrFall_Admin.ui`).
Core command groups:
- `/rof map <list|create|select|delete>`
- `/rof lobby <set|tp>`
- `/rof spawn <add|list|clear>`
- `/rof platform <pos1|pos2|add|list|remove|clear>`
- `/rof voidy <y>`
- `/rof breakdelay <seconds>`
- `/rof start`, `/rof stop`, `/rof status`

Quick setup for a new map:
1. Create/select map.
2. Set lobby.
3. Add at least one spawn.
4. Select platform corners (`pos1`, `pos2`) and save platform (with target block item ID in admin UI).
5. Verify `voidY`, break delay, and auto-start settings.

## Persistence
- `runorfall_settings`
- `runorfall_maps`
- `runorfall_map_spawns`
- `runorfall_map_platforms`
- `runorfall_player_stats`

Default gameplay values:
- `voidY = 40`
- `blockBreakDelaySeconds = 0.2`
- `minPlayers = 2`
- `minPlayersTimeSeconds = 300`
- `optimalPlayers = 4`
- `optimalPlayersTimeSeconds = 60`
- `blinkDistanceBlocks = 7`

Legacy migration:
- If `mods/RunOrFall/config.json` exists and SQL config is still default/empty, config is migrated to SQL and the JSON is renamed to `config.json.migrated`.

## Key Files
- Runtime entry: `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/HyvexaRunOrFallPlugin.java`
- Round logic: `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/manager/RunOrFallGameManager.java`
- Config storage: `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/manager/RunOrFallConfigStore.java`
- Stats storage: `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/manager/RunOrFallStatsStore.java`
- Command: `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/command/RunOrFallCommand.java`
- Admin page: `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/ui/RunOrFallAdminPage.java`
