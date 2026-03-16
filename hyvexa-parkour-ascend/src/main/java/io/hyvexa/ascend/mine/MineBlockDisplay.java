package io.hyvexa.ascend.mine;

public final class MineBlockDisplay {

    private MineBlockDisplay() {
    }

    public static String getItemId(String blockTypeId) {
        return stripNamespace(blockTypeId);
    }

    public static String getDisplayName(String blockTypeId) {
        if (blockTypeId == null || blockTypeId.isEmpty()) {
            return "Unknown";
        }

        String registered = MineBlockRegistry.getDisplayName(blockTypeId);
        if (!registered.equals(blockTypeId)) {
            return registered;
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
