<!-- Last verified against code: 2026-03-25 -->
# Ascend Database Schema

Tables owned by `hyvexa-parkour-ascend` for the Ascend idle mode and Mine subsystem.
All use the shared MySQL database via `DatabaseManager`.

---

# Ascend Tables

## ascend_players
Stores Ascend player state including prestige progress.

Suggested schema (base CREATE TABLE + all migration columns):
```sql
CREATE TABLE IF NOT EXISTS ascend_players (
  uuid VARCHAR(36) PRIMARY KEY,
  volt_mantissa DOUBLE NOT NULL DEFAULT 0,
  volt_exp10 INT NOT NULL DEFAULT 0,
  elevation_multiplier INT NOT NULL DEFAULT 1,
  ascension_count INT NOT NULL DEFAULT 0,
  skill_tree_points INT NOT NULL DEFAULT 0,
  total_volt_earned_mantissa DOUBLE NOT NULL DEFAULT 0,
  total_volt_earned_exp10 INT NOT NULL DEFAULT 0,
  total_manual_runs INT NOT NULL DEFAULT 0,
  summit_accumulated_volt_mantissa DOUBLE NOT NULL DEFAULT 0,
  summit_accumulated_volt_exp10 INT NOT NULL DEFAULT 0,
  elevation_accumulated_volt_mantissa DOUBLE NOT NULL DEFAULT 0,
  elevation_accumulated_volt_exp10 INT NOT NULL DEFAULT 0,
  active_title VARCHAR(64) DEFAULT NULL,
  -- Migration columns (added via ALTER TABLE):
  ascension_started_at BIGINT DEFAULT NULL,
  fastest_ascension_ms BIGINT DEFAULT NULL,
  last_active_timestamp BIGINT DEFAULT NULL,
  has_unclaimed_passive BOOLEAN NOT NULL DEFAULT FALSE,
  auto_upgrade_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  auto_evolution_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  auto_elevation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  auto_elevation_timer_seconds INT NOT NULL DEFAULT 0,
  auto_elevation_targets TEXT DEFAULT '[]',
  auto_elevation_target_index INT NOT NULL DEFAULT 0,
  auto_summit_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  auto_summit_timer_seconds INT NOT NULL DEFAULT 0,
  auto_summit_config TEXT DEFAULT '[...]',
  auto_summit_rotation_index INT NOT NULL DEFAULT 0,
  auto_ascend_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  break_ascension_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  hide_other_runners BOOLEAN NOT NULL DEFAULT FALSE,
  hud_hidden BOOLEAN NOT NULL DEFAULT FALSE,
  players_hidden BOOLEAN NOT NULL DEFAULT FALSE,
  player_name VARCHAR(32) DEFAULT NULL,
  seen_tutorials INT NOT NULL DEFAULT 0,
  transcendence_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;
```

Notes:
- Volt values are stored as scientific notation pairs (`*_mantissa` + `*_exp10`) for BigNumber support.
- `summit_accumulated_volt_*` tracks total volt earned during the current Summit progress cycle.
- `elevation_multiplier` stores the elevation **level** (not the actual multiplier). The actual multiplier is `max(1, level)` (linear). Column name kept for backwards compatibility.
- `ascension_count` tracks how many times the player has Ascended.
- `transcendence_count` tracks how many times the player has Transcended (4th prestige).
- `skill_tree_points` is the total points earned (ascension_count, may differ if points are granted by other means).
- `total_volt_earned_*` is lifetime volt for achievement tracking (never resets).
- `total_manual_runs` is lifetime manual completions for achievement tracking.
- `active_title` is the currently selected title from achievements.
- `auto_*` columns store automation settings from the Ascendancy skill tree.
- `seen_tutorials` is a bitmask tracking which tutorials the player has seen.
- Migration columns are added automatically by `AscendDatabaseSetup` on startup.

## ascend_maps
Stores Ascend map definitions.

```sql
CREATE TABLE IF NOT EXISTS ascend_maps (
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

```sql
CREATE TABLE IF NOT EXISTS ascend_player_maps (
  player_uuid VARCHAR(36) NOT NULL,
  map_id VARCHAR(32) NOT NULL,
  unlocked BOOLEAN NOT NULL DEFAULT FALSE,
  completed_manually BOOLEAN NOT NULL DEFAULT FALSE,
  has_robot BOOLEAN NOT NULL DEFAULT FALSE,
  robot_speed_level INT NOT NULL DEFAULT 0,
  robot_stars INT NOT NULL DEFAULT 0,
  multiplier_mantissa DOUBLE NOT NULL DEFAULT 1.0,
  multiplier_exp10 INT NOT NULL DEFAULT 0,
  best_time_ms BIGINT NULL,
  last_collection_at TIMESTAMP NULL,
  PRIMARY KEY (player_uuid, map_id),
  FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE,
  FOREIGN KEY (map_id) REFERENCES ascend_maps(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- `multiplier_mantissa` + `multiplier_exp10` store the multiplier as scientific notation (mantissa x 10^exp10) for BigNumber support. Migrated from a single `multiplier DOUBLE` column.
- `robot_stars` tracks evolution level (0-5). Each star doubles the multiplier increment per completion.
- `robot_speed_level` resets to 0 when evolving to a new star level.
- `best_time_ms` stores the player's personal best time for this map (used for ghost recordings and PB display).

## ascend_upgrade_costs
Stores upgrade cost tiers (optional, can use calculated values instead).

```sql
CREATE TABLE IF NOT EXISTS ascend_upgrade_costs (
  upgrade_type VARCHAR(32) NOT NULL,
  level INT NOT NULL,
  cost BIGINT NOT NULL,
  PRIMARY KEY (upgrade_type, level)
) ENGINE=InnoDB;
```

## ascend_player_summit
Stores Summit XP per category per player.

```sql
CREATE TABLE IF NOT EXISTS ascend_player_summit (
  player_uuid VARCHAR(36) NOT NULL,
  category VARCHAR(32) NOT NULL,
  xp DOUBLE NOT NULL DEFAULT 0,
  xp_scale_v2 TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (player_uuid, category),
  FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- `category` values: `MULTIPLIER_GAIN`, `RUNNER_SPEED`, `EVOLUTION_POWER`
- `xp` is the Summit XP in that category (converted to level via formula in code)
- `xp_scale_v2` is a migration marker (prevents re-running XP scale migration)
- Summit levels reset on Ascension
- **Migration history:** Old `level` column was migrated to `xp`, then XP values were rescaled (exponent 2.5 -> 2.0)

## ascend_player_skills
Stores unlocked skill tree nodes per player.

```sql
CREATE TABLE IF NOT EXISTS ascend_player_skills (
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

```sql
CREATE TABLE IF NOT EXISTS ascend_player_achievements (
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

```sql
CREATE TABLE IF NOT EXISTS ascend_settings (
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

```sql
CREATE TABLE IF NOT EXISTS ascend_challenges (
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

```sql
CREATE TABLE IF NOT EXISTS ascend_challenge_records (
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

```sql
CREATE TABLE IF NOT EXISTS ascend_ghost_recordings (
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

## ascend_player_cats
Stores collectible cat tokens found per player in Ascend mode.

```sql
CREATE TABLE IF NOT EXISTS ascend_player_cats (
  player_uuid VARCHAR(36) NOT NULL,
  cat_token VARCHAR(16) NOT NULL,
  found_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (player_uuid, cat_token),
  FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Auto-created by `AscendDatabaseSetup` on startup.

---

# Mine Tables

Tables owned by `hyvexa-parkour-ascend` for the Mine subsystem within Ascend mode.
All created by `AscendDatabaseSetup.ensureTables()` on startup.

## mine_definitions
Stores mine configuration definitions.

```sql
CREATE TABLE IF NOT EXISTS mine_definitions (
  id VARCHAR(32) PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  display_order INT NOT NULL DEFAULT 0,
  unlock_cost_mantissa DOUBLE NOT NULL DEFAULT 0,
  unlock_cost_exp10 INT NOT NULL DEFAULT 0,
  world VARCHAR(64) NOT NULL DEFAULT '',
  spawn_x DOUBLE NOT NULL DEFAULT 0,
  spawn_y DOUBLE NOT NULL DEFAULT 0,
  spawn_z DOUBLE NOT NULL DEFAULT 0,
  spawn_rot_x FLOAT NOT NULL DEFAULT 0,
  spawn_rot_y FLOAT NOT NULL DEFAULT 0,
  spawn_rot_z FLOAT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
```

Notes:
- Unlock cost stored as BigNumber pair (`unlock_cost_mantissa` x 10^`unlock_cost_exp10`)
- `display_order` controls the order in which mines appear in the UI
- Manager: `MineConfigStore` (in `hyvexa-parkour-ascend`)

## mine_zones
Stores mineable zone regions within each mine.

```sql
CREATE TABLE IF NOT EXISTS mine_zones (
  id VARCHAR(32) NOT NULL,
  mine_id VARCHAR(32) NOT NULL,
  min_x INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL,
  max_x INT NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL,
  block_table_json TEXT NOT NULL DEFAULT '{}',
  block_hp_json TEXT NOT NULL DEFAULT '{}',
  regen_threshold DOUBLE NOT NULL DEFAULT 0.8,
  regen_cooldown_seconds INT NOT NULL DEFAULT 45,
  PRIMARY KEY (id),
  FOREIGN KEY (mine_id) REFERENCES mine_definitions(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- `block_table_json` is a JSON object mapping block type IDs to spawn weight probabilities (e.g., `{"Rock_Stone": 0.7, "Ore_Iron_Stone": 0.3}`)
- `block_hp_json` is a JSON object mapping block type IDs to hit points (e.g., `{"Ore_Iron_Stone": 3}`). Blocks not listed default to 1 HP (instant break). Configurable via admin UI +/- buttons.
- `regen_threshold` is the fraction of blocks that must be mined before the zone regenerates (0.0-1.0)
- `regen_cooldown_seconds` is the cooldown between zone regenerations
- Manager: `MineConfigStore` (in `hyvexa-parkour-ascend`)

## mine_zone_layers
Stores depth layers within mine zones for Y-dependent block distributions and HP overrides.

```sql
CREATE TABLE IF NOT EXISTS mine_zone_layers (
  id VARCHAR(32) NOT NULL,
  zone_id VARCHAR(32) NOT NULL,
  min_y INT NOT NULL,
  max_y INT NOT NULL,
  block_table_json TEXT NOT NULL DEFAULT '{}',
  -- Migration columns (added via ALTER TABLE):
  block_hp_json TEXT NOT NULL DEFAULT '{}',
  egg_drop_chance DOUBLE NOT NULL DEFAULT 0.5,
  display_name VARCHAR(64) NOT NULL DEFAULT '',
  PRIMARY KEY (id),
  FOREIGN KEY (zone_id) REFERENCES mine_zones(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- Each layer overrides the zone's `block_table_json` and `block_hp_json` for its Y range
- Layers must not overlap in Y range within the same zone
- `egg_drop_chance` controls the probability of egg drops from blocks in this layer (for gacha system)
- `display_name` is the human-readable name shown in UI
- Manager: `MineConfigStore` (in `hyvexa-parkour-ascend`)

## mine_gate
Stores entry/exit gate regions for teleporting players in/out of the mine area.

```sql
CREATE TABLE IF NOT EXISTS mine_gate (
  id INT NOT NULL PRIMARY KEY DEFAULT 1,
  min_x DOUBLE NOT NULL DEFAULT 0, min_y DOUBLE NOT NULL DEFAULT 0, min_z DOUBLE NOT NULL DEFAULT 0,
  max_x DOUBLE NOT NULL DEFAULT 0, max_y DOUBLE NOT NULL DEFAULT 0, max_z DOUBLE NOT NULL DEFAULT 0,
  fallback_x DOUBLE NOT NULL DEFAULT 0, fallback_y DOUBLE NOT NULL DEFAULT 0, fallback_z DOUBLE NOT NULL DEFAULT 0,
  fallback_rot_x FLOAT NOT NULL DEFAULT 0, fallback_rot_y FLOAT NOT NULL DEFAULT 0, fallback_rot_z FLOAT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
```

Notes:
- Two rows: `id = 1` for entry gate (ascend area -> mine), `id = 2` for exit gate (mine -> ascend area)
- `min_*` / `max_*` define the AABB trigger region
- `fallback_*` define the teleport destination position and rotation
- Manager: `MineConfigStore` (in `hyvexa-parkour-ascend`)

## mine_players
Stores per-player mine progress including crystal currency and upgrade levels.

```sql
CREATE TABLE IF NOT EXISTS mine_players (
  uuid VARCHAR(36) PRIMARY KEY,
  crystals DECIMAL(20,2) NOT NULL DEFAULT 0,
  -- Migration columns (added via ALTER TABLE):
  mining_speed_level INT NOT NULL DEFAULT 0,  -- DEPRECATED: no longer used
  bag_capacity_level INT NOT NULL DEFAULT 0,
  multi_break_level INT NOT NULL DEFAULT 0,   -- DEPRECATED: no longer used
  auto_sell_level INT NOT NULL DEFAULT 0,
  upgrade_momentum INT NOT NULL DEFAULT 0,
  upgrade_fortune INT NOT NULL DEFAULT 0,
  upgrade_jackhammer INT NOT NULL DEFAULT 0,
  upgrade_stomp INT NOT NULL DEFAULT 0,
  upgrade_blast INT NOT NULL DEFAULT 0,
  upgrade_haste INT NOT NULL DEFAULT 0,
  upgrade_conveyor_capacity INT NOT NULL DEFAULT 0,
  upgrade_cashback INT NOT NULL DEFAULT 0,
  in_mine TINYINT(1) NOT NULL DEFAULT 0,
  pickaxe_tier INT NOT NULL DEFAULT 0,
  pickaxe_enhancement INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- `crystals` is the mine-specific currency (migrated from BigNumber mantissa+exp10 to BIGINT, then to DECIMAL(20,2) for fractional cashback amounts)
- Upgrade level columns added via `ensureMineUpgradeColumns()` migration
- `upgrade_momentum` through `upgrade_cashback` are the 8 active upgrade types (see `MineUpgradeType` enum)
- `mining_speed_level` and `multi_break_level` are deprecated legacy columns (no longer read by code)
- `pickaxe_tier` tracks the player's current pickaxe tier
- `pickaxe_enhancement` tracks the player's current pickaxe enhancement level within their tier
- `in_mine` tracks whether the player is currently in the mine (persisted for reconnect restoration)
- Manager: `MinePlayerStore` (in `hyvexa-parkour-ascend`) -- dirty-tracking with 5-second batched saves

## mine_player_inventory
Stores virtual block counts per player (mined blocks awaiting sale).

```sql
CREATE TABLE IF NOT EXISTS mine_player_inventory (
  player_uuid VARCHAR(36) NOT NULL,
  block_type_id VARCHAR(64) NOT NULL,
  amount INT NOT NULL DEFAULT 0,
  PRIMARY KEY (player_uuid, block_type_id),
  FOREIGN KEY (player_uuid) REFERENCES mine_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- Stored blocks are sold for crystals at prices defined in `block_prices`
- Inventory is delete+re-insert on save (full replace strategy)
- Manager: `MinePlayerStore` (in `hyvexa-parkour-ascend`)

## block_prices
Stores global sell prices for each block type (not per-mine).

```sql
CREATE TABLE IF NOT EXISTS block_prices (
  block_type_id VARCHAR(64) PRIMARY KEY,
  price BIGINT NOT NULL DEFAULT 1
) ENGINE=InnoDB;
```

Notes:
- Default price is 1 crystal per block if no row exists
- Migrated from BigNumber pair (`price_mantissa` + `price_exp10`) to simple BIGINT
- Manager: `MineConfigStore` (in `hyvexa-parkour-ascend`)

## mine_player_mines
Stores per-player per-mine unlock and completion state.

```sql
CREATE TABLE IF NOT EXISTS mine_player_mines (
  player_uuid VARCHAR(36) NOT NULL,
  mine_id VARCHAR(32) NOT NULL,
  unlocked BOOLEAN NOT NULL DEFAULT FALSE,
  completed_manually BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (player_uuid, mine_id),
  FOREIGN KEY (player_uuid) REFERENCES mine_players(uuid) ON DELETE CASCADE,
  FOREIGN KEY (mine_id) REFERENCES mine_definitions(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- First mine is auto-unlocked for all players on first load
- Manager: `MinePlayerStore` (in `hyvexa-parkour-ascend`)

## mine_player_miners
Stores per-player per-mine automated miner (NPC) state.

```sql
CREATE TABLE IF NOT EXISTS mine_player_miners (
  player_uuid VARCHAR(36) NOT NULL,
  mine_id VARCHAR(32) NOT NULL,
  has_miner BOOLEAN NOT NULL DEFAULT FALSE,
  speed_level INT NOT NULL DEFAULT 0,
  stars INT NOT NULL DEFAULT 0,
  -- Migration column (added via ALTER TABLE):
  slot_index INT NOT NULL DEFAULT 0,
  PRIMARY KEY (player_uuid, mine_id, slot_index),
  FOREIGN KEY (player_uuid) REFERENCES mine_players(uuid) ON DELETE CASCADE,
  FOREIGN KEY (mine_id) REFERENCES mine_definitions(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- `stars` tracks evolution level (similar to Ascend runners)
- `speed_level` is the miner's speed upgrade level
- `slot_index` added via migration to support multi-slot miners; PK migrated from `(player_uuid, mine_id)` to `(player_uuid, mine_id, slot_index)`
- Legacy table -- new gacha-based miner system uses `mine_player_miners_v2` and `mine_player_slot_assignments`
- Manager: `MinePlayerStore` (in `hyvexa-parkour-ascend`)

## block_hp
Stores global HP (hit points) for each block type.

```sql
CREATE TABLE IF NOT EXISTS block_hp (
  block_type_id VARCHAR(64) PRIMARY KEY,
  hp INT NOT NULL DEFAULT 1
) ENGINE=InnoDB;
```

Notes:
- Blocks not listed default to 1 HP (instant break)
- Manager: `MineConfigStore` (in `hyvexa-parkour-ascend`)

## mine_achievements
Stores unlocked mine achievements per player.

```sql
CREATE TABLE IF NOT EXISTS mine_achievements (
  player_uuid VARCHAR(36) NOT NULL,
  achievement_id VARCHAR(50) NOT NULL,
  completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (player_uuid, achievement_id)
) ENGINE=InnoDB;
```

Notes:
- Achievement unlocks are permanent
- Auto-created by `AscendDatabaseSetup.ensureTables()` on startup

## mine_player_stats
Stores lifetime mine counters per player (for achievement tracking).

```sql
CREATE TABLE IF NOT EXISTS mine_player_stats (
  player_uuid VARCHAR(36) PRIMARY KEY,
  total_blocks_mined BIGINT NOT NULL DEFAULT 0,
  total_crystals_earned BIGINT NOT NULL DEFAULT 0,
  -- Migration column (added via ALTER TABLE):
  manual_blocks_mined BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
```

Notes:
- `manual_blocks_mined` tracks blocks mined by the player directly (not by automated miners)
- Auto-created by `AscendDatabaseSetup.ensureTables()` on startup

## mine_miner_slots
Stores admin-configured miner NPC positions and conveyor endpoints per mine per slot.

```sql
CREATE TABLE IF NOT EXISTS mine_miner_slots (
  mine_id VARCHAR(32) NOT NULL,
  -- Migration column (added via ALTER TABLE, PK migrated):
  slot_index INT NOT NULL DEFAULT 0,
  npc_x DOUBLE NOT NULL DEFAULT 0,
  npc_y DOUBLE NOT NULL DEFAULT 0,
  npc_z DOUBLE NOT NULL DEFAULT 0,
  npc_yaw FLOAT NOT NULL DEFAULT 0,
  block_x INT NOT NULL DEFAULT 0,
  block_y INT NOT NULL DEFAULT 0,
  block_z INT NOT NULL DEFAULT 0,
  interval_seconds DOUBLE NOT NULL DEFAULT 5.0,
  -- Migration columns (added via ALTER TABLE):
  conveyor_end_x DOUBLE NOT NULL DEFAULT 0,
  conveyor_end_y DOUBLE NOT NULL DEFAULT 0,
  conveyor_end_z DOUBLE NOT NULL DEFAULT 0,
  conveyor_speed DOUBLE NOT NULL DEFAULT 2.0,
  PRIMARY KEY (mine_id, slot_index),
  FOREIGN KEY (mine_id) REFERENCES mine_definitions(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- `slot_index` added via migration to support multiple miner slots per mine; PK migrated from `(mine_id)` to `(mine_id, slot_index)`
- `conveyor_*` columns define the conveyor belt endpoint and speed for block transport
- Manager: `MineConfigStore` (in `hyvexa-parkour-ascend`)

## mine_conveyor_waypoints
Stores conveyor belt waypoints per mine per slot.

```sql
CREATE TABLE IF NOT EXISTS mine_conveyor_waypoints (
  mine_id VARCHAR(32) NOT NULL,
  slot_index INT NOT NULL,
  waypoint_order INT NOT NULL,
  x DOUBLE NOT NULL,
  y DOUBLE NOT NULL,
  z DOUBLE NOT NULL,
  PRIMARY KEY (mine_id, slot_index, waypoint_order),
  FOREIGN KEY (mine_id) REFERENCES mine_definitions(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- Waypoints define the path blocks follow from miner to conveyor endpoint
- Manager: `MineConfigStore` (in `hyvexa-parkour-ascend`)

## mine_player_conveyor_buffer
Stores per-player conveyor buffer contents (blocks in transit).

```sql
CREATE TABLE IF NOT EXISTS mine_player_conveyor_buffer (
  player_uuid VARCHAR(36) NOT NULL,
  block_type_id VARCHAR(64) NOT NULL,
  amount INT NOT NULL DEFAULT 0,
  PRIMARY KEY (player_uuid, block_type_id),
  FOREIGN KEY (player_uuid) REFERENCES mine_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- Persists block counts on the conveyor belt for reconnect restoration
- Auto-created by `AscendDatabaseSetup.ensureTables()` on startup

## mine_player_eggs
Stores per-player egg inventory for the miner gacha system.

```sql
CREATE TABLE IF NOT EXISTS mine_player_eggs (
  player_uuid VARCHAR(36) NOT NULL,
  layer_id VARCHAR(64) NOT NULL,
  count INT NOT NULL DEFAULT 0,
  PRIMARY KEY (player_uuid, layer_id)
) ENGINE=InnoDB;
```

Notes:
- Eggs are earned by mining blocks and can be hatched to obtain miners
- `layer_id` corresponds to the mine zone layer where the egg was found
- Auto-created by `AscendDatabaseSetup.ensureTables()` on startup

## mine_player_miners_v2
Stores per-player miner collection (gacha-based system, replaces legacy `mine_player_miners`).

```sql
CREATE TABLE IF NOT EXISTS mine_player_miners_v2 (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  player_uuid VARCHAR(36) NOT NULL,
  layer_id VARCHAR(64) NOT NULL,
  rarity VARCHAR(16) NOT NULL,
  speed_level INT NOT NULL DEFAULT 0,
  INDEX idx_player (player_uuid)
) ENGINE=InnoDB;
```

Notes:
- Each row is a unique miner instance owned by a player
- `rarity` values correspond to gacha rarity tiers (e.g., `COMMON`, `RARE`, etc.)
- `layer_id` identifies the mine zone layer the miner originated from
- Old miners from `mine_player_miners` are auto-migrated to v2 as COMMON on first startup
- Auto-created by `AscendDatabaseSetup.ensureTables()` on startup

## mine_player_slot_assignments
Stores miner-to-slot assignments (which miner is deployed to which slot).

```sql
CREATE TABLE IF NOT EXISTS mine_player_slot_assignments (
  player_uuid VARCHAR(36) NOT NULL,
  slot_index INT NOT NULL,
  miner_id BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, slot_index)
) ENGINE=InnoDB;
```

Notes:
- `miner_id` references `mine_player_miners_v2.id`
- One miner per slot per player
- Auto-created by `AscendDatabaseSetup.ensureTables()` on startup

## mine_layer_rarity_blocks
Stores per-layer per-rarity block tables for miner output configuration.

```sql
CREATE TABLE IF NOT EXISTS mine_layer_rarity_blocks (
  layer_id VARCHAR(64) NOT NULL,
  rarity VARCHAR(16) NOT NULL,
  block_table_json TEXT NOT NULL DEFAULT '{}',
  PRIMARY KEY (layer_id, rarity)
) ENGINE=InnoDB;
```

Notes:
- `block_table_json` maps block type IDs to spawn weights (same format as `mine_zones.block_table_json`)
- Admin-configured or auto-seeded from zone layer data
- Auto-created by `AscendDatabaseSetup.ensureTables()` on startup

## mine_layer_miner_defs
Stores admin-configurable miner display names and portraits per layer per rarity.

```sql
CREATE TABLE IF NOT EXISTS mine_layer_miner_defs (
  layer_id VARCHAR(64) NOT NULL,
  rarity VARCHAR(16) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  portrait_id VARCHAR(32) NOT NULL,
  PRIMARY KEY (layer_id, rarity)
) ENGINE=InnoDB;
```

Notes:
- One row per layer×rarity combination
- `portrait_id` references a UI portrait element ID (e.g. `PortraitCommon1`)
- When no row exists, `MinerVariant` falls back to hardcoded defaults (variant 0)
- Managed via Mine Admin -> Miners admin page

## pickaxe_tier_recipes
Stores block requirements for pickaxe tier upgrades.

```sql
CREATE TABLE IF NOT EXISTS pickaxe_tier_recipes (
  tier INT NOT NULL,
  block_type_id VARCHAR(64) NOT NULL,
  amount INT NOT NULL DEFAULT 1,
  PRIMARY KEY (tier, block_type_id)
) ENGINE=InnoDB;
```

Notes:
- Each tier upgrade requires collecting specific block types and amounts
- Auto-created by `AscendDatabaseSetup.ensureTables()` on startup

## pickaxe_enhance_costs
Stores crystal costs for pickaxe enhancement per tier per level.

```sql
CREATE TABLE IF NOT EXISTS pickaxe_enhance_costs (
  tier INT NOT NULL,
  level INT NOT NULL,
  crystal_cost BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (tier, level)
) ENGINE=InnoDB;
```

Notes:
- Defines the crystal cost to enhance a pickaxe at a given tier and enhancement level
- Auto-created by `AscendDatabaseSetup.ensureTables()` on startup
