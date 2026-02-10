# Review Priority 1 (Highest)

Scope: Critical issues only. Focus on data integrity and world-thread safety.

## Cluster: Persistence and Completion Pipeline

- [x] **Run completion path performs blocking DB I/O (and retry sleeps) on gameplay thread**
  File/location:
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/tracker/RunTracker.java:790`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/ProgressStore.java:285`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/ProgressStore.java:300`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/ProgressStore.java:311`,
  `hyvexa-core/src/main/java/io/hyvexa/core/db/DatabaseRetry.java:37`
  Why it matters:
  Can stall world ticks, increase latency, and serialize progress operations behind DB slowness.
  Non-breaking fix checklist:
  - [x] Split `recordMapCompletion(...)` into in-memory apply + async persistence phases.
  - [x] Keep result calculation on world thread and return immediately.
  - [x] Move JDBC/retry logic to background executor only.
  - [x] Ensure `fileLock` covers only in-memory mutation, never JDBC.
  - [x] Preserve current warning semantics (`completionSaved`) via async completion signal.
  Uncertainty:
  None.

- [x] **`ProgressStore` debounced save can lose dirty updates**
  File/location:
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/ProgressStore.java:823`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/ProgressStore.java:839`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/ProgressStore.java:840`,
  `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/ProgressStore.java:829`
  Why it matters:
  Dirty state can be skipped when updates arrive during an in-flight save.
  Non-breaking fix checklist:
  - [x] Stop clearing `dirtyPlayers` before DB writes.
  - [x] Snapshot IDs, save snapshot, and remove only successfully written IDs.
  - [x] After save task finishes, check if dirty set is still non-empty.
  - [x] If dirty remains, immediately queue another save cycle.
  Uncertainty:
  None.

- [x] **`AscendPlayerStore` save scheduling can lose same-player updates during save window**
  File/location:
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java:1238`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java:1261`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java:1449`,
  `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java:1246`
  Why it matters:
  Repeated updates to the same player during save can be erased by `removeAll(toSave)`.
  Non-breaking fix checklist:
  - [x] Replace coarse dirty removal with versioned dirty tracking per player.
  - [x] Increment per-player dirty version in `markDirty`.
  - [x] Snapshot `(playerId, version)` pairs before save.
  - [x] Remove dirty entry only when current version equals snapshotted version.
  - [x] Requeue save if any dirty state remains after cycle completion.
  Uncertainty:
  None.
