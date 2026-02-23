package io.hyvexa.purge.data;

public record PurgeVariantConfig(String key, String label, int baseHealth, float baseDamage,
                                  double speedMultiplier, String npcType, int scrapReward) {

    private static final String DEFAULT_NPC_TYPE = "Zombie";

    /** Backwards-compatible constructor without scrapReward. */
    public PurgeVariantConfig(String key, String label, int baseHealth, float baseDamage,
                              double speedMultiplier, String npcType) {
        this(key, label, baseHealth, baseDamage, speedMultiplier, npcType, 10);
    }

    /** Backwards-compatible constructor without npcType. */
    public PurgeVariantConfig(String key, String label, int baseHealth, float baseDamage, double speedMultiplier) {
        this(key, label, baseHealth, baseDamage, speedMultiplier, DEFAULT_NPC_TYPE, 10);
    }

    /** Returns the NPC type to spawn, never null. */
    public String effectiveNpcType() {
        return npcType != null && !npcType.isBlank() ? npcType : DEFAULT_NPC_TYPE;
    }
}
