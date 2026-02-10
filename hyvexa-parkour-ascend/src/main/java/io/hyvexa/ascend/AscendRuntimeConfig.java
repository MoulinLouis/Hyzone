package io.hyvexa.ascend;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;

/**
 * Runtime flags for Ascend plugin behavior.
 */
public final class AscendRuntimeConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Path CONFIG_PATH = Path.of("mods", "Parkour", "ascend.properties");
    private static final String ENABLE_TEST_COMMANDS_KEY = "ascend.enableTestCommands";
    private static final boolean ENABLE_TEST_COMMANDS_DEFAULT = false;

    private final boolean enableTestCommands;

    private AscendRuntimeConfig(boolean enableTestCommands) {
        this.enableTestCommands = enableTestCommands;
    }

    public static AscendRuntimeConfig load() {
        Properties properties = new Properties();
        if (!Files.exists(CONFIG_PATH)) {
            properties.setProperty(ENABLE_TEST_COMMANDS_KEY, Boolean.toString(ENABLE_TEST_COMMANDS_DEFAULT));
            saveDefaults(properties);
            LOGGER.atInfo().log("Created default Ascend runtime config at " + CONFIG_PATH);
            return new AscendRuntimeConfig(ENABLE_TEST_COMMANDS_DEFAULT);
        }

        try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
            properties.load(input);
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to load Ascend runtime config at " + CONFIG_PATH);
            return new AscendRuntimeConfig(ENABLE_TEST_COMMANDS_DEFAULT);
        }

        boolean enableTestCommands = parseBoolean(
            properties.getProperty(ENABLE_TEST_COMMANDS_KEY),
            ENABLE_TEST_COMMANDS_DEFAULT,
            ENABLE_TEST_COMMANDS_KEY
        );
        return new AscendRuntimeConfig(enableTestCommands);
    }

    public boolean isEnableTestCommands() {
        return enableTestCommands;
    }

    private static void saveDefaults(Properties properties) {
        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(output, "Ascend runtime settings");
            }
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to create default Ascend runtime config at " + CONFIG_PATH);
        }
    }

    private static boolean parseBoolean(String value, boolean defaultValue, String key) {
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        LOGGER.at(Level.WARNING).log("Invalid boolean value for " + key + ": '" + value + "' (using default: " + defaultValue + ")");
        return defaultValue;
    }
}
