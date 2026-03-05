package io.hyvexa.ascend.mine;

import java.util.Map;

public final class MineBlockDisplay {

    private static final Map<String, String> DISPLAY_NAMES = Map.of(
        "Rock_Stone", "Stone",
        "Rock_Crystal_Blue_Block", "Blue Crystal",
        "Rock_Crystal_Green_Block", "Green Crystal",
        "Rock_Crystal_Pink_Block", "Pink Crystal",
        "Rock_Crystal_Red_Block", "Red Crystal",
        "Rock_Crystal_White_Block", "White Crystal",
        "Rock_Crystal_Yellow_Block", "Yellow Crystal"
    );

    private MineBlockDisplay() {
    }

    public static String getItemId(String blockTypeId) {
        return stripNamespace(blockTypeId);
    }

    public static String getDisplayName(String blockTypeId) {
        if (blockTypeId == null || blockTypeId.isEmpty()) {
            return "Unknown";
        }
        String direct = DISPLAY_NAMES.get(blockTypeId);
        if (direct != null) {
            return direct;
        }

        String name = stripNamespace(blockTypeId);
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    sb.append(parts[i].substring(1));
                }
            }
        }
        return sb.toString();
    }

    private static String stripNamespace(String blockTypeId) {
        if (blockTypeId == null) {
            return "";
        }
        int colonIndex = blockTypeId.indexOf(':');
        return colonIndex >= 0 ? blockTypeId.substring(colonIndex + 1) : blockTypeId;
    }
}
