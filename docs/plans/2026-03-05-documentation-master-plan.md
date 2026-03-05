> **Status: Executed on 2026-03-05.** All phases completed successfully.

# Documentation Harmonization Master Plan

This is the canonical merged plan for bringing the repository documentation back in sync with the codebase. It replaces the earlier `doc-alignment-*` and `doc-harmonization-*` drafts with a smaller, cleaner set of plan files.

## Core Decisions
- Code is the source of truth for modules, entry points, commands, stores, tables, and runtime behavior.
- `README.md` is the human-facing repository overview.
- `CLAUDE.md` is the canonical agent-facing bootstrap and workflow reference.
- `AGENTS.md` stays intentionally minimal: it should quickly state the most important reminders, explicitly require reading `CLAUDE.md` in full, and avoid becoming a second full instruction manual.
- `docs/ARCHITECTURE.md` owns system boundaries, module lifecycles, and cross-module relationships.
- `docs/DATABASE.md` owns the full shared schema across the suite.
- Module landing pages own quick-reference navigation for each module and link to specialized docs instead of duplicating them.

## Scope
- In: top-level docs, agent docs, architecture/schema docs, module landing pages, cross-links, and documentation maintenance rules.
- Out: gameplay changes, balance changes, code refactors, vendor docs under `docs/hytale-custom-ui/`, and changelog rewriting.

## Validated Gaps

| Area | Confirmed issue |
|---|---|
| Top-level repo docs | `hyvexa-votifier` exists in `settings.gradle` but is missing from several top-level docs. |
| Workflow docs | `README.md` and `CLAUDE.md` currently read as contradictory around build/test usage because audience is not explicit enough. |
| Database docs | `docs/DATABASE.md` is framed too narrowly and is missing Mine tables plus additional Purge tables. |
| Architecture docs | `docs/ARCHITECTURE.md` needs explicit Votifier coverage and Mine subsystem coverage. |
| Module docs | Core, Hub, Ascend, Purge, Votifier, and Discord Bot still need standardized landing pages; Wardrobe needs one canonical path decision. |
| Agent docs | `CLAUDE.md` is carrying too much detail directly; `AGENTS.md` must remain a short front door to `CLAUDE.md`, not a competing source of truth. |
| Docs hygiene | Empty placeholder directories still exist under `docs/` and should be cleaned or justified. |

## Target Outputs
- A slimmer but still authoritative `CLAUDE.md`.
- An `AGENTS.md` that stays short and points hard to `CLAUDE.md`.
- A suite-wide `docs/DATABASE.md` and `docs/ARCHITECTURE.md` that match the real codebase.
- One obvious landing page per major module or service.
- A human-facing `README.md` that reflects the actual product surface.
- A maintenance model that makes future drift harder to reintroduce.

## Execution Order
1. Rework `CLAUDE.md` and keep `AGENTS.md` minimal.
2. Reconcile `docs/DATABASE.md` and `docs/ARCHITECTURE.md`.
3. Create or normalize module landing pages.
4. Update `README.md`.
5. Clean up leftovers and verify links/contradictions.

## Companion Files
- `docs/plans/2026-03-05-documentation-execution.md`
- `docs/plans/2026-03-05-documentation-governance.md`

## Success Criteria
- Every module in `settings.gradle` is represented consistently in the appropriate top-level docs.
- `AGENTS.md` stays short and clearly instructs agents to read `CLAUDE.md` in full.
- Every major module/service has one obvious quick-reference document.
- `docs/DATABASE.md` and `docs/ARCHITECTURE.md` describe the current multi-module system, including Mine and Votifier.
- Specialized docs are reachable from landing pages instead of acting as isolated documentation islands.
- Future changes have explicit update triggers so the docs stay aligned.
