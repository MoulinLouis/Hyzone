package io.hyvexa.common.util;

public final class AssetPathUtils {

    private AssetPathUtils() {
    }

    public static String normalizeIconAssetPath(String iconPath) {
        if (iconPath == null || iconPath.isBlank()) {
            return null;
        }

        String normalized = iconPath.replace('\\', '/');
        while (normalized.startsWith("../")) {
            normalized = normalized.substring(3);
        }

        if (normalized.startsWith("Common/")) {
            normalized = normalized.substring("Common/".length());
        }

        if (normalized.startsWith("Textures/")) {
            return "UI/Custom/" + normalized;
        }

        return normalized;
    }
}
