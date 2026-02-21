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
| `manager/PurgeWaveConfigManager.java` | Wave config CRUD | DB-backed wave definitions (variant counts + spawn pacing) |
| `manager/PurgeSettingsManager.java` | Session settings | DB-backed start/exit teleport locations |
| `manager/PurgeUpgradeManager.java` | Upgrade effects | Applies/reverts run upgrades, regen task lifecycle |
| `hud/PurgeHudManager.java` | HUD routing | Attach/detach, routes wave/scrap updates to PurgeHud |
| `hud/PurgeHud.java` | HUD rendering | CustomUIHud subclass, dirty-check cache on all updates |
| `data/PurgeSession.java` | Runtime session state | Volatile fields, AtomicInteger kills, task handles |
| `data/SessionState.java` | Enum | COUNTDOWN, SPAWNING, COMBAT, UPGRADE_PICK, INTERMISSION, ENDED |
| `data/PurgeWaveDefinition.java` | Record | `record(int waveNumber, int slowCount, int normalCount, int fastCount, int spawnDelayMs, int spawnBatchSize)` |
| `data/PurgeUpgradeState.java` | Upgrade stacks | Per-session stacks by upgrade type |
| `data/PurgeUpgradeType.java` | Upgrade enum | SWIFT_FEET, IRON_SKIN, AMMO_CACHE, SECOND_WIND, THICK_HIDE, SCAVENGER |
| `data/PurgeScrapStore.java` | Scrap currency (singleton) | Lazy-load, immediate writes, dual cache (scrap + lifetime) |
| `data/PurgePlayerStore.java` | Player stats (singleton) | Lazy-load, immediate writes, single cache |
| `data/PurgePlayerStats.java` | Stats POJO | bestWave, totalKills, totalSessions |
| `data/PurgeSpawnPoint.java` | Record | `record(int id, double x, double y, double z, float yaw)` |
| `data/PurgeZombieVariant.java` | Enum | SLOW/NORMAL/FAST with baseHealth, baseDamage, speedMultiplier |
| `data/PurgeLocation.java` | Record | Location data for spawn points |
| `command/PurgeCommand.java` | `/purge` | start, stop, stats, admin |
| `command/PurgeSpawnCommand.java` | `/purgespawn` | add, remove, list, clear (OP only) |
| `ui/PurgeAdminIndexPage.java` | Admin menu | Tab navigation for admin pages |
| `ui/PurgeWaveAdminPage.java` | Wave config UI | Add/edit/remove wave definitions |
| `ui/PurgeSpawnAdminPage.java` | Spawn point UI | Manage spawn points via GUI |
| `ui/PurgeSettingsAdminPage.java` | Settings UI | Configure session settings |
| `ui/PurgeUpgradePickPage.java` | Upgrade UI | Mid-run upgrade selection popup between waves |

**UI file:** `hyvexa-purge/src/main/resources/Common/UI/Custom/Pages/Purge_RunHud.ui`

**Zombie spawning (code-driven, no custom JSON templates):**
All zombies are spawned as vanilla `"Zombie"` NPC type. HP, damage, speed, and drops are overridden in code after spawning. See "NPC Role Template Limitations" section below for why.

Customization lives in `PurgeZombieVariant` enum:
- `SLOW` — baseHealth: 49, baseDamage: 20, speedMultiplier: 8/9
- `NORMAL` — baseHealth: 49, baseDamage: 20, speedMultiplier: 1.0
- `FAST` — baseHealth: 49, baseDamage: 20, speedMultiplier: 11/9

Post-spawn overrides (in `PurgeWaveManager.spawnZombie()`):
1. **HP**: `EntityStatMap` modifier (`PURGE_HP_MODIFIER`, multiplicative wave scaling)
2. **Damage**: `PurgeDamageModifierSystem` intercepts damage events, forces per-variant `baseDamage`
3. **Speed**: Reflection on `MotionControllerWalk.horizontalSpeedMultiplier` (non-final protected field)
4. **Drops**: Reflection on `Role.dropListId` (final field) → set to `"Empty"`

---

## Manager Dependency Graph

```
Plugin creates in this order:
  1. spawnPointManager = new PurgeSpawnPointManager()              // loads DB
  2. settingsManager = new PurgeSettingsManager()                  // start/exit locations
  3. waveConfigManager = new PurgeWaveConfigManager()              // wave definitions
  4. hudManager = new PurgeHudManager()
  5. waveManager = new PurgeWaveManager(spawnPointManager, waveConfigManager, hudManager)
  6. sessionManager = new PurgeSessionManager(spawnPointManager, waveManager, hudManager, settingsManager)
       └── constructor calls waveManager.setSessionManager(this)   // breaks circular dep
  7. upgradeManager = new PurgeUpgradeManager()
  8. waveManager.setUpgradeManager(upgradeManager)
  9. sessionManager.setUpgradeManager(upgradeManager)
```

**Circular dependency:** WaveManager needs SessionManager to call `stopSession()` on death/victory. SessionManager needs WaveManager to remove live zombies during cleanup. Resolved with setter injection.

---

## Session Lifecycle (state machine)

```
/purge start
  → COUNTDOWN (5s, 1s ticks, chat messages)
  → SPAWNING (wave-defined batches: `spawnBatchSize` every `spawnDelayMs`)
  → COMBAT (all spawned, 200ms tick polling ref.isValid())
  → UPGRADE_PICK (after wave clear, if next wave exists)
  → INTERMISSION (5s countdown, chat + HUD updates)
  → back to SPAWNING (next wave)
  ...
  → ENDED (player death / /purge stop / disconnect / world-leave / shutdown)
```

**On ENDED (current flow):**
1. `stopSession()` removes active session, sets `ENDED`, cancels tasks, hides run HUD, persists stats/scrap.
2. `runWorldCleanup()` resolves player world and schedules world-safe cleanup (`world.execute`) with inline fallback.
3. `performWorldCleanup()` does: remove zombies, revert+cleanup upgrades, heal player to full, remove run loadout, optional exit teleport (`voluntary stop`/`victory`), then send summary.

---

## Scheduled Tasks per Session

| Task | Field | Interval | What it does | Cancelled when |
|------|-------|----------|-------------|----------------|
| Countdown | `spawnTask` (reused) | 1000ms | Chat countdown, HUD intermission text | Countdown hits 0 |
| Spawn batches | `spawnTask` | `wave.spawnDelayMs()` | Spawn up to `wave.spawnBatchSize()` zombies per tick | All spawned |
| Wave tick | `waveTick` | 200ms | Poll zombie deaths, update HUD, check wave complete, check player death | Wave complete or ENDED |
| Intermission | `intermissionTask` | 1000ms | Countdown chat + HUD | Countdown hits 0 or ENDED |

**HUD slow tick** (plugin-level, not per-session): 5000ms — computes player count once (`Universe.get().getPlayers().size()`), then updates player count, vexa, and scrap for each attached purge HUD.

---

## Constants

**Gameplay (PurgeWaveManager):**
| Constant | Value | Controls |
|----------|-------|----------|
| `VANILLA_ZOMBIE_NPC_TYPE` | `"Zombie"` | Vanilla NPC type used for all variants |
| `WAVE_TICK_INTERVAL_MS` | `200` | Death detection polling rate |
| `INTERMISSION_SECONDS` | `5` | Rest between waves |
| `COUNTDOWN_SECONDS` | `5` | Pre-game countdown |
| `SPAWN_RANDOM_OFFSET` | `2.0` | +/- blocks X/Z offset on spawn |
| `MIN_SPAWN_DISTANCE` | `15.0` | (SpawnPointManager) Min horizontal distance from player |

**Wave config defaults (PurgeWaveConfigManager):**
| Constant | Value | Controls |
|----------|-------|----------|
| `DEFAULT_SLOW_COUNT` | `0` | New wave default |
| `DEFAULT_NORMAL_COUNT` | `5` | New wave default |
| `DEFAULT_FAST_COUNT` | `0` | New wave default |
| `DEFAULT_SPAWN_DELAY_MS` | `500` | New wave spawn interval default |
| `DEFAULT_SPAWN_BATCH_SIZE` | `5` | New wave batch size default |

**Loadout (HyvexaPurgePlugin):**
| Constant | Value |
|----------|-------|
| `ITEM_ORB_BLUE` | `"Purge_Orb_Blue"` |
| `ITEM_ORB_ORANGE` | `"Purge_Orb_Orange"` |
| `ITEM_ORB_RED` | `"Purge_Orb_Red"` |
| `ITEM_AK47` | `"AK47"` |
| `ITEM_BULLET` | `"Bullet"` |
| `STARTING_BULLET_COUNT` | `120` |
| `SLOT_ORB_BLUE` | `0` |
| `SLOT_ORB_ORANGE` | `1` |
| `SLOT_PRIMARY_WEAPON` | `0` |
| `SLOT_PRIMARY_AMMO` | `1` |
| `SLOT_QUIT_ORB` | `8` |
| `SLOT_SERVER_SELECTOR` | `8` |

---

## Scaling Formulas

```
zombieCount(wave) = max(0, slowCount) + max(0, normalCount) + max(0, fastCount)
spawnDelay(wave)  = max(100, spawnDelayMs)
spawnBatch(wave)  = max(1, spawnBatchSize)
hpMultiplier(w)   = 1.0 + max(0, w - 2) * 0.12

// Upgrade multipliers (if selected during run):
thickHideHealthMult(stacks) = max(0.2, 1.0 - stacks * 0.08)
scrapMult(stacks)           = 1.0 + stacks * 0.25
```

**Notes:**
- Zombie counts and spawn pacing are fully data-driven from `purge_waves`.
- WaveManager currently only defines base HP scaling (`hpMultiplier`).
- No wave-native speed/damage multiplier formula is wired in PurgeWaveManager.
- Thick Hide applies as an additional health multiplier on spawned zombies.

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

purge_waves (
  wave_number INT NOT NULL PRIMARY KEY,
  slow_count INT NOT NULL DEFAULT 0,
  normal_count INT NOT NULL DEFAULT 0,
  fast_count INT NOT NULL DEFAULT 0,
  spawn_delay_ms INT NOT NULL DEFAULT 500,
  spawn_batch_size INT NOT NULL DEFAULT 5
)

purge_settings (
  id INT NOT NULL PRIMARY KEY,
  start_x DOUBLE NULL, start_y DOUBLE NULL, start_z DOUBLE NULL,
  start_rot_x FLOAT NULL, start_rot_y FLOAT NULL, start_rot_z FLOAT NULL,
  stop_x DOUBLE NULL, stop_y DOUBLE NULL, stop_z DOUBLE NULL,
  stop_rot_x FLOAT NULL, stop_rot_y FLOAT NULL, stop_rot_z FLOAT NULL
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

All tables use `ENGINE=InnoDB`, are created with `CREATE TABLE IF NOT EXISTS`, and use `DatabaseManager.applyQueryTimeout(stmt)`. `purge_settings` and player stores use upsert (`INSERT ... ON DUPLICATE KEY UPDATE`). `purge_waves` also has migration guards for `spawn_delay_ms` and `spawn_batch_size`.

---

## HUD Element IDs

| ID | Type | Updated by | Default |
|----|------|-----------|---------|
| `#WaveStatusRow` | Group | `setWaveStatusVisible()` | `Visible: false` |
| `#WaveLabel` | Label | `updateWaveStatus()` | `"WAVE 1"` |
| `#ZombieCountLabel` | Label | `updateWaveStatus()` / `updateIntermission()` | `"Zombies: 0/5"` |
| `#PlayerHealthLabel` | Label | `updatePlayerHealth()` | `"HP: 100 / 100"` |
| `#PlayerVexaValue` | Label | `updateVexa()` | `"0"` |
| `#PlayerScrapValue` | Label | `updateScrap()` | `"0 scrap"` |
| `#PlayerCountText` | Label | `updatePlayerCount()` | `"0"` |

**Color scheme:** Green accent `#4ade80`, vexa green `#62d96b`, scrap orange `#f59e0b`, text `#e7f1f4`, secondary `#9fb0ba`, background `#0d1620(0.95)`.

---

## Event Handlers (HyvexaPurgePlugin)

**PlayerReadyEvent** (player enters Purge world):
1. Validate ref, world is Purge, get PlayerRef + Player
2. `hudManager.attach(playerRef, player)` — base HUD, wave status hidden
3. Clear inventory and apply idle base orbs
4. `DiscordLinkStore.checkAndRewardVexa()`

**AddPlayerToWorldEvent** (world change):
- If entering Purge world: idempotently ensure HUD is attached and idle base orbs exist
- If leaving Purge world: `sessionManager.cleanupPlayer()` (stop reason `"disconnect"`), `hudManager.removePlayer()`

**PlayerDisconnectEvent:**
- Cleanup session, remove HUD, evict all stores (Vexa, Discord, PurgePlayer, PurgeScrap)

---

## Command/Admin Guardrails

- `/purge` and `/purgespawn` both short-circuit if player world is unresolved: `"Could not resolve your world."`
- Spawn and wave management are DB-required surfaces:
  - `PurgeSpawnPointManager.isPersistenceAvailable()` / `getPersistenceDisabledMessage()`
  - `PurgeWaveConfigManager.isPersistenceAvailable()` / `getPersistenceDisabledMessage()`
- Spawn/Wave admin UI pages and `/purgespawn` handlers return explicit DB-unavailable messages instead of pretending success.

---

## Threading Rules

| Operation | Thread | Safe? |
|-----------|--------|-------|
| `npcPlugin.spawnNPC()` | Must be world thread (`world.execute()`) | YES |
| `store.removeEntity()` | Must be world thread (`world.execute()`) | YES |
| `ref.isValid()` reads | Any thread | YES |
| `store.getComponent()` reads | Any thread | YES |
| `UICommandBuilder` HUD updates | Any thread | YES (packet-based) |
| `store.addComponent()` (`Teleport`) | Must NOT be inside EntityTickingSystem.tick() | Used for start/exit teleports in session flow |

---

## Known TODOs / Gaps

| Gap | Location | Impact |
|-----|----------|--------|
| Wave-native damage scaling | `PurgeZombieVariant` / `PurgeDamageModifierSystem` | Damage is flat per-variant (20f for all). No wave-based damage curve yet |
| Player healing on intermission | `PurgeWaveManager.startIntermission()` | No per-intermission heal is wired (explicit TODO in code) |
| Player death detection | `PurgeWaveManager.startWaveTick()` / `updatePlayerHealthHud()` | Hybrid polling model (200ms session tick + stat checks), not event-driven |
| Automatic ammo refill between waves | Not implemented | Ammo increases only via `AMMO_CACHE` upgrade (+60), no baseline inter-wave refill |

---

## Zombie Spawn Flow (detail)

1. `buildSpawnQueue(wave)` creates interleaved list of `PurgeZombieVariant` (NORMAL, SLOW, FAST)
2. `startSpawning(session, spawnQueue, wave)` scheduled at `wave.spawnDelayMs()` intervals, batches of `wave.spawnBatchSize()`
3. Each tick: read player position, pick spawn point via `selectSpawnPoint(playerX, playerZ)`
4. Spawn point selection: filter >= 15 blocks away, weight by distance², random pick
5. Apply +/- 2 block random X/Z offset to chosen point
6. `world.execute(() -> npcPlugin.spawnNPC(store, "Zombie", "", position, rotation))`
7. Extract `Ref<EntityStore>` from result via reflection (getFirst/getLeft/getKey/first/left pattern)
8. `session.addAliveZombie(entityRef, variant)` — tracks ref + variant for damage lookup
9. `applyZombieStats()` — wave HP scaling via `EntityStatMap` modifier, set nameplate to `"HP / HP"`
10. `applySpeedMultiplier()` — reflection on `MotionControllerWalk.horizontalSpeedMultiplier`
11. `clearDropList()` — reflection on `Role.dropListId` → `"Empty"`
12. Force aggro: `npcEntity.getRole().setMarkedTarget("LockedTarget", playerRef)` + set state "Angry"
13. When all spawned: set `spawningComplete = true`, transition SPAWNING → COMBAT
14. Wave clear path: `onWaveComplete()` → `UPGRADE_PICK` page (if next wave exists) → INTERMISSION → next wave

### NPC Role Template Limitations (Hytale Engine)

**Plugin-defined Variant templates DO NOT WORK.** Any JSON file in a plugin's asset pack using `"Type": "Variant"` with `"Reference": "Template_Aggressive_Zombies"` (or any vanilla Abstract template) will fail builder validation at load time:

```
IllegalStateException: Builder Purge_Zombie_Slow failed validation!
```

**What was tried and failed:**
1. Minimal `Modify` block (just `MaxHealth`) — still fails
2. Matching vanilla `Zombie.json` structure exactly (with `Parameters`, `NameTranslationKey`, `IsMemory`) — still fails
3. Stripping all optional fields — still fails

**Root cause:** `BuilderRoleVariant.validate()` delegates to the referenced Abstract template's validate method. The Abstract template's validation runs in a context that plugin variants cannot satisfy (likely related to asset loading order or scope restrictions).

**Workaround:** Spawn vanilla NPC types directly (e.g. `"Zombie"`) and apply all customizations in code:
- HP: `EntityStatMap` modifiers (public API)
- Speed: Reflection on `MotionControllerWalk.horizontalSpeedMultiplier` (protected, non-final)
- Drops: Reflection on `Role.dropListId` (protected, final)
- Damage: Intercept via `DamageEventSystem` and override `event.setAmount()`

**Vanilla zombie reference** (from `Assets.zip`, `Server/NPC/Roles/Undead/Zombie/Zombie.json`):
- HP: 49, Melee damage: 18 physical, Walk speed: 9 (from Abstract template)
- Drop list: `"Drop_Zombie"`, Appearance: `"Zombie"`

---

## Key Methods to Know

**Starting a session:**
```
PurgeSessionManager.startSession(UUID, Ref<EntityStore>)
  → guards (active session? spawn points? configured waves?)
  → HyvexaPurgePlugin.grantLoadout(player)
  → optional teleportToConfiguredStart(playerRef, store)
  → hudManager.showRunHud(playerId)
  → waveManager.startCountdown(session)
```

**Stopping a session:**
```
PurgeSessionManager.stopSession(UUID, String reason)
  → remove active session + DamageBypassRegistry.remove(playerId)
  → session.setState(ENDED)
  → session.cancelAllTasks()
  → hudManager.hideRunHud(playerId)
  → persistResults(playerId, session)  // stats + scrap
  → runWorldCleanup(...)
       → performWorldCleanup(...)
            → waveManager.removeAllZombies(session)
            → upgradeManager.revertAllUpgrades(...) + cleanupPlayer(playerId)
            → heal player to full
            → HyvexaPurgePlugin.removeLoadout(player)
            → optional teleportToConfiguredExit(...) for voluntary stop/victory
            → send summary message
```

**Detecting zombie death:**
```
PurgeWaveManager.startWaveTick(session)  // every 200ms
  → check playerRef validity (invalid => stopSession("death"))
  → checkZombieDeaths(session):
       if zombie ref == null || !ref.isValid() => incrementKills + remove
  → updateWaveWorldState(session):
       update zombie HP nameplates + player HP HUD
       if player HP <= 0 => stopSession("death")
  → if spawningComplete && aliveZombies == 0 => onWaveComplete()
```
