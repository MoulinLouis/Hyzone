# Review Priority 3

Scope: Medium-priority reliability and maintainability issues.

## Cluster: Access and Observability

- [x] **Additional debug/admin command surface appears broadly accessible**
  File/location:
  `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java:237`,
  `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java:238`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/command/ParkourAdminItemCommand.java:27`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/command/ParkourMusicDebugCommand.java:30`
  Why it matters:
  Potentially exposes admin/debug actions to non-staff users.
  Non-breaking fix checklist:
  - [x] Add explicit OP checks in command handlers.
  - [x] Keep command names/syntax unchanged.
  - [x] Clarify descriptions/help text for staff-only usage.
  - [ ] Optionally gate registration via debug config flag.
  Uncertainty:
  Medium (external ACL may already block these commands).

- [x] **Silent exception swallowing hides robot teleport failures**
  File/location:
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotManager.java:457`
  Why it matters:
  Failures become invisible and difficult to debug.
  Non-breaking fix checklist:
  - [x] Replace silent catch with rate-limited warning logs.
  - [x] Include owner/map/position context in logs.
  - [x] Keep control flow non-throwing to preserve runtime behavior.
  Uncertainty:
  None.

- [x] **Cinematic pipeline suppresses exceptions and reduces recoverability**
  File/location:
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ascension/AscensionCinematic.java:77`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ascension/AscensionCinematic.java:123`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ascension/AscensionCinematic.java:147`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ascension/AscensionCinematic.java:174`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ascension/AscensionCinematic.java:187`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ascension/AscensionCinematic.java:207`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ascension/AscensionCinematic.java:248`
  Why it matters:
  Root causes are hidden; debugging camera/movement recovery failures is hard.
  Non-breaking fix checklist:
  - [x] Replace ignored catches with throttled warning logs.
  - [x] Add phase identifiers to logs.
  - [x] Add always-run finalizer to restore movement/camera state.
  - [x] Keep cinematic flow best-effort and non-throwing.
  Uncertainty:
  Low.

## Cluster: Concurrency Correctness

- [x] **Jump counting can lose increments due concurrent flush pattern**
  File/location:
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunTracker.java:1501`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunTracker.java:1509`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunTracker.java:1518`,
  `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java:469`
  Why it matters:
  Jump stats can undercount during flush windows.
  Non-breaking fix checklist:
  - [x] Replace iterate+clear with atomic drain semantics.
  - [x] Use map swap or per-key remove-drain strategy.
  - [x] Preserve current flush cadence and persisted schema.
  Uncertainty:
  Low.

- [x] **Near-finish detection can enqueue duplicate completion checks**
  File/location:
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/system/AscendFinishDetectionSystem.java:55`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/system/AscendFinishDetectionSystem.java:58`
  Why it matters:
  Duplicate checks can accumulate before first completion resolves.
  Non-breaking fix checklist:
  - [x] Add per-player in-flight guard for finish checks.
  - [x] Enqueue only on CAS success.
  - [x] Clear in-flight flag in `finally`.
  Uncertainty:
  Low.

## Cluster: Duplication and Structure

- [x] **Ghost subsystem duplication across Parkour/Ascend with drift**
  File/location:
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ghost/GhostRecorder.java`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ghost/GhostRecorder.java`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ghost/GhostStore.java`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ghost/GhostStore.java`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ghost/GhostRecording.java:23`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ghost/GhostRecording.java:23`
  Why it matters:
  Increases bug-fix divergence and maintenance burden.
  Non-breaking fix checklist:
  - [x] Extract shared ghost core in `hyvexa-core`.
  - [x] Inject mode-specific table/resolver strategy.
  - [x] Keep current public APIs as wrappers.
  - [x] Migrate incrementally with parity tests.
  Uncertainty:
  None.

- [ ] **Large multi-responsibility classes increase slop/regression risk**
  File/location:
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunTracker.java`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotManager.java`,
  `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java`
  Why it matters:
  Raises change risk and slows safe optimization.
  Non-breaking fix checklist:
  - [ ] Extract internal collaborators while preserving public APIs.
  - [ ] Prioritize extraction: save scheduler/repositories, run completion services, UI renderer/refresh services.
  - [ ] Add characterization tests before and after each extraction phase.
  Uncertainty:
  None (impact magnitude scales with change frequency).
