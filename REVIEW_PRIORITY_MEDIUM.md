# Priority Medium Tasklist (AI Slop + Optimization)

Date: 2026-02-10
Source: `AI_SLOP_DEEP_DIVE_FINDINGS.md`

## Cluster A: Correctness and Stability

### M-1: `ProgressionResult` data contract is misleading
Evidence:
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/ProgressStore.java:323`
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/ProgressStore.java:321`
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunTracker.java:847`

Tasklist (non-breaking):
- [x] Compute `xpAwarded` as `max(0, newXp - oldXp)` in `recordMapCompletion`.
- [x] Keep current callback-driven persistence warning path as the authoritative signal for save success.
- [x] If `completionSaved` remains, document it as queue/attempt status instead of hard save confirmation.
- [x] Preserve existing method signatures and call sites.

### M-3: `MapAdminPage.readTransform()` null safety gap
Evidence:
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/MapAdminPage.java:752`
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/MapAdminPage.java:756`
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/MapAdminPage.java:758`
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/MapAdminPage.java:761`

Tasklist (non-breaking):
- [x] Return `null` from `readTransform(...)` when position is missing.
- [x] Return `null` from `readTransform(...)` when selected rotation is missing.
- [x] Reuse existing caller null-guard behavior to keep user-facing flow unchanged.

## Cluster B: Observability and Polling Efficiency

### M-2: Async tasks swallow failures or omit exception reporting
Evidence:
- `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java:593`
- `hyvexa-hub/src/main/java/io/hyvexa/hub/HyvexaHubPlugin.java:102`
- `hyvexa-hub/src/main/java/io/hyvexa/hub/routing/HubRouter.java:62`
- `hyvexa-parkour/src/main/java/io/hyvexa/manager/HudManager.java:73`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/system/AscendFinishDetectionSystem.java:72`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/CinematicTestCommand.java:194`

Tasklist (non-breaking):
- [x] Add a shared async execution helper that logs exceptions with action/player/map context.
- [x] Replace silent catches with throttled warning logs to avoid log spam.
- [x] Keep gameplay flow best-effort (log and continue, no disruptive rethrow on user paths).

### M-4: `AscendMapSelectPage` refresh path does repeated scans and side-effect checks
Evidence:
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java:584`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java:632`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java:699`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java:1073`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java:1119`

Tasklist (non-breaking):
- [x] Build a single per-refresh snapshot of unlocked map state plus affordability.
- [x] Reuse snapshot data for row rendering and aggregate button states (`Buy All`, `Evolve All`).
- [x] Restrict unlock mutations to explicit action handlers (select/purchase/completion), not polling/refresh.
- [x] Keep refresh cadence and rendered output behavior unchanged.

## Cluster C: Maintainability and Refactor Safety

### M-5: Dispatch/handler chain complexity and drift risk
Evidence:
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/MapAdminPage.java:96`
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/MapAdminPage.java:119`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/AscendCommand.java:128`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/AscendCommand.java:146`

Tasklist (non-breaking):
- [x] Replace large if/switch routing blocks with dispatch maps (`Map<String, Runnable/Consumer>`).
- [x] Add a shared helper for open/close/register page lifecycle boilerplate.
- [x] Keep command strings, event payload keys, and routing semantics unchanged.

### M-6: Monolith-sized classes mix unrelated concerns
Evidence (line counts):
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java` (1763)
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunTracker.java` (1592)
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotManager.java` (1190)
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java` (1167)
- `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java` (865)

Tasklist (non-breaking):
- [x] Extract internal collaborators first (persistence adapters, UI renderers, dispatchers, tick services).
- [x] Keep current classes as stable facades/delegators while moving logic behind them.
- [x] Migrate in small slices and verify behavior parity after each slice.
- [x] Avoid public entry-point or external contract changes during extraction.
