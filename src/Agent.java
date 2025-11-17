/**
 * The Bidding Agent (Client).
 * Connects to Bank for funds and AuctionHouse to bid.
 */
public class Agent {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java Agent [Name] [AH_Host] [AH_Port]");
            return;
        }

        String name = args[0];
        String host = args[1];
        int port = Integer.parseInt(args[2]);

        Agent agent = new Agent(name);
        agent.run(host, port);
    }

    private String name;

    public Agent(String name) {
        this.name = name;
    }

    public void run(String ahHost, int ahPort) {
        // 1. Connect to Bank
        // TODO: Create Bank Account

        // 2. Connect to Auction House
        // TODO: Connect and Listen for Item List

        // 3. User Interface Loop
        // TODO: Scanner loop for 'bid', 'balance', 'exit'
        System.out.println("Agent " + name + " started.");
    }
}