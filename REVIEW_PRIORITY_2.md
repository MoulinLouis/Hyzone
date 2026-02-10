# Review Priority 2

Scope: High-priority operational risks. Focus on async backpressure, world-transition correctness, and production command exposure.

## Cluster: Scheduler and Refresh Backpressure

- [x] **Ascend tick loop can enqueue unbounded per-world async work**
  File/location:
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java:329`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java:431`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java:438`
  Why it matters:
  Async queue depth can grow under lag and amplify jitter.
  Non-breaking fix checklist:
  - [x] Add per-world in-flight guard (e.g., `ConcurrentHashMap<World, AtomicBoolean>`).
  - [x] Skip enqueue when a world tick job is already running.
  - [x] Clear in-flight flag in `finally`.
  - [x] Keep existing tick frequency and logic unchanged.
  Uncertainty:
  Low.

- [x] **Ascend UI auto-refresh pages can stack overlapping async updates**
  File/location:
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java:582`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/ElevationPage.java:179`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/SummitPage.java:197`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/StatsPage.java:205`
  Why it matters:
  Overlapping UI refresh jobs cause stale churn and unnecessary CPU usage.
  Non-breaking fix checklist:
  - [x] Introduce shared page refresh helper for these pages.
  - [x] Add `refreshInFlight` guard per page.
  - [x] Coalesce overlapping refresh ticks.
  - [x] Keep intervals and visible behavior unchanged.
  Uncertainty:
  Low.

## Cluster: State Transition Correctness

- [x] **Passive earnings leave-time can be reset repeatedly on world transitions**
  File/location:
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java:281`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java:306`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/passive/PassiveEarningsManager.java:190`
  Why it matters:
  Repeated timestamp resets can undercount offline passive earnings.
  Non-breaking fix checklist:
  - [x] Track explicit in-memory `Ascend -> non-Ascend` edge transition state.
  - [x] Trigger leave handler only on real edge transition.
  - [x] On disconnect, call leave handler only if player was in Ascend state.
  - [x] Keep schema and reward formulas unchanged.
  Uncertainty:
  Medium (depends on runtime event sequence).

## Cluster: Production Surface Hardening

- [x] **Temporary/test commands are registered in production setup at Adventure permission level**
  File/location:
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java:182`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java:183`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/CinematicTestCommand.java:35`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/CinematicTestCommand.java:48`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/HudPreviewCommand.java:21`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/HudPreviewCommand.java:29`
  Why it matters:
  Broadens production debug surface and can impact UX/perf.
  Non-breaking fix checklist:
  - [x] Add config flag `ascend.enableTestCommands` (default `false`).
  - [x] Register these commands only when flag is enabled.
  - [x] Add OP guard in command entry as defense-in-depth.
  - [x] Keep command behavior unchanged for dev environments.
  Uncertainty:
  Medium (external ACLs may already restrict access).
