# Hyvexa Suite -- Database Schema

This document is the suite-wide schema reference for the Hyvexa plugin family.
All modules share a single MySQL database via `DatabaseManager` (in `hyvexa-core`), with one exception: `hyvexa-votifier` uses its own local SQLite/in-memory storage (see Votifier section below).
Tables are grouped by the subsystem that owns them.

Runtime notes:
- Server working directory is `run/`, so runtime config lives in `mods/Parkour/`
- `mods/Parkour/database.json` holds MySQL credentials (gitignored)
- MySQL is the source of truth for persisted state; some runtime values are intentionally computed from constants (for example Ascend map balance values from `display_order`)
- `DatabaseManager` lives in `hyvexa-core` and is shared across modules

---

# Core / Shared Tables

Tables in this section are owned by `hyvexa-core` and used across multiple modules.

## player_vexa
Stores the global vexa currency balance per player. Shared across all modules.

```sql
CREATE TABLE IF NOT EXISTS player_vexa (
  uuid VARCHAR(36) NOT NULL PRIMARY KEY,
  vexa BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
```

Notes:
- Auto-created by `VexaStore.initialize()` (extends `CachedCurrencyStore`) on startup
- Managed by `hyvexa-core/src/main/java/io/hyvexa/core/economy/VexaStore.java`
- Writes are immediate (no dirty tracking) since vexa is rare/precious
- Player cache is evicted on disconnect (lazy-loaded on next access)

## player_feathers
Parkour feather currency per player. Follows VexaStore pattern (lazy-load, immediate writes, evict on disconnect).

```sql
CREATE TABLE IF NOT EXISTS player_feathers (
  uuid VARCHAR(36) NOT NULL PRIMARY KEY,
  feathers BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
```

Manager: `FeatherStore` (singleton in `hyvexa-core`, extends `CachedCurrencyStore`)

## player_cosmetics
Stores purchased cosmetics and equipped state per player.

```sql
CREATE TABLE IF NOT EXISTS player_cosmetics (
  player_uuid VARCHAR(36) NOT NULL,
  cosmetic_id VARCHAR(64) NOT NULL,
  equipped BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (player_uuid, cosmetic_id)
) ENGINE=InnoDB;
```

Notes:
- Auto-created by `CosmeticStore.initialize()` on startup
- Managed by `hyvexa-core/src/main/java/io/hyvexa/core/cosmetic/CosmeticStore.java`
- At most one cosmetic can be `equipped = TRUE` per player at a time
- Writes are immediate (same pattern as VexaStore)
- Player cache is evicted on disconnect (lazy-loaded on next access)

## cosmetic_shop_config
Stores shop availability and pricing for wardrobe cosmetics.

```sql
CREATE TABLE IF NOT EXISTS cosmetic_shop_config (
  cosmetic_id VARCHAR(64) NOT NULL PRIMARY KEY,
  available BOOLEAN NOT NULL DEFAULT FALSE,
  price INT NOT NULL DEFAULT 0,
  currency VARCHAR(16) NOT NULL DEFAULT 'vexa'
) ENGINE=InnoDB;
```

Notes:
- Auto-created by `CosmeticShopConfigStore.initialize()` on startup
- Manager: `CosmeticShopConfigStore` (singleton in `hyvexa-core`)
- `currency` toggles between `'vexa'` and `'feathers'`

---

# Analytics Tables

Owned by `hyvexa-core`, used for cross-module gameplay analytics.

## analytics_events
Append-only event log for gameplay analytics. Purged after 90 days.

```sql
CREATE TABLE IF NOT EXISTS analytics_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  timestamp_ms BIGINT NOT NULL,
  player_uuid VARCHAR(36) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  data_json TEXT NULL,
  INDEX idx_timestamp (timestamp_ms),
  INDEX idx_player (player_uuid),
  INDEX idx_type_timestamp (event_type, timestamp_ms)
) ENGINE=InnoDB;
```

Notes:
- Auto-created by `AnalyticsStore.initialize()` on startup
- Event types: `player_join`, `player_leave`, `map_start`, `map_complete`, `level_up`, `duel_finish`, `mode_switch`, `gem_spend`, `discord_link`, `ascend_manual_run`, `ascend_elevation_up`, `ascend_summit_up`, `ascend_ascension`, `ascend_runner_buy`, `ascend_runner_evolve`, `ascend_challenge_start`, `ascend_challenge_complete`, `ascend_achievement`
- `data_json` contains event-specific payload (e.g., `{"map_id":"...", "time_ms":1234}`)
- Events older than 90 days are purged on startup

## analytics_daily
Pre-computed daily aggregate statistics. Kept indefinitely.

```sql
CREATE TABLE IF NOT EXISTS analytics_daily (
  date DATE NOT NULL PRIMARY KEY,
  dau INT NOT NULL,
  new_players INT NOT NULL,
  avg_session_ms BIGINT NOT NULL,
  total_sessions INT NOT NULL,
  parkour_time_pct FLOAT NOT NULL,
  ascend_time_pct FLOAT NOT NULL,
  peak_concurrent INT NOT NULL,
  data_json TEXT NULL
) ENGINE=InnoDB;
```

Notes:
- One row per day, computed by `AnalyticsStore.computeDailyAggregates()`
- Computed on server shutdown and on-demand via `/analytics refresh`
- `dau` = distinct players with `player_join` events that day
- `parkour_time_pct` / `ascend_time_pct` = percentage of `mode_switch` events to each mode

## /analytics Command (OP-only)

```
/analytics [days]          Overview: DAU, retention, sessions, mode split (default 7d)
/analytics parkour [days]  Map starts/completions/rate, PBs, first completions,
                           level ups, unique players, duels (by reason), top maps
/analytics ascend [days]   Manual runs, unique runners, elevations, summits,
                           ascensions, runners bought/evolved, achievements,
                           challenges (start/complete/rate), top summit categories
/analytics economy [days]  Vexa spent, purchases, unique buyers, discord links,
                           top purchased items
/analytics refresh         Recompute today's daily aggregates
/analytics purge           Purge events older than 90 days
```

Days range: 1-365, default 7 for all subcommands.

---

# Discord Linking Tables

Owned by `hyvexa-core`, shared between the plugin and the Discord bot.

## discord_link_codes
Stores temporary link codes generated in-game. Codes expire after 5 minutes.

```sql
CREATE TABLE IF NOT EXISTS discord_link_codes (
  code VARCHAR(7) NOT NULL PRIMARY KEY,
  player_uuid VARCHAR(36) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL
) ENGINE=InnoDB;
```

Notes:
- Codes are 6 alphanumeric characters, displayed as XXX-XXX (stored without dash)
- Expired codes are cleaned on startup and replaced when a player generates a new code
- Auto-created by `DiscordLinkStore.initialize()` on startup

## discord_links
Stores permanent Discord-Minecraft account links and vexa reward tracking.

```sql
CREATE TABLE IF NOT EXISTS discord_links (
  player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
  discord_id VARCHAR(20) NOT NULL UNIQUE,
  linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  vexa_rewarded BOOLEAN NOT NULL DEFAULT FALSE,
  current_rank VARCHAR(20) DEFAULT 'Unranked',
  last_synced_rank VARCHAR(20) DEFAULT NULL
) ENGINE=InnoDB;
```

Notes:
- One-to-one mapping: each game account links to exactly one Discord account and vice versa
- `vexa_rewarded` tracks whether the one-time 100 vexa reward has been given
- `current_rank` is the player's parkour rank, written by the plugin on rank-up and login
- `last_synced_rank` is the rank last synced to Discord by the bot; when it differs from `current_rank`, the bot knows to update roles
- The Discord bot writes to this table; the plugin reads it on player login
- Auto-created by `DiscordLinkStore.initialize()` on startup (columns added via ALTER TABLE migration for existing installs)
- Managed by `hyvexa-core/src/main/java/io/hyvexa/core/discord/DiscordLinkStore.java`

---

# Vote Tables

Owned by `hyvexa-core`.

## player_votes
Append-only log of every individual vote, for analytics and time-based leaderboards.

```sql
CREATE TABLE IF NOT EXISTS player_votes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  player_uuid VARCHAR(36) NOT NULL,
  player_name VARCHAR(32) NOT NULL,
  source VARCHAR(32) NOT NULL,
  voted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_player (player_uuid),
  INDEX idx_voted_at (voted_at),
  INDEX idx_player_voted (player_uuid, voted_at)
) ENGINE=InnoDB;
```

Notes:
- `source` values: `hytale.game` (polling), `votifier` (Votifier V1/V2)
- Used for time-period leaderboards (weekly, monthly) via `GROUP BY` + `COUNT(*)`
- Auto-created by `VoteStore.initialize()` on startup
- Managed by `hyvexa-core/src/main/java/io/hyvexa/core/vote/VoteStore.java`

## player_vote_counts
Denormalized aggregate vote counter per player for fast leaderboard reads.

```sql
CREATE TABLE IF NOT EXISTS player_vote_counts (
  player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
  player_name VARCHAR(32) NOT NULL,
  total_votes INT NOT NULL DEFAULT 0,
  last_voted_at TIMESTAMP NULL
) ENGINE=InnoDB;
```

Notes:
- Incremented atomically alongside each `player_votes` INSERT (same transaction)
- Avoids `COUNT(*)` on `player_votes` for all-time leaderboard queries
- `player_name` updated on each vote to keep current username
- Auto-created by `VoteStore.initialize()` on startup
- Managed by `hyvexa-core/src/main/java/io/hyvexa/core/vote/VoteStore.java`

---

# Parkour Tables

Tables owned by `hyvexa-parkour` for the core parkour gameplay mode.

## players
Stores player progression and profile state.

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
  jump_count BIGINT NOT NULL DEFAULT 0,
  first_join_ms BIGINT NULL,
  last_seen_ms BIGINT NULL
) ENGINE=InnoDB;
```

Notes:
- `teleport_item_use_count` tracks how many times the player has used the Map Selector teleport item (for showing hints during first 5 uses)
- `jump_count` tracks the total number of jumps the player has ever made (cumulative)
- `first_join_ms` / `last_seen_ms` added via ALTER TABLE migration for analytics

## maps
Stores map definitions and primary transforms.

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
  active BOOLEAN NOT NULL DEFAULT TRUE,
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
  bronze_time_ms BIGINT NULL,
  silver_time_ms BIGINT NULL,
  gold_time_ms BIGINT NULL,
  author_time_ms BIGINT NULL,
  created_at TIMESTAMP NULL,
  updated_at TIMESTAMP NULL
) ENGINE=InnoDB;
```

Notes:
- `bronze_time_ms`, `silver_time_ms`, `gold_time_ms`, `author_time_ms` -- optional medal time thresholds in milliseconds. Set via `/pk admin` Maps panel. Author < Gold < Silver < Bronze enforced by admin UI.

## map_checkpoints
Stores checkpoint transforms per map.

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

```sql
CREATE TABLE IF NOT EXISTS player_checkpoint_times (
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
- Auto-created by `DatabaseManager.createPlayerCheckpointTimesTable()` on startup.

## settings
Stores global server settings (single row, id = 1).

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

```sql
CREATE TABLE global_messages (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  message VARCHAR(240) NOT NULL,
  display_order INT NOT NULL
) ENGINE=InnoDB;
```

## global_message_settings
Stores the global message interval (single row, id = 1).

```sql
CREATE TABLE global_message_settings (
  id INT NOT NULL PRIMARY KEY,
  interval_minutes BIGINT NOT NULL
) ENGINE=InnoDB;
```

## player_count_samples
Stores player count analytics samples.

```sql
CREATE TABLE player_count_samples (
  timestamp_ms BIGINT NOT NULL PRIMARY KEY,
  count INT NOT NULL
) ENGINE=InnoDB;
```

## medal_rewards
Feather reward amounts per map category per medal tier.

```sql
CREATE TABLE IF NOT EXISTS medal_rewards (
  category VARCHAR(32) NOT NULL PRIMARY KEY,
  bronze_feathers INT NOT NULL DEFAULT 0,
  silver_feathers INT NOT NULL DEFAULT 0,
  gold_feathers INT NOT NULL DEFAULT 0,
  emerald_feathers INT NOT NULL DEFAULT 0,
  -- Migration column (added via ALTER TABLE):
  insane_feathers INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
```

Notes:
- Column was renamed from `author_feathers` to `emerald_feathers` via migration
- `insane_feathers` added via migration for the Insane difficulty category
- Manager: `MedalRewardStore` (singleton in `hyvexa-parkour`) -- loaded into memory on startup, edited via `/pk admin` -> Medal Rewards.

## player_medals
Tracks which medal tiers each player has earned per map. Medals are earned once per map per tier.

```sql
CREATE TABLE IF NOT EXISTS player_medals (
  player_uuid VARCHAR(36) NOT NULL,
  map_id VARCHAR(64) NOT NULL,
  medal VARCHAR(7) NOT NULL,
  earned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (player_uuid, map_id, medal)
) ENGINE=InnoDB;
```

- `medal` values: `BRONZE`, `SILVER`, `GOLD`, `EMERALD`, `INSANE`
- Manager: `MedalStore` (singleton in `hyvexa-parkour`) -- lazy-loads per player, evicts on disconnect.

## saved_run_state
Persists in-progress parkour run state so players can resume after disconnect or server restart.

```sql
CREATE TABLE IF NOT EXISTS saved_run_state (
    player_uuid         CHAR(36)    NOT NULL PRIMARY KEY,
    map_id              VARCHAR(64) NOT NULL,
    elapsed_ms          BIGINT      NOT NULL,
    last_checkpoint     INT         NOT NULL DEFAULT -1,
    touched_checkpoints TEXT        NOT NULL,
    checkpoint_times    TEXT        NOT NULL,
    map_updated_at      BIGINT      NOT NULL,
    saved_at            BIGINT      NOT NULL,
    CONSTRAINT fk_saved_run_map FOREIGN KEY (map_id) REFERENCES maps(id) ON DELETE CASCADE
) ENGINE=InnoDB
```

Notes:
- One row per player (only one active run possible at a time)
- `touched_checkpoints`: comma-separated checkpoint indices (e.g., "0,1,3")
- `checkpoint_times`: comma-separated index:time pairs (e.g., "0:1234,1:5678,3:12000")
- `map_updated_at`: snapshot of map's last modification time at save -- if map changed since save, run is discarded on restore
- `ON DELETE CASCADE` on `map_id` handles map deletion automatically
- Rows are deleted on run completion, abandoned after 30 days via cleanup
- Auto-created by `RunStateStore.ensureTable()` on startup
- Manager: `RunStateStore` in `hyvexa-parkour` -- no in-memory cache, loaded only on reconnect

## player_settings
Stores player-chosen settings (toggles, music, HUD, VIP speed) that persist across reconnections.

```sql
CREATE TABLE IF NOT EXISTS player_settings (
    player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
    reset_item_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    players_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    duel_hide_opponent BOOLEAN NOT NULL DEFAULT FALSE,
    ghost_visible BOOLEAN NOT NULL DEFAULT TRUE,
    advanced_hud_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    hud_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    music_label VARCHAR(32) DEFAULT NULL,
    checkpoint_sfx_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    victory_sfx_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    vip_speed_multiplier FLOAT NOT NULL DEFAULT 1.0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;
```

Notes:
- Auto-created by `PlayerSettingsPersistence.ensureTable()` on startup
- Manager: `PlayerSettingsPersistence` (singleton in `hyvexa-parkour`)
- Immediate write on every toggle change (settings change rarely)
- Loaded on `PlayerReadyEvent`, in-memory maps cleared on disconnect (DB rows persist)
- `music_label` stores the display label (e.g., "Zelda OST", "Celeste OST", "No Music"); NULL = default
- `vip_speed_multiplier` only applied if player has VIP/Founder status

---

# Duel Tables

Owned by `hyvexa-parkour` for the duel minigame subsystem.

## duel_category_prefs
Stores per-player duel category preferences.

```sql
CREATE TABLE IF NOT EXISTS duel_category_prefs (
  player_uuid VARCHAR(36) PRIMARY KEY,
  easy_enabled BOOLEAN DEFAULT TRUE,
  medium_enabled BOOLEAN DEFAULT TRUE,
  hard_enabled BOOLEAN DEFAULT FALSE,
  insane_enabled BOOLEAN DEFAULT FALSE,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

Manager: `DuelPreferenceStore` (in `hyvexa-parkour`)

## duel_matches
Stores completed duel match history.

```sql
CREATE TABLE IF NOT EXISTS duel_matches (
  id VARCHAR(36) PRIMARY KEY,
  player1_uuid VARCHAR(36) NOT NULL,
  player2_uuid VARCHAR(36) NOT NULL,
  map_id VARCHAR(64) NOT NULL,
  winner_uuid VARCHAR(36),
  player1_time_ms BIGINT,
  player2_time_ms BIGINT,
  finish_reason VARCHAR(20),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Manager: `DuelMatchStore` (in `hyvexa-parkour`)

## duel_player_stats
Stores duel win/loss statistics per player.

```sql
CREATE TABLE IF NOT EXISTS duel_player_stats (
  player_uuid VARCHAR(36) PRIMARY KEY,
  player_name VARCHAR(64),
  wins INT DEFAULT 0,
  losses INT DEFAULT 0,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

Manager: `DuelStatsStore` (in `hyvexa-parkour`)

## parkour_ghost_recordings
Stores ghost recordings for parkour mode (personal best movement paths).

```sql
CREATE TABLE IF NOT EXISTS parkour_ghost_recordings (
  player_uuid VARCHAR(36) NOT NULL,
  map_id VARCHAR(32) NOT NULL,
  recording_blob MEDIUMBLOB NOT NULL,
  completion_time_ms BIGINT NOT NULL,
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (player_uuid, map_id)
) ENGINE=InnoDB;
```

Notes:
- Same schema as `ascend_ghost_recordings` but for parkour mode
- Created by `GhostStore("parkour_ghost_recordings", "parkour")` in `HyvexaPlugin`
- See `ascend_ghost_recordings` notes for blob format details

## saved_run_state
Persists in-progress run state so players can resume after disconnect or server restart.

```sql
CREATE TABLE IF NOT EXISTS saved_run_state (
  player_uuid CHAR(36) NOT NULL PRIMARY KEY,
  map_id VARCHAR(64) NOT NULL,
  elapsed_ms BIGINT NOT NULL,
  last_checkpoint INT NOT NULL DEFAULT -1,
  touched_checkpoints TEXT NOT NULL,
  checkpoint_times TEXT NOT NULL,
  map_updated_at BIGINT NOT NULL,
  saved_at BIGINT NOT NULL,
  CONSTRAINT fk_saved_run_map FOREIGN KEY (map_id) REFERENCES maps(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- One row per player (replaced on each save via `REPLACE INTO`)
- `touched_checkpoints` and `checkpoint_times` are comma-separated encoded strings
- `map_updated_at` used to invalidate saved state if map was edited since save
- Manager: `RunStateStore` (in `hyvexa-parkour`) -- no in-memory cache, loaded only on reconnect

## player_settings
Persists player settings (toggles, music, HUD, speed) across reconnections.

```sql
CREATE TABLE IF NOT EXISTS player_settings (
  player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
  reset_item_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  players_hidden BOOLEAN NOT NULL DEFAULT FALSE,
  duel_hide_opponent BOOLEAN NOT NULL DEFAULT FALSE,
  ghost_visible BOOLEAN NOT NULL DEFAULT TRUE,
  advanced_hud_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  hud_hidden BOOLEAN NOT NULL DEFAULT FALSE,
  music_label VARCHAR(32) DEFAULT NULL,
  checkpoint_sfx_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  victory_sfx_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  vip_speed_multiplier FLOAT NOT NULL DEFAULT 1.0,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;
```

Notes:
- Immediate write on change, lazy load per player (DuelPreferenceStore pattern)
- Manager: `PlayerSettingsPersistence` (in `hyvexa-parkour`)

---

# Ascend Tables

Tables owned by `hyvexa-parkour-ascend` for the Ascend idle mode.

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

# Ascend Mine Tables

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
  spawn_rot_z FLOAT NOT NULL DEFAULT 0
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
  id VARCHAR(64) NOT NULL,
  zone_id VARCHAR(32) NOT NULL,
  min_y INT NOT NULL,
  max_y INT NOT NULL,
  block_table_json TEXT NOT NULL DEFAULT '{}',
  block_hp_json TEXT NOT NULL DEFAULT '{}',
  PRIMARY KEY (id),
  FOREIGN KEY (zone_id) REFERENCES mine_zones(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- Each layer overrides the zone's `block_table_json` and `block_hp_json` for its Y range
- Layers must not overlap in Y range within the same zone
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
  crystals BIGINT NOT NULL DEFAULT 0,
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
  in_mine TINYINT(1) NOT NULL DEFAULT 0,
  pickaxe_tier INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- `crystals` is the mine-specific currency (migrated from BigNumber mantissa+exp10 to plain BIGINT)
- Upgrade level columns added via `ensureMineUpgradeColumns()` migration
- `upgrade_momentum` through `upgrade_haste` are the 6 active upgrade types (see `MineUpgradeType` enum)
- `mining_speed_level` and `multi_break_level` are deprecated legacy columns (no longer read by code)
- `pickaxe_tier` tracks the player's current pickaxe tier
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
  price_mantissa DOUBLE NOT NULL DEFAULT 1,
  price_exp10 INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
```

Notes:
- Price stored as BigNumber pair (`price_mantissa` x 10^`price_exp10`)
- Default price is 1 crystal per block if no row exists
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
  PRIMARY KEY (player_uuid, mine_id),
  FOREIGN KEY (player_uuid) REFERENCES mine_players(uuid) ON DELETE CASCADE,
  FOREIGN KEY (mine_id) REFERENCES mine_definitions(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```

Notes:
- `stars` tracks evolution level (similar to Ascend runners)
- `speed_level` is the miner's speed upgrade level
- Manager: `MinePlayerStore` (in `hyvexa-parkour-ascend`)

---

# Purge Tables

Tables owned by `hyvexa-purge` for the zombie survival PvE mode.

## purge_player_stats
Stores per-player Purge mode statistics.

```sql
CREATE TABLE IF NOT EXISTS purge_player_stats (
  uuid VARCHAR(36) NOT NULL PRIMARY KEY,
  best_wave INT NOT NULL DEFAULT 0,
  total_kills INT NOT NULL DEFAULT 0,
  total_sessions INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
```

Manager: `PurgePlayerStore` (in `hyvexa-purge`)

## purge_player_scrap
Stores per-player scrap currency for Purge mode.

```sql
CREATE TABLE IF NOT EXISTS purge_player_scrap (
  uuid VARCHAR(36) NOT NULL PRIMARY KEY,
  scrap BIGINT NOT NULL DEFAULT 0,
  lifetime_scrap_earned BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
```

Manager: `PurgeScrapStore` (in `hyvexa-purge`)

## purge_weapon_upgrades
Stores per-player weapon upgrade levels in Purge mode.

```sql
CREATE TABLE IF NOT EXISTS purge_weapon_upgrades (
  uuid VARCHAR(36) NOT NULL,
  weapon_id VARCHAR(32) NOT NULL,
  level INT NOT NULL DEFAULT 0,
  PRIMARY KEY (uuid, weapon_id)
) ENGINE=InnoDB;
```

Manager: `PurgeWeaponUpgradeStore` (in `hyvexa-purge`)

## purge_weapon_xp
Stores per-player weapon XP and level progression.

```sql
CREATE TABLE IF NOT EXISTS purge_weapon_xp (
  uuid VARCHAR(36) NOT NULL,
  weapon_id VARCHAR(32) NOT NULL,
  xp INT NOT NULL DEFAULT 0,
  level INT NOT NULL DEFAULT 0,
  PRIMARY KEY (uuid, weapon_id)
) ENGINE=InnoDB;
```

Notes:
- XP increments on every kill; persisted asynchronously to avoid blocking the world thread
- Auto-created by `WeaponXpStore.initialize()` on startup
- Manager: `WeaponXpStore` (singleton in `hyvexa-purge`)

## purge_daily_missions
Stores daily mission progress per player per day.

```sql
CREATE TABLE IF NOT EXISTS purge_daily_missions (
  uuid VARCHAR(36) NOT NULL,
  mission_date DATE NOT NULL,
  total_kills INT NOT NULL DEFAULT 0,
  best_wave INT NOT NULL DEFAULT 0,
  best_combo INT NOT NULL DEFAULT 0,
  claimed_wave TINYINT NOT NULL DEFAULT 0,
  claimed_combo TINYINT NOT NULL DEFAULT 0,
  claimed_kill TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (uuid, mission_date)
) ENGINE=InnoDB;
```

Notes:
- One row per player per day; stale rows are ignored (fresh day = fresh progress)
- `claimed_*` columns track whether the player has collected each mission reward
- Auto-created by `PurgeMissionStore.initialize()` on startup
- Manager: `PurgeMissionStore` (singleton in `hyvexa-purge`)

## purge_player_classes
Stores unlocked combat classes per player.

```sql
CREATE TABLE IF NOT EXISTS purge_player_classes (
  uuid VARCHAR(36) NOT NULL,
  class_id VARCHAR(32) NOT NULL,
  PRIMARY KEY (uuid, class_id)
) ENGINE=InnoDB;
```

Notes:
- `class_id` is the enum name from `PurgeClass`
- Auto-created by `PurgeClassStore.initialize()` on startup
- Manager: `PurgeClassStore` (singleton in `hyvexa-purge`)

## purge_player_selected_class
Stores the currently selected combat class per player.

```sql
CREATE TABLE IF NOT EXISTS purge_player_selected_class (
  uuid VARCHAR(36) NOT NULL PRIMARY KEY,
  selected_class VARCHAR(32) DEFAULT NULL
) ENGINE=InnoDB;
```

Notes:
- `selected_class` is NULL if no class is selected
- Auto-created by `PurgeClassStore.initialize()` on startup
- Manager: `PurgeClassStore` (singleton in `hyvexa-purge`)

## purge_weapon_levels
Stores configurable weapon level stats (damage, cost per level).

```sql
CREATE TABLE IF NOT EXISTS purge_weapon_levels (
  weapon_id VARCHAR(32) NOT NULL,
  level INT NOT NULL,
  damage INT NOT NULL,
  cost BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (weapon_id, level)
) ENGINE=InnoDB;
```

Manager: `PurgeWeaponConfigManager` (in `hyvexa-purge`)

## purge_weapon_defaults
Stores weapon unlock configuration defaults.

```sql
CREATE TABLE IF NOT EXISTS purge_weapon_defaults (
  weapon_id VARCHAR(32) NOT NULL PRIMARY KEY,
  default_unlocked BOOLEAN NOT NULL DEFAULT FALSE,
  unlock_cost BIGINT NOT NULL DEFAULT 500,
  session_weapon BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;
```

Manager: `PurgeWeaponConfigManager` (in `hyvexa-purge`)

## purge_weapon_skins
Stores per-player weapon skin ownership and selection in Purge mode.

```sql
CREATE TABLE IF NOT EXISTS purge_weapon_skins (
  uuid VARCHAR(36) NOT NULL,
  weapon_id VARCHAR(32) NOT NULL,
  skin_id VARCHAR(64) NOT NULL,
  selected BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (uuid, weapon_id, skin_id)
) ENGINE=InnoDB;
```

Manager: `PurgeSkinStore` (singleton in `hyvexa-core`)

## purge_zombie_variants
Stores configurable zombie variant types for Purge mode. Seeded with SLOW/NORMAL/FAST on first load.

```sql
CREATE TABLE IF NOT EXISTS purge_zombie_variants (
  variant_key VARCHAR(32) NOT NULL PRIMARY KEY,
  label VARCHAR(64) NOT NULL,
  base_health INT NOT NULL DEFAULT 50,
  base_damage FLOAT NOT NULL DEFAULT 20,
  speed_multiplier DOUBLE NOT NULL DEFAULT 1.0,
  npc_type VARCHAR(64) NOT NULL DEFAULT 'Zombie',
  scrap_reward INT NOT NULL DEFAULT 10
) ENGINE=InnoDB;
```

Manager: `PurgeVariantConfigManager` -- in-memory cache, immediate DB writes.

## purge_wave_variant_counts
Join table linking waves to variant spawn counts (replaces old `slow_count`/`normal_count`/`fast_count` columns on `purge_waves`).

```sql
CREATE TABLE IF NOT EXISTS purge_wave_variant_counts (
  wave_number INT NOT NULL,
  variant_key VARCHAR(32) NOT NULL,
  count INT NOT NULL DEFAULT 0,
  PRIMARY KEY (wave_number, variant_key)
) ENGINE=InnoDB;
```

Manager: `PurgeWaveConfigManager` -- migrates from old columns on startup if they exist.

## purge_waves
Stores wave configuration for Purge mode.

```sql
CREATE TABLE IF NOT EXISTS purge_waves (
  wave_number INT NOT NULL PRIMARY KEY,
  spawn_delay_ms INT NOT NULL DEFAULT 500,
  spawn_batch_size INT NOT NULL DEFAULT 5
) ENGINE=InnoDB;
```

Manager: `PurgeWaveConfigManager` (in `hyvexa-purge`)

## purge_settings
Stores key-value settings for Purge mode.

```sql
CREATE TABLE IF NOT EXISTS purge_settings (
  setting_key VARCHAR(64) NOT NULL PRIMARY KEY,
  setting_value VARCHAR(255) NOT NULL
) ENGINE=InnoDB;
```

Manager: `PurgeWeaponConfigManager` (in `hyvexa-purge`)

## purge_migrations
Tracks applied migration keys for Purge mode.

```sql
CREATE TABLE IF NOT EXISTS purge_migrations (
  migration_key VARCHAR(64) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
```

Manager: `PurgeWeaponUpgradeStore` (in `hyvexa-purge`)

---

# RunOrFall Tables

Tables owned by `hyvexa-runorfall` for the platforming minigame mode.

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

---

# Votifier Tables (SQLite -- separate from shared MySQL)

The `hyvexa-votifier` module uses a **local SQLite database** (not the shared MySQL).
This table is documented for completeness but lives in a separate file on disk.

## player_votes (SQLite)
Tracks the last vote timestamp per username for duplicate-vote prevention.

```sql
CREATE TABLE IF NOT EXISTS player_votes (
  username TEXT PRIMARY KEY NOT NULL,
  last_vote_timestamp INTEGER NOT NULL
);
-- Additional index:
CREATE INDEX IF NOT EXISTS idx_last_vote ON player_votes (last_vote_timestamp);
```

Notes:
- This is a **SQLite** table, not MySQL. Managed by `SQLiteVoteStorage` in `hyvexa-votifier`.
- Uses SQLite WAL mode for concurrent performance.
- Expired votes are cleaned via `cleanupExpiredVotes()`.
- **Not to be confused** with the MySQL `player_votes` table in `hyvexa-core` (VoteStore) which logs all votes for leaderboards.

---

# Notes

- Foreign keys are not required by the current code on all tables, but some (Ascend, Mine) use them explicitly.
- Most tables are auto-created on startup by their respective Store or DatabaseSetup class.
- Some parkour tables (`players`, `maps`, `map_checkpoints`, `player_completions`, `settings`, `global_messages`, `global_message_settings`, `player_count_samples`) are not created programmatically by the plugin -- they are created manually or by an external migration script.
- The Hub module (`hyvexa-hub`) and Wardrobe module (`hyvexa-wardrobe`) do not own any database tables. Hub routing is runtime-only; wardrobe cosmetics use the shared `player_cosmetics` and `cosmetic_shop_config` tables from `hyvexa-core`.
