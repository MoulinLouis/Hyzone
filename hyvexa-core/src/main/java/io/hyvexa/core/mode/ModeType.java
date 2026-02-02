package io.hyvexa.core.mode;

/**
 * Enumeration of available game modes in the Hyvexa server.
 * Used for mode identification, state tracking, and routing decisions.
 */
public enum ModeType {

    /**
     * Hub mode - lobby/spawn area for mode selection.
     */
    HUB("Hub", "Hub"),

    /**
     * Parkour mode - traditional parkour with maps, timers, and leaderboards.
     */
    PARKOUR("Parkour", "Parkour"),

    /**
     * Ascend mode - idle/incremental parkour with runners and prestige.
     */
    ASCEND("Ascend", "Ascend");

    private final String id;
    private final String worldName;

    ModeType(String id, String worldName) {
        this.id = id;
        this.worldName = worldName;
    }

    /**
     * Get the unique identifier for this mode.
     * @return The mode ID (e.g., "Hub", "Parkour", "Ascend")
     */
    public String getId() {
        return id;
    }

    /**
     * Get the default world name for this mode.
     * @return The world name
     */
    public String getWorldName() {
        return worldName;
    }

    /**
     * Find a mode type by world name (case-insensitive).
     * @param worldName The world name to search for
     * @return The matching ModeType, or null if not found
     */
    public static ModeType fromWorldName(String worldName) {
        if (worldName == null) {
            return null;
        }
        for (ModeType mode : values()) {
            if (mode.worldName.equalsIgnoreCase(worldName)) {
                return mode;
            }
        }
        return null;
    }

    /**
     * Find a mode type by ID (case-insensitive).
     * @param id The mode ID to search for
     * @return The matching ModeType, or null if not found
     */
    public static ModeType fromId(String id) {
        if (id == null) {
            return null;
        }
        for (ModeType mode : values()) {
            if (mode.id.equalsIgnoreCase(id)) {
                return mode;
            }
        }
        return null;
    }
}
