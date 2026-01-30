package io.hyvexa.common.util;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class HylogramsBridge {
    private static final String HYLOGRAMS_GROUP = "ehko";
    private static final String HYLOGRAMS_NAME = "Hylograms";
    private static final String HYLOGRAMS_API_CLASS = "dev.ehko.hylograms.api.HologramsAPI";

    private HylogramsBridge() {
    }

    public static boolean isAvailable() {
        return resolveHylogramsClassLoader() != null;
    }

    public static List<String> listHologramNames() {
        Class<?> apiClass = resolveApiClass();
        try {
            Method listMethod = apiClass.getMethod("list");
            Object result = listMethod.invoke(null);
            if (!(result instanceof List<?> rawList)) {
                throw new IllegalStateException("Hylograms API returned an unexpected result.");
            }
            List<String> names = new ArrayList<>(rawList.size());
            for (Object entry : rawList) {
                if (entry != null) {
                    names.add(entry.toString());
                }
            }
            return names;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to access Hylograms API.", e);
        }
    }

    private static Class<?> resolveApiClass() {
        ClassLoader classLoader = resolveHylogramsClassLoader();
        if (classLoader == null) {
            throw new IllegalStateException("Hylograms plugin is not loaded.");
        }
        try {
            return Class.forName(HYLOGRAMS_API_CLASS, true, classLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Hylograms API is not available at runtime.", e);
        }
    }

    private static ClassLoader resolveHylogramsClassLoader() {
        PluginManager manager = PluginManager.get();
        if (manager == null) {
            return null;
        }
        PluginIdentifier identifier = new PluginIdentifier(HYLOGRAMS_GROUP, HYLOGRAMS_NAME);
        PluginBase plugin = manager.getPlugin(identifier);
        if (plugin == null) {
            return null;
        }
        return plugin.getClass().getClassLoader();
    }
}
