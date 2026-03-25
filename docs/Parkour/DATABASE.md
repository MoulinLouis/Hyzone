<!-- Last verified against code: 2026-03-25 -->
# Parkour Database Schema

Tables owned by `hyvexa-parkour` for the core parkour gameplay mode.
All use the shared MySQL database via `DatabaseManager`.

---

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
  emerald_time_ms BIGINT NULL,
  created_at TIMESTAMP NULL,
  updated_at TIMESTAMP NULL
) ENGINE=InnoDB;
```

Notes:
- `bronze_time_ms`, `silver_time_ms`, `gold_time_ms`, `emerald_time_ms` -- optional medal time thresholds in milliseconds. Set via `/pk admin` Maps panel. Emerald < Gold < Silver < Bronze enforced by admin UI. Column was renamed from `author_time_ms` to `emerald_time_ms` via migration.

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
- Manager: `MedalRewardStore` (wired by `HyvexaPlugin` in `hyvexa-parkour`) -- loaded into memory on startup, edited via `/pk admin` -> Medal Rewards.

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
- Manager: `MedalStore` (wired by `HyvexaPlugin` in `hyvexa-parkour`) -- lazy-loads per player, evicts on disconnect.

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

## parkour_migrations
Tracks applied migration keys for Parkour mode.

```sql
CREATE TABLE IF NOT EXISTS parkour_migrations (
  migration_key VARCHAR(64) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
```

Manager: `ParkourDatabaseSetup` (in `hyvexa-parkour`)

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
- See [Ascend DATABASE.md](../Ascend/DATABASE.md#ascend_ghost_recordings) for blob format details

---

# Notes

- Some parkour tables (`players`, `maps`, `map_checkpoints`, `player_completions`, `settings`, `global_messages`, `global_message_settings`, `player_count_samples`) are not created programmatically by the plugin -- they are created manually or by an external migration script.
