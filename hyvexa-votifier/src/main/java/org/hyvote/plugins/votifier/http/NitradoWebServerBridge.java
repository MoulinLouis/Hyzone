package org.hyvote.plugins.votifier.http;

import net.nitrado.hytale.plugins.webserver.WebServerPlugin;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;

import java.util.logging.Level;

/**
 * Bridge class for Nitrado WebServer integration.
 *
 * <p>This class is loaded lazily to avoid {@link NoClassDefFoundError} when
 * the Nitrado WebServer plugin is not installed. All references to Jakarta
 * Servlet classes are contained within this class and its dependencies.</p>
 */
public final class NitradoWebServerBridge {

    private NitradoWebServerBridge() {
        // Utility class
    }

    /**
     * Registers the vote and status servlets with the Nitrado WebServer.
     *
     * @param plugin    the HytaleVotifier plugin instance
     * @param webServer the Nitrado WebServer plugin instance
     * @return true if registration succeeded, false otherwise
     */
    public static boolean registerServlets(HytaleVotifierPlugin plugin, WebServerPlugin webServer) {
        try {
            webServer.addServlet(plugin, "/vote", new VoteServlet(plugin));
            webServer.addServlet(plugin, "/status", new StatusServlet(plugin));
            plugin.getLogger().at(Level.INFO).log("Registered HTTP endpoints at /Hyvote/HytaleVotifier/vote and /status");
            return true;
        } catch (Exception e) {
            plugin.getLogger().at(Level.SEVERE).log("Failed to register HTTP endpoints: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Unregisters servlets from the Nitrado WebServer.
     *
     * @param plugin    the HytaleVotifier plugin instance
     * @param webServer the Nitrado WebServer plugin instance
     */
    public static void unregisterServlets(HytaleVotifierPlugin plugin, WebServerPlugin webServer) {
        webServer.removeServlets(plugin);
        plugin.getLogger().at(Level.INFO).log("Unregistered HTTP endpoints");
    }
}
