# Purge Mode - Agent Working Reference

Quick-access reference for working on `hyvexa-purge`. Read this if needed instead of re-scanning the codebase.

---

## File Map

All under `hyvexa-purge/src/main/java/io/hyvexa/purge/`:

| File | Role | Key detail |
|------|------|------------|
| `HyvexaPurgePlugin.java` | Plugin entry point | Owns all managers, events, loadout methods |
| `manager/PurgeSessionManager.java` | Session lifecycle | Start/stop/cleanup, scrap rewards, persist stats |
| `manager/PurgeWaveManager.java` | Core gameplay loop | Spawn, combat, intermission, death detection |
| `manager/PurgeSpawnPointManager.java` | Spawn point CRUD | DB-backed, weighted random selection |
| `hud/PurgeHudManager.java` | HUD routing | Attach/detach, routes wave/scrap updates to PurgeHud |
| `hud/PurgeHud.java` | HUD rendering | CustomUIHud subclass, dirty-check cache on all updates |
| `data/PurgeSession.java` | Runtime session state | Volatile fields, AtomicInteger kills, task handles |
| `data/PurgeScrapStore.java` | Scrap currency (singleton) | Lazy-load, immediate writes, dual cache (scrap + lifetime) |
| `data/PurgePlayerStore.java` | Player stats (singleton) | Lazy-load, immediate writes, single cache |
| `data/PurgePlayerStats.java` | Stats POJO | bestWave, totalKills, totalSessions |
| `data/PurgeSpawnPoint.java` | Record | `record(int id, double x, double y, double z, float yaw)` |
| `data/SessionState.java` | Enum | COUNTDOWN, SPAWNING, COMBAT, INTERMISSION, ENDED |
| `manager/PurgeWaveConfigManager.java` | Wave config CRUD | DB-backed wave definitions (slow/normal/fast counts) |
| `manager/PurgeSettingsManager.java` | Session settings | DB-backed key-value settings |
| `data/PurgeWaveDefinition.java` | Record | `record(int wave, int slowCount, int normalCount, int fastCount)` |
| `data/PurgeZombieVariant.java` | Enum | SLOW/NORMAL/FAST with NPC type names |
| `data/PurgeLocation.java` | Record | Location data for spawn points |
| `command/PurgeCommand.java` | `/purge` | start, stop, stats, admin |
| `command/PurgeSpawnCommand.java` | `/purgespawn` | add, remove, list, clear (OP only) |
| `ui/PurgeAdminIndexPage.java` | Admin menu | Tab navigation for admin pages |
| `ui/PurgeWaveAdminPage.java` | Wave config UI | Add/edit/remove wave definitions |
| `ui/PurgeSpawnAdminPage.java` | Spawn point UI | Manage spawn points via GUI |
| `ui/PurgeSettingsAdminPage.java` | Settings UI | Configure session settings |

**UI file:** `hyvexa-purge/src/main/resources/Common/UI/Custom/Pages/Purge_RunHud.ui`

**Zombie NPC roles (resources):**
All variants extend the **vanilla** `Template_Aggressive_Zombies` (Hytale built-in). Do NOT use custom abstract templates — they fail builder validation.
- `Server/NPC/Roles/Purge/Purge_Zombie.json` — default variant (NORMAL speed)
- `Server/NPC/Roles/Purge/Purge_Zombie_Normal.json` — same as Purge_Zombie (fallback)
- `Server/NPC/Roles/Purge/Purge_Zombie_Slow.json` — `MaxWalkSpeed: 8`
- `Server/NPC/Roles/Purge/Purge_Zombie_Fast.json` — `MaxWalkSpeed: 11`

Each variant sets: `DropList: "Empty"`, `Appearance: "Zombie"`, `MaxHealth: 49`, `_InteractionVars` (18 physical melee damage). Only Slow/Fast override `MaxWalkSpeed`.

---

## Manager Dependency Graph

```
Plugin creates in this order:
  1. spawnPointManager = new PurgeSpawnPointManager()        // loads DB
  2. hudManager = new PurgeHudManager()
  3. waveManager = new PurgeWaveManager(spawnPointManager, hudManager)
  4. sessionManager = new PurgeSessionManager(spawnPointManager, waveManager, hudManager)
       └── constructor calls waveManager.setSessionManager(this)  // breaks circular dep
```

**Circular dependency:** WaveManager needs SessionManager to call `stopSession()` on player death. SessionManager needs WaveManager to call `removeAllZombies()`. Resolved via setter injection in SessionManager's constructor.

---

## Session Lifecycle (state machine)

```
/purge start
  → COUNTDOWN (5s, 1s ticks, chat messages)
  → SPAWNING (staggered batches of 5 every 500ms)
  → COMBAT (all spawned, 200ms tick polling ref.isValid())
  → INTERMISSION (5s countdown, chat + HUD updates)
  → back to SPAWNING (next wave)
  ...
  → ENDED (player death / /purge stop / disconnect / world-leave / shutdown)
```

**On ENDED:** cancelAllTasks → removeAllZombies → hideRunHud → persistResults → removeLoadout → send summary. Each step wrapped in `runSafe()` (try/catch).

---

## Scheduled Tasks per Session

| Task | Field | Interval | What it does | Cancelled when |
|------|-------|----------|-------------|----------------|
| Countdown | `spawnTask` (reused) | 1000ms | Chat countdown, HUD intermission text | Countdown hits 0 |
| Spawn batches | `spawnTask` | 500ms | Spawn up to 5 zombies per tick | All spawned |
| Wave tick | `waveTick` | 200ms | Poll zombie deaths, update HUD, check wave complete, check player death | Wave complete or ENDED |
| Intermission | `intermissionTask` | 1000ms | Countdown chat + HUD | Countdown hits 0 or ENDED |

**HUD slow tick** (plugin-level, not per-session): 5000ms — updates player count, gems, scrap for all connected players.

---

## Constants

**Gameplay (PurgeWaveManager):**
| Constant | Value | Controls |
|----------|-------|----------|
| _(no constant)_ | `candidateNpcTypes()` | Variant-specific NPC type + fallback |
| `WAVE_TICK_INTERVAL_MS` | `200` | Death detection polling rate |
| `SPAWN_STAGGER_MS` | `500` | Delay between spawn batches |
| `SPAWN_BATCH_SIZE` | `5` | Zombies per batch |
| `INTERMISSION_SECONDS` | `5` | Rest between waves |
| `COUNTDOWN_SECONDS` | `5` | Pre-game countdown |
| `SPAWN_RANDOM_OFFSET` | `2.0` | +/- blocks X/Z offset on spawn |
| `MIN_SPAWN_DISTANCE` | `15.0` | (SpawnPointManager) Min horizontal distance from player |

**Loadout (HyvexaPurgePlugin):**
| Constant | Value |
|----------|-------|
| `ITEM_AK47` | `"AK47"` |
| `ITEM_BULLET` | `"Bullet"` |
| `STARTING_BULLET_COUNT` | `120` |
| `SLOT_PRIMARY_WEAPON` | `0` |
| `SLOT_PRIMARY_AMMO` | `1` |
| `SLOT_SERVER_SELECTOR` | `8` |

---

## Scaling Formulas

```
zombieCount(wave)     = 5 + (wave - 1) * 2
hpMultiplier(wave)    = 1.0 + max(0, wave - 2) * 0.12
speedMultiplier(wave) = 1.0 + max(0, wave - 4) * 0.025
damageMultiplier(wave) = 1.0 + max(0, wave - 4) * 0.05
```

| Wave | Zombies | HP mult | Speed mult | Dmg mult |
|------|---------|---------|------------|----------|
| 1 | 5 | 1.00 | 1.00 | 1.00 |
| 5 | 13 | 1.36 | 1.025 | 1.05 |
| 10 | 23 | 1.96 | 1.15 | 1.30 |
| 15 | 33 | 2.56 | 1.275 | 1.55 |
| 20 | 43 | 3.16 | 1.40 | 1.80 |
| 25 | 53 | 3.76 | 1.525 | 2.05 |

**HP scaling is applied on spawn** (`EntityStatMap` max-health modifier).  
**Speed/damage formulas are still not auto-wired** (speed currently comes from role variants).

---

## Scrap Reward Brackets

```
Waves 1-4:   0 scrap
Waves 5-9:   20 scrap
Waves 10-14: 60 scrap
Waves 15-19: 120 scrap
Waves 20-24: 200 scrap
Waves 25+:   300 + 50 * ((wavesReached - 25) / 5)
```

---

## DB Tables

```sql
purge_spawn_points (
  id INT AUTO_INCREMENT PRIMARY KEY,
  x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,
  yaw FLOAT NOT NULL DEFAULT 0
)

purge_player_stats (
  uuid VARCHAR(36) NOT NULL PRIMARY KEY,
  best_wave INT NOT NULL DEFAULT 0,
  total_kills INT NOT NULL DEFAULT 0,
  total_sessions INT NOT NULL DEFAULT 0
)

purge_player_scrap (
  uuid VARCHAR(36) NOT NULL PRIMARY KEY,
  scrap BIGINT NOT NULL DEFAULT 0,
  lifetime_scrap_earned BIGINT NOT NULL DEFAULT 0
)
```

All tables use `ENGINE=InnoDB`, created with `CREATE TABLE IF NOT EXISTS`, queries use `DatabaseManager.applyQueryTimeout(stmt)`, upserts use `INSERT ... ON DUPLICATE KEY UPDATE`.

---

## HUD Element IDs

| ID | Type | Updated by | Default |
|----|------|-----------|---------|
| `#WaveStatusRow` | Group | `setWaveStatusVisible()` | `Visible: false` |
| `#WaveLabel` | Label | `updateWaveStatus()` | `"WAVE 1"` |
| `#ZombieCountLabel` | Label | `updateWaveStatus()` / `updateIntermission()` | `"Zombies: 0/5"` |
| `#PlayerGemsValue` | Label | `updateGems()` | `"0"` |
| `#PlayerScrapValue` | Label | `updateScrap()` | `"0 scrap"` |
| `#PlayerCountText` | Label | `updatePlayerCount()` | `"0"` |

**Color scheme:** Green accent `#4ade80`, gem green `#62d96b`, scrap orange `#f59e0b`, text `#e7f1f4`, secondary `#9fb0ba`, background `#0d1620(0.95)`.

---

## Event Handlers (HyvexaPurgePlugin)

**PlayerReadyEvent** (player enters Purge world):
1. Validate ref, world is Purge, get PlayerRef + Player
2. Add to `playersInPurgeWorld`
3. `hudManager.attach(playerRef, player)` — base HUD, wave status hidden
4. `giveServerSelector(player)` — no weapon/ammo (session handles that)
5. `DiscordLinkStore.checkAndRewardGems()`

**AddPlayerToWorldEvent** (world change):
- If entering Purge world: add to tracking set
- If leaving Purge world: remove from set, `sessionManager.cleanupPlayer()`, `hudManager.removePlayer()`

**PlayerDisconnectEvent:**
- Remove from tracking, cleanup session, remove HUD, evict all stores (Gem, Discord, PurgePlayer, PurgeScrap)

---

## Threading Rules

| Operation | Thread | Safe? |
|-----------|--------|-------|
| `npcPlugin.spawnNPC()` | Must be world thread (`world.execute()`) | YES |
| `store.removeEntity()` | Must be world thread (`world.execute()`) | YES |
| `ref.isValid()` reads | Any thread | YES |
| `store.getComponent()` reads | Any thread | YES |
| `UICommandBuilder` HUD updates | Any thread | YES (packet-based) |
| `store.addComponent()` / `removeComponent()` | Must NOT be inside EntityTickingSystem.tick() | N/A (not used in purge) |

---

## Known TODOs / Gaps

| Gap | Location | Impact |
|-----|----------|--------|
| Wave-based speed/damage scaling | `PurgeWaveManager` | HP scaling applied per wave; speed comes from static role variants (Slow/Normal/Fast); damage scaling not yet wired |
| Player healing on intermission | `PurgeWaveManager.startIntermission()` line ~277 | No healing between waves — API unknown |
| Player death detection | `PurgeWaveManager.startWaveTick()` | Uses 200ms `ref.isValid()` polling, not event-based |
| Ammo resupply | Not implemented | Player starts with 120 bullets, no refills |

---

## Zombie Spawn Flow (detail)

1. `buildSpawnQueue(wave)` creates interleaved list of `PurgeZombieVariant` (NORMAL, SLOW, FAST)
2. `startSpawning(session, spawnQueue)` scheduled at 500ms intervals, batches of 5
3. Each tick: read player position, pick spawn point via `selectSpawnPoint(playerX, playerZ)`
4. Spawn point selection: filter >= 15 blocks away, weight by distance², random pick
5. Apply +/- 2 block random X/Z offset to chosen point
6. `candidateNpcTypes(variant)` returns NPC type + fallback (e.g. SLOW -> `["Purge_Zombie_Slow", "Purge_Zombie"]`)
7. `world.execute(() -> npcPlugin.spawnNPC(store, npcType, "", position, rotation))`
8. Extract `Ref<EntityStore>` from result via reflection (getFirst/getLeft/getKey/first/left pattern)
9. Apply wave HP scaling via `EntityStatMap` modifier, set nameplate to `"HP / HP"`
10. Force aggro: `npcEntity.getRole().setMarkedTarget("LockedTarget", playerRef)` + set state "Angry"
11. Add to `session.aliveZombies`
12. When all spawned: set `spawningComplete = true`, transition SPAWNING → COMBAT

### NPC Role Template Rules

Zombie variants use Hytale's `Variant` / `Abstract` template system:
```json
{
  "Type": "Variant",
  "Reference": "Template_Aggressive_Zombies",
  "Modify": { "MaxHealth": 49, "MaxWalkSpeed": 8, ... }
}
```

**Critical**: Always reference **vanilla** abstract templates (e.g. `Template_Aggressive_Zombies`). Custom abstract templates fail builder validation at spawn time. The `Modify` block overrides properties from the vanilla template.

---

## Key Methods to Know

**Starting a session:**
```
PurgeSessionManager.startSession(UUID, Ref<EntityStore>)
  → guards (active session? spawn points?)
  → HyvexaPurgePlugin.grantLoadout(player)
  → hudManager.showRunHud(playerId)
  → waveManager.startCountdown(session)
```

**Stopping a session:**
```
PurgeSessionManager.stopSession(UUID, String reason)
  → session.setState(ENDED)
  → session.cancelAllTasks()
  → waveManager.removeAllZombies(session)
  → hudManager.hideRunHud(playerId)
  → persistResults(playerId, session)  // stats + scrap
  → HyvexaPurgePlugin.removeLoadout(player)
  → send summary message
```

**Detecting zombie death:**
```
PurgeWaveManager.checkZombieDeaths(session)  // called every 200ms
  → iterate session.getAliveZombies()
  → if ref == null || !ref.isValid(): dead → incrementKills(), remove from set
  → if spawningComplete && aliveZombies.isEmpty(): onWaveComplete()
```
