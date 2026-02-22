package io.hyvexa.core.cosmetic;

/**
 * Registry of all purchasable cosmetic effects.
 * Each entry maps to a custom EntityEffect JSON in Server/Entity/Effects/Cosmetics/.
 */
public enum CosmeticDefinition {

    GOLD_GLOW("Cosmetic_GoldGlow", "Gold Glow", "#FFD700", 100),
    PURPLE_GLOW("Cosmetic_PurpleGlow", "Purple Glow", "#A855F7", 100),
    BLUE_GLOW("Cosmetic_BlueGlow", "Blue Glow", "#3B82F6", 100),
    GREEN_GLOW("Cosmetic_GreenGlow", "Green Glow", "#22C55E", 100),
    CYAN_PULSE("Cosmetic_CyanPulse", "Cyan Pulse", "#06B6D4", 100),
    STONESKIN("Cosmetic_Stoneskin", "Stoneskin", "#888C8D", 100);

    private final String effectId;
    private final String displayName;
    private final String hexColor;
    private final int price;

    CosmeticDefinition(String effectId, String displayName, String hexColor, int price) {
        this.effectId = effectId;
        this.displayName = displayName;
        this.hexColor = hexColor;
        this.price = price;
    }

    public String getEffectId() {
        return effectId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getHexColor() {
        return hexColor;
    }

    public int getPrice() {
        return price;
    }

    /**
     * The string ID used for DB storage and lookups. Matches the enum name.
     */
    public String getId() {
        return name();
    }

    /**
     * Look up a cosmetic by its string ID (enum name). Returns null if not found.
     */
    public static CosmeticDefinition fromId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
