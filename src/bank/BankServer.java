package bank;

import common.NetworkServer;
import java.io.IOException;
import java.net.Socket;

/**
 * The central Bank Server.
 * Manages the main server thread and launches handlers for client connections.
 */
public class BankServer {

    public static final int DEFAULT_PORT = 5000;

    public static void main(String[] args) {
        // 1. Initialize the core Bank logic manager
        Bank bank = new Bank();

        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port, using default: " + DEFAULT_PORT);
            }
        }

        System.out.println("Bank Server initializing on port " + port);

        try {
            // 2. Start the Network Listener (using common.NetworkServer)
            NetworkServer networkServer = new NetworkServer(port);
            networkServer.start();

            System.out.println("[BANK SERVER] Waiting for connections...");

            // 3. Accept Loop
            while (networkServer.isRunning()) {
                Socket clientSocket = networkServer.acceptConnection();
                System.out.println("[BANK SERVER] New client connected: " + clientSocket.getInetAddress());

                // 4. Spawn a Handler Thread
                BankClientHandler handler = new BankClientHandler(bank, clientSocket);
                Thread handlerThread = new Thread(handler);
                handlerThread.setDaemon(true); // Auto-close if main thread dies
                handlerThread.start();
            }
        } catch (IOException e) {
            System.err.println("Bank Server failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }
}