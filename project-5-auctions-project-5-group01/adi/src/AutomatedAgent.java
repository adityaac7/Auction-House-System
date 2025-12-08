import java.io.IOException;
import java.util.Random;

/**
 * Automated agent that randomly bids on items
 */
public class AutomatedAgent {

    private Agent agent;
    private volatile boolean running = false;
    private Random random;
    private long bidInterval;      // Milliseconds between bids
    private double bidMultiplier;  // How much to increase bid (e.g., 1.1 = 10% increase)

    public AutomatedAgent(String agentName,
                          double initialBalance,
                          String bankHost,
                          int bankPort,
                          long bidInterval,
                          double bidMultiplier)
            throws IOException, ClassNotFoundException {
        this.agent = new Agent(agentName, initialBalance, bankHost, bankPort);
        this.random = new Random();
        this.bidInterval = bidInterval;
        this.bidMultiplier = bidMultiplier;
        System.out.println("[AUTO AGENT] Created: " + agentName);
        System.out.println("[AUTO AGENT] Bid Interval: " + bidInterval + "ms");
        System.out.println("[AUTO AGENT] Bid Multiplier: " + bidMultiplier);
    }

    public void start() {
        running = true;

        Thread biddingThread = new Thread(() -> {
            try {
                // Connect to all auction houses
                AuctionHouseInfo[] auctionHouses = agent.getAuctionHouses();
                for (AuctionHouseInfo house : auctionHouses) {
                    agent.connectToAuctionHouse(house.auctionHouseId);
                    agent.startListeningForNotifications(house.auctionHouseId);
                }

                System.out.println("[AUTO AGENT] Connected to "
                        + auctionHouses.length + " auction houses");

                // Continuously bid on items
                while (running) {
                    try {
                        if (auctionHouses.length > 0) {
                            // Select a random auction house
                            AuctionHouseInfo selectedHouse =
                                    auctionHouses[random.nextInt(auctionHouses.length)];

                            // Get items from the auction house
                            AuctionItem[] items = agent.getItemsFromAuctionHouse(
                                    selectedHouse.auctionHouseId);
                            if (items.length > 0) {
                                // Select a random item
                                AuctionItem selectedItem =
                                        items[random.nextInt(items.length)];

                                double bidAmount = calculateBidAmount(selectedItem);

                                // Check if we have enough available funds
                                if (agent.getAvailableFunds() >= bidAmount) {
                                    agent.placeBid(
                                            selectedHouse.auctionHouseId,
                                            selectedItem.itemId,
                                            bidAmount);
                                } else {
                                    System.out.println(
                                            "[AUTO AGENT] Insufficient funds for bid");
                                }
                            }
                        }

                        Thread.sleep(bidInterval);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.out.println("[AUTO AGENT] Error during bidding: "
                                + e.getMessage());
                        try {
                            Thread.sleep(bidInterval);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[AUTO AGENT] Error: " + e.getMessage());
                e.printStackTrace();
            }
        });

        biddingThread.setDaemon(true);
        biddingThread.start();
    }

    private double calculateBidAmount(AuctionItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Item cannot be null");
        }

        if (item.currentBid <= 0) {
            // First bid: near minimum
            return item.minimumBid
                    + random.nextDouble() * item.minimumBid * 0.5;
        } else {
            // Increase current bid
            return item.currentBid * bidMultiplier
                    + random.nextDouble() * item.minimumBid * 0.1;
        }
    }

    public void stop() {
        running = false;
        agent.disconnect();
        System.out.println("[AUTO AGENT] Stopped");
    }

    public static void main(String[] args) {
        String agentName = "AutoBot";
        double initialBalance = 5000;
        String bankHost = "localhost";
        int bankPort = 5000; // FIXED: match BankServer default port
        long bidInterval = 5000;  // 5 seconds
        double bidMultiplier = 1.15; // 15% increase per bid

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-n") || args[i].equals("--name")) {
                if (i + 1 < args.length) {
                    agentName = args[i + 1];
                    i++;
                }
            } else if (args[i].equals("-b") || args[i].equals("--balance")) {
                if (i + 1 < args.length) {
                    initialBalance = Double.parseDouble(args[i + 1]);
                    i++;
                }
            } else if (args[i].equals("-bh") || args[i].equals("--bank-host")) {
                if (i + 1 < args.length) {
                    bankHost = args[i + 1];
                    i++;
                }
            } else if (args[i].equals("-bp") || args[i].equals("--bank-port")) {
                if (i + 1 < args.length) {
                    bankPort = Integer.parseInt(args[i + 1]);
                    i++;
                }
            } else if (args[i].equals("-i") || args[i].equals("--interval")) {
                if (i + 1 < args.length) {
                    bidInterval = Long.parseLong(args[i + 1]);
                    i++;
                }
            } else if (args[i].equals("-m") || args[i].equals("--multiplier")) {
                if (i + 1 < args.length) {
                    bidMultiplier = Double.parseDouble(args[i + 1]);
                    i++;
                }
            }
        }

        try {
            AutomatedAgent agent = new AutomatedAgent(
                    agentName, initialBalance, bankHost, bankPort,
                    bidInterval, bidMultiplier);
            agent.start();

            // Keep the agent running until interrupted
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[AUTO AGENT] Shutting down...");
                agent.stop();
            }));

            // Wait indefinitely
            Thread.currentThread().join();
        } catch (IOException | InterruptedException | ClassNotFoundException e) {
            System.err.println("Error starting automated agent: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
