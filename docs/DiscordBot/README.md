# Discord Bot

Quick reference for `discord-bot/`.

## Scope
- Node.js Discord bot for Hytale-Discord account linking and automatic rank role synchronization.
- Shares the same MySQL database as the Java plugin modules.
- Not a Gradle module -- runs as a standalone Node.js service.

Entry point: `discord-bot/src/index.js`

## Account Linking Flow

1. Player runs `/link` in-game (handled by plugin-side code in any module).
2. `DiscordLinkStore` (in `hyvexa-core`) generates a 6-character code (format `XXX-XXX`, e.g. `X7K-9M2`) and inserts it into `discord_link_codes` with a 5-minute expiry.
3. Player enters `/link X7K-9M2` on Discord.
4. Bot validates the code atomically (`claimCodeAndCreateLink`):
   - Locks the code row (`FOR UPDATE`).
   - Checks that neither the Discord account nor the game account is already linked.
   - Deletes the code and inserts a permanent record into `discord_links`.
5. Bot responds with a success message confirming 100 vexa will be awarded on next login.
6. On next game login, `DiscordLinkStore.checkAndRewardVexaOnLoginAsync()` detects the unrewarded link, atomically sets `vexa_rewarded = TRUE` and grants 100 vexa, then sends a congratulation message.

## Rank Role Synchronization

The bot polls `discord_links` for rows where `current_rank != last_synced_rank`:
1. Runs every 30 seconds (configurable via `RANK_SYNC_INTERVAL_MS`).
2. Fetches up to 50 desynced rows per batch (configurable via `RANK_SYNC_BATCH_SIZE`).
3. For each row:
   - Removes the old rank's Discord role (if any).
   - Adds the new rank's Discord role.
   - Updates `last_synced_rank` in the database.

Rank roles are mapped via environment variables (`RANK_ROLE_IRON`, `RANK_ROLE_GOLD`, etc.).

The plugin side writes `current_rank` to `discord_links` via `DiscordLinkStore.updateRankIfLinkedAsync()` whenever a player's parkour rank changes.

## Commands

| Command | Platform | Description |
|---------|----------|-------------|
| `/link <code>` | Discord | Links a Discord account to a Hytale account using a code from in-game |

## Environment Configuration

Config via `discord-bot/.env` (see `.env.example`):

| Variable | Description |
|----------|-------------|
| `DISCORD_TOKEN` | Bot token (required) |
| `GUILD_ID` | Discord server ID (optional; if set, commands are guild-specific) |
| `DB_HOST` | MySQL host (default: `localhost`) |
| `DB_PORT` | MySQL port (default: `3306`) |
| `DB_USER` | MySQL user (default: `root`) |
| `DB_PASSWORD` | MySQL password |
| `DB_NAME` | MySQL database name (default: `hyvexa`) |
| `RANK_ROLE_UNRANKED` .. `RANK_ROLE_VEXAGOD` | Discord role IDs for each parkour rank |
| `RANK_SYNC_BATCH_SIZE` | Max rows per sync cycle (default: `50`) |
| `RANK_SYNC_INTERVAL_MS` | Sync polling interval in ms (default: `30000`) |

## Dependencies

From `package.json`:
- `discord.js` ^14.14.1
- `dotenv` ^16.3.1
- `mysql2` ^3.6.5

## Running

```bash
cd discord-bot
npm install
npm run start          # foreground
# or with pm2:
pm2 start src/index.js --name hyvexa-discord-bot
```

## Database Tables Used

The bot reads and writes these tables (owned by `DiscordLinkStore` in `hyvexa-core`):

| Table | Usage |
|-------|-------|
| `discord_link_codes` | Validates and deletes link codes during the `/link` flow |
| `discord_links` | Creates permanent links, reads/writes rank sync columns |

## Key Files
- `discord-bot/src/index.js` -- bot startup, command handling, rank sync loop
- `discord-bot/src/db.js` -- all MySQL queries (code claiming, link lookup, rank sync)
- `discord-bot/package.json` -- dependencies and scripts
- `discord-bot/.env.example` -- environment variable template

## Plugin-Side Integration

- `DiscordLinkStore` (`hyvexa-core/src/main/java/io/hyvexa/core/discord/DiscordLinkStore.java`) -- code generation, link checking, vexa reward, rank updates. Initialized by all 4 gameplay modules (Parkour, Ascend, Hub, Purge).
- The `/link` in-game command (registered in `hyvexa-parkour`) calls `DiscordLinkStore.generateCode()`.
- The `/unlink` admin command calls `DiscordLinkStore.unlinkPlayer()`.
- On player login, each module calls `checkAndRewardVexaOnLoginAsync()` to deliver pending rewards.
- On rank change, `updateRankIfLinkedAsync()` writes the new rank to `discord_links.current_rank` for the bot to sync.

## Related Docs
- Core module (DiscordLinkStore details): `docs/Core/README.md`
- Database schema: `docs/DATABASE.md`
- Architecture overview: `docs/ARCHITECTURE.md`
