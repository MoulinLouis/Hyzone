package io.hyvexa.core.db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DatabaseConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Path CONFIG_PATH = Path.of("mods/Parkour/database.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String host = "localhost";
    private int port = 3306;
    private String database = "hytale_parkour";
    private String user = "root";
    private String password = "";

    public static DatabaseConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            DatabaseConfig defaultConfig = new DatabaseConfig();
            defaultConfig.save();
            LOGGER.atInfo().log("Created default database config at " + CONFIG_PATH);
            return defaultConfig;
        }

        try {
            String json = Files.readString(CONFIG_PATH);
            DatabaseConfig config = GSON.fromJson(json, DatabaseConfig.class);
            LOGGER.atInfo().log("Loaded database config from " + CONFIG_PATH);
            return config;
        } catch (IOException | JsonParseException e) {
            LOGGER.atSevere().log("Failed to load database config: " + e.getMessage());
            return new DatabaseConfig();
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.atSevere().log("Failed to save database config: " + e.getMessage());
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        String envPassword = System.getenv("HYVEXA_DB_PASSWORD");
        return envPassword != null ? envPassword : password;
    }

    public static Path getConfigPath() {
        return CONFIG_PATH;
    }
}
