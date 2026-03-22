# Core Module

Quick reference for `hyvexa-core`.

## Scope
- Shared library module with no plugin entry point.
- Provides database connectivity, currency stores, cosmetics, analytics, discord linking, ghost recording, voting, shop infrastructure, UI utilities, and cross-module bridges.
- All other modules depend on `hyvexa-core`.

## Key Subsystems

### Database (`core/db/`)
- `DatabaseManager` -- singleton HikariCP connection pool to MySQL. Config loaded from `mods/Parkour/database.json`. First module to call `initialize()` creates the pool; subsequent calls are no-ops.
- `ConnectionProvider` -- minimal interface for classes that need pooled SQL connections without depending on the concrete singleton.
- `DatabaseConfig` -- reads JSON config.
- `DatabaseRetry` -- retry helper for transient failures.
- Helper methods: `columnExists`, `addColumnIfMissing`, `renameColumnIfExists`, `applyQueryTimeout`, `logSlowQuery`.

### Economy (`core/economy/`)
- `CurrencyStore` -- shared contract for persistent player currencies.
- `CachedCurrencyStore` -- abstract base with in-memory cache, TTL (30 min), async refresh, and immediate writes.
- `VexaStore` -- primary currency (vexa). Table: `player_vexa`.
- `FeatherStore` -- secondary currency (feathers, earned via voting, medal completions, and RunOrFall rewards). Table: `player_feathers`.
- `CurrencyBridge` -- lightweight registry so cross-module code (e.g. wardrobe) can query/deduct any currency without direct dependencies.

### Cosmetics (`core/cosmetic/`)
- `CosmeticStore` -- player cosmetic ownership and equip state. Table: `player_cosmetics`.
- `CosmeticDefinition` -- defines a cosmetic's ID, display name, price, kind (TRAIL vs other).
- `CosmeticManager` -- manages trail/effect activation for equipped cosmetics.

### Discord Linking (`core/discord/`)
- `DiscordLinkStore` -- generates link codes, validates links, awards one-time vexa reward. Tables: `discord_link_codes`, `discord_links`.
- Shared between the Java plugin and the external Discord bot (see `docs/DiscordBot/README.md`).

### Analytics (`core/analytics/`)
- `PlayerAnalytics` -- write-side analytics interface for gameplay code.
- `AnalyticsStore` -- fire-and-forget event logging, daily aggregate computation, retention analysis. Tables: `analytics_events`, `analytics_daily`.
- Also adds `first_join_ms` and `last_seen_ms` columns to the `players` table.

### Voting (`core/vote/`)
- `VoteStore` -- records individual votes and maintains denormalized counts. Tables: `player_votes`, `player_vote_counts`.
- `VoteManager` -- polls an external vote API, claims unclaimed votes, awards feathers, broadcasts vote messages. Uses dedicated IO and reward thread pools.
- `VoteConfig` -- holds API base URL, secret key, reward-per-vote, poll interval.

### Ghost Recordings (`common/ghost/`)
- `GhostStore` -- MySQL-backed compressed ghost recording storage (GZIP binary blobs). Table name is configurable per mode instance.
- `AbstractGhostRecorder`, `GhostRecording`, `GhostSample`, `GhostInterpolation` -- recording/playback infrastructure used by parkour ghosts.

### Wardrobe Bridge (`core/wardrobe/`)
- `WardrobeBridge` -- maps wardrobe cosmetics to permission nodes, handles purchases (atomic deduct + insert), and re-grants permissions on login. Table: `player_cosmetics` (shared with CosmeticStore).
- `CosmeticShopConfigStore` -- admin-editable availability and pricing for wardrobe items. Table: `cosmetic_shop_config`.

### Purge Skins (`common/skin/`)
- `PurgeSkinStore` -- weapon skin ownership/selection for Purge mode. Table: `purge_weapon_skins`.
- `PurgeSkinRegistry`, `PurgeSkinDefinition` -- in-code skin catalog.
- `DailyShopRotation` -- rotating daily shop selection.

### Shop Framework (`common/shop/`)
- `ShopTabRegistry` -- registers shop tabs from different modules.
- `ShopTab`, `ShopTabResult` -- tab rendering abstraction.
- `ShopItemInteraction` -- item interaction for opening the shop.

### Queue (`core/queue/`)
- `RunOrFallQueueStore` -- cross-module queue so players can join RunOrFall from other worlds.
- `RunOrFallQueueCommand` -- `/rofqueue` command registered in Hub.

### Tebex (`core/tebex/`)
- `TebexConfig` -- configuration for Tebex store integration.

### Trail System (`core/trail/`)
- `TrailManager`, `AbstractTrailManager`, `ModelParticleTrailManager` -- per-player cosmetic trail spawning.

### Utility Classes (`common/util/`)
- `ModeGate` -- world-name checks to gate commands/features to the correct mode.
- `WorldConstants` -- canonical world name constants (`Hub`, `Parkour`, `Ascend`, `Purge`, `RunOrFall`).
- `MultiHudBridge` -- integrates with MultipleHUD plugin so multiple plugins' HUDs coexist.
- `InventoryUtils` -- hotbar/container helpers.
- `FormatUtils` -- number/time formatting.
- `CommandUtils` -- command argument helpers.
- `PermissionUtils` -- OP checks.
- `PlayerUtils` -- player lookup helpers.
- `PlayerCleanupHelper` -- handles player disconnect cleanup.
- `OrphanedEntityCleanup` -- removes orphaned entities on a schedule.
- `AsyncExecutionHelper` -- world-thread async execution wrapper.
- `StoreInitializer` -- safe sequential store initialization.
- `SystemMessageUtils` -- system message formatting.
- `DailyResetUtils` -- daily reset timestamp helpers.
- `DamageBypassRegistry` -- tracks entities that should bypass damage protection.
- `AssetPathUtils` -- resolves asset file paths.
- `HylogramsBridge` -- bridge to Hylograms hologram plugin.

### Visibility (`common/visibility/`)
- `EntityVisibilityManager`, `EntityVisibilityFilterSystem` -- per-player entity visibility filtering.

### Whitelist (`common/whitelist/`)
- `AscendWhitelistManager` -- JSON-based whitelist for Ascend mode access control.
- `WhitelistRegistry` -- cross-classloader singleton registry.

### UI Utilities (`common/ui/`)
- `ButtonEventData` -- standard codec for button events in custom UI pages.
- `PaginationState` -- pagination logic for paged UI lists.

### Commands (`common/command/`)
- `HelloCommand` -- diagnostic command.
- `PingCommand` -- latency check command.

## Owned Database Tables

| Table | Owner |
|-------|-------|
| `player_vexa` | VexaStore |
| `player_feathers` | FeatherStore |
| `player_cosmetics` | CosmeticStore |
| `cosmetic_shop_config` | CosmeticShopConfigStore |
| `discord_link_codes` | DiscordLinkStore |
| `discord_links` | DiscordLinkStore |
| `analytics_events` | AnalyticsStore |
| `analytics_daily` | AnalyticsStore |
| `player_votes` | VoteStore |
| `player_vote_counts` | VoteStore |
| `purge_weapon_skins` | PurgeSkinStore |
| `ghost_recordings` (configurable name) | GhostStore |

## Key Source Paths
- `hyvexa-core/src/main/java/io/hyvexa/core/` -- stores, managers, bridges
- `hyvexa-core/src/main/java/io/hyvexa/common/` -- shared utilities, ghost system, UI, skin system
- `hyvexa-core/src/test/java/` -- unit tests for pure-logic classes

## Related Docs
- Database schema: `docs/DATABASE.md`
- Architecture overview: `docs/ARCHITECTURE.md`
- Wardrobe details: `docs/WARDROBE_MOD.md`
- Discord bot integration: `docs/DiscordBot/README.md`
