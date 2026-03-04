package org.hyvote.plugins.votifier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import org.hyvote.plugins.votifier.command.TestVoteCommand;
import org.hyvote.plugins.votifier.command.VoteCommand;
import org.hyvote.plugins.votifier.crypto.RSAKeyManager;
import org.hyvote.plugins.votifier.http.FallbackHttpServer;
import org.hyvote.plugins.votifier.http.NitradoWebServerBridge;
import org.hyvote.plugins.votifier.reminder.VoteReminderService;
import org.hyvote.plugins.votifier.reminder.VoteTracker;
import org.hyvote.plugins.votifier.socket.VotifierSocketServer;
import org.hyvote.plugins.votifier.storage.StorageException;
import org.hyvote.plugins.votifier.storage.VoteStorage;
import org.hyvote.plugins.votifier.storage.VoteStorageFactory;
import org.hyvote.plugins.votifier.util.UpdateChecker;
import org.hyvote.plugins.votifier.util.UpdateNotificationUtil;
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * HytaleVotifier - A Votifier-style plugin for Hytale that receives vote notifications
 * from voting websites via HTTP and fires events for other plugins to handle rewards.
 */
public class HytaleVotifierPlugin extends JavaPlugin {

    private static final String CONFIG_FILE = "config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final String pluginVersion;
    private VotifierConfig config;
    private RSAKeyManager keyManager;
    private WebServerPlugin webServerPlugin;
    private FallbackHttpServer fallbackHttpServer;
    private VotifierSocketServer socketServer;
    private VoteStorage voteStorage;
    private VoteReminderService voteReminderService;
    private volatile boolean updateAvailable = false;
    private volatile String latestVersion = null;

    public HytaleVotifierPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        this.pluginVersion = loadVersionFromManifest();
    }

    private String loadVersionFromManifest() {
        try (InputStream is = getClass().getResourceAsStream("/manifest.json")) {
            if (is == null) {
                getLogger().at(Level.WARNING).log("manifest.json not found, using fallback version");
                return "unknown";
            }
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                var manifest = GSON.fromJson(reader, ManifestInfo.class);
                return manifest.version() != null ? manifest.version() : "unknown";
            }
        } catch (IOException e) {
            getLogger().at(Level.WARNING).log("Failed to read manifest.json: %s", e.getMessage());
            return "unknown";
        }
    }

    private record ManifestInfo(String Version) {
        String version() { return Version; }
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("HytaleVotifier enabling...");
        loadConfig();
        initializeKeys();
        initializeWebServer();
        initializeSocketServer();
        initializeVoteReminderService();
        registerCommands();
        registerEventListeners();
        checkForUpdates();
        getLogger().at(Level.INFO).log("HytaleVotifier enabled - debug=%s, keyPath=%s", config.debug(), config.keyPath());
    }

    @Override
    protected void shutdown() {
        if (socketServer != null && socketServer.isRunning()) {
            socketServer.stop();
        }
        if (fallbackHttpServer != null && fallbackHttpServer.isRunning()) {
            fallbackHttpServer.stop();
        }
        if (webServerPlugin != null) {
            NitradoWebServerBridge.unregisterServlets(this, webServerPlugin);
        }
        if (voteReminderService != null) {
            voteReminderService.shutdown();
        }
        if (voteStorage != null) {
            voteStorage.shutdown();
        }
        getLogger().at(Level.INFO).log("HytaleVotifier disabled");
    }

    /**
     * Returns the plugin configuration.
     *
     * @return the current configuration
     */
    public VotifierConfig getConfig() {
        return config;
    }

    /**
     * Returns the RSA key manager.
     *
     * @return the RSA key manager
     */
    public RSAKeyManager getKeyManager() {
        return keyManager;
    }

    private void loadConfig() {
        getLogger().at(Level.INFO).log("Loading configuration...");
        Path configPath = getDataDirectory().resolve(CONFIG_FILE);
        VotifierConfig defaults = VotifierConfig.defaults();

        try {
            Files.createDirectories(configPath.getParent());

            if (Files.exists(configPath)) {
                String json = Files.readString(configPath);
                VotifierConfig loaded = GSON.fromJson(json, VotifierConfig.class);
                // Merge with defaults to handle missing/null fields
                VoteMessageConfig mergedVoteMessage = loaded.voteMessage() != null
                        ? loaded.voteMessage().merge(defaults.voteMessage())
                        : defaults.voteMessage();
                BroadcastConfig mergedBroadcast = loaded.broadcast() != null
                        ? loaded.broadcast().merge(defaults.broadcast())
                        : defaults.broadcast();
                VoteSiteTokenConfig mergedVoteSites = loaded.voteSites() != null
                        ? loaded.voteSites()
                        : defaults.voteSites();
                SocketConfig mergedSocket = loaded.socketServer() != null
                        ? loaded.socketServer().merge(defaults.socketServer())
                        : defaults.socketServer();
                HttpServerConfig mergedHttpServer = loaded.internalHttpServer() != null
                        ? loaded.internalHttpServer().merge(defaults.internalHttpServer())
                        : defaults.internalHttpServer();
                ProtocolConfig mergedProtocols = loaded.protocols() != null
                        ? loaded.protocols().merge(defaults.protocols())
                        : defaults.protocols();
                VoteCommandConfig mergedVoteCommand = loaded.voteCommand() != null
                        ? loaded.voteCommand().merge(defaults.voteCommand())
                        : defaults.voteCommand();
                VoteReminderConfig mergedVoteReminder = loaded.voteReminder() != null
                        ? loaded.voteReminder().merge(defaults.voteReminder())
                        : defaults.voteReminder();
                this.config = new VotifierConfig(
                        loaded.debug(),
                        loaded.keyPath() != null ? loaded.keyPath() : defaults.keyPath(),
                        mergedVoteMessage,
                        mergedBroadcast,
                        loaded.rewardCommands() != null ? loaded.rewardCommands() : defaults.rewardCommands(),
                        mergedVoteSites,
                        mergedSocket,
                        mergedHttpServer,
                        mergedProtocols,
                        mergedVoteCommand,
                        mergedVoteReminder
                );

                // Write merged config back to add any new config sections to legacy configs
                String mergedJson = GSON.toJson(config);
                if (!mergedJson.equals(json)) {
                    Files.writeString(configPath, mergedJson);
                    getLogger().at(Level.INFO).log("Updated configuration with new fields at %s", configPath);
                } else {
                    getLogger().at(Level.INFO).log("Loaded configuration from %s", configPath);
                }
            } else {
                this.config = defaults;
                Files.writeString(configPath, GSON.toJson(config));
                getLogger().at(Level.INFO).log("Created default configuration at %s", configPath);
            }
        } catch (IOException e) {
            getLogger().at(Level.WARNING).log("Failed to load/save config, using defaults: %s", e.getMessage());
            this.config = defaults;
        }
    }

    private void initializeKeys() {
        this.keyManager = new RSAKeyManager();
        Path keyDirectory = getDataDirectory().resolve(config.keyPath());

        try {
            if (keyManager.keysExist(keyDirectory)) {
                keyManager.loadKeyPair(keyDirectory);
                getLogger().at(Level.INFO).log("Loaded existing RSA keys from %s", keyDirectory);
            } else {
                getLogger().at(Level.INFO).log("No RSA keys found, generating new 2048-bit key pair...");
                keyManager.generateKeyPair();
                keyManager.saveKeyPair(keyDirectory);
                getLogger().at(Level.INFO).log("Generated new RSA key pair and saved to %s", keyDirectory);
            }
        } catch (IOException e) {
            getLogger().at(Level.SEVERE).log("Failed to initialize RSA keys: %s", e.getMessage());
            throw new RuntimeException("Failed to initialize RSA keys", e);
        }
    }

    private void initializeWebServer() {
        // Check if V1 protocol is enabled - HTTP server is only needed for V1
        ProtocolConfig protocols = config.protocols();
        if (protocols == null || !Boolean.TRUE.equals(protocols.v1Enabled())) {
            getLogger().at(Level.INFO).log("V1 protocol disabled - HTTP server not started");
            return;
        }

        var plugin = PluginManager.get().getPlugin(new PluginIdentifier("Nitrado", "WebServer"));
        if (plugin instanceof WebServerPlugin webServer) {
            this.webServerPlugin = webServer;
            registerServlets();
            return;
        }

        // Nitrado:WebServer not available, try fallback HTTP server
        getLogger().at(Level.WARNING).log("Nitrado:WebServer plugin not found - attempting fallback HTTP server");
        initializeFallbackHttpServer();
    }

    private void initializeFallbackHttpServer() {
        HttpServerConfig httpConfig = config.internalHttpServer();
        if (httpConfig == null || !httpConfig.enabled()) {
            getLogger().at(Level.SEVERE).log("Fallback HTTP server disabled - HTTP endpoints will not be available");
            return;
        }

        try {
            FallbackHttpServer server = new FallbackHttpServer(this, httpConfig.port());
            server.start();
            fallbackHttpServer = server;
        } catch (IOException e) {
            getLogger().at(Level.SEVERE).log("Failed to start fallback HTTP server on port %d: %s",
                    httpConfig.port(), e.getMessage());
        }
    }

    private void registerServlets() {
        if (webServerPlugin == null) {
            return;
        }
        // Use bridge class to defer loading of servlet classes until we know Nitrado is available
        NitradoWebServerBridge.registerServlets(this, webServerPlugin);
    }

    private void initializeSocketServer() {
        SocketConfig socketConfig = config.socketServer();
        if (socketConfig == null || !socketConfig.enabled()) {
            getLogger().at(Level.INFO).log("V2 socket server disabled");
            return;
        }

        if (config.voteSites() == null || !config.voteSites().isV2Enabled()) {
            getLogger().at(Level.WARNING).log("V2 socket server enabled but no voteSites configured - socket server will reject all votes");
        }

        try {
            socketServer = new VotifierSocketServer(this, socketConfig.port());
            socketServer.start();
        } catch (IOException e) {
            getLogger().at(Level.SEVERE).log("Failed to start V2 socket server on port %d: %s",
                    socketConfig.port(), e.getMessage());
        }
    }

    private void initializeVoteReminderService() {
        VoteReminderConfig reminderConfig = config.voteReminder();
        if (reminderConfig == null || !reminderConfig.enabled()) {
            getLogger().at(Level.INFO).log("Vote reminder service disabled");
            return;
        }

        // Initialize vote storage backend
        try {
            voteStorage = VoteStorageFactory.create(
                    reminderConfig.storage(),
                    getDataDirectory(),
                    getLogger()
            );
            getLogger().at(Level.INFO).log("Vote storage initialized: type=%s", voteStorage.getType());
        } catch (StorageException e) {
            getLogger().at(Level.SEVERE).log("Failed to initialize vote storage: %s", e.getMessage());
            return;
        }

        VoteTracker voteTracker = new VoteTracker(voteStorage);
        voteReminderService = new VoteReminderService(this, voteTracker);
        getLogger().at(Level.INFO).log("Vote reminder service enabled - sendOnJoin=%s, delayInSeconds=%d, voteExpiryInterval=%d, storage=%s",
                reminderConfig.sendOnJoin(), reminderConfig.delayInSeconds(), reminderConfig.voteExpiryInterval(), voteStorage.getType());
    }

    private void registerCommands() {
        TestVoteCommand testVoteCommand = new TestVoteCommand(this);
        getCommandRegistry().registerCommand(testVoteCommand);
        getLogger().at(Level.INFO).log("Registered /testvote command");

        VoteCommand voteCommand = new VoteCommand(this);
        getCommandRegistry().registerCommand(voteCommand);
        getLogger().at(Level.INFO).log("Registered /vote command");
    }

    private void registerEventListeners() {
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        getLogger().at(Level.INFO).log("Registered player ready event listener");
    }

    private void checkForUpdates() {
        UpdateChecker.checkForUpdate(this, pluginVersion).thenAccept(newVersion -> {
            if (newVersion != null) {
                this.updateAvailable = true;
                this.latestVersion = newVersion;
                UpdateNotificationUtil.logUpdateAvailable(this, newVersion);
            }
        });
    }

    private void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();

        // Handle vote reminders for all players
        if (voteReminderService != null) {
            voteReminderService.onPlayerJoin(player);
        }

        // Handle update notifications for admin players
        if (updateAvailable) {
            if (player.hasPermission("votifier.admin") || player.hasPermission("votifier.admin.update_notifications")) {
                UpdateNotificationUtil.sendUpdateNotification(this, player);

                if (config.debug()) {
                    getLogger().at(Level.INFO).log(
                            "Notified OP player %s about available update",
                            player.getDisplayName());
                }
            }
        }
    }

    /**
     * Returns the current plugin version.
     *
     * @return the plugin version string
     */
    public String getPluginVersion() {
        return pluginVersion;
    }

    /**
     * Returns whether an update is available.
     *
     * @return true if a newer version is available
     */
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    /**
     * Returns the latest available version, or null if not checked or up-to-date.
     *
     * @return the latest version string, or null
     */
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Returns the vote reminder service, or null if disabled.
     *
     * @return the vote reminder service, or null
     */
    public VoteReminderService getVoteReminderService() {
        return voteReminderService;
    }
}
