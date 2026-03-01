package io.hyvexa.purge.data;

public enum PurgeClass {
    SCAVENGER("Scavenger", 500,  "#22c55e"),
    TANK     ("Tank",      500,  "#3b82f6"),
    ASSAULT  ("Assault",   750,  "#ef4444"),
    MEDIC    ("Medic",     750,  "#f59e0b");

    private final String displayName;
    private final long unlockCost;
    private final String color;

    PurgeClass(String displayName, long unlockCost, String color) {
        this.displayName = displayName;
        this.unlockCost = unlockCost;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public long getUnlockCost() { return unlockCost; }
    public String getColor() { return color; }

    public static PurgeClass fromName(String name) {
        for (PurgeClass c : values()) {
            if (c.name().equalsIgnoreCase(name) || c.displayName.equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }
}
