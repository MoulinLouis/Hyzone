# Priority Low Tasklist (AI Slop + Optimization)

Date: 2026-02-10
Source: `AI_SLOP_DEEP_DIVE_FINDINGS.md`

## Cluster A: Data Model Clarity and Documentation Drift

### L-1: Legacy schema/runtime drift in `AscendMapStore`
Evidence:
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendMapStore.java:35`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendMapStore.java:50`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendMapStore.java:167`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendMapStore.java:168`

Tasklist (non-breaking):
- [x] Define and document source-of-truth policy for map values (computed vs DB-authored).
- [x] Stop selecting legacy columns that are no longer authoritative at runtime.
- [x] If deprecating old columns, stage migration with backward-compatible reads first.
- [x] Remove legacy paths only after compatibility period confirms safe rollout.

## Cluster B: UX Messaging Consistency

### L-2: Hub gate message is stale relative to current route access logic
Evidence:
- `hyvexa-hub/src/main/java/io/hyvexa/hub/ui/HubMenuPage.java:39`
- `hyvexa-hub/src/main/java/io/hyvexa/hub/ui/HubMenuPage.java:41`
- `hyvexa-hub/src/main/java/io/hyvexa/hub/ui/HubMenuPage.java:97`
- `hyvexa-hub/src/main/java/io/hyvexa/hub/ui/HubMenuPage.java:103`

Tasklist (non-breaking):
- [x] Update denied-access copy to match actual behavior (restricted/whitelist gate, not "coming soon").
- [x] Keep routing, permission checks, and whitelist logic unchanged.
- [x] Confirm only user-facing text changes are introduced.
