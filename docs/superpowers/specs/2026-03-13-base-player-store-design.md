# BasePlayerStore Abstraction

**Date:** 2026-03-13
**Module:** hyvexa-core
**TECH_DEBT item:** 2.3

## Problem

Three player stores — `PurgePlayerStore`, `DuelStatsStore`, `RunOrFallStatsStore` — share 70-80% identical code: ConcurrentHashMap cache, lazy DB load on cache miss, INSERT...ON DUPLICATE KEY UPDATE upsert, eviction, DB-not-initialized guards.

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

    /** Parse one ResultSet row into V. Cursor is already on the row. */
    protected abstract V parseRow(ResultSet rs) throws SQLException;

    /** Bind all upsert parameters onto the PreparedStatement. */
    protected abstract void bindUpsertParams(PreparedStatement stmt, UUID playerId, V value) throws SQLException;

    /** Default value when no DB row exists or DB is not initialized. */
    protected abstract V defaultValue();

    // --- Provided behavior ---

    public V getOrLoad(UUID playerId) {
        return cache.computeIfAbsent(playerId, this::loadFromDatabase);
    }

    public void save(UUID playerId, V value) {
        cache.put(playerId, value);
        persistToDatabase(playerId, value);
    }

    public void evict(UUID playerId) {
        cache.remove(playerId);
    }

    protected V getCached(UUID playerId) {
        return cache.get(playerId);
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
                    return parseRow(rs);
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
        HytaleLogger.error("[" + getClass().getSimpleName() + "] DB error " + context + ": " + e.getMessage());
    }
}
```

### Subclass Migrations

#### PurgePlayerStore (purge module)

Before: ~80 LOC with manual cache, load, save, evict, DB guards.
After: ~35 LOC — singleton boilerplate + 5 template methods.

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
    @Override protected V parseRow(ResultSet rs) throws SQLException {
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

Before: ~100 LOC with manual cache, load, save, syncLoad, ensureTable.
After: ~45 LOC — 5 template methods + `getStatsByName()` (linear search, kept as-is) + `recordWin()`/`recordLoss()` convenience methods.

Changes:
- Drops `syncLoad()` bulk-load — uses lazy `getOrLoad()` instead
- Drops manual `ensureTable()` — table creation handled by module setup
- Keeps `getStatsByName(String)` as a subclass-specific method (iterates cache values)
- `recordWin(UUID, name)` and `recordLoss(UUID, name)` call `getOrLoad()` then `save()`

#### RunOrFallStatsStore (runorfall module)

Before: ~130 LOC with synchronized methods, bulk syncLoad, column migrations.
After: ~50 LOC — 5 template methods + `recordWin()`/`recordLoss()` convenience methods.

Changes:
- Drops all `synchronized` keywords — `computeIfAbsent` handles thread safety
- Drops `syncLoad()` bulk-load — uses lazy `getOrLoad()` instead
- Drops `ensureColumns()` migration calls — column migrations handled by module setup
- `recordWin(BiConsumer)` / `recordLoss(BiConsumer)` pattern stays, calls `save()` after mutation

### Public API Changes

| Current method | New method | Notes |
|----------------|------------|-------|
| `PurgePlayerStore.getOrCreate(UUID)` | `getOrLoad(UUID)` | Rename for consistency |
| `DuelStatsStore.getStats(UUID)` | `getOrLoad(UUID)` | Same behavior |
| `RunOrFallStatsStore.getStats(UUID, name)` | `getOrLoad(UUID)` | Name param handled differently (see below) |
| `*.evictPlayer(UUID)` | `evict(UUID)` | Simplified name |

For `RunOrFallStatsStore.getStats(UUID, name)`: the `name` parameter is used to set the player name on first load. The subclass can override `getOrLoad` or provide a separate `getOrLoadWithName(UUID, String)` method that calls `getOrLoad()` then updates the name field if needed.

### Error Handling

- DB-not-initialized: return `defaultValue()`, no log (matches current behavior)
- SQLException on load: log error via `HytaleLogger.error()`, return `defaultValue()`
- SQLException on save: log error via `HytaleLogger.error()`, cache still updated (matches current behavior — cache is source of truth during session)

### Testing

Only pure-logic classes with zero Hytale imports are testable per CLAUDE.md. `BasePlayerStore` imports `HytaleLogger` and depends on `DatabaseManager`, so no unit tests. Correctness verified by:
1. Compile check (gradlew build)
2. Callers of the 3 stores continue to work unchanged (API compatibility)
