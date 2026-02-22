package io.hyvexa.purge.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PurgeSkinRegistry {

    private static final Map<String, List<PurgeSkinDefinition>> SKINS = new LinkedHashMap<>();

    static {
        register(new PurgeSkinDefinition("AK47", "Asimov", "Asimov", 100));
        register(new PurgeSkinDefinition("AK47", "Blossom", "Blossom", 100));
        register(new PurgeSkinDefinition("AK47", "CyberpunkNeon", "Cyberpunk Neon", 100));
        register(new PurgeSkinDefinition("AK47", "FrozenVoltage", "Frozen Voltage", 100));
    }

    private PurgeSkinRegistry() {}

    private static void register(PurgeSkinDefinition def) {
        SKINS.computeIfAbsent(def.getWeaponId(), k -> new ArrayList<>()).add(def);
    }

    public static List<PurgeSkinDefinition> getSkinsForWeapon(String weaponId) {
        return SKINS.getOrDefault(weaponId, Collections.emptyList());
    }

    public static PurgeSkinDefinition getSkin(String weaponId, String skinId) {
        for (PurgeSkinDefinition def : getSkinsForWeapon(weaponId)) {
            if (def.getSkinId().equals(skinId)) {
                return def;
            }
        }
        return null;
    }

    public static String getSkinnedItemId(String weaponId, String skinId) {
        PurgeSkinDefinition def = getSkin(weaponId, skinId);
        return def != null ? def.getItemId() : weaponId;
    }

    public static List<PurgeSkinDefinition> getAllSkins() {
        List<PurgeSkinDefinition> all = new ArrayList<>();
        for (List<PurgeSkinDefinition> weaponSkins : SKINS.values()) {
            all.addAll(weaponSkins);
        }
        return all;
    }

    public static boolean hasAnySkins(String weaponId) {
        return !getSkinsForWeapon(weaponId).isEmpty();
    }
}
