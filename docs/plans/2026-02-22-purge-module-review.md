# Purge Module Review (Gameplay Consistency and Edge Cases)

Date: 2026-02-22
Scope: `hyvexa-purge` runtime flow (sessions, waves, party start paths, cleanup ordering, and UI/runtime guards)

This review intentionally excludes scrap-balancing/economy concerns.

## Non-Breaking Change Rules

Apply all fixes under these constraints:

1. Do not change command names or arguments (`/purge ...` stays the same).
2. Do not change DB schema or table names.
3. Do not rename UI IDs or `.ui` page paths.
4. Keep current public manager APIs callable the same way from existing code.
5. Prefer guard clauses and sequencing changes over architectural rewrites.

## Findings and Update Instructions

## 1) Spawn failures can still complete waves

Severity: High  
Files: `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeWaveManager.java`

### Problem
Inside spawn batching, failed `spawnZombie(...)` attempts still decrement remaining wave budget. If many spawns fail (bad variant config, NPC spawn failure, bad instance data), the wave can still transition to complete with `alive == 0`.

### Non-breaking fix
1. Track two counters in spawn loop:
   - `attempted` (queue consumed)
   - `spawnedSuccessfully` (actual spawned)
2. Only mark spawn phase complete after queue is fully attempted.
3. Gate wave completion on both:
   - `session.isSpawningComplete()`
   - `spawnedSuccessfully > 0 || wave.totalCount() == 0` (explicit empty-wave case only)
4. If `wave.totalCount() > 0` and `spawnedSuccessfully == 0`, end session with a technical failure message instead of granting a normal wave clear.

This is non-breaking because gameplay endpoints and APIs stay unchanged; only invalid/failure behavior is corrected.

## 2) Team-wipe / wave-complete checks race with world-thread death handling

Severity: High  
Files: `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeWaveManager.java`

### Problem
`checkZombieDeaths()` and wipe/complete checks run before world-thread HP/death processing (`updateWaveWorldState -> world.execute(...)`) finishes. A player can be effectively dead in the same tick but still counted alive for transition logic.

### Non-breaking fix
1. Move team-wipe and wave-complete transition checks into the same world-thread execution path that updates HP/death state.
2. Keep HUD update timing the same, but derive transition decisions only after death processing has run.
3. Add a transition guard method to avoid duplicate stop/complete calls in adjacent ticks.

This is non-breaking because it preserves states and UI behavior, but makes ordering deterministic.

## 3) Party leader-only start rule is bypassable through `/purge start`

Severity: Medium  
Files: `hyvexa-purge/src/main/java/io/hyvexa/purge/command/PurgeCommand.java`, `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeSessionManager.java`

### Problem
UI flow enforces leader start in party mode, but command flow allows any party member to call `/purge start`, which dissolves the party and launches.

### Non-breaking fix
1. Add a party leadership check before `sessionManager.startSession(...)` in command start path.
2. If caller is in a party and not leader, send the same policy message used by UI.
3. Keep solo behavior unchanged.

This is non-breaking because command name/signature stays identical; only authorization consistency is fixed.

## 4) Instance release can happen before all player cleanup work finishes

Severity: Medium  
Files: `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeSessionManager.java`

### Problem
Instance release currently waits for zombie cleanup future, but per-player world cleanup is scheduled independently. Arena reuse may start while prior player teleports/loadout cleanup is still in flight.

### Non-breaking fix
1. Make `runPlayerWorldCleanup(...)` return a `CompletableFuture<Void>`.
2. Aggregate all player cleanup futures + zombie cleanup future with `CompletableFuture.allOf(...)`.
3. Release instance only after all futures complete (with timeout fallback + warning log if needed).

This is non-breaking because no external method contracts need to change for callers; sequencing becomes safer.

## 5) Purge world entry path is inconsistent (ready vs transfer)

Severity: Medium  
Files: `hyvexa-purge/src/main/java/io/hyvexa/purge/HyvexaPurgePlugin.java`

### Problem
`PlayerReadyEvent` path clears inventory, grants base loadout, and initializes default weapon ownership. `AddPlayerToWorldEvent` path mainly ensures missing orbs. Player state differs based on entry timing/path.

### Non-breaking fix
1. Extract a single idempotent helper for "ensure purge idle state":
   - ensure HUD attached
   - ensure base loadout present for non-session players
   - ensure default weapon ownership initialized
2. Call this helper from both event handlers.
3. Keep active-session players excluded from idle-loadout overwrite (current behavior).

This is non-breaking because it aligns existing behavior across entry routes without changing APIs or UX surfaces.

## 6) Party invite drawer has null-unsafe world lookup

Severity: Low  
Files: `hyvexa-purge/src/main/java/io/hyvexa/purge/ui/PurgePartyMenuPage.java`

### Problem
`ref.getStore().getExternalData().getWorld()` is used without null guards in invite candidate filtering. Transient refs can throw and break refresh.

### Non-breaking fix
1. Add defensive checks:
   - `store != null`
   - `store.getExternalData() != null`
   - `world != null`
2. Skip candidate if world context is unavailable.
3. Keep filter logic and UI structure unchanged.

This is non-breaking and only improves resilience.

## 7) Co-op wave summary kill total is inflated

Severity: Low  
Files: `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeWaveManager.java`

### Problem
Each alive player gets +1 kill per zombie death (shared-kill model), then totals are summed across players for wave-complete message. In co-op this inflates "total kills" beyond actual zombies killed.

### Non-breaking fix
Pick one of these without changing data model:
1. Message-only fix (recommended): change text to "team kill credits" to match current semantics.
2. Keep player credits as-is, but compute displayed wave total from zombie deaths this wave.

This is non-breaking because progression and stored stats remain unchanged.

## Recommended Implementation Order

1. Fix #1 and #2 first (highest gameplay correctness risk).
2. Fix #4 next (session isolation/reuse safety).
3. Fix #3 and #5 (consistency across entry/start surfaces).
4. Apply #6 and #7 as polish/hardening.

## Regression Checklist (No Breaking Changes)

1. Solo start/stop still works from orb and `/purge`.
2. Party start still works for leader; non-leader receives denial message.
3. Existing wave configs load unchanged.
4. No DB migrations required.
5. No UI parse changes required.
6. Session transitions remain: `COUNTDOWN -> SPAWNING -> COMBAT -> UPGRADE_PICK -> INTERMISSION -> ... -> ENDED`.
