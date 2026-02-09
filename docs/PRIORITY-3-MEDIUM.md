# Priority 3 — Medium (Duplication, Optimization, Consistency)

Code duplication, per-tick allocations, and consistency improvements. Grouped by file/system.

---

### Cluster: HyvexaHubPlugin.java — Full cleanup pass
> All in the same file. Do in one editing session.

- [x] **#22 — `hubHudAttached` should be a Set**
  - `ConcurrentHashMap<UUID, Boolean>` only ever stores `true`. Semantically a Set.
  - **Fix:** Replace with `ConcurrentHashMap.newKeySet()`. Update `put` → `add`, `get` → `contains`.

- [x] **#23 — Redundant HUD reset every tick**
  - Every 200ms, `setCustomHud()` called even if already set.
  - **Fix:** Remove from tick loop. `needsAttach` check already handles new arrivals.

- [x] **#24 — Fully-qualified class names despite existing imports**
  - Lines 148, 152 use FQCNs for already-imported `CompletableFuture` and `Player`.
  - **Fix:** Replace with short names.

- [x] **#31 — `isHubWorld(Store)` duplicates logic**
  - Reimplements world-name check instead of delegating to `isHubWorld(World)`.
  - **Fix:** Extract world and delegate.

- [x] **#34 — Same-package import**
  - `import io.hyvexa.hub.HubConstants;` — unnecessary.
  - **Fix:** Remove.

- [x] **#36 — `collectPlayersByWorld` allocations every tick**
  - New `HashMap` + `ArrayList` every 200ms. Only processes one world.
  - **Fix:** Iterate players directly, check `isHubWorld` inline.

- [x] **#37 — `hubHudReadyAt` magic number**
  - Undocumented 250ms delay.
  - **Fix:** Extract as named constant with comment.

---

### Cluster: Hub routing/menu — Constants + shared logic + slop comments
> Creating `HubConstants` (#15) enables the shared routing method (#30).

- [x] **#15 — World name constants duplicated across 4 files**
  - `"Hub"`, `"Parkour"`, `"Ascend"` defined separately in `HyvexaHubPlugin`, `HubRouter`, `HubCommand`, `HubMenuInteraction`.
  - **Fix:** Move to `HubConstants`, reference from all 4 files.

- [x] **#30 — `HubCommand` and `HubMenuInteraction` duplicate routing logic**
  - Both implement: check if in hub world -> open menu, otherwise -> route.
  - **Fix:** Extract shared method (e.g., `HubRouter.openMenuOrRoute()`).

- [x] **#35 — AI slop comments in `HubMenuPage`**
  - Lines 88, 97, 104 restate the code. Line 89 is useful.
  - **Fix:** Remove 88, 97, 104. Keep 89.

---

### Cluster: FormatUtils.java — Dead code + optimization

- [ ] **#9 — Dead code in `FormatUtils` (+ `HylogramsBridge`, `CommandUtils`, `SystemMessageUtils`)**
  - `formatVexaForHud`, `formatVexaForHudDecimal` zero callers. ~10 `HylogramsBridge` methods unused. `getArgOrDefault`, `ggPrompt` unused.
  - **Fix:** Delete all dead methods.

- [ ] **#10 — `formatBigNumber` suffix table re-allocated every call**
  - Fresh `String[][]` + `Integer.parseInt` in loop on every HUD update.
  - **Fix:** Make `private static final` with pre-parsed exponents.

---

### Cluster: DuelTracker.java — Duplication + unused fields

- [ ] **#17 — Duplicated utility methods between `RunTracker` and `DuelTracker`**
  - 6 methods duplicated (~200 lines): `distanceSqWithVerticalBonus`, `teleportToSpawn`, `resolveCheckpointIndex`, `shouldRespawnFromFall`, sound methods.
  - **Fix:** Extract into `TrackerUtils`. Both trackers call shared implementations.

- [ ] **#20 — `DuelPlayerState` has unused fields**
  - `playerId` and `matchId` stored but never read.
  - **Fix:** Remove unused fields and constructor assignments.

---

### Standalone items

- [ ] **#12 — `BigNumber.normalize` uses O(n) loop instead of O(1) log10**
  - **File:** `hyvexa-core/.../math/BigNumber.java:85-95`
  - **Fix:** Replace with `Math.floor(Math.log10(abs))`.

- [ ] **#13 — `DatabaseManager` redundant `Class.forName`**
  - **File:** `hyvexa-core/.../db/DatabaseManager.java:84-89`
  - **Fix:** Remove the try-catch block.

- [ ] **#16 — `PaginationState` duplicated verbatim between modules**
  - **Files:** `hyvexa-parkour/.../ui/PaginationState.java`, `hyvexa-parkour-ascend/.../ui/PaginationState.java`
  - **Fix:** Move to `hyvexa-core` and update imports.

- [ ] **#18 — Duplicated `applyDropFilter`**
  - **Files:** `InventoryUtils.java:191-201`, `InventorySyncManager.java:232-242`
  - **Fix:** `InventorySyncManager` delegates to `InventoryUtils`. Remove duplicates.

- [ ] **#19 — `DuelConstants.MSG_WIN_VS` unused duplicate**
  - **File:** `DuelConstants.java:36`
  - **Fix:** Delete line.
