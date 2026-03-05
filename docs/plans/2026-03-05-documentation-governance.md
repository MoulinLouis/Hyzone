> **Status: Executed on 2026-03-05.** All phases completed successfully.

# Documentation Ownership and Maintenance Rules

This file defines the target documentation structure and the rules that should keep it aligned after the initial cleanup pass.

## Canonical Ownership Rules
- Code is the source of truth for module existence, entry points, commands, stores, and persisted data.
- `AGENTS.md` is a short front door only:
  - it highlights a few critical reminders
  - it explicitly tells agents to read `CLAUDE.md` in full
  - it must not become a duplicate instruction manual
- `CLAUDE.md` owns the authoritative agent-facing bootstrap and workflow rules.
- `README.md` owns the human-facing repository overview.
- `docs/ARCHITECTURE.md` owns module boundaries, lifecycles, and cross-module relationships.
- `docs/DATABASE.md` owns the full shared schema grouped by subsystem.
- `docs/CODE_PATTERNS.md` owns reusable implementation patterns.
- Module landing pages own module scope, entry point, key files, commands, table ownership, and links to deeper docs.
- Specialized docs own deep topic details only for the subsystem they are named after.

## Target Landing Pages

| Module / system | Target doc | Notes |
|---|---|---|
| Core | `docs/Core/README.md` | Shared stores, bridges, cross-module services. |
| Hub | `docs/Hub/README.md` | Routing, mode handoff, mode-state touchpoints. |
| Parkour | `docs/Parkour/README.md` | Keep and update. |
| Ascend | `docs/Ascend/README.md` | New landing page above balance/tutorial docs. |
| Purge | `docs/Purge/README.md` | New landing page above design/dev docs. |
| RunOrFall | `docs/RunOrFall/README.md` | Keep and update. |
| Wardrobe | Canonicalize `docs/WARDROBE_MOD.md` or replace with `docs/Wardrobe/README.md` | Pick one and link consistently. |
| Votifier | `docs/Votifier/README.md` | New landing page. |
| Discord Bot | `docs/DiscordBot/README.md` | New landing page. |

## Mandatory Update Triggers

| If this changes in code | Update these docs |
|---|---|
| `settings.gradle`, module directories, or plugin entry points | `README.md`, `CLAUDE.md`, `docs/ARCHITECTURE.md`, relevant landing page |
| New command, renamed command, or removed command | Relevant landing page and any player/admin reference doc that lists commands |
| New DB table, migration, or ownership move between stores | `docs/DATABASE.md`, relevant landing page, `docs/ARCHITECTURE.md` if ownership changed |
| New UI parser rule or reusable UI pattern | `docs/CODE_PATTERNS.md`, and `CLAUDE.md` if agent-critical |
| Economy/progression formula changes | Relevant balance/design doc and any landing-page summary that would otherwise become misleading |
| New external integration or major integration-flow change | Relevant integration doc, relevant landing page, and top-level docs if the repo surface changed |
| Agent workflow rule changes | `CLAUDE.md`, and `AGENTS.md` only if the front-door reminder itself must change |

## Review Checklist
- [ ] No module exists in code without showing up in the appropriate top-level docs.
- [ ] No top-level doc describes a module or workflow that no longer exists.
- [ ] `AGENTS.md` remains short and points clearly to `CLAUDE.md`.
- [ ] `CLAUDE.md` contains the real authoritative agent guidance.
- [ ] `docs/DATABASE.md` covers Mine and the currently missing Purge tables.
- [ ] `docs/ARCHITECTURE.md` covers Votifier and the Mine subsystem.
- [ ] Every major module/service has one obvious landing page.
- [ ] Specialized docs are linked from landing pages instead of acting as hidden knowledge islands.
- [ ] Build/test wording is audience-aware rather than contradictory.

## Definition of Done
- A contributor or agent can determine the current module set, the right canonical document for each subsystem, and the correct place to update after a code change without reading the whole repository.
