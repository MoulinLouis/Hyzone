# Ascend MVP Implementation Plan (UI-First)

Define an MVP Ascend loop that is fully configurable in-game (no hard-coded maps), with admin-first UI plus command fallback, basic HUD (coins + pending + multiplier placeholder), and manual run play. This focuses on the admin workflow + player-facing flow so the mode is visible and testable on a live server.

## Scope
- In: Admin UI for map creation/editing with command fallback, persistent map data in MySQL, map selection UI, manual run tracking, Ascend HUD basics using existing UI properties, coin collection flow, single Ascend world.
- Out: Robot spawning/AI, economy balancing, VFX/sounds polish, cross-mode currency links, per-map worlds.

## Implementation ideas (feasibility notes)
- **Map config is 100% runtime**: All map fields (start/finish/waypoints, reward, price, order) persist in `ascend_maps`. No map definitions in code.
- **UI-first admin workflow**: Use `Ascend_MapAdmin.ui` + `AscendAdminPage` to create/update, with `/as admin map ...` as fallback.
- **Unlocks without new UI controls**: If the map list entry is clicked and locked, attempt to spend coins and unlock on click; otherwise show an error. This works with the existing entry button.
- **Pending coins pipeline**: Manual completion adds **pending** coins per map; `/ascend collect` moves pending → wallet. HUD shows both values.
- **HUD-safe updates**: Only set labels already defined in `.ui` and reuse known styles (`Label`, `Group`, `TextField`, `TextButton`). Avoid new UI property types.
- **World-thread safety**: Teleports and run completion are executed on the world thread (`CompletableFuture.runAsync(..., world)` already in place via tick loop).
- **Single Ascend world**: All maps are in the same world; map `world` value is used for spawn/teleport and stored for future flexibility.

## Action items
[ ] Audit current Ascend module vs MVP (commands, HUD, UI assets, tracker, stores) under `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/` and `hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/`.
[ ] Implement UI-first admin workflow in `Ascend_MapAdmin*.ui` + handlers: create map, set start/finish, add/clear waypoints, set reward/price/order; add command fallback for each action (`/as admin map ...`).
[ ] Ensure `AscendMapStore` persists all map config from admin actions (no code-defined maps): world name, start/finish/waypoints, display order, prices/rewards.
[ ] Wire player-facing map selection using `Ascend_MapSelect*.ui`: show locked/unlocked status, teleport to start on select, and gate to Ascend world.
[ ] Finish manual run tracking in `AscendRunTracker`: completion radius checks, mark manual completion, add pending coins, teleport back to start; handle invalid refs and missing config gracefully.
[ ] Implement Ascend HUD basics (coins + pending + multiplier placeholder) using **only existing, known UI properties**; avoid introducing new UI property types.
[ ] Add a collection flow (`/ascend collect` + optional collection UI) and persist pending coin changes in the player store.
[ ] Update `CHANGELOG.md` with a short note for the completed MVP features and validate in-game on a test server (map creation, run completion, HUD updates, collect).

## Open questions
- None; defaults are UI-first admin setup, single Ascend world, HUD shows coins + pending + multiplier placeholder using existing UI properties only.
