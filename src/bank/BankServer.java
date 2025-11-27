package bank;

import common.NetworkServer;
import java.io.IOException;
import java.net.Socket;

/**
 * The central Bank Server application.
 * responsible for initializing the Bank logic and managing the network listener.
 * This class supports start/stop operations to allow integration with a JavaFX GUI.
 */
public class BankServer {

    public static final int DEFAULT_PORT = 5000;

    private Bank bank;
    private NetworkServer networkServer;
    private volatile boolean running = false;
    private int port;

    /**
     * Initializes a new BankServer on the specified port.
     *
     * @param port The TCP port to listen on.
     */
    public BankServer(int port) {
        this.port = port;
        this.bank = new Bank();
    }

    /**
     * Starts the server in a non-blocking background thread.
     * This initializes the NetworkServer and begins accepting client connections.
     *
     * @throws IOException If the server cannot bind to the specified port.
     */
    public void start() throws IOException {
        this.networkServer = new NetworkServer(port);
        this.networkServer.start();
        this.running = true;

        System.out.println("[BANK SERVER] Started on port " + port);

        // Spawn a daemon thread to handle the accept loop so the main thread isn't blocked
        Thread acceptThread = new Thread(this::acceptLoop);
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /**
     * The main loop that continuously accepts incoming client connections.
     * For each connection, a new BankClientHandler thread is spawned.
     */
    private void acceptLoop() {
        while (running && networkServer.isRunning()) {
            try {
                Socket clientSocket = networkServer.acceptConnection();

                // Create and start a handler for this specific client
                BankClientHandler handler = new BankClientHandler(bank, clientSocket);
                Thread handlerThread = new Thread(handler);
                handlerThread.setDaemon(true);
                handlerThread.start();

            } catch (IOException e) {
                if (running) {
                    System.out.println("[BANK SERVER] Accept loop error: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Stops the server and closes the listening socket.
     * Existing client connections may be terminated.
     */
    public void stop() {
        running = false;
        if (networkServer != null) {
            networkServer.stop();
        }
        System.out.println("[BANK SERVER] Stopped.");
    }

    /**
     * Entry point for running the Bank Server in console mode.
     *
     * @param args Command line arguments (optional port number).
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port argument. Using default port: " + DEFAULT_PORT);
            }
        }

        BankServer server = new BankServer(port);
        try {
            server.start();

            // Keep the main thread alive to prevent the JVM from exiting
            System.out.println("Press Enter to stop the server...");
            System.in.read();

            server.stop();
        } catch (IOException e) {
            System.err.println("Bank Server failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }
}