import java.io.IOException;
import java.net.Socket;

/**
 * Main Bank Server that listens for connections from agents and auction houses
 */
public class BankServer {
    private Bank bank;
    private NetworkServer networkServer;
    private volatile boolean running = false;

    public BankServer(int port) throws IOException {
        this.bank = new Bank();
        this.networkServer = new NetworkServer(port);
    }

    public void start() throws IOException {
        running = true;
        networkServer.start();
        System.out.println("[BANK] Server started on port " + networkServer.getPort());
        System.out.println("[BANK] Waiting for connections...");

        // Accept client connections in a separate thread
        Thread acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = networkServer.acceptConnection();
                    System.out.println("[BANK] New client connected: " +
                            clientSocket.getInetAddress());

                    BankClientHandler handler = new BankClientHandler(bank, clientSocket);
                    Thread handlerThread = new Thread(handler);
                    handlerThread.setDaemon(true);
                    handlerThread.start();

                } catch (IOException e) {
                    if (running) {
                        System.out.println("[BANK] Error accepting connection: " + e.getMessage());
                    }
                }
            }
        });
        acceptThread.setDaemon(false);
        acceptThread.start();
    }

    public void stop() {
        running = false;
        networkServer.stop();
        System.out.println("[BANK] Server stopped");
    }

    public int getPort() {
        return networkServer.getPort();
    }

    public static void main(String[] args) {
        int port = 5000; // Default port

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number. Using default port 5000.");
            }
        }

        try {
            BankServer server = new BankServer(port);
            server.start();

            System.out.println("\nBank Server running on port " + server.getPort());
            System.out.println("Press Enter to stop the server...\n");

            System.in.read();

            server.stop();
            System.out.println("Bank server shutdown complete.");
            System.exit(0);

        } catch (IOException e) {
            System.err.println("Failed to start bank server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
