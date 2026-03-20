# Ascend Module Audit

Deep code review of every file in `hyvexa-parkour-ascend`. No files were modified.

**Legend:** [HIGH] correctness/crash risk | [MED] meaningful quality/perf improvement | [LOW] cleanup/style

---

## Table of Contents

- [Core & Data Layer](#core--data-layer)
- [Commands & Interactions](#commands--interactions)
- [Ascension, Summit, Transcendence, Achievements](#ascension-summit-transcendence-achievements)
- [HUD & Toast System](#hud--toast-system)
- [Robot System (Runners)](#robot-system-runners)
- [Mine Subsystem](#mine-subsystem)
- [UI Pages (General)](#ui-pages-general)
- [UI Pages (Mine)](#ui-pages-mine)
- [Cross-Cutting Themes](#cross-cutting-themes)

---

## Core & Data Layer

### AscendConstants.java

**[LOW] Duplicated bounds-check accessor pattern** (7 methods, ~50 lines)
```java
public static double getMapSpeedMultiplier(int displayOrder) {
    if (displayOrder < 0 || displayOrder >= MAP_SPEED_MULTIPLIERS.length) {
        return MAP_SPEED_MULTIPLIERS[MAP_SPEED_MULTIPLIERS.length - 1];
    }
    return MAP_SPEED_MULTIPLIERS[displayOrder];
}
```
Seven nearly identical methods each duplicate the same clamp-to-last pattern.
**Fix:** Extract a single `clampedLookup(int index, T[] array)` helper. Reduces ~50 lines to ~10.

**[LOW] Uniform MAP_SPEED_MULTIPLIERS array** (lines 43-51)
```java
public static final double[] MAP_SPEED_MULTIPLIERS = {
    0.10, 0.10, 0.10, 0.10, 0.10, 0.10
};
```
All 6 values are identical. Replace with a single constant `MAP_SPEED_MULTIPLIER = 0.10`.

---

### AscendRuntimeConfig.java

**[LOW] Hardcoded default security token** (line 24)
```java
private static final String NPC_COMMAND_TOKEN_DEFAULT = "hx7Kq9mW";
```
Anyone with source access knows the fallback token. Generate a random token at first startup instead.

---

### AscendPlayerProgress.java

**[HIGH] `skillTreePoints` lost-update race** (line 256)
```java
public int addSkillTreePoints(int amount) {
    skillTreePoints = Math.max(0, skillTreePoints + amount);
    return skillTreePoints;
}
```
`skillTreePoints` is `volatile`, but `read + modify + write` is not atomic. Two concurrent calls can lose an increment.
**Fix:** Use `AtomicInteger` (consistent with other numeric fields like `volt`).

**[HIGH] `seenTutorials` bitmask race** (line 548)
```java
public void markTutorialSeen(int bit) {
    seenTutorials |= bit;
}
```
`|=` on a `volatile int` is not atomic. Concurrent calls can lose a bit.
**Fix:** Use `AtomicInteger` and `getAndUpdate(v -> v | bit)`.

**[LOW] Mixed concurrency primitives**
Some fields use `AtomicInteger`/`AtomicReference`, others use plain `volatile`. Document the threading model or standardize on one approach.

---

### AscendPlayerStore.java

**[MED] `atomicAddVolt` always returns true** (lines 322-329)
```java
public boolean atomicAddVolt(UUID playerId, BigNumber amount) {
    // ... always returns true
    return true;
}
```
Callers check the return value (e.g., `if (!playerStore.atomicAddVolt(...))`), but that branch is unreachable.
**Fix:** Change return type to `void`, or add actual failure handling.

**[MED] `checkVoltTutorialThresholds` calls `getPlayer` twice** (lines 353, 369)
The progress object is fetched, used, then fetched again a few lines later under a different variable name.
**Fix:** Hoist to a single local variable.

---

### AscendPlayerPersistence.java

**[MED] Hand-rolled JSON serialization** (lines 1132-1241)
```java
StringBuilder sb = new StringBuilder("[");
// manual JSON building with string splitting for parsing
```
The parser splits by `"},{"` which would break with nested objects or whitespace variations.
**Fix:** Use Gson (already on classpath) for both serialization and parsing.

**[MED] `doSyncSave` opens 12 PreparedStatements simultaneously** (lines 335-346)
6 of the 12 are delete statements only used for reset-pending players (rare path).
**Fix:** Only prepare delete statements inside the `if (resetPendingPlayers.contains(playerId))` block.

**[LOW] Fully qualified class names used inline** (line 885)
```java
Map<UUID, ...> merged = new java.util.LinkedHashMap<>();
```
Several places use FQNs instead of imports. Add imports and use short names.

---

### AscendDatabaseSetup.java

**[LOW] No migration versioning** (entire file, 400+ lines)
Every migration runs on every server startup. All are idempotent (IF NOT EXISTS), so no correctness issue, but wasted startup time.
**Fix:** Implement a `schema_migrations` table with numbered migrations. Only run new ones.

---

### AscendMapStore.java

**[LOW] Unused legacy constants** (lines 23-24)
```java
private static final long LEGACY_ROBOT_TIME_REDUCTION_MS = 0L;
private static final int LEGACY_STORAGE_CAPACITY = 100;
```
Only used to populate legacy DB columns. Inline them or document more prominently.

---

### AscendMap.java

**[LOW] Dead method: `getEffectiveRobotPrice()`** (line 46-48)
Always returns `0L`. If runners are free, callers shouldn't need this. Verify callers and remove if unused.

---

### AscendSettingsStore.java

No issues found. Clean persistence layer.

---

### AscendFinishDetectionSystem.java

**[LOW] String allocation on every near-finish check** (lines 62-73)
```java
String playerIdText = playerId != null ? playerId.toString() : "unknown";
```
`UUID.toString()` allocates on every tick for every player near a finish line, but the string is only used for error logging.
**Fix:** Build the string lazily, only in the error path.

---

### AscendRunTracker.java

**[MED] Duplicate finish-line distance calculation** (lines 181-184 and 249-253)
The proximity check is duplicated: once in `isNearFinish()` and again in `checkPlayer()`.
**Fix:** Have `checkPlayer` call `isNearFinish` instead of recalculating.

**[LOW] `ParkourAscendPlugin.getInstance()` fetched twice in same method** (lines 450, 470)
Two calls under different variable names. Use a single local.

---

### PassiveEarningsManager.java

**[LOW] `effectiveOfflineRate` is always 10** (line 109)
```java
long effectiveOfflineRate = AscendConstants.PASSIVE_OFFLINE_RATE_PERCENT;
```
Unnecessary local variable for a constant. Inline it.

---

### TutorialTriggerService.java

**[LOW] Duplicated async scheduling pattern** (lines 156-177 and 192-221)
Two methods share ~90% of their scheduling logic. Only difference is entity resolution.
**Fix:** Extract the scheduled-async-on-world-thread pattern into a shared private method.

---

### MapUnlockHelper.java

**[LOW] `isFirstMap` iterates all maps** (lines 82-92)
```java
for (AscendMap other : mapStore.listMaps()) {
    if (other.getDisplayOrder() < map.getDisplayOrder()) return false;
}
```
Replace with `return map.getDisplayOrder() == 0;` — O(1) instead of O(n).

---

### ParkourAscendPlugin.java

**[MED] Massive `setup()` method (~400 lines)**
Handles database init, store creation, manager creation, command registration, event handlers, and tick scheduling all in one method.
**Fix:** Extract into `initializeDatabase()`, `initializeStores()`, `initializeManagers()`, `registerCommands()`, `registerEventHandlers()`, `startTickTasks()`.

**[LOW] Repeated `playerId != null` checks** (lines 361-402)
Four sequential null checks on the same variable. Add a single early return.

---

## Commands & Interactions

### AscendAdminCommand.java

**[MED] Static `minePos1`/`minePos2` maps leak memory** (lines 39-40)
```java
public static final Map<UUID, int[]> minePos1 = new ConcurrentHashMap<>();
public static final Map<UUID, int[]> minePos2 = new ConcurrentHashMap<>();
```
Entries are never cleaned up on disconnect. Add cleanup in a disconnect handler.

**[MED] Flawed start/finish detection** (lines 286-287)
```java
boolean hasStart = map.getStartX() != 0 || map.getStartY() != 0 || map.getStartZ() != 0;
```
If start is legitimately at origin (0,0,0), this reports "No Start". Add a `hasStartSet()` boolean to `AscendMap`.

**[LOW] Silent returns on null plugin/mapStore** (lines 93-107)
Admin gets zero feedback. Send a "not loaded" message.

---

### AscendCommand.java

**[HIGH] Potential NPE in `openChallengePage`** (line 309)
```java
plugin.getAscensionManager().hasAscensionChallenges()
```
`getChallengeManager()` is null-checked, but `getAscensionManager()` is not. If challenge manager exists but ascension manager is null, this NPEs.
**Fix:** Add null check for `getAscensionManager()`.

**[MED] Duplicated tutorial-gate pattern** (lines 235-320)
Four methods each do the same check-tutorial → show-tutorial-page → return flow.
**Fix:** Extract a `showTutorialIfNeeded(...)` helper that returns boolean.

---

### CatCommand.java

**[LOW] Hardcoded `5` instead of `VALID_CATS.size()`** (lines 109, 112)
**[LOW] `VALID_CATS` map values are never read** — use a `Set<String>` or use the names in toast messages.

---

### CinematicTestCommand.java

**[LOW] Static ScheduledExecutorService never shut down** (line 51) — leaks threads on plugin reload.
**[LOW] Repeated float parsing pattern** (10+ instances) — extract a `parseFloat(args, index, default)` helper.
**[LOW] File is documented as temporary** (line 47) — consider removing if ascension cinematic is finalized.

---

### SkillCommand.java

**[MED] Dual coordinate mappings that can silently diverge** (lines 168-217)
`parseCoord` and `getCoord` are inverse functions maintained as two separate switch blocks. If a new skill node is added, both must be updated.
**Fix:** Define coordinates in a single `Map<SkillTreeNode, String>` and derive both directions.

---

### AscendLeaveInteraction.java

**[MED] `PENDING_LEAVES` entries not cleaned on disconnect** (line 35)
```java
private static final ConcurrentHashMap<UUID, PendingLeave> PENDING_LEAVES = new ConcurrentHashMap<>();
```
If player disconnects before confirming, the entry leaks. Verify disconnect handler calls `clearPendingLeave()`.

---

### AscendTranscendenceInteraction.java

**[LOW] Re-derives ref/store/playerRef despite parent already having them** (lines 38-46)
`validateDependencies` doesn't receive these from the parent's `handle()` method, so it re-resolves them. Refactor signature to accept the already-resolved values.

---

### MineChestInteraction.java

**[LOW] Fragile command dispatch via string** (line 28)
```java
CommandManager.get().handleCommand(player, "mine");
```
If `MineCommand` is renamed or removed, this silently breaks. Call the page-opening logic directly.

---

### MineCommand.java

**[LOW] Admin `addcrystals` subcommand inside player-facing `/mine`** (lines 107-128)
Inconsistent with `AscendAdminCommand` pattern. Move to `/as admin mine addcrystals`.

---

## Ascension, Summit, Transcendence, Achievements

### AscensionCinematic.java

**[MED] `LAST_WARNING_BY_PHASE` unbounded memory leak** (line 46)
```java
private static final Map<String, Long> LAST_WARNING_BY_PHASE = new ConcurrentHashMap<>();
```
Keyed by `playerId + "|" + phaseId`, never evicted. Over time with many unique players, grows unboundedly.
**Fix:** Periodically prune entries older than `WARNING_THROTTLE_MS`, or use a bounded LRU.

**[LOW] Duplicated camera settings across phases** (lines 94-191)
Every phase callback sets `applyLookType`, `lookMultiplier`, `skipCharacterPhysics` identically. Move into the `base3p()` helper.

---

### AscensionManager.java

**[LOW] Fully qualified `BigNumber` used 5 times without import** (lines 33, 43, 72, 75-76)
Add `import io.hyvexa.common.math.BigNumber;`.

**[LOW] 16 trivial `has*()` wrapper methods** (lines 159-235)
Each just delegates to `hasSkillNode(playerId, SkillTreeNode.X)`. Make `hasSkillNode` public and let callers use it directly.

**[LOW] Dead comment about removed feature** (line 195)
```java
// Elevation Remnant was removed — its functionality is no longer available
```
Remove.

**[LOW] if-else chain should be a switch** (lines 118-128)
Use a switch statement for the skill node automation toggles.

---

### ChallengeManager.java

**[MED] 6 DB methods with identical connection-handling boilerplate** (lines 568-706)
~12 lines of ceremony per method, ~72 lines total.
**Fix:** Extract a `executeQuery(sql, binder, mapper, default)` helper. Each method becomes 3-5 lines.

**[LOW] Inconsistent `runTracker` null checks** (lines 71, 124, 169)
`runTracker` is a final constructor field. Other managers (`AscensionManager`, `TranscendenceManager`) don't null-check it.
**Fix:** Either validate in constructor with `Objects.requireNonNull` and remove checks, or check everywhere consistently.

**[LOW] `getFirstMapId` duplicated with SummitManager** (lines 650-656)
Both classes do `mapStore.listMapsSorted().get(0)`. Add `getFirstMapId()` to `AscendMapStore`.

---

### ChallengeSnapshot.java

**[MED] No validation on `restore`** (line 96)
Corrupted snapshots (negative elevationMultiplier, nonsensical BigNumber values) are applied directly. This is a crash-recovery path where defensive validation matters.
**Fix:** Add sanity checks: `elevationMultiplier >= 1`, `voltMantissa >= 0`, etc.

---

### SummitManager.java

**[MED] Heavy coupling to ParkourAscendPlugin singleton** (lines 87-88, 155-156, 180-181, 201-202, 224)
Every bonus method calls `ParkourAscendPlugin.getInstance()` to reach `ChallengeManager` and `AscensionManager`.
**Fix:** Constructor-inject these managers instead of using the service locator pattern.

**[LOW] `previewSummit` called redundantly in `performSummit`** (line 101)
Only `hasGain()` from the preview is used. Inline the level-gain check directly.

**[LOW] Raw `double[]` return from `getXpProgress`**
Use a record like `XpProgress(double xpInLevel, double xpRequired)` instead of positional array indexing.

---

### AchievementManager.java

**[MED] Duplicated switch logic: `isAchievementEarned` vs `getProgress`** (lines 72-116 vs 242-372)
Two parallel switch statements over the same achievement types. Adding a new type requires updating both.
**Fix:** Unify: `isAchievementEarned` becomes `getProgress(achievement).current >= getProgress(achievement).required`.

**[LOW] `markDirty` called per achievement inside loop** (line 51)
If multiple achievements unlock at once, `markDirty` is called redundantly.
**Fix:** Call once after the loop, only if `!newlyUnlocked.isEmpty()`.

---

### TranscendenceManager.java

No significant issues. Clean and focused.

---

## HUD & Toast System

### AscendHud.java

**[HIGH] `updateAscensionQuest` sends 116+ UI commands per tick** (lines 338-345)
Progress bar iterates 100 segments + 16 accents, sending visibility for all even when only 1-2 changed.
**Fix:** Track previous `filledBarSegments` / `filledAccentSegments`. Only send commands for segments that changed (the transition boundary).

**[LOW] Unnecessary `Boolean.valueOf()` boxing** (lines 106, 170, 256, 289)
```java
Boolean.valueOf(showElevation).equals(lastElevationVisible)
```
Allocates a Boolean object each tick per player. Use primitive comparison.

**[LOW] Comments restating code** (lines 38, 100, 110, 141, 160, 334)
`// Create command builder`, `// Send the update` — remove these.

---

### AscendHudManager.java

**[MED] `getOrRefreshEconomyCache` does 8+ store lookups per cache miss** (lines 361-395)
Each lookup calls `playerStore.getPlayer(playerId)` internally.
**Fix:** Get the `AscendPlayerProgress` object once and pass it to helper methods.

**[LOW] Redundant null check on final `playerStore` field** (line 92)
`playerStore` is final and set in constructor. Remove the check.

**[LOW] Duplicated screen fade/bar pattern** (lines 286-313)
`showScreenFade` and `updateScreenFadeBar` both try AscendHud then fall through to HiddenAscendHud.
**Fix:** Extract a `sendToAnyHud(UUID, UICommandBuilder)` helper.

---

### ToastManager.java

**[MED] 48 UI commands per tick for toast type toggling** (lines 85-92)
For each of 4 slots, iterates all 6 `ToastType.values()`, setting visibility even when type hasn't changed.
**Fix:** Track last-shown type per slot and only send commands when the type changes.

**[LOW] Dead `dirty` field** (line 126)
`ToastEntry.dirty` is set but never read for any decision. Remove it.

---

### AscendHologramManager.java

**[LOW] Duplicated null/availability guard** (lines 23-65)
Four methods each start with identical `if (!HylogramsBridge.isAvailable())` and map-null checks.
**Fix:** Extract `isReady(AscendMap)` helper.

---

## Robot System (Runners)

### RobotManager.java

**[HIGH] `isActiveRunnerUuid` linear scan on every ECS tick** (lines 790-803)
Called from `RunnerCleanupSystem.tick()` for every entity with Frozen+Invulnerable. O(N*M) per tick.
**Fix:** Maintain a `Set<UUID> activeEntityUuids` updated on spawn/despawn for O(1) lookups.

**[MED] `calculateSpeedMultiplier` has 5 sequential hardcoded skill checks** (lines 562-580)
```java
if (ascensionManager.hasRunnerSpeedBoost1(ownerId)) speedMultiplier *= 1.05;
if (ascensionManager.hasRunnerSpeedBoost2(ownerId)) speedMultiplier *= 1.05;
// ... 3 more
```
**Fix:** Store tiers in an array and loop over them.

**[LOW] Trivial `updatePreviousPosition` wrapper** (lines 455-457) — inline it.
**[LOW] Constant `CHUNK_LOAD_DISTANCE` declared mid-class** (line 719) — move to constants block.

---

### RobotSpawner.java

**[HIGH] Reflection-based `extractEntityRef` on every spawn** (lines 142-163)
Tries 5 method names via reflection to extract a Ref from an unknown return type.
**Fix:** Determine the actual return type of `npcPlugin.spawnNPC()` once, cast directly. At minimum cache the resolved `Method`.

**[MED] `hideFromActiveRunners` scans all robots for owner** (lines 224-230)
The caller already has `state.getOwnerId()`. Pass it as a parameter instead of searching.

---

### RobotState.java

**[MED] `setPreviousPosition` allocates `new double[3]` every movement tick** (line 160)
With many robots at 50ms intervals, this creates garbage.
**Fix:** Store x/y/z as separate fields, or allocate the array once and mutate in place.

**[LOW] Mixed AtomicInteger/volatile with no documented threading model** (lines 17-36)
Document which fields are written from which threads.

---

### RobotRefreshSystem.java

**[LOW] `drainDirtyPlayers` copies the ConcurrentHashMap.KeySetView unnecessarily** (lines 80-89)
CHM iterators are safe to iterate directly. Avoid the `new HashSet<>()` copy.

---

### RunnerCleanupSystem.java

**[HIGH] `Query.any()` fallback matches ALL entities** (lines 97-98)
If component types are null, falls back to `Query.any()` which iterates every entity in the world on every tick. The frozen/null check at line 51 filters them out, but the cost of iterating all entities is high.
**Fix:** Return an impossible query or skip registration entirely if component types are null.

---

### AutoRunnerUpgradeEngine.java

**[LOW] Repeated `ParkourAscendPlugin.getInstance()` calls** — pass the plugin instance as a parameter.
**[LOW] Duplicated iteration structure between `performAutoElevation` and `performAutoSummit`** — extract shared helper.

---

## Mine Subsystem

### MineAchievementTracker.java

**[HIGH] Synchronous DB load on game thread** (lines 175-185)
```java
private PlayerAchievementState getOrLoadState(UUID playerId) {
    state = loadFromDatabase(playerId); // blocks ECS event thread
```
If the player isn't cached (first block mined), this blocks the game tick with a DB query.
**Fix:** Pre-load state in `onPlayerJoin` so the hot path never hits the DB.

**[HIGH] Synchronous DB write on game thread** (lines 224-237)
`grantAchievement` → `saveAchievementCompletion` does a synchronous INSERT from the game thread.
**Fix:** Queue the write asynchronously like other persistence systems do.

**[MED] `checkStatAchievements` iterates all achievements on every block break** (lines 123-132)
**Fix:** Pre-partition achievements by `StatType` into static lists.

---

### MineRobotManager.java

**[MED] Non-thread-safe `ArrayList` inside `ConcurrentHashMap`** (line 81)
```java
private final Map<UUID, List<ConveyorItemState>> conveyorItems = new ConcurrentHashMap<>();
```
`computeIfAbsent(...).add()` — the add happens outside the atomic operation.
**Fix:** Use `CopyOnWriteArrayList` or synchronize access.

**[MED] `isActiveMinerUuid` O(N*M) scan** (lines 994-1002)
Nested loop over all players' miners for every orphan check.
**Fix:** Maintain a `Set<UUID>` of active miner UUIDs.

**[MED] Reflection-based `extractEntityRef`** (lines 952-971) — same issue as RobotSpawner.

---

### ConveyorItemState.java

**[MED] Triple array allocation per item per tick** (lines 56-84)
`getX`, `getY`, `getZ` each call `getPosition()` which allocates `new double[3]`.
**Fix:** Call `getPosition()` once and destructure: `double[] pos = item.getPosition(now);`

---

### MineManager.java

**[MED] Block table resolved on every zone regeneration** (lines 207-214)
`BlockType.getAssetMap().getIndex()` lookups per entry per regen. These never change at runtime.
**Fix:** Cache the resolved block table per zone.

**[LOW] `packPosition` duplicated in 3 files** (BlockDamageTracker, MineManager, MineAoEBreaker)
Extract to a shared utility.

---

### MineAoEBreaker.java

**[MED] "AoE" passed as blockTypeId to toast** (line 153)
```java
mineHudManager.showMineToast(playerId, "AoE", totalBroken);
```
Produces broken display — invalid item icon, "Aoe" as name. Use a dedicated AoE toast path.

**[LOW] AoE radius formula duplicated from MineUpgradeType** (line 63)
```java
int radius = 1 + stompLevel / 5;
```
Use `MineUpgradeType.STOMP.getEffect(stompLevel)` instead.

---

### MineBreakSystem.java / MineDamageSystem.java

**[LOW] `getWorld()` fetched twice in same method** (MineBreakSystem lines 97, 121; MineDamageSystem lines 105, 132)
**[LOW] `ParkourAscendPlugin.getInstance()` called multiple times per handler** (MineDamageSystem lines 84, 89)

---

### MineRewardHelper.java

**[LOW] Dead if/else in `handleMomentumCombo`** (lines 75-79)
Both branches call `incrementCombo()`. Remove the conditional.

---

### MinePlayerProgress.java

**[MED] `ConcurrentHashMap` used under `synchronized` methods** (lines 18-19)
```java
private final Map<MineUpgradeType, Integer> upgradeLevels = new ConcurrentHashMap<>();
```
All access is already `synchronized`. Use `EnumMap` instead — less overhead.

**[LOW] `volatile` fields inside `MinerProgress` are always accessed under `synchronized`** (lines 283-295)
The volatile modifiers add unnecessary memory barriers.

**[LOW] Dead null check in `isHoldingExpectedPickaxe`** (lines 183-184)
`getPickaxeTierEnum()` always returns a valid tier with a non-null item ID.

---

### MinePlayerStore.java

**[LOW] `flushAll` copies CHM keySet unnecessarily** (line 58) — iterate directly.
**[MED] `savePlayerSync` can NPE if plugin shuts down** (line 266) — add null check on `getInstance()`.

---

### PickaxeTier.java

**[MED] CRYSTAL/VOID/PRISMATIC requirements unreachable in single-mine system** (lines 55-62)
```java
case CRYSTAL -> unlockedMineIds.size() >= 2; // impossible with 1 mine
```
Either remove dead requirement checks or document that these tiers are locked until multi-mine returns.

---

### MineAchievement.java

**[LOW] Stale EXPLORER achievement** (line 27)
Description says "Unlock all mines" but single-mine system makes this ungrantable. Verify if it's ever checked.

---

### MineBlockRegistry.java

**[MED] Mutable static collections exposed** (lines 93-98)
```java
public static List<BlockDef> getAll() { return ALL_BLOCKS; }
```
Any caller can mutate the internal list. Wrap in `Collections.unmodifiableList()`.

---

### Mine.java / MineZone.java / MineZoneLayer.java

**[LOW] `CopyOnWriteArrayList` and `ConcurrentHashMap` for config-only data**
Populated once at startup, never modified. Use `ArrayList`/`HashMap` and treat as immutable.

---

### MineHudManager.java

**[MED] Inventory copy+sort every tick before cache check** (lines 120-126)
Allocates a new `LinkedHashMap` copy and sorts entries before checking if anything changed.
**Fix:** Compute cache key first; only sort if key changed.

---

### MineUpgradeType.java

**[LOW] `getColumnName()` likely unused** — verify if called anywhere.

---

## UI Pages (General)

### AscendMapSelectPage.java (1270 lines)

**[MED] Massive file — poor separation of concerns**
Handles: map display, runner purchase, speed upgrades, evolution, Buy All, Evolve All, affordability colors, map unlock propagation, momentum display, challenge/transcendence tab routing.
**Fix:** Extract buy/upgrade logic and affordability refresh into separate helper classes.

**[MED] Duplicated map-unlock-on-upgrade logic** (lines 646-672 vs 1029-1053)
Nearly identical unlock check + tutorial trigger + UI add logic.
**Fix:** Extract into `checkAndProcessMapUnlocks(...)`.

**[MED] `displayedMapIds` is a `List` used for `contains()` — O(n)** (line 80)
**Fix:** Use a `Set<String>`.

---

### AscendSettingsPage.java / AscendMusicPage.java

**[MED] Page re-opening instead of `sendUpdate` for every toggle**
Every setting toggle re-opens the entire page, rebuilding and re-sending the full UI.
**Fix:** Use `sendUpdate` with targeted UI property changes.

---

### AscendMusicPage.java

**[MED] Static ConcurrentHashMaps as memory leak risk** (lines 48-51)
`MUSIC_LABELS`, `MUSIC_SELECTIONS`, etc. are static maps keyed by UUID. If `clearPlayer()` isn't called on disconnect, entries accumulate forever.

---

### AutomationPage.java

**[MED] `recalculateTargetIndex` runs every 1-second refresh** (line 172)
Only matters after elevation changes, not every second.
**Fix:** Call only when targets are modified or elevation changes.

**[LOW] `SummitCategory.values()` allocates a new array every call** — cache as static field.
**[LOW] Fully qualified type names inline** — add imports.

---

### StatsPage.java

**[MED] `listMapsSorted()` called twice per 500ms refresh** (lines 115, 170)
**Fix:** Fetch once in `updateAllStats` and pass to `formatCombinedIncome`.

---

### SummitPage.java

**[LOW] Duplicate `SummitCategory[]` array definition** (lines 88-92, 138-142)
Extract to a `private static final` field.

**[LOW] `formatBonus` has unused `category` parameter** (lines 372-374) — remove it.

---

### SkillTreePage.java

**[LOW] `toPascalCase` recomputed on every call** — cache in a static `EnumMap`.

---

### AscendChallengePage.java

**[LOW] Hardcoded challenge reward descriptions** (lines 411-421)
Move `getRewardDescription()` to the `ChallengeType` enum.

**[LOW] Hardcoded "Maps 4 & 5 locked" text** (lines 377-381)
Generate dynamically from `getBlockedMapDisplayOrders()`.

---

### AscendOnboardingCopy.java / AscendTutorialPage.java / AscendWelcomePage.java

**[LOW] Array re-allocation on every build()** — cache as static fields or instance fields.

---

### BaseAscendPage.java

**[LOW] Potential memory leak in `currentPageIds`** (line 22)
Static map. Verify `removeCurrentPage` is called on disconnect.

---

### Multiple UI pages

**[LOW] Redundant null checks on fields initialized to `""`**
`AscendAdminPage`, `AscendAdminVoltPage`, `MineAdminPage`, `MineZoneAdminPage` all check `field != null ? field : ""` where the field is initialized to `""`.

**[MED] Challenge manager null-check chain duplicated 6 times** across `AscensionPage`, `ElevationPage`, `SummitPage`
```java
if (plugin != null && plugin.getChallengeManager() != null
        && plugin.getChallengeManager().isSomething())
```
**Fix:** Add `ParkourAscendPlugin.getChallengeManagerSafe()` returning `Optional<ChallengeManager>`.

---

## UI Pages (Mine)

### MineGateAdminPage.java

**[HIGH] Static position maps leak memory forever** (lines 30-33)
```java
private static final Map<UUID, double[]> entryPos1 = new ConcurrentHashMap<>();
// ... 3 more maps
```
Never cleaned up. Every admin who opens this page leaks 4 entries permanently.
**Fix:** Clear entries on page close or disconnect.

**[LOW] Duplicated min/max AABB calculation** (lines 97-98, 114-115) — extract helper.

---

### MinePage.java

**[MED] `checkPickaxeRequirement` always returns true** (lines 378-381)
```java
private boolean checkPickaxeRequirement(PickaxeTier tier) { return true; }
```
The associated requirement UI text (lines 366-369) is dead code.
**Fix:** Remove the method, variable, and dead UI logic.

**[LOW] Parallel arrays for accent colors** (lines 56-68)
Four arrays that must be kept in sync. Use a `record AccentColor(String name, String hex)`.

**[LOW] `getUpgradeDescription` is a single-line wrapper** (lines 714-716) — inline it.

---

### MineSellPage.java / MineBagPage.java

**[HIGH] `MineSellPage` is near-complete duplication of `MineBagPage`**
Same `gatherAllPrices()`, same `populateContent` structure, same sell-all logic, same achievement tracking.
**Fix:** Delete `MineSellPage` and use `MineBagPage` everywhere, or extract shared sell logic into a utility class.

---

### MineBlockHpPage.java

**[LOW] Per-block store lookups in loop** (lines 183, 189)
Fetch HP and price maps once before the loop.

**[LOW] Stale Javadoc referencing deleted pages** (lines 25-28)

---

### MineAdminPage.java

**[LOW] Wrong comment on block X offset** (line 298-299)
Comment says "+Z direction" but code offsets X by 2.

**[LOW] Hardcoded `5` for slot count** (line 319) — extract constant.

---

### MineZoneAdminPage.java

**[LOW] `handlePos1`/`handlePos2` are identical except map variable** (lines 204-241) — extract helper.
**[LOW] Zone info string built identically in two places** — extract helper.

---

## Cross-Cutting Themes

### 1. Singleton Access Pattern
`ParkourAscendPlugin.getInstance()` is called repeatedly within single methods across ~20 files. Cache as a local variable or constructor-inject the specific manager.

### 2. Reflection for NPC Spawning
Both `RobotSpawner` and `MineRobotManager` use trial-and-error reflection to extract entity refs from `npcPlugin.spawnNPC()`. Determine the actual return type once and cast directly.

### 3. Static Map Memory Leaks
At least 6 static `ConcurrentHashMap` instances keyed by player UUID are never cleaned on disconnect:
- `AscendAdminCommand.minePos1` / `minePos2`
- `AscendLeaveInteraction.PENDING_LEAVES`
- `AscensionCinematic.LAST_WARNING_BY_PHASE`
- `MineGateAdminPage.entryPos1/entryPos2/exitPos1/exitPos2`
- `AscendMusicPage` static maps

### 4. Concurrent Collection Overuse
Multiple classes use `ConcurrentHashMap` or `CopyOnWriteArrayList` for data that is either:
- Only written at startup (Mine/MineZone config data)
- Always accessed under `synchronized` (MinePlayerProgress)
Use plain collections where concurrency overhead isn't needed.

### 5. Redundant Null Checks on Initialized Fields
~10 UI pages check `field != null ? field : ""` where the field is initialized to `""` and never set to null.

### 6. `packPosition` Duplicated 3x
BlockDamageTracker, MineManager, MineAoEBreaker all define identical bit-packing methods. Extract to a shared utility.
