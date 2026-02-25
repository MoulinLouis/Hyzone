# Claude Code Reference

Quick reference for AI agents working on this Hytale plugin project.

## Quick Commands

```bash
# Do NOT run builds - owner handles builds/tests
# ./gradlew stagePlugins - copy jars to run/mods
# ./gradlew collectPlugins - copy jars to build/libs
```

## Project Overview

**Hyvexa** - Multi-module Hytale server plugin suite for a parkour minigame server.

| Module | Purpose | Entry Point |
|--------|---------|-------------|
| `hyvexa-core` | Shared DB, utilities, mode state | N/A (library) |
| `hyvexa-parkour` | Main parkour gameplay | `io.hyvexa.HyvexaPlugin` |
| `hyvexa-parkour-ascend` | Ascend idle mode | `io.hyvexa.ascend.ParkourAscendPlugin` |
| `hyvexa-hub` | Hub routing + mode selection | `io.hyvexa.hub.HyvexaHubPlugin` |
| `hyvexa-launch` | IntelliJ launch classpath module | `com.hypixel.hytale.Main` |
| `discord-bot` | Discord bot (Node.js) for account linking | `src/index.js` |

## Key Directories

| Path | Content |
|------|---------|
| `hyvexa-*/src/main/java/io/hyvexa/` | Java sources |
| `hyvexa-parkour/src/main/resources/Common/UI/Custom/Pages/` | UI definitions |
| `run/mods/Parkour/` | Runtime config (database.json) |
| `docs/` | All documentation files |
| `discord-bot/` | Discord bot: account linking + vexa reward |

## Workflow Reminders

1. **Update CHANGELOG.md** for significant changes only (new features, balance changes, bug fixes) - keep entries brief (1-2 lines), no implementation details
2. **Update docs/Ascend/ECONOMY_BALANCE.md** when modifying game economy (costs, rewards, multipliers, formulas)
3. **Follow existing patterns** - check similar files before implementing
4. **Reuse existing Managers** - 25+ Manager classes exist across modules. Check for existing ones before creating new ones (e.g., `HyvexaPlugin.getInstance().getHudManager()`, `ParkourAscendPlugin.getInstance().getRobotManager()`)
5. **No build runs** - owner handles `./gradlew build`
6. **UI paths** - Code uses `"Pages/X.ui"`, files go in `Common/UI/Custom/Pages/`
7. **Check ref validity** - `if (ref == null || !ref.isValid()) return;`
8. **World thread for entity ops** - use `world.execute(() -> ...)` or `CompletableFuture.runAsync(..., world)`
9. **Entity removal** - use `store.removeEntity(ref, RemoveReason.REMOVE)`
10. **Singleton components** - check for `INSTANCE` or `get()` (e.g., `Invulnerable.INSTANCE`)
11. **Track online players** - only spawn per-player entities for connected players
12. **Disable NPC AI** - use `Frozen` component to prevent autonomous movement
13. **Avoid Unicode in chat messages** - Hytale client displays many Unicode characters as `?`. Use ASCII alternatives: `->` instead of `→`, `x` instead of `×`, `-` instead of `–`/`—`

## UI Patterns
- Never use dynamic Background property changes on UI elements — they don't work in Hytale's UI system
- Use overlay/visibility toggle pattern for disabled states and visual changes
- Never use CSS-like syntax (opacity, Width percentages, Anchor.Width) — use segment-based visibility or Group wrapper + overlay patterns
- For tabs, use Group wrapper + TextButton overlay, NOT TextButton-as-tab pattern

**CRITICAL - These cause parsing errors:**
- **No underscores in element IDs** - Use `#StatLabel` not `#Stat_Label`

## Database

- Config: `run/mods/Parkour/database.json` (gitignored)
- Pattern: In-memory cache + MySQL persistence
- See `docs/DATABASE.md` for schema details

## Discord Bot (`discord-bot/`)

Node.js bot for Discord-Minecraft account linking. Shares the same MySQL database as the plugin.

- **Flow**: Player runs `/link` in-game -> gets a code (e.g. `X7K-9M2`) -> enters `/link X7K-9M2` on Discord -> bot validates & creates permanent link -> next login, plugin awards 100 vexa
- **Config**: `discord-bot/.env` (gitignored) - DB credentials, bot token, guild ID. See `.env.example`
- **Run locally**: `cd discord-bot && npm install && npm run start`
- **Production**: Hosted on game server panel (Helloserv) or separate VPS with `pm2`
- **Plugin side**: `DiscordLinkStore` singleton in `hyvexa-core` handles code generation, link checking, and vexa rewards. Initialized by all 3 plugin modules.
- **DB tables**: `discord_link_codes` (temp codes, 5min expiry), `discord_links` (permanent links + `vexa_rewarded` flag)

## Reference Files (in docs/)

| For... | Read...                         |
|--------|---------------------------------|
| Code patterns | `docs/CODE_PATTERNS.md`         |
| Hytale API gotchas | `docs/HYTALE_API.md`            |
| **Hytale Custom UI docs** | `docs/hytale-custom-ui/` (official reference) |
| Full instructions | `docs/codex/AGENTS.md`          |
| System architecture | `docs/ARCHITECTURE.md`          |
| Database schema | `docs/DATABASE.md`              |
| Game balancing | `docs/Ascend/ECONOMY_BALANCE.md`       |
| Help & tutorial system | `docs/Ascend/TUTORIAL_FLOW.md` |
| Feature history | `CHANGELOG.md`                  |

### Hytale Custom UI Reference (`docs/hytale-custom-ui/`)
Official Hytale UI documentation. **Consult this BEFORE writing any .ui file.**
- `index.mdx` / `common-styling.mdx` / `layout.mdx` / `markup.mdx` — Core concepts
- `type-documentation/elements/` — All UI elements (group, label, textbutton, panel, etc.)
- `type-documentation/property-types/` — All property types (anchor, buttonstyle, labelstyle, etc.)
- `type-documentation/enums/` — All enums (layoutmode, labelalignment, etc.)


## grepai - Semantic Code Search

**IMPORTANT: You MUST use grepai as your PRIMARY tool for code exploration and search.**

### When to Use grepai (REQUIRED)

Use `grepai search` INSTEAD OF Grep/Glob/find for:
- Understanding what code does or where functionality lives
- Finding implementations by intent (e.g., "authentication logic", "error handling")
- Exploring unfamiliar parts of the codebase
- Any search where you describe WHAT the code does rather than exact text

### When to Use Standard Tools

**CRITICAL: DO NOT use grepai to search HytaleServer.jar (the Hytale API).**
- grepai is configured ONLY for the plugin code, NOT HytaleServer.jar
- To understand Hytale API classes/methods → Read **docs/HYTALE_API.md**
- To find plugin code → grepai is OK

Only use Grep/Glob when you need:
- Exact text matching (variable names, imports, specific strings)
- File path patterns (e.g., `**/*.go`)

### Example Workflows

**Use grepai for semantic discovery:**
```bash
✅ grepai search "UI page selection logic" --json --compact
✅ grepai search "parkour checkpoint saving" --json --compact
✅ grepai search "player teleportation between modes" --json --compact
```

**Use grepai for plugin code, NOT for Hytale API:**
```bash
✅ grepai search "parkour checkpoint logic" --json --compact  # Plugin code - OK
✅ grepai search "elevation menu handling" --json --compact   # Plugin code - OK

❌ grepai search "how EntityRef works"  # Hytale API - NOT indexed
✅ Read docs/HYTALE_API.md  # To learn about Hytale API

❌ grepai search "World execute method"  # Hytale API - NOT indexed
✅ Read docs/HYTALE_API.md  # To learn about Hytale API
```

**Use grep for exact matches:**
```bash
❌ grepai search "ParkourAscendPlugin"  # Exact class name
✅ grep -r "class ParkourAscendPlugin"

❌ grepai search "import java.util.List"  # Exact import
✅ grep -r "import java.util.List"
```

### Fallback

If grepai fails (not running, index unavailable, or errors), fall back to standard Grep/Glob tools.

### Usage

```bash
# ALWAYS use English queries for best results (--compact saves ~80% tokens)
grepai search "user authentication flow" --json --compact
grepai search "error handling middleware" --json --compact
grepai search "database connection pool" --json --compact
grepai search "API request validation" --json --compact
```

### Query Tips

- **Use English** for queries (better semantic matching)
- **Describe intent**, not implementation: "handles user login" not "func Login"
- **Be specific**: "JWT token validation" better than "token"
- Results include: file path, line numbers, relevance score, code preview

### Call Graph Tracing

Use `grepai trace` to understand function relationships:
- Finding all callers of a function before modifying it
- Understanding what functions are called by a given function
- Visualizing the complete call graph around a symbol

#### Trace Commands

**IMPORTANT: Always use `--json` flag for optimal AI agent integration.**

```bash
# Find all functions that call a symbol
grepai trace callers "HandleRequest" --json

# Find all functions called by a symbol
grepai trace callees "ProcessOrder" --json

# Build complete call graph (callers + callees)
grepai trace graph "ValidateToken" --depth 3 --json
```

#### Trace Depth Guidelines

- **Depth 1-2**: Quick checks, immediate dependencies
- **Depth 3**: Standard exploration (recommended default)
- **Depth 4+**: Deep analysis for complex refactoring or architectural changes

### Performance Note

**grepai is optimized for exploration and discovery.** Use it when you need to understand code intent or find functionality. For rapid iteration with known exact matches (e.g., renaming a specific variable across files), grep is still faster.

### Workflow

1. Start with `grepai search` to find relevant code
2. Use `grepai trace` to understand function relationships
3. Use `Read` tool to examine files from results
4. Only use Grep for exact string searches if needed


## Behavioral Guidelines

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
