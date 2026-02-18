# Purge Module - Zombie Wave Survival

Game design and implementation plan for the Purge module: a zombie wave survival mode set in the Purge world. Solo mode is the priority; the architecture keeps co-op support in mind for the future.

---

## Table of Contents

1. [Implementation Plans](#implementation-plans)
2. [Module Structure](#module-structure)
3. [Spawn Points](#spawn-points)
4. [Player Loadout](#player-loadout)
5. [Wave System](#wave-system)
6. [Zombie Spawn Logic](#zombie-spawn-logic)
7. [HUD](#hud)
8. [Session Lifecycle](#session-lifecycle)
9. [Data Persistence](#data-persistence)
10. [Purge Currency (Scrap)](#purge-currency-scrap)

---

## Implementation Plans

This section splits development into ordered implementation plans so we can ship a first playable build fast, then harden and expand safely.

### Plan 1 - First Functional Version (MVP Solo)

Goal: one player can start a run, fight escalating waves, die/stop, and keep persistent progression.

Scope:
- In: Solo sessions only, manual start/stop flow, basic wave progression, AK-47 + Bullet loadout, spawn points, minimal HUD, stats + scrap persistence.
- Out: Co-op, advanced enemy variants, long-term progression systems, heavy polish.

Ordered tasks:
1. Foundation and data bootstrap
   - Implement `PurgeSession`, `PurgeSessionManager`, `PurgeWaveManager`.
   - Add DB setup for `purge_spawn_points`, `purge_player_stats`, `purge_player_scrap`.
2. Admin spawn point workflow
   - Implement `PurgeSpawnPointManager` and `/purgespawn add|remove|list|clear`.
   - Block `/purge start` when no spawn points exist.
3. Session command flow
   - Implement `/purge start|stop|stats` with one active session per player.
   - Add strict state guards to prevent double starts.
4. Core wave loop
   - Spawn staggered zombies by wave.
   - Track alive set and complete on `spawningComplete && aliveZombies.isEmpty()`.
   - Apply baseline scaling formulas (count/HP/speed/damage).
5. Loadout and lifecycle
   - Grant `AK47` + starter `Bullet` on session start.
   - Cleanly remove entities/loadout on death, stop, disconnect, and world-leave.
6. Minimal HUD delivery
   - Show wave, zombies remaining, intermission countdown.
   - Show scrap balance and player count.
7. Reward and persistence
   - Persist `best_wave`, `total_kills`, `total_sessions`.
   - Award and persist scrap at session end.
8. MVP validation
   - Validate the full loop: start -> waves -> death/stop -> saved stats/scrap.
   - Validate edge paths: disconnect/reconnect, world switch mid-session.

Exit criteria:
- A tester can complete multiple sessions in a row without stuck states, orphan zombies, or lost progression.

### Plan 2 - Stabilization and Production Readiness

Goal: harden runtime behavior and prevent race conditions/data issues.

Scope:
- In: task cancellation safety, thread-safety, persistence integrity, operational logs.
- Out: new gameplay systems.

Ordered tasks:
1. Scheduler and concurrency hardening
   - Audit all periodic tasks (wave tick, spawn task, HUD updates).
   - Ensure idempotent cleanup and one-time reward application.
2. World-thread compliance pass
   - Re-check all entity operations for world-thread execution.
   - Keep `ref` validity checks on all tracked entities.
3. Persistence integrity pass
   - Confirm writes are stable on repeated session end paths.
   - Add lightweight recovery behavior for partial failure scenarios.
4. Observability and support
   - Improve logs for session start/end, rewards, and cleanup failures.
   - Add useful diagnostics in `/purge stats`.
5. Regression checklist
   - Re-test repeated starts/stops, no-spawnpoint path, disconnects, and world changes.

Exit criteria:
- No blocker bugs during repeated soak tests by staff.

### Plan 3 - Gameplay and UX Iteration

Goal: tune difficulty/economy and improve clarity once core is stable.

Scope:
- In: balancing, spawn quality, HUD polish, reward tuning.
- Out: co-op and large feature additions.

Ordered tasks:
1. Difficulty tuning pass
   - Adjust wave and multiplier curves with playtest data.
2. Spawn quality pass
   - Improve weighted spawn logic and anti-close-spawn reliability.
3. HUD polish
   - Improve transition messaging, countdown clarity, and end-of-run summary readability.
4. Scrap economy tuning
   - Tune reward brackets for healthy early progression and replay value.
5. Documentation sync
   - Update `docs/DATABASE.md` and related docs once values/schema are finalized.

Exit criteria:
- First-time testers understand the mode quickly and report acceptable pacing.

### Plan 4 - Post-V1 Expansion (Optional)

Goal: extend the proven solo architecture without rework.

Candidates:
- Co-op sessions (shared wave state, per-player rewards, last-alive fail condition).
- Additional weapon paths and scrap sinks (upgrades, unlocks, rerolls).
- Enemy variants and elite wave modifiers.

---

## Module Structure

Follows the same singleton-plugin pattern as the other modules.

```
hyvexa-purge/
  src/main/java/io/hyvexa/purge/
    HyvexaPurgePlugin.java           # Singleton entry point (JavaPlugin)
    manager/
      PurgeSessionManager.java       # Creates/destroys game sessions
      PurgeHudManager.java           # Per-player HUD lifecycle
      PurgeWaveManager.java          # Wave sequencing, intermissions, spawning
      PurgeSpawnPointManager.java    # Load/save admin-defined spawn points
    data/
      PurgePlayerStore.java          # Per-player stats (lazy-load, evict on disconnect)
      PurgeScrapStore.java           # Per-player Purge currency balances (Scrap)
      PurgeSession.java              # Runtime state for one game session
      PurgeSpawnPoint.java           # Position + rotation for a spawn point
    hud/
      PurgeHud.java                  # CustomUIHud subclass
    command/
      PurgeCommand.java              # /purge start|stop|stats
      PurgeSpawnCommand.java         # /purgespawn add|remove|list|clear
    system/
      ZombieTickSystem.java          # (Optional) EntityTickingSystem for zombie AI
  src/main/resources/Common/UI/Custom/Pages/
    Purge_RunHud.ui                  # Main HUD overlay
```

### Key Classes

| Class | Responsibility |
|-------|----------------|
| `PurgeSessionManager` | One `PurgeSession` per active game. In solo mode, one session per player. Co-op future: one session per group. |
| `PurgeWaveManager` | Drives the wave loop: spawn phase -> combat phase -> intermission -> next wave. Owned by a session. |
| `PurgeSpawnPointManager` | CRUD for admin-defined spawn points. Persists to DB. Loaded on plugin startup. |
| `PurgeHudManager` | Attaches/detaches `PurgeHud` per player. Delta updates via `UICommandBuilder`. |

### Plugin Initialization

```java
// HyvexaPurgePlugin.setup()
DatabaseManager.getInstance().initialize();
PurgeScrapStore.getInstance().initialize();
AnalyticsStore.getInstance().initialize();

spawnPointManager = new PurgeSpawnPointManager();   // loads from DB
sessionManager = new PurgeSessionManager(spawnPointManager);
hudManager = new PurgeHudManager();

// Register commands, events, scheduled tasks
```

---

## Spawn Points

Admin-defined positions in the Purge world where zombies can appear.

### Admin Commands

| Command | Description |
|---------|-------------|
| `/purgespawn add` | Saves the admin's current position + rotation as a spawn point |
| `/purgespawn remove <id>` | Removes a spawn point by ID |
| `/purgespawn list` | Lists all spawn points with coordinates |
| `/purgespawn clear` | Removes all spawn points |

### Storage

DB table `purge_spawn_points`:

| Column | Type | Description |
|--------|------|-------------|
| `id` | INT AUTO_INCREMENT PK | Spawn point ID |
| `x` | DOUBLE | X coordinate |
| `y` | DOUBLE | Y coordinate |
| `z` | DOUBLE | Z coordinate |
| `yaw` | FLOAT | Rotation yaw (direction zombie faces on spawn) |

Loaded into memory on plugin startup (`ConcurrentHashMap<Integer, PurgeSpawnPoint>`). Writes go to DB immediately (admin action, infrequent).

### Spawn Point Selection

During a wave, the `PurgeWaveManager` picks spawn points using weighted random selection:
- Points farther from the player are preferred (avoids spawning on top of the player)
- Minimum distance threshold: 15 blocks (skip points too close)
- If all points are within the threshold, fall back to the farthest one
- Co-op future: distance check against the nearest player in the session

If there are no configured spawn points, `/purge start` fails fast with a clear message:
- "No purge spawn points configured. Use /purgespawn add first."
- Session does not start, no loadout/HUD is attached

---

## Player Loadout

When a session starts, the player receives a fixed starting loadout.

### Default Loadout

| Slot | Item | Hytale Asset ID |
|------|------|-----------------|
| Weapon | Hyguns AK-47 | `AK47` |
| Ammo | Hyguns Bullet x120 | `Bullet` |

The loadout is granted via inventory API on session start and removed on session end. The weapon uses Hyguns shoot/reload interactions.

### Future Expansion (Co-op)

- Additional weapons
- Ammo balancing (starting bullets, pickups, or wave rewards)
- Shared loot drops visible to all session players

---

## Wave System

### Overview

Waves escalate in difficulty. Each wave has a fixed zombie count that the player must eliminate to advance. A 5-second intermission separates waves.

### Wave Loop

```
SESSION START
  -> Grant loadout
  -> Attach HUD
  -> 5s countdown ("Wave 1 starting...")
  -> WAVE LOOP:
      1. Spawn zombies (staggered over first few seconds)
      2. Combat phase (player fights until all zombies dead)
      3. Wave complete notification
      4. 5s intermission (heal, reposition)
         - HUD shows "Next wave in 5... 4... 3..."
      5. Increment wave, go to step 1
  -> SESSION END (player dies or quits)
      - Remove loadout
      - Detach HUD
      - Save stats
      - Announce final wave reached
```

### Difficulty Scaling

| Wave | Zombie Count | Zombie HP Multiplier | Zombie Speed Multiplier | Zombie Damage Multiplier |
|------|-------------|---------------------|------------------------|--------------------------|
| 1 | 5 | x1.0 | x1.0 | x1.0 |
| 2 | 7 | x1.0 | x1.0 | x1.0 |
| 3 | 9 | x1.12 | x1.0 | x1.0 |
| 5 | 13 | x1.36 | x1.025 | x1.05 |
| 10 | 23 | x1.96 | x1.15 | x1.30 |
| 15 | 33 | x2.56 | x1.275 | x1.55 |
| 20 | 43 | x3.16 | x1.40 | x1.80 |
| 25 | 53 | x3.76 | x1.525 | x2.05 |

**Formulas:**

```
zombieCount(wave) = 5 + (wave - 1) * 2
hpMultiplier(wave) = 1.0 + max(0, wave - 2) * 0.12
speedMultiplier(wave) = 1.0 + max(0, wave - 4) * 0.025
damageMultiplier(wave) = 1.0 + max(0, wave - 4) * 0.05
```

The formulas above are the source of truth. The table is illustrative (sample waves only).

Waves are uncapped. Difficulty scales indefinitely until the player is overwhelmed.

### Intermission

- Duration: 5 seconds
- Player health restored to full at intermission start
- HUD countdown displayed
- No zombies alive during intermission
- Scheduled via `HytaleServer.SCHEDULED_EXECUTOR` (1-second ticks for countdown display)

### Timing

| Task | Interval | Purpose |
|------|----------|---------|
| Wave tick | 200ms | Check remaining zombies, detect wave completion |
| HUD timer | 100ms | Update intermission countdown, wave timer |
| Spawn stagger | 500ms per batch | Avoid spawning all zombies in a single frame |

---

## Zombie Spawn Logic

### Entity Type

Zombies use a Hytale NPC entity. The specific model depends on available assets (e.g., `Trork_Grunt` or a dedicated zombie model if available in the asset pack).

### Spawning

```java
// Per-wave spawn sequence
AtomicInteger remaining = new AtomicInteger(zombieCount);
session.setSpawningComplete(false);

session.setSpawnTask(SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
    if (session.getState() == SessionState.ENDED) {
        session.cancelSpawnTask();
        return;
    }

    int toSpawn = Math.min(5, remaining.get());
    if (toSpawn <= 0) {
        session.setSpawningComplete(true);
        session.cancelSpawnTask();
        return;
    }

    world.execute(() -> {
        for (int i = 0; i < toSpawn && remaining.get() > 0; i++) {
            PurgeSpawnPoint point = selectSpawnPoint(playerPosition);
            spawnZombie(store, point, wave);
            remaining.decrementAndGet();
        }
    });
}, 0, 500, TimeUnit.MILLISECONDS));  // 500ms between batches
```

### Zombie Properties

Each zombie gets:

| Property | Source |
|----------|--------|
| Position + rotation | From selected `PurgeSpawnPoint` (with small random offset: +/-2 blocks X/Z) |
| HP | Base HP * `hpMultiplier(wave)` |
| Speed | Base speed * `speedMultiplier(wave)` via `MovementSettings` |
| Damage | Base damage * `damageMultiplier(wave)` |
| AI target | The session player (aggro on spawn) |
| Nameplate | Hidden (`Nameplate` component with empty name) |

### Zombie Tracking

`PurgeSession` maintains a `Set<Ref<EntityStore>>` of alive zombies for the current wave.

- On confirmed zombie death (combat HP reaches <= 0): remove from set, increment kill counter
- On despawn/cleanup/invalidation: remove from set, do NOT increment kill counter
- Wave complete condition: `spawningComplete && aliveZombies.isEmpty()`
- On session end: remove all remaining zombies (`store.removeEntity(ref, RemoveReason.REMOVE)`)
- Ref validity check on every wave tick (`ref.isValid()`)

### Death Detection

Two approaches (choose during implementation based on available API):

1. **Component polling (preferred):** Wave tick reads zombie HP component. If HP <= 0, count a kill. If ref is invalid, remove from tracking without awarding a kill.
2. **Event-based:** Register for entity death/removal events, filter by tracked zombie refs, and only count kills on confirmed combat deaths.

---

## HUD

### Design Principles

- Same color scheme as Parkour/Ascend HUDs (dark blue background, pink accent, light text)
- Right-side info box for core run data (scrap, player count, IP)
- Center-top for wave status (wave number, zombies remaining)
- Minimal — combat focus means less HUD clutter

### Layout

```
+------------------------------------------------------------------+
|                                                                    |
|                    WAVE 7                                          |
|               Zombies: 12/19                                       |
|                                                                    |
|                                                                    |
|                                                                    |
|                                                                    |
|                                                  +-----------+     |
|                                                  | PURGE     |     |
|                                                  | 42 scrap  |     |
|                                                  | 12 online |     |
|                                                  | play.hy.. |     |
|                                                  +-----------+     |
+------------------------------------------------------------------+
```

**Center-top block (wave status):**
- `#WaveLabel` — "WAVE 7" (large, bold, white)
- `#ZombieCountLabel` — "Zombies: 12/19" (smaller, secondary color)
- During intermission: replaces zombie count with "Next wave in 5..."

**Right-side info box (same pattern as other modules):**
- Module name ("PURGE")
- Scrap display (`#PlayerScrapValue`)
- Player count (`#PlayerCountText`)
- Server IP (`#InfoIpText`)

### UI File: `Purge_RunHud.ui`

```
$C = "../Common.ui";

$C.@PageOverlay {
  // -- Wave status (center-top) --
  Group #WaveStatusRow {
    Anchor: (Top: 30, Left: 0, Right: 0, Height: 80)
    LayoutMode: Left

    Group { FlexWeight: 1 }

    Group #WaveStatusRoot {
      Anchor: (Width: 300, Height: 80)
      LayoutMode: Top

      Label #WaveLabel {
        Anchor: (Height: 40)
        Style: (
          FontSize: 28,
          HorizontalAlignment: Center,
          RenderBold: true,
          TextColor: #e7f1f4
        )
        Text: "WAVE 1"
      }

      Label #ZombieCountLabel {
        Anchor: (Top: 4, Height: 24)
        Style: (
          FontSize: 16,
          HorizontalAlignment: Center,
          TextColor: #9fb0ba
        )
        Text: "Zombies: 0/5"
      }
    }

    Group { FlexWeight: 1 }
  }

  // -- Right info box (same pattern as Parkour/Ascend) --
  Group #InfoBoxRoot {
    Anchor: (Top: 400, Right: 20, Width: 200, Height: 160)
    Background: #0d1620(0.95)

    Group #InfoAccentBar {
      Anchor: (Top: 0, Left: 0, Width: 4, Bottom: 0)
      Background: #ec4899
    }

    Group #InfoContent {
      Anchor: (Full: 0)
      Padding: (Top: 12, Left: 16, Right: 12, Bottom: 12)
      LayoutMode: Top

      Label #ModuleNameLabel {
        Anchor: (Height: 20)
        Style: (FontSize: 14, RenderBold: true, TextColor: #ec4899)
        Text: "PURGE"
      }

      Label #PlayerScrapValue {
        Anchor: (Height: 20)
        Style: (FontSize: 13, TextColor: #62d96b)
        Text: "0 scrap"
      }

      Label #PlayerCountText {
        Anchor: (Height: 20)
        Style: (FontSize: 13, TextColor: #9fb0ba)
        Text: "0 online"
      }

      Label #InfoIpText {
        Anchor: (Height: 20)
        Style: (FontSize: 12, TextColor: #ffd166)
        Text: "play.hyvexa.com (/vote)"
      }
    }
  }
}
```

### HUD Updates

| Update | Interval | Fields |
|--------|----------|--------|
| Wave status | 200ms | `#WaveLabel.Text`, `#ZombieCountLabel.Text` |
| Intermission countdown | 1000ms | `#ZombieCountLabel.Text` (repurposed for "Next wave in X...") |
| Scrap | 5000ms | `#PlayerScrapValue.Text` |
| Player count | 5000ms | `#PlayerCountText.Text` |

Delta updates via `UICommandBuilder.set()`. Cache previous values to skip redundant sends (same pattern as `AscendHud`).

```java
// PurgeHud.java
public void updateWaveStatus(int wave, int alive, int total) {
    if (wave != lastWave || alive != lastAlive) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#WaveLabel.Text", "WAVE " + wave);
        cmd.set("#ZombieCountLabel.Text", "Zombies: " + alive + "/" + total);
        update(false, cmd);
        lastWave = wave;
        lastAlive = alive;
    }
}

public void updateIntermission(int seconds) {
    UICommandBuilder cmd = new UICommandBuilder();
    cmd.set("#ZombieCountLabel.Text", "Next wave in " + seconds + "...");
    update(false, cmd);
}
```

---

## Session Lifecycle

### PurgeSession State

```java
public class PurgeSession {
    private final Set<UUID> participantIds = ConcurrentHashMap.newKeySet(); // size 1 in solo
    private final ConcurrentHashMap<UUID, Ref<EntityStore>> participantRefs = new ConcurrentHashMap<>();
    private int currentWave = 0;
    private int totalKills = 0;
    private SessionState state;  // COUNTDOWN, SPAWNING, COMBAT, INTERMISSION, ENDED
    private volatile boolean spawningComplete = false;
    private final Set<Ref<EntityStore>> aliveZombies = ConcurrentHashMap.newKeySet();
    private ScheduledFuture<?> waveTick;
    private ScheduledFuture<?> spawnTask;
    private long sessionStartTime;
}
```

### State Machine

```
COUNTDOWN (initial 5s)
  -> SPAWNING (zombies being placed)
  -> COMBAT (all zombies spawned, player fighting)
  -> INTERMISSION (wave cleared, 5s rest)
  -> SPAWNING (next wave)
  ...
  -> ENDED (player died or quit)
```

### Cleanup

**On player death:**
1. Set state to `ENDED`
2. Cancel all scheduled tasks (`waveTick`, `spawnTask`)
3. Remove remaining zombies from world
4. Show final stats ("Wave 12 - 87 kills")
5. Remove loadout
6. Detach HUD
7. Save stats to DB

**On player disconnect:**
1. Same as death cleanup
2. Additionally evict from `PurgePlayerStore` and `PurgeScrapStore`

**On player leaving Purge world (still connected):**
1. End active purge session + remove spawned zombies + detach HUD + remove loadout
2. Keep player caches warm (do not evict `PurgePlayerStore` / `PurgeScrapStore`)
3. Full eviction still happens only on disconnect

**On player quit (voluntary /purge stop):**
1. Same as death cleanup

All cleanup steps wrapped individually in try/catch (same `runSafe` pattern as other modules).

---

## Data Persistence

### DB Tables

**`purge_player_stats`** — Per-player lifetime stats:

| Column | Type | Description |
|--------|------|-------------|
| `uuid` | VARCHAR(36) PK | Player UUID |
| `best_wave` | INT DEFAULT 0 | Highest wave reached |
| `total_kills` | INT DEFAULT 0 | Lifetime zombie kills |
| `total_sessions` | INT DEFAULT 0 | Total games played |

**`purge_player_scrap`** — Per-player Purge currency balance:

| Column | Type | Description |
|--------|------|-------------|
| `uuid` | VARCHAR(36) PK | Player UUID |
| `scrap` | BIGINT DEFAULT 0 | Current Scrap balance (Purge-only currency) |
| `lifetime_scrap_earned` | BIGINT DEFAULT 0 | Total Scrap earned over time |

**`purge_spawn_points`** — Admin-defined spawn points (see [Spawn Points](#spawn-points)).

### PurgePlayerStore / PurgeScrapStore

Same lazy-load/evict pattern as `AscendPlayerStore`:
- Load on first session start (not on connect — player might not play Purge)
- Evict on disconnect
- Write on session end (immediate, not debounced — sessions are infrequent)

---

## Purge Currency (Scrap)

Purge uses a dedicated mode currency named **Scrap** (can be renamed to **Bolt** later if desired).
This currency is **not shared** with other game modes.

### Scrap Rewards

Scrap awarded on session end based on waves survived:

| Waves Survived | Scrap Reward |
|----------------|------------|
| 1-4 | 0 |
| 5-9 | 20 |
| 10-14 | 60 |
| 15-19 | 120 |
| 20-24 | 200 |
| 25+ | 300 + 50 per 5 waves beyond 25 |

Scrap exists only for Purge progression and does not affect global cross-mode economies.

### Integration Points

- `PurgeScrapStore.getInstance().addScrap(uuid, amount)` on session end
- Scrap display on HUD updated every 5 seconds (`#PlayerScrapValue.Text`)
- Optional OP tooling: `/purge scrap <check|add|set|remove> <player> [amount]`
- Purge module does not call cross-mode currency stores for gameplay rewards

---

## Co-op Considerations (Future)

The architecture is designed so co-op can be added later without major rewrites:

| Concept | Solo (Now) | Co-op (Future) |
|---------|-----------|----------------|
| Session | One player per `PurgeSession` | Multiple players per `PurgeSession` |
| Spawn point distance | Distance from single player | Distance from nearest player |
| Wave zombie count | Based on wave number | Scaled by player count (e.g., `+50%` per extra player) |
| Scrap rewards | Per player | Per player (same formula) |
| HUD | Shows personal stats | Shows shared wave + personal kills |
| Loadout | Granted to one player | Granted to all session players |
| Session end | Player dies = game over | Last player dies = game over |

Key design decision: `PurgeSession` holds a `Set<UUID>` of participants (size 1 for solo). All spawn distance checks, kill tracking, and cleanup iterate over participants. This means the co-op path is adding players to the set and adjusting scaling formulas — not restructuring the session model.
