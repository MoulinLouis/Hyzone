package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Mine system table creation and migrations: mine definitions, zones, layers, players,
 * inventory, upgrades, pickaxe, conveyor, gacha, and block prices/HP.
 */
final class AscendMineSchema {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private AscendMineSchema() {
    }

    static void createAndMigrate(Connection conn, Statement stmt) throws SQLException {
        // Mine system tables
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_definitions (
                id VARCHAR(32) PRIMARY KEY,
                name VARCHAR(64) NOT NULL,
                display_order INT NOT NULL DEFAULT 0,
                unlock_cost_mantissa DOUBLE NOT NULL DEFAULT 0,
                unlock_cost_exp10 INT NOT NULL DEFAULT 0,
                world VARCHAR(64) NOT NULL DEFAULT '',
                spawn_x DOUBLE NOT NULL DEFAULT 0,
                spawn_y DOUBLE NOT NULL DEFAULT 0,
                spawn_z DOUBLE NOT NULL DEFAULT 0,
                spawn_rot_x FLOAT NOT NULL DEFAULT 0,
                spawn_rot_y FLOAT NOT NULL DEFAULT 0,
                spawn_rot_z FLOAT NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB
            """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_zones (
                id VARCHAR(32) NOT NULL,
                mine_id VARCHAR(32) NOT NULL,
                min_x INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL,
                max_x INT NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL,
                block_table_json TEXT NOT NULL DEFAULT '{}',
                regen_threshold DOUBLE NOT NULL DEFAULT 0.8,
                regen_cooldown_seconds INT NOT NULL DEFAULT 45,
                PRIMARY KEY (id),
                FOREIGN KEY (mine_id) REFERENCES mine_definitions(id) ON DELETE CASCADE
            ) ENGINE=InnoDB
            """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_zone_layers (
                id VARCHAR(32) NOT NULL,
                zone_id VARCHAR(32) NOT NULL,
                min_y INT NOT NULL,
                max_y INT NOT NULL,
                block_table_json TEXT NOT NULL DEFAULT '{}',
                PRIMARY KEY (id),
                FOREIGN KEY (zone_id) REFERENCES mine_zones(id) ON DELETE CASCADE
            ) ENGINE=InnoDB
            """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_gate (
                id INT NOT NULL PRIMARY KEY DEFAULT 1,
                min_x DOUBLE NOT NULL DEFAULT 0, min_y DOUBLE NOT NULL DEFAULT 0, min_z DOUBLE NOT NULL DEFAULT 0,
                max_x DOUBLE NOT NULL DEFAULT 0, max_y DOUBLE NOT NULL DEFAULT 0, max_z DOUBLE NOT NULL DEFAULT 0,
                fallback_x DOUBLE NOT NULL DEFAULT 0, fallback_y DOUBLE NOT NULL DEFAULT 0, fallback_z DOUBLE NOT NULL DEFAULT 0,
                fallback_rot_x FLOAT NOT NULL DEFAULT 0, fallback_rot_y FLOAT NOT NULL DEFAULT 0, fallback_rot_z FLOAT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB
            """);

        // Mine player progress
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_players (
                uuid VARCHAR(36) PRIMARY KEY,
                crystals DECIMAL(20,2) NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                FOREIGN KEY (uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
            ) ENGINE=InnoDB
            """);

        // Mine player inventory (virtual block counts)
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_player_inventory (
                player_uuid VARCHAR(36) NOT NULL,
                block_type_id VARCHAR(64) NOT NULL,
                amount INT NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, block_type_id),
                FOREIGN KEY (player_uuid) REFERENCES mine_players(uuid) ON DELETE CASCADE
            ) ENGINE=InnoDB
            """);

        ensureMineUpgradeColumns(conn);
        ensureMineInMineColumn(conn);
        ensurePickaxeTierColumn(conn);
        migrateCrystalsToBigint(conn);
        migrateCrystalsToDecimal(conn);
        ensureBlockHpColumns(conn);
        migrateBlockPricesToSimple(conn);

        // Drop old per-mine price table (not in prod, no migration needed)
        stmt.executeUpdate("DROP TABLE IF EXISTS mine_block_prices");

        // Global block sell prices
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS block_prices (
                block_type_id VARCHAR(64) PRIMARY KEY,
                price BIGINT NOT NULL DEFAULT 1
            ) ENGINE=InnoDB
            """);

        // Global block HP
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS block_hp (
                block_type_id VARCHAR(64) PRIMARY KEY,
                hp INT NOT NULL DEFAULT 1
            ) ENGINE=InnoDB
            """);

        // Per-mine player state (unlocked, completed)
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_player_mines (
                player_uuid VARCHAR(36) NOT NULL,
                mine_id VARCHAR(32) NOT NULL,
                unlocked BOOLEAN NOT NULL DEFAULT FALSE,
                completed_manually BOOLEAN NOT NULL DEFAULT FALSE,
                PRIMARY KEY (player_uuid, mine_id),
                FOREIGN KEY (player_uuid) REFERENCES mine_players(uuid) ON DELETE CASCADE,
                FOREIGN KEY (mine_id) REFERENCES mine_definitions(id) ON DELETE CASCADE
            ) ENGINE=InnoDB
            """);

        // Per-mine miner state (hired miner, speed, stars)
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_player_miners (
                player_uuid VARCHAR(36) NOT NULL,
                mine_id VARCHAR(32) NOT NULL,
                has_miner BOOLEAN NOT NULL DEFAULT FALSE,
                speed_level INT NOT NULL DEFAULT 0,
                stars INT NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, mine_id),
                FOREIGN KEY (player_uuid) REFERENCES mine_players(uuid) ON DELETE CASCADE,
                FOREIGN KEY (mine_id) REFERENCES mine_definitions(id) ON DELETE CASCADE
            ) ENGINE=InnoDB
            """);

        // Mine achievement tracking
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_achievements (
                player_uuid VARCHAR(36) NOT NULL,
                achievement_id VARCHAR(50) NOT NULL,
                completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (player_uuid, achievement_id)
            ) ENGINE=InnoDB
            """);

        // Mine player stats (lifetime counters for achievements)
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_player_stats (
                player_uuid VARCHAR(36) PRIMARY KEY,
                total_blocks_mined BIGINT NOT NULL DEFAULT 0,
                total_crystals_earned BIGINT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB
            """);

        // Miner NPC + block positions per mine (admin-configured)
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_miner_slots (
                mine_id VARCHAR(32) PRIMARY KEY,
                npc_x DOUBLE NOT NULL DEFAULT 0,
                npc_y DOUBLE NOT NULL DEFAULT 0,
                npc_z DOUBLE NOT NULL DEFAULT 0,
                npc_yaw FLOAT NOT NULL DEFAULT 0,
                block_x INT NOT NULL DEFAULT 0,
                block_y INT NOT NULL DEFAULT 0,
                block_z INT NOT NULL DEFAULT 0,
                interval_seconds DOUBLE NOT NULL DEFAULT 5.0,
                FOREIGN KEY (mine_id) REFERENCES mine_definitions(id) ON DELETE CASCADE
            ) ENGINE=InnoDB
            """);

        ensureConveyorColumns(conn);

        // Conveyor waypoints table (per-slot and main line)
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_conveyor_waypoints (
                mine_id VARCHAR(32) NOT NULL,
                slot_index INT NOT NULL,
                waypoint_order INT NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                PRIMARY KEY (mine_id, slot_index, waypoint_order),
                FOREIGN KEY (mine_id) REFERENCES mine_definitions(id) ON DELETE CASCADE
            ) ENGINE=InnoDB
            """);

        // Multi-slot migrations (add slot_index to miner slots and player miners)
        ensureMultiSlotMigration(conn);

        // Conveyor buffer persistence (per-player block counts)
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_player_conveyor_buffer (
                player_uuid VARCHAR(36) NOT NULL,
                block_type_id VARCHAR(64) NOT NULL,
                amount INT NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, block_type_id),
                FOREIGN KEY (player_uuid) REFERENCES mine_players(uuid) ON DELETE CASCADE
            ) ENGINE=InnoDB
            """);

        // --- Egg-based miner gacha system tables ---

        // Player egg inventory
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_player_eggs (
                player_uuid VARCHAR(36) NOT NULL,
                layer_id VARCHAR(64) NOT NULL,
                count INT NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, layer_id)
            ) ENGINE=InnoDB
            """);

        // Player miner collection (v2 — gacha-based)
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_player_miners_v2 (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                layer_id VARCHAR(64) NOT NULL,
                rarity VARCHAR(16) NOT NULL,
                speed_level INT NOT NULL DEFAULT 0,
                INDEX idx_player (player_uuid)
            ) ENGINE=InnoDB
            """);

        // Miner-to-slot assignments
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_player_slot_assignments (
                player_uuid VARCHAR(36) NOT NULL,
                slot_index INT NOT NULL,
                miner_id BIGINT NOT NULL,
                PRIMARY KEY (player_uuid, slot_index)
            ) ENGINE=InnoDB
            """);

        // Per-layer per-rarity block tables (admin-configured or auto-seeded)
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_layer_rarity_blocks (
                layer_id VARCHAR(64) NOT NULL,
                rarity VARCHAR(16) NOT NULL,
                block_table_json TEXT NOT NULL DEFAULT '{}',
                PRIMARY KEY (layer_id, rarity)
            ) ENGINE=InnoDB
            """);

        // Add egg_drop_chance and display_name columns to mine_zone_layers
        ensureLayerGachaColumns(conn);

        // Migrate old miners to v2
        migrateOldMinersToV2(conn);

        // Pickaxe enhancement + recipe system
        ensurePickaxeEnhancementColumn(conn);

        // Pickaxe tier recipes (block requirements for tier upgrades)
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS pickaxe_tier_recipes (
                tier INT NOT NULL,
                block_type_id VARCHAR(64) NOT NULL,
                amount INT NOT NULL DEFAULT 1,
                PRIMARY KEY (tier, block_type_id)
            ) ENGINE=InnoDB
            """);

        // Pickaxe enhancement costs (crystal cost per tier/level)
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS pickaxe_enhance_costs (
                tier INT NOT NULL,
                level INT NOT NULL,
                crystal_cost BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (tier, level)
            ) ENGINE=InnoDB
            """);

        // Miner definitions per layer (admin-configurable names/portraits)
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_layer_miner_defs (
                layer_id VARCHAR(64) NOT NULL,
                rarity VARCHAR(16) NOT NULL,
                display_name VARCHAR(64) NOT NULL,
                portrait_id VARCHAR(32) NOT NULL,
                PRIMARY KEY (layer_id, rarity)
            ) ENGINE=InnoDB
            """);

        ensureManualBlocksMinedColumn(conn);

        // Quest progress tracking
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS mine_quest_progress (
                player_uuid VARCHAR(36) NOT NULL,
                chain_id VARCHAR(32) NOT NULL,
                quest_index INT NOT NULL DEFAULT 0,
                objective_progress BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, chain_id)
            ) ENGINE=InnoDB
            """);
    }

    private static void ensureLayerGachaColumns(Connection conn) {
        AscendDatabaseSetup.ensureColumn(conn, "mine_zone_layers", "egg_drop_chance", "DOUBLE NOT NULL DEFAULT 0.5");
        AscendDatabaseSetup.ensureColumn(conn, "mine_zone_layers", "display_name", "VARCHAR(64) NOT NULL DEFAULT ''");
        AscendDatabaseSetup.ensureColumn(conn, "mine_zone_layers", "egg_item_id", "VARCHAR(64) DEFAULT NULL");
    }

    /**
     * One-time migration: convert old mine_player_miners rows (has_miner=1) into
     * mine_player_miners_v2 entries as COMMON miners, and create slot assignments.
     */
    private static void migrateOldMinersToV2(Connection conn) {
        // Only migrate if v2 table is empty and old table has data
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM mine_player_miners_v2")) {
                if (rs.next() && rs.getInt(1) > 0) return; // already migrated
            }

            // Check if old table has any miners
            int oldCount = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM mine_player_miners WHERE has_miner = 1")) {
                if (rs.next()) oldCount = rs.getInt(1);
            }
            if (oldCount == 0) return;

            // Get first layer ID as fallback origin
            String firstLayerId = "";
            try (ResultSet rs = stmt.executeQuery("SELECT id FROM mine_zone_layers ORDER BY min_y ASC LIMIT 1")) {
                if (rs.next()) firstLayerId = rs.getString("id");
            }

            // Migrate each old miner
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO mine_player_miners_v2 (player_uuid, layer_id, rarity, speed_level) " +
                    "SELECT m.player_uuid, ?, 'COMMON', m.speed_level " +
                    "FROM mine_player_miners m WHERE m.has_miner = 1")) {
                ps.setString(1, firstLayerId);
                ps.executeUpdate();
            }

            // Create slot assignments
            stmt.executeUpdate(
                "INSERT INTO mine_player_slot_assignments (player_uuid, slot_index, miner_id) " +
                "SELECT m.player_uuid, m.slot_index, v2.id " +
                "FROM mine_player_miners m " +
                "JOIN mine_player_miners_v2 v2 ON v2.player_uuid = m.player_uuid " +
                "WHERE m.has_miner = 1");

            LOGGER.atInfo().log("Migrated " + oldCount + " old miners to v2 gacha system");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to migrate old miners to v2: " + e.getMessage());
        }
    }

    private static void ensureMineUpgradeColumns(Connection conn) {
        String[][] columns = {
            {"mining_speed_level", "INT NOT NULL DEFAULT 0"},
            {"bag_capacity_level", "INT NOT NULL DEFAULT 0"},
            {"multi_break_level", "INT NOT NULL DEFAULT 0"},
            {"auto_sell_level", "INT NOT NULL DEFAULT 0"},
            {"upgrade_momentum", "INT NOT NULL DEFAULT 0"},
            {"upgrade_fortune", "INT NOT NULL DEFAULT 0"},
            {"upgrade_jackhammer", "INT NOT NULL DEFAULT 0"},
            {"upgrade_stomp", "INT NOT NULL DEFAULT 0"},
            {"upgrade_blast", "INT NOT NULL DEFAULT 0"},
            {"upgrade_haste", "INT NOT NULL DEFAULT 0"},
            {"upgrade_conveyor_capacity", "INT NOT NULL DEFAULT 0"},
            {"upgrade_cashback", "INT NOT NULL DEFAULT 0"}
        };
        for (String[] col : columns) {
            AscendDatabaseSetup.ensureColumn(conn, "mine_players", col[0], col[1]);
        }
    }

    private static void ensureConveyorColumns(Connection conn) {
        String[][] columns = {
            {"conveyor_end_x", "DOUBLE NOT NULL DEFAULT 0"},
            {"conveyor_end_y", "DOUBLE NOT NULL DEFAULT 0"},
            {"conveyor_end_z", "DOUBLE NOT NULL DEFAULT 0"},
            {"conveyor_speed", "DOUBLE NOT NULL DEFAULT 2.0"},
        };
        for (String[] col : columns) {
            AscendDatabaseSetup.ensureColumn(conn, "mine_miner_slots", col[0], col[1]);
        }
    }

    private static void ensureMineInMineColumn(Connection conn) {
        AscendDatabaseSetup.ensureColumn(conn, "mine_players", "in_mine", "TINYINT(1) NOT NULL DEFAULT 0");
    }

    private static void ensurePickaxeTierColumn(Connection conn) {
        AscendDatabaseSetup.ensureColumn(conn, "mine_players", "pickaxe_tier", "INT NOT NULL DEFAULT 0");
    }

    /**
     * Migrate crystals from mantissa+exp10 (DOUBLE+INT) to a single BIGINT column.
     * Idempotent: only runs if crystals_mantissa column still exists.
     */
    private static void migrateCrystalsToBigint(Connection conn) {
        if (!AscendDatabaseSetup.columnExists(conn, "mine_players", "crystals_mantissa")) return;
        try (Statement stmt = conn.createStatement()) {
            // Add new column if it doesn't exist
            if (!AscendDatabaseSetup.columnExists(conn, "mine_players", "crystals")) {
                stmt.executeUpdate("ALTER TABLE mine_players ADD COLUMN crystals BIGINT NOT NULL DEFAULT 0");
            }
            // Convert existing data: crystals = ROUND(crystals_mantissa * POW(10, crystals_exp10))
            stmt.executeUpdate("UPDATE mine_players SET crystals = ROUND(crystals_mantissa * POW(10, crystals_exp10))");
            // Drop old columns
            stmt.executeUpdate("ALTER TABLE mine_players DROP COLUMN crystals_mantissa, DROP COLUMN crystals_exp10");
            LOGGER.atInfo().log("Migrated mine_players crystals from mantissa+exp10 to BIGINT");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to migrate crystals to BIGINT: " + e.getMessage());
        }
    }

    /**
     * Migrate crystals column from BIGINT to DECIMAL(20,2) for fractional cashback amounts.
     * Idempotent: only runs if the column is still BIGINT.
     */
    private static void migrateCrystalsToDecimal(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM mine_players LIKE 'crystals'")) {
            if (rs.next()) {
                String type = rs.getString("Type").toUpperCase();
                if (type.contains("BIGINT")) {
                    stmt.executeUpdate("ALTER TABLE mine_players MODIFY COLUMN crystals DECIMAL(20,2) NOT NULL DEFAULT 0");
                    LOGGER.atInfo().log("Migrated mine_players crystals column from BIGINT to DECIMAL(20,2)");
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to migrate crystals to DECIMAL: " + e.getMessage());
        }
    }

    private static void ensureBlockHpColumns(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            if (!AscendDatabaseSetup.columnExists(conn, "mine_zones", "block_hp_json")) {
                stmt.executeUpdate("ALTER TABLE mine_zones ADD COLUMN block_hp_json TEXT NOT NULL DEFAULT '{}' AFTER block_table_json");
            }
            if (!AscendDatabaseSetup.columnExists(conn, "mine_zone_layers", "block_hp_json")) {
                stmt.executeUpdate("ALTER TABLE mine_zone_layers ADD COLUMN block_hp_json TEXT NOT NULL DEFAULT '{}' AFTER block_table_json");
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to add block_hp_json columns: " + e.getMessage());
        }
    }

    private static void migrateBlockPricesToSimple(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // If old mantissa/exp columns exist, migrate data and drop them
            if (AscendDatabaseSetup.columnExists(conn, "block_prices", "price_mantissa")) {
                if (!AscendDatabaseSetup.columnExists(conn, "block_prices", "price")) {
                    stmt.executeUpdate("ALTER TABLE block_prices ADD COLUMN price BIGINT NOT NULL DEFAULT 1");
                }
                stmt.executeUpdate("UPDATE block_prices SET price = CAST(price_mantissa * POW(10, price_exp10) AS SIGNED) WHERE price = 1");
                stmt.executeUpdate("ALTER TABLE block_prices DROP COLUMN price_mantissa");
                stmt.executeUpdate("ALTER TABLE block_prices DROP COLUMN price_exp10");
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to migrate block_prices to simple format: " + e.getMessage());
        }
    }

    /**
     * Add slot_index column to mine_miner_slots and mine_player_miners, then migrate PKs.
     * Existing rows get slot_index=0 via DEFAULT.
     */
    private static void ensureMultiSlotMigration(Connection conn) {
        // mine_miner_slots: add slot_index + migrate PK
        if (!AscendDatabaseSetup.columnExists(conn, "mine_miner_slots", "slot_index")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE mine_miner_slots ADD COLUMN slot_index INT NOT NULL DEFAULT 0");
                stmt.executeUpdate("ALTER TABLE mine_miner_slots DROP PRIMARY KEY, ADD PRIMARY KEY (mine_id, slot_index)");
                LOGGER.atInfo().log("Added slot_index to mine_miner_slots and migrated PK");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add slot_index to mine_miner_slots: " + e.getMessage());
            }
        }

        // mine_player_miners: add slot_index + migrate PK
        if (!AscendDatabaseSetup.columnExists(conn, "mine_player_miners", "slot_index")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE mine_player_miners ADD COLUMN slot_index INT NOT NULL DEFAULT 0");
                stmt.executeUpdate("ALTER TABLE mine_player_miners DROP PRIMARY KEY, ADD PRIMARY KEY (player_uuid, mine_id, slot_index)");
                LOGGER.atInfo().log("Added slot_index to mine_player_miners and migrated PK");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add slot_index to mine_player_miners: " + e.getMessage());
            }
        }
    }

    private static void ensurePickaxeEnhancementColumn(Connection conn) {
        AscendDatabaseSetup.ensureColumn(conn, "mine_players", "pickaxe_enhancement", "INT NOT NULL DEFAULT 0");
    }

    private static void ensureManualBlocksMinedColumn(Connection conn) {
        AscendDatabaseSetup.ensureColumn(conn, "mine_player_stats", "manual_blocks_mined", "BIGINT NOT NULL DEFAULT 0");
    }
}
