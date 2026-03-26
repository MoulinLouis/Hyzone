<!-- Last verified against code: 2026-03-25 -->
# Hyvexa Suite -- Database Schema Index

Suite-wide schema index for the Hyvexa plugin family.
All modules share a single MySQL database via `DatabaseManager` (in `hyvexa-core`), with one exception: `hyvexa-votifier` uses its own local SQLite storage (documented in [Core/DATABASE.md](Core/DATABASE.md#votifier-tables-sqlite----separate-from-shared-mysql)).

## Runtime Notes

- Runtime config lives in `mods/Parkour/` relative to the server working directory (see [DEVELOPMENT_ENVIRONMENT.md](DEVELOPMENT_ENVIRONMENT.md))
- `mods/Parkour/database.json` holds MySQL credentials (gitignored)
- MySQL is the source of truth for persisted state; some runtime values are intentionally computed from constants (for example Ascend map balance values from `display_order`)
- `DatabaseManager` lives in `hyvexa-core` and is shared across modules

## Schema Files

| File | Module | Tables |
|------|--------|--------|
| [Core/DATABASE.md](Core/DATABASE.md) | `hyvexa-core` | `player_vexa`, `player_feathers`, `player_cosmetics`, `cosmetic_shop_config`, `analytics_events`, `analytics_daily`, `discord_link_codes`, `discord_links`, `player_votes`, `player_vote_counts`, Votifier SQLite `player_votes` |
| [Parkour/DATABASE.md](Parkour/DATABASE.md) | `hyvexa-parkour` | `players`, `maps`, `map_checkpoints`, `player_completions`, `player_checkpoint_times`, `settings`, `global_messages`, `global_message_settings`, `player_count_samples`, `medal_rewards`, `player_medals`, `saved_run_state`, `player_settings`, `parkour_migrations`, `duel_category_prefs`, `duel_matches`, `duel_player_stats`, `parkour_ghost_recordings` |
| [Ascend/DATABASE.md](Ascend/DATABASE.md) | `hyvexa-parkour-ascend` | `ascend_players`, `ascend_maps`, `ascend_player_maps`, `ascend_upgrade_costs`, `ascend_player_summit`, `ascend_player_skills`, `ascend_player_achievements`, `ascend_settings`, `ascend_challenges`, `ascend_challenge_records`, `ascend_ghost_recordings`, `ascend_player_cats`, `mine_definitions`, `mine_zones`, `mine_zone_layers`, `mine_gate`, `mine_players`, `mine_player_inventory`, `block_prices`, `mine_player_mines`, `mine_player_miners`, `block_hp`, `mine_achievements`, `mine_player_stats`, `mine_miner_slots`, `mine_conveyor_waypoints`, `mine_player_conveyor_buffer`, `mine_player_eggs`, `mine_player_miners_v2`, `mine_player_slot_assignments`, `mine_layer_rarity_blocks`, `pickaxe_tier_recipes`, `pickaxe_enhance_costs`, `mine_layer_miner_defs` |
| [Purge/DATABASE.md](Purge/DATABASE.md) | `hyvexa-purge` | `purge_player_stats`, `purge_player_scrap`, `purge_weapon_upgrades`, `purge_weapon_xp`, `purge_daily_missions`, `purge_player_classes`, `purge_player_selected_class`, `purge_weapon_levels`, `purge_weapon_defaults`, `purge_weapon_skins`, `purge_zombie_variants`, `purge_wave_variant_counts`, `purge_waves`, `purge_settings`, `purge_migrations` |
| [RunOrFall/DATABASE.md](RunOrFall/DATABASE.md) | `hyvexa-runorfall` | `runorfall_settings`, `runorfall_maps`, `runorfall_map_spawns`, `runorfall_map_platforms`, `runorfall_player_stats`, `runorfall_migrations` |

## Modules Without Tables

- **Hub** (`hyvexa-hub`) — routing is runtime-only, no database tables
- **Wardrobe** (`hyvexa-wardrobe`) — uses shared `player_cosmetics` and `cosmetic_shop_config` from `hyvexa-core`

## General Notes

- Foreign keys are not required by the current code on all tables, but some (Ascend, Mine) use them explicitly.
- Most tables are auto-created on startup by their respective Store or DatabaseSetup class.
- Some parkour tables (`players`, `maps`, `map_checkpoints`, `player_completions`, `settings`, `global_messages`, `global_message_settings`, `player_count_samples`) are not created programmatically by the plugin -- they are created manually or by an external migration script.
