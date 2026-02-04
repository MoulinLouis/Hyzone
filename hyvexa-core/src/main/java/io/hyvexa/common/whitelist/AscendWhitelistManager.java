package io.hyvexa.common.whitelist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Manages the whitelist of players allowed to access Ascend mode via the Hub menu.
 * Admin commands remain OP-only.
 */
public class AscendWhitelistManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AscendWhitelistManager.class);

    private final File whitelistFile;
    private final Set<String> whitelistedPlayers = new HashSet<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private boolean enabled = false; // Disabled by default - everyone can access until explicitly enabled

    public AscendWhitelistManager(File whitelistFile) {
        this.whitelistFile = whitelistFile;
        load();
    }

    /**
     * Adds a player to the whitelist.
     * @param username The player's username (case-insensitive)
     * @return true if the player was added, false if already whitelisted
     */
    public boolean add(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        String normalized = username.toLowerCase();
        if (whitelistedPlayers.contains(normalized)) {
            return false;
        }

        whitelistedPlayers.add(normalized);
        save();
        return true;
    }

    /**
     * Removes a player from the whitelist.
     * @param username The player's username (case-insensitive)
     * @return true if the player was removed, false if not whitelisted
     */
    public boolean remove(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        String normalized = username.toLowerCase();
        if (!whitelistedPlayers.contains(normalized)) {
            return false;
        }

        whitelistedPlayers.remove(normalized);
        save();
        return true;
    }

    /**
     * Checks if a player is whitelisted.
     * @param username The player's username (case-insensitive)
     * @return true if the player is whitelisted
     */
    public boolean contains(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return whitelistedPlayers.contains(username.toLowerCase());
    }

    /**
     * Gets a list of all whitelisted players.
     * @return List of whitelisted usernames (sorted alphabetically)
     */
    public List<String> list() {
        List<String> sorted = new ArrayList<>(whitelistedPlayers);
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * Checks if the whitelist is enabled.
     * @return true if whitelist is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables the whitelist.
     * When enabled, whitelisted players + OPs can access.
     * When disabled, only OPs can access (default secure behavior).
     * @param enabled true to enable whitelist, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    private void load() {
        if (!whitelistFile.exists()) {
            LOGGER.info("Whitelist file not found, creating new one: {}", whitelistFile.getPath());
            save();
            return;
        }

        try (FileReader reader = new FileReader(whitelistFile)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (json == null) {
                LOGGER.warn("Whitelist file is empty, resetting");
                save();
                return;
            }

            // Load enabled flag (default to true if not present)
            if (json.has("enabled")) {
                enabled = json.get("enabled").getAsBoolean();
            }

            JsonArray whitelisted = json.getAsJsonArray("whitelisted");
            if (whitelisted != null) {
                whitelistedPlayers.clear();
                for (JsonElement element : whitelisted) {
                    String username = element.getAsString();
                    if (username != null && !username.trim().isEmpty()) {
                        whitelistedPlayers.add(username.toLowerCase());
                    }
                }
                LOGGER.info("Loaded {} whitelisted players (enabled: {})", whitelistedPlayers.size(), enabled);
            }
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            LOGGER.error("Failed to load whitelist file, resetting: {}", e.getMessage());
            whitelistedPlayers.clear();
            enabled = true;
            save();
        }
    }

    private void save() {
        try {
            if (!whitelistFile.getParentFile().exists()) {
                whitelistFile.getParentFile().mkdirs();
            }

            JsonObject json = new JsonObject();
            json.addProperty("enabled", enabled);

            JsonArray whitelisted = new JsonArray();

            // Sort for consistent file output
            List<String> sorted = new ArrayList<>(whitelistedPlayers);
            Collections.sort(sorted);

            for (String username : sorted) {
                whitelisted.add(username);
            }

            json.add("whitelisted", whitelisted);

            try (FileWriter writer = new FileWriter(whitelistFile)) {
                gson.toJson(json, writer);
            }

            LOGGER.debug("Saved whitelist with {} players (enabled: {})", whitelistedPlayers.size(), enabled);
        } catch (IOException e) {
            LOGGER.error("Failed to save whitelist file: {}", e.getMessage());
        }
    }
}
