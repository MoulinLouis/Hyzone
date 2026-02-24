package io.hyvexa.parkour.data;

public enum Medal {
    BRONZE, SILVER, GOLD;

    public Long getThreshold(Map map) {
        return switch (this) {
            case BRONZE -> map.getBronzeTimeMs();
            case SILVER -> map.getSilverTimeMs();
            case GOLD -> map.getGoldTimeMs();
        };
    }
}
