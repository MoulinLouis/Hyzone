package io.hyvexa.parkour.data;

public enum Medal {
    BRONZE("#cd7f32", "Sword_Signature_Status"),
    SILVER("#c0c0c0", "Drop_Epic"),
    GOLD("#ffd700", "Drop_Legendary"),
    AUTHOR("#50C878", "Crown_Gold");

    private final String color;
    private final String effectId;

    Medal(String color, String effectId) {
        this.color = color;
        this.effectId = effectId;
    }

    public String getColor() {
        return color;
    }

    public String getEffectId() {
        return effectId;
    }

    public Long getThreshold(Map map) {
        return switch (this) {
            case BRONZE -> map.getBronzeTimeMs();
            case SILVER -> map.getSilverTimeMs();
            case GOLD -> map.getGoldTimeMs();
            case AUTHOR -> map.getAuthorTimeMs();
        };
    }
}
