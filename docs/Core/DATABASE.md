<!-- Last verified against code: 2026-03-25 -->
# Core Database Schema

Tables owned by `hyvexa-core`, shared across multiple modules.
All use the shared MySQL database via `DatabaseManager`.

---

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
