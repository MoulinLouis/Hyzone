# Design Spec: Consolidate Ad-Hoc Column Migrations (TECH_DEBT 2.5)

**Date:** 2026-03-13
**Modules:** parkour, runorfall
**Pattern:** Follow PurgeDatabaseSetup (per-module migrations table)

## Problem

19 calls to `DatabaseManager.addColumnIfMissing()` scattered across `MapStore` (4), `MedalRewardStore` (2), and `RunOrFallConfigStore` (13). Each call checks `columnExists()` on every server startup with no tracking or audit trail.

## Solution

Create `ParkourDatabaseSetup` and `RunOrFallDatabaseSetup` following the `PurgeDatabaseSetup` pattern. Each gets a migrations tracking table and named migration methods. Remove all ad-hoc `addColumnIfMissing()`/`renameColumnIfExists()` calls from the individual stores.

## New Files

### ParkourDatabaseSetup

**Path:** `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/ParkourDatabaseSetup.java`

**Structure:**
```java
public final class ParkourDatabaseSetup {
    public static void ensureTables() {
        // 1. Check DB initialized
        // 2. Get connection
        // 3. CREATE TABLE IF NOT EXISTS parkour_migrations (migration_key VARCHAR(64) NOT NULL PRIMARY KEY) ENGINE=InnoDB
        // 4. Run migrations in order:
        migrateMapMedalTimes(conn);       // "map_medal_times_v1"
        migrateMedalRewardFeathers(conn); // "medal_reward_feathers_v1"
    }
}
```

**Migration: `migrateMapMedalTimes`** (key: `map_medal_times_v1`)
- Checks `parkour_migrations` for key, returns if present
- Adds `bronze_time_ms BIGINT DEFAULT NULL` to `maps`
- Adds `silver_time_ms BIGINT DEFAULT NULL` to `maps`
- Adds `gold_time_ms BIGINT DEFAULT NULL` to `maps`
- Renames `author_time_ms` → `emerald_time_ms` via `DatabaseManager.renameColumnIfExists()`
- Adds `emerald_time_ms BIGINT DEFAULT NULL` to `maps` (fallback if rename source didn't exist)
- Records migration key

**Migration: `migrateMedalRewardFeathers`** (key: `medal_reward_feathers_v1`)
- Checks `parkour_migrations` for key, returns if present
- Renames `author_feathers` → `emerald_feathers` via `DatabaseManager.renameColumnIfExists()`
- Renames `platinum_feathers` → `emerald_feathers` via `DatabaseManager.renameColumnIfExists()`
- Adds `emerald_feathers INT NOT NULL DEFAULT 0` to `medal_rewards` (fallback)
- Adds `insane_feathers INT NOT NULL DEFAULT 0` to `medal_rewards`
- Records migration key

### RunOrFallDatabaseSetup

**Path:** `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/data/RunOrFallDatabaseSetup.java`

**Structure:**
```java
public final class RunOrFallDatabaseSetup {
    public static void ensureTables() {
        // 1. Check DB initialized
        // 2. Get connection
        // 3. CREATE TABLE IF NOT EXISTS runorfall_migrations (migration_key VARCHAR(64) NOT NULL PRIMARY KEY) ENGINE=InnoDB
        // 4. Run migrations in order:
        migrateSettingsColumns(conn);  // "settings_columns_v1"
        migrateMapColumns(conn);       // "map_columns_v1"
        migratePlatformColumns(conn);  // "platform_columns_v1"
    }
}
```

**Migration: `migrateSettingsColumns`** (key: `settings_columns_v1`)
- Adds 11 columns to `runorfall_settings`:
  - `active_map_id VARCHAR(64) NULL`
  - `min_players INT NOT NULL DEFAULT 2`
  - `min_players_time_seconds INT NOT NULL DEFAULT 300`
  - `optimal_players INT NOT NULL DEFAULT 4`
  - `optimal_players_time_seconds INT NOT NULL DEFAULT 60`
  - `blink_distance_blocks INT NOT NULL DEFAULT 7`
  - `blink_start_charges INT NOT NULL DEFAULT 1`
  - `blink_charge_every_blocks_broken INT NOT NULL DEFAULT 100`
  - `feathers_per_minute_alive INT NOT NULL DEFAULT 1`
  - `feathers_per_player_eliminated INT NOT NULL DEFAULT 5`
  - `feathers_for_win INT NOT NULL DEFAULT 25`

**Migration: `migrateMapColumns`** (key: `map_columns_v1`)
- Adds `min_players INT NOT NULL DEFAULT 2` to `runorfall_maps`

**Migration: `migratePlatformColumns`** (key: `platform_columns_v1`)
- Adds `target_block_item_id VARCHAR(128) NULL` to `runorfall_map_platforms`

## Modified Files

### MapStore.java
**Remove from `syncLoad()` (lines ~67-77):**
- 4 `DatabaseManager.addColumnIfMissing()` calls
- 1 `DatabaseManager.renameColumnIfExists()` call

### MedalRewardStore.java
**Remove from `initialize()` (lines ~42-49):**
- 2 `DatabaseManager.addColumnIfMissing()` calls
- 2 `DatabaseManager.renameColumnIfExists()` calls

### RunOrFallConfigStore.java
**Remove from `initializeTables()` (lines ~434-453):**
- 13 `DatabaseManager.addColumnIfMissing()` calls

### HyvexaPlugin.java (parkour entry point)
**Add** call to `ParkourDatabaseSetup.ensureTables()` in `onEnable()`, before store initialization.

### HyvexaRunOrFallPlugin.java (runorfall entry point)
**Add** call to `RunOrFallDatabaseSetup.ensureTables()` in `onEnable()`, before `RunOrFallConfigStore.initializeTables()`.

## Migration Idempotency

Each migration method follows the Purge pattern:
1. `SELECT 1 FROM xxx_migrations WHERE migration_key = ?` — skip if present
2. Use `DatabaseManager.addColumnIfMissing()` internally for each column (still safe if column already exists)
3. `INSERT INTO xxx_migrations (migration_key) VALUES (?)` — record completion

This double-safety (migration key check + columnExists check) means existing databases with columns already added will simply record the migration key and skip on subsequent startups.

## What Stays Unchanged

- `DatabaseManager.addColumnIfMissing()` and `renameColumnIfExists()` utility methods remain (used inside migration methods and potentially elsewhere)
- `AscendDatabaseSetup` and `PurgeDatabaseSetup` are not modified
- No schema changes — all columns are identical to what the ad-hoc calls were adding
