require('dotenv').config();

const { Client, GatewayIntentBits, SlashCommandBuilder, REST, Routes } = require('discord.js');
const db = require('./db');

const TOKEN = process.env.DISCORD_TOKEN;
const GUILD_ID = process.env.GUILD_ID;
const RANK_SYNC_BATCH_SIZE = Math.max(1, parseInt(process.env.RANK_SYNC_BATCH_SIZE || '50', 10) || 50);
const RANK_SYNC_INTERVAL_MS = parseInt(process.env.RANK_SYNC_INTERVAL_MS || '30000', 10) || 30_000;

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

async function syncRankRoles() {
  if (!GUILD_ID || RANK_ROLES.size === 0) return;

  let guild;
  try {
    guild = await client.guilds.fetch(GUILD_ID);
  } catch (err) {
    console.error('Rank sync: failed to fetch guild:', err);
    return;
  }

  let rows;
  try {
    rows = await db.getDesyncedRanks(RANK_SYNC_BATCH_SIZE);
  } catch (err) {
    console.error('Rank sync: DB query failed:', err);
    return;
  }

  for (const row of rows) {
    try {
      const member = await guild.members.fetch(row.discord_id);

      if (row.last_synced_rank && RANK_ROLES.has(row.last_synced_rank)) {
        const oldRoleId = RANK_ROLES.get(row.last_synced_rank);
        if (member.roles.cache.has(oldRoleId)) {
          await member.roles.remove(oldRoleId);
        }
      }

      const newRoleId = RANK_ROLES.get(row.current_rank);
      if (newRoleId) {
        await member.roles.add(newRoleId);
      }

      await db.markRankSynced(row.player_uuid, row.current_rank);
    } catch (err) {
      console.warn(`Rank sync: failed for ${row.discord_id} (${row.player_uuid}):`, err);
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

  if (GUILD_ID && RANK_ROLES.size > 0) {
    setInterval(() => syncRankRoles().catch(err => console.error('Rank sync failed:', err)), RANK_SYNC_INTERVAL_MS);
    console.log(`Rank role sync enabled (${RANK_ROLES.size} roles configured, polling every ${RANK_SYNC_INTERVAL_MS / 1000}s)`);
  }
});

client.on('interactionCreate', async (interaction) => {
  if (!interaction.isChatInputCommand() || interaction.commandName !== 'link') {
    return;
  }

  await interaction.deferReply({ ephemeral: true });

  try {
    const rawCode = interaction.options.getString('code', true);
    const code = rawCode.toUpperCase().replace(/[-\s]/g, '');

    if (code.length !== 6 || !/^[A-Z0-9]{6}$/.test(code)) {
      await interaction.editReply('Invalid code format. Use the 6-character code from `/link` in-game (e.g. X7K-9M2).');
      return;
    }

    const result = await db.claimCodeAndCreateLink(code, interaction.user.id);
    if (!result.ok) {
      await interaction.editReply(result.message);
      return;
    }

    await interaction.editReply(
      'Account linked successfully! You will receive **100 vexa** next time you log in.'
    );
  } catch (err) {
    console.error('Error handling /link:', err);
    await interaction.editReply('Something went wrong. Please try again later.').catch((editErr) => {
      console.debug('editReply failed:', editErr);
    });
  }
});

process.on('SIGINT', async () => {
  console.log('Shutting down...');
  await db.shutdown();
  client.destroy();
  process.exit(0);
});

client.login(TOKEN);
