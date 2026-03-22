# Claude Code Reference

Quick reference for AI agents working on this Hytale plugin project.

## Quick Commands

```bash
# Do NOT run builds - owner handles builds
# ./gradlew stagePlugins - copy jars to run/mods
# ./gradlew collectPlugins - copy jars to build/libs
# cmd.exe /c "gradlew.bat test"  (use cmd.exe — WSL2 has I/O issues with gradlew)
```

## Project Overview

**Hyvexa** - Multi-module Hytale server plugin suite for a parkour minigame server.

| Module | Purpose | Entry Point |
|--------|---------|-------------|
| `hyvexa-core` | Shared DB, utilities, mode state | N/A (library) |
| `hyvexa-parkour` | Main parkour gameplay | `io.hyvexa.HyvexaPlugin` |
| `hyvexa-parkour-ascend` | Ascend idle mode | `io.hyvexa.ascend.ParkourAscendPlugin` |
| `hyvexa-hub` | Hub routing + mode selection | `io.hyvexa.hub.HyvexaHubPlugin` |
| `hyvexa-purge` | Zombie survival PvE mode | `io.hyvexa.purge.HyvexaPurgePlugin` |
| `hyvexa-runorfall` | Platforming minigame mode | `io.hyvexa.runorfall.HyvexaRunOrFallPlugin` |
| `hyvexa-wardrobe` | Cosmetics shop module | `io.hyvexa.wardrobe.WardrobePlugin` |
| `hyvexa-votifier` | Vote notification receiver (Votifier protocol) | `org.hyvote.plugins.votifier.HytaleVotifierPlugin` |
| `hyvexa-launch` | IntelliJ launch classpath module | N/A (classpath anchor) |
| `discord-bot` | Discord bot (Node.js) for account linking | `src/index.js` |

## Key Directories

| Path | Content |
|------|---------|
| `hyvexa-*/src/main/java/io/hyvexa/` | Java sources (gameplay modules only, not launch/votifier) |
| `hyvexa-*/src/main/resources/Common/UI/Custom/Pages/` | UI definitions (gameplay modules with UI) |
| `hyvexa-*/src/main/resources/Common/UI/Custom/Textures/` | UI texture assets (gameplay modules with UI) |
| `hyvexa-*/src/main/resources/Server/Item/` | Item definitions and interactions (gameplay modules only) |
| `hyvexa-parkour/src/main/resources/Common/Music/` | Music files |
| `hyvexa-parkour/src/main/resources/Common/Sounds/` | Sound effects |
| `run/mods/Parkour/` | Runtime config (database.json) |
| `docs/` | All documentation files |
| `discord-bot/` | Discord bot: account linking + vexa reward |

## Workflow Reminders

1. **Update CHANGELOG.md** for significant changes only (new features, balance changes, bug fixes) - keep entries brief (1-2 lines), no implementation details
2. **Update docs/Ascend/ECONOMY_BALANCE.md** when modifying game economy (costs, rewards, multipliers, formulas)
3. **Follow existing patterns** - check similar files before implementing
4. **Reuse existing Managers** - Many Manager classes (~50) exist across modules. Check for existing ones before creating new ones (e.g., `HyvexaPlugin.getInstance().getHudManager()`, `ParkourAscendPlugin.getInstance().getRobotManager()`)
5. **Run tests selectively** - only run tests when explicitly asked OR after large/significant changes (new features, multi-file refactors). Do NOT run tests after every small task. Only pure-logic classes with zero Hytale imports are testable — classes importing Hytale types (e.g. `Message`, `HytaleLogger`, `EntityRef`) cannot be tested without `HytaleServer.jar` on the test classpath (not yet configured).
6. **UI paths** - Code uses `"Pages/X.ui"`, files go in `Common/UI/Custom/Pages/`
7. **Check ref validity** - `if (ref == null || !ref.isValid()) return;`
8. **World thread for entity ops** - use `world.execute(() -> ...)` or `CompletableFuture.runAsync(..., world)`
9. **Entity removal** - use `store.removeEntity(ref, RemoveReason.REMOVE)`
10. **Singleton components** - check for `INSTANCE` or `get()` (e.g., `Invulnerable.INSTANCE`)
11. **Track online players** - only spawn per-player entities for connected players
12. **Disable NPC AI** - use `Frozen` component to prevent autonomous movement
13. **Avoid Unicode in chat messages** - Hytale client displays many Unicode characters as `?`. Use ASCII alternatives: `->` instead of `→`, `x` instead of `×`, `-` instead of `–`/`—`
14. **No auto-memory for project knowledge** - Multiple agents work on this repo. Store discoveries in `docs/` not in Claude's auto-memory, so all agents benefit.
15. **Plans go in `docs/plans/`** - Write implementation plans as `.md` files in the project, not only in Claude's internal plan file, so other agents can reference them.

## UI Patterns
- Never use dynamic Background property changes on UI elements — they don't work in Hytale's UI system
- Use overlay/visibility toggle pattern for disabled states and visual changes
- Never use CSS-like syntax (opacity, Width percentages, Anchor.Width) — use segment-based visibility or Group wrapper + overlay patterns
- For tabs, use Group wrapper + TextButton overlay, NOT TextButton-as-tab pattern

**CRITICAL - These cause parsing errors:**
- **No underscores in element IDs** - Use `#StatLabel` not `#Stat_Label`
- **LabelAlignment values are `Start`, `Center`, `End`** - NOT `Left`/`Right`. Using invalid enum values crashes the global UI parser, breaking ALL .ui files

## Database

- Config: `run/mods/Parkour/database.json` (gitignored)
- Pattern: In-memory cache + MySQL persistence
- See `docs/DATABASE.md` for schema details

## Discord Bot (`discord-bot/`)

Node.js bot for Discord account linking + rank sync. See `docs/DiscordBot/README.md` for full details.

## Reference Files (in docs/)

| For... | Read... |
|--------|---------|
| Code patterns | `docs/CODE_PATTERNS.md` |
| Hytale API gotchas | `docs/HYTALE_API.md` |
| **Hytale Custom UI** | `docs/hytale-custom-ui/` — **consult BEFORE writing .ui files** |
| Architecture | `docs/ARCHITECTURE.md` |
| Database schema | `docs/DATABASE.md` |
| grepai reference | `docs/GREPAI.md` — primary code search tool, falls back to Grep/Glob |
| Economy balance | `docs/Ascend/ECONOMY_BALANCE.md` |
| Feature history | `CHANGELOG.md` |
| Module READMEs | `docs/<Module>/README.md` (e.g. `docs/Ascend/README.md`) |

## Language

- The user's native language is French — conversation in French is fine.
- **All written files must be in English**: code, comments, documentation, plans, commit messages, changelogs, UI text. No exceptions.
