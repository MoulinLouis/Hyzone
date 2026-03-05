# grepai - Semantic Code Search

**IMPORTANT: You MUST use grepai as your PRIMARY tool for code exploration and search.**

## When to Use grepai (REQUIRED)

Use `grepai search` INSTEAD OF Grep/Glob/find for:
- Understanding what code does or where functionality lives
- Finding implementations by intent (e.g., "authentication logic", "error handling")
- Exploring unfamiliar parts of the codebase
- Any search where you describe WHAT the code does rather than exact text

## When to Use Standard Tools

**CRITICAL: DO NOT use grepai to search HytaleServer.jar (the Hytale API).**
- grepai is configured ONLY for the plugin code, NOT HytaleServer.jar
- To understand Hytale API classes/methods → Read **docs/HYTALE_API.md**
- To find plugin code → grepai is OK

Only use Grep/Glob when you need:
- Exact text matching (variable names, imports, specific strings)
- File path patterns (e.g., `**/*.go`)

## Example Workflows

**Use grepai for semantic discovery:**
```bash
grepai search "UI page selection logic" --json --compact
grepai search "parkour checkpoint saving" --json --compact
grepai search "player teleportation between modes" --json --compact
```

**Use grepai for plugin code, NOT for Hytale API:**
```bash
grepai search "parkour checkpoint logic" --json --compact  # Plugin code - OK
grepai search "elevation menu handling" --json --compact   # Plugin code - OK

# Hytale API - NOT indexed by grepai:
# Read docs/HYTALE_API.md instead
```

**Use grep for exact matches:**
```bash
# Exact class name -> use grep, not grepai
grep -r "class ParkourAscendPlugin"

# Exact import -> use grep, not grepai
grep -r "import java.util.List"
```

## Fallback

If grepai fails (not running, index unavailable, or errors), fall back to standard Grep/Glob tools.

## Usage

```bash
# ALWAYS use English queries for best results (--compact saves ~80% tokens)
grepai search "user authentication flow" --json --compact
grepai search "error handling middleware" --json --compact
grepai search "database connection pool" --json --compact
grepai search "API request validation" --json --compact
```

## Query Tips

- **Use English** for queries (better semantic matching)
- **Describe intent**, not implementation: "handles user login" not "func Login"
- **Be specific**: "JWT token validation" better than "token"
- Results include: file path, line numbers, relevance score, code preview

## Call Graph Tracing

Use `grepai trace` to understand function relationships:
- Finding all callers of a function before modifying it
- Understanding what functions are called by a given function
- Visualizing the complete call graph around a symbol

### Trace Commands

**IMPORTANT: Always use `--json` flag for optimal AI agent integration.**

```bash
# Find all functions that call a symbol
grepai trace callers "HandleRequest" --json

# Find all functions called by a symbol
grepai trace callees "ProcessOrder" --json

# Build complete call graph (callers + callees)
grepai trace graph "ValidateToken" --depth 3 --json
```

### Trace Depth Guidelines

- **Depth 1-2**: Quick checks, immediate dependencies
- **Depth 3**: Standard exploration (recommended default)
- **Depth 4+**: Deep analysis for complex refactoring or architectural changes

## Performance Note

**grepai is optimized for exploration and discovery.** Use it when you need to understand code intent or find functionality. For rapid iteration with known exact matches (e.g., renaming a specific variable across files), grep is still faster.

## Workflow

1. Start with `grepai search` to find relevant code
2. Use `grepai trace` to understand function relationships
3. Use `Read` tool to examine files from results
4. Only use Grep for exact string searches if needed
