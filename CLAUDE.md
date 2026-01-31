# Claude Code Reference

Quick reference for AI agents working on this Hytale plugin project.

## Quick Commands

```bash
# Do NOT run builds - owner handles builds/tests
# Use these for reference only:
# ./gradlew stagePlugins - copy jars to run/mods
# ./gradlew collectPlugins - copy jars to build/libs
```

## Project Overview

**Hyvexa** - Multi-module Hytale server plugin suite for a parkour minigame server.

| Module | Purpose | Entry Point |
|--------|---------|-------------|
| `hyvexa-core` | Shared DB, utilities, mode state | N/A (library) |
| `hyvexa-parkour` | Main parkour gameplay | `io.hyvexa.HyvexaPlugin` |
| `hyvexa-parkour-ascend` | Ascend idle mode | `io.hyvexa.ascend.ParkourAscendPlugin` |
| `hyvexa-hub` | Hub routing + mode selection | `io.hyvexa.hub.HyvexaHubPlugin` |

## Key Directories

| Path | Content |
|------|---------|
| `hyvexa-*/src/main/java/io/hyvexa/` | Java sources |
| `hyvexa-parkour/src/main/resources/Common/UI/Custom/Pages/` | UI definitions |
| `hyvexa-parkour/src/main/resources/Server/` | Server assets |
| `run/mods/Parkour/` | Runtime config (database.json) |

## Code Patterns

### Commands
```java
public class MyCommand extends AbstractAsyncCommand {
    @Override
    public CompletableFuture<Void> executeAsync(CommandContext context) {
        // Implementation
        return CompletableFuture.completedFuture(null);
    }
}
// Register: getCommandRegistry().registerCommand(new MyCommand());
```

### UI Pages
```java
public class MyPage extends InteractiveCustomUIPage {
    public MyPage(PlayerRef playerRef) {
        super("Common/UI/Custom/Pages/MyPage.ui");  // Forward slashes
    }
}
// Open: CustomUI.open(playerRef, new MyPage(playerRef));
```

### Data Stores
```java
// Memory-first with MySQL persistence
private final ConcurrentHashMap<UUID, Data> cache = new ConcurrentHashMap<>();
public void syncLoad() { /* Load from MySQL on startup */ }
public void save(Data d) { cache.put(d.id, d); /* + MySQL write */ }
```

### Threading
```java
// Entity/world ops require world thread
CompletableFuture.runAsync(() -> {
    store.addComponent(ref, Teleport.getComponentType(), teleport);
}, world);
```

### Thread Safety Patterns
```java
// Use AtomicLong/AtomicInteger for counters accessed from multiple threads
private final AtomicLong counter = new AtomicLong(0);
counter.incrementAndGet();

// Use volatile for simple flags
private volatile boolean initialized = false;

// Use synchronized for initialization
synchronized (INIT_LOCK) {
    if (initialized) return;
    // ... initialize ...
    initialized = true;
}

// Use computeIfAbsent instead of check-then-put
map.computeIfAbsent(key, k -> new Value());
```

### ECS Access
```java
Ref<EntityStore> ref = playerRef.getReference();
if (ref == null || !ref.isValid()) return;
Store<EntityStore> store = ref.getStore();
Player player = store.getComponent(ref, Player.getComponentType());
```

## Key Classes

| Purpose | Class |
|---------|-------|
| Run tracking | `RunTracker` (parkour), `AscendRunTracker` (ascend) |
| HUD management | `HudManager`, `RunHud`, `HubHud`, `AscendHud` |
| Player progress | `ProgressStore` (parkour), `AscendPlayerStore` (ascend) |
| Map storage | `MapStore` (parkour), `AscendMapStore` (ascend) |
| Settings | `SettingsStore` |
| DB connections | `DatabaseManager` (core) |
| Mode routing | `HubRouter` |

### Manager Classes (extracted from HyvexaPlugin)

| Manager | Purpose |
|---------|---------|
| `LeaderboardHologramManager` | Hologram refresh, leaderboard formatting |
| `CollisionManager` | Player collision disabling |
| `InventorySyncManager` | Inventory sync, drop protection, welcome |
| `WorldMapManager` | World map generation control |
| `ChatFormatter` | Chat message formatting with ranks/badges |
| `PlayerPerksManager` | VIP perks, speed boosts, badges |
| `AnnouncementManager` | Chat/HUD announcements |
| `PlaytimeManager` | Playtime tracking |
| `PlayerCleanupManager` | Stale player cleanup |

### Utility Classes (hyvexa-core)

| Utility | Purpose |
|---------|---------|
| `CommandUtils` | Command argument parsing |
| `DatabaseRetry` | Retry with exponential backoff |
| `FormatUtils` | Time/rank formatting |
| `HylogramsBridge` | Hylograms API reflection bridge (with method caching) |
| `MapUnlockHelper` | Map unlock logic (ascend) |

## Workflow Reminders

1. **Update CHANGELOG.md** after completing each feature
2. **Follow existing patterns** - check similar files before implementing
3. **No build runs** - owner handles `./gradlew build`
4. **UI paths use forward slashes** - `"Common/UI/Custom/Pages/X.ui"`
5. **Check ref validity** - `if (ref == null || !ref.isValid()) return;`
6. **World thread for entity ops** - use `CompletableFuture.runAsync(..., world)`

## Database

- Config: `mods/Parkour/database.json` (gitignored)
- Tables: `players`, `maps`, `map_checkpoints`, `player_completions`, `settings`, etc.
- Ascend tables: `ascend_players`, `ascend_maps`, `ascend_player_maps`, `ascend_upgrade_costs`
- Pattern: In-memory cache + MySQL persistence

## Current Focus: Ascend Mode

Ascend is an idle/incremental parkour mode where:
- Players complete maps manually to unlock them and earn coins
- Per-map multiplier digits increase with completions
- Robots can be purchased to auto-run maps
- Rebirth system converts coins to permanent multipliers

See `ASCEND_MODULE_SUMMARY.md` for full Ascend implementation details.

## Constants

Timing constants are documented in `ParkourTimingConstants.java`:
- `HUD_UPDATE_INTERVAL_MS` (100ms) - HUD refresh rate
- `STALE_PLAYER_SWEEP_INTERVAL_SECONDS` (120s) - Cleanup sweep
- `LEADERBOARD_HOLOGRAM_ENTRIES` (10) - Global leaderboard size
- `MAP_HOLOGRAM_TOP_LIMIT` (5) - Per-map leaderboard size

## Reference Files

| For... | Read... |
|--------|---------|
| Full instructions | `AGENTS.md` |
| System architecture | `ARCHITECTURE.md` |
| Database schema | `DATABASE.md` |
| Ascend details | `ASCEND_MODULE_SUMMARY.md`, `ASCEND_CURRENT.md` |
| Feature history | `CHANGELOG.md` |
| Code review findings | `CODE_REVIEW.md` |
| Implementation plan | `docs/plans/2026-01-31-code-review-fixes.md` |
