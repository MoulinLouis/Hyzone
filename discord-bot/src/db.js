const mysql = require('mysql2/promise');

let pool = null;

function getPool() {
  if (!pool) {
    pool = mysql.createPool({
      host: process.env.DB_HOST || 'localhost',
      port: parseInt(process.env.DB_PORT || '3306', 10),
      user: process.env.DB_USER || 'root',
      password: process.env.DB_PASSWORD || '',
      database: process.env.DB_NAME || 'hyvexa',
      waitForConnections: true,
      connectionLimit: 5,
      queueLimit: 0,
    });
  }
  return pool;
}

/**
 * Look up a non-expired link code.
 * Returns { code, player_uuid, expires_at } or null.
 */
async function findValidCode(code) {
  const [rows] = await getPool().execute(
    'SELECT code, player_uuid, expires_at FROM discord_link_codes WHERE code = ? AND expires_at > NOW()',
    [code]
  );
  return rows.length > 0 ? rows[0] : null;
}

/**
 * Check if a Discord user is already linked to any game account.
 * Returns the player_uuid or null.
 */
async function findLinkByDiscordId(discordId) {
  const [rows] = await getPool().execute(
    'SELECT player_uuid FROM discord_links WHERE discord_id = ?',
    [discordId]
  );
  return rows.length > 0 ? rows[0].player_uuid : null;
}

/**
 * Check if a game account is already linked to any Discord user.
 * Returns the discord_id or null.
 */
async function findLinkByPlayerUuid(playerUuid) {
  const [rows] = await getPool().execute(
    'SELECT discord_id FROM discord_links WHERE player_uuid = ?',
    [playerUuid]
  );
  return rows.length > 0 ? rows[0].discord_id : null;
}

/**
 * Create a permanent link between a game account and Discord user.
 * Deletes the used code afterwards.
 */
async function createLink(playerUuid, discordId) {
  const conn = await getPool().getConnection();
  try {
    await conn.beginTransaction();
    await conn.execute(
      'INSERT INTO discord_links (player_uuid, discord_id, gems_rewarded) VALUES (?, ?, FALSE)',
      [playerUuid, discordId]
    );
    await conn.execute(
      'DELETE FROM discord_link_codes WHERE player_uuid = ?',
      [playerUuid]
    );
    await conn.commit();
  } catch (err) {
    await conn.rollback();
    throw err;
  } finally {
    conn.release();
  }
}

/**
 * Find all linked players whose current_rank differs from last_synced_rank.
 * Returns an array of { player_uuid, discord_id, current_rank, last_synced_rank }.
 */
async function getDesyncedRanks() {
  const [rows] = await getPool().execute(
    `SELECT player_uuid, discord_id, current_rank, last_synced_rank
     FROM discord_links
     WHERE current_rank IS NOT NULL
       AND (last_synced_rank IS NULL OR last_synced_rank != current_rank)`
  );
  return rows;
}

/**
 * Mark a player's rank as synced after the Discord role has been updated.
 */
async function markRankSynced(playerUuid, rank) {
  await getPool().execute(
    'UPDATE discord_links SET last_synced_rank = ? WHERE player_uuid = ?',
    [rank, playerUuid]
  );
}

/**
 * Gracefully close the connection pool.
 */
async function shutdown() {
  if (pool) {
    await pool.end();
    pool = null;
  }
}

module.exports = {
  findValidCode,
  findLinkByDiscordId,
  findLinkByPlayerUuid,
  createLink,
  getDesyncedRanks,
  markRankSynced,
  shutdown,
};
