package io.hyvexa.parkour.data;

public enum Medal {
    BRONZE("#cd7f32", "Sword_Signature_Status", 1),
    SILVER("#c0c0c0", "Drop_Epic", 2),
    GOLD("#ffd700", "Drop_Legendary", 3),
    EMERALD("#50C878", "Crown_Gold", 4),
    INSANE("#ff4d6d", "Crown_Gold", 5);

    private final String color;
    private final String effectId;
    private final int points;

    Medal(String color, String effectId, int points) {
        this.color = color;
        this.effectId = effectId;
        this.points = points;
    }

    public String getColor() {
        return color;
    }

    public String getEffectId() {
        return effectId;
    }

    public int getPoints() {
        return points;
    }

    public Long getThreshold(Map map) {
        return switch (this) {
            case BRONZE -> map.getBronzeTimeMs();
            case SILVER -> map.getSilverTimeMs();
            case GOLD -> map.getGoldTimeMs();
            case EMERALD -> map.getEmeraldTimeMs();
            case INSANE -> null;
        };
    }
}
