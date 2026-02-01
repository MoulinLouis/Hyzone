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
4. **UI paths use forward slashes** - `"Common/UI/Custom/Pages/X.ui"`
5. **Check ref validity** - `if (ref == null || !ref.isValid()) return;`
6. **World thread for entity ops** - use `world.execute(() -> ...)` or `CompletableFuture.runAsync(..., world)`
7. **Entity removal** - use `store.removeEntity(ref, RemoveReason.REMOVE)`
8. **Singleton components** - check for `INSTANCE` or `get()` (e.g., `Invulnerable.INSTANCE`)
9. **Track online players** - only spawn per-player entities for connected players
10. **Disable NPC AI** - use `Frozen` component to prevent autonomous movement

## Database

- Config: `run/mods/Parkour/database.json` (gitignored)
- Pattern: In-memory cache + MySQL persistence
- See `docs/DATABASE.md` for schema details

## Reference Files (in docs/)

| For... | Read... |
|--------|---------|
| Code patterns | `docs/CODE_PATTERNS.md` |
| Hytale API gotchas | `docs/HYTALE_API.md` |
| Full instructions | `docs/AGENTS.md` |
| System architecture | `docs/ARCHITECTURE.md` |
| Database schema | `docs/DATABASE.md` |
| Ascend mode details | `docs/ASCEND_MODULE_SUMMARY.md` |
| Feature history | `CHANGELOG.md` |
