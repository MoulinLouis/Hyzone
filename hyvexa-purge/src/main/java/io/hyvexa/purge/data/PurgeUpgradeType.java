package io.hyvexa.purge.data;

public enum PurgeUpgradeType {

    SWIFT_FEET("Swift Feet", "+10% move speed per stack", "#3b82f6"),
    IRON_SKIN("Iron Skin", "+20% max HP per stack", "#ef4444"),
    AMMO_CACHE("Ammo Cache", "+60 bullets", "#f59e0b"),
    SECOND_WIND("Second Wind", "Regen 1 HP/sec per stack", "#10b981"),
    THICK_HIDE("Thick Hide", "-8% damage taken per stack", "#a855f7"),
    SCAVENGER("Scavenger", "+25% bonus scrap per stack", "#eab308");

    private final String displayName;
    private final String description;
    private final String color;

    PurgeUpgradeType(String displayName, String description, String color) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getColor() {
        return color;
    }
}
