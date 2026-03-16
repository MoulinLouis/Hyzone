package io.hyvexa.ascend.mine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MineBlockRegistry {

    public static final String CAT_ROCK = "Rock";
    public static final String CAT_CRYSTAL = "Crystal";

    private static final List<BlockDef> ALL_BLOCKS = new ArrayList<>();
    private static final Map<String, List<BlockDef>> BY_CATEGORY = new LinkedHashMap<>();

    static {
        register(CAT_ROCK, "Rock_Stone", "Stone");
        register(CAT_ROCK, "Rock_Stone_Mossy", "Mossy Stone");
        register(CAT_ROCK, "Rock_Shale", "Shale");
        register(CAT_ROCK, "Rock_Slate", "Slate");
        register(CAT_ROCK, "Rock_Quartzite", "Quartzite");
        register(CAT_ROCK, "Rock_Sandstone", "Sandstone");
        register(CAT_ROCK, "Rock_Sandstone_Red", "Red Sandstone");
        register(CAT_ROCK, "Rock_Sandstone_White", "White Sandstone");
        register(CAT_ROCK, "Rock_Basalt", "Basalt");
        register(CAT_ROCK, "Rock_Volcanic", "Volcanic");
        register(CAT_ROCK, "Rock_Marble", "Marble");
        register(CAT_ROCK, "Rock_Calcite", "Calcite");
        register(CAT_ROCK, "Rock_Aqua", "Aqua");
        register(CAT_ROCK, "Rock_Chalk", "Chalk");
        register(CAT_ROCK, "Rock_Bedrock", "Bedrock");
        register(CAT_ROCK, "Rock_Salt", "Salt");

        register(CAT_CRYSTAL, "Rock_Crystal_Blue_Block", "Blue Crystal");
        register(CAT_CRYSTAL, "Rock_Crystal_Green_Block", "Green Crystal");
        register(CAT_CRYSTAL, "Rock_Crystal_Pink_Block", "Pink Crystal");
        register(CAT_CRYSTAL, "Rock_Crystal_Red_Block", "Red Crystal");
        register(CAT_CRYSTAL, "Rock_Crystal_White_Block", "White Crystal");
        register(CAT_CRYSTAL, "Rock_Crystal_Yellow_Block", "Yellow Crystal");

        // Only register IDs with exact asset-definition JSONs from Assets.zip.
        // PNG-only matches are deferred until a runtime BlockType probe confirms they resolve.
        String cat = "Ore - Cobalt";
        register(cat, "Ore_Cobalt_Shale", "Cobalt Shale");
        register(cat, "Ore_Cobalt_Slate", "Cobalt Slate");

        cat = "Ore - Copper";
        register(cat, "Ore_Copper_Sandstone", "Copper Sandstone");
        register(cat, "Ore_Copper_Shale", "Copper Shale");
        register(cat, "Ore_Copper_Stone", "Copper Stone");

        cat = "Ore - Gold";
        register(cat, "Ore_Gold_Basalt", "Gold Basalt");
        register(cat, "Ore_Gold_Sandstone", "Gold Sandstone");
        register(cat, "Ore_Gold_Shale", "Gold Shale");
        register(cat, "Ore_Gold_Stone", "Gold Stone");
        register(cat, "Ore_Gold_Volcanic", "Gold Volcanic");

        cat = "Ore - Iron";
        register(cat, "Ore_Iron_Basalt", "Iron Basalt");
        register(cat, "Ore_Iron_Sandstone", "Iron Sandstone");
        register(cat, "Ore_Iron_Shale", "Iron Shale");
        register(cat, "Ore_Iron_Slate", "Iron Slate");
        register(cat, "Ore_Iron_Stone", "Iron Stone");
        register(cat, "Ore_Iron_Volcanic", "Iron Volcanic");

        cat = "Ore - Mithril";
        register(cat, "Ore_Mithril_Stone", "Mithril Stone");

        cat = "Ore - Silver";
        register(cat, "Ore_Silver_Basalt", "Silver Basalt");
        register(cat, "Ore_Silver_Sandstone", "Silver Sandstone");
        register(cat, "Ore_Silver_Shale", "Silver Shale");
        register(cat, "Ore_Silver_Slate", "Silver Slate");
        register(cat, "Ore_Silver_Stone", "Silver Stone");
        register(cat, "Ore_Silver_Volcanic", "Silver Volcanic");

        cat = "Ore - Thorium";
        register(cat, "Ore_Thorium_Sandstone", "Thorium Sandstone");
    }

    private MineBlockRegistry() {}

    private static void register(String category, String blockTypeId, String displayName) {
        BlockDef def = new BlockDef(blockTypeId, displayName, category);
        ALL_BLOCKS.add(def);
        BY_CATEGORY.computeIfAbsent(category, key -> new ArrayList<>()).add(def);
    }

    public static List<BlockDef> getAll() {
        return ALL_BLOCKS;
    }

    public static Map<String, List<BlockDef>> getByCategory() {
        return BY_CATEGORY;
    }

    public static String getDisplayName(String blockTypeId) {
        for (BlockDef def : ALL_BLOCKS) {
            if (def.blockTypeId.equals(blockTypeId)) {
                return def.displayName;
            }
        }
        return blockTypeId;
    }

    public static class BlockDef {
        public final String blockTypeId;
        public final String displayName;
        public final String category;

        BlockDef(String blockTypeId, String displayName, String category) {
            this.blockTypeId = blockTypeId;
            this.displayName = displayName;
            this.category = category;
        }
    }
}
