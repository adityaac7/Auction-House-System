import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Auction House.
 * Acts as a Client to the Bank and a Server to Agents.
 */
public class AuctionHouse {
        private final int auctionHouseId;
        private int auctionHouseAccountNumber;
        private final String bankHost;
        private final int bankPort;

       //private final Map<Integer, AuctionItem> items = new ConcurrentHashMap<>();
       // private final Map<Integer, NetworkClient> agentConnections = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java AuctionHouse [HouseId] [Bank_Host] [Bank_Port]");
            return;
        }

        int auctionHouseId = Integer.parseInt(args[0]);
        String bankHost = args[1];
        int bankPort = Integer.parseInt(args[2]);

        AuctionHouse house = new AuctionHouse(auctionHouseId, bankHost, bankPort);
        house.start();
    }

    public AuctionHouse(int auctionHouseId, String bankHost, int bankPort) {
        this.auctionHouseId = auctionHouseId;
        this.bankHost = bankHost;
        this.bankPort = bankPort;
        this.auctionHouseAccountNumber = -1;
        System.out.println("[AUCTION HOUSE " + auctionHouseId + "] Created for bank "
                + bankHost + ":" + bankPort);
    }


    private void start() {
        try {
            // 1. Connect to Bank
            connectToBank();

            // 2. Register House with Bank
            // TODO: Get Account ID from Bank

            // 3. Start Server for Agents
            startAgentServer();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void connectToBank() {
        // TODO: Implement socket connection to BankServer
        System.out.println("Connecting to Bank...");
    }

    private void startAgentServer() {
        // TODO: Open ServerSocket on dynamic port
        // TODO: Spawn AuctionServiceThreads for incoming Agents
        System.out.println("Starting Agent Server...");
    }
}