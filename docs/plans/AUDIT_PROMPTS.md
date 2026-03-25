# Codebase Audit — Prioritized Prompts

Generated 2026-03-24 from a full-codebase audit. Run each prompt in a fresh session, in order.
Excludes the `runorfall` module.

---

## Tier 1 — Bugs and Broken Behavior

### 1. Fix stale-read race in AscendPlayerStore volt side effects

In `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java`, `atomicAddVolt()` (~line 372) reads `oldBalance` before calling `addVolt()` (which is itself atomic via `AtomicReference.updateAndGet` in `EconomyState`). However, another thread can call `addVolt` between the `getVolt()` at line 374 and the `addVolt()` at line 375, making `oldBalance` stale. The tutorial threshold check at line 378 then uses a stale before-value, potentially firing or missing the tutorial trigger. Use a CAS loop that captures both old and new values atomically, or synchronize the read-modify-check sequence per player.

### 2. Fix double-check locking bug in MineConfigStore

In `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/data/MineConfigStore.java`, `listMinesSorted()` (~lines 412-429) acquires a **read lock** to write to `sortedCache`. Multiple threads can enter simultaneously and race on the write. Change `lock.readLock().lock()` to `lock.writeLock().lock()` in that method.

### 3. Fix race condition in CachedCurrencyStore.modifyBalance

In `hyvexa-core/src/main/java/io/hyvexa/core/economy/CachedCurrencyStore.java`, the `modifyBalance()` method (~line 186) has a TOCTOU race: it checks staleness, loads from DB, then computes — but concurrent calls on the same player can interleave. If `persistToDatabase` fails, the cache temporarily holds the wrong value. Make the entire modify operation (check + load + compute + persist) atomic under `cache.compute()` with a single synchronized block per player UUID.

### 4. Fix race condition in CosmeticStore.purchaseShopCosmetic

In `hyvexa-core/src/main/java/io/hyvexa/core/cosmetic/CosmeticStore.java`, `purchaseShopCosmetic()` (~line 117) checks balance then deducts via `CurrencyBridge.deduct()` in separate steps. Multiple concurrent purchases can all pass the balance check before any deduction occurs. Perform the balance check + deduction in a single database transaction with row-level locking.

### 5. Fix TOCTOU race in PurgeInstanceManager.acquireAvailableInstance

In `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeInstanceManager.java`, `acquireAvailableInstance()` (~line 69) checks `leasedInstanceIds.contains()` and then `add()` in separate steps on a ConcurrentHashMap key set. Two threads can claim the same instance. Use `leasedInstanceIds.add(id)` as the atomic check-and-claim (it returns false if already present) instead of separate contains+add.

### 6. Fix non-atomic kill streak recording in PurgeSessionPlayerState

In `hyvexa-purge/src/main/java/io/hyvexa/purge/data/PurgeSessionPlayerState.java`, `recordKillStreak()` (~lines 81-93) reads and writes multiple volatile fields (`lastKillTimeMs`, `killStreak`, `bestCombo`) without synchronization. Concurrent kills produce incorrect streak counts. Synchronize the entire method.

### 7. Fix unsafe array access in AbstractGhostRecorder.stopRecording

In `hyvexa-core/src/main/java/io/hyvexa/common/ghost/AbstractGhostRecorder.java`, `stopRecording()` (~lines 80-89) reads `recording.samples.size()` without synchronization, while `sampleActiveRecordings()` modifies it inside a synchronized block. Move the size check inside a `synchronized (recording.samples)` block.

### 8. Fix unhandled promise rejection crash in discord-bot syncRankRoles

In `discord-bot/src/index.js`, `syncRankRoles()` (~line 56) is called via `setInterval()` without awaiting. An unhandled promise rejection (e.g., Discord API failure during member fetch) crashes the Node.js process. Wrap the `setInterval` callback body in a try-catch, or wrap the `syncRankRoles()` call: `setInterval(() => syncRankRoles().catch(err => console.error('Rank sync failed:', err)), ...)`.

### 9. Fix null dereference in HubMenuInteraction

In `hyvexa-hub/src/main/java/io/hyvexa/hub/interaction/HubMenuInteraction.java` (~line 33), `store.getExternalData().getWorld()` is called without null-checking `getExternalData()`. If it returns null, the interaction handler crashes. Add an early return if `store.getExternalData() == null`, matching the defensive pattern used in `HubRouter`.

### 10. Fix null dereference in CosmeticManager

In `hyvexa-core/src/main/java/io/hyvexa/core/cosmetic/CosmeticManager.java` (~lines 204, 285, 300), `store.getExternalData().getWorld()` is called without null-checking `getExternalData()`. Add explicit null checks before each dereference chain.

### 11. Fix floating-point precision loss in Ascend cashback calculation

In `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/system/MineRewardHelper.java` (~line 57), the cashback formula `Math.floor(blockPrice * blocksGained * cashbackPercent / 100.0 * 100.0) / 100.0` uses double arithmetic that loses precision for large values. The current code intentionally truncates (floors) to cents — preserve that truncation behavior. Replace the intermediate arithmetic with `BigDecimal` to avoid IEEE 754 precision loss on large `blockPrice * blocksGained` products, then apply `BigDecimal.setScale(2, RoundingMode.FLOOR)` to maintain the existing floor semantics.

### 12. Fix unbounded upgrade accumulation in PurgeUpgradeState

In `hyvexa-purge/src/main/java/io/hyvexa/purge/data/PurgeUpgradeState.java`, `addValue()` uses `merge()` to accumulate upgrade values with no cap. Duplicate or repeated upgrades grow unbounded, leading to extreme stat boosts. Add a maximum cap per upgrade type or validate that upgrades are only applied once per wave.

### 13. Fix VoteProcessor null dereference on voteSites

In `hyvexa-votifier/src/main/java/org/hyvote/plugins/votifier/http/VoteProcessor.java` (~line 121), `plugin.getConfig().voteSites()` can return null, but `.isV2Enabled()` is called on it without a null check. Add a null guard before accessing voteSites methods.

### 14. Fix PurgeWaveConfigManager removeWave transaction inconsistency

In `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeWaveConfigManager.java` (~lines 83-100), if the delete+shift transaction fails, the in-memory `waves` map is not rolled back, causing DB/memory divergence. Only call `waves.remove(waveNumber)` after the transaction commits successfully.

### 15. Fix ZombieAggroBooster missing recursion depth limit

In `hyvexa-purge/src/main/java/io/hyvexa/purge/util/ZombieAggroBooster.java`, `traverseAndBoost()` (~lines 54-87) recurses into child instructions without a depth limit. A malformed or deeply nested instruction tree causes `StackOverflowError`. Add a depth parameter and bail out beyond a reasonable maximum (e.g., 20).

---

## Tier 2 — Structural and Architectural Issues

### 16. Make CurrencyBridge.deduct atomic

In `hyvexa-core/src/main/java/io/hyvexa/core/economy/CurrencyBridge.java`, `deduct()` (~line 34) calls `getBalance()` then `removeBalance()` — two separate operations. Add a `deductIfSufficient()` method to the CurrencyStore interface that performs balance check + deduction atomically in one operation, and use it from CurrencyBridge.

### 17. Fix SQLiteVoteStorage single-connection concurrency

In `hyvexa-votifier/src/main/java/org/hyvote/plugins/votifier/storage/SQLiteVoteStorage.java` (~line 42), a single `Connection` is shared across all threads. Concurrent vote handlers can cause SQLITE_BUSY errors. Either open the connection in WAL mode, add a connection pool, or serialize access with a lock.

### 18. Fix swallowed exceptions in AnalyticsStore.tryAlterColumn

In `hyvexa-core/src/main/java/io/hyvexa/core/analytics/AnalyticsStore.java`, `tryAlterColumn()` (~line 107) catches all `SQLException` and only logs if the message doesn't contain "Duplicate column". This silently hides real schema migration failures (permissions, syntax, connectivity). Log unexpected SQL errors as SEVERE; only suppress the expected "Duplicate column" case.

### 19. Fix BasePlayerStore silent row parsing failures

In `hyvexa-core/src/main/java/io/hyvexa/core/db/BasePlayerStore.java`, `loadAll()` (~line 74) catches all exceptions per row and only reports a count at the end. Log the UUID and exception for each failed row so corrupted data can be identified and repaired.

### 20. Close VoteManager HttpClient on shutdown

In `hyvexa-core/src/main/java/io/hyvexa/core/vote/VoteManager.java`, the `HttpClient` created in `initialize()` (~line 84) is never closed — `shutdown()` sets it to null without calling `close()`. The executor threads are independently shut down via `shutdownExecutors()`, so this is not a thread leak, but the HttpClient may hold other internal resources (connection pool, SSL contexts). Add `httpClient.close()` before nulling for clean resource release.

### 21. Add input validation to discord-bot /link command

In `discord-bot/src/index.js` (~line 120), the code sanitizes the link code (uppercase, strip dashes/spaces) and checks length == 6, but doesn't validate that the result is alphanumeric. Add a regex check `/^[A-Z0-9]{6}$/` after sanitization to reject malformed codes before hitting the database.

---

## Tier 3 — Incremental Improvements

### 22. Fix floating-point timer precision in RunTracker

In `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunTracker.java`, `advanceRunTime()` (~line 1096) discards `elapsedRemainderMs` when falling back to `System.currentTimeMillis()`. Preserve the remainder across fallback paths and consider using `System.nanoTime()` for sub-millisecond precision to prevent accumulated drift in leaderboard times.

### 23. Optimize AbstractTrailManager active-trail checking

In `hyvexa-core/src/main/java/io/hyvexa/core/trail/AbstractTrailManager.java`, `hasActiveTrails()` (~line 215) iterates all managers and all trails to decide whether to cancel the tick task — O(n*m) every tick. Replace with a global `AtomicInteger` counter that increments on trail start and decrements on trail stop for O(1) checking.

### 24. Add snapshot to WaveDeathTracker zombie iteration

In `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/WaveDeathTracker.java` (~line 56), the death collection loop iterates `session.getAliveZombies()` while spawning logic can modify it concurrently. Take a snapshot before iteration to prevent missed or double-counted zombies.

### 25. Add snapshot to PurgeWaveManager retargetZombies

In `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeWaveManager.java`, `retargetZombies()` (~line 640) iterates `session.getAliveZombies()` without a snapshot. If a zombie becomes invalid mid-iteration, `store.getComponent()` fails silently. Snapshot the set before iterating and collect invalid refs for separate cleanup.

### 26. Log silent analytics exception in HubRouter

In `hyvexa-hub/src/main/java/io/hyvexa/hub/routing/HubRouter.java` (~line 77), the analytics `logEvent` call silently swallows all exceptions. Add at least a WARNING-level log so analytics failures are visible during debugging.

### 27. Review Purge lootbox drop rate scaling

In `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/WaveDeathTracker.java` (~line 153), lootbox drops are rolled independently per-zombie-per-player. With 4 players and 10 zombies, that is 40 independent rolls — the effective drop rate scales multiplicatively with player count. Verify this is intended behavior; if not, change to one roll per zombie shared across players.

### 28. Replace ShopTabRegistry linear search with HashMap

In `hyvexa-core/src/main/java/io/hyvexa/common/shop/ShopTabRegistry.java`, `getTab()` (~line 24) does a linear scan over the tab list. Add a `ConcurrentHashMap<String, ShopTab>` index alongside the list for O(1) lookups by ID.

### 29. Remove unnecessary volatile on AscendPlayerStore manager references

In `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java` (~lines 52-60), manager references are `volatile` but only set once during initialization via `setRuntimeServices()`. The volatile barrier on every read is unnecessary. Make them effectively-final fields set once, or use lazy initialization with proper happens-before guarantees.

### 30. Standardize null-checking pattern for getExternalData across modules

Multiple modules use different patterns for null-checking `store.getExternalData()`: ternary chains (HyvexaHubPlugin), direct dereference (HubMenuInteraction), explicit early return (HubRouter). Standardize on the explicit early-return pattern across all call sites for consistency and crash prevention.
