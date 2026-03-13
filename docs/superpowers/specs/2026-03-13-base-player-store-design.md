# BasePlayerStore Abstraction

**Date:** 2026-03-13
**Module:** hyvexa-core
**TECH_DEBT item:** 2.3

## Problem

Three player stores — `PurgePlayerStore`, `DuelStatsStore`, `RunOrFallStatsStore` — share 70-80% identical code: ConcurrentHashMap cache, DB load on cache miss, INSERT...ON DUPLICATE KEY UPDATE upsert, eviction, DB-not-initialized guards.

## Scope

### In scope
- New `BasePlayerStore<V>` abstract class in core
- Migrate `PurgePlayerStore`, `DuelStatsStore`, `RunOrFallStatsStore` to extend it

### Out of scope
- `WeaponXpStore` — nested cache key (`UUID` + `String`), doesn't fit `BasePlayerStore<V>`
- `PurgeScrapStore` — dirty tracking, per-player locks, async flush loop, transaction support
- `CachedCurrencyStore` — different pattern (TTL, currency-specific)
- Table creation — stays in each module's setup class

## Design

### BasePlayerStore<V>

**Location:** `hyvexa-core/src/main/java/io/hyvexa/core/db/BasePlayerStore.java`

```java
public abstract class BasePlayerStore<V> {

    private final ConcurrentHashMap<UUID, V> cache = new ConcurrentHashMap<>();

    // --- Template methods (subclasses implement) ---

    /** SELECT query with single `?` for UUID. */
    protected abstract String loadSql();

    /** INSERT ... ON DUPLICATE KEY UPDATE query. */
    protected abstract String upsertSql();

    /** Parse one ResultSet row into V. Cursor is already on the row. playerId is provided so subclasses don't need to redundantly SELECT the UUID column on single-player loads. */
    protected abstract V parseRow(ResultSet rs, UUID playerId) throws SQLException;

    /** Bind all upsert parameters onto the PreparedStatement. */
    protected abstract void bindUpsertParams(PreparedStatement stmt, UUID playerId, V value) throws SQLException;

    /** Default value when no DB row exists or DB is not initialized. */
    protected abstract V defaultValue();

    // --- Provided behavior ---

    public V getOrLoad(UUID playerId) {
        if (playerId == null) return defaultValue();
        return cache.computeIfAbsent(playerId, this::loadFromDatabase);
    }

    public void save(UUID playerId, V value) {
        if (playerId == null) return;
        cache.put(playerId, value);
        persistToDatabase(playerId, value);
    }

    public void evict(UUID playerId) {
        if (playerId == null) return;
        cache.remove(playerId);
    }

    /** Read from cache only, no DB fallback. Returns null if not cached. */
    protected V getCached(UUID playerId) {
        return playerId == null ? null : cache.get(playerId);
    }

    /** Expose cache values for subclasses that need listing (leaderboards). */
    protected Collection<V> cacheValues() {
        return cache.values();
    }

    /** Bulk-load all rows for subclasses that need it (leaderboards). */
    protected void loadAll(String loadAllSql, Function<ResultSet, UUID> keyExtractor) {
        if (!DatabaseManager.getInstance().isInitialized()) return;
        int skipped = 0;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = DatabaseManager.prepare(conn, loadAllSql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID key = keyExtractor.apply(rs);
                    V value = parseRow(rs, key);
                    if (key != null && value != null) {
                        cache.put(key, value);
                    }
                } catch (Exception e) {
                    skipped++;
                }
            }
        } catch (SQLException e) {
            logError("bulk loading", e);
        }
        if (skipped > 0) {
            HytaleLogger.forEnclosingClass().atWarning()
                .log("[%s] Skipped %d malformed rows during bulk load", getClass().getSimpleName(), skipped);
        }
    }

    private V loadFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return defaultValue();
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = DatabaseManager.prepare(conn, loadSql())) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return parseRow(rs, playerId);
                }
            }
        } catch (SQLException e) {
            logError("loading player " + playerId, e);
        }
        return defaultValue();
    }

    private void persistToDatabase(UUID playerId, V value) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = DatabaseManager.prepare(conn, upsertSql())) {
            bindUpsertParams(stmt, playerId, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logError("saving player " + playerId, e);
        }
    }

    private void logError(String context, SQLException e) {
        HytaleLogger.forEnclosingClass().atWarning().withCause(e)
            .log("[%s] DB error %s", getClass().getSimpleName(), context);
    }
}
```

### Key Design Decisions

**Null safety:** All public methods guard against null `playerId` (matches existing `PurgePlayerStore` behavior).

**Logging:** Uses `HytaleLogger.forEnclosingClass().atWarning().withCause(e)` to preserve stack traces (matches existing store logging pattern).

**Bulk loading:** `loadAll()` is a protected helper for subclasses that need it (`DuelStatsStore`, `RunOrFallStatsStore` use it for leaderboards). `PurgePlayerStore` doesn't call it. This keeps bulk-load opt-in without polluting the base class API.

**Cache-first saves:** `save()` updates cache then persists. This matches `PurgePlayerStore` and `DuelStatsStore` behavior. `RunOrFallStatsStore` currently does DB-first-then-cache, but this is overly conservative — if the DB write fails, the cache still reflects the game state for the current session, and the next `save()` will retry.

**Thread safety:** `computeIfAbsent` prevents duplicate DB loads. For read-modify-write cycles (`recordWin`/`recordLoss`), concurrent mutations on the same player are not possible in practice — a player can only be in one game/duel at a time. This assumption is documented, and `RunOrFallStatsStore` drops its `synchronized` blocks.

### Subclass Migrations

#### PurgePlayerStore (purge module)

Before: ~80 LOC. After: ~35 LOC.

```java
public class PurgePlayerStore extends BasePlayerStore<PurgePlayerStats> {
    private static final PurgePlayerStore INSTANCE = new PurgePlayerStore();
    public static PurgePlayerStore getInstance() { return INSTANCE; }
    private PurgePlayerStore() {}

    @Override protected String loadSql() {
        return "SELECT best_wave, total_kills, total_sessions FROM purge_player_stats WHERE uuid = ?";
    }
    @Override protected String upsertSql() {
        return "INSERT INTO purge_player_stats (uuid, best_wave, total_kills, total_sessions) VALUES (?, ?, ?, ?) "
             + "ON DUPLICATE KEY UPDATE best_wave = ?, total_kills = ?, total_sessions = ?";
    }
    @Override protected PurgePlayerStats parseRow(ResultSet rs, UUID playerId) throws SQLException {
        return new PurgePlayerStats(rs.getInt("best_wave"), rs.getInt("total_kills"), rs.getInt("total_sessions"));
    }
    @Override protected void bindUpsertParams(PreparedStatement stmt, UUID id, PurgePlayerStats s) throws SQLException {
        stmt.setString(1, id.toString());
        stmt.setInt(2, s.getBestWave()); stmt.setInt(3, s.getTotalKills()); stmt.setInt(4, s.getTotalSessions());
        stmt.setInt(5, s.getBestWave()); stmt.setInt(6, s.getTotalKills()); stmt.setInt(7, s.getTotalSessions());
    }
    @Override protected PurgePlayerStats defaultValue() { return new PurgePlayerStats(0, 0, 0); }
}
```

#### DuelStatsStore (parkour module)

Before: ~100 LOC. After: ~60 LOC.

Changes:
- Keeps `syncLoad()` — calls `loadAll()` from base class to populate cache for leaderboard
- Keeps `getStatsByName(String)` — uses `cacheValues()` for linear search
- Keeps `recordWin(UUID, name)` / `recordLoss(UUID, name)` — call `getOrLoad()` then `save()`
- Keeps `listStats()` — returns `new ArrayList<>(cacheValues())`
- **Nullable `getStats(UUID)`**: Subclass provides `getStats(UUID)` that returns `getCached(playerId)` (nullable) for callers like `DuelCommand` that check `if (stats == null)`. `getOrLoad(UUID)` is used internally by `recordWin`/`recordLoss` when a non-null result is needed.
- `upsertSql()` includes `updated_at` timestamp column (5th parameter)
- **Table creation**: `ensureTable()` remains on the subclass (parkour module has no centralized DB setup class yet). Called from `syncLoad()` before `loadAll()`.

#### RunOrFallStatsStore (runorfall module)

Before: ~130 LOC. After: ~65 LOC.

Changes:
- Keeps `syncLoad()` — calls `loadAll()` from base class to populate cache for leaderboard
- Drops all `synchronized` keywords
- Drops `ensureColumns()` — column migrations handled by module setup
- Keeps `recordWin(BiConsumer)` / `recordLoss(BiConsumer)` pattern, calls `save()` after mutation
- Keeps `listStats()` — returns `new ArrayList<>(cacheValues())`
- **Name parameter**: Provides `getOrLoadWithName(UUID, String)` that calls `getOrLoad(UUID)` then updates the player name on the returned object if it differs. Callers migrate from `getStats(UUID, name)` to `getOrLoadWithName(UUID, name)`.
- **Defensive copy**: Overrides `getOrLoad()` — calls `super.getOrLoad(playerId)` to populate cache, then returns `.copy()` on the result. The `recordResult` methods call `super.getOrLoad()` to get the real cached reference, mutate a copy, then call `save()` to atomically replace the cache entry.
- **Name sanitization**: `sanitizePlayerName()` remains as a private helper on the subclass (RunOrFall-specific concern).

### Public API Changes

| Current method | New method | Notes |
|----------------|------------|-------|
| `PurgePlayerStore.getOrCreate(UUID)` | `getOrLoad(UUID)` | Rename, same behavior |
| `DuelStatsStore.getStats(UUID)` | `getStats(UUID)` (nullable, kept) + `getOrLoad(UUID)` (non-null, new) | `getStats` returns cached only; `getOrLoad` does DB fallback |
| `RunOrFallStatsStore.getStats(UUID, name)` | `getOrLoadWithName(UUID, name)` | Delegates to `getOrLoad()` + sets name |
| `*.evictPlayer(UUID)` | `evict(UUID)` | Simplified name |
| `*.listStats()` | `listStats()` (kept on subclass) | Uses `cacheValues()` from base |

**Caller updates required:**
- `PurgePlayerStore`: All callers rename `getOrCreate()` -> `getOrLoad()`, `evictPlayer()` -> `evict()`
- `DuelStatsStore`: `DuelCommand` callers keep using `getStats()` (still nullable). `DuelTracker` callers use `recordWin()`/`recordLoss()` (unchanged).
- `RunOrFallStatsStore`: Callers rename `getStats(uuid, name)` -> `getOrLoadWithName(uuid, name)`. `recordWin`/`recordLoss` unchanged.

### Error Handling

- DB-not-initialized: return `defaultValue()`, no log (matches current behavior)
- SQLException on load: log warning with stack trace via `HytaleLogger`, return `defaultValue()`
- SQLException on save: log warning with stack trace, cache still updated (cache is source of truth during session)
- Null playerId: return `defaultValue()` (load) or no-op (save/evict)

### Testing

`BasePlayerStore` depends on `HytaleLogger` and `DatabaseManager` (Hytale imports), so no unit tests per CLAUDE.md constraints. Correctness verified by:
1. Compile check (gradlew build)
2. Callers of the 3 stores continue to work unchanged (API compatibility)
