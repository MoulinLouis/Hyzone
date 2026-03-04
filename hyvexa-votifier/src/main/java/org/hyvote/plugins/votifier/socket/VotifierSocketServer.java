package org.hyvote.plugins.votifier.socket;

import org.hyvote.plugins.votifier.HytaleVotifierPlugin;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * TCP socket server for Votifier V2 protocol.
 *
 * <p>Listens on a configurable port (default 8192) and handles incoming
 * vote connections using the V2 protocol with challenge-response authentication.</p>
 *
 * <p>Protocol flow:</p>
 * <ol>
 *   <li>Client connects</li>
 *   <li>Server sends: "VOTIFIER 2 &lt;challenge&gt;\n"</li>
 *   <li>Client sends: 0x733A (magic) + length (2 bytes) + JSON wrapper</li>
 *   <li>Server validates and responds with JSON result</li>
 * </ol>
 */
public class VotifierSocketServer {

    private final HytaleVotifierPlugin plugin;
    private final int port;
    private final ExecutorService executorService;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running = false;

    /**
     * Creates a new VotifierSocketServer.
     *
     * @param plugin the plugin instance
     * @param port the port to listen on
     */
    public VotifierSocketServer(HytaleVotifierPlugin plugin, int port) {
        this.plugin = plugin;
        this.port = port;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "VotifierSocket-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the socket server.
     *
     * @throws IOException if the server cannot be started
     */
    public void start() throws IOException {
        if (running) {
            return;
        }

        serverSocket = new ServerSocket(port);
        running = true;

        acceptThread = new Thread(this::acceptLoop, "VotifierSocket-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        plugin.getLogger().at(Level.INFO).log("V2 socket server started on port %d", port);
    }

    /**
     * Stops the socket server.
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        // Close the server socket to interrupt accept()
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                plugin.getLogger().at(Level.WARNING).log("Error closing server socket: %s", e.getMessage());
            }
        }

        // Interrupt the accept thread
        if (acceptThread != null) {
            acceptThread.interrupt();
        }

        // Shutdown the executor service
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        plugin.getLogger().at(Level.INFO).log("V2 socket server stopped");
    }

    /**
     * Returns whether the server is running.
     *
     * @return true if the server is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the port the server is listening on.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();

                if (plugin.getConfig().debug()) {
                    plugin.getLogger().at(Level.INFO).log("V2 socket connection from %s",
                            clientSocket.getRemoteSocketAddress());
                }

                // Handle the connection in a separate thread
                executorService.submit(new VotifierSocketHandler(plugin, clientSocket));

            } catch (SocketException e) {
                // Expected when server socket is closed during shutdown
                if (running) {
                    plugin.getLogger().at(Level.WARNING).log("Socket accept error: %s", e.getMessage());
                }
            } catch (IOException e) {
                if (running) {
                    plugin.getLogger().at(Level.WARNING).log("Error accepting connection: %s", e.getMessage());
                }
            }
        }
    }
}
