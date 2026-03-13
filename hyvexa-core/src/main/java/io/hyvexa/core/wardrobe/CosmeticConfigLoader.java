package io.hyvexa.core.wardrobe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads wardrobe cosmetic definitions from mods/Parkour/cosmetics.json.
 * Generates a default file from the hardcoded list on first run.
 */
public class CosmeticConfigLoader {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Path CONFIG_PATH = Path.of("mods/Parkour/cosmetics.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private CosmeticConfigLoader() {
    }

    /**
     * Reads and parses cosmetics.json, generating the default file if missing.
     * Returns an empty list on parse failure (shop non-functional, server stays up).
     */
    public static List<WardrobeBridge.WardrobeCosmeticDef> load() {
        if (!Files.exists(CONFIG_PATH)) {
            generateDefault();
            LOGGER.atInfo().log("Generated default cosmetics config at " + CONFIG_PATH);
        }

        try {
            String json = Files.readString(CONFIG_PATH);
            CosmeticEntry[] entries = GSON.fromJson(json, CosmeticEntry[].class);
            if (entries == null) {
                LOGGER.atSevere().log("Failed to load cosmetics config: file parsed as null");
                return List.of();
            }
            return validate(entries);
        } catch (IOException | JsonParseException e) {
            LOGGER.atSevere().log("Failed to load cosmetics config: " + e.getMessage());
            return List.of();
        }
    }

    private static List<WardrobeBridge.WardrobeCosmeticDef> validate(CosmeticEntry[] entries) {
        List<WardrobeBridge.WardrobeCosmeticDef> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (CosmeticEntry entry : entries) {
            String id = "WD_" + entry.fileName;

            if (isBlank(entry.fileName)) {
                LOGGER.atSevere().log("Skipping cosmetic entry with blank fileName: " + entry);
                continue;
            }
            if (isBlank(entry.displayName)) {
                LOGGER.atSevere().log("Skipping cosmetic entry with blank displayName: " + entry);
                continue;
            }
            if (isBlank(entry.permissionNode)) {
                LOGGER.atSevere().log("Skipping cosmetic entry with blank permissionNode: " + entry);
                continue;
            }
            if (isBlank(entry.category)) {
                LOGGER.atSevere().log("Skipping cosmetic entry with blank category: " + entry);
                continue;
            }

            if (!seen.add(id)) {
                LOGGER.atSevere().log("Skipping duplicate cosmetic id: " + id);
                continue;
            }

            if (entry.iconKey == null) {
                LOGGER.atWarning().log("Cosmetic " + id + " has null iconKey");
            }

            result.add(new WardrobeBridge.WardrobeCosmeticDef(
                    id,
                    entry.displayName,
                    entry.permissionNode,
                    entry.category,
                    entry.iconKey,
                    entry.iconPath
            ));
        }

        return result;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Writes the default cosmetics.json containing all 101 current cosmetics.
     * This is a one-time migration: once the file exists, this method is never called again.
     */
    static void generateDefault() {
        String json = "[\n"
                + "  {\n    \"fileName\": \"Badge_Hyvexa\",\n    \"displayName\": \"Hyvexa Badge\",\n    \"permissionNode\": \"hyvexa.cosmetic.badge_hyvexa\",\n    \"category\": \"Badge\",\n    \"iconKey\": \"BadgeHyvexa\",\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Badges/Hyvexa.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Badge_Pride\",\n    \"displayName\": \"Pride Badge\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.badges.badge_pride\",\n    \"category\": \"Badge\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Badges/Pride.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Cloak_Chippy\",\n    \"displayName\": \"Chippy Cloak\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.capes.cloak_chippy\",\n    \"category\": \"Cape\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Capes/Cloak_Chippy.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Cloak_Snifferish\",\n    \"displayName\": \"Snifferish Cloak\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.capes.cloak_snifferish\",\n    \"category\": \"Cape\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Capes/Cloak_Snifferish.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Mobstar_Capes_Creeper\",\n    \"displayName\": \"Creeper Cape\",\n    \"permissionNode\": \"hyvexa.cosmetic.mobstar.capes.creeper\",\n    \"category\": \"Cape\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/Mobstar_Capes_Creeper_Green.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Mobstar_Capes_Tree_Trunk\",\n    \"displayName\": \"Tree Trunk Cape\",\n    \"permissionNode\": \"hyvexa.cosmetic.mobstar.capes.tree_trunk\",\n    \"category\": \"Cape\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/Mobstar_Capes_Tree_Trunks.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Mobstar_Capes_Undead\",\n    \"displayName\": \"Undead Cape\",\n    \"permissionNode\": \"hyvexa.cosmetic.mobstar.capes.undead\",\n    \"category\": \"Cape\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/Mobstar_Capes_Undead_Murky.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Mobstar_Capes_Void\",\n    \"displayName\": \"Void Cape\",\n    \"permissionNode\": \"hyvexa.cosmetic.mobstar.capes.void\",\n    \"category\": \"Cape\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/Mobstar_Capes_Void_Blue.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Mobstar_Capes_WIP\",\n    \"displayName\": \"WIP Cape\",\n    \"permissionNode\": \"hyvexa.cosmetic.mobstar.capes.wip\",\n    \"category\": \"Cape\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/Mobstar_Capes_WIP.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Tail_Imp\",\n    \"displayName\": \"Imp Tail\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.capes.tail_imp\",\n    \"category\": \"Cape\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Capes/Tail_Imp_Red.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Wings_Bee\",\n    \"displayName\": \"Bee Wings\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.capes.wings_bee\",\n    \"category\": \"Cape\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Capes/Bee_Wings.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Wings_Butterfly\",\n    \"displayName\": \"Butterfly Wings\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.capes.wings_butterfly\",\n    \"category\": \"Cape\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Capes/Butterfly_Wings_Orange.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Antelope_Ears\",\n    \"displayName\": \"Antelope Ears\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.ears.antelope_ears\",\n    \"category\": \"Ears\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Ears/Antelope_Ears.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Cat_Ears\",\n    \"displayName\": \"Cat Ears\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.ears.cat_ears\",\n    \"category\": \"Ears\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Ears/Cat_Ears.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Feran_Ears\",\n    \"displayName\": \"Feran Ears\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.ears.feran_ears\",\n    \"category\": \"Ears\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Ears/Fox_Ears_Blond.png\"\n  },\n"
                + "  {\n    \"fileName\": \"FoxEars_Big\",\n    \"displayName\": \"Fox Ears\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.ears.foxears_big\",\n    \"category\": \"Ears\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Ears/FoxEars_Big.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Mouse_Ears\",\n    \"displayName\": \"Mouse Ears\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.ears.mouse_ears\",\n    \"category\": \"Ears\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Ears/Mouse_Ears.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Ovis_Ears\",\n    \"displayName\": \"Ovis Ears\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.ears.ovis_ears\",\n    \"category\": \"Ears\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Ears/Ovis_Ears.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Piggy_Ears\",\n    \"displayName\": \"Piggy Ears\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.ears.piggy_ears\",\n    \"category\": \"Ears\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Ears/Piggy_Ears.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Rabbit_Ears\",\n    \"displayName\": \"Rabbit Ears\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.ears.rabbit_ears\",\n    \"category\": \"Ears\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Ears/Rabbit_Ears.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Wolf_Ears\",\n    \"displayName\": \"Wolf Ears\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.ears.wolf_ears\",\n    \"category\": \"Ears\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Ears/Wolf_Ears.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Moody_Mask\",\n    \"displayName\": \"Moody Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.faceaccessories.moody_mask\",\n    \"category\": \"Face\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/FaceAccessories/Moody_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Scarak_Defender_Mask\",\n    \"displayName\": \"Scarak Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.faceaccessories.scarak_defender_mask\",\n    \"category\": \"Face\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/FaceAccessories/Scarak_Defender_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Alien_Antenna\",\n    \"displayName\": \"Alien Antenna\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.alien_antenna\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Alien_Antenna.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Bee_Antenna\",\n    \"displayName\": \"Bee Antenna\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.bee_antenna\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Bee_Antenna.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Bee_Hairclip\",\n    \"displayName\": \"Bee Hairclip\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.bee_hairclip\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Bee_Hairclip.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Butterfly_Hairclip\",\n    \"displayName\": \"Butterfly Hairclip\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.butterfly_hairclip\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Butterfly_Hairclip.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Cactee_Beanie\",\n    \"displayName\": \"Cactee Beanie\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.cactee_beanie\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Cactee_Beanie.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Cap_BigDude\",\n    \"displayName\": \"BigDude Cap\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.cap_bigdude\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Cap_BigDude.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Cap_Flash\",\n    \"displayName\": \"Flash Cap\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.cap_flash\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Cap_Flash.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Cat_Headphones\",\n    \"displayName\": \"Cat Headphones\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.cat_headphones\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Cat_Headphones_Blue.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Chippy_Hat\",\n    \"displayName\": \"Chippy Hat\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.chippy_hat\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Chippy_Hat.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Computer_Head\",\n    \"displayName\": \"Computer Head\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.computer_head\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Computer_Head.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Deer_Antlers\",\n    \"displayName\": \"Deer Antlers\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.deer_antlers\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Deer_Antlers.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Devil_Horns\",\n    \"displayName\": \"Devil Horns\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.devil_horns\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Devil_Horns_Red.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Hanerpop_Cap\",\n    \"displayName\": \"Hanerpop Cap\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.hanerpop_cap\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Hanerpop_Cap.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Heart_Hairclip\",\n    \"displayName\": \"Heart Hairclip\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.heart_hairclip\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Heart_Hairclip.png\"\n  },\n"
                + "  {\n    \"fileName\": \"IBallisticSquid_Mask\",\n    \"displayName\": \"iBallisticSquid Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.iballisticsquid_mask\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/IBallisticSquid_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"ITMG\",\n    \"displayName\": \"ITMG\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.itmg\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Creature.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Kweebec_Mask\",\n    \"displayName\": \"Kweebec Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.kweebec_mask\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Kweebec_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"LittleWood_Headband\",\n    \"displayName\": \"LittleWood Headband\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.littlewood_headband\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/LittleWood_Headband.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Mushroom_Hat\",\n    \"displayName\": \"Mushroom Hat\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.mushroom_hat\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Mushroom_Hat.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Paige_Headband\",\n    \"displayName\": \"Paige Headband\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.paige_headband\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Paige_Headband.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Ram_Horns\",\n    \"displayName\": \"Ram Horns\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.ram_horns\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Ram_Horns_Yellow.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Slothian_Mask\",\n    \"displayName\": \"Slothian Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.slothian_mask\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Slothian_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Thirty_Headphones\",\n    \"displayName\": \"Thirty Headphones\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.thirty_headphones\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Thirty_Headphones.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Tiefling_Horns\",\n    \"displayName\": \"Tiefling Horns\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.tiefling_horns\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/Tiefling_Horns_Purple.png\"\n  },\n"
                + "  {\n    \"fileName\": \"TV_Head\",\n    \"displayName\": \"TV Head\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.headaccessories.tv_head\",\n    \"category\": \"Head\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/HeadAccessories/TV_Head_Grey.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Bear_Mask\",\n    \"displayName\": \"Bear Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_bear_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Bear_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Black_Wolf_Mask\",\n    \"displayName\": \"Black Wolf Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_black_wolf_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Black_Wolf_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Cactee_Mask\",\n    \"displayName\": \"Cactee Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_cactee_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Cactee_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Cat_Mask\",\n    \"displayName\": \"Cat Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_cat_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Cat_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Cow_Mask\",\n    \"displayName\": \"Cow Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_cow_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Cow_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Feran_Mask\",\n    \"displayName\": \"Feran Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_feran_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Feran_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Fox_Mask\",\n    \"displayName\": \"Fox Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_fox_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Fox_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Grey_Wolf_Mask\",\n    \"displayName\": \"Grey Wolf Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_grey_wolf_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Grey_Wolf_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Klops_Mask\",\n    \"displayName\": \"Klops Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_klops_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Klops_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Kweebec_Sapling_Orange_Mask\",\n    \"displayName\": \"Kweebec Orange Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_kweebec_sapling_orange_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Kweebec_Sapling_Orange_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Kweebec_Sapling_Pink_Mask\",\n    \"displayName\": \"Kweebec Pink Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_kweebec_sapling_pink_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Kweebec_Sapling_Pink_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Mosshorn_Mask\",\n    \"displayName\": \"Mosshorn Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_mosshorn_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Mosshorn_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Mosshorn2_Mask\",\n    \"displayName\": \"Mosshorn 2 Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_mosshorn2_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Mosshorn2_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Pig_Pink_Mask\",\n    \"displayName\": \"Pink Pig Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_pig_pink_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Pig_Pink_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Pig_Wild_Mask\",\n    \"displayName\": \"Wild Pig Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_pig_wild_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Pig_Wild_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Polar_Bear_Mask\",\n    \"displayName\": \"Polar Bear Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_polar_bear_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Polar_Bear_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Ram_Mask\",\n    \"displayName\": \"Ram Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_ram_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Ram_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Sabertooth_Tiger_Mask\",\n    \"displayName\": \"Sabertooth Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_sabertooth_tiger_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Sabertooth_Tiger_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Sabertooth_Tiger_Snow_Mask\",\n    \"displayName\": \"Snow Sabertooth Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_sabertooth_tiger_snow_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Sabertooth_Tiger_Snow_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Sabertooth_Tiger_Snow2_Mask\",\n    \"displayName\": \"Snow Sabertooth 2 Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_sabertooth_tiger_snow2_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Sabertooth_Tiger_Snow2_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Undead_Pig_Mask\",\n    \"displayName\": \"Undead Pig Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_undead_pig_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Undead_Pig_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_Warthog_Mask\",\n    \"displayName\": \"Warthog Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_warthog_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_Warthog_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"HayHay_Head_White_Wolf_Mask\",\n    \"displayName\": \"White Wolf Mask\",\n    \"permissionNode\": \"hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_white_wolf_mask\",\n    \"category\": \"Mask\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/ItemsGenerated/HayHay_Head_White_Wolf_Mask.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Antelope_Horns\",\n    \"displayName\": \"Antelope Horns\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.horns.antelope_horns\",\n    \"category\": \"Horns\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Horns/Antelope/Antelope_Horns.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Ovis_Horns\",\n    \"displayName\": \"Ovis Horns\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.horns.ovis_horns\",\n    \"category\": \"Horns\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Horns/Ovis/Ovis_Horns_Black.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Ovis_Horns_Low\",\n    \"displayName\": \"Ovis Horns Low\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.horns.ovis_horns_low\",\n    \"category\": \"Horns\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Horns/Ovis/Ovis_Horns_Low.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Beak_Hawk\",\n    \"displayName\": \"Hawk Beak\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.mouths.beak_hawk\",\n    \"category\": \"Mouth\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Mouths/Beak/Beak_Hawk.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Piggy_Snout\",\n    \"displayName\": \"Piggy Snout\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.mouths.piggy_snout\",\n    \"category\": \"Mouth\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Mouths/Piggy_Snout.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Fur_Socks\",\n    \"displayName\": \"Fur Socks\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.overpants.fur_socks\",\n    \"category\": \"Overpants\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Socks/Fur_Socks.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Reptile_Socks\",\n    \"displayName\": \"Reptile Socks\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.overpants.reptile_socks\",\n    \"category\": \"Overpants\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Socks/Reptile_Socks.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Jacket_Racing\",\n    \"displayName\": \"Racing Jacket\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.overtops.jacket_racing\",\n    \"category\": \"Overtop\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Overtops/Jacket_Racing_Cyan.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Sweater\",\n    \"displayName\": \"Sweater\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.overtops.sweater\",\n    \"category\": \"Overtop\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Overtops/Sweater_Pride.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Thirty_Hoodie\",\n    \"displayName\": \"Thirty Hoodie\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.overtops.thirty_hoodie\",\n    \"category\": \"Overtop\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Overtops/Thirty_Hoodie.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Overalls\",\n    \"displayName\": \"Overalls\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.pants.overalls\",\n    \"category\": \"Pants\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Pants/Overalls_Washed.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Hooves_Cloven\",\n    \"displayName\": \"Cloven Hooves\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.shoes.hooves_cloven\",\n    \"category\": \"Shoes\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Feet/Hooves_Cloven.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Paws_Clawed\",\n    \"displayName\": \"Clawed Paws\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.shoes.paws_clawed\",\n    \"category\": \"Shoes\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Feet/Paws_Clawed.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Paws_Clawed_CalicoBeans\",\n    \"displayName\": \"Calico Paws\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.shoes.paws_clawed_calicobeans\",\n    \"category\": \"Shoes\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Feet/CalicoBeans.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Paws_Clawed_PinkBeans\",\n    \"displayName\": \"Pink Paws\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.shoes.paws_clawed_pinkbeans\",\n    \"category\": \"Shoes\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Feet/PinkBeans.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Bird_Tail1\",\n    \"displayName\": \"Bird Tail\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.tails.bird_tail1\",\n    \"category\": \"Tail\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Tails/Bird_Tail1.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Bunny_Tail\",\n    \"displayName\": \"Bunny Tail\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.tails.bunny_tail\",\n    \"category\": \"Tail\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Tails/Bunny_Tail.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Cat_Tail\",\n    \"displayName\": \"Cat Tail\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.tails.cat_tail\",\n    \"category\": \"Tail\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Tails/Cat_Tail.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Fox_Tail\",\n    \"displayName\": \"Fox Tail\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.tails.fox_tail\",\n    \"category\": \"Tail\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Tails/Fox_Tail.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Rat_Tail\",\n    \"displayName\": \"Rat Tail\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.tails.rat_tail\",\n    \"category\": \"Tail\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Tails/Rat_Tail.png\"\n  },\n"
                + "  {\n    \"fileName\": \"ReptileLarge_Tail\",\n    \"displayName\": \"Reptile Tail\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.tails.reptilelarge_tail\",\n    \"category\": \"Tail\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Tails/ReptileLarge_Tail.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Tuft_Tail\",\n    \"displayName\": \"Tuft Tail\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.tails.tuft_tail\",\n    \"category\": \"Tail\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Tails/Tuft_Tail.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Wolf_Tail\",\n    \"displayName\": \"Wolf Tail\",\n    \"permissionNode\": \"hyvexa.cosmetic.cechoo.tails.wolf_tail\",\n    \"category\": \"Tail\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Tails/Wolf_Tail.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Shirt\",\n    \"displayName\": \"Shirt\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.undertops.shirt\",\n    \"category\": \"Undertop\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Undertops/Shirt_Alien.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Shirt_Buddhacat\",\n    \"displayName\": \"Buddhacat Shirt\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.undertops.shirt_buddhacat\",\n    \"category\": \"Undertop\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Undertops/Shirt_Buddhacat.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Shirt_LittleWood\",\n    \"displayName\": \"LittleWood Shirt\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.undertops.shirt_littlewood\",\n    \"category\": \"Undertop\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Undertops/Shirt_LittleWood.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Shirt_Paige\",\n    \"displayName\": \"Paige Shirt\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.undertops.shirt_paige\",\n    \"category\": \"Undertop\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Undertops/Shirt_Paige.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Shirt_Slamma\",\n    \"displayName\": \"Slamma Shirt\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.undertops.shirt_slamma\",\n    \"category\": \"Undertop\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Undertops/Shirt_Slamma.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Shirt_Vinesauce\",\n    \"displayName\": \"Vinesauce Shirt\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.undertops.shirt_vinesauce\",\n    \"category\": \"Undertop\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Undertops/Shirt_Vinesauce.png\"\n  },\n"
                + "  {\n    \"fileName\": \"Thanzotl_Shirt\",\n    \"displayName\": \"Thanzotl Shirt\",\n    \"permissionNode\": \"hyvexa.cosmetic.violet.undertops.thanzotl_shirt\",\n    \"category\": \"Undertop\",\n    \"iconKey\": null,\n    \"iconPath\": \"Icons/Wardrobe/Cosmetics/Undertops/Thanzotl_Shirt.png\"\n  }\n"
                + "]";

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException e) {
            LOGGER.atSevere().log("Failed to write default cosmetics config: " + e.getMessage());
        }
    }

    /** JSON deserialization DTO. Field names match the JSON format exactly. */
    private static class CosmeticEntry {
        String fileName;
        String displayName;
        String permissionNode;
        String category;
        String iconKey;
        String iconPath;

        @Override
        public String toString() {
            return "{fileName=" + fileName + ", displayName=" + displayName + "}";
        }
    }
}
