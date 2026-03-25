<!-- Last verified against code: 2026-03-25 -->
# Purge Database Schema

Tables owned by `hyvexa-purge` for the zombie survival PvE mode.
All use the shared MySQL database via `DatabaseManager`.

---

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
