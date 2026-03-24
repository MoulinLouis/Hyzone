# Plan: Docs Migration Alignment

Align the project's living documentation with the completed WSL2 migration. Establish one canonical description of the hybrid setup, then update or label every document that still describes the old `run/`-based workflow. The goal: no future contributor or agent sees two conflicting operational models.

## Current state (hybrid model)

- **Source code** lives on WSL2 ext4 (`~/dev/Hytale/hyvexa_plugin/`)
- **Hytale install + server runtime** lives on Windows (`C:\Users\<user>\hyvexa_server\`)
- **`stagePlugins`** copies JARs to the path in `stageModsDir` (set in `~/.gradle/gradle.properties`)
- **`run/`** no longer exists — all references to it are stale

## Scope

**In:**
- Create `docs/DEVELOPMENT_ENVIRONMENT.md` as the single canonical environment/workflow reference
- Update root docs and technical references that still point to `run/`, `gradlew.bat`, or pre-migration runtime assumptions
- Mark historical plan docs as completed/historical
- Add path convention rules to `docs/README.md`

**Out:**
- Gradle build logic changes (but see "Follow-up" section for the stale `run/mods` default in `build.gradle` and `hyvexa-purge/build.gradle`)
- IntelliJ/run-config implementation work
- Rewriting historical entries in `CHANGELOG.md`
- Changing local machine-only files (`~/.gradle/gradle.properties`)

## Stale reference inventory

Pre-computed from codebase search — this replaces the inventory step.

| File | Lines | What's stale |
|------|-------|-------------|
| `README.md` | 82, 88, 93, 115, 126 | `gradlew.bat`, `run/mods`, "working directory is `run/`" |
| `docs/ARCHITECTURE.md` | ~420 | Note about `run/mods/Parkour/` inconsistency |
| `docs/DATABASE.md` | 9 | "Server working directory is `run/`" |
| `docs/TEBEX_INTEGRATION.md` | 30, 182 | `run/mods/Parkour/tebex.json` paths |
| `docs/HYGUNS_REFERENCE.md` | 3, 122, 179 | `run/mods/Hyguns/` paths |
| `docs/plans/wsl2-native-migration.md` | throughout | Historical — needs banner only |
| `docs/plans/unit-test-expansion.md` | 178-179 | `cmd.exe /c "gradlew.bat test"` |
| `.gitignore` | 10 | `run/` entry (dead, directory no longer exists) |

**Already clean (no action needed):** `CLAUDE.md`, `docs/README.md`, `docs/HYTALE_API.md`, `docs/CODE_PATTERNS.md`, all module READMEs, all `docs/hytale-custom-ui/` files.

## Action items

Ordered by dependency. Steps within the same phase can be parallelized.

### Phase 1 — Create the canonical reference

- [ ] **1.1** Create `docs/DEVELOPMENT_ENVIRONMENT.md` defining the post-migration model:
  - Repo on WSL2 ext4, Hytale install + server runtime on Windows
  - `stagePlugins` → copies to `stageModsDir` (configured in `~/.gradle/gradle.properties`)
  - `collectPlugins` → copies to `build/libs` (CI-friendly, no Windows dependency)
  - Runtime config at `mods/Parkour/` relative to server working directory
  - Short section on IntelliJ setup (end-state description, not migration steps)
  - One concrete example with Windows paths, then use `mods/...` logical paths elsewhere

### Phase 2 — Register and set conventions

- [ ] **2.1** Update `docs/README.md`:
  - Add `DEVELOPMENT_ENVIRONMENT.md` to the source-of-truth matrix (topic: "Development environment & workflow")
  - Add a "Path conventions" section: use `mods/...` for logical runtime paths, use `hyvexa_server/...` only when giving the concrete Windows example, keep machine-specific overrides out of committed config
  - Clarify that `docs/plans/` files are working or historical documents, not canonical operational guidance

### Phase 3 — Update active docs (parallelizable)

- [ ] **3.1** Update `README.md` (lines 82, 88, 93, 115, 126):
  - Remove `gradlew.bat` reference — native `./gradlew` is the only build command
  - Replace `run/mods` with explanation of `stagePlugins` → `stageModsDir`
  - Replace "working directory is `run/`" with the hybrid model summary + link to `DEVELOPMENT_ENVIRONMENT.md`
  - Show one concrete Windows path example, then link out
- [ ] **3.2** Update `docs/DATABASE.md` (line 9):
  - Replace "Server working directory is `run/`" with `mods/Parkour/` relative to server working directory, link to `DEVELOPMENT_ENVIRONMENT.md`
- [ ] **3.3** Update `docs/ARCHITECTURE.md` (~line 420):
  - Replace the `run/mods/Parkour/` inconsistency note with the resolved convention: `mods/Parkour/` is the canonical runtime path
- [ ] **3.4** Update `docs/TEBEX_INTEGRATION.md` (lines 30, 182):
  - Replace `run/mods/Parkour/tebex.json` with `mods/Parkour/tebex.json` (server working directory)
- [ ] **3.5** Update `docs/HYGUNS_REFERENCE.md` (lines 3, 122, 179):
  - Replace `run/mods/Hyguns/` with `mods/Hyguns/` (server working directory)

### Phase 4 — Label historical docs (parallelizable)

- [ ] **4.1** Add banner to `docs/plans/wsl2-native-migration.md`:
  - `> **Status: Completed.** For the current setup, see [DEVELOPMENT_ENVIRONMENT.md](../DEVELOPMENT_ENVIRONMENT.md).`
  - Keep content verbatim — no rewrite
- [ ] **4.2** Add banner to `docs/plans/unit-test-expansion.md`:
  - `> **Note:** Build commands in this plan predate the WSL2 migration. Use native `./gradlew` commands — see [DEVELOPMENT_ENVIRONMENT.md](../DEVELOPMENT_ENVIRONMENT.md).`

### Phase 5 — Cleanup

- [ ] **5.1** Remove `run/` from `.gitignore` (directory no longer exists, won't be recreated)

### Phase 6 — Verify

- [ ] **6.1** Run verification search:
  ```
  rg -n "run/mods|gradlew\.bat|working directory is \`run/\`|launch from the \`run/\` directory|cmd\.exe /c" README.md CLAUDE.md docs/ --glob '!docs/plans/*'
  ```
  Confirm zero hits in canonical docs. Historical plan docs are excluded intentionally.
- [ ] **6.2** Spot-check that `docs/plans/*.md` hits are only in bannered historical files.

## Follow-up (out of scope, separate task)

These are Gradle config issues found during the inventory — not docs changes, so they belong in a separate task:

- `build.gradle:42` — default `stageModsDir` fallback is `rootProject.file("run/mods")`. Should either fail-fast when `stageModsDir` is unset or use a sensible default.
- `hyvexa-purge/build.gradle:11-12` — hardcoded `run/mods/HygunsPlugin-3.6.1.jar` path. Dead on current setup.
