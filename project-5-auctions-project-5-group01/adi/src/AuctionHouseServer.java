import java.io.*;
import java.net.Socket;

/**
 * Main Auction House Server that manages items and handles bids
 * FIXED: Proper agent connection tracking, message handling, and deregistration
 */
public class AuctionHouseServer {

    private AuctionHouse auctionHouse;
    private NetworkServer networkServer;
    private NetworkClient bankClient;
    private volatile boolean running = false;

    // Bank account number for this auction house
    private int auctionHouseAccountNumber;

    public AuctionHouseServer(String bankHost,
                              int bankPort,
                              int auctionHousePort)
            throws IOException, ClassNotFoundException {
        this(bankHost, bankPort, auctionHousePort, true);
    }

    public AuctionHouseServer(String bankHost,
                              int bankPort,
                              int auctionHousePort,
                              boolean autoCreateItems)
            throws IOException, ClassNotFoundException {
        // Connect to bank
        this.bankClient = new NetworkClient(bankHost, bankPort);

        // Create network server
        this.networkServer = new NetworkServer(auctionHousePort);
        networkServer.start();

        // Register with bank
        String host = networkServer.getHost();
        int port = networkServer.getPort();
        BankMessages.RegisterAuctionHouseRequest regRequest =
                new BankMessages.RegisterAuctionHouseRequest(host, port);
        bankClient.sendMessage(regRequest);
        BankMessages.RegisterAuctionHouseResponse regResponse =
                (BankMessages.RegisterAuctionHouseResponse) bankClient.receiveMessage();
        if (!regResponse.success) {
            throw new IOException("Failed to register with bank: " + regResponse.message);
        }

        // Store bank account number for deregistration and transfers
        this.auctionHouseAccountNumber = regResponse.accountNumber;

        // Create auction house with account number
        int itemCount = autoCreateItems ? 3 : 0;
        this.auctionHouse = new AuctionHouse(
                regResponse.auctionHouseId,
                auctionHouseAccountNumber,
                bankClient,
                itemCount);

        System.out.println("[AUCTION HOUSE] Registered with bank");
        System.out.println("[AUCTION HOUSE] ID: " + regResponse.auctionHouseId);
        System.out.println("[AUCTION HOUSE] Account: " + regResponse.accountNumber);
        System.out.println("[AUCTION HOUSE] Address: " + host + ":" + port);

        // Start accepting agent connections
        startAcceptingConnections();
    }

    private void startAcceptingConnections() {
        running = true;
        Thread acceptThread = new Thread(() -> {
            System.out.println("[AUCTION HOUSE] Waiting for agent connections...");
            while (running) {
                try {
                    Socket agentSocket = networkServer.acceptConnection();
                    System.out.println("[AUCTION HOUSE] Agent connected: "
                            + agentSocket.getInetAddress());
                    // Socket first, then AuctionHouse
                    Thread handlerThread = new Thread(
                            new AuctionHouseClientHandler(agentSocket, auctionHouse));
                    handlerThread.setDaemon(true);
                    handlerThread.start();
                } catch (IOException e) {
                    if (running) {
                        System.out.println("[AUCTION HOUSE] Error accepting connection: "
                                + e.getMessage());
                    }
                }
            }
        });
        acceptThread.setDaemon(false);
        acceptThread.start();
    }

    public void stop() {
        running = false;

        // Attempt to deregister from bank
        try {
            if (bankClient != null) {
                BankMessages.DeregisterRequest request = new BankMessages.DeregisterRequest(auctionHouseAccountNumber,
                                "AUCTION_HOUSE");
                bankClient.sendMessage(request);
                // Ignore response body, just ensure round-trip
                bankClient.receiveMessage();
            }
        } catch (Exception e) {
            System.out.println("[AUCTION HOUSE] Error during deregistration: "
                    + e.getMessage());
        }

        networkServer.stop();
        System.out.println("[AUCTION HOUSE] Server stopped");
    }

    public AuctionHouse getAuctionHouse() {
        return auctionHouse;
    }
    public int getAuctionHouseAccountNumber() {
        return auctionHouseAccountNumber;
    }

    public void deregisterFromBank(BankMessages.DeregisterRequest request) throws Exception {
        bankClient.sendMessage(request);
        bankClient.receiveMessage();
    }

    public int getPort() {
        return networkServer.getPort();
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            // Correct usage: bank host, bank port, optional auction port
            System.out.println(
                    "Usage: java AuctionHouseServer <bank_host> <bank_port> [auction_port]");
            System.exit(1);
        }

        try {
            String bankHost = args[0];
            int bankPort = Integer.parseInt(args[1]);
            int auctionPort = args.length > 2 ? Integer.parseInt(args[2]) : 0;

            AuctionHouseServer server =
                    new AuctionHouseServer(bankHost, bankPort, auctionPort);
            System.out.println("Auction House Server running. Press Enter to stop...");
            System.in.read();
            server.stop();
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
