import java.io.IOException;

/**
 * The Auction House.
 * Acts as a Client to the Bank and a Server to Agents.
 */
public class AuctionHouse {

    public static void main(String[] args) {
        // TODO: Handle command line args for Bank Host/Port

        AuctionHouse house = new AuctionHouse();
        house.start();
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