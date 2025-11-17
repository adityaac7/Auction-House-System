import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The central Bank Server.
 * TODO: Implement thread-safe account management.
 * TODO: Implement protocol for Agents and Auction Houses.
 */
public class BankServer {

    public static final int DEFAULT_PORT = 45454;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        System.out.println("Bank Server initializing on port " + port);

        // TODO: Initialize AccountManager (Thread-Safe Monitor)

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                System.out.println("Waiting for connections...");
                Socket client = serverSocket.accept();
                // TODO: Spawn a new ClientHandler thread for this connection
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}