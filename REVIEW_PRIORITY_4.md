# Review Priority 4 (Lowest)

Scope: Low-priority cleanup and incremental optimization.

## Cluster: Logging and Code Hygiene

- [x] **`printStackTrace()` is used in command handlers instead of structured logging**
  File/location:
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/command/DatabaseReloadCommand.java:70`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/command/DatabaseTestCommand.java:61`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/command/DatabaseTestCommand.java:74`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/command/DatabaseClearCommand.java:92`
  Why it matters:
  Produces noisy, unstructured logs and weakens diagnostics.
  Non-breaking fix checklist:
  - [x] Replace `printStackTrace()` with logger calls using `withCause(...)`.
  - [x] Keep current player-facing messages unchanged.
  - [x] Use appropriate log levels (`WARNING`/`SEVERE`) based on failure type.
  Uncertainty:
  None.

- [x] **Double-brace initialization adds avoidable overhead**
  File/location:
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerProgress.java:115`
  Why it matters:
  Creates anonymous class/allocation overhead and is harder to maintain.
  Non-breaking fix checklist:
  - [x] Replace double-brace map init with explicit local `EnumMap` + `put(...)` loop.
  - [x] Keep method signature and output exactly the same.
  Uncertainty:
  None.

## Cluster: Idle Polling Optimization

- [x] **Hub HUD polling loop does near-zero work every 200ms**
  File/location:
  `hyvexa-hub/src/main/java/io/hyvexa/hub/HyvexaHubPlugin.java:136`,
  `hyvexa-hub/src/main/java/io/hyvexa/hub/HyvexaHubPlugin.java:205`
  Why it matters:
  Avoidable baseline overhead for all hub players.
  Non-breaking fix checklist:
  - [x] Shift to event-driven HUD attach/reattach triggers where possible.
  - [x] If polling remains, increase interval and skip stable-ready players.
  - [x] Keep HUD behavior unchanged.
  Uncertainty:
  Medium (depends on real runtime HUD invalidation frequency).
