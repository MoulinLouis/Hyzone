# grepai - Semantic Code Search

Primary tool for code exploration. Use it to find code by intent rather than exact text.

**Does NOT index HytaleServer.jar** — for Hytale API, read `docs/HYTALE_API.md`.

Falls back to Grep/Glob if grepai is unavailable.

## Search

```bash
# Always use English queries, --json --compact flags
grepai search "parkour checkpoint saving" --json --compact
grepai search "player teleportation between modes" --json --compact
```

**Query tips:** describe intent ("handles user login"), not implementation ("func Login"). Be specific ("JWT token validation" > "token").

**Use Grep/Glob instead** for exact text matches (class names, imports, variable names).

## Call Graph Tracing

```bash
grepai trace callers "HandleRequest" --json     # who calls this?
grepai trace callees "ProcessOrder" --json      # what does this call?
grepai trace graph "ValidateToken" --depth 3 --json  # full call graph
```

Depth: 1-2 for quick checks, 3 for standard exploration (default), 4+ for deep refactoring.
