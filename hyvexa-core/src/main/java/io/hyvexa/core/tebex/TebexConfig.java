package io.hyvexa.core.tebex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TebexConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Path CONFIG_PATH = Path.of("mods/Parkour/tebex.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String secretKey = "";

    public static TebexConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            TebexConfig defaultConfig = new TebexConfig();
            defaultConfig.save();
            LOGGER.atInfo().log("Created default tebex config at " + CONFIG_PATH + " -- fill in your secret key");
            return defaultConfig;
        }

        try {
            String json = Files.readString(CONFIG_PATH);
            TebexConfig config = GSON.fromJson(json, TebexConfig.class);
            LOGGER.atInfo().log("Loaded tebex config from " + CONFIG_PATH);
            return config;
        } catch (IOException | JsonParseException e) {
            LOGGER.atSevere().log("Failed to load tebex config: " + e.getMessage());
            return new TebexConfig();
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.atSevere().log("Failed to save tebex config: " + e.getMessage());
        }
    }

    public String getSecretKey() {
        return secretKey;
    }
}
