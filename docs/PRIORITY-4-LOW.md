# Priority 4 — Low (Minor Code Issues, UI Consistency)

Minor code quality improvements and UI file consistency fixes.

---

## Code

- [x] **#25 — `ParkourAscendPlugin` tautological null check**
  - **File:** `ParkourAscendPlugin.java:168`
  - `hologramManager` just assigned via `new` — can never be null.
  - **Fix:** Remove the check.

- [x] **#26 — `PlayerVisibilityFilterSystem` is an empty legacy wrapper**
  - **File:** `hyvexa-parkour/.../system/PlayerVisibilityFilterSystem.java`
  - Empty class extending `EntityVisibilityFilterSystem`.
  - **Fix:** ~~Search for references. If none, delete. Otherwise leave for future cleanup.~~ Deleted wrapper, replaced usage in HyvexaPlugin.java with parent class `EntityVisibilityFilterSystem` directly.

- [x] **#27 — `InventoryUtils` OP/non-OP code duplication**
  - **File:** `InventoryUtils.java:33-96,115-139`
  - OP and non-OP branches with nearly identical item-placement logic.
  - **Fix:** Extracted into `prepareInventory()` and `finalizeInventory()` private helpers with boolean isOp parameter.

## UI Files

- [x] **#38 — Three inconsistent back/close button styles across UI files**
  - Style A (no hover): 7 files. Style B (gold hover): 9 files. Style C (blue-gray): 9 files.
  - **Fix:** Standardized per audience — admin files (gold text, dark bg), player content pages (blue-gray text, darker bg), player utility pages (white-on-light in Group wrappers), and parkour pages (blue-gray #1a2530) are each internally consistent with proper hover states.

- [x] **#39 — Tutorial templates duplicated 6-7 times (~900 lines)**
  - 6 `Ascend_Tutorial_*.ui` files share identical ~189-line structure.
  - **Fix:** Added missing `#TipBox` element (hidden) to 4 files that lacked it, ensuring all 6 have identical structural template for synchronized future changes.

- [x] **#40 — CloseButton style duplicated across 8+ Ascend files (~96 lines)**
  - **Fix:** Maintenance note — changes must hit all 8 files simultaneously.
  - Convention: Ascend admin files use gold `#93844c` on `#000000(0.13)`, player content pages use blue-gray `#9fb0ba` on `#000000(0.3)`.

- [x] **#41 — Pagination block duplicated in 5 parkour files (~150 lines)**
  - **Fix:** Maintenance note — keep all 5 in sync.
  - Files: Parkour_Leaderboard, Parkour_AdminPlayers, Parkour_MapLeaderboard, Duel_Leaderboard, Parkour_PlaytimeAdmin.

- [x] **#42 — Element-level `Background:` on TextButtons (Style A files)**
  - **Fix:** Moved `Background:` into `TextButtonStyle(Default: (Background: ...))` and added `Hovered` states for all 7 affected files.

- [x] **#43 — `#BackButton` vs `#CloseButton` naming inconsistency**
  - Parkour uses `#BackButton`, Ascend uses `#CloseButton` for same function.
  - **Convention chosen:** `#BackButton` for Parkour, `#CloseButton` for Ascend main pages. Tutorials and Welcome use `#BackButton` (they have multi-step navigation). Retroactive rename not needed — convention is already consistent within each module.
