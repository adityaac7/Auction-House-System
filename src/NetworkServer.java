import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;


/**
 * Utility class for handling simple TCP server connections.
 * Wraps a {@link ServerSocket} and exposes convenience methods
 * for starting, stopping, and accepting client connections.
 */
public class NetworkServer {
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    /**
     * Creates a new {@code NetworkServer} bound to the given port.
     * @param port the local port to bind to; use {@code 0} to let the OS choose an ephemeral port
     * @throws IOException if the underlying {@link ServerSocket} cannot be created
     */
    public NetworkServer(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
    }

    /**
     * Marks the server as running so that calls to {@link #acceptConnection()}
     * are allowed to accept incoming client sockets.
     * This method does not block; it simply flips the internal {@code running} flag.
     */
    public void start() {
        running = true;
    }

    /**
     * Stops the server and closes the underlying {@link ServerSocket}.
     * After this call, {isRunning()} returns {@code false} and
     * {@link #acceptConnection()} will fail with an {@link IOException}.
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Blocks until a client connects, then returns the accepted {@Socket}.
     * @return a connected {@Socket} for communicating with the client
     * @throws IOException if the server is not running or an I/O error occurs
     * while waiting for a connection
     */
    public Socket acceptConnection() throws IOException {
        if (!running) {
            throw new IOException("Server is not running");
        }
        return serverSocket.accept();
    }
}
