package org.hyvote.plugins.votifier.http;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;

import java.io.IOException;
import java.util.logging.Level;

/**
 * HTTP endpoint for server status.
 *
 * <p>Endpoint: GET /Hyvote/HytaleVotifier/status</p>
 *
 * <p>Returns JSON with server status information including supported protocols.</p>
 *
 * <p>Response format:</p>
 * <pre>
 * {
 *   "status": "ok",
 *   "version": "1.1.1",
 *   "serverType": "HytaleVotifier",
 *   "protocols": {"v1": true, "v2": true}
 * }
 * </pre>
 */
public class StatusServlet extends HttpServlet {

    private final HytaleVotifierPlugin plugin;

    public StatusServlet(HytaleVotifierPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");

        try {
            boolean v1Enabled = plugin.getConfig().protocols() != null
                    && Boolean.TRUE.equals(plugin.getConfig().protocols().v1Enabled());
            boolean v2Enabled = plugin.getConfig().protocols() != null
                    && Boolean.TRUE.equals(plugin.getConfig().protocols().v2Enabled())
                    && plugin.getConfig().voteSites() != null
                    && plugin.getConfig().voteSites().isV2Enabled();

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().println(VoteProcessor.statusJson(plugin.getPluginVersion(), v1Enabled, v2Enabled));
        } catch (Exception e) {
            plugin.getLogger().at(Level.SEVERE).withCause(e).log("Failed to process status request");
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().println(VoteProcessor.errorJson("Internal server error"));
        }
    }
}
