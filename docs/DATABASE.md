# Database Schema

This project stores parkour data in MySQL. The tables below reflect the current code paths
(read/write queries) in the parkour plugin. Types are recommended defaults that match usage.

Runtime notes:
- Server working directory is `run/`, so runtime config lives in `mods/Parkour/`
- `mods/Parkour/database.json` holds MySQL credentials (gitignored)
- MySQL is the source of truth for persisted state; some runtime values are intentionally computed from constants (for example Ascend map balance values from `display_order`)
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
  playtime_ms BIGINT NOT NULL,
  vip BOOLEAN NOT NULL DEFAULT FALSE,
  founder BOOLEAN NOT NULL DEFAULT FALSE,
  teleport_item_use_count INT NOT NULL DEFAULT 0,
  jump_count BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
```

Notes:
- `teleport_item_use_count` tracks how many times the player has used the Map Selector teleport item (for showing hints during first 5 uses)
- `jump_count` tracks the total number of jumps the player has ever made (cumulative)

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
  glider_enabled BOOLEAN NOT NULL,
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
  fly_zone_min_x DOUBLE NULL,
  fly_zone_min_y DOUBLE NULL,
  fly_zone_min_z DOUBLE NULL,
  fly_zone_max_x DOUBLE NULL,
  fly_zone_max_y DOUBLE NULL,
  fly_zone_max_z DOUBLE NULL,
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

## player_gems
Stores the global gems currency balance per player. Shared across all modules.

Suggested schema:
```sql
CREATE TABLE player_gems (
  uuid VARCHAR(36) NOT NULL PRIMARY KEY,
  gems BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
```

Notes:
- Auto-created by `GemStore.initialize()` on startup
- Managed by `hyvexa-core/src/main/java/io/hyvexa/core/economy/GemStore.java`
- Writes are immediate (no dirty tracking) since gems are rare/precious
- Player cache is evicted on disconnect (lazy-loaded on next access)

## Notes
- Foreign keys are not required by the current code, but you can add them if desired.
- All reads/writes are performed by the stores in `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/`.
- Hub state is stored in `hyvexa-core/src/main/java/io/hyvexa/core/state/PlayerModeStateStore.java`.

## Shared Database

Parkour and Parkour Ascend will share the same MySQL database. Each module owns its own tables
(e.g., `parkour_*` vs `ascend_*`) to avoid collisions.

---

# Ascend Tables

## ascend_players
Stores Ascend player state including prestige progress.

Suggested schema:
```sql
CREATE TABLE ascend_players (
  uuid VARCHAR(36) PRIMARY KEY,
  vexa DECIMAL(65,2) NOT NULL DEFAULT 0,
  elevation_multiplier INT NOT NULL DEFAULT 1,
  ascension_count INT NOT NULL DEFAULT 0,
  skill_tree_points INT NOT NULL DEFAULT 0,
  total_vexa_earned DECIMAL(65,2) NOT NULL DEFAULT 0,
  total_manual_runs INT NOT NULL DEFAULT 0,
  summit_accumulated_vexa DECIMAL(65,2) NOT NULL DEFAULT 0,
  active_title VARCHAR(64) DEFAULT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;
```

Notes:
- `vexa`, `total_vexa_earned`, and `summit_accumulated_vexa` use DECIMAL(65,2) for precise fractional values (BigDecimal in code).
- `summit_accumulated_vexa` tracks total vexa earned during the current Summit progress cycle (used for VEXA_FLOW progression).
- `elevation_multiplier` stores the elevation **level** (not the actual multiplier). The actual multiplier is calculated as `1 + 0.1 × level^0.65`. Column name kept for backwards compatibility.
- `ascension_count` tracks how many times the player has Ascended.
- `skill_tree_points` is the total points earned (ascension_count, may differ if points are granted by other means).
- `total_vexa_earned` is lifetime vexa for achievement tracking (never resets).
- `total_manual_runs` is lifetime manual completions for achievement tracking.
- `active_title` is the currently selected title from achievements.

## ascend_maps
Stores Ascend map definitions.

Suggested schema:
```sql
CREATE TABLE ascend_maps (
  id VARCHAR(32) PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  price BIGINT NOT NULL DEFAULT 0,
  robot_price BIGINT NOT NULL,
  base_reward BIGINT NOT NULL,
  base_run_time_ms BIGINT NOT NULL,
  robot_time_reduction_ms BIGINT NOT NULL DEFAULT 0,
  storage_capacity INT NOT NULL DEFAULT 100,
  world VARCHAR(64) NOT NULL,
  start_x DOUBLE NOT NULL,
  start_y DOUBLE NOT NULL,
  start_z DOUBLE NOT NULL,
  start_rot_x FLOAT NOT NULL DEFAULT 0,
  start_rot_y FLOAT NOT NULL DEFAULT 0,
  start_rot_z FLOAT NOT NULL DEFAULT 0,
  finish_x DOUBLE NOT NULL,
  finish_y DOUBLE NOT NULL,
  finish_z DOUBLE NOT NULL,
  waypoints_json TEXT,
  display_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
```

Notes:
- Runtime source of truth for Ascend map balance is `display_order` + `AscendConstants` (unlock price, runner price, base reward, and base run time are computed at runtime).
- `price`, `robot_price`, `base_reward`, `base_run_time_ms`, `robot_time_reduction_ms`, and `storage_capacity` are legacy compatibility columns retained for older schemas/tools.
- `AscendMapStore` no longer reads legacy balance/storage columns; this is the backward-compatible read stage for migration.
- Legacy column writes are still preserved during rollout. Remove legacy writes/columns only after a compatibility window confirms safe migration.

## ascend_player_maps
Stores per-player progress on each Ascend map.

Suggested schema:
```sql
CREATE TABLE ascend_player_maps (
  player_uuid VARCHAR(36) NOT NULL,
  map_id VARCHAR(32) NOT NULL,
  unlocked BOOLEAN NOT NULL DEFAULT FALSE,
  completed_manually BOOLEAN NOT NULL DEFAULT FALSE,
  has_robot BOOLEAN NOT NULL DEFAULT FALSE,
  robot_speed_level INT NOT NULL DEFAULT 0,
  robot_stars INT NOT NULL DEFAULT 0,
  multiplier DECIMAL(65,20) NOT NULL DEFAULT 1.0,
  best_time_ms BIGINT NULL,
  last_collection_at TIMESTAMP NULL,
  PRIMARY KEY (player_uuid, map_id),
  FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE,
  FOREIGN KEY (map_id) REFERENCES ascend_maps(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- `robot_stars` tracks evolution level (0-5). Each star doubles the multiplier increment per completion.
- `robot_speed_level` resets to 0 when evolving to a new star level.
- `best_time_ms` stores the player's personal best time for this map (used for ghost recordings and PB display).

## ascend_upgrade_costs
Stores upgrade cost tiers (optional, can use calculated values instead).

Suggested schema:
```sql
CREATE TABLE ascend_upgrade_costs (
  upgrade_type VARCHAR(32) NOT NULL,
  level INT NOT NULL,
  cost BIGINT NOT NULL,
  PRIMARY KEY (upgrade_type, level)
) ENGINE=InnoDB;
```

## ascend_player_summit
Stores Summit XP per category per player.

Suggested schema:
```sql
CREATE TABLE ascend_player_summit (
  player_uuid VARCHAR(36) NOT NULL,
  category VARCHAR(32) NOT NULL,
  xp BIGINT NOT NULL DEFAULT 0,
  xp_scale_v2 TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (player_uuid, category),
  FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- `category` values: `VEXA_FLOW`, `RUNNER_SPEED`, `MANUAL_MASTERY`
- `xp` is the Summit XP in that category (converted to level via formula in code)
- `xp_scale_v2` is a migration marker (prevents re-running XP scale migration)
- Summit levels reset on Ascension
- **Migration history:** Old `level` column was migrated to `xp`, then XP values were rescaled (exponent 2.5 -> 2.0)

## ascend_player_skills
Stores unlocked skill tree nodes per player.

Suggested schema:
```sql
CREATE TABLE ascend_player_skills (
  player_uuid VARCHAR(36) NOT NULL,
  skill_node VARCHAR(64) NOT NULL,
  unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (player_uuid, skill_node),
  FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- `skill_node` is the enum name from `AscendConstants.SkillTreeNode`
- Skill unlocks are permanent (never reset)
- Skill nodes: VEXA_T1_*, SPEED_T1_*, MANUAL_T1_*, HYBRID_*, ULTIMATE_*

## ascend_player_achievements
Stores unlocked achievements per player.

Suggested schema:
```sql
CREATE TABLE ascend_player_achievements (
  player_uuid VARCHAR(36) NOT NULL,
  achievement VARCHAR(64) NOT NULL,
  unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (player_uuid, achievement),
  FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- `achievement` is the enum name from `AscendConstants.AchievementType`
- Achievement unlocks are permanent (never reset)
- Each achievement grants a title that can be selected via `/ascend title`

## ascend_settings
Stores global Ascend settings (single row, id = 1).

Suggested schema:
```sql
CREATE TABLE ascend_settings (
  id INT NOT NULL PRIMARY KEY,
  spawn_x DOUBLE NOT NULL DEFAULT 0,
  spawn_y DOUBLE NOT NULL DEFAULT 0,
  spawn_z DOUBLE NOT NULL DEFAULT 0,
  spawn_rot_x FLOAT NOT NULL DEFAULT 0,
  spawn_rot_y FLOAT NOT NULL DEFAULT 0,
  spawn_rot_z FLOAT NOT NULL DEFAULT 0,
  npc_x DOUBLE NOT NULL DEFAULT 0,
  npc_y DOUBLE NOT NULL DEFAULT 0,
  npc_z DOUBLE NOT NULL DEFAULT 0,
  npc_rot_x FLOAT NOT NULL DEFAULT 0,
  npc_rot_y FLOAT NOT NULL DEFAULT 0,
  npc_rot_z FLOAT NOT NULL DEFAULT 0,
  void_y_threshold DOUBLE DEFAULT NULL
) ENGINE=InnoDB;
```

Notes:
- Single row with `id = 1`
- `spawn_*` fields store the spawn teleport location (configurable via `/as admin` panel)
- `npc_*` fields store the NPC teleport location (configurable via `/as admin` panel)
- `void_y_threshold` stores the Y coordinate below which players are teleported back to spawn (NULL = disabled)

## ascend_challenges
Stores active challenge state per player (one active challenge at a time).

Suggested schema:
```sql
CREATE TABLE ascend_challenges (
  player_uuid VARCHAR(36) PRIMARY KEY,
  challenge_type_id INT NOT NULL,
  started_at_ms BIGINT NOT NULL,
  snapshot_json MEDIUMTEXT NOT NULL,
  FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- One active challenge per player (PK on player_uuid)
- `challenge_type_id` maps to an enum in code
- `snapshot_json` stores challenge-specific state (e.g., target values, progress)
- Row is deleted when challenge completes or is abandoned

## ascend_challenge_records
Stores permanent best times and completion counts per challenge type per player.

Suggested schema:
```sql
CREATE TABLE ascend_challenge_records (
  player_uuid VARCHAR(36) NOT NULL,
  challenge_type_id INT NOT NULL,
  best_time_ms BIGINT DEFAULT NULL,
  completions INT NOT NULL DEFAULT 0,
  PRIMARY KEY (player_uuid, challenge_type_id),
  FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- Permanent records (never deleted)
- `best_time_ms` is NULL until first completion
- `completions` counts total successful completions of that challenge type

## ascend_ghost_recordings
Stores ghost recordings for runner replay (personal best movement paths).

Suggested schema:
```sql
CREATE TABLE ascend_ghost_recordings (
  player_uuid VARCHAR(36) NOT NULL,
  map_id VARCHAR(32) NOT NULL,
  recording_blob MEDIUMBLOB NOT NULL,
  completion_time_ms BIGINT NOT NULL,
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (player_uuid, map_id)
) ENGINE=InnoDB;
```

Notes:
- `recording_blob` contains GZIP-compressed binary data with position/rotation samples at 50ms intervals
- Maximum ~12,000 samples allowed (10 minutes max recording) to prevent DoS
- Typical recording size: 5-10 KB per map after compression
- `completion_time_ms` stores the player's personal best time for this recording
- Recordings are only saved when achieving a new personal best
- Table is created automatically by `GhostStore.ensureGhostTableExists()` on startup
