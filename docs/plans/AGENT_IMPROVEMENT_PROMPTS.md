# Agent Improvement Prompts

Generated 2026-03-24 from a full codebase + documentation audit.
Goal: reduce friction that causes AI agents to produce wrong, inconsistent, or low-quality output.

Run each prompt in a fresh session, in order. Each prompt is self-contained.

---

## Tier 1 — High-frequency agent errors

These fix problems that come up on nearly every session involving the affected area.

### 1. Split RunOrFallGameManager into focused classes

`hyvexa-runorfall/.../manager/RunOrFallGameManager.java` is 1994 lines — the largest file in the codebase. Agents lose context mid-edit and produce inconsistent changes (wrong state transitions, missed cleanup, duplicated logic).

**Prompt:**

> Read `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/manager/RunOrFallGameManager.java` and `docs/RunOrFall/README.md`. Split it into focused single-responsibility classes. Likely candidates:
>
> - `RunOrFallRoundManager` — round lifecycle (start, end, transitions)
> - `RunOrFallPlatformManager` — platform spawning, breaking, void detection
> - `RunOrFallPlayerManager` — player state, respawn, blink ability
> - `RunOrFallScoreManager` — scoring, elimination, leaderboard updates
>
> Keep `RunOrFallGameManager` as a thin orchestrator that delegates to these. Use constructor injection per existing patterns (see `docs/CODE_PATTERNS.md` Dependency Wiring section). Update `docs/RunOrFall/README.md` to reflect the new structure. Run tests afterward: `./gradlew :hyvexa-runorfall:test`.

---

### 2. Split AscendPlayerStore into store + persistence + side-effects

`hyvexa-parkour-ascend/.../data/AscendPlayerStore.java` is 1588 lines. It mixes data access, persistence logic, and gameplay side-effects (tutorial triggers, economy mutations). Agents editing one concern accidentally break another.

**Prompt:**

> Read `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java` and `docs/Ascend/README.md`. The file already has a companion `AscendPlayerPersistence.java` (1311 lines) — assess whether the split between them is clean.
>
> Refactor so that:
> - `AscendPlayerStore` owns only in-memory state and cache operations
> - `AscendPlayerPersistence` owns all SQL read/write operations
> - Side-effects (tutorial triggers, volt thresholds, achievement checks) move to a new `AscendPlayerEventHandler` or similar — these are gameplay reactions, not data operations
>
> Follow the constructor injection pattern. Update `docs/Ascend/README.md`. Run `./gradlew :hyvexa-parkour-ascend:test`.

---

### 3. Split HyvexaPlugin (Parkour) into plugin + event router

`hyvexa-parkour/.../HyvexaPlugin.java` is 1402 lines. It's the composition root AND the event handler for PlayerReady, Disconnect, Chat, etc. Agents adding new event handlers to this file often break initialization order or miss cleanup steps.

**Prompt:**

> Read `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java`. Extract event handling into a dedicated `ParkourEventRouter` class:
>
> - Move all `getEventRegistry().registerGlobal(...)` lambdas into `ParkourEventRouter`
> - `ParkourEventRouter` receives all needed managers/stores via constructor
> - `HyvexaPlugin.setup()` remains the composition root — it creates the router and calls `router.registerAll()`
> - Keep `HyvexaPlugin` under 500 lines (composition root + accessor methods only)
>
> Update `docs/Parkour/README.md`. Run `./gradlew :hyvexa-parkour:test`.

---

### 4. Document the "new Store" pattern end-to-end

Agents creating new stores often miss steps (forgetting `initialize()` call, not adding eviction on disconnect, wrong cache pattern). There's no end-to-end guide.

**Prompt:**

> Read `docs/CODE_PATTERNS.md`, `hyvexa-core/src/main/java/io/hyvexa/core/db/BasePlayerStore.java`, and one concrete implementation (e.g., `VexaStore.java`). Add a new section to `CODE_PATTERNS.md` titled "## Creating a New Store (End-to-End)" that covers:
>
> 1. Extend `BasePlayerStore<V>` — show the 5 required method overrides with explanations
> 2. Implement `loadSql()`, `upsertSql()`, `parseRow()`, `bindUpsertParams()`, `defaultValue()`
> 3. Add table creation in the module's `*DatabaseSetup.java` (or `initialize()` for core stores)
> 4. Register `initialize()` call in `Plugin.setup()` with `initSafe()` wrapper
> 5. Add `evict(playerId)` call in the plugin's `PlayerDisconnectEvent` handler
> 6. Add schema to `docs/DATABASE.md` under the correct module section
> 7. Show the full lifecycle: player connects -> `getOrLoad()` -> cache hit/miss -> `save()` -> disconnect -> `evict()`
>
> Use actual code from the codebase, not hypothetical examples.

---

### 5. Document cross-module communication patterns

Agents don't know how modules communicate. They either reach across module boundaries incorrectly (calling `ParkourAscendPlugin.getInstance()` from another module) or duplicate logic. `CurrencyBridge` and `GameModeBridge` exist but aren't documented.

**Prompt:**

> Read these files:
> - `hyvexa-core/src/main/java/io/hyvexa/core/economy/CurrencyBridge.java`
> - `hyvexa-core/src/main/java/io/hyvexa/core/bridge/GameModeBridge.java`
> - `hyvexa-core/src/main/java/io/hyvexa/core/state/ModeMessages.java`
> - `docs/CODE_PATTERNS.md`
> - `docs/ARCHITECTURE.md` (the module boundary rules)
>
> Add a new section to `CODE_PATTERNS.md` titled "## Cross-Module Communication" that covers:
>
> 1. **Rule:** Modules never import from each other. All cross-cutting goes through `hyvexa-core`.
> 2. **CurrencyBridge pattern:** How to query/modify currency from any module. Show actual API.
> 3. **GameModeBridge pattern:** How hub routing works, how modules register themselves.
> 4. **ModeMessages pattern:** How modules share state/announcements.
> 5. **Anti-pattern examples:** What NOT to do (direct plugin cross-access, shared mutable state).
>
> Keep it concise — 1 code example per pattern, max 60 lines total for the section.

---

### 6. Add CLAUDE.md files for the 3 largest modules

Agents working in `hyvexa-parkour-ascend` (139 files), `hyvexa-parkour` (132 files), or `hyvexa-purge` (71 files) lack module-specific context. They miss module-specific gotchas, file conventions, and key classes. Module CLAUDE.md files auto-load when the agent's working directory is inside the module.

**Prompt:**

> Read the module READMEs at `docs/Ascend/README.md`, `docs/Parkour/README.md`, and `docs/Purge/README.md`. Also read the root `CLAUDE.md` for the format.
>
> Create `CLAUDE.md` files in each module root (`hyvexa-parkour-ascend/CLAUDE.md`, `hyvexa-parkour/CLAUDE.md`, `hyvexa-purge/CLAUDE.md`). Each should be **under 50 lines** and contain only:
>
> 1. **Key classes** — The 5-8 most important classes an agent will touch, with one-line descriptions
> 2. **Module-specific gotchas** — Things that are NOT in the root CLAUDE.md (e.g., Ascend's `BaseAscendPage` requirement, Purge's session lifecycle constraints, Parkour's run state machine rules)
> 3. **File layout** — Where to find UI files, stores, managers for this module specifically
> 4. **Related docs pointer** — Link to the full module README
>
> Do NOT duplicate root CLAUDE.md content. These supplement it.

---

### 7. Document ECS tick system registration pattern

Agents asked to create new per-entity tick logic don't know how to register custom `EntityTickingSystem` subclasses. `CODE_PATTERNS.md` covers `CommandBuffer` usage inside ticks but not how to set up the system itself.

**Prompt:**

> Search the codebase for all classes extending `EntityTickingSystem` or `EntityEventSystem`. Read 2-3 representative examples and their registration code in the plugin setup. Then add a section to `docs/CODE_PATTERNS.md` titled "## Custom ECS Systems" covering:
>
> 1. How to create an `EntityTickingSystem` subclass (required method signatures)
> 2. How to register it on a specific world (show the actual registration API call)
> 3. How to filter which entities the system ticks (archetype filtering)
> 4. When to use ECS tick system vs `SCHEDULED_EXECUTOR` vs world-thread `runAsync`
> 5. Include the existing CommandBuffer guidance as a subsection
>
> Use real examples from the codebase. Keep it under 80 lines.

---

### 8. Standardize manager access pattern documentation

`CODE_PATTERNS.md` says "constructor injection" but agents see `VexaStore.getInstance()` in actual code and follow that pattern instead. The rule is: core singletons use `getInstance()`, module-specific managers use constructor injection — but this isn't stated anywhere.

**Prompt:**

> Read `docs/CODE_PATTERNS.md` (Dependency Wiring section) and scan the codebase for both `getInstance()` usage and constructor injection patterns. Clarify the existing Dependency Wiring section in `CODE_PATTERNS.md`:
>
> 1. **Core singletons** (`VexaStore`, `FeatherStore`, `DatabaseManager`, `CosmeticStore`, etc.) — use `getInstance()`. These are initialized once in the parkour plugin and shared globally. List all of them.
> 2. **Module-specific managers/stores** — use constructor injection. Created in `Plugin.setup()`, passed down through constructors.
> 3. **The rule:** Business logic and pages must NEVER call `Plugin.getInstance().getXyz()`. Only composition roots (plugin setup, commands, interactions) may access the plugin instance.
> 4. Add 2 short examples: one showing correct core singleton access, one showing correct module DI.
>
> This is a clarification of existing docs, not a code change.

---

## Tier 2 — Medium-frequency agent errors

These cause issues when agents work in specific areas.

### 9. Split DATABASE.md by module

`DATABASE.md` is 1746 lines. Agents searching for a table schema often read the wrong section, miss related tables, or add new tables in the wrong place.

**Prompt:**

> Read `docs/DATABASE.md` and `docs/README.md` (source of truth matrix). Split `DATABASE.md` into module-owned files:
>
> - `docs/DATABASE.md` — Keep as index: runtime notes, connection config, then a table listing which file owns which tables
> - `docs/Core/DATABASE.md` — Core/shared tables (vexa, feathers, cosmetics, discord, analytics, votes)
> - `docs/Parkour/DATABASE.md` — Parkour tables
> - `docs/Ascend/DATABASE.md` — Ascend + Mine tables
> - `docs/Purge/DATABASE.md` — Purge tables
> - `docs/RunOrFall/DATABASE.md` — RunOrFall tables
>
> Each file should be self-contained with CREATE TABLE statements, notes, and owning store class. Update the source of truth matrix in `docs/README.md` to reflect the split. Update any cross-references in module READMEs.

---

### 10. Create a Hytale UI quick-reference cheat sheet

The `docs/hytale-custom-ui/` directory has 120+ files. Agents creating UI spend excessive time searching for the right element type or property. A single cheat sheet would eliminate most lookups.

**Prompt:**

> Read `docs/CODE_PATTERNS.md` (UI sections) and scan `docs/hytale-custom-ui/type-documentation/elements/` (read the README and 5-6 most-used elements: Label, TextButton, Button, Group, Image, ProgressBar). Create `docs/UI_CHEAT_SHEET.md` containing:
>
> 1. **Element quick-reference table** — Every element type, one-line description, most common properties (max 2 columns wide)
> 2. **Layout patterns** — The 4-5 most common layouts (vertical stack, horizontal row, centered content, scrollable list, grid) with minimal .ui snippets
> 3. **Property gotchas** — Consolidated list from CODE_PATTERNS.md + CLAUDE.md (no underscores, LabelAlignment values, TextButton no children, dynamic background alpha, LayoutMode:Center doesn't exist)
> 4. **Template reference** — Common `$C.@` templates and what they expand to
>
> Target: an agent should be able to build any standard UI page by reading only this file + CODE_PATTERNS.md. Under 200 lines.

---

### 11. Split CHANGELOG.md [Unreleased] into versioned sections

`CHANGELOG.md` is 1153 lines with a massive `[Unreleased]` section. Agents told to "update CHANGELOG" append entries in random locations within the section, creating duplicates or misplaced entries.

**Prompt:**

> Read `CHANGELOG.md`. The [Unreleased] section contains dozens of features across multiple development phases. Restructure it:
>
> 1. Keep the `[Unreleased]` section for genuinely unreleased work (new features in progress)
> 2. Create versioned sections (e.g., `[1.1.0]`, `[1.0.0]`) for features that have been deployed to the server, based on what you can infer from git history (`git log --oneline`) and the content itself
> 3. Within each section, maintain the existing categories: Added, Changed, Fixed
> 4. Add a comment at the top of [Unreleased] with a brief instruction: `<!-- Add new entries at the TOP of the relevant category (Added/Changed/Fixed). One line per change. -->`
>
> Ask me which features have been released before moving them out of [Unreleased].

---

### 12. Add EntityVisibilityManager to HYTALE_API.md

The entity visibility system (`EntityVisibilityManager`, `EntityVisibilityFilterSystem`) exists in hyvexa-core but isn't documented. Agents writing code that needs to hide/show entities per player reinvent the wheel or use broken approaches.

**Prompt:**

> Read `hyvexa-core/src/main/java/io/hyvexa/common/visibility/EntityVisibilityManager.java` and `EntityVisibilityFilterSystem.java`. Add a section to `docs/HYTALE_API.md` titled "## Entity Visibility (Per-Player)" covering:
>
> 1. What it does (hide/show entities per player)
> 2. API: how to hide an entity from a player, how to show it
> 3. How it works under the hood (filter system registered on the world)
> 4. Gotchas (timing with spawn, interaction with other systems)
> 5. When to use this vs other approaches (like teleporting entities away)

---

### 13. Add missing tests for core stores

Agents modifying `CachedCurrencyStore`, `CosmeticStore`, or `VoteStore` have no way to verify their changes. These are pure-logic classes that could be tested but aren't.

**Prompt:**

> Read `hyvexa-core/src/main/java/io/hyvexa/core/economy/CachedCurrencyStore.java`, `hyvexa-core/src/main/java/io/hyvexa/core/cosmetic/CosmeticStore.java`, and existing test examples in `hyvexa-core/src/test/`. Write unit tests for:
>
> 1. `CachedCurrencyStore` — test `modifyBalance` atomicity (mock the ConnectionProvider), test cache eviction, test staleness detection
> 2. `CosmeticStore` — test `purchaseShopCosmetic` balance check logic, test cosmetic ownership tracking
>
> Follow the existing test patterns in the project. Only test pure logic that doesn't require Hytale server runtime. Run `./gradlew :hyvexa-core:test` to verify.

---

### 14. Split AscendDatabaseSetup into grouped files

`AscendDatabaseSetup.java` is 1669 lines of CREATE TABLE + ALTER TABLE statements. Agents adding new Ascend tables paste them in random locations. The file is too large to scan for existing schema before adding.

**Prompt:**

> Read `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendDatabaseSetup.java`. Split into focused setup classes:
>
> - `AscendCoreDatabaseSetup` — Player, progress, evolution, achievement tables
> - `AscendMineDatabaseSetup` — Mine zones, blocks, pickaxes, conveyor tables
> - `AscendSkillDatabaseSetup` — Skill tree, skill nodes, challenge tables
> - `AscendGhostDatabaseSetup` — Ghost recording tables
>
> Each class should have a single `setup(Connection)` method. The main `AscendDatabaseSetup` becomes an orchestrator calling each in order. Update `docs/Ascend/README.md` if needed. Run `./gradlew :hyvexa-parkour-ascend:test`.

---

### 15. Document the complete UI page creation workflow

Agents creating new UI pages miss steps: they create the .ui file but forget the Java page class, or create the page class but use wrong event data codec, or wire events incorrectly. There's no end-to-end checklist.

**Prompt:**

> Read `docs/CODE_PATTERNS.md` (UI Pages + UI Files sections), one complete page example (e.g., search for a simple page in hyvexa-purge or hyvexa-wardrobe — under 200 lines), and its corresponding .ui file. Add a section to `CODE_PATTERNS.md` titled "## Creating a New UI Page (End-to-End)" that covers the full workflow:
>
> 1. Create the `.ui` file in `src/main/resources/Common/UI/Custom/Pages/ModuleName_PageName.ui`
> 2. Create the Java page class extending `InteractiveCustomUIPage<T>` (or `BaseAscendPage` for Ascend)
> 3. Define the event data type and codec
> 4. Implement `build()` — append the .ui path, set initial values, bind events
> 5. Implement `handleDataEvent()` — dispatch on button/event keys
> 6. Open the page: `CustomUI.open(playerRef, new MyPage(...))`
> 7. Wire it to a command or interaction trigger
>
> Include a minimal but complete working example (page + .ui file pair). Under 80 lines of doc.

---

## Tier 3 — Low-frequency but high-impact

These cause serious issues when they occur, but agents hit them less often.

### 16. Split RobotManager (Ascend ghost replay)

`RobotManager.java` is 1375 lines handling ghost NPC spawning, replay, interpolation, and cleanup. Agents editing ghost behavior often break spawn lifecycle or cleanup.

**Prompt:**

> Read `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotManager.java` and `docs/Ascend/README.md`. Split into:
>
> - `RobotManager` — Orchestrator, manages per-player robot state, public API
> - `RobotSpawner` — NPC creation, positioning, variant setup
> - `RobotReplayer` — Tick-based interpolation, sample playback, timing
> - `RobotCleanup` — Despawn logic, orphan detection, disconnect cleanup
>
> Constructor injection for all. Update docs. Run tests.

---

### 17. Split MineConfigStore

`MineConfigStore.java` is 1156 lines mixing mine zone config, block config, tier config, and sorted cache logic. Agents modifying mine balance touch the wrong config section.

**Prompt:**

> Read `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/data/MineConfigStore.java`. Assess whether it can be split into:
>
> - `MineZoneConfigStore` — Zone definitions, depth layers
> - `MineBlockConfigStore` — Block types, HP, rewards
> - `MineTierConfigStore` — Tier thresholds, progression
>
> Or if the coupling is too tight, at least extract the sorted cache logic into a separate helper. The goal is files under 500 lines. Update docs. Run tests.

---

### 18. Add test coverage for RunOrFall scoring/elimination

`RunOrFallGameManager` has complex scoring and elimination logic but only 2 test files cover basic stats and platforms. Agents modifying round transitions or scoring have no safety net.

**Prompt:**

> Read `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/manager/RunOrFallGameManager.java` (the scoring/elimination sections) and existing tests in `hyvexa-runorfall/src/test/`. Write unit tests for:
>
> 1. Round transition logic (when does a round end, what triggers next round)
> 2. Player elimination conditions (void detection, lives, disconnection)
> 3. Score calculation (kills, survival time, combo bonuses if any)
>
> Extract testable pure-logic methods if needed. Only test logic that doesn't require Hytale runtime. Run `./gradlew :hyvexa-runorfall:test`.

---

### 19. Document the Purge session lifecycle constraints

Agents adding Purge features don't understand the session state machine. They add logic that runs outside a valid session, or modify state during transitions. The README mentions the lifecycle but doesn't specify constraints.

**Prompt:**

> Read `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeSessionManager.java`, `PurgeWaveManager.java`, and `docs/Purge/README.md`. Add a "## Session Lifecycle Rules" section to `docs/Purge/README.md` with:
>
> 1. State diagram: what states exist, what transitions are valid
> 2. Per-state rules: what can/cannot be modified in each state
> 3. Thread safety: which methods must run on the world thread
> 4. Common mistakes: what agents get wrong (e.g., modifying wave config during an active session)

---

### 20. Consolidate HUD patterns across modules

Each module implements HUD differently. Agents creating HUDs in a new context don't know which pattern to follow and may pick the wrong one.

**Prompt:**

> Read the HUD implementations across modules:
> - `hyvexa-parkour/src/main/java/io/hyvexa/parkour/hud/HudManager.java`
> - `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/hud/AscendHudManager.java`
> - `hyvexa-purge/src/main/java/io/hyvexa/purge/hud/PurgeHudManager.java`
> - `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/hud/` (whatever exists)
>
> Add a section to `docs/CODE_PATTERNS.md` titled "## HUD Management" that:
>
> 1. Describes the common pattern shared across all implementations (attach, update, detach lifecycle)
> 2. Notes the differences and when each variation is appropriate
> 3. Shows the recommended pattern for new modules
> 4. Lists gotchas (e.g., HUD cleanup on disconnect, world-thread requirements)
>
> This is documentation only — don't refactor the existing HUD code.

---

## Execution notes

- **Dependencies:** Prompts 1-3 (file splits) are independent and can run in parallel. Prompt 4 depends on reading existing stores. Prompts 5, 7, 8 are pure doc work and can run in any order.
- **Verification:** Every code-change prompt includes a test command. If tests fail, fix them before moving on.
- **Scope control:** Each prompt is one logical change. Don't combine prompts in a single session — the context load will degrade quality.
- **Existing bugs:** This file targets agent friction only. See `docs/plans/AUDIT_PROMPTS.md` for the separate bug/code-quality audit.
