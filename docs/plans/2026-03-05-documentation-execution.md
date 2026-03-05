> **Status: Executed on 2026-03-05.** All phases completed successfully.

# Documentation Harmonization Execution Plan

This file contains the concrete execution steps for the merged documentation plan. The emphasis is on accuracy, fewer duplicate documents, and a clear split between human docs, agent docs, architecture docs, and module quick references.

## Phase 1: `CLAUDE.md` and `AGENTS.md`

Goal: make `CLAUDE.md` authoritative and keep `AGENTS.md` intentionally minimal.

- Extract the long grepai reference from `CLAUDE.md` into `docs/GREPAI.md`, then replace it with a short summary plus a link.
- Keep `AGENTS.md` as a short front-door document:
  - state that `CLAUDE.md` is the canonical source of project instructions
  - require reading `CLAUDE.md` in full before any task
  - keep only a few highest-signal reminders such as the no-build rule and the grepai sandbox note
  - do not move full workflow rules, behavioral guidelines, module inventories, or doc indexes into `AGENTS.md`
- Keep behavioral guidance in `CLAUDE.md`, but trim it so it remains high-signal rather than generic.
- Add `hyvexa-votifier` to the module table in `CLAUDE.md`.
- Fix brittle/stale reminders in `CLAUDE.md`:
  - renumber the workflow list cleanly
  - replace any stale hardcoded manager-count claim with an audited count or stable phrasing such as `40+`
  - verify the Discord bot initialization claim before preserving it
- Expand the Reference Files table so it points to the current module docs and new `docs/GREPAI.md`.
- Update the Key Directories table to include important UI/resource paths that matter to agents.

## Phase 2: `docs/DATABASE.md` and `docs/ARCHITECTURE.md`

Goal: make the two main technical reference docs reflect the full current suite.

- Reframe `docs/DATABASE.md` as a suite-wide schema reference instead of a parkour-only document.
- Add the full Mine schema from `AscendDatabaseSetup.java`:
  - `mine_definitions`
  - `mine_zones`
  - `mine_gate`
  - `mine_players`
  - `mine_player_inventory`
  - `mine_block_prices`
  - `mine_player_mines`
  - `mine_player_miners`
- Add the currently missing Purge tables that exist in code:
  - `purge_weapon_xp`
  - `purge_daily_missions`
  - `purge_player_classes`
  - `purge_player_selected_class`
- Group schema sections by subsystem ownership so the file is easier to maintain.
- Add `hyvexa-votifier` to `docs/ARCHITECTURE.md`, including a short lifecycle description.
- Add the Ascend Mine subsystem to `docs/ARCHITECTURE.md`, including its managers, stores, and table ownership.
- Make sure quick-reference lists in architecture docs stay consistent with the detailed sections below them.

## Phase 3: Module Landing Pages

Goal: ensure every important module/service has one obvious quick-reference page.

- Create `docs/Core/README.md`.
- Create `docs/Hub/README.md`.
- Create `docs/Ascend/README.md`.
- Create `docs/Purge/README.md`.
- Create `docs/Votifier/README.md`.
- Create `docs/DiscordBot/README.md`.
- Update `docs/Parkour/README.md` and `docs/RunOrFall/README.md` so they remain current and link to specialized docs.
- Decide the canonical Wardrobe landing-page path:
  - either keep `docs/WARDROBE_MOD.md` as the canonical quick reference
  - or normalize to `docs/Wardrobe/README.md` and link the old path if needed
- Keep each landing page focused on:
  - scope
  - entry point
  - key managers/stores
  - runtime flow
  - commands
  - owned tables
  - key files
  - related docs
- Do not duplicate deep design/balance docs inside the landing pages; link out to them.

## Phase 4: `README.md`

Goal: make the root README accurate for human contributors without turning it into a second architecture manual.

- Update the module table so it matches the real current module set.
- Mention `discord-bot` as a service/tooling component even though it is not a Gradle module.
- Add feature summaries for Purge, RunOrFall, and the Ascend Mine subsystem.
- Update the project layout block so it shows the major module roots that actually exist.
- Update the documentation links table so it points to the module landing pages and key specialized docs.
- Clarify audience around build/test instructions:
  - `README.md` is for humans
  - agent execution rules live in `CLAUDE.md`
  - the wording should make that distinction obvious instead of contradictory

## Phase 5: Cleanup and Verification

Goal: remove noise and leave behind a maintainable documentation structure.

- Remove empty placeholder directories under `docs/` if they are truly unused.
- Mark old implemented plan files when appropriate, instead of leaving them ambiguous forever.
- Verify all markdown cross-links between `README.md`, `CLAUDE.md`, `docs/`, and the module landing pages.
- Run a contradiction pass for:
  - module counts
  - workflow/build wording
  - schema coverage
  - module ownership language
  - Discord bot notes
- Keep this merged 3-file plan set as the canonical documentation overhaul plan and avoid recreating parallel plan families.
