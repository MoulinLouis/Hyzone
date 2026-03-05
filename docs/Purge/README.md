# Purge Module

Quick reference for `hyvexa-purge`.

## Scope

Purge is a zombie wave survival PvE mode. Players fight escalating waves of zombies using ranged and melee weapons, earn scrap currency, level up weapons, unlock upgrades, and compete for high wave records. Supports solo play and co-op parties.

Entry point: `hyvexa-purge/src/main/java/io/hyvexa/purge/HyvexaPurgePlugin.java`

## Key Managers

| Manager | Purpose |
|---------|---------|
| `PurgeSessionManager` | Session lifecycle: start, stop, cleanup, scrap rewards, stat persistence |
| `PurgeWaveManager` | Core gameplay loop: spawn zombies, combat, intermission, death detection |
| `PurgeInstanceManager` | Map instance CRUD (JSON config at `mods/Purge/purge_instances.json`) |
| `PurgeWaveConfigManager` | DB-backed wave definitions (variant counts, spawn pacing per wave) |
| `PurgeVariantConfigManager` | DB-backed zombie variant config (speed, HP, model) |
| `PurgeWeaponConfigManager` | DB-backed weapon config (item IDs, damage, levels, defaults) |
| `PurgeUpgradeManager` | Per-run upgrade effects (apply/revert stacks: swift feet, iron skin, ammo cache, etc.) |
| `PurgePartyManager` | Party formation, invites, leader management |
| `PurgeClassManager` | Player class system with upgrade interactions |
| `WeaponXpManager` | Weapon XP tracking and level-up bonuses |
| `PurgeMissionManager` | Daily mission rotation and progress tracking |
| `PurgeHudManager` | HUD routing: attach/detach, wave/scrap/combo bar updates |

## Key Stores

| Store | Purpose |
|-------|---------|
| `PurgePlayerStore` | Player stats: best wave, total kills, total sessions |
| `PurgeScrapStore` | Scrap currency (lazy-load, immediate writes, dual cache) |
| `WeaponXpStore` | Per-player per-weapon XP |
| `PurgeWeaponUpgradeStore` | Weapon upgrade levels per player |
| `PurgeClassStore` | Player class unlocks and selected class |
| `PurgeMissionStore` | Daily mission progress |
| `PurgeSkinStore` | Weapon skin selections (in `hyvexa-core`) |

## Runtime Flow

1. `HyvexaPurgePlugin.setup()` initializes all stores via `StoreInitializer` (DB, Vexa, Discord, Analytics, Scrap, Player, WeaponUpgrade, Skin, WeaponXp, Class, Mission).
2. Managers are created and cross-wired: instances -> variants -> waves -> weapons -> HUD -> sessions -> parties -> upgrades -> classes -> missions.
3. `PurgeDamageModifierSystem` is registered as an ECS system for damage scaling.
4. Commands and interaction codecs are registered.
5. Event handlers: `PlayerReadyEvent` (attach HUD, initialize weapon defaults, evict stale skin cache), `AddPlayerToWorldEvent` (ensure idle state or cleanup on world leave), `PlayerDisconnectEvent` (cleanup session, party, HUD, evict all stores).
6. Two scheduled tickers: slow HUD updates (5s, player count/vexa/scrap) and fast combo bar decay (50ms).

## Session Lifecycle

1. Player starts via `/purge start` or blue orb interaction.
2. Session enters `COUNTDOWN` -> `SPAWNING` -> `COMBAT` cycle.
3. Between waves: `UPGRADE_PICK` (player picks a run upgrade) -> `INTERMISSION`.
4. On death or quit: `ENDED`, stats saved, scrap rewarded, loadout reverted to idle.

## Commands

Player-facing:
- `/purge start` -- start a solo session (or party session if leader)
- `/purge stop` -- end current session
- `/purge stats` -- view personal stats
- `/purge party <create|invite|kick|leave|list>` -- party management
- `/purge upgrade` -- view weapon upgrades
- `/purge loadout` -- weapon select page
- `/purge shop` -- cosmetics shop
- `/purge skins` -- weapon skin selection
- `/purge class` -- class selection
- `/purge scrap` -- check scrap balance

Admin/staff:
- `/purge admin` -- opens admin index page (wave config, instances, weapons, variants, skins, settings)
- `/setammo <count>` -- debug: set ammo count
- `/camtest` -- camera test command

## Owned Tables

- `purge_player_stats` -- player stats (best wave, total kills, sessions)
- `purge_player_scrap` -- scrap currency and lifetime scrap
- `purge_weapon_xp` -- per-player per-weapon XP
- `purge_weapon_upgrades` -- weapon upgrade levels
- `purge_player_classes` -- class unlocks per player
- `purge_player_selected_class` -- currently selected class
- `purge_daily_missions` -- daily mission progress
- `purge_waves` -- wave definitions
- `purge_wave_variant_counts` -- per-wave zombie variant counts
- `purge_zombie_variants` -- zombie variant config
- `purge_weapon_levels` -- weapon level config
- `purge_weapon_defaults` -- default weapon config
- `purge_weapon_skins` -- weapon skin selections (in `hyvexa-core`)
- `purge_settings` -- session settings (start/exit teleport)
- `purge_migrations` -- migration tracking

Instance config (JSON, not DB):
- `mods/Purge/purge_instances.json` -- map instance definitions (spawn points, locations)

## Key Files

- Plugin entry: `hyvexa-purge/src/main/java/io/hyvexa/purge/HyvexaPurgePlugin.java`
- Session manager: `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeSessionManager.java`
- Wave manager: `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeWaveManager.java`
- HUD: `hyvexa-purge/src/main/java/io/hyvexa/purge/hud/PurgeHud.java`
- Commands: `hyvexa-purge/src/main/java/io/hyvexa/purge/command/PurgeCommand.java`
- Session model: `hyvexa-purge/src/main/java/io/hyvexa/purge/data/PurgeSession.java`
- Upgrade types: `hyvexa-purge/src/main/java/io/hyvexa/purge/data/PurgeUpgradeType.java`
- UI pages: `hyvexa-purge/src/main/java/io/hyvexa/purge/ui/`

## Related Docs

- Game design: `docs/Purge/GAME_DESIGN.md`
- Agent working reference: `docs/Purge/PURGE_MODE_DEV.md`
- Player scaling plan: `docs/Purge/PLAYER_SCALING_PLAN.md`
- Architecture overview: `docs/ARCHITECTURE.md`
- Database schema: `docs/DATABASE.md`
