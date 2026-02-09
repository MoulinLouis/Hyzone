# Priority 5 — Trivial (Polish)

Cosmetic cleanup and documentation-only items.

---

- [x] **#47 — Off-screen invisible labels used as data carriers**
  - **File:** `Parkour_RunRecordsHud.ui:140-150`
  - Labels positioned off-screen with zero dimensions, used as invisible data carriers.
  - **Fix:** No code change. Document as known workaround pattern.

- [x] **#50 — Unused `Gson`/`TypeToken` in `AscendMapStore`**
  - **File:** `AscendMapStore.java:3-4,25`
  - Never referenced. Allocates unused `Gson` instance.
  - **Fix:** Remove imports and `GSON` field.
