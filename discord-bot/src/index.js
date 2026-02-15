require('dotenv').config();

const { Client, GatewayIntentBits, SlashCommandBuilder, REST, Routes } = require('discord.js');
const db = require('./db');

const TOKEN = process.env.DISCORD_TOKEN;
const GUILD_ID = process.env.GUILD_ID;
const LINKED_ROLE_ID = process.env.LINKED_ROLE_ID || '';

if (!TOKEN) {
  console.error('DISCORD_TOKEN is required in .env');
  process.exit(1);
}

const client = new Client({ intents: [GatewayIntentBits.Guilds] });

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

client.once('ready', async () => {
  console.log(`Logged in as ${client.user.tag}`);
  try {
    await registerCommands();
  } catch (err) {
    console.error('Failed to register commands:', err);
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

    // Assign the "Linked" role if configured
    if (LINKED_ROLE_ID && interaction.guild) {
      try {
        const member = await interaction.guild.members.fetch(discordId);
        await member.roles.add(LINKED_ROLE_ID);
      } catch (roleErr) {
        console.warn('Failed to assign linked role:', roleErr.message);
      }
    }

    await interaction.editReply(
      'Account linked successfully! You\'ll receive **100 gems** next time you log in to the server.'
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
