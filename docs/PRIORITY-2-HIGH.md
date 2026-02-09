# Priority 2 — High (Dead Code, Thread Safety)

Significant dead code and remaining thread safety issues.

---

- [x] **#4 — Dead code: `HyvexaPlugin.tickMapDetection()` and `mapDetectionTask`**
  - **File:** `hyvexa-parkour/.../HyvexaPlugin.java:137,632-669,895`
  - `mapDetectionTask` declared and canceled but **never scheduled**. `tickMapDetection()` never called. Replaced by `RunTrackerTickSystem`.
  - **Fix:** Remove the field, its cancellation in `shutdown()`, and the entire method.

- [x] **#6 — Three entirely unused classes in `hyvexa-core`**
  - **Files:**
    - `hyvexa-core/.../inventory/ModeInventoryManager.java` (230 lines)
    - `hyvexa-core/.../mode/Mode.java` (97 lines)
    - `hyvexa-core/.../mode/ModeType.java` (81 lines)
  - ~400 lines of dead code from a planned refactoring that never materialized.
  - **Fix:** Delete all three files.

- [x] **#21 — `HubHud.applied` field is not thread-safe**
  - **File:** `hyvexa-hub/.../hud/HubHud.java:9`
  - Read/written from multiple threads without synchronization.
  - **Fix:** Declare as `private volatile boolean applied;`.
