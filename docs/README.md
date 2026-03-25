# Documentation Index

Quick reference for AI agents. Find the right doc, know when to update it.

## Source of Truth Matrix

| Topic | Source of truth | Update when... |
|-------|----------------|----------------|
| Project overview & modules | `../README.md` | Module added/removed, project description changes |
| Agent workflow & rules | `../CLAUDE.md` | Workflow or tooling changes |
| Architecture & module boundaries | `ARCHITECTURE.md` | Module added, major class/package restructured, new integration |
| Architecture visual diagrams | `ARCHITECTURE_DIAGRAMS.md` | Keep in sync with `ARCHITECTURE.md` |
| Database schema (index) | `DATABASE.md` | New table added (update index), new module gets tables |
| Database schema (per-module) | `<Module>/DATABASE.md` | Table/column added, removed, or renamed |
| Economy & balance (Ascend) | `Ascend/ECONOMY_BALANCE.md` | Cost, reward, multiplier, formula, or progression change |
| Code patterns & conventions | `CODE_PATTERNS.md` | New reusable pattern established |
| Hytale API gotchas | `HYTALE_API.md` | New API quirk discovered or workaround found |
| Hytale Custom UI reference | `hytale-custom-ui/` | Hytale UI system changes (external reference) |
| Release history (dev) | `../CHANGELOG.md` | Any player-visible change shipped |
| Development environment & workflow | `DEVELOPMENT_ENVIRONMENT.md` | Build/deploy process, runtime paths, or dev setup changes |
| Release history (player-facing) | `PLAYER_PATCH_NOTES.md` | New version released to players |
| Tebex store integration | `TEBEX_INTEGRATION.md` | Store packages or webhook config changes |
| Wardrobe mod assets | `WARDROBE_MOD.md` | Wardrobe asset structure or JSON format changes |
| HyGuns weapons reference | `HYGUNS_REFERENCE.md` | Weapon stats or system changes |
| Item definitions | `items/*.json` | New cosmetic items added |
| Weapon UV maps | `weapons/AK47_UV_MAP.md` | UV mapping changes |

## Module READMEs

Each module has a README documenting its scope, managers, stores, tables, and key files.

| Module | File | Covers |
|--------|------|--------|
| Core | `Core/README.md` | Shared DB, utilities, subsystems, stores |
| Parkour | `Parkour/README.md` | Run lifecycle, checkpoints, ghosts, leaderboards |
| Ascend | `Ascend/README.md` | Idle mode: mine, summit, skills, challenges, evolution |
| Hub | `Hub/README.md` | Mode routing, server selection, HUD |
| Purge | `Purge/README.md` | Zombie PvE: waves, scaling, weapons, session lifecycle |
| RunOrFall | `RunOrFall/README.md` | Platforming minigame: rounds, scoring, persistence |
| Wardrobe | `Wardrobe/README.md` | Cosmetics shop: skins, emotes, effects |
| Votifier | `Votifier/README.md` | Vote notification: protocol, lifecycle, integration |
| Discord Bot | `DiscordBot/README.md` | Account linking, rank sync, vexa rewards |

## Ascend-Specific Docs

| File | Purpose | Status |
|------|---------|--------|
| `Ascend/ECONOMY_BALANCE.md` | All economy values, formulas, progression | Canonical — update on any balance change |
| `Ascend/TUTORIAL_FLOW.md` | Onboarding step sequence | Canonical — update when tutorial changes |
| `Ascend/MINE_IMPROVEMENTS.md` | Working notes on mine system improvements | Working notes |
| `Ascend/MINE_BALANCE_PLAN.md` | Mine balance design notes | Working notes |
| `Ascend/PICKAXE_REWORK_PLAN.md` | Pickaxe system redesign notes | Working notes |

## Purge-Specific Docs

| File | Purpose | Status |
|------|---------|--------|
| `Purge/GAME_DESIGN.md` | Core game design document | Canonical |
| `Purge/PURGE_MODE_DEV.md` | Development reference for agents | Working reference |

## Other Directories

| Directory | Content |
|-----------|---------|
| `plans/` | Implementation plans (dated `YYYY-MM-DD-*.md`) — working or historical documents, not canonical operational guidance |
| `hytale-custom-ui/` | Hytale Custom UI type documentation (120+ files) — consult before writing `.ui` files |
| `items/` | Cosmetic item JSON definitions |
| `weapons/` | Weapon reference docs |

## Path Conventions

- Use `mods/...` for logical runtime paths (relative to the server working directory)
- Use `hyvexa_server/...` only when giving the concrete Windows example
- Keep machine-specific overrides (like `stageModsDir`) out of committed config
- See `DEVELOPMENT_ENVIRONMENT.md` for the full hybrid layout

## Changelog Ownership

- **`CHANGELOG.md`** (root) — internal dev log, tracks all player-visible changes. Agents update this.
- **`PLAYER_PATCH_NOTES.md`** — player-facing patch notes, written in player-friendly tone. Derived from CHANGELOG.md at release time via `/patchnotes`.
