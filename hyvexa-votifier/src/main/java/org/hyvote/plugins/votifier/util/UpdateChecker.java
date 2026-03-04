package org.hyvote.plugins.votifier.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Utility class for checking GitHub releases for plugin updates.
 * Uses the public GitHub API (no authentication required for public repos).
 */
public final class UpdateChecker {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/Hyvote/hytale-votifier/releases/latest";
    private static final String CURSEFORGE_URL = "https://www.curseforge.com/hytale/mods/votifier";
    private static final String GITHUB_RELEASES_URL = "https://github.com/Hyvote/hytale-votifier/releases/latest";
    private static final Gson GSON = new Gson();

    // Cache the result to avoid excessive API calls.
    // Note: Static cache persists across plugin reloads. Call clearCache() if needed.
    private static String cachedLatestVersion = null;
    private static long lastCheckTime = 0;
    private static final long CACHE_DURATION_MS = 10 * 60 * 1000; // 10 minutes

    private UpdateChecker() {
        // Utility class
    }

    /**
     * Returns the CurseForge download URL.
     */
    public static String getCurseForgeUrl() {
        return CURSEFORGE_URL;
    }

    /**
     * Returns the GitHub releases URL.
     */
    public static String getGitHubReleasesUrl() {
        return GITHUB_RELEASES_URL;
    }

    /**
     * Asynchronously checks if a newer version is available on GitHub.
     *
     * @param plugin         the plugin instance for logging
     * @param currentVersion the current plugin version (e.g., "1.0.0")
     * @return a CompletableFuture that resolves to the latest version if newer, or null if up-to-date or error
     */
    public static CompletableFuture<String> checkForUpdate(HytaleVotifierPlugin plugin, String currentVersion) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String latestVersion = fetchLatestVersion(plugin);
                if (latestVersion == null) {
                    return null;
                }

                if (isNewerVersion(latestVersion, currentVersion)) {
                    if (plugin.getConfig().debug()) {
                        plugin.getLogger().at(Level.INFO).log(
                                "Update available: %s -> %s", currentVersion, latestVersion);
                    }
                    return latestVersion;
                } else {
                    if (plugin.getConfig().debug()) {
                        plugin.getLogger().at(Level.INFO).log(
                                "Plugin is up to date (current: %s, latest: %s)", currentVersion, latestVersion);
                    }
                    return null;
                }
            } catch (Exception e) {
                plugin.getLogger().at(Level.WARNING).log(
                        "Failed to check for updates: %s", e.getMessage());
                return null;
            }
        });
    }

    /**
     * Fetches the latest version from GitHub API.
     * Results are cached to avoid excessive API calls.
     */
    private static String fetchLatestVersion(HytaleVotifierPlugin plugin) throws IOException {
        // Check cache first
        long now = System.currentTimeMillis();
        if (cachedLatestVersion != null && (now - lastCheckTime) < CACHE_DURATION_MS) {
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log("Using cached version: %s", cachedLatestVersion);
            }
            return cachedLatestVersion;
        }

        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(GITHUB_API_URL);
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "HytaleVotifier-UpdateChecker");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                if (plugin.getConfig().debug()) {
                    plugin.getLogger().at(Level.WARNING).log(
                            "GitHub API returned status %d", responseCode);
                }
                return null;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                JsonObject json = GSON.fromJson(response.toString(), JsonObject.class);
                if (!json.has("tag_name") || json.get("tag_name").isJsonNull()) {
                    if (plugin.getConfig().debug()) {
                        plugin.getLogger().at(Level.WARNING).log(
                                "GitHub API response missing tag_name field");
                    }
                    return null;
                }
                String tagName = json.get("tag_name").getAsString();

                // Remove 'v' prefix if present (e.g., "v1.0.0" -> "1.0.0")
                String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;

                // Update cache
                cachedLatestVersion = version;
                lastCheckTime = now;

                return version;
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Compares two semantic version strings.
     * Release versions (without suffix) are considered newer than pre-release versions
     * (with suffix like -SNAPSHOT, -beta) of the same base version.
     *
     * @param latestVersion  the latest version from GitHub
     * @param currentVersion the current plugin version
     * @return true if latestVersion is newer than currentVersion
     */
    static boolean isNewerVersion(String latestVersion, String currentVersion) {
        // Normalize versions by removing suffixes for base comparison
        String latestBase = normalizeVersion(latestVersion);
        String currentBase = normalizeVersion(currentVersion);

        String[] latestParts = latestBase.split("\\.");
        String[] currentParts = currentBase.split("\\.");

        int maxLength = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < maxLength; i++) {
            int latestPart = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
            int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;

            if (latestPart > currentPart) {
                return true;
            } else if (latestPart < currentPart) {
                return false;
            }
        }

        // Base versions are equal - check if latest is a release and current is pre-release
        // A release (no suffix) is newer than a pre-release (has suffix) of the same version
        boolean latestIsPreRelease = latestVersion.contains("-");
        boolean currentIsPreRelease = currentVersion.contains("-");

        if (!latestIsPreRelease && currentIsPreRelease) {
            // Latest is release, current is pre-release (e.g., 1.0.0 vs 1.0.0-SNAPSHOT)
            return true;
        }

        return false;
    }

    /**
     * Normalizes a version string by removing suffixes like -SNAPSHOT, -beta, etc.
     */
    private static String normalizeVersion(String version) {
        int dashIndex = version.indexOf('-');
        return dashIndex > 0 ? version.substring(0, dashIndex) : version;
    }

    /**
     * Parses a version part string to an integer.
     */
    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Clears the version cache, forcing a fresh check on the next call.
     */
    public static void clearCache() {
        cachedLatestVersion = null;
        lastCheckTime = 0;
    }
}
