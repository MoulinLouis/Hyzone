package io.hyvexa.core.wardrobe;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import io.hyvexa.core.cosmetic.CosmeticStore;
import io.hyvexa.core.economy.CurrencyBridge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bridge between Hyvexa economy and the Wardrobe mod's permission-based locking.
 * Handles purchasing wardrobe cosmetics (vexa deduction + permission grant)
 * and re-granting permissions on login.
 */
public class WardrobeBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final WardrobeBridge INSTANCE = new WardrobeBridge();

    /** All wardrobe cosmetics available for purchase. */
    private static final List<WardrobeCosmeticDef> COSMETICS = List.of(
            // Badges
            wd("Badge_Hyvexa", "Hyvexa Badge", "hyvexa.cosmetic.badge_hyvexa", "Badge", "BadgeHyvexa", "Icons/Wardrobe/Cosmetics/Badges/Hyvexa.png"),
            wd("Badge_Pride", "Pride Badge", "hyvexa.cosmetic.violet.badges.badge_pride", "Badge", null, "Icons/Wardrobe/Cosmetics/Badges/Pride.png"),

            // Capes
            wd("Cloak_Chippy", "Chippy Cloak", "hyvexa.cosmetic.violet.capes.cloak_chippy", "Cape", null, "Icons/Wardrobe/Cosmetics/Capes/Cloak_Chippy.png"),
            wd("Cloak_Snifferish", "Snifferish Cloak", "hyvexa.cosmetic.violet.capes.cloak_snifferish", "Cape", null, "Icons/Wardrobe/Cosmetics/Capes/Cloak_Snifferish.png"),
            wd("Mobstar_Capes_Creeper", "Creeper Cape", "hyvexa.cosmetic.mobstar.capes.creeper", "Cape", null, "Icons/ItemsGenerated/Mobstar_Capes_Creeper_Green.png"),
            wd("Mobstar_Capes_Tree_Trunk", "Tree Trunk Cape", "hyvexa.cosmetic.mobstar.capes.tree_trunk", "Cape", null, "Icons/ItemsGenerated/Mobstar_Capes_Tree_Trunks.png"),
            wd("Mobstar_Capes_Undead", "Undead Cape", "hyvexa.cosmetic.mobstar.capes.undead", "Cape", null, "Icons/ItemsGenerated/Mobstar_Capes_Undead_Murky.png"),
            wd("Mobstar_Capes_Void", "Void Cape", "hyvexa.cosmetic.mobstar.capes.void", "Cape", null, "Icons/ItemsGenerated/Mobstar_Capes_Void_Blue.png"),
            wd("Mobstar_Capes_WIP", "WIP Cape", "hyvexa.cosmetic.mobstar.capes.wip", "Cape", null, "Icons/ItemsGenerated/Mobstar_Capes_WIP.png"),
            wd("Tail_Imp", "Imp Tail", "hyvexa.cosmetic.violet.capes.tail_imp", "Cape", null, "Icons/Wardrobe/Cosmetics/Capes/Tail_Imp_Red.png"),
            wd("Wings_Bee", "Bee Wings", "hyvexa.cosmetic.violet.capes.wings_bee", "Cape", null, "Icons/Wardrobe/Cosmetics/Capes/Bee_Wings.png"),
            wd("Wings_Butterfly", "Butterfly Wings", "hyvexa.cosmetic.violet.capes.wings_butterfly", "Cape", null, "Icons/Wardrobe/Cosmetics/Capes/Butterfly_Wings_Orange.png"),

            // Ears
            wd("Antelope_Ears", "Antelope Ears", "hyvexa.cosmetic.cechoo.ears.antelope_ears", "Ears", null, "Icons/Wardrobe/Ears/Antelope_Ears.png"),
            wd("Cat_Ears", "Cat Ears", "hyvexa.cosmetic.cechoo.ears.cat_ears", "Ears", null, "Icons/Wardrobe/Ears/Cat_Ears.png"),
            wd("Feran_Ears", "Feran Ears", "hyvexa.cosmetic.cechoo.ears.feran_ears", "Ears", null, "Icons/Wardrobe/Ears/Fox_Ears_Blond.png"),
            wd("FoxEars_Big", "Fox Ears", "hyvexa.cosmetic.cechoo.ears.foxears_big", "Ears", null, "Icons/Wardrobe/Ears/FoxEars_Big.png"),
            wd("Mouse_Ears", "Mouse Ears", "hyvexa.cosmetic.cechoo.ears.mouse_ears", "Ears", null, "Icons/Wardrobe/Ears/Mouse_Ears.png"),
            wd("Ovis_Ears", "Ovis Ears", "hyvexa.cosmetic.cechoo.ears.ovis_ears", "Ears", null, "Icons/Wardrobe/Ears/Ovis_Ears.png"),
            wd("Piggy_Ears", "Piggy Ears", "hyvexa.cosmetic.cechoo.ears.piggy_ears", "Ears", null, "Icons/Wardrobe/Ears/Piggy_Ears.png"),
            wd("Rabbit_Ears", "Rabbit Ears", "hyvexa.cosmetic.cechoo.ears.rabbit_ears", "Ears", null, "Icons/Wardrobe/Ears/Rabbit_Ears.png"),
            wd("Wolf_Ears", "Wolf Ears", "hyvexa.cosmetic.cechoo.ears.wolf_ears", "Ears", null, "Icons/Wardrobe/Ears/Wolf_Ears.png"),

            // Face Accessories
            wd("Moody_Mask", "Moody Mask", "hyvexa.cosmetic.violet.faceaccessories.moody_mask", "Face", null, "Icons/Wardrobe/Cosmetics/FaceAccessories/Moody_Mask.png"),
            wd("Scarak_Defender_Mask", "Scarak Mask", "hyvexa.cosmetic.violet.faceaccessories.scarak_defender_mask", "Face", null, "Icons/Wardrobe/Cosmetics/FaceAccessories/Scarak_Defender_Mask.png"),

            // Head Accessories
            wd("Alien_Antenna", "Alien Antenna", "hyvexa.cosmetic.violet.headaccessories.alien_antenna", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Alien_Antenna.png"),
            wd("Bee_Antenna", "Bee Antenna", "hyvexa.cosmetic.violet.headaccessories.bee_antenna", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Bee_Antenna.png"),
            wd("Bee_Hairclip", "Bee Hairclip", "hyvexa.cosmetic.violet.headaccessories.bee_hairclip", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Bee_Hairclip.png"),
            wd("Butterfly_Hairclip", "Butterfly Hairclip", "hyvexa.cosmetic.violet.headaccessories.butterfly_hairclip", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Butterfly_Hairclip.png"),
            wd("Cactee_Beanie", "Cactee Beanie", "hyvexa.cosmetic.violet.headaccessories.cactee_beanie", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Cactee_Beanie.png"),
            wd("Cap_BigDude", "BigDude Cap", "hyvexa.cosmetic.violet.headaccessories.cap_bigdude", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Cap_BigDude.png"),
            wd("Cap_Flash", "Flash Cap", "hyvexa.cosmetic.violet.headaccessories.cap_flash", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Cap_Flash.png"),
            wd("Cat_Headphones", "Cat Headphones", "hyvexa.cosmetic.violet.headaccessories.cat_headphones", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Cat_Headphones_Blue.png"),
            wd("Chippy_Hat", "Chippy Hat", "hyvexa.cosmetic.violet.headaccessories.chippy_hat", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Chippy_Hat.png"),
            wd("Computer_Head", "Computer Head", "hyvexa.cosmetic.violet.headaccessories.computer_head", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Computer_Head.png"),
            wd("Deer_Antlers", "Deer Antlers", "hyvexa.cosmetic.violet.headaccessories.deer_antlers", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Deer_Antlers.png"),
            wd("Devil_Horns", "Devil Horns", "hyvexa.cosmetic.violet.headaccessories.devil_horns", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Devil_Horns_Red.png"),
            wd("Hanerpop_Cap", "Hanerpop Cap", "hyvexa.cosmetic.violet.headaccessories.hanerpop_cap", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Hanerpop_Cap.png"),
            wd("Heart_Hairclip", "Heart Hairclip", "hyvexa.cosmetic.violet.headaccessories.heart_hairclip", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Heart_Hairclip.png"),
            wd("IBallisticSquid_Mask", "iBallisticSquid Mask", "hyvexa.cosmetic.violet.headaccessories.iballisticsquid_mask", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/IBallisticSquid_Mask.png"),
            wd("ITMG", "ITMG", "hyvexa.cosmetic.violet.headaccessories.itmg", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Creature.png"),
            wd("Kweebec_Mask", "Kweebec Mask", "hyvexa.cosmetic.violet.headaccessories.kweebec_mask", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Kweebec_Mask.png"),
            wd("LittleWood_Headband", "LittleWood Headband", "hyvexa.cosmetic.violet.headaccessories.littlewood_headband", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/LittleWood_Headband.png"),
            wd("Mushroom_Hat", "Mushroom Hat", "hyvexa.cosmetic.violet.headaccessories.mushroom_hat", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Mushroom_Hat.png"),
            wd("Paige_Headband", "Paige Headband", "hyvexa.cosmetic.violet.headaccessories.paige_headband", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Paige_Headband.png"),
            wd("Ram_Horns", "Ram Horns", "hyvexa.cosmetic.violet.headaccessories.ram_horns", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Ram_Horns_Yellow.png"),
            wd("Slothian_Mask", "Slothian Mask", "hyvexa.cosmetic.violet.headaccessories.slothian_mask", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Slothian_Mask.png"),
            wd("Thirty_Headphones", "Thirty Headphones", "hyvexa.cosmetic.violet.headaccessories.thirty_headphones", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Thirty_Headphones.png"),
            wd("Tiefling_Horns", "Tiefling Horns", "hyvexa.cosmetic.violet.headaccessories.tiefling_horns", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/Tiefling_Horns_Purple.png"),
            wd("TV_Head", "TV Head", "hyvexa.cosmetic.violet.headaccessories.tv_head", "Head", null, "Icons/Wardrobe/Cosmetics/HeadAccessories/TV_Head_Grey.png"),

            // Head Accessories - HayHay Masks
            wd("HayHay_Head_Bear_Mask", "Bear Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_bear_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Bear_Mask.png"),
            wd("HayHay_Head_Black_Wolf_Mask", "Black Wolf Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_black_wolf_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Black_Wolf_Mask.png"),
            wd("HayHay_Head_Cactee_Mask", "Cactee Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_cactee_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Cactee_Mask.png"),
            wd("HayHay_Head_Cat_Mask", "Cat Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_cat_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Cat_Mask.png"),
            wd("HayHay_Head_Cow_Mask", "Cow Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_cow_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Cow_Mask.png"),
            wd("HayHay_Head_Feran_Mask", "Feran Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_feran_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Feran_Mask.png"),
            wd("HayHay_Head_Fox_Mask", "Fox Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_fox_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Fox_Mask.png"),
            wd("HayHay_Head_Grey_Wolf_Mask", "Grey Wolf Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_grey_wolf_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Grey_Wolf_Mask.png"),
            wd("HayHay_Head_Klops_Mask", "Klops Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_klops_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Klops_Mask.png"),
            wd("HayHay_Head_Kweebec_Sapling_Orange_Mask", "Kweebec Orange Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_kweebec_sapling_orange_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Kweebec_Sapling_Orange_Mask.png"),
            wd("HayHay_Head_Kweebec_Sapling_Pink_Mask", "Kweebec Pink Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_kweebec_sapling_pink_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Kweebec_Sapling_Pink_Mask.png"),
            wd("HayHay_Head_Mosshorn_Mask", "Mosshorn Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_mosshorn_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Mosshorn_Mask.png"),
            wd("HayHay_Head_Mosshorn2_Mask", "Mosshorn 2 Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_mosshorn2_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Mosshorn2_Mask.png"),
            wd("HayHay_Head_Pig_Pink_Mask", "Pink Pig Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_pig_pink_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Pig_Pink_Mask.png"),
            wd("HayHay_Head_Pig_Wild_Mask", "Wild Pig Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_pig_wild_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Pig_Wild_Mask.png"),
            wd("HayHay_Head_Polar_Bear_Mask", "Polar Bear Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_polar_bear_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Polar_Bear_Mask.png"),
            wd("HayHay_Head_Ram_Mask", "Ram Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_ram_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Ram_Mask.png"),
            wd("HayHay_Head_Sabertooth_Tiger_Mask", "Sabertooth Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_sabertooth_tiger_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Sabertooth_Tiger_Mask.png"),
            wd("HayHay_Head_Sabertooth_Tiger_Snow_Mask", "Snow Sabertooth Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_sabertooth_tiger_snow_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Sabertooth_Tiger_Snow_Mask.png"),
            wd("HayHay_Head_Sabertooth_Tiger_Snow2_Mask", "Snow Sabertooth 2 Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_sabertooth_tiger_snow2_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Sabertooth_Tiger_Snow2_Mask.png"),
            wd("HayHay_Head_Undead_Pig_Mask", "Undead Pig Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_undead_pig_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Undead_Pig_Mask.png"),
            wd("HayHay_Head_Warthog_Mask", "Warthog Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_warthog_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_Warthog_Mask.png"),
            wd("HayHay_Head_White_Wolf_Mask", "White Wolf Mask", "hyvexa.cosmetic.hayhay.headaccessories.hayhay_head_white_wolf_mask", "Mask", null, "Icons/ItemsGenerated/HayHay_Head_White_Wolf_Mask.png"),

            // Horns
            wd("Antelope_Horns", "Antelope Horns", "hyvexa.cosmetic.cechoo.horns.antelope_horns", "Horns", null, "Icons/Wardrobe/Horns/Antelope/Antelope_Horns.png"),
            wd("Ovis_Horns", "Ovis Horns", "hyvexa.cosmetic.cechoo.horns.ovis_horns", "Horns", null, "Icons/Wardrobe/Horns/Ovis/Ovis_Horns_Black.png"),
            wd("Ovis_Horns_Low", "Ovis Horns Low", "hyvexa.cosmetic.cechoo.horns.ovis_horns_low", "Horns", null, "Icons/Wardrobe/Horns/Ovis/Ovis_Horns_Low.png"),

            // Mouths
            wd("Beak_Hawk", "Hawk Beak", "hyvexa.cosmetic.cechoo.mouths.beak_hawk", "Mouth", null, "Icons/Wardrobe/Mouths/Beak/Beak_Hawk.png"),
            wd("Piggy_Snout", "Piggy Snout", "hyvexa.cosmetic.cechoo.mouths.piggy_snout", "Mouth", null, "Icons/Wardrobe/Mouths/Piggy_Snout.png"),

            // Overpants
            wd("Fur_Socks", "Fur Socks", "hyvexa.cosmetic.cechoo.overpants.fur_socks", "Overpants", null, "Icons/Wardrobe/Socks/Fur_Socks.png"),
            wd("Reptile_Socks", "Reptile Socks", "hyvexa.cosmetic.cechoo.overpants.reptile_socks", "Overpants", null, "Icons/Wardrobe/Socks/Reptile_Socks.png"),

            // Overtops
            wd("Jacket_Racing", "Racing Jacket", "hyvexa.cosmetic.violet.overtops.jacket_racing", "Overtop", null, "Icons/Wardrobe/Cosmetics/Overtops/Jacket_Racing_Cyan.png"),
            wd("Sweater", "Sweater", "hyvexa.cosmetic.violet.overtops.sweater", "Overtop", null, "Icons/Wardrobe/Cosmetics/Overtops/Sweater_Pride.png"),
            wd("Thirty_Hoodie", "Thirty Hoodie", "hyvexa.cosmetic.violet.overtops.thirty_hoodie", "Overtop", null, "Icons/Wardrobe/Cosmetics/Overtops/Thirty_Hoodie.png"),

            // Pants
            wd("Overalls", "Overalls", "hyvexa.cosmetic.violet.pants.overalls", "Pants", null, "Icons/Wardrobe/Cosmetics/Pants/Overalls_Washed.png"),

            // Shoes
            wd("Hooves_Cloven", "Cloven Hooves", "hyvexa.cosmetic.cechoo.shoes.hooves_cloven", "Shoes", null, "Icons/Wardrobe/Feet/Hooves_Cloven.png"),
            wd("Paws_Clawed", "Clawed Paws", "hyvexa.cosmetic.cechoo.shoes.paws_clawed", "Shoes", null, "Icons/Wardrobe/Feet/Paws_Clawed.png"),
            wd("Paws_Clawed_CalicoBeans", "Calico Paws", "hyvexa.cosmetic.cechoo.shoes.paws_clawed_calicobeans", "Shoes", null, "Icons/Wardrobe/Feet/Paws_Clawed.png"),
            wd("Paws_Clawed_PinkBeans", "Pink Paws", "hyvexa.cosmetic.cechoo.shoes.paws_clawed_pinkbeans", "Shoes", null, "Icons/Wardrobe/Feet/Paws_Clawed.png"),

            // Tails
            wd("Bird_Tail1", "Bird Tail", "hyvexa.cosmetic.cechoo.tails.bird_tail1", "Tail", null, "Icons/Wardrobe/Tails/Bird_Tail1.png"),
            wd("Bunny_Tail", "Bunny Tail", "hyvexa.cosmetic.cechoo.tails.bunny_tail", "Tail", null, "Icons/Wardrobe/Tails/Bunny_Tail.png"),
            wd("Cat_Tail", "Cat Tail", "hyvexa.cosmetic.cechoo.tails.cat_tail", "Tail", null, "Icons/Wardrobe/Tails/Cat_Tail.png"),
            wd("Fox_Tail", "Fox Tail", "hyvexa.cosmetic.cechoo.tails.fox_tail", "Tail", null, "Icons/Wardrobe/Tails/Fox_Tail.png"),
            wd("Rat_Tail", "Rat Tail", "hyvexa.cosmetic.cechoo.tails.rat_tail", "Tail", null, "Icons/Wardrobe/Tails/Rat_Tail.png"),
            wd("ReptileLarge_Tail", "Reptile Tail", "hyvexa.cosmetic.cechoo.tails.reptilelarge_tail", "Tail", null, "Icons/Wardrobe/Tails/ReptileLarge_Tail.png"),
            wd("Tuft_Tail", "Tuft Tail", "hyvexa.cosmetic.cechoo.tails.tuft_tail", "Tail", null, "Icons/Wardrobe/Tails/Tuft_Tail.png"),
            wd("Wolf_Tail", "Wolf Tail", "hyvexa.cosmetic.cechoo.tails.wolf_tail", "Tail", null, "Icons/Wardrobe/Tails/Wolf_Tail.png"),

            // Undertops
            wd("Shirt", "Shirt", "hyvexa.cosmetic.violet.undertops.shirt", "Undertop", null, "Icons/Wardrobe/Cosmetics/Undertops/Shirt_Alien.png"),
            wd("Shirt_Buddhacat", "Buddhacat Shirt", "hyvexa.cosmetic.violet.undertops.shirt_buddhacat", "Undertop", null, "Icons/Wardrobe/Cosmetics/Undertops/Shirt_Buddhacat.png"),
            wd("Shirt_LittleWood", "LittleWood Shirt", "hyvexa.cosmetic.violet.undertops.shirt_littlewood", "Undertop", null, "Icons/Wardrobe/Cosmetics/Undertops/Shirt_LittleWood.png"),
            wd("Shirt_Paige", "Paige Shirt", "hyvexa.cosmetic.violet.undertops.shirt_paige", "Undertop", null, "Icons/Wardrobe/Cosmetics/Undertops/Shirt_Paige.png"),
            wd("Shirt_Slamma", "Slamma Shirt", "hyvexa.cosmetic.violet.undertops.shirt_slamma", "Undertop", null, "Icons/Wardrobe/Cosmetics/Undertops/Shirt_Slamma.png"),
            wd("Shirt_Vinesauce", "Vinesauce Shirt", "hyvexa.cosmetic.violet.undertops.shirt_vinesauce", "Undertop", null, "Icons/Wardrobe/Cosmetics/Undertops/Shirt_Vinesauce.png"),
            wd("Thanzotl_Shirt", "Thanzotl Shirt", "hyvexa.cosmetic.violet.undertops.thanzotl_shirt", "Undertop", null, "Icons/Wardrobe/Cosmetics/Undertops/Thanzotl_Shirt.png")
    );

    private static WardrobeCosmeticDef wd(String fileName, String displayName, String permissionNode,
                                           String category, String iconKey, String iconPath) {
        return new WardrobeCosmeticDef("WD_" + fileName, displayName, 0, permissionNode, category, iconKey, iconPath);
    }

    private WardrobeBridge() {
    }

    public static WardrobeBridge getInstance() {
        return INSTANCE;
    }

    public List<WardrobeCosmeticDef> getAllCosmetics() {
        return COSMETICS;
    }

    public WardrobeCosmeticDef findById(String id) {
        for (WardrobeCosmeticDef def : COSMETICS) {
            if (def.id().equals(id)) return def;
        }
        return null;
    }

    /** Returns distinct ordered category list. */
    public List<String> getCategories() {
        LinkedHashSet<String> cats = new LinkedHashSet<>();
        for (WardrobeCosmeticDef def : COSMETICS) {
            cats.add(def.category());
        }
        return new ArrayList<>(cats);
    }

    /** Returns cosmetics filtered by category (null = all). */
    public List<WardrobeCosmeticDef> getCosmeticsByCategory(String category) {
        if (category == null) return COSMETICS;
        List<WardrobeCosmeticDef> result = new ArrayList<>();
        for (WardrobeCosmeticDef def : COSMETICS) {
            if (def.category().equals(category)) result.add(def);
        }
        return result;
    }

    /**
     * Purchase a wardrobe cosmetic: deduct currency, record ownership, grant permission.
     * Uses CosmeticShopConfigStore for price/currency and CurrencyBridge for deduction.
     * Returns a result message for the player.
     */
    public String purchase(UUID playerId, String cosmeticId) {
        WardrobeCosmeticDef def = findById(cosmeticId);
        if (def == null) return "Unknown cosmetic: " + cosmeticId;

        CosmeticShopConfigStore configStore = CosmeticShopConfigStore.getInstance();
        if (!configStore.isAvailable(cosmeticId)) {
            return "This cosmetic is not currently available for purchase.";
        }

        if (CosmeticStore.getInstance().ownsCosmetic(playerId, cosmeticId)) {
            return "You already own " + def.displayName() + "!";
        }

        int price = configStore.getPrice(cosmeticId);
        String currency = configStore.getCurrency(cosmeticId);
        long balance = CurrencyBridge.getBalance(currency, playerId);
        if (balance < price) {
            return "Not enough " + currency + "! You need " + price + " but have " + balance + ".";
        }

        CurrencyBridge.deduct(currency, playerId, price);
        CosmeticStore.getInstance().purchaseCosmetic(playerId, cosmeticId);
        grantPermission(playerId, def.permissionNode());

        LOGGER.atInfo().log("Player " + playerId + " purchased wardrobe cosmetic: " + cosmeticId);
        return "Purchased " + def.displayName() + " for " + price + " " + currency + "! Open /wardrobe to equip it.";
    }

    /**
     * Re-grant all wardrobe permissions for owned cosmetics. Call on player login.
     */
    public void regrantPermissions(UUID playerId) {
        for (WardrobeCosmeticDef def : COSMETICS) {
            if (CosmeticStore.getInstance().ownsCosmetic(playerId, def.id())) {
                grantPermission(playerId, def.permissionNode());
            }
        }
    }

    /**
     * Reset all wardrobe cosmetics: clear DB records and revoke all permissions.
     */
    public void resetAll(UUID playerId) {
        for (WardrobeCosmeticDef def : COSMETICS) {
            revokePermission(playerId, def.permissionNode());
        }
        CosmeticStore.getInstance().resetAllCosmetics(playerId);
        LOGGER.atInfo().log("Reset all wardrobe cosmetics for player " + playerId);
    }

    @SuppressWarnings("removal")
    private void revokePermission(UUID playerId, String permissionNode) {
        PermissionsModule permissions = PermissionsModule.get();
        if (permissions == null) return;
        permissions.removeUserPermission(playerId, Set.of(permissionNode));
    }

    @SuppressWarnings("removal")
    private void grantPermission(UUID playerId, String permissionNode) {
        PermissionsModule permissions = PermissionsModule.get();
        if (permissions == null) {
            LOGGER.atWarning().log("PermissionsModule not available, cannot grant: " + permissionNode);
            return;
        }
        permissions.addUserPermission(playerId, Set.of(permissionNode));
    }

    public record WardrobeCosmeticDef(String id, String displayName, int price,
                                       String permissionNode, String category, String iconKey,
                                       String iconPath) {
    }
}
