# Parkour Module Audit

Deep review of all 129 Java files in `hyvexa-parkour`. No files were modified.

**Date:** 2026-03-20
**Scope:** AI slop, optimization opportunities, code quality issues

---

## Table of Contents

- [HyvexaPlugin.java](#hyvexaplugin)
- [Manager Layer](#manager-layer)
- [Duel Subsystem](#duel-subsystem)
- [Commands (Part 1)](#commands-part-1)
- [Commands (Part 2)](#commands-part-2)
- [Data Layer](#data-layer)
- [Ghost System + Interactions](#ghost-system--interactions)
- [Systems + Trackers](#systems--trackers)
- [UI Pages (Part 1)](#ui-pages-part-1)
- [UI Pages (Part 2) + Utilities](#ui-pages-part-2--utilities)
- [Priority Summary](#priority-summary)

---

## HyvexaPlugin

### `HyvexaPlugin.java`

**1. AI Slop: Repetitive try/catch store initialization (lines 209-257)**
```java
try { VexaStore.getInstance().initialize(); }
catch (Exception e) { LOGGER.atWarning().withCause(e).log("Failed to initialize VexaStore"); }
try { DiscordLinkStore.getInstance().initialize(); }
catch (Exception e) { LOGGER.atWarning().withCause(e).log("Failed to initialize DiscordLinkStore"); }
// ... repeated 8+ more times
```
Same pattern repeats in shutdown (lines 1338-1386) with 20+ blocks.

**Fix:** Extract `initSafe(String name, Runnable init)` and `shutdownSafe(String name, Runnable action)` helpers. Eliminates ~80 lines of boilerplate.

**2. Code Quality: God method `setup()` is ~420 lines (lines 203-621)**
The `PlayerReadyEvent` handler alone (lines 390-505) is ~115 lines of deeply nested inline lambda. Run-restore logic (lines 449-500) is another dense block.

**Fix:** Extract `handlePlayerReady(PlayerReadyEvent)` and `attemptRunRestore(...)` as separate methods.

**3. Optimization: Three separate loops over `Universe.get().getPlayers()` during setup (lines 508-531, 599-601, 613-621)**
Each loop does different work (cache HUD, start playtime, sync inventory). Could be one pass.

**Fix:** Consolidate into a single loop over players.

**4. Code Quality: Redundant `playerRef != null` checks (lines 438, 444, 449)**
After the component fetch on line 407, `playerRef` is checked for null multiple times across subsequent blocks when a single early-return would suffice.

**Fix:** Single early-return after the component fetch.

**5. Code Quality: `shouldApplyParkourMode(UUID, World)` ignores the UUID parameter (lines 1181-1198)**
The `playerId` parameter is never used -- only the world is checked.

**Fix:** Remove the parameter or rename to `isParkourWorld(World)`.

---

## Manager Layer

### `AnnouncementManager.java`

**1. Code Quality: Redundant modular arithmetic (lines 185-189)**
```java
if (chatAnnouncementIndex >= chatAnnouncements.size()) {
    chatAnnouncementIndex = 0;
}
message = chatAnnouncements.get(chatAnnouncementIndex);
chatAnnouncementIndex = (chatAnnouncementIndex + 1) % chatAnnouncements.size();
```
The `if` block is redundant -- the modulo on line 189 already handles wraparound.

**Fix:** Remove the bounds check (lines 185-187).

---

### `HudManager.java`

**1. Optimization: Medal notification cache key includes `elapsed`, making cache useless (line 589)**
```java
String cacheKey = notif.medal.name() + "|" + vis.iconVisible + "|" + vis.titleVisible + "|"
        + vis.titleColor + "|" + vis.featherVisible + "|" + vis.barVisible + "|" + elapsed;
```
`elapsed` changes every tick, so the key is never reused. The cache is effectively disabled.

**Fix:** Remove `elapsed` from the cache key. Use `vis.barValue` instead if frame grouping is desired, or remove the caching mechanism entirely.

**2. Code Quality: Dead writes in state reset (lines 142-143)**
```java
state.recordsMapId = null;          // line 142
state.recordsLeaderboardVersion = -1L;  // line 143
```
These are immediately overwritten by lines 165-166 which always execute in the same `!running` branch.

**Fix:** Remove lines 142-143.

**3. Code Quality: `toRecordLines` dereferences `selfRow` without null check (line 360)**
```java
ProgressStore.LeaderboardHudRow selfRow = snapshot.getSelfRow();
lines.add(new RunRecordsHud.RecordLine(selfRow.getRank(), selfRow.getName(), selfRow.getTime()));
```
Every other access in the method is guarded. If `getSelfRow()` returns null, this NPEs.

**Fix:** Add a null check before the add.

---

### `InventorySyncManager.java`

**1. Optimization: Double world-thread dispatch (lines 69-107)**
`syncRunInventoryOnReady` dispatches to the world thread (line 69), then calls `syncRunInventoryOnConnect` which dispatches to the world thread again (line 91).

**Fix:** Extract the actual sync logic into a package-private method that assumes world-thread context, and call it directly from `syncRunInventoryOnReady`.

**2. Code Quality: `sendLanguageNotice` re-resolves ref/store/world (lines 131-153)**
Called from within `syncRunInventoryOnReady` which already has these values resolved.

**Fix:** Pass the already-resolved values as parameters.

---

### `LeaderboardHologramManager.java`

**1. Code Quality: Duplicate world-resolution logic (lines 93-103 vs 195-206)**
`refreshMapLeaderboardHologram` has inline world resolution. `resolveParkourWorld` does the exact same thing.

**Fix:** Use the shared `resolveParkourWorld` method in both places.

**2. Code Quality: Constructor duplication (lines 32-42)**
The 2-arg constructor duplicates field assignments instead of delegating to the 3-arg constructor.

**Fix:** `this(progressStore, mapStore, "Parkour")`.

---

### `PlayerPerksManager.java`

**1. Optimization: Duplicate isFounder/isVip lookups (lines 64-84)**
`getSpecialRankLabel` and `getSpecialRankColor` both independently call `progressStore.isFounder()` and `progressStore.isVip()`. ChatFormatter calls both on every chat message, resulting in 4 store lookups where 2 would suffice.

**Fix:** Add a `getSpecialRank(UUID)` method returning an enum, use it in both label and color methods.

**2. Code Quality: Float precision in `stripTrailingZeros` (line 272)**
```java
return String.valueOf(value);  // can produce "1.100000024"
```

**Fix:** Use `String.format("%.1f", value)` for user-facing multiplier display.

---

### `PlaytimeManager.java`

**1. Code Quality: `long[] deltaMs = new long[1]` lambda hack (line 57)**
Uses a single-element array to smuggle data out of a `ConcurrentHashMap.compute` lambda.

**Fix:** Replace with `put` + old-value check:
```java
Long start = playtimeSessionStart.put(playerId, now);
if (start != null) {
    long delta = Math.max(0L, now - start);
    if (delta > 0L) progressStore.addPlaytime(playerId, playerRef.getUsername(), delta);
}
```

**2. Code Quality: `onlinePlayerCount` AtomicInteger is redundant (line 20)**
Manually incremented/decremented on connect/disconnect, but `tickPlayerCounts` overwrites it every tick with `Universe.get().getPlayers().size()`. The manual tracking is dead logic.

**Fix:** Remove the AtomicInteger and always derive from `Universe.get().getPlayers().size()`, or remove the tick override.

---

### `WorldMapManager.java`

**1. Optimization: Unnecessary component lookup for logging (line 40)**
`PlayerRef` is fetched just to log the player name. The log fires for every player connection.

**Fix:** Log the UUID instead, or gate behind `LOGGER.isLoggable(Level.FINE)`.

**2. Code Quality: Info-level logging for routine operation (line 49)**

**Fix:** Change `LOGGER.atInfo()` to `LOGGER.atFine()`.

---

## Duel Subsystem

### `DuelQueue.java`

**1. Bug: `removePair` can silently lose a queued player (lines 68-74)**
```java
public boolean removePair(UUID player1, UUID player2) {
    synchronized (lock) {
        boolean removed1 = waitingPlayers.remove(player1);
        boolean removed2 = waitingPlayers.remove(player2);
        return removed1 && removed2;
    }
}
```
If only one player is in the queue, that player is removed but the method returns `false`. The caller in `DuelTracker.tryMatch()` treats `false` as "match failed" -- the removed player is silently lost.

**Fix:** Check both are present before removing:
```java
if (!waitingPlayers.contains(player1) || !waitingPlayers.contains(player2)) return false;
waitingPlayers.remove(player1);
waitingPlayers.remove(player2);
return true;
```

### `DuelMatch.java`

**2. Bug: `state` check-then-set in `endMatch` is not atomic (DuelTracker lines 662-666)**
```java
if (match.getState() == DuelState.FINISHED) return;
match.setState(DuelState.FINISHED);
```
`state` is `volatile` but has no CAS protection. Two threads can both pass the check, causing double stat recording and double messages.

**Fix:** Make `state` an `AtomicReference<DuelState>` and use `compareAndSet(current, FINISHED)`.

---

### `DuelTracker.java`

**3. Optimization: `hasAvailableMaps` does full map selection + random pick just to check existence (lines 570-579)**

**Fix:** Add `anyDuelMapMatches(EnumSet<DuelCategory>)` that returns `true` on first eligible map.

**4. Optimization: `hiddenBeforeDuel` stores empty sets unnecessarily (lines 840-847)**
```java
if (currentHidden.isEmpty()) {
    hiddenBeforeDuel.put(viewerId, Set.of());
    return;
}
```
The `restoreVisibility` method returns early on empty sets. The map entry is pure waste.

**Fix:** Skip the `put` when empty.

**5. Code Quality: Manual JSON construction in analytics (lines 728-735)**
Hand-rolled JSON with no escaping and a silent empty catch block.

**Fix:** At minimum log the exception. Use proper JSON builder if available.

**6. Code Quality: `endMatch` is 75 lines with duplicated message logic (lines 662-739)**

**Fix:** Extract message construction per `FinishReason` into helper methods.

---

### `DuelCommand.java`

**7. Code Quality: Duplicated queue-join logic with DuelMenuPage (DuelCommand lines 87-119 vs DuelMenuPage lines 85-136)**
Both contain near-identical 30-line sequences: check isInMatch, check isQueued, check active map, check unlock requirement, check available maps, enqueue, get position, format message, try match.

**Fix:** Extract join flow into `DuelTracker.tryEnqueue(UUID, RunTracker)` returning a result enum.

**8. Code Quality: Unreachable `adminRef == null` check (line 278)**
`adminRef` is validated at line 70-71 before `handleAdminForce` is called.

**Fix:** Remove the dead check.

---

### `DuelPreferenceStore.java`

**9. Code Quality: `toFlags` builds an EnumMap just to call `.get()` 4 times (lines 222-228)**

**Fix:** Inline `enabled.contains(DuelCategory.EASY)` directly in `setBoolean` calls. Delete `toFlags`.

---

### `DuelMenuPage.java`

**10. Code Quality: `handleActiveMatches` duplicates `DuelCommand.handleAdminMatches` (lines 312-337 vs DuelCommand lines 248-261)**

**Fix:** Extract into `DuelTracker.formatActiveMatches()`.

---

### `DuelStatsStore.java`

**11. Code Quality: `defaultValue()` returns stats with null UUID (lines 129-131)**
If `BasePlayerStore` ever uses this default, `getPlayerId()` returns null, causing downstream NPEs.

**Fix:** Verify usage in `BasePlayerStore`. If the default is used, this needs a valid UUID.

---

## Commands (Part 1)

### `CheckpointCommand.java`

**1. AI Slop: Overly defensive action parsing (lines 59-71)**
```java
if (action != null) {
    action = action.trim();
    if (!action.isEmpty()) {
        action = action.toLowerCase(Locale.ROOT);
    }
}
```
The `isEmpty()` guard before `toLowerCase()` is pointless. Dual-path argument resolution (OptionalArg vs tokenize) is accidental complexity.

**Fix:** Simplify to a single ternary + `toLowerCase()`.

**2. Code Quality: Potential memory leak in `CHECKPOINTS` static map (line 35)**
```java
private static final Map<UUID, Checkpoint> CHECKPOINTS = new ConcurrentHashMap<>();
```
No timeout or cleanup on disconnect. If `clearCheckpoint(UUID)` isn't called on disconnect, entries accumulate.

**Fix:** Verify disconnect cleanup calls `CheckpointCommand.clearCheckpoint(uuid)`.

---

### `DatabaseClearCommand.java` / `DatabaseReloadCommand.java` / `DatabaseTestCommand.java`

**3. Optimization: Blocking DB I/O on calling thread (all three files)**
`executeAsync` does synchronous DB work without dispatching to a background/world thread. Other commands in the codebase use `CompletableFuture.runAsync(..., world)`.

**Fix:** Wrap DB work in `CompletableFuture.runAsync()`.

---

### `DiscordCommand.java` + `LinkCommand.java`

**4. Code Quality: Duplicated `DISCORD_URL` constant**
Both files define `private static final String DISCORD_URL = "https://discord.gg/2PAygkyFnK";`

**Fix:** Move to `ChatColors` or a shared constants class.

---

### `LinkCommand.java`

**5. Code Quality: Redundant ref/store re-resolution in `handleLink` (lines 48-56)**
The ref and store are already resolved in `executeAsync` but `handleLink` resolves them again from scratch.

**Fix:** Pass `ref` as a parameter and only re-validate `isValid()`.

---

### `AbstractCurrencyCommand.java`

**6. Code Quality: Undocumented broadcast feature in `add` subcommand (lines 110-116)**
Any OP can broadcast arbitrary text to all players via an extra argument that isn't in the usage message. Also uses fully-qualified `java.util.Arrays.copyOfRange`.

**Fix:** Document in usage message or remove. Add the import.

---

## Commands (Part 2)

### `MobGalleryCommand.java`

**1. Code Quality: `SpawnedMobRecord` should be a Java record (line 620-628)**
Plain data carrier with no behavior.

**Fix:** Replace with `private record SpawnedMobRecord(String worldName, UUID entityUuid) {}`.

**2. Optimization: Unnecessary `List.copyOf` on already-local list (line 315)**

**Fix:** Pass `entry.getValue()` directly.

---

### `ParkourCommand.java`

**3. Code Quality: `findOnlineByName` duplicates `CommandUtils.findPlayerByName` (line 351-363)**

**Fix:** Use `CommandUtils.findPlayerByName(target)`.

**4. Code Quality: Dead `progressStore == null` check in `handleAdminRankBroadcast` (line 275)**
Already checked by the caller on line 214.

**Fix:** Remove.

**5. Code Quality: Dead `store == null` check in `handleAdminHologramRefresh` (line 429)**

**Fix:** Remove.

---

### `ParkourMusicDebugCommand.java`

**6. AI Slop: Missing import, fully-qualified `CompletableFuture` used 6 times**

**Fix:** Add `import java.util.concurrent.CompletableFuture;`.

**7. Code Quality: `ModeGate` check runs off world thread (line 57)**
Other commands run `ModeGate.denyIfNot` inside the world-thread callback.

**Fix:** Move inside the `runAsync` block if `ModeGate` reads world state.

---

### `PetTestCommand.java`

**8. Bug: Entity operations not on world thread**
`petManager.spawnPet()` and `despawnPet()` called directly from the async command thread. Per CLAUDE.md: "World thread for entity ops."

**Fix:** Wrap all pet operations in `CompletableFuture.runAsync(() -> { ... }, world)`.

---

### `SpectatorCommand.java`

**9. Bug: No same-world validation for spectating (lines 103-114)**
The spectating player's world and the target's world are resolved independently with no check that they match.

**Fix:** Add a same-world check before setting up the camera.

---

### `ParkourConstants.java`

**10. Code Quality: `RANK_XP_REQUIREMENTS` (7 entries) vs `COMPLETION_RANK_NAMES` (12 entries) length mismatch (lines 26-50)**
No comment explaining why. Indexing `RANK_XP_REQUIREMENTS` at rank >= 7 throws `ArrayIndexOutOfBoundsException`.

**Fix:** Add documentation explaining the relationship, or align the arrays.

---

## Data Layer

### `GlobalMessageStore.java` (and all stores)

**1. AI Slop: Redundant `conn == null` checks after connection pool (pervasive)**
Every method that gets a connection does `if (conn == null) { LOGGER.atWarning()... return; }`. This pattern appears ~50+ times across all store files. If `getConnection()` can return null, that's a pool bug, not something every caller should handle.

**Fix:** Either make `getConnection()` throw (never return null), or extract a `withConnection(Consumer<Connection>)` helper.

**2. AI Slop: Vestigial `fileLock` field name (GlobalMessageStore, MapStore, PlayerCountStore, SettingsStore)**
Named from when stores were file-backed; now MySQL-backed.

**Fix:** Rename to `lock` or `rwLock`.

---

### `Map.java`

**3. Code Quality: Fly zone as 6 nullable boxed Doubles (lines 27-32)**
Six individual `Double` fields with autoboxing overhead and a 6-field null check in `hasFlyZone()`.

**Fix:** Introduce `record FlyZone(double minX, ...)`. Field becomes `@Nullable FlyZone flyZone`, `hasFlyZone()` is `flyZone != null`.

**4. Code Quality: Mutable checkpoints list exposed directly (line 112)**
```java
public List<TransformData> getCheckpoints() { return checkpoints; }
```
Callers can mutate freely, undermining `MapStore.copyMap()` thread safety.

**Fix:** Either make `Map` properly immutable or accept it as a mutable DTO and stop deep-copying.

---

### `MapStore.java`

**5. Code Quality: `addMap` and `updateMap` are identical (lines 301-331)**

**Fix:** Make `updateMap` delegate to `addMap`, or consolidate into `saveMap`.

**6. Optimization: `findMapByStartTrigger` ignores world-scoped index (lines 240-262)**
Iterates all maps. Meanwhile, `findMapByStartTriggerReadonly` (line 264) uses the world-scoped index.

**Fix:** Add a world parameter or remove the non-world-scoped variant if unused.

**7. AI Slop: `copyMap` null check on checkpoints (line 619)**
`checkpoints` is initialized as `new ArrayList<>()` and never set to null.

**Fix:** Remove the `if (source.getCheckpoints() != null)` guard.

---

### `MedalRewardStore.java`

**8. Bug: `setRewards` persists unclamped values (line 86-88)**
```java
rewards.put(key, new MedalRewards(Math.max(0, bronze), ...));
persistToDatabase(key, bronze, silver, gold, emerald, insane);  // raw values!
```
DB stores negatives while memory has clamped zeros.

**Fix:** Pass clamped values to `persistToDatabase`.

**9. Code Quality: `getAllRewards()` returns internal ConcurrentHashMap (line 91)**

**Fix:** Return `Collections.unmodifiableMap(rewards)`.

---

### `MedalStore.java`

**10. Code Quality: DDL + migration runs every startup (lines 47-78)**
Unlike `ParkourDatabaseSetup` which tracks migrations, this ALTER+UPDATE runs unconditionally every boot.

**Fix:** Move into `ParkourDatabaseSetup` with a migration key.

**11. Optimization: `Medal.values()` allocates new array on each call (lines 183, 206)**

**Fix:** Cache: `private static final int MEDAL_COUNT = Medal.values().length;`

---

### `ProgressStore.java`

**12. Optimization: `calculateCompletionXp` deep-copies every map (lines 860-868)**
```java
Map map = mapStore.getMap(mapId);  // deep copy!
total += getMapCompletionXp(map);
```
A player with 50 completed maps triggers 50 deep copies just to sum XP values. **This is the single biggest performance issue in the module.**

**Fix:** Use `mapStore.getMapReadonly(mapId)` -- only reads `getFirstCompletionXp()`.

**13. Optimization: `getTotalPossibleXp` deep-copies ALL maps (line 828-833)**
Same issue. `listMaps()` deep-copies every map to read one long field.

**Fix:** Add `mapStore.sumFirstCompletionXp()` or use readonly access.

**14. Optimization: `ConcurrentHashMap`s inside `PlayerProgress` are overkill (lines 1086-1088)**
All mutations are guarded by `fileLock` write lock. The concurrent structures add CAS overhead for no benefit.

**Fix:** Use `HashSet` and `HashMap`.

**15. Code Quality: `getCompletionRank` if-else chain duplicated with `getCompletionXpToNextRank` (lines 530-562)**
Two representations of the same rank thresholds.

**Fix:** Define thresholds as an array and derive both methods from it.

---

### `SettingsStore.java`

**16. Bug: `syncSave` reads fields without read lock (lines 156-213)**
Every setter acquires the write lock, mutates, releases, then calls `syncSave()`. But `syncSave` reads fields with no lock, risking torn state under concurrent access.

**Fix:** Acquire `fileLock.readLock()` inside `syncSave()` while reading fields.

---

### `PlayerSettingsPersistence.java`

**17. Code Quality: Non-thread-safe singleton pattern (lines 67-75)**
Constructor sets static `INSTANCE` without `volatile` or synchronization.

**Fix:** Use `volatile` or eager static initialization like other singletons.

**18. Optimization: `updateField` does read-modify-write with 2 DB roundtrips (lines 166-170)**
Every toggle change (hide players, toggle music) does SELECT + UPDATE.

**Fix:** Use targeted `UPDATE ... SET <field> = ?` for hot-path changes.

---

### `TransformData.java`

**19. Code Quality: `ParkourUtils.copyTransformData()` duplicates `TransformData.copy()` (line 70-79)**

**Fix:** Use `TransformData.copy()` consistently. Move null handling into `TransformData.copyOrNull()`.

---

## Ghost System + Interactions

### `GhostNpcManager.java`

**1. Bug: Zombie `GhostNpcState` left in `activeGhosts` when world name is null (lines 120-135)**
```java
activeGhosts.put(playerId, state);   // state tracked
if (worldName == null || worldName.isEmpty()) {
    return;   // state left with no entity, ticked every 50ms forever
}
```

**Fix:** `activeGhosts.remove(playerId)` on early return.

**2. Optimization: Redundant recording + map lookups every 50ms tick (lines 310-328)**
Every tick per ghost does `getRecording()`, `getMapReadonly()`, and `getWorld()` lookups. Recording and map are immutable for the ghost's lifetime.

**Fix:** Cache `recording` and `worldName` in `GhostNpcState` at spawn time.

**3. Code Quality: `despawnGhost` during iteration of `activeGhosts` (lines 287-296, 340)**
`tick()` iterates `activeGhosts.values()` and `tickGhost` can call `despawnGhost` which removes from the map.

**Fix:** Collect ghosts to despawn into a list, despawn after iteration.

**4. AI Slop: Excessive individual try/catch in `spawnNpcOnWorldThread` (lines 193-219)**
Three separate try/catch blocks for `addComponent` calls that share the same store/ref and are inside an outer try/catch.

**Fix:** Remove the individual try/catch blocks.

**5. Dead Code: `markOrphanCleaned()` and `isCleanupPending()` never called (lines 427-433)**

**Fix:** Remove both methods.

---

### `PetManager.java`

**6. Bug: `despawnEntity` nulls refs before async removal (lines 352-353)**
```java
world.execute(() -> { store.removeEntity(entityRef, RemoveReason.REMOVE); });
state.entityRef = null;   // nulled before world.execute runs
state.entityUuid = null;
```

**Fix:** Move nulling inside the `world.execute` lambda, after successful removal.

**7. Code Quality: No orphan tracking for pets (vs ghost system)**
If server crashes while pets are spawned, NPC entities persist with no cleanup.

**Fix:** Add orphan tracking similar to `GhostNpcManager`, or document as accepted tech debt.

**8. Dead Code: Debug logging infrastructure (~30 lines, lines 47-50, 405-432)**
`ROTATION_DEBUG_LOGS = false` is hardcoded. `maybeLogRotation`, `shortOwner`, and three constants are never active.

**Fix:** Remove all dead debug code.

**9. Code Quality: `respawn()` is a trivial wrapper for `spawnPet()` (line 149)**

**Fix:** Remove `respawn()`, change callers to `spawnPet()`.

**10. Code Quality: Public mutable `PetState` fields (lines 436-453)**

**Fix:** Make class and mutable fields package-private.

---

### `LeaveInteraction.java`

**11. Code Quality: Redundant null check on `mapName` (line 99)**
Already guaranteed non-null by the ternary on lines 94-96.

**Fix:** Replace `mapName != null ? mapName : "Map"` with just `mapName`.

**12. Code Quality: Potential `PENDING_LEAVES` memory leak (line 34)**
No timeout cleanup for entries where player clicks once but never confirms.

**Fix:** Hook into player disconnect to call `clearPendingLeave`, or add periodic cleanup.

---

### `ResetInteraction.java` / `RestartCheckpointInteraction.java`

**13. Code Quality: Near-identical code structure between the two files**
Map resolution block and practice-enabled check are duplicated verbatim. Only the final action differs (reset vs. checkpoint teleport).

**Fix:** Extract shared logic into a utility method: `resolveActiveMap(HyvexaPlugin, UUID)`.

---

### Interaction files (LeavePractice, Practice, PracticeCheckpoint, ToggleFly)

**14. AI Slop: Unnecessary null checks on `plugin.getRunTracker()` (multiple files)**
Initialized at startup and never nulled.

**Fix:** Remove these checks or consolidate to one.

---

## Systems + Trackers

### `NoPlayerDamageSystem.java` / `NoWeaponDamageSystem.java`

**1. Code Quality: Identical 6-line damage cancellation block (both files)**
```java
if (event.hasMetaObject(Damage.KNOCKBACK_COMPONENT)) {
    event.removeMetaObject(Damage.KNOCKBACK_COMPONENT);
}
event.setCancelled(true);
event.setAmount(0f);
buffer.tryRemoveComponent(chunk.getReferenceTo(entityId), KnockbackComponent.getComponentType());
```

**Fix:** Extract to `DamageUtils.cancelDamageWithKnockback(event, buffer, chunk, entityId)`.

**2. Code Quality: Identical `getGroup()` caching pattern (both files)**

**Fix:** Extract to a shared base class `CachingDamageEventSystem`.

---

### `RunTrackerTickSystem.java`

**3. AI Slop: Unreachable null check on final fields (lines 43-45)**
```java
if (runTracker == null || plugin == null) return;
```
Both are `final` fields set in the constructor.

**Fix:** Remove.

**4. Code Quality: Redundant duel check (lines 57-59)**
`RunTracker.checkPlayer()` also checks `duelTracker.isInMatch()`. The check happens twice per tick.

**Fix:** Remove from one location.

---

### `JumpTracker.java`

**5. AI Slop: Redundant null checks (lines 23-29, 44-46)**
`playerRef.getUuid()` never returns null. `ConcurrentHashMap` never contains null keys/values.

**Fix:** Remove both null checks.

---

### `PingTracker.java`

**6. AI Slop: Redundant null checks (lines 71-73, 96-98)**
`readPingMs` checks `playerRef == null` after callers already checked. `convertPingToMs` checks `unit == null` for a `static final` field.

**Fix:** Remove.

---

### `RunHud.java`

**7. Optimization: Unnecessary Boolean boxing (lines 64, 146)**
```java
Boolean.valueOf(visible).equals(lastCheckpointSplitVisible)
```

**Fix:** Compare directly: `lastCheckpointSplitVisible != null && lastCheckpointSplitVisible == visible`.

---

### `RunRecordsHud.java`

**8. Dead Code: Unused `earned` parameter in `updateMedals` (line 67)**
```java
public void updateMedals(Map map, Set<Medal> earned) {  // earned never used
```

**Fix:** Remove the parameter and update callers.

**9. Code Quality: `resolved[5]` written but never read (line 34)**
Array slot 5 is assigned but only slots 0-4 are used in the loop.

**Fix:** Change array to size 5 and use the `self` local directly.

---

### `RunSessionTracker.java`

**10. Dead Code: `firstFailureTimestamp`, `recommendationShown`, `practiceHintShown` never read (lines 74-79)**
`checkRecommendations` is an empty stub. These are remnants of a disabled feature.

**Fix:** Remove the dead fields. Consider removing the entire class if the recommendation feature is permanently shelved.

---

### `RunTeleporter.java`

**11. AI Slop: Redundant null checks in `addTeleport` and `recordTeleport` (lines 172-184)**
All callers pass validated non-null values.

**Fix:** Remove.

---

### `RunValidator.java`

**12. Dead Code: Unused private method `distanceSqWithVerticalBonus` (lines 487-489)**

**Fix:** Delete it.

**13. Code Quality: Unnecessary same-package import (line 30)**
`import io.hyvexa.parkour.tracker.CheckpointDetector;` -- same package.

**Fix:** Remove.

---

### `RunTracker.java`

**14. Code Quality: Duplicate `TOUCH_RADIUS_SQ` constant with RunValidator (line 48)**
```java
private static final double TOUCH_RADIUS_SQ = ParkourConstants.TOUCH_RADIUS * ParkourConstants.TOUCH_RADIUS;
```
Same constant in RunValidator line 43.

**Fix:** Define once in `ParkourConstants`.

**15. Code Quality: `setFly` method duplication (lines 376-424)**
Enabled/disabled branches have nearly identical structure (~50 lines). Only difference is the boolean value.

**Fix:** Collapse into a single flow parameterized by `boolean enabled`.

**16. Optimization: `lastSeenAt.put` on every tick (line 491)**
Writes to `ConcurrentHashMap` every tick for every player. Only used for 30-minute expiry checks.

**Fix:** Update every ~10 seconds or on meaningful state changes only.

**17. Code Quality: Inline JSON construction for analytics (lines 113-114)**
Manual JSON string with no escaping.

**Fix:** Use JSON builder or at minimum escape values.

---

### `TrackerUtils.java`

**18. AI Slop: Null check on Integer from Set iteration (lines 85-86)**
`Set<Integer>` populated via `add(i)` will never contain null.

**Fix:** Remove.

---

## UI Pages (Part 1)

### `AdminPlayerStatsPage.java`

**1. Code Quality: 10-line "resolve target" block copy-pasted 5 times (lines 152-312)**
```java
PlayerRef targetRef = Universe.get().getPlayer(targetId);
if (targetRef == null) { setStatus("Player not connected."); return; }
Ref<EntityStore> targetEntityRef = targetRef.getReference();
if (targetEntityRef == null || !targetEntityRef.isValid()) { setStatus("..."); return; }
Store<EntityStore> targetStore = targetEntityRef.getStore();
```

**Fix:** Extract `ResolvedTarget resolveTarget()` record and method. Each handler shrinks from ~20 lines to ~8.

---

### `CategorySelectPage.java`

**2. Code Quality: `applyCategoryOrder` result immediately overwritten by `applyCategoryMapOrder` (lines 101-142)**
```java
List<String> orderedCategories = applyCategoryOrder(categories);        // carefully ordered
orderedCategories = applyCategoryMapOrder(orderedCategories, maps);     // completely re-sorted
```
The settings-based order from `SettingsStore.getCategoryOrder()` is never honored.

**Fix:** Remove `applyCategoryOrder` if map-based order is intended, or use it as a tiebreaker.

**3. Code Quality: `LinkedList` used unnecessarily (line 121)**

**Fix:** Change to `new ArrayList<>()`.

---

### `MapAdminPage.java`

**4. Code Quality: 28-line duplicated medal validation in `handleCreate`/`handleUpdate` (lines 255-282 vs 451-478)**

**Fix:** Extract `boolean applyMedalTimes(Player, Map)`.

**5. Code Quality: `openIndex` duplicates `AdminPageUtils.openIndex` (lines 696-706)**
Also calls `HyvexaPlugin.getInstance()` 3 times without null checks.

**Fix:** Replace body with `AdminPageUtils.openIndex(ref, store)`.

**6. Code Quality: Dead null check (line 556)**
`if (plugin == null)` is unreachable because line 542 already returns if `progressStore == null`, which would require `plugin == null`.

**Fix:** Remove.

**7. Code Quality: Inconsistent toggle labels -- "Enabled"/"Disabled" vs "YES"/"NO" (lines 729-734)**

**Fix:** Pick one style.

---

### `AdminIndexPage.java`

**8. Code Quality: Fully-qualified `Message.raw()` on lines 131/136 when import exists**

**Fix:** Use the imported `Message.raw()`.

---

### `LeaderboardPage.java` / `MapLeaderboardPage.java`

**9. Code Quality: `java.util.ArrayList` via FQN instead of import**

**Fix:** Add `import java.util.ArrayList;`.

---

### `MapLeaderboardPage.java` / `LeaderboardMapSelectPage.java` / `LeaderboardMenuPage.java`

**10. Dead Code: `runTracker` field unused in all three classes**
Threaded through `LeaderboardMenuPage -> LeaderboardMapSelectPage -> MapLeaderboardPage` and never used.

**Fix:** Remove from all three.

---

### `AdminPlayerStatsPage.java` / `AdminPlayerMapProgressPage.java`

**11. Code Quality: Duplicated map sort comparator**

**Fix:** Extract to `ParkourUtils.MAP_ORDER_COMPARATOR`.

---

## UI Pages (Part 2) + Utilities

### `PlayerSettingsPage.java`

**1. Dead Code: `applyHiddenState` `hide` parameter always `true` (lines 249-281)**
Only ever called with `true`. The `else` branch (show logic) is dead code.

**Fix:** Remove the parameter and the dead `else` branch.

**2. Code Quality: 7 redundant `HyvexaPlugin.getInstance()` calls (lines 60-207)**

**Fix:** Fetch once and reuse.

**3. Code Quality: Duplicated ghost spawn/despawn with FQN import (lines 169-192)**

**Fix:** Add import, restructure to fetch `ghostNpcManager` once.

---

### `PlayerMusicPage.java`

**4. Code Quality: `Hytale_MUSIC_LABEL` naming convention violation (line 42)**
```java
private static final String Hytale_MUSIC_LABEL = "Default Music";
```

**Fix:** Rename to `HYTALE_MUSIC_LABEL`.

**5. Code Quality: "Aura" migration never persists (lines 197-199)**
Mutates in-memory map every session but never writes back to DB. Re-migrates on every reconnect.

**Fix:** Persist the corrected label or add a one-time DB migration.

---

### `PlayerCountAdminPage.java`

**6. Dead Code: Unused fields in `DisplaySample` (lines 238-242)**
`min`, `latestTimestampMs`, and `aggregated` are stored but never read.

**Fix:** Remove.

**7. Code Quality: Dead `samples.isEmpty()` check (line 126)**
Already guarded by early return on line 111.

**Fix:** Remove ternary.

---

### `PlaytimeAdminPage.java`

**8. Optimization: Sort-then-filter wastes cycles (lines 62-76)**
All player IDs are sorted (calling `getPlaytimeMs` per comparison), then filtered.

**Fix:** Filter first, then sort.

---

### `MedalRewardAdminPage.java`

**9. Optimization: Redundant `getRewards("insane")` call (lines 46-62)**
Loop already fetches insane rewards. Line 61 fetches again.

**Fix:** Capture during loop iteration.

---

### `MapSelectPage.java`

**10. Code Quality: Redundant `mapName != null` check (line 136)**
Already guaranteed non-null.

**Fix:** Remove.

**11. Optimization: Full map list copied and sorted before category filtering (lines 149-160)**

**Fix:** Filter by category first, then sort.

---

### `InventoryUtils.java`

**12. Code Quality: Duplicated weapon slot logic between practice/normal modes (lines 53-94)**
Six nearly identical lines for sword/daggers/glider setup.

**Fix:** Extract `assignWeaponSlots(Inventory, Map, int startSlot)`.

**13. Code Quality: `getValidHotbar` Optional wrapper used once (lines 23-32)**

**Fix:** Inline directly in `giveDuelItems`.

---

### `PlayerSettingsStore.java`

**14. Code Quality: `clear()` is a pointless wrapper for `clearSession()` (lines 133-135)**

**Fix:** Keep one method, remove the alias.

---

### `WelcomeTutorialScreen1/2/3Page.java`

**15. Code Quality: Missing null-button guard (all three files)**
Other pages have `if (data.getButton() == null) return;` before component lookups.

**Fix:** Add the guard to all three.

---

## Priority Summary

### Bugs (fix these)

| # | File | Issue |
|---|------|-------|
| 1 | `DuelQueue.removePair` | Silently loses queued player on partial removal |
| 2 | `DuelMatch.state` | Non-atomic check-then-set allows double `endMatch` execution |
| 3 | `SettingsStore.syncSave` | Reads fields without lock -- torn state possible |
| 4 | `MedalRewardStore.setRewards` | Persists unclamped values to DB |
| 5 | `GhostNpcManager.spawnGhost` | Zombie state left in `activeGhosts` when world is null |
| 6 | `PetManager.despawnEntity` | Nulls refs before async removal completes |
| 7 | `PetTestCommand` | Entity operations not on world thread |
| 8 | `SpectatorCommand` | No same-world validation for spectating |

### Top Performance Issues

| # | File | Issue |
|---|------|-------|
| 1 | `ProgressStore.calculateCompletionXp` | Deep-copies every completed map to read one field |
| 2 | `ProgressStore.getTotalPossibleXp` | Deep-copies ALL maps to sum one field |
| 3 | `HudManager` medal cache | Cache key includes `elapsed` -- cache never hit |
| 4 | `GhostNpcManager.tickGhost` | Redundant recording + map lookups every 50ms |
| 5 | `RunTracker.lastSeenAt` | ConcurrentHashMap write every tick per player |
| 6 | `ProgressStore.PlayerProgress` | ConcurrentHashMap overhead when external lock exists |

### Worst AI Slop

| # | Pattern | Locations |
|---|---------|-----------|
| 1 | `conn == null` boilerplate after pool | All store files (~50+ instances) |
| 2 | Repetitive try/catch init/shutdown | `HyvexaPlugin` (80+ lines) |
| 3 | Null checks on things that can't be null | JumpTracker, PingTracker, RunTeleporter, TrackerUtils, RunTrackerTickSystem |
| 4 | `fileLock` vestigial naming | GlobalMessageStore, MapStore, PlayerCountStore, SettingsStore |
| 5 | Missing imports with FQN usage | ParkourMusicDebugCommand, LeaderboardPage, MapLeaderboardPage, AdminIndexPage |

### Worst Code Duplication

| # | What | Where |
|---|------|-------|
| 1 | Queue-join flow (~30 lines) | `DuelCommand` vs `DuelMenuPage` |
| 2 | Medal validation (~28 lines) | `MapAdminPage.handleCreate` vs `handleUpdate` |
| 3 | Target resolution (~10 lines x5) | `AdminPlayerStatsPage` (5 handlers) |
| 4 | Damage cancellation (6 lines) | `NoPlayerDamageSystem` vs `NoWeaponDamageSystem` |
| 5 | Map resolution + practice check | `ResetInteraction` vs `RestartCheckpointInteraction` |
| 6 | `addMap` / `updateMap` identical bodies | `MapStore` |

### Dead Code

| # | File | What |
|---|------|------|
| 1 | `RunSessionTracker` | Entire recommendation system disabled |
| 2 | `PetManager` | Debug logging infrastructure (30+ lines) |
| 3 | `GhostNpcManager` | `markOrphanCleaned` / `isCleanupPending` |
| 4 | `RunValidator` | `distanceSqWithVerticalBonus` private method |
| 5 | `RunRecordsHud` | Unused `earned` parameter |
| 6 | `PlayerSettingsPage` | Dead `else` branch in `applyHiddenState` |
| 7 | `PlayerCountAdminPage` | 3 unused `DisplaySample` fields |
| 8 | Leaderboard pages (3 files) | `runTracker` field unused in all three |
