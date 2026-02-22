package io.hyvexa.purge.data;

public record PurgeVariantConfig(String key, String label, int baseHealth, float baseDamage,
                                  double speedMultiplier, String npcType) {

    private static final String DEFAULT_NPC_TYPE = "Zombie";

    /** Backwards-compatible constructor without npcType. */
    public PurgeVariantConfig(String key, String label, int baseHealth, float baseDamage, double speedMultiplier) {
        this(key, label, baseHealth, baseDamage, speedMultiplier, DEFAULT_NPC_TYPE);
    }

    /** Returns the NPC type to spawn, never null. */
    public String effectiveNpcType() {
        return npcType != null && !npcType.isBlank() ? npcType : DEFAULT_NPC_TYPE;
    }
}
