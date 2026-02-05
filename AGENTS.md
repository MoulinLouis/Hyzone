# Agent Instructions

**IMPORTANT: All project instructions, coding conventions, workflow rules, and reference documentation are maintained in **CLAUDE.md** at the repository root.**

**Read CLAUDE.md in full before starting any task.** It contains:

- Project overview and module structure
- Build and workflow rules (do NOT run builds)
- Key directories and file conventions
- UI file rules (critical parsing constraints)
- Database patterns
- Code patterns and Hytale API references
- Links to all documentation in `docs/`

## grepai (this environment)
- `grepai search`/`trace` may fail in sandbox because embeddings use local Ollama (`127.0.0.1:11434`).
- If that happens, run `grepai` outside sandbox (escalated); otherwise fallback to `rg`/`grep`.
