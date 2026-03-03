package io.hyvexa.core.vote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VoteConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Path CONFIG_PATH = Path.of("mods/Parkour/vote.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String baseUrl = "https://hytale.game/wp-json/hytale-api/v1";
    private String secretKey = "";
    private int rewardPerVote = 50;
    private int pollIntervalSeconds = 300;

    public static VoteConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            VoteConfig defaultConfig = new VoteConfig();
            defaultConfig.save();
            LOGGER.atInfo().log("Created default vote config at " + CONFIG_PATH + " -- fill in your secret key");
            return defaultConfig;
        }

        try {
            String json = Files.readString(CONFIG_PATH);
            VoteConfig config = GSON.fromJson(json, VoteConfig.class);
            LOGGER.atInfo().log("Loaded vote config from " + CONFIG_PATH);
            return config;
        } catch (IOException | JsonParseException e) {
            LOGGER.atSevere().log("Failed to load vote config: " + e.getMessage());
            return new VoteConfig();
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.atSevere().log("Failed to save vote config: " + e.getMessage());
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public int getRewardPerVote() {
        return rewardPerVote;
    }

    public int getPollIntervalSeconds() {
        return pollIntervalSeconds;
    }
}
