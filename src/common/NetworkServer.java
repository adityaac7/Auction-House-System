package common;

import java.io.*;
import java.net.*;
import java.util.Enumeration;


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
        this.serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
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
    /**
     * Returns the local port number that this server is bound to.
     *
     * @return the local TCP port, or {@code -1} if the server socket is not initialized
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Returns the IP address of the local host that this server is running on.
     * This method attempts to find a non-loopback, non-link-local IPv4 address.
     *
     * @return the string representation of the local host address
     * @throws UnknownHostException if no suitable address could be found
     */
    public String getHost() throws UnknownHostException {
        try {
            // Try to find a suitable network interface
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

                    // We want IPv4, non-loopback, non-link-local
                    if (address instanceof Inet4Address &&
                            !address.isLoopbackAddress() &&
                            !address.isLinkLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }

            // Fallback: return localhost if no suitable address found
            return InetAddress.getLocalHost().getHostAddress();

        } catch (SocketException e) {
            throw new UnknownHostException("Failed to get network interfaces: " + e.getMessage());
        }
    }

    /**
     * Indicates whether the server is currently marked as running.
     * <p>
     * Note that this flag is independent of the underlying socket state; it simply
     * reflects whether {@link #start()} has been called without a subsequent {@link #stop()}.
     *
     * @return {@code true} if the server is marked as running, {@code false} otherwise
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Indicates whether the underlying {@link ServerSocket} has been closed.
     *
     * @return {@code true} if the server socket is closed, {@code false} otherwise
     */
    public boolean isClosed() {
        return serverSocket.isClosed();
    }
}
