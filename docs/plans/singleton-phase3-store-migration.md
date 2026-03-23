# Phase 3 — Store `DatabaseManager` Migration

Parent plan: [singleton-decoupling.md](singleton-decoupling.md)

## Goal

Inject `ConnectionProvider` into every store, eliminating all `DatabaseManager.getInstance()` calls from business logic. After this phase, `DatabaseManager.getInstance()` should only appear in composition roots (plugin `setup()` methods) and `DatabaseManager` itself.

## Status — Updated 2026-03-23

**COMPLETE.** All stores have been migrated. Every store class uses a `ConnectionProvider` field for database access. `DatabaseManager.getInstance()` only remains in:
- No-arg / singleton private constructors (backwards-compat, Phase 5 cleanup target)
- Plugin `setup()` methods (composition roots — target state)
- `*DatabaseSetup` schema init classes (acceptable)
- Database admin commands (acceptable)

**Already migrated (all stores):**
- `BasePlayerStore` — accepts injected `ConnectionProvider`, backwards-compatible no-arg constructor
- `RunOrFallStatsStore` — injected from `HyvexaRunOrFallPlugin`
- `VoteStore` — injected from `HyvexaPlugin`
- `MedalStore` — injected from `HyvexaPlugin`
- `MedalRewardStore` — injected from `HyvexaPlugin`

## Migration Order

Ordered by risk (lowest first) and grouped by module. Each group is an independent unit of work.

### Group A — Small isolated stores (low risk)

| Store | Calls | Module | Composition root |
|-------|-------|--------|-----------------|
| `GhostStore` | 5 | core | `HyvexaPlugin.setup()` / `ParkourAscendPlugin.setup()` |
| `DuelPreferenceStore` | 5 | parkour | `HyvexaPlugin.setup()` |
| `DuelMatchStore` | 4 | parkour | `HyvexaPlugin.setup()` |
| `DuelStatsStore` | 2 | parkour | `HyvexaPlugin.setup()` |
| `PlayerCountStore` | 8 | parkour | `HyvexaPlugin.setup()` |
| `RunStateStore` | 5 | parkour | `HyvexaPlugin.setup()` |
| `SettingsStore` | 5 | parkour | `HyvexaPlugin.setup()` |
| `PlayerSettingsPersistence` | 6 | parkour | `HyvexaPlugin.setup()` |
| `GlobalMessageStore` | 11 | parkour | `HyvexaPlugin.setup()` |

### Group B — Parkour core stores (medium risk)

| Store | Calls | Module | Composition root |
|-------|-------|--------|-----------------|
| `ProgressStore` | 14 | parkour | `HyvexaPlugin.setup()` |
| `MapStore` | 6 | parkour | `HyvexaPlugin.setup()` |

### Group C — Core shared stores (medium risk, cross-module)

| Store | Calls | Module | Injected from |
|-------|-------|--------|--------------|
| `CachedCurrencyStore` / `VexaStore` | 6 | core | Each plugin's `setup()` |
| `FeatherStore` | (via CachedCurrencyStore) | core | Each plugin's `setup()` |
| `CosmeticStore` | 10 | core | Each plugin's `setup()` |
| `CosmeticShopConfigStore` | 6 | core | `WardrobePlugin.setup()` / `HyvexaPlugin.setup()` |
| `DiscordLinkStore` | 19 | core | Each plugin's `setup()` |
| `PurgeSkinStore` | 12 | core | `HyvexaPurgePlugin.setup()` |

### Group D — Ascend stores (medium risk)

| Store | Calls | Module | Composition root |
|-------|-------|--------|-----------------|
| `AscendPlayerStore` | 3 | parkour-ascend | `ParkourAscendPlugin.setup()` |
| `AscendPlayerPersistence` | 14 | parkour-ascend | `ParkourAscendPlugin.setup()` |
| `AscendMapStore` | 6 | parkour-ascend | `ParkourAscendPlugin.setup()` |
| `AscendSettingsStore` | 6 | parkour-ascend | `ParkourAscendPlugin.setup()` |
| `ChallengeManager` | 13 | parkour-ascend | `ParkourAscendPlugin.setup()` |
| `MineConfigStore` | 38 | parkour-ascend | `ParkourAscendPlugin.setup()` |
| `MinePlayerStore` | 8 | parkour-ascend | `ParkourAscendPlugin.setup()` |
| `MineAchievementTracker` | 8 | parkour-ascend | `ParkourAscendPlugin.setup()` |

### Group E — Purge stores (medium risk)

| Store | Calls | Module | Composition root |
|-------|-------|--------|-----------------|
| `PurgeScrapStore` | 9 | purge | `HyvexaPurgePlugin.setup()` |
| `PurgeWeaponUpgradeStore` | 11 | purge | `HyvexaPurgePlugin.setup()` |
| `PurgeClassStore` | 11 | purge | `HyvexaPurgePlugin.setup()` |
| `PurgeWeaponConfigManager` | 13 | purge | `HyvexaPurgePlugin.setup()` |
| `PurgeWaveConfigManager` | 10 | purge | `HyvexaPurgePlugin.setup()` |
| `PurgeVariantConfigManager` | 9 | purge | `HyvexaPurgePlugin.setup()` |
| `PurgeMissionStore` | 5 | purge | `HyvexaPurgePlugin.setup()` |
| `WeaponXpStore` | 5 | purge | `HyvexaPurgePlugin.setup()` |
| `PurgePlayerStore` | (via BasePlayerStore) | purge | `HyvexaPurgePlugin.setup()` |

### Group F — RunOrFall stores

| Store | Calls | Module | Composition root |
|-------|-------|--------|-----------------|
| `RunOrFallConfigStore` | 13 | runorfall | `HyvexaRunOrFallPlugin.setup()` |

### Group G — Analytics (largest surface, last)

| Store | Calls | Module | Composition root |
|-------|-------|--------|-----------------|
| `AnalyticsStore` | 24 | core | Each plugin's `setup()` |

### Cleanup — Non-store direct usage

After all stores are migrated, check for remaining `DatabaseManager.getInstance()` in non-store code:
- `DatabaseTestCommand`, `DatabaseReloadCommand`, `DatabaseClearCommand` — admin tools, acceptable to keep or inject
- `ParkourDatabaseSetup`, `AscendDatabaseSetup`, `RunOrFallDatabaseSetup`, `PurgeDatabaseSetup` — schema init, runs once at startup, acceptable to keep
- `WardrobeBridge` — should receive `ConnectionProvider`

## Pattern

Same pattern for every store:

```java
// 1. Add ConnectionProvider field + constructor
public class SomeStore {
    private final ConnectionProvider db;

    public SomeStore(ConnectionProvider db) {
        this.db = db;
    }

    // Keep no-arg constructor temporarily for backwards compat if needed
    public SomeStore() {
        this(DatabaseManager.getInstance());
    }

    // 2. Replace all internal DatabaseManager.getInstance() with this.db
    public void load(UUID playerId) {
        try (var conn = db.getConnection()) { ... }
    }
}
```

```java
// 3. Wire at composition root
// In plugin setup():
var db = DatabaseManager.getInstance();
var someStore = new SomeStore(db);
```

## Completion Criteria

- `DatabaseManager.getInstance()` only appears in:
  - `DatabaseManager` itself
  - Plugin `setup()` methods (composition roots)
  - `*DatabaseSetup` schema init classes
  - No-arg backwards-compat constructors (to be removed in Phase 5)
- Fresh `rg` count drops from ~370 to <20
