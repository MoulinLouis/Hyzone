require('dotenv').config();

const { Client, GatewayIntentBits, SlashCommandBuilder, REST, Routes } = require('discord.js');
const db = require('./db');

const TOKEN = process.env.DISCORD_TOKEN;
const GUILD_ID = process.env.GUILD_ID;

if (!TOKEN) {
  console.error('DISCORD_TOKEN is required in .env');
  process.exit(1);
}

const client = new Client({ intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMembers] });

// Rank role mapping: rank name -> Discord role ID
const RANK_ROLES = new Map();
const RANK_NAMES = [
  'Unranked', 'Iron', 'Bronze', 'Silver', 'Gold', 'Platinum',
  'Emerald', 'Diamond', 'Master', 'Grandmaster', 'Challenger', 'VexaGod',
];
for (const name of RANK_NAMES) {
  const envKey = `RANK_ROLE_${name.toUpperCase()}`;
  const roleId = process.env[envKey];
  if (roleId) {
    RANK_ROLES.set(name, roleId);
  }
}


// Register the /link slash command
async function registerCommands() {
  const command = new SlashCommandBuilder()
    .setName('link')
    .setDescription('Link your Hytale account to Discord')
    .addStringOption((opt) =>
      opt.setName('code').setDescription('The link code from /link in-game (e.g. X7K-9M2)').setRequired(true)
    );

  const rest = new REST({ version: '10' }).setToken(TOKEN);

  if (GUILD_ID) {
    await rest.put(Routes.applicationGuildCommands(client.user.id, GUILD_ID), {
      body: [command.toJSON()],
    });
    console.log(`Registered /link command for guild ${GUILD_ID}`);
  } else {
    await rest.put(Routes.applicationCommands(client.user.id), {
      body: [command.toJSON()],
    });
    console.log('Registered /link command globally');
  }
}

// Poll for rank changes and sync Discord roles
async function syncRankRoles() {
  if (!GUILD_ID || RANK_ROLES.size === 0) return;

  let guild;
  try {
    guild = await client.guilds.fetch(GUILD_ID);
  } catch (err) {
    console.error('Rank sync: failed to fetch guild:', err.message);
    return;
  }

  let rows;
  try {
    rows = await db.getDesyncedRanks();
  } catch (err) {
    console.error('Rank sync: DB query failed:', err.message);
    return;
  }

  for (const row of rows) {
    try {
      const member = await guild.members.fetch(row.discord_id);

      // Remove old rank role
      if (row.last_synced_rank && RANK_ROLES.has(row.last_synced_rank)) {
        const oldRoleId = RANK_ROLES.get(row.last_synced_rank);
        if (member.roles.cache.has(oldRoleId)) {
          await member.roles.remove(oldRoleId);
        }
      }

      // Add new rank role
      const newRoleId = RANK_ROLES.get(row.current_rank);
      if (newRoleId) {
        await member.roles.add(newRoleId);
      }

      await db.markRankSynced(row.player_uuid, row.current_rank);
    } catch (err) {
      console.warn(`Rank sync: failed for ${row.discord_id} (${row.player_uuid}):`, err.message);
    }
  }
}

client.once('ready', async () => {
  console.log(`Logged in as ${client.user.tag}`);
  try {
    await registerCommands();
  } catch (err) {
    console.error('Failed to register commands:', err);
  }

  // Start rank role sync polling (every 30 seconds)
  if (GUILD_ID && RANK_ROLES.size > 0) {
    setInterval(syncRankRoles, 30_000);
    console.log(`Rank role sync enabled (${RANK_ROLES.size} roles configured, polling every 30s)`);
  }
});

client.on('interactionCreate', async (interaction) => {
  if (!interaction.isChatInputCommand() || interaction.commandName !== 'link') {
    return;
  }

  await interaction.deferReply({ ephemeral: true });

  try {
    const rawCode = interaction.options.getString('code', true);
    // Normalize: uppercase, strip spaces/dashes
    const code = rawCode.toUpperCase().replace(/[-\s]/g, '');

    if (code.length !== 6) {
      await interaction.editReply('Invalid code format. Use the 6-character code from `/link` in-game (e.g. X7K-9M2).');
      return;
    }

    // Look up the code
    const codeRow = await db.findValidCode(code);
    if (!codeRow) {
      await interaction.editReply('Invalid or expired code. Generate a new one with `/link` in-game.');
      return;
    }

    const playerUuid = codeRow.player_uuid;
    const discordId = interaction.user.id;

    // Check if this Discord account is already linked
    const existingByDiscord = await db.findLinkByDiscordId(discordId);
    if (existingByDiscord) {
      await interaction.editReply('Your Discord account is already linked to a game account.');
      return;
    }

    // Check if this game account is already linked
    const existingByPlayer = await db.findLinkByPlayerUuid(playerUuid);
    if (existingByPlayer) {
      await interaction.editReply('This game account is already linked to another Discord account.');
      return;
    }

    // Create the link
    await db.createLink(playerUuid, discordId);

    await interaction.editReply(
      'Account linked successfully! You\'ll receive **100 vexa** next time you log in to the server.'
    );
  } catch (err) {
    console.error('Error handling /link:', err);
    await interaction.editReply('Something went wrong. Please try again later.').catch(() => {});
  }
});

// Graceful shutdown
process.on('SIGINT', async () => {
  console.log('Shutting down...');
  await db.shutdown();
  client.destroy();
  process.exit(0);
});

client.login(TOKEN);
