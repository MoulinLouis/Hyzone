# Priority 1 — Critical (Bugs, Leaks, Crashes)

Active bugs, memory leaks, and crash risks. Grouped by file/system for single-pass fixes.

---

### Cluster: HubRouter.java — Memory leak + refactor + cleanup
> Fix #1 and #14 first (the core issues), then clean up the rest in the same pass.

- [x] **#1 — Memory Leak: `HubRouter.hubHuds` never cleaned up**
  - **File:** `hyvexa-hub/.../routing/HubRouter.java:35,195`
  - `hubHuds` map entries added via `attachHubHud()` but **never removed**. No disconnect handler. Pure leak.
  - **Fix:** Remove `hubHuds` field from `HubRouter`. Delegate to plugin's map.

- [x] **#14 — Massive code duplication between `HubRouter` and `HyvexaHubPlugin`**
  - **Files:** `HubRouter.java:164-229`, `HyvexaHubPlugin.java:186-218`
  - Near-identical `clearInventory`, `giveHubItems`, `clearContainer`, `attachHubHud`. Both fire on player arrival → **double execution**.
  - **Fix:** Centralize into shared utility. Router handles teleportation only.

- [x] **#28 — `HubRouter.resolveWorld()` has excessive fallback logic**
  - **File:** `HubRouter.java:111-135`
  - Four fallback levels with redundant `findWorldByName` calls. Worlds are preloaded.
  - **Fix:** Simplify to `getWorld()` -> `loadWorld()` fallback -> default world.

- [x] **#29 — Unused `TransformComponent` fetch**
  - **File:** `HubRouter.java:74-76`
  - `transform` fetched and null-checked but never used.
  - **Fix:** Remove the fetch and its null check.

- [x] **#44 — Empty constructor**
  - **File:** `HubRouter.java:37-38`
  - **Fix:** Remove. Java generates it automatically.

- [x] **#45 — Comment restating the code**
  - **File:** `HubRouter.java:82`
  - **Fix:** Remove.

- [x] **#46 — Overly defensive null checks that cannot fail**
  - **File:** `HubRouter.java:53-58, 101-102, 142`
  - Null checks on string literals, `playerRef.getUuid()`, already-checked values.
  - **Fix:** Remove provably impossible checks.

---

### Cluster: Whitelist system — Thread safety + disk I/O + logging
> Fix #7 first (thread safety), then #2 (use cached instance), then #11 (logging/charset).

- [x] **#7 — Thread safety: `WhitelistRegistry.instance` not volatile + non-thread-safe `HashSet`**
  - **Files:** `WhitelistRegistry.java:10`, `AscendWhitelistManager.java:25,27`
  - **Fix:** Add `volatile` to `instance` and `enabled`. Replace `HashSet` with `ConcurrentHashMap.newKeySet()`.

- [x] **#2 — `AscendWhitelistManager` re-created from disk on every button click**
  - **File:** `hyvexa-hub/.../ui/HubMenuPage.java:90-94`
  - **Fix:** Use `WhitelistRegistry.get()` instead of constructing a new instance.

- [x] **#11 — Wrong logging framework + missing charset**
  - **File:** `AscendWhitelistManager.java`
  - Uses SLF4J (should be `HytaleLogger`). `FileReader`/`FileWriter` use platform-default charset.
  - **Fix:** Replace with `HytaleLogger` and `Files.newBufferedReader/Writer(path, StandardCharsets.UTF_8)`.

---

### Cluster: AscendCommand.java — Leak + page tracking + boilerplate
> Fix #32 first (extract helper), then #3 and #33 are cleaner to add.

- [ ] **#32 — Repeated plugin-instance-fetch pattern (12 times)**
  - **File:** `AscendCommand.java`
  - **Fix:** Extract `requirePlugin(Player)` helper. Replace 12 occurrences.

- [ ] **#3 — `onPlayerDisconnect()` is never called**
  - **File:** `AscendCommand.java:70-72`
  - `activePages` map entries leak on disconnect.
  - **Fix:** Add `AscendCommand.onPlayerDisconnect(playerId);` in `ParkourAscendPlugin.java` disconnect handler (~line 310).

- [ ] **#33 — `openHelpPage()` doesn't close active page**
  - **File:** `AscendCommand.java:223-225`
  - Breaks page-tracking convention (no `closeActivePage` / `registerActivePage`).
  - **Fix:** Add both calls, matching all other open methods.

---

### Cluster: AscendConstants.java — Unicode + stale docs
> All quick text fixes in the same file.

- [x] **#5 — Unicode characters display as `?` in Hytale chat**
  - **File:** `AscendConstants.java:560-561`
  - **Fix:** Replace `\u00d7` with `x`, `\u2192` with `->`.

- [x] **#49 — Javadoc exponents don't match constants**
  - **File:** `AscendConstants.java:284-293`
  - Javadoc says `0.77`/`0.63`, actual constants are `0.72`/`0.58`.
  - **Fix:** Update Javadoc to match.

- [x] **#51 — Stale removal comment**
  - **File:** `AscendConstants.java:10`
  - **Fix:** Delete line.

---

### Cluster: AscendPlayerStore.java — Unbounded query + cleanup
> Fix #48 first (the real issue), then clean up #52 and #53 in the same pass.

- [ ] **#48 — Leaderboard query fetches ALL players without LIMIT**
  - **File:** `AscendPlayerStore.java:1497-1501`
  - **Fix:** Add `ORDER BY ... LIMIT 100`.

- [ ] **#52 — Orphaned Javadoc**
  - **File:** `AscendPlayerStore.java:1149-1153`
  - **Fix:** Delete orphaned doc block.

- [ ] **#53 — Unreachable null check after `getOrCreatePlayer`**
  - **File:** `AscendPlayerStore.java:750-752`
  - **Fix:** Remove null check.

---

### Standalone

- [ ] **#8 — `DatabaseConfig.load` missing `JsonParseException` catch**
  - **File:** `hyvexa-core/.../db/DatabaseConfig.java:32-40`
  - Malformed JSON crashes plugin initialization.
  - **Fix:** Change `catch (IOException e)` to `catch (IOException | com.google.gson.JsonParseException e)`.
