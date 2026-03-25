<!-- Last verified against code: 2026-03-25 -->
# RunOrFall Database Schema

Tables owned by `hyvexa-runorfall` for the platforming minigame mode.
All use the shared MySQL database via `DatabaseManager`.

---

## runorfall_settings
Stores global RunOrFall mode settings (single row, id = 1).

```sql
CREATE TABLE IF NOT EXISTS runorfall_settings (
  id TINYINT NOT NULL PRIMARY KEY,
  lobby_x DOUBLE NULL,
  lobby_y DOUBLE NULL,
  lobby_z DOUBLE NULL,
  lobby_rot_x FLOAT NULL,
  lobby_rot_y FLOAT NULL,
  lobby_rot_z FLOAT NULL,
  void_y DOUBLE NOT NULL DEFAULT 40.0,
  block_break_delay_seconds DOUBLE NOT NULL DEFAULT 0.2,
  min_players INT NOT NULL DEFAULT 2,
  min_players_time_seconds INT NOT NULL DEFAULT 300,
  optimal_players INT NOT NULL DEFAULT 4,
  optimal_players_time_seconds INT NOT NULL DEFAULT 60,
  blink_distance_blocks INT NOT NULL DEFAULT 7,
  blink_start_charges INT NOT NULL DEFAULT 1,
  blink_charge_every_blocks_broken INT NOT NULL DEFAULT 100,
  feathers_per_minute_alive INT NOT NULL DEFAULT 1,
  feathers_per_player_eliminated INT NOT NULL DEFAULT 5,
  feathers_for_win INT NOT NULL DEFAULT 25,
  active_map_id VARCHAR(64) NULL
) ENGINE=InnoDB;
```

Manager: `RunOrFallConfigStore` (in `hyvexa-runorfall`)

## runorfall_maps
Stores RunOrFall map definitions.

```sql
CREATE TABLE IF NOT EXISTS runorfall_maps (
  map_id VARCHAR(64) NOT NULL PRIMARY KEY,
  min_players INT NOT NULL DEFAULT 2,
  lobby_x DOUBLE NULL,
  lobby_y DOUBLE NULL,
  lobby_z DOUBLE NULL,
  lobby_rot_x FLOAT NULL,
  lobby_rot_y FLOAT NULL,
  lobby_rot_z FLOAT NULL
) ENGINE=InnoDB;
```

Manager: `RunOrFallConfigStore` (in `hyvexa-runorfall`)

## runorfall_map_spawns
Stores player spawn positions per RunOrFall map.

```sql
CREATE TABLE IF NOT EXISTS runorfall_map_spawns (
  map_id VARCHAR(64) NOT NULL,
  spawn_order INT NOT NULL,
  x DOUBLE NOT NULL,
  y DOUBLE NOT NULL,
  z DOUBLE NOT NULL,
  rot_x FLOAT NOT NULL,
  rot_y FLOAT NOT NULL,
  rot_z FLOAT NOT NULL,
  PRIMARY KEY (map_id, spawn_order)
) ENGINE=InnoDB;
```

Manager: `RunOrFallConfigStore` (in `hyvexa-runorfall`)

## runorfall_map_platforms
Stores platform regions per RunOrFall map.

```sql
CREATE TABLE IF NOT EXISTS runorfall_map_platforms (
  map_id VARCHAR(64) NOT NULL,
  platform_order INT NOT NULL,
  min_x INT NOT NULL,
  min_y INT NOT NULL,
  min_z INT NOT NULL,
  max_x INT NOT NULL,
  max_y INT NOT NULL,
  max_z INT NOT NULL,
  target_block_item_id VARCHAR(128) NULL,
  PRIMARY KEY (map_id, platform_order)
) ENGINE=InnoDB;
```

Manager: `RunOrFallConfigStore` (in `hyvexa-runorfall`)

## runorfall_player_stats
Stores per-player RunOrFall statistics.

```sql
CREATE TABLE IF NOT EXISTS runorfall_player_stats (
  player_uuid CHAR(36) NOT NULL PRIMARY KEY,
  player_name VARCHAR(32) NOT NULL,
  wins INT NOT NULL DEFAULT 0,
  losses INT NOT NULL DEFAULT 0,
  current_win_streak INT NOT NULL DEFAULT 0,
  best_win_streak INT NOT NULL DEFAULT 0,
  longest_survived_ms BIGINT NOT NULL DEFAULT 0,
  total_blocks_broken BIGINT NOT NULL DEFAULT 0,
  total_blinks_used BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
```

Manager: `RunOrFallStatsStore` (in `hyvexa-runorfall`)

## runorfall_migrations
Tracks applied migration keys for RunOrFall mode.

```sql
CREATE TABLE IF NOT EXISTS runorfall_migrations (
  migration_key VARCHAR(64) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
```

Manager: `RunOrFallDatabaseSetup` (in `hyvexa-runorfall`)
