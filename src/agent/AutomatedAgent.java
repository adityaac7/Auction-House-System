package agent;

import common.AuctionHouseInfo;
import common.AuctionItem;

import java.io.IOException;
import java.util.Random;

/**
 * Automated agent that randomly bids on items in all visible auction houses.
 * <p>
 * This class wraps a regular {@link Agent} instance and drives it with a background
 * bidding thread that periodically:
 * <ul>
 *   <li>Chooses a random auction house</li>
 *   <li>Fetches its current items</li>
 *   <li>Selects a random item</li>
 *   <li>Places a bid based on a configurable multiplier strategy</li>
 * </ul>
 * The process continues until {@link #stop()} is called or the JVM shuts down.
 */
public class AutomatedAgent {

    /** Underlying interactive Agent used for all network communication and bidding. */
    private Agent agent;
    /** Flag indicating whether the automated bidding loop should continue running. */
    private volatile boolean running = false;
    /** Source of randomness for selecting auction houses, items, and small bid noise. */
    private Random random;
    /** Milliseconds to sleep between bid attempts. */
    private long bidInterval;
    /** Multiplier applied to the current bid when increasing an existing bid. */
    private double bidMultiplier;

    /**
     * Constructs a new {@code AutomatedAgent} and registers the underlying {@link Agent}
     * with the bank.
     *
     * @param agentName      display name of this automated agent
     * @param initialBalance initial balance to register with the bank
     * @param bankHost       hostname or IP of the bank server
     * @param bankPort       port of the bank server
     * @param bidInterval    delay in milliseconds between bid attempts
     * @param bidMultiplier  multiplier applied to the current bid for new bids
     * @throws IOException            if communication with the bank fails
     * @throws ClassNotFoundException if deserializing messages from the bank fails
     */
    public AutomatedAgent(String agentName, double initialBalance, String bankHost, int bankPort, long bidInterval,
                          double bidMultiplier) throws IOException, ClassNotFoundException {
        this.agent = new Agent(agentName, initialBalance, bankHost, bankPort);
        this.random = new Random();
        this.bidInterval = bidInterval;
        this.bidMultiplier = bidMultiplier;
        System.out.println("[AUTO AGENT] Created: " + agentName);
        System.out.println("[AUTO AGENT] Bid Interval: " + bidInterval + "ms");
        System.out.println("[AUTO AGENT] Bid Multiplier: " + bidMultiplier);
    }

    /**
     * Starts the automated bidding behavior in a daemon background thread.
     * <p>
     * This method:
     * <ol>
     *   <li>Marks the agent as running</li>
     *   <li>Connects to all known auction houses and starts notification listeners</li>
     *   <li>Enters a loop that periodically attempts random bids until stopped</li>
     * </ol>
     * Calling this method multiple times without stopping has no additional effect
     * beyond starting more bidding threads.
     */
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

    /**
     * Calculates the next bid amount for a given item using the configured strategy.
     * <p>
     * If the item has no current bid, a value near its minimum bid is chosen.
     * Otherwise, the bid is increased by {@code bidMultiplier} plus a small
     * random component.
     *
     * @param item auction item for which to compute a bid
     * @return the proposed bid amount
     * @throws IllegalArgumentException if {@code item} is {@code null}
     */
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

    /**
     * Stops the automated bidding loop and disconnects the underlying agent
     * from all auction houses and the bank.
     * <p>
     * After calling this method, the instance can be restarted with {@link #start()},
     * but any existing network state will have been torn down.
     */
    public void stop() {
        running = false;
        agent.disconnect();
        System.out.println("[AUTO AGENT] Stopped");
    }

    /**
     * Entry point for running the {@code AutomatedAgent} from the command line.
     * <p>
     * Supported arguments:
     * <ul>
     *   <li>{@code -n, --name} &lt;agentName&gt;</li>
     *   <li>{@code -b, --balance} &lt;initialBalance&gt;</li>
     *   <li>{@code -bh, --bank-host} &lt;host&gt;</li>
     *   <li>{@code -bp, --bank-port} &lt;port&gt;</li>
     *   <li>{@code -i, --interval} &lt;bidIntervalMs&gt;</li>
     *   <li>{@code -m, --multiplier} &lt;bidMultiplier&gt;</li>
     * </ul>
     * The process installs a shutdown hook so the agent is cleanly stopped
     * when the JVM is terminated.
     *
     * @param args command-line arguments configuring the automated agent
     */
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
