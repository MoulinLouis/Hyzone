# Phase 4 — Module Plugin Singleton Cleanup

Parent plan: [singleton-decoupling.md](singleton-decoupling.md)

## Goal

Eliminate `XxxPlugin.getInstance()` service-locator calls from business logic in every module. After this phase, plugin `getInstance()` should only appear in the plugin class itself and in Hytale framework bootstrap points (codec-instantiated interactions) behind narrow static bridges.

## Status — Updated 2026-03-23

| Module | Plugin singleton | External calls | Status |
|--------|-----------------|---------------|--------|
| `hyvexa-parkour-ascend` | `ParkourAscendPlugin.getInstance()` | 0 | **DONE** |
| `hyvexa-parkour` | `HyvexaPlugin.getInstance()` | 0 | **DONE** |
| `hyvexa-purge` | `HyvexaPurgePlugin.getInstance()` | 0 | **DONE** |
| `hyvexa-runorfall` | `HyvexaRunOrFallPlugin.getInstance()` | 0 | **DONE** |
| `hyvexa-hub` | `HyvexaHubPlugin.getInstance()` | 1 (HubMenuInteraction) | **Nearly done** |
| `hyvexa-wardrobe` | `WardrobePlugin.getInstance()` | 0 | **DONE** |

All parkour pages decoupled via `ParkourAdminNavigator` and `ParkourInteractionBridge`. See `singleton-phase4-remaining.md` for details.

### Other module-level singletons to clean up

| Singleton | Calls | Module | Notes |
|-----------|-------|--------|-------|
| `PurgeScrapStore.getInstance()` | 27 | purge | Overlaps with Phase 3 store migration |
| `PurgeWeaponUpgradeStore.getInstance()` | 16 | purge | Overlaps with Phase 3 |
| `RunOrFallQueueStore.getInstance()` | 15 | runorfall | Not a DB store — pure in-memory singleton |
| `PurgeSkinStore.getInstance()` | 9 | core (used by purge) | Overlaps with Phase 3 |
| `PurgeClassStore.getInstance()` | 8 | purge | Overlaps with Phase 3 |
| `WardrobeBridge.getInstance()` | 7 | core | Small — wire at composition root |
| `PlayerSettingsPersistence.getInstance()` | 4 | parkour | Overlaps with Phase 3 |
| `TrailManager.getInstance()` | 4 | parkour | In-memory manager |
| `ModelParticleTrailManager.getInstance()` | 4 | parkour | In-memory manager |
| `CosmeticManager.getInstance()` | 4 | parkour | In-memory manager |

---

## Module 1 — Parkour (`HyvexaPlugin.getInstance()`)

**Scope:** 27 files, ~47 calls. Largest remaining module.

### 1a — Interactions (codec-instantiated, 11 files)

These are Hytale framework-instantiated via no-arg constructors. Same pattern as Ascend: create a narrow `ParkourInteractionBridge`.

| File | Calls | What it fetches |
|------|-------|----------------|
| `MenuInteraction` | 1 | page dependencies |
| `LeaderboardInteraction` | 1 | page dependencies |
| `LeaveInteraction` | 1 | run tracker |
| `LeavePracticeInteraction` | 1 | run tracker |
| `ResetInteraction` | 1 | run tracker |
| `RestartCheckpointInteraction` | 1 | run tracker |
| `PracticeInteraction` | 1 | run tracker |
| `PracticeCheckpointInteraction` | 1 | run tracker |
| `StatsInteraction` | 1 | page dependencies |
| `ToggleFlyInteraction` | 1 | settings |
| `ForfeitInteraction` (duel) | 1 | duel tracker |

**Approach:**
1. Create `ParkourInteractionBridge` (same pattern as `AscendInteractionBridge`)
2. Initialize it from `HyvexaPlugin.setup()` with the needed managers
3. Each interaction calls the bridge instead of `HyvexaPlugin.getInstance()`

### 1b — Pages and managers (9 files)

| File | Calls | What it fetches |
|------|-------|----------------|
| `AdminIndexPage` | 8 | multiple managers for admin panel |
| `DuelMenuPage` | 6 | duel tracker, stores |
| `DuelTracker` | 5 | multiple managers |
| `ProgressStore` | 3 | map store, analytics |
| `RunValidator` | 2 | settings, analytics |
| `RunTracker` | 1 | hud manager |
| `ParkourCommand` | 2 | multiple managers |
| `PlayerSettingsPage` | 2 | settings store |
| `ProgressAdminPage` | 2 | progress store |

**Approach:** Constructor-inject from `HyvexaPlugin.setup()`. For pages opened from interactions, pass dependencies through the bridge → interaction → page constructor chain.

### 1c — Admin pages (4 files)

| File | Calls |
|------|-------|
| `AdminPageUtils` | 1 |
| `GlobalMessageAdminPage` | 1 |
| `MapAdminPage` | 1 |
| `WelcomeTutorialScreen2Page` | 1 |

**Approach:** Create `ParkourAdminNavigator` (same pattern as `AscendAdminNavigator`) or inject directly since call count is low.

### 1d — Duel subsystem (3 files)

| File | Calls |
|------|-------|
| `DuelCommand` | 1 |
| `DuelLeaderboardPage` | 1 |
| `DuelTracker` | 5 |

**Approach:** `DuelTracker` is the hub — inject its dependencies from `HyvexaPlugin.setup()`, then it passes them to pages it creates.

---

## Module 2 — Purge (`HyvexaPurgePlugin.getInstance()`)

**Scope:** 8 files, ~13 calls. Medium.

| File | Calls | What it fetches |
|------|-------|----------------|
| `PurgeSessionManager` | 3 | wave manager, stores |
| `PurgeLootboxRollPage` | 3 | scrap store, weapon store |
| `PurgeWaveManager` | 2 | damage system, stores |
| `PurgeInteractionContext` | 1 | session manager |
| `PurgeDamageModifierSystem` | 1 | weapon config |
| `PurgeWeaponSelectPage` | 1 | weapon store |
| `WaveDeathTracker` | 1 | session manager |
| `HyvexaPurgePlugin` (self) | 1 | Skip |

**Approach:**
1. `PurgeSessionManager` and `PurgeWaveManager` get constructor injection from `HyvexaPurgePlugin.setup()`
2. `PurgeInteractionContext` is likely framework-instantiated — create `PurgeInteractionBridge` if needed, or check if it's already manually constructed
3. Pages receive dependencies from whichever manager opens them

---

## Module 3 — RunOrFall (`HyvexaRunOrFallPlugin.getInstance()`)

**Scope:** 7 files, ~11 calls. Medium.

| File | Calls | What it fetches |
|------|-------|----------------|
| `RunOrFallGameManager` | 4 | queue store, stats store, config |
| `RunOrFallSettingsPage` | 2 | settings |
| `RunOrFallBlinkInteraction` | 1 | game manager |
| `RunOrFallJoinInteraction` | 1 | game manager |
| `RunOrFallProfileInteraction` | 1 | stats store |
| `RunOrFallStatsInteraction` | 1 | stats store |
| `HyvexaRunOrFallPlugin` (self) | 1 | Skip |

**Approach:**
1. Inject into `RunOrFallGameManager` from `HyvexaRunOrFallPlugin.setup()`
2. Interactions: create `RunOrFallInteractionBridge` (4 interactions, all simple)
3. `RunOrFallSettingsPage` receives deps from whoever opens it

Also clean up `RunOrFallQueueStore.getInstance()` (15 calls) — inject from composition root.

---

## Module 4 — Hub (`HyvexaHubPlugin.getInstance()`)

**Scope:** 2 files, ~2 calls. Trivial.

| File | Calls |
|------|-------|
| `HubMenuInteraction` | 1 |
| `HyvexaHubPlugin` (self) | 1 |

**Approach:** `HubMenuInteraction` is codec-instantiated — create a minimal `HubInteractionBridge` or inline the one getter it needs.

---

## Module 5 — Wardrobe (`WardrobePlugin.getInstance()`)

**Scope:** 1 file, ~1 call. Trivial.

Already minimal — just verify `WardrobePlugin` self-reference isn't leaking and clean up if needed.

---

## Execution Order

1. **Parkour** (largest, highest value) — do interactions first (1a), then managers/pages (1b-1d)
2. **Purge** (medium, self-contained)
3. **RunOrFall** (medium, self-contained)
4. **Hub** (trivial)
5. **Wardrobe** (trivial)

Each module is fully independent — can be done in any order or in parallel by different agents.

## Completion Criteria

- Each plugin's `getInstance()` only appears in:
  - The plugin class itself
  - `*InteractionBridge` static bootstrap (for codec-instantiated classes)
- All `@Deprecated` getters from Phase 1 can be removed
- All manager/store singletons within each module are composed at the plugin boundary
