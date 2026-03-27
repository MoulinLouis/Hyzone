package io.hyvexa.ascend.mine.data;

import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class GateConfigStore {

    private static final int GATE_ENTRY = 1;
    private static final int GATE_EXIT = 2;

    private final ConnectionProvider db;

    private record GateConfig(
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        double destX, double destY, double destZ,
        float destRotX, float destRotY, float destRotZ
    ) {
        boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    private volatile GateConfig entryGate;
    private volatile GateConfig exitGate;

    public GateConfigStore(ConnectionProvider db) {
        this.db = db;
    }

    public void syncLoad(Connection conn) throws SQLException {
        loadGate(conn);
    }

    private void loadGate(Connection conn) throws SQLException {
        String sql = "SELECT id, min_x, min_y, min_z, max_x, max_y, max_z, " +
            "fallback_x, fallback_y, fallback_z, fallback_rot_x, fallback_rot_y, fallback_rot_z " +
            "FROM mine_gate WHERE id IN (" + GATE_ENTRY + ", " + GATE_EXIT + ")";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    GateConfig gate = new GateConfig(
                        rs.getDouble("min_x"), rs.getDouble("min_y"), rs.getDouble("min_z"),
                        rs.getDouble("max_x"), rs.getDouble("max_y"), rs.getDouble("max_z"),
                        rs.getDouble("fallback_x"), rs.getDouble("fallback_y"), rs.getDouble("fallback_z"),
                        rs.getFloat("fallback_rot_x"), rs.getFloat("fallback_rot_y"), rs.getFloat("fallback_rot_z")
                    );
                    if (id == GATE_ENTRY) {
                        entryGate = gate;
                    } else if (id == GATE_EXIT) {
                        exitGate = gate;
                    }
                }
            }
        }
    }

    // --- Save ---

    public void saveEntryGate(double minX, double minY, double minZ,
                              double maxX, double maxY, double maxZ,
                              double destX, double destY, double destZ,
                              float destRotX, float destRotY, float destRotZ) {
        this.entryGate = new GateConfig(minX, minY, minZ, maxX, maxY, maxZ,
            destX, destY, destZ, destRotX, destRotY, destRotZ);

        saveGateToDatabase(GATE_ENTRY, minX, minY, minZ, maxX, maxY, maxZ,
            destX, destY, destZ, destRotX, destRotY, destRotZ);
    }

    public void saveExitGate(double minX, double minY, double minZ,
                             double maxX, double maxY, double maxZ,
                             double destX, double destY, double destZ,
                             float destRotX, float destRotY, float destRotZ) {
        this.exitGate = new GateConfig(minX, minY, minZ, maxX, maxY, maxZ,
            destX, destY, destZ, destRotX, destRotY, destRotZ);

        saveGateToDatabase(GATE_EXIT, minX, minY, minZ, maxX, maxY, maxZ,
            destX, destY, destZ, destRotX, destRotY, destRotZ);
    }

    private void saveGateToDatabase(int gateId, double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ,
                                    double destX, double destY, double destZ,
                                    float destRotX, float destRotY, float destRotZ) {
        String sql = """
            INSERT INTO mine_gate (id, min_x, min_y, min_z, max_x, max_y, max_z,
                fallback_x, fallback_y, fallback_z, fallback_rot_x, fallback_rot_y, fallback_rot_z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                min_x = VALUES(min_x), min_y = VALUES(min_y), min_z = VALUES(min_z),
                max_x = VALUES(max_x), max_y = VALUES(max_y), max_z = VALUES(max_z),
                fallback_x = VALUES(fallback_x), fallback_y = VALUES(fallback_y), fallback_z = VALUES(fallback_z),
                fallback_rot_x = VALUES(fallback_rot_x), fallback_rot_y = VALUES(fallback_rot_y),
                fallback_rot_z = VALUES(fallback_rot_z)
            """;

        DatabaseManager.execute(this.db, sql, stmt -> {
            int i = 1;
            stmt.setInt(i++, gateId);
            stmt.setDouble(i++, minX);
            stmt.setDouble(i++, minY);
            stmt.setDouble(i++, minZ);
            stmt.setDouble(i++, maxX);
            stmt.setDouble(i++, maxY);
            stmt.setDouble(i++, maxZ);
            stmt.setDouble(i++, destX);
            stmt.setDouble(i++, destY);
            stmt.setDouble(i++, destZ);
            stmt.setFloat(i++, destRotX);
            stmt.setFloat(i++, destRotY);
            stmt.setFloat(i, destRotZ);
        });
    }

    // --- Query ---

    public boolean isInsideEntryGate(double x, double y, double z) {
        GateConfig gate = entryGate;
        return gate != null && gate.contains(x, y, z);
    }

    public boolean isInsideExitGate(double x, double y, double z) {
        GateConfig gate = exitGate;
        return gate != null && gate.contains(x, y, z);
    }

    // Entry gate
    public boolean isEntryGateConfigured() { return entryGate != null; }
    public double getEntryMinX() { GateConfig g = entryGate; return g != null ? g.minX() : 0; }
    public double getEntryMinY() { GateConfig g = entryGate; return g != null ? g.minY() : 0; }
    public double getEntryMinZ() { GateConfig g = entryGate; return g != null ? g.minZ() : 0; }
    public double getEntryMaxX() { GateConfig g = entryGate; return g != null ? g.maxX() : 0; }
    public double getEntryMaxY() { GateConfig g = entryGate; return g != null ? g.maxY() : 0; }
    public double getEntryMaxZ() { GateConfig g = entryGate; return g != null ? g.maxZ() : 0; }
    public double getEntryDestX() { GateConfig g = entryGate; return g != null ? g.destX() : 0; }
    public double getEntryDestY() { GateConfig g = entryGate; return g != null ? g.destY() : 0; }
    public double getEntryDestZ() { GateConfig g = entryGate; return g != null ? g.destZ() : 0; }
    public float getEntryDestRotX() { GateConfig g = entryGate; return g != null ? g.destRotX() : 0; }
    public float getEntryDestRotY() { GateConfig g = entryGate; return g != null ? g.destRotY() : 0; }
    public float getEntryDestRotZ() { GateConfig g = entryGate; return g != null ? g.destRotZ() : 0; }

    // Exit gate
    public boolean isExitGateConfigured() { return exitGate != null; }
    public double getExitMinX() { GateConfig g = exitGate; return g != null ? g.minX() : 0; }
    public double getExitMinY() { GateConfig g = exitGate; return g != null ? g.minY() : 0; }
    public double getExitMinZ() { GateConfig g = exitGate; return g != null ? g.minZ() : 0; }
    public double getExitMaxX() { GateConfig g = exitGate; return g != null ? g.maxX() : 0; }
    public double getExitMaxY() { GateConfig g = exitGate; return g != null ? g.maxY() : 0; }
    public double getExitMaxZ() { GateConfig g = exitGate; return g != null ? g.maxZ() : 0; }
    public double getExitDestX() { GateConfig g = exitGate; return g != null ? g.destX() : 0; }
    public double getExitDestY() { GateConfig g = exitGate; return g != null ? g.destY() : 0; }
    public double getExitDestZ() { GateConfig g = exitGate; return g != null ? g.destZ() : 0; }
    public float getExitDestRotX() { GateConfig g = exitGate; return g != null ? g.destRotX() : 0; }
    public float getExitDestRotY() { GateConfig g = exitGate; return g != null ? g.destRotY() : 0; }
    public float getExitDestRotZ() { GateConfig g = exitGate; return g != null ? g.destRotZ() : 0; }
}
