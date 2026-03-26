package io.hyvexa.ascend.mine.data;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

/**
 * Resolves miner portraits and display names.
 * Config-aware overloads check MineConfigStore first, falling back to hardcoded defaults (variant 0).
 */
public final class MinerVariant {

    private MinerVariant() {}

    // Display names indexed [rarity ordinal][variant 0..2]
    static final String[][] DISPLAY_NAMES = {
        // COMMON
        {"Stone Golem", "Mine Rat", "Mole"},
        // UNCOMMON
        {"Copper Golem", "Dwarf Miner", "Rock Beetle"},
        // RARE
        {"Crystal Golem", "Living Drill", "Mine Spirit"},
        // EPIC
        {"Obsidian Golem", "Lava Worm", "Deep Dragon"},
        // LEGENDARY
        {"Diamond Titan", "Forge Phoenix", "Ancient Guardian"},
    };

    // Portrait element IDs indexed [rarity ordinal][variant 0..2]
    // Must match IDs in .ui templates (no underscores)
    static final String[][] PORTRAIT_IDS = {
        {"PortraitCommon1", "PortraitCommon2", "PortraitCommon3"},
        {"PortraitUncommon1", "PortraitUncommon2", "PortraitUncommon3"},
        {"PortraitRare1", "PortraitRare2", "PortraitRare3"},
        {"PortraitEpic1", "PortraitEpic2", "PortraitEpic3"},
        {"PortraitLegendary1", "PortraitLegendary2", "PortraitLegendary3"},
    };

    // All portrait element IDs (for toggling visibility)
    public static final String[] ALL_PORTRAIT_IDS = {
        "PortraitEmpty",
        "PortraitCommon1", "PortraitCommon2", "PortraitCommon3",
        "PortraitUncommon1", "PortraitUncommon2", "PortraitUncommon3",
        "PortraitRare1", "PortraitRare2", "PortraitRare3",
        "PortraitEpic1", "PortraitEpic2", "PortraitEpic3",
        "PortraitLegendary1", "PortraitLegendary2", "PortraitLegendary3",
    };

    // --- Config-aware overloads (preferred) ---

    public static String getDisplayName(MineConfigStore store, CollectedMiner miner) {
        MinerDefinition def = store.getMinerDefinition(miner.getLayerId(), miner.getRarity());
        if (def != null) return def.displayName();
        return DISPLAY_NAMES[miner.getRarity().ordinal()][0];
    }

    public static String getPortraitId(MineConfigStore store, CollectedMiner miner) {
        MinerDefinition def = store.getMinerDefinition(miner.getLayerId(), miner.getRarity());
        if (def != null) return def.portraitId();
        return PORTRAIT_IDS[miner.getRarity().ordinal()][0];
    }

    public static void applyPortrait(UICommandBuilder cmd, String selector,
                                      MineConfigStore store, CollectedMiner miner) {
        String targetId = miner != null ? getPortraitId(store, miner) : "PortraitEmpty";
        for (String id : ALL_PORTRAIT_IDS) {
            cmd.set(selector + " #" + id + ".Visible", id.equals(targetId));
        }
    }

    /**
     * Apply a specific portrait by ID (for admin page preview).
     */
    public static void applyPortraitById(UICommandBuilder cmd, String selector, String portraitId) {
        String targetId = portraitId != null ? portraitId : "PortraitEmpty";
        for (String id : ALL_PORTRAIT_IDS) {
            cmd.set(selector + " #" + id + ".Visible", id.equals(targetId));
        }
    }

    /** Fallback display name for a rarity (variant 0). */
    public static String getDefaultDisplayName(MinerRarity rarity) {
        return DISPLAY_NAMES[rarity.ordinal()][0];
    }

    /** Fallback portrait ID for a rarity (variant 0). */
    public static String getDefaultPortraitId(MinerRarity rarity) {
        return PORTRAIT_IDS[rarity.ordinal()][0];
    }
}
