package io.hyvexa.ascend.mine.data;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

/**
 * Resolves miner portraits and display names from rarity + variant index.
 * Each miner's variant is deterministic: abs(id) % variantCount.
 */
public final class MinerVariant {

    private MinerVariant() {}

    private static final int VARIANTS_PER_RARITY = 3;

    // Display names indexed [rarity ordinal][variant 0..2]
    private static final String[][] DISPLAY_NAMES = {
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
    private static final String[][] PORTRAIT_IDS = {
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

    public static int resolveVariant(CollectedMiner miner) {
        return (int) (Math.abs(miner.getId()) % VARIANTS_PER_RARITY);
    }

    public static String getDisplayName(MinerRarity rarity, int variant) {
        return DISPLAY_NAMES[rarity.ordinal()][variant];
    }

    public static String getDisplayName(CollectedMiner miner) {
        return getDisplayName(miner.getRarity(), resolveVariant(miner));
    }

    public static String getPortraitId(MinerRarity rarity, int variant) {
        return PORTRAIT_IDS[rarity.ordinal()][variant];
    }

    public static String getPortraitId(CollectedMiner miner) {
        return getPortraitId(miner.getRarity(), resolveVariant(miner));
    }

    /**
     * Toggle portrait visibility within a parent selector.
     * Shows the portrait matching the given miner, hides all others.
     * If miner is null, shows the empty portrait.
     */
    public static void applyPortrait(UICommandBuilder cmd, String selector, CollectedMiner miner) {
        String targetId = miner != null ? getPortraitId(miner) : "PortraitEmpty";
        for (String id : ALL_PORTRAIT_IDS) {
            cmd.set(selector + " #" + id + ".Visible", id.equals(targetId));
        }
    }
}
