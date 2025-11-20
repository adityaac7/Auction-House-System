import java.io.*;
import java.net.ServerSocket;


/**
 * Utility class for handling server connections
 */
public class NetworkServer {
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public NetworkServer(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
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
}
