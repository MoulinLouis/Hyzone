package io.hyvexa.core.cosmetic;

/**
 * Registry of all purchasable glow/trail cosmetics.
 */
public enum CosmeticDefinition {

    GOLD_GLOW(Kind.GLOW, Type.ENTITY_EFFECT, "Cosmetic_GoldGlow", "Gold Glow", "#FFD700", 100),
    PURPLE_GLOW(Kind.GLOW, Type.ENTITY_EFFECT, "Cosmetic_PurpleGlow", "Purple Glow", "#A855F7", 100),
    BLUE_GLOW(Kind.GLOW, Type.ENTITY_EFFECT, "Cosmetic_BlueGlow", "Blue Glow", "#3B82F6", 100),
    GREEN_GLOW(Kind.GLOW, Type.ENTITY_EFFECT, "Cosmetic_GreenGlow", "Green Glow", "#22C55E", 100),
    CYAN_PULSE(Kind.GLOW, Type.ENTITY_EFFECT, "Cosmetic_CyanPulse", "Cyan Pulse", "#06B6D4", 100),
    STONESKIN(Kind.GLOW, Type.ENTITY_EFFECT, "Cosmetic_Stoneskin", "Stoneskin", "#888C8D", 100),

    TRAIL_GOLD(Kind.TRAIL, Type.WORLD_PARTICLE_TRAIL, "Firework_GS", "Gold Trail", "#FFD700", 100, 0.5f, 200L, 0f, 0.1f, 0f),
    TRAIL_FIREWORK_MIX(Kind.TRAIL, Type.WORLD_PARTICLE_TRAIL, "Firework_Mix2", "Firework Mix Trail", "#f472b6", 100, 0.5f, 200L, 0f, 0.1f, 0f),
    TRAIL_FIREWORK_MIX3(Kind.TRAIL, Type.WORLD_PARTICLE_TRAIL, "Firework_Mix3", "Firework Mix 3 Trail", "#f472b6", 100, 0.5f, 200L, 0f, 0.1f, 0f),
    TRAIL_FIREWORK_MIX4(Kind.TRAIL, Type.WORLD_PARTICLE_TRAIL, "Firework_Mix4", "Firework Mix 4 Trail", "#f472b6", 100, 0.5f, 200L, 0f, 0.1f, 0f),
    TRAIL_RINGS(Kind.TRAIL, Type.WORLD_PARTICLE_TRAIL, "Rings_Rings", "Rings Trail", "#a78bfa", 100, 0.6f, 220L, 0f, 0.1f, 0f);

    private final Kind kind;
    private final Type type;
    private final String visualId;
    private final String displayName;
    private final String hexColor;
    private final int price;
    private final float scale;
    private final long intervalMs;
    private final float xOffset;
    private final float yOffset;
    private final float zOffset;

    CosmeticDefinition(Kind kind, Type type, String visualId, String displayName, String hexColor, int price) {
        this(kind, type, visualId, displayName, hexColor, price, 1.0f, 200L, 0f, 0f, 0f);
    }

    CosmeticDefinition(Kind kind, Type type, String visualId, String displayName, String hexColor, int price,
                       float scale, long intervalMs, float xOffset, float yOffset, float zOffset) {
        this.kind = kind;
        this.type = type;
        this.visualId = visualId;
        this.displayName = displayName;
        this.hexColor = hexColor;
        this.price = price;
        this.scale = scale;
        this.intervalMs = intervalMs;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.zOffset = zOffset;
    }

    public Kind getKind() {
        return kind;
    }

    public Type getType() {
        return type;
    }

    public String getVisualId() {
        return visualId;
    }

    public String getEffectId() {
        return type == Type.ENTITY_EFFECT ? visualId : null;
    }

    public String getParticleId() {
        return type == Type.ENTITY_EFFECT ? null : visualId;
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

    public float getScale() {
        return scale;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public float getXOffset() {
        return xOffset;
    }

    public float getYOffset() {
        return yOffset;
    }

    public float getZOffset() {
        return zOffset;
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

    public enum Kind {
        GLOW,
        TRAIL
    }

    public enum Type {
        ENTITY_EFFECT,
        WORLD_PARTICLE_TRAIL,
        MODEL_PARTICLE_TRAIL
    }
}
