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
| `hyvexa-launch` | IntelliJ launch classpath module | `com.hypixel.hytale.Main` |
| `discord-bot` | Discord bot (Node.js) for account linking | `src/index.js` |

## Key Directories

| Path | Content |
|------|---------|
| `hyvexa-*/src/main/java/io/hyvexa/` | Java sources |
| `hyvexa-*/src/main/resources/Common/UI/Custom/Pages/` | UI definitions (parkour, ascend, hub, purge, runorfall, wardrobe) |
| `hyvexa-*/src/main/resources/Common/UI/Custom/Textures/` | UI texture assets |
| `hyvexa-*/src/main/resources/Server/Item/` | Item definitions and interactions |
| `hyvexa-parkour/src/main/resources/Common/Music/` | Music files |
| `hyvexa-parkour/src/main/resources/Common/Sounds/` | Sound effects |
| `run/mods/Parkour/` | Runtime config (database.json) |
| `docs/` | All documentation files |
| `discord-bot/` | Discord bot: account linking + vexa reward |

## Workflow Reminders

1. **Update CHANGELOG.md** for significant changes only (new features, balance changes, bug fixes) - keep entries brief (1-2 lines), no implementation details
2. **Update docs/Ascend/ECONOMY_BALANCE.md** when modifying game economy (costs, rewards, multipliers, formulas)
3. **Follow existing patterns** - check similar files before implementing
4. **Reuse existing Managers** - Many Manager classes (45+) exist across modules. Check for existing ones before creating new ones (e.g., `HyvexaPlugin.getInstance().getHudManager()`, `ParkourAscendPlugin.getInstance().getRobotManager()`)
5. **No build runs** - owner handles `./gradlew build`
6. **Run tests selectively** - only run tests when explicitly asked OR after large/significant changes (new features, multi-file refactors). Do NOT run tests after every small task. Only pure-logic classes with zero Hytale imports are testable — classes importing Hytale types (e.g. `Message`, `HytaleLogger`, `EntityRef`) cannot be tested without `HytaleServer.jar` on the test classpath (not yet configured).
7. **UI paths** - Code uses `"Pages/X.ui"`, files go in `Common/UI/Custom/Pages/`
8. **Check ref validity** - `if (ref == null || !ref.isValid()) return;`
9. **World thread for entity ops** - use `world.execute(() -> ...)` or `CompletableFuture.runAsync(..., world)`
10. **Entity removal** - use `store.removeEntity(ref, RemoveReason.REMOVE)`
11. **Singleton components** - check for `INSTANCE` or `get()` (e.g., `Invulnerable.INSTANCE`)
12. **Track online players** - only spawn per-player entities for connected players
13. **Disable NPC AI** - use `Frozen` component to prevent autonomous movement
14. **Avoid Unicode in chat messages** - Hytale client displays many Unicode characters as `?`. Use ASCII alternatives: `->` instead of `→`, `x` instead of `×`, `-` instead of `–`/`—`
15. **No auto-memory for project knowledge** - Multiple agents work on this repo. Store discoveries in `docs/` not in Claude's auto-memory, so all agents benefit.

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

Node.js bot for Discord-Minecraft account linking. Shares the same MySQL database as the plugin.

- **Flow**: Player runs `/link` in-game -> gets a code (e.g. `X7K-9M2`) -> enters `/link X7K-9M2` on Discord -> bot validates & creates permanent link -> next login, plugin awards 100 vexa
- **Config**: `discord-bot/.env` (gitignored) - DB credentials, bot token, guild ID. See `.env.example`
- **Run locally**: `cd discord-bot && npm install && npm run start`
- **Production**: Hosted on game server panel (Helloserv) or separate VPS with `pm2`
- **Plugin side**: `DiscordLinkStore` singleton in `hyvexa-core` handles code generation, link checking, and vexa rewards. Initialized by all 4 gameplay modules (parkour, ascend, hub, purge).
- **DB tables**: `discord_link_codes` (temp codes, 5min expiry), `discord_links` (permanent links + `vexa_rewarded` flag)

## Reference Files (in docs/)

| For... | Read... |
|--------|---------|
| Code patterns | `docs/CODE_PATTERNS.md` |
| Hytale API gotchas | `docs/HYTALE_API.md` |
| **Hytale Custom UI docs** | `docs/hytale-custom-ui/` (official reference) |
| System architecture | `docs/ARCHITECTURE.md` |
| Database schema | `docs/DATABASE.md` |
| grepai reference | `docs/GREPAI.md` |
| Tech debt & refactoring | `docs/TECH_DEBT.md` |
| Game balancing | `docs/Ascend/ECONOMY_BALANCE.md` |
| Help & tutorial system | `docs/Ascend/TUTORIAL_FLOW.md` |
| Feature history | `CHANGELOG.md` |
| Core module | `docs/Core/README.md` |
| Hub module | `docs/Hub/README.md` |
| Ascend module | `docs/Ascend/README.md` |
| Purge module | `docs/Purge/README.md` |
| Parkour module | `docs/Parkour/README.md` |
| RunOrFall module | `docs/RunOrFall/README.md` |
| Wardrobe module | `docs/Wardrobe/README.md` |
| Votifier module | `docs/Votifier/README.md` |
| Discord bot | `docs/DiscordBot/README.md` |

### Hytale Custom UI Reference (`docs/hytale-custom-ui/`)
Official Hytale UI documentation. **Consult this BEFORE writing any .ui file.**
- `index.mdx` / `common-styling.mdx` / `layout.mdx` / `markup.mdx` — Core concepts
- `type-documentation/elements/` — All UI elements (group, label, textbutton, panel, etc.)
- `type-documentation/property-types/` — All property types (anchor, buttonstyle, labelstyle, etc.)
- `type-documentation/enums/` — All enums (layoutmode, labelalignment, etc.)


## grepai - Semantic Code Search

Use `grepai search` as the primary tool for code exploration. Use `grepai trace` for call graph analysis. grepai indexes only plugin code, NOT HytaleServer.jar — for Hytale API, read `docs/HYTALE_API.md`. Falls back to Grep/Glob if grepai is unavailable.

See [docs/GREPAI.md](docs/GREPAI.md) for full reference.


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

### 4. Course-Correct Mid-Flight

**If your approach isn't working, stop. Don't push through.**

- If a fix keeps cascading into more changes, pause and reconsider the approach.
- If you're unsure your changes actually work, say so — don't claim completion.
- Prove it works before marking done: run tests, check output, show evidence.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
