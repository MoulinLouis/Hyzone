package io.hyvexa.ascend;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;


/**
 * Runtime flags for Ascend plugin behavior.
 */
public final class AscendRuntimeConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Path CONFIG_PATH = Path.of("mods", "Parkour", "ascend.properties");
    private static final String ENABLE_TEST_COMMANDS_KEY = "ascend.enableTestCommands";
    private static final String NPC_COMMAND_TOKEN_KEY = "ascend.npcCommandToken";
    private static final boolean ENABLE_TEST_COMMANDS_DEFAULT = false;
    private static final String NPC_COMMAND_TOKEN_DEFAULT = "hx7Kq9mW";

    private final boolean enableTestCommands;
    private final String npcCommandToken;

    private AscendRuntimeConfig(boolean enableTestCommands, String npcCommandToken) {
        this.enableTestCommands = enableTestCommands;
        this.npcCommandToken = npcCommandToken;
    }

    public static AscendRuntimeConfig load() {
        Properties properties = new Properties();
        if (!Files.exists(CONFIG_PATH)) {
            properties.setProperty(ENABLE_TEST_COMMANDS_KEY, Boolean.toString(ENABLE_TEST_COMMANDS_DEFAULT));
            properties.setProperty(NPC_COMMAND_TOKEN_KEY, NPC_COMMAND_TOKEN_DEFAULT);
            saveDefaults(properties);
            LOGGER.atInfo().log("Created default Ascend runtime config at " + CONFIG_PATH);
            return new AscendRuntimeConfig(ENABLE_TEST_COMMANDS_DEFAULT, NPC_COMMAND_TOKEN_DEFAULT);
        }

        try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
            properties.load(input);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load Ascend runtime config at " + CONFIG_PATH);
            return new AscendRuntimeConfig(ENABLE_TEST_COMMANDS_DEFAULT, NPC_COMMAND_TOKEN_DEFAULT);
        }

        boolean enableTestCommands = parseBoolean(
            properties.getProperty(ENABLE_TEST_COMMANDS_KEY),
            ENABLE_TEST_COMMANDS_DEFAULT,
            ENABLE_TEST_COMMANDS_KEY
        );
        String npcCommandToken = parseToken(
            properties.getProperty(NPC_COMMAND_TOKEN_KEY),
            NPC_COMMAND_TOKEN_DEFAULT,
            NPC_COMMAND_TOKEN_KEY
        );
        return new AscendRuntimeConfig(enableTestCommands, npcCommandToken);
    }

    public boolean isEnableTestCommands() {
        return enableTestCommands;
    }

    public String getNpcCommandToken() {
        return npcCommandToken;
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
            LOGGER.atWarning().withCause(e).log("Failed to create default Ascend runtime config at " + CONFIG_PATH);
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
        LOGGER.atWarning().log("Invalid boolean value for " + key + ": '" + value + "' (using default: " + defaultValue + ")");
        return defaultValue;
    }

    private static String parseToken(String value, String defaultValue, String key) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            LOGGER.atWarning().log("Empty value for " + key + " (using default)");
            return defaultValue;
        }
        return trimmed;
    }
}
