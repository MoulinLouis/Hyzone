# Votifier Module

Quick reference for `hyvexa-votifier`.

## Scope
- Receives vote notifications from voting websites and fires `VoteEvent` for reward processing.
- Supports two protocols: V1 (RSA-encrypted HTTP) and V2 (HMAC-SHA256 signed socket/JSON).
- Provides in-game `/vote` command (shows voting site links) and `/testvote` command (admin testing).
- Includes vote reminder service, configurable reward commands, broadcast announcements, and update checker.

Entry point: `hyvexa-votifier/src/main/java/org/hyvote/plugins/votifier/HytaleVotifierPlugin.java`

Package: `org.hyvote.plugins.votifier` (separate from the `io.hyvexa` namespace used by other modules).

## Startup and Runtime

`HytaleVotifierPlugin.setup()`:
1. Loads config from `mods/HytaleVotifier/config.json` (creates defaults if missing, merges new fields into existing configs).
2. Initializes RSA key pair (`RSAKeyManager`) -- generates 2048-bit keys on first run, loads existing keys on subsequent runs.
3. Initializes HTTP server for V1 protocol (uses Nitrado WebServer plugin if available, falls back to built-in `FallbackHttpServer`).
4. Initializes V2 socket server (`VotifierSocketServer`) if enabled in config.
5. Initializes vote reminder service (tracks last vote timestamps, sends reminders on join).
6. Registers `/vote` and `/testvote` commands.
7. Registers `PlayerReadyEvent` listener for vote reminders and update notifications.
8. Checks for plugin updates asynchronously.

### Vote Processing Flow
1. Vote payload arrives via HTTP (V1) or socket (V2).
2. `VoteProcessor.processPayload()` detects protocol, decrypts/validates, and parses into a `Vote` record.
3. `VoteProcessor.dispatchVote()`:
   - Fires `VoteEvent` on Hytale's event bus.
   - Records vote in reminder service.
   - Displays toast notification to the player.
   - Broadcasts vote announcement to all players.
   - Executes configurable reward commands.

## Integration with Hyvexa

Votifier is a standalone plugin. It integrates with the Hyvexa ecosystem via:
- **VoteEvent listener** (in `hyvexa-parkour`'s `HyvexaPlugin.java`) -- the parkour module registers a listener for `VoteEvent` at startup, which records the vote and awards rewards. This is where incoming Votifier votes are actually processed.
- **VoteStore** (in `hyvexa-core`) -- records votes and maintains leaderboard counts.
- **VoteManager** (in `hyvexa-core`) -- polls an external vote API and awards feathers. This is a separate flow from Votifier's direct webhook/socket reception.

The two systems are complementary: Votifier handles incoming webhooks from vote sites, while VoteManager actively polls for unclaimed votes. Note that the VoteEvent listener lives in `hyvexa-parkour`, not `hyvexa-core`.

## Commands

| Command | Description | Access |
|---------|-------------|--------|
| `/vote` | Displays voting site links (configurable) | All players |
| `/testvote <username> [service]` | Fires a test VoteEvent for debugging | `votifier.admin.testvote` permission |

## Configuration

All config lives in `mods/HytaleVotifier/config.json`:
- `debug` -- enable verbose logging.
- `keyPath` -- RSA key directory (relative to plugin data dir).
- `voteMessage` -- toast notification settings.
- `broadcast` -- server-wide announcement settings.
- `rewardCommands` -- commands executed per vote (supports `{player}`, `{service}` placeholders).
- `voteSites` -- per-site HMAC tokens for V2 protocol.
- `socketServer` -- V2 socket server settings (enabled, port).
- `internalHttpServer` -- fallback HTTP server settings (enabled, port).
- `protocols` -- enable/disable V1 and V2.
- `voteCommand` -- customize `/vote` output (header, footer, site list template).
- `voteReminder` -- join reminder settings (enabled, delay, expiry interval, storage type).

## Storage

The vote reminder service uses its own storage backend (separate from the main MySQL database):
- `VoteStorage` interface with implementations: `InMemoryVoteStorage`, `SQLiteVoteStorage`.
- Storage type is configured via `voteReminder.storage` in config.
- Tracks last vote timestamps per player for reminder logic.

## Key Classes

| Class | Purpose |
|-------|---------|
| `HytaleVotifierPlugin` | Plugin lifecycle and initialization |
| `VoteProcessor` | Protocol detection, parsing, event dispatch |
| `VotifierSocketServer` | V2 socket listener |
| `FallbackHttpServer` | Built-in HTTP server for V1 |
| `NitradoWebServerBridge` | Nitrado WebServer integration |
| `RSAKeyManager` | RSA key generation and persistence |
| `VoteReminderService` | Scheduled reminders for players who haven't voted |
| `VoteTracker` | Wraps VoteStorage for reminder service |
| `VoteCommand` | `/vote` command |
| `TestVoteCommand` | `/testvote` admin command |
| `Vote` | Vote data record (service, username, address, timestamp) |
| `VoteEvent` | Hytale event fired when a vote is received |

## Key Source Paths
- `hyvexa-votifier/src/main/java/org/hyvote/plugins/votifier/` -- all source code
- `hyvexa-votifier/src/main/java/org/hyvote/plugins/votifier/http/` -- HTTP/V1 processing
- `hyvexa-votifier/src/main/java/org/hyvote/plugins/votifier/socket/` -- V2 socket server
- `hyvexa-votifier/src/main/java/org/hyvote/plugins/votifier/vote/` -- vote parsing and protocol detection
- `hyvexa-votifier/src/main/java/org/hyvote/plugins/votifier/storage/` -- vote storage backends

## Owned Database Tables

None in the shared MySQL database. The Votifier module uses its own local SQLite storage (or in-memory) for vote tracking, configured independently from the main Hyvexa database.

## Related Docs
- Architecture overview: `docs/ARCHITECTURE.md`
- Core vote system (VoteStore, VoteManager): `docs/Core/README.md`
