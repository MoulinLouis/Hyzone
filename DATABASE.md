# Database Schema

This project stores parkour data in MySQL. The tables below reflect the current code paths
(read/write queries) in the parkour plugin. Types are recommended defaults that match usage.

Runtime notes:
- Server working directory is `run/`, so runtime config lives in `mods/Parkour/`
- `mods/Parkour/database.json` holds MySQL credentials (gitignored)
- MySQL is the source of truth; in-memory caches load from MySQL on startup
- `DatabaseManager` lives in `hyvexa-core` and is shared across modules

## players
Stores player progression and profile state.

Suggested schema:
```sql
CREATE TABLE players (
  uuid CHAR(36) NOT NULL PRIMARY KEY,
  name VARCHAR(32) NULL,
  xp BIGINT NOT NULL,
  level INT NOT NULL,
  welcome_shown BOOLEAN NOT NULL,
  playtime_ms BIGINT NOT NULL
) ENGINE=InnoDB;
```

## maps
Stores map definitions and primary transforms.

Suggested schema:
```sql
CREATE TABLE maps (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  name VARCHAR(128) NULL,
  category VARCHAR(64) NULL,
  world VARCHAR(64) NULL,
  difficulty INT NOT NULL,
  display_order INT NOT NULL,
  first_completion_xp BIGINT NOT NULL,
  mithril_sword_enabled BOOLEAN NOT NULL,
  mithril_daggers_enabled BOOLEAN NOT NULL,
  free_fall_enabled BOOLEAN NOT NULL,
  dropper_enabled BOOLEAN NOT NULL,
  duel_enabled BOOLEAN NOT NULL,
  start_x DOUBLE NULL,
  start_y DOUBLE NULL,
  start_z DOUBLE NULL,
  start_rot_x FLOAT NULL,
  start_rot_y FLOAT NULL,
  start_rot_z FLOAT NULL,
  finish_x DOUBLE NULL,
  finish_y DOUBLE NULL,
  finish_z DOUBLE NULL,
  finish_rot_x FLOAT NULL,
  finish_rot_y FLOAT NULL,
  finish_rot_z FLOAT NULL,
  start_trigger_x DOUBLE NULL,
  start_trigger_y DOUBLE NULL,
  start_trigger_z DOUBLE NULL,
  start_trigger_rot_x FLOAT NULL,
  start_trigger_rot_y FLOAT NULL,
  start_trigger_rot_z FLOAT NULL,
  leave_trigger_x DOUBLE NULL,
  leave_trigger_y DOUBLE NULL,
  leave_trigger_z DOUBLE NULL,
  leave_trigger_rot_x FLOAT NULL,
  leave_trigger_rot_y FLOAT NULL,
  leave_trigger_rot_z FLOAT NULL,
  leave_teleport_x DOUBLE NULL,
  leave_teleport_y DOUBLE NULL,
  leave_teleport_z DOUBLE NULL,
  leave_teleport_rot_x FLOAT NULL,
  leave_teleport_rot_y FLOAT NULL,
  leave_teleport_rot_z FLOAT NULL,
  created_at TIMESTAMP NULL,
  updated_at TIMESTAMP NULL
) ENGINE=InnoDB;
```

## map_checkpoints
Stores checkpoint transforms per map.

Suggested schema:
```sql
CREATE TABLE map_checkpoints (
  map_id VARCHAR(64) NOT NULL,
  checkpoint_index INT NOT NULL,
  x DOUBLE NOT NULL,
  y DOUBLE NOT NULL,
  z DOUBLE NOT NULL,
  rot_x FLOAT NOT NULL,
  rot_y FLOAT NOT NULL,
  rot_z FLOAT NOT NULL,
  PRIMARY KEY (map_id, checkpoint_index)
) ENGINE=InnoDB;
```

## player_completions
Stores completed maps and best times per player.

Suggested schema:
```sql
CREATE TABLE player_completions (
  player_uuid CHAR(36) NOT NULL,
  map_id VARCHAR(64) NOT NULL,
  best_time_ms BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, map_id)
) ENGINE=InnoDB;
```

## player_checkpoint_times
Stores checkpoint split times for each player's personal best run on each map.

Suggested schema:
```sql
CREATE TABLE player_checkpoint_times (
  player_uuid CHAR(36) NOT NULL,
  map_id VARCHAR(64) NOT NULL,
  checkpoint_index INT NOT NULL,
  time_ms BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, map_id, checkpoint_index)
) ENGINE=InnoDB;
```

Notes:
- `time_ms` is the elapsed time from run start to reaching that checkpoint.
- Only updated when a player achieves a new personal best.
- `checkpoint_index` is 0-based.

## player_mode_state
Stores per-player mode state and hub return locations (owned by the hub).

Suggested schema:
```sql
CREATE TABLE player_mode_state (
  player_uuid CHAR(36) PRIMARY KEY,
  current_mode VARCHAR(16) NOT NULL,
  parkour_world VARCHAR(64) NULL,
  parkour_x DOUBLE NULL,
  parkour_y DOUBLE NULL,
  parkour_z DOUBLE NULL,
  parkour_rot_x FLOAT NULL,
  parkour_rot_y FLOAT NULL,
  parkour_rot_z FLOAT NULL,
  ascend_world VARCHAR(64) NULL,
  ascend_x DOUBLE NULL,
  ascend_y DOUBLE NULL,
  ascend_z DOUBLE NULL,
  ascend_rot_x FLOAT NULL,
  ascend_rot_y FLOAT NULL,
  ascend_rot_z FLOAT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;
```

Notes:
- If you previously created `depths_*` columns, the hub will migrate them to `ascend_*` on startup.

## settings
Stores global server settings (single row, id = 1).

Suggested schema:
```sql
CREATE TABLE settings (
  id INT NOT NULL PRIMARY KEY,
  fall_respawn_seconds DOUBLE NOT NULL,
  void_y_failsafe DOUBLE NOT NULL,
  weapon_damage_disabled BOOLEAN NOT NULL,
  debug_mode BOOLEAN NOT NULL,
  spawn_x DOUBLE NULL,
  spawn_y DOUBLE NULL,
  spawn_z DOUBLE NULL,
  spawn_rot_x FLOAT NULL,
  spawn_rot_y FLOAT NULL,
  spawn_rot_z FLOAT NULL,
  idle_fall_respawn_for_op BOOLEAN NOT NULL,
  category_order_json TEXT NULL
) ENGINE=InnoDB;
```

## global_messages
Stores broadcast messages.

Suggested schema:
```sql
CREATE TABLE global_messages (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  message VARCHAR(240) NOT NULL,
  display_order INT NOT NULL
) ENGINE=InnoDB;
```

## global_message_settings
Stores the global message interval (single row, id = 1).

Suggested schema:
```sql
CREATE TABLE global_message_settings (
  id INT NOT NULL PRIMARY KEY,
  interval_minutes BIGINT NOT NULL
) ENGINE=InnoDB;
```

## player_count_samples
Stores player count analytics samples.

Suggested schema:
```sql
CREATE TABLE player_count_samples (
  timestamp_ms BIGINT NOT NULL PRIMARY KEY,
  count INT NOT NULL
) ENGINE=InnoDB;
```

## Notes
- Foreign keys are not required by the current code, but you can add them if desired.
- All reads/writes are performed by the stores in `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/`.
- Hub state is stored in `hyvexa-core/src/main/java/io/hyvexa/core/state/PlayerModeStateStore.java`.

## Shared Database

Parkour and Parkour Ascend will share the same MySQL database. Each module owns its own tables
(e.g., `parkour_*` vs `ascend_*`) to avoid collisions.
