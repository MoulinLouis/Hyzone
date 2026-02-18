package io.hyvexa.purge.data;

public enum PurgeZombieVariant {
    SLOW("Slow", "Purge_Zombie_Slow"),
    NORMAL("Normal", "Purge_Zombie"),
    FAST("Fast", "Purge_Zombie_Fast");

    private final String label;
    private final String npcType;

    PurgeZombieVariant(String label, String npcType) {
        this.label = label;
        this.npcType = npcType;
    }

    public String getLabel() {
        return label;
    }

    public String getNpcType() {
        return npcType;
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
