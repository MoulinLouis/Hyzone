# Purge MVP Solo Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** One player can start a zombie wave survival run, fight escalating waves, die or stop, and keep persistent progression (stats + scrap).

**Architecture:** Solo session per player, driven by `PurgeSessionManager` + `PurgeWaveManager`. Zombies are Trork_Grunt NPCs spawned via `NPCPlugin` at admin-defined spawn points. Death detection via ref-validity polling. Stats and scrap persisted to MySQL on session end.

**Tech Stack:** Java 21, Hytale Server API, NPCPlugin, Hyguns (AK47 + Bullet), MySQL via HikariCP (DatabaseManager).

**Spec:** `docs/GAME_DESIGN.md` (Plan 1 section)

---

## File Map

All new files go under `hyvexa-purge/src/main/java/io/hyvexa/purge/`:

| File | Purpose |
|------|---------|
| `data/PurgeSpawnPoint.java` | Spawn point record (id, x, y, z, yaw) |
| `data/PurgePlayerStats.java` | Per-player stats POJO (bestWave, totalKills, totalSessions) |
| `data/SessionState.java` | Session state enum |
| `data/PurgeSession.java` | Runtime state for one game session |
| `data/PurgePlayerStore.java` | Singleton — player stats cache + DB persistence |
| `data/PurgeScrapStore.java` | Singleton — scrap balance cache + DB persistence |
| `manager/PurgeSpawnPointManager.java` | CRUD for spawn points, weighted selection |
| `manager/PurgeSessionManager.java` | Create/destroy sessions, lifecycle cleanup |
| `manager/PurgeWaveManager.java` | Wave loop: spawn, combat, intermission |
| `hud/PurgeHudManager.java` | HUD attach/detach/update lifecycle |
| `command/PurgeCommand.java` | `/purge start\|stop\|stats` |
| `command/PurgeSpawnCommand.java` | `/purgespawn add\|remove\|list\|clear` (OP) |

Modified files:

| File | Changes |
|------|---------|
| `HyvexaPurgePlugin.java` | Initialize managers, register commands, update event handlers |
| `hud/PurgeHud.java` | Add wave status + scrap update methods |
| `resources/.../Purge_RunHud.ui` | Add center-top wave status block, add scrap display |

---

## Task 1: Data Classes & DB Bootstrap

**Files:**
- Create: `data/PurgeSpawnPoint.java`
- Create: `data/PurgePlayerStats.java`
- Create: `data/SessionState.java`
- Create: `data/PurgeScrapStore.java`
- Create: `data/PurgePlayerStore.java`

### Step 1: Create PurgeSpawnPoint

```java
// data/PurgeSpawnPoint.java
package io.hyvexa.purge.data;

public record PurgeSpawnPoint(int id, double x, double y, double z, float yaw) {}
```

### Step 2: Create SessionState

```java
// data/SessionState.java
package io.hyvexa.purge.data;

public enum SessionState {
    COUNTDOWN,
    SPAWNING,
    COMBAT,
    INTERMISSION,
    ENDED
}
```

### Step 3: Create PurgePlayerStats

```java
// data/PurgePlayerStats.java
package io.hyvexa.purge.data;

public class PurgePlayerStats {
    private int bestWave;
    private int totalKills;
    private int totalSessions;

    public PurgePlayerStats(int bestWave, int totalKills, int totalSessions) {
        this.bestWave = bestWave;
        this.totalKills = totalKills;
        this.totalSessions = totalSessions;
    }

    // Getters + setters for all fields
    // updateBestWave(int wave) — sets bestWave = max(bestWave, wave)
    // incrementKills(int amount)
    // incrementSessions()
}
```

### Step 4: Create PurgeScrapStore

Follows GemStore pattern: static singleton, `ConcurrentHashMap<UUID, Long>` cache, lazy DB load, immediate write. Table: `purge_player_scrap`.

```java
// data/PurgeScrapStore.java
package io.hyvexa.purge.data;

// Key imports:
// import io.hyvexa.core.db.DatabaseManager;
// import java.util.concurrent.ConcurrentHashMap;

public class PurgeScrapStore {
    private static final PurgeScrapStore INSTANCE = new PurgeScrapStore();
    private final ConcurrentHashMap<UUID, Long> scrapCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lifetimeCache = new ConcurrentHashMap<>();

    public static PurgeScrapStore getInstance() { return INSTANCE; }

    // initialize() — CREATE TABLE IF NOT EXISTS purge_player_scrap (
    //   uuid VARCHAR(36) NOT NULL PRIMARY KEY,
    //   scrap BIGINT NOT NULL DEFAULT 0,
    //   lifetime_scrap_earned BIGINT NOT NULL DEFAULT 0
    // ) ENGINE=InnoDB

    // getScrap(UUID) — lazy-load from DB on cache miss, return 0 if not found
    // addScrap(UUID, long amount) — add to balance + lifetime, persist immediately
    // getLifetimeScrap(UUID) — lazy-load from DB on cache miss
    // evictPlayer(UUID) — cache.remove()

    // Private: loadFromDatabase(UUID), persistToDatabase(UUID, long scrap, long lifetime)
    // Use INSERT ... ON DUPLICATE KEY UPDATE pattern
    // Use DatabaseManager.applyQueryTimeout(stmt) on all PreparedStatements
}
```

### Step 5: Create PurgePlayerStore

Follows same lazy-load/evict pattern. Table: `purge_player_stats`. Immediate write on session end (no dirty tracking needed — sessions are infrequent).

```java
// data/PurgePlayerStore.java
package io.hyvexa.purge.data;

public class PurgePlayerStore {
    private static final PurgePlayerStore INSTANCE = new PurgePlayerStore();
    private final ConcurrentHashMap<UUID, PurgePlayerStats> cache = new ConcurrentHashMap<>();

    public static PurgePlayerStore getInstance() { return INSTANCE; }

    // initialize() — CREATE TABLE IF NOT EXISTS purge_player_stats (
    //   uuid VARCHAR(36) NOT NULL PRIMARY KEY,
    //   best_wave INT NOT NULL DEFAULT 0,
    //   total_kills INT NOT NULL DEFAULT 0,
    //   total_sessions INT NOT NULL DEFAULT 0
    // ) ENGINE=InnoDB

    // getOrCreate(UUID) — lazy-load from DB, or create new PurgePlayerStats(0, 0, 0)
    // save(UUID, PurgePlayerStats) — persist immediately via INSERT ... ON DUPLICATE KEY UPDATE
    // evictPlayer(UUID) — cache.remove()
}
```

### Step 6: Commit

```
feat(purge): add data classes and DB stores for Purge MVP
```

---

## Task 2: Spawn Point Manager

**Files:**
- Create: `manager/PurgeSpawnPointManager.java`

### Step 1: Create PurgeSpawnPointManager

Loads all spawn points from DB on construction. Provides CRUD and weighted selection.

```java
// manager/PurgeSpawnPointManager.java
package io.hyvexa.purge.manager;

// Key fields:
// private final ConcurrentHashMap<Integer, PurgeSpawnPoint> spawnPoints = new ConcurrentHashMap<>();

public class PurgeSpawnPointManager {

    // Constructor: createTable() then loadAll()

    // createTable() — CREATE TABLE IF NOT EXISTS purge_spawn_points (
    //   id INT AUTO_INCREMENT PRIMARY KEY,
    //   x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,
    //   yaw FLOAT NOT NULL DEFAULT 0
    // ) ENGINE=InnoDB

    // loadAll() — SELECT * FROM purge_spawn_points -> populate spawnPoints map

    // addSpawnPoint(double x, double y, double z, float yaw) -> int id
    //   INSERT INTO purge_spawn_points (x, y, z, yaw) VALUES (?, ?, ?, ?)
    //   Use Statement.RETURN_GENERATED_KEYS to get auto-increment ID
    //   Add to spawnPoints map, return id

    // removeSpawnPoint(int id) -> boolean
    //   DELETE FROM purge_spawn_points WHERE id = ?
    //   Remove from spawnPoints map

    // clearAll()
    //   DELETE FROM purge_spawn_points (or TRUNCATE)
    //   spawnPoints.clear()

    // getAll() -> Collection<PurgeSpawnPoint>
    //   return List.copyOf(spawnPoints.values())

    // hasSpawnPoints() -> boolean
    //   return !spawnPoints.isEmpty()

    // selectSpawnPoint(double[] playerPosition) -> PurgeSpawnPoint
    //   Weighted random: farther points preferred. Min distance 15 blocks.
    //   See GAME_DESIGN.md "Spawn Point Selection" section for logic.
    //   Algorithm:
    //     1. Filter points >= 15 blocks from player
    //     2. If none pass filter, pick the farthest one
    //     3. Weight = distance^2 (squared distance favors far points)
    //     4. Weighted random pick from filtered set
}
```

**Important:** Distance calculation uses `double[]` arrays (not Vector3d — no accessors). Use `Math.sqrt((px-sx)^2 + (pz-sz)^2)` for horizontal distance.

### Step 2: Commit

```
feat(purge): add PurgeSpawnPointManager with DB persistence and weighted selection
```

---

## Task 3: Spawn Point Command

**Files:**
- Create: `command/PurgeSpawnCommand.java`

### Step 1: Create PurgeSpawnCommand

OP-only command. Follows `AbstractAsyncCommand` pattern from other modules.

```java
// command/PurgeSpawnCommand.java
package io.hyvexa.purge.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;

public class PurgeSpawnCommand extends AbstractAsyncCommand {

    private final PurgeSpawnPointManager spawnPointManager;

    public PurgeSpawnCommand(PurgeSpawnPointManager spawnPointManager) {
        super("purgespawn", "Manage Purge spawn points");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        // Guard: player only, OP only
        // Parse subcommand from ctx.getArguments(): "add", "remove <id>", "list", "clear"

        // /purgespawn add — save player's current position + rotation as spawn point
        //   Get position: TransformComponent from store
        //   float yaw = transform.getRotation().getYaw()  (or getY() on rotation Vector3f)
        //   int id = spawnPointManager.addSpawnPoint(x, y, z, yaw)
        //   Send: "Spawn point #<id> added at <x>, <y>, <z>"

        // /purgespawn remove <id> — remove by ID
        //   Parse int id from args
        //   boolean removed = spawnPointManager.removeSpawnPoint(id)
        //   Send success or "Spawn point #<id> not found"

        // /purgespawn list — list all with coords
        //   For each point: "#<id>: <x>, <y>, <z> (yaw: <yaw>)"
        //   If empty: "No spawn points configured."

        // /purgespawn clear — remove all
        //   spawnPointManager.clearAll()
        //   Send: "All spawn points cleared."
    }
}
```

**Getting player position:** Use `TransformComponent`:
```java
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;

TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
if (transform != null) {
    double[] pos = transform.getPosition();  // double[3]
    float yaw = transform.getRotation().getY();  // Y component = yaw
}
```

### Step 2: Commit

```
feat(purge): add /purgespawn command for admin spawn point management
```

---

## Task 4: PurgeSession

**Files:**
- Create: `data/PurgeSession.java`

### Step 1: Create PurgeSession

Runtime state for a single game session. Fields per spec in GAME_DESIGN.md.

```java
// data/PurgeSession.java
package io.hyvexa.purge.data;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

public class PurgeSession {
    private final UUID playerId;
    private volatile Ref<EntityStore> playerRef;
    private volatile int currentWave = 0;
    private volatile int totalKills = 0;
    private volatile int waveZombieCount = 0;  // total for current wave
    private volatile SessionState state = SessionState.COUNTDOWN;
    private volatile boolean spawningComplete = false;
    private final Set<Ref<EntityStore>> aliveZombies = ConcurrentHashMap.newKeySet();
    private volatile ScheduledFuture<?> waveTick;
    private volatile ScheduledFuture<?> spawnTask;
    private volatile ScheduledFuture<?> intermissionTask;
    private final long sessionStartTime = System.currentTimeMillis();

    public PurgeSession(UUID playerId, Ref<EntityStore> playerRef) {
        this.playerId = playerId;
        this.playerRef = playerRef;
    }

    // Getters/setters for all fields

    // addAliveZombie(Ref<EntityStore>) — aliveZombies.add(ref)
    // removeAliveZombie(Ref<EntityStore>) — aliveZombies.remove(ref)
    // getAliveZombieCount() — aliveZombies.size()
    // getAliveZombies() — returns the set (for iteration in wave tick)
    // incrementKills() — totalKills++

    // cancelAllTasks() — cancel waveTick, spawnTask, intermissionTask (each null-safe, cancel(false))
    // cancelSpawnTask() — cancel just spawnTask
}
```

### Step 2: Commit

```
feat(purge): add PurgeSession state model
```

---

## Task 5: Wave Manager

**Files:**
- Create: `manager/PurgeWaveManager.java`

This is the core gameplay loop. Handles wave spawning, zombie tracking, intermissions.

### Step 1: Create PurgeWaveManager

```java
// manager/PurgeWaveManager.java
package io.hyvexa.purge.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeSpawnPoint;
import io.hyvexa.purge.data.SessionState;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PurgeWaveManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String ZOMBIE_NPC_TYPE = "Trork_Grunt";
    private static final long WAVE_TICK_INTERVAL_MS = 200;
    private static final long SPAWN_STAGGER_MS = 500;
    private static final int SPAWN_BATCH_SIZE = 5;
    private static final int INTERMISSION_SECONDS = 5;
    private static final double SPAWN_RANDOM_OFFSET = 2.0;  // +/- blocks on X/Z

    private final PurgeSpawnPointManager spawnPointManager;
    private volatile NPCPlugin npcPlugin;

    public PurgeWaveManager(PurgeSpawnPointManager spawnPointManager) {
        this.spawnPointManager = spawnPointManager;
        try {
            this.npcPlugin = NPCPlugin.get();
        } catch (Exception e) {
            LOGGER.atWarning().log("NPCPlugin not available: " + e.getMessage());
        }
    }

    // --- Scaling Formulas (source of truth from GAME_DESIGN.md) ---

    public static int zombieCount(int wave) {
        return 5 + (wave - 1) * 2;
    }

    public static double hpMultiplier(int wave) {
        return 1.0 + Math.max(0, wave - 2) * 0.12;
    }

    public static double speedMultiplier(int wave) {
        return 1.0 + Math.max(0, wave - 4) * 0.025;
    }

    public static double damageMultiplier(int wave) {
        return 1.0 + Math.max(0, wave - 4) * 0.05;
    }

    // --- Wave Lifecycle ---

    // startCountdown(PurgeSession session, Runnable onCountdownComplete)
    //   session.setState(SessionState.COUNTDOWN)
    //   Schedule 1s ticks for 5 seconds, send countdown messages
    //   On complete: call startNextWave(session)

    // startNextWave(PurgeSession session)
    //   session.setCurrentWave(session.getCurrentWave() + 1)
    //   int count = zombieCount(session.getCurrentWave())
    //   session.setWaveZombieCount(count)
    //   session.setSpawningComplete(false)
    //   session.setState(SessionState.SPAWNING)
    //   startSpawning(session, count)
    //   startWaveTick(session)

    // startSpawning(PurgeSession session, int totalCount)
    //   Get player position from session.getPlayerRef() -> TransformComponent
    //   AtomicInteger remaining = new AtomicInteger(totalCount)
    //   Schedule repeating task at SPAWN_STAGGER_MS:
    //     Guard: if session state == ENDED, cancel and return
    //     int batch = Math.min(SPAWN_BATCH_SIZE, remaining.get())
    //     if batch <= 0: session.setSpawningComplete(true), cancel task, return
    //     world.execute(() -> {
    //       for each in batch:
    //         PurgeSpawnPoint point = spawnPointManager.selectSpawnPoint(playerPos)
    //         spawnZombie(session, store, point, session.getCurrentWave())
    //         remaining.decrementAndGet()
    //     })
    //   When spawning complete and session was in SPAWNING: setState(COMBAT)

    // spawnZombie(PurgeSession session, Store<EntityStore> store, PurgeSpawnPoint point, int wave)
    //   Add random offset: x += ThreadLocalRandom.current().nextDouble(-OFFSET, OFFSET), same for z
    //   Vector3d position = new Vector3d(x, y, z)
    //   Vector3f rotation = new Vector3f(0, point.yaw(), 0)
    //   Object result = npcPlugin.spawnNPC(store, ZOMBIE_NPC_TYPE, "", position, rotation)
    //   Extract Ref<EntityStore> via extractEntityRef(result) — same pattern as GhostNpcManager
    //   if entityRef != null:
    //     session.addAliveZombie(entityRef)
    //     Hide nameplate: store.ensureAndGetComponent(entityRef, Nameplate.getComponentType()).setText("")
    //     // TODO: Apply HP/speed/damage scaling when API is discovered
    //     //   double hp = baseHp * hpMultiplier(wave)
    //     //   double speed = baseSpeed * speedMultiplier(wave)
    //     //   double damage = baseDamage * damageMultiplier(wave)

    // startWaveTick(PurgeSession session)
    //   Schedule at WAVE_TICK_INTERVAL_MS:
    //     Guard: if session state == ENDED, cancel
    //     checkZombieDeaths(session)
    //     if session.isSpawningComplete() && session.getAliveZombieCount() == 0:
    //       onWaveComplete(session)

    // checkZombieDeaths(PurgeSession session)
    //   Set<Ref<EntityStore>> dead = new HashSet<>()
    //   for (Ref<EntityStore> ref : session.getAliveZombies()):
    //     if ref == null || !ref.isValid():
    //       dead.add(ref)
    //       session.incrementKills()  // invalid ref during combat = killed by player
    //   session.getAliveZombies().removeAll(dead)

    // onWaveComplete(PurgeSession session)
    //   Cancel waveTick
    //   session.setState(SessionState.INTERMISSION)
    //   Notify player: "Wave X complete!"
    //   Start intermission countdown (5s)
    //   On complete: startNextWave(session)

    // startIntermission(PurgeSession session)
    //   AtomicInteger countdown = new AtomicInteger(INTERMISSION_SECONDS)
    //   // TODO: Heal player to full (API discovery needed)
    //   Schedule 1s repeating task:
    //     Guard: if session state == ENDED, cancel
    //     int remaining = countdown.decrementAndGet()
    //     Update HUD: "Next wave in <remaining>..."
    //     if remaining <= 0:
    //       cancel task
    //       startNextWave(session)

    // --- Cleanup ---

    // removeAllZombies(PurgeSession session)
    //   For each ref in session.getAliveZombies():
    //     if ref != null && ref.isValid():
    //       world.execute(() -> {
    //         Store<EntityStore> store = ref.getStore();
    //         if (store != null) store.removeEntity(ref, RemoveReason.REMOVE);
    //       })
    //   session.getAliveZombies().clear()

    // --- Utility ---

    // extractEntityRef(Object pairResult) -> Ref<EntityStore>
    //   Copy exact pattern from GhostNpcManager (reflection: getFirst/getLeft/getKey/first/left)
}
```

**Critical threading rules:**
- All `npcPlugin.spawnNPC()` calls MUST be on world thread (`world.execute(...)`)
- All `store.removeEntity()` calls MUST be on world thread
- Wave tick runs on SCHEDULED_EXECUTOR thread — reads are safe, entity removal deferred to world thread
- `checkZombieDeaths` only reads ref validity — safe from any thread

### Step 2: Commit

```
feat(purge): add PurgeWaveManager with wave loop, zombie spawning, and death detection
```

---

## Task 6: Session Manager

**Files:**
- Create: `manager/PurgeSessionManager.java`

### Step 1: Create PurgeSessionManager

Orchestrates session creation, cleanup, and integration between wave manager, stores, and HUD.

```java
// manager/PurgeSessionManager.java
package io.hyvexa.purge.manager;

import io.hyvexa.purge.data.*;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeSessionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ConcurrentHashMap<UUID, PurgeSession> activeSessions = new ConcurrentHashMap<>();
    private final PurgeSpawnPointManager spawnPointManager;
    private final PurgeWaveManager waveManager;
    private final PurgeHudManager hudManager;

    public PurgeSessionManager(PurgeSpawnPointManager spawnPointManager,
                               PurgeWaveManager waveManager,
                               PurgeHudManager hudManager) {
        this.spawnPointManager = spawnPointManager;
        this.waveManager = waveManager;
        this.hudManager = hudManager;
    }

    // startSession(UUID playerId, Ref<EntityStore> playerRef) -> boolean
    //   Guard: if activeSessions.containsKey(playerId): send "Already in a session", return false
    //   Guard: if !spawnPointManager.hasSpawnPoints(): send message, return false
    //   PurgeSession session = new PurgeSession(playerId, playerRef)
    //   activeSessions.put(playerId, session)
    //   Grant loadout (clear inventory, give AK47 + bullets) — call HyvexaPurgePlugin helpers
    //   hudManager.showRunHud(playerId)  — show wave status elements
    //   waveManager.startCountdown(session, () -> { /* countdown complete callback */ })
    //   return true

    // stopSession(UUID playerId, String reason)
    //   PurgeSession session = activeSessions.remove(playerId)
    //   if session == null: return (no active session)
    //   session.setState(SessionState.ENDED)
    //   session.cancelAllTasks()
    //   waveManager.removeAllZombies(session)
    //   hudManager.hideRunHud(playerId)  — hide wave status elements
    //   persistResults(playerId, session)
    //   Send summary: "Purge ended - Wave <N> - <kills> kills - <scrap> scrap earned"

    // persistResults(UUID playerId, PurgeSession session)
    //   PurgePlayerStats stats = PurgePlayerStore.getInstance().getOrCreate(playerId)
    //   stats.updateBestWave(session.getCurrentWave())
    //   stats.incrementKills(session.getTotalKills())
    //   stats.incrementSessions()
    //   PurgePlayerStore.getInstance().save(playerId, stats)
    //   int scrap = calculateScrapReward(session.getCurrentWave())
    //   if scrap > 0: PurgeScrapStore.getInstance().addScrap(playerId, scrap)

    // calculateScrapReward(int wavesReached) -> int
    //   Per GAME_DESIGN.md:
    //   1-4: 0, 5-9: 20, 10-14: 60, 15-19: 120, 20-24: 200
    //   25+: 300 + 50 * ((wavesReached - 25) / 5)

    // hasActiveSession(UUID playerId) -> boolean
    // getSession(UUID playerId) -> PurgeSession (nullable)

    // cleanupPlayer(UUID playerId)
    //   stopSession(playerId, "disconnect")
    //   — called from disconnect/world-leave handlers

    // shutdown()
    //   For each active session: stopSession(playerId, "server shutdown")
    //   activeSessions.clear()
}
```

### Step 2: Commit

```
feat(purge): add PurgeSessionManager for session lifecycle orchestration
```

---

## Task 7: HUD Manager & Updates

**Files:**
- Modify: `hud/PurgeHud.java`
- Create: `hud/PurgeHudManager.java`
- Modify: `resources/.../Purge_RunHud.ui`

### Step 1: Update Purge_RunHud.ui

Add center-top wave status block and scrap display inside the right panel. Add elements inside `#HudLayer`, before `#RunHudRoot`.

```
// Add BEFORE #RunHudRoot, inside #HudLayer:

Group #WaveStatusRow {
  Anchor: (Top: 30, Left: 0, Right: 0, Height: 80);
  LayoutMode: Left;
  Visible: false;

  Group { FlexWeight: 1; }

  Group #WaveStatusRoot {
    Anchor: (Width: 300, Height: 80);
    LayoutMode: Top;

    Label #WaveLabel {
      Anchor: (Height: 40);
      Style: (
        FontSize: 28,
        HorizontalAlignment: Center,
        RenderBold: true,
        TextColor: #e7f1f4
      );
      Text: "WAVE 1";
    }

    Label #ZombieCountLabel {
      Anchor: (Top: 4, Height: 24);
      Style: (
        FontSize: 16,
        HorizontalAlignment: Center,
        TextColor: #9fb0ba
      );
      Text: "Zombies: 0/5";
    }
  }

  Group { FlexWeight: 1; }
}
```

Also add a scrap row inside `#InfoContent` (after `#PlayerGemsRow` or wherever appropriate). Add a label `#PlayerScrapValue` for scrap display:

```
Group #PlayerScrapRow {
  Anchor: (Left: 0, Right: 0, Top: 4, Height: 20);
  LayoutMode: Left;

  Group {
    Anchor: (Width: 16, Height: 16);
    Background: (TexturePath: "../Textures/storm.png");
  }
  Group { Anchor: (Width: 6); }
  Label #PlayerScrapValue {
    Style: (FontSize: 14, TextColor: #f59e0b, RenderBold: true);
    Text: "0 scrap";
  }
}
```

### Step 2: Update PurgeHud

Add wave status update methods with dirty-checking:

```java
// Add to PurgeHud.java:
private int lastWave = -1;
private int lastAlive = -1;
private int lastTotal = -1;
private long lastScrap = -1;
private String lastIntermissionText = null;

public void updateWaveStatus(int wave, int alive, int total) {
    if (wave == lastWave && alive == lastAlive && total == lastTotal) return;
    lastWave = wave;
    lastAlive = alive;
    lastTotal = total;
    lastIntermissionText = null;  // clear intermission text
    UICommandBuilder cmd = new UICommandBuilder();
    cmd.set("#WaveLabel.Text", "WAVE " + wave);
    cmd.set("#ZombieCountLabel.Text", "Zombies: " + alive + "/" + total);
    update(false, cmd);
}

public void updateIntermission(int seconds) {
    String text = "Next wave in " + seconds + "...";
    if (text.equals(lastIntermissionText)) return;
    lastIntermissionText = text;
    UICommandBuilder cmd = new UICommandBuilder();
    cmd.set("#ZombieCountLabel.Text", text);
    update(false, cmd);
}

public void updateScrap(long scrap) {
    if (scrap == lastScrap) return;
    lastScrap = scrap;
    UICommandBuilder cmd = new UICommandBuilder();
    cmd.set("#PlayerScrapValue.Text", scrap + " scrap");
    update(false, cmd);
}

public void setWaveStatusVisible(boolean visible) {
    UICommandBuilder cmd = new UICommandBuilder();
    cmd.set("#WaveStatusRow.Visible", visible);
    update(false, cmd);
}

public void resetCache() {
    lastWave = -1;
    lastAlive = -1;
    lastTotal = -1;
    lastScrap = -1;
    lastPlayerCount = -1;
    lastGems = -1;
    lastIntermissionText = null;
}
```

### Step 3: Create PurgeHudManager

Manages HUD attach/detach and periodic updates. Replaces the inline HUD management in HyvexaPurgePlugin.

```java
// hud/PurgeHudManager.java
package io.hyvexa.purge.hud;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeHudManager {

    private final ConcurrentHashMap<UUID, PurgeHud> purgeHuds = new ConcurrentHashMap<>();

    // attach(PlayerRef playerRef, Player player)
    //   PurgeHud hud = new PurgeHud(playerRef);
    //   purgeHuds.put(playerId, hud);
    //   MultiHudBridge.setCustomHud(player, playerRef, hud);
    //   player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass);
    //   MultiHudBridge.showIfNeeded(hud);

    // getHud(UUID playerId) -> PurgeHud (nullable)
    // removePlayer(UUID playerId) — purgeHuds.remove()

    // showRunHud(UUID playerId) — getHud() -> setWaveStatusVisible(true)
    // hideRunHud(UUID playerId) — getHud() -> setWaveStatusVisible(false)

    // tickSlowUpdates() — called every 5s from plugin
    //   For each hud: updatePlayerCount(), updateGems(), updateScrap()

    // updateWaveStatus(UUID playerId, int wave, int alive, int total)
    //   getHud(playerId)?.updateWaveStatus(wave, alive, total)

    // updateIntermission(UUID playerId, int seconds)
    //   getHud(playerId)?.updateIntermission(seconds)
}
```

### Step 4: Commit

```
feat(purge): add wave status HUD, scrap display, and PurgeHudManager
```

---

## Task 8: Purge Command

**Files:**
- Create: `command/PurgeCommand.java`

### Step 1: Create PurgeCommand

```java
// command/PurgeCommand.java
package io.hyvexa.purge.command;

public class PurgeCommand extends AbstractAsyncCommand {

    private final PurgeSessionManager sessionManager;

    public PurgeCommand(PurgeSessionManager sessionManager) {
        super("purge", "Purge zombie survival commands");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        // Guard: player only
        // Parse subcommand from arguments

        // /purge start
        //   Guard: must be in Purge world (ModeGate.isPurgeWorld)
        //   boolean started = sessionManager.startSession(playerId, ref)
        //   if started: send "Purge session starting..."
        //   else: error already sent by sessionManager

        // /purge stop
        //   if !sessionManager.hasActiveSession(playerId): send "No active session"
        //   else: sessionManager.stopSession(playerId, "voluntary stop")

        // /purge stats
        //   PurgePlayerStats stats = PurgePlayerStore.getInstance().getOrCreate(playerId)
        //   long scrap = PurgeScrapStore.getInstance().getScrap(playerId)
        //   Send: "-- Purge Stats --"
        //   Send: "Best wave: <N>"
        //   Send: "Total kills: <N>"
        //   Send: "Total sessions: <N>"
        //   Send: "Scrap: <N>"

        // No subcommand or unknown: send usage help
    }
}
```

### Step 2: Commit

```
feat(purge): add /purge start|stop|stats command
```

---

## Task 9: Plugin Integration & Lifecycle

**Files:**
- Modify: `HyvexaPurgePlugin.java`

This is the integration task — wire everything together in the main plugin class.

### Step 1: Add manager fields

```java
// Add to HyvexaPurgePlugin fields:
private PurgeSpawnPointManager spawnPointManager;
private PurgeSessionManager sessionManager;
private PurgeWaveManager waveManager;
private PurgeHudManager hudManager;
```

### Step 2: Update setup()

After existing store initializations, add:

```java
// Initialize Purge-specific stores
try { PurgeScrapStore.getInstance().initialize(); }
catch (Exception e) { LOGGER.atWarning().withCause(e).log("Failed to init PurgeScrapStore"); }
try { PurgePlayerStore.getInstance().initialize(); }
catch (Exception e) { LOGGER.atWarning().withCause(e).log("Failed to init PurgePlayerStore"); }

// Create managers
spawnPointManager = new PurgeSpawnPointManager();
waveManager = new PurgeWaveManager(spawnPointManager);
hudManager = new PurgeHudManager();
sessionManager = new PurgeSessionManager(spawnPointManager, waveManager, hudManager);

// Register commands
getCommandRegistry().registerCommand(new PurgeCommand(sessionManager));
getCommandRegistry().registerCommand(new PurgeSpawnCommand(spawnPointManager));
```

### Step 3: Update event handlers

**PlayerReadyEvent:** Replace inline HUD management with `hudManager.attach(...)`. Remove direct loadout grant (session start handles it now). Keep Discord link check.

```java
// PlayerReadyEvent handler (revised):
// 1. Validate ref, world, playerRef (keep existing guards)
// 2. playersInPurgeWorld.add(playerId)
// 3. hudManager.attach(playerRef, player)  // attach base HUD (info panel)
// 4. giveServerSelector(player)  // keep server selector
// 5. DiscordLinkStore.checkAndRewardGems(...)
// NOTE: Don't give weapon/ammo here — session start handles it
// NOTE: Don't clear inventory here — session start handles it
```

**AddPlayerToWorldEvent:** Add session cleanup when leaving Purge world.

```java
// If NOT purge world and playerId was tracked:
//   playersInPurgeWorld.remove(playerId)
//   sessionManager.cleanupPlayer(playerId)  // end active session if any
//   hudManager.removePlayer(playerId)
```

**PlayerDisconnectEvent:** Add session cleanup and store eviction.

```java
// Existing: playersInPurgeWorld.remove, GemStore.evict, DiscordLinkStore.evict
// Add:
//   sessionManager.cleanupPlayer(playerId)
//   hudManager.removePlayer(playerId)
//   PurgePlayerStore.getInstance().evictPlayer(playerId)
//   PurgeScrapStore.getInstance().evictPlayer(playerId)
```

### Step 4: Update tickHudUpdates

Replace inline HUD iteration with `hudManager.tickSlowUpdates()`.

### Step 5: Update shutdown

```java
// Add before existing cleanup:
if (sessionManager != null) sessionManager.shutdown();
```

### Step 6: Move loadout methods to be accessible from SessionManager

The `giveStartingWeapon`, `giveStartingAmmo`, `InventoryUtils.clearAllContainers` calls need to be accessible from `PurgeSessionManager.startSession()`. Options:
- Make them package-private static methods on `HyvexaPurgePlugin`
- Have `PurgeSessionManager` call them via `HyvexaPurgePlugin.getInstance()`
- Move them to a utility class

**Recommended:** Have `PurgeSessionManager` access them via `HyvexaPurgePlugin.getInstance()`. Add public methods:
```java
// Add to HyvexaPurgePlugin:
public void grantLoadout(Player player) {
    InventoryUtils.clearAllContainers(player);
    giveStartingWeapon(player);
    giveStartingAmmo(player);
    giveServerSelector(player);
}

public void removeLoadout(Player player) {
    InventoryUtils.clearAllContainers(player);
    giveServerSelector(player);  // keep server selector
}
```

### Step 7: Commit

```
feat(purge): integrate all managers and commands into plugin lifecycle
```

---

## Task 10: Wire Wave Manager to HUD

**Files:**
- Modify: `manager/PurgeWaveManager.java`
- Modify: `manager/PurgeSessionManager.java`

The wave manager needs to push HUD updates during the wave loop. Pass `PurgeHudManager` reference to `PurgeWaveManager`.

### Step 1: Add HUD callbacks to wave manager

```java
// Add to PurgeWaveManager constructor:
private final PurgeHudManager hudManager;

public PurgeWaveManager(PurgeSpawnPointManager spawnPointManager, PurgeHudManager hudManager) {
    this.spawnPointManager = spawnPointManager;
    this.hudManager = hudManager;
    // ... npcPlugin init
}

// In startCountdown: hudManager.updateIntermission(playerId, seconds) each tick
// In startWaveTick: hudManager.updateWaveStatus(playerId, wave, alive, total) each tick
// In startIntermission: hudManager.updateIntermission(playerId, seconds) each tick
// In onWaveComplete: send message to player
```

### Step 2: Send chat messages to player

For session notifications (wave complete, countdown, session end), send via `Player.sendMessage()`:

```java
// Getting Player from session:
Ref<EntityStore> ref = session.getPlayerRef();
if (ref != null && ref.isValid()) {
    Store<EntityStore> store = ref.getStore();
    Player player = store.getComponent(ref, Player.getComponentType());
    if (player != null) {
        player.sendMessage(Message.raw("Wave " + wave + " complete!"));
    }
}
```

**Import:** `com.hypixel.hytale.server.core.entity.entities.Player` and `com.hypixel.hytale.protocol.Message`.

### Step 3: Commit

```
feat(purge): wire wave manager HUD updates and player notifications
```

---

## Task 11: Final Integration & Edge Cases

**Files:**
- Modify: multiple managers for edge case handling

### Step 1: Handle player death

Player death during a session should end it. Two approaches:

**Option A (polling):** In the wave tick, check if the player ref is still valid:
```java
// In wave tick:
Ref<EntityStore> playerRef = session.getPlayerRef();
if (playerRef == null || !playerRef.isValid()) {
    sessionManager.stopSession(session.getPlayerId(), "death");
    return;
}
```

**Option B (DamageEventSystem):** Register a damage event system that detects lethal damage. This is more complex and can be added in Plan 2 if polling is insufficient.

**Use Option A for MVP.** Add the player-validity check at the start of every wave tick.

### Step 2: Validate session start preconditions

In `PurgeSessionManager.startSession()`, add guards:
- Player must be in Purge world
- Player ref must be valid
- No existing session for this player
- Spawn points must exist

### Step 3: runSafe pattern for cleanup

Wrap each cleanup step in try/catch to prevent partial cleanup failures from blocking subsequent steps:

```java
// In stopSession:
private void runSafe(String label, Runnable action) {
    try { action.run(); }
    catch (Exception e) { LOGGER.atWarning().log("Cleanup failed [" + label + "]: " + e.getMessage()); }
}

// Usage:
runSafe("cancel tasks", session::cancelAllTasks);
runSafe("remove zombies", () -> waveManager.removeAllZombies(session));
runSafe("hide hud", () -> hudManager.hideRunHud(playerId));
runSafe("persist", () -> persistResults(playerId, session));
```

### Step 4: Commit

```
feat(purge): add death detection, precondition guards, and safe cleanup
```

---

## Dependency Graph

```
Task 1 (data classes + stores)
  └── Task 2 (spawn point manager)
       └── Task 3 (spawn point command)
       └── Task 5 (wave manager)
  └── Task 4 (session model)
       └── Task 5 (wave manager)
       └── Task 6 (session manager)
  └── Task 7 (HUD)
       └── Task 6 (session manager)
       └── Task 10 (wire HUD to wave)

Task 8 (purge command) depends on Task 6

Task 9 (plugin integration) depends on Tasks 2-8

Task 10 (wire HUD) depends on Tasks 5, 7

Task 11 (edge cases) depends on Task 9
```

**Recommended execution order:** 1 → 2 → 3 → 4 → 5 → 7 → 6 → 8 → 9 → 10 → 11

---

## Known Gaps (to resolve during implementation)

| Gap | Impact | Mitigation |
|-----|--------|------------|
| NPC health component unknown | Can't scale zombie HP per wave | Zombies use default HP. Scaling formulas ready for when API is found. |
| NPC speed/damage API unknown | Can't scale zombie speed/damage | Default stats apply. Document TODO for API discovery. |
| Player heal API unknown | Can't heal player during intermission | Skip healing in MVP. Document TODO. |
| Player death event unknown | Can't detect exact moment of death | Poll `ref.isValid()` in wave tick (200ms). |
| TransformComponent.getPosition() return type | Need to confirm it returns `double[]` vs Vector3d | Check at implementation time. If Vector3d, use reflection or alternative. |

---

## Validation Checklist (MVP Exit Criteria)

From GAME_DESIGN.md Plan 1:

- [ ] `/purgespawn add` creates spawn points, `/purgespawn list` shows them
- [ ] `/purge start` begins a session with 5s countdown
- [ ] Zombies spawn in staggered batches at configured spawn points
- [ ] Killing all zombies advances to next wave with 5s intermission
- [ ] Wave count and zombie count display on HUD
- [ ] Scrap earned based on waves survived
- [ ] `/purge stop` cleanly ends session, removes zombies, saves stats
- [ ] `/purge stats` shows lifetime stats and scrap
- [ ] Disconnect during session: zombies removed, stats saved
- [ ] World switch during session: session ended, zombies cleaned up
- [ ] Multiple sessions in a row work without stuck states
- [ ] No orphan zombies remain after session end
