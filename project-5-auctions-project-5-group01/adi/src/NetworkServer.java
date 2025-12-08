import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utility class for handling server connections
 * FIXED: Host address discovery
 */
public class NetworkServer {
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public NetworkServer(int port) throws IOException {
        this.serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
    }

    public void start() {
        running = true;
    }

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

    public Socket acceptConnection() throws IOException {
        if (!running) {
            throw new IOException("Server is not running");
        }
        return serverSocket.accept();
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    // Proper host address discovery
    public String getHost() throws UnknownHostException {
        return InetAddress.getLocalHost().getHostAddress();
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isClosed() {
        return serverSocket.isClosed();
    }
}
