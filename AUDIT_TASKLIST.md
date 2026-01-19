# Audit Tasklist

- [x] Added a scheduled stale-player sweep to clear orphaned per-player caches.
- [x] Removed stale run tracking state for offline players during sweeps.
- [x] Pruned hidden-player visibility data for offline viewers and targets.
- [x] Removed deep-copy map iteration from the start-trigger hot path.
- [x] Added HUD dirty-checks to skip redundant UI updates.
- [x] Cached sorted leaderboard entries to avoid rebuilding top times every HUD tick.
- [x] Batched player tick work per world to reduce scheduled task overhead.
- [x] Added defensive exception handling to all event handlers to prevent plugin exceptions from propagating to engine.
