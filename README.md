# Hyvexa

A multi-module Hytale server plugin suite for a parkour minigame server. Players join a hub and choose between game modes: classic Parkour, Parkour Ascend (idle/incremental progression), Purge (zombie wave survival), and Run or Fall (platforming minigame).

## Modules

| Module | Purpose |
|--------|---------|
| `hyvexa-core` | Shared database, utilities, mode state, and common APIs |
| `hyvexa-parkour` | Classic parkour gameplay with maps, leaderboards, and ranks |
| `hyvexa-parkour-ascend` | Idle parkour mode with prestige tiers, skill trees, and economy |
| `hyvexa-hub` | Hub world with mode selection routing and UI |
| `hyvexa-purge` | Zombie wave survival PvE mode with classes and weapon progression |
| `hyvexa-runorfall` | Last-player-standing minigame where platforms break beneath players |
| `hyvexa-wardrobe` | Wardrobe cosmetics integration (`/wardrobe` visibility + `/shop` bridge) |
| `hyvexa-votifier` | Standalone vote notification receiver (Votifier protocol) |
| `hyvexa-launch` | Launch-only IntelliJ classpath module for `com.hypixel.hytale.Main` |

**Services:**

| Service | Purpose |
|---------|---------|
| `discord-bot` | Node.js Discord bot for account linking and vexa rewards |

## Features

### Hub
- Mode selection menu via hotbar item or `/menu`
- Seamless routing between Hub, Parkour, and Ascend worlds

### Parkour
- **Map Selection** - Category-first UI (`/pk`) with Easy/Medium/Hard/Insane difficulties
- **Leaderboards** - Global and per-map best times with tied-rank support
- **Progression** - Rank system (Bronze to Grandmaster) based on map completions
- **1v1 Duels** - Matchmaking queue with duel stats, category filters, and opponent visibility toggle
- **Practice Mode** - Per-run checkpoints for practicing difficult sections
- **Fall Respawn** - Automatic respawn safety is active again during runs
- **Run HUD** - Live timer with tick-precise checkpoint splits and delta display
- **Ghost Replay** - Per-map personal best ghost recordings for runner playback
- **Admin Tools** - Map/player management, settings, playtime tracking, population history, and expanded Map Admin UI space
- **VIP/Founder Ranks** - Purchasable ranks with chat tags, nameplates, and speed multipliers
- **New Player Onboarding** - 3-screen tutorial, smart map recommendations, practice mode hints

### Parkour Ascend
- **Idle Economy** - Runners complete maps automatically, earning Vexa (currency) and multipliers
- **Runner Evolution** - 6-star evolution system with visual NPC progression (Kweebec models)
- **Three Prestige Tiers**:
  - **Elevation** - Convert accumulated coins into a global multiplier
  - **Summit** - Permanent category upgrades (Coin Flow, Runner Speed, Manual Mastery)
  - **Ascension** - Full reset for skill tree points and deeper progression
- **Skill Tree** - 8 unlockable nodes across branching paths with OR-logic prerequisites
- **Achievements** - 30 achievements across 6 categories with hidden/secret achievements
- **Ascension Challenges** - Timed challenges with malus effects for bonus summit XP
- **Passive Earnings** - Runners generate coins at 25% rate while offline (up to 24h)
- **Per-Map Leaderboard** - Best times per map with tabs, search, and pagination
- **Toast Notifications** - HUD-based notifications for upgrades, evolutions, and economy events
- **Cinematic System** - Camera, particles, and sounds for ascension events
- **Mine Subsystem** - Mining progression area within Ascend where players mine blocks, collect crystals, upgrade tools, and hire robot miners for passive income

### Purge
- **Zombie Wave Survival** - PvE mode where players fight waves of zombies with class-based abilities and weapon XP progression
- **Daily Missions** - Rotating mission objectives for bonus rewards

### Run or Fall
- **Last Player Standing** - Players stand on platforms that break when stepped on; last player alive wins. Includes blink ability, stats, and leaderboards.

## Quick Start

### Build
```bash
./gradlew build
# Windows: gradlew.bat build
```
Produces shaded plugin JARs per module that bundle runtime dependencies.

### Deploy
```bash
./gradlew stagePlugins   # Copy JARs to run/mods
./gradlew collectPlugins # Copy JARs to build/libs
```

### Run
Use the `HytaleServerLaunch` IntelliJ run config (generated from `hyvexa-launch`) or set your existing `HytaleServer` config to module `hyvexa-launch.main`; both must launch from the `run/` directory.

The Hytale server JAR is expected at:
```
%USERPROFILE%/AppData/Roaming/Hytale/install/<patchline>/package/game/latest/Server/HytaleServer.jar
```
Where `<patchline>` is configured in `gradle.properties`.

## Project Layout

```
hyvexa-core/                    # Shared utilities, DB, mode state
hyvexa-parkour/                 # Classic parkour gameplay
hyvexa-parkour-ascend/          # Ascend idle mode (includes Mine subsystem)
hyvexa-hub/                     # Hub routing + UI
hyvexa-purge/                   # Zombie wave survival PvE
hyvexa-runorfall/               # Platforming minigame
hyvexa-wardrobe/                # Cosmetics shop
hyvexa-votifier/                # Vote receiver plugin
hyvexa-launch/                  # IntelliJ launch classpath module
discord-bot/                    # Node.js Discord bot for account linking
docs/                           # All documentation and module landing pages
run/                            # Working directory and runtime config

hyvexa-*/src/main/java/         # Java sources
hyvexa-*/src/main/resources/
  manifest.json                 # Plugin manifest
  Common/UI/Custom/Pages/       # UI definition files
```

## Configuration

- All runtime data is stored in MySQL and loaded into memory on startup
- Working directory is `run/`, runtime config lives in `mods/Parkour/`
- DB config: `mods/Parkour/database.json` (gitignored)
- Ascend runtime flags: `mods/Parkour/ascend.properties`

## Documentation

### Module Guides

| Module | Guide |
|--------|-------|
| Core | [docs/Core/README.md](docs/Core/README.md) |
| Parkour | [docs/Parkour/README.md](docs/Parkour/README.md) |
| Ascend | [docs/Ascend/README.md](docs/Ascend/README.md) |
| Hub | [docs/Hub/README.md](docs/Hub/README.md) |
| Purge | [docs/Purge/README.md](docs/Purge/README.md) |
| Run or Fall | [docs/RunOrFall/README.md](docs/RunOrFall/README.md) |
| Wardrobe | [docs/Wardrobe/README.md](docs/Wardrobe/README.md) |
| Votifier | [docs/Votifier/README.md](docs/Votifier/README.md) |
| Discord Bot | [docs/DiscordBot/README.md](docs/DiscordBot/README.md) |

### Technical References

| Topic | File |
|-------|------|
| Architecture & threading | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Database schema | [docs/DATABASE.md](docs/DATABASE.md) |
| Code patterns | [docs/CODE_PATTERNS.md](docs/CODE_PATTERNS.md) |
| Hytale API notes | [docs/HYTALE_API.md](docs/HYTALE_API.md) |
| Game economy balance | [docs/Ascend/ECONOMY_BALANCE.md](docs/Ascend/ECONOMY_BALANCE.md) |
| Changelog | [CHANGELOG.md](CHANGELOG.md) |

For AI agent instructions, see [CLAUDE.md](CLAUDE.md).

## License

Internal project. Add a license before distributing.
