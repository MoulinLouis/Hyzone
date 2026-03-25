# Hub Module

Quick reference for `hyvexa-hub`.

## Scope
- Hub world: the landing zone where players connect and choose a game mode.
- Mode selection UI (menu page), world routing/teleportation, HUD with player count and vexa display.
- Access control for restricted modes (Ascend whitelist, Purge staff-only).

Entry point: `hyvexa-hub/src/main/java/io/hyvexa/hub/HyvexaHubPlugin.java`

## Startup and Runtime

`HyvexaHubPlugin.setup()`:
1. Initializes `DatabaseManager`, `VexaStore`, `DiscordLinkStore`, `AnalyticsStore` (if not already initialized by another module).
2. Creates `HubRouter` for world teleportation.
3. Preloads all game worlds (`Hub`, `Parkour`, `Ascend`, `Purge`, `RunOrFall`) via `Universe.loadWorld()`.
4. Registers the `Hub_Menu_Interaction` interaction codec and commands (`/menu`, `/rofqueue`).
5. Registers event listeners:
   - `PlayerReadyEvent` -- clears inventory, gives hub items (server selector), attaches Hub HUD, checks Discord link reward.
   - `AddPlayerToWorldEvent` -- manages HUD lifecycle when entering/leaving Hub world.
   - `PlayerDisconnectEvent` -- evicts HUD, VexaStore, and DiscordLinkStore caches.
6. Starts scheduled tasks:
   - Hub HUD recovery tick (1s interval) -- retries HUD attachment for players in PENDING state.
   - Player count tick (5s interval) -- updates HUD with current player count and vexa balance.

## Hub Menu

The mode selection menu (`Hub_Menu.ui`) is opened via:
- The server selector item in hotbar slot 0 (triggers `HubMenuInteraction`).
- The `/menu` command.

Menu buttons:
- **Parkour** -- routes to Parkour world (always available).
- **Parkour Ascend** -- routes to Ascend world. Access controlled by whitelist file (`mods/Parkour/ascend_whitelist.json`): public mode = open to all, whitelist mode = named players + OPs only.
- **Purge** -- routes to Purge world (staff-only, OP required).
- **RunOrFall** -- routes to RunOrFall world (staff-only, hidden from non-OP players).
- **Discord** -- sends Discord invite link in chat.
- **Store** -- sends store link in chat.
- **Hub** -- teleports back to Hub spawn.
- **Close** -- closes the menu.

## World Routing

`HubRouter.routeToWorld(playerRef, targetWorldName)`:
1. Resolves target world (loads if needed).
2. Skips if already in the target world.
3. Logs a `mode_switch` analytics event.
4. Adds a `Teleport` component to the player entity.

## HUD

`HubHud` (`Pages/Hub_RunHud.ui`):
- Displays current online player count (`#PlayerCountText`).
- Displays player's vexa balance (`#PlayerVexaValue`).
- Updated every 5 seconds by the player count tick.
- Uses `MultiHudBridge` for compatibility with MultipleHUD plugin.

## Commands

| Command | Description | Access |
|---------|-------------|--------|
| `/menu` | Opens the hub mode selection menu | All players |
| `/rofqueue` | Joins the RunOrFall cross-world queue | All players |

## Key Files
- Plugin entry: `hyvexa-hub/src/main/java/io/hyvexa/hub/HyvexaHubPlugin.java`
- Routing: `hyvexa-hub/src/main/java/io/hyvexa/hub/routing/HubRouter.java`
- Menu page: `hyvexa-hub/src/main/java/io/hyvexa/hub/ui/HubMenuPage.java`
- HUD: `hyvexa-hub/src/main/java/io/hyvexa/hub/hud/HubHud.java`
- Menu interaction: `hyvexa-hub/src/main/java/io/hyvexa/hub/interaction/HubMenuInteraction.java`
- Command: `hyvexa-hub/src/main/java/io/hyvexa/hub/command/HubCommand.java`

## Owned Database Tables

None. The Hub module uses shared stores from `hyvexa-core` (VexaStore, DiscordLinkStore, AnalyticsStore) but does not own any tables of its own.

## Related Docs
- Architecture overview: `docs/ARCHITECTURE.md`
- Core module stores: `docs/Core/README.md`
- Database schema: `docs/DATABASE.md` (Hub has no tables)
