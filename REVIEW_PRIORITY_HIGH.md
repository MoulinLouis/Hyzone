# Priority High Tasklist (AI Slop + Optimization)

Date: 2026-02-10
Source: `AI_SLOP_DEEP_DIVE_FINDINGS.md`

## Cluster A: Progression Integrity and Access Control

### H-1: Ascend map unlock bypass via crafted UI payload
Evidence:
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java:157`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java:167`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java:1161`

Tasklist (non-breaking):
- [x] Validate incoming `mapId` against `displayedMapIds` before any selection logic runs.
- [x] Replace force-unlock behavior in `ensureUnlocked(...)` with requirement validation (`MapUnlockHelper.checkAndEnsureUnlock(..., mapStore)`).
- [x] Remove forced unlock fallback (`setUnlocked(true)`) from crafted/invalid selection paths.
- [x] Keep existing event keys, button payload formats, and page flow unchanged.

## Cluster B: UI Reliability and Rendering Contract

### H-2: Runtime `Background` mutations are unreliable and violate UI guidance
Evidence:
- `CLAUDE.md:49`
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/MapAdminPage.java:676`
- `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/MapAdminPage.java:681`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java:231`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendLeaderboardPage.java:200`

Tasklist (non-breaking):
- [x] Add explicit overlay UI elements for selected/disabled/active visual states.
- [x] Replace runtime `.Background` writes with `.Visible` toggles on those overlays.
- [x] Preserve current element IDs used by Java handlers and keep existing payload bindings.
- [x] Migrate one page at a time and verify player-visible parity after each page migration.

## Cluster C: Hot-Path Persistence and Disconnect Performance

### H-3: Disconnect path can trigger synchronous full-store DB save
Evidence:
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java:1221`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java:1231`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java:336`

Tasklist (non-breaking):
- [x] Add targeted persistence path (for example `savePlayerIfDirty(playerId)`) to avoid full-store flush on single-player disconnect.
- [x] Keep existing debounced global save behavior for normal periodic persistence.
- [x] On disconnect, enqueue targeted async save for the player and then evict that player from memory.
- [x] Keep shutdown full flush semantics unchanged to preserve durability expectations.
