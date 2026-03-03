package io.hyvexa.purge.data;

public record PurgeVariantConfig(String key, String label, int baseHealth, float baseDamage,
                                  double speedMultiplier, String npcType, int scrapReward) {

    private static final String DEFAULT_NPC_TYPE = "Zombie";

    /** Returns the NPC type to spawn, never null. */
    public String effectiveNpcType() {
        return npcType != null && !npcType.isBlank() ? npcType : DEFAULT_NPC_TYPE;
    }
}
