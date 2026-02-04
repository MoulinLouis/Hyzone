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

## Key Directories

| Path | Content |
|------|---------|
| `hyvexa-*/src/main/java/io/hyvexa/` | Java sources |
| `hyvexa-parkour/src/main/resources/Common/UI/Custom/Pages/` | UI definitions |
| `run/mods/Parkour/` | Runtime config (database.json) |
| `docs/` | All documentation files |

## Workflow Reminders

1. **Update CHANGELOG.md** after completing each feature
2. **Follow existing patterns** - check similar files before implementing
3. **No build runs** - owner handles `./gradlew build`
4. **UI paths** - Code uses `"Pages/X.ui"`, files go in `Common/UI/Custom/Pages/`
5. **Check ref validity** - `if (ref == null || !ref.isValid()) return;`
6. **World thread for entity ops** - use `world.execute(() -> ...)` or `CompletableFuture.runAsync(..., world)`
7. **Entity removal** - use `store.removeEntity(ref, RemoveReason.REMOVE)`
8. **Singleton components** - check for `INSTANCE` or `get()` (e.g., `Invulnerable.INSTANCE`)
9. **Track online players** - only spawn per-player entities for connected players
10. **Disable NPC AI** - use `Frozen` component to prevent autonomous movement

## UI File Rules (.ui)

**CRITICAL - These cause parsing errors:**
- **No underscores in element IDs** - Use `#StatLabel` not `#Stat_Label`
- **No HorizontalAlignment in Label styles** - Causes UI crash. Use `Anchor` positioning instead

```
❌ Label { Style: (HorizontalAlignment: Right); }  // CRASHES
✅ Label { Anchor: (Right: 16); }                   // Use positioning instead
```

## Database

- Config: `run/mods/Parkour/database.json` (gitignored)
- Pattern: In-memory cache + MySQL persistence
- See `docs/DATABASE.md` for schema details

## Reference Files (in docs/)

| For... | Read...                         |
|--------|---------------------------------|
| Code patterns | `docs/CODE_PATTERNS.md`         |
| Hytale API gotchas | `docs/HYTALE_API.md`            |
| Full instructions | `docs/codex/AGENTS.md`          |
| System architecture | `docs/ARCHITECTURE.md`          |
| Database schema | `docs/DATABASE.md`              |
| Ascend mode details | `docs/ASCEND_MODULE_SUMMARY.md` |
| Game balancing | `docs/ECONOMY_BALANCE.md`       |
| Feature history | `CHANGELOG.md`                  |


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

