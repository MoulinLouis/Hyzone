package io.hyvexa.common.whitelist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Manages the whitelist of players allowed to access Ascend mode via the Hub menu.
 * Admin commands remain OP-only.
 */
public class AscendWhitelistManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final File whitelistFile;
    private final Set<String> whitelistedPlayers = new ConcurrentSkipListSet<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private volatile boolean enabled = false; // Disabled by default - only OPs can access until whitelist is enabled
    private volatile boolean publicMode = false; // When true, anyone can join (no whitelist check at all)

    public AscendWhitelistManager(File whitelistFile) {
        this.whitelistFile = whitelistFile;
        load();
    }

    /** @return true if added, false if already whitelisted */
    public boolean add(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        String normalized = username.toLowerCase();
        if (!whitelistedPlayers.add(normalized)) {
            return false;
        }
        save();
        return true;
    }

    /** @return true if removed, false if not whitelisted */
    public boolean remove(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        String normalized = username.toLowerCase();
        if (!whitelistedPlayers.remove(normalized)) {
            return false;
        }
        save();
        return true;
    }

    public boolean contains(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return whitelistedPlayers.contains(username.toLowerCase());
    }

    public List<String> list() {
        return new ArrayList<>(whitelistedPlayers);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * When enabled, whitelisted players plus OPs can access.
     * When disabled, only OPs can access.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public boolean isPublicMode() {
        return publicMode;
    }

    /**
     * When enabled, all players can access Ascend without restriction.
     */
    public void setPublicMode(boolean publicMode) {
        this.publicMode = publicMode;
        save();
    }

    private void load() {
        if (!whitelistFile.exists()) {
            LOGGER.atInfo().log("Whitelist file not found, creating new one: " + whitelistFile.getPath());
            save();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(whitelistFile.toPath(), StandardCharsets.UTF_8)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (json == null) {
                LOGGER.atWarning().log("Whitelist file is empty, resetting");
                save();
                return;
            }

            // Load enabled flag (default remains false if not present)
            if (json.has("enabled")) {
                enabled = json.get("enabled").getAsBoolean();
            }

            // Load public mode flag (default remains false if not present)
            if (json.has("publicMode")) {
                publicMode = json.get("publicMode").getAsBoolean();
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
                LOGGER.atInfo().log("Loaded " + whitelistedPlayers.size() + " whitelisted players (enabled: " + enabled + ")");
            }
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load whitelist file, resetting to safe mode (access restricted to OPs only)");
            whitelistedPlayers.clear();
            enabled = false;
            publicMode = false;
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
            json.addProperty("publicMode", publicMode);

            JsonArray whitelisted = new JsonArray();

            for (String username : whitelistedPlayers) {
                whitelisted.add(username);
            }

            json.add("whitelisted", whitelisted);

            try (BufferedWriter writer = Files.newBufferedWriter(whitelistFile.toPath(), StandardCharsets.UTF_8)) {
                gson.toJson(json, writer);
            }
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save whitelist file");
        }
    }
}
