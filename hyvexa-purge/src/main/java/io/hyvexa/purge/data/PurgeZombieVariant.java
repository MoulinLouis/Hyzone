package io.hyvexa.purge.data;

public enum PurgeZombieVariant {
    SLOW("Slow", 49, 20f, 8.0 / 9.0),
    NORMAL("Normal", 49, 20f, 1.0),
    FAST("Fast", 49, 20f, 11.0 / 9.0);

    private final String label;
    private final int baseHealth;
    private final float baseDamage;
    /** Multiplier relative to vanilla zombie walk speed (9). */
    private final double speedMultiplier;

    PurgeZombieVariant(String label, int baseHealth, float baseDamage, double speedMultiplier) {
        this.label = label;
        this.baseHealth = baseHealth;
        this.baseDamage = baseDamage;
        this.speedMultiplier = speedMultiplier;
    }

    public String getLabel() {
        return label;
    }

    public int getBaseHealth() {
        return baseHealth;
    }

    public float getBaseDamage() {
        return baseDamage;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public static PurgeZombieVariant fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (PurgeZombieVariant variant : values()) {
            if (variant.name().equalsIgnoreCase(key)) {
                return variant;
            }
        }
        return null;
    }
}
