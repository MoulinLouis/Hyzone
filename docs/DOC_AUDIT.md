# Documentation Audit - 2026-03-20

Cross-referenced all documentation against the actual codebase. Findings organized by file.

---

## docs/ARCHITECTURE.md

### [HIGH] MineUpgradeType enum completely wrong
- **Section:** "Upgrades (MineUpgradeType enum)"
- **Doc claims:** 3 upgrades: `MINING_SPEED` (max 100), `BAG_CAPACITY` (max 50), `MULTI_BREAK` (max 20)
- **Code shows:** 8 upgrades: `BAG_CAPACITY(50)`, `MOMENTUM(25)`, `FORTUNE(25)`, `JACKHAMMER(10)`, `STOMP(15)`, `BLAST(15)`, `HASTE(20)`, `CONVEYOR_CAPACITY(25)`. `MINING_SPEED` and `MULTI_BREAK` no longer exist.
- **Fix:** Replace the 3-row upgrade table with the actual 8 upgrades and their correct max levels.

### [HIGH] ParkourConstants XP values wrong
- **Section:** "ParkourConstants.java"
- **Doc claims:** `MAP_XP_EASY = 100`, `MAP_XP_MEDIUM = 200`, `MAP_XP_HARD = 400`, `MAP_XP_INSANE = 800`
- **Code shows:** `MAP_XP_EASY = 15L`, `MAP_XP_MEDIUM = 30L`, `MAP_XP_HARD = 60L`, `MAP_XP_INSANE = 100L`
- **Fix:** Update values to 15, 30, 60, 100.

### [HIGH] ELEVATION_MULTIPLIER_EXPONENT does not exist
- **Section:** "AscendConstants.java"
- **Doc claims:** `ELEVATION_MULTIPLIER_EXPONENT = 1.05`
- **Code shows:** No such constant. Elevation multiplier is `Math.max(1, level)` (linear). Cost formula uses `ELEVATION_COST_GROWTH = 1.15` and `ELEVATION_COST_CURVE = 0.72`.
- **Fix:** Remove `ELEVATION_MULTIPLIER_EXPONENT` row. Add `ELEVATION_COST_GROWTH = 1.15` and `ELEVATION_COST_CURVE = 0.72`.

### [MEDIUM] MineCommand subcommands outdated
- **Section:** "MineCommand"
- **Doc claims:** subcommands: `sell`, `upgrades`, `select`, `addcrystals (admin)`
- **Code shows:** `sell`, `upgrades`, `achievements`, `addcrystals`. No `select` subcommand (removed in single-mine refactor).
- **Fix:** Replace `select` with `achievements`.

### [MEDIUM] Missing database tables
- `mine_zone_layers` not in table list (exists in `AscendDatabaseSetup.java` and `MineConfigStore.java`)
- `parkour_ghost_recordings` not in table list (exists in `HyvexaPlugin.java`)
- `saved_run_state` not in table list (exists in `RunStateStore`)
- `player_settings` not in table list (exists in `PlayerSettingsPersistence`)
- **Fix:** Add all four tables to the database tables section.

---

## docs/DATABASE.md

### [HIGH] Summit category values wrong
- **Section:** `ascend_player_summit` table notes
- **Doc claims:** `category` values: `VEXA_FLOW`, `RUNNER_SPEED`, `MANUAL_MASTERY`
- **Code shows:** `MULTIPLIER_GAIN`, `RUNNER_SPEED`, `EVOLUTION_POWER`
- **Fix:** Update category values.

### [MEDIUM] parkour_ghost_recordings table missing entirely
- **Code shows:** Created in `HyvexaPlugin.java` as `GhostStore("parkour_ghost_recordings", "parkour")`
- **Fix:** Add table entry (same schema as `ascend_ghost_recordings` but different table name).

### [MEDIUM] New mine upgrade columns not documented
- **Section:** `mine_players` table
- **Doc says:** `mining_speed_level` and `multi_break_level` are `DEPRECATED: no longer used`
- **Code shows:** New columns added via migration (`upgrade_momentum`, `upgrade_fortune`, `upgrade_jackhammer`, `upgrade_stomp`, `upgrade_blast`, `upgrade_haste`) but not documented.
- **Fix:** Add the 6 new upgrade columns to the `mine_players` schema.

---

## docs/Ascend/ECONOMY_BALANCE.md

### [HIGH] Ascendancy Tree completely wrong
- **Section:** "Ascendancy Tree" table
- **Doc shows:** 13 nodes, costs 1-75, total 286 AP, with names like "Runner Speed+", "Momentum Speed", "Automation"
- **Code shows:** 19 nodes, costs 1-1000, total 2353 AP, with names like "Auto-Upgrade + Momentum", "Ascension Challenges", "Auto Ascend"
- **Fix:** Replace entire Ascendancy Tree table with the 19-node tree from `AscendConstants.java`.

### [HIGH] Challenge list outdated (7 vs 8) with wrong values
- **Section:** "Challenges"
- **Doc claims:** 7 challenges
- **Code shows:** 8 challenges (Challenge 8: "Complete an Ascension without Elevation or Summit")
- Also wrong divisor values:
  - Doc "Runner Speed at 50%" vs code "Runner Speed /3"
  - Doc "Multiplier Gain at 50%" vs code "Multiplier Gain /4"
  - Doc "Evolution Power at 50%" vs code "Evolution Power /5"
  - Doc "All Summit bonuses at 50%" vs code "Runner Speed /4 and Multiplier Gain /4"
  - Doc "Maps 4 & 5 locked" vs code "all Summit bonuses /2, maps 3 & 4 locked"
- **Fix:** Update to 8 challenges with correct divisor values and locked maps.

### [MEDIUM] Runner multiplier gain at 1 star wrong
- **Section:** "Runner Multiplier (Automatic)"
- **Doc claims:** "1 star+: +0.2 per completion (x2 after evolution)"
- **Code shows:** Evolution Power base = 3.0, so 1 star = `0.1 * 3^1 = 0.3`. The x2 system was replaced with Evolution Power.
- **Fix:** Update to +0.3 and explain the Evolution Power system.

### [MEDIUM] Multiplier Slots count wrong
- **Section:** "Multiplier Slots"
- **Doc claims:** "Total slots: 6 (one per map; map 6 requires Transcendence Milestone 1)"
- **Code shows:** `MULTIPLIER_SLOTS = 5`
- **Fix:** Update to 5.

### [MEDIUM] "Evolution benefit: x2 multiplier gain" is outdated
- **Section:** "Balance Constraints"
- **Doc claims:** "Evolution benefit: x2 multiplier gain with no cost penalty"
- **Code shows:** Evolution Power = 3.0 base + 0.10 per summit level, not x2.
- **Fix:** Update to reflect Evolution Power system.

### [MEDIUM] Elevation formula missing soft cap
- **Section:** "Cost Formula"
- **Doc claims:** `cost = 30,000 x 1.15^(currentLevel^0.72)`
- **Code shows:** Two-phase formula with soft cap at level 300. Above 300: `1.15^(300^0.72 + (level-300)^0.58)`
- (Correct formula IS documented later in "Formula Consistency" section but not in the main section.)
- **Fix:** Add soft cap note in the main elevation section.

---

## docs/Ascend/TUTORIAL_FLOW.md

### [MEDIUM] Elevation tutorial examples use old formula
- **Section:** "Step 1 - Elevation"
- **Doc claims:** "level 10 = x11, level 100 = x126"
- **Code shows:** `getElevationMultiplier(level) = Math.max(1, level)`, so level 10 = x10, level 100 = x100.
- **Fix:** Update to "level 10 = x10, level 100 = x100".

---

## docs/Ascend/MINE_STATUS.md

### [HIGH] Entirely outdated - based on multi-mine system
- Lists 3 upgrade types (`Mining Speed`, `Bag Capacity`, `Multi-Break`) -- code now has 8
- References `MineSelectPage` -- removed in single-mine refactor
- References `/mine select` subcommand -- removed
- Lists `MineBonusCalculator` cross-progression bonuses (Runner Speed +5%/+10%, etc.) -- code now returns 1.0 for all
- Table count may be wrong (mine_zone_layers added)
- **Fix:** Complete rewrite needed to reflect single-mine system and 8 upgrade types.

---

## docs/Ascend/MINE_BALANCE_ROADMAP.md

### [MEDIUM] Superseded by single-mine implementation
- Written as a design proposal for 5 mines with pickaxe progression.
- Codebase was refactored to a single mine (commit `536cbf4`).
- **Fix:** Add header noting it's a historical design doc superseded by the single-mine implementation, or delete.

---

## docs/Ascend/README.md

### [LOW] /mine subcommands outdated
- **Line ~59:** `/mine sell / upgrades / select`
- **Code shows:** `select` removed, `achievements` added.
- **Fix:** Change to `/mine sell / upgrades / achievements`.

---

## docs/TECH_DEBT.md

### [HIGH] File deleted but still referenced
- File is deleted (`D` in git status) but `CLAUDE.md` still references it in the Reference Files table.
- **Fix:** Remove from CLAUDE.md's Reference Files table (see CLAUDE.md finding below).

---

## docs/CODE_PATTERNS.md

No issues found. All patterns verified against codebase.

## docs/HYTALE_API.md

No issues found. All API gotchas verified (Vector3d, singletons, entity removal, NPC variants, state machine, player HP, UI lifecycle).

## docs/Core/README.md

No issues found. All subsystems, stores, utilities, and table ownership verified.

## docs/Hub/README.md

No issues found. HubRouter, HubHud, startup flow verified.

## docs/Parkour/README.md

No issues found. Stores, run lifecycle, table ownership verified.

## docs/Purge/README.md

No issues found. Managers, stores, session lifecycle, commands verified.

## docs/RunOrFall/README.md

No issues found. Game flow, persistence, default values verified.

## docs/Wardrobe/README.md

No issues found. Classes, stores, runtime flow, commands verified.

## docs/Votifier/README.md

No issues found. Plugin lifecycle, VoteProcessor, integration verified.

## docs/DiscordBot/README.md

No issues found. Linking flow, rank sync, env config, tables verified.

## docs/GREPAI.md

No issues found. Tool reference doc, nothing to verify against code.

---

## CLAUDE.md

### [HIGH] References deleted TECH_DEBT.md
- **Section:** Reference Files table
- **Doc says:** `Tech debt & refactoring | docs/TECH_DEBT.md`
- **Fix:** Remove this row.

### [MEDIUM] hyvexa-launch entry point is misleading
- **Section:** Module table
- **Doc says:** Entry point `com.hypixel.hytale.Main`
- **Reality:** `hyvexa-launch` has zero Java source files. It's a classpath anchor module. `com.hypixel.hytale.Main` lives in `HytaleServer.jar`, not in this module.
- **Fix:** Change entry point to `N/A (classpath anchor)`.

### [LOW] Key Directories wildcards too broad
- `hyvexa-*/src/main/resources/Server/Item/` -- `hyvexa-wardrobe`, `hyvexa-core`, `hyvexa-launch` don't have this path.
- `hyvexa-*/src/main/resources/Common/UI/Custom/Pages/` -- `hyvexa-core`, `hyvexa-launch`, `hyvexa-votifier` don't have this path.
- **Fix:** Note these are "gameplay modules" not all modules, or list specific modules.

### [LOW] Manager count slightly understated
- **Doc says:** "45+"
- **Actual count:** 48 Manager classes.
- **Fix:** Update to "~50" or leave as "45+" (still technically correct).

---

## CHANGELOG.md

### [MEDIUM] ~20 recent commits not reflected
Significant features and fixes from recent commits are missing:
- `a5519ff` feat: persist player settings across reconnections
- `ee10ae9` feat(ascend): add conveyor chest UI for block collection
- `0a9496a` feat(ascend): multi-slot miner purchase UI and full reset
- `845e31d` feat(ascend): multi-slot miner waypoint conveyor system
- `536cbf4` refactor(ascend): simplify mine system to single mine
- `3bb96df` fix(parkour): prevent sweep from deleting saved run state in DB
- `fdfe34b` fix(ascend): keep global Ascend HUD visible during mine mode
- Multiple miner block placement direction fixes
- **Fix:** Add entries for significant features and fixes.

### [LOW] RunOrFall Coins added-then-removed clutter
- Both "Coins currency + round rewards" (added) and "Coins feature removed" appear in same `[Unreleased]` section.
- Feature was added and fully removed before any release.
- **Fix:** Remove the "added" entry; rephrase the removal as the chat announcement feature that replaced it.

### [LOW] Duplicate section headers
- `[Unreleased]` has duplicate `### Added` and `### Changed` headers, making it harder to parse.
- **Fix:** Consolidate into single headers per type.

---

## Inline Code Documentation

### [MEDIUM] Parkour manifest wrong dependency identifier
- **File:** `hyvexa-parkour/src/main/resources/manifest.json:17`
- **Doc says:** `"Hardaway:Wardrobe": "*"` in OptionalDependencies
- **Code shows:** Wardrobe's actual identifier is `io.hyvexa:HyvexaWardrobe` (as correctly used in Purge's manifest)
- **Fix:** Change to `"io.hyvexa:HyvexaWardrobe": "*"`.

### [MEDIUM] discord-bot/.env.example missing env vars
- **File:** `discord-bot/.env.example`
- Bot code reads `RANK_SYNC_BATCH_SIZE` and `RANK_SYNC_INTERVAL_MS` from `process.env` (with defaults 50 and 30000ms) but these aren't in .env.example.
- **Fix:** Add as commented optional vars:
  ```
  # Rank sync tuning (optional - defaults shown)
  # RANK_SYNC_BATCH_SIZE=50
  # RANK_SYNC_INTERVAL_MS=30000
  ```

### [LOW] build.gradle comment says "build.properties" instead of "gradle.properties"
- **File:** `hyvexa-parkour/build.gradle:28`
- **Fix:** Change `build.properties file` to `gradle.properties file`.

### [LOW] discord-bot/package.json description incomplete
- **File:** `discord-bot/package.json:4`
- **Doc says:** "Discord bot for Hyvexa account linking and vexa rewards"
- **Missing:** Rank role syncing feature.
- **Fix:** Add "and rank role sync" to description.

### [LOW] Possibly unused votifier dependency in parkour build.gradle
- **File:** `hyvexa-parkour/build.gradle:9`
- `compileOnly project(':hyvexa-votifier')` declared but no source file imports from `org.hyvote`.
- Vote integration uses `io.hyvexa.core.vote.*` from `hyvexa-core`.
- **Fix:** Verify if needed; remove if not.

---

## Summary

| Severity | Count | Key Areas |
|----------|-------|-----------|
| HIGH | 8 | Mine upgrades wrong, XP values wrong, Ascendancy Tree wrong, Challenges wrong, Summit categories wrong, MINE_STATUS.md outdated, TECH_DEBT.md deleted but referenced |
| MEDIUM | 12 | Missing DB tables, wrong formulas, outdated subcommands, missing changelog entries, manifest mismatch, .env.example gaps |
| LOW | 8 | Wildcard paths too broad, manager count, build comment typo, duplicate changelog headers, minor description gaps |
