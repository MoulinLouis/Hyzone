package io.hyvexa.ascend.mine.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ConveyorConfigStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ConnectionProvider db;
    private final Supplier<String> defaultMineId;
    private final MinerConfigStore minerConfigStore;

    // mineId -> { slotIndex -> [[x,y,z], ...] }  slotIndex=-1 for main line
    private final Map<String, Map<Integer, List<double[]>>> conveyorWaypoints = new ConcurrentHashMap<>();

    public ConveyorConfigStore(ConnectionProvider db, Supplier<String> defaultMineId,
                               MinerConfigStore minerConfigStore) {
        this.db = db;
        this.defaultMineId = defaultMineId;
        this.minerConfigStore = minerConfigStore;
    }

    public void syncLoad(Connection conn) throws SQLException {
        loadConveyorWaypoints(conn);
    }

    private void loadConveyorWaypoints(Connection conn) throws SQLException {
        conveyorWaypoints.clear();
        String sql = "SELECT mine_id, slot_index, waypoint_order, x, y, z " +
            "FROM mine_conveyor_waypoints ORDER BY mine_id, slot_index, waypoint_order";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String mineId = rs.getString("mine_id");
                    int slotIndex = rs.getInt("slot_index");
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    conveyorWaypoints
                        .computeIfAbsent(mineId, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(slotIndex, k -> new ArrayList<>())
                        .add(new double[]{x, y, z});
                }
            }
        }
    }

    public List<double[]> getSlotWaypoints(String mineId, int slotIndex) {
        Map<Integer, List<double[]>> mineWps = conveyorWaypoints.get(mineId);
        if (mineWps == null) return Collections.emptyList();
        List<double[]> wps = mineWps.get(slotIndex);
        return wps != null ? Collections.unmodifiableList(wps) : Collections.emptyList();
    }

    public List<double[]> getMainLineWaypoints(String mineId) {
        return getSlotWaypoints(mineId, -1);
    }

    public double getConveyorSpeed(String mineId) {
        MinerSlot slot = minerConfigStore.getMinerSlot(mineId, 0);
        return slot != null ? slot.getConveyorSpeed() : 2.0;
    }

    public boolean isConveyorConfigured(String mineId) {
        return !getMainLineWaypoints(mineId).isEmpty();
    }

    public void addConveyorWaypoint(String mineId, int slotIndex, double x, double y, double z) {
        List<double[]> wps = conveyorWaypoints
            .computeIfAbsent(mineId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(slotIndex, k -> new ArrayList<>());
        int order = wps.size();
        wps.add(new double[]{x, y, z});

        if (!this.db.isInitialized()) return;

        DatabaseManager.execute(this.db,
            "INSERT INTO mine_conveyor_waypoints (mine_id, slot_index, waypoint_order, x, y, z) VALUES (?, ?, ?, ?, ?, ?)",
            stmt -> {
                stmt.setString(1, mineId);
                stmt.setInt(2, slotIndex);
                stmt.setInt(3, order);
                stmt.setDouble(4, x);
                stmt.setDouble(5, y);
                stmt.setDouble(6, z);
            });
    }

    public void clearConveyorWaypoints(String mineId, int slotIndex) {
        Map<Integer, List<double[]>> mineWps = conveyorWaypoints.get(mineId);
        if (mineWps != null) {
            mineWps.remove(slotIndex);
        }

        if (!this.db.isInitialized()) return;

        DatabaseManager.execute(this.db,
            "DELETE FROM mine_conveyor_waypoints WHERE mine_id = ? AND slot_index = ?",
            stmt -> {
                stmt.setString(1, mineId);
                stmt.setInt(2, slotIndex);
            });
    }

    // --- Single-mine convenience ---

    public List<double[]> getSlotWaypoints(int slotIndex) {
        String id = defaultMineId.get();
        return id != null ? getSlotWaypoints(id, slotIndex) : Collections.emptyList();
    }

    public List<double[]> getMainLineWaypoints() {
        String id = defaultMineId.get();
        return id != null ? getMainLineWaypoints(id) : Collections.emptyList();
    }

    public double getConveyorSpeed() {
        String id = defaultMineId.get();
        return id != null ? getConveyorSpeed(id) : 2.0;
    }

    public boolean isConveyorConfigured() {
        String id = defaultMineId.get();
        return id != null && isConveyorConfigured(id);
    }
}
